package com.voidchats.media;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaCacheUtil {

    private static final Logger log = LoggerFactory.getLogger(MediaCacheUtil.class);

    private static final int CACHE_MAX_AGE = 300;

    // Caché en memoria altamente optimizada y concurrente para los ETags
    private static final Cache<String, String> ETAG_CACHE = Caffeine.newBuilder()
            .maximumSize(5000) // Mantiene los ETags de los últimos 5000 archivos más solicitados
            .expireAfterAccess(24, TimeUnit.HOURS) // Libera la RAM si un archivo no se pide en 24hs
            .build();

    // Por defecto asume que el contenido es PÚBLICO (false)
    public static boolean processCacheHeaders(HttpServletRequest req, HttpServletResponse resp, File file) {
        return processCacheHeaders(req, resp, file, false);
    }

    public static boolean processCacheHeaders(HttpServletRequest req, HttpServletResponse resp, File file, boolean isPrivate) {
        long lastModified = file.lastModified();

        // Llave compuesta (Ruta + LastModified) para obligar a recalcular si el archivo cambia en disco
        String cacheKey = file.getAbsolutePath() + "_" + lastModified;
        String eTag = ETAG_CACHE.get(cacheKey, k -> generateStrongETag(file.getName(), file.length(), lastModified));


        String ifNoneMatch = req.getHeader("If-None-Match");
        if (ifNoneMatch != null && (ifNoneMatch.contains(eTag) || ifNoneMatch.trim().equals("*"))) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return true;
        }

        long ifModifiedSince = -1;
        try {
            ifModifiedSince = req.getDateHeader("If-Modified-Since");
        } catch (IllegalArgumentException e) {
            // Ignorar cabecera malformada enviada por atacante/crawler
        }

        if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince >= (lastModified / 1000 * 1000)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return true;
        }

        // Establecer nuevas cabeceras
        resp.setHeader("ETag", eTag);
        resp.setDateHeader("Last-Modified", lastModified);
        String cacheType = isPrivate ? "private" : "public";
        resp.setHeader("Cache-Control", cacheType + ", max-age=" + CACHE_MAX_AGE + ", must-revalidate");

        return false;
    }


    private static String generateStrongETag(String fileName, long length, long lastModified) {
        try {
            String source = fileName + "-" + length + "-" + lastModified;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "\"" + hexString.toString() + "\"";
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 algorithm not found for ETag generation. Falling back to plain string for file: {}", fileName);
            // Fallback sin W/ para no romper If-Range
            return "\"" + fileName + "-" + length + "-" + lastModified + "\"";
        }
    }

    public static void destruirCache() {
        ETAG_CACHE.invalidateAll();
        ETAG_CACHE.cleanUp();
        log.info("MediaCacheUtil static Caffeine cache cleared to prevent memory leaks.");
    }
}