package com.server.wardenlog;

import org.bukkit.Bukkit;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * Paper listener logging grief, explosions, dangerous mobs, lag machines, and login/command events to events.jsonl.
 */
public class WardenLogPlugin extends JavaPlugin implements Listener {

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

    private static final Set<EntityType> DANGEROUS_MOBS = EnumSet.of(
            EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN,
            EntityType.RAVAGER, EntityType.EVOKER
    );

    static {
        for (Material m : Material.values()) {
            if (m.name().endsWith("SHULKER_BOX")) {
                VALUABLE_BLOCKS.add(m);
            }
        }
    }

    private final RateTracker rateTracker = new RateTracker();
    private EventLogWriter logWriter;

    private int entityLimit;
    private int griefBlocks;
    private int lagMachinePistons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        entityLimit = getConfig().getInt("limits.entities_per_chunk", 150);
        griefBlocks = getConfig().getInt("limits.grief_blocks", 45);
        lagMachinePistons = getConfig().getInt("limits.lag_machine_pistons", 500);
        int logRetentionDays = Math.max(1, getConfig().getInt("log_retention_days", 14));

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File logFile = new File(getDataFolder(), "events.jsonl");
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            getLogger().warning("Failed to create events.jsonl: " + e.getMessage());
        }

        logWriter = new EventLogWriter(logFile, logRetentionDays, ZoneId.systemDefault(),
                msg -> getLogger().warning(msg));

        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().getPlugin("AuthMe") != null) {
            getServer().getPluginManager().registerEvents(new AuthMeListener(this), this);
            getLogger().info("AuthMe detected — failed login tracking enabled.");
        } else {
            getLogger().info("AuthMe not found — failed login tracking disabled.");
        }

        getLogger().info("WardenLog enabled! Logging events to " + logFile.getName());

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, logWriter::flush, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::scanLoadedChunksForEntities, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, rateTracker::clearPistons, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, () -> rateTracker.prune(System.currentTimeMillis()), 300L, 300L);
    }

    @Override
    public void onDisable() {
        if (logWriter != null) {
            logWriter.flush();
        }
        getLogger().info("WardenLog disabled.");
    }

    private void scanLoadedChunksForEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int entityCount = chunk.getEntities().length;
                if (entityCount > entityLimit) {
                    logEvent(EventJson.of("high_entity_count")
                            .field("count", entityCount)
                            .field("x", chunk.getX() * 16)
                            .field("z", chunk.getZ() * 16)
                            .field("world", world.getName())
                            .build());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.TNT) {
            Location loc = block.getLocation();
            logEvent(EventJson.of("place_tnt")
                    .field("player", event.getPlayer().getName())
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String playerName = player.getName();
        Location loc = block.getLocation();

        if (VALUABLE_BLOCKS.contains(block.getType())) {
            logEvent(EventJson.of("break_valuable")
                    .field("player", playerName)
                    .field("block", block.getType().name())
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        }

        int burst = rateTracker.recordBreak(playerName, System.currentTimeMillis(), griefBlocks);
        if (burst > 0) {
            logEvent(EventJson.of("grief_alert")
                    .field("player", playerName)
                    .field("blocks_broken", burst)
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
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
            int tntDestroyed = 0;
            for (Block b : event.blockList()) {
                if (b.getType() == Material.TNT) {
                    tntDestroyed++;
                }
            }
            if (tntDestroyed > 2) {
                logEvent(EventJson.of("lag_machine")
                        .field("reason", "Цепная реакция TNT (" + tntDestroyed + " шт)")
                        .field("x", loc.getBlockX())
                        .field("z", loc.getBlockZ())
                        .field("world", loc.getWorld().getName())
                        .build());
            }
            String source = tnt.getSource() != null ? tnt.getSource().getName() : "Unknown";
            logEvent(EventJson.of("explode_tnt")
                    .field("source", source)
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        } else if (entity != null && entity.getType() == EntityType.END_CRYSTAL) {
            Location loc = entity.getLocation();
            logEvent(EventJson.of("explode_crystal")
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        logEvent(EventJson.of("player_join").field("player", event.getPlayer().getName()).build());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        logEvent(EventJson.of("player_quit").field("player", event.getPlayer().getName()).build());
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
        logEvent(EventJson.of("dangerous_mob_spawn")
                .field("mob", type.name())
                .field("player", nearest)
                .field("x", loc.getBlockX())
                .field("y", loc.getBlockY())
                .field("z", loc.getBlockZ())
                .field("world", loc.getWorld().getName())
                .build());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLavaPlace(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET) {
            Location loc = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
            logEvent(EventJson.of("place_lava")
                    .field("player", event.getPlayer().getName())
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (event.getAction().name().contains("RIGHT_CLICK_BLOCK")
                && event.getItem() != null
                && event.getItem().getType() == Material.END_CRYSTAL
                && event.getClickedBlock() != null) {
            Location loc = event.getClickedBlock().getLocation();
            logEvent(EventJson.of("place_crystal")
                    .field("player", event.getPlayer().getName())
                    .field("x", loc.getBlockX())
                    .field("y", loc.getBlockY())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String rawCmd = event.getMessage();
        if (CommandFilter.isSuspicious(rawCmd)) {
            logEvent(EventJson.of("suspicious_command")
                    .field("player", event.getPlayer().getName())
                    .field("command", rawCmd.toLowerCase())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            logEvent(EventJson.of("player_kill")
                    .field("killer", killer.getName())
                    .field("victim", victim.getName())
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        long chunkKey = event.getBlock().getChunk().getChunkKey();
        if (rateTracker.recordPiston(chunkKey, System.currentTimeMillis(), lagMachinePistons)) {
            Location loc = event.getBlock().getLocation();
            logEvent(EventJson.of("lag_machine")
                    .field("x", loc.getBlockX())
                    .field("z", loc.getBlockZ())
                    .field("world", loc.getWorld().getName())
                    .build());
        }
    }

    /** Public so that optional listener classes (e.g. AuthMeListener) can write events. */
    public void logEvent(String jsonLine) {
        if (logWriter != null) {
            logWriter.offer(jsonLine);
        }
    }

    /** Public so that optional listener classes can safely escape strings. */
    public String escapeString(String str) {
        return EventJson.escape(str);
    }
}
