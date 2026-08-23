package com.voidchats.admin;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.voidchats.IPUtils;


import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.voidchats.AuditLogger;

@WebServlet("/toggle_account")
public class ToggleAccountServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ToggleAccountServlet.class);

    private DataSource ds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for ToggleAccountServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for ToggleAccountServlet.");
        }
        log.info("ToggleAccountServlet initialized successfully.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "No session.");
            return;
        }

        String adminUser = (String) session.getAttribute("user");
        String targetUser = req.getParameter("username");
        String action = req.getParameter("action");

        if (targetUser != null && targetUser.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long username parameter (length: {}) by admin {}", targetUser.length(), adminUser);
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Username is too long.");
            return;
        }

        String csrfHeader = req.getHeader("X-CSRF-Token");
        String csrfSession = (String) session.getAttribute("csrfToken");

        if (targetUser == null || action == null || csrfHeader == null || !csrfHeader.equals(csrfSession)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid request or CSRF token.");
            return;
        }

        if (adminUser.equals(targetUser)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Admin cannot toggle its own account.");
            return;
        }

        Connection conn = null;
        try {
            conn = ds.getConnection();

            boolean isAdmin = false;
            try (PreparedStatement ps = conn.prepareStatement("SELECT tipo FROM usuarios WHERE username=?")) {
                ps.setString(1, adminUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "admin".equalsIgnoreCase(rs.getString("tipo"))) {
                        isAdmin = true;
                    }
                }
            }

            if (!isAdmin) {
                log.warn("SECURITY WARNING: User '{}' attempted to toggle account '{}' without admin privileges.", adminUser, targetUser);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Action not allowed.");
                return;
            }

            conn.setAutoCommit(false);

            int newState = "disable".equalsIgnoreCase(action) ? 0 : 1;

            boolean updateSuccess = false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET active=? WHERE username=?")) {
                ps.setInt(1, newState);
                ps.setString(2, targetUser);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    updateSuccess = true;
                }
            }

            if (updateSuccess) {
                conn.commit();

                String adminIp = IPUtils.getClientIp(req);
                String eventType = (newState == 1) ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED";
                String details = "Target user: " + targetUser;

                AuditLogger.getInstance().logAsync(adminIp, adminUser, eventType, details);

                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("active", newState == 1);
                resp.getWriter().write(jsonResponse.toString());

            } else {

                conn.rollback();
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
            }

        } catch (SQLException e) {
            log.error("Database error while toggling account '{}' by admin '{}'. Initiating rollback.", targetUser, adminUser, e);

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("CRITICAL ERROR: Failed to rollback transaction.", ex);
                }
            }

            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error while toggling account status.");
        } finally {

            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    log.error("Failed to restore autoCommit.", e);
                }
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close database connection.", e);
                }
            }
        }
    }
}