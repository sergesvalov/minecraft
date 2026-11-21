package com.server.wardenlog.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Thread-safe JSONL event queue with buffered flushing, daily file rotation, and retention cleanup.
 */
public final class EventLogWriter {

    private static final String ARCHIVE_PREFIX = "events-";
    private static final String ARCHIVE_SUFFIX = ".jsonl";

    private final File logFile;
    private final int retentionDays;
    private final ZoneId zone;
    private final Consumer<String> errorLogger;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public EventLogWriter(File logFile, int retentionDays, ZoneId zone, Consumer<String> errorLogger) {
        this.logFile = logFile;
        this.retentionDays = Math.max(1, retentionDays);
        this.zone = zone;
        this.errorLogger = errorLogger;
    }

    public void offer(String jsonLine) {
        queue.offer(jsonLine);
    }

    public synchronized void flush() {
        flush(LocalDate.now(zone));
    }

    /** Flushes queued lines and rotates if {@code logFile} belongs to an earlier date than {@code today}. */
    public synchronized void flush(LocalDate today) {
        if (queue.isEmpty()) {
            return;
        }
        rotateIfNeeded(today);
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter fw = new FileWriter(logFile, StandardCharsets.UTF_8, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            String line;
            while ((line = queue.poll()) != null) {
                out.println(line);
            }
        } catch (IOException e) {
            errorLogger.accept("Failed to write to WardenLog events.jsonl: " + e.getMessage());
        }
    }

    void rotateIfNeeded(LocalDate today) {
        if (!logFile.exists() || logFile.length() == 0) {
            return;
        }
        LocalDate fileDate = Instant.ofEpochMilli(logFile.lastModified()).atZone(zone).toLocalDate();
        if (!fileDate.isBefore(today)) {
            return;
        }

        File dir = logFile.getParentFile();
        File archive = new File(dir, ARCHIVE_PREFIX + fileDate + ARCHIVE_SUFFIX);
        if (archive.exists()) {
            archive = new File(dir, ARCHIVE_PREFIX + fileDate + "-" + System.currentTimeMillis() + ARCHIVE_SUFFIX);
        }
        if (!logFile.renameTo(archive)) {
            errorLogger.accept("Failed to rotate events.jsonl to " + archive.getName());
            return;
        }

        File[] archives = dir.listFiles((d, name) -> name.startsWith(ARCHIVE_PREFIX) && name.endsWith(ARCHIVE_SUFFIX));
        if (archives == null) {
            return;
        }
        LocalDate cutoff = today.minusDays(retentionDays);
        for (File old : archives) {
            String name = old.getName();
            if (name.length() < ARCHIVE_PREFIX.length() + 10) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(name.substring(ARCHIVE_PREFIX.length(), ARCHIVE_PREFIX.length() + 10));
                if (date.isBefore(cutoff) && !old.delete()) {
                    errorLogger.accept("Failed to delete old log " + name);
                }
            } catch (DateTimeParseException ignored) {
                // not one of our archives
            }
        }
    }
}

