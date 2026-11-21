package com.server.halloumi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HalloumiTest {

    @Test
    void tavernSerializeAndParseRoundTripAllStates() {
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
    }

    @Test
    void tavernGeometryHelpers() {
        Tavern pending = new Tavern("world", 32, 60, 64, false, Tavern.State.PENDING);
        assertEquals((32 + TavernBuilder.SIZE_X / 2) >> 4, pending.chunkX());
        assertEquals((64 + TavernBuilder.SIZE_Z / 2) >> 4, pending.chunkZ());
        assertEquals(32 + TavernBuilder.SIZE_X / 2.0, pending.centerX());
        assertEquals(64 + TavernBuilder.SIZE_Z / 2.0, pending.centerZ());

        Tavern built = pending.built(70);
        assertEquals(Tavern.State.BUILT, built.state());
        assertEquals(70, built.y());
        assertFalse(built.decorated());
        assertTrue(built.withDecorated().decorated());
    }

    @Test
    void compassDirectionMapsAllEightSectors() {
        assertEquals("на севере", HalloumiTraderPlugin.compassDirection(0, -100));
        assertEquals("на северо-востоке", HalloumiTraderPlugin.compassDirection(100, -100));
        assertEquals("на востоке", HalloumiTraderPlugin.compassDirection(100, 0));
        assertEquals("на юго-востоке", HalloumiTraderPlugin.compassDirection(100, 100));
        assertEquals("на юге", HalloumiTraderPlugin.compassDirection(0, 100));
        assertEquals("на юго-западе", HalloumiTraderPlugin.compassDirection(-100, 100));
        assertEquals("на западе", HalloumiTraderPlugin.compassDirection(-100, 0));
        assertEquals("на северо-западе", HalloumiTraderPlugin.compassDirection(-100, -100));
    }
}

