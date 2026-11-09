package com.server.halloumi;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
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
 * Keeps the list of taverns (taverns.txt). Builds taverns whose spot was picked during world generation once the
 * chunks around them are loaded, tears down old (before 1.4) ones, and for built taverns in loaded chunks makes
 * sure the innkeeper stands behind the counter, writes the signs, puts cushions on the chairs and lets wandering
 * halloumi traders drop in as guests.
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

    /** Built taverns and the ones waiting to be built (old ones being torn down don't count). */
    List<Tavern> all() {
        drainPlaced();
        return taverns.stream().filter(t -> !t.state().old()).toList();
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
            if (world == null) {
                continue;
            }
            if (tavern.state().old()) {
                if (areaLoaded(world, OldTaverns.area(tavern), true)) {
                    OldTaverns.demolish(world, tavern, this::ours);
                    plugin.getLogger().info("Old tavern torn down at " + where(tavern));
                    taverns.remove(i--);
                    dirty = true;
                }
                continue;
            }
            if (tavern.state() == Tavern.State.PENDING) {
                if (!areaLoaded(world, TavernBuilder.area(tavern.x(), tavern.z()), false)) {
                    continue;
                }
                tavern = tryBuild(world, tavern);
                dirty = true;
                if (tavern == null) {
                    taverns.remove(i--);
                    continue;
                }
                taverns.set(i, tavern);
            }
            if (!world.isChunkLoaded(tavern.chunkX(), tavern.chunkZ())
                    || !world.getChunkAt(tavern.chunkX(), tavern.chunkZ()).isEntitiesLoaded()) {
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

    /** True if every chunk of the area is loaded (and, if asked, its entities too). Never loads chunks itself. */
    private static boolean areaLoaded(World world, int[] area, boolean entities) {
        for (int cx = area[0] >> 4; cx <= area[2] >> 4; cx++) {
            for (int cz = area[1] >> 4; cz <= area[3] >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz) || (entities && !world.getChunkAt(cx, cz).isEntitiesLoaded())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean ours(Entity entity) {
        var pdc = entity.getPersistentDataContainer();
        return pdc.has(plugin.innkeeperKey(), PersistentDataType.BYTE) || pdc.has(plugin.guestKey(), PersistentDataType.BYTE);
    }

    /**
     * Builds a tavern whose spot was picked during world generation. Building in the live world means the
     * neighbouring chunks are fully generated and their trees can't grow through the house later.
     * Returns the built tavern, or null if the site doesn't fit after all.
     */
    private Tavern tryBuild(World world, Tavern tavern) {
        TavernBuilder.Blocks blocks = TavernBuilder.worldBlocks(world);
        int oy = TavernBuilder.siteFloorY(blocks, tavern.x(), tavern.z(), world.getMinHeight(), world.getMaxHeight());
        if (oy == TavernBuilder.NO_SPOT) {
            plugin.getLogger().info("No room for the tavern planned at " + where(tavern));
            return null;
        }
        TavernPalette palette = TavernPalette.forBiome(
                world.getBiome(tavern.x() + 5, oy, tavern.z() + 4).getKey().getKey());
        TavernBuilder.build(blocks, tavern.x(), oy, tavern.z(), palette != null ? palette : TavernPalette.OAK);
        Tavern built = tavern.built(oy);
        plugin.getLogger().info("Tavern built at " + where(built));
        return built;
    }

    private static String where(Tavern t) {
        return t.world() + " " + t.x() + " " + t.y() + " " + t.z();
    }

    private void tickTavern(World world, Tavern tavern) {
        BoundingBox box = new BoundingBox(tavern.x() - 1, tavern.y(), tavern.z() - 1,
                tavern.x() + TavernBuilder.SIZE_X + 1, tavern.y() + TavernBuilder.DECK + 1,
                tavern.z() + TavernBuilder.SIZE_Z + 1);
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
            innkeeper = spawnInnkeeper(world, tavern);
        } else {
            // Restock when sold out; also picks up menu changes from config.yml
            var recipes = innkeeper.getRecipes();
            boolean soldOut = recipes.stream().anyMatch(r -> r.getUses() >= r.getMaxUses());
            boolean menuChanged = recipes.size() != plugin.menuSize();
            if (soldOut || menuChanged) {
                innkeeper.setRecipes(plugin.innkeeperRecipes());
            }
            if (menuChanged) {
                writeMenuBook(world, tavern);
            }
        }

        // The resident lives on both floors; he can't leave, but look a bit wider in case a command moved him
        BoundingBox house = new BoundingBox(tavern.x() - 8, tavern.y() - 2, tavern.z() - 8,
                tavern.x() + TavernBuilder.SIZE_X + 8, tavern.y() + TavernBuilder.CEILING, tavern.z() + TavernBuilder.SIZE_Z + 8);
        Villager resident = null;
        for (Entity entity : world.getNearbyEntities(house, e -> e instanceof Villager && plugin.residents().isResident(e))) {
            resident = (Villager) entity;
        }
        if (resident == null) {
            resident = plugin.residents().spawn(world, tavern);
        }
        plugin.residents().track(resident, tavern, innkeeper);

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

    /**
     * One-time: the two signs above the door, the menu book on the lectern and cushions on every chair.
     * Skips blocks players already broke.
     */
    private void decorate(World world, Tavern tavern) {
        int sx = tavern.x() + TavernBuilder.SIGN_X;
        int sy = tavern.y() + TavernBuilder.SIGN_Y;
        int sz = tavern.z() + TavernBuilder.SIGN_Z;
        writeSign(world.getBlockAt(sx, sy, sz), plugin.getConfig().getStringList("tavern.sign"));
        writeSign(world.getBlockAt(sx + 1, sy, sz), plugin.getConfig().getStringList("tavern.sign-right"));
        writeMenuBook(world, tavern);

        int[] area = TavernBuilder.area(tavern.x(), tavern.z());
        plugin.seats().removeCushions(world, new BoundingBox(area[0], tavern.y(), area[1],
                area[2] + 1, tavern.y() + TavernBuilder.CEILING, area[3] + 1));
        for (TavernBuilder.Chair chair : TavernBuilder.CHAIRS) {
            plugin.seats().placeCushion(world, tavern.x() + chair.x(), tavern.y() + chair.y(), tavern.z() + chair.z(),
                    Material.RED_CARPET);
        }
    }

    /** Also called when the menu changed (new dishes in config.yml), so the book matches the innkeeper. */
    private void writeMenuBook(World world, Tavern tavern) {
        Block lecternBlock = world.getBlockAt(tavern.x() + TavernBuilder.LECTERN_X, tavern.y() + TavernBuilder.LECTERN_Y,
                tavern.z() + TavernBuilder.LECTERN_Z);
        if (lecternBlock.getState() instanceof Lectern lectern
                && lectern.getInventory() instanceof LecternInventory inventory) {
            inventory.setBook(plugin.createMenuBook());
        }
    }

    private static void writeSign(Block block, List<String> lines) {
        if (block.getState() instanceof Sign sign) {
            SignSide front = sign.getSide(Side.FRONT);
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                front.line(i, Component.text(lines.get(i), NamedTextColor.DARK_RED));
            }
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    private void drainPlaced() {
        Tavern tavern;
        while ((tavern = placed.poll()) != null) {
            taverns.add(tavern);
            dirty = true;
            plugin.getLogger().info("Tavern planned at " + where(tavern) + ", built once the chunks around are loaded");
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
        lines.add("# world,x,y,z,decorated,state (inn = built, inn-pending = not built yet, none/east/west = old one"
                + " to tear down) — generated by HalloumiTrader, one tavern per line");
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
