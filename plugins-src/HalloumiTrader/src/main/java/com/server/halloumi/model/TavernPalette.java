package com.server.halloumi.model;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bukkit.Material.*;

/** Building materials of a tavern, picked by biome so it looks like it belongs there (like village variants). */
public enum TavernPalette {
    OAK(COBBLESTONE, OAK_PLANKS, OAK_LOG, SPRUCE_PLANKS, SPRUCE_STAIRS, SPRUCE_SLAB,
            SPRUCE_PLANKS, OAK_STAIRS, OAK_FENCE, OAK_DOOR, OAK_WALL_SIGN,
            List.of("plains", "sunflower_plains", "meadow", "forest", "flower_forest")),
    SPRUCE(COBBLESTONE, SPRUCE_PLANKS, SPRUCE_LOG, DARK_OAK_PLANKS, DARK_OAK_STAIRS, DARK_OAK_SLAB,
            DARK_OAK_PLANKS, SPRUCE_STAIRS, SPRUCE_FENCE, SPRUCE_DOOR, SPRUCE_WALL_SIGN,
            List.of("taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_plains", "grove")),
    BIRCH(COBBLESTONE, BIRCH_PLANKS, BIRCH_LOG, OAK_PLANKS, DARK_OAK_STAIRS, DARK_OAK_SLAB,
            OAK_PLANKS, BIRCH_STAIRS, BIRCH_FENCE, BIRCH_DOOR, BIRCH_WALL_SIGN,
            List.of("birch_forest", "old_growth_birch_forest")),
    ACACIA(TERRACOTTA, ACACIA_PLANKS, ACACIA_LOG, ACACIA_PLANKS, ACACIA_STAIRS, ACACIA_SLAB,
            ACACIA_PLANKS, ACACIA_STAIRS, ACACIA_FENCE, ACACIA_DOOR, ACACIA_WALL_SIGN,
            List.of("savanna", "savanna_plateau")),
    SANDSTONE(Material.SANDSTONE, SMOOTH_SANDSTONE, CUT_SANDSTONE, SMOOTH_SANDSTONE, SANDSTONE_STAIRS, SANDSTONE_SLAB,
            CUT_SANDSTONE, JUNGLE_STAIRS, JUNGLE_FENCE, JUNGLE_DOOR, JUNGLE_WALL_SIGN,
            List.of("desert")),
    CHERRY(STONE_BRICKS, CHERRY_PLANKS, CHERRY_LOG, SPRUCE_PLANKS, SPRUCE_STAIRS, SPRUCE_SLAB,
            SPRUCE_PLANKS, CHERRY_STAIRS, CHERRY_FENCE, CHERRY_DOOR, CHERRY_WALL_SIGN,
            List.of("cherry_grove")),
    DARK_OAK(MOSSY_COBBLESTONE, DARK_OAK_PLANKS, DARK_OAK_LOG, SPRUCE_PLANKS, DEEPSLATE_TILE_STAIRS, DEEPSLATE_TILE_SLAB,
            SPRUCE_PLANKS, DARK_OAK_STAIRS, DARK_OAK_FENCE, DARK_OAK_DOOR, DARK_OAK_WALL_SIGN,
            List.of("dark_forest", "pale_garden")),
    JUNGLE(MOSSY_COBBLESTONE, JUNGLE_PLANKS, JUNGLE_LOG, OAK_PLANKS, JUNGLE_STAIRS, JUNGLE_SLAB,
            JUNGLE_PLANKS, JUNGLE_STAIRS, JUNGLE_FENCE, JUNGLE_DOOR, JUNGLE_WALL_SIGN,
            List.of("jungle", "sparse_jungle", "bamboo_jungle"));

    public final Material base;
    public final Material wall;
    public final Material post;
    public final Material floor;
    public final Material roofStairs;
    public final Material roofSlab;
    public final Material counter;
    public final Material chairStairs;
    public final Material fence;
    public final Material door;
    public final Material wallSign;
    private final List<String> biomes;

    private static final Map<String, TavernPalette> BY_BIOME = new HashMap<>();

    static {
        for (TavernPalette palette : values()) {
            for (String biome : palette.biomes) {
                BY_BIOME.put(biome, palette);
            }
        }
    }

    TavernPalette(Material base, Material wall, Material post, Material floor, Material roofStairs, Material roofSlab,
                  Material counter, Material chairStairs, Material fence, Material door, Material wallSign,
                  List<String> biomes) {
        this.base = base;
        this.wall = wall;
        this.post = post;
        this.floor = floor;
        this.roofStairs = roofStairs;
        this.roofSlab = roofSlab;
        this.counter = counter;
        this.chairStairs = chairStairs;
        this.fence = fence;
        this.door = door;
        this.wallSign = wallSign;
        this.biomes = biomes;
    }

    /** @param biomeKey biome key without namespace, e.g. "plains"; returns null where taverns don't belong */
    public static TavernPalette forBiome(String biomeKey) {
        return BY_BIOME.get(biomeKey);
    }

    /** The palette whose wall material this is (every palette has its own), or null. */
    public static TavernPalette byWall(Material wall) {
        for (TavernPalette palette : values()) {
            if (palette.wall == wall) {
                return palette;
            }
        }
        return null;
    }
}

