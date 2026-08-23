package com.voidchats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);


    private static AuditLogger instance;

    private final DataSource ds;
    // Usamos un solo hilo (FIFO) para garantizar el orden de los logs sociales en el mismo segundo
    private final ExecutorService executor;

    private AuditLogger(DataSource ds) {
        this.ds = ds;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized void init(DataSource ds) {
        if (instance == null) {
            instance = new AuditLogger(ds);
            log.info("NullChats AuditLogger initialized successfully with async thread pool.");
        }
    }

    public static AuditLogger getInstance() {
        if (instance == null) {
            log.error("CRITICAL: AuditLogger accessed before initialization!");
            throw new IllegalStateException("AuditLogger not initialized");
        }
        return instance;
    }

    /**
     * Registra un evento de auditoría de forma totalmente asíncrona
     */
    public void logAsync(String ip, String username, String eventType, String details) {
        if (executor.isShutdown()) {
            log.warn("Cannot log event, AuditLogger is shutting down.");
            return;
        }

        final String safeDetails = (details != null && details.length() > 250) ? details.substring(0, 247) + "..." : details;

        executor.submit(() -> {
            String sql = "INSERT INTO access_logs (ip_address, username, event_type, details) VALUES (?, ?, ?, ?)";

            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, ip);
                ps.setString(2, username);
                ps.setString(3, eventType);
                ps.setString(4, safeDetails);

                ps.executeUpdate();

            } catch (SQLException e) {
                log.error("Database error saving asynchronous Audit log for IP: {}", ip, e);
            }
        });
    }

    public void shutdown() {
        log.info("Shutting down AuditLogger async pool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of AuditLogger executor...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}