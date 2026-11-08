package com.server.greatbuildings;

/** Ground under the plan, read from the world before drawing. */
interface Terrain {

    int NONE = Integer.MIN_VALUE;

    /**
     * Top of the solid natural ground at (x, z) relative to the building's ground level (0 = level with it,
     * -3 = three blocks lower). Water, plants, leaves and logs don't count. {@link #NONE} if nothing found.
     */
    int groundY(int x, int z);
}
