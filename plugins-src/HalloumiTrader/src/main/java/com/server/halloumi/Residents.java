package com.server.halloumi;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;

/**
 * The resident of a tavern: a villager who lives there and never leaves. By day he buys a dish from the innkeeper,
 * sits down at a free chair and eats it, and strolls around the hall in between; at night he goes upstairs to his
 * room (the north-west one) and sleeps in his bed until morning.
 *
 * <p>His vanilla AI is off, so he can't wander out, take a job or be pushed around: the plugin walks him along fixed
 * routes inside the tavern (the aisle from the door to the counter, the free rows between the tables, the stairs,
 * the corridor) by moving him a little every tick, which clients show as walking. Routes are in coordinates relative
 * to the tavern origin, see {@link TavernBuilder}; y is the feet level (1 on the ground floor, 8 upstairs).
 */
final class Residents implements Listener {

    /** Walking speed, blocks per tick (a villager walks at about 0.1–0.15). */
    private static final double SPEED = 0.11;
    private static final double AISLE_X = 15.0;
    /** Where a new resident appears, and where a lost one is put back. */
    private static final Vector HOME = new Vector(AISLE_X, 1, 12.5);
    /** Facing the innkeeper across the counter. */
    private static final Vector COUNTER = new Vector(AISLE_X, 1, 5.6);
    private static final Vector STAIRS_BOTTOM = new Vector(2.0, 1, 18.5);
    /** His room: the north-west one upstairs, door leaves at x 11 and 12, bed head at x 9, z 1. */
    private static final int ROOM_DOOR_X = 11, ROOM_DOOR_Z = 8, BED_X = 9, BED_Z = 1;
    private static final Vector IN_ROOM = new Vector(10.5, TavernBuilder.DECK + 1, 3.5);
    /** Free spots around the hall to stroll to, all on the free rows between the tables. */
    private static final Vector[] STROLL = {
            new Vector(6.5, 1, 7.5), new Vector(24.5, 1, 7.5), new Vector(8.5, 1, 12.5), new Vector(21.5, 1, 12.5),
            new Vector(10.5, 1, 17.5), new Vector(20.5, 1, 17.5), new Vector(AISLE_X, 1, 20.0)};

    private final HalloumiTraderPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Resident> active = new HashMap<>();
    private BukkitTask task;

    Residents(HalloumiTraderPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "resident");
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Stops all residents: stands them up, puts them back home (or leaves them asleep). */
    void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Resident r : active.values()) {
            r.villager.getEquipment().setItemInMainHand(null);
            if (r.villager.isInsideVehicle()) {
                r.villager.leaveVehicle();
            }
        }
        active.clear();
    }

    boolean isResident(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    Villager spawn(World world, Tavern tavern) {
        String name = plugin.getConfig().getString("tavern.resident-name", "Постоялец");
        Villager.Type type = villagerType(TavernPalette.byWall(world.getType(tavern.x() + 1, tavern.y() + 2, tavern.z())));
        return world.spawn(abs(world, tavern, HOME), Villager.class, v -> {
            v.customName(Component.text(name, NamedTextColor.YELLOW));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            v.setVillagerType(type);
            v.setProfession(Villager.Profession.NONE);
            v.setAI(false);           // the plugin walks him; he can't wander off or take a job
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.getEquipment().setItemInMainHandDropChance(0f);
        });
    }

    /** Called every 10 seconds for each loaded tavern with its resident: starts driving him if not yet. */
    void track(Villager villager, Tavern tavern, WanderingTrader innkeeper) {
        Resident r = active.get(villager.getUniqueId());
        if (r == null) {
            r = new Resident(villager, tavern);
            active.put(villager.getUniqueId(), r);
            r.reset();
        }
        r.innkeeper = innkeeper;
    }

    private void tick() {
        for (Iterator<Resident> it = active.values().iterator(); it.hasNext(); ) {
            Resident r = it.next();
            if (!r.villager.isValid()) {
                it.remove(); // unloaded or gone; picked up again by track() when back
                continue;
            }
            r.tick();
        }
    }

    /** Residents and innkeepers never turn into witches (lightning) or anything else. */
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (isResident(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private static Location abs(World world, Tavern t, Vector rel) {
        return new Location(world, t.x() + rel.getX(), t.y() + rel.getY(), t.z() + rel.getZ());
    }

    private static Villager.Type villagerType(TavernPalette palette) {
        if (palette == null) {
            return Villager.Type.PLAINS;
        }
        return switch (palette) {
            case SPRUCE -> Villager.Type.TAIGA;
            case ACACIA -> Villager.Type.SAVANNA;
            case SANDSTONE -> Villager.Type.DESERT;
            case JUNGLE -> Villager.Type.JUNGLE;
            case DARK_OAK -> Villager.Type.SWAMP;
            default -> Villager.Type.PLAINS;
        };
    }

    private static boolean night(World world) {
        long time = world.getTime();
        return time >= 12600 && time < 23300;
    }

    /** One resident and what he is doing. */
    private final class Resident {

        private record Step(Vector to, Runnable onReach) {
        }

        final Villager villager;
        final Tavern tavern;
        final World world;
        WanderingTrader innkeeper;

        private final Deque<Step> path = new ArrayDeque<>();
        /** Where he stands, relative; null while in bed. */
        private Vector here = HOME.clone();
        private boolean asleep;
        private Block chair;
        private Vector beforeChair;
        private ItemStack dish;

        /** Current activity: runs {@code during} every tick for {@code left} ticks, then {@code then}. */
        private int left;
        private int elapsed;
        private IntConsumer during;
        private Runnable then;

        Resident(Villager villager, Tavern tavern) {
            this.villager = villager;
            this.tavern = tavern;
            this.world = villager.getWorld();
        }

        /** Start from a known state: asleep in bed at night, otherwise standing in the hall. */
        void reset() {
            path.clear();
            during = null;
            then = null;
            left = 0;
            chair = null;
            villager.getEquipment().setItemInMainHand(null);
            if (villager.isInsideVehicle()) {
                villager.leaveVehicle();
            }
            if (villager.isSleeping() && night(world)) {
                asleep = true;
                here = null;
                return;
            }
            if (villager.isSleeping()) {
                villager.wakeup();
            }
            asleep = false;
            here = HOME.clone();
            villager.teleport(abs(world, tavern, here));
        }

        void tick() {
            if (!path.isEmpty()) {
                walk();
                return;
            }
            if (left > 0) {
                if (during != null) {
                    during.accept(elapsed);
                }
                elapsed++;
                left--;
                return;
            }
            if (then != null) {
                Runnable next = then;
                then = null;
                next.run();
                return;
            }
            decide();
        }

        private void doFor(int ticks, IntConsumer during, Runnable then) {
            this.left = ticks;
            this.elapsed = 0;
            this.during = during;
            this.then = then;
        }

        private void decide() {
            // Lost (a piston, a command)? Back to the hall. He never leaves the tavern.
            if (!asleep && chair == null && villager.getLocation().distanceSquared(abs(world, tavern, here)) > 4) {
                reset();
            }
            boolean night = night(world);
            if (asleep) {
                if (night) {
                    doFor(100, null, null);
                } else {
                    wakeUp();
                }
            } else if (night) {
                goToBed();
            } else if (ThreadLocalRandom.current().nextDouble() < 0.55) {
                buyAndEat();
            } else {
                Vector spot = STROLL[ThreadLocalRandom.current().nextInt(STROLL.length)];
                walkOnGround(spot);
                then = () -> doFor(100 + ThreadLocalRandom.current().nextInt(200), t -> {
                    if (t % 60 == 30) {
                        look(ThreadLocalRandom.current().nextFloat() * 360f);
                    }
                }, null);
            }
        }

        // ------------------------------------------------------------------ walking

        private void walk() {
            Step step = path.peek();
            Location at = villager.getLocation();
            Location to = abs(world, tavern, step.to());
            Vector delta = to.toVector().subtract(at.toVector());
            double dist = delta.length();
            float yaw = at.getYaw();
            Vector flat = new Vector(delta.getX(), 0, delta.getZ());
            if (flat.lengthSquared() > 1.0E-4) {
                yaw = (float) Math.toDegrees(Math.atan2(-flat.getX(), flat.getZ()));
            }
            if (dist <= SPEED) {
                to.setYaw(yaw);
                villager.teleport(to);
                here = step.to().clone();
                path.poll();
                if (step.onReach() != null) {
                    step.onReach().run();
                }
            } else {
                Location next = at.clone().add(delta.multiply(SPEED / dist));
                next.setYaw(yaw);
                next.setPitch(0f);
                villager.teleport(next);
            }
        }

        private void go(Vector to) {
            path.add(new Step(to, null));
        }

        private void go(Vector to, Runnable onReach) {
            path.add(new Step(to, onReach));
        }

        /** Along the free row he stands on to the aisle, along the aisle, then along the target's row. */
        private void walkOnGround(Vector target) {
            Vector from = path.isEmpty() ? here : path.peekLast().to();
            if (from.getX() != AISLE_X) {
                go(new Vector(AISLE_X, 1, from.getZ()));
            }
            if (from.getZ() != target.getZ() || from.getX() != AISLE_X) {
                go(new Vector(AISLE_X, 1, target.getZ()));
            }
            go(target.clone());
        }

        private void look(float yaw) {
            Location l = villager.getLocation();
            l.setYaw(yaw);
            l.setPitch(0f);
            villager.teleport(l);
        }

        // ------------------------------------------------------------------ buying and eating

        private void buyAndEat() {
            List<MerchantRecipe> recipes = plugin.innkeeperRecipes();
            dish = recipes.get(ThreadLocalRandom.current().nextInt(recipes.size())).getResult();
            dish.setAmount(1);
            walkOnGround(COUNTER);
            then = this::buy;
        }

        /** At the counter: hands over an emerald, the innkeeper serves the dish. */
        private void buy() {
            look(180f); // north, at the innkeeper
            villager.getEquipment().setItemInMainHand(new ItemStack(Material.EMERALD));
            world.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
            doFor(60, t -> {
                WanderingTrader keeper = innkeeper != null && innkeeper.isValid() ? innkeeper : null;
                if (t == 20) {
                    villager.getEquipment().setItemInMainHand(null);
                    if (keeper != null) {
                        keeper.getEquipment().setItemInMainHandDropChance(0f);
                        keeper.getEquipment().setItemInMainHand(dish.clone());
                        world.playSound(keeper.getLocation(), Sound.ENTITY_WANDERING_TRADER_TRADE, 1f, 1f);
                    }
                } else if (t == 45) {
                    villager.getEquipment().setItemInMainHand(dish.clone());
                    if (keeper != null) {
                        keeper.getEquipment().setItemInMainHand(null);
                        world.playSound(keeper.getLocation(), Sound.ENTITY_WANDERING_TRADER_YES, 1f, 1f);
                        world.spawnParticle(Particle.HAPPY_VILLAGER, keeper.getEyeLocation().add(0, 0.3, 0),
                                6, 0.3, 0.2, 0.3);
                    }
                }
            }, this::goSitDown);
        }

        private void goSitDown() {
            List<TavernBuilder.Chair> chairs = TavernBuilder.CHAIRS.stream()
                    .filter(c -> c.y() == 1 && (c.z() == 8 || c.z() == 10 || c.z() == 14 || c.z() == 16))
                    .filter(c -> !plugin.seats().occupied(chairBlock(c)))
                    .toList();
            if (chairs.isEmpty()) {
                eat(); // every chair taken: eat standing at the counter
                return;
            }
            TavernBuilder.Chair c = chairs.get(ThreadLocalRandom.current().nextInt(chairs.size()));
            // The free row next to the chair: north of the north rows, south of the south rows
            double row = switch (c.z()) {
                case 8 -> 7.5;
                case 10 -> 11.5;
                case 14 -> 13.5;
                default -> 17.5;
            };
            Vector front = new Vector(c.x() + 0.5, 1, row);
            walkOnGround(front);
            then = () -> {
                Block block = chairBlock(c);
                if (plugin.seats().sit(villager, block)) {
                    chair = block;
                    beforeChair = front;
                }
                eat();
            };
        }

        private Block chairBlock(TavernBuilder.Chair c) {
            return world.getBlockAt(tavern.x() + c.x(), tavern.y() + c.y(), tavern.z() + c.z());
        }

        /** A few bites with sounds and crumbs, a happy sigh, then a rest before getting up. */
        private void eat() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            doFor(120, t -> {
                if (t % 4 == 0 && (t / 4) % 8 < 5) {
                    Location mouth = villager.getEyeLocation().add(villager.getLocation().getDirection().multiply(0.35))
                            .subtract(0, 0.2, 0);
                    world.playSound(mouth, Sound.ENTITY_GENERIC_EAT, 0.6f, 0.8f + random.nextFloat() * 0.4f);
                    world.spawnParticle(Particle.ITEM, mouth, 4, 0.08, 0.05, 0.08, 0.04, dish);
                }
            }, () -> {
                villager.getEquipment().setItemInMainHand(null);
                world.spawnParticle(Particle.HAPPY_VILLAGER, villager.getEyeLocation().add(0, 0.3, 0), 5, 0.3, 0.2, 0.3);
                if (random.nextBoolean()) {
                    world.playSound(villager.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.7f, 0.9f);
                } else {
                    world.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                }
                doFor(80 + random.nextInt(160), null, this::standUp);
            });
        }

        private void standUp() {
            if (chair != null) {
                villager.leaveVehicle();
                here = beforeChair.clone();
                villager.teleport(abs(world, tavern, here));
                chair = null;
            }
        }

        // ------------------------------------------------------------------ sleeping

        /** Down the hall to the stairs, up, along the corridor, through his door, to bed. */
        private void goToBed() {
            int up = TavernBuilder.DECK + 1;
            walkOnGround(new Vector(AISLE_X, 1, STAIRS_BOTTOM.getZ()));
            go(STAIRS_BOTTOM.clone());
            // Up the stairs: one block per block northwards, along the nosings of the steps
            go(new Vector(2.0, up, 11.5));
            go(new Vector(2.0, up, 10.5));
            go(new Vector(ROOM_DOOR_X + 1.0, up, 10.5));
            go(new Vector(ROOM_DOOR_X + 1.0, up, ROOM_DOOR_Z + 1.5), () -> door(true));
            go(new Vector(ROOM_DOOR_X + 1.0, up, ROOM_DOOR_Z - 1.5), () -> door(false));
            go(IN_ROOM.clone(), this::sleep);
        }

        private void sleep() {
            Location bed = new Location(world, tavern.x() + BED_X, tavern.y() + TavernBuilder.DECK + 1, tavern.z() + BED_Z);
            if (villager.sleep(bed)) {
                asleep = true;
                here = null;
            } else {
                doFor(200, null, null); // someone else is in his bed: stand by and try later
            }
        }

        private void wakeUp() {
            int up = TavernBuilder.DECK + 1;
            villager.wakeup();
            asleep = false;
            here = IN_ROOM.clone();
            villager.teleport(abs(world, tavern, here));
            go(new Vector(ROOM_DOOR_X + 1.0, up, ROOM_DOOR_Z - 1.5), () -> door(true));
            go(new Vector(ROOM_DOOR_X + 1.0, up, ROOM_DOOR_Z + 1.5), () -> door(false));
            go(new Vector(ROOM_DOOR_X + 1.0, up, 10.5));
            go(new Vector(2.0, up, 10.5));
            go(new Vector(2.0, up, 11.5));
            go(STAIRS_BOTTOM.clone());
            go(new Vector(AISLE_X, 1, STAIRS_BOTTOM.getZ()));
            then = () -> world.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f);
        }

        /** Opens or closes his room's double door (both leaves, both halves). */
        private void door(boolean open) {
            boolean changed = false;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Block block = world.getBlockAt(tavern.x() + ROOM_DOOR_X + dx, tavern.y() + TavernBuilder.DECK + 1 + dy,
                            tavern.z() + ROOM_DOOR_Z);
                    if (block.getBlockData() instanceof Door d && d.isOpen() != open) {
                        d.setOpen(open);
                        block.setBlockData(d, false);
                        changed = true;
                    }
                }
            }
            if (changed) {
                world.playSound(abs(world, tavern, new Vector(ROOM_DOOR_X + 1.0, TavernBuilder.DECK + 1, ROOM_DOOR_Z)),
                        open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 1f);
            }
        }
    }
}
