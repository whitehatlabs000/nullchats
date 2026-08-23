package com.voidchats.video;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/get-message-status")
public class GetMessageStatusServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(GetMessageStatusServlet.class);

    private DataSource ds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for GetMessageStatusServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for GetMessageStatusServlet.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        long messageId;
        try {
            messageId = Long.parseLong(req.getParameter("id"));
        } catch (NumberFormatException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid message ID.");
            return;
        }


        String sql = "SELECT status, preview_file FROM messages WHERE id = ?";
        JsonObject responseJson = new JsonObject();

        try (Connection conn = this.ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, messageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    responseJson.addProperty("id", messageId);
                    responseJson.addProperty("status", rs.getString("status"));
                    responseJson.addProperty("previewFile", rs.getString("preview_file"));
                } else {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Message not found.");
                    return;
                }
            }
        } catch (SQLException e) {
            log.error("Database error while fetching status for message ID {}", messageId, e);
            throw new ServletException("Database error", e);
        }

        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().print(new Gson().toJson(responseJson));
    }
}