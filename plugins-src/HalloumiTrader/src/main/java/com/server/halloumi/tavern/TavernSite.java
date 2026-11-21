package com.server.halloumi.tavern;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Terrain suitability scanning and foundation filling for tavern sites.
 */
final class TavernSite {

    /** Ground under the site may be this much below / above the floor; the builder levels it. */
    private static final int MAX_DROP = 6;
    private static final int MAX_RISE = 4;

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
    }

    private TavernSite() {
    }

    static boolean isNaturalGround(Material type) {
        return NATURAL_GROUND.contains(type);
    }

    /** Topmost ground block, skipping air, plants, snow layers, leaves and tree trunks. NO_SPOT on water/lava. */
    static int groundY(BlockGrid blocks, int x, int z, int minY) {
        for (int y = blocks.highestY(x, z); y > minY; y--) {
            Material type = blocks.type(x, y, z);
            if (type == Material.WATER || type == Material.LAVA) {
                return TavernLayout.NO_SPOT;
            }
            if (type.isAir() || !type.isSolid() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
                continue;
            }
            return y;
        }
        return TavernLayout.NO_SPOT;
    }

    /**
     * Quick check during world generation, within one chunk: natural, dry and flat enough ground at (ox, oz)
     * and the next 12 blocks east and south. Returns the ground level, or NO_SPOT.
     */
    static int findFloorY(BlockGrid blocks, int ox, int oz, int minY, int maxSlope) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 12; dx += 6) {
            for (int dz = 0; dz <= 12; dz += 6) {
                int y = groundY(blocks, ox + dx, oz + dz, minY);
                if (y == TavernLayout.NO_SPOT || !isNaturalGround(blocks.type(ox + dx, y, oz + dz))) {
                    return TavernLayout.NO_SPOT;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min > maxSlope ? TavernLayout.NO_SPOT : max;
    }

    /**
     * Floor level for a tavern at (ox, oz) in the live world, or NO_SPOT. The site must be dry, roughly level and
     * hold nothing but natural terrain and plants, so building there can't destroy anything a player made.
     */
    static int siteFloorY(BlockGrid b, int ox, int oz, int minY, int maxY) {
        int[] area = TavernLayout.area(ox, oz);
        int width = area[2] - area[0] + 1;
        int depth = area[3] - area[1] + 1;
        int[] grid = new int[width * depth];
        int idx = 0;
        for (int x = area[0]; x <= area[2]; x++) {
            for (int z = area[1]; z <= area[3]; z++) {
                int ground = groundY(b, x, z, minY);
                if (ground == TavernLayout.NO_SPOT || !isNaturalGround(b.type(x, ground, z))) {
                    return TavernLayout.NO_SPOT;
                }
                grid[idx++] = ground;
            }
        }
        int[] sorted = grid.clone();
        Arrays.sort(sorted);
        int oy = sorted[sorted.length / 2] + 1;
        if (oy + TavernLayout.HEIGHT >= maxY) {
            return TavernLayout.NO_SPOT;
        }
        idx = 0;
        for (int x = area[0]; x <= area[2]; x++) {
            for (int z = area[1]; z <= area[3]; z++) {
                int ground = grid[idx++];
                if (ground < oy - 1 - MAX_DROP || ground > oy - 1 + MAX_RISE) {
                    return TavernLayout.NO_SPOT;
                }
                for (int y = ground + 1; y <= oy + TavernLayout.HEIGHT; y++) {
                    if (!clearable(b.type(x, y, z))) {
                        return TavernLayout.NO_SPOT;
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

    static void fillDown(BlockGrid b, int x, int startY, int z, BlockData base) {
        for (int y = startY; y > startY - 8; y--) {
            Material type = b.type(x, y, z);
            if (type.isSolid() && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type)) {
                return;
            }
            b.set(x, y, z, base);
        }
    }
}

