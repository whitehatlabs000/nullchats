package com.voidchats.admin;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;


import com.voidchats.IPUtils;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.voidchats.AuditLogger;

@WebServlet("/admin_change_password")
public class AdminChangePasswordServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminChangePasswordServlet.class);

    private DataSource ds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for AdminChangePasswordServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for AdminChangePasswordServlet.");
        }
        log.info("AdminChangePasswordServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("home");
            return;
        }

        String adminUser = (String) session.getAttribute("user");
        String targetUser = req.getParameter("u");


        if (targetUser != null && targetUser.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long target user parameter (length: {}) in GET request.", targetUser.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Target user is too long.");
            return;
        }

        if (targetUser == null || targetUser.trim().isEmpty()) {
            resp.sendRedirect("home");
            return;
        }

        try (Connection cn = ds.getConnection()) {
            boolean isAdmin = false;
            try (PreparedStatement ps = cn.prepareStatement("SELECT tipo FROM usuarios WHERE username=?")) {
                ps.setString(1, adminUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "admin".equalsIgnoreCase(rs.getString("tipo"))) {
                        isAdmin = true;
                    }
                }
            }
            if (!isAdmin) {
                log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access password change page for target '{}'.", adminUser, targetUser);
                resp.sendRedirect("home");
                return;
            }
        } catch (Exception e) {
            log.error("Database error verifying admin privileges for user '{}'", adminUser, e);
            resp.sendRedirect("home");
            return;
        }

        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            byte[] tokenBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(tokenBytes);
            csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute("csrfToken", csrfToken);
        }

        req.setAttribute("targetUser", targetUser);
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("error", session.getAttribute("error"));
        req.setAttribute("ok", session.getAttribute("ok"));
        session.removeAttribute("error");
        session.removeAttribute("ok");

        req.getRequestDispatcher("/WEB-INF/jsp/admin_change_password.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login");
            return;
        }

        String adminUser = (String) session.getAttribute("user");
        String targetUser = req.getParameter("target_user");
        String newPass = req.getParameter("new_password");
        String confirmPass = req.getParameter("confirm_password");
        String formToken = req.getParameter("csrfToken");
        String sessionToken = (String) session.getAttribute("csrfToken");

        if (targetUser != null && targetUser.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long target user parameter (length: {}) in POST request.", targetUser.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Target user is too long.");
            return;
        }

        if (targetUser == null || targetUser.trim().isEmpty()) {
            resp.sendRedirect("home");
            return;
        }

        if (sessionToken == null || formToken == null || !sessionToken.equals(formToken)) {
            session.setAttribute("error", "Invalid CSRF token.");
            resp.sendRedirect("admin_change_password?u=" + targetUser);
            return;
        }

        if (newPass == null || confirmPass == null || !newPass.equals(confirmPass)) {
            session.setAttribute("error", "Passwords do not match.");
            resp.sendRedirect("admin_change_password?u=" + targetUser);
            return;
        }

        if (newPass.length() < 6 || newPass.length() > 100) {
            session.setAttribute("error", "Password must be between 6 and 100 characters.");
            resp.sendRedirect("admin_change_password?u=" + targetUser);
            return;
        }

        if (!isValidPassword(newPass)) {
            session.setAttribute("error", "Password contains invalid characters.");
            resp.sendRedirect("admin_change_password?u=" + targetUser);
            return;
        }

        String newHash;
        try {
            newHash = BCrypt.hashpw(newPass, BCrypt.gensalt());
        } catch (Exception e) {
            throw new ServletException("Error hashing password", e);
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
                log.warn("SECURITY WARNING: Non-admin user '{}' attempted to change password for target '{}'.", adminUser, targetUser);
                resp.sendRedirect("home");
                return;
            }

            conn.setAutoCommit(false);

            boolean updateSuccess = false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET password=? WHERE username=?")) {
                ps.setString(1, newHash);
                ps.setString(2, targetUser);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    updateSuccess = true;
                }
            }

            if (updateSuccess) {

                conn.commit();

                String adminIp = IPUtils.getClientIp(req);
                String eventType = "PASSWORD_CHANGE";
                String details = "Target user: " + targetUser;

                AuditLogger.getInstance().logAsync(adminIp, adminUser, eventType, details);
                session.setAttribute("ok", "Password updated successfully for user: " + targetUser);
                byte[] tokenBytes = new byte[32];
                new java.security.SecureRandom().nextBytes(tokenBytes);
                String newToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
                session.setAttribute("csrfToken", newToken);

            } else {
                conn.rollback();
                session.setAttribute("error", "User not found or update failed.");
            }

        } catch (Exception e) {
            log.error("Database error while changing password for user '{}' by admin '{}'. Initiating rollback.", targetUser, adminUser, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("CRITICAL ERROR: Failed to rollback transaction.", ex);
                }
            }
            session.setAttribute("error", "An internal error occurred while changing the password.");
        } finally {
            if (conn != null) {
                try {

                    if (!conn.getAutoCommit()) {
                        conn.setAutoCommit(true);
                    }
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

        resp.sendRedirect("admin_change_password?u=" + targetUser);
    }

    private boolean isValidPassword(String password) {
        String regex = "^[a-zA-Z0-9@#%&!$^*._-]+$";
        return Pattern.matches(regex, password);
    }
}