package com.voidchats.admin;


import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-block-ip")
public class AdminBlockIPServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminBlockIPServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {


        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            if (session != null) log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access Admin Block IP page.", session.getAttribute("user"));
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String searchQuery = request.getParameter("q");

        if (searchQuery != null && searchQuery.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long search query (length: {}) in AdminBlockIPServlet.", searchQuery.length());
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Search query is too long.");
            return;
        }

        List<String> blockedIPs = new java.util.ArrayList<>(IPBlockManager.getBlockedIPs());

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String finalQuery = searchQuery.trim();
            blockedIPs = blockedIPs.stream()
                    .filter(ip -> ip.contains(finalQuery))
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            blockedIPs.sort(String::compareTo);
        }

        request.setAttribute("blockedIPs", blockedIPs);
        request.setAttribute("searchQuery", searchQuery);

        String csrfToken = UUID.randomUUID().toString();
        session.setAttribute("csrfToken", csrfToken);
        request.setAttribute("csrfToken", csrfToken);

        if(session.getAttribute("message") != null){
            request.setAttribute("message", session.getAttribute("message"));
            session.removeAttribute("message");
        }

        request.getRequestDispatcher("/WEB-INF/jsp/admin-block-ip.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            if (session != null) log.warn("SECURITY WARNING: Non-admin user '{}' attempted to POST to Admin Block IP.", session.getAttribute("user"));
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }


        String action = request.getParameter("action");
        String ip = request.getParameter("ip");

        if (ip != null && ip.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long IP parameter (length: {}) in AdminBlockIPServlet.", ip.length());
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "IP parameter is too long.");
            return;
        }

        String formToken = request.getParameter("csrfToken");
        String sessionToken = (String) session.getAttribute("csrfToken");


        if (sessionToken == null || formToken == null || !sessionToken.equals(formToken)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token.");
            return;
        }

        session.removeAttribute("csrfToken");

        try {
            String adminUser = (String) session.getAttribute("user");
            if ("add".equals(action)) {
                IPBlockManager.addIP(ip);
                log.info("Admin '{}' successfully added IP '{}' to the blocklist.", adminUser, ip);
                session.setAttribute("message", "IP " + ip + " added to the blocklist.");
            } else if ("remove".equals(action)) {
                IPBlockManager.removeIP(ip);
                log.info("Admin '{}' successfully removed IP '{}' from the blocklist.", adminUser, ip);
                session.setAttribute("message", "IP " + ip + " removed from the blocklist.");
            }
        } catch (IOException e) {
            log.error("I/O error while modifying the IP blocklist (Action: {}, IP: {})", action, ip, e);
            session.setAttribute("message", "Error modifying the IP list. Please try again.");
        }

        response.sendRedirect("admin-block-ip");
    }
}