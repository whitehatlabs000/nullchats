package com.voidchats.filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.voidchats.DynamicResponseUtil;
import com.voidchats.IPUtils;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SecurityHeadersFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    private static final java.util.regex.Pattern STATIC_FILES_PATTERN = java.util.regex.Pattern.compile(".*\\.(css|js|jpg|jpeg|png|gif|ico|woff|woff2|ttf|eot|svg|mp4|webm|ogg|webmanifest|json)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final String CSP_HEADER_VALUE = String.join("; ",

            "default-src 'self'",

            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.google.com https://www.gstatic.com https://static.cloudflareinsights.com",

            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",

            "img-src 'self' data: blob:",

            "font-src 'self' https://fonts.gstatic.com",

            "frame-src 'self' https://www.google.com",

            "media-src 'self' blob:",

            "connect-src 'self' https://www.google.com https://cloudflareinsights.com"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String uri = httpRequest.getRequestURI();

            if (uri != null && uri.length() > 2048) {
                log.warn("DoS SHIELD: SecurityHeadersFilter intercepted massive URI (length: {}) from IP: {}", uri.length(), IPUtils.getClientIp(httpRequest));                httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
                return;
            }

            if (!isCacheableResource(httpRequest)) {
                DynamicResponseUtil.disableCaching(httpResponse);
            }

            httpResponse.setHeader("Content-Security-Policy", CSP_HEADER_VALUE);

            httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");

            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        chain.doFilter(request, response);
    }


    private boolean isCacheableResource(HttpServletRequest httpRequest) {
        String uri = httpRequest.getRequestURI();
        if (uri == null) return false;

        String contextPath = httpRequest.getContextPath();

        // Estos servlets usan MediaCacheUtil para gestionar su propio caché
        if (uri.startsWith(contextPath + "/media") ||
                uri.startsWith(contextPath + "/profile-img")) {
            return true;
        }

        // Evaluación con Expresión Regular pre-compilada para archivos estáticos puros
        if (STATIC_FILES_PATTERN.matcher(uri).matches()) {
            return true;
        }

        // Si no es ni multimedia ni archivo estático, es una página dinámica (JSP, HTML, JSON)
        return false;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("SecurityHeadersFilter initialized successfully.");
    }

    @Override
    public void destroy() {
        // destrucción del filtro
    }
}