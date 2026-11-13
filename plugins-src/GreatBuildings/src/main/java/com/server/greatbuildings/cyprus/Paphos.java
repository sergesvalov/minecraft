package com.server.greatbuildings.cyprus;

import com.server.greatbuildings.buildings.Shapes;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Country;
import com.server.greatbuildings.model.Face;
import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.Terrain;

import java.util.List;

import static com.server.greatbuildings.model.States.b;
import static com.server.greatbuildings.model.States.lantern;
import static com.server.greatbuildings.model.States.leaves;
import static com.server.greatbuildings.model.States.logY;
import static com.server.greatbuildings.model.States.slab;
import static com.server.greatbuildings.model.States.stairs;
import static com.server.greatbuildings.model.States.wallSign;

/**
 * Paphos Castle (Κάστро της Πάφου) in Kato Paphos, Cyprus, on the Mediterranean harbor mole.
 * Built by the Byzantines and Lusignans (13th c.), dismantled by Venetians (1570), and rebuilt
 * in 1592 by Ottoman governor Ahmet Pasha. Scale 1:1 (1 block = 1 metre).
 *
 * <p>A squat, heavy coastal fortress tower (approx. 33 × 23 m, roof deck at 11 m, upper keep to 16 m)
 * built of warm Cypriot limestone. Features a seawater moat with an arched stone bridge, single arched
 * portal with the famous 1592 white marble inscription slab, barrel-vaulted ground floor with dungeons,
 * barracks, and British salt store, a stone grand staircase, an open roof platform with 12 embrasures
 * mounting coastal cannons, and an upper keep housing the garrison quarters and mosque.
 */
public final class Paphos implements Building {

    static final int EX = 46, EZ = 52;
    private static final int TOP = 20;

    // Castle rectangular footprint
    private static final int CX1 = 7, CX2 = 39;   // 33 blocks wide
    private static final int CZ1 = 6, CZ2 = 28;   // 23 blocks deep
    private static final int GATE_X = 23;         // Centre of south entrance and bridge

    private static final String AIR = Plan.AIR;
    private static final String WATER = b("water");

    // Warm Cypriot limestone palette
    private static final String LIGHT = b("smooth_sandstone");
    private static final String LIGHT2 = b("cut_sandstone");
    private static final String WARM = b("sandstone");
    private static final String DARK = b("terracotta");
    private static final String CARVED = b("chiseled_sandstone");
    private static final String WALL = b("sandstone_wall");
    private static final String STAIRS_STONE = "smooth_sandstone_stairs";
    private static final String SLAB_STONE = "smooth_sandstone_slab";

    // Marble inscription slab
    private static final String MARBLE = b("calcite");

    // Cannon materials
    private static final String CANNON_BODY = b("polished_blackstone");
    private static final String CANNON_MUZZLE = b("dispenser");

    // Dungeons and metalwork
    private static final String BARS = b("iron_bars");
    private static final String CHAIN = b("iron_chain") + "[axis=y]";
    private static final String DOOR = "dark_oak_door";

    @Override
    public String id() {
        return "paphos";
    }

    @Override
    public Country country() {
        return Country.CYPRUS;
    }

    @Override
    public String title() {
        return "Пафосский замок";
    }

    @Override
    public String where() {
        return "Пафос, Кипр, гавань Като-Пафос";
    }

    @Override
    public int[] bounds() {
        return new int[]{-4, -15, -4, EX + 4, TOP, EZ + 2};
    }

    @Override
    public int[] anchor() {
        // Player stands on the harbor promenade, looking north across the bridge into the castle
        return new int[]{GATE_X, EZ + 6};
    }

    @Override
    public void draw(Plan p, Terrain t) {
        siteAndMoat(p, t);
        bridge(p, t);
        castleCore(p, t);
        groundFloorInterior(p);
        staircase(p);
        roofDeckAndCannons(p);
        upperKeep(p);
        promenadeAndHarbor(p);
    }

    // ------------------------------------------------------------------ materials & masonry

    static int hash(int x, int y, int z) {
        return Shapes.hash(x, y, z);
    }

    /** Cypriot weathered limestone masonry. */
    private static String ashlar(int x, int y, int z) {
        int r = hash(x, y, z) % 100;
        return r < 58 ? LIGHT : r < 80 ? LIGHT2 : r < 92 ? WARM : DARK;
    }

    private static String paving(int x, int z) {
        int r = hash(x, 0, z) % 100;
        return b(r < 50 ? "stone_bricks" : r < 70 ? "polished_andesite" : r < 85 ? "cracked_stone_bricks" : "cobblestone");
    }

    private static String rubble(int x, int y, int z) {
        int r = hash(x, y, z) % 100;
        return b(r < 40 ? "cobblestone" : r < 60 ? "mossy_cobblestone" : r < 80 ? "andesite" : "stone");
    }

    private static void foundation(Plan p, Terrain t, int x, int z, String state) {
        Shapes.foundation(p, t, x, z, state);
    }

    private static void clearColumn(Plan p, int x, int z) {
        Shapes.clearColumn(p, x, z, TOP);
    }

    // ------------------------------------------------------------------ site, sea & moat

    /**
     * Prepares the Mediterranean harbor site:
     * - Open sea on north, west, and east edges (y = -2..0 is water, sandy seabed at -3).
     * - Seawater moat on the south side of the castle (z = 29..36).
     * - Harbor promenade / mole on the south approach (z = 37..EZ).
     */
    private void siteAndMoat(Plan p, Terrain t) {
        for (int x = -4; x <= EX + 4; x++) {
            for (int z = -4; z <= EZ + 2; z++) {
                clearColumn(p, x, z);

                boolean inCastleFootprint = (x >= CX1 && x <= CX2 && z >= CZ1 && z <= CZ2);
                boolean inPromenade = (z >= 37);
                boolean inMoat = (z >= 29 && z <= 36 && x >= 2 && x <= EX - 2);
                boolean isOpenSea = !inCastleFootprint && !inPromenade && !inMoat;

                if (inCastleFootprint) {
                    p.set(x, 0, z, LIGHT2);
                    foundation(p, t, x, z, b("stone"));
                } else if (inPromenade) {
                    p.set(x, 0, z, paving(x, z));
                    foundation(p, t, x, z, b("stone"));
                } else {
                    // Seawater channel / moat / sea
                    foundation(p, t, x, z, b("sand"));
                    p.set(x, -3, z, b("sandstone"));
                    for (int y = -2; y <= 0; y++) {
                        p.set(x, y, z, WATER);
                    }
                }
            }
        }

        // Rocky sea breakwater around the outer sea perimeter (natural boulders)
        for (int x = -2; x <= EX + 2; x++) {
            for (int z = -2; z <= 4; z++) {
                if (hash(x, 0, z) % 100 < 35) {
                    p.set(x, 0, z, rubble(x, 0, z));
                    if (hash(x, 1, z) % 100 < 40) {
                        p.set(x, 1, z, rubble(x, 1, z));
                    }
                }
            }
        }
    }

    /** Stone arched bridge spanning the seawater moat to the castle gate. */
    private void bridge(Plan p, Terrain t) {
        int x1 = GATE_X - 2; // 21
        int x2 = GATE_X + 2; // 25
        int z1 = 29;
        int z2 = 36;

        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                // Bridge piers down to bedrock / sea floor
                foundation(p, t, x, z, b("stone"));

                // Bridge deck at y = 1
                p.set(x, 1, z, paving(x, z));

                // Arched stone vault underneath the bridge over the water
                if (z >= 31 && z <= 34) {
                    p.set(x, 0, z, AIR); // water flow under bridge
                } else {
                    p.set(x, 0, z, ashlar(x, 0, z));
                }

                // Parapets on east and west sides of the bridge
                if (x == x1 || x == x2) {
                    p.set(x, 2, z, WALL);
                }
            }
        }

        // Bridge lanterns on corner posts
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                p.set(x, 2, z, CARVED);
                p.set(x, 3, z, lantern(false));
            }
        }

        // Lectern with historical book at the southern bridgehead
        p.set(x1 - 1, 1, z2 + 1, b("lectern") + "[facing=east]");
        p.lecterns.add(new int[]{x1 - 1, 1, z2 + 1});
    }

    // ------------------------------------------------------------------ castle core structure

    /** Heavy limestone castle walls, perimeter, and single arched portal. */
    private void castleCore(Plan p, Terrain t) {
        int wallTop = 11;

        // Outer masonry shell: 3 blocks thick
        for (int x = CX1; x <= CX2; x++) {
            for (int z = CZ1; z <= CZ2; z++) {
                boolean isOuterWall = (x <= CX1 + 2 || x >= CX2 - 2 || z <= CZ1 + 2 || z >= CZ2 - 2);

                if (isOuterWall) {
                    foundation(p, t, x, z, b("stone"));
                    p.set(x, 0, z, LIGHT2);

                    for (int y = 1; y <= wallTop; y++) {
                        p.set(x, y, z, ashlar(x, y, z));
                    }
                }
            }
        }

        // Single arched entrance portal at south wall (x = 22..25, z = 26..28)
        for (int x = GATE_X - 1; x <= GATE_X + 2; x++) {
            for (int z = CZ2 - 2; z <= CZ2; z++) {
                p.set(x, 1, z, paving(x, z));
                p.set(x, 2, z, AIR);
                p.set(x, 3, z, AIR);
            }
            // Portal arch head
            p.set(x, 4, CZ2, stairs(STAIRS_STONE, x == GATE_X - 1 ? Face.WEST : x == GATE_X + 2 ? Face.EAST : Face.SOUTH, true));
        }

        // Heavy dark oak double doors between stone jambs
        for (int x : new int[]{GATE_X - 1, GATE_X + 2}) {
            p.set(x, 2, CZ2 - 1, ashlar(x, 2, CZ2 - 1));
            p.set(x, 3, CZ2 - 1, ashlar(x, 3, CZ2 - 1));
        }
        p.doubleDoor(GATE_X, 2, CZ2 - 1, DOOR, Face.NORTH);

        // The famous 1592 white marble inscription slab of Ahmet Pasha above the entrance
        for (int x = GATE_X - 1; x <= GATE_X + 2; x++) {
            p.set(x, 5, CZ2, MARBLE);
        }

        // Plaques flanking the entrance portal in Greek and Russian
        p.set(GATE_X - 2, 3, CZ2 + 1, wallSign("dark_oak", Face.SOUTH));
        p.signs.add(new Plan.SignText(GATE_X - 2, 3, CZ2 + 1, PLAQUE_EL));
        p.set(GATE_X + 3, 3, CZ2 + 1, wallSign("dark_oak", Face.SOUTH));
        p.signs.add(new Plan.SignText(GATE_X + 3, 3, CZ2 + 1, PLAQUE_RU));
    }

    private static final List<String> PLAQUE_EL = List.of("Κάστρο", "της Πάφου", "1592", "Κύπρος");
    private static final List<String> PLAQUE_RU = List.of("Пафосский замок", "Пафос, Кипр", "1592 год", "масштаб 1:1");

    // ------------------------------------------------------------------ ground floor interior

    /**
     * Ground floor interior:
     * - Central vaulted hall (corridor).
     * - West wing: Ottoman dungeons / prison cells with iron bars.
     * - East wing: British salt store and armory / guardrooms.
     */
    private void groundFloorInterior(Plan p) {
        int floorY = 1;
        int ceilingY = 7;

        // Clear interior and lay floor
        for (int x = CX1 + 3; x <= CX2 - 3; x++) {
            for (int z = CZ1 + 3; z <= CZ2 - 3; z++) {
                p.set(x, floorY, z, paving(x, z));
                for (int y = 2; y <= ceilingY; y++) {
                    p.set(x, y, z, AIR);
                }
                // Heavy stone barrel vault / ceiling above ground floor
                for (int y = ceilingY + 1; y <= 10; y++) {
                    p.set(x, y, z, ashlar(x, y, z));
                }
            }
        }

        // Internal dividing walls along north-south: separating central hall from wings
        // Central hall is x = 20..26
        int westWallX = 19;
        int eastWallX = 27;

        for (int z = CZ1 + 3; z <= CZ2 - 3; z++) {
            for (int y = 2; y <= ceilingY; y++) {
                p.set(westWallX, y, z, ashlar(westWallX, y, z));
                p.set(eastWallX, y, z, ashlar(eastWallX, y, z));
            }
        }

        // Partition walls inside the wings to form individual rooms / cells
        for (int zPart : new int[]{13, 20}) {
            // West wing cells (dungeons)
            for (int x = CX1 + 3; x < westWallX; x++) {
                for (int y = 2; y <= ceilingY; y++) {
                    p.set(x, y, zPart, ashlar(x, y, zPart));
                }
            }
            // East wing rooms (salt store & guardroom)
            for (int x = eastWallX + 1; x <= CX2 - 3; x++) {
                for (int y = 2; y <= ceilingY; y++) {
                    p.set(x, y, zPart, ashlar(x, y, zPart));
                }
            }
        }

        // Doors connecting central hall to the chambers
        // West dungeon doors (iron bars)
        for (int zDoor : new int[]{10, 16, 23}) {
            p.set(westWallX, 2, zDoor, BARS);
            p.set(westWallX, 3, zDoor, BARS);
        }
        // East storage / guardroom doors (wooden doors)
        for (int zDoor : new int[]{10, 16, 23}) {
            p.doubleDoor(eastWallX, 2, zDoor, DOOR, Face.EAST);
        }

        // Dungeon furnishings (west wing): prisoner bunks, iron chains
        for (int zDunc : new int[]{10, 16, 23}) {
            p.set(CX1 + 4, 2, zDunc, b("iron_bars"));
            p.set(CX1 + 4, 3, zDunc, CHAIN);
            p.set(CX1 + 5, 2, zDunc, slab(SLAB_STONE));
        }

        // British salt store furnishings (east wing north chamber): quartz blocks and barrels
        for (int x = eastWallX + 2; x <= eastWallX + 5; x++) {
            for (int z = CZ1 + 4; z <= CZ1 + 6; z++) {
                if ((x + z) % 2 == 0) {
                    p.set(x, 2, z, b("smooth_quartz"));
                    if (hash(x, 0, z) % 2 == 0) {
                        p.set(x, 3, z, b("smooth_quartz"));
                    }
                } else {
                    p.set(x, 2, z, b("barrel"));
                }
            }
        }

        // Guardroom armory (east wing south chamber): weapon racks / anvils
        p.set(eastWallX + 4, 2, 24, b("anvil"));
        p.set(eastWallX + 5, 2, 24, b("chest"));

        // Lighting in the central hall and chambers
        p.set(GATE_X, 6, 20, lantern(true));
        p.set(GATE_X, 6, 12, lantern(true));
        p.set(14, 5, 10, lantern(true));
        p.set(14, 5, 16, lantern(true));
        p.set(14, 5, 23, lantern(true));
        p.set(32, 5, 10, lantern(true));
        p.set(32, 5, 16, lantern(true));
        p.set(32, 5, 23, lantern(true));
    }

    /** Grand stone staircase connecting ground floor to roof deck. */
    private void staircase(Plan p) {
        // Steps climbing along the west side of the central hall (x = 21, z = 12..21)
        int x = 21;
        for (int i = 0; i <= 9; i++) {
            int z = 12 + i;
            int y = 2 + i;
            p.set(x, y, z, stairs(STAIRS_STONE, Face.SOUTH, false));
            for (int yy = 2; yy < y; yy++) {
                p.set(x, yy, z, ashlar(x, yy, z));
            }
            // Clear headroom above stairs
            for (int yy = y + 1; yy <= Math.min(TOP, y + 3); yy++) {
                p.set(x, yy, z, AIR);
            }
        }
        // Stair opening through the roof deck at z = 21..22
        p.set(x, 11, 21, AIR);
        p.set(x, 11, 22, AIR);
        p.set(x + 1, 12, 21, WALL);
        p.set(x + 1, 12, 22, WALL);
    }

    // ------------------------------------------------------------------ roof & battlements

    /**
     * Roof deck platform (y = 11), 12 perimeter embrasures (ramparts),
     * and authentic coastal artillery cannons pointing out to sea!
     */
    private void roofDeckAndCannons(Plan p) {
        int deckY = 11;

        // Paved roof platform
        for (int x = CX1; x <= CX2; x++) {
            for (int z = CZ1; z <= CZ2; z++) {
                p.set(x, deckY, z, paving(x, z));
            }
        }

        // Perimeter parapet walls (y = 12..13)
        for (int x = CX1; x <= CX2; x++) {
            p.set(x, 12, CZ1, ashlar(x, 12, CZ1));
            p.set(x, 13, CZ1, WALL);
            p.set(x, 12, CZ2, ashlar(x, 12, CZ2));
            p.set(x, 13, CZ2, WALL);
        }
        for (int z = CZ1; z <= CZ2; z++) {
            p.set(CX1, 12, z, ashlar(CX1, 12, z));
            p.set(CX1, 13, z, WALL);
            p.set(CX2, 12, z, ashlar(CX2, 12, z));
            p.set(CX2, 13, z, WALL);
        }

        // 12 distinct cannon embrasures (3 on North, 3 on South, 3 on West, 3 on East)
        // North embrasures (facing sea)
        int[] northXs = {13, 23, 33};
        for (int ex : northXs) {
            cannon(p, ex, deckY + 1, CZ1, Face.NORTH);
        }

        // South embrasures (facing harbor entrance)
        int[] southXs = {13, 23, 33};
        for (int ex : southXs) {
            if (ex != GATE_X) { // Keep above entrance clear
                cannon(p, ex, deckY + 1, CZ2, Face.SOUTH);
            }
        }

        // West embrasures (facing open Mediterranean)
        int[] westZs = {10, 17, 24};
        for (int ez : westZs) {
            cannon(p, CX1, deckY + 1, ez, Face.WEST);
        }

        // East embrasures (facing harbor mole)
        int[] eastZs = {10, 17, 24};
        for (int ez : eastZs) {
            cannon(p, CX2, deckY + 1, ez, Face.EAST);
        }

        // Roof torches / lanterns on posts
        int[][] roofLanterns = {
                {CX1 + 2, CZ1 + 2}, {CX2 - 2, CZ1 + 2},
                {CX1 + 2, CZ2 - 2}, {CX2 - 2, CZ2 - 2}
        };
        for (int[] rl : roofLanterns) {
            p.set(rl[0], 12, rl[1], WALL);
            p.set(rl[0], 13, rl[1], lantern(false));
        }
    }

    /** Mounts a coastal defense cannon in an embrasure. */
    private static void cannon(Plan p, int x, int y, int z, Face dir) {
        // Cut open the embrasure
        p.set(x, y, z, AIR);
        p.set(x, y + 1, z, AIR);

        // Cannon carriage and barrel
        int bx = x - dir.dx;
        int bz = z - dir.dz;
        p.set(bx, y, bz, CANNON_BODY);
        p.set(x, y, z, CANNON_MUZZLE + "[facing=" + dir.id() + "]");
    }

    // ------------------------------------------------------------------ upper keep

    /**
     * Upper secondary tower / keep on the roof (x = 17..29, z = 8..18, y = 11..16):
     * houses the Ottoman garrison commander's quarters and small mosque,
     * with an upper lookout terrace and flagpole.
     */
    private void upperKeep(Plan p) {
        int kx1 = 17, kx2 = 29;
        int kz1 = 8, kz2 = 18;
        int floorY = 11;
        int roofY = 16;

        // Outer walls of upper keep
        for (int x = kx1; x <= kx2; x++) {
            for (int z = kz1; z <= kz2; z++) {
                boolean perimeter = (x == kx1 || x == kx2 || z == kz1 || z == kz2);
                if (perimeter) {
                    for (int y = floorY + 1; y <= roofY; y++) {
                        p.set(x, y, z, ashlar(x, y, z));
                    }
                } else {
                    for (int y = floorY + 1; y < roofY; y++) {
                        p.set(x, y, z, AIR);
                    }
                    // Ceiling / upper roof deck
                    p.set(x, roofY, z, paving(x, z));
                }
            }
        }

        // Entrance door into upper keep from the roof terrace (south side, z = 18)
        p.doubleDoor(GATE_X, floorY + 1, kz2, DOOR, Face.NORTH);

        // Arched lookout windows in the keep walls
        p.set(kx1, floorY + 2, 13, b("glass"));
        p.set(kx2, floorY + 2, 13, b("glass"));
        p.set(GATE_X, floorY + 2, kz1, b("glass"));

        // Interior: Ottoman mosque / prayer chamber
        // Red prayer carpet
        for (int x = kx1 + 2; x <= kx2 - 2; x++) {
            for (int z = kz1 + 2; z <= kz2 - 2; z++) {
                p.set(x, floorY + 1, z, b("red_carpet"));
            }
        }
        // Mihrab prayer niche facing south-east
        p.set(kx2 - 1, floorY + 1, kz2 - 1, CARVED);
        p.set(kx2 - 1, floorY + 2, kz2 - 1, stairs(STAIRS_STONE, Face.NORTH, true));

        // Lantern hanging in keep
        p.set(GATE_X, roofY - 1, 13, lantern(true));

        // Parapet around the keep roof deck (y = 17)
        for (int x = kx1; x <= kx2; x++) {
            p.set(x, 17, kz1, WALL);
            p.set(x, 17, kz2, WALL);
        }
        for (int z = kz1; z <= kz2; z++) {
            p.set(kx1, 17, z, WALL);
            p.set(kx2, 17, z, WALL);
        }

        // Flagpole / tower signal beacon at the center of the keep roof (y = 17..20)
        p.set(GATE_X, 17, 13, CARVED);
        p.set(GATE_X, 18, 13, b("iron_bars"));
        p.set(GATE_X, 19, 13, b("iron_bars"));
        p.set(GATE_X, 20, 13, lantern(false));
    }

    // ------------------------------------------------------------------ harbor promenade

    /** Paphos harbor promenade: stone quay, mooring bollards, palm trees, and lantern posts. */
    private void promenadeAndHarbor(Plan p) {
        // Mooring bollards along the water edge of the promenade (z = 37)
        for (int x = 6; x <= EX - 6; x += 6) {
            p.set(x, 1, 37, b("polished_andesite"));
            p.set(x, 2, 37, slab("smooth_stone_slab"));
        }

        // Promenade lantern posts
        int[][] posts = {
                {GATE_X - 6, EZ - 2}, {GATE_X + 6, EZ - 2},
                {8, 42}, {EX - 8, 42},
                {GATE_X - 6, 44}, {GATE_X + 6, 44}
        };
        for (int[] post : posts) {
            p.set(post[0], 1, post[1], b("cobblestone_wall"));
            p.set(post[0], 2, post[1], b("cobblestone_wall"));
            p.set(post[0], 3, post[1], lantern(false));
        }

        // Mediterranean palm trees along the promenade
        int[][] palms = {
                {8, 48}, {EX - 8, 48},
                {4, EZ - 2}, {EX - 4, EZ - 2}
        };
        for (int[] palm : palms) {
            palmTree(p, palm[0], palm[1]);
        }
    }

    private static void palmTree(Plan p, int x, int z) {
        for (int y = 1; y <= 5; y++) {
            p.set(x, y, z, logY("jungle_log"));
        }
        p.set(x, 6, z, leaves("jungle_leaves"));
        for (Face f : Face.values()) {
            p.set(x + f.dx, 5, z + f.dz, leaves("jungle_leaves"));
            p.set(x + 2 * f.dx, 4, z + 2 * f.dz, leaves("jungle_leaves"));
        }
    }

    // ------------------------------------------------------------------ book

    @Override
    public Book book() {
        return new Book("Пафосский замок", "Великие здания мира", List.of(
                "Пафосский замок\n(Κάστρο της Πάφου)\n\nПафос, Кипр, западный мол гавани Като-Пафос на берегу "
                        + "Средиземного моря.\n\nПостроен в масштабе 1:1: один блок — один метр.",
                "История укрепления насчитывает более 800 лет. Первая византийская крепость Саранта Колонес "
                        + "была разрушена землетрясением 1222 года. В XIII веке правители Кипра из династии "
                        + "Лузиньянов возвели на молу портовый замок из двух башен.",
                "В XIV–XV веках замок укрепляли генуэзцы и венецианцы. В 1570 году, перед османским вторжением, "
                        + "венецианцы сами взорвали башни, чтобы не сдавать их врагу. Нынешний облик замок обрёл в 1592 году, "
                        + "когда его заново отстроил турецкий правитель Ахмет-паша.",
                "Над единственным арочным входом до сих пор сохранилась беломраморная плита с резной надписью "
                        + "об османской реконструкции 1592 года.\n\nЗамок сложен из массивных тёсаных блоков кипрского "
                        + "известняка. Вход защищён рвом с морской водой и каменным арочным мостом.",
                "Нижний ярус:\nМассивный сводчатый зал и боковые казематы с толстыми стенами. В османский период здесь "
                        + "располагались тюремные камеры для гарнизона и заключённых. С 1878 года при британцах "
                        + "помещения использовались как соляной склад.",
                "Верхний ярус и крыша:\nШирокая каменная лестница ведёт на открытую площадку с 12 зубчатыми амбразурами, "
                        + "где стояли береговые артиллерийские орудия. На крыше возвышается верхняя башня с караульным "
                        + "помещением и мечетью, откуда открывается вид на гавань.",
                "С 1935 года замок объявлен памятником древности, а с 1980 года входит в список Всемирного наследия "
                        + "ЮНЕСКО как часть археологического парка Пафоса.\n\nСегодня это главный исторический символ "
                        + "города и место ежегодного оперного фестиваля «Афродита»."));
    }
}

