package com.voidchats.messaging;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Servlet dedicado EXCLUSIVAMENTE a despachar la vista HTML (Full Page) de mensajería.
 * Las peticiones AJAX/Polling deben ir a /api/messaging.
 */
@WebServlet("/messaging")
public class MessagingViewServlet extends HttpServlet {

    private static final SecureRandom sr = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);


        if (session == null || session.getAttribute("userId") == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }


        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = generateCSRFToken();
            session.setAttribute("csrfToken", csrfToken);
        }


        req.setAttribute("csrfToken", csrfToken);


        req.getRequestDispatcher("/WEB-INF/jsp/messaging.jsp").forward(req, resp);
    }

    private String generateCSRFToken() {
        byte[] bytes = new byte[16];
        sr.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}