package com.server.greatbuildings;

import com.server.greatbuildings.buildings.BuildingCatalog;
import com.server.greatbuildings.command.GbCommand;
import com.server.greatbuildings.engine.BuildManager;
import com.server.greatbuildings.storage.BuildingRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Великие здания мира: famous buildings at 1:1 scale, built in front of the superadmin on command.
 * Building and teleporting are limited to {@code greatbuildings.build}.
 */
public final class GreatBuildingsPlugin extends JavaPlugin {

    private BuildManager buildManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BuildingCatalog catalog = new BuildingCatalog();
        BuildingRegistry registry = new BuildingRegistry(getDataFolder(), getLogger());
        buildManager = new BuildManager(this, catalog, registry);

        var command = getCommand("greatbuildings");
        if (command != null) {
            GbCommand handler = new GbCommand(catalog, registry, buildManager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (buildManager != null) {
            buildManager.shutdown();
        }
    }
}
