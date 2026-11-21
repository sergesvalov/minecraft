package com.server.wardenlog;

import com.server.wardenlog.config.WardenConfig;
import com.server.wardenlog.listener.AuthMeListener;
import com.server.wardenlog.listener.BlockEventListener;
import com.server.wardenlog.listener.EntityEventListener;
import com.server.wardenlog.listener.PlayerEventListener;
import com.server.wardenlog.log.EventJson;
import com.server.wardenlog.log.EventLogWriter;
import com.server.wardenlog.tracking.RateTracker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Paper composition root wiring config, JSONL event logging, and security/performance listeners.
 */
public class WardenLogPlugin extends JavaPlugin {

    private final RateTracker rateTracker = new RateTracker();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private EventLogWriter logWriter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        WardenConfig config = WardenConfig.from(getConfig());

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

        logWriter = new EventLogWriter(logFile, config.logRetentionDays(), ZoneId.systemDefault(),
                msg -> getLogger().warning(msg));

        EntityEventListener entityListener = new EntityEventListener(this::logEvent, config);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockEventListener(this::logEvent, rateTracker, config), this);
        pm.registerEvents(entityListener, this);
        pm.registerEvents(new PlayerEventListener(this::logEvent), this);

        if (pm.getPlugin("AuthMe") != null) {
            pm.registerEvents(new AuthMeListener(this::logEvent), this);
            getLogger().info("AuthMe detected — failed login tracking enabled.");
        } else {
            getLogger().info("AuthMe not found — failed login tracking disabled.");
        }

        BukkitScheduler scheduler = Bukkit.getScheduler();
        tasks.add(scheduler.runTaskTimerAsynchronously(this, () -> logWriter.flush(), 100L, 100L));
        tasks.add(scheduler.runTaskTimer(this,
                () -> entityListener.scanLoadedChunksForEntities(Bukkit.getWorlds()), 1200L, 1200L));
        tasks.add(scheduler.runTaskTimer(this, rateTracker::clearPistons, 100L, 100L));
        tasks.add(scheduler.runTaskTimer(this, () -> rateTracker.prune(System.currentTimeMillis()), 300L, 300L));

        getLogger().info("WardenLog enabled! Logging events to " + logFile.getName());
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (logWriter != null) {
            logWriter.flush();
        }
        getLogger().info("WardenLog disabled.");
    }

    /** Public so optional listener classes can queue JSONL events. */
    public void logEvent(String jsonLine) {
        if (logWriter != null) {
            logWriter.offer(jsonLine);
        }
    }

    /** Public so optional listener classes can safely escape strings. */
    public String escapeString(String str) {
        return EventJson.escape(str);
    }
}
