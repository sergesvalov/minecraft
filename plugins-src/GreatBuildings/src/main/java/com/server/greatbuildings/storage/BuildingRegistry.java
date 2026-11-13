package com.server.greatbuildings.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Persists built buildings in {@code buildings.txt} using the CSV format
 * {@code id,world,x,y,z} where {@code x y z} is the anchor spot in front of the entrance.
 */
public final class BuildingRegistry {

    public record Entry(String id, String world, int x, int y, int z) {
        public String serialize() {
            return id + "," + world + "," + x + "," + y + "," + z;
        }

        public static Entry parse(String line) {
            String[] p = line.split(",");
            if (p.length != 5) {
                throw new IllegalArgumentException("Expected 5 comma-separated fields");
            }
            return new Entry(
                    p[0].trim(),
                    p[1].trim(),
                    Integer.parseInt(p[2].trim()),
                    Integer.parseInt(p[3].trim()),
                    Integer.parseInt(p[4].trim())
            );
        }
    }

    private final File file;
    private final Logger logger;
    private final List<Entry> entries = new ArrayList<>();

    public BuildingRegistry(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "buildings.txt");
        this.logger = logger;
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                try {
                    entries.add(Entry.parse(line));
                } catch (RuntimeException e) {
                    logger.warning("Skipping malformed line " + (i + 1) + " in buildings.txt: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to read buildings.txt: " + e.getMessage());
        }
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public Optional<Entry> findLatest(String id) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.id().equalsIgnoreCase(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public void add(Entry entry) {
        entries.add(entry);
        save();
    }

    public void remove(Entry entry) {
        if (entries.remove(entry)) {
            save();
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>(entries.size() + 1);
        lines.add("# id,world,x,y,z — buildings by GreatBuildings; x y z = in front of the entrance");
        for (Entry entry : entries) {
            lines.add(entry.serialize());
        }
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.severe("Failed to write buildings.txt: " + e.getMessage());
        }
    }
}

