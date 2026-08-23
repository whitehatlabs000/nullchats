package com.voidchats.filters;

import com.voidchats.admin.MaintenanceManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MaintenanceFilter implements Filter {

    // Lista de rutas que siempre deben funcionar (assets, login, el propio admin)
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
            "/css/", "/js/", "/scripts/", "/webfonts/", "/assets/", // Estilos y scripts
            "/login", "/api/csrf-token", // Login y utilidades
            "/admin", // Permitimos rutas que empiecen con /admin para que el admin no se bloquee a sí mismo
            "/maintenance.html" // La página de aviso
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestURI = httpRequest.getRequestURI();
        if (requestURI != null && requestURI.length() > 2048) {
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        String path = requestURI.substring(httpRequest.getContextPath().length());

        if (!MaintenanceManager.isMaintenanceModeEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // Si está ENCENDIDO, verificamos excepciones:

        // A. Recursos estáticos y rutas permitidas
        boolean isAllowedPath = ALLOWED_PATHS.stream().anyMatch(path::startsWith);
        if (isAllowedPath) {
            chain.doFilter(request, response);
            return;
        }

        // B. Verificar si es ADMINISTRADOR
        HttpSession session = httpRequest.getSession(false);
        if (session != null && "admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            // Es admin, lo dejamos pasar como si nada ocurriera
            chain.doFilter(request, response);
            return;
        }

        if (!"/maintenance.html".equals(path)) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/maintenance.html");
        } else {
            chain.doFilter(request, response);
        }
    }
}