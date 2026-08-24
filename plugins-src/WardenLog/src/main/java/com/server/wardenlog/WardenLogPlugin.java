package com.server.wardenlog;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

public class WardenLogPlugin extends JavaPlugin implements Listener {

    private File logFile;

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
    }

    @Override
    public void onDisable() {
        getLogger().info("WardenLog disabled.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.TNT) {
            Player player = event.getPlayer();
            Location loc = block.getLocation();
            
            String json = String.format(
                "{\"timestamp\":\"%s\", \"event\":\"place_tnt\", \"player\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}",
                Instant.now().toString(),
                escape(player.getName()),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                escape(loc.getWorld().getName())
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
            String source = "Unknown";
            
            if (tnt.getSource() != null) {
                source = tnt.getSource().getName();
            }
            
            String json = String.format(
                "{\"timestamp\":\"%s\", \"event\":\"explode_tnt\", \"source\":\"%s\", \"x\":%d, \"y\":%d, \"z\":%d, \"world\":\"%s\"}",
                Instant.now().toString(),
                escape(source),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                escape(loc.getWorld().getName())
            );
            
            appendLog(json);
        }
    }

    private void appendLog(String jsonLine) {
        // Run async to avoid blocking main server thread
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
