package com.voidchats.media;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.voidchats.IPUtils;

@WebServlet("/profile-img")
public class ProfileImageServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ProfileImageServlet.class);

    private String profileDir;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.profileDir = (String) context.getAttribute("profileDir");

        if (this.profileDir == null) {
            log.error("Critical failure: 'profileDir' not found in context for ProfileImageServlet.");
            throw new ServletException("Critical failure: 'profileDir' not found in context for ProfileImageServlet.");
        }

        log.info("ProfileImageServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String fileName = req.getParameter("file");

        if (fileName == null || fileName.trim().isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "File name parameter is missing.");
            return;
        }

        if (fileName.length() > 255) {
            String clientIp = IPUtils.getClientIp(req);
            log.warn("DoS SHIELD: Blocked extremely long file name request in ProfileImageServlet (length: {}) from IP: {}", fileName.length(), clientIp);
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "File name is too long.");
            return;
        }

        File baseDirFile = new File(profileDir);
        File file = new File(baseDirFile, fileName);

        String canonicalBaseDir = baseDirFile.getCanonicalPath();
        String canonicalFile = file.getCanonicalPath();

        if (!canonicalFile.startsWith(canonicalBaseDir)) {
            String clientIp = IPUtils.getClientIp(req);
            log.warn("SECURITY: Directory traversal attempt blocked for file: '{}' from IP: {}", fileName, clientIp);
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied.");
            return;
        }

        if (!file.exists() || !file.isFile()) {
            file = new File(profileDir, "default_profile.jpg");
            if (!file.exists() || !file.isFile()) {
                log.error("SYSTEM CONFIG ERROR: Default profile fallback image 'default_profile.jpg' is missing from directory: {}", profileDir);
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Image not found");
                return;
            }
        }

        // Pasamos explícitamente 'false' (público) para de foto perfil
        if (MediaCacheUtil.processCacheHeaders(req, resp, file, false)) {
            // El navegador ya tiene la versión más reciente en su memoria.
            // Respondemos HTTP 304 y evitamos leer el archivo de nuevo.
            return;
        }

        String mime = getServletContext().getMimeType(file.getName());
        if (mime == null) {
            mime = "application/octet-stream";
        }
        resp.setContentType(mime);
        resp.setContentLengthLong(file.length());

        try (OutputStream out = resp.getOutputStream()) {
            java.nio.file.Files.copy(file.toPath(), out);
        }
    }
}
