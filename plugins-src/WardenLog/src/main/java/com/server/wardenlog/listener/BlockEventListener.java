package com.server.wardenlog.listener;

import com.server.wardenlog.config.WardenConfig;
import com.server.wardenlog.log.EventJson;
import com.server.wardenlog.tracking.RateTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Logs suspicious block placement/destruction, lava buckets, end crystal placement, and piston lag machines.
 */
public final class BlockEventListener implements Listener {

    private static final Set<Material> VALUABLE_BLOCKS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.BARREL, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.NETHERITE_BLOCK,
            Material.GOLD_BLOCK, Material.IRON_BLOCK,
            Material.SPAWNER, Material.BEACON, Material.ENCHANTING_TABLE,
            Material.ANVIL, Material.BREWING_STAND, Material.CONDUIT,
            Material.LODESTONE, Material.RESPAWN_ANCHOR, Material.JUKEBOX,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER
    );

    static {
        for (Material m : Material.values()) {
            if (m.name().endsWith("SHULKER_BOX")) {
                VALUABLE_BLOCKS.add(m);
            }
        }
    }

    private final Consumer<String> eventSink;
    private final RateTracker rateTracker;
    private final WardenConfig config;

    public BlockEventListener(Consumer<String> eventSink, RateTracker rateTracker, WardenConfig config) {
        this.eventSink = eventSink;
        this.rateTracker = rateTracker;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.TNT) {
            Location loc = block.getLocation();
            eventSink.accept(EventJson.of("place_tnt")
                    .field("player", event.getPlayer().getName())
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String playerName = player.getName();
        Location loc = block.getLocation();
        String worldName = loc.getWorld().getName();

        if (VALUABLE_BLOCKS.contains(block.getType())) {
            eventSink.accept(EventJson.of("break_valuable")
                    .field("player", playerName)
                    .field("block", block.getType().name())
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), worldName)
                    .build());
        }

        int burst = rateTracker.recordBreak(playerName, System.currentTimeMillis(), config.griefBlocks());
        if (burst > 0) {
            eventSink.accept(EventJson.of("grief_alert")
                    .field("player", playerName)
                    .field("blocks_broken", burst)
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), worldName)
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLavaPlace(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET) {
            Location loc = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
            eventSink.accept(EventJson.of("place_lava")
                    .field("player", event.getPlayer().getName())
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getItem() != null
                && event.getItem().getType() == Material.END_CRYSTAL
                && event.getClickedBlock() != null) {
            Location loc = event.getClickedBlock().getLocation();
            eventSink.accept(EventJson.of("place_crystal")
                    .field("player", event.getPlayer().getName())
                    .xyz(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        long chunkKey = event.getBlock().getChunk().getChunkKey();
        if (rateTracker.recordPiston(chunkKey, System.currentTimeMillis(), config.lagMachinePistons())) {
            Location loc = event.getBlock().getLocation();
            eventSink.accept(EventJson.of("lag_machine")
                    .xz(loc.getBlockX(), loc.getBlockZ(), loc.getWorld().getName())
                    .build());
        }
    }
}

