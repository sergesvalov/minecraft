package com.server.halloumi;

/** A tavern in the world. (x, y, z) is the floor corner, see {@link TavernBuilder}. */
record Tavern(String world, int x, int y, int z, boolean decorated) {

    Tavern withDecorated() {
        return new Tavern(world, x, y, z, true);
    }

    int chunkX() {
        return (x + 5) >> 4;
    }

    int chunkZ() {
        return (z + 4) >> 4;
    }

    double centerX() {
        return x + 5.5;
    }

    double centerZ() {
        return z + 4.5;
    }

    String serialize() {
        return world + "," + x + "," + y + "," + z + "," + decorated;
    }

    static Tavern parse(String line) {
        String[] p = line.split(",");
        return new Tavern(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                p.length > 4 && Boolean.parseBoolean(p[4]));
    }
}
