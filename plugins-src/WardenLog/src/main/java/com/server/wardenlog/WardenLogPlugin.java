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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.xephi.authme.events.FailedLoginEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WardenLogPlugin extends JavaPlugin implements Listener {

    private File logFile;
    private final Map<Long, Integer> chunkPistonCounts = new HashMap<>();
    private final Map<Long, Long> chunkPistonCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        logFile = new File(getDataFolder(), "events.jsonl");
        
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            getLogger().warning("Failed to create events.jsonl: " + e.getMessage());
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("WardenLog enabled! Logging events to " + logFile.getName());

        // Background task for entity counts (once per minute)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    if (chunk.getEntities().length > 150) {
                        String json = String.format(
                            "{\"timestamp\":\"%s\", \"event\":\"high_entity_count\", \"count\":%d, \"x\":%d, \"z\":%d, \"world\":\"%s\"}",
                            Instant.now().toString(),
                            chunk.getEntities().length,
                            chunk.getX() * 16,
                            chunk.getZ() * 16,
                            escape(world.getName())
                        );
                        appendLog(json);
                    }
                }
            }
        }, 1200L, 1200L); // 1200 ticks = 60 seconds

        // Background task to clear piston counters (every 5 seconds)
        Bukkit.getScheduler().runTaskTimer(this, chunkPistonCounts::clear, 100L, 100L);
    }

    @Override
    public void onDisable() {
        getLogger().info("WardenLog disabled.");
    }

    // Existing TNT listeners...
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.TNT) {
            Player player = event.getPlayer();
            Location loc = block.getLocation();
            String json = String.format(
                "{\"timestamp\":\"%s\", \"event\":\"place_tnt\", \"player\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}",
                Instant.now().toString(), escape(player.getName()), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), escape(loc.getWorld().getName())
            );
            appendLog(json);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) entity;
            Location loc = tnt.getLocation();
            String source = tnt.getSource() != null ? tnt.getSource().getName() : "Unknown";
            String json = String.format(
                "{\"timestamp\":\"%s\", \"event\":\"explode_tnt\", \"source\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}",
                Instant.now().toString(), escape(source), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), escape(loc.getWorld().getName())
            );
            appendLog(json);
        } else if (entity != null && entity.getType() == EntityType.ENDER_CRYSTAL) {
            Location loc = entity.getLocation();
            String json = String.format(
                "{\"timestamp\":\"%s\", \"event\":\"explode_crystal\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}",
                Instant.now().toString(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), escape(loc.getWorld().getName())
            );
            appendLog(json);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"player_join\", \"player\":\"%s\"}", Instant.now().toString(), escape(event.getPlayer().getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"player_quit\", \"player\":\"%s\"}", Instant.now().toString(), escape(event.getPlayer().getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobSpawn(EntitySpawnEvent event) {
        EntityType type = event.getEntity().getType();
        if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || 
            type == EntityType.WARDEN || type == EntityType.RAVAGER || type == EntityType.EVOKER) {
            Location loc = event.getLocation();
            String player = "Unknown";
            double closestDist = Double.MAX_VALUE;
            for (Player p : loc.getWorld().getPlayers()) {
                double dist = p.getLocation().distanceSquared(loc);
                if (dist < 2500 && dist < closestDist) { 
                    closestDist = dist;
                    player = p.getName();
                }
            }
            appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"dangerous_mob_spawn\", \"mob\":\"%s\", \"player\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}", Instant.now().toString(), type.name(), escape(player), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), escape(loc.getWorld().getName())));
        }
    }

    // New Listeners

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLavaPlace(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET) {
            Player player = event.getPlayer();
            Location loc = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
            appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"place_lava\", \"player\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}", Instant.now().toString(), escape(player.getName()), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), escape(loc.getWorld().getName())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (event.getAction().name().contains("RIGHT_CLICK_BLOCK") && event.getItem() != null && event.getItem().getType() == Material.END_CRYSTAL) {
            Player player = event.getPlayer();
            Location loc = event.getClickedBlock().getLocation();
            appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"place_crystal\", \"player\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}", Instant.now().toString(), escape(player.getName()), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), escape(loc.getWorld().getName())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/op ") || cmd.startsWith("/deop ") || cmd.startsWith("/ban ") || 
            cmd.startsWith("/kick ") || cmd.equals("/stop") || cmd.startsWith("//set ") || 
            cmd.startsWith("//replace ")) {
            appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"suspicious_command\", \"player\":\"%s\", \"command\":\"%s\"}", Instant.now().toString(), escape(event.getPlayer().getName()), escape(cmd)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"player_kill\", \"killer\":\"%s\", \"victim\":\"%s\"}", Instant.now().toString(), escape(killer.getName()), escape(victim.getName())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailedLogin(FailedLoginEvent event) {
        appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"failed_login\", \"player\":\"%s\"}", Instant.now().toString(), escape(event.getPlayer().getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        long chunkKey = event.getBlock().getChunk().getChunkKey();
        long now = System.currentTimeMillis();
        
        if (chunkPistonCooldowns.getOrDefault(chunkKey, 0L) > now) {
            return; // In cooldown, ignore
        }

        int count = chunkPistonCounts.getOrDefault(chunkKey, 0) + 1;
        chunkPistonCounts.put(chunkKey, count);

        if (count > 500) { // 500 extensions in 5 seconds
            Location loc = event.getBlock().getLocation();
            appendLog(String.format("{\"timestamp\":\"%s\", \"event\":\"lag_machine\", \"x\":%d, \"z\":%d, \"world\":\"%s\"}", Instant.now().toString(), loc.getBlockX(), loc.getBlockZ(), escape(loc.getWorld().getName())));
            chunkPistonCooldowns.put(chunkKey, now + 300000L); // 5 minutes cooldown
        }
    }

    private void appendLog(String jsonLine) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.println(jsonLine);
            } catch (IOException e) {
                getLogger().severe("Failed to write to WardenLog events.jsonl: " + e.getMessage());
            }
        });
    }

    private String escape(String str) {
        if (str == null) return "null";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
