package com.server.greatbuildings.model;

import java.util.List;

/**
 * A real-world building at 1:1 scale (1 block = 1 metre). Coordinates in {@link #draw} are plan-local:
 * y = 0 is the main floor/yard level, x runs east, z runs south, north is -z.
 */
public interface Building {

    String id();

    Country country();

    String title();

    String where();

    /** Bounding box {minX, minY, minZ, maxX, maxY, maxZ} in plan coordinates. */
    int[] bounds();

    /**
     * Where the player stands relative to the plan's (0, 0, 0) when starting the build — outside the
     * building, in front of its entrance, so the building appears in front of them and they aren't buried
     * in a wall. {x, z} at yard level y = 0.
     */
    int[] anchor();

    void draw(Plan plan, Terrain terrain);

    /** Written book placed on the lectern at the entrance. */
    Book book();

    record Book(String title, String author, List<String> pages) {
    }
}

