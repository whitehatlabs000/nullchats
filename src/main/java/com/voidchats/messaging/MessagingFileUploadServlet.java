package com.voidchats.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.voidchats.video.MessageVideoProcessingTask;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/upload-media")
@MultipartConfig(
        maxFileSize = 50 * 1024 * 1024, // 50 MB (Límite Duro)
        fileSizeThreshold = 1024 * 1024, // 1 MB
        maxRequestSize = 55 * 1024 * 1024 // 55 MB (Límite Duro Total)
)
public class MessagingFileUploadServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(MessagingFileUploadServlet.class);

    // Recursos compartidos (obtenidos del Listener)
    private DataSource ds;
    private String messagingDir;
    private String messagingPendingDir;
    private String ffmpegPath;
    private ExecutorService videoExecutor;
    private ExecutorService gobblerExecutor;
    private long maxFileSizeBytes;

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    // Límite para el envío de mensajes multimedia (cargado desde init)
    private int maxMultimediaMessagesPerDay;

    private static final java.util.List<String> ALLOWED_IMAGE_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final java.util.List<String> ALLOWED_VIDEO_TYPES = Arrays.asList("video/mp4", "video/webm", "video/ogg");

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");
        this.messagingDir = (String) context.getAttribute("messagingDir");
        this.messagingPendingDir = (String) context.getAttribute("messagingPendingDir");
        this.ffmpegPath = (String) context.getAttribute("ffmpegPath");
        this.videoExecutor = (ExecutorService) context.getAttribute("videoExecutor");
        this.gobblerExecutor = (ExecutorService) context.getAttribute("gobblerExecutor");
        Long maxFileSize = (Long) context.getAttribute("maxFileSizeBytes");
        Integer maxMultimediaMessages = (Integer) context.getAttribute("maxMultimediaMessagesPerDay");

        if (this.ds == null || this.messagingDir == null || this.messagingPendingDir == null ||
                this.ffmpegPath == null || this.videoExecutor == null || this.gobblerExecutor == null ||
                maxFileSize == null || maxMultimediaMessages == null) {
            log.error("Critical failure: Missing essential context resources (dbDataSource, directories, ffmpegPath, pools, or limits) for MessagingFileUploadServlet.");
            throw new ServletException("Critical failure: Missing essential resources in context for MessagingFileUploadServlet.");
        }

        this.maxFileSizeBytes = maxFileSize;
        this.maxMultimediaMessagesPerDay = maxMultimediaMessages;

        log.info("MessagingFileUploadServlet initialized. Max File Size: {} bytes, Max multimedia/day: {}", this.maxFileSizeBytes, this.maxMultimediaMessagesPerDay);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access denied.");
            return;
        }

        String tokenFromRequest = req.getParameter("csrfToken");
        String tokenFromSession = (String) session.getAttribute("csrfToken");
        if (tokenFromRequest == null || !tokenFromRequest.equals(tokenFromSession)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token.");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        Map<String, Object> response = new HashMap<>();


        String uniqueFilename = null;
        String previewFilename = null;
        String originalFilename = null;
        String messageType = null;
        String status = "COMPLETE";
        String fileSystemDirectory = this.messagingDir;

        Integer senderId = (Integer) session.getAttribute("userId");
        int receiverId;
        Part filePart;

        Connection conn = null;

        try {

            receiverId = Integer.parseInt(req.getParameter("receiverId"));
            filePart = req.getPart("file");

            if (filePart == null || filePart.getSize() == 0) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file has been sent.");
                return;
            }


            if (filePart.getSize() > this.maxFileSizeBytes) {
                long maxFileSizeMB = this.maxFileSizeBytes / (1024 * 1024);
                // Devolvemos un JSON de error, que es lo que espera este servlet
                response.put("success", false);
                response.put("error", "The file exceeds the configured limit of " + maxFileSizeMB + "MB.");
                resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                resp.getWriter().write(gson.toJson(response));
                return;
            }


            originalFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
            originalFilename = originalFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
            if (originalFilename.trim().isEmpty() || originalFilename.matches("_+")) {
                originalFilename = "file";
            }

            if (originalFilename.length() > 250) {
                originalFilename = originalFilename.substring(0, 250);
            }

            String contentType = filePart.getContentType();
            String safeFileExtension;


            if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
                messageType = "IMAGE";
                safeFileExtension = contentType.substring(contentType.lastIndexOf('/') + 1);
                if (safeFileExtension.equals("jpeg")) safeFileExtension = "jpg";
            } else if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
                messageType = "VIDEO";
                safeFileExtension = contentType.substring(contentType.lastIndexOf('/') + 1);
            } else {
                response.put("success", false);
                response.put("error", "File type not allowed.");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            uniqueFilename = UUID.randomUUID().toString() + "." + safeFileExtension;


            try (Connection con = ds.getConnection()) {
                String multimediaCountSql = "SELECT COUNT(*) FROM message_send_logs WHERE sender_id = ? AND message_type IN ('IMAGE', 'VIDEO') AND created_at >= NOW() - INTERVAL 24 HOUR";
                try (PreparedStatement ps = con.prepareStatement(multimediaCountSql)) {
                    ps.setInt(1, senderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= this.maxMultimediaMessagesPerDay) {
                            response.put("success", false);
                            response.put("error", "You have reached the limit of multimedia messages sent per day.");
                            resp.setStatus(429); // Too Many Requests
                            resp.getWriter().write(gson.toJson(response));
                            return;
                        }
                    }
                }

                String blockStatus = getBlockStatus(con, senderId, receiverId);
                if (!"NONE".equals(blockStatus)) {
                    response.put("success", false);
                    response.put("error", "User blocked. Message cannot be sent.");
                    response.put("errorCode", "USER_BLOCKED");
                    resp.getWriter().write(gson.toJson(response));
                    return;
                }
            }


            if ("VIDEO".equals(messageType)) {

                status = "PROCESSING";
                previewFilename = null; // El worker lo generará
                fileSystemDirectory = this.messagingPendingDir; // Se guardará en 'pending'

                Path finalPendingPath = Paths.get(fileSystemDirectory, uniqueFilename);

                try (InputStream input = filePart.getInputStream()) {
                    Files.copy(input, finalPendingPath, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("Video '{}' saved to 'pending' directory for processing.", uniqueFilename);

            } else {

                status = "COMPLETE";
                fileSystemDirectory = this.messagingDir;
                Path finalImgPath = Paths.get(fileSystemDirectory, uniqueFilename);

                Path tempImgPath = null;
                try {
                    tempImgPath = Files.createTempFile("upload-msg-img-", ".tmp");
                    try (InputStream input = filePart.getInputStream()) {
                        Files.copy(input, tempImgPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    if (ImageIO.read(tempImgPath.toFile()) == null) {
                        response.put("success", false);
                        response.put("error", "File is not a valid image.");
                        resp.getWriter().write(gson.toJson(response));
                        return;
                    }

                    Files.copy(tempImgPath, finalImgPath, StandardCopyOption.REPLACE_EXISTING);

                } finally {
                    if (tempImgPath != null) {
                        Files.deleteIfExists(tempImgPath);
                    }
                }
            }

            conn = ds.getConnection();
            conn.setAutoCommit(false);

            String sql = "INSERT INTO messages (sender_id, receiver_id, message_type, file_path, preview_file, original_filename, created_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            Timestamp now = new Timestamp(System.currentTimeMillis());
            long generatedId = -1;

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, senderId);
                ps.setInt(2, receiverId);
                ps.setString(3, messageType);
                ps.setString(4, uniqueFilename);
                ps.setString(5, previewFilename);
                ps.setString(6, originalFilename);
                ps.setTimestamp(7, now);
                ps.setString(8, status);

                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            generatedId = generatedKeys.getLong(1);
                        }
                    }
                } else {
                    throw new SQLException("Insert into messages failed, no rows affected.");
                }
            }


            String logSql = "INSERT INTO message_send_logs (sender_id, receiver_id, message_type) VALUES (?, ?, ?)";
            try (PreparedStatement logPs = conn.prepareStatement(logSql)) {
                logPs.setInt(1, senderId);
                logPs.setInt(2, receiverId);
                logPs.setString(3, messageType);
                logPs.executeUpdate();
            }

            if ("VIDEO".equals(messageType)) {
                if (generatedId == -1) {
                    throw new SQLException("Could not retrieve message ID for video task.");
                }
                try {
                    MessageVideoProcessingTask task = new MessageVideoProcessingTask(
                            generatedId,
                            uniqueFilename,
                            this.ds,
                            this.messagingDir,
                            this.messagingPendingDir,
                            this.ffmpegPath,
                            this.gobblerExecutor
                    );

                    this.videoExecutor.submit(task);
                    log.info("Video processing task for message {} successfully submitted to executor.", generatedId);
                } catch (Exception e) {
                    log.error("CRITICAL ERROR: Failed to submit video task to executor. Initiating rollback.", e);
                    throw new SQLException("Failed to enqueue video task", e);
                }
            }

            conn.commit();


            response.put("success", true);
            Map<String, Object> sentMessage = new HashMap<>();
            sentMessage.put("id", generatedId);
            sentMessage.put("senderId", senderId);
            sentMessage.put("message_type", messageType);
            sentMessage.put("file_path", uniqueFilename);
            sentMessage.put("preview_file", previewFilename); // Será null para videos inicialmente
            sentMessage.put("timestamp", now.toInstant().toString());
            sentMessage.put("is_read", false);
            sentMessage.put("status", status); // Enviar el estado al cliente
            response.put("message", sentMessage);

            resp.getWriter().write(gson.toJson(response));

        } catch (Exception e) {

            log.error("Error in MessagingFileUploadServlet (general catch)", e);


            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Error on transaction rollback", ex);
                }
            }



            deleteFileFromDir(fileSystemDirectory, uniqueFilename);


            response.put("success", false);
            response.put("error", "Internal server error while processing the file.");
            resp.getWriter().write(gson.toJson(response));

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
                    log.error("Error closing TX connection", e);
                }
            }
        }
    }

    /**
     * Intenta eliminar un archivo de un directorio específico.
     * No lanza excepción si falla, solo lo loguea.
     * @param directory La ruta base del directorio (ej. this.messagingDir o this.messagingPendingDir)
     * @param fileName El nombre del archivo a borrar.
     */
    private void deleteFileFromDir(String directory, String fileName) {
        if (fileName == null || fileName.isEmpty() || directory == null) {
            return;
        }

        Path filePath = null;

        try {
            filePath = Paths.get(directory, fileName);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Cleaned up orphaned file: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Could not delete orphaned file: {}", (filePath != null ? filePath : fileName), e);
        }
    }


    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String getBlockStatus(Connection con, int currentUserId, int partnerId) throws SQLException {
        String sqlCheckMyBlock = "SELECT 1 FROM blocked_users WHERE blocker_id = ? AND blocked_id = ? LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sqlCheckMyBlock)) {
            ps.setInt(1, currentUserId);
            ps.setInt(2, partnerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return "I_BLOCKED";
            }
        }

        String sqlCheckTheirBlock = "SELECT 1 FROM blocked_users WHERE blocker_id = ? AND blocked_id = ? LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sqlCheckTheirBlock)) {
            ps.setInt(1, partnerId);
            ps.setInt(2, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return "THEY_BLOCKED";
            }
        }
        return "NONE";
    }
}