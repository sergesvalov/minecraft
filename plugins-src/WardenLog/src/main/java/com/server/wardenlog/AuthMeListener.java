package com.server.wardenlog;

import fr.xephi.authme.events.FailedLoginEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Instant;

/**
 * Separate listener for AuthMe events.
 * Isolated into its own class so that the main plugin can load
 * even when AuthMe is not installed on the server.
 */
public class AuthMeListener implements Listener {

    private final WardenLogPlugin plugin;

    public AuthMeListener(WardenLogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailedLogin(FailedLoginEvent event) {
        plugin.logEvent(String.format("{\"timestamp\":\"%s\", \"event\":\"failed_login\", \"player\":\"%s\"}",
                Instant.now().toString(), plugin.escapeString(event.getPlayer().getName())));
    }
}
