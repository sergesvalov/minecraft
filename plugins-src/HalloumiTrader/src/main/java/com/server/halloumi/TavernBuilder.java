package com.server.halloumi;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the tavern: one two-storey house with the tavern itself (counter, innkeeper, tables) on the ground floor,
 * guest rooms upstairs, and a fenced yard with a grill (mangal) and tables in front. Every door and gate is a
 * double one, and rooms and passages are sized for them. Always built in the live world: the house spans several
 * chunks, and features of neighbouring chunks generated later (trees) would grow right through it.
 *
 * <p>Layout, relative to the origin (ox, oy, oz) = floor corner; the front door faces south (+z):
 * walls x 0..29, z 0..22; ground floor y 1..6, upper floor deck at oy+7, rooms y 8..13, ceiling at oy+14,
 * roof ridge at oy+26. Yard x 0..29, z 23..33 with the fence on its outer edge.
 * <ul>
 *   <li>Ground floor: counter along the back with the innkeeper behind it, kitchen barrels and smokers along the
 *   back wall, two long tables split by the aisle from the door, four small tables, stairs up along the west wall.</li>
 *   <li>Upper floor: landing at the west end (x 1..7) over the stairs, corridor z 9..13 along the house, three rooms
 *   north (z 1..7) and three south (z 15..21) of it, each 6 x 7 and 6 high.</li>
 * </ul>
 */
final class TavernBuilder {

    /** Minimal block access to the world (and to a LimitedRegion when checking a spot during world generation). */
    interface Blocks {
        Material type(int x, int y, int z);

        void set(int x, int y, int z, BlockData data);

        int highestY(int x, int z);
    }

    /** A chair (stairs) with a cushion; {@code back} is the side of the backrest. Relative to the origin. */
    record Chair(int x, int y, int z, BlockFace back) {
    }

    static final int SIZE_X = 30;
    static final int SIZE_Z = 23;
    /** Last yard row (the fence line). The yard starts right after the front wall, at z = SIZE_Z. */
    static final int YARD_END_Z = 33;
    /** Upper floor deck and the ceiling (attic floor), relative to the floor. */
    static final int DECK = 7, CEILING = 14;
    /** Highest block above the floor: the ridge slab at +26, plus one cleared row for tree tops. */
    static final int HEIGHT = 27;
    /** West leaf of the front double door; the east one is at DOOR_X + 1. */
    static final int DOOR_X = 14;
    /** Side gates of the yard: z GATE_Z and GATE_Z + 1. */
    static final int GATE_Z = 27;
    /** Result of {@link #findFloorY} / {@link #siteFloorY} when the spot is unsuitable. */
    static final int NO_SPOT = Integer.MIN_VALUE;

    // Positions inside the tavern, relative to the origin
    static final double TRADER_X = 15.0, TRADER_Z = 2.5;
    static final double[][] GUEST_SPOTS = {{8.5, 12.5}, {21.5, 12.5}, {15.0, 7.5}, {15.0, 17.5}};
    /** Two signs above the front door, one over each leaf. */
    static final int SIGN_X = DOOR_X, SIGN_Y = 3, SIGN_Z = SIZE_Z;
    /** Lectern with the menu book, inside by the door. */
    static final int LECTERN_X = 17, LECTERN_Y = 1, LECTERN_Z = 20;

    /** Ground under the site may be this much below / above the floor; the builder levels it. */
    private static final int MAX_DROP = 6, MAX_RISE = 4;

    /** Guest rooms upstairs: x ranges; the same three north and south of the corridor. */
    private static final int[][] ROOMS = {{9, 14}, {16, 21}, {23, 28}};

    /** West ends of the long tables (7 long), either side of the aisle from the door. */
    private static final int[] TABLE_STARTS = {5, 18};

    static final List<Chair> CHAIRS = new ArrayList<>();
    private static final Set<Material> NATURAL_GROUND = new HashSet<>();
    private static final Set<Material> CLEARABLE = new HashSet<>();

    static {
        for (String name : List.of("GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL", "ROOTED_DIRT", "MOSS_BLOCK",
                "PALE_MOSS_BLOCK", "SAND", "RED_SAND", "SNOW_BLOCK", "STONE", "GRAVEL", "MUD")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                NATURAL_GROUND.add(material);
            }
        }
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

        // Hall: two long tables in two rows, split by the aisle from the door to the counter
        for (int tz : new int[]{9, 15}) {
            for (int start : TABLE_STARTS) {
                for (int x = start; x <= start + 6; x += 2) {
                    CHAIRS.add(new Chair(x, 1, tz - 1, BlockFace.NORTH));
                    CHAIRS.add(new Chair(x, 1, tz + 1, BlockFace.SOUTH));
                }
            }
        }
        for (int[] t : smallTables()) {
            CHAIRS.add(new Chair(t[0] - 1, t[1], t[2], BlockFace.WEST));
            CHAIRS.add(new Chair(t[0] + 1, t[1], t[2], BlockFace.EAST));
        }
    }

    private TavernBuilder() {
    }

    /** Small round tables {x, y, z}: in the hall, on the landing upstairs and in the yard. */
    private static int[][] smallTables() {
        return new int[][]{
                {4, 1, 5}, {25, 1, 5}, {4, 1, 19}, {25, 1, 19},
                {4, DECK + 1, 4}, {4, DECK + 1, 19},
                {4, 1, 26}, {9, 1, 26}, {20, 1, 26}, {25, 1, 26}, {4, 1, 30}, {9, 1, 30}};
    }

    static boolean isNaturalGround(Material type) {
        return NATURAL_GROUND.contains(type);
    }

    /** Block access to the live world, without physics: doors, beds and lanterns are placed piece by piece. */
    static Blocks worldBlocks(World world) {
        return new Blocks() {
            @Override
            public Material type(int x, int y, int z) {
                return world.getType(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, BlockData data) {
                world.getBlockAt(x, y, z).setBlockData(data, false);
            }

            @Override
            public int highestY(int x, int z) {
                return world.getHighestBlockYAt(x, z);
            }
        };
    }

    /** Topmost ground block, skipping air, plants, snow layers, leaves and tree trunks. NO_SPOT on water/lava. */
    static int groundY(Blocks blocks, int x, int z, int minY) {
        for (int y = blocks.highestY(x, z); y > minY; y--) {
            Material type = blocks.type(x, y, z);
            if (type == Material.WATER || type == Material.LAVA) {
                return NO_SPOT;
            }
            if (type.isAir() || !type.isSolid() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
                continue;
            }
            return y;
        }
        return NO_SPOT;
    }

    /**
     * Quick check during world generation, within one chunk: natural, dry and flat enough ground at (ox, oz)
     * and the next 12 blocks east and south. Returns the ground level, or NO_SPOT.
     */
    static int findFloorY(Blocks blocks, int ox, int oz, int minY, int maxSlope) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 12; dx += 6) {
            for (int dz = 0; dz <= 12; dz += 6) {
                int y = groundY(blocks, ox + dx, oz + dz, minY);
                if (y == NO_SPOT || !NATURAL_GROUND.contains(blocks.type(ox + dx, y, oz + dz))) {
                    return NO_SPOT;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min > maxSlope ? NO_SPOT : max;
    }

    /** Area {minX, minZ, maxX, maxZ} that the tavern and its yard take, roof overhang included. */
    static int[] area(int ox, int oz) {
        return new int[]{ox - 1, oz - 1, ox + SIZE_X, oz + YARD_END_Z};
    }

    /**
     * Floor level for a tavern at (ox, oz) in the live world, or NO_SPOT. The site must be dry, roughly level and
     * hold nothing but natural terrain and plants, so building there can't destroy anything a player made.
     */
    static int siteFloorY(Blocks b, int ox, int oz, int minY, int maxY) {
        int[] area = area(ox, oz);
        List<Integer> grounds = new ArrayList<>();
        for (int x = area[0]; x <= area[2]; x++) {
            for (int z = area[1]; z <= area[3]; z++) {
                int ground = groundY(b, x, z, minY);
                if (ground == NO_SPOT || !isNaturalGround(b.type(x, ground, z))) {
                    return NO_SPOT;
                }
                grounds.add(ground);
            }
        }
        grounds.sort(null);
        int oy = grounds.get(grounds.size() / 2) + 1;
        if (oy + HEIGHT >= maxY) {
            return NO_SPOT;
        }
        for (int x = area[0]; x <= area[2]; x++) {
            for (int z = area[1]; z <= area[3]; z++) {
                int ground = groundY(b, x, z, minY);
                if (ground < oy - 1 - MAX_DROP || ground > oy - 1 + MAX_RISE) {
                    return NO_SPOT;
                }
                for (int y = ground + 1; y <= oy + HEIGHT; y++) {
                    if (!clearable(b.type(x, y, z))) {
                        return NO_SPOT;
                    }
                }
            }
        }
        return oy;
    }

    private static boolean clearable(Material type) {
        if (type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        return type.isAir() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type) || Tag.REPLACEABLE.isTagged(type)
                || Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type) || Tag.CAVE_VINES.isTagged(type)
                || isNaturalGround(type) || CLEARABLE.contains(type);
    }

    static void build(Blocks b, int ox, int oy, int oz, TavernPalette p) {
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
                fillDown(b, ox + x, oy - 1, oz + z, base);
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
        BlockData southSlope = stairs(p.roofStairs, BlockFace.NORTH);
        BlockData northSlope = stairs(p.roofStairs, BlockFace.SOUTH);
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
        BlockData ridge = slab(p.roofSlab);
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
        doubleDoor(b, ox + DOOR_X, oy + 1, oz + SIZE_Z - 1, BlockFace.NORTH, p.door);
        b.set(ox + SIGN_X, oy + SIGN_Y, oz + SIGN_Z, facing(p.wallSign, BlockFace.SOUTH));
        b.set(ox + SIGN_X + 1, oy + SIGN_Y, oz + SIGN_Z, facing(p.wallSign, BlockFace.SOUTH));

        windows(b, ox, oy, oz);
        buildHall(b, ox, oy, oz, p);
        buildRooms(b, ox, oy, oz, p);
        buildYard(b, ox, oy, oz, p);

        // Chairs everywhere get a cushion (a display entity, added by TavernManager) and can be sat on
        for (Chair c : CHAIRS) {
            b.set(ox + c.x(), oy + c.y(), oz + c.z(), stairs(p.chairStairs, c.back()));
        }
    }

    private static void windows(Blocks b, int ox, int oy, int oz) {
        BlockData glass = Material.GLASS.createBlockData();
        // Ground floor: tall windows; the back ones start higher, above the kitchen barrels
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
        // Upper floor: one window per room, three on the landing, one at the end of the corridor
        int[] front = {3, 4, 11, 12, 18, 19, 25, 26};
        for (int x : front) {
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

    private static void buildHall(Blocks b, int ox, int oy, int oz, TavernPalette p) {
        BlockData floor = p.floor.createBlockData();

        // Stairs up along the west wall, two wide, rising northwards from z=17 to the deck at z=11, on a solid base
        BlockData step = stairs(p.chairStairs, BlockFace.NORTH);
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
        b.set(ox + 9, oy + 2, oz + 4, lantern(false));
        b.set(ox + 20, oy + 2, oz + 4, lantern(false));

        // Kitchen along the back wall: barrels and smokers
        BlockData barrel = facing(Material.BARREL, BlockFace.UP);
        for (int x : new int[]{1, 2, 9, 10, 11, 12, 14, 15, 17, 18, 19, 20, 27, 28}) {
            b.set(ox + x, oy + 1, oz + 1, barrel);
            b.set(ox + x, oy + 2, oz + 1, barrel);
        }
        b.set(ox + 13, oy + 1, oz + 1, facing(Material.SMOKER, BlockFace.SOUTH));
        b.set(ox + 16, oy + 1, oz + 1, facing(Material.SMOKER, BlockFace.SOUTH));

        // Long tables (fence + carpet); chairs are placed from CHAIRS
        BlockData carpet = Material.RED_CARPET.createBlockData();
        for (int tz : new int[]{9, 15}) {
            for (int start : TABLE_STARTS) {
                for (int x = start; x <= start + 6; x++) {
                    b.set(ox + x, oy + 1, oz + tz, fence(p.fence, x > start, x < start + 6, false, false));
                    b.set(ox + x, oy + 2, oz + tz, carpet);
                }
            }
        }
        BlockData tablePost = p.fence.createBlockData();
        for (int[] t : smallTables()) {
            b.set(ox + t[0], oy + t[1], oz + t[2], tablePost);
            b.set(ox + t[0], oy + t[1] + 1, oz + t[2], carpet);
        }

        // Menu lectern by the door
        b.set(ox + LECTERN_X, oy + LECTERN_Y, oz + LECTERN_Z, facing(Material.LECTERN, BlockFace.WEST));

        // Lanterns hang from the upper floor, so the hall stays lit and no mobs spawn
        BlockData hanging = lantern(true);
        for (int[] l : new int[][]{{4, 2}, {25, 2}, {11, 7}, {18, 7}, {7, 12}, {22, 12}, {15, 12},
                {11, 18}, {18, 18}, {6, 19}, {23, 19}, {15, 20}}) {
            b.set(ox + l[0], oy + DECK - 1, oz + l[1], hanging);
        }
    }

    private static void buildRooms(Blocks b, int ox, int oy, int oz, TavernPalette p) {
        BlockData wall = p.wall.createBlockData();
        BlockData hanging = lantern(true);
        int y0 = DECK + 1;

        // Corridor z 9..13 along the house, rooms north (z 1..7) and south (z 15..21); the landing x 1..7 is open
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

        // Railing around the stairwell, a runner and lanterns in the corridor and on the landing
        Set<Long> rail = new HashSet<>();
        for (int z = 12; z <= 16; z++) {
            rail.add(key(3, z));
        }
        rail.add(key(1, 16));
        rail.add(key(2, 16));
        for (long k : rail) {
            int x = (int) (k >> 32);
            int z = (int) k;
            b.set(ox + x, oy + y0, oz + z, fence(p.fence, rail.contains(key(x - 1, z)), rail.contains(key(x + 1, z)),
                    rail.contains(key(x, z - 1)), rail.contains(key(x, z + 1))));
        }
        BlockData runner = Material.RED_CARPET.createBlockData();
        for (int x = 4; x < SIZE_X - 1; x++) {
            b.set(ox + x, oy + y0, oz + 11, runner);
        }
        for (int[] l : new int[][]{{11, 11}, {18, 11}, {25, 11}, {4, 8}, {5, 14}}) {
            b.set(ox + l[0], oy + CEILING - 1, oz + l[1], hanging);
        }

        // Six guest rooms in Cypriot white and blue: two beds, a barrel for belongings, a blue orchid, a lantern
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
                    b.set(ox + x, oy + y0, oz + back, bed(bed, headFace, Bed.Part.HEAD));
                    b.set(ox + x, oy + y0, oz + footZ, bed(bed, headFace, Bed.Part.FOOT));
                }
                b.set(ox + start + 2, oy + y0, oz + back, facing(Material.BARREL, BlockFace.UP));
                if (orchid != null) {
                    b.set(ox + start + 3, oy + y0, oz + back, orchid.createBlockData());
                }
                b.set(ox + start + 2, oy + CEILING - 1, oz + (north ? 4 : 18), hanging);
                doubleDoor(b, ox + start + 2, oy + y0, oz + doorZ, headFace, p.door);
            }
        }

        // A few lanterns in the closed attic, so it stays lit too
        for (int x : new int[]{4, 11, 18, 25}) {
            b.set(ox + x, oy + CEILING + 1, oz + SIZE_Z / 2, lantern(false));
        }
    }

    private static void buildYard(Blocks b, int ox, int oy, int oz, TavernPalette p) {
        // Fence on three sides (the house closes the fourth), double gates south and on both sides
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
            b.set(ox + x, oy + 1, oz + z, fence(p.fence, west, east, north, south));
        }
        if (gate != null) {
            for (int i = 0; i <= 1; i++) {
                b.set(ox + DOOR_X + i, oy + 1, oz + YARD_END_Z, gate(gate, BlockFace.SOUTH));
                b.set(ox, oy + 1, oz + GATE_Z + i, gate(gate, BlockFace.WEST));
                b.set(ox + SIZE_X - 1, oy + 1, oz + GATE_Z + i, gate(gate, BlockFace.EAST));
            }
        }
        BlockData lantern = lantern(false);
        for (int[] l : new int[][]{{0, YARD_END_Z}, {SIZE_X - 1, YARD_END_Z}, {DOOR_X - 1, YARD_END_Z},
                {DOOR_X + 2, YARD_END_Z}, {0, GATE_Z - 1}, {0, GATE_Z + 2}, {SIZE_X - 1, GATE_Z - 1},
                {SIZE_X - 1, GATE_Z + 2}, {0, SIZE_Z}, {SIZE_X - 1, SIZE_Z}, {6, YARD_END_Z}, {23, YARD_END_Z}}) {
            b.set(ox + l[0], oy + 2, oz + l[1], lantern);
        }

        // Mangal: three campfires between stone ends, a smoker and a barrel of supplies next to it
        BlockData end = p.base.createBlockData();
        b.set(ox + 19, oy + 1, oz + 30, end);
        for (int x = 20; x <= 22; x++) {
            b.set(ox + x, oy + 1, oz + 30, campfire());
        }
        b.set(ox + 23, oy + 1, oz + 30, end);
        b.set(ox + 24, oy + 1, oz + 30, facing(Material.SMOKER, BlockFace.SOUTH));
        b.set(ox + 25, oy + 1, oz + 30, facing(Material.BARREL, BlockFace.UP));
    }

    static void fillDown(Blocks b, int x, int startY, int z, BlockData base) {
        for (int y = startY; y > startY - 8; y--) {
            Material type = b.type(x, y, z);
            if (type.isSolid() && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type)) {
                return;
            }
            b.set(x, y, z, base);
        }
    }

    /**
     * Double door at (x, z) and the next cell along the wall (+x for doors facing north/south, +z for east/west),
     * hinged on the outer sides so the leaves open apart. {@code facing} points into the room, the way a player
     * walks in; y is the lower half.
     */
    static void doubleDoor(Blocks b, int x, int y, int z, BlockFace facing, Material material) {
        boolean alongX = facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
        // Seen walking in, the lower-coordinate leaf is on the left when facing north or east
        boolean firstLeft = facing == BlockFace.NORTH || facing == BlockFace.EAST;
        for (int leaf = 0; leaf <= 1; leaf++) {
            int lx = alongX ? x + leaf : x;
            int lz = alongX ? z : z + leaf;
            Door.Hinge hinge = (leaf == 0) == firstLeft ? Door.Hinge.LEFT : Door.Hinge.RIGHT;
            b.set(lx, y, lz, door(material, facing, Bisected.Half.BOTTOM, hinge));
            b.set(lx, y + 1, lz, door(material, facing, Bisected.Half.TOP, hinge));
        }
    }

    static BlockData stairs(Material material, BlockFace face) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Stairs s) {
                s.setFacing(face);
                s.setHalf(Bisected.Half.BOTTOM);
            }
        });
    }

    static BlockData slab(Material material) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Slab s) {
                s.setType(Slab.Type.BOTTOM);
            }
        });
    }

    private static BlockData door(Material material, BlockFace face, Bisected.Half half, Door.Hinge hinge) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Door door) {
                door.setFacing(face);
                door.setHalf(half);
                door.setHinge(hinge);
            }
        });
    }

    static BlockData lantern(boolean hanging) {
        return Bukkit.createBlockData(Material.LANTERN, d -> {
            if (d instanceof Lantern l) {
                l.setHanging(hanging);
            }
        });
    }

    static BlockData facing(Material material, BlockFace face) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Directional dir) {
                dir.setFacing(face);
            }
        });
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

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
