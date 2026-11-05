package com.server.halloumi;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.inventory.LecternInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the list of taverns (taverns.txt) and, for taverns in loaded chunks, makes sure the innkeeper
 * stands behind the counter, writes the sign and lets wandering halloumi traders drop in as guests.
 */
final class TavernManager {

    private final HalloumiTraderPlugin plugin;
    private final File file;
    private final List<Tavern> taverns = new ArrayList<>();
    /** Filled from world generation threads, drained on the main thread. */
    private final Queue<Tavern> placed = new ConcurrentLinkedQueue<>();
    private boolean dirty;

    TavernManager(HalloumiTraderPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "taverns.txt");
        load();
    }

    /** Thread-safe: called by the populator. */
    void onPlaced(Tavern tavern) {
        placed.add(tavern);
    }

    void add(Tavern tavern) {
        taverns.add(tavern);
        dirty = true;
    }

    List<Tavern> all() {
        drainPlaced();
        return List.copyOf(taverns);
    }

    /** Nearest known tavern in the same world, or null. */
    Tavern nearest(Location location) {
        return all().stream()
                .filter(t -> t.world().equals(location.getWorld().getName()))
                .min(Comparator.comparingDouble(t -> distanceSq(t, location)))
                .orElse(null);
    }

    static double distanceSq(Tavern t, Location location) {
        double dx = t.centerX() - location.getX();
        double dz = t.centerZ() - location.getZ();
        return dx * dx + dz * dz;
    }

    /** Runs on the main thread every few seconds. */
    void tick() {
        drainPlaced();
        for (int i = 0; i < taverns.size(); i++) {
            Tavern tavern = taverns.get(i);
            World world = Bukkit.getWorld(tavern.world());
            if (world == null || !world.isChunkLoaded(tavern.chunkX(), tavern.chunkZ())) {
                continue;
            }
            if (!world.getChunkAt(tavern.chunkX(), tavern.chunkZ()).isEntitiesLoaded()) {
                continue;
            }
            if (!tavern.decorated()) {
                decorate(world, tavern);
                tavern = tavern.withDecorated();
                taverns.set(i, tavern);
                dirty = true;
            }
            tickTavern(world, tavern);
        }
        if (dirty) {
            save();
        }
    }

    private void tickTavern(World world, Tavern tavern) {
        BoundingBox box = new BoundingBox(tavern.x() - 1, tavern.y(), tavern.z() - 1,
                tavern.x() + TavernBuilder.SIZE_X + 1, tavern.y() + 6, tavern.z() + TavernBuilder.SIZE_Z + 1);
        WanderingTrader innkeeper = null;
        int guests = 0;
        for (Entity entity : world.getNearbyEntities(box, e -> e instanceof WanderingTrader)) {
            var pdc = entity.getPersistentDataContainer();
            if (pdc.has(plugin.innkeeperKey(), PersistentDataType.BYTE)) {
                innkeeper = (WanderingTrader) entity;
            } else if (pdc.has(plugin.guestKey(), PersistentDataType.BYTE)) {
                guests++;
            }
        }

        if (innkeeper == null) {
            spawnInnkeeper(world, tavern);
        } else {
            // Restock when sold out; also picks up menu changes from config.yml
            var recipes = innkeeper.getRecipes();
            boolean soldOut = recipes.stream().anyMatch(r -> r.getUses() >= r.getMaxUses());
            if (soldOut || recipes.size() != plugin.menuSize()) {
                innkeeper.setRecipes(plugin.innkeeperRecipes());
            }
        }

        int maxGuests = plugin.getConfig().getInt("tavern.guests.max", 2);
        double chance = plugin.getConfig().getDouble("tavern.guests.chance", 0.04);
        if (guests < maxGuests && ThreadLocalRandom.current().nextDouble() < chance && playerNearby(world, tavern)) {
            double[] spot = TavernBuilder.GUEST_SPOTS[ThreadLocalRandom.current().nextInt(TavernBuilder.GUEST_SPOTS.length)];
            plugin.spawnTrader(new Location(world, tavern.x() + spot[0], tavern.y() + 1, tavern.z() + spot[1]), true);
        }
    }

    private boolean playerNearby(World world, Tavern tavern) {
        double radius = plugin.getConfig().getDouble("tavern.guests.player-radius", 48);
        for (Player player : world.getPlayers()) {
            if (distanceSq(tavern, player.getLocation()) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    WanderingTrader spawnInnkeeper(World world, Tavern tavern) {
        Location location = new Location(world, tavern.x() + TavernBuilder.TRADER_X, tavern.y() + 1,
                tavern.z() + TavernBuilder.TRADER_Z, 0f, 0f); // yaw 0 = facing south, towards the guests
        String name = plugin.getConfig().getString("tavern.innkeeper-name", "Трактирщик");
        return world.spawn(location, WanderingTrader.class, trader -> {
            trader.customName(Component.text(name, NamedTextColor.GOLD));
            trader.setCustomNameVisible(true);
            trader.getPersistentDataContainer().set(plugin.innkeeperKey(), PersistentDataType.BYTE, (byte) 1);
            trader.setRecipes(plugin.innkeeperRecipes());
            trader.setDespawnDelay(0); // 0 = never leaves
            trader.setAI(false);       // stays behind the counter
            trader.setInvulnerable(true);
            trader.setPersistent(true);
            trader.setRemoveWhenFarAway(false);
        });
    }

    /** One-time: sign text above the door and the menu book on the lectern. Skips blocks players already broke. */
    private void decorate(World world, Tavern tavern) {
        Block signBlock = world.getBlockAt(tavern.x() + TavernBuilder.SIGN_X, tavern.y() + TavernBuilder.SIGN_Y,
                tavern.z() + TavernBuilder.SIGN_Z);
        if (signBlock.getState() instanceof Sign sign) {
            List<String> lines = plugin.getConfig().getStringList("tavern.sign");
            SignSide front = sign.getSide(Side.FRONT);
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                front.line(i, Component.text(lines.get(i), NamedTextColor.DARK_RED));
            }
            sign.setWaxed(true);
            sign.update(true, false);
        }

        Block lecternBlock = world.getBlockAt(tavern.x() + TavernBuilder.LECTERN_X, tavern.y() + TavernBuilder.LECTERN_Y,
                tavern.z() + TavernBuilder.LECTERN_Z);
        if (lecternBlock.getState() instanceof Lectern lectern
                && lectern.getInventory() instanceof LecternInventory inventory) {
            inventory.setBook(plugin.createMenuBook());
        }
    }

    private void drainPlaced() {
        Tavern tavern;
        while ((tavern = placed.poll()) != null) {
            taverns.add(tavern);
            dirty = true;
            plugin.getLogger().info("Tavern generated at " + tavern.world() + " " + tavern.x() + " " + tavern.y() + " " + tavern.z());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    taverns.add(Tavern.parse(line.trim()));
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Failed to read taverns.txt: " + e.getMessage());
        }
    }

    void save() {
        drainPlaced();
        List<String> lines = new ArrayList<>();
        lines.add("# world,x,y,z,signWritten — generated by HalloumiTrader, one tavern per line");
        taverns.forEach(t -> lines.add(t.serialize()));
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write taverns.txt: " + e.getMessage());
        }
    }
}
