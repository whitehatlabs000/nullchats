package com.voidchats.filters;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.voidchats.IPUtils;
import com.voidchats.AuditLogger;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Límites (se cargarán desde init)
    private int requestLimit;
    private int staticRequestLimit;
    private int strictRequestLimit;
    private long timeWindowMs;
    private long blockDurationMs;
    private String[] staticExtensions;
    private String[] staticPaths;
    private String[] strictPaths;


    private Cache<String, RequestCounter> requestCache;

    private static class RequestCounter {
        final long count;
        final long windowStartTime;
        final long blockExpiresTime;

        RequestCounter(long count, long windowStartTime, long blockExpiresTime) {
            this.count = count;
            this.windowStartTime = windowStartTime;
            this.blockExpiresTime = blockExpiresTime;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = IPUtils.getClientIp(httpRequest);
        long currentTime = System.currentTimeMillis();

        String method = httpRequest.getMethod();       // GET, POST, etc.
        String endpoint = httpRequest.getRequestURI(); // /api/usuarios, /index.css, etc.

        if (endpoint != null && endpoint.length() > 2048) {
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        // LÓGICA DE SEPARACIÓN DE RUTAS
        String cacheKey = clientIp;
        boolean isStatic = false;
        boolean isStrict = false;

        for (String path : strictPaths) {
            if (endpoint.endsWith(path)) {
                isStrict = true;
                break;
            }
        }

        if (!isStrict) {
            for (String ext : staticExtensions) {
                if (endpoint.endsWith(ext)) {
                    isStatic = true;
                    break;
                }
            }
        }

        if (!isStrict && !isStatic) {
            for (String path : staticPaths) {
                if (endpoint.contains(path)) {
                    isStatic = true;
                    break;
                }
            }
        }

        if (isStrict) {
            cacheKey = clientIp + "_STRICT";
        } else if (isStatic) {
            cacheKey = clientIp + "_STATIC";
        }


        log.debug("RL Key [{}] -> {}: {}", cacheKey, method, endpoint);

        // Si la IP principal está castigada, no le servimos ni estáticos ni dinámicos.
        RequestCounter mainCounter = requestCache.getIfPresent(clientIp);
        if (mainCounter != null && mainCounter.blockExpiresTime > 0 && currentTime < mainCounter.blockExpiresTime) {
            long retryAfterSeconds = (mainCounter.blockExpiresTime - currentTime) / 1000 + 1;

            HttpSession session = httpRequest.getSession(false);
            String username = (session != null) ? (String) session.getAttribute("user") : null;
            AuditLogger.getInstance().logAsync(clientIp, username, "RATE_LIMITED", "Blocked from endpoint: " + endpoint);

            httpResponse.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
            httpResponse.sendError(429, "Too Many Requests");
            return;
        }

        RequestCounter counter = requestCache.asMap().compute(cacheKey, (ip, existingCounter) -> {

            if (existingCounter == null) return new RequestCounter(1, currentTime, 0L);

            if (currentTime < existingCounter.blockExpiresTime) return existingCounter;

            if (currentTime - existingCounter.windowStartTime > this.timeWindowMs) {
                return new RequestCounter(1, currentTime, 0L);
            }

            long newCount = existingCounter.count + 1;

            int currentLimit;
            if (ip.endsWith("_STRICT")) {
                currentLimit = RateLimitingFilter.this.strictRequestLimit;
            } else if (ip.endsWith("_STATIC")) {
                currentLimit = RateLimitingFilter.this.staticRequestLimit;
            } else {
                currentLimit = RateLimitingFilter.this.requestLimit;
            }

            if (newCount > currentLimit) {
                long newBlockExpiresTime = currentTime + this.blockDurationMs;
                return new RequestCounter(newCount, existingCounter.windowStartTime, newBlockExpiresTime);
            }

            return new RequestCounter(newCount, existingCounter.windowStartTime, 0L);
        });

        // --- CONTAGIO DE PENALIZACIÓN ---
        // Si se bloqueo un canal secundario (estático o estricto), bloqueamos también la IP principal inmediatamente.
        if ((isStatic || isStrict) && counter.blockExpiresTime > 0 && currentTime < counter.blockExpiresTime) {
            requestCache.put(clientIp, counter); // Sobrescribimos la IP principal con el bloqueo
            String channel = isStrict ? "strict routes" : "static files";
            log.warn("IP {} globally blocked due to abuse of {}.", clientIp, channel);
        }

        if (counter.blockExpiresTime > 0 && currentTime < counter.blockExpiresTime) {
            long retryAfterSeconds = (counter.blockExpiresTime - currentTime) / 1000 + 1;
            httpResponse.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
            httpResponse.sendError(429, "Too Many Requests");
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        ServletContext context = filterConfig.getServletContext();

        // Obtenemos los valores del context (Límites)
        Integer limit = (Integer) context.getAttribute("rateLimitCount");
        Integer staticLimit = (Integer) context.getAttribute("rateLimitStaticCount");
        Integer strictLimit = (Integer) context.getAttribute("rateLimitStrictCount");
        Integer windowSec = (Integer) context.getAttribute("rateLimitWindowSeconds");
        Integer blockDurationSec = (Integer) context.getAttribute("rateLimitBlockDurationSeconds");

        // Asignamos valores (con defaults)
        this.requestLimit = (limit != null) ? limit : 200;
        this.staticRequestLimit = (staticLimit != null) ? staticLimit : 500;
        this.strictRequestLimit = (strictLimit != null) ? strictLimit : 15;
        this.timeWindowMs = (windowSec != null) ? (long) windowSec * 1000 : 60 * 1000;
        this.blockDurationMs = (blockDurationSec != null) ? (long) blockDurationSec * 1000 : 60 * 1000;

        // Cargar extensiones estáticas
        String extStr = (String) context.getAttribute("rateLimitStaticExtensions");
        this.staticExtensions = (extStr != null && !extStr.isEmpty()) ? extStr.split(",") : new String[0];

        // Cargar rutas estáticas
        String pathStr = (String) context.getAttribute("rateLimitStaticPaths");
        this.staticPaths = (pathStr != null && !pathStr.isEmpty()) ? pathStr.split(",") : new String[0];

        // Cargar rutas estrictas
        String strictStr = (String) context.getAttribute("rateLimitStrictPaths");
        this.strictPaths = (strictStr != null && !strictStr.isEmpty()) ? strictStr.split(",") : new String[0];

        long maxEntryAgeMs = this.timeWindowMs + this.blockDurationMs + 10000;

        this.requestCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(maxEntryAgeMs, TimeUnit.MILLISECONDS)
                .build();

        log.info("RateLimitingFilter initialized | Limits (Normal: {}, Static: {}, Strict: {}) requests per {}s | Block Duration: {}s | Max Cache Size: 50000",
                this.requestLimit,
                this.staticRequestLimit,
                this.strictRequestLimit,
                (this.timeWindowMs / 1000),
                (this.blockDurationMs / 1000));
    }

    @Override
    public void destroy() {
        if (this.requestCache != null) {
            this.requestCache.invalidateAll();
            this.requestCache.cleanUp();
        }
    }


}