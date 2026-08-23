package com.voidchats.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPBlockManager {

    private static final Logger log = LoggerFactory.getLogger(IPBlockManager.class);

    private static Path blockFilePath;
    private static Set<String> cachedIPs = ConcurrentHashMap.newKeySet();

    public static synchronized void init(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            log.error("Critical failure: IPBlockManager initialization path cannot be null or empty.");
            throw new IllegalArgumentException("Path cannot be null.");
        }
        blockFilePath = Paths.get(path);

        Path parentDir = blockFilePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        if (Files.exists(blockFilePath)) {
            java.util.List<String> lines = Files.readAllLines(blockFilePath, StandardCharsets.UTF_8);

            Set<String> tempIPs = ConcurrentHashMap.newKeySet();
            for(String line : lines) {
                if(!line.trim().isEmpty()) tempIPs.add(line.trim());
            }

            cachedIPs = tempIPs;
        }
        log.info("IPBlockManager initialized successfully. Loaded {} blocked IPs into cache.", cachedIPs.size());
    }

    private static void checkInitialized() {
        if (blockFilePath == null) {
            log.error("Attempted to use IPBlockManager before initialization.");
            throw new IllegalStateException("IPBlockManager not initialized.");
        }
    }

    public static Set<String> getBlockedIPs() {
        checkInitialized();
        return cachedIPs;
    }

    public static synchronized void addIP(String ip) throws IOException {
        checkInitialized();
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        if (!isValidIP(ip)) {
            log.warn("SECURITY WARNING: Attempted to add an invalid IP address format to blocklist: '{}'", ip);
            return;
        }
        String cleanIp = ip.trim();

        // Verificamos en memoria primero (rápido)
        if (!cachedIPs.contains(cleanIp)) {
            // 1. Actualizamos memoria
            cachedIPs.add(cleanIp);
            // 2. Persistimos en disco
            saveToDisk();
            log.info("IP address successfully added to blocklist: {}", cleanIp);
        } else {
            log.debug("IP address already in blocklist: {}", cleanIp);
        }
    }

    public static synchronized void removeIP(String ip) throws IOException {
        checkInitialized();
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        String cleanIp = ip.trim();

        boolean removed = cachedIPs.remove(cleanIp);

        if (removed) {
            saveToDisk();
            log.info("IP address successfully removed from blocklist: {}", cleanIp);
        } else {
            log.debug("Attempted to remove IP not in blocklist: {}", cleanIp);
        }
    }

    private static void saveToDisk() throws IOException {
        Files.write(blockFilePath, cachedIPs, StandardCharsets.UTF_8);
    }

    private static final java.util.regex.Pattern IPV4_PATTERN =
            java.util.regex.Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$");

    // Regex básico para IPv6 (Evita inyección de letras que activen resoluciones DNS)
    private static final java.util.regex.Pattern IPV6_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F:]+$");

    private static boolean isValidIP(String ip) {
        if (ip == null || ip.length() > 45) return false;
        String clean = ip.trim();
        return IPV4_PATTERN.matcher(clean).matches() || IPV6_PATTERN.matcher(clean).matches();
    }
}