package com.server.greatbuildings.buildings;

import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.Terrain;

/**
 * Shared deterministic geometry and terrain-adaptation helpers used across building plans.
 */
public final class Shapes {

    private Shapes() {
    }

    /** Deterministic 3D spatial hash returning a non-negative integer. */
    public static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Fills a foundation column from the detected terrain surface (or plan minY) up to y = -1. */
    public static void foundation(Plan plan, Terrain terrain, int x, int z, String state) {
        int ground = terrain.groundY(x, z);
        for (int y = ground == Terrain.NONE ? plan.minY : Math.max(plan.minY, ground + 1); y < 0; y++) {
            plan.set(x, y, z, state);
        }
    }

    /** Clears air in a vertical column from y = 1 to {@code topY} inclusive. */
    public static void clearColumn(Plan plan, int x, int z, int topY) {
        for (int y = 1; y <= topY; y++) {
            plan.set(x, y, z, Plan.AIR);
        }
    }
}

