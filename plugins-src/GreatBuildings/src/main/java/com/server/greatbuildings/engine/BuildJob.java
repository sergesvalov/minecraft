package com.server.greatbuildings.engine;

import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.Terrain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LecternInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Builds one {@link Building} into the world without freezing the server:
 * loads the chunks asynchronously, reads the ground, draws the plan off the main thread, then places
 * a limited number of blocks per tick. Remembers what it replaced, so the build can be undone.
 */
public final class BuildJob {

    private final JavaPlugin plugin;
    private final Building building;
    private final World world;
    /** World position of the plan origin; oy is the ground level. */
    private final int ox, oy, oz;
    private final UUID owner;
    private final int writesPerTick;
    private final int checksPerTick;

    private Plan plan;
    private BlockData[] palette;
    private int cursor;
    private int[] undoIndex = new int[1 << 14];
    private BlockData[] undoData = new BlockData[1 << 14];
    private int undoCount;
    private final List<int[]> chunks = new ArrayList<>();
    private BukkitTask task;
    private int nextReport = 25;
    private boolean finished;

    public BuildJob(JavaPlugin plugin, Building building, World world, int ox, int oy, int oz, UUID owner) {
        this.plugin = plugin;
        this.building = building;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.owner = owner;
        this.writesPerTick = Math.max(100, plugin.getConfig().getInt("blocks-per-tick", 4000));
        this.checksPerTick = Math.max(writesPerTick, plugin.getConfig().getInt("checks-per-tick", 30000));
        int[] bounds = building.bounds();
        for (int cx = (ox + bounds[0]) >> 4; cx <= (ox + bounds[3]) >> 4; cx++) {
            for (int cz = (oz + bounds[2]) >> 4; cz <= (oz + bounds[5]) >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
    }

    public Building building() {
        return building;
    }

    public boolean finished() {
        return finished;
    }

    /** Runs the whole build; {@code done} gets null on success or an error message, on the main thread. */
    public void start(Consumer<String> done) {
        tell("Загружаю " + chunks.size() + " чанков...");
        List<CompletableFuture<Chunk>> loads = new ArrayList<>(chunks.size());
        for (int[] c : chunks) {
            loads.add(world.getChunkAtAsync(c[0], c[1], true));
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        fail(done, "не удалось загрузить чанки: " + error.getMessage());
                        return;
                    }
                    for (int[] c : chunks) {
                        world.addPluginChunkTicket(c[0], c[1], plugin);
                    }
                    Terrain terrain = readTerrain();
                    tell("Чертёж готовится...");
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Plan drawn;
                        try {
                            drawn = new Plan(building.bounds());
                            building.draw(drawn, terrain);
                        } catch (RuntimeException e) {
                            plugin.getLogger().severe("Drawing " + building.id() + " failed: " + e);
                            Bukkit.getScheduler().runTask(plugin, () -> fail(done, "ошибка в чертеже: " + e.getMessage()));
                            return;
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> place(drawn, done));
                    });
                }));
    }

    /** Ground heights over the whole plan area, relative to oy. Main thread. */
    private Terrain readTerrain() {
        int[] b = building.bounds();
        int sizeX = b[3] - b[0] + 1;
        int sizeZ = b[5] - b[2] + 1;
        int[] ground = new int[sizeX * sizeZ];
        int lowest = Math.max(world.getMinHeight(), oy + b[1] - 1);
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) {
                ground[j * sizeX + i] = groundY(ox + b[0] + i, oz + b[2] + j, lowest);
            }
        }
        return (x, z) -> {
            int i = x - b[0];
            int j = z - b[2];
            if (i < 0 || j < 0 || i >= sizeX || j >= sizeZ) {
                return Terrain.NONE;
            }
            return ground[j * sizeX + i];
        };
    }

    private int groundY(int x, int z, int lowest) {
        for (int y = world.getHighestBlockYAt(x, z); y >= lowest; y--) {
            Material type = world.getType(x, y, z);
            if (type.isSolid() && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type)) {
                return y - oy;
            }
        }
        return Terrain.NONE;
    }

    private void place(Plan drawn, Consumer<String> done) {
        plan = drawn;
        List<String> states = plan.palette();
        palette = new BlockData[states.size()];
        for (int i = 0; i < states.size(); i++) {
            palette[i] = blockData(states.get(i));
        }
        tell("Строю...");
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (step()) {
                task.cancel();
                decorate();
                releaseChunks();
                finished = true;
                done.accept(null);
            }
        }, 1L, 1L);
    }

    /** Places blocks for one tick; true when everything is placed. */
    private boolean step() {
        int writes = 0;
        int checks = 0;
        int volume = plan.volume();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        while (cursor < volume && writes < writesPerTick && checks < checksPerTick) {
            int raw = plan.rawAt(cursor);
            if (raw != 0) {
                checks++;
                int y = oy + plan.y(cursor);
                if (y >= minY && y < maxY) {
                    Block block = world.getBlockAt(ox + plan.x(cursor), y, oz + plan.z(cursor));
                    BlockData target = palette[raw - 1];
                    boolean same = target.getMaterial().isAir() ? block.getType().isAir() : block.getBlockData().equals(target);
                    if (!same) {
                        remember(cursor, block.getBlockData());
                        block.setBlockData(target, false);
                        writes++;
                    }
                }
            }
            cursor++;
        }
        int percent = (int) (100L * cursor / volume);
        if (percent >= nextReport && cursor < volume) {
            tell("Построено " + percent + "%");
            nextReport += 25;
        }
        return cursor >= volume;
    }

    private void remember(int index, BlockData previous) {
        if (undoCount == undoIndex.length) {
            undoIndex = Arrays.copyOf(undoIndex, undoCount * 2);
            undoData = Arrays.copyOf(undoData, undoCount * 2);
        }
        undoIndex[undoCount] = index;
        undoData[undoCount] = previous;
        undoCount++;
    }

    /** Sign texts and lectern books: block entities can only be filled once the blocks are in the world. */
    private void decorate() {
        for (Plan.SignText text : plan.signs) {
            if (world.getBlockAt(ox + text.x(), oy + text.y(), oz + text.z()).getState() instanceof Sign sign) {
                SignSide front = sign.getSide(Side.FRONT);
                for (int i = 0; i < Math.min(4, text.lines().size()); i++) {
                    front.line(i, Component.text(text.lines().get(i), NamedTextColor.DARK_BLUE));
                }
                sign.setWaxed(true);
                sign.update(true, false);
            }
        }
        ItemStack book = book(building.book());
        for (int[] pos : plan.lecterns) {
            if (world.getBlockAt(ox + pos[0], oy + pos[1], oz + pos[2]).getState() instanceof Lectern lectern
                    && lectern.getInventory() instanceof LecternInventory inventory) {
                inventory.setBook(book.clone());
            }
        }
    }

    static ItemStack book(Building.Book content) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.setTitle(content.title());
            meta.setAuthor(content.author());
            meta.addPages(content.pages().stream().map(page -> (Component) Component.text(page)).toArray(Component[]::new));
        });
        return book;
    }

    /** Puts back everything this build replaced, newest first. {@code done} runs on the main thread. */
    public void undo(Runnable done) {
        tell("Разбираю " + building.title() + "...");
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int i = undoCount - 1;

            @Override
            public void run() {
                int writes = 0;
                while (i >= 0 && writes < writesPerTick) {
                    int index = undoIndex[i];
                    world.getBlockAt(ox + plan.x(index), oy + plan.y(index), oz + plan.z(index))
                            .setBlockData(undoData[i], false);
                    undoData[i] = null;
                    i--;
                    writes++;
                }
                if (i < 0) {
                    task.cancel();
                    undoCount = 0;
                    finished = true;
                    done.run();
                }
            }
        }, 1L, 1L);
    }

    public int changedBlocks() {
        return undoCount;
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
        }
        releaseChunks();
        finished = true;
    }

    private void releaseChunks() {
        for (int[] c : chunks) {
            world.removePluginChunkTicket(c[0], c[1], plugin);
        }
    }

    private void fail(Consumer<String> done, String message) {
        releaseChunks();
        finished = true;
        done.accept(message);
    }

    /**
     * BlockData for a state string. Falls back for blocks renamed between versions (iron_chain was chain before
     * 1.21.9), and to stone if nothing works, so a single unknown block never stops a build.
     */
    private BlockData blockData(String state) {
        try {
            return Bukkit.createBlockData(state);
        } catch (IllegalArgumentException e) {
            String fallback = state.replace("minecraft:iron_chain", "minecraft:chain");
            if (!fallback.equals(state)) {
                try {
                    return Bukkit.createBlockData(fallback);
                } catch (IllegalArgumentException ignored) {
                    // fall through to stone
                }
            }
            plugin.getLogger().warning("Unknown block state in " + building.id() + ": " + state + " — using stone");
            return Material.STONE.createBlockData();
        }
    }

    private void tell(String message) {
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            player.sendActionBar(Component.text(building.title() + ": " + message, NamedTextColor.GOLD));
        }
        plugin.getLogger().info(building.id() + ": " + message);
    }
}

