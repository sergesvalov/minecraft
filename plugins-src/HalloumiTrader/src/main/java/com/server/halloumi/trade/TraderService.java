package com.server.halloumi.trade;

import com.server.halloumi.model.Tavern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Manages wandering halloumi traders, tavern guest/innkeeper PDC identity tags, random player visits,
 * trade locking, and compass hints toward the nearest tavern.
 */
public final class TraderService implements Listener {

    private static final int SPAWN_ATTEMPTS = 15;
    static final long HINT_COOLDOWN_MS = 60_000L;
    private static final String[] DIRECTIONS = {
            "на севере", "на северо-востоке", "на востоке", "на юго-востоке",
            "на юге", "на юго-западе", "на западе", "на северо-западе"
    };

    private final JavaPlugin plugin;
    private final MenuService menu;
    private final Function<Location, Tavern> nearestTavern;
    private final Predicate<Entity> isResident;

    private final NamespacedKey traderKey;
    private final NamespacedKey guestKey;
    private final NamespacedKey innkeeperKey;
    private final Map<UUID, Long> hintCooldowns = new HashMap<>();
    private BukkitTask spawnTask;

    public TraderService(JavaPlugin plugin, MenuService menu,
                         Function<Location, Tavern> nearestTavern, Predicate<Entity> isResident) {
        this.plugin = plugin;
        this.menu = menu;
        this.nearestTavern = nearestTavern;
        this.isResident = isResident;
        this.traderKey = new NamespacedKey(plugin, "halloumi_trader");
        this.guestKey = new NamespacedKey(plugin, "tavern_guest");
        this.innkeeperKey = new NamespacedKey(plugin, "innkeeper");
    }

    public NamespacedKey innkeeperKey() {
        return innkeeperKey;
    }

    public boolean isInnkeeper(Entity entity) {
        return has(entity, innkeeperKey);
    }

    public boolean isGuest(Entity entity) {
        return has(entity, guestKey);
    }

    public boolean isTavernTrader(Entity entity) {
        return isInnkeeper(entity) || isGuest(entity);
    }

    public void startSpawnTask() {
        stopSpawnTask();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("random-spawn.enabled", true)) {
            return;
        }
        long periodTicks = Math.max(1, cfg.getLong("random-spawn.check-interval-minutes", 20)) * 60L * 20L;
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> trySpawnNearRandomPlayer(false), periodTicks, periodTicks);
    }

    public void stopSpawnTask() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    /**
     * Picks a random player in an overworld-type world and spawns the trader nearby.
     *
     * @param force skip the chance roll and the max-active limit (used by {@code /halloumi random})
     * @return the player the trader came to, or null if nothing was spawned
     */
    public Player trySpawnNearRandomPlayer(boolean force) {
        FileConfiguration cfg = plugin.getConfig();
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
        FileConfiguration cfg = plugin.getConfig();
        double minDistance = cfg.getDouble("random-spawn.min-distance", 10);
        double maxDistance = Math.max(minDistance, cfg.getDouble("random-spawn.max-distance", 20));
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

    public WanderingTrader spawnTrader(Location location) {
        return spawnTrader(location, false);
    }

    public WanderingTrader spawnTrader(Location location, boolean tavernGuest) {
        FileConfiguration cfg = plugin.getConfig();
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
            trader.setRecipes(List.of(menu.createHalloumiRecipe(cfg, maxUses)));
            trader.setDespawnDelay(despawnTicks);
        });
    }

    /** Random-visit traders only: tavern guests and innkeepers don't count. */
    public int countActiveTraders() {
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

    private boolean has(Entity entity, NamespacedKey key) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Keep the offer list to halloumi / tavern menu only. */
    @EventHandler(ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        Entity entity = event.getEntity();
        if (has(entity, traderKey) || has(entity, innkeeperKey) || isResident.test(entity)) {
            event.setCancelled(true);
        }
    }

    /** A wandering halloumi trader tells roughly where the nearest tavern is. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof WanderingTrader) || !has(entity, traderKey)
                || !plugin.getConfig().getBoolean("tavern.hints", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!tryAcquireHintCooldown(hintCooldowns, player.getUniqueId(), System.currentTimeMillis())) {
            return;
        }

        String name = plugin.getConfig().getString("trader-name", "Торговец халуми");
        Tavern tavern = nearestTavern.apply(player.getLocation());
        String text = formatTavernHint(tavern, player.getLocation().getX(), player.getLocation().getZ());
        player.sendMessage(Component.text("<" + name + "> ", NamedTextColor.GOLD)
                .append(Component.text(text, NamedTextColor.YELLOW)));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hintCooldowns.remove(event.getPlayer().getUniqueId());
    }

    public static boolean tryAcquireHintCooldown(Map<UUID, Long> cooldowns, UUID playerId, long nowMs) {
        Long last = cooldowns.get(playerId);
        if (last != null && nowMs - last < HINT_COOLDOWN_MS) {
            return false;
        }
        cooldowns.put(playerId, nowMs);
        return true;
    }

    public static String formatTavernHint(Tavern nearest, double playerX, double playerZ) {
        if (nearest == null) {
            return "Говорят, где-то есть таверна, где халуми подают каждый день. Но где — не знаю.";
        }
        double dx = nearest.centerX() - playerX;
        double dz = nearest.centerZ() - playerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 64) {
            return "Таверна совсем рядом — принюхайся!";
        }
        long rounded = Math.max(100, Math.round(distance / 100.0) * 100);
        return "Ближайшая таверна " + compassDirection(dx, dz) + ", примерно в " + rounded + " блоках отсюда.";
    }

    public static String compassDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north (-z), 90 = east (+x)
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        return DIRECTIONS[index];
    }
}

