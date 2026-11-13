package com.server.greatbuildings;

import com.server.greatbuildings.storage.BuildingRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {

    private static final Logger LOGGER = Logger.getLogger("RegistryTest");

    @Test
    void persistsReloadsAndFindsLatestEntry(@TempDir Path tempDir) {
        BuildingRegistry registry = new BuildingRegistry(tempDir.toFile(), LOGGER);
        assertTrue(registry.all().isEmpty());

        BuildingRegistry.Entry e1 = new BuildingRegistry.Entry("svetitskhoveli", "world", 10, 64, 20);
        BuildingRegistry.Entry e2 = new BuildingRegistry.Entry("paphos", "world_nether", -5, 70, 100);
        BuildingRegistry.Entry e3 = new BuildingRegistry.Entry("svetitskhoveli", "world", 300, 65, -200);
        registry.add(e1);
        registry.add(e2);
        registry.add(e3);

        assertTrue(Files.exists(tempDir.resolve("buildings.txt")));
        BuildingRegistry reloaded = new BuildingRegistry(tempDir.toFile(), LOGGER);
        List<BuildingRegistry.Entry> all = reloaded.all();
        assertEquals(3, all.size());
        assertEquals(e3, reloaded.findLatest("svetitskhoveli").orElseThrow());
        assertEquals(e2, reloaded.findLatest("PAPHOS").orElseThrow());
        assertTrue(reloaded.findLatest("bagrati").isEmpty());

        reloaded.remove(e1);
        BuildingRegistry afterRemove = new BuildingRegistry(tempDir.toFile(), LOGGER);
        assertEquals(2, afterRemove.all().size());
        assertEquals(e2, afterRemove.all().get(0));
    }

    @Test
    void skipsMalformedLinesWithoutDroppingValidEntries(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("buildings.txt");
        Files.writeString(file, "# comment\n\ninvalid,line\npaphos,world,1,2,3\nbadnum,world,x,2,3\nbagrati,world,4,5,6\n");
        BuildingRegistry registry = new BuildingRegistry(tempDir.toFile(), LOGGER);
        assertEquals(2, registry.all().size());
        assertEquals("paphos", registry.all().get(0).id());
        assertEquals("bagrati", registry.all().get(1).id());
    }
}
