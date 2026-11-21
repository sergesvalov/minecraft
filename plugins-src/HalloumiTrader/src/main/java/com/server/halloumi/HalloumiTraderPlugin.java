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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HalloumiTraderPlugin extends JavaPlugin implements Listener {

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
    private Seats seats;
    private Residents residents;
    private final Map<UUID, Long> hintCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateMenu();
        // Add keys introduced by newer versions (tavern, menu) to an existing config.yml
        getConfig().options().copyDefaults(true);
        saveConfig();
        traderKey = new NamespacedKey(this, "halloumi_trader");
        guestKey = new NamespacedKey(this, "tavern_guest");
        innkeeperKey = new NamespacedKey(this, "innkeeper");
        itemKey = new NamespacedKey(this, "halloumi");
        dishKey = new NamespacedKey(this, "dish");
        menu = Dish.load(getConfig(), getLogger());

        seats = new Seats(this);
        residents = new Residents(this);
        taverns = new TavernManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(seats, this);
        getServer().getPluginManager().registerEvents(residents, this);
        residents.start();
        var command = getCommand("halloumi");
        if (command != null) {
            HalloumiCommand handler = new HalloumiCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
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
        if (residents != null) {
            residents.stop();
        }
        if (seats != null) {
            seats.removeSeats();
        }
        if (taverns != null) {
            taverns.save();
        }
    }

    NamespacedKey traderKey() {
        return traderKey;
    }

    Seats seats() {
        return seats;
    }

    Residents residents() {
        return residents;
    }

    NamespacedKey guestKey() {
        return guestKey;
    }

    NamespacedKey innkeeperKey() {
        return innkeeperKey;
    }

    TavernManager taverns() {
        return taverns;
    }

    void reloadPlugin() {
        reloadConfig();
        menu = Dish.load(getConfig(), getLogger());
        startSpawnTask();
        registerPopulator();
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

    /**
     * /halloumi tavern [palette] (or inn): the tavern with its yard right in front of (north of) the player:
     * the yard gate 3 blocks away, the front door in line with the player.
     */
    void buildTavernAt(Player player, TavernPalette forced) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int ox = loc.getBlockX() - TavernBuilder.DOOR_X;
        int oz = loc.getBlockZ() - 3 - TavernBuilder.YARD_END_Z;
        int oy = loc.getBlockY();
        if (oy + TavernBuilder.HEIGHT >= world.getMaxHeight()) {
            player.sendMessage(Component.text("Слишком высоко для таверны.", NamedTextColor.RED));
            return;
        }
        TavernPalette palette = forced != null ? forced
                : TavernPalette.forBiome(world.getBiome(loc).getKey().getKey());
        if (palette == null) {
            palette = TavernPalette.OAK;
        }

        TavernBuilder.build(TavernBuilder.worldBlocks(world), ox, oy, oz, palette);
        taverns.add(new Tavern(world.getName(), ox, oy, oz, false, Tavern.State.BUILT));
        taverns.tick(); // innkeeper, signs and cushions right away
        player.sendMessage(Component.text("Таверна построена (" + palette.name().toLowerCase(Locale.ROOT) + ") на "
                + ox + " " + oy + " " + oz + ".", NamedTextColor.GREEN));
    }

    /**
     * Dishes added to the default menu in a newer version are appended to an existing config.yml once
     * (copyDefaults doesn't merge lists). tavern.menu-version remembers it, so dishes removed later stay removed.
     */
    private void migrateMenu() {
        FileConfiguration cfg = getConfig();
        var defaults = cfg.getDefaults();
        if (defaults == null) {
            return;
        }
        int current = defaults.getInt("tavern.menu-version", 1);
        int seen = cfg.contains("tavern.menu-version", true) ? cfg.getInt("tavern.menu-version") : 1;
        if (seen >= current || !cfg.contains("tavern.menu", true)) {
            return;
        }
        List<Map<?, ?>> menuList = new ArrayList<>(cfg.getMapList("tavern.menu"));
        Set<String> ids = new HashSet<>();
        menuList.forEach(dish -> ids.add(String.valueOf(dish.get("id"))));
        int added = 0;
        for (Map<?, ?> dish : defaults.getMapList("tavern.menu")) {
            if (ids.add(String.valueOf(dish.get("id")))) {
                menuList.add(dish);
                added++;
            }
        }
        cfg.set("tavern.menu", menuList);
        cfg.set("tavern.menu-version", current);
        getLogger().info("tavern.menu: added " + added + " new dishes to config.yml");
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
    Player trySpawnNearRandomPlayer(boolean force) {
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

    ItemStack createHalloumi(int amount) {
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
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, Dish.modelData("halloumi"));
        return item;
    }

    private boolean has(Entity entity, NamespacedKey key) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Random-visit traders only: tavern guests and innkeepers don't count. */
    int countActiveTraders() {
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
        if (has(event.getEntity(), traderKey) || has(event.getEntity(), innkeeperKey)
                || residents.isResident(event.getEntity())) {
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
                long rounded = Math.max(100, Math.round(distance / 100.0) * 100);
                text = "Ближайшая таверна " + compassDirection(dx, dz) + ", примерно в " + rounded + " блоках отсюда.";
            }
        }
        player.sendMessage(Component.text("<" + name + "> ", NamedTextColor.GOLD)
                .append(Component.text(text, NamedTextColor.YELLOW)));
    }

    static String compassDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north (-z), 90 = east (+x)
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        return DIRECTIONS[index];
    }
}
