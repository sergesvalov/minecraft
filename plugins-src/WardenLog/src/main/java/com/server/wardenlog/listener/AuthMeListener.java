package com.server.wardenlog.listener;

import com.server.wardenlog.log.EventJson;
import fr.xephi.authme.events.FailedLoginEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.Consumer;

/**
 * Separate listener for AuthMe events.
 * Isolated into its own class so that the main plugin can load even when AuthMe is not installed.
 */
public final class AuthMeListener implements Listener {

    private final Consumer<String> eventSink;

    public AuthMeListener(Consumer<String> eventSink) {
        this.eventSink = eventSink;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailedLogin(FailedLoginEvent event) {
        eventSink.accept(EventJson.of("failed_login")
                .field("player", event.getPlayer().getName())
                .build());
    }
}

