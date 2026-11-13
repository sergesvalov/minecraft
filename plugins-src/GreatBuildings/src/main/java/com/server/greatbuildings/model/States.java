package com.server.greatbuildings.model;

/**
 * Block states as vanilla strings ("minecraft:oak_stairs[facing=north,half=bottom]").
 * Buildings are drawn with strings so the drawing code doesn't need a running server
 * (the plugin turns them into BlockData once per distinct string when it builds).
 */
public final class States {

    private States() {
    }

    public static String b(String id) {
        return "minecraft:" + id;
    }

    /** Stairs: {@code facing} is the side of the tall back. {@code top} = upside down (for arches). */
    public static String stairs(String id, Face facing, boolean top) {
        return b(id) + "[facing=" + facing.id() + ",half=" + (top ? "top" : "bottom") + "]";
    }

    public static String slab(String id) {
        return b(id) + "[type=bottom]";
    }

    /** Door half; {@code facing} points into the room (the way a player walks in). */
    public static String door(String id, Face facing, boolean upper, boolean rightHinge) {
        return b(id) + "[facing=" + facing.id() + ",half=" + (upper ? "upper" : "lower")
                + ",hinge=" + (rightHinge ? "right" : "left") + "]";
    }

    public static String wallSign(String wood, Face facing) {
        return b(wood + "_wall_sign") + "[facing=" + facing.id() + "]";
    }

    public static String lantern(boolean hanging) {
        return b("lantern") + "[hanging=" + hanging + "]";
    }

    public static String candles(int count) {
        return b("candle") + "[candles=" + count + ",lit=true]";
    }

    public static String leaves(String id) {
        return b(id) + "[persistent=true]";
    }

    public static String logY(String id) {
        return b(id) + "[axis=y]";
    }

    public static String facing(String id, Face facing) {
        return b(id) + "[facing=" + facing.id() + "]";
    }
}

