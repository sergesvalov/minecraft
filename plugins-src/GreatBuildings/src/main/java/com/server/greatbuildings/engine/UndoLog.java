package com.server.greatbuildings.engine;

import org.bukkit.block.data.BlockData;

import java.util.Arrays;

/**
 * Compact dynamic buffer of previous block states recorded during building so {@code /gb undo}
 * can restore every modified cell in reverse order.
 */
public final class UndoLog {

    private int[] indices;
    private BlockData[] data;
    private int count;

    public UndoLog() {
        this(1 << 14);
    }

    public UndoLog(int initialCapacity) {
        int cap = Math.max(4, initialCapacity);
        this.indices = new int[cap];
        this.data = new BlockData[cap];
    }

    public void record(int index, BlockData previous) {
        if (count == indices.length) {
            int nextCap = count * 2;
            indices = Arrays.copyOf(indices, nextCap);
            data = Arrays.copyOf(data, nextCap);
        }
        indices[count] = index;
        data[count] = previous;
        count++;
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int indexAt(int i) {
        return indices[i];
    }

    public BlockData takeData(int i) {
        BlockData previous = data[i];
        data[i] = null;
        return previous;
    }

    public void clear() {
        Arrays.fill(data, 0, count, null);
        count = 0;
    }
}

