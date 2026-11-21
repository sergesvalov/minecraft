package com.server.halloumi.tavern;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/**
 * Bukkit {@link BlockData} builders used when constructing taverns.
 */
final class BlockStates {

    private BlockStates() {
    }

    /**
     * Double door at (x, z) and the next cell along the wall (+x for doors facing north/south, +z for east/west),
     * hinged on the outer sides so the leaves open apart. {@code facing} points into the room, the way a player
     * walks in; y is the lower half.
     */
    static void doubleDoor(BlockGrid b, int x, int y, int z, BlockFace facing, Material material) {
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

    static BlockData door(Material material, BlockFace face, Bisected.Half half, Door.Hinge hinge) {
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

    static BlockData fence(Material material, boolean west, boolean east, boolean north, boolean south) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof MultipleFacing f) {
                f.setFace(BlockFace.WEST, west);
                f.setFace(BlockFace.EAST, east);
                f.setFace(BlockFace.NORTH, north);
                f.setFace(BlockFace.SOUTH, south);
            }
        });
    }

    static BlockData gate(Material material, BlockFace face) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Gate g) {
                g.setFacing(face);
                g.setOpen(false);
            }
        });
    }

    static BlockData bed(Material material, BlockFace face, Bed.Part part) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Bed bed) {
                bed.setFacing(face);
                bed.setPart(part);
            }
        });
    }

    static BlockData campfire() {
        return Bukkit.createBlockData(Material.CAMPFIRE, d -> {
            if (d instanceof Campfire c) {
                c.setFacing(BlockFace.SOUTH);
                c.setLit(true);
                c.setSignalFire(false);
            }
        });
    }

    static BlockData axis(Material material, Axis axis) {
        return Bukkit.createBlockData(material, d -> {
            if (d instanceof Orientable o) {
                o.setAxis(axis);
            }
        });
    }
}

