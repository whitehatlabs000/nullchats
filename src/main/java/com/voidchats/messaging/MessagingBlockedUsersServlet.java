package com.voidchats.messaging;

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

@WebServlet("/blocked-users")
public class MessagingBlockedUsersServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(MessagingBlockedUsersServlet.class);

    private DataSource ds;
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for MessagingBlockedUsersServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for MessagingBlockedUsersServlet.");
        }
        log.info("MessagingBlockedUsersServlet initialized successfully.");
    }

    // Para obtener la lista de usuarios bloqueados
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Integer currentUserId = (Integer) session.getAttribute("userId");

        List<Map<String, Object>> blockedUsers = new ArrayList<>();
        // Consulta que une blocked_users con usuarios para obtener los datos de los bloqueados
        String sql = "SELECT u.id, u.username, u.profile_img FROM usuarios u " +
                "JOIN blocked_users bu ON u.id = bu.blocked_id " +
                "WHERE bu.blocker_id = ?";

        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("id"));
                    user.put("username", rs.getString("username"));
                    user.put("profileImage", rs.getString("profile_img"));
                    blockedUsers.add(user);
                }
            }
        } catch (SQLException e) {
            log.error("Database error getting blocked users for user ID: {}", currentUserId, e);
            throw new ServletException("Database error getting blocked users", e);
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(blockedUsers));
    }

    // Para procesar la solicitud de desbloqueo
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Map<String, Object> response = new HashMap<>();

        String sessionCsrfToken = (session != null) ? (String) session.getAttribute("csrfToken") : null;
        String requestCsrfToken = req.getParameter("csrfToken");

        if (session == null || session.getAttribute("userId") == null || sessionCsrfToken == null || !sessionCsrfToken.equals(requestCsrfToken)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.put("success", false);
            response.put("error", "Access denied or invalid token.");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        Integer currentUserId = (Integer) session.getAttribute("userId");
        int userToUnblockId;
        try {
            userToUnblockId = Integer.parseInt(req.getParameter("userIdToUnblock"));
        } catch (NumberFormatException e) {
            log.warn("Invalid user ID format for unblock request from user ID: {}", currentUserId);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("success", false);
            response.put("error", "Invalid user ID.");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        String sql = "DELETE FROM blocked_users WHERE blocker_id = ? AND blocked_id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentUserId);
            ps.setInt(2, userToUnblockId);
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                response.put("success", true);
            } else {
                response.put("success", false);
                response.put("error", "The user could not be unlocked or was already unlocked.");
            }
        } catch (SQLException e) {
            log.error("Database error when unlocking user {} for blocker ID {}", userToUnblockId, currentUserId, e);
            throw new ServletException("Database error when unlocking user", e);
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(response));
    }
}