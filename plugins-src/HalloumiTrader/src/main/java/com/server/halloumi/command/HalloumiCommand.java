package com.server.halloumi.command;

import com.server.halloumi.HalloumiTraderPlugin;
import com.server.halloumi.model.Tavern;
import com.server.halloumi.model.TavernPalette;
import com.server.halloumi.tavern.TavernLayout;
import com.server.halloumi.tavern.TavernManager;
import com.server.halloumi.trade.MenuService;
import com.server.halloumi.trade.TraderService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@code /halloumi} subcommands and tab completion.
 */
public final class HalloumiCommand implements TabExecutor {

    private final HalloumiTraderPlugin plugin;
    private final TraderService traders;
    private final MenuService menu;
    private final TavernManager taverns;

    public HalloumiCommand(HalloumiTraderPlugin plugin, TraderService traders, MenuService menu, TavernManager taverns) {
        this.plugin = plugin;
        this.traders = traders;
        this.menu = menu;
        this.taverns = taverns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("tavern") || sub.equals("inn")) {
            return handleTavernBuild(sender, sub, args);
        }

        if (!sender.hasPermission("halloumi.admin")) {
            sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }
        switch (sub) {
            case "spawn" -> handleSpawn(sender);
            case "random" -> handleRandom(sender);
            case "give" -> handleGive(sender, args);
            case "count" -> sender.sendMessage(Component.text(
                    "Странствующих торговцев халуми в загруженных чанках: " + traders.countActiveTraders()
                            + ", известных таверн: " + taverns.all().size(), NamedTextColor.YELLOW));
            case "taverns" -> handleTaverns(sender);
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("Конфиг HalloumiTrader перезагружен.", NamedTextColor.GREEN));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean handleTavernBuild(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("halloumi." + sub)) {
            sender.sendMessage(Component.text("Строить таверны может только суперадмин.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use /halloumi " + sub);
            return true;
        }
        TavernPalette palette = null;
        if (args.length > 1) {
            try {
                palette = TavernPalette.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text("Стили: " + paletteNames(), NamedTextColor.RED));
                return true;
            }
        }
        taverns.buildTavernAt(player, palette);
        return true;
    }

    private void handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use /halloumi spawn");
            return;
        }
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            direction = new Vector(1, 0, 0);
        }
        Location location = player.getLocation().add(direction.normalize().multiply(2));
        traders.spawnTrader(location);
        sender.sendMessage(Component.text("Торговец халуми вызван.", NamedTextColor.GREEN));
    }

    private void handleRandom(CommandSender sender) {
        Player target = traders.trySpawnNearRandomPlayer(true);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Не получилось: нет игроков в обычном мире или не нашлось места.", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("Торговец появился рядом с " + target.getName() + ".", NamedTextColor.GREEN));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use /halloumi give");
            return;
        }
        int amount = 4;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Количество должно быть числом.", NamedTextColor.RED));
                return;
            }
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(
                menu.createHalloumi(plugin.getConfig(), amount));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        sender.sendMessage(Component.text("Выдано халуми: " + amount, NamedTextColor.GREEN));
    }

    private void handleTaverns(CommandSender sender) {
        List<Tavern> all = taverns.all();
        sender.sendMessage(Component.text("Известных таверн: " + all.size(), NamedTextColor.YELLOW));
        if (sender instanceof Player player) {
            all.stream()
                    .filter(t -> t.world().equals(player.getWorld().getName()))
                    .sorted((a, b) -> Double.compare(
                            TavernManager.distanceSq(a, player.getLocation()),
                            TavernManager.distanceSq(b, player.getLocation())))
                    .limit(5)
                    .forEach(t -> sender.sendMessage(Component.text(String.format(Locale.ROOT,
                            "  %d %d %d — %.0f блоков%s", t.x() + TavernLayout.DOOR_X, t.y() + 1,
                            t.z() + TavernLayout.SIZE_Z,
                            Math.sqrt(TavernManager.distanceSq(t, player.getLocation())),
                            t.state() == Tavern.State.PENDING ? " (ещё не построена)" : ""),
                            NamedTextColor.GRAY)));
        }
    }

    private static String paletteNames() {
        return String.join(", ", Arrays.stream(TavernPalette.values())
                .map(p -> p.name().toLowerCase(Locale.ROOT)).toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Set<String> options = new HashSet<>();
            if (sender.hasPermission("halloumi.admin")) {
                options.addAll(List.of("spawn", "random", "give", "count", "taverns", "reload"));
            }
            for (String build : List.of("tavern", "inn")) {
                if (sender.hasPermission("halloumi." + build)) {
                    options.add(build);
                }
            }
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        String build = args.length == 2 ? args[0].toLowerCase(Locale.ROOT) : "";
        if ((build.equals("tavern") || build.equals("inn")) && sender.hasPermission("halloumi." + build)) {
            return Arrays.stream(TavernPalette.values()).map(p -> p.name().toLowerCase(Locale.ROOT))
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}

