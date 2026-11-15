package com.server.greatbuildings.buildings;

import com.server.greatbuildings.model.Face;
import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.States;
import com.server.greatbuildings.model.Terrain;

import java.util.List;

/**
 * Shared deterministic geometry, landscaping, and terrain-adaptation helpers used across building plans.
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

    /** Places a slender Mediterranean cypress tree (trunk y = 1..6, foliage y = 2..9). */
    public static void cypress(Plan plan, int x, int z, String leafState) {
        for (int y = 1; y <= 6; y++) {
            plan.set(x, y, z, States.logY("spruce_log"));
        }
        for (int y = 2; y <= 7; y++) {
            for (Face f : Face.values()) {
                plan.set(x + f.dx, y, z + f.dz, leafState);
            }
        }
        plan.set(x, 7, z, leafState);
        plan.set(x, 8, z, leafState);
        plan.set(x, 9, z, leafState);
    }

    /** Places a two-block stone pedestal with a wall sign on its facing side. */
    public static void plaquePillar(Plan plan, int x, int z, String pillarState, String wood, Face facing, List<String> lines) {
        plan.set(x, 1, z, pillarState);
        plan.set(x, 2, z, pillarState);
        int sx = x + facing.dx;
        int sz = z + facing.dz;
        plan.set(sx, 2, sz, States.wallSign(wood, facing));
        plan.signs.add(new Plan.SignText(sx, 2, sz, lines));
    }
}
