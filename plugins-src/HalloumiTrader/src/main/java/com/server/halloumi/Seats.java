package com.server.halloumi;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Cushions on tavern chairs, and sitting on them. A cushion is a flat carpet {@link BlockDisplay} lying on the seat
 * half of the stairs. Right-clicking a chair with a cushion (with an empty hand or anything, not sneaking) seats
 * the player on an invisible marker armor stand; getting up (sneak) removes it again. Seats are never saved.
 */
final class Seats implements Listener {

    /** Height of the seat entity above the chair block: the top of the stairs' lower half. */
    private static final double SEAT_Y = 0.5;

    private final JavaPlugin plugin;
    private final NamespacedKey cushionKey;
    private final NamespacedKey seatKey;

    Seats(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cushionKey = new NamespacedKey(plugin, "cushion");
        this.seatKey = new NamespacedKey(plugin, "seat");
    }

    /** Puts a cushion of the given carpet on the chair at (x, y, z), if the block there is still a chair. */
    void placeCushion(World world, int x, int y, int z, Material carpet) {
        if (!(world.getBlockAt(x, y, z).getBlockData() instanceof Stairs stairs)
                || stairs.getHalf() != Bisected.Half.BOTTOM) {
            return;
        }
        // Seat half of the stairs, inset by 1/16 at the edges; the backrest is on the side the stairs face
        float minX = 1 / 16f, maxX = 15 / 16f, minZ = 1 / 16f, maxZ = 15 / 16f;
        switch (stairs.getFacing()) {
            case NORTH -> minZ = 0.5f;
            case SOUTH -> maxZ = 0.5f;
            case WEST -> minX = 0.5f;
            case EAST -> maxX = 0.5f;
            default -> {
            }
        }
        Location at = new Location(world, x + 0.5, y + SEAT_Y, z + 0.5);
        float fx = minX, fz = minZ, sx = maxX - minX, sz = maxZ - minZ;
        world.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(carpet.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(fx - 0.5f, 0.002f, fz - 0.5f),
                    new AxisAngle4f(), new Vector3f(sx, 1f, sz), new AxisAngle4f()));
            display.setPersistent(true);
            display.getPersistentDataContainer().set(cushionKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Removes the cushions in the box, so a tavern can be decorated again without doubling them. */
    void removeCushions(World world, BoundingBox box) {
        for (Entity entity : world.getNearbyEntities(box, e -> e instanceof BlockDisplay && isCushion(e))) {
            entity.remove();
        }
    }

    private boolean isCushion(Entity entity) {
        return entity.getPersistentDataContainer().has(cushionKey, PersistentDataType.BYTE);
    }

    private boolean isSeat(Entity entity) {
        return entity.getPersistentDataContainer().has(seatKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !Tag.STAIRS.isTagged(block.getType()) || player.isSneaking() || player.isInsideVehicle()
                || !(block.getBlockData() instanceof Stairs stairs) || stairs.getHalf() != Bisected.Half.BOTTOM) {
            return;
        }
        if (!hasCushion(block)) {
            return;
        }
        event.setCancelled(true);
        sit(player, block);
    }

    private boolean hasCushion(Block chair) {
        Location center = chair.getLocation().add(0.5, SEAT_Y, 0.5);
        return !chair.getWorld().getNearbyEntities(center, 0.3, 0.3, 0.3,
                e -> e instanceof BlockDisplay && isCushion(e)).isEmpty();
    }

    /** True if someone (a player or the tavern resident) sits on this chair. */
    boolean occupied(Block chair) {
        Location center = chair.getLocation().add(0.5, SEAT_Y, 0.5);
        return chair.getWorld().getNearbyEntities(center, 0.3, 0.3, 0.3,
                e -> e instanceof ArmorStand && isSeat(e) && !e.getPassengers().isEmpty()).stream().findAny().isPresent();
    }

    /**
     * Seats {@code rider} on the chair (bottom-half stairs), facing away from the backrest, towards the table.
     * Returns false if the chair is taken or isn't one.
     */
    boolean sit(Entity rider, Block chair) {
        if (!(chair.getBlockData() instanceof Stairs stairs) || stairs.getHalf() != Bisected.Half.BOTTOM) {
            return false;
        }
        World world = chair.getWorld();
        Location center = chair.getLocation().add(0.5, SEAT_Y, 0.5);
        for (Entity seat : world.getNearbyEntities(center, 0.3, 0.3, 0.3, e -> e instanceof ArmorStand && isSeat(e))) {
            if (!seat.getPassengers().isEmpty()) {
                return false; // taken
            }
            seat.remove();
        }
        center.setYaw(yaw(stairs.getFacing().getOppositeFace()));
        ArmorStand seat = world.spawn(center, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.getPersistentDataContainer().set(seatKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (!seat.addPassenger(rider)) {
            seat.remove();
            return false;
        }
        return true;
    }

    /** Getting up: the seat goes away; vanilla puts the player on a free spot next to the chair. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        Entity seat = event.getDismounted();
        if (seat instanceof ArmorStand && isSeat(seat)) {
            Bukkit.getScheduler().runTask(plugin, seat::remove);
        }
    }

    /** On shutdown: stand everyone up and remove the seats (they aren't saved anyway). */
    void removeSeats() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (isSeat(stand)) {
                    stand.eject();
                    stand.remove();
                }
            }
        }
    }

    static float yaw(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }
}
