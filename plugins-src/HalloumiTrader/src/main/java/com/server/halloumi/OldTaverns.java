package com.server.halloumi;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Tears down taverns built before 1.4: the one-room tavern (11 x 9) and, if it had one, the guesthouse next to it
 * (23 x 17 with the yard, 3 blocks east or west) and the path between them. Their volume is cleared, the raised
 * floor removed and the stone foundation under it turned back into dirt with grass on top (sand for sandstone),
 * and the innkeeper and guests are sent away. The geometry here is that of the old builders.
 */
final class OldTaverns {

    /** Column range in world coordinates, cleared from the floor up to {@code height} above it. */
    private record Box(int minX, int minZ, int maxX, int maxZ, int height) {
    }

    private static final int TAVERN_X = 11, TAVERN_Z = 9, TAVERN_HEIGHT = 12;
    private static final int HOUSE_X = 23, HOUSE_Z = 17, HOUSE_YARD_END_Z = 27, HOUSE_HEIGHT = 20, HOUSE_GAP = 3;
    private static final int HOUSE_GATE_Z = 22;
    /** How deep the old builders filled foundations under the floor. */
    private static final int FOUNDATION_DEPTH = 8;

    private static final Set<Material> FOUNDATIONS = new HashSet<>();

    static {
        for (TavernPalette palette : TavernPalette.values()) {
            FOUNDATIONS.add(palette.base);
        }
    }

    private OldTaverns() {
    }

    private static List<Box> boxes(Tavern t) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(t.x() - 1, t.z() - 1, t.x() + TAVERN_X, t.z() + TAVERN_Z, TAVERN_HEIGHT));
        if (t.state() == Tavern.State.OLD_EAST || t.state() == Tavern.State.OLD_WEST) {
            boolean west = t.state() == Tavern.State.OLD_WEST;
            int hx = west ? t.x() - HOUSE_GAP - HOUSE_X : t.x() + TAVERN_X + HOUSE_GAP;
            int hz = t.z() + TAVERN_Z - HOUSE_Z;
            boxes.add(new Box(hx - 1, hz - 1, hx + HOUSE_X, hz + HOUSE_YARD_END_Z, HOUSE_HEIGHT));
            // Path from the tavern doorstep south, then along to the yard's side gate
            int doorX = t.x() + 5;
            int gateZ = hz + HOUSE_GATE_Z;
            int gateX = west ? hx + HOUSE_X - 1 : hx;
            boxes.add(new Box(doorX, t.z() + TAVERN_Z + 1, doorX, gateZ, 4));
            boxes.add(new Box(Math.min(doorX, gateX), gateZ, Math.max(doorX, gateX), gateZ, 4));
        }
        return boxes;
    }

    /** Area {minX, minZ, maxX, maxZ} the old tavern and its guesthouse take. */
    static int[] area(Tavern t) {
        int[] area = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Box box : boxes(t)) {
            area[0] = Math.min(area[0], box.minX());
            area[1] = Math.min(area[1], box.minZ());
            area[2] = Math.max(area[2], box.maxX());
            area[3] = Math.max(area[3], box.maxZ());
        }
        return area;
    }

    /** Removes the old buildings and the traders in them ({@code ours} tells the innkeeper and guests apart). */
    static void demolish(World world, Tavern t, Predicate<Entity> ours) {
        TavernBuilder.Blocks b = TavernBuilder.worldBlocks(world);
        var air = Material.AIR.createBlockData();
        int oy = t.y();
        for (Box box : boxes(t)) {
            BoundingBox bounds = new BoundingBox(box.minX(), oy - 1, box.minZ(),
                    box.maxX() + 1, oy + box.height() + 1, box.maxZ() + 1);
            for (Entity entity : world.getNearbyEntities(bounds, e -> e instanceof WanderingTrader && ours.test(e))) {
                entity.remove();
            }
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = oy + box.height(); y >= oy; y--) {
                        if (!b.type(x, y, z).isAir()) {
                            b.set(x, y, z, air);
                        }
                    }
                    restoreGround(b, x, oy - 1, z);
                }
            }
        }
    }

    /** Turns the stone foundation under the old floor back into dirt (or sand), grass on top. */
    private static void restoreGround(TavernBuilder.Blocks b, int x, int startY, int z) {
        for (int y = startY; y > startY - FOUNDATION_DEPTH; y--) {
            Material type = b.type(x, y, z);
            if (type.isAir()) {
                continue;
            }
            if (!FOUNDATIONS.contains(type)) {
                break;
            }
            b.set(x, y, z, (type == Material.SANDSTONE ? Material.SAND : Material.DIRT).createBlockData());
        }
        for (int y = startY; y > startY - FOUNDATION_DEPTH; y--) {
            Material type = b.type(x, y, z);
            if (type.isAir()) {
                continue;
            }
            if (type == Material.DIRT) {
                b.set(x, y, z, Material.GRASS_BLOCK.createBlockData());
            }
            break;
        }
    }
}
