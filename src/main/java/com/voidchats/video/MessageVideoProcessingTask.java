package com.voidchats.video;



import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class MessageVideoProcessingTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MessageVideoProcessingTask.class);

    private final long messageId;
    private final String persistentTempFileName;
    private final DataSource ds;
    private final String messagingDir;
    private final String pendingDir;
    private final String ffmpegPath;
    private final ExecutorService executor;


    public MessageVideoProcessingTask(long messageId, String persistentTempFileName, DataSource ds,
                                      String messagingDir, String pendingDir, String ffmpegPath,
                                      ExecutorService executor) {
        this.messageId = messageId;
        this.persistentTempFileName = persistentTempFileName;
        this.ds = ds;
        this.messagingDir = messagingDir;
        this.pendingDir = pendingDir;
        this.ffmpegPath = ffmpegPath;
        this.executor = executor;
    }

    @Override
    public void run() {
        log.info("WORKER (Messaging): Starting processing for message ID: {}", messageId);
        Path tempVideoPath = Paths.get(pendingDir, persistentTempFileName);
        Path finalVideoPath = Paths.get(messagingDir, persistentTempFileName);

        // Generar nombre de miniatura
        String thumbnailFileName = UUID.randomUUID() + "_preview.jpg";
        Path thumbnailPath = Paths.get(messagingDir, thumbnailFileName);

        String finalError = null;

        try {
            if (!Files.exists(tempVideoPath)) {
                throw new IOException("Temp file does not exist: " + tempVideoPath);
            }


            ProcessBuilder pbOptimize = new ProcessBuilder(
                    ffmpegPath, "-i", tempVideoPath.toString(),
                    "-c", "copy", "-movflags", "+faststart", finalVideoPath.toString()
            );
            Process processOptimize = pbOptimize.start();

            // --- USA EL POOL B (GOBBLER) ---
            StreamGobbler stdOutGobblerOpt = new StreamGobbler(processOptimize.getInputStream(), log::debug);
            StreamGobbler stdErrGobblerOpt = new StreamGobbler(processOptimize.getErrorStream(), log::debug);
            this.executor.submit(stdOutGobblerOpt);
            this.executor.submit(stdErrGobblerOpt);


            int exitCodeOptimize = processOptimize.waitFor();

            if (exitCodeOptimize != 0) {
                throw new IOException("FFmpeg (optimize) failed with exit code: " + exitCodeOptimize);
            }

            // --- Generar la vista previa ---
            ProcessBuilder pbThumbnail = new ProcessBuilder(
                    ffmpegPath, "-i", finalVideoPath.toString(),
                    "-ss", "00:00:01.000", "-vframes", "1",
                    "-vf", "scale=320:-1",
                    thumbnailPath.toString()
            );
            Process processThumbnail = pbThumbnail.start();

            StreamGobbler stdOutGobblerThumb = new StreamGobbler(processThumbnail.getInputStream(), log::debug);
            StreamGobbler stdErrGobblerThumb = new StreamGobbler(processThumbnail.getErrorStream(), log::debug);
            this.executor.submit(stdOutGobblerThumb);
            this.executor.submit(stdErrGobblerThumb);


            int exitCodeThumbnail = processThumbnail.waitFor();

            if (exitCodeThumbnail != 0) {
                log.warn("WORKER (Messaging): FFmpeg (thumbnail) failed for message ID: {}", messageId);
                thumbnailFileName = null; // La miniatura falló, pero el video está bien
            }


            log.info("WORKER (Messaging): Processing successful for message ID: {}", messageId);
            updateDatabase("COMPLETE", thumbnailFileName, null);

        } catch (Exception e) {
            log.error("WORKER (Messaging): Catastrophic failure processing message ID: {}", messageId, e);
            finalError = e.getMessage();

            updateDatabase("FAILED", null, finalError);

            // Limpieza de archivos fallidos (si se crearon)
            try { Files.deleteIfExists(finalVideoPath); } catch (IOException io) {}
            try { Files.deleteIfExists(thumbnailPath); } catch (IOException io) {}
        } finally {
            // Limpieza final del archivo temporal original
            try {
                Files.deleteIfExists(tempVideoPath);
                log.debug("WORKER (Messaging): Cleaned up temp file: {}", tempVideoPath);
            } catch (IOException e) {
                log.warn("WORKER (Messaging): Could not delete temp file: {}", tempVideoPath);
            }
        }
    }

    private void updateDatabase(String status, String finalThumbnailName, String errorMessage) {
        // Actualiza el estado, la miniatura y el error
        String sql = "UPDATE messages SET status = ?, preview_file = ?, processing_error = ? WHERE id = ?";

        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);

            if (finalThumbnailName != null) {
                stmt.setString(2, finalThumbnailName);
            } else {
                stmt.setNull(2, java.sql.Types.VARCHAR);
            }

            if (errorMessage != null) {
                String truncatedError = errorMessage.length() > 255 ? errorMessage.substring(0, 255) : errorMessage;
                stmt.setString(3, truncatedError);
            } else {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            }

            stmt.setLong(4, messageId);

            stmt.executeUpdate();
            log.debug("WORKER (Messaging): Database updated for message ID: {} to status: {}", messageId, status);
        } catch (SQLException e) {
            log.error("WORKER (Messaging): CRITICAL ERROR!! Could not update DB for message ID: {}", messageId, e);
        }
    }
}