package com.voidchats.filters;

import com.voidchats.IPUtils;
import com.voidchats.admin.IPBlockManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.voidchats.AuditLogger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleWaffFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SimpleWaffFilter.class);

    // --- CONFIGURACIÓN DE RATE LIMITING ---
    private int maxAttempts = 3; // Valor por defecto, se sobrescribe en init
    private long timeWindowMs = 3600000L; // 1 hora por defecto (ms)

    // Expira automáticamente entradas viejas para evitar Memory Leaks
    private Cache<String, List<Long>> violationTracker;

    private List<Pattern> sqlPatterns = new ArrayList<>();
    private List<Pattern> xssPatterns = new ArrayList<>();

    // Rutas donde permitimos HTML/Scripts (Cargadas desde config.properties)
    private Set<String> xssAllowedPaths = new HashSet<>();

    // Rutas donde no bloqueamos SQL Injection (Para pruebas/debug/o lo controla su servlet)
    private Set<String> sqliAllowedPaths = new HashSet<>();

    // Rutas donde BLOQUEAMOS la petición pero NO BANEAMOS la IP (Bloqueo suave)
    private Set<String> sqliNoBanPaths = new HashSet<>();

    // Rutas exclusivas para Administradores (Cargadas desde config.properties)
    private Set<String> adminPaths = new HashSet<>();

    // Rutas Honeypot (Cargadas a RAM en el Listener)
    private Set<String> honeypotPaths = new HashSet<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext context = filterConfig.getServletContext();


        String xssAllowedStr = (String) context.getAttribute("waf.xss.allowedPaths");
        if (xssAllowedStr != null && !xssAllowedStr.isEmpty()) {
            this.xssAllowedPaths = parsePaths(xssAllowedStr);
        }

        String sqliAllowedStr = (String) context.getAttribute("waf.sqli.allowedPaths");
        if (sqliAllowedStr != null && !sqliAllowedStr.isEmpty()) {
            this.sqliAllowedPaths = parsePaths(sqliAllowedStr);
        }

        String sqliNoBanStr = (String) context.getAttribute("waf.sqli.noBanPaths");
        if (sqliNoBanStr != null && !sqliNoBanStr.isEmpty()) {
            this.sqliNoBanPaths = parsePaths(sqliNoBanStr);
        }

        // Strikes antes de Ban
        Integer maxAttemptsConfig = (Integer) context.getAttribute("waf.block.maxAttempts");
        Integer timeWindowSecondsConfig = (Integer) context.getAttribute("waf.block.timeWindowSeconds");

        if (maxAttemptsConfig != null) {
            this.maxAttempts = maxAttemptsConfig;
        }
        if (timeWindowSecondsConfig != null) {
            this.timeWindowMs = timeWindowSecondsConfig * 1000L; // Convertir a milisegundos
        }

        this.violationTracker = Caffeine.newBuilder()
                .expireAfterAccess(timeWindowMs, TimeUnit.MILLISECONDS)
                .maximumSize(100_000) // Tope de seguridad extra para evitar OOM extremo
                .build();

        String adminPathsStr = (String) context.getAttribute("security.admin.paths");
        if (adminPathsStr != null && !adminPathsStr.isEmpty()) {
            this.adminPaths = parsePaths(adminPathsStr);
        }

        @SuppressWarnings("unchecked")
        Set<String> loadedHoneypots = (Set<String>) context.getAttribute("honeypotPathsSet");
        if (loadedHoneypots != null) {
            this.honeypotPaths = loadedHoneypots;
        }

        log.info("SimpleWaffFilter initialized | XSS Exemptions: {} | SQLi Exemptions: {} | Admin Paths: {} | Max Attempts: {} | Ban Window: {}s",
                xssAllowedPaths.size(), sqliAllowedPaths.size(), adminPaths.size(), maxAttempts, (timeWindowMs/1000));

        // Reglas SQL Injection

        // A. Inyección basada en Tautologías y Uniones (Ej: ' OR '1'='1', UNION SELECT)
        // Detecta comillas seguidas de palabras clave lógicas o de unión.
        sqlPatterns.add(Pattern.compile("(?i)(['\"])\\s*(or|and|union|select|insert|update|delete|drop|alter|create|truncate)\\s+"));

        // B. Comentarios CONTEXTUALES (La clave para eliminar falsos positivos)
        sqlPatterns.add(Pattern.compile("(?i)(['\";])\\s*(--|#|/\\*)"));

        // C. Detecta intentos de fingerprinting o time-based blind SQLi
        sqlPatterns.add(Pattern.compile("(?i)\\b(sleep|benchmark|delay|waitfor)\\s*\\("));

        // D. Stacked Queries (Punto y coma seguido de nueva instrucción)
        sqlPatterns.add(Pattern.compile("(?i);\\s*(drop|alter|shutdown|grant|exec)\\b"));

        // E. Inyección de UNION (Detecta "UNION SELECT" o "UNION ALL SELECT")
        sqlPatterns.add(Pattern.compile("(?i)\\bunion\\s+(all\\s+)?select\\b"));

        // F. Tautologías Numéricas Típicas
        sqlPatterns.add(Pattern.compile("(?i)\\b(or|and)\\s+\\d+\\s*=\\s*\\d+"));

        // G. Manipulación de Orden y Agrupamiento
        sqlPatterns.add(Pattern.compile("(?i)\\b(order|group)\\s+by\\s+"));

        // H. Tautologías de String en campos Numéricos (Cubre el hueco de Regla F)
        sqlPatterns.add(Pattern.compile("(?i)\\b(or|and)\\s+['\"][a-zA-Z0-9]+['\"]\\s*=\\s*['\"][a-zA-Z0-9]+['\"]"));

        // I. Comentarios peligrosos al final de números
        // Detecta: numero + (espacio opcional) + -- + (espacio O final de linea)
        // Bloquea: "id=1--" (Final de linea) o "id=1-- drop" (Espacio)
        // PERMITE: "10--20" (Rango) o "Capitulo 1--Intro"
        sqlPatterns.add(Pattern.compile("(?i)\\d+\\s*--(\\s|$)"));

        // Reglas XSS (SE APLICAN SOLO EN RUTAS NO PERMITIDAS)
        xssPatterns.add(Pattern.compile("(?i)(<script|%3Cscript)"));
        xssPatterns.add(Pattern.compile("(?i)(javascript:|vbscript:|data:)"));
        xssPatterns.add(Pattern.compile("(?i)(onload|onerror|onclick|onmouseover)\\s*="));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();

        if (uri != null && uri.length() > 2048) {
            log.warn("DoS SHIELD: SimpleWaffFilter intercepted massive URI (length: {}) from IP: {}", uri.length(), IPUtils.getClientIp(httpRequest));
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        // --- 0. DEFENSA HONEYPOT (AUTO-BAN) ---
        if (honeypotPaths.contains(uri.toLowerCase())) {
            String attackerIp = IPUtils.getClientIp(httpRequest);

            log.error("HONEYPOT TRIPPED: IP '{}' attempted to access forbidden scanner path '{}'. Immediate Ban.", attackerIp, uri);

            // Banear IP real instantáneamente
            IPBlockManager.addIP(attackerIp);

            // Registro Asíncrono en Base de Datos de Auditoría
            HttpSession session = httpRequest.getSession(false);
            String username = (session != null) ? (String) session.getAttribute("user") : null;
            AuditLogger.getInstance().logAsync(attackerIp, username, "BLACKLISTED", "Honeypot path accessed. IP Auto-banned.");

            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Security Policy Violation");
            return;
        }

        // --- SEGURIDAD DE ADMINISTRADOR ---
        if (isAdminPath(uri)) {
            HttpSession session = httpRequest.getSession(false);
            String tipoUsuario = (session != null) ? (String) session.getAttribute("tipoUsuario") : null;

            if (session == null || !"admin".equalsIgnoreCase(tipoUsuario)) {
                log.warn("SECURITY BLOCK: Unauthorized admin access attempt to URI: {} from IP: {}", uri, IPUtils.getClientIp(httpRequest));
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Administrators only.");
                return;
            }
        }

        // --- INSPECCIÓN DE PARÁMETROS ---
        Map<String, String[]> params = httpRequest.getParameterMap();

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String paramName = entry.getKey();

            if (paramName != null && paramName.length() > 255) {
                log.warn("WAF BLOCK: Parameter name too large (length: {}) from IP={}", paramName.length(), IPUtils.getClientIp(httpRequest));
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter name is too long.");
                return;
            }

            String[] values = entry.getValue();
            for (String value : values) {

                if (value != null && value.length() > 20000) {
                    log.warn("WAF BLOCK: Payload too large (length: {}) in param '{}' from IP={}", value.length(), paramName, IPUtils.getClientIp(httpRequest));
                    httpResponse.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload is too large to process.");
                    return;
                }


                if (!isPathAllowedForSQLi(uri) && isMatch(value, sqlPatterns)) {

                    // Obtenemos la IP real del atacante para banearlo a él, no al proxy.
                    String attackerIp = IPUtils.getClientIp(httpRequest);

                    if (isPathNoBan(uri)) {
                        log.warn("WAF SOFT-BLOCK: SQLi pattern detected in No-Ban zone (URI: {}). IP={}", uri, attackerIp);
                        logAndBlock(response, request, "Invalid Characters Detected", entry.getKey(), value);
                        return;
                    }

                    boolean shouldBan = trackViolationAndCheckBan(attackerIp);

                    if (shouldBan) {

                        log.error("CRITICAL SECURITY: IP {} permanently banned. Too many SQLi attempts within time window.", attackerIp);

                        IPBlockManager.addIP(attackerIp);
                        violationTracker.invalidate(attackerIp);

                        logAndBlock(response, request, "SQL Injection detected (IP Banned due to repeated attacks)", entry.getKey(), value);
                    } else {
                        int current = getCurrentAttempts(attackerIp);
                        log.warn("WAF WARNING: SQLi attempt {}/{} from IP {}", current, maxAttempts, attackerIp);

                        logAndBlock(response, request, "Security Violation: Suspicious pattern detected", entry.getKey(), value);
                    }
                    return;
                }

                if (!isPathAllowedForXSS(uri) && isMatch(value, xssPatterns)) {
                    log.warn("WAF BLOCK: XSS pattern detected from IP={} on URI={}", IPUtils.getClientIp(httpRequest), uri);
                    logAndBlock(response, request, "XSS pattern detected in sensitive area", entry.getKey(), value);
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    // --- Métodos Auxiliares ---

    // Método helper para parsear las cadenas de config
    private Set<String> parsePaths(String rawConfig) {
        return Arrays.stream(rawConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean isMatch(String input, List<Pattern> patterns) {
        if (input == null || input.isEmpty()) return false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPathAllowedForXSS(String uri) {
        for (String allowed : xssAllowedPaths) {
            if (uri.endsWith(allowed)) return true;
        }
        return false;
    }

    private boolean isPathAllowedForSQLi(String uri) {
        for (String allowed : sqliAllowedPaths) {
            if (uri.endsWith(allowed)) return true;
        }
        return false;
    }

    private boolean isPathNoBan(String uri) {
        for (String noBan : sqliNoBanPaths) {
            // endsWith es más seguro para evitar coincidencias parciales erróneas.
            if (uri.endsWith(noBan)) return true;
        }
        return false;
    }

    private boolean isAdminPath(String uri) {
        for (String adminPath : adminPaths) {
            if (uri.contains(adminPath)) return true;
        }
        return false;
    }

    private boolean trackViolationAndCheckBan(String ip) {
        long now = System.currentTimeMillis();

        List<Long> timestamps = violationTracker.get(ip, k -> new ArrayList<>());

        if (timestamps == null) return false; // Safety check

        synchronized (timestamps) {
            timestamps.add(now);
            timestamps.removeIf(ts -> (now - ts) > timeWindowMs);
            boolean banned = timestamps.size() >= maxAttempts;
            // Al usar expireAfterAccess en la inicialización, Caffeine ya sabe que la IP
            return banned;
        }
    }

    private int getCurrentAttempts(String ip) {
        List<Long> timestamps = violationTracker.getIfPresent(ip);
        if (timestamps == null) return 0;

        synchronized (timestamps) {
            long now = System.currentTimeMillis();
            timestamps.removeIf(ts -> (now - ts) > timeWindowMs);
            return timestamps.size();
        }
    }


    private void logAndBlock(ServletResponse response, ServletRequest request, String reason, String param, String value) throws IOException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String ipReal = IPUtils.getClientIp(httpRequest);

        HttpSession session = httpRequest.getSession(false);
        String username = (session != null) ? (String) session.getAttribute("user") : null;

        String safeValue = (value != null && value.length() > 100) ? value.substring(0, 100) + "... [TRUNCATED]" : value;

        log.warn("WAF BLOCK [{}]: IP={} Param={} Value={}", reason, ipReal, param, safeValue);

        // Determinamos el tipo de evento en función del mensaje
        String eventType = "WAF_BLOCKED";
        if (reason.contains("SQL Injection")) eventType = "SQL_INJECTION";
        else if (reason.contains("XSS pattern")) eventType = "XSS_BLOCKED";

        // Registramos el evento asíncronamente
        AuditLogger.getInstance().logAsync(
                ipReal,
                username,
                eventType,
                "Param=" + param + " Value=" + safeValue
        );

        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Security Violation: " + reason);
    }

    @Override
    public void destroy() {
        if (this.violationTracker != null) {
            this.violationTracker.invalidateAll();
            this.violationTracker.cleanUp();
            log.info("SimpleWaffFilter destroyed. Caffeine cache cleared to prevent memory leaks.");
        }
    }
}