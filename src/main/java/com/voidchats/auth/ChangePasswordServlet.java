package com.voidchats.auth;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/change_password")
public class ChangePasswordServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordServlet.class);

    private DataSource ds;
    private int maxPasswordChangesPerDay;

    @Override
    public void init() throws ServletException {
        try {
            ServletContext context = getServletContext();
            this.ds = (DataSource) context.getAttribute("dbDataSource");
            Integer maxChanges = (Integer) context.getAttribute("maxPasswordChangesPerDay");

            if (ds == null) {
                throw new ServletException("DataSource (dbDataSource) not found in context.");
            }

            if (maxChanges == null) {
                log.warn("Warning: 'maxPasswordChangesPerDay' not found in context. Using default of 5.");
                this.maxPasswordChangesPerDay = 5;
            } else {
                this.maxPasswordChangesPerDay = maxChanges;
            }

            log.info("ChangePasswordServlet initialized | Max password changes per day: {}", this.maxPasswordChangesPerDay);

        } catch (Exception e) {
            log.error("Catastrophic error initializing ChangePasswordServlet", e);
            throw new ServletException("Could not initialize ChangePasswordServlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login");
            return;
        }

        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);


        String msgOk = (String) session.getAttribute("ok");
        String msgError = (String) session.getAttribute("error");
        if (msgOk != null) {
            req.setAttribute("ok", msgOk);
            session.removeAttribute("ok");
        }
        if (msgError != null) {
            req.setAttribute("error", msgError);
            session.removeAttribute("error");
        }

        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            byte[] tokenBytes = new byte[32];
            new SecureRandom().nextBytes(tokenBytes);
            csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute("csrfToken", csrfToken);
        }

        req.setAttribute("csrfToken", csrfToken);
        req.getRequestDispatcher("/WEB-INF/jsp/change_password.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("login");
            return;
        }

        String username = (String) session.getAttribute("user");
        String oldPass = req.getParameter("old_password");
        String newPass = req.getParameter("new_password");
        String confirmPass = req.getParameter("confirm_password");

        String formToken = req.getParameter("csrfToken");
        String sessionToken = (String) session.getAttribute("csrfToken");

        if (formToken == null || sessionToken == null || !formToken.equals(sessionToken)) {
            session.setAttribute("error", "Invalid CSRF token.");
            resp.sendRedirect("change_password");
            return;
        }

        if (oldPass == null || newPass == null || confirmPass == null) {
            session.setAttribute("error", "You must complete all fields.");
            resp.sendRedirect("change_password");
            return;
        }

        if (oldPass.length() > 100) {
            session.setAttribute("error", "Invalid old password length.");
            resp.sendRedirect("change_password");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            session.setAttribute("error", "The new passwords do not match.");
            resp.sendRedirect("change_password");
            return;
        }

        if (newPass.length() < 6 || newPass.length() > 100) {
            session.setAttribute("error", "The new password must be between 6 and 100 characters long.");
            resp.sendRedirect("change_password");
            return;
        }

        if (!isValidPassword(newPass)) {
            session.setAttribute("error", "The password contains illegal characters.");
            resp.sendRedirect("change_password");
            return;
        }

        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);

            String storedHash = null;
            int sessionUserId = -1;

            PreparedStatement psUser = conn.prepareStatement(
                    "SELECT id, password FROM usuarios WHERE username=?");
            psUser.setString(1, username);
            ResultSet rsUser = psUser.executeQuery();

            if (!rsUser.next()) {
                conn.rollback();
                session.setAttribute("error", "User not found.");
                resp.sendRedirect("change_password");
                return;
            }
            sessionUserId = rsUser.getInt("id");
            storedHash = rsUser.getString("password");
            rsUser.close();
            psUser.close();

            int currentCount = 0;
            PreparedStatement psLimit = conn.prepareStatement(
                    "SELECT COUNT(id) FROM password_change_logs WHERE user_id = ? AND created_at >= CURDATE()");
            psLimit.setInt(1, sessionUserId);
            ResultSet rsLimit = psLimit.executeQuery();
            if (rsLimit.next()) {
                currentCount = rsLimit.getInt(1);
            }
            rsLimit.close();
            psLimit.close();

            if (currentCount >= this.maxPasswordChangesPerDay) {
                conn.rollback();
                log.warn("RATE LIMIT: User '{}' reached daily password change limit ({}).", username, currentCount);
                session.setAttribute("error", "You have reached the daily password change limit.");
                resp.sendRedirect("change_password");
                return;
            }

            boolean match;
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                match = BCrypt.checkpw(oldPass, storedHash);
            } else {
                match = storedHash.equals(sha256(oldPass));
            }

            if (!match) {
                conn.rollback();
                session.setAttribute("error", "Current password is incorrect.");
                resp.sendRedirect("change_password");
                return;
            }

            String newHash = BCrypt.hashpw(newPass, BCrypt.gensalt());
            PreparedStatement psUpdate = conn.prepareStatement(
                    "UPDATE usuarios SET password=? WHERE id=?");
            psUpdate.setString(1, newHash);
            psUpdate.setInt(2, sessionUserId);
            psUpdate.executeUpdate();
            psUpdate.close();

            PreparedStatement psLog = conn.prepareStatement(
                    "INSERT INTO password_change_logs (user_id) VALUES (?)");
            psLog.setInt(1, sessionUserId);
            psLog.executeUpdate();
            psLog.close();

            conn.commit();
            log.info("SECURITY EVENT: User '{}' successfully changed their password.", username);

            byte[] tokenBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(tokenBytes);
            String newToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute("csrfToken", newToken);

            session.setAttribute("ok", "Password updated successfully.");
            resp.sendRedirect("change_password");

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Error executing rollback", ex); }
            }
            log.error("Database or processing error in ChangePasswordServlet (doPost) for user '{}'", username, e);
            throw new ServletException(e);
        } finally {

            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("Error closing connection", e);
                }
            }
        }
    }

    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private boolean isValidPassword(String password) {
        String regex = "^[a-zA-Z0-9@#%&!$^*._-]+$";
        return Pattern.matches(regex, password);
    }
}
