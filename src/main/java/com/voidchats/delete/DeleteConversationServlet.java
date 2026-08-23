package com.voidchats.delete;

import com.google.gson.Gson;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.voidchats.media.MessagingMediaServlet;
import com.voidchats.IPUtils;
import com.voidchats.AuditLogger;

@WebServlet("/delete-conversation")
public class DeleteConversationServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DeleteConversationServlet.class);

    private DataSource ds;
    private Path messagingDir;
    private final Gson gson = new Gson();



    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");
        String messagingPathStr = (String) context.getAttribute("messagingDir");

        if (this.ds == null || messagingPathStr == null) {
            log.error("Critical failure: Missing essential context resources for DeleteConversationServlet.");
            throw new ServletException("Critical failure: Missing essential resources ('dbDataSource', 'messagingDir') in context for DeleteConversationServlet.");
        }

        this.messagingDir = Paths.get(messagingPathStr);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Map<String, Object> jsonResponse = new HashMap<>();

        // 1. Verificación de seguridad
        Integer currentUserId = (session != null) ? (Integer) session.getAttribute("userId") : null;
        String sessionCsrfToken = (session != null) ? (String) session.getAttribute("csrfToken") : null;
        String requestCsrfToken = request.getParameter("csrfToken");

        if (currentUserId == null || sessionCsrfToken == null || !sessionCsrfToken.equals(requestCsrfToken)) {
            String clientIp = IPUtils.getClientIp(request);
            log.warn("SECURITY ALERT: Unauthenticated attempt or CSRF mismatch on DeleteConversationServlet from IP: {}", clientIp);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            jsonResponse.put("success", false);
            jsonResponse.put("error", "Access denied.");
            response.getWriter().write(gson.toJson(jsonResponse));
            return;
        }

        // 2. Obtener el ID del partner
        int partnerId;
        try {
            partnerId = Integer.parseInt(request.getParameter("partnerId"));
        } catch (NumberFormatException e) {
            String clientIp = IPUtils.getClientIp(request);
            log.warn("SECURITY ALERT: Invalid partner ID format submitted to DeleteConversationServlet from IP: {}", clientIp);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("success", false);
            jsonResponse.put("error", "Invalid partner ID.");
            response.getWriter().write(gson.toJson(jsonResponse));
            return;
        }

        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false); // Iniciar transacción



            // 3. Obtener la lista de archivos multimedia ANTES de borrar los mensajes
            List<Path> filesToDelete = new ArrayList<>();
            String getFilesSql = "SELECT file_path, preview_file FROM messages WHERE ((sender_id = ? AND receiver_id = ?) OR (receiver_id = ? AND sender_id = ?))";

            try (PreparedStatement psFiles = conn.prepareStatement(getFilesSql)) {
                psFiles.setInt(1, currentUserId);
                psFiles.setInt(2, partnerId);
                psFiles.setInt(3, currentUserId);
                psFiles.setInt(4, partnerId);
                try (ResultSet rs = psFiles.executeQuery()) {
                    while (rs.next()) {
                        // Obtenemos ambos archivos
                        String mediaFile = rs.getString("file_path");
                        String previewFile = rs.getString("preview_file");

                        // Añadimos el archivo multimedia (usando this.messagingDir)
                        if (mediaFile != null && !mediaFile.isEmpty()) {
                            filesToDelete.add(this.messagingDir.resolve(mediaFile));
                        }
                        // Añadimos el preview (usando this.messagingDir)
                        if (previewFile != null && !previewFile.isEmpty()) {
                            filesToDelete.add(this.messagingDir.resolve(previewFile));
                        }
                    }
                }
            }

            // 4. Borrar los mensajes de la base de datos
            String deleteSql = "DELETE FROM messages WHERE (sender_id = ? AND receiver_id = ?) OR (receiver_id = ? AND sender_id = ?)";
            try (PreparedStatement psDelete = conn.prepareStatement(deleteSql)) {
                psDelete.setInt(1, currentUserId);
                psDelete.setInt(2, partnerId);
                psDelete.setInt(3, currentUserId);
                psDelete.setInt(4, partnerId);
                psDelete.executeUpdate();
            }

            // 5. Si todo fue bien en la BD, confirmar la transacción
            conn.commit();


            String clientIp = IPUtils.getClientIp(request);
            String sessionUser = (session != null) ? (String) session.getAttribute("user") : "Unknown";
            String details = "Deleted conversation with partner ID: " + partnerId;
            AuditLogger.getInstance().logAsync(clientIp, sessionUser, "CONVERSATION_DELETED", details);


            // 6. Invalidar la caché de seguridad de medios para AMBOS usuarios
            MessagingMediaServlet.invalidateAllUserMediaAccess(currentUserId);
            MessagingMediaServlet.invalidateAllUserMediaAccess(partnerId);

            // 7. SOLO DESPUÉS del commit, borramos los archivos del disco físico
            for (Path filePath : filesToDelete) {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete file (post-commit): {}", filePath, e);
                }
            }

            log.info("SECURITY EVENT: User ID {} successfully deleted conversation with partner ID {}. Media cache cleared.", currentUserId, partnerId);

            jsonResponse.put("success", true);
            response.getWriter().write(gson.toJson(jsonResponse));

        } catch (SQLException e) {
            log.error("SQL error during conversation deletion between user {} and {}. Rolling back.", currentUserId, partnerId, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Critical error attempting rollback.", ex);
                }
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false);
            jsonResponse.put("error", "Error en el servidor al eliminar la conversación.");
            response.getWriter().write(gson.toJson(jsonResponse));
        } finally {
            if (conn != null) {

                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    log.error("Error restoring autoCommit", e);
                }
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Error closing connection.", e);
                }
            }
        }
    }
}