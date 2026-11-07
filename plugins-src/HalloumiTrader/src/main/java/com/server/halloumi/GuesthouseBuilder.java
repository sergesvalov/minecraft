package com.server.halloumi;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Gate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.server.halloumi.TavernBuilder.door;
import static com.server.halloumi.TavernBuilder.facing;
import static com.server.halloumi.TavernBuilder.fillDown;
import static com.server.halloumi.TavernBuilder.lantern;
import static com.server.halloumi.TavernBuilder.slab;
import static com.server.halloumi.TavernBuilder.stairs;

/**
 * The guesthouse next to a tavern: a two-storey house in the tavern's style with four times its floor area
 * (dining hall downstairs, eight bedrooms upstairs) and a fenced yard in front with a grill (mangal) and tables.
 *
 * <p>Layout, relative to the house origin (hx, oy, hz) = floor corner; the door faces south (+z) like the
 * tavern's: walls x 0..22, z 0..16; upper floor deck at oy+5, ceiling at oy+10, roof ridge at oy+19;
 * yard x 0..22, z 17..27 with the fence on its outer edge. The house stands {@link #GAP} blocks east or west
 * of the tavern with the front walls in line, and a path leads from the tavern door to the yard gate.
 *
 * <p>Always built in the live world (never during world generation): it spans several chunks, and features
 * of neighbouring chunks generated later (trees) would grow right through it.
 */
final class GuesthouseBuilder {

    static final int SIZE_X = 23;
    static final int SIZE_Z = 17;
    /** Last yard row (the fence line). The yard starts right after the front wall, at z = SIZE_Z. */
    static final int YARD_END_Z = 27;
    /** Highest block above the floor: the ridge slab at +19, plus one cleared row for tree tops. */
    static final int HEIGHT = 20;
    /** Free blocks between the tavern wall and the house wall. */
    static final int GAP = 3;
    static final int DOOR_X = 11;
    static final int SIGN_X = 11, SIGN_Y = 3, SIGN_Z = SIZE_Z;
    /** Side gates of the yard. */
    static final int GATE_Z = 22;

    /** Ground under the site may be this much below / above the floor; the builder levels it. */
    private static final int MAX_DROP = 6, MAX_RISE = 4;

    private static final int[] ROOM_STARTS = {1, 7, 12, 17};
    private static final int[] ROOM_ENDS = {5, 10, 15, 21};

    private static final Set<Material> CLEARABLE = new HashSet<>();

    static {
        for (String name : List.of("BROWN_MUSHROOM", "RED_MUSHROOM", "SWEET_BERRY_BUSH", "CACTUS", "BAMBOO",
                "BAMBOO_SAPLING", "PUMPKIN", "MELON", "BEE_NEST", "MOSS_CARPET", "PALE_MOSS_CARPET", "PALE_HANGING_MOSS",
                "LEAF_LITTER", "BUSH", "FIREFLY_BUSH", "COCOA", "SUGAR_CANE", "MUSHROOM_STEM", "BROWN_MUSHROOM_BLOCK",
                "RED_MUSHROOM_BLOCK", "MOSSY_COBBLESTONE", "SHORT_DRY_GRASS", "TALL_DRY_GRASS", "DEAD_BUSH",
                "PINK_PETALS", "WILDFLOWERS", "SPORE_BLOSSOM", "AZALEA", "FLOWERING_AZALEA", "BIG_DRIPLEAF",
                "BIG_DRIPLEAF_STEM", "SMALL_DRIPLEAF", "GLOW_LICHEN", "VINE", "CREAKING_HEART", "RESIN_CLUMP",
                "HANGING_ROOTS", "SNOW", "POWDER_SNOW", "CLAY", "ANDESITE", "DIORITE", "GRANITE", "TUFF",
                "SANDSTONE", "COAL_ORE", "IRON_ORE", "COPPER_ORE")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                CLEARABLE.add(material);
            }
        }
    }

    private GuesthouseBuilder() {
    }

    /** House origin x; the house stands east or west of the tavern at origin tavernX. */
    static int originX(int tavernX, Tavern.Annex side) {
        return side == Tavern.Annex.WEST ? tavernX - GAP - SIZE_X : tavernX + TavernBuilder.SIZE_X + GAP;
    }

    /** House origin z: its front wall lines up with the tavern's. */
    static int originZ(int tavernZ) {
        return tavernZ + TavernBuilder.SIZE_Z - SIZE_Z;
    }

    /** Area {minX, minZ, maxX, maxZ} that the house, yard and path take on the given side, roof overhang included. */
    static int[] area(int ox, int oz, Tavern.Annex side) {
        int hx = originX(ox, side);
        int hz = originZ(oz);
        int minX = Math.min(hx - 1, ox + 5);
        int maxX = Math.max(hx + SIZE_X, ox + 5);
        return new int[]{minX, hz - 1, maxX, hz + YARD_END_Z};
    }

    /**
     * True if the site on this side is dry, roughly level and holds nothing but natural terrain and plants,
     * so building there can't destroy anything a player made.
     */
    static boolean siteOk(TavernBuilder.Blocks b, int ox, int oy, int oz, Tavern.Annex side, int minY) {
        int hx = originX(ox, side);
        int hz = originZ(oz);
        for (int x = hx - 1; x <= hx + SIZE_X; x++) {
            for (int z = hz - 1; z <= hz + YARD_END_Z; z++) {
                if (!columnOk(b, x, z, oy, minY)) {
                    return false;
                }
            }
        }
        for (int[] cell : pathCells(ox, oz, side)) {
            if (!columnOk(b, cell[0], cell[1], oy, minY)) {
                return false;
            }
        }
        return true;
    }

    private static boolean columnOk(TavernBuilder.Blocks b, int x, int z, int oy, int minY) {
        int ground = TavernBuilder.groundY(b, x, z, minY);
        if (ground == TavernBuilder.NO_SPOT || ground < oy - 1 - MAX_DROP || ground > oy - 1 + MAX_RISE
                || !TavernBuilder.isNaturalGround(b.type(x, ground, z))) {
            return false;
        }
        for (int y = ground + 1; y <= oy + HEIGHT; y++) {
            if (!clearable(b.type(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean clearable(Material type) {
        if (type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        return type.isAir() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type) || Tag.REPLACEABLE.isTagged(type)
                || Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type) || Tag.CAVE_VINES.isTagged(type)
                || TavernBuilder.isNaturalGround(type) || CLEARABLE.contains(type);
    }

    /** Path from the tavern doorstep south, then along to the yard's side gate. */
    private static List<int[]> pathCells(int ox, int oz, Tavern.Annex side) {
        int hx = originX(ox, side);
        int gateZ = originZ(oz) + GATE_Z;
        int doorX = ox + 5;
        int gateX = side == Tavern.Annex.WEST ? hx + SIZE_X - 1 : hx;
        int step = side == Tavern.Annex.WEST ? -1 : 1;
        List<int[]> cells = new java.util.ArrayList<>();
        for (int z = oz + TavernBuilder.SIZE_Z + 1; z <= gateZ; z++) {
            cells.add(new int[]{doorX, z});
        }
        for (int x = doorX + step; x != gateX; x += step) {
            cells.add(new int[]{x, gateZ});
        }
        return cells;
    }

    /** House, yard and the path from the tavern at (ox, oy, oz). */
    static void buildNextTo(TavernBuilder.Blocks b, int ox, int oy, int oz, Tavern.Annex side, TavernPalette p) {
        BlockData air = Material.AIR.createBlockData();
        BlockData base = p.base.createBlockData();
        for (int[] cell : pathCells(ox, oz, side)) {
            for (int y = 1; y <= 4; y++) {
                b.set(cell[0], oy + y, cell[1], air);
            }
            b.set(cell[0], oy, cell[1], base);
            fillDown(b, cell[0], oy - 1, cell[1], base);
        }
        build(b, originX(ox, side), oy, originZ(oz), p);
    }

    static void build(TavernBuilder.Blocks b, int hx, int oy, int hz, TavernPalette p) {
        BlockData air = Material.AIR.createBlockData();
        BlockData base = p.base.createBlockData();
        BlockData floor = p.floor.createBlockData();
        BlockData wall = p.wall.createBlockData();

        // Clear the volume of the house (with the roof overhang) and the yard
        for (int x = -1; x <= SIZE_X; x++) {
            for (int z = -1; z <= YARD_END_Z; z++) {
                for (int y = 1; y <= HEIGHT; y++) {
                    b.set(hx + x, oy + y, hz + z, air);
                }
            }
        }

        // Foundation and floor of the house, paved yard
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z <= YARD_END_Z; z++) {
                boolean inside = z < SIZE_Z && x > 0 && z > 0 && x < SIZE_X - 1 && z < SIZE_Z - 1;
                b.set(hx + x, oy, hz + z, inside ? floor : base);
                fillDown(b, hx + x, oy - 1, hz + z, base);
            }
        }

        // Walls: stone bottom row, planks, logs at the corners and the half-timber posts, a log beam at the
        // upper floor. Front and back walls go one higher, under the roof.
        BlockData post = axis(p.post, Axis.Y);
        BlockData beamX = axis(p.post, Axis.X);
        BlockData beamZ = axis(p.post, Axis.Z);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                boolean sideWall = x == 0 || x == SIZE_X - 1;
                boolean frontBack = z == 0 || z == SIZE_Z - 1;
                if (!sideWall && !frontBack) {
                    continue;
                }
                boolean postColumn = frontBack ? (x == 0 || x == 6 || x == 16 || x == SIZE_X - 1) : (z == 8);
                int top = frontBack ? 10 : 9;
                for (int y = 1; y <= top; y++) {
                    BlockData data;
                    if (postColumn && y <= 9) {
                        data = post;
                    } else if (y == 1) {
                        data = base;
                    } else if (y == 5) {
                        data = frontBack ? beamX : beamZ;
                    } else {
                        data = wall;
                    }
                    b.set(hx + x, oy + y, hz + z, data);
                }
            }
        }

        // Gable roof like the tavern's, from the eaves at +10 up to the slab ridge at +19
        BlockData southSlope = stairs(p.roofStairs, BlockFace.NORTH);
        BlockData northSlope = stairs(p.roofStairs, BlockFace.SOUTH);
        int slopes = (SIZE_Z + 1) / 2;
        for (int i = 0; i < slopes; i++) {
            int y = oy + 10 + i;
            for (int x = -1; x <= SIZE_X; x++) {
                b.set(hx + x, y, hz - 1 + i, northSlope);
                b.set(hx + x, y, hz + SIZE_Z - i, southSlope);
            }
            for (int z = i; z <= SIZE_Z - 1 - i; z++) {
                b.set(hx, y, hz + z, wall);
                b.set(hx + SIZE_X - 1, y, hz + z, wall);
            }
        }
        BlockData ridge = slab(p.roofSlab);
        for (int x = -1; x <= SIZE_X; x++) {
            b.set(hx + x, oy + 10 + slopes, hz + SIZE_Z / 2, ridge);
        }

        // Upper floor deck and the ceiling, with the stairwell opening in the deck
        for (int x = 1; x < SIZE_X - 1; x++) {
            for (int z = 1; z < SIZE_Z - 1; z++) {
                boolean stairwell = x >= 2 && x <= 4 && (z == 8 || z == 9);
                b.set(hx + x, oy + 5, hz + z, stairwell ? air : floor);
                b.set(hx + x, oy + 10, hz + z, floor);
            }
        }

        // Front door and the sign above it (text is written once built, see TavernManager)
        b.set(hx + DOOR_X, oy + 1, hz + SIZE_Z - 1, door(p.door, Bisected.Half.BOTTOM));
        b.set(hx + DOOR_X, oy + 2, hz + SIZE_Z - 1, door(p.door, Bisected.Half.TOP));
        b.set(hx + SIGN_X, oy + SIGN_Y, hz + SIGN_Z, facing(p.wallSign, BlockFace.SOUTH));

        // Windows: two rows on the ground floor, one per room upstairs
        BlockData glass = Material.GLASS.createBlockData();
        for (int x : new int[]{2, 3, 8, 9, 13, 14, 19, 20}) {
            for (int y = 2; y <= 3; y++) {
                b.set(hx + x, oy + y, hz, glass);
                b.set(hx + x, oy + y, hz + SIZE_Z - 1, glass);
            }
        }
        for (int z : new int[]{3, 4, 12, 13}) {
            for (int y = 2; y <= 3; y++) {
                b.set(hx, oy + y, hz + z, glass);
                b.set(hx + SIZE_X - 1, oy + y, hz + z, glass);
            }
        }
        for (int x : new int[]{3, 8, 9, 13, 14, 19}) {
            for (int y = 7; y <= 8; y++) {
                b.set(hx + x, oy + y, hz, glass);
                b.set(hx + x, oy + y, hz + SIZE_Z - 1, glass);
            }
        }
        for (int z : new int[]{3, 7, 9, 13}) {
            for (int y = 7; y <= 8; y++) {
                b.set(hx, oy + y, hz + z, glass);
                b.set(hx + SIZE_X - 1, oy + y, hz + z, glass);
            }
        }

        buildDiningHall(b, hx, oy, hz, p);
        buildBedrooms(b, hx, oy, hz, p);
        buildYard(b, hx, oy, hz, p);
    }

    private static void buildDiningHall(TavernBuilder.Blocks b, int hx, int oy, int hz, TavernPalette p) {
        BlockData floor = p.floor.createBlockData();

        // Stairs up along the west wall, rising westwards from x=5 to the deck at x=1, on a solid base
        BlockData step = stairs(p.chairStairs, BlockFace.WEST);
        for (int k = 0; k < 5; k++) {
            int x = 5 - k;
            for (int z = 8; z <= 9; z++) {
                for (int y = 1; y <= k; y++) {
                    b.set(hx + x, oy + y, hz + z, floor);
                }
                b.set(hx + x, oy + 1 + k, hz + z, step);
            }
        }

        // Kitchen along the back wall: barrels, smokers and a serving counter
        BlockData barrel = facing(Material.BARREL, BlockFace.UP);
        for (int x : new int[]{1, 2, 3, 10, 11, 12, 19, 20, 21}) {
            b.set(hx + x, oy + 1, hz + 1, barrel);
            b.set(hx + x, oy + 2, hz + 1, barrel);
        }
        b.set(hx + 9, oy + 1, hz + 1, facing(Material.SMOKER, BlockFace.SOUTH));
        b.set(hx + 13, oy + 1, hz + 1, facing(Material.SMOKER, BlockFace.SOUTH));
        BlockData counter = p.counter.createBlockData();
        for (int x = 9; x <= 13; x++) {
            b.set(hx + x, oy + 1, hz + 3, counter);
        }
        b.set(hx + 9, oy + 2, hz + 3, lantern(false));
        b.set(hx + 13, oy + 2, hz + 3, lantern(false));

        // Two long tables with chairs on both sides, and two small ones by the front windows
        BlockData carpet = Material.RED_CARPET.createBlockData();
        for (int tz : new int[]{7, 11}) {
            for (int x = 8; x <= 18; x++) {
                b.set(hx + x, oy + 1, hz + tz, fence(p.fence, x > 8, x < 18, false, false));
                b.set(hx + x, oy + 2, hz + tz, carpet);
            }
            for (int x = 8; x <= 18; x += 2) {
                b.set(hx + x, oy + 1, hz + tz - 1, stairs(p.chairStairs, BlockFace.NORTH));
                b.set(hx + x, oy + 1, hz + tz + 1, stairs(p.chairStairs, BlockFace.SOUTH));
            }
        }
        BlockData post = p.fence.createBlockData();
        for (int tx : new int[]{3, 19}) {
            b.set(hx + tx, oy + 1, hz + 13, post);
            b.set(hx + tx, oy + 2, hz + 13, carpet);
            b.set(hx + tx - 1, oy + 1, hz + 13, stairs(p.chairStairs, BlockFace.WEST));
            b.set(hx + tx + 1, oy + 1, hz + 13, stairs(p.chairStairs, BlockFace.EAST));
        }

        // Lanterns hang from the upper floor, so the hall stays lit and no mobs spawn
        BlockData hanging = lantern(true);
        for (int[] l : new int[][]{{4, 4}, {11, 5}, {18, 4}, {7, 9}, {15, 9}, {4, 13}, {11, 13}, {18, 13}}) {
            b.set(hx + l[0], oy + 4, hz + l[1], hanging);
        }
    }

    private static void buildBedrooms(TavernBuilder.Blocks b, int hx, int oy, int hz, TavernPalette p) {
        BlockData wall = p.wall.createBlockData();
        BlockData hanging = lantern(true);

        // Corridor z 7..9 along the house; rooms to the north (z 1..5) and south (z 11..15)
        for (int x = 1; x < SIZE_X - 1; x++) {
            for (int y = 6; y <= 9; y++) {
                b.set(hx + x, oy + y, hz + 6, wall);
                b.set(hx + x, oy + y, hz + 10, wall);
            }
        }
        for (int x : new int[]{6, 11, 16}) {
            for (int y = 6; y <= 9; y++) {
                for (int z = 1; z <= 5; z++) {
                    b.set(hx + x, oy + y, hz + z, wall);
                }
                for (int z = 11; z <= 15; z++) {
                    b.set(hx + x, oy + y, hz + z, wall);
                }
            }
        }

        // Railing on the stairwell edge, a runner and lanterns in the corridor
        b.set(hx + 5, oy + 6, hz + 8, fence(p.fence, false, false, false, true));
        b.set(hx + 5, oy + 6, hz + 9, fence(p.fence, false, false, true, false));
        BlockData runner = Material.RED_CARPET.createBlockData();
        for (int x = 6; x < SIZE_X - 1; x++) {
            b.set(hx + x, oy + 6, hz + 8, runner);
        }
        for (int x : new int[]{8, 14, 19}) {
            b.set(hx + x, oy + 9, hz + 8, hanging);
        }

        // Eight bedrooms in Cypriot white and blue: two beds, a barrel for belongings, a lantern
        int index = 0;
        for (boolean north : new boolean[]{true, false}) {
            int back = north ? 1 : 15;
            int doorZ = north ? 6 : 10;
            int front = north ? 5 : 11;
            BlockFace headFace = north ? BlockFace.NORTH : BlockFace.SOUTH;
            int footZ = north ? back + 1 : back - 1;
            for (int r = 0; r < ROOM_STARTS.length; r++, index++) {
                int start = ROOM_STARTS[r];
                int end = ROOM_ENDS[r];
                int center = (start + end) / 2;
                boolean blue = index % 2 == 0;
                BlockData carpet = (blue ? Material.WHITE_CARPET : Material.LIGHT_BLUE_CARPET).createBlockData();
                Material bed = blue ? Material.LIGHT_BLUE_BED : Material.WHITE_BED;
                for (int x = start; x <= end; x++) {
                    for (int z = Math.min(back, front); z <= Math.max(back, front); z++) {
                        b.set(hx + x, oy + 6, hz + z, carpet);
                    }
                }
                for (int x : new int[]{start, end}) {
                    b.set(hx + x, oy + 6, hz + back, bed(bed, headFace, Bed.Part.HEAD));
                    b.set(hx + x, oy + 6, hz + footZ, bed(bed, headFace, Bed.Part.FOOT));
                }
                b.set(hx + center, oy + 6, hz + back, facing(Material.BARREL, BlockFace.UP));
                b.set(hx + center, oy + 9, hz + (north ? 3 : 13), hanging);
                b.set(hx + center, oy + 6, hz + doorZ, door(p.door, Bisected.Half.BOTTOM));
                b.set(hx + center, oy + 7, hz + doorZ, door(p.door, Bisected.Half.TOP));
            }
        }

        // A few lanterns in the closed attic, so it stays lit too
        for (int x : new int[]{4, 11, 18}) {
            b.set(hx + x, oy + 11, hz + 8, lantern(false));
        }
    }

    private static void buildYard(TavernBuilder.Blocks b, int hx, int oy, int hz, TavernPalette p) {
        // Fence on three sides (the house closes the fourth), gates south and on both sides
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
            b.set(hx + x, oy + 1, hz + z, fence(p.fence, west, east, north, south));
        }
        Material gate = Material.matchMaterial(p.fence.name() + "_GATE");
        if (gate != null) {
            b.set(hx + DOOR_X, oy + 1, hz + YARD_END_Z, gate(gate, BlockFace.SOUTH));
            b.set(hx, oy + 1, hz + GATE_Z, gate(gate, BlockFace.WEST));
            b.set(hx + SIZE_X - 1, oy + 1, hz + GATE_Z, gate(gate, BlockFace.EAST));
        }
        BlockData lantern = lantern(false);
        for (int[] l : new int[][]{{0, YARD_END_Z}, {SIZE_X - 1, YARD_END_Z}, {DOOR_X - 1, YARD_END_Z},
                {DOOR_X + 1, YARD_END_Z}, {0, GATE_Z - 1}, {0, GATE_Z + 1}, {SIZE_X - 1, GATE_Z - 1},
                {SIZE_X - 1, GATE_Z + 1}, {0, SIZE_Z}, {SIZE_X - 1, SIZE_Z}, {5, YARD_END_Z}, {17, YARD_END_Z}}) {
            b.set(hx + l[0], oy + 2, hz + l[1], lantern);
        }

        // Tables with chairs, leaving the middle free for the way from the door to the gate
        BlockData post = p.fence.createBlockData();
        BlockData carpet = Material.RED_CARPET.createBlockData();
        for (int[] t : new int[][]{{3, 20}, {7, 20}, {15, 20}, {19, 20}, {3, 24}, {7, 24}}) {
            b.set(hx + t[0], oy + 1, hz + t[1], post);
            b.set(hx + t[0], oy + 2, hz + t[1], carpet);
            b.set(hx + t[0] - 1, oy + 1, hz + t[1], stairs(p.chairStairs, BlockFace.WEST));
            b.set(hx + t[0] + 1, oy + 1, hz + t[1], stairs(p.chairStairs, BlockFace.EAST));
        }

        // Mangal: three campfires between stone ends, a smoker and a barrel of supplies next to it
        BlockData end = p.base.createBlockData();
        b.set(hx + 14, oy + 1, hz + 24, end);
        for (int x = 15; x <= 17; x++) {
            b.set(hx + x, oy + 1, hz + 24, campfire());
        }
        b.set(hx + 18, oy + 1, hz + 24, end);
        b.set(hx + 19, oy + 1, hz + 24, facing(Material.SMOKER, BlockFace.SOUTH));
        b.set(hx + 20, oy + 1, hz + 24, facing(Material.BARREL, BlockFace.UP));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static BlockData fence(Material material, boolean west, boolean east, boolean north, boolean south) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof MultipleFacing f) {
                f.setFace(BlockFace.WEST, west);
                f.setFace(BlockFace.EAST, east);
                f.setFace(BlockFace.NORTH, north);
                f.setFace(BlockFace.SOUTH, south);
            }
        });
    }

    private static BlockData gate(Material material, BlockFace face) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Gate g) {
                g.setFacing(face);
                g.setOpen(false);
            }
        });
    }

    private static BlockData bed(Material material, BlockFace face, Bed.Part part) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Bed bed) {
                bed.setFacing(face);
                bed.setPart(part);
            }
        });
    }

    private static BlockData campfire() {
        return Bukkit.createBlockData(Material.CAMPFIRE, d -> {
            if (d instanceof Campfire c) {
                c.setFacing(BlockFace.SOUTH);
                c.setLit(true);
                c.setSignalFire(false);
            }
        });
    }

    private static BlockData axis(Material material, Axis axis) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Orientable o) {
                o.setAxis(axis);
            }
        });
    }
}
