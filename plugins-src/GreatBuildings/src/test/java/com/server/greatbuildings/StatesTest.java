package com.server.greatbuildings;

import com.server.greatbuildings.model.Country;
import com.server.greatbuildings.model.Face;
import com.server.greatbuildings.model.States;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatesTest {

    @Test
    void formatsBlockStatesCorrectly() {
        assertEquals("minecraft:stone", States.b("stone"));
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom]",
                States.stairs("oak_stairs", Face.NORTH, false));
        assertEquals("minecraft:oak_stairs[facing=south,half=top]",
                States.stairs("oak_stairs", Face.SOUTH, true));
        assertEquals("minecraft:stone_slab[type=bottom]", States.slab("stone_slab"));
        assertEquals("minecraft:lantern[hanging=true]", States.lantern(true));
        assertEquals("minecraft:lantern[hanging=false]", States.lantern(false));
        assertEquals("minecraft:candle[candles=4,lit=true]", States.candles(4));
        assertEquals("minecraft:spruce_leaves[persistent=true]", States.leaves("spruce_leaves"));
        assertEquals("minecraft:oak_log[axis=y]", States.logY("oak_log"));
    }

    @Test
    void countryAndFaceLookups() {
        assertEquals(Country.GEORGIA, Country.fromString("georgia"));
        assertEquals(Country.GEORGIA, Country.fromString("Грузия"));
        assertEquals(Country.CYPRUS, Country.fromString("Кипр"));
        assertNull(Country.fromString("unknown"));
        assertNull(Country.fromString(null));

        assertEquals(Face.SOUTH, Face.NORTH.opposite());
        assertEquals(Face.WEST, Face.EAST.opposite());
    }
}
