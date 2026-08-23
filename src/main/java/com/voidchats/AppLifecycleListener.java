package com.voidchats;

import com.voidchats.admin.IPBlockManager;
import com.voidchats.media.MediaCacheUtil;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class AppLifecycleListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppLifecycleListener.class);

    private ExecutorService videoExecutor;
    private ExecutorService gobblerExecutor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        log.info("Starting application: Configuring shared resources...");

        try {
            // 1. Cargar config.properties (UNA SOLA VEZ)
            Properties props = new Properties();
            try (InputStream input = context.getResourceAsStream("/WEB-INF/config.properties")) {
                if (input == null) {
                    log.error("Critical error: Cannot find /WEB-INF/config.properties.");
                    throw new RuntimeException("Cannot find /WEB-INF/config.properties.");
                }
                props.load(input);
            }

            // 2. Cargar todas las propiedades del archivo

            // --- Propiedades de AppConfigListener ---
            String blockedIpsPath = props.getProperty("path.blocked-ips");
            String honeypotPathsStr = props.getProperty("path.honeypot-paths");
            String baseUrl = props.getProperty("app.baseUrl", "");
            String messagingDir = props.getProperty("path.messaging");
            String profileDir = props.getProperty("path.profile");
            String operatingSystem = props.getProperty("os.environment");
            String videoThreadsStr = props.getProperty("video.processing.threads", "2");
            String maxFileSizeMBStr = props.getProperty("limits.upload.maxFileSizeMB");
            String maxImageSizeMBStr = props.getProperty("limits.upload.image.maxFileSizeMB");
            String maxUserProfileSizeMBStr = props.getProperty("limits.upload.userProfile.maxFileSizeMB");
            String maxAccountsPerDayStr = props.getProperty("limits.accounts.perDay");
            String maxMultimediaMessagesPerDayStr = props.getProperty("limits.multimediaMessages.perDay");
            String maxUniqueRecipientsStr = props.getProperty("limits.messages.uniqueRecipients");
            String maxTotalMessagesStr = props.getProperty("limits.messages.totalPerDay");
            String rateLimitCountStr = props.getProperty("limits.rate.requestLimit");
            String rateLimitStaticCountStr = props.getProperty("limits.rate.staticRequestLimit", "500");
            String rateLimitStrictCountStr = props.getProperty("limits.rate.strictRequestLimit", "15");
            String rateLimitWindowSecStr = props.getProperty("limits.rate.timeWindowSeconds");
            String rateLimitBlockDurationSecStr = props.getProperty("limits.rate.blockDurationSeconds");
            String rateLimitStaticExtStr = props.getProperty("ratelimit.static.extensions", "");
            String rateLimitStaticPathsStr = props.getProperty("ratelimit.static.paths", "");
            String rateLimitStrictPathsStr = props.getProperty("ratelimit.strict.paths", "");
            String maxLoginAttemptsStr = props.getProperty("limits.login.maxAttempts");
            String loginTimeWindowSecStr = props.getProperty("limits.login.timeWindowSeconds");
            String trustedProxiesStr = props.getProperty("server.trustedProxies");
            String maxPasswordChangesPerDayStr = props.getProperty("limits.password.changesPerDay");
            String logIgnoreExtensionsStr = props.getProperty("logfilter.ignore.extensions", "");
            String logIgnorePathsStr = props.getProperty("logfilter.ignore.paths", "");
            String userStatusIgnorePathsStr = props.getProperty("userstatus.ignore.paths", "");
            String wafXssAllowedPathsStr = props.getProperty("waf.xss.allowedPaths", "");
            String wafSqliAllowedPathsStr = props.getProperty("waf.sqli.allowedPaths", "");
            String wafSqliNoBanPathsStr = props.getProperty("waf.sqli.noBanPaths", "");
            String wafBlockMaxAttemptsStr = props.getProperty("waf.block.maxAttempts");
            String wafBlockTimeWindowStr = props.getProperty("waf.block.timeWindowSeconds");
            String adminPathsStr = props.getProperty("security.admin.paths", "");
            String messagingMasterKey = props.getProperty("security.messaging.masterKey");
            String maxMessageLengthStr = props.getProperty("limits.messages.maxLength");
            String maxSearchLengthStr = props.getProperty("limits.search.maxLength");
            String legacyEncryptionSupportStr = props.getProperty("messaging.legacy.encryption.support", "yes");
            String reservedUsernamesStr = props.getProperty("limits.accounts.reservedUsernames", "");

            // --- API DiceBear ---
            String aiDicebearApiUrl = props.getProperty("ai.dicebear.apiUrl", "https://api.dicebear.com/7.x");

            // VALIDACIÓN DE CONFIGURACIÓN CRÍTICA
            if (messagingDir == null || profileDir == null || operatingSystem == null ||
                    blockedIpsPath == null ||
                    rateLimitCountStr == null || rateLimitWindowSecStr == null || rateLimitBlockDurationSecStr == null ||
                    trustedProxiesStr == null || messagingMasterKey == null || maxSearchLengthStr == null ||
                    wafXssAllowedPathsStr == null || adminPathsStr == null || wafSqliAllowedPathsStr == null ||
                    userStatusIgnorePathsStr == null || maxLoginAttemptsStr == null || loginTimeWindowSecStr == null) {

                log.error("Critical startup failure: Essential variables missing in config.properties.");
                throw new RuntimeException("Critical startup failure: Essential variables missing in config.properties.");
            }

            // Parsear valores numéricos de forma SEGURA
            int videoThreads = parseIntSafe(videoThreadsStr, 2);

            // Tamaños de archivo (convertimos MB a bytes), (Por defecto 2MB si están vacíos)
            long maxFileSizeBytes = parseLongSafe(maxFileSizeMBStr, 2) * 1024 * 1024;
            long maxImageFileSizeBytes = parseLongSafe(maxImageSizeMBStr, 2) * 1024 * 1024;
            long maxUserProfileFileSizeBytes = parseLongSafe(maxUserProfileSizeMBStr, 2) * 1024 * 1024;

            int maxAccountsPerDay = parseIntSafe(maxAccountsPerDayStr, 1);
            int maxMultimediaMessagesPerDay = parseIntSafe(maxMultimediaMessagesPerDayStr, 10);
            int maxUniqueRecipients = parseIntSafe(maxUniqueRecipientsStr, 10);
            int maxTotalMessages = parseIntSafe(maxTotalMessagesStr, 50);
            int rateLimitCount = parseIntSafe(rateLimitCountStr, 100);
            int rateLimitStaticCount = parseIntSafe(rateLimitStaticCountStr, 250);
            int rateLimitStrictCount = parseIntSafe(rateLimitStrictCountStr, 15);
            int rateLimitWindowSeconds = parseIntSafe(rateLimitWindowSecStr, 60);
            int rateLimitBlockDurationSeconds = parseIntSafe(rateLimitBlockDurationSecStr, 60);
            int maxLoginAttempts = parseIntSafe(maxLoginAttemptsStr, 5);
            int loginTimeWindowSeconds = parseIntSafe(loginTimeWindowSecStr, 300);
            int maxPasswordChangesPerDay = parseIntSafe(maxPasswordChangesPerDayStr, 1);
            int maxMessageLength = parseIntSafe(maxMessageLengthStr, 1000);
            int maxSearchLength = parseIntSafe(maxSearchLengthStr, 100);
            boolean legacyEncryptionSupport = "1".equals(legacyEncryptionSupportStr) || "yes".equalsIgnoreCase(legacyEncryptionSupportStr);
            int wafBlockMaxAttempts = parseIntSafe(wafBlockMaxAttemptsStr, 5);
            int wafBlockTimeWindowSeconds = parseIntSafe(wafBlockTimeWindowStr, 300);

            // 4. Inicializar servicios (Lógica movida de AppConfigListener)
            IPBlockManager.init(blockedIpsPath);
            IPUtils.init(trustedProxiesStr);

            // --- Cargar lista Honeypot en RAM (Soporta OS path y rutas web relativas) ---
            java.util.Set<String> honeypotSet = new java.util.HashSet<>();
            if (honeypotPathsStr != null && !honeypotPathsStr.isEmpty()) {
                try {
                    String finalHoneypotPath = honeypotPathsStr;
                    String realPath = context.getRealPath(honeypotPathsStr);

                    // Lógica inteligente: Si Tomcat encuentra el archivo dentro de la estructura del proyecto, la usamos.
                    // Si no, asume que es una ruta absoluta del sistema (C:/, /var/...)
                    if (realPath != null && java.nio.file.Files.exists(java.nio.file.Paths.get(realPath))) {
                        finalHoneypotPath = realPath;
                    }

                    java.nio.file.Path honeypotFile = java.nio.file.Paths.get(finalHoneypotPath);
                    if (java.nio.file.Files.exists(honeypotFile)) {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(honeypotFile, java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                                honeypotSet.add(trimmed.toLowerCase());
                            }
                        }
                        log.info("Loaded {} Honeypot paths into memory from: {}", honeypotSet.size(), finalHoneypotPath);
                    } else {
                        log.warn("Honeypot file not found at resolved path: {}", finalHoneypotPath);
                    }
                } catch (Exception e) {
                    log.error("Error reading honeypot file. Proceeding with empty set.", e);
                }
            }
            context.setAttribute("honeypotPathsSet", honeypotSet);
            // -----------------------------------------------------------------------

            context.setAttribute("appBaseUrl", baseUrl);

            log.info("IPBlockManager, IPUtils, and appBaseUrl initialized.");

            // 5. Inicializar recursos pesados (Lógica de AppLifecycleListener)

            // Pool A: Para las tareas de video (larga duración, intensivo en CPU)
            videoExecutor = Executors.newFixedThreadPool(videoThreads);
            // Pool B: Para los StreamGobblers (corta duración, bloqueo de I/O)
            gobblerExecutor = Executors.newCachedThreadPool();

            context.setAttribute("videoExecutor", videoExecutor);
            context.setAttribute("gobblerExecutor", gobblerExecutor);
            log.info("Video processing pool started with {} threads.", videoThreads);
            log.info("Gobbler pool started (cached thread pool).");

            // Crear directorios
            Files.createDirectories(Paths.get(messagingDir));
            Files.createDirectories(Paths.get(messagingDir, "pending"));
            Files.createDirectories(Paths.get(profileDir));
            log.info("Base directories created (messaging, profiles).");


            // Calcular la ruta de FFmpeg
            String ffmpegExecutablePath;
            if ("windows".equalsIgnoreCase(operatingSystem)) {
                ffmpegExecutablePath = context.getRealPath("/WEB-INF/bin/windows/ffmpeg.exe");
            } else if ("linux".equalsIgnoreCase(operatingSystem)) {
                // (lógica de linux)
                ffmpegExecutablePath = context.getRealPath("/WEB-INF/bin/linux/ffmpeg");
                if (ffmpegExecutablePath != null) {
                    File ffmpegFile = new File(ffmpegExecutablePath);
                    if (ffmpegFile.exists() && !ffmpegFile.canExecute()) {
                        ffmpegFile.setExecutable(true);
                    }
                }
            } else {
                log.error("Unsupported operating system specified in config: {}", operatingSystem);
                throw new RuntimeException("Unsupported operating system: " + operatingSystem);
            }

            if (ffmpegExecutablePath == null) {
                log.error("Could not find FFmpeg executable path.");
                throw new RuntimeException("Could not find FFmpeg executable.");
            }

            // Buscar el DataSource (JNDI)
            InitialContext ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/PostDB");

            // --- INICIALIZAR EL MOTOR DE AUDITORÍA ASÍNCRONA ---
            AuditLogger.init(ds);

            // 6. Poner TODO en el ServletContext para que los Servlets lo usen
            context.setAttribute("dbDataSource", ds);
            context.setAttribute("profileDir", profileDir);
            context.setAttribute("messagingDir", messagingDir);
            context.setAttribute("messagingPendingDir", Paths.get(messagingDir, "pending").toString());
            context.setAttribute("ffmpegPath", ffmpegExecutablePath);
            context.setAttribute("maxFileSizeBytes", maxFileSizeBytes);
            context.setAttribute("maxImageFileSizeBytes", maxImageFileSizeBytes);
            context.setAttribute("maxUserProfileFileSizeBytes", maxUserProfileFileSizeBytes);
            context.setAttribute("maxAccountsPerDay", maxAccountsPerDay);
            context.setAttribute("maxMultimediaMessagesPerDay", maxMultimediaMessagesPerDay);
            context.setAttribute("maxUniqueRecipients", maxUniqueRecipients);
            context.setAttribute("maxTotalMessages", maxTotalMessages);
            context.setAttribute("rateLimitCount", rateLimitCount);
            context.setAttribute("rateLimitStaticCount", rateLimitStaticCount);
            context.setAttribute("rateLimitStrictCount", rateLimitStrictCount);
            context.setAttribute("rateLimitWindowSeconds", rateLimitWindowSeconds);
            context.setAttribute("rateLimitBlockDurationSeconds", rateLimitBlockDurationSeconds);
            context.setAttribute("rateLimitStaticExtensions", rateLimitStaticExtStr);
            context.setAttribute("rateLimitStaticPaths", rateLimitStaticPathsStr);
            context.setAttribute("rateLimitStrictPaths", rateLimitStrictPathsStr);
            context.setAttribute("maxLoginAttempts", maxLoginAttempts);
            context.setAttribute("loginTimeWindowSeconds", loginTimeWindowSeconds);
            context.setAttribute("trustedProxies", trustedProxiesStr);
            context.setAttribute("maxPasswordChangesPerDay", maxPasswordChangesPerDay);
            context.setAttribute("logfilter.ignore.extensions", logIgnoreExtensionsStr);
            context.setAttribute("logfilter.ignore.paths", logIgnorePathsStr);
            context.setAttribute("userstatus.ignore.paths", userStatusIgnorePathsStr);
            context.setAttribute("waf.xss.allowedPaths", wafXssAllowedPathsStr);
            context.setAttribute("waf.sqli.allowedPaths", wafSqliAllowedPathsStr);
            context.setAttribute("waf.sqli.noBanPaths", wafSqliNoBanPathsStr);
            context.setAttribute("waf.block.maxAttempts", wafBlockMaxAttempts);
            context.setAttribute("waf.block.timeWindowSeconds", wafBlockTimeWindowSeconds);
            context.setAttribute("security.admin.paths", adminPathsStr);
            AppPrivateConfig.setMessagingMasterKey(messagingMasterKey);
            context.setAttribute("maxMessageLength", maxMessageLength);
            context.setAttribute("maxSearchLength", maxSearchLength);
            context.setAttribute("legacyEncryptionSupport", legacyEncryptionSupport);
            context.setAttribute("reservedUsernames", reservedUsernamesStr);

            // Agregamos el context para DiceBear para que esté disponible en donde se generan perfiles
            context.setAttribute("aiDicebearApiUrl", aiDicebearApiUrl);

            log.info("All shared resources have been successfully configured.");

        } catch (Exception e) {
            log.error("CATASTROPHIC FAILURE: Application could not be initialized.", e);

            if (videoExecutor != null && !videoExecutor.isShutdown()) {
                videoExecutor.shutdownNow();
            }

            if (gobblerExecutor != null && !gobblerExecutor.isShutdown()) {
                gobblerExecutor.shutdownNow();
            }

            throw new RuntimeException("Initialization failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Starting application shutdown sequence...");

        // Limpiar cachés estáticas para evitar Memory Leaks
        try {
            MediaCacheUtil.destruirCache();
        } catch (Exception e) {
            log.error("Error clearing MediaCacheUtil cache during shutdown.", e);
        }

        // (Esta lógica de apagado ya es correcta y maneja el videoExecutor)
        if (videoExecutor != null) {
            log.info("Stopping video processing pool...");
            videoExecutor.shutdown(); // Deshabilita nuevas tareas
            try {
                // Espera 60 segundos a que las tareas actuales terminen
                if (!videoExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    videoExecutor.shutdownNow(); // Cancela tareas en ejecución
                    if (!videoExecutor.awaitTermination(60, TimeUnit.SECONDS))
                        log.error("The video thread pool did not stop.");
                }
            } catch (InterruptedException ie) {
                videoExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Video processing pool stopped.");
        }

        // Apagamos el pool de gobblers de inmediato (son tareas cortas)
        if (gobblerExecutor != null) {
            log.info("Stopping Gobbler pool...");
            gobblerExecutor.shutdownNow();
            log.info("Gobbler pool stopped.");
        }

        // Apagamos el motor de auditoría asíncrona
        try {
            AuditLogger.getInstance().shutdown();
        } catch (Exception e) {
            log.error("Error shutting down AuditLogger.", e);
        }
    }

    // --- MÉTODOS AUXILIARES DE PARSEO SEGURO ---
    private int parseIntSafe(String str, int defaultValue) {
        if (str == null || str.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            log.warn("Warning: Could not parse '{}' to Integer. Using default value: {}", str, defaultValue);
            return defaultValue;
        }
    }

    private long parseLongSafe(String str, long defaultValue) {
        if (str == null || str.trim().isEmpty()) return defaultValue;
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            log.warn("Warning: Could not parse '{}' to Long. Using default value: {}", str, defaultValue);
            return defaultValue;
        }
    }


}