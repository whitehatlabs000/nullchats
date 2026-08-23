package com.voidchats.admin;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-manage_users")
public class AdminManageUsersServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminManageUsersServlet.class);

    private DataSource ds;

    public static class UserData {
        public String username, profileImg, tipo, lastConnection;
        public boolean active;
    }

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for AdminManageUsersServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for AdminManageUsersServlet.");
        }
        log.info("AdminManageUsersServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("home");
            return;
        }

        // Verificar si el usuario es admin (con cursores sellados)
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT tipo FROM usuarios WHERE username=?")) {
            ps.setString(1, (String) session.getAttribute("user"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"admin".equalsIgnoreCase(rs.getString("tipo"))) {
                    log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access Admin Manage Users page.", session.getAttribute("user"));
                    resp.sendRedirect("home");
                    return;
                }
            }
        } catch (SQLException e) {
            log.error("Database error during admin check for user '{}'", session.getAttribute("user"), e);
            throw new ServletException("Admin check failed", e);
        }

        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            byte[] tokenBytes = new byte[32];
            new SecureRandom().nextBytes(tokenBytes);
            csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute("csrfToken", csrfToken);
        }
        req.setAttribute("csrfToken", csrfToken);

        String action = req.getParameter("action");
        if ("load_users".equals(action)) {
            handleAjaxUsers(req, resp);
        } else {
            handleInitialPage(req, resp);
        }
    }

    private String escapeSqlLike(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void handleInitialPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("q", req.getParameter("q"));
        req.setAttribute("order", req.getParameter("order"));
        // Pasa el nuevo parámetro de filtro a la página JSP
        req.setAttribute("filter", req.getParameter("filter"));
        req.getRequestDispatcher("/WEB-INF/jsp/admin-manage_users.jsp").forward(req, resp);
    }

    private void handleAjaxUsers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawQ = req.getParameter("q");

        // VALIDACIÓN TEMPRANA (Escudo DoS): Proteger CPU del .trim() con textos gigantes
        if (rawQ != null && rawQ.length() > 200) {
            log.warn("DoS SHIELD: Blocked extremely long search query (length: {}) in AdminManageUsersServlet", rawQ.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Search query is too long.");
            return;
        }

        String q = (rawQ != null) ? rawQ.trim() : "";

        String order = req.getParameter("order") != null ? req.getParameter("order") : "newest";
        String filter = req.getParameter("filter");

        String orderBy;
        switch (order) {
            case "oldest": orderBy = "u.id ASC"; break;
            case "username": orderBy = "u.username ASC"; break;
            case "newest":
            default: orderBy = "u.id DESC"; break;
        }

        int page = 1;
        final int LIMIT = 20;
        final int MAX_PAGE = 10000; // Límite de seguridad para evitar "Deep Paging" abusivo

        try {
            String pageParam = req.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
                // Lógica de protección (Cap)
                if (page < 1) page = 1;
                if (page > MAX_PAGE) page = MAX_PAGE;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid page parameter format: '{}'. Defaulting to page 1.", req.getParameter("page"));
            page = 1;
        }
        int offset = (page - 1) * LIMIT;

        List<UserData> users = new ArrayList<>();
        try (Connection conn = ds.getConnection()) {
            // Construcción dinámica y segura de la consulta
            StringBuilder sqlBuilder = new StringBuilder(
                    "SELECT u.username, u.profile_img, u.active, u.tipo, " +
                            "(SELECT MAX(event_timestamp) FROM access_logs al WHERE al.username = u.username) AS last_connection " +
                            "FROM usuarios u WHERE u.username LIKE ? ESCAPE '\\\\'"
            );

            // Añadir filtros si están presentes
            if ("admins".equals(filter)) {
                sqlBuilder.append(" AND u.tipo = 'admin'");
            } else if ("banned".equals(filter)) {
                sqlBuilder.append(" AND u.active = '0'");
            }

            sqlBuilder.append(" ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");

            String sql = sqlBuilder.toString();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String sanitizedQ = escapeSqlLike(q);
                ps.setString(1, "%" + sanitizedQ + "%");
                ps.setInt(2, LIMIT);
                ps.setInt(3, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UserData ud = new UserData();
                        ud.username = rs.getString("username");
                        ud.profileImg = rs.getString("profile_img");
                        if (ud.profileImg == null || ud.profileImg.trim().isEmpty()) {
                            ud.profileImg = "default_profile.jpg";
                        }
                        ud.active = rs.getBoolean("active");
                        ud.tipo = rs.getString("tipo");

                        java.sql.Timestamp lastConn = rs.getTimestamp("last_connection");
                        ud.lastConnection = (lastConn != null) ? lastConn.toInstant().toString() : null;

                        users.add(ud);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Database error while loading users for admin panel with query '{}'", q, e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading users.");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.print(new Gson().toJson(users));
        }
    }
}