package com.server.greatbuildings.cyprus;

import com.server.greatbuildings.buildings.Shapes;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Country;
import com.server.greatbuildings.model.Face;
import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.Terrain;

import java.util.List;

import static com.server.greatbuildings.model.States.b;
import static com.server.greatbuildings.model.States.candles;
import static com.server.greatbuildings.model.States.lantern;
import static com.server.greatbuildings.model.States.leaves;
import static com.server.greatbuildings.model.States.logY;
import static com.server.greatbuildings.model.States.slab;
import static com.server.greatbuildings.model.States.stairs;
import static com.server.greatbuildings.model.States.wallSign;

/**
 * Monumental bronze statue of Apollo Hylates ("Apollo of the Woodlands"), patron deity of
 * ancient Kourion, Cyprus, standing on a stepped marble pedestal inside a colonnaded sanctuary
 * courtyard with a sacrificial altar and sacred cypress grove.
 *
 * <p>Depicts Apollo in classical contrapposto holding a golden bow in his right hand and a golden
 * kithara (lyre) in his left, crowned with a golden laurel wreath and draped in a chlamys cloak.
 */
public final class ApolloKourion implements Building {

    static final int EX = 30, EZ = 30;
    private static final int TOP = 24;
    private static final int CX = 15, CZ = 15;

    private static final String AIR = Plan.AIR;

    // Sanctuary limestone & marble
    private static final String LIGHT = b("smooth_sandstone");
    private static final String LIGHT2 = b("cut_sandstone");
    private static final String WARM = b("sandstone");
    private static final String CARVED = b("chiseled_sandstone");
    private static final String WALL = b("sandstone_wall");
    private static final String STAIRS_STONE = "smooth_sandstone_stairs";
    private static final String SLAB_STONE = "smooth_sandstone_slab";

    private static final String MARBLE = b("calcite");
    private static final String QUARTZ = b("quartz_block");
    private static final String QUARTZ_CHISELED = b("chiseled_quartz_block");
    private static final String PILLAR = b("quartz_pillar") + "[axis=y]";
    private static final String QUARTZ_STAIRS = "quartz_stairs";
    private static final String QUARTZ_SLAB = "quartz_slab";

    // Classical polished bronze & gold attributes
    private static final String BRONZE = b("waxed_copper_block");
    private static final String BRONZE_CUT = b("waxed_cut_copper");
    private static final String BRONZE_STAIRS = "waxed_cut_copper_stairs";
    private static final String BRONZE_SLAB = "waxed_cut_copper_slab";
    private static final String CLOAK = b("terracotta");
    private static final String CLOAK_DARK = b("waxed_exposed_copper");
    private static final String GOLD = b("gold_block");
    private static final String BARS = b("iron_bars");
    private static final String CYPRESS = leaves("spruce_leaves");

    @Override
    public String id() {
        return "apollo";
    }

    @Override
    public Country country() {
        return Country.CYPRUS;
    }

    @Override
    public String title() {
        return "Статуя Аполлона Курионского";
    }

    @Override
    public String where() {
        return "Святилище Аполлона Гилата, Курион, Кипр";
    }

    @Override
    public int[] bounds() {
        return new int[]{-4, -15, -4, EX + 4, TOP, EZ + 2};
    }

    @Override
    public int[] anchor() {
        return new int[]{CX, EZ + 4};
    }

    @Override
    public void draw(Plan p, Terrain t) {
        sanctuaryCourt(p, t);
        colonnadeAndAltar(p);
        pedestal(p);
        statueOfApollo(p);
        sacredGroveAndSigns(p);
    }

    // ------------------------------------------------------------------ sanctuary courtyard

    private static String paving(int x, int z) {
        int r = Shapes.hash(x, 0, z) % 100;
        return b(r < 55 ? "smooth_sandstone" : r < 80 ? "cut_sandstone" : "polished_andesite");
    }

    private void sanctuaryCourt(Plan p, Terrain t) {
        for (int x = 0; x <= EX; x++) {
            for (int z = 0; z <= EZ; z++) {
                Shapes.clearColumn(p, x, z, TOP);
                Shapes.foundation(p, t, x, z, b("stone"));

                boolean insideTemenos = (x >= 2 && x <= EX - 2 && z >= 2 && z <= EZ - 2);
                if (insideTemenos) {
                    p.set(x, 0, z, paving(x, z));
                } else {
                    p.set(x, 0, z, b("grass_block"));
                }
            }
        }

        // Low temenos perimeter wall with wide southern entrance
        for (int x = 2; x <= EX - 2; x++) {
            p.set(x, 1, 2, LIGHT2);
            p.set(x, 2, 2, WALL);
            if (x < CX - 3 || x > CX + 3) {
                p.set(x, 1, EZ - 2, LIGHT2);
                p.set(x, 2, EZ - 2, WALL);
            }
        }
        for (int z = 2; z <= EZ - 2; z++) {
            p.set(2, 1, z, LIGHT2);
            p.set(2, 2, z, WALL);
            p.set(EX - 2, 1, z, LIGHT2);
            p.set(EX - 2, 2, z, WALL);
        }

        // Central marble processional path from southern gate to altar and pedestal
        for (int x = CX - 1; x <= CX + 1; x++) {
            for (int z = 19; z <= EZ; z++) {
                p.set(x, 0, z, MARBLE);
            }
        }
    }

    private void colonnadeAndAltar(Plan p) {
        // Semicircular / U-shaped sanctuary colonnade framing the statue on W, N, E
        int[] colX = {5, 9, 13, 17, 21, 25};
        for (int cx : colX) {
            column(p, cx, 5);
        }
        for (int cz : new int[]{9, 13, 17, 21}) {
            column(p, 5, cz);
            column(p, 25, cz);
        }

        // Architrave beams connecting the colonnade
        p.fill(5, 7, 5, 25, 7, 5, LIGHT2);
        p.fill(5, 8, 5, 25, 8, 5, slab(SLAB_STONE));
        p.fill(5, 7, 5, 5, 7, 21, LIGHT2);
        p.fill(5, 8, 5, 5, 8, 21, slab(SLAB_STONE));
        p.fill(25, 7, 5, 25, 7, 21, LIGHT2);
        p.fill(25, 8, 5, 25, 8, 21, slab(SLAB_STONE));

        // Sacrificial marble altar in front of the statue (z = 22..24)
        p.fill(CX - 1, 1, 22, CX + 1, 1, 24, QUARTZ);
        p.set(CX, 2, 23, QUARTZ_CHISELED);
        p.set(CX, 3, 23, b("campfire"));
        p.set(CX - 1, 2, 23, candles(3));
        p.set(CX + 1, 2, 23, candles(3));
    }

    private static void column(Plan p, int x, int z) {
        p.set(x, 1, z, CARVED);
        for (int y = 2; y <= 5; y++) {
            p.set(x, y, z, PILLAR);
        }
        p.set(x, 6, z, QUARTZ_CHISELED);
    }

    // ------------------------------------------------------------------ pedestal

    private void pedestal(Plan p) {
        // Stepped marble crepidoma (y = 1..2)
        p.fill(CX - 4, 1, CZ - 4, CX + 4, 1, CZ + 4, MARBLE);
        p.fill(CX - 3, 2, CZ - 3, CX + 3, 2, CZ + 3, QUARTZ);

        // Main carved marble plinth (y = 3..5)
        p.fill(CX - 2, 3, CZ - 2, CX + 2, 5, CZ + 2, QUARTZ);
        for (int x = CX - 2; x <= CX + 2; x++) {
            p.set(x, 5, CZ - 3, stairs(QUARTZ_STAIRS, Face.NORTH, true));
            p.set(x, 5, CZ + 3, stairs(QUARTZ_STAIRS, Face.SOUTH, true));
        }
        for (int z = CZ - 2; z <= CZ + 2; z++) {
            p.set(CX - 3, 5, z, stairs(QUARTZ_STAIRS, Face.WEST, true));
            p.set(CX + 3, 5, z, stairs(QUARTZ_STAIRS, Face.EAST, true));
        }
        // Decorative carved frieze & golden dedication plate on the south face
        p.set(CX - 1, 4, CZ + 2, QUARTZ_CHISELED);
        p.set(CX, 4, CZ + 2, GOLD);
        p.set(CX + 1, 4, CZ + 2, QUARTZ_CHISELED);
        p.set(CX, 6, CZ, MARBLE);
    }

    // ------------------------------------------------------------------ statue of Apollo (y = 6..21)

    /**
     * Classical bronze statue in contrapposto:
     * - Weight-bearing right leg (x = CX - 1, z = CZ), relaxed left leg stepped slightly back (x = CX + 1, z = CZ - 1).
     * - Athletic torso (y = 12..16), chlamys cloak draped over left shoulder and down the back.
     * - Right arm raised holding a golden bow; left arm cradling a golden kithara (lyre).
     * - Head (y = 18..20) facing south with bronze curls and a golden laurel wreath.
     */
    private void statueOfApollo(Plan p) {
        // Right leg (weight-bearing, straight, x = CX - 1)
        p.set(CX - 1, 6, CZ + 1, BRONZE_SLAB); // foot
        for (int y = 6; y <= 11; y++) {
            p.set(CX - 1, y, CZ, BRONZE);
        }

        // Left leg (relaxed, slightly back, x = CX + 1)
        p.set(CX + 1, 6, CZ, BRONZE_SLAB);
        p.set(CX + 1, 6, CZ - 1, BRONZE);
        p.set(CX + 1, 7, CZ - 1, BRONZE);
        p.set(CX + 1, 8, CZ - 1, BRONZE_CUT);
        p.set(CX + 1, 9, CZ, BRONZE);
        p.set(CX + 1, 10, CZ, BRONZE);
        p.set(CX + 1, 11, CZ, BRONZE);

        // Pelvis & hips (y = 11..12)
        p.fill(CX - 1, 11, CZ, CX + 1, 12, CZ, BRONZE);
        p.set(CX, 12, CZ + 1, BRONZE_CUT);

        // Athletic torso (y = 13..16)
        p.fill(CX - 1, 13, CZ - 1, CX + 1, 16, CZ + 1, BRONZE);
        // Sculpted chest & waist taper
        p.set(CX - 1, 13, CZ + 1, AIR);
        p.set(CX + 1, 13, CZ + 1, AIR);
        p.set(CX, 15, CZ + 1, BRONZE_CUT);
        p.set(CX - 1, 15, CZ + 1, BRONZE_CUT);
        p.set(CX + 1, 15, CZ + 1, BRONZE_CUT);

        // Shoulders (y = 16..17)
        p.fill(CX - 2, 16, CZ, CX + 2, 16, CZ, BRONZE);
        p.set(CX - 2, 17, CZ, BRONZE_SLAB);
        p.set(CX + 2, 17, CZ, BRONZE_SLAB);

        // Neck & head (y = 17..20)
        p.set(CX, 17, CZ, BRONZE);
        p.fill(CX - 1, 18, CZ - 1, CX + 1, 20, CZ + 1, BRONZE);
        // Facial features & curls
        p.set(CX, 18, CZ + 1, BRONZE_CUT);
        p.set(CX - 1, 18, CZ + 1, AIR);
        p.set(CX + 1, 18, CZ + 1, AIR);
        p.set(CX - 1, 20, CZ + 1, BRONZE_STAIRS);
        p.set(CX + 1, 20, CZ + 1, BRONZE_STAIRS);
        // Golden laurel wreath crowning the brow
        p.set(CX - 1, 20, CZ, GOLD);
        p.set(CX, 20, CZ + 1, GOLD);
        p.set(CX + 1, 20, CZ, GOLD);
        p.set(CX, 21, CZ, GOLD);

        // Chlamys cloak draped over left shoulder (+x side) and cascading down the back (-z)
        p.set(CX + 2, 17, CZ - 1, CLOAK);
        for (int y = 7; y <= 16; y++) {
            p.set(CX + 1, y, CZ - 2, (y % 2 == 0) ? CLOAK : CLOAK_DARK);
            p.set(CX + 2, y, CZ - 1, CLOAK);
        }
        p.fill(CX, 12, CZ - 2, CX + 1, 16, CZ - 2, CLOAK);
        p.set(CX, 6, CZ - 2, CLOAK_DARK);
        p.set(CX + 1, 6, CZ - 2, CLOAK);

        // Right arm (west side, x < CX) extended forward holding the golden bow
        p.set(CX - 3, 16, CZ, BRONZE);
        p.set(CX - 3, 15, CZ + 1, BRONZE);
        p.set(CX - 3, 15, CZ + 2, BRONZE_CUT);
        // Golden bow (arc at x = CX - 3, z = CZ + 3)
        p.set(CX - 3, 15, CZ + 3, GOLD);
        p.set(CX - 3, 16, CZ + 3, GOLD);
        p.set(CX - 3, 17, CZ + 2, GOLD);
        p.set(CX - 3, 14, CZ + 3, GOLD);
        p.set(CX - 3, 13, CZ + 2, GOLD);
        p.set(CX - 3, 14, CZ + 2, BARS);
        p.set(CX - 3, 16, CZ + 2, BARS);

        // Left arm (east side, x > CX) cradling the golden kithara / lyre
        p.set(CX + 3, 15, CZ, BRONZE);
        p.set(CX + 3, 14, CZ + 1, BRONZE);
        p.set(CX + 2, 14, CZ + 2, BRONZE_CUT);
        // Golden lyre (kithara) held against the left chest/arm
        p.set(CX + 3, 14, CZ + 2, GOLD);
        p.set(CX + 3, 15, CZ + 2, GOLD);
        p.set(CX + 2, 15, CZ + 2, BARS); // strings
        p.set(CX + 2, 16, CZ + 2, GOLD);
        p.set(CX + 3, 16, CZ + 2, GOLD);
    }

    // ------------------------------------------------------------------ grove, plaques & lectern

    private static final List<String> PLAQUE_EL = List.of("Απόλλων Υλάτης", "Κούριον", "Ιερόν", "Κύπρος");
    private static final List<String> PLAQUE_RU = List.of("Аполлон Гилат", "Святилище", "Курион, Кипр", "масштаб 1:1");

    private void sacredGroveAndSigns(Plan p) {
        // Lectern with book at the entrance
        p.set(CX - 3, 1, EZ - 1, b("lectern") + "[facing=south]");
        p.lecterns.add(new int[]{CX - 3, 1, EZ - 1});

        // Bilingual plaques flanking the pedestal approach
        Shapes.plaquePillar(p, CX - 3, 25, CARVED, "dark_oak", Face.SOUTH, PLAQUE_EL);
        Shapes.plaquePillar(p, CX + 3, 25, CARVED, "dark_oak", Face.SOUTH, PLAQUE_RU);

        // Sanctuary lanterns
        p.set(CX - 3, 3, 25, lantern(false));
        p.set(CX + 3, 3, 25, lantern(false));

        // Sacred grove of cypresses ("Hylates" = of the Woodlands)
        int[][] trees = {
                {4, 26}, {EX - 4, 26},
                {8, 25}, {EX - 8, 25},
                {3, 4}, {EX - 3, 4}
        };
        for (int[] tree : trees) {
            Shapes.cypress(p, tree[0], tree[1], CYPRESS);
        }
    }

    // ------------------------------------------------------------------ book

    @Override
    public Book book() {
        return new Book("Аполлон Курионский", "Великие здания мира", List.of(
                "Статуя Аполлона Гилата\n(Απόλλων Υλάτης)\n\nСвятилище Аполлона Гилата близ древнего Куриона, Кипр.",
                "Аполлон Гилат — «Покровитель лесов» — был главным божеством и хранителем города Куриона с VIII века "
                        + "до н.э. до IV века н.э. К его святилищу, окружённому священной кипарисовой рощей, стекались "
                        + "паломники со всего Средиземноморья.",
                "В античности здесь стояли сотни вотивных статуй из известняка, мрамора и бронзы, изображавших "
                        + "самого бога и жрецов. Монументальная культовая статуя Аполлона воплощала его главные ипостаси: "
                        + "бога-лучника и покровителя музыки и гармонии.",
                "В этой реконструкции Аполлон предстаёт в классическом античном контрапосте: с золотым луком в "
                        + "правой руке, золотой кифарой (лирой) в левой и золотым лавровым венком на голове, на высоком "
                        + "мраморном постаменте перед жертвенным алтарём."));
    }
}

