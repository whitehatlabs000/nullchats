package com.voidchats;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.UUID;

@WebServlet("/about")
public class About extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // 1. Obtener la sesión (true para crearla si no existe, ya que necesitamos guardar el token)
        HttpSession session = req.getSession(true);

        // 2. GESTIÓN INTELIGENTE DE CSRF
        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);
        }

        // 3. Lo pasamos al request para que el JSP y el widget lo impriman
        req.setAttribute("csrfToken", csrfToken);

        req.getRequestDispatcher("/WEB-INF/jsp/about.jsp").forward(req, resp);

    }
}