package com.server.greatbuildings.model;

/** Cardinal direction in plan coordinates (x east, z south). */
public enum Face {
    NORTH(0, -1),
    SOUTH(0, 1),
    WEST(-1, 0),
    EAST(1, 0);

    public final int dx;
    public final int dz;

    Face(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public String id() {
        return name().toLowerCase();
    }

    public Face opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}

