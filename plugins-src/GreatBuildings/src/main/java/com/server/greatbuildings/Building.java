package com.server.greatbuildings;

import java.util.List;

/** A famous building that can be built in the world. Implementations only draw into a {@link Plan}. */
public interface Building {

    /** Id for commands, e.g. "svetitskhoveli". */
    String id();

    /** Country / region of the building. */
    Country country();

    /** Name for players. */
    String title();

    /** One line for /gb list: where it is in the real world. */
    String where();

    /** Plan box relative to the origin: {minX, minY, minZ, maxX, maxY, maxZ}; y = 0 is the ground. */
    int[] bounds();

    /**
     * Where the builder stands, relative to the origin (x, z): the building is placed so that this
     * point is at the player's feet. Must be outside {@link #bounds()} so the player isn't built in.
     */
    int[] anchor();

    /** Draws the building. {@code terrain} tells how high the real ground is, to fill foundations. */
    void draw(Plan plan, Terrain terrain);

    /** Book put on the lecterns of {@link Plan#lecterns}. */
    Book book();

    record Book(String title, String author, List<String> pages) {
    }
}
