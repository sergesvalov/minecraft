package com.server.halloumi;

import java.util.Locale;

/**
 * A tavern in the world. (x, y, z) is the floor corner, see {@link TavernBuilder}.
 * {@code state} says whether it is built, still waiting to be built, or an old (before 1.4) tavern to tear down.
 */
record Tavern(String world, int x, int y, int z, boolean decorated, State state) {

    enum State {
        /** Spot picked during world generation; built once the chunks around it are loaded and the site fits. */
        PENDING("inn-pending"),
        BUILT("inn"),
        /** Old one-room tavern without a guesthouse (before 1.4): torn down once its chunks are loaded. */
        OLD("none"),
        /** Old tavern with the guesthouse east of it. */
        OLD_EAST("east"),
        /** Old tavern with the guesthouse west of it. */
        OLD_WEST("west");

        final String id;

        State(String id) {
            this.id = id;
        }

        boolean old() {
            return this == OLD || this == OLD_EAST || this == OLD_WEST;
        }

        /** Old files have "pending", "none", "east", "west" or nothing in this column: all old taverns. */
        static State parse(String id) {
            for (State state : values()) {
                if (state.id.equals(id)) {
                    return state;
                }
            }
            return OLD;
        }
    }

    Tavern withDecorated() {
        return new Tavern(world, x, y, z, true, state);
    }

    Tavern built(int floorY) {
        return new Tavern(world, x, floorY, z, false, State.BUILT);
    }

    /** Chunk in the middle of the house: innkeeper and guests are only handled while it is loaded. */
    int chunkX() {
        return (x + TavernBuilder.SIZE_X / 2) >> 4;
    }

    int chunkZ() {
        return (z + TavernBuilder.SIZE_Z / 2) >> 4;
    }

    double centerX() {
        return x + TavernBuilder.SIZE_X / 2.0;
    }

    double centerZ() {
        return z + TavernBuilder.SIZE_Z / 2.0;
    }

    String serialize() {
        return world + "," + x + "," + y + "," + z + "," + decorated + "," + state.id;
    }

    static Tavern parse(String line) {
        String[] p = line.split(",");
        State state = State.parse(p.length > 5 ? p[5].trim().toLowerCase(Locale.ROOT) : "");
        return new Tavern(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                p.length > 4 && Boolean.parseBoolean(p[4]), state);
    }
}
