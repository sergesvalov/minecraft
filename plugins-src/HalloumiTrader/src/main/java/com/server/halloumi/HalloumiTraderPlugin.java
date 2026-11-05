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
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HalloumiTraderPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final int SPAWN_ATTEMPTS = 15;
    private static final long HINT_COOLDOWN_MS = 60_000;
    private static final String[] DIRECTIONS = {"на севере", "на северо-востоке", "на востоке", "на юго-востоке",
            "на юге", "на юго-западе", "на западе", "на северо-западе"};

    /** Wandering halloumi traders: random visits and tavern guests. */
    private NamespacedKey traderKey;
    /** Set in addition to traderKey on tavern guests, so they don't count towards random-spawn.max-active. */
    private NamespacedKey guestKey;
    /** The innkeeper who always stands behind a tavern counter. */
    private NamespacedKey innkeeperKey;
    private NamespacedKey itemKey;
    private NamespacedKey dishKey;
    private List<Dish> menu = List.of();

    private BukkitTask spawnTask;
    private BukkitTask tavernTask;
    private TavernManager taverns;
    private TavernPopulator populator;
    private final Map<UUID, Long> hintCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Add keys introduced by newer versions (tavern, menu) to an existing config.yml
        getConfig().options().copyDefaults(true);
        saveConfig();
        traderKey = new NamespacedKey(this, "halloumi_trader");
        guestKey = new NamespacedKey(this, "tavern_guest");
        innkeeperKey = new NamespacedKey(this, "innkeeper");
        itemKey = new NamespacedKey(this, "halloumi");
        dishKey = new NamespacedKey(this, "dish");
        menu = Dish.load(getConfig(), getLogger());

        taverns = new TavernManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("halloumi");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startSpawnTask();
        registerPopulator();
        tavernTask = Bukkit.getScheduler().runTaskTimer(this, taverns::tick, 200L, 200L);
        getLogger().info("HalloumiTrader enabled, known taverns: " + taverns.all().size());
    }

    @Override
    public void onDisable() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        if (tavernTask != null) {
            tavernTask.cancel();
        }
        unregisterPopulator();
        if (taverns != null) {
            taverns.save();
        }
    }

    NamespacedKey traderKey() {
        return traderKey;
    }

    NamespacedKey guestKey() {
        return guestKey;
    }

    NamespacedKey innkeeperKey() {
        return innkeeperKey;
    }

    // ---------------------------------------------------------------- tavern generation

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
        if (populator != null && getConfig().getStringList("tavern.worlds").contains(world.getName())
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

    /** Builds a tavern right in front of (north of) the player: /halloumi tavern [palette]. */
    private void buildTavernAt(Player player, TavernPalette forced) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int ox = loc.getBlockX() - 5;
        int oz = loc.getBlockZ() - 13; // doorstep ends up 3 blocks north of the player
        int oy = loc.getBlockY();
        TavernPalette palette = forced != null ? forced
                : TavernPalette.forBiome(world.getBiome(loc).getKey().getKey());
        if (palette == null) {
            palette = TavernPalette.OAK;
        }

        TavernBuilder.build(new TavernBuilder.Blocks() {
            @Override
            public Material type(int x, int y, int z) {
                return world.getType(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, BlockData data) {
                Block block = world.getBlockAt(x, y, z);
                block.setBlockData(data, false); // no physics: doors and lanterns are placed piece by piece
            }

            @Override
            public int highestY(int x, int z) {
                return world.getHighestBlockYAt(x, z);
            }
        }, ox, oy, oz, palette);

        taverns.add(new Tavern(world.getName(), ox, oy, oz, false));
        taverns.tick(); // innkeeper and sign right away
        player.sendMessage(Component.text("Таверна построена (" + palette.name().toLowerCase(Locale.ROOT)
                + ") на " + ox + " " + oy + " " + oz + ".", NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- random visits

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

    WanderingTrader spawnTrader(Location location) {
        return spawnTrader(location, false);
    }

    WanderingTrader spawnTrader(Location location, boolean tavernGuest) {
        FileConfiguration cfg = getConfig();
        String name = cfg.getString("trader-name", "Торговец халуми");
        int despawnTicks = Math.max(1, cfg.getInt("despawn-minutes", 30)) * 60 * 20;
        int maxUses = Math.max(1, cfg.getInt("trade.max-uses", 16));

        return location.getWorld().spawn(location, WanderingTrader.class, trader -> {
            trader.customName(Component.text(name, NamedTextColor.GOLD));
            trader.setCustomNameVisible(true);
            trader.getPersistentDataContainer().set(traderKey, PersistentDataType.BYTE, (byte) 1);
            if (tavernGuest) {
                trader.getPersistentDataContainer().set(guestKey, PersistentDataType.BYTE, (byte) 1);
            }
            trader.setRecipes(List.of(createRecipe(maxUses)));
            trader.setDespawnDelay(despawnTicks);
        });
    }

    MerchantRecipe createRecipe(int maxUses) {
        FileConfiguration cfg = getConfig();
        MerchantRecipe recipe = new MerchantRecipe(
                createHalloumi(Math.max(1, cfg.getInt("trade.amount", 4))), Math.max(1, maxUses));
        recipe.addIngredient(new ItemStack(Material.EMERALD, Math.max(1, cfg.getInt("trade.price-emeralds", 2))));
        recipe.setExperienceReward(false);
        return recipe;
    }

    // ---------------------------------------------------------------- tavern menu

    /** Halloumi first, then the Cypriot menu from tavern.menu. */
    List<MerchantRecipe> innkeeperRecipes() {
        int maxUses = getConfig().getInt("tavern.innkeeper-max-uses", 64);
        List<MerchantRecipe> recipes = new java.util.ArrayList<>();
        recipes.add(createRecipe(maxUses));
        for (Dish dish : menu) {
            MerchantRecipe recipe = new MerchantRecipe(dish.create(dishKey), Math.max(1, maxUses));
            recipe.addIngredient(new ItemStack(Material.EMERALD, dish.price()));
            recipe.setExperienceReward(false);
            recipes.add(recipe);
        }
        return recipes;
    }

    int menuSize() {
        return 1 + menu.size();
    }

    /** Written book for the lectern: the menu with prices. */
    ItemStack createMenuBook() {
        FileConfiguration cfg = getConfig();
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(org.bukkit.inventory.meta.BookMeta.class, meta -> {
            meta.setTitle(cfg.getString("tavern.menu-title", "Меню"));
            meta.setAuthor(cfg.getString("tavern.innkeeper-name", "Трактирщик"));

            List<Component> pages = new java.util.ArrayList<>();
            pages.add(Component.text(cfg.getString("tavern.menu-title", "Меню"), NamedTextColor.DARK_RED)
                    .decorate(TextDecoration.BOLD)
                    .appendNewline().appendNewline()
                    .append(Component.text(cfg.getString("tavern.menu-intro",
                            "Кипрское мезе. Цены в изумрудах, заказ — у трактирщика за стойкой."), NamedTextColor.BLACK)));

            List<Component> entries = new java.util.ArrayList<>();
            entries.add(menuEntry(cfg.getString("halloumi.name", "Халуми"), cfg.getStringList("halloumi.lore"),
                    cfg.getInt("trade.amount", 4), cfg.getInt("trade.price-emeralds", 2)));
            for (Dish dish : menu) {
                entries.add(menuEntry(dish.name(), dish.lore(), Math.min(dish.amount(), dish.base().getMaxStackSize()), dish.price()));
            }
            // Three dishes per page fit comfortably
            for (int i = 0; i < entries.size(); i += 3) {
                Component page = Component.empty();
                for (int j = i; j < Math.min(i + 3, entries.size()); j++) {
                    page = page.append(entries.get(j)).appendNewline();
                }
                pages.add(page);
            }
            meta.addPages(pages.toArray(new Component[0]));
        });
        return book;
    }

    private static Component menuEntry(String name, List<String> lore, int amount, int price) {
        Component entry = Component.text(name, NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD).appendNewline();
        if (!lore.isEmpty()) {
            entry = entry.append(Component.text(lore.get(0), NamedTextColor.DARK_GRAY)).appendNewline();
        }
        return entry.append(Component.text(amount + " шт. — " + price + " изумр.", NamedTextColor.DARK_GREEN)).appendNewline();
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

    private boolean has(Entity entity, NamespacedKey key) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Random-visit traders only: tavern guests and innkeepers don't count. */
    private int countActiveTraders() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (WanderingTrader trader : world.getEntitiesByClass(WanderingTrader.class)) {
                if (has(trader, traderKey) && !has(trader, guestKey)) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- events

    /** Keep the offer list to halloumi only. */
    @EventHandler(ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (has(event.getEntity(), traderKey) || has(event.getEntity(), innkeeperKey)) {
            event.setCancelled(true);
        }
    }

    /** A wandering halloumi trader tells roughly where the nearest tavern is — hard to find, but possible. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof WanderingTrader) || !has(entity, traderKey)
                || !getConfig().getBoolean("tavern.hints", true)) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = hintCooldowns.get(player.getUniqueId());
        if (last != null && now - last < HINT_COOLDOWN_MS) {
            return;
        }
        hintCooldowns.put(player.getUniqueId(), now);

        String name = getConfig().getString("trader-name", "Торговец халуми");
        Tavern tavern = taverns.nearest(player.getLocation());
        String text;
        if (tavern == null) {
            text = "Говорят, где-то есть таверна, где халуми подают каждый день. Но где — не знаю.";
        } else {
            double dx = tavern.centerX() - player.getLocation().getX();
            double dz = tavern.centerZ() - player.getLocation().getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 64) {
                text = "Таверна совсем рядом — принюхайся!";
            } else {
                double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north (-z), 90 = east (+x)
                int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
                long rounded = Math.max(100, Math.round(distance / 100.0) * 100);
                text = "Ближайшая таверна " + DIRECTIONS[index] + ", примерно в " + rounded + " блоках отсюда.";
            }
        }
        player.sendMessage(Component.text("<" + name + "> ", NamedTextColor.GOLD)
                .append(Component.text(text, NamedTextColor.YELLOW)));
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("tavern")) {
            if (!sender.hasPermission("halloumi.tavern")) {
                sender.sendMessage(Component.text("Строить таверны может только суперадмин.", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only a player can use /halloumi tavern");
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
            buildTavernAt(player, palette);
            return true;
        }

        if (!sender.hasPermission("halloumi.admin")) {
            sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }
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
                    "Странствующих торговцев халуми в загруженных чанках: " + countActiveTraders()
                            + ", известных таверн: " + taverns.all().size(), NamedTextColor.YELLOW));
            case "taverns" -> {
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
                                    "  %d %d %d — %.0f блоков", t.x() + 5, t.y() + 1, t.z() + 10,
                                    Math.sqrt(TavernManager.distanceSq(t, player.getLocation()))), NamedTextColor.GRAY)));
                }
            }
            case "reload" -> {
                reloadConfig();
                menu = Dish.load(getConfig(), getLogger());
                startSpawnTask();
                registerPopulator();
                sender.sendMessage(Component.text("Конфиг HalloumiTrader перезагружен.", NamedTextColor.GREEN));
            }
            default -> {
                return false;
            }
        }
        return true;
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
            if (sender.hasPermission("halloumi.tavern")) {
                options.add("tavern");
            }
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tavern") && sender.hasPermission("halloumi.tavern")) {
            return Arrays.stream(TavernPalette.values()).map(p -> p.name().toLowerCase(Locale.ROOT))
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
