package com.server.greatbuildings;

import com.server.greatbuildings.cyprus.Paphos;
import com.server.greatbuildings.georgia.Bagrati;
import com.server.greatbuildings.georgia.Svetitskhoveli;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Великие здания мира: famous buildings at 1:1 scale, built in front of the superadmin on command.
 * Categorized by country (Georgia, Cyprus, etc.).
 * Building is limited to greatbuildings.build (default false, granted to papa only at server start).
 */
public final class GreatBuildingsPlugin extends JavaPlugin implements TabExecutor {

    private static final long CONFIRM_MS = 60_000;
    private static final String[] DIRECTIONS = {"на севере", "на северо-востоке", "на востоке", "на юго-востоке",
            "на юге", "на юго-западе", "на западе", "на северо-западе"};

    private final Map<String, Building> catalog = new LinkedHashMap<>();
    private Registry registry;
    /** The build in progress, if any: one at a time. */
    private BuildJob running;
    /** The last finished build of this server run, for /gb undo. */
    private BuildJob last;
    private Registry.Entry lastEntry;
    private Pending pending;

    private record Pending(UUID player, Building building, World world, int ox, int oy, int oz, Location anchor,
                           long expires) {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Georgian buildings
        register(new Svetitskhoveli());
        register(new Bagrati());

        // Cypriot buildings
        register(new Paphos());

        registry = new Registry(getDataFolder(), getLogger());
        var command = getCommand("greatbuildings");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getLogger().info("GreatBuildings enabled: " + String.join(", ", catalog.keySet())
                + "; built: " + registry.all().size());
    }

    @Override
    public void onDisable() {
        if (running != null && !running.finished()) {
            running.cancel();
            getLogger().warning("Server stopped in the middle of building " + running.building.id()
                    + ": it stays half-built");
        }
    }

    private void register(Building building) {
        catalog.put(building.id(), building);
    }

    public List<Building> buildingsByCountry(Country country) {
        return catalog.values().stream().filter(b -> b.country() == country).toList();
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> list(sender, args);
            case "build" -> {
                if (!canBuild(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text(formatCatalogHelp(label), NamedTextColor.YELLOW));
                    return true;
                }

                // Check /gb build <country> <building> or /gb build <building>
                String firstArg = args[1].toLowerCase(Locale.ROOT);
                Country country = Country.fromString(firstArg);

                if (country != null) {
                    if (args.length < 3) {
                        List<String> inCountry = buildingsByCountry(country).stream().map(Building::id).toList();
                        sender.sendMessage(Component.text("Здания (" + country.titleRu() + "): "
                                + String.join(", ", inCountry) + ". Пример: /" + label + " build "
                                + country.id() + " " + (inCountry.isEmpty() ? "<здание>" : inCountry.get(0)), NamedTextColor.YELLOW));
                        return true;
                    }
                    String buildingId = args[2].toLowerCase(Locale.ROOT);
                    Building building = catalog.get(buildingId);
                    if (building == null || building.country() != country) {
                        List<String> inCountry = buildingsByCountry(country).stream().map(Building::id).toList();
                        sender.sendMessage(Component.text("В категории " + country.titleRu() + " нет здания '"
                                + buildingId + "'. Есть: " + String.join(", ", inCountry), NamedTextColor.RED));
                        return true;
                    }
                    prepare((Player) sender, building);
                    return true;
                }

                // Direct lookup: /gb build <buildingId>
                Building building = catalog.get(firstArg);
                if (building == null) {
                    sender.sendMessage(Component.text("Нет такого здания. " + formatCatalogHelp(label), NamedTextColor.RED));
                    return true;
                }
                prepare((Player) sender, building);
            }
            case "confirm" -> {
                if (canBuild(sender)) {
                    confirm((Player) sender);
                }
            }
            case "undo" -> {
                if (canBuild(sender)) {
                    undo((Player) sender);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private String formatCatalogHelp(String label) {
        StringBuilder sb = new StringBuilder("Здания: ");
        for (Country c : Country.values()) {
            List<String> ids = buildingsByCountry(c).stream().map(Building::id).toList();
            if (!ids.isEmpty()) {
                sb.append(c.titleRu()).append(" (").append(String.join(", ", ids)).append("); ");
            }
        }
        sb.append("Пример: /").append(label).append(" build bagrati или /").append(label).append(" build cyprus paphos");
        return sb.toString();
    }

    private boolean canBuild(CommandSender sender) {
        if (!sender.hasPermission("greatbuildings.build")) {
            sender.sendMessage(Component.text("Строить великие здания может только суперадмин.", NamedTextColor.RED));
            return false;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only a player can build: the building goes in front of them.");
            return false;
        }
        return true;
    }

    /** Works out where the building goes (north of the player, entrance facing them) and asks to confirm. */
    private void prepare(Player player, Building building) {
        if (running != null && !running.finished()) {
            player.sendMessage(Component.text("Уже идёт стройка: " + running.building.title(), NamedTextColor.RED));
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
        pending = new Pending(player.getUniqueId(), building, world, ox, oy, oz, loc.toBlockLocation(),
                System.currentTimeMillis() + CONFIRM_MS);
        player.sendMessage(Component.text(String.format(Locale.ROOT,
                "%s (%s): %d × %d блоков к северу от тебя (x %d…%d, z %d…%d), высота %d. Всё, что там стоит, будет "
                        + "заменено; почва выровняется по уровню под твоими ногами.",
                building.title(), building.country().titleRu(), b[3] - b[0] + 1, b[5] - b[2] + 1, ox + b[0], ox + b[3], oz + b[2], oz + b[5],
                b[4]), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("[Строить]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/gb confirm"))
                .append(Component.text(" — или /gb confirm в течение минуты.", NamedTextColor.GRAY)));
    }

    private void confirm(Player player) {
        Pending p = pending;
        if (p == null || !p.player().equals(player.getUniqueId()) || System.currentTimeMillis() > p.expires()) {
            player.sendMessage(Component.text("Нечего подтверждать: сначала /gb build <здание>.", NamedTextColor.RED));
            return;
        }
        if (running != null && !running.finished()) {
            player.sendMessage(Component.text("Уже идёт стройка: " + running.building.title(), NamedTextColor.RED));
            return;
        }
        pending = null;
        BuildJob job = new BuildJob(this, p.building(), p.world(), p.ox(), p.oy(), p.oz(), player.getUniqueId());
        running = job;
        job.start(error -> {
            UUID id = p.player();
            Player owner = getServer().getPlayer(id);
            if (error != null) {
                if (owner != null) {
                    owner.sendMessage(Component.text("Стройка не удалась: " + error, NamedTextColor.RED));
                }
                return;
            }
            Location a = p.anchor();
            Registry.Entry entry = new Registry.Entry(p.building().id(), p.world().getName(),
                    a.getBlockX(), a.getBlockY(), a.getBlockZ());
            registry.add(entry);
            last = job;
            lastEntry = entry;
            getLogger().info("Built " + p.building().id() + " at " + entry.serialize() + ", "
                    + job.changedBlocks() + " blocks changed");
            if (owner != null) {
                owner.sendMessage(Component.text(p.building().title() + " построен! Изменено блоков: "
                        + job.changedBlocks() + ". Отменить: /gb undo (до перезапуска сервера).", NamedTextColor.GREEN));
            }
        });
    }

    private void undo(Player player) {
        if (running != null && !running.finished()) {
            player.sendMessage(Component.text("Дождись конца стройки.", NamedTextColor.RED));
            return;
        }
        if (last == null) {
            player.sendMessage(Component.text("Отменять нечего: после перезапуска сервера отмена недоступна.",
                    NamedTextColor.RED));
            return;
        }
        BuildJob job = last;
        Registry.Entry entry = lastEntry;
        last = null;
        lastEntry = null;
        running = job;
        job.undo(() -> {
            registry.remove(entry);
            running = null;
            player.sendMessage(Component.text(job.building.title() + " разобран, местность возвращена.",
                    NamedTextColor.GREEN));
        });
    }

    private void list(CommandSender sender, String[] args) {
        Country filterCountry = args.length >= 2 ? Country.fromString(args[1]) : null;
        List<Registry.Entry> all = registry.all();

        if (all.isEmpty()) {
            sender.sendMessage(Component.text("Великих зданий в мире пока нет. Доступны для постройки:", NamedTextColor.YELLOW));
            for (Country c : Country.values()) {
                List<String> ids = buildingsByCountry(c).stream().map(Building::id).toList();
                sender.sendMessage(Component.text("  " + c.titleRu() + ": " + String.join(", ", ids), NamedTextColor.GRAY));
            }
            return;
        }

        sender.sendMessage(Component.text("Великие здания мира:", NamedTextColor.GOLD));

        for (Country country : Country.values()) {
            if (filterCountry != null && filterCountry != country) {
                continue;
            }
            List<Registry.Entry> countryEntries = all.stream().filter(e -> {
                Building b = catalog.get(e.id());
                return b != null && b.country() == country;
            }).toList();

            if (countryEntries.isEmpty() && filterCountry != null) {
                sender.sendMessage(Component.text("  В категории " + country.titleRu() + " пока ничего не построено.", NamedTextColor.YELLOW));
                continue;
            }

            if (!countryEntries.isEmpty()) {
                sender.sendMessage(Component.text("  [" + country.titleRu() + "]", NamedTextColor.AQUA));
                for (Registry.Entry e : countryEntries) {
                    Building building = catalog.get(e.id());
                    String title = building != null ? building.title() + " (" + building.where() + ")" : e.id();
                    String line = "    " + title + " — " + e.x() + " " + e.y() + " " + e.z();
                    if (sender instanceof Player player && player.getWorld().getName().equals(e.world())) {
                        double dx = e.x() + 0.5 - player.getLocation().getX();
                        double dz = e.z() + 0.5 - player.getLocation().getZ();
                        double distance = Math.sqrt(dx * dx + dz * dz);
                        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north (-z), 90 = east (+x)
                        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
                        line += distance < 50 ? ", ты рядом" : String.format(Locale.ROOT, ", %.0f блоков %s", distance, DIRECTIONS[index]);
                    } else if (!(sender instanceof Player)) {
                        line += " (" + e.world() + ")";
                    }
                    sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean builder = sender.hasPermission("greatbuildings.build");
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list"));
            if (builder) {
                options.addAll(List.of("build", "confirm", "undo"));
            }
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("list")) {
                List<String> countries = new ArrayList<>();
                for (Country c : Country.values()) {
                    countries.add(c.id());
                }
                return countries.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (builder && args[0].equalsIgnoreCase("build")) {
                List<String> options = new ArrayList<>();
                for (Country c : Country.values()) {
                    options.add(c.id());
                }
                options.addAll(catalog.keySet());
                return options.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
        }
        if (args.length == 3 && builder && args[0].equalsIgnoreCase("build")) {
            Country country = Country.fromString(args[1]);
            if (country != null) {
                return buildingsByCountry(country).stream()
                        .map(Building::id)
                        .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .toList();
            }
        }
        return List.of();
    }
}
