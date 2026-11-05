package com.server.halloumi;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class HalloumiTraderPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final int SPAWN_ATTEMPTS = 15;

    private NamespacedKey traderKey;
    private NamespacedKey itemKey;
    private BukkitTask spawnTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        traderKey = new NamespacedKey(this, "halloumi_trader");
        itemKey = new NamespacedKey(this, "halloumi");

        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("halloumi");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startSpawnTask();
        getLogger().info("HalloumiTrader enabled");
    }

    @Override
    public void onDisable() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
    }

    private void startSpawnTask() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (!getConfig().getBoolean("random-spawn.enabled", true)) {
            return;
        }
        long periodTicks = Math.max(1, getConfig().getLong("random-spawn.check-interval-minutes", 20)) * 60L * 20L;
        spawnTask = Bukkit.getScheduler().runTaskTimer(this, () -> trySpawnNearRandomPlayer(false), periodTicks, periodTicks);
    }

    /**
     * Picks a random player in an overworld-type world and spawns the trader nearby.
     *
     * @param force skip the chance roll and the max-active limit (used by /halloumi random)
     * @return the player the trader came to, or null if nothing was spawned
     */
    private Player trySpawnNearRandomPlayer(boolean force) {
        FileConfiguration cfg = getConfig();
        if (!force) {
            if (countActiveTraders() >= cfg.getInt("random-spawn.max-active", 1)) {
                return null;
            }
            if (ThreadLocalRandom.current().nextDouble() >= cfg.getDouble("random-spawn.chance", 0.5)) {
                return null;
            }
        }

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .map(p -> (Player) p)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Location location = findSpawnLocation(target.getLocation());
        if (location == null) {
            return null;
        }

        spawnTrader(location);
        String message = cfg.getString("announce-message", "").replace("%player%", target.getName());
        if (!message.isBlank()) {
            Bukkit.broadcast(Component.text(message, NamedTextColor.GOLD));
        }
        return target;
    }

    private Location findSpawnLocation(Location center) {
        World world = center.getWorld();
        double minDistance = getConfig().getDouble("random-spawn.min-distance", 10);
        double maxDistance = Math.max(minDistance, getConfig().getDouble("random-spawn.max-distance", 20));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < SPAWN_ATTEMPTS; i++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);

            Block ground = world.getHighestBlockAt(x, z);
            if (ground.isLiquid() || !ground.getType().isSolid()) {
                continue;
            }
            Block feet = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);
            if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
                continue;
            }
            return feet.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    private WanderingTrader spawnTrader(Location location) {
        FileConfiguration cfg = getConfig();
        String name = cfg.getString("trader-name", "Торговец халуми");
        int despawnTicks = Math.max(1, cfg.getInt("despawn-minutes", 30)) * 60 * 20;

        return location.getWorld().spawn(location, WanderingTrader.class, trader -> {
            trader.customName(Component.text(name, NamedTextColor.GOLD));
            trader.setCustomNameVisible(true);
            trader.getPersistentDataContainer().set(traderKey, PersistentDataType.BYTE, (byte) 1);
            trader.setRecipes(List.of(createRecipe()));
            trader.setDespawnDelay(despawnTicks);
        });
    }

    private MerchantRecipe createRecipe() {
        FileConfiguration cfg = getConfig();
        MerchantRecipe recipe = new MerchantRecipe(
                createHalloumi(Math.max(1, cfg.getInt("trade.amount", 4))),
                Math.max(1, cfg.getInt("trade.max-uses", 16)));
        recipe.addIngredient(new ItemStack(Material.EMERALD, Math.max(1, cfg.getInt("trade.price-emeralds", 2))));
        recipe.setExperienceReward(false);
        return recipe;
    }

    private ItemStack createHalloumi(int amount) {
        FileConfiguration cfg = getConfig();
        ItemStack item = new ItemStack(Material.BREAD, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text(cfg.getString("halloumi.name", "Халуми"), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(cfg.getStringList("halloumi.lore").stream()
                    .map(line -> (Component) Component.text(line, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        });
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(cfg.getInt("halloumi.nutrition", 8))
                .saturation((float) cfg.getDouble("halloumi.saturation", 12.8))
                .build());
        return item;
    }

    private boolean isHalloumiTrader(org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
    }

    private int countActiveTraders() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (WanderingTrader trader : world.getEntitiesByClass(WanderingTrader.class)) {
                if (isHalloumiTrader(trader)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Keep the trader's offer list to halloumi only. */
    @EventHandler(ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (isHalloumiTrader(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only a player can use /halloumi spawn");
                    return true;
                }
                Vector direction = player.getLocation().getDirection().setY(0);
                if (direction.lengthSquared() < 1.0E-6) {
                    direction = new Vector(1, 0, 0);
                }
                Location location = player.getLocation().add(direction.normalize().multiply(2));
                spawnTrader(location);
                sender.sendMessage(Component.text("Торговец халуми вызван.", NamedTextColor.GREEN));
            }
            case "random" -> {
                Player target = trySpawnNearRandomPlayer(true);
                if (target == null) {
                    sender.sendMessage(Component.text(
                            "Не получилось: нет игроков в обычном мире или не нашлось места.", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Торговец появился рядом с " + target.getName() + ".", NamedTextColor.GREEN));
                }
            }
            case "give" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only a player can use /halloumi give");
                    return true;
                }
                int amount = 4;
                if (args.length > 1) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Количество должно быть числом.", NamedTextColor.RED));
                        return true;
                    }
                }
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(createHalloumi(amount));
                leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                sender.sendMessage(Component.text("Выдано халуми: " + amount, NamedTextColor.GREEN));
            }
            case "count" -> sender.sendMessage(Component.text(
                    "Торговцев халуми в загруженных чанках: " + countActiveTraders(), NamedTextColor.YELLOW));
            case "reload" -> {
                reloadConfig();
                startSpawnTask();
                sender.sendMessage(Component.text("Конфиг HalloumiTrader перезагружен.", NamedTextColor.GREEN));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "random", "give", "count", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
