package com.voidchats.messaging;

import com.voidchats.AppPrivateConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jsoup.nodes.Entities;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@WebServlet("/api/messaging")
public class MessagingApiServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(MessagingApiServlet.class);

    private DataSource ds;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private static final SecureRandom sr = new SecureRandom();
    // Constantes de Cifrado
    private SecretKeySpec masterKey;
    private static final int GCM_IV_LENGTH = 12; // 12 bytes
    private static final int GCM_TAG_LENGTH = 128; // 128 bits


    // Límites para el envío de mensajes (cargados desde init)
    private int maxUniqueRecipientsPerDay;
    private int maxTotalMessagesPerDay;
    private int maxMessageLength;
    private int maxSearchLength;
    private boolean legacyEncryptionSupport;
    // por si mañana necesitamos reinventar el cifrado, no perder los mensajes antiguos
    private static final byte ENCRYPTION_VERSION_1 = 0x01;
    private static final byte ENCRYPTION_VERSION_2 = 0x02;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");
        Integer maxUniqueRecipients = (Integer) context.getAttribute("maxUniqueRecipients");
        Integer maxTotalMessages = (Integer) context.getAttribute("maxTotalMessages");
        String masterKeyString = AppPrivateConfig.getMessagingMasterKey();
        Integer maxMessageLength = (Integer) context.getAttribute("maxMessageLength");
        Integer maxSearchLength = (Integer) context.getAttribute("maxSearchLength");
        Boolean legacyEncryptionSupportObj = (Boolean) context.getAttribute("legacyEncryptionSupport");

        if (this.ds == null || maxUniqueRecipients == null || maxTotalMessages == null || masterKeyString == null || maxMessageLength == null || maxSearchLength == null || legacyEncryptionSupportObj == null) {
            log.error("Critical failure: Missing essential context resources (dbDataSource, limits, or keys) for MessagingServlet.");
            throw new ServletException("Critical failure: Missing essential resources in context for MessagingServlet.");
        }

        this.maxUniqueRecipientsPerDay = maxUniqueRecipients;
        this.maxTotalMessagesPerDay = maxTotalMessages;
        this.maxMessageLength = maxMessageLength;
        this.maxSearchLength = maxSearchLength;
        this.legacyEncryptionSupport = legacyEncryptionSupportObj;

        try {
            this.masterKey = deriveKeyFromMaster(masterKeyString);
            log.info("MessagingApiServlet initialized successfully. Master key derived.");
        } catch (Exception e) {
            log.error("Critical failure: Error deriving master encryption key.", e);
            throw new ServletException("Critical failure: Error deriving master encryption key.", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        String action = req.getParameter("action");

        if (session == null || session.getAttribute("userId") == null) {
            if (action == null) {

                resp.sendRedirect(req.getContextPath() + "/login");
            } else {

                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired or invalid.");
            }
            return;
        }
        Integer currentUserId = (Integer) session.getAttribute("userId");
        if (action == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing API action.");
            return;
        }
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            switch (action) {
                case "search_users":
                    handleSearchUsers(req, currentUserId, resp);
                    break;
                case "get_conversations":
                    handleGetConversations(currentUserId, resp);
                    break;
                case "get_messages":
                    handleGetMessages(req, currentUserId, resp);
                    break;
                case "get_new_events":
                    handleGetNewEvents(req, currentUserId, resp);
                    break;
                case "get_current_user_id":
                    resp.getWriter().write(gson.toJson(Map.of("userId", currentUserId)));
                    break;
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid GET action.");
            }
        } catch (Exception e) {
            log.error("Error in MessagingApiServlet doGet for action '{}' (User ID: {})", action, currentUserId, e);
            throw new ServletException("Database error on GET", e);
        }
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
        String action = req.getParameter("action");
        Integer currentUserId = (Integer) session.getAttribute("userId");
        try {
            switch (action) {
                case "send_message":
                    handleSendMessage(req, currentUserId, resp);
                    break;
                case "mark_as_read":
                    handleMarkAsRead(req, currentUserId, resp);
                    break;

                case "block_user":
                    handleBlockUser(req, currentUserId, resp);
                    break;
                case "unblock_user":
                    handleUnblockUser(req, currentUserId, resp);
                    break;

                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid POST action.");
            }
        } catch (Exception e) {
            log.error("Error in MessagingApiServlet doPost for action '{}' (User ID: {})", action, currentUserId, e);
            throw new ServletException("Database error on POST", e);
        }
    }
    private void handleSendMessage(HttpServletRequest req, Integer senderId, HttpServletResponse resp) throws SQLException, IOException {
        int receiverId = Integer.parseInt(req.getParameter("receiverId"));
        String rawContent = req.getParameter("content"); // 1. Obtenemos el contenido "crudo"
        Map<String, Object> response = new HashMap<>();

        if (rawContent == null || rawContent.length() > this.maxMessageLength) {
            response.put("success", false);
            response.put("error", "The message is too long. The maximum is " + this.maxMessageLength + " characters.");

            resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        String content = Entities.escape(rawContent);
        if (content.trim().isEmpty()) {
            response.put("success", false);
            response.put("error", "The message content cannot be empty.");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        byte[] aad = (senderId + ":" + receiverId).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Connection con = null;
        try {
            con = ds.getConnection();
            con.setAutoCommit(false);

            // 1. Verificar el límite total de mensajes enviados (500)
            String totalMessagesSql = "SELECT COUNT(*) FROM message_send_logs WHERE sender_id = ? AND message_type = 'TEXT' AND created_at >= NOW() - INTERVAL 24 HOUR";
            try (PreparedStatement ps = con.prepareStatement(totalMessagesSql)) {
                ps.setInt(1, senderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= this.maxTotalMessagesPerDay) {
                        log.warn("RATE LIMIT: User ID {} reached maximum TOTAL messages sent per day limit ({}).", senderId, this.maxTotalMessagesPerDay);
                        response.put("success", false);
                        response.put("error", "You have reached the limit of total messages sent per day (500).");
                        resp.setStatus(429);
                        resp.getWriter().write(gson.toJson(response));
                        con.rollback();
                        return;
                    }
                }
            }

            // 2. Verificar el límite de destinatarios únicos (250)
            String uniqueRecipientsSql = "SELECT COUNT(DISTINCT receiver_id) FROM message_send_logs WHERE sender_id = ? AND message_type = 'TEXT' AND created_at >= NOW() - INTERVAL 24 HOUR";
            try (PreparedStatement ps = con.prepareStatement(uniqueRecipientsSql)) {
                ps.setInt(1, senderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= this.maxUniqueRecipientsPerDay) {
                        log.warn("RATE LIMIT: User ID {} reached maximum UNIQUE recipients per day limit ({}).", senderId, this.maxUniqueRecipientsPerDay);
                        response.put("success", false);
                        response.put("error", "You have reached the limit of unique users you can message per day (250).");
                        resp.setStatus(429);
                        resp.getWriter().write(gson.toJson(response));
                        con.rollback();
                        return;
                    }
                }
            }

            String blockStatus = "NONE";
            String blockStatusSql =
                    "SELECT " +
                            "    CASE " +
                            "        WHEN my_block.blocker_id IS NOT NULL THEN 'I_BLOCKED' " +
                            "        WHEN their_block.blocker_id IS NOT NULL THEN 'THEY_BLOCKED' " +
                            "        ELSE 'NONE' " +
                            "    END AS blockStatus " +
                            "FROM (SELECT 1) AS dummy " +
                            "LEFT JOIN blocked_users AS my_block " +
                            "    ON my_block.blocker_id = ? AND my_block.blocked_id = ? " + // Yo bloqueé
                            "LEFT JOIN blocked_users AS their_block " +
                            "    ON their_block.blocker_id = ? AND their_block.blocked_id = ?"; // Ellos me bloquearon

            try (PreparedStatement ps = con.prepareStatement(blockStatusSql)) {
                ps.setInt(1, senderId);
                ps.setInt(2, receiverId);
                ps.setInt(3, receiverId);
                ps.setInt(4, senderId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        blockStatus = rs.getString("blockStatus");
                    }
                }
            }

            if (!"NONE".equals(blockStatus)) {
                response.put("success", false);
                response.put("error", "User blocked. Message cannot be sent..");
                response.put("errorCode", "USER_BLOCKED");
                response.put("blockStatus", blockStatus);
                resp.getWriter().write(gson.toJson(response));
                con.rollback();
                return;
            }



            String sql = "INSERT INTO messages (sender_id, receiver_id, content, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ps.setInt(1, senderId);
                ps.setInt(2, receiverId);

                ps.setString(3, encrypt(content, aad));

                ps.setTimestamp(4, now);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            long id = generatedKeys.getLong(1);

                            String logSql = "INSERT INTO message_send_logs (sender_id, receiver_id, message_type) VALUES (?, ?, ?)";
                            try (PreparedStatement logPs = con.prepareStatement(logSql)) {
                                logPs.setInt(1, senderId);
                                logPs.setInt(2, receiverId);
                                logPs.setString(3, "TEXT");
                                logPs.executeUpdate();
                            }

                            response.put("success", true);
                            Map<String, Object> sentMessage = new HashMap<>();
                            sentMessage.put("id", id);
                            sentMessage.put("senderId", senderId);
                            sentMessage.put("content", content);
                            sentMessage.put("timestamp", now.toInstant().toString());
                            sentMessage.put("is_read", false);
                            response.put("message", sentMessage);
                        }
                    }
                } else {
                    response.put("success", false);
                    response.put("error", "The message could not be saved to the database.");
                    con.rollback();
                }
            }

            con.commit();

        } catch (SQLException e) {
            log.error("SQL Error sending message from user {} to {}", senderId, receiverId, e);
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    log.error("Critical error during transaction rollback", ex);
                }
            }
            response.put("success", false);
            response.put("error", "A database error occurred while sending the message.");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    log.error("Error closing connection", e);
                }
            }
        }
        resp.getWriter().write(gson.toJson(response));
    }

    private void handleBlockUser(HttpServletRequest req, Integer blockerId, HttpServletResponse resp) throws SQLException, IOException {
        int blockedId = Integer.parseInt(req.getParameter("partnerId"));

        String sql = "INSERT INTO blocked_users (blocker_id, blocked_id) VALUES (?, ?)";

        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            ps.executeUpdate();

        } catch (SQLException e) {

            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {

                log.debug("Attempt to insert a duplicate block (already existed). Ignored. Blocker: {}, Blocked: {}", blockerId, blockedId);
            } else {
                throw e;
            }
        }

        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleUnblockUser(HttpServletRequest req, Integer blockerId, HttpServletResponse resp) throws SQLException, IOException {
        int blockedId = Integer.parseInt(req.getParameter("partnerId"));
        String sql = "DELETE FROM blocked_users WHERE blocker_id = ? AND blocked_id = ?";

        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            ps.executeUpdate();
        }
        resp.getWriter().write(gson.toJson(Map.of("success", true)));
    }

    private void handleGetNewEvents(HttpServletRequest req, Integer userId, HttpServletResponse resp) throws SQLException, IOException {
        int partnerId = Integer.parseInt(req.getParameter("partnerId"));
        String lastTimestampStr = req.getParameter("since");
        Timestamp lastTimestamp = Timestamp.from(Instant.parse(lastTimestampStr));

        // Captura la hora actual del servidor al inicio del chequeo.
        String pollTimestamp = Instant.now().toString();

        Map<String, Object> events = new HashMap<>();
        List<Map<String, Object>> newMessages = new ArrayList<>();
        boolean partnerHasRead = false;

        try (Connection con = ds.getConnection()) {

            String sqlNewMessages = "SELECT id, sender_id, receiver_id, content, created_at, is_read, message_type, file_path, preview_file, original_filename FROM messages " +
                    "WHERE receiver_id = ? AND sender_id = ? AND created_at > ? " +
                    "ORDER BY created_at ASC";

            try (PreparedStatement ps = con.prepareStatement(sqlNewMessages)) {
                ps.setInt(1, userId);
                ps.setInt(2, partnerId);
                ps.setTimestamp(3, lastTimestamp);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        newMessages.add(mapMessage(rs));
                    }
                }
            }
            events.put("newMessages", newMessages);

            String sqlCheckRead = "SELECT 1 FROM messages WHERE sender_id = ? AND receiver_id = ? AND is_read = 1 AND updated_at > ? LIMIT 1";

            try (PreparedStatement ps = con.prepareStatement(sqlCheckRead)) {
                ps.setInt(1, userId);
                ps.setInt(2, partnerId);
                ps.setTimestamp(3, lastTimestamp);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        partnerHasRead = true;
                    }
                }
            }
            events.put("partnerHasRead", partnerHasRead);

        }

        events.put("pollTimestamp", pollTimestamp);

        resp.getWriter().write(gson.toJson(events));
    }

    private void handleGetConversations(Integer userId, HttpServletResponse resp) throws SQLException, IOException {
        List<Map<String, Object>> conversations = new ArrayList<>();

        String sql =
                "WITH LastMessages AS (" +
                        "    SELECT m.id, m.content, m.created_at, m.message_type, " +
                        "           m.sender_id, m.receiver_id, " +
                        "           CASE WHEN m.sender_id = ? THEN m.receiver_id ELSE m.sender_id END AS partner_id " +
                        "    FROM messages m " +
                        "    INNER JOIN (" +
                        "        SELECT LEAST(sender_id, receiver_id) AS user1, GREATEST(sender_id, receiver_id) AS user2, MAX(id) AS max_message_id " +
                        "        FROM messages " +
                        "        WHERE sender_id = ? OR receiver_id = ? " +
                        "        GROUP BY user1, user2" +
                        "    ) AS Convos ON m.id = Convos.max_message_id" +
                        ")" +
                        "SELECT u.id AS partnerId, u.username AS partnerUsername, u.profile_img AS partnerProfileImage, " +
                        "       lm.content, lm.created_at AS timestamp, lm.message_type, " +
                        "       lm.sender_id, lm.receiver_id, " +
                        "       (SELECT COUNT(*) FROM messages WHERE sender_id = u.id AND receiver_id = ? AND is_read = 0) AS unreadCount, " +
                        "       bu_me.blocker_id AS i_blocked_them, " + // Será el ID del blocker (o NULL)
                        "       bu_them.blocker_id AS they_blocked_me " + // Será el ID del blocker (o NULL)
                        "FROM LastMessages lm " +
                        "JOIN usuarios u ON u.id = lm.partner_id " +
                        "LEFT JOIN blocked_users bu_me ON bu_me.blocker_id = ? AND bu_me.blocked_id = u.id " + // Yo los bloqueé
                        "LEFT JOIN blocked_users bu_them ON bu_them.blocker_id = u.id AND bu_them.blocked_id = ? " + // Ellos me bloquearon
                        "ORDER BY lm.created_at DESC";

        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                ps.setInt(4, userId); // Para el unreadCount
                ps.setInt(5, userId); // Para el JOIN bu_me
                ps.setInt(6, userId); // Para el JOIN bu_them

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> conv = new HashMap<>();
                        conv.put("partnerId", rs.getInt("partnerId"));
                        conv.put("partnerUsername", rs.getString("partnerUsername"));
                        conv.put("partnerProfileImage", rs.getString("partnerProfileImage"));

                        String messageType = rs.getString("message_type");
                        String lastMessageText;
                        if ("IMAGE".equals(messageType)) {
                            lastMessageText = "📷 Image";
                        } else if ("VIDEO".equals(messageType)) {
                            lastMessageText = "📹 Video";
                        } else {

                            int senderId = rs.getInt("sender_id");
                            int receiverId = rs.getInt("receiver_id");
                            byte[] aad = (senderId + ":" + receiverId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            lastMessageText = decrypt(rs.getString("content"), aad);
                        }
                        conv.put("lastMessage", lastMessageText);

                        Timestamp timestamp = rs.getTimestamp("timestamp");
                        if (timestamp != null) {
                            conv.put("timestamp", timestamp.toInstant().toString());
                        } else {
                            conv.put("timestamp", null);
                        }

                        conv.put("unreadCount", rs.getInt("unreadCount"));

                        String blockStatus = "NONE";
                        if (rs.getObject("i_blocked_them") != null) {
                            blockStatus = "I_BLOCKED";
                        } else if (rs.getObject("they_blocked_me") != null) {
                            blockStatus = "THEY_BLOCKED";
                        }
                        conv.put("blockStatus", blockStatus);

                        conversations.add(conv);
                    }
                }
            }
        }
        resp.getWriter().write(gson.toJson(conversations));
    }

    private void handleMarkAsRead(HttpServletRequest req, Integer userId, HttpServletResponse resp) throws SQLException, IOException {
        int partnerId = Integer.parseInt(req.getParameter("partnerId"));
        String sql = "UPDATE messages SET is_read = 1, updated_at = ? WHERE sender_id = ? AND receiver_id = ? AND is_read = 0";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            // Usamos la hora del servidor de la aplicación como única fuente de verdad.
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, partnerId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
        resp.getWriter().write("{\"success\": true}");
    }

    private void handleGetMessages(HttpServletRequest req, Integer userId, HttpServletResponse resp) throws SQLException, IOException {
        int partnerId = Integer.parseInt(req.getParameter("partnerId"));
        int offset = 0;

        // Constantes de seguridad
        final int DEFAULT_LIMIT = 20;
        final int MAX_LIMIT = 50;
        int limit = DEFAULT_LIMIT;

        try {

            String offsetParam = req.getParameter("offset");
            if (offsetParam != null && !offsetParam.isEmpty()) {
                offset = Integer.parseInt(offsetParam);
                if (offset < 0) offset = 0;
            }

            String limitParam = req.getParameter("limit");
            if (limitParam != null && !limitParam.isEmpty()) {
                int requestedLimit = Integer.parseInt(limitParam);
                if (requestedLimit < 1) {
                    limit = 1;
                } else if (requestedLimit > MAX_LIMIT) {
                    limit = MAX_LIMIT;
                } else {
                    limit = requestedLimit;
                }
            }
        } catch (NumberFormatException e) {

        }

        Map<String, Object> responseData = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        String blockStatus = "NONE";

        try (Connection con = ds.getConnection()) {

            String blockStatusSql =
                    "SELECT " +
                            "    CASE " +
                            "        WHEN my_block.blocker_id IS NOT NULL THEN 'I_BLOCKED' " +
                            "        WHEN their_block.blocker_id IS NOT NULL THEN 'THEY_BLOCKED' " +
                            "        ELSE 'NONE' " +
                            "    END AS blockStatus " +
                            "FROM (SELECT 1) AS dummy " +
                            "LEFT JOIN blocked_users AS my_block " +
                            "    ON my_block.blocker_id = ? AND my_block.blocked_id = ? " + // Yo bloqueé
                            "LEFT JOIN blocked_users AS their_block " +
                            "    ON their_block.blocker_id = ? AND their_block.blocked_id = ?"; // Ellos me bloquearon

            try (PreparedStatement ps = con.prepareStatement(blockStatusSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, partnerId);
                ps.setInt(3, partnerId);
                ps.setInt(4, userId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        blockStatus = rs.getString("blockStatus");
                    }
                }
            }
            responseData.put("blockStatus", blockStatus);

            String sql = "SELECT id, sender_id, receiver_id, content, created_at, is_read, message_type, file_path, preview_file, original_filename FROM messages " +
                    "WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                    "ORDER BY created_at DESC LIMIT ? OFFSET ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, partnerId);
                ps.setInt(3, partnerId);
                ps.setInt(4, userId);
                ps.setInt(5, limit);
                ps.setInt(6, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapMessage(rs));
                    }
                }
            }
            responseData.put("messages", messages);
        }
        resp.getWriter().write(gson.toJson(responseData));
    }


    private Map<String, Object> mapMessage(ResultSet rs) throws SQLException {
        Map<String, Object> msg = new HashMap<>();

        int messageId = rs.getInt("id");
        int senderId = rs.getInt("sender_id");
        int receiverId = rs.getInt("receiver_id");
        String content = rs.getString("content");

        msg.put("id", messageId);
        msg.put("senderId", senderId);

        byte[] aad = (senderId + ":" + receiverId).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        try {
            msg.put("content", decrypt(content, aad));
        } catch (Exception e) {
            log.error("Fallo grave de descifrado en el mensaje ID: {}. Sender: {}, Receiver: {}", messageId, senderId, receiverId, e);
            msg.put("content", "[Error de seguridad en el mensaje]");
        }

        msg.put("timestamp", rs.getTimestamp("created_at").toInstant().toString());
        msg.put("is_read", rs.getBoolean("is_read"));
        msg.put("message_type", rs.getString("message_type"));
        msg.put("file_path", rs.getString("file_path"));
        msg.put("preview_file", rs.getString("preview_file")); // ¡NUEVO!
        msg.put("original_filename", rs.getString("original_filename"));
        return msg;
    }

    private String escapeSqlLike(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void handleSearchUsers(HttpServletRequest req, Integer currentUserId, HttpServletResponse resp) throws SQLException, IOException {
        String query = req.getParameter("query");

        if (query == null || query.trim().isEmpty()) {
            resp.getWriter().write("[]");
            return;
        }

        // Usamos la variable de instancia cargada desde el config.properties
        if (query.length() > this.maxSearchLength) {
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "The search query is too long.");
            return;
        }

        List<Map<String, Object>> users = new ArrayList<>();

        String sql = "SELECT u.id, u.username, u.profile_img FROM usuarios u " +
                "WHERE u.username LIKE ? ESCAPE '\\\\' AND u.id != ? AND NOT EXISTS (" +
                "    SELECT 1 FROM blocked_users bu " +
                "    WHERE (bu.blocker_id = u.id AND bu.blocked_id = ?) OR (bu.blocker_id = ? AND bu.blocked_id = u.id)" +
                ") LIMIT 5";

        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

            String sanitizedQuery = escapeSqlLike(query.trim());
            ps.setString(1, "%" + sanitizedQuery + "%");

            ps.setInt(2, currentUserId);
            ps.setInt(3, currentUserId);
            ps.setInt(4, currentUserId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("id"));
                    user.put("username", rs.getString("username"));
                    user.put("profileImageFilename", rs.getString("profile_img"));
                    users.add(user);
                }
            }
        }
        resp.getWriter().write(gson.toJson(users));
    }

    private String generateCSRFToken() {
        byte[] bytes = new byte[16];
        sr.nextBytes(bytes);

        // Convertir los 16 bytes aleatorios a un string Base64 URL-safe sin padding.

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Toma la clave del config.properties y la convierte en una clave de cifrado
     * reproducible de 256 bits (32 bytes) usando SHA-256.
     */
    private SecretKeySpec deriveKeyFromMaster(String masterKeyString) throws NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(masterKeyString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Cifra un mensaje de texto.
     */
    private String encrypt(String plainText, byte[] aad) { // <-- CAMBIO
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            // 1. Generar un IV aleatorio y seguro
            byte[] iv = new byte[GCM_IV_LENGTH];
            sr.nextBytes(iv);

            // 2. Preparar el cifrador
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, this.masterKey, parameterSpec);

            // 3. AÑADIR EL AAD
            cipher.updateAAD(aad);

            // 4. Cifrar
            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 5. Combinar [VERSION] + [IV] + [Texto Cifrado]
            ByteBuffer byteBuffer = ByteBuffer.allocate(1 + iv.length + cipherText.length);

            byteBuffer.put(ENCRYPTION_VERSION_2);

            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // 6. Codificar en Base64 para almacenamiento en BD
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption error during message encryption process", e);
            throw new RuntimeException("Encryption error", e);
        }
    }

    /**
     * Descifra (codificado en Base64).
     * Si legacyEncryptionSupport es false, toma una vía rápida de alto rendimiento asumiendo V2.
     * Si es true, analiza si es V-1 (texto plano), V0 (sin versión), V1 (sin AAD) o V2 (con AAD).
     */
    private String decrypt(String base64CipherText, byte[] aad) {
        if (base64CipherText == null || base64CipherText.isEmpty()) {
            return base64CipherText;
        }

        byte[] combined;
        try {
            // 1. INTENTAMOS decodificar de Base64.
            combined = Base64.getDecoder().decode(base64CipherText);

            // Verificación de tamaño mínimo (IV 12 + Tag 16 = 28 bytes mínimo)
            if (combined.length < 28) {
                if (this.legacyEncryptionSupport) {
                    return base64CipherText; // Fallback a texto plano heredado
                } else {
                    return "[Illegible or corrupted message]"; // rechazo ultra rápido
                }
            }
        } catch (IllegalArgumentException e) {
            // FALLO DE BASE64, Es texto plano.
            if (this.legacyEncryptionSupport) {
                log.debug("Detected old V-1 (plain text) message format. Bypassing decryption.");
                return base64CipherText;
            } else {
                return "[Illegible or corrupted message]";
            }
        }

        if (!this.legacyEncryptionSupport) {
            try {
                // Asumimos ciegamente que la estructura es la ultima version:
                // [1 byte versión] + [12 bytes IV] + [Resto CipherText]

                // Si por alguna razon el primer byte no es V2, fallamos rápido para evitar errores raros
                if (combined[0] != ENCRYPTION_VERSION_2) {
                    return "[Illegible or corrupted message]";
                }

                byte[] iv = Arrays.copyOfRange(combined, 1, 1 + GCM_IV_LENGTH);
                byte[] cipherText = Arrays.copyOfRange(combined, 1 + GCM_IV_LENGTH, combined.length);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, this.masterKey, parameterSpec);
                cipher.updateAAD(aad); // V2 requiere AAD de forma estricta

                byte[] plainTextBytes = cipher.doFinal(cipherText);
                return new String(plainTextBytes, java.nio.charset.StandardCharsets.UTF_8);

            } catch (javax.crypto.AEADBadTagException e) {
                log.warn("SECURITY/CRYPTO WARNING: Fast-Path Authentication tag failed! Possible message tampering. Snippet: {}...", base64CipherText.substring(0, Math.min(20, base64CipherText.length())));
                return "[Illegible or corrupted message]";
            } catch (Exception e) {
                log.warn("SECURITY WARNING: Strict V2 decryption failed due to invalid format or internal error. Snippet: {}...", base64CipherText.substring(0, Math.min(20, base64CipherText.length())));
                return "[Illegible or corrupted message]";
            }
        }

        // MODO COMPATIBILIDAD

        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            byteBuffer.mark();
            byte firstByte = byteBuffer.get();

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText;
            boolean isV2 = false;

            if (firstByte == ENCRYPTION_VERSION_2) {
                // --- V2 (CON AAD) ---
                isV2 = true;
                byteBuffer.get(iv);
                cipherText = new byte[byteBuffer.remaining()];
                byteBuffer.get(cipherText);
            } else if (firstByte == ENCRYPTION_VERSION_1) {
                // --- V1 ( SIN AAD) ---
                byteBuffer.get(iv);
                cipherText = new byte[byteBuffer.remaining()];
                byteBuffer.get(cipherText);
            } else {

                byteBuffer.reset(); // Vuelve a la posición 0
                byteBuffer.get(iv);
                cipherText = new byte[byteBuffer.remaining()];
                byteBuffer.get(cipherText);
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, this.masterKey, parameterSpec);

            if (isV2) {
                cipher.updateAAD(aad);
            }

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, java.nio.charset.StandardCharsets.UTF_8);

        } catch (javax.crypto.AEADBadTagException e) {
            log.warn("SECURITY/CRYPTO WARNING: Authentication tag failed! Snippet: {}...", base64CipherText.substring(0, Math.min(20, base64CipherText.length())));
            return "[Illegible or corrupted message]";
        } catch (Exception e) {
            log.error("Legacy decryption error for snippet: {}...", base64CipherText.substring(0, Math.min(20, base64CipherText.length())), e);
            return "[Illegible or corrupted message]";
        }
    }
}
