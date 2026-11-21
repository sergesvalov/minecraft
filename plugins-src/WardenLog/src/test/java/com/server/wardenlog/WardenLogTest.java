package com.server.wardenlog;

import com.server.wardenlog.log.EventJson;
import com.server.wardenlog.log.EventLogWriter;
import com.server.wardenlog.tracking.CommandFilter;
import com.server.wardenlog.tracking.RateTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WardenLogTest {

    @Test
    void eventJsonEscapesControlCharsAndFormatsCoordinates() {
        assertEquals("null", EventJson.escape(null));
        assertEquals("a\\\"b\\\\c\\nd\\te\\u0001", EventJson.escape("a\"b\\c\nd\te\u0001"));

        String xyzLine = EventJson.of("place_tnt")
                .field("player", "papa")
                .xyz(10, 64, -5, "world")
                .build();
        assertTrue(xyzLine.startsWith("{\"timestamp\":\""));
        assertTrue(xyzLine.contains("\"event\":\"place_tnt\""));
        assertTrue(xyzLine.contains("\"player\":\"papa\""));
        assertTrue(xyzLine.contains("\"x\":10, \"y\":64, \"z\":-5, \"world\":\"world\""));
        assertTrue(xyzLine.endsWith("}"));

        String xzLine = EventJson.of("high_entity_count")
                .field("count", 180)
                .xz(32, -48, "world_nether")
                .build();
        assertTrue(xzLine.contains("\"count\":180, \"x\":32, \"z\":-48, \"world\":\"world_nether\""));
        assertFalse(xzLine.contains("\"y\":"));
    }

    @Test
    void commandFilterStripsNamespacesAndHandlesEdgeCases() {
        assertEquals("", CommandFilter.normalize(null));
        assertEquals("", CommandFilter.normalize("   "));
        assertEquals("say hi", CommandFilter.normalize(" say hi "));
        assertEquals("/op steve", CommandFilter.normalize(" /Minecraft:OP steve "));

        assertFalse(CommandFilter.isSuspicious(null));
        assertFalse(CommandFilter.isSuspicious("   "));
        assertFalse(CommandFilter.isSuspicious("op steve"));
        assertTrue(CommandFilter.isSuspicious("/op steve"));
        assertTrue(CommandFilter.isSuspicious("/minecraft:ban griefer"));
        assertTrue(CommandFilter.isSuspicious("//set 0"));
        assertTrue(CommandFilter.isSuspicious("//replace dirt stone"));
        assertFalse(CommandFilter.isSuspicious("/home"));
        assertFalse(CommandFilter.isSuspicious("/open"));
        assertFalse(CommandFilter.isSuspicious("/gb build temple"));
    }

    @Test
    void rateTrackerEnforcesGriefWindowAndCooldowns() {
        RateTracker tracker = new RateTracker();
        long t = 1_000_000L;
        for (int i = 0; i < 45; i++) {
            assertEquals(0, tracker.recordBreak("alex", t + i * 10L, 45));
        }
        assertEquals(46, tracker.recordBreak("alex", t + 500L, 45));
        // Cooldown suppresses immediate repeat alert
        assertEquals(0, tracker.recordBreak("alex", t + 1_000L, 45));

        // After 60s cooldown expires, prior breaks are evicted and a new burst of 46 triggers 46 again
        long t2 = t + 65_000L;
        for (int i = 0; i < 45; i++) {
            assertEquals(0, tracker.recordBreak("alex", t2 + i * 10L, 45));
        }
        assertEquals(46, tracker.recordBreak("alex", t2 + 500L, 45));

        // Piston limit + 5-min cooldown + prune recovery
        for (int i = 0; i < 5; i++) {
            assertFalse(tracker.recordPiston(42L, t + i, 5));
        }
        assertTrue(tracker.recordPiston(42L, t + 10, 5));
        tracker.clearPistons();
        // Still inside 5-min cooldown
        assertFalse(tracker.recordPiston(42L, t + 20, 5));

        // After 5-min cooldown expires and prune cleans state, a new burst triggers again
        long t3 = t + 400_000L;
        tracker.prune(t3);
        tracker.clearPistons();
        for (int i = 0; i < 5; i++) {
            assertFalse(tracker.recordPiston(42L, t3 + i, 5));
        }
        assertTrue(tracker.recordPiston(42L, t3 + 10, 5));
    }

    @Test
    void eventLogWriterFlushesRotatesAndCleansRetention(@TempDir Path dir) throws Exception {
        List<String> errors = new ArrayList<>();
        File logFile = dir.resolve("events.jsonl").toFile();
        File oldArchive = dir.resolve("events-2026-11-01.jsonl").toFile();
        Files.writeString(oldArchive.toPath(), "{\"old\":true}\n");

        EventLogWriter writer = new EventLogWriter(logFile, 7, ZoneOffset.UTC, errors::add);
        writer.offer("{\"event\":\"day1\"}");
        LocalDate day1 = LocalDate.of(2026, 11, 20);
        writer.flush(day1);
        assertTrue(logFile.exists());

        // Simulate yesterday's mtime on events.jsonl so flushing on day2 triggers rotation
        Files.setLastModifiedTime(logFile.toPath(),
                FileTime.from(day1.atStartOfDay().toInstant(ZoneOffset.UTC)));
        writer.offer("{\"event\":\"day2\"}");
        writer.flush(LocalDate.of(2026, 11, 21));

        assertTrue(errors.isEmpty());
        assertTrue(Files.exists(dir.resolve("events-2026-11-20.jsonl")));
        assertFalse(oldArchive.exists());
        assertEquals("{\"event\":\"day2\"}\n", Files.readString(logFile.toPath()).replace("\r\n", "\n"));
    }
}
