package com.server.halloumi.tavern;

import com.server.halloumi.model.TavernPalette;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;

import java.util.HashSet;
import java.util.Set;

import static com.server.halloumi.tavern.TavernLayout.CEILING;
import static com.server.halloumi.tavern.TavernLayout.CHAIRS;
import static com.server.halloumi.tavern.TavernLayout.DECK;
import static com.server.halloumi.tavern.TavernLayout.DOOR_X;
import static com.server.halloumi.tavern.TavernLayout.GATE_Z;
import static com.server.halloumi.tavern.TavernLayout.HEIGHT;
import static com.server.halloumi.tavern.TavernLayout.LECTERN_X;
import static com.server.halloumi.tavern.TavernLayout.LECTERN_Y;
import static com.server.halloumi.tavern.TavernLayout.LECTERN_Z;
import static com.server.halloumi.tavern.TavernLayout.ROOMS;
import static com.server.halloumi.tavern.TavernLayout.SIGN_X;
import static com.server.halloumi.tavern.TavernLayout.SIGN_Y;
import static com.server.halloumi.tavern.TavernLayout.SIGN_Z;
import static com.server.halloumi.tavern.TavernLayout.SIZE_X;
import static com.server.halloumi.tavern.TavernLayout.SIZE_Z;
import static com.server.halloumi.tavern.TavernLayout.TABLE_STARTS;
import static com.server.halloumi.tavern.TavernLayout.YARD_END_Z;

/**
 * Builds the 1.4+ two-storey tavern: counter, innkeeper, tables on the ground floor, six guest rooms
 * upstairs, and a fenced yard with a mangal and outdoor tables. Every door and gate is a double one.
 */
final class TavernBuilder {

    private TavernBuilder() {
    }

    static void build(BlockGrid b, int ox, int oy, int oz, TavernPalette p) {
        BlockData air = Material.AIR.createBlockData();
        BlockData base = p.base.createBlockData();
        BlockData floor = p.floor.createBlockData();
        BlockData wall = p.wall.createBlockData();

        // Clear the volume of the house (with the roof overhang) and the yard
        for (int x = -1; x <= SIZE_X; x++) {
            for (int z = -1; z <= YARD_END_Z; z++) {
                for (int y = 1; y <= HEIGHT; y++) {
                    b.set(ox + x, oy + y, oz + z, air);
                }
            }
        }

        // Foundation and floor of the house, paved yard
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z <= YARD_END_Z; z++) {
                boolean inside = z < SIZE_Z && x > 0 && z > 0 && x < SIZE_X - 1 && z < SIZE_Z - 1;
                b.set(ox + x, oy, oz + z, inside ? floor : base);
                TavernSite.fillDown(b, ox + x, oy - 1, oz + z, base);
            }
        }

        // Walls: stone bottom row, planks, logs at the corners and half-timber posts, beam at the upper floor
        BlockData post = BlockStates.axis(p.post, Axis.Y);
        BlockData beamX = BlockStates.axis(p.post, Axis.X);
        BlockData beamZ = BlockStates.axis(p.post, Axis.Z);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                boolean sideWall = x == 0 || x == SIZE_X - 1;
                boolean frontBack = z == 0 || z == SIZE_Z - 1;
                if (!sideWall && !frontBack) {
                    continue;
                }
                boolean postColumn = frontBack ? (x == 0 || x == 8 || x == 21 || x == SIZE_X - 1) : (z == 8 || z == 14);
                int top = frontBack ? CEILING : CEILING - 1;
                for (int y = 1; y <= top; y++) {
                    BlockData data;
                    if (postColumn && y < CEILING) {
                        data = post;
                    } else if (y == 1) {
                        data = base;
                    } else if (y == DECK) {
                        data = frontBack ? beamX : beamZ;
                    } else {
                        data = wall;
                    }
                    b.set(ox + x, oy + y, oz + z, data);
                }
            }
        }

        // Gable roof from the eaves at +14 up to the slab ridge at +26; gable ends filled with planks
        BlockData southSlope = BlockStates.stairs(p.roofStairs, BlockFace.NORTH);
        BlockData northSlope = BlockStates.stairs(p.roofStairs, BlockFace.SOUTH);
        int slopes = (SIZE_Z + 1) / 2;
        for (int i = 0; i < slopes; i++) {
            int y = oy + CEILING + i;
            for (int x = -1; x <= SIZE_X; x++) {
                b.set(ox + x, y, oz - 1 + i, northSlope);
                b.set(ox + x, y, oz + SIZE_Z - i, southSlope);
            }
            for (int z = i; z <= SIZE_Z - 1 - i; z++) {
                b.set(ox, y, oz + z, wall);
                b.set(ox + SIZE_X - 1, y, oz + z, wall);
            }
        }
        BlockData ridge = BlockStates.slab(p.roofSlab);
        for (int x = -1; x <= SIZE_X; x++) {
            b.set(ox + x, oy + CEILING + slopes, oz + SIZE_Z / 2, ridge);
        }

        // Upper floor deck with the stairwell opening, and the ceiling
        for (int x = 1; x < SIZE_X - 1; x++) {
            for (int z = 1; z < SIZE_Z - 1; z++) {
                boolean stairwell = x <= 2 && z >= 12 && z <= 15;
                b.set(ox + x, oy + DECK, oz + z, stairwell ? air : floor);
                b.set(ox + x, oy + CEILING, oz + z, floor);
            }
        }

        // Front double door and the signs above it (text is written once built, see TavernManager)
        BlockStates.doubleDoor(b, ox + DOOR_X, oy + 1, oz + SIZE_Z - 1, BlockFace.NORTH, p.door);
        b.set(ox + SIGN_X, oy + SIGN_Y, oz + SIGN_Z, BlockStates.facing(p.wallSign, BlockFace.SOUTH));
        b.set(ox + SIGN_X + 1, oy + SIGN_Y, oz + SIGN_Z, BlockStates.facing(p.wallSign, BlockFace.SOUTH));

        windows(b, ox, oy, oz);
        buildHall(b, ox, oy, oz, p);
        buildRooms(b, ox, oy, oz, p);
        buildYard(b, ox, oy, oz, p);

        for (TavernLayout.Chair c : CHAIRS) {
            b.set(ox + c.x(), oy + c.y(), oz + c.z(), BlockStates.stairs(p.chairStairs, c.back()));
        }
    }

    private static void windows(BlockGrid b, int ox, int oy, int oz) {
        BlockData glass = Material.GLASS.createBlockData();
        for (int x : new int[]{3, 4, 10, 11, 18, 19, 25, 26}) {
            for (int y = 2; y <= 4; y++) {
                b.set(ox + x, oy + y, oz + SIZE_Z - 1, glass);
                if (y >= 3) {
                    b.set(ox + x, oy + y, oz, glass);
                }
            }
        }
        for (int z : new int[]{3, 4, 18, 19}) {
            for (int y = 2; y <= 4; y++) {
                b.set(ox, oy + y, oz + z, glass);
                b.set(ox + SIZE_X - 1, oy + y, oz + z, glass);
            }
        }
        for (int x : new int[]{3, 4, 11, 12, 18, 19, 25, 26}) {
            for (int y = DECK + 2; y <= DECK + 4; y++) {
                b.set(ox + x, oy + y, oz, glass);
                b.set(ox + x, oy + y, oz + SIZE_Z - 1, glass);
            }
        }
        for (int y = DECK + 2; y <= DECK + 4; y++) {
            for (int z : new int[]{3, 4, 10, 11, 12, 18, 19}) {
                b.set(ox, oy + y, oz + z, glass);
                b.set(ox + SIZE_X - 1, oy + y, oz + z, glass);
            }
        }
    }

    private static void buildHall(BlockGrid b, int ox, int oy, int oz, TavernPalette p) {
        BlockData floor = p.floor.createBlockData();

        // Stairs up along the west wall, two wide, rising northwards from z=17 to the deck at z=11
        BlockData step = BlockStates.stairs(p.chairStairs, BlockFace.NORTH);
        for (int k = 0; k < DECK; k++) {
            int z = 17 - k;
            for (int x = 1; x <= 2; x++) {
                for (int y = 1; y <= k; y++) {
                    b.set(ox + x, oy + y, oz + z, floor);
                }
                b.set(ox + x, oy + 1 + k, oz + z, step);
            }
        }

        // Counter across the back with gaps at both ends; the innkeeper stands behind it
        BlockData counter = p.counter.createBlockData();
        for (int x = 9; x <= 20; x++) {
            b.set(ox + x, oy + 1, oz + 4, counter);
        }
        b.set(ox + 9, oy + 2, oz + 4, BlockStates.lantern(false));
        b.set(ox + 20, oy + 2, oz + 4, BlockStates.lantern(false));

        // Kitchen along the back wall: barrels and smokers
        BlockData barrel = BlockStates.facing(Material.BARREL, BlockFace.UP);
        for (int x : new int[]{1, 2, 9, 10, 11, 12, 14, 15, 17, 18, 19, 20, 27, 28}) {
            b.set(ox + x, oy + 1, oz + 1, barrel);
            b.set(ox + x, oy + 2, oz + 1, barrel);
        }
        b.set(ox + 13, oy + 1, oz + 1, BlockStates.facing(Material.SMOKER, BlockFace.SOUTH));
        b.set(ox + 16, oy + 1, oz + 1, BlockStates.facing(Material.SMOKER, BlockFace.SOUTH));

        // Long tables and small tables
        BlockData carpet = Material.RED_CARPET.createBlockData();
        for (int tz : new int[]{9, 15}) {
            for (int start : TABLE_STARTS) {
                for (int x = start; x <= start + 6; x++) {
                    b.set(ox + x, oy + 1, oz + tz, BlockStates.fence(p.fence, x > start, x < start + 6, false, false));
                    b.set(ox + x, oy + 2, oz + tz, carpet);
                }
            }
        }
        BlockData tablePost = p.fence.createBlockData();
        for (int[] t : TavernLayout.smallTables()) {
            b.set(ox + t[0], oy + t[1], oz + t[2], tablePost);
            b.set(ox + t[0], oy + t[1] + 1, oz + t[2], carpet);
        }

        // Menu lectern by the door
        b.set(ox + LECTERN_X, oy + LECTERN_Y, oz + LECTERN_Z, BlockStates.facing(Material.LECTERN, BlockFace.WEST));

        // Hanging lanterns in the hall
        BlockData hanging = BlockStates.lantern(true);
        for (int[] l : new int[][]{{4, 2}, {25, 2}, {11, 7}, {18, 7}, {7, 12}, {22, 12}, {15, 12},
                {11, 18}, {18, 18}, {6, 19}, {23, 19}, {15, 20}}) {
            b.set(ox + l[0], oy + DECK - 1, oz + l[1], hanging);
        }
    }

    private static void buildRooms(BlockGrid b, int ox, int oy, int oz, TavernPalette p) {
        BlockData wall = p.wall.createBlockData();
        BlockData hanging = BlockStates.lantern(true);
        int y0 = DECK + 1;

        for (int y = y0; y < CEILING; y++) {
            for (int x = 8; x < SIZE_X - 1; x++) {
                b.set(ox + x, oy + y, oz + 8, wall);
                b.set(ox + x, oy + y, oz + 14, wall);
            }
            for (int x : new int[]{8, 15, 22}) {
                for (int z = 1; z <= 7; z++) {
                    b.set(ox + x, oy + y, oz + z, wall);
                }
                for (int z = 15; z <= 21; z++) {
                    b.set(ox + x, oy + y, oz + z, wall);
                }
            }
        }

        Set<Long> rail = new HashSet<>();
        for (int z = 12; z <= 16; z++) {
            rail.add(key(3, z));
        }
        rail.add(key(1, 16));
        rail.add(key(2, 16));
        for (long k : rail) {
            int x = (int) (k >> 32);
            int z = (int) k;
            b.set(ox + x, oy + y0, oz + z, BlockStates.fence(p.fence, rail.contains(key(x - 1, z)),
                    rail.contains(key(x + 1, z)), rail.contains(key(x, z - 1)), rail.contains(key(x, z + 1))));
        }
        BlockData runner = Material.RED_CARPET.createBlockData();
        for (int x = 4; x < SIZE_X - 1; x++) {
            b.set(ox + x, oy + y0, oz + 11, runner);
        }
        for (int[] l : new int[][]{{11, 11}, {18, 11}, {25, 11}, {4, 8}, {5, 14}}) {
            b.set(ox + l[0], oy + CEILING - 1, oz + l[1], hanging);
        }

        Material orchid = Material.matchMaterial("POTTED_BLUE_ORCHID");
        int index = 0;
        for (boolean north : new boolean[]{true, false}) {
            int back = north ? 1 : 21;
            int front = north ? 7 : 15;
            int doorZ = north ? 8 : 14;
            BlockFace headFace = north ? BlockFace.NORTH : BlockFace.SOUTH;
            int footZ = north ? back + 1 : back - 1;
            for (int[] room : ROOMS) {
                int start = room[0];
                int end = room[1];
                boolean blue = index++ % 2 == 0;
                BlockData carpet = (blue ? Material.WHITE_CARPET : Material.LIGHT_BLUE_CARPET).createBlockData();
                Material bed = blue ? Material.LIGHT_BLUE_BED : Material.WHITE_BED;
                for (int x = start; x <= end; x++) {
                    for (int z = Math.min(back, front); z <= Math.max(back, front); z++) {
                        b.set(ox + x, oy + y0, oz + z, carpet);
                    }
                }
                for (int x : new int[]{start, end}) {
                    b.set(ox + x, oy + y0, oz + back, BlockStates.bed(bed, headFace, Bed.Part.HEAD));
                    b.set(ox + x, oy + y0, oz + footZ, BlockStates.bed(bed, headFace, Bed.Part.FOOT));
                }
                b.set(ox + start + 2, oy + y0, oz + back, BlockStates.facing(Material.BARREL, BlockFace.UP));
                if (orchid != null) {
                    b.set(ox + start + 3, oy + y0, oz + back, orchid.createBlockData());
                }
                b.set(ox + start + 2, oy + CEILING - 1, oz + (north ? 4 : 18), hanging);
                BlockStates.doubleDoor(b, ox + start + 2, oy + y0, oz + doorZ, headFace, p.door);
            }
        }

        // Attic lighting
        for (int x : new int[]{4, 11, 18, 25}) {
            b.set(ox + x, oy + CEILING + 1, oz + SIZE_Z / 2, BlockStates.lantern(false));
        }
    }

    private static void buildYard(BlockGrid b, int ox, int oy, int oz, TavernPalette p) {
        Material gate = Material.matchMaterial(p.fence.name() + "_GATE");
        Set<Long> fenceLine = new HashSet<>();
        for (int x = 0; x < SIZE_X; x++) {
            fenceLine.add(key(x, YARD_END_Z));
        }
        for (int z = SIZE_Z; z <= YARD_END_Z; z++) {
            fenceLine.add(key(0, z));
            fenceLine.add(key(SIZE_X - 1, z));
        }
        for (long k : fenceLine) {
            int x = (int) (k >> 32);
            int z = (int) k;
            boolean north = z == SIZE_Z || fenceLine.contains(key(x, z - 1));
            boolean south = fenceLine.contains(key(x, z + 1));
            boolean west = fenceLine.contains(key(x - 1, z));
            boolean east = fenceLine.contains(key(x + 1, z));
            b.set(ox + x, oy + 1, oz + z, BlockStates.fence(p.fence, west, east, north, south));
        }
        if (gate != null) {
            for (int i = 0; i <= 1; i++) {
                b.set(ox + DOOR_X + i, oy + 1, oz + YARD_END_Z, BlockStates.gate(gate, BlockFace.SOUTH));
                b.set(ox, oy + 1, oz + GATE_Z + i, BlockStates.gate(gate, BlockFace.WEST));
                b.set(ox + SIZE_X - 1, oy + 1, oz + GATE_Z + i, BlockStates.gate(gate, BlockFace.EAST));
            }
        }
        BlockData lantern = BlockStates.lantern(false);
        for (int[] l : new int[][]{{0, YARD_END_Z}, {SIZE_X - 1, YARD_END_Z}, {DOOR_X - 1, YARD_END_Z},
                {DOOR_X + 2, YARD_END_Z}, {0, GATE_Z - 1}, {0, GATE_Z + 2}, {SIZE_X - 1, GATE_Z - 1},
                {SIZE_X - 1, GATE_Z + 2}, {0, SIZE_Z}, {SIZE_X - 1, SIZE_Z}, {6, YARD_END_Z}, {23, YARD_END_Z}}) {
            b.set(ox + l[0], oy + 2, oz + l[1], lantern);
        }

        // Mangal: three campfires between stone ends, a smoker and a barrel of supplies next to it
        BlockData end = p.base.createBlockData();
        b.set(ox + 19, oy + 1, oz + 30, end);
        for (int x = 20; x <= 22; x++) {
            b.set(ox + x, oy + 1, oz + 30, BlockStates.campfire());
        }
        b.set(ox + 23, oy + 1, oz + 30, end);
        b.set(ox + 24, oy + 1, oz + 30, BlockStates.facing(Material.SMOKER, BlockFace.SOUTH));
        b.set(ox + 25, oy + 1, oz + 30, BlockStates.facing(Material.BARREL, BlockFace.UP));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}

