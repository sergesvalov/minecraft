package com.server.halloumi.tavern;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometric layout constants and immutable furniture coordinates for the 1.4+ two-storey tavern.
 * Intentionally free of {@code Bukkit}/{@code Tag} static state so it can be unit-tested offline.
 */
public final class TavernLayout {

    /** A chair (stairs) with a cushion; {@code back} is the side of the backrest. Relative to the origin. */
    public record Chair(int x, int y, int z, BlockFace back) {
    }

    /** Relative 2D floor position for NPC/guest placement. */
    public record Spot(double x, double z) {
    }

    public static final int SIZE_X = 30;
    public static final int SIZE_Z = 23;
    /** Last yard row (the fence line). The yard starts right after the front wall, at z = SIZE_Z. */
    public static final int YARD_END_Z = 33;
    /** Upper floor deck and the ceiling (attic floor), relative to the floor. */
    public static final int DECK = 7;
    public static final int CEILING = 14;
    /** Highest block above the floor: the ridge slab at +26, plus one cleared row for tree tops. */
    public static final int HEIGHT = 27;
    /** West leaf of the front double door; the east one is at DOOR_X + 1. */
    public static final int DOOR_X = 14;
    /** Side gates of the yard: z GATE_Z and GATE_Z + 1. */
    public static final int GATE_Z = 27;
    /** Result of site height scans when the spot is unsuitable. */
    public static final int NO_SPOT = Integer.MIN_VALUE;

    // Interior positions relative to the origin
    public static final double TRADER_X = 15.0;
    public static final double TRADER_Z = 2.5;
    public static final List<Spot> GUEST_SPOTS = List.of(
            new Spot(8.5, 12.5),
            new Spot(21.5, 12.5),
            new Spot(15.0, 7.5),
            new Spot(15.0, 17.5)
    );
    /** Two signs above the front door, one over each leaf. */
    public static final int SIGN_X = DOOR_X;
    public static final int SIGN_Y = 3;
    public static final int SIGN_Z = SIZE_Z;
    /** Lectern with the menu book, inside by the door. */
    public static final int LECTERN_X = 17;
    public static final int LECTERN_Y = 1;
    public static final int LECTERN_Z = 20;

    /** Guest rooms upstairs: x ranges; the same three north and south of the corridor. */
    public static final int[][] ROOMS = {{9, 14}, {16, 21}, {23, 28}};
    /** West ends of the long tables (7 long), either side of the aisle from the door. */
    public static final int[] TABLE_STARTS = {5, 18};

    public static final List<Chair> CHAIRS;

    static {
        List<Chair> built = new ArrayList<>();
        for (int tz : new int[]{9, 15}) {
            for (int start : TABLE_STARTS) {
                for (int x = start; x <= start + 6; x += 2) {
                    built.add(new Chair(x, 1, tz - 1, BlockFace.NORTH));
                    built.add(new Chair(x, 1, tz + 1, BlockFace.SOUTH));
                }
            }
        }
        for (int[] t : smallTables()) {
            built.add(new Chair(t[0] - 1, t[1], t[2], BlockFace.WEST));
            built.add(new Chair(t[0] + 1, t[1], t[2], BlockFace.EAST));
        }
        CHAIRS = List.copyOf(built);
    }

    private TavernLayout() {
    }

    /** Small round tables {x, y, z}: in the hall, on the landing upstairs and in the yard. */
    public static int[][] smallTables() {
        return new int[][]{
                {4, 1, 5}, {25, 1, 5}, {4, 1, 19}, {25, 1, 19},
                {4, DECK + 1, 4}, {4, DECK + 1, 19},
                {4, 1, 26}, {9, 1, 26}, {20, 1, 26}, {25, 1, 26}, {4, 1, 30}, {9, 1, 30}
        };
    }

    /** Area {minX, minZ, maxX, maxZ} that the tavern and its yard take, roof overhang included. */
    public static int[] area(int ox, int oz) {
        return new int[]{ox - 1, oz - 1, ox + SIZE_X, oz + YARD_END_Z};
    }
}

