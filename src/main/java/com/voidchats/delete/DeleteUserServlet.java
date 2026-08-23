package com.voidchats.delete;

import com.google.gson.JsonObject;
import com.voidchats.IPUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.voidchats.media.MessagingMediaServlet;
import com.voidchats.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/delete_user")
public class DeleteUserServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DeleteUserServlet.class);

    private DataSource ds;
    private Path profileDir;
    private Path messagingDir;



    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");
        String profilePathStr = (String) context.getAttribute("profileDir");
        String messagingPathStr = (String) context.getAttribute("messagingDir");

        if (this.ds == null || profilePathStr == null || messagingPathStr == null) {
            log.error("Critical failure: Missing essential context resources for DeleteUserServlet.");
            throw new ServletException("Critical failure: Missing essential resources ('dbDataSource', 'profileDir', 'messagingDir') in context for DeleteUserServlet.");
        }

        this.profileDir = Paths.get(profilePathStr);
        this.messagingDir = Paths.get(messagingPathStr);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            String clientIp = IPUtils.getClientIp(req);
            log.warn("SECURITY ALERT: Unauthenticated attempt to delete user from IP: {}", clientIp);
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "No session.");
            return;
        }

        String adminUser = (String) session.getAttribute("user");
        String targetUser = req.getParameter("username");

        String csrfHeader = req.getHeader("X-CSRF-Token");
        String csrfSession = (String) session.getAttribute("csrfToken");

        if (targetUser == null || targetUser.trim().isEmpty() || csrfHeader == null || csrfSession == null || !csrfHeader.equals(csrfSession)) {
            String clientIp = IPUtils.getClientIp(req);
            log.warn("SECURITY ALERT: CSRF mismatch or missing target user on DeleteUserServlet from IP: {}. Potential CSRF attack.", clientIp);
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request or CSRF token.");
            return;
        }

        if (adminUser.equals(targetUser)) {
            String clientIp = IPUtils.getClientIp(req);
            log.warn("SECURITY ALERT: Admin '{}' attempted to delete their own account from IP: {}", adminUser, clientIp);
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Admin cannot delete itself.");
            return;
        }

        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);


            int targetUserId = -1;
            boolean isAdmin = false;
            String profileImg = null, coverImg = null;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT u_target.id, u_target.profile_img, u_target.cover_img, u_admin.tipo " +
                            "FROM usuarios u_target, usuarios u_admin " +
                            "WHERE u_target.username = ? AND u_admin.username = ?")) {

                ps.setString(1, targetUser);
                ps.setString(2, adminUser);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        targetUserId = rs.getInt("id");
                        profileImg = rs.getString("profile_img");
                        coverImg = rs.getString("cover_img");
                        isAdmin = "admin".equalsIgnoreCase(rs.getString("tipo"));
                    } else {

                        conn.rollback();
                        sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "User to delete not found.");
                        return;
                    }
                }
            }

            if (!isAdmin) {
                conn.rollback();
                sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Action not allowed.");
                return;
            }

            Set<Path> filesToDelete = new HashSet<>();

            addFileToList(filesToDelete, profileDir, profileImg);
            addFileToList(filesToDelete, profileDir, coverImg);

            try (PreparedStatement ps = conn.prepareStatement("SELECT file_path, preview_file FROM messages WHERE sender_id = ? OR receiver_id = ?")) {
                ps.setInt(1, targetUserId);
                ps.setInt(2, targetUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        addFileToList(filesToDelete, messagingDir, rs.getString("file_path"));
                        addFileToList(filesToDelete, messagingDir, rs.getString("preview_file"));
                    }
                }
            }

            // Como el AuditLogger usa un hilo separado, lo lanzamos aquí para que registre el intento.
            // Si el commit falla más abajo, quedará como evidencia de que un admin intentó borrar la cuenta.
            String adminIp = IPUtils.getClientIp(req);
            String eventType = "ACCOUNT_DELETED";
            String details = "Deleted user: " + targetUser;
            AuditLogger.getInstance().logAsync(adminIp, adminUser, eventType, details);

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
                ps.setInt(1, targetUserId);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("User deletion failed, no rows affected.");
                }
            }

            conn.commit();

            MessagingMediaServlet.invalidateAllUserMediaAccess(targetUserId);

            for (Path filePath : filesToDelete) {
                String fileName = filePath.getFileName().toString();

                // Invalidación individual en RAM para evitar Errores 500.
                MessagingMediaServlet.invalidateMediaAccess(targetUserId, fileName);

                // Borrado físico del disco
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete file: {}. Error: {}", filePath.toAbsolutePath(), e.getMessage());
                }
            }

            log.info("SECURITY EVENT: Admin '{}' successfully deleted user '{}' and all associated data.", adminUser, targetUser);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "User " + targetUser + " deleted successfully.");
            resp.getWriter().write(jsonResponse.toString());

        } catch (SQLException e) {
            log.error("Database error during deletion of user '{}' by admin '{}'", targetUser, adminUser, e);

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Error rolling back transaction", ex);
                }
            }

            throw new ServletException("Database error during user deletion.", e);
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

    // Método de utilidad para añadir archivos a la lista de borrado de forma segura
    private void addFileToList(Set<Path> set, Path directory, String fileName) {
        if (fileName == null || fileName.trim().isEmpty() ||
                fileName.equals("default_profile.jpg") || fileName.equals("default_cover.jpg")) {
            return;
        }
        set.add(directory.resolve(fileName));

    }

    // Método de utilidad para enviar errores en formato JSON
    private void sendJsonError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("success", false);
        errorJson.addProperty("error", message);
        resp.getWriter().write(errorJson.toString());
    }

}