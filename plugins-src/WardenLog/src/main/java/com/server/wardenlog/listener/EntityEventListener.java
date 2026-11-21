package com.server.wardenlog.listener;

import com.server.wardenlog.config.WardenConfig;
import com.server.wardenlog.log.EventJson;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Logs TNT/crystal explosions, dangerous mob spawns, and high entity concentrations in loaded chunks.
 */
public final class EntityEventListener implements Listener {

    private static final Set<EntityType> DANGEROUS_MOBS = EnumSet.of(
            EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN,
            EntityType.RAVAGER, EntityType.EVOKER
    );

    private final Consumer<String> eventSink;
    private final WardenConfig config;

    public EntityEventListener(Consumer<String> eventSink, WardenConfig config) {
        this.eventSink = eventSink;
        this.config = config;
    }

    public void scanLoadedChunksForEntities(Collection<World> worlds) {
        for (World world : worlds) {
            String worldName = world.getName();
            for (Chunk chunk : world.getLoadedChunks()) {
                int entityCount = chunk.getEntities().length;
                if (entityCount > config.entityLimit()) {
                    eventSink.accept(EventJson.of("high_entity_count")
                            .field("count", entityCount)
                            .xz(chunk.getX() * 16, chunk.getZ() * 16, worldName)
                            .build());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.blockList().isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed tnt) {
            Location loc = tnt.getLocation();
            String worldName = loc.getWorld().getName();
            int tntDestroyed = 0;
            for (Block b : event.blockList()) {
                if (b.getType() == Material.TNT) {
                    tntDestroyed++;
                }
            }
            if (tntDestroyed > 2) {
                eventSink.accept(EventJson.of("lag_machine")
                        .field("reason", "Цепная реакция TNT (" + tntDestroyed + " шт)")
                        .xz(loc.getBlockX(), loc.getBlockZ(), worldName)
                        .build());
            }
            String source = tnt.getSource() != null ? tnt.getSource().getName() : "Unknown";
            eventSink.accept(EventJson.of("explode_tnt")
                    .field("source", source)
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), worldName)
                    .build());
        } else if (entity != null && entity.getType() == EntityType.END_CRYSTAL) {
            Location loc = entity.getLocation();
            eventSink.accept(EventJson.of("explode_crystal")
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobSpawn(EntitySpawnEvent event) {
        EntityType type = event.getEntity().getType();
        if (!DANGEROUS_MOBS.contains(type)) {
            return;
        }
        Location loc = event.getLocation();
        String nearest = "Unknown";
        double closestDist = 2500.0;
        for (Player p : loc.getWorld().getPlayers()) {
            double dist = p.getLocation().distanceSquared(loc);
            if (dist < closestDist) {
                closestDist = dist;
                nearest = p.getName();
            }
        }
        eventSink.accept(EventJson.of("dangerous_mob_spawn")
                .field("mob", type.name())
                .field("player", nearest)
                .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())
                .build());
    }
}

