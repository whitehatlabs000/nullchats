package com.voidchats.admin;

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
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-logs")
public class AdminLogsServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminLogsServlet.class);

    private DataSource ds;
    private static final int RECORDS_PER_PAGE = 100;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for AdminLogsServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for AdminLogsServlet.");
        }
        log.info("AdminLogsServlet initialized successfully.");
    }

    private String escapeSqlLike(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access Admin Logs viewer.", user);
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);
        }
        request.setAttribute("csrfToken", csrfToken);

        String action = request.getParameter("action");
        if ("load_logs".equals(action)) {
            String requestToken = request.getHeader("X-CSRF-Token");
            if (csrfToken == null || !csrfToken.equals(requestToken)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token.");
                return;
            }
            handleAjaxLogs(request, response);
        } else {
            handleInitialPage(request, response);
        }
    }

    private void handleInitialPage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        loadLogsData(request, 1);
        try (Connection conn = ds.getConnection()) {
            request.setAttribute("eventTypes", getDistinctEventTypes(conn));
        } catch (SQLException e) {
            log.error("Database error while loading distinct event types for the logs filter.", e);
            request.setAttribute("eventTypes", new ArrayList<>());
        }
        request.getRequestDispatcher("/WEB-INF/jsp/admin-logs.jsp").forward(request, response);
    }

    private void handleAjaxLogs(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int currentPage = 1;
        final int MAX_PAGE = 10000;

        String pageParam = request.getParameter("page");
        if (pageParam != null && !pageParam.isEmpty()) {
            try {
                currentPage = Integer.parseInt(pageParam);
                if (currentPage < 1) currentPage = 1;
                if (currentPage > MAX_PAGE) currentPage = MAX_PAGE;
            } catch (NumberFormatException e) {
                log.warn("Invalid page parameter format: '{}' in AdminLogsServlet. Defaulting to page 1.", pageParam);
                currentPage = 1;
            }
        }

        Map<String, Object> responseData = loadLogsData(request, currentPage);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(new Gson().toJson(responseData));
        }
    }

    private List<String> getDistinctEventTypes(Connection conn) throws SQLException {
        List<String> eventTypes = new ArrayList<>();
        String sql = "SELECT DISTINCT event_type FROM access_logs ORDER BY 1 ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                eventTypes.add(rs.getString(1));
            }
        }
        return eventTypes;
    }

    private Map<String, Object> loadLogsData(HttpServletRequest request, int currentPage) {
        String filterDate = request.getParameter("filterDate");
        String filterUsername = request.getParameter("filterUsername");
        String filterIp = request.getParameter("filterIp");
        String filterDetails = request.getParameter("filterDetails");
        String filterEvent = request.getParameter("filterEvent");

        if ((filterUsername != null && filterUsername.length() > 50) ||
                (filterIp != null && filterIp.length() > 50) ||
                (filterDetails != null && filterDetails.length() > 100) ||
                (filterEvent != null && filterEvent.length() > 50)) {
            log.warn("DoS SHIELD: Blocked query with extremely large filter parameters in AdminLogsServlet.");
            return new HashMap<>();
        }

        List<Map<String, String>> logs = new ArrayList<>();
        int totalLogs = 0;

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        StringBuilder filterParamsStr = new StringBuilder();

        if (filterDate != null && !filterDate.isEmpty()) {
            whereClause.append(" AND DATE(event_timestamp) = ?");
            params.add(filterDate);
            filterParamsStr.append("filterDate=").append(URLEncoder.encode(filterDate, StandardCharsets.UTF_8)).append("&");
        }
        if (filterUsername != null && !filterUsername.trim().isEmpty()) {
            whereClause.append(" AND username LIKE ? ESCAPE '\\\\'");
            params.add("%" + escapeSqlLike(filterUsername.trim()) + "%");
            filterParamsStr.append("filterUsername=").append(URLEncoder.encode(filterUsername.trim(), StandardCharsets.UTF_8)).append("&");
        }
        if (filterIp != null && !filterIp.trim().isEmpty()) {
            whereClause.append(" AND ip_address LIKE ? ESCAPE '\\\\'");
            params.add("%" + escapeSqlLike(filterIp.trim()) + "%");
            filterParamsStr.append("filterIp=").append(URLEncoder.encode(filterIp.trim(), StandardCharsets.UTF_8)).append("&");
        }
        if (filterDetails != null && !filterDetails.trim().isEmpty()) {
            whereClause.append(" AND details LIKE ? ESCAPE '\\\\'");
            params.add("%" + escapeSqlLike(filterDetails.trim()) + "%");
            filterParamsStr.append("filterDetails=").append(URLEncoder.encode(filterDetails.trim(), StandardCharsets.UTF_8)).append("&");
        }
        if (filterEvent != null && !filterEvent.isEmpty()) {
            whereClause.append(" AND event_type = ?");
            params.add(filterEvent);
            filterParamsStr.append("filterEvent=").append(URLEncoder.encode(filterEvent, StandardCharsets.UTF_8)).append("&");
        }

        try (Connection conn = ds.getConnection()) {
            // Contar totales (Consulta simple)
            String countSql = "SELECT COUNT(*) FROM access_logs" + whereClause.toString();
            try (PreparedStatement psCount = conn.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    psCount.setObject(i + 1, params.get(i));
                }
                try (ResultSet rsCount = psCount.executeQuery()) {
                    if (rsCount.next()) {
                        totalLogs = rsCount.getInt(1);
                    }
                }
            }

            String selectSql = "SELECT id, event_timestamp, ip_address, username, event_type, details FROM access_logs" +
                    whereClause.toString() + " ORDER BY event_timestamp DESC, id DESC LIMIT ? OFFSET ?";

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                int paramIndex = 1;
                for (Object param : params) {
                    ps.setObject(paramIndex++, param);
                }
                ps.setInt(paramIndex++, RECORDS_PER_PAGE);
                ps.setInt(paramIndex++, (currentPage - 1) * RECORDS_PER_PAGE);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> logEntry = new HashMap<>();
                        logEntry.put("id", rs.getString("id"));
                        logEntry.put("timestamp", rs.getString("event_timestamp"));
                        logEntry.put("ip", rs.getString("ip_address"));
                        logEntry.put("username", rs.getString("username") != null ? rs.getString("username") : "N/A");
                        logEntry.put("event", rs.getString("event_type"));
                        logEntry.put("details", rs.getString("details"));

                        logs.add(logEntry);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Database error while loading and filtering access logs.", e);
            request.setAttribute("error", "An error occurred while querying the database.");
        }

        int totalPages = (int) Math.ceil((double) totalLogs / RECORDS_PER_PAGE);

        Map<String, Object> data = new HashMap<>();
        data.put("logs", logs);
        data.put("currentPage", currentPage);
        data.put("totalPages", totalPages);
        data.put("filterParams", filterParamsStr.toString());

        request.setAttribute("logs", logs);
        request.setAttribute("currentPage", currentPage);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("filterDate", filterDate);
        request.setAttribute("filterUsername", filterUsername);
        request.setAttribute("filterIp", filterIp);
        request.setAttribute("filterDetails", filterDetails);
        request.setAttribute("filterEvent", filterEvent);

        return data;
    }
}