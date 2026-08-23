package com.voidchats.admin;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-dashboard")
public class AdminDashboardServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardServlet.class);

    private DataSource ds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");
        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for AdminDashboardServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for AdminDashboardServlet.");
        }
        log.info("AdminDashboardServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access Admin Dashboard.", user);
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }


        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);
        }

        request.setAttribute("csrfToken", csrfToken);

        Map<String, String> eventStatuses = new HashMap<>();
        String globalSchedulerStatus = "UNKNOWN";

        try (Connection conn = ds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SHOW VARIABLES LIKE 'event_scheduler'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    globalSchedulerStatus = rs.getString("Value").toUpperCase();
                }
            }

            String sql = "SELECT EVENT_NAME, STATUS FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE() AND EVENT_NAME IN ('clean_audit_logs', 'clean_rate_limit_logs')";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    eventStatuses.put(rs.getString("EVENT_NAME"), rs.getString("STATUS"));
                }
            }
        } catch (SQLException e) {
            log.error("Database error while checking MySQL Event Scheduler and event statuses.", e);
        }

        request.setAttribute("globalSchedulerStatus", globalSchedulerStatus);
        request.setAttribute("eventStatuses", eventStatuses);

        request.getRequestDispatcher("/WEB-INF/jsp/admin-dashboard.jsp").forward(request, response);
    }
}