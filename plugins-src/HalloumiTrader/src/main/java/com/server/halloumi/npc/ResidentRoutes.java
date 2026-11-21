package com.server.halloumi.npc;

import com.server.halloumi.model.TavernPalette;
import com.server.halloumi.tavern.TavernLayout;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure coordinates, schedule predicates, and indoor navigation paths for tavern residents.
 */
public final class ResidentRoutes {

    /** Walking speed, blocks per tick (a villager walks at about 0.1-0.15). */
    public static final double SPEED = 0.11;
    public static final double AISLE_X = 15.0;
    /** Where a new resident appears, and where a lost one is put back. */
    public static final Vector HOME = new Vector(AISLE_X, 1, 12.5);
    /** Facing the innkeeper across the counter. */
    public static final Vector COUNTER = new Vector(AISLE_X, 1, 5.6);
    public static final Vector STAIRS_BOTTOM = new Vector(2.0, 1, 18.5);
    /** His room: the north-west one upstairs, door leaves at x 11 and 12, bed head at x 9, z 1. */
    public static final int ROOM_DOOR_X = 11;
    public static final int ROOM_DOOR_Z = 8;
    public static final int BED_X = 9;
    public static final int BED_Z = 1;
    public static final Vector IN_ROOM = new Vector(10.5, TavernLayout.DECK + 1, 3.5);
    /** Free spots around the hall to stroll to, all on the free rows between the tables. */
    public static final List<Vector> STROLL = List.of(
            new Vector(6.5, 1, 7.5),
            new Vector(24.5, 1, 7.5),
            new Vector(8.5, 1, 12.5),
            new Vector(21.5, 1, 12.5),
            new Vector(10.5, 1, 17.5),
            new Vector(20.5, 1, 17.5),
            new Vector(AISLE_X, 1, 20.0)
    );

    private ResidentRoutes() {
    }

    public static boolean isNight(long worldTime) {
        return worldTime >= 12600 && worldTime < 23300;
    }

    public static String villagerTypeKey(TavernPalette palette) {
        if (palette == null) {
            return "plains";
        }
        return switch (palette) {
            case SPRUCE -> "taiga";
            case ACACIA -> "savanna";
            case SANDSTONE -> "desert";
            case JUNGLE -> "jungle";
            case DARK_OAK -> "swamp";
            default -> "plains";
        };
    }

    public static Villager.Type villagerType(TavernPalette palette) {
        return switch (villagerTypeKey(palette)) {
            case "taiga" -> Villager.Type.TAIGA;
            case "savanna" -> Villager.Type.SAVANNA;
            case "desert" -> Villager.Type.DESERT;
            case "jungle" -> Villager.Type.JUNGLE;
            case "swamp" -> Villager.Type.SWAMP;
            default -> Villager.Type.PLAINS;
        };
    }

    public static boolean isDiningChair(TavernLayout.Chair c) {
        return c.y() == 1 && (c.z() == 8 || c.z() == 10 || c.z() == 14 || c.z() == 16);
    }

    /** Free row in front of a long-table chair. */
    public static double chairApproachZ(int chairZ) {
        return switch (chairZ) {
            case 8 -> 7.5;
            case 10 -> 11.5;
            case 14 -> 13.5;
            default -> 17.5;
        };
    }

    /** Waypoints along the free row -> central aisle -> target's row -> target. */
    public static List<Vector> groundRoute(Vector from, Vector target) {
        List<Vector> waypoints = new ArrayList<>(3);
        if (from.getX() != AISLE_X) {
            waypoints.add(new Vector(AISLE_X, 1, from.getZ()));
        }
        if (from.getZ() != target.getZ() || from.getX() != AISLE_X) {
            waypoints.add(new Vector(AISLE_X, 1, target.getZ()));
        }
        waypoints.add(target.clone());
        return waypoints;
    }
}

