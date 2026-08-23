package com.voidchats.filters;

import com.voidchats.IPUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.ServletContext;
import com.voidchats.AuditLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IPLoggingFilter.class);

    private Set<String> ignoreExtensions = new HashSet<>();
    private Set<String> ignorePaths = new HashSet<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            ServletContext context = filterConfig.getServletContext();

            String baseUrl = (String) context.getAttribute("appBaseUrl");
            if (baseUrl == null) {
                baseUrl = "";
            }
            // Corregir si baseUrl es "/" para evitar rutas como "//home"
            if (baseUrl.equals("/")) {
                baseUrl = "";
            }

            final String finalBaseUrl = baseUrl;

            String extensionsStr = (String) context.getAttribute("logfilter.ignore.extensions");
            if (extensionsStr != null && !extensionsStr.isEmpty()) {
                this.ignoreExtensions = Arrays.stream(extensionsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
            }

            String pathsStr = (String) context.getAttribute("logfilter.ignore.paths");
            if (pathsStr != null && !pathsStr.isEmpty()) {

                this.ignorePaths = Arrays.stream(pathsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(path -> finalBaseUrl + path)
                        .collect(Collectors.toSet());
            }

            log.info("IPLoggingFilter initialized. Ignoring {} extensions and {} paths.", this.ignoreExtensions.size(), this.ignorePaths.size());

        } catch (Exception e) {
            log.error("Critical failure initializing IPLoggingFilter", e);
            throw new ServletException("Could not initialize IPLoggingFilter", e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        if (uri != null && uri.length() > 2048) {
            log.warn("DoS SHIELD: IPLoggingFilter intercepted massive URI (length: {})", uri.length());
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        String uriLower = uri.toLowerCase();
        String rootPath = httpRequest.getContextPath() + "/";

        boolean shouldLog = true;

        for (String path : ignorePaths) {

            if (path.equals(rootPath)) {
                if (uri.equals(rootPath)) {
                    shouldLog = false;
                    break;
                }
            }

            else {
                if (uri.startsWith(path)) {
                    shouldLog = false;
                    break;
                }
            }
        }

        if (shouldLog) {
            for (String ext : ignoreExtensions) {
                if (uriLower.endsWith(ext)) {
                    shouldLog = false;
                    break;
                }
            }
        }

        // Si NO fue ignorada (shouldLog = true), registramos la visita
        if (shouldLog) {
            String ipAddress = IPUtils.getClientIp(httpRequest);
            HttpSession session = httpRequest.getSession(false);
            String username = (session != null) ? (String) session.getAttribute("user") : null;

            String queryString = httpRequest.getQueryString();
            String fullPath = uri;

            if (queryString != null && !queryString.isEmpty()) {
                if (queryString.length() > 500) {
                    queryString = queryString.substring(0, 500) + "...";
                }
                fullPath += "?" + queryString;
            }

            String detailsText = "Visited: " + fullPath;
            if (detailsText.length() > 250) {
                detailsText = detailsText.substring(0, 247) + "...";
            }

            AuditLogger.getInstance().logAsync(ipAddress, username, "PAGE_VIEW", detailsText);
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}