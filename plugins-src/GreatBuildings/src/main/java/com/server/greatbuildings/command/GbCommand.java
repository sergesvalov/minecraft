package com.server.greatbuildings.command;

import com.server.greatbuildings.buildings.BuildingCatalog;
import com.server.greatbuildings.engine.BuildManager;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Country;
import com.server.greatbuildings.storage.BuildingRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /greatbuildings} ({@code /gb}) subcommands and tab completion.
 */
public final class GbCommand implements TabExecutor {

    private static final String BUILD_PERMISSION = "greatbuildings.build";
    private static final String[] DIRECTIONS = {
            "на севере", "на северо-востоке", "на востоке", "на юго-востоке",
            "на юге", "на юго-западе", "на западе", "на северо-западе"
    };

    private final BuildingCatalog catalog;
    private final BuildingRegistry registry;
    private final BuildManager buildManager;

    public GbCommand(BuildingCatalog catalog, BuildingRegistry registry, BuildManager buildManager) {
        this.catalog = catalog;
        this.registry = registry;
        this.buildManager = buildManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender, args);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "build" -> {
                Player player = requireBuilder(sender);
                if (player == null) {
                    return true;
                }
                Building building = resolveBuilding(args);
                if (building == null) {
                    sender.sendMessage(Component.text(formatCatalogHelp(label), NamedTextColor.YELLOW));
                    return true;
                }
                buildManager.prepare(player, building);
            }
            case "confirm" -> {
                Player player = requireBuilder(sender);
                if (player != null) {
                    buildManager.confirm(player);
                }
            }
            case "undo" -> {
                Player player = requireBuilder(sender);
                if (player != null) {
                    buildManager.undo(player);
                }
            }
            case "tp" -> {
                Player player = requireBuilder(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Использование: /" + label + " tp <здание>", NamedTextColor.YELLOW));
                    return true;
                }
                buildManager.teleport(player, args[1]);
            }
            default -> sender.sendMessage(Component.text(
                    "Команды: /" + label + " list [страна] | build <здание> | confirm | undo | tp <здание>",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private Player requireBuilder(CommandSender sender) {
        if (!sender.hasPermission(BUILD_PERMISSION)) {
            sender.sendMessage(Component.text("Эта команда доступна только суперадмину.", NamedTextColor.RED));
            return null;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игроку.", NamedTextColor.RED));
            return null;
        }
        return player;
    }

    private Building resolveBuilding(String[] args) {
        if (args.length < 2) {
            return null;
        }
        if (args.length >= 3 && Country.fromString(args[1]) != null) {
            Country country = Country.fromString(args[1]);
            Building building = catalog.byId(args[2]);
            if (building != null && building.country() == country) {
                return building;
            }
            return null;
        }
        return catalog.byId(args[1]);
    }

    private String formatCatalogHelp(String label) {
        StringBuilder sb = new StringBuilder("Здания: ");
        for (Country c : Country.values()) {
            List<String> ids = catalog.byCountry(c).stream().map(Building::id).toList();
            if (!ids.isEmpty()) {
                sb.append(c.russian()).append(" (").append(String.join(", ", ids)).append("); ");
            }
        }
        sb.append("Пример: /").append(label).append(" build bagrati или /").append(label).append(" build cyprus paphos");
        return sb.toString();
    }

    private void list(CommandSender sender, String[] args) {
        Country filterCountry = args.length >= 2 ? Country.fromString(args[1]) : null;
        List<BuildingRegistry.Entry> all = registry.all();

        if (all.isEmpty()) {
            sendCatalogFallback(sender);
            return;
        }

        boolean canTeleport = sender instanceof Player && sender.hasPermission(BUILD_PERMISSION);
        sender.sendMessage(Component.text("Великие здания мира:", NamedTextColor.GOLD));

        for (Country country : Country.values()) {
            if (filterCountry != null && filterCountry != country) {
                continue;
            }
            List<BuildingRegistry.Entry> countryEntries = all.stream().filter(e -> {
                Building b = catalog.byId(e.id());
                return b != null && b.country() == country;
            }).toList();

            if (countryEntries.isEmpty()) {
                if (filterCountry != null) {
                    sender.sendMessage(Component.text("  В категории " + country.russian() + " пока ничего не построено.",
                            NamedTextColor.YELLOW));
                }
                continue;
            }

            sender.sendMessage(Component.text("  [" + country.russian() + "]", NamedTextColor.AQUA));
            for (BuildingRegistry.Entry entry : countryEntries) {
                sender.sendMessage(formatEntryComponent(sender, entry, canTeleport));
            }
        }
    }

    private void sendCatalogFallback(CommandSender sender) {
        sender.sendMessage(Component.text("Великих зданий в мире пока нет. Доступны для постройки:", NamedTextColor.YELLOW));
        for (Country c : Country.values()) {
            List<String> ids = catalog.byCountry(c).stream().map(Building::id).toList();
            sender.sendMessage(Component.text("  " + c.russian() + ": " + String.join(", ", ids), NamedTextColor.GRAY));
        }
    }

    private Component formatEntryComponent(CommandSender sender, BuildingRegistry.Entry entry, boolean canTeleport) {
        Building building = catalog.byId(entry.id());
        String title = building != null ? building.title() + " (" + building.where() + ")" : entry.id();
        StringBuilder line = new StringBuilder("    ")
                .append(title).append(" — ")
                .append(entry.x()).append(' ').append(entry.y()).append(' ').append(entry.z());
        if (sender instanceof Player player && player.getWorld().getName().equals(entry.world())) {
            double dx = entry.x() + 0.5 - player.getLocation().getX();
            double dz = entry.z() + 0.5 - player.getLocation().getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double angle = Math.toDegrees(Math.atan2(dx, -dz));
            int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
            line.append(distance < 50 ? ", ты рядом"
                    : String.format(Locale.ROOT, ", %.0f блоков %s", distance, DIRECTIONS[index]));
        } else if (!(sender instanceof Player)) {
            line.append(" (").append(entry.world()).append(')');
        }

        Component msg = Component.text(line.toString(), NamedTextColor.YELLOW);
        if (canTeleport && building != null) {
            msg = msg.append(Component.text(" [ТП]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/gb tp " + building.id()))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Телепортироваться к " + building.title(), NamedTextColor.GRAY))));
        }
        return msg;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean builder = sender.hasPermission(BUILD_PERMISSION);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list"));
            if (builder) {
                options.addAll(List.of("build", "confirm", "undo", "tp"));
            }
            return filterPrefix(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("list")) {
                return filterPrefix(countryIds(), args[1]);
            }
            if (builder && sub.equals("build")) {
                List<String> options = new ArrayList<>(countryIds());
                options.addAll(catalog.ids());
                return filterPrefix(options, args[1]);
            }
            if (builder && sub.equals("tp")) {
                List<String> builtIds = registry.all().stream().map(BuildingRegistry.Entry::id).distinct().toList();
                return filterPrefix(builtIds, args[1]);
            }
        }
        if (args.length == 3 && builder && args[0].equalsIgnoreCase("build")) {
            Country country = Country.fromString(args[1]);
            if (country != null) {
                return filterPrefix(catalog.byCountry(country).stream().map(Building::id).toList(), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> countryIds() {
        List<String> ids = new ArrayList<>(Country.values().length);
        for (Country c : Country.values()) {
            ids.add(c.id());
        }
        return ids;
    }

    private static List<String> filterPrefix(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(lower)).toList();
    }
}

