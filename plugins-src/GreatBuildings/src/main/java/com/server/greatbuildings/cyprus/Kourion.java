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
import static com.server.greatbuildings.model.States.slab;
import static com.server.greatbuildings.model.States.stairs;

/**
 * Greco-Roman Theatre of Kourion (Curium), Cyprus, overlooking Episkopi Bay.
 * Originally built in the late 2nd century BC (Hellenistic) and expanded in the 2nd century AD
 * (Trajanic Roman period) to a diameter of ~62 m, seating ~3,500 spectators. Scale 1:1 (1 block = 1 metre).
 *
 * <p>Orientation: the semicircular cavea (auditorium) rises to the north (-z), embracing a 17 m
 * diameter semicircular orchestra, while the stage (proskenion), marble-columned scaenae frons
 * wings, and twin vaulted parodoi (side entrances) face the player arriving from the south (+z).
 */
public final class Kourion implements Building {

    static final int EX = 72, EZ = 52;
    private static final int TOP = 22;

    /** Centre of the semicircular orchestra. */
    private static final int CX = 36;
    private static final int CZ = 36;

    /** Orchestra diameter ~17 m (radius 8.5 -> 9), total cavea diameter ~62 m (radius 31). */
    private static final int R_ORCH = 9;
    private static final int R_DIAZOMA_IN = 20;
    private static final int R_DIAZOMA_OUT = 22;
    private static final int R_CAVEA_OUT = 29;
    private static final int R_WALL_OUT = 31;

    private static final String AIR = Plan.AIR;

    // Warm Cypriot limestone & marble palette
    private static final String LIGHT = b("smooth_sandstone");
    private static final String LIGHT2 = b("cut_sandstone");
    private static final String WARM = b("sandstone");
    private static final String DARK = b("terracotta");
    private static final String CARVED = b("chiseled_sandstone");
    private static final String WALL = b("sandstone_wall");
    private static final String STAIRS_STONE = "smooth_sandstone_stairs";
    private static final String SLAB_STONE = "smooth_sandstone_slab";
    private static final String STAIRS_BRICK = "stone_brick_stairs";

    private static final String MARBLE = b("calcite");
    private static final String PILLAR = b("quartz_pillar") + "[axis=y]";
    private static final String MARBLE_BASE = b("chiseled_quartz_block");
    private static final String CYPRESS = leaves("spruce_leaves");

    @Override
    public String id() {
        return "kourion";
    }

    @Override
    public Country country() {
        return Country.CYPRUS;
    }

    @Override
    public String title() {
        return "Амфитеатр Куриона";
    }

    @Override
    public String where() {
        return "Курион, Кипр, близ Лимасола";
    }

    @Override
    public int[] bounds() {
        return new int[]{-4, -15, -4, EX + 4, TOP, EZ + 2};
    }

    @Override
    public int[] anchor() {
        return new int[]{CX, EZ + 5};
    }

    @Override
    public void draw(Plan p, Terrain t) {
        site(p, t);
        orchestra(p);
        cavea(p, t);
        stageAndParodoi(p, t);
        approachAndTrees(p);
    }

    // ------------------------------------------------------------------ materials

    private static String limestone(int x, int y, int z) {
        int r = Shapes.hash(x, y, z) % 100;
        return r < 55 ? LIGHT : r < 78 ? LIGHT2 : r < 92 ? WARM : DARK;
    }

    private static String paving(int x, int z) {
        int r = Shapes.hash(x, 0, z) % 100;
        return b(r < 55 ? "smooth_sandstone" : r < 75 ? "cut_sandstone" : r < 90 ? "polished_andesite" : "stone_bricks");
    }

    // ------------------------------------------------------------------ site

    private void site(Plan p, Terrain t) {
        Shapes.levelGround(p, t, 0, 0, EX, EZ, TOP, b("grass_block"), b("dirt"));
        // Paved southern approach plaza in front of the stage and parodoi
        for (int x = CX - 20; x <= CX + 20; x++) {
            for (int z = CZ + 8; z <= EZ; z++) {
                p.set(x, 0, z, paving(x, z));
            }
        }
    }

    // ------------------------------------------------------------------ orchestra

    /** Semicircular 17 m orchestra paved with limestone and a marble center mosaic. */
    private void orchestra(Plan p) {
        for (int x = CX - R_ORCH; x <= CX + R_ORCH; x++) {
            for (int z = CZ - R_ORCH; z <= CZ + 1; z++) {
                double dist = Math.hypot(x - CX, z - CZ);
                if (dist <= R_ORCH) {
                    if (dist <= 2.2) {
                        p.set(x, 0, z, MARBLE);
                    } else if (Math.abs(dist - 5.5) < 0.65) {
                        p.set(x, 0, z, CARVED);
                    } else {
                        p.set(x, 0, z, paving(x, z));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ cavea (62 m diameter bowl)

    /**
     * Semicircular seating bowl (cavea) rising to the north:
     * - Lower tier (r = 9..19) and upper tier (r = 23..29) divided by the diazoma walkway (r = 20..22).
     * - Radial stairways (kerkides) every 30 degrees.
     * - Thick Roman outer perimeter wall (r = 30..31) with buttresses and upper colonnade.
     */
    private void cavea(Plan p, Terrain t) {
        for (int x = CX - R_WALL_OUT - 1; x <= CX + R_WALL_OUT + 1; x++) {
            for (int z = CZ - R_WALL_OUT - 1; z <= CZ; z++) {
                int dx = x - CX;
                int dz = z - CZ;
                double dist = Math.hypot(dx, dz);
                if (dist < R_ORCH || dist > R_WALL_OUT) {
                    continue;
                }

                Shapes.foundation(p, t, x, z, b("stone"));

                // Angle in degrees from West (180) through North (90) to East (0)
                double angleDeg = Math.toDegrees(Math.atan2(-dz, dx));
                boolean isStairway = isRadialStairway(dist, angleDeg);

                if (dist >= R_CAVEA_OUT) {
                    // Outer Roman perimeter wall + upper portico gallery
                    int wallTop = 16;
                    for (int y = 0; y <= wallTop; y++) {
                        p.set(x, y, z, limestone(x, y, z));
                    }
                    // Battlement / cornice cap
                    p.set(x, wallTop + 1, z, dist >= R_WALL_OUT - 1 ? WALL : slab(SLAB_STONE));
                    // Periodic arched niches / windows along the outer upper gallery
                    int sector = (int) Math.round(angleDeg / 15.0);
                    if (Math.abs(angleDeg - sector * 15.0) < 2.2 && dist >= R_CAVEA_OUT + 0.5 && dist <= R_WALL_OUT - 0.5) {
                        p.set(x, wallTop - 1, z, AIR);
                        p.set(x, wallTop - 2, z, AIR);
                    }
                } else if (dist >= R_DIAZOMA_IN && dist <= R_DIAZOMA_OUT) {
                    // Diazoma horizontal walkway
                    int diazomaY = seatHeight(R_DIAZOMA_IN);
                    for (int y = 0; y <= diazomaY; y++) {
                        p.set(x, y, z, limestone(x, y, z));
                    }
                    p.set(x, diazomaY, z, paving(x, z));
                    if (dist < R_DIAZOMA_IN + 0.9 && !isStairway) {
                        p.set(x, diazomaY + 1, z, WALL);
                    }
                } else {
                    // Seating tiers (lower: r = 9..19, upper: r = 23..29)
                    int y = seatHeight(dist);
                    for (int yy = 0; yy < y; yy++) {
                        p.set(x, yy, z, limestone(x, yy, z));
                    }
                    if (isStairway) {
                        p.set(x, y, z, stairs(STAIRS_BRICK, facingAwayFromCenter(dx, dz), false));
                    } else {
                        p.set(x, y, z, stairs(STAIRS_STONE, facingAwayFromCenter(dx, dz), false));
                    }
                }
            }
        }

        // Retaining analemma walls along the southern edge of the cavea (z = CZ)
        for (int side : new int[]{-1, 1}) {
            for (int r = R_ORCH; r <= R_WALL_OUT; r++) {
                int x = CX + side * r;
                int topY = (r >= R_CAVEA_OUT) ? 16 : seatHeight(r) + 1;
                for (int y = 0; y <= topY; y++) {
                    p.set(x, y, CZ, limestone(x, y, CZ));
                }
            }
        }
    }

    private static int seatHeight(double dist) {
        if (dist <= R_DIAZOMA_IN) {
            return 1 + (int) Math.round((dist - R_ORCH) * 0.68);
        }
        return seatHeight(R_DIAZOMA_IN) + 1 + (int) Math.floor((dist - R_DIAZOMA_OUT) * 0.68);
    }

    private static boolean isRadialStairway(double dist, double angleDeg) {
        for (int a = 30; a <= 150; a += 30) {
            if (dist * Math.toRadians(Math.abs(angleDeg - a)) <= 1.05) {
                return true;
            }
        }
        return false;
    }

    private static Face facingAwayFromCenter(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Face.EAST : Face.WEST;
        }
        return dz < 0 ? Face.NORTH : Face.SOUTH;
    }

    // ------------------------------------------------------------------ stage, scaenae frons & parodoi

    /**
     * Stage platform (proskenion) at z = CZ+2..CZ+7, twin vaulted parodoi entrances on the flanks,
     * and reconstructed marble columns of the scaenae frons framing the view into the cavea.
     */
    private void stageAndParodoi(Plan p, Terrain t) {
        int stageZ1 = CZ + 2; // 38
        int stageZ2 = CZ + 7; // 43
        int stageX1 = CX - 13; // 23
        int stageX2 = CX + 13; // 49

        // Raised stone stage platform (y = 1..2)
        for (int x = stageX1; x <= stageX2; x++) {
            for (int z = stageZ1; z <= stageZ2; z++) {
                Shapes.foundation(p, t, x, z, b("stone"));
                p.set(x, 0, z, limestone(x, 0, z));
                p.set(x, 1, z, limestone(x, 1, z));
                p.set(x, 2, z, (z == stageZ1 || x == stageX1 || x == stageX2) ? MARBLE : LIGHT2);
            }
        }

        // Stairs from the orchestra and from the south plaza onto the stage
        for (int x = CX - 2; x <= CX + 2; x++) {
            p.set(x, 1, stageZ1 - 1, stairs(STAIRS_STONE, Face.SOUTH, false));
            p.set(x, 2, stageZ1, stairs(STAIRS_STONE, Face.SOUTH, false));
            p.set(x, 1, stageZ2 + 1, stairs(STAIRS_STONE, Face.NORTH, false));
            p.set(x, 2, stageZ2, stairs(STAIRS_STONE, Face.NORTH, false));
            p.set(x, 2, stageZ2 + 1, AIR);
        }

        // Scaenae frons colonnade wings (leaving central 9 m low so the cavea remains visible from the south)
        int[] colXs = {CX - 11, CX - 8, CX - 5, CX + 5, CX + 8, CX + 11};
        for (int cx : colXs) {
            p.set(cx, 3, stageZ2 - 1, MARBLE_BASE);
            for (int y = 4; y <= 7; y++) {
                p.set(cx, y, stageZ2 - 1, PILLAR);
            }
            p.set(cx, 8, stageZ2 - 1, MARBLE);
        }
        // Architrave / entablature beams over the left and right colonnade wings
        p.fill(CX - 12, 9, stageZ2 - 1, CX - 4, 9, stageZ2 - 1, LIGHT);
        p.fill(CX + 4, 9, stageZ2 - 1, CX + 12, 9, stageZ2 - 1, LIGHT);
        p.fill(CX - 12, 10, stageZ2 - 1, CX - 4, 10, stageZ2 - 1, slab(SLAB_STONE));
        p.fill(CX + 4, 10, stageZ2 - 1, CX + 12, 10, stageZ2 - 1, slab(SLAB_STONE));

        // Twin vaulted parodoi (side entrance passages into the orchestra)
        for (int side : new int[]{-1, 1}) {
            int px1 = side < 0 ? CX - 20 : CX + 15;
            int px2 = side < 0 ? CX - 15 : CX + 20;
            int doorCenterX = (px1 + px2) / 2;
            for (int x = px1; x <= px2; x++) {
                for (int z = CZ + 1; z <= stageZ2; z++) {
                    Shapes.foundation(p, t, x, z, b("stone"));
                    p.set(x, 0, z, paving(x, z));
                    boolean isSideWall = (x == px1 || x == px2);
                    if (isSideWall) {
                        for (int y = 1; y <= 6; y++) {
                            p.set(x, y, z, limestone(x, y, z));
                        }
                    } else {
                        for (int y = 1; y <= 4; y++) {
                            p.set(x, y, z, AIR);
                        }
                        // Vaulted roof at y = 5..6
                        p.set(x, 5, z, (x == px1 + 1 || x == px2 - 1) ? limestone(x, 5, z) : AIR);
                        p.set(x, 6, z, limestone(x, 6, z));
                    }
                }
            }
            p.set(doorCenterX, 4, stageZ2, lantern(true));
        }
    }

    // ------------------------------------------------------------------ approach, signs, trees

    private static final List<String> PLAQUE_EL = List.of("Αρχαίο Θέατρο", "Κουρίου", "2ος αι. π.Χ.", "Κύπρος");
    private static final List<String> PLAQUE_RU = List.of("Амфитеатр", "Куриона", "II в. до н.э.", "масштаб 1:1");

    private void approachAndTrees(Plan p) {
        // Lectern with historical book in front of the stage approach
        p.lectern(CX - 4, 1, EZ - 1, Face.SOUTH);

        // Bilingual plaques on stone pedestals flanking the central approach
        Shapes.plaquePillar(p, CX - 6, stageZ2() + 2, CARVED, "dark_oak", Face.SOUTH, PLAQUE_EL);
        Shapes.plaquePillar(p, CX + 6, stageZ2() + 2, CARVED, "dark_oak", Face.SOUTH, PLAQUE_RU);

        // Mediterranean cypresses framing the hillside theatre
        int[][] cypresses = {
                {4, 10}, {EX - 4, 10},
                {3, 28}, {EX - 3, 28},
                {8, EZ - 2}, {EX - 8, EZ - 2}
        };
        for (int[] c : cypresses) {
            Shapes.cypress(p, c[0], c[1], CYPRESS);
        }
    }

    private static int stageZ2() {
        return CZ + 7;
    }

    // ------------------------------------------------------------------ book

    @Override
    public Book book() {
        return new Book("Амфитеатр Куриона", "Великие здания мира", List.of(
                "Амфитеатр Куриона\n(Αρχαίο Θέατρο Κουρίου)\n\nКипр, древний город Курион на высоком утёсе над "
                        + "заливом Эпископи.\n\nПостроен в масштабе 1:1: один блок — один метр (диаметр 62 м).",
                "Театр был заложен в конце II века до н.э. в эллинистическую эпоху и первоначально вмещал около "
                        + "1500–2000 зрителей. Во II веке н.э., при римском императоре Траяне, его значительно "
                        + "расширили: орхестру превратили в полукруг диаметром 17 м, а зрительный зал увеличили до 62 м.",
                "В эпоху расцвета театр вмещал до 3500 зрителей. Полукруглая кавея была разделена горизонтальным "
                        + "проходом (диазомой) и радиальными лестницами на клиновидные сектора, а с флангов в орхестру "
                        + "вели сводчатые проходы — пародосы.",
                "В III веке н.э. римляне временно приспособили театр под арену для гладиаторских боёв и травли "
                        + "зверей, убрав нижние ряды сидений. В IV веке он снова стал театром, но был разрушен "
                        + "сильнейшими землетрясениями около 365 года н.э.",
                "В 1961 году Департамент древностей Кипра восстановил нижний ярус из 19 рядов известняковых "
                        + "сидений. Сегодня здесь снова проходят постановки древнегреческих трагедий и музыкальные "
                        + "фестивали под открытым небом."));
    }
}

