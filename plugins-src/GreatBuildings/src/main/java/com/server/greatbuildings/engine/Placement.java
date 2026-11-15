package com.server.greatbuildings.engine;

import com.server.greatbuildings.model.Building;
import org.bukkit.block.structure.StructureRotation;

/**
 * Pure coordinate transformation utilities for orienting a {@link Building} plan in front of a player.
 */
public final class Placement {

    private Placement() {
    }

    /** Maps a Bukkit player yaw angle to the nearest 90-degree structure rotation facing away from the player. */
    public static StructureRotation rotationFromYaw(float yaw) {
        int dir = Math.floorMod(Math.round(yaw / 90.0f), 4);
        return switch (dir) {
            case 0 -> StructureRotation.CLOCKWISE_180;      // facing south (+z)
            case 1 -> StructureRotation.COUNTERCLOCKWISE_90; // facing west (-x)
            case 3 -> StructureRotation.CLOCKWISE_90;        // facing east (+x)
            default -> StructureRotation.NONE;               // facing north (-z)
        };
    }

    public static String directionLabel(StructureRotation rotation) {
        return switch (rotation) {
            case NONE -> "на север";
            case CLOCKWISE_90 -> "на восток";
            case CLOCKWISE_180 -> "на юг";
            case COUNTERCLOCKWISE_90 -> "на запад";
        };
    }

    /** Computes rotated world {minX, minZ, maxX, maxZ} for a building placed at the given player anchor. */
    public static int[] worldBounds(Building building, int anchorWorldX, int anchorWorldZ, StructureRotation rotation) {
        int[] b = building.bounds();
        int[] a = building.anchor();
        int[] xs = {b[0], b[3]};
        int[] zs = {b[2], b[5]};
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int lx : xs) {
            for (int lz : zs) {
                int wx = toWorldX(lx, lz, a[0], a[1], anchorWorldX, rotation);
                int wz = toWorldZ(lx, lz, a[0], a[1], anchorWorldZ, rotation);
                minX = Math.min(minX, wx);
                minZ = Math.min(minZ, wz);
                maxX = Math.max(maxX, wx);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    public static int toWorldX(int lx, int lz, int ax, int az, int anchorWorldX, StructureRotation rot) {
        int dx = lx - ax;
        int dz = lz - az;
        return anchorWorldX + switch (rot) {
            case NONE -> dx;
            case CLOCKWISE_90 -> -dz;
            case CLOCKWISE_180 -> -dx;
            case COUNTERCLOCKWISE_90 -> dz;
        };
    }

    public static int toWorldZ(int lx, int lz, int ax, int az, int anchorWorldZ, StructureRotation rot) {
        int dx = lx - ax;
        int dz = lz - az;
        return anchorWorldZ + switch (rot) {
            case NONE -> dz;
            case CLOCKWISE_90 -> dx;
            case CLOCKWISE_180 -> -dz;
            case COUNTERCLOCKWISE_90 -> -dx;
        };
    }
}

