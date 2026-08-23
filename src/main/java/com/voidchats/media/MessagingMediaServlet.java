package com.voidchats.media;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/media/*")
public class MessagingMediaServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(MessagingMediaServlet.class);

    private String messagingDir;
    private DataSource ds;
    private static final int DEFAULT_BUFFER_SIZE = 20480; // 20KB

    private static final Cache<String, Boolean> accessCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.messagingDir = (String) context.getAttribute("messagingDir");
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.messagingDir == null || this.ds == null) {
            log.error("Critical failure: Missing essential context resources ('messagingDir', 'dbDataSource') for MessagingMediaServlet.");
            throw new ServletException("Critical failure: Missing essential resources ('messagingDir', 'dbDataSource') in context for MessagingMediaServlet.");
        }

        log.info("MessagingMediaServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {


        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "You must be logged in to view media.");
            return;
        }
        Integer currentUserId = (Integer) session.getAttribute("userId");


        String requestedFile = req.getPathInfo();
        if (requestedFile == null || requestedFile.equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (requestedFile.length() > 255) {
            log.warn("DoS SHIELD: Blocked extremely long file path request in MessagingMediaServlet (length: {})", requestedFile.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Requested file path is too long.");
            return;
        }

        String filename = URLDecoder.decode(requestedFile.substring(1), StandardCharsets.UTF_8);
        File file = new File(messagingDir, filename);

        if (!file.exists() || !file.isFile() || !file.getCanonicalPath().startsWith(new File(messagingDir).getCanonicalPath())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Verificamos si el usuario actual es el emisor O el receptor del mensaje
        // al que pertenece este archivo.
        String cacheKey = currentUserId + "_" + filename;

        Boolean hasAccess = accessCache.get(cacheKey, key -> {
            try (Connection connAccess = ds.getConnection()) {
                // Buscamos un mensaje que contenga este archivo (ya sea como adjunto o vista previa)
                // Y donde el usuario actual sea parte de la conversación.
                String accessSql = "SELECT 1 FROM messages " +
                        "WHERE (file_path = ? OR preview_file = ?) " +
                        "AND (sender_id = ? OR receiver_id = ?) " +
                        "LIMIT 1";

                try (PreparedStatement psAccess = connAccess.prepareStatement(accessSql)) {
                    psAccess.setString(1, filename);
                    psAccess.setString(2, filename);
                    psAccess.setInt(3, currentUserId);
                    psAccess.setInt(4, currentUserId);
                    try (ResultSet rsAccess = psAccess.executeQuery()) {
                        return rsAccess.next();
                    }
                }
            } catch (SQLException e) {
                log.error("Database error while checking access permissions for file '{}'", filename, e);
                return null; // Retornamos null para que Caffeine no cachee el error y reintente
            }
        });


        if (hasAccess == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error checking permissions.");
            return;
        } else if (!hasAccess) {
            log.warn("SECURITY: Unauthorized access attempt to private messaging media '{}' by user ID {}", filename, currentUserId);
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this file.");
            return;
        }


        // Pasamos 'true' porque este servlet es exclusivo de mensajería privada
        if (MediaCacheUtil.processCacheHeaders(req, resp, file, true)) {
            // El usuario tiene permisos y el navegador ya tiene el archivo multimedia.
            return;
        }

        long length = file.length();
        String contentType = getServletContext().getMimeType(file.getName());
        if (contentType == null) contentType = "application/octet-stream";

        // NOTA: No usamos resp.reset() para no borrar las cabeceras de MediaCacheUtil
        resp.setBufferSize(DEFAULT_BUFFER_SIZE);
        resp.setHeader("Content-Type", contentType);
        resp.setHeader("Accept-Ranges", "bytes");

        // Recuperamos el ETag generado por la utilidad para la lógica If-Range de fragmentos de video
        String etag = resp.getHeader("ETag");


        String safeFileName = filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String encodedFileName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        String headerValue = "inline; filename=\"" + safeFileName + "\"; filename*=UTF-8''" + encodedFileName;
        resp.setHeader("Content-Disposition", headerValue);

        String rangeHeader = req.getHeader("Range");
        String ifRangeHeader = req.getHeader("If-Range");

        if (rangeHeader != null && (ifRangeHeader == null || ifRangeHeader.equals(etag))) {
            if (!rangeHeader.matches("^bytes=\\d*-\\d*$")) {
                resp.setHeader("Content-Range", "bytes */" + length);
                resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            String[] ranges = rangeHeader.substring(6).split("-");
            long start = (ranges.length > 0 && !ranges[0].isEmpty()) ? Long.parseLong(ranges[0]) : 0;
            long end = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : length - 1;

            if (start > end || end >= length) {
                resp.setHeader("Content-Range", "bytes */" + length);
                resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            long contentLength = end - start + 1;
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            resp.setHeader("Content-Length", String.valueOf(contentLength));
            resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);

            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                 OutputStream output = resp.getOutputStream()) {

                randomAccessFile.seek(start);
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                long bytesToWrite = contentLength;

                while (bytesToWrite > 0) {
                    int bytesRead = randomAccessFile.read(buffer, 0, (int) Math.min(bytesToWrite, buffer.length));
                    if (bytesRead == -1) break;
                    output.write(buffer, 0, bytesRead);
                    bytesToWrite -= bytesRead;
                }
            } catch (IOException e) {
                // El cliente cerró la conexión, es normal.
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Content-Length", String.valueOf(length));
            java.nio.file.Files.copy(file.toPath(), resp.getOutputStream());
        }
    }

    // METODOS PUBLICOS DE INVALIDACION DE CACHE

    /**
     * Invalida el acceso a un archivo específico para un usuario.
     * Util cuando se borra un solo mensaje multimedia.
     */
    public static void invalidateMediaAccess(int userId, String filename) {
        if (accessCache != null) {
            accessCache.invalidate(userId + "_" + filename);
        }
    }

    /**
     * Invalida TODOS los accesos cacheados de un usuario.
     * Util cuando se borra un chat entero y no queremos iterar archivo por archivo.
     */
    public static void invalidateAllUserMediaAccess(int userId) {
        if (accessCache != null) {
            String prefix = userId + "_";
            // Recorre las llaves y borra todas las que empiecen con "ID_"
            accessCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    @Override
    public void destroy() {
        if (accessCache != null) {
            accessCache.invalidateAll();
            accessCache.cleanUp();
            log.info("MessagingMediaServlet accessCache cleared to prevent memory leaks.");
        }
        super.destroy();
    }
}
