package com.server.greatbuildings.model;

/** Ground height at plan (x, z), relative to the build's anchor y = 0. */
@FunctionalInterface
public interface Terrain {

    /** No solid ground found within the search depth. */
    int NONE = Integer.MIN_VALUE;

    int groundY(int x, int z);
}

