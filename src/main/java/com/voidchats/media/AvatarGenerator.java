package com.voidchats.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.sql.DataSource;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AvatarGenerator {

    private static final Logger log = LoggerFactory.getLogger(AvatarGenerator.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Genera un avatar para un Usuario Humano en segundo plano.
     */
    public static void generateForUserAsync(DataSource ds, String apiUrl, String targetDir, String username) {
        CompletableFuture.runAsync(() -> {
            try {
                // En NullChats todos los usuarios son representados como entidades robóticas
                String style = "bottts";
                String avatarUrl = apiUrl + "/" + style + "/png?seed=" + username + "&size=200";

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(avatarUrl)).GET().build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 200) {
                    // VALIDACIÓN DE SEGURIDAD: Comprobamos que la API realmente devolvió una imagen
                    String contentType = response.headers().firstValue("Content-Type").orElse("");

                    if (contentType.toLowerCase().startsWith("image/")) {
                        String fileName = username + "_auto_" + System.currentTimeMillis() + ".png";
                        Path finalPath = Paths.get(targetDir, fileName);

                        // 1. Guardamos en el disco duro
                        Files.copy(response.body(), finalPath, StandardCopyOption.REPLACE_EXISTING);

                        boolean dbSuccess = false;

                        // VALIDACIÓN FÍSICA: Verificar que el archivo no sea un HTML de error o corrupto (< 1KB)
                        if (Files.size(finalPath) < 1024) {
                            log.warn("DiceBear returned a file smaller than 1KB. Assuming proxy error/corruption for user {}.", username);
                            // Dejamos dbSuccess en false para que el bloque de ROLLBACK de abajo borre este archivo corrupto
                        } else {
                            // 2. Intentamos guardar en la Base de Datos
                            try (Connection conn = ds.getConnection();
                                 PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET profile_img = ? WHERE username = ?")) {
                                ps.setString(1, fileName);
                                ps.setString(2, username);
                                int rowsAffected = ps.executeUpdate();

                                // Si afectó al menos 1 fila, el usuario existe y se guardó
                                if (rowsAffected > 0) {
                                    dbSuccess = true;
                                    log.info("Async auto-avatar generated and linked for user: {}", username);
                                }
                            } catch (Exception e) {
                                log.error("Database error linking avatar for user: {}", username, e);
                            }
                        }

                        // 3. ROLLBACK: Si la BD falló o el usuario no existe, borramos la imagen huérfana
                        if (!dbSuccess) {
                            try {
                                Files.deleteIfExists(finalPath);
                                log.warn("Rolled back (deleted) orphaned avatar file for user: {}", username);
                            } catch (IOException ioEx) {
                                log.error("Failed to delete orphaned file: {}", finalPath, ioEx);
                            }
                        }
                    } else {
                        log.warn("DiceBear API returned 200 OK but invalid Content-Type: {}. Avatar creation aborted for user {}.", contentType, username);
                    }
                } else {
                    log.warn("DiceBear API failed with status {} for user {}", response.statusCode(), username);
                }
            } catch (Exception e) {
                log.error("Failed to generate async avatar for user: {}", username, e);
            }
        });
    }

}