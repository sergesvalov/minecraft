package com.server.halloumi;

import com.server.halloumi.command.HalloumiCommand;
import com.server.halloumi.npc.Residents;
import com.server.halloumi.npc.Seats;
import com.server.halloumi.tavern.TavernManager;
import com.server.halloumi.tavern.TavernPopulator;
import com.server.halloumi.tavern.TavernStorage;
import com.server.halloumi.trade.MenuService;
import com.server.halloumi.trade.TraderService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Set;

/**
 * Paper composition root wiring trader visits, meze menu, Cypriot taverns, seats, and NPCs.
 */
public class HalloumiTraderPlugin extends JavaPlugin implements Listener {

    private MenuService menu;
    private Seats seats;
    private Residents residents;
    private TraderService traders;
    private TavernManager taverns;
    private TavernPopulator populator;
    private BukkitTask tavernTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        menu = new MenuService(this);
        menu.migrateDefaults(getConfig(), getLogger());
        getConfig().options().copyDefaults(true);
        saveConfig();
        menu.reload(getConfig(), getLogger());

        seats = new Seats(this);
        residents = new Residents(this, () -> menu.innkeeperRecipes(getConfig()), seats);
        traders = new TraderService(this, menu, loc -> taverns.nearest(loc), residents::isResident);
        TavernStorage storage = new TavernStorage(new File(getDataFolder(), "taverns.txt"), getLogger());
        taverns = new TavernManager(this, storage, menu, traders, seats, residents);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(traders, this);
        pm.registerEvents(seats, this);
        pm.registerEvents(residents, this);
        residents.start();

        var command = getCommand("halloumi");
        if (command != null) {
            HalloumiCommand handler = new HalloumiCommand(this, traders, menu, taverns);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        traders.startSpawnTask();
        registerPopulator();
        tavernTask = Bukkit.getScheduler().runTaskTimer(this, taverns::tick, 200L, 200L);
        getLogger().info("HalloumiTrader enabled, known taverns: " + taverns.all().size());
    }

    @Override
    public void onDisable() {
        if (traders != null) {
            traders.stopSpawnTask();
        }
        if (tavernTask != null) {
            tavernTask.cancel();
        }
        unregisterPopulator();
        if (residents != null) {
            residents.stop();
        }
        if (seats != null) {
            seats.removeSeats();
        }
        if (taverns != null) {
            taverns.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        menu.reload(getConfig(), getLogger());
        traders.startSpawnTask();
        registerPopulator();
    }

    private void registerPopulator() {
        unregisterPopulator();
        FileConfiguration cfg = getConfig();
        if (!cfg.getBoolean("tavern.enabled", true)) {
            return;
        }
        populator = new TavernPopulator(
                Set.copyOf(cfg.getStringList("tavern.worlds")),
                cfg.getInt("tavern.spacing-chunks", 48),
                cfg.getDouble("tavern.chance", 0.6),
                cfg.getInt("tavern.max-slope", 3),
                taverns::onPlaced);
        for (World world : Bukkit.getWorlds()) {
            attachPopulator(world);
        }
    }

    private void attachPopulator(World world) {
        if (populator != null && populator.supportsWorld(world.getName())
                && !world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
        }
    }

    private void unregisterPopulator() {
        if (populator != null) {
            for (World world : Bukkit.getWorlds()) {
                world.getPopulators().remove(populator);
            }
            populator = null;
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attachPopulator(event.getWorld());
    }
}
