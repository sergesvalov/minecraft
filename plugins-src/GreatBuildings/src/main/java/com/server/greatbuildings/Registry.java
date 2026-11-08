package com.server.greatbuildings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Built buildings, kept in buildings.txt: "id,world,x,y,z" where x y z is the spot in front of the entrance. */
final class Registry {

    record Entry(String id, String world, int x, int y, int z) {
        String serialize() {
            return id + "," + world + "," + x + "," + y + "," + z;
        }

        static Entry parse(String line) {
            String[] p = line.split(",");
            return new Entry(p[0].trim(), p[1].trim(), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                    Integer.parseInt(p[4].trim()));
        }
    }

    private final File file;
    private final Logger logger;
    private final List<Entry> entries = new ArrayList<>();

    Registry(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "buildings.txt");
        this.logger = logger;
        if (!file.exists()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    entries.add(Entry.parse(line));
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.severe("Failed to read buildings.txt: " + e.getMessage());
        }
    }

    List<Entry> all() {
        return List.copyOf(entries);
    }

    void add(Entry entry) {
        entries.add(entry);
        save();
    }

    void remove(Entry entry) {
        entries.remove(entry);
        save();
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# id,world,x,y,z — buildings by GreatBuildings; x y z = in front of the entrance");
        entries.forEach(e -> lines.add(e.serialize()));
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.severe("Failed to write buildings.txt: " + e.getMessage());
        }
    }
}
