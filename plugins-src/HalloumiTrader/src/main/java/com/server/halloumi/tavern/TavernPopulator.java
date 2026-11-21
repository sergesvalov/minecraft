package com.server.halloumi.tavern;

import com.server.halloumi.model.Tavern;
import com.server.halloumi.model.TavernPalette;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Places taverns into newly generated chunks, like a vanilla "random spread" structure:
 * the world is split into cells of {@code spacing x spacing} chunks, each cell gets at most one
 * candidate chunk (deterministic from the world seed), and a tavern is planned there only if the
 * roll succeeds and the terrain and biome fit; it is built later, in the live world.
 */
public final class TavernPopulator extends BlockPopulator {

    /** Keep candidates away from cell borders so two taverns are never right next to each other. */
    static final int SEPARATION = 8;

    private final Set<String> worlds;
    private final int spacing;
    private final double chance;
    private final int maxSlope;
    private final Consumer<Tavern> onPlaced;

    public TavernPopulator(Set<String> worlds, int spacing, double chance, int maxSlope, Consumer<Tavern> onPlaced) {
        this.worlds = worlds;
        this.spacing = Math.max(SEPARATION + 2, spacing);
        this.chance = chance;
        this.maxSlope = maxSlope;
        this.onPlaced = onPlaced;
    }

    public boolean supportsWorld(String worldName) {
        return worlds.contains(worldName);
    }

    /** Deterministic seed-based random-spread candidate selection for a chunk. */
    public static boolean isCandidateChunk(long worldSeed, int chunkX, int chunkZ, int spacing, double chance) {
        int effectiveSpacing = Math.max(SEPARATION + 2, spacing);
        int cellX = Math.floorDiv(chunkX, effectiveSpacing);
        int cellZ = Math.floorDiv(chunkZ, effectiveSpacing);
        Random cellRandom = new Random(worldSeed ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L) ^ 0x4A1L);
        if (cellRandom.nextDouble() >= chance) {
            return false;
        }
        int candidateX = cellX * effectiveSpacing + cellRandom.nextInt(effectiveSpacing - SEPARATION);
        int candidateZ = cellZ * effectiveSpacing + cellRandom.nextInt(effectiveSpacing - SEPARATION);
        return chunkX == candidateX && chunkZ == candidateZ;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (worldInfo.getEnvironment() != World.Environment.NORMAL || !supportsWorld(worldInfo.getName())) {
            return;
        }
        if (!isCandidateChunk(worldInfo.getSeed(), chunkX, chunkZ, spacing, chance)) {
            return;
        }

        int ox = (chunkX << 4) + 2;
        int oz = (chunkZ << 4) + 2;
        BlockGrid blocks = BlockGrid.ofRegion(region);

        int surface = region.getHighestBlockYAt(ox + 5, oz + 4);
        Biome biome = region.getBiome(ox + 5, surface, oz + 4);
        TavernPalette palette = TavernPalette.forBiome(biome.getKey().getKey());
        if (palette == null) {
            return;
        }
        int oy = TavernSite.findFloorY(blocks, ox, oz, worldInfo.getMinHeight(), maxSlope);
        if (oy == TavernLayout.NO_SPOT || oy + 1 + TavernLayout.HEIGHT >= worldInfo.getMaxHeight()) {
            return;
        }
        onPlaced.accept(new Tavern(worldInfo.getName(), ox, oy + 1, oz, false, Tavern.State.PENDING));
    }
}

