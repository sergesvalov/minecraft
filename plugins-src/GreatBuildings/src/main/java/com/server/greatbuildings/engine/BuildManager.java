package com.server.greatbuildings.engine;

import com.server.greatbuildings.buildings.BuildingCatalog;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.storage.BuildingRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates pending build confirmations, active {@link BuildJob} execution, undo state,
 * and superadmin teleports to built structures.
 */
public final class BuildManager {

    private static final long CONFIRM_TTL_MS = 60_000L;

    private record Pending(Building building, World world, int ox, int oy, int oz, Location anchor, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final BuildingCatalog catalog;
    private final BuildingRegistry registry;
    private final Map<UUID, Pending> pendingByPlayer = new HashMap<>();

    private BuildJob running;
    private BuildJob last;
    private BuildingRegistry.Entry lastEntry;

    public BuildManager(JavaPlugin plugin, BuildingCatalog catalog, BuildingRegistry registry) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.registry = registry;
    }

    public void prepare(Player player, Building building) {
        if (isBusy()) {
            player.sendMessage(Component.text("Уже идёт стройка: " + running.building().title(), NamedTextColor.RED));
            return;
        }
        Location loc = player.getLocation();
        World world = player.getWorld();
        int[] anchor = building.anchor();
        int[] b = building.bounds();
        int ox = loc.getBlockX() - anchor[0];
        int oz = loc.getBlockZ() - anchor[1];
        int oy = loc.getBlockY() - 1;
        if (oy + b[4] >= world.getMaxHeight()) {
            player.sendMessage(Component.text("Слишком высоко: здание не поместится под потолок мира.", NamedTextColor.RED));
            return;
        }
        pendingByPlayer.put(player.getUniqueId(),
                new Pending(building, world, ox, oy, oz, loc.toBlockLocation(), System.currentTimeMillis() + CONFIRM_TTL_MS));
        player.sendMessage(Component.text(String.format(Locale.ROOT,
                "%s (%s): %d × %d блоков к северу от тебя (x %d…%d, z %d…%d), высота %d. Всё, что там стоит, будет "
                        + "заменено; почва выровняется по уровню под твоими ногами.",
                building.title(), building.country().russian(), b[3] - b[0] + 1, b[5] - b[2] + 1,
                ox + b[0], ox + b[3], oz + b[2], oz + b[5], b[4]), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("[Строить]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/gb confirm"))
                .append(Component.text(" — или /gb confirm в течение минуты.", NamedTextColor.GRAY)));
    }

    public void confirm(Player player) {
        Pending p = pendingByPlayer.get(player.getUniqueId());
        if (p == null || System.currentTimeMillis() > p.expiresAt()) {
            pendingByPlayer.remove(player.getUniqueId());
            player.sendMessage(Component.text("Нечего подтверждать: сначала /gb build <здание>.", NamedTextColor.RED));
            return;
        }
        if (isBusy()) {
            player.sendMessage(Component.text("Уже идёт стройка: " + running.building().title(), NamedTextColor.RED));
            return;
        }
        pendingByPlayer.remove(player.getUniqueId());

        BuildJob job = new BuildJob(plugin, p.building(), p.world(), p.ox(), p.oy(), p.oz(), player.getUniqueId());
        running = job;
        job.start(error -> {
            if (running == job) {
                running = null;
            }
            Player owner = Bukkit.getPlayer(player.getUniqueId());
            if (error != null) {
                if (owner != null) {
                    owner.sendMessage(Component.text("Стройка не удалась: " + error, NamedTextColor.RED));
                }
                return;
            }
            Location a = p.anchor();
            BuildingRegistry.Entry entry = new BuildingRegistry.Entry(
                    p.building().id(), p.world().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ());
            registry.add(entry);
            last = job;
            lastEntry = entry;
            plugin.getLogger().info("Built " + p.building().id() + " at " + entry.serialize() + ", "
                    + job.changedBlocks() + " blocks changed");
            if (owner != null) {
                owner.sendMessage(Component.text(p.building().title() + " построен! Изменено блоков: "
                        + job.changedBlocks() + ". Отменить: /gb undo (до перезапуска сервера).", NamedTextColor.GREEN));
            }
        });
    }

    public void undo(Player player) {
        if (isBusy()) {
            player.sendMessage(Component.text("Дождись конца стройки.", NamedTextColor.RED));
            return;
        }
        if (last == null) {
            player.sendMessage(Component.text("Отменять нечего: после перезапуска сервера отмена недоступна.",
                    NamedTextColor.RED));
            return;
        }
        BuildJob job = last;
        BuildingRegistry.Entry entry = lastEntry;
        last = null;
        lastEntry = null;
        running = job;
        job.undo(() -> {
            if (running == job) {
                running = null;
            }
            registry.remove(entry);
            Player owner = Bukkit.getPlayer(player.getUniqueId());
            if (owner != null) {
                owner.sendMessage(Component.text(job.building().title() + " разобран, местность возвращена.",
                        NamedTextColor.GREEN));
            }
        });
    }

    public void teleport(Player player, String buildingId) {
        Building building = catalog.byId(buildingId);
        if (building == null) {
            player.sendMessage(Component.text("Неизвестное здание: " + buildingId, NamedTextColor.RED));
            return;
        }
        Optional<BuildingRegistry.Entry> found = registry.findLatest(building.id());
        if (found.isEmpty()) {
            player.sendMessage(Component.text("Здание " + building.title() + " ещё не построено в мире.",
                    NamedTextColor.RED));
            return;
        }
        BuildingRegistry.Entry entry = found.get();
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            player.sendMessage(Component.text("Мир '" + entry.world() + "' не загружен.", NamedTextColor.RED));
            return;
        }
        Location target = new Location(world, entry.x() + 0.5, entry.y(), entry.z() + 0.5, 180f, 0f);
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("Телепортация ко входу: " + building.title() + ".",
                        NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Не удалось телепортироваться.", NamedTextColor.RED));
            }
        });
    }

    public void shutdown() {
        if (running != null && !running.finished()) {
            running.cancel();
        }
        running = null;
        pendingByPlayer.clear();
        last = null;
        lastEntry = null;
    }

    private boolean isBusy() {
        return running != null && !running.finished();
    }
}

