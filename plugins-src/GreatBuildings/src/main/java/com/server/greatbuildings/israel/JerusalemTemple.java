package com.server.greatbuildings.israel;

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
import static com.server.greatbuildings.model.States.stairs;

/**
 * The Second Jerusalem Temple (Herod's Temple) in its full splendour (1st century BC – 1st century AD),
 * 1:1 scale (1 block = 1 metre), based on Josephus and Tractate Middot.
 *
 * <p>Plan layout (facing the player arriving from the south +z, advancing north -z):
 * <ul>
 *   <li>Court of the Women (Ezrat Nashim): 62 × 62 m with four corner chambers and colonnaded porticoes.</li>
 *   <li>15 steps up to the Corinthian bronze Gate of Nicanor.</li>
 *   <li>Court of Israel &amp; Court of the Priests: 62 × 36 m with the 14 × 14 m stone Altar of Burnt Offering
 *       (and 4-block wide ramp) and the bronze Laver (Kiyor).</li>
 *   <li>Main Sanctuary (Heichal): 12 marble steps up to the monumental 48 × 48 m white marble and gold Ulam facade,
 *       20 m long Hekhal (Menorah, Table of Showbread, Golden Incense Altar), double curtain (Parokhet), and
 *       10 × 10 × 10 m Holy of Holies housing the Foundation Stone (Even ha-Shetiyah).</li>
 * </ul>
 */
public final class JerusalemTemple implements Building {

    static final int EX = 73, EZ = 148;
    private static final int TOP = 58;
    private static final int CX = 36;

    private static final String AIR = Plan.AIR;

    // Jerusalem white marble & warm limestone
    private static final String MARBLE = b("smooth_quartz");
    private static final String MARBLE_BLOCK = b("quartz_block");
    private static final String MARBLE_CARVED = b("chiseled_quartz_block");
    private static final String PILLAR = b("quartz_pillar") + "[axis=y]";

    private static final String STONE_LIGHT = b("mud_bricks");
    private static final String STONE_CUT = b("packed_mud");
    private static final String STONE_WARM = b("smooth_sandstone");
    private static final String STONE_WALL = b("mud_brick_wall");
    private static final String STONE_STAIRS = "mud_brick_stairs";
    private static final String DOOR = "dark_oak_door";

    // Gold, bronze, curtains & unhewn altar stone
    private static final String GOLD = b("gold_block");
    private static final String GOLD_RAW = b("raw_gold_block");
    private static final String BRONZE = b("waxed_copper_block");
    private static final String BRONZE_CUT = b("waxed_cut_copper");
    private static final String WATER = b("water");

    private static final String CURTAIN_BLUE = b("blue_wool");
    private static final String CURTAIN_PURPLE = b("purple_wool");
    private static final String CURTAIN_RED = b("red_wool");

    @Override
    public String id() {
        return "temple";
    }

    @Override
    public Country country() {
        return Country.ISRAEL;
    }

    @Override
    public String title() {
        return "Иерусалимский Храм";
    }

    @Override
    public String where() {
        return "Иерусалим, Храмовая гора (Второй Храм)";
    }

    @Override
    public int[] bounds() {
        return new int[]{-4, -15, -4, EX + 4, TOP, EZ + 1};
    }

    @Override
    public int[] anchor() {
        return new int[]{CX, EZ + 5};
    }

    @Override
    public void draw(Plan p, Terrain t) {
        siteAndCourtyards(p, t);
        courtOfWomen(p);
        nicanorStepsAndGate(p);
        courtOfPriestsAndAltar(p);
        templeSteps(p);
        sanctuaryBuilding(p);
        sanctuaryInterior(p);
        approachAndTrees(p);
    }

    // ------------------------------------------------------------------ materials

    private static String limestone(int x, int y, int z) {
        int r = Shapes.hash(x, y, z) % 100;
        return r < 70 ? STONE_LIGHT : r < 88 ? STONE_CUT : STONE_WARM;
    }

    private static String paving(int x, int z) {
        int r = Shapes.hash(x, 0, z) % 100;
        return r < 60 ? STONE_LIGHT : r < 80 ? STONE_CUT : r < 92 ? STONE_WARM : b("calcite");
    }

    private static String unhewnStone(int x, int y, int z) {
        int r = Shapes.hash(x, y, z) % 100;
        return b(r < 40 ? "cobblestone" : r < 70 ? "andesite" : r < 90 ? "stone" : "mossy_cobblestone");
    }

    // ------------------------------------------------------------------ base platform & terraces

    private void siteAndCourtyards(Plan p, Terrain t) {
        Shapes.levelGround(p, t, 0, 0, EX, EZ, TOP, JerusalemTemple::paving, b("stone"));

        // Elevated terrace for Court of Israel & Priests (z = 50..85, y = 1..3)
        for (int x = 6; x <= 67; x++) {
            for (int z = 50; z <= 85; z++) {
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, limestone(x, y, z));
                }
                p.set(x, 3, z, paving(x, z));
            }
        }
        // Court of Priests raised +1 step above Court of Israel (z = 50..77, y = 4)
        for (int x = 6; x <= 67; x++) {
            for (int z = 50; z <= 77; z++) {
                p.set(x, 4, z, paving(x, z));
            }
        }
        // Stair step along z = 78 separating Court of Israel and Court of Priests
        for (int x = 10; x <= 63; x++) {
            p.set(x, 4, 78, stairs(STONE_STAIRS, Face.NORTH, false));
        }

        // Elevated podium for the Main Sanctuary (z = 4..49, y = 1..6)
        for (int x = 10; x <= 63; x++) {
            for (int z = 4; z <= 49; z++) {
                for (int y = 1; y <= 6; y++) {
                    p.set(x, y, z, limestone(x, y, z));
                }
                p.set(x, 6, z, MARBLE);
            }
        }

        // Outer enclosure walls around the whole inner complex (x = 6..67, z = 4..147)
        for (int x = 6; x <= 67; x++) {
            wallColumn(p, x, 4, 14);
            wallColumn(p, x, 147, 12);
        }
        for (int z = 4; z <= 147; z++) {
            int h = z <= 85 ? 14 : 12;
            wallColumn(p, 6, z, h);
            wallColumn(p, 67, z, h);
        }
    }

    private static void wallColumn(Plan p, int x, int z, int topY) {
        for (int y = 1; y <= topY; y++) {
            p.set(x, y, z, limestone(x, y, z));
        }
        p.set(x, topY + 1, z, x % 2 == 0 || z % 2 == 0 ? MARBLE_CARVED : AIR);
    }

    // ------------------------------------------------------------------ Court of the Women (62 x 62 m)

    private void courtOfWomen(Plan p) {
        // Southern main entrance gate into the Court of the Women (x = CX-3..CX+3, z = 147, 7 m wide)
        for (int x = CX - 3; x <= CX + 3; x++) {
            for (int y = 1; y <= 7; y++) {
                p.set(x, y, 147, AIR);
            }
            p.set(x, 8, 147, GOLD);
            p.set(x, 9, 147, MARBLE_CARVED);
        }
        // Flanking marble columns at the outer gate
        for (int gx : new int[]{CX - 4, CX + 4}) {
            for (int y = 1; y <= 10; y++) {
                p.set(gx, y, 148, PILLAR);
            }
            p.set(gx, 11, 148, GOLD);
        }

        // Four corner chambers (10 × 10 m each) in the Court of the Women
        cornerChamber(p, 7, 136, 16, 146, Face.NORTH);   // SW: Oil chamber
        cornerChamber(p, 57, 136, 66, 146, Face.NORTH);  // SE: Nazirite chamber
        cornerChamber(p, 7, 89, 16, 99, Face.SOUTH);     // NW: Lepers chamber
        cornerChamber(p, 57, 89, 66, 99, Face.SOUTH);    // NE: Wood chamber

        // Side colonnades / balconies along west and east walls of the Court of the Women
        for (int z = 102; z <= 133; z += 4) {
            colonnadePillar(p, 10, z, 6);
            colonnadePillar(p, 63, z, 6);
        }
        p.fill(7, 7, 100, 10, 7, 135, MARBLE_BLOCK);
        p.fill(63, 7, 100, 66, 7, 135, MARBLE_BLOCK);
        p.fill(10, 8, 100, 10, 8, 135, STONE_WALL);
        p.fill(63, 8, 100, 63, 8, 135, STONE_WALL);
    }

    private static void cornerChamber(Plan p, int x1, int z1, int x2, int z2, Face doorSide) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = (x == x1 || x == x2 || z == z1 || z == z2);
                if (edge) {
                    for (int y = 1; y <= 6; y++) {
                        p.set(x, y, z, limestone(x, y, z));
                    }
                } else {
                    for (int y = 1; y <= 5; y++) {
                        p.set(x, y, z, AIR);
                    }
                    p.set(x, 6, z, MARBLE);
                }
            }
        }
        // Wide 4-block open doorway into the chamber so characters can walk freely
        int midX = (x1 + x2) / 2;
        int doorZ = (doorSide == Face.NORTH) ? z1 : z2;
        for (int x = midX - 1; x <= midX + 2; x++) {
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, doorZ, AIR);
            }
        }
        p.set(midX, 5, (z1 + z2) / 2, lantern(true));
    }

    private static void colonnadePillar(Plan p, int x, int z, int topY) {
        p.set(x, 1, z, MARBLE_CARVED);
        for (int y = 2; y <= topY; y++) {
            p.set(x, y, z, PILLAR);
        }
    }

    // ------------------------------------------------------------------ Nicanor Gate & 15 steps

    private void nicanorStepsAndGate(Plan p) {
        // Partition wall between Court of Women and Court of Israel at z = 86
        for (int x = 7; x <= 66; x++) {
            wallColumn(p, x, 86, 13);
        }

        // Wide monumental steps (12 blocks wide, rising y = 1..3) up to the Gate of Nicanor
        for (int x = CX - 6; x <= CX + 5; x++) {
            for (int s = 0; s < 3; s++) {
                int z = 89 - s;
                int y = 1 + s;
                for (int yy = 1; yy < y; yy++) {
                    p.set(x, yy, z, MARBLE);
                }
                p.set(x, y, z, stairs(STONE_STAIRS, Face.NORTH, false));
            }
        }

        // Corinthian Bronze Gate of Nicanor (6 m wide, 8 m tall)
        for (int x = CX - 3; x <= CX + 2; x++) {
            for (int y = 4; y <= 10; y++) {
                p.set(x, y, 86, AIR);
            }
            p.set(x, 11, 86, BRONZE);
            p.set(x, 12, 86, GOLD);
        }
        // Bronze gate leaves slightly ajar on left & right jambs
        for (int y = 4; y <= 9; y++) {
            p.set(CX - 3, y, 86, BRONZE_CUT);
            p.set(CX + 2, y, 86, BRONZE_CUT);
        }
        p.set(CX - 4, 4, 87, lantern(false));
        p.set(CX + 3, 4, 87, lantern(false));
    }

    // ------------------------------------------------------------------ Court of Priests & Great Altar

    private void courtOfPriestsAndAltar(Plan p) {
        // Great Altar of Burnt Offering (Mizbeach): 14 × 14 m base (x = 29..42, z = 58..71) of unhewn stone
        for (int x = 29; x <= 42; x++) {
            for (int z = 58; z <= 71; z++) {
                p.set(x, 5, z, unhewnStone(x, 5, z));
                if (x >= 30 && x <= 41 && z >= 59 && z <= 70) {
                    p.set(x, 6, z, unhewnStone(x, 6, z));
                    p.set(x, 7, z, unhewnStone(x, 7, z));
                }
                if (x >= 31 && x <= 40 && z >= 60 && z <= 69) {
                    p.set(x, 8, z, unhewnStone(x, 8, z));
                }
            }
        }
        // Four altar horns at corners (y = 9)
        for (int hx : new int[]{31, 40}) {
            for (int hz : new int[]{60, 69}) {
                p.set(hx, 9, hz, unhewnStone(hx, 9, hz));
            }
        }
        // Perpetual fire on top of the altar (y = 9)
        p.fill(34, 8, 63, 37, 8, 66, b("netherrack"));
        p.fill(34, 9, 63, 37, 9, 66, b("fire"));

        // Wide 4-block ramp (Kevesh) on the south side of the altar (x = 34..37, z = 70..76)
        for (int x = 34; x <= 37; x++) {
            for (int i = 0; i <= 4; i++) {
                int z = 75 - i;
                int y = 5 + (i * 3) / 4;
                for (int yy = 5; yy <= y; yy++) {
                    p.set(x, yy, z, unhewnStone(x, yy, z));
                }
            }
        }

        // Bronze Laver (Kiyor) to the southwest of the altar (x = 21..24, z = 63..66)
        p.fill(21, 5, 63, 24, 5, 66, BRONZE);
        p.fill(21, 6, 63, 24, 6, 66, BRONZE_CUT);
        p.fill(22, 6, 64, 23, 6, 65, WATER);
    }

    // ------------------------------------------------------------------ 12 Temple steps

    private void templeSteps(Plan p) {
        // 12-block wide grand staircase from Court of Priests (y = 4) to Sanctuary Porch (y = 6), flush at z = 50
        for (int x = CX - 6; x <= CX + 5; x++) {
            for (int s = 0; s < 2; s++) {
                int z = 51 - s;
                int y = 5 + s;
                for (int yy = 5; yy < y; yy++) {
                    p.set(x, yy, z, MARBLE);
                }
                p.set(x, y, z, stairs(STONE_STAIRS, Face.NORTH, false));
            }
        }
    }

    // ------------------------------------------------------------------ Main Sanctuary (Heichal)

    /**
     * Monumental Ulam facade (48 × 48 m: x = 13..60, y = 7..54) and 3-story side chambers (ta'im)
     * flanking the 20 m Hekhal and 10 m Holy of Holies.
     */
    private void sanctuaryBuilding(Plan p) {
        // 1. Monumental Ulam (Porch) facade: 48 m wide (x = 13..60), 6 m deep (z = 41..46), 48 m tall (y = 7..54)
        for (int x = 13; x <= 60; x++) {
            for (int z = 41; z <= 46; z++) {
                boolean isOuter = (x == 13 || x == 60 || z == 41 || z == 46);
                for (int y = 7; y <= 54; y++) {
                    if (isOuter || y >= 28) {
                        p.set(x, y, z, limestone(x, y, z));
                    } else {
                        p.set(x, y, z, AIR);
                    }
                }
            }
        }
        // Golden cornice & spikes along top of the Ulam facade (y = 54..56)
        p.fill(13, 54, 46, 60, 54, 46, GOLD);
        for (int x = 13; x <= 60; x += 2) {
            p.set(x, 55, 46, GOLD_RAW);
            p.set(x, 56, 46, b("lightning_rod"));
        }

        // Grand open Ulam portal (10 m wide: x = 32..41, 20 m tall: y = 7..26)
        for (int x = 32; x <= 41; x++) {
            for (int z = 41; z <= 46; z++) {
                for (int y = 7; y <= 26; y++) {
                    p.set(x, y, z, AIR);
                }
            }
            // Golden beams and famous golden vine ornament above the portal
            p.set(x, 27, 46, GOLD);
            p.set(x, 28, 46, GOLD_RAW);
            p.set(x, 29, 46, GOLD);
        }
        // Two monumental golden-crowned columns flanking the Ulam portal (Jachin & Boaz heritage)
        for (int cx : new int[]{30, 43}) {
            p.fill(cx, 7, 47, cx, 8, 47, MARBLE_CARVED);
            for (int y = 9; y <= 26; y++) {
                p.set(cx, y, 47, PILLAR);
            }
            p.set(cx, 27, 47, GOLD);
            p.set(cx, 28, 47, GOLD_RAW);
        }

        // 2. Main Sanctuary body behind Ulam (z = 8..40): central Hekhal + Holy of Holies + side chambers
        // Outer width x = 25..48 (24 m wide including side chambers), height y = 7..46
        for (int x = 25; x <= 48; x++) {
            for (int z = 8; z <= 40; z++) {
                boolean isOuterWall = (x <= 26 || x >= 47 || z <= 9);
                boolean isInnerSanctuaryWall = (x == 31 || x == 42 || z == 40);
                for (int y = 7; y <= 46; y++) {
                    if (isOuterWall || isInnerSanctuaryWall || y >= 27) {
                        p.set(x, y, z, limestone(x, y, z));
                    } else {
                        p.set(x, y, z, AIR);
                    }
                }
            }
        }
        // Golden trim along sanctuary roof & golden entrance door from Ulam into Hekhal (z = 40, x = 35..38)
        p.fill(25, 46, 8, 48, 46, 40, GOLD);
        for (int x = 35; x <= 38; x++) {
            for (int y = 7; y <= 14; y++) {
                p.set(x, y, 40, AIR);
            }
            p.set(x, 15, 40, GOLD);
        }

        // Side chambers (Ta'im) on west (x = 27..30) and east (x = 43..46), 3 floors high (y = 7..25)
        for (int floorY : new int[]{13, 19, 25}) {
            p.fill(27, floorY, 10, 30, floorY, 39, MARBLE_BLOCK);
            p.fill(43, floorY, 10, 46, floorY, 39, MARBLE_BLOCK);
        }
        // Clear Ulam back wall (z = 41) and place framed 2x2 double doors (z = 40) into west and east side chambers
        for (int doorX : new int[]{28, 44}) {
            for (int x = doorX; x <= doorX + 1; x++) {
                for (int y = 7; y <= 10; y++) {
                    p.set(x, y, 41, AIR);
                }
                p.set(x, 7, 40, AIR);
                p.set(x, 8, 40, AIR);
            }
            p.doubleDoor(doorX, 7, 40, DOOR, Face.NORTH);

            // 2-block-wide interior staircases climbing through floor 1 -> floor 2 (y = 7..13) and floor 2 -> floor 3 (y = 14..19)
            for (int x = doorX; x <= doorX + 1; x++) {
                for (int i = 0; i <= 6; i++) {
                    int z = 32 - i;
                    int y = 7 + i;
                    for (int yy = 7; yy < y; yy++) {
                        p.set(x, yy, z, limestone(x, yy, z));
                    }
                    p.set(x, y, z, stairs(STONE_STAIRS, Face.NORTH, false));
                    for (int yy = y + 1; yy <= Math.min(18, y + 3); yy++) {
                        p.set(x, yy, z, AIR);
                    }
                }
                for (int i = 0; i <= 5; i++) {
                    int z = 22 - i;
                    int y = 14 + i;
                    for (int yy = 14; yy < y; yy++) {
                        p.set(x, yy, z, limestone(x, yy, z));
                    }
                    p.set(x, y, z, stairs(STONE_STAIRS, Face.NORTH, false));
                    for (int yy = y + 1; yy <= Math.min(24, y + 3); yy++) {
                        p.set(x, yy, z, AIR);
                    }
                }
            }
        }
        // Hanging lanterns illuminating all three floors of the side chambers
        for (int lx : new int[]{29, 44}) {
            for (int lz : new int[]{13, 25, 36}) {
                for (int ly : new int[]{12, 18, 24}) {
                    p.set(lx, ly, lz, lantern(true));
                }
            }
        }
    }

    // ------------------------------------------------------------------ Hekhal & Holy of Holies interior

    private void sanctuaryInterior(Plan p) {
        // Hekhal interior: x = 32..41 (10 m wide), z = 21..39 (19 m long), y = 7..26
        // Golden wall paneling in Hekhal and Holy of Holies
        for (int z = 10; z <= 39; z++) {
            for (int y = 7; y <= 16; y++) {
                p.set(32, y, z, (y % 4 == 0) ? GOLD_RAW : GOLD);
                p.set(41, y, z, (y % 4 == 0) ? GOLD_RAW : GOLD);
            }
        }
        // Clear inner floor at y = 7
        p.fill(33, 6, 10, 40, 6, 39, MARBLE);

        // Golden Menorah (7-branched lampstand) on south/west side of Hekhal (x = 34, z = 27..31)
        p.set(34, 7, 29, GOLD);
        for (int y = 8; y <= 11; y++) {
            p.set(34, y, 29, GOLD);
        }
        for (int dz = -2; dz <= 2; dz++) {
            p.set(34, 10, 29 + dz, GOLD);
            p.set(34, 11, 29 + dz, GOLD);
            p.set(34, 12, 29 + dz, lantern(false));
        }

        // Table of Showbread on opposite side (x = 39, z = 28..30)
        p.fill(39, 7, 28, 39, 7, 30, GOLD);
        p.set(39, 8, 29, b("cake"));

        // Golden Altar of Incense centered before the Veil (x = 36..37, z = 23)
        p.set(36, 7, 23, GOLD);
        p.set(37, 7, 23, GOLD);
        p.set(36, 8, 23, b("campfire"));

        // Double Veil (Parokhet) at z = 19 and z = 20 separating Hekhal from Holy of Holies (2-block central passage)
        for (int z : new int[]{19, 20}) {
            for (int x = 32; x <= 41; x++) {
                for (int y = 7; y <= 16; y++) {
                    if (x >= 36 && x <= 37 && y <= 10) {
                        p.set(x, y, z, AIR);
                    } else {
                        String stripe = ((x + y) % 3 == 0) ? CURTAIN_BLUE : ((x + y) % 3 == 1) ? CURTAIN_PURPLE : CURTAIN_RED;
                        p.set(x, y, z, stripe);
                    }
                }
            }
        }

        // Holy of Holies (Kodesh ha-Kodashim): x = 32..41, z = 10..18, y = 7..16 (10 × 10 × 10 m cube)
        for (int x = 32; x <= 41; x++) {
            for (int y = 7; y <= 16; y++) {
                p.set(x, y, 10, GOLD);
            }
            for (int z = 10; z <= 18; z++) {
                p.set(x, 16, z, GOLD);
            }
        }
        // Foundation Stone (Even ha-Shetiyah) in the center of the Holy of Holies
        p.fill(36, 7, 13, 37, 7, 14, b("stone"));
        p.set(36, 8, 13, b("andesite"));
    }

    // ------------------------------------------------------------------ entrance plaza & plaques

    private static final List<String> PLAQUE_HE = List.of("בית המקדש", "ירושלים", "המקדש השני", "קנה מידה 1:1");
    private static final List<String> PLAQUE_RU = List.of("Иерусалимский", "Храм", "Второй Храм", "масштаб 1:1");

    private void approachAndTrees(Plan p) {
        // Lectern with history book in front of the main gate
        p.lectern(CX - 6, 1, EZ, Face.SOUTH);

        // Bilingual plaques flanking the outer gate
        Shapes.plaquePillar(p, CX - 5, EZ, MARBLE_CARVED, "dark_oak", Face.SOUTH, PLAQUE_HE);
        Shapes.plaquePillar(p, CX + 5, EZ, MARBLE_CARVED, "dark_oak", Face.SOUTH, PLAQUE_RU);

        // Olive / cypress trees along the outer plaza
        int[][] trees = {{2, 145}, {EX - 2, 145}, {2, 90}, {EX - 2, 90}};
        for (int[] tree : trees) {
            Shapes.cypress(p, tree[0], tree[1], leaves("spruce_leaves"));
        }
    }

    // ------------------------------------------------------------------ book

    @Override
    public Book book() {
        return new Book("Иерусалимский Храм", "Великие здания мира", List.of(
                "Второй Иерусалимский Храм\n(Храм Ирода в расцвете сил)\n\nМасштаб 1:1: один блок — один метр.",
                "Построен на Храмовой горе в Иерусалиме. После масштабной перестройки царя Ирода Великого "
                        + "(с 20 г. до н.э.) комплекс стал одним из самых величественных сооружений античного мира.",
                "Структура комплекса (по трактату Миддот и Иосифу Флавию):\n"
                        + "1. Двор Женщин (62 × 62 м) с четырьмя угловыми палатами и портиками.\n"
                        + "2. Бронзовые Ворота Никанора за 15 ступенями.",
                "3. Двор Израиля и Двор Священников с Великим Жертвенником всесожжения из необтёсанных камней "
                        + "(14 × 14 м) и Медным умывальником.\n"
                        + "4. 12 мраморных ступеней ко входу в Храм.",
                "5. Главное здание (Хейхал): величественный мраморно-золотой фасад Улам (48 × 48 м), "
                        + "Святилище с Менорой, Столом Хлебов Предложения и Алтарём воскурений, двойная завеса "
                        + "Парохет и Святая Святых (10 × 10 × 10 м) с Камнем Основания."));
    }
}

