package com.server.halloumi.tavern;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.LimitedRegion;

/**
 * Minimal block access abstraction for both the live {@link World} and a {@link LimitedRegion} during generation.
 */
public interface BlockGrid {

    Material type(int x, int y, int z);

    void set(int x, int y, int z, BlockData data);

    int highestY(int x, int z);

    /** Live world access without physics updates: doors, beds and lanterns are placed piece by piece. */
    static BlockGrid ofWorld(World world) {
        return new BlockGrid() {
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

    /** Read-only region access during world generation threads. */
    static BlockGrid ofRegion(LimitedRegion region) {
        return new BlockGrid() {
            @Override
            public Material type(int x, int y, int z) {
                return region.getType(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, BlockData data) {
                throw new UnsupportedOperationException("taverns are not built during world generation");
            }

            @Override
            public int highestY(int x, int z) {
                return region.getHighestBlockYAt(x, z);
            }
        };
    }
}

