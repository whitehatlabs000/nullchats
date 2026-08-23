package com.voidchats.filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.voidchats.AuditLogger;
import com.voidchats.IPUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UserStatusFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UserStatusFilter.class);

    private DataSource ds;
    private Set<String> ignoreExtensions = new HashSet<>();
    private Set<String> ignorePaths = new HashSet<>();

    private Cache<String, Boolean> userStatusCache;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext context = filterConfig.getServletContext();

        this.ds = (DataSource) context.getAttribute("dbDataSource");
        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for UserStatusFilter.");
            throw new ServletException("DataSource not found in context.");
        }

        // Obtener baseUrl para construir rutas absolutas correctamente
        String baseUrlRaw = (String) context.getAttribute("appBaseUrl");
        final String baseUrl = (baseUrlRaw == null || baseUrlRaw.equals("/")) ? "" : baseUrlRaw;

        // extensiones a ignorar (Optimización)
        String extensionsStr = (String) context.getAttribute("logfilter.ignore.extensions");
        if (extensionsStr != null && !extensionsStr.isEmpty()) {
            this.ignoreExtensions = Arrays.stream(extensionsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }

        // rutas a ignorar
        String pathsStr = (String) context.getAttribute("userstatus.ignore.paths");
        if (pathsStr != null && !pathsStr.isEmpty()) {
            this.ignorePaths = Arrays.stream(pathsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(path -> baseUrl + path)
                    .collect(Collectors.toSet());
        }

        this.userStatusCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES) // El estado vive 1 minuto en RAM
                .maximumSize(10_000) // Soporta hasta 10,000 usuarios activos simultáneos
                .build();

        log.info("UserStatusFilter initialized | Ignoring {} extensions and {} paths | Cache: 1 minute", ignoreExtensions.size(), ignorePaths.size());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();

        if (uri != null && uri.length() > 2048) {
            log.warn("DoS SHIELD: UserStatusFilter intercepted massive URI (length: {})", uri.length());
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        String uriLower = uri.toLowerCase();

        for (String ext : ignoreExtensions) {
            if (uriLower.endsWith(ext)) {
                chain.doFilter(request, response);
                return;
            }
        }

        for (String path : ignorePaths) {
            if (uri.startsWith(path)) {
                chain.doFilter(request, response);
                return;
            }
        }

        HttpSession session = httpRequest.getSession(false);

        if (session != null && session.getAttribute("user") != null) {
            String username = (String) session.getAttribute("user");

            if (!isUserActive(username)) {
                // Usuario BANEADO o ELIMINADO detectado
                log.info("ACCOUNT DISABLED: Active session intercepted and destroyed for banned/deleted user '{}'", username);

                // Logueo asíncrono
                String ipAddress = IPUtils.getClientIp(httpRequest);
                AuditLogger.getInstance().logAsync(ipAddress, username, "ACCOUNT_ACCESS_DENIED", "Attempted to browse while disabled/deleted.");

                // Destruir la sesión inmediatamente
                session.invalidate();

                // Redirigir al login con mensaje de error
                String loginPath = httpRequest.getContextPath() + "/login";

                if (!uri.equals(loginPath)) {
                    httpResponse.sendRedirect(loginPath + "?error=account_disabled");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isUserActive(String username) {
        // Delegamos la lógica a Caffeine.
        return userStatusCache.get(username, key -> fetchUserStatusFromDB(key));
    }

    private boolean fetchUserStatusFromDB(String username) {
        String sql = "SELECT active FROM usuarios WHERE username = ?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("active");
                } else {
                    return false;
                }
            }
        } catch (SQLException e) {
            log.error("Database error while checking status for user '{}'", username, e);
            // En caso de error de DB, permitimos el paso para no bloquear a todos por un fallo técnico
            return true;
        }
    }

    @Override
    public void destroy() {
        if (this.userStatusCache != null) {
            this.userStatusCache.invalidateAll();
            this.userStatusCache.cleanUp();
            log.info("UserStatusFilter destroyed. Caffeine cache cleared to prevent memory leaks.");
        }
    }
}