package com.server.greatbuildings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a building puts where, in coordinates relative to its origin: y = 0 is the ground level the
 * building stands on. A box of cells, each either untouched or one block state from a palette.
 * Pure data, no server classes: it can be drawn off the main thread and rendered in tests.
 */
public final class Plan {

    public static final String AIR = "minecraft:air";

    public final int minX, minY, minZ, maxX, maxY, maxZ;
    public final int sizeX, sizeY, sizeZ;
    /** 0 = untouched, otherwise palette index + 1. */
    private final short[] cells;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    public final List<SignText> signs = new ArrayList<>();
    public final List<int[]> lecterns = new ArrayList<>();

    public record SignText(int x, int y, int z, List<String> lines) {
    }

    public Plan(int[] bounds) {
        this(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
    }

    public Plan(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.cells = new short[sizeX * sizeY * sizeZ];
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Cells are ordered bottom-up, so applying them in index order lays foundations first. */
    public int index(int x, int y, int z) {
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }

    public int x(int index) {
        return index % sizeX + minX;
    }

    public int z(int index) {
        return (index / sizeX) % sizeZ + minZ;
    }

    public int y(int index) {
        return index / (sizeX * sizeZ) + minY;
    }

    public int volume() {
        return cells.length;
    }

    /** Silently ignores cells outside the box. */
    public void set(int x, int y, int z, String state) {
        if (!contains(x, y, z)) {
            return;
        }
        Short id = ids.get(state);
        if (id == null) {
            if (palette.size() >= Short.MAX_VALUE - 1) {
                throw new IllegalStateException("Too many block states in one plan");
            }
            palette.add(state);
            id = (short) palette.size();
            ids.put(state, id);
        }
        cells[index(x, y, z)] = id;
    }

    /**
     * Double door: two leaves side by side, hinged on the outer sides so they open apart, at (x, z) and the
     * next cell along the wall (+x for doors facing north/south, +z for east/west). {@code facing} points into
     * the room, the way a player walks in; y is the lower half.
     */
    public void doubleDoor(int x, int y, int z, String id, Face facing) {
        boolean alongX = facing == Face.NORTH || facing == Face.SOUTH;
        int x2 = alongX ? x + 1 : x;
        int z2 = alongX ? z : z + 1;
        // Seen walking in, the lower-coordinate leaf is on the left when facing north or east
        boolean firstRight = facing == Face.SOUTH || facing == Face.WEST;
        for (int half = 0; half <= 1; half++) {
            set(x, y + half, z, States.door(id, facing, half == 1, firstRight));
            set(x2, y + half, z2, States.door(id, facing, half == 1, !firstRight));
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** State at a cell, or null if untouched (or outside the box). */
    public String get(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return null;
        }
        return stateAt(index(x, y, z));
    }

    String stateAt(int index) {
        short id = cells[index];
        return id == 0 ? null : palette.get(id - 1);
    }

    /** Palette id at a cell: 0 = untouched, otherwise an index + 1 into {@link #palette()}. */
    int rawAt(int index) {
        return cells[index];
    }

    List<String> palette() {
        return palette;
    }
}
