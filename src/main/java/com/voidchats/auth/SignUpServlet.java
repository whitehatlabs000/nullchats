package com.voidchats.auth;

import com.voidchats.IPUtils;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/sign_up")
public class SignUpServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(SignUpServlet.class);

    private DataSource ds;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,25}$");
    private int maxAccountsPerDay;
    private Set<String> reservedUsernames;

    private String profileDir;
    private String dicebearApiUrl;

    @Override
    public void init() throws ServletException {
        try {
            // Obtenemos los recursos del AppLifecycleListener
            ServletContext context = getServletContext();

            this.ds = (DataSource) context.getAttribute("dbDataSource");
            Integer maxAccounts = (Integer) context.getAttribute("maxAccountsPerDay");

            this.profileDir = (String) context.getAttribute("profileDir");
            this.dicebearApiUrl = (String) context.getAttribute("dicebearApiUrl");
            // Fallback por si la variable no está inyectada globalmente
            if (this.dicebearApiUrl == null) this.dicebearApiUrl = "https://api.dicebear.com/7.x";

            if (ds == null || maxAccounts == null || profileDir == null) {
                log.error("Catastrophic failure: Listener could not load ds, maxAccountsPerDay, or profileDir.");
                throw new ServletException("Missing context resources (ds, maxAccounts, profileDir)");
            }

            this.maxAccountsPerDay = maxAccounts;

            String reservedUsernamesStr = (String) context.getAttribute("reservedUsernames");
            this.reservedUsernames = new HashSet<>();
            if (reservedUsernamesStr != null && !reservedUsernamesStr.trim().isEmpty()) {
                String[] arr = reservedUsernamesStr.toLowerCase().split(",");
                this.reservedUsernames.addAll(Arrays.asList(arr));
            }

            log.info("SignUpServlet initialized | Reserved names loaded: {}", this.reservedUsernames.size());

        } catch (Exception e) {
            log.error("Catastrophic failure initializing SignUpServlet", e);
            throw new ServletException("Failed to initialize servlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(true);

        if (session.getAttribute("user") != null) {
            resp.sendRedirect("home");
            return;
        }

        String okMessage = (String) session.getAttribute("ok");
        if (okMessage != null) {
            req.setAttribute("ok", okMessage);
            req.setAttribute("successRedirect", true); // Para el meta tag del JSP
            session.removeAttribute("ok");
        }

        String flashError = (String) session.getAttribute("flashError");
        if (flashError != null) {
            req.setAttribute("error", flashError);
            session.removeAttribute("flashError");
        }

        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);
        }
        req.setAttribute("csrfToken", csrfToken);

        req.getRequestDispatcher("/WEB-INF/jsp/sign_up.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String clientIp = IPUtils.getClientIp(req);

        String user = req.getParameter("username");
        String pass = req.getParameter("password");

        String requestToken = req.getParameter("csrfToken");
        HttpSession session = req.getSession(false);

        if (session == null) {
            session = req.getSession(true);
            session.setAttribute("flashError", "Your session has expired. Please try again.");
            resp.sendRedirect("sign_up");
            return;
        }

        String sessionToken = (String) session.getAttribute("csrfToken");

        if (sessionToken == null || requestToken == null || !sessionToken.equals(requestToken)) {
            session.setAttribute("flashError", "Invalid form submission. Please try again.");
            resp.sendRedirect("sign_up");
            return;
        }


        // Validar formato de usuario
        if (user == null || !USERNAME_PATTERN.matcher(user).matches()) {
            session.setAttribute("flashError", "Username is invalid. Only letters, numbers, hyphens, and underscores are allowed.");
            resp.sendRedirect("sign_up");
            return;
        }

        // Validar palabras reservadas (sin distinguir mayúsculas/minúsculas)
        if (this.reservedUsernames.contains(user.toLowerCase())) {
            log.warn("Reserved username attempt: IP {} tried to register '{}'", clientIp, user);
            session.setAttribute("flashError", "This username is reserved and cannot be registered.");
            resp.sendRedirect("sign_up");
            return;
        }

        // Validar contraseña
        if (pass == null || pass.length() < 6 || pass.length() > 100) {
            session.setAttribute("flashError", "Password must be between 6 and 100 characters.");
            resp.sendRedirect("sign_up");
            return;
        }

        try (Connection cnRead = ds.getConnection()) {

            // Consultar límite de cuentas
            String countSql = "SELECT COUNT(*) FROM account_creation_logs WHERE ip_address = ? AND created_at >= NOW() - INTERVAL 24 HOUR";
            try (PreparedStatement countStmt = cnRead.prepareStatement(countSql)) {
                countStmt.setString(1, clientIp);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= this.maxAccountsPerDay) {
                        log.warn("RATE LIMIT: IP {} reached maximum accounts per day ({})", clientIp, this.maxAccountsPerDay);
                        session.setAttribute("flashError", "You have reached the maximum number of accounts that can be created in 24 hours.");
                        resp.sendRedirect("sign_up");
                        return;
                    }
                }
            }

            // Comprobar si el usuario ya existe
            try (PreparedStatement chk = cnRead.prepareStatement("SELECT 1 FROM usuarios WHERE username=?")) {
                chk.setString(1, user);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        session.setAttribute("flashError", "Username not available.");
                        resp.sendRedirect("sign_up");
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            throw new ServletException("A database error occurred during validation.", e);
        }

        String hash;
        try {
            hash = BCrypt.hashpw(pass, BCrypt.gensalt());
        } catch (Exception e) {
            throw new ServletException("An unexpected error occurred while securing credentials.", e);
        }

        Connection cnWrite = null;
        try {
            cnWrite = ds.getConnection();
            cnWrite.setAutoCommit(false);

            // --- DOBLE VERIFICACIÓN (Double-Check del Rate Limit) ---
            // Evita que peticiones concurrentes salten el límite tras calcular el hash
            String checkCountSql = "SELECT COUNT(*) FROM account_creation_logs WHERE ip_address = ? AND created_at >= NOW() - INTERVAL 24 HOUR";
            try (PreparedStatement countStmt = cnWrite.prepareStatement(checkCountSql)) {
                countStmt.setString(1, clientIp);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= this.maxAccountsPerDay) {
                        cnWrite.rollback();
                        session.setAttribute("flashError", "You have reached the maximum number of accounts that can be created in 24 hours.");
                        resp.sendRedirect("sign_up");
                        return;
                    }
                }
            }

            // 1. Insertar usuario
            try (PreparedStatement ins = cnWrite.prepareStatement(
                    "INSERT INTO usuarios (username, password, tipo) VALUES (?, ?, 'standard')")) {
                ins.setString(1, user);
                ins.setString(2, hash);
                ins.executeUpdate();
            }

            String logSql = "INSERT INTO account_creation_logs (ip_address, username) VALUES (?, ?)";
            try (PreparedStatement logStmt = cnWrite.prepareStatement(logSql)) {
                logStmt.setString(1, clientIp);
                logStmt.setString(2, user);
                logStmt.executeUpdate();
            }

            String accessLogSql = "INSERT INTO access_logs (ip_address, username, event_type, details) VALUES (?, ?, 'ACCOUNT_CREATED', 'New User Registration')";
            try (PreparedStatement accessLogStmt = cnWrite.prepareStatement(accessLogSql)) {
                accessLogStmt.setString(1, clientIp);
                accessLogStmt.setString(2, user);
                accessLogStmt.executeUpdate();
            }

            cnWrite.commit();

            log.info("SECURITY EVENT: New user registered '{}' from IP {}", user, clientIp);

            // Generar Avatar en Segundo Plano ---
            com.voidchats.media.AvatarGenerator.generateForUserAsync(ds, dicebearApiUrl, profileDir, user);

            session.setAttribute("ok", "User created... log in.");
            resp.sendRedirect("sign_up");

        } catch (SQLException e) {

            if (cnWrite != null) {
                try {

                    if (!cnWrite.isClosed() && !cnWrite.getAutoCommit()) {
                        cnWrite.rollback();
                    }
                } catch (SQLException ex) {
                    log.error("Secondary failure attempting rollback for user {}", user, ex);
                }
            }

            // --- MANEJO DE CONDICIÓN DE CARRERA (TOCTOU) ---

            if ("23000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                // Verificamos que el error sea por el username y no por otra restricción
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("username")) {
                    session.setAttribute("flashError", "Username not available (taken just now).");
                    resp.sendRedirect("sign_up");
                    return;
                }
            }

            log.error("Database writing error during registration for user '{}' from IP {}", user, clientIp, e);
            throw new ServletException("A database error occurred during writing.", e);

        } finally {

            if (cnWrite != null) {
                try {
                    cnWrite.setAutoCommit(true);
                } catch (SQLException e) {
                    log.error("Error resetting autoCommit in SignUpServlet", e);
                }
                try {
                    cnWrite.close();
                } catch (SQLException e) {
                    log.error("Error closing connection in SignUpServlet", e);
                }
            }
        }


    }
}