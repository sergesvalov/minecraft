package com.server.halloumi.model;

import com.server.halloumi.tavern.TavernLayout;

import java.util.Locale;

/**
 * A tavern in the world. {@code (x, y, z)} is the floor corner, see {@link TavernLayout}.
 * {@code state} tracks whether it is built, pending build, or a pre-1.4 tavern to tear down.
 */
public record Tavern(String world, int x, int y, int z, boolean decorated, State state) {

    public enum State {
        /** Spot picked during world generation; built once the chunks around it are loaded and the site fits. */
        PENDING("inn-pending"),
        BUILT("inn"),
        /** Old one-room tavern without a guesthouse (before 1.4): torn down once its chunks are loaded. */
        OLD("none"),
        /** Old tavern with the guesthouse east of it. */
        OLD_EAST("east"),
        /** Old tavern with the guesthouse west of it. */
        OLD_WEST("west");

        public final String id;

        State(String id) {
            this.id = id;
        }

        public boolean old() {
            return this == OLD || this == OLD_EAST || this == OLD_WEST;
        }

        /** Old files have "pending", "none", "east", "west" or nothing in this column: all old taverns. */
        public static State parse(String id) {
            for (State state : values()) {
                if (state.id.equals(id)) {
                    return state;
                }
            }
            return OLD;
        }
    }

    public Tavern withDecorated() {
        return new Tavern(world, x, y, z, true, state);
    }

    public Tavern built(int floorY) {
        return new Tavern(world, x, floorY, z, false, State.BUILT);
    }

    /** Chunk in the middle of the house: innkeeper and guests are only handled while it is loaded. */
    public int chunkX() {
        return (x + TavernLayout.SIZE_X / 2) >> 4;
    }

    public int chunkZ() {
        return (z + TavernLayout.SIZE_Z / 2) >> 4;
    }

    public double centerX() {
        return x + TavernLayout.SIZE_X / 2.0;
    }

    public double centerZ() {
        return z + TavernLayout.SIZE_Z / 2.0;
    }

    public String serialize() {
        return world + "," + x + "," + y + "," + z + "," + decorated + "," + state.id;
    }

    public static Tavern parse(String line) {
        String[] p = line.split(",");
        if (p.length < 4) {
            throw new IllegalArgumentException("Expected at least 4 comma-separated fields: " + line);
        }
        State state = State.parse(p.length > 5 ? p[5].trim().toLowerCase(Locale.ROOT) : "");
        return new Tavern(
                p[0].trim(),
                Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()),
                Integer.parseInt(p[3].trim()),
                p.length > 4 && Boolean.parseBoolean(p[4].trim()),
                state
        );
    }
}

