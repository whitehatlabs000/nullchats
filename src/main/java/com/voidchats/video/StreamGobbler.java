package com.voidchats.video;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class consumes an InputStream in a separate thread.
 * This is crucial to prevent external processes (like FFmpeg)
 * from blocking when their output buffers (stdout/stderr) fill up.
 */
public class StreamGobbler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StreamGobbler.class);

    private InputStream inputStream;
    private Consumer<String> consumer;

    /**
     * @param inputStream The stream to consume (e.g., process.getInputStream())
     * @param consumer    A function to process each line (e.g., log::debug)
     */
    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Pass the line to the consumer (e.g., log::debug)
                consumer.accept(line);
            }
        } catch (Exception e) {
            // If there is an error reading the stream (e.g., the process is killed abruptly)
            // we log the error and let the thread terminate cleanly.
            log.error("Error in StreamGobbler", e);
        }
    }
}