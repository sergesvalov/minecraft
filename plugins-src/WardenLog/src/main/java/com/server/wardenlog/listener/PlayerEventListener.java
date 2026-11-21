package com.server.wardenlog.listener;

import com.server.wardenlog.log.EventJson;
import com.server.wardenlog.tracking.CommandFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Logs player session lifecycle, watched admin commands, and PVP kills.
 */
public final class PlayerEventListener implements Listener {

    private final Consumer<String> eventSink;

    public PlayerEventListener(Consumer<String> eventSink) {
        this.eventSink = eventSink;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        eventSink.accept(EventJson.of("player_join").field("player", event.getPlayer().getName()).build());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        eventSink.accept(EventJson.of("player_quit").field("player", event.getPlayer().getName()).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String rawCmd = event.getMessage();
        if (CommandFilter.isSuspicious(rawCmd)) {
            eventSink.accept(EventJson.of("suspicious_command")
                    .field("player", event.getPlayer().getName())
                    .field("command", rawCmd.toLowerCase(Locale.ROOT))
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            eventSink.accept(EventJson.of("player_kill")
                    .field("killer", killer.getName())
                    .field("victim", victim.getName())
                    .build());
        }
    }
}

