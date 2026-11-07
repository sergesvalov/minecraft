package com.server.halloumi;

import java.util.Locale;

/**
 * A tavern in the world. (x, y, z) is the floor corner, see {@link TavernBuilder}.
 * {@code annex} says whether and where its guesthouse ({@link GuesthouseBuilder}) stands.
 */
record Tavern(String world, int x, int y, int z, boolean decorated, Annex annex) {

    enum Annex {
        /** Not built yet: built once the chunks around the tavern are loaded and there is room. */
        PENDING,
        EAST,
        WEST,
        /** No guesthouse: no room for it, or a tavern built with /halloumi tavern. */
        NONE
    }

    Tavern withDecorated() {
        return new Tavern(world, x, y, z, true, annex);
    }

    Tavern withAnnex(Annex newAnnex) {
        return new Tavern(world, x, y, z, decorated, newAnnex);
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
        return world + "," + x + "," + y + "," + z + "," + decorated + "," + annex.name().toLowerCase(Locale.ROOT);
    }

    /** Lines written before the guesthouse existed have no annex column: those taverns get one too. */
    static Tavern parse(String line) {
        String[] p = line.split(",");
        Annex annex = p.length > 5 ? Annex.valueOf(p[5].trim().toUpperCase(Locale.ROOT)) : Annex.PENDING;
        return new Tavern(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                p.length > 4 && Boolean.parseBoolean(p[4]), annex);
    }
}
