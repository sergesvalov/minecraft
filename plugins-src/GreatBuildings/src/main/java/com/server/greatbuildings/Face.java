package com.server.greatbuildings;

import java.util.Locale;

/** Horizontal direction. x grows to the east, z grows to the south (Minecraft axes). */
enum Face {
    NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);

    final int dx;
    final int dz;

    Face(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    Face opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
