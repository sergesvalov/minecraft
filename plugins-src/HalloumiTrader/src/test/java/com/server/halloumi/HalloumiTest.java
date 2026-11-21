package com.server.halloumi;

import com.server.halloumi.model.Dish;
import com.server.halloumi.model.Tavern;
import com.server.halloumi.model.TavernPalette;
import com.server.halloumi.tavern.TavernLayout;
import com.server.halloumi.tavern.TavernStorage;
import com.server.halloumi.trade.TraderService;
import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HalloumiTest {

    @Test
    void tavernSerializeParseAndMalformedRejection() {
        for (Tavern.State state : Tavern.State.values()) {
            Tavern t = new Tavern("world", -120, 68, 340, state == Tavern.State.BUILT, state);
            Tavern parsed = Tavern.parse(t.serialize());
            assertEquals(t, parsed);
        }

        // Legacy pre-1.4 formats without state or with old codes map cleanly to OLD variants
        Tavern legacyOld = Tavern.parse("world,10,64,-20");
        assertEquals(Tavern.State.OLD, legacyOld.state());
        assertFalse(legacyOld.decorated());

        Tavern legacyEast = Tavern.parse("world,10,64,-20,true,east");
        assertEquals(Tavern.State.OLD_EAST, legacyEast.state());
        assertTrue(legacyEast.decorated());
        assertTrue(legacyEast.state().old());

        assertThrows(IllegalArgumentException.class, () -> Tavern.parse("world,10,64"));
        assertThrows(NumberFormatException.class, () -> Tavern.parse("world,x,64,10"));
    }

    @Test
    void tavernGeometryHelpers() {
        Tavern pending = new Tavern("world", 32, 60, 64, false, Tavern.State.PENDING);
        assertEquals((32 + TavernLayout.SIZE_X / 2) >> 4, pending.chunkX());
        assertEquals((64 + TavernLayout.SIZE_Z / 2) >> 4, pending.chunkZ());
        assertEquals(32 + TavernLayout.SIZE_X / 2.0, pending.centerX());
        assertEquals(64 + TavernLayout.SIZE_Z / 2.0, pending.centerZ());

        Tavern built = pending.built(70);
        assertEquals(Tavern.State.BUILT, built.state());
        assertEquals(70, built.y());
        assertFalse(built.decorated());
        assertTrue(built.withDecorated().decorated());
    }

    @Test
    void tavernStorageRoundTripAndSkipsMalformedLines(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("taverns.txt");
        Files.writeString(file, """
                # header
                world,10,64,20,true,inn
                bad,line
                world,50,70,80,false,inn-pending
                """);

        TavernStorage storage = new TavernStorage(file.toFile(), Logger.getLogger("test"));
        List<Tavern> loaded = storage.load();
        assertEquals(2, loaded.size());
        assertEquals(Tavern.State.BUILT, loaded.get(0).state());
        assertEquals(Tavern.State.PENDING, loaded.get(1).state());

        assertTrue(storage.save(loaded));
        List<Tavern> reloaded = storage.load();
        assertEquals(loaded, reloaded);
    }

    @Test
    void paletteBiomeAndUniqueWallLookup() {
        assertEquals(TavernPalette.OAK, TavernPalette.forBiome("plains"));
        assertEquals(TavernPalette.SANDSTONE, TavernPalette.forBiome("desert"));
        assertNull(TavernPalette.forBiome("ocean"));

        Set<Material> walls = new HashSet<>();
        for (TavernPalette palette : TavernPalette.values()) {
            assertTrue(walls.add(palette.wall), "Duplicate wall material in palette " + palette);
            assertEquals(palette, TavernPalette.byWall(palette.wall));
        }
        assertNull(TavernPalette.byWall(Material.DIAMOND_BLOCK));
    }

    @Test
    void dishParseColorSupportsOptionalHash() {
        assertEquals(Color.fromRGB(0x6F, 0x4E, 0x37), Dish.parseColor("#6F4E37"));
        assertEquals(Color.fromRGB(0x12, 0x34, 0x56), Dish.parseColor("123456"));
    }

    @Test
    void traderCompassHintsAndCooldowns() {
        assertEquals("на севере", TraderService.compassDirection(0, -100));
        assertEquals("на северо-востоке", TraderService.compassDirection(100, -100));
        assertEquals("на востоке", TraderService.compassDirection(100, 0));
        assertEquals("на юго-востоке", TraderService.compassDirection(100, 100));
        assertEquals("на юге", TraderService.compassDirection(0, 100));
        assertEquals("на юго-западе", TraderService.compassDirection(-100, 100));
        assertEquals("на западе", TraderService.compassDirection(-100, 0));
        assertEquals("на северо-западе", TraderService.compassDirection(-100, -100));

        assertTrue(TraderService.formatTavernHint(null, 0, 0).contains("не знаю"));

        Tavern tavern = new Tavern("world", 0, 64, 0, true, Tavern.State.BUILT);
        assertTrue(TraderService.formatTavernHint(tavern, tavern.centerX(), tavern.centerZ()).contains("совсем рядом"));
        String farHint = TraderService.formatTavernHint(tavern, tavern.centerX(), tavern.centerZ() + 800);
        assertTrue(farHint.contains("на севере"));
        assertTrue(farHint.contains("800"));

        Map<UUID, Long> cooldowns = new HashMap<>();
        UUID id = UUID.randomUUID();
        assertTrue(TraderService.tryAcquireHintCooldown(cooldowns, id, 100_000L));
        assertFalse(TraderService.tryAcquireHintCooldown(cooldowns, id, 130_000L));
        assertTrue(TraderService.tryAcquireHintCooldown(cooldowns, id, 160_000L));
    }
}
