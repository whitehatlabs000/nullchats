package com.voidchats.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.voidchats.IPUtils;
import com.voidchats.admin.IPBlockManager;
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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);

    private static final SecureRandom secureRandom = new SecureRandom();

    private DataSource ds;
    private String appBaseUrl;
    private int maxLoginAttempts;
    private int loginTimeWindowSeconds;
    private Cache<String, LoginAttempt> attemptsCache;

    private static class LoginAttempt {
        int count;
        long firstAttemptTime;

        LoginAttempt(int count, long firstAttemptTime) {
            this.count = count;
            this.firstAttemptTime = firstAttemptTime;
        }
    }

    @Override
    public void init() throws ServletException {
        try {
            ServletContext context = getServletContext();
            this.ds = (DataSource) context.getAttribute("dbDataSource");

            Integer maxAttempts = (Integer) context.getAttribute("maxLoginAttempts");
            Integer windowSec = (Integer) context.getAttribute("loginTimeWindowSeconds");

            this.appBaseUrl = (String) context.getAttribute("appBaseUrl");
            if (this.appBaseUrl == null) this.appBaseUrl = "";

            if (this.ds == null) {
                log.error("Critical failure: DataSource not found in context for LoginServlet.");
                throw new ServletException("DataSource not found in context.");
            }

            this.maxLoginAttempts = (maxAttempts != null) ? maxAttempts : 5;
            this.loginTimeWindowSeconds = (windowSec != null) ? windowSec : 300;

            this.attemptsCache = Caffeine.newBuilder()
                    .expireAfterWrite(this.loginTimeWindowSeconds + 60, TimeUnit.SECONDS)
                    .maximumSize(10_000)
                    .build();

            log.info("LoginServlet initialized successfully.");

        } catch (Exception e) {
            log.error("Failed to initialize LoginServlet", e);
            throw new ServletException("Error initializing LoginServlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            // Si el usuario ya está logueado y por historial vuelve a caer en /login,
            // lo mandamos a la portada ("/") en lugar de empujarlo de nuevo a /messaging.
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }

        if (session == null) {
            session = req.getSession(true);
        }

        String flashError = (String) session.getAttribute("flashError");

        if (flashError != null) {
            req.setAttribute("error", flashError);
            session.removeAttribute("flashError");
        }

        String csrfToken = generateCSRFToken();
        session.setAttribute("csrfToken", csrfToken);
        req.setAttribute("csrfToken", csrfToken);

        String referrer = req.getHeader("Referer");

        // Evaluamos si el referer es la página de inicio en sus diferentes variantes
        boolean isHomePage = referrer != null && (
                referrer.endsWith(this.appBaseUrl) ||
                        referrer.endsWith(this.appBaseUrl + "/") ||
                        referrer.contains("/index") ||
                        referrer.contains("/home")
        );

        // Solo guardamos la redirección si NO venimos del login, sign_up, ni de la portada
        if (referrer != null &&
                !referrer.contains("/login") &&
                !referrer.contains("/sign_up") &&
                !isHomePage &&
                isValidReferrer(referrer, req)) {

            session.setAttribute("loginRedirectUrl", referrer);
        } else {
            session.removeAttribute("loginRedirectUrl");
        }

        req.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String ipAddress = IPUtils.getClientIp(req);

        LoginAttempt existingAttempt = attemptsCache.getIfPresent(ipAddress);
        if (existingAttempt != null) {
            long now = System.currentTimeMillis();
            if ((now - existingAttempt.firstAttemptTime <= loginTimeWindowSeconds * 1000L) &&
                    existingAttempt.count >= maxLoginAttempts) {

                log.warn("BRUTE FORCE BLOCKED: IP {} permanently blocked due to too many failed login attempts (Pre-check).", ipAddress);
                IPBlockManager.addIP(ipAddress);
                attemptsCache.invalidate(ipAddress);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Too many failed attempts. IP Blocked.");
                return;
            }
        }

        String user = req.getParameter("username");
        String pass = req.getParameter("password");
        String token = req.getParameter("csrfToken");

        HttpSession session = req.getSession(false);
        if (session == null) {
            session = req.getSession(true);
            session.setAttribute("flashError", "Session expired. Please try again.");
            resp.sendRedirect("login");
            return;
        }

        String sessionToken = (String) session.getAttribute("csrfToken");
        if (token == null || !token.equals(sessionToken)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
            return;
        }

        if (user == null || user.length() > 50 || pass == null || pass.length() > 100) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid data.");
            return;
        }

        try {
            UserResult result = validate(user, pass);

            if (result != null) {

                logLoginAttempt(result.username, ipAddress, true);

                attemptsCache.invalidate(ipAddress);

                // Lógica de Sesión

                String redirectUrl = (String) session.getAttribute("loginRedirectUrl");

                session.invalidate();

                session = req.getSession(true);

                // Añadimos los datos del usuario autenticado
                session.setAttribute("user", result.username);
                session.setAttribute("userId", result.id);
                session.setAttribute("tipoUsuario", result.tipo);
                session.setAttribute("profileImg", result.profileImg);
                session.setAttribute("csrfToken", generateCSRFToken());

                // Usamos los datos que guardamos para redirigir
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    // No es necesario remover "loginRedirectUrl", la sesión vieja fue destruida
                    resp.sendRedirect(redirectUrl);
                } else {
                    resp.sendRedirect("messaging");
                }

            } else {

                logLoginAttempt(user, ipAddress, false);

                LoginAttempt updatedStats = attemptsCache.asMap().compute(ipAddress, (key, val) -> {
                    long now = System.currentTimeMillis();

                    if (val == null) {
                        return new LoginAttempt(1, now);
                    }

                    if (now - val.firstAttemptTime > (loginTimeWindowSeconds * 1000L)) {
                        return new LoginAttempt(1, now);
                    }

                    val.count++;
                    return val;
                });

                if (updatedStats.count >= maxLoginAttempts) {
                    log.warn("BRUTE FORCE BAN: IP {} permanently banned after {} failed login attempts.", ipAddress, updatedStats.count);
                    IPBlockManager.addIP(ipAddress);
                    attemptsCache.invalidate(ipAddress);
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Too many failed attempts. IP Blocked.");
                    return;
                }

                session.setAttribute("flashError", "Invalid credentials. Please try again.");
                resp.sendRedirect("login");
            }

        } catch (Exception e) {
            log.error("Unexpected error during login process for IP {}", ipAddress, e);
            throw new ServletException("Internal Server Error during login", e);
        }
    }

    private class UserResult {
        Integer id;
        String tipo;
        String username;
        String profileImg;
    }

    private UserResult validate(String user, String pass) throws Exception {
        String sql = "SELECT id, username, password, tipo, profile_img FROM usuarios WHERE LOWER(username) = LOWER(?) AND active=1";

        try (Connection cn = ds.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password");
                    boolean passwordMatches = hash.startsWith("$2") ?
                            BCrypt.checkpw(pass, hash) :
                            hash.equals(sha256(pass));

                    if (passwordMatches) {
                        UserResult result = new UserResult();
                        result.id = rs.getInt("id");
                        result.tipo = rs.getString("tipo");
                        result.username = rs.getString("username");

                        // Guardamos la imagen. Si es null o vacía en la BD, asignamos la por defecto.
                        String img = rs.getString("profile_img");
                        result.profileImg = (img != null && !img.trim().isEmpty()) ? img : "default_profile.jpg";

                        return result;
                    }
                }
            }
        }
        return null;
    }

    private void logLoginAttempt(String username, String ipAddress, boolean success) {
        String eventType = success ? "LOGIN_SUCCESS" : "LOGIN_FAIL";
        String sql = "INSERT INTO access_logs (ip_address, username, event_type, details) VALUES (?, ?, ?, ?)";

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            ps.setString(2, username);
            ps.setString(3, eventType);
            ps.setString(4, "Login attempt for user: " + username);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Database error while logging login attempt for user '{}' from IP {}", username, ipAddress, e);
        }
    }

    private String sha256(String str) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }


    private boolean isValidReferrer(String referrer, HttpServletRequest req) {
        if (referrer == null || referrer.isEmpty()) return false;

        StringBuilder serverBase = new StringBuilder();
        serverBase.append(req.getScheme()).append("://").append(req.getServerName());

        int port = req.getServerPort();
        if (port != 80 && port != 443) {
            serverBase.append(":").append(port);
        }

        serverBase.append(this.appBaseUrl);
        return referrer.startsWith(serverBase.toString());
    }

    private String generateCSRFToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}