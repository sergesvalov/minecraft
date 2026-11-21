package com.server.halloumi.tavern;

import com.server.halloumi.model.Tavern;
import com.server.halloumi.model.TavernPalette;
import com.server.halloumi.npc.Seats;
import com.server.halloumi.npc.Residents;
import com.server.halloumi.trade.MenuService;
import com.server.halloumi.trade.TraderService;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates tavern lifecycle: builds world-gen pending taverns once surrounding chunks load,
 * demolishes pre-1.4 structures, keeps innkeepers/residents/guests alive, and builds command taverns.
 */
public final class TavernManager {

    private final JavaPlugin plugin;
    private final TavernStorage storage;
    private final MenuService menu;
    private final TraderService traders;
    private final Seats seats;
    private final Residents residents;

    private final List<Tavern> taverns = new ArrayList<>();
    /** Filled from world generation threads, drained on the main thread. */
    private final Queue<Tavern> placed = new ConcurrentLinkedQueue<>();
    private boolean dirty;

    public TavernManager(JavaPlugin plugin, TavernStorage storage, MenuService menu,
                         TraderService traders, Seats seats, Residents residents) {
        this.plugin = plugin;
        this.storage = storage;
        this.menu = menu;
        this.traders = traders;
        this.seats = seats;
        this.residents = residents;
        this.taverns.addAll(storage.load());
    }

    /** Thread-safe: called by the block populator. */
    public void onPlaced(Tavern tavern) {
        placed.add(tavern);
    }

    public void add(Tavern tavern) {
        taverns.add(tavern);
        dirty = true;
    }

    /** Built taverns and pending sites (old ones being torn down are excluded). */
    public List<Tavern> all() {
        drainPlaced();
        return taverns.stream().filter(t -> !t.state().old()).toList();
    }

    /** Nearest known tavern in the same world, or null. */
    public Tavern nearest(Location location) {
        return all().stream()
                .filter(t -> t.world().equals(location.getWorld().getName()))
                .min(Comparator.comparingDouble(t -> distanceSq(t, location)))
                .orElse(null);
    }

    public static double distanceSq(Tavern t, Location location) {
        double dx = t.centerX() - location.getX();
        double dz = t.centerZ() - location.getZ();
        return dx * dx + dz * dz;
    }

    /** Builds a tavern with its yard gate 3 blocks north of the player and door aligned with player X. */
    public void buildTavernAt(Player player, TavernPalette forced) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int ox = loc.getBlockX() - TavernLayout.DOOR_X;
        int oz = loc.getBlockZ() - 3 - TavernLayout.YARD_END_Z;
        int oy = loc.getBlockY();
        if (oy + TavernLayout.HEIGHT >= world.getMaxHeight()) {
            player.sendMessage(Component.text("Слишком высоко для таверны.", NamedTextColor.RED));
            return;
        }
        TavernPalette palette = forced != null ? forced
                : TavernPalette.forBiome(world.getBiome(loc).getKey().getKey());
        if (palette == null) {
            palette = TavernPalette.OAK;
        }

        TavernBuilder.build(BlockGrid.ofWorld(world), ox, oy, oz, palette);
        add(new Tavern(world.getName(), ox, oy, oz, false, Tavern.State.BUILT));
        tick();
        player.sendMessage(Component.text("Таверна построена (" + palette.name().toLowerCase(Locale.ROOT) + ") на "
                + ox + " " + oy + " " + oz + ".", NamedTextColor.GREEN));
    }

    /** Runs on the main thread every 10 seconds. */
    public void tick() {
        drainPlaced();
        for (int i = 0; i < taverns.size(); i++) {
            Tavern tavern = taverns.get(i);
            World world = Bukkit.getWorld(tavern.world());
            if (world == null) {
                continue;
            }
            if (tavern.state().old()) {
                if (areaLoaded(world, OldTaverns.area(tavern), true)) {
                    OldTaverns.demolish(world, tavern, traders::isTavernTrader);
                    plugin.getLogger().info("Old tavern torn down at " + where(tavern));
                    taverns.remove(i--);
                    dirty = true;
                }
                continue;
            }
            if (tavern.state() == Tavern.State.PENDING) {
                if (!areaLoaded(world, TavernLayout.area(tavern.x(), tavern.z()), false)) {
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

    private Tavern tryBuild(World world, Tavern tavern) {
        BlockGrid blocks = BlockGrid.ofWorld(world);
        int oy = TavernSite.siteFloorY(blocks, tavern.x(), tavern.z(), world.getMinHeight(), world.getMaxHeight());
        if (oy == TavernLayout.NO_SPOT) {
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
                tavern.x() + TavernLayout.SIZE_X + 1, tavern.y() + TavernLayout.DECK + 1,
                tavern.z() + TavernLayout.SIZE_Z + 1);
        WanderingTrader innkeeper = null;
        int guests = 0;
        for (Entity entity : world.getNearbyEntities(box, e -> e instanceof WanderingTrader)) {
            if (traders.isInnkeeper(entity)) {
                innkeeper = (WanderingTrader) entity;
            } else if (traders.isGuest(entity)) {
                guests++;
            }
        }

        if (innkeeper == null) {
            innkeeper = spawnInnkeeper(world, tavern);
        } else {
            var recipes = innkeeper.getRecipes();
            boolean soldOut = recipes.stream().anyMatch(r -> r.getUses() >= r.getMaxUses());
            boolean menuChanged = recipes.size() != menu.menuSize();
            if (soldOut || menuChanged) {
                innkeeper.setRecipes(menu.innkeeperRecipes(plugin.getConfig()));
            }
            if (menuChanged) {
                writeMenuBook(world, tavern);
            }
        }

        BoundingBox house = new BoundingBox(tavern.x() - 8, tavern.y() - 2, tavern.z() - 8,
                tavern.x() + TavernLayout.SIZE_X + 8, tavern.y() + TavernLayout.CEILING, tavern.z() + TavernLayout.SIZE_Z + 8);
        Villager resident = null;
        for (Entity entity : world.getNearbyEntities(house, e -> e instanceof Villager && residents.isResident(e))) {
            resident = (Villager) entity;
        }
        if (resident == null) {
            resident = residents.spawn(world, tavern);
        }
        residents.track(resident, tavern, innkeeper);

        int maxGuests = plugin.getConfig().getInt("tavern.guests.max", 2);
        double chance = plugin.getConfig().getDouble("tavern.guests.chance", 0.04);
        if (guests < maxGuests && ThreadLocalRandom.current().nextDouble() < chance && playerNearby(world, tavern)) {
            TavernLayout.Spot spot = TavernLayout.GUEST_SPOTS.get(
                    ThreadLocalRandom.current().nextInt(TavernLayout.GUEST_SPOTS.size()));
            traders.spawnTrader(new Location(world, tavern.x() + spot.x(), tavern.y() + 1, tavern.z() + spot.z()), true);
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

    private WanderingTrader spawnInnkeeper(World world, Tavern tavern) {
        Location location = new Location(world, tavern.x() + TavernLayout.TRADER_X, tavern.y() + 1,
                tavern.z() + TavernLayout.TRADER_Z, 0f, 0f);
        String name = plugin.getConfig().getString("tavern.innkeeper-name", "Трактирщик");
        return world.spawn(location, WanderingTrader.class, trader -> {
            trader.customName(Component.text(name, NamedTextColor.GOLD));
            trader.setCustomNameVisible(true);
            trader.getPersistentDataContainer().set(traders.innkeeperKey(), PersistentDataType.BYTE, (byte) 1);
            trader.setRecipes(menu.innkeeperRecipes(plugin.getConfig()));
            trader.setDespawnDelay(0);
            trader.setAI(false);
            trader.setInvulnerable(true);
            trader.setPersistent(true);
            trader.setRemoveWhenFarAway(false);
        });
    }

    private void decorate(World world, Tavern tavern) {
        int sx = tavern.x() + TavernLayout.SIGN_X;
        int sy = tavern.y() + TavernLayout.SIGN_Y;
        int sz = tavern.z() + TavernLayout.SIGN_Z;
        writeSign(world.getBlockAt(sx, sy, sz), plugin.getConfig().getStringList("tavern.sign"));
        writeSign(world.getBlockAt(sx + 1, sy, sz), plugin.getConfig().getStringList("tavern.sign-right"));
        writeMenuBook(world, tavern);

        int[] area = TavernLayout.area(tavern.x(), tavern.z());
        seats.removeCushions(world, new BoundingBox(area[0], tavern.y(), area[1],
                area[2] + 1, tavern.y() + TavernLayout.CEILING, area[3] + 1));
        for (TavernLayout.Chair chair : TavernLayout.CHAIRS) {
            seats.placeCushion(world, tavern.x() + chair.x(), tavern.y() + chair.y(), tavern.z() + chair.z(),
                    Material.RED_CARPET);
        }
    }

    private void writeMenuBook(World world, Tavern tavern) {
        Block lecternBlock = world.getBlockAt(tavern.x() + TavernLayout.LECTERN_X,
                tavern.y() + TavernLayout.LECTERN_Y, tavern.z() + TavernLayout.LECTERN_Z);
        if (lecternBlock.getState() instanceof Lectern lectern
                && lectern.getInventory() instanceof LecternInventory inventory) {
            inventory.setBook(menu.createMenuBook(plugin.getConfig()));
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

    public void save() {
        drainPlaced();
        if (storage.save(taverns)) {
            dirty = false;
        }
    }
}

