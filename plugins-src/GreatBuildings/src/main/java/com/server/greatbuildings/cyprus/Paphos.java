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

/**
 * Paphos Castle (Κάστρο της Πάφου) in Kato Paphos, Cyprus, on the Mediterranean harbor mole.
 * Built by the Byzantines and Lusignans (13th c.), dismantled by Venetians (1570), and rebuilt
 * in 1592 by Ottoman governor Ahmet Pasha. Scale 1:1 (1 block = 1 metre).
 *
 * <p>Authentic 40 × 20 m rectangular coastal fortress tower with 3 m thick limestone walls,
 * seawater moat with a stone arched bridge, symmetric double-door arched portal with the 1592
 * white marble inscription slab, a longitudinal pointed-vaulted central gallery with a central
 * skylight and cistern, deep embrasured side alcoves and Ottoman prison cells, vaulted end halls
 * (British salt store and guard/armory hall), north-wall staircase to the roof deck with
 * 12 cannon embrasures, and an authentic 3-room upper tower (central Ottoman mosque flanked by
 * garrison quarters and commander's room) with protruding stone cornice and flat roof parapet.
 */
public final class Paphos implements Building {

    static final int EX = 55, EZ = 54;
    private static final int TOP = 22;

    // Castle rectangular footprint: 40 × 20 m (x = 8..47, z = 8..27)
    private static final int CX1 = 8, CX2 = 47;   // 40 blocks wide
    private static final int CZ1 = 8, CZ2 = 27;   // 20 blocks deep
    /** Left leaf of the 2-block-wide central gate (x = 27..28, perfectly centered in 8..47). */
    private static final int GATE_X = 27;

    private static final String AIR = Plan.AIR;
    private static final String WATER = b("water");

    // Warm Cypriot limestone masonry using mud bricks
    private static final String LIGHT = b("mud_bricks");
    private static final String LIGHT2 = b("packed_mud");
    private static final String WARM = b("smooth_sandstone");
    private static final String CARVED = b("chiseled_sandstone");
    private static final String WALL = b("mud_brick_wall");
    private static final String STAIRS_STONE = "mud_brick_stairs";
    private static final String SLAB_STONE = "mud_brick_slab";

    // Marble inscription slab
    private static final String MARBLE = b("calcite");

    // Cannon materials
    private static final String CANNON_BODY = b("polished_blackstone");
    private static final String CANNON_MUZZLE = b("dispenser");

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
        roofDeckAndCannons(p);
        staircase(p);
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
        return r < 72 ? LIGHT : r < 90 ? LIGHT2 : WARM;
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
     * - Seawater moat on the south side of the castle (z = 28..35).
     * - Harbor promenade / mole on the south approach (z = 36..EZ).
     */
    private void siteAndMoat(Plan p, Terrain t) {
        for (int x = -4; x <= EX + 4; x++) {
            for (int z = -4; z <= EZ + 2; z++) {
                clearColumn(p, x, z);

                boolean inCastleFootprint = (x >= CX1 && x <= CX2 && z >= CZ1 && z <= CZ2);
                boolean onPromenade = (z >= 36 && z <= EZ && x >= 0 && x <= EX);

                if (inCastleFootprint) {
                    foundation(p, t, x, z, b("stone"));
                    p.set(x, 0, z, LIGHT2);
                } else if (onPromenade) {
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
            for (int z = -2; z <= 5; z++) {
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
        int x1 = GATE_X - 2; // 25 (west parapet)
        int x2 = GATE_X + 3; // 30 (east parapet); roadway is 26..29 centered on 27..28
        int z1 = 28;
        int z2 = 35;

        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                // Bridge piers down to sea floor
                foundation(p, t, x, z, b("stone"));

                // Bridge deck at y = 1
                p.set(x, 1, z, paving(x, z));

                // Arched stone vault underneath the bridge over the water
                if (z >= 30 && z <= 33) {
                    p.set(x, 0, z, AIR);
                } else {
                    p.set(x, 0, z, ashlar(x, 0, z));
                }

                // Parapets on east and west sides of the bridge
                if (x == x1 || x == x2) {
                    p.set(x, 2, z, WALL);
                }
            }
        }

        // 4-wide steps from the southern promenade up onto the bridge
        for (int x = GATE_X - 1; x <= GATE_X + 2; x++) {
            p.set(x, 1, z2 + 1, stairs(STAIRS_STONE, Face.NORTH, false));
        }

        // Bridge lanterns on corner posts
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                p.set(x, 2, z, CARVED);
                p.set(x, 3, z, lantern(false));
            }
        }

        // Lectern with historical book at the southern bridgehead
        p.lectern(x1 - 1, 1, z2 + 1, Face.EAST);
    }

    // ------------------------------------------------------------------ castle core structure

    /** Heavy 3-metre limestone castle walls, perimeter, and single arched double-door portal. */
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

        // Single arched entrance portal at south wall: strictly 2 blocks wide (x = 27..28) matching the double door
        for (int x = GATE_X; x <= GATE_X + 1; x++) {
            for (int z = CZ2 - 2; z <= CZ2; z++) {
                p.set(x, 1, z, paving(x, z));
                p.set(x, 2, z, AIR);
                p.set(x, 3, z, AIR);
            }
            // Portal arch head on outer/inner faces (y = 4), solid lintel over the door leaf at CZ2 - 1
            Face archSide = (x == GATE_X) ? Face.WEST : Face.EAST;
            p.set(x, 4, CZ2, stairs(STAIRS_STONE, archSide, true));
            p.set(x, 4, CZ2 - 2, stairs(STAIRS_STONE, archSide, true));
        }

        // Heavy dark oak double door seated cleanly between solid stone jambs (x = 26 and x = 29)
        p.doubleDoor(GATE_X, 2, CZ2 - 1, DOOR, Face.NORTH);

        // The famous 1592 white marble inscription slab of Ahmet Pasha above the entrance
        p.set(GATE_X - 1, 5, CZ2, CARVED);
        p.set(GATE_X, 5, CZ2, MARBLE);
        p.set(GATE_X + 1, 5, CZ2, MARBLE);
        p.set(GATE_X + 2, 5, CZ2, CARVED);

        // Plaques flanking the entrance portal in Greek and Russian
        p.wallSign(GATE_X - 2, 3, CZ2 + 1, "dark_oak", Face.SOUTH, PLAQUE_EL);
        p.wallSign(GATE_X + 3, 3, CZ2 + 1, "dark_oak", Face.SOUTH, PLAQUE_RU);
    }

    private static final List<String> PLAQUE_EL = List.of("Κάστρο", "της Πάφου", "1592", "Κύπρος");
    private static final List<String> PLAQUE_RU = List.of("Пафосский замок", "Пафос, Кипр", "1592 год", "масштаб 1:1");

    // ------------------------------------------------------------------ ground floor interior

    /**
     * Authentic Paphos Castle ground floor (x = 11..44, z = 11..24) matching the museum ground plan:
     * - Full-length pointed-vaulted central gallery (z = 16..19) from east end hall to west end hall,
     *   with open central skylight (x = 33..35, z = 17..18) and recessed cistern below.
     * - Entrance vestibule (x = 26..29, z = 20..24) and north stair foyer + guard/ticket niche.
     * - South & north flanks: open vaulted alcoves with recessed cut-stone thresholds and mezzanine
     *   corbels alternating with double-doored Ottoman prison cells and deep splayed wall embrasures.
     * - West End Hall (British salt store) + NW corner dungeon; East Grand Vaulted Hall + SE armory.
     * - NE Under-Stair Vault accessed by double door at (35..36, 2, 15).
     */
    private void groundFloorInterior(Plan p) {
        // 1. Base clearing across castle interior (5-block tall default rooms y = 2..6, solid roof y = 7..10)
        for (int x = CX1 + 3; x <= CX2 - 3; x++) {
            for (int z = CZ1 + 3; z <= CZ2 - 3; z++) {
                p.set(x, 1, z, paving(x, z));
                for (int y = 2; y <= 6; y++) {
                    p.set(x, y, z, AIR);
                }
                for (int y = 7; y <= 10; y++) {
                    p.set(x, y, z, ashlar(x, y, z));
                }
            }
        }

        // 2. Longitudinal pointed-vaulted central gallery (x = 15..39, z = 16..19): 6-7 blocks tall inside vault
        for (int x = 15; x <= 39; x++) {
            p.set(x, 6, 16, stairs(STAIRS_STONE, Face.NORTH, true));
            p.set(x, 6, 19, stairs(STAIRS_STONE, Face.SOUTH, true));
            p.set(x, 7, 17, AIR);
            p.set(x, 7, 18, AIR);
            p.set(x, 8, 17, stairs(STAIRS_STONE, Face.NORTH, true));
            p.set(x, 8, 18, stairs(STAIRS_STONE, Face.SOUTH, true));
        }
        // Pointed transverse stone arch ribs across the central gallery
        for (int ribX : new int[]{15, 20, 25, 30, 39}) {
            p.set(ribX, 6, 16, CARVED);
            p.set(ribX, 6, 19, CARVED);
            p.set(ribX, 7, 17, stairs(STAIRS_STONE, Face.NORTH, true));
            p.set(ribX, 7, 18, stairs(STAIRS_STONE, Face.SOUTH, true));
        }

        // Vaulted ceilings in the entrance vestibule, west end hall, and east transverse grand hall
        for (int z = 20; z <= 24; z++) {
            p.set(26, 7, z, stairs(STAIRS_STONE, Face.WEST, true));
            p.set(27, 7, z, AIR);
            p.set(28, 7, z, AIR);
            p.set(29, 7, z, stairs(STAIRS_STONE, Face.EAST, true));
        }
        for (int z = 15; z <= 24; z++) {
            p.set(11, 7, z, stairs(STAIRS_STONE, Face.WEST, true));
            p.set(12, 7, z, AIR);
            p.set(13, 7, z, AIR);
            p.set(14, 7, z, stairs(STAIRS_STONE, Face.EAST, true));
        }
        for (int z = 11; z <= 19; z++) {
            p.set(40, 7, z, stairs(STAIRS_STONE, Face.WEST, true));
            p.set(41, 7, z, AIR);
            p.set(42, 7, z, AIR);
            p.set(43, 7, z, AIR);
            p.set(44, 7, z, stairs(STAIRS_STONE, Face.EAST, true));
        }

        // 3. Structural internal dividing walls (solid stone y = 2..10, pierced by wide arches or double doors)
        // Transverse walls flanking the end halls (open 4-wide pointed arch along central gallery z = 16..19)
        for (int z = 11; z <= 15; z++) {
            wallColumn(p, 15, z, 2, 10);
            wallColumn(p, 39, z, 2, 10);
        }
        for (int z = 20; z <= 24; z++) {
            wallColumn(p, 15, z, 2, 10);
            wallColumn(p, 39, z, 2, 10);
        }
        // Partition separating NW Corner Dungeon from West End Hall
        for (int x = 11; x <= 14; x++) {
            wallColumn(p, x, 14, 2, 10);
        }
        // Partition separating SE Corner Armory from East Grand Hall
        for (int x = 40; x <= 44; x++) {
            wallColumn(p, x, 20, 2, 10);
        }
        // South-flank partitions (between SW Alcove, SW Cell, Vestibule, SE Cell, SE Alcove)
        for (int z = 20; z <= 24; z++) {
            wallColumn(p, 20, z, 2, 10);
            wallColumn(p, 25, z, 2, 10);
            wallColumn(p, 30, z, 2, 10);
            wallColumn(p, 35, z, 2, 10);
        }
        // Front walls closing the SW and SE Ottoman prison cells (z = 20)
        for (int x = 21; x <= 24; x++) {
            wallColumn(p, x, 20, 2, 10);
        }
        for (int x = 31; x <= 34; x++) {
            wallColumn(p, x, 20, 2, 10);
        }
        // North-flank partitions (between NW Alcove, NW Cell, Stair Foyer / Guard Niche, NE Vault)
        for (int z = 11; z <= 15; z++) {
            wallColumn(p, 20, z, 2, 10);
            wallColumn(p, 25, z, 2, 10);
        }
        for (int z = 13; z <= 15; z++) {
            wallColumn(p, 32, z, 2, 10);
        }
        // Front walls closing the NW prison cell and NE under-stair vault (z = 15)
        for (int x = 21; x <= 24; x++) {
            wallColumn(p, x, 15, 2, 10);
        }
        for (int x = 33; x <= 38; x++) {
            wallColumn(p, x, 15, 2, 10);
        }

        // Open arches into SW & SE Vaulted Alcoves (chamfered arch heads at y = 6, open y = 2..5 below)
        p.set(16, 6, 20, stairs(STAIRS_STONE, Face.WEST, true));
        p.set(19, 6, 20, stairs(STAIRS_STONE, Face.EAST, true));
        p.set(16, 6, 15, stairs(STAIRS_STONE, Face.WEST, true));
        p.set(19, 6, 15, stairs(STAIRS_STONE, Face.EAST, true));
        p.set(36, 6, 20, stairs(STAIRS_STONE, Face.WEST, true));
        p.set(38, 6, 20, stairs(STAIRS_STONE, Face.EAST, true));

        // 4. Open-air central skylight (x = 33..35, z = 17..18) and recessed stone cistern directly underneath
        for (int x = 33; x <= 35; x++) {
            for (int z = 17; z <= 18; z++) {
                for (int y = 2; y <= 11; y++) {
                    p.set(x, y, z, AIR);
                }
            }
        }
        for (int x = 33; x <= 35; x++) {
            for (int z = 16; z <= 18; z++) {
                p.set(x, 1, z, b("cut_sandstone"));
            }
        }
        p.set(34, 0, 17, LIGHT2);
        p.set(34, 1, 17, WATER);

        // Semi-circular flush cut-sandstone thresholds at open vaulted alcoves (no height step)
        for (int x = 16; x <= 19; x++) {
            p.set(x, 1, 20, b("cut_sandstone"));
            p.set(x, 1, 15, b("cut_sandstone"));
        }
        for (int x = 17; x <= 18; x++) {
            p.set(x, 1, 19, b("cut_sandstone"));
            p.set(x, 1, 16, b("cut_sandstone"));
        }
        for (int x = 36; x <= 38; x++) {
            p.set(x, 1, 20, b("cut_sandstone"));
        }
        p.set(37, 1, 19, b("cut_sandstone"));

        // 5. Six interior double doors + high barred vent slots above cells
        p.doubleDoor(12, 2, 14, DOOR, Face.NORTH); // NW Corner Dungeon
        p.doubleDoor(22, 2, 20, DOOR, Face.SOUTH); // SW Prison Cell
        p.doubleDoor(22, 2, 15, DOOR, Face.NORTH); // NW Prison Cell
        p.doubleDoor(32, 2, 20, DOOR, Face.SOUTH); // SE Prison Cell
        p.doubleDoor(35, 2, 15, DOOR, Face.NORTH); // NE Under-Stair Vault
        p.doubleDoor(41, 2, 20, DOOR, Face.SOUTH); // SE Corner Armory
        p.set(22, 5, 20, BARS);
        p.set(22, 5, 15, BARS);
        p.set(32, 5, 20, BARS);

        // 6. Deep splayed embrasures & barred windows in the 3-metre outer stone walls
        // North sea embrasures (z = 8..10: outer bars at z = 8, open embrasure at z = 9..10)
        embrasureZ(p, 12, 13, 3, 3, 9, 10, 8);  // NW Corner Dungeon
        embrasureZ(p, 17, 18, 3, 4, 9, 10, 8);  // NW Vaulted Alcove
        embrasureZ(p, 22, 23, 3, 3, 9, 10, 8);  // NW Prison Cell
        embrasureZ(p, 42, 42, 3, 4, 9, 10, 8);  // East Grand Hall (north embrasure)
        // South harbour embrasures (z = 25..27: outer bars at z = 27, open embrasure at z = 25..26)
        embrasureZ(p, 17, 18, 2, 3, 25, 26, 27); // SW Vaulted Alcove low barred window
        embrasureZ(p, 22, 23, 3, 3, 25, 26, 27); // SW Prison Cell
        embrasureZ(p, 32, 33, 3, 3, 25, 26, 27); // SE Prison Cell
        embrasureZ(p, 37, 37, 2, 3, 25, 26, 27); // SE Vaulted Alcove low barred window
        embrasureZ(p, 42, 42, 3, 3, 25, 26, 27); // SE Corner Armory
        // West and East sea embrasures
        embrasureX(p, 9, 10, 8, 3, 4, 20, 21);   // West End Hall
        embrasureX(p, 45, 46, 47, 3, 4, 13, 14); // East Grand Hall (east north)
        embrasureX(p, 45, 46, 47, 3, 4, 17, 18); // East Grand Hall (east south)
        embrasureX(p, 45, 46, 47, 3, 3, 22, 23); // SE Corner Armory (east)

        // 7. Authentic room furnishings (leaving all doorway and gallery walking zones clear)
        // NW Corner Dungeon
        p.set(11, 2, 11, slab(SLAB_STONE));
        p.set(14, 2, 11, BARS);
        p.set(14, 4, 11, CHAIN);
        p.set(14, 5, 11, CHAIN);

        // West End Hall: British Salt Store (stacked quartz salt blocks & barrels in south end)
        for (int x = 11; x <= 13; x++) {
            for (int z = 22; z <= 24; z++) {
                if (x == 11 || z == 24) {
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
        }

        // SW & SE Vaulted Alcoves: wooden mezzanine corbel brackets at y = 4..5 + oak benches
        p.set(16, 4, 24, stairs("spruce_stairs", Face.WEST, true));
        p.set(19, 4, 24, stairs("spruce_stairs", Face.EAST, true));
        p.set(16, 5, 23, b("spruce_slab") + "[type=top]");
        p.set(16, 5, 24, b("spruce_slab") + "[type=top]");
        p.set(19, 5, 23, b("spruce_slab") + "[type=top]");
        p.set(19, 5, 24, b("spruce_slab") + "[type=top]");

        p.set(16, 5, 11, b("spruce_slab") + "[type=top]");
        p.set(16, 5, 12, b("spruce_slab") + "[type=top]");
        p.set(19, 5, 11, b("spruce_slab") + "[type=top]");
        p.set(19, 5, 12, b("spruce_slab") + "[type=top]");
        p.set(17, 2, 11, stairs("oak_stairs", Face.NORTH, false));
        p.set(18, 2, 11, stairs("oak_stairs", Face.NORTH, false));

        p.set(36, 5, 23, b("spruce_slab") + "[type=top]");
        p.set(36, 5, 24, b("spruce_slab") + "[type=top]");
        p.set(38, 5, 23, b("spruce_slab") + "[type=top]");
        p.set(38, 5, 24, b("spruce_slab") + "[type=top]");
        p.set(37, 2, 24, stairs("oak_stairs", Face.SOUTH, false));

        // SW, NW, SE Ottoman Prison Cells: stone bunks, iron bars, hanging chains
        p.set(21, 2, 24, slab(SLAB_STONE));
        p.set(24, 2, 24, BARS);
        p.set(24, 4, 24, CHAIN);
        p.set(24, 5, 24, CHAIN);

        p.set(24, 2, 11, slab(SLAB_STONE));
        p.set(21, 2, 11, BARS);
        p.set(21, 4, 11, CHAIN);
        p.set(21, 5, 11, CHAIN);

        p.set(34, 2, 24, slab(SLAB_STONE));
        p.set(31, 2, 24, BARS);
        p.set(31, 4, 24, CHAIN);
        p.set(31, 5, 24, CHAIN);

        // Entrance Vestibule benches
        p.set(26, 2, 22, stairs("oak_stairs", Face.WEST, false));
        p.set(26, 2, 23, stairs("oak_stairs", Face.WEST, false));
        p.set(29, 2, 22, stairs("oak_stairs", Face.EAST, false));
        p.set(29, 2, 23, stairs("oak_stairs", Face.EAST, false));

        // Guard / ticket niche opposite the entrance (x = 28..31, z = 14)
        p.set(28, 2, 14, stairs("oak_stairs", Face.NORTH, false));
        p.set(29, 2, 14, b("spruce_planks"));
        p.set(30, 2, 14, b("spruce_planks"));
        p.set(31, 2, 14, b("spruce_planks"));
        p.set(30, 3, 14, lantern(false));

        // NE Under-Stair Munitions Vault (cannonballs, chests, barrels along back edge z = 11..12)
        p.set(33, 2, 11, b("barrel"));
        p.set(34, 2, 11, b("barrel"));
        p.set(37, 2, 11, b("barrel"));
        p.set(38, 2, 11, b("barrel"));
        p.set(33, 2, 12, b("chest"));
        p.set(38, 2, 12, CANNON_BODY);

        // East Grand Hall: armory anvil, supply chests, barrels, and wooden benches
        p.set(40, 2, 11, b("anvil"));
        p.set(41, 2, 11, b("chest"));
        p.set(43, 2, 11, b("barrel"));
        p.set(44, 2, 11, b("barrel"));
        p.set(44, 2, 15, stairs("oak_stairs", Face.EAST, false));
        p.set(44, 2, 16, stairs("oak_stairs", Face.EAST, false));

        // SE Corner Armory / Guard Chamber
        p.set(40, 2, 24, b("chest"));
        p.set(40, 4, 24, CHAIN);
        p.set(40, 5, 24, CHAIN);
        p.set(43, 2, 24, b("barrel"));
        p.set(44, 2, 24, b("barrel"));
        p.set(44, 2, 23, b("anvil"));

        // 8. Hanging lanterns along the central gallery, end halls, alcoves, and chambers
        int[][] hangingLanterns = {
                {18, 7, 17}, {23, 7, 17}, {28, 7, 17}, {37, 7, 17},
                {27, 6, 21}, {28, 6, 21},
                {13, 6, 17}, {13, 6, 22},
                {42, 6, 13}, {42, 6, 17},
                {17, 6, 22}, {18, 6, 13}, {37, 6, 22},
                {12, 5, 12}, {22, 5, 22}, {22, 5, 13}, {33, 5, 22}, {36, 5, 13}, {42, 5, 22}
        };
        for (int[] hl : hangingLanterns) {
            p.set(hl[0], hl[1], hl[2], lantern(true));
        }
    }

    private static void wallColumn(Plan p, int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            p.set(x, y, z, ashlar(x, y, z));
        }
    }

    private static void embrasureZ(Plan p, int x1, int x2, int y1, int y2, int openZ1, int openZ2, int barZ) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = openZ1; z <= openZ2; z++) {
                    p.set(x, y, z, AIR);
                }
                p.set(x, y, barZ, BARS);
            }
        }
    }

    private static void embrasureX(Plan p, int openX1, int openX2, int barX, int y1, int y2, int z1, int z2) {
        for (int z = z1; z <= z2; z++) {
            for (int y = y1; y <= y2; y++) {
                for (int x = openX1; x <= openX2; x++) {
                    p.set(x, y, z, AIR);
                }
                p.set(barX, y, z, BARS);
            }
        }
    }

    /** Grand 2-block-wide stone staircase climbing along the north wall (x = 28..37, z = 11..12) to the roof deck. */
    private void staircase(Plan p) {
        for (int i = 0; i <= 9; i++) {
            int x = 28 + i;
            int y = 2 + i;
            for (int z = 11; z <= 12; z++) {
                p.set(x, y, z, stairs(STAIRS_STONE, Face.EAST, false));
                if (i <= 4) {
                    for (int yy = 2; yy < y; yy++) {
                        p.set(x, yy, z, ashlar(x, yy, z));
                    }
                    for (int yy = y + 1; yy <= 10; yy++) {
                        p.set(x, yy, z, AIR);
                    }
                } else {
                    // Cantilevered stone soffit above the NE Under-Stair Vault (yy = 2..y-2 stays open)
                    p.set(x, y - 1, z, ashlar(x, y - 1, z));
                    for (int yy = y + 1; yy <= TOP; yy++) {
                        p.set(x, yy, z, AIR);
                    }
                }
            }
            // Cheek wall on the south side of the stair flight (z = 13)
            int wallBottom = (i <= 4) ? 2 : y - 1;
            int wallTop = (i <= 4) ? 10 : 11;
            wallColumn(p, x, 13, wallBottom, wallTop);
        }

        // Protective parapet around the roof stairwell opening (leaving east exit x = 38, z = 11..12 open)
        for (int x = 32; x <= 37; x++) {
            p.set(x, 12, 10, WALL);
            p.set(x, 12, 13, WALL);
        }
        p.set(32, 12, 11, WALL);
        p.set(32, 12, 12, WALL);
        p.set(37, 13, 13, lantern(false));
    }

    // ------------------------------------------------------------------ roof & battlements

    /**
     * Roof deck platform (y = 11), central skylight parapet, 12 perimeter embrasures (ramparts),
     * and coastal artillery cannons pointing out to sea.
     */
    private void roofDeckAndCannons(Plan p) {
        int deckY = 11;

        // Paved roof platform (leaving central skylight and north stairwell opening clear)
        for (int x = CX1; x <= CX2; x++) {
            for (int z = CZ1; z <= CZ2; z++) {
                boolean inSkylight = (x >= 33 && x <= 35 && z >= 17 && z <= 18);
                boolean inStairwell = (x >= 33 && x <= 37 && z >= 11 && z <= 12);
                if (!inSkylight && !inStairwell) {
                    p.set(x, deckY, z, paving(x, z));
                }
            }
        }

        // Parapet around the central skylight opening (x = 32..36, z = 16..19)
        for (int x = 32; x <= 36; x++) {
            p.set(x, 12, 16, WALL);
            p.set(x, 12, 19, WALL);
        }
        for (int z = 16; z <= 19; z++) {
            p.set(32, 12, z, WALL);
            p.set(36, 12, z, WALL);
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

        // 12 distinct cannon embrasures: 4 North (clearing stairwell at x = 32..37), 4 South, 2 West, 2 East
        int[] northXs = {13, 21, 31, 42};
        for (int ex : northXs) {
            cannon(p, ex, deckY + 1, CZ1, Face.NORTH);
        }

        int[] southXs = {13, 21, 34, 42};
        for (int ex : southXs) {
            cannon(p, ex, deckY + 1, CZ2, Face.SOUTH);
        }

        int[] sideZs = {13, 22};
        for (int ez : sideZs) {
            cannon(p, CX1, deckY + 1, ez, Face.WEST);
            cannon(p, CX2, deckY + 1, ez, Face.EAST);
        }

        // Roof corner lanterns on posts
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
        p.set(x, y, z, AIR);
        p.set(x, y + 1, z, AIR);

        int bx = x - dir.dx;
        int bz = z - dir.dz;
        p.set(bx, y, bz, CANNON_BODY);
        p.set(x, y, z, CANNON_MUZZLE + "[facing=" + dir.id() + "]");
    }

    // ------------------------------------------------------------------ upper keep

    /**
     * Authentic 3-room upper tower (x = 13..29, z = 13..22, 17 × 10 m, floor y = 11, roof y = 17):
     * - Central Ottoman mosque with prayer carpets and mihrab, entered through a south double door.
     * - West garrison barracks (double door from mosque) with soldier bunks and chests.
     * - East commander's & armory room (double door from mosque) with desk, bookshelf, and equipment.
     * - Tall barred windows, protruding limestone cornice molding, and flat viewing-deck parapet.
     */
    private void upperKeep(Plan p) {
        int kx1 = 13, kx2 = 29; // 17 blocks wide
        int kz1 = 13, kz2 = 22; // 10 blocks deep
        int floorY = 11;
        int roofY = 17;

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
                    p.set(x, roofY, z, paving(x, z));
                }
            }
        }

        // Interior dividing walls dividing upper tower into West Barracks (14..17), Mosque (19..23), East Commander (25..28)
        for (int z = kz1 + 1; z <= kz2 - 1; z++) {
            wallColumn(p, 18, z, floorY + 1, roofY - 1);
            wallColumn(p, 24, z, floorY + 1, roofY - 1);
        }

        // Three double doors connecting terrace -> Mosque -> West & East rooms
        p.doubleDoor(20, floorY + 1, kz2, DOOR, Face.NORTH);
        p.doubleDoor(18, floorY + 1, 17, DOOR, Face.WEST);
        p.doubleDoor(24, floorY + 1, 17, DOOR, Face.EAST);

        // Tall iron-barred lattice windows characteristic of the upper tower
        for (int y = floorY + 2; y <= floorY + 4; y++) {
            p.set(23, y, kz2, BARS);
            p.set(21, y, kz1, BARS);
            p.set(kx1, y, 17, BARS);
            p.set(kx1, y, 18, BARS);
            p.set(kx2, y, 17, BARS);
            p.set(kx2, y, 18, BARS);
        }
        for (int y = floorY + 2; y <= floorY + 3; y++) {
            p.set(15, y, kz2, BARS);
            p.set(27, y, kz2, BARS);
            p.set(15, y, kz1, BARS);
            p.set(27, y, kz1, BARS);
        }

        // Central Ottoman mosque: bordered prayer rugs (leaving door thresholds z = 21, x = 19, x = 23 clear)
        for (int x = 20; x <= 22; x++) {
            for (int z = 15; z <= 20; z++) {
                boolean inner = (x == 21 && z >= 16 && z <= 19);
                p.set(x, floorY + 1, z, b(inner ? "red_carpet" : "green_carpet"));
            }
        }
        // Carved mihrab niche at south-east mosque wall and Quran bookshelf stand
        p.set(23, floorY + 1, 21, CARVED);
        p.set(23, floorY + 2, 21, stairs(STAIRS_STONE, Face.NORTH, true));
        p.set(23, floorY + 3, 21, CARVED);
        p.set(19, floorY + 1, 14, b("bookshelf"));
        p.set(19, floorY + 2, 14, lantern(false));

        // West garrison barracks: wooden bunks, pillows, chests, barrel, crafting bench
        p.set(14, floorY + 1, 14, b("white_wool"));
        p.set(14, floorY + 1, 15, slab("spruce_slab"));
        p.set(14, floorY + 1, 20, slab("spruce_slab"));
        p.set(14, floorY + 1, 21, b("white_wool"));
        p.set(15, floorY + 1, 14, b("chest"));
        p.set(16, floorY + 1, 14, b("barrel"));
        p.set(15, floorY + 1, 21, b("chest"));
        p.set(16, floorY + 1, 21, b("crafting_table"));

        // East commander's & armory room: desk, chair, bookshelf, chests, barrel, anvil
        p.set(26, floorY + 1, 14, b("bookshelf"));
        p.set(27, floorY + 1, 14, b("spruce_planks"));
        p.set(28, floorY + 1, 14, b("spruce_planks"));
        p.set(28, floorY + 2, 14, lantern(false));
        p.set(27, floorY + 1, 15, stairs("oak_stairs", Face.NORTH, false));
        p.set(28, floorY + 1, 20, b("chest"));
        p.set(28, floorY + 1, 21, b("barrel"));
        p.set(27, floorY + 1, 21, b("anvil"));

        // Hanging lanterns in all 3 rooms
        for (int rx : new int[]{16, 21, 26}) {
            p.set(rx, roofY - 1, 16, lantern(true));
            p.set(rx, roofY - 1, 19, lantern(true));
        }

        // Protruding limestone cornice ring at roofY (characteristic Ottoman exterior trim)
        String cornice = b(SLAB_STONE) + "[type=top]";
        for (int x = kx1 - 1; x <= kx2 + 1; x++) {
            p.set(x, roofY, kz1 - 1, cornice);
            p.set(x, roofY, kz2 + 1, cornice);
        }
        for (int z = kz1; z <= kz2; z++) {
            p.set(kx1 - 1, roofY, z, cornice);
            p.set(kx2 + 1, roofY, z, cornice);
        }

        // Flat upper viewing deck parapet (y = 18) with no central mast/beacon
        for (int x = kx1; x <= kx2; x++) {
            p.set(x, roofY + 1, kz1, WALL);
            p.set(x, roofY + 1, kz2, WALL);
        }
        for (int z = kz1; z <= kz2; z++) {
            p.set(kx1, roofY + 1, z, WALL);
            p.set(kx2, roofY + 1, z, WALL);
        }
    }

    // ------------------------------------------------------------------ harbor promenade

    /** Paphos harbor promenade: stone quay, mooring bollards, palm trees, and lantern posts. */
    private void promenadeAndHarbor(Plan p) {
        // Mooring bollards along the water edge of the promenade (z = 36)
        for (int x = 6; x <= EX - 6; x += 6) {
            p.set(x, 1, 36, b("polished_andesite"));
            p.set(x, 2, 36, slab("smooth_stone_slab"));
        }

        // Promenade lantern posts
        int[][] posts = {
                {GATE_X - 6, EZ - 2}, {GATE_X + 7, EZ - 2},
                {8, 42}, {EX - 8, 42},
                {GATE_X - 6, 44}, {GATE_X + 7, 44}
        };
        for (int[] post : posts) {
            p.set(post[0], 1, post[1], b("cobblestone_wall"));
            p.set(post[0], 2, post[1], b("cobblestone_wall"));
            p.set(post[0], 3, post[1], lantern(false));
        }

        // Mediterranean palm trees along the promenade
        int[][] palms = {
                {8, 49}, {EX - 8, 49},
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
                        + "Средиземного моря.\n\nПостроен в масштабе 1:1: один блок — один метр (40 × 20 м).",
                "История укрепления насчитывает более 800 лет. Первая византийская крепость Саранта Колонес "
                        + "была разрушена землетрясением 1222 года. В XIII веке правители Кипра из династии "
                        + "Лузиньянов возвели на молу портовый замок из двух башен.",
                "В XIV–XV веках замок укрепляли генуэзцы и венецианцы. В 1570 году, перед османским вторжением, "
                        + "венецианцы сами взорвали башни, чтобы не сдавать их врагу. Нынешний облик замок обрёл в 1592 году, "
                        + "когда его заново отстроил турецкий правитель Ахмет-паша.",
                "Над единственным арочным входом до сих пор сохранилась беломраморная плита с резной надписью "
                        + "об османской реконструкции 1592 года.\n\nЗамок сложен из массивных тёсаных блоков кипрского "
                        + "известняка. Вход защищён рвом с морской водой и каменным арочным мостом.",
                "Нижний ярус:\nДлинная стрельчато-сводчатая галерея во всю длину форта со световым проёмом и колодцем, "
                        + "открытые боковые альковы и закрытые османские темницы с глубокими бойницами, соляной склад и караульный зал.",
                "Верхний ярус и крыша:\nКаменная лестница у северной стены ведёт на крышу с 12 амбразурами для пушек. "
                        + "Наверху стоит трёхкомнатная башня (~17 × 10 м) с мечетью в центре, казармой гарнизона и покоями коменданта.",
                "С 1935 года замок объявлен памятником древности, а с 1980 года входит в список Всемирного наследия "
                        + "ЮНЕСКО как часть археологического парка Пафоса.\n\nСегодня это главный исторический символ "
                        + "города и место ежегодного оперного фестиваля «Афродита»."));
    }
}
