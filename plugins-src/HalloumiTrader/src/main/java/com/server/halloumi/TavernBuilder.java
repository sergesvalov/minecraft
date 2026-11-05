package com.server.halloumi;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.Axis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the tavern. Used both from world generation (through a LimitedRegion) and from the
 * /halloumi tavern command (through the live world), so block access goes through {@link Blocks}.
 *
 * <p>Layout, relative to the origin (ox, oy, oz) = floor corner; the door faces south (+z):
 * walls x 0..10, z 0..8; floor at oy; roof with overhang x -1..11, z -1..9, ridge at oy+10.
 */
final class TavernBuilder {

    /** Minimal block access shared by LimitedRegion (worldgen) and World (command). */
    interface Blocks {
        Material type(int x, int y, int z);

        void set(int x, int y, int z, BlockData data);

        int highestY(int x, int z);
    }

    static final int SIZE_X = 11;
    static final int SIZE_Z = 9;
    /** Result of {@link #findFloorY} when the spot is unsuitable. */
    static final int NO_SPOT = Integer.MIN_VALUE;

    // Positions inside the tavern, relative to the origin
    static final double TRADER_X = 5.5, TRADER_Z = 2.5;
    static final double[][] GUEST_SPOTS = {{5.5, 5.5}, {2.5, 4.5}, {8.5, 4.5}};
    static final int SIGN_X = 5, SIGN_Y = 3, SIGN_Z = 9;
    /** Lectern with the menu book, in the front-left corner by the door. */
    static final int LECTERN_X = 1, LECTERN_Y = 1, LECTERN_Z = 7;

    private static final Set<Material> NATURAL_GROUND = new HashSet<>();

    static {
        for (String name : List.of("GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL", "ROOTED_DIRT", "MOSS_BLOCK",
                "PALE_MOSS_BLOCK", "SAND", "RED_SAND", "SNOW_BLOCK", "STONE", "GRAVEL", "MUD")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                NATURAL_GROUND.add(material);
            }
        }
    }

    private TavernBuilder() {
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
     * Checks that the footprint is natural, dry and flat enough (so the tavern doesn't land on a village,
     * a ruin or a cliff) and returns the floor level, or NO_SPOT.
     */
    static int findFloorY(Blocks blocks, int ox, int oz, int minY, int maxSlope) {
        int[][] samples = {
                {0, 0}, {SIZE_X - 1, 0}, {0, SIZE_Z - 1}, {SIZE_X - 1, SIZE_Z - 1},
                {5, 0}, {5, SIZE_Z - 1}, {0, 4}, {SIZE_X - 1, 4}, {5, 4}, {5, SIZE_Z}
        };
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] s : samples) {
            int y = groundY(blocks, ox + s[0], oz + s[1], minY);
            if (y == NO_SPOT || !NATURAL_GROUND.contains(blocks.type(ox + s[0], y, oz + s[1]))) {
                return NO_SPOT;
            }
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (max - min > maxSlope) {
            return NO_SPOT;
        }
        return max;
    }

    static void build(Blocks b, int ox, int oy, int oz, TavernPalette p) {
        BlockData air = Material.AIR.createBlockData();

        // Clear the volume (trees, grass, terrain bumps)
        for (int x = -1; x <= SIZE_X; x++) {
            for (int z = -1; z <= SIZE_Z; z++) {
                for (int y = 1; y <= 12; y++) {
                    b.set(ox + x, oy + y, oz + z, air);
                }
            }
        }

        // Foundation down to the ground, and the floor
        BlockData base = p.base.createBlockData();
        BlockData floor = p.floor.createBlockData();
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                boolean edge = x == 0 || z == 0 || x == SIZE_X - 1 || z == SIZE_Z - 1;
                b.set(ox + x, oy, oz + z, edge ? base : floor);
                fillDown(b, ox + x, oy - 1, oz + z, base);
            }
        }
        // Doorstep
        b.set(ox + 5, oy, oz + SIZE_Z, base);
        fillDown(b, ox + 5, oy - 1, oz + SIZE_Z, base);

        // Walls: stone bottom row, planks above, logs in the corners. Front/back walls go one higher under the roof.
        BlockData wall = p.wall.createBlockData();
        BlockData post = axisY(p.post);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                boolean sideWall = x == 0 || x == SIZE_X - 1;
                boolean frontBack = z == 0 || z == SIZE_Z - 1;
                if (!sideWall && !frontBack) {
                    continue;
                }
                boolean corner = sideWall && frontBack;
                int top = frontBack ? 5 : 4;
                for (int y = 1; y <= top; y++) {
                    BlockData data = corner && y <= 4 ? post : (y == 1 ? base : wall);
                    b.set(ox + x, oy + y, oz + z, data);
                }
            }
        }

        // Gable roof: stairs from both eaves up to a slab ridge; gable ends filled with planks
        BlockData southSlope = stairs(p.roofStairs, BlockFace.NORTH);
        BlockData northSlope = stairs(p.roofStairs, BlockFace.SOUTH);
        for (int i = 0; i < 5; i++) {
            int y = oy + 5 + i;
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
            b.set(ox + x, oy + 10, oz + 4, ridge);
        }

        // Door and windows
        b.set(ox + 5, oy + 1, oz + 8, door(p.door, Bisected.Half.BOTTOM));
        b.set(ox + 5, oy + 2, oz + 8, door(p.door, Bisected.Half.TOP));
        BlockData glass = Material.GLASS.createBlockData();
        for (int[] w : new int[][]{{4, 0}, {6, 0}, {2, 8}, {3, 8}, {7, 8}, {8, 8}, {0, 3}, {0, 5}, {10, 3}, {10, 5}}) {
            b.set(ox + w[0], oy + 2, oz + w[1], glass);
        }

        // Bar counter with the innkeeper behind it, barrels and a smoker along the back wall
        BlockData counter = p.counter.createBlockData();
        for (int x = 1; x <= 7; x++) {
            b.set(ox + x, oy + 1, oz + 3, counter);
        }
        BlockData lantern = lantern(false);
        b.set(ox + 2, oy + 2, oz + 3, lantern);
        b.set(ox + 7, oy + 2, oz + 3, lantern);
        BlockData barrel = facing(Material.BARREL, BlockFace.UP);
        for (int x : new int[]{2, 3, 7, 8, 9}) {
            b.set(ox + x, oy + 1, oz + 1, barrel);
            b.set(ox + x, oy + 2, oz + 1, barrel);
        }
        b.set(ox + 1, oy + 1, oz + 1, facing(Material.SMOKER, BlockFace.SOUTH));

        // Two tables (fence + carpet) with stair chairs
        BlockData fence = p.fence.createBlockData();
        BlockData carpet = Material.RED_CARPET.createBlockData();
        for (int tx : new int[]{3, 7}) {
            b.set(ox + tx, oy + 1, oz + 6, fence);
            b.set(ox + tx, oy + 2, oz + 6, carpet);
            b.set(ox + tx - 1, oy + 1, oz + 6, stairs(p.chairStairs, BlockFace.WEST));
            b.set(ox + tx + 1, oy + 1, oz + 6, stairs(p.chairStairs, BlockFace.EAST));
        }

        // Lanterns under the ridge keep the floor lit, so no mobs spawn inside
        BlockData hanging = lantern(true);
        b.set(ox + 3, oy + 9, oz + 4, hanging);
        b.set(ox + 7, oy + 9, oz + 4, hanging);

        // Sign above the door and the menu lectern. Their text and book are added once the chunk is loaded:
        // block entities can't be filled during world generation.
        b.set(ox + SIGN_X, oy + SIGN_Y, oz + SIGN_Z, facing(p.wallSign, BlockFace.SOUTH));
        b.set(ox + LECTERN_X, oy + LECTERN_Y, oz + LECTERN_Z, facing(Material.LECTERN, BlockFace.EAST));
    }

    private static void fillDown(Blocks b, int x, int startY, int z, BlockData base) {
        for (int y = startY; y > startY - 8; y--) {
            Material type = b.type(x, y, z);
            if (type.isSolid() && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type)) {
                return;
            }
            b.set(x, y, z, base);
        }
    }

    private static BlockData stairs(Material material, BlockFace face) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Stairs s) {
                s.setFacing(face);
                s.setHalf(Bisected.Half.BOTTOM);
            }
        });
    }

    private static BlockData slab(Material material) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Slab s) {
                s.setType(Slab.Type.BOTTOM);
            }
        });
    }

    private static BlockData door(Material material, Bisected.Half half) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Door door) {
                door.setFacing(BlockFace.NORTH);
                door.setHalf(half);
                door.setHinge(Door.Hinge.LEFT);
            }
        });
    }

    private static BlockData lantern(boolean hanging) {
        return Bukkit.createBlockData(Material.LANTERN, d -> {
            if (d instanceof Lantern l) {
                l.setHanging(hanging);
            }
        });
    }

    private static BlockData facing(Material material, BlockFace face) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Directional dir) {
                dir.setFacing(face);
            }
        });
    }

    private static BlockData axisY(Material material) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Orientable o) {
                o.setAxis(Axis.Y);
            }
        });
    }
}
