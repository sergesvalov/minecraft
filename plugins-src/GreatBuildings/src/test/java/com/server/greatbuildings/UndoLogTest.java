package com.server.greatbuildings;

import com.server.greatbuildings.engine.UndoLog;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class UndoLogTest {

    private static BlockData dummyBlockData(String label) {
        return (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, args) -> method.getName().equals("getAsString") ? label : null);
    }

    @Test
    void growsDynamicallyTakesDataAndClears() {
        UndoLog log = new UndoLog(4);
        assertTrue(log.isEmpty());

        for (int i = 0; i < 20; i++) {
            log.record(i * 10, dummyBlockData("block-" + i));
        }
        assertFalse(log.isEmpty());
        assertEquals(20, log.size());
        assertEquals(190, log.indexAt(19));

        BlockData taken = log.takeData(19);
        assertNotNull(taken);
        assertEquals("block-19", taken.getAsString());
        assertNull(log.takeData(19));

        log.clear();
        assertEquals(0, log.size());
        assertTrue(log.isEmpty());
    }
}

