package com.server.halloumi;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Places taverns into newly generated chunks, like a vanilla "random spread" structure:
 * the world is split into cells of {@code spacing x spacing} chunks, each cell gets at most one
 * candidate chunk (deterministic from the world seed), and the tavern is built there only if the
 * roll succeeds and the terrain and biome fit. Runs on world generation threads, so it only
 * touches the LimitedRegion and immutable settings.
 */
final class TavernPopulator extends BlockPopulator {

    /** Keep candidates away from cell borders so two taverns are never right next to each other. */
    private static final int SEPARATION = 8;

    private final Set<String> worlds;
    private final int spacing;
    private final double chance;
    private final int maxSlope;
    private final Consumer<Tavern> onPlaced;

    TavernPopulator(Set<String> worlds, int spacing, double chance, int maxSlope, Consumer<Tavern> onPlaced) {
        this.worlds = worlds;
        this.spacing = Math.max(SEPARATION + 2, spacing);
        this.chance = chance;
        this.maxSlope = maxSlope;
        this.onPlaced = onPlaced;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (worldInfo.getEnvironment() != World.Environment.NORMAL || !worlds.contains(worldInfo.getName())) {
            return;
        }
        int cellX = Math.floorDiv(chunkX, spacing);
        int cellZ = Math.floorDiv(chunkZ, spacing);
        Random cellRandom = new Random(worldInfo.getSeed() ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L) ^ 0x4A1L);
        if (cellRandom.nextDouble() >= chance) {
            return;
        }
        int candidateX = cellX * spacing + cellRandom.nextInt(spacing - SEPARATION);
        int candidateZ = cellZ * spacing + cellRandom.nextInt(spacing - SEPARATION);
        if (chunkX != candidateX || chunkZ != candidateZ) {
            return;
        }

        // Origin inside the chunk so the whole tavern incl. roof overhang stays in this chunk
        int ox = (chunkX << 4) + 2;
        int oz = (chunkZ << 4) + 2;
        TavernBuilder.Blocks blocks = blocks(region);

        int surface = region.getHighestBlockYAt(ox + 5, oz + 4);
        Biome biome = region.getBiome(ox + 5, surface, oz + 4);
        TavernPalette palette = TavernPalette.forBiome(biome.getKey().getKey());
        if (palette == null) {
            return;
        }
        int oy = TavernBuilder.findFloorY(blocks, ox, oz, worldInfo.getMinHeight(), maxSlope);
        if (oy == TavernBuilder.NO_SPOT || oy + 12 >= worldInfo.getMaxHeight()) {
            return;
        }
        oy += 1;
        TavernBuilder.build(blocks, ox, oy, oz, palette);
        onPlaced.accept(new Tavern(worldInfo.getName(), ox, oy, oz, false, Tavern.Annex.PENDING));
    }

    private static TavernBuilder.Blocks blocks(LimitedRegion region) {
        return new TavernBuilder.Blocks() {
            @Override
            public Material type(int x, int y, int z) {
                return region.getType(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, BlockData data) {
                if (region.isInRegion(x, y, z)) {
                    region.setBlockData(x, y, z, data);
                }
            }

            @Override
            public int highestY(int x, int z) {
                return region.getHighestBlockYAt(x, z);
            }
        };
    }
}
