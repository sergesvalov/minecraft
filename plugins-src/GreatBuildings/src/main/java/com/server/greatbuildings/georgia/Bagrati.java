package com.server.greatbuildings.georgia;

import com.server.greatbuildings.Building;
import com.server.greatbuildings.Country;
import com.server.greatbuildings.Face;
import com.server.greatbuildings.Plan;
import com.server.greatbuildings.Terrain;

import java.util.List;

import static com.server.greatbuildings.States.b;
import static com.server.greatbuildings.States.candles;
import static com.server.greatbuildings.States.door;
import static com.server.greatbuildings.States.lantern;
import static com.server.greatbuildings.States.leaves;
import static com.server.greatbuildings.States.logY;
import static com.server.greatbuildings.States.slab;
import static com.server.greatbuildings.States.stairs;
import static com.server.greatbuildings.States.wallSign;

/**
 * Bagrati Cathedral (Cathedral of the Dormition) in Kutaisi, Georgia, atop Ukimerioni Hill.
 * Consecrated in 1003 by King Bagrat III; crowned king David the Builder in 1089.
 *
 * <p>Scale 1:1 (1 block = 1 metre), altar facing east, southern approach facing the player.
 * Built as a classical medieval Georgian triconch (three semicircular conchs: east altar,
 * north and south transept arms) on four free-standing cruciform piers, with an elongated western arm,
 * choir gallery, the famous 1030s southern open portico, western portico, 16-faceted cylindrical drum,
 * and conical copper patina (oxidized copper) dome.
 *
 * <p>Dimensions and archaeological data from the Orthodox Encyclopedia ("Баграта храм", pravenc.ru/text/77326.html)
 * and Georgian architectural surveys: ~44 m long, ~34 m wide across conchs (with south portico ~38 m),
 * dome height ~35 m, cross up to ~38 m.
 */
public final class Bagrati implements Building {

    static final int EX = 66, EZ = 62;
    private static final int TOP = 40;
    private static final int CU = 18, CV = 30;
    /** Crossing / dome centre relative to CU. */
    private static final int DOME_U = 24;

    private static final String AIR = Plan.AIR;
    private static final String LIGHT = b("smooth_sandstone");
    private static final String LIGHT2 = b("cut_sandstone");
    private static final String WARM = b("sandstone");
    private static final String DARK = b("terracotta");
    private static final String PLASTER = b("calcite");
    private static final String CARVED = b("chiseled_sandstone");
    private static final String WALL = b("sandstone_wall");
    private static final String GLASS = b("glass");
    private static final String TRIM = "smooth_sandstone_stairs";

    // Patina green / oxidized copper roof — iconic visual signature of restored Bagrati
    private static final String COPPER_BLOCK = b("oxidized_cut_copper");
    private static final String COPPER_ROOF_STAIRS = "oxidized_copper_stairs";
    private static final String COPPER_ROOF_SLAB = "oxidized_copper_slab";

    private static final String DOOR = "dark_oak_door";
    private static final String CHAIN = b("iron_chain") + "[axis=y]";
    private static final String CYPRESS = leaves("spruce_leaves");

    @Override
    public String id() {
        return "bagrati";
    }

    @Override
    public Country country() {
        return Country.GEORGIA;
    }

    @Override
    public String title() {
        return "Храм Баграта";
    }

    @Override
    public String where() {
        return "Кутаиси, Грузия, холм Укимериони";
    }

    @Override
    public int[] bounds() {
        return new int[]{-4, -25, -4, EX + 4, TOP, EZ + 2};
    }

    @Override
    public int[] anchor() {
        // Player stands on the southern plaza, looking north at the south portico and cathedral
        return new int[]{CU + DOME_U, EZ + 5};
    }

    @Override
    public void draw(Plan p, Terrain t) {
        site(p, t);
        fortressRuins(p, t);
        cathedral(p, t);
        southPortico(p, t);
        westPortico(p, t);
        yard(p);
    }

    // ------------------------------------------------------------------ materials

    static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Natural masonry variation of golden/ochre Kutaisi sandstone. */
    private static String ashlar(int x, int y, int z) {
        int r = hash(x, y, z) % 100;
        return r < 60 ? LIGHT : r < 82 ? LIGHT2 : r < 94 ? WARM : DARK;
    }

    private static String rubble(int x, int y, int z) {
        int r = hash(x, y, z) % 100;
        return b(r < 35 ? "cobblestone" : r < 55 ? "stone" : r < 75 ? "stone_bricks"
                : r < 90 ? "mossy_cobblestone" : "andesite");
    }

    private static String paving(int x, int z) {
        int r = hash(x, 0, z) % 100;
        return b(r < 55 ? "stone_bricks" : r < 75 ? "polished_andesite" : r < 90 ? "cracked_stone_bricks" : "cobblestone");
    }

    private static String floor(int x, int z) {
        return ((x + z) & 1) == 0 ? LIGHT : LIGHT2;
    }

    private static void foundation(Plan p, Terrain t, int x, int z, String state) {
        int ground = t.groundY(x, z);
        for (int y = ground == Terrain.NONE ? p.minY : Math.max(p.minY, ground + 1); y < 0; y++) {
            p.set(x, y, z, state);
        }
    }

    private static void foundationRubble(Plan p, Terrain t, int x, int z) {
        int ground = t.groundY(x, z);
        for (int y = ground == Terrain.NONE ? p.minY : Math.max(p.minY, ground + 1); y < 0; y++) {
            p.set(x, y, z, rubble(x, y, z));
        }
    }

    private static void clearColumn(Plan p, int x, int z) {
        for (int y = 1; y <= TOP; y++) {
            p.set(x, y, z, AIR);
        }
    }

    private static void setRel(Plan p, int u, int y, int v, String state) {
        p.set(CU + u, y, CV + v, state);
    }

    // ------------------------------------------------------------------ site & ruins

    /** Prepares the Ukimerioni hill plateau: grass at y = 0, foundations down to terrain. */
    private void site(Plan p, Terrain t) {
        for (int x = 0; x <= EX; x++) {
            for (int z = 0; z <= EZ; z++) {
                clearColumn(p, x, z);
                p.set(x, 0, z, b("grass_block"));
                foundation(p, t, x, z, b("dirt"));
            }
        }

        // Stone walkways: around the cathedral, to the south plaza, and west entrance
        for (int x = CU - 8; x <= CU + 42; x++) {
            for (int z = CV - 18; z <= CV + 23; z++) {
                // Paved perimeter path
                int u = x - CU;
                int v = z - CV;
                if (!inBody(u, v) && (Math.abs(v) == 16 || Math.abs(v) == 17 || u == -2 || u == -3 || u == 40 || u == 41)) {
                    p.set(x, 0, z, paving(x, z));
                }
            }
        }

        // Wide southern plaza in front of the South Portico
        for (int x = CU + DOME_U - 7; x <= CU + DOME_U + 7; x++) {
            for (int z = CV + 16; z <= EZ - 2; z++) {
                p.set(x, 0, z, paving(x, z));
            }
        }

        // Western approach path to the west portico
        for (int x = 2; x <= CU - 1; x++) {
            for (int z = CV - 3; z <= CV + 3; z++) {
                p.set(x, 0, z, paving(x, z));
            }
        }
    }

    /** Historic ruins of the ancient Ukimerioni fortress and 6th-century basilica on the hill. */
    private void fortressRuins(Plan p, Terrain t) {
        // Northern perimeter ruined fortress wall
        for (int x = 4; x <= EX - 4; x++) {
            int z = 2 + (hash(x, 0, 1) % 2);
            foundationRubble(p, t, x, z);
            int wallH = 1 + (hash(x, 0, 7) % 3);
            for (int y = 0; y <= wallH; y++) {
                p.set(x, y, z, rubble(x, y, z));
            }
        }

        // North-east corner ruined round tower base (Ukimerioni citadel)
        int tcx = EX - 8, tcz = 8;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 4.2) {
                    int x = tcx + dx, z = tcz + dz;
                    foundationRubble(p, t, x, z);
                    p.set(x, 0, z, rubble(x, 0, z));
                    int h = d > 2.5 ? 2 + (hash(x, 0, z) % 2) : 1;
                    for (int y = 1; y <= h; y++) {
                        p.set(x, y, z, rubble(x, y, z));
                    }
                }
            }
        }

        // Foundations of the 6th-century basilica to the north-west of the cathedral
        for (int x = 10; x <= 22; x++) {
            for (int z = 6; z <= 12; z++) {
                if (x == 10 || x == 22 || z == 6 || z == 12) {
                    p.set(x, 1, z, rubble(x, 1, z));
                    p.set(x, 0, z, rubble(x, 0, z));
                }
            }
        }
    }

    // ------------------------------------------------------------------ cathedral geometry

    /**
     * Checks if (u, v) is inside the cathedral envelope:
     * - West arm: u in 0..19, |v| <= 10.
     * - Crossing: u in 19..29, |v| <= 5.
     * - North conch: semicircle centered at (24, -5), radius 9 out to v = -14.
     * - South conch: semicircle centered at (24, 5), radius 9 out to v = 14.
     * - East apse: semicircle centered at (29, 0), radius 9 out to u = 38, plus pastophoria (prothesis & diakonikon)
     *   at u in 29..35, |v| in 5..9.
     */
    static boolean inBody(int u, int v) {
        int av = Math.abs(v);
        // Western basilica arm (central nave + side aisles)
        if (u >= 0 && u <= 19 && av <= 10) {
            return true;
        }
        // Central crossing
        if (u >= 19 && u <= 29 && av <= 5) {
            return true;
        }
        // North & South triconch exedras (semicircles of radius 9 from transept arms)
        if (u >= 15 && u <= 33 && av > 5 && av <= 14) {
            double d = Math.hypot(u - DOME_U, av - 5);
            if (d <= 9.0) {
                return true;
            }
        }
        // Eastern sanctuary (central semicircular apse + side pastophoria)
        if (u > 29) {
            if (u <= 38 && av <= 9) {
                double d = Math.hypot(u - 29, v);
                if (d <= 9.0) {
                    return true;
                }
            }
            // Pastophoria rectangular side wings
            if (u <= 35 && av >= 5 && av <= 9) {
                return true;
            }
        }
        return false;
    }

    /**
     * Roof height y at (u, v):
     * - High crossing arms (west nave, north/south transept, east bema): eaves 18, ridge 23.
     * - Lower side aisles in west arm (|v| 5..10): lean-to roof from 15 down to 11.
     * - Conch roofs: conical/hipped roofs peaking at 21 down to 17.
     * - Pastophoria: lean-to roofs sloping down to 13.
     */
    static int roofH(int u, int v) {
        if (!inBody(u, v)) {
            return Integer.MIN_VALUE;
        }
        int av = Math.abs(v);
        int h = Integer.MIN_VALUE;

        // Central longitudinal nave (west to east)
        if (av <= 4 && u >= 0 && u <= 29) {
            h = Math.max(h, 23 - av);
        }

        // Side aisles of western arm
        if (u >= 0 && u <= 19 && av >= 5 && av <= 10) {
            h = Math.max(h, 19 - av);
        }

        // North & South transepts and conchs
        if (av > 4 && av <= 14 && u >= 15 && u <= 33) {
            int distCross = Math.abs(u - DOME_U);
            int peak = 23 - distCross;
            if (av <= 6) {
                h = Math.max(h, peak);
            } else {
                // Slope down towards the conch perimeter
                double d = Math.hypot(u - DOME_U, av - 5);
                h = Math.max(h, (int) Math.round(22 - d * 0.7));
            }
        }

        // Eastern apse and pastophoria
        if (u > 29) {
            double d = Math.hypot(u - 29, v);
            if (av <= 4) {
                h = Math.max(h, (int) Math.round(23 - (u - 29) * 0.65 - av));
            } else {
                h = Math.max(h, (int) Math.round(18 - d * 0.6));
            }
        }

        return Math.max(h, 10);
    }

    /** Wall depth: 1 = outer face, 2 = inner core, 3 = interior room. */
    static int depth(int u, int v) {
        for (int r = 1; r <= 2; r++) {
            for (int du = -r; du <= r; du++) {
                for (int dv = -r; dv <= r; dv++) {
                    if (!inBody(u + du, v + dv)) {
                        return r;
                    }
                }
            }
        }
        return 3;
    }

    static Face outward(int u, int v) {
        for (Face f : Face.values()) {
            if (!inBody(u + f.dx, v + f.dz)) {
                return f;
            }
        }
        return null;
    }

    static Face exposed(int u, int v, int y) {
        for (Face f : Face.values()) {
            int nu = u + f.dx;
            int nv = v + f.dz;
            if (!inBody(nu, nv) || y >= roofH(nu, nv)) {
                return f;
            }
        }
        return null;
    }

    private static String roofPiece(int u, int v, int h) {
        Face up = null;
        int best = h;
        for (Face f : Face.values()) {
            int nu = u + f.dx;
            int nv = v + f.dz;
            if (inBody(nu, nv) && roofH(nu, nv) > best) {
                best = roofH(nu, nv);
                up = f;
            }
        }
        return up != null ? stairs(COPPER_ROOF_STAIRS, up, false) : slab(COPPER_ROOF_SLAB);
    }

    // ------------------------------------------------------------------ cathedral building

    private void cathedral(Plan p, Terrain t) {
        // 1. Massing: walls, interior voids, vaults, and roofs
        for (int u = 0; u <= 39; u++) {
            for (int v = -15; v <= 15; v++) {
                if (!inBody(u, v)) {
                    continue;
                }
                int x = CU + u;
                int z = CV + v;
                int h = roofH(u, v);
                int d = depth(u, v);

                foundation(p, t, x, z, b("stone"));
                p.set(x, 0, z, LIGHT2);

                for (int y = 1; y < h; y++) {
                    Face open = d == 1 ? outward(u, v) : exposed(u, v, y);
                    String s;
                    if (d == 1 || open != null) {
                        s = ashlar(x, y, z);
                    } else if (d == 2 || y >= h - 1) {
                        s = PLASTER;
                    } else if (y == 1) {
                        s = floor(x, z);
                    } else {
                        s = AIR;
                    }
                    p.set(x, y, z, s);
                }
                p.set(x, h, z, roofPiece(u, v, h));
            }
        }

        // 2. Interior architectural elements
        apseAndSanctuary(p);
        piersAndArches(p);
        westGallery(p);
        facadesAndWindows(p);
        drumAndDome(p);
        interiorDetails(p);
        lights(p);
    }

    // ------------------------------------------------------------------ interior & sanctuary

    /** Semicircular altar apse, synthronon bench, altar table, and chancel barrier. */
    private void apseAndSanctuary(Plan p) {
        // Raised sanctuary solea at y = 2
        for (int u = 28; u <= 36; u++) {
            for (int v = -7; v <= 7; v++) {
                if (inBody(u, v) && depth(u, v) >= 2) {
                    setRel(p, u, 1, v, LIGHT2);
                    setRel(p, u, 2, v, floor(CU + u, CV + v));
                }
            }
        }
        // Sanctuary steps facing west
        for (int v = -5; v <= 5; v++) {
            setRel(p, 27, 2, v, stairs(TRIM, Face.WEST, false));
        }

        // Low carved stone iconostasis / chancel barrier
        for (int v = -6; v <= 6; v++) {
            if (Math.abs(v) != 0 && Math.abs(v) != 1) { // Royal doors in center are open
                setRel(p, 28, 3, v, CARVED);
                setRel(p, 28, 4, v, WALL);
            }
        }

        // Altar table with lit candles in the centre of the eastern apse
        setRel(p, 33, 3, 0, CARVED);
        setRel(p, 33, 4, 0, candles(3));

        // Synthronon (semicircular tiered stone bench along the apse wall for clergy)
        for (int u = 32; u <= 37; u++) {
            for (int v = -6; v <= 6; v++) {
                double d = Math.hypot(u - 29, v);
                if (d >= 5.5 && d <= 7.2) {
                    Face f = (u - 29 >= Math.abs(v)) ? Face.EAST : (v > 0 ? Face.SOUTH : Face.NORTH);
                    setRel(p, u, 3, v, stairs(TRIM, f, false));
                }
            }
        }
    }

    /** Four grand cruciform piers supporting the central dome and high crossing arches. */
    private void piersAndArches(Plan p) {
        // 4 massive piers
        int[][] pierBases = {
                {20, -4}, // North-West
                {20, 4},  // South-West
                {27, -4}, // North-East
                {27, 4}   // South-East
        };

        for (int[] base : pierBases) {
            int pu = base[0];
            int pv = base[1];
            // Cruciform section 2x2 with 4 attached pilasters
            for (int du = 0; du <= 1; du++) {
                for (int dv = 0; dv <= 1; dv++) {
                    int signU = pu == 20 ? -1 : 1;
                    int signV = pv < 0 ? -1 : 1;
                    int u = pu + du * signU;
                    int v = pv + dv * signV;
                    for (int y = 2; y <= 16; y++) {
                        setRel(p, u, y, v, ashlar(CU + u, y, CV + v));
                    }
                }
            }
        }

        // High transverse arches (подпружные арки) connecting the 4 piers at y = 14..17
        // West arch (u = 20, v = -3..3) and East arch (u = 27, v = -3..3)
        for (int u : new int[]{20, 27}) {
            for (int v = -3; v <= 3; v++) {
                int av = Math.abs(v);
                int yArch = (av == 3) ? 14 : (av == 2) ? 15 : 16;
                setRel(p, u, yArch, v, stairs(TRIM, v < 0 ? Face.NORTH : Face.SOUTH, true));
                for (int y = yArch + 1; y <= 18; y++) {
                    setRel(p, u, y, v, LIGHT);
                }
            }
        }
        // North arch (v = -4, u = 21..26) and South arch (v = 4, u = 21..26)
        for (int v : new int[]{-4, 4}) {
            for (int u = 21; u <= 26; u++) {
                int dist = Math.abs(u - DOME_U);
                int yArch = (dist >= 2) ? 14 : 15;
                setRel(p, u, yArch, v, stairs(TRIM, u < DOME_U ? Face.WEST : Face.EAST, true));
                for (int y = yArch + 1; y <= 18; y++) {
                    setRel(p, u, y, v, LIGHT);
                }
            }
        }

        // Additional aisle arcade in the western arm (separating nave from aisles)
        for (int u = 4; u <= 19; u += 5) {
            for (int v : new int[]{-5, 5}) {
                for (int y = 2; y <= 10; y++) {
                    setRel(p, u, y, v, ashlar(CU + u, y, CV + v));
                }
            }
        }
    }

    /** Western choir gallery (хоры): second level overlooking the nave where kings and choir stood. */
    private void westGallery(Plan p) {
        // Floor of the gallery at y = 6
        for (int u = 2; u <= 8; u++) {
            for (int v = -8; v <= 8; v++) {
                if (inBody(u, v) && depth(u, v) >= 2) {
                    setRel(p, u, 6, v, LIGHT2);
                }
            }
        }
        // Carved balustrade overlooking the nave at u = 8
        for (int v = -4; v <= 4; v++) {
            setRel(p, 8, 7, v, WALL);
        }

        // Stone spiral stairs to the gallery in the south-west corner (u = 2..4, v = 6..8)
        int[][] stairPath = {{2, 6}, {2, 7}, {3, 7}, {4, 7}, {4, 6}};
        for (int i = 0; i < stairPath.length; i++) {
            int su = stairPath[i][0];
            int sv = stairPath[i][1];
            int y = 2 + i;
            setRel(p, su, y, sv, stairs(TRIM, Face.NORTH, false));
            for (int yy = 2; yy < y; yy++) {
                setRel(p, su, yy, sv, LIGHT);
            }
        }
    }

    /** Coronation throne of David the Builder (1089) and interior appointments. */
    private void interiorDetails(Plan p) {
        // Royal Throne / seat of King David the Builder at the South-West pier (u = 18, v = 3)
        setRel(p, 18, 2, 3, CARVED);
        setRel(p, 18, 2, 2, stairs(TRIM, Face.NORTH, false));
        setRel(p, 18, 3, 3, b("red_carpet"));
        setRel(p, 18, 4, 3, WALL);
        setRel(p, 18, 5, 3, slab("smooth_sandstone_slab"));

        // Baptismal font in the north aisle (u = 6, v = -7)
        setRel(p, 6, 2, -7, CARVED);
        setRel(p, 6, 3, -7, b("water_cauldron") + "[level=3]");
    }

    // ------------------------------------------------------------------ drum & dome

    /**
     * Reconstructed 16-faceted cylindrical drum (y = 19..28), 16 narrow windows with blind arches,
     * and conical copper patina dome (y = 29..35) with a 3-block golden cross (y = 36..38).
     */
    private void drumAndDome(Plan p) {
        int cu = DOME_U;
        int cv = 0;
        int cx = CU + cu;
        int cz = CV + cv;

        double rOuter = 5.8;
        double rInner = 4.4;

        // Pendentives / squinches converting crossing square to circle (y = 17..19)
        for (int du = -6; du <= 6; du++) {
            for (int dv = -6; dv <= 6; dv++) {
                double d = Math.hypot(du, dv);
                if (d <= rOuter && (Math.abs(du) >= 4 || Math.abs(dv) >= 4)) {
                    for (int y = 17; y <= 19; y++) {
                        setRel(p, cu + du, y, cv + dv, PLASTER);
                    }
                }
            }
        }

        // Cylindrical 16-faceted drum (y = 19..28)
        for (int du = -6; du <= 6; du++) {
            for (int dv = -6; dv <= 6; dv++) {
                double d = Math.hypot(du, dv);
                if (d > rOuter) {
                    continue;
                }
                int u = cu + du;
                int v = cv + dv;
                int x = cx + du;
                int z = cz + dv;

                if (d <= rInner) {
                    // Open vertical well inside the drum up into the dome
                    for (int y = 19; y <= 28; y++) {
                        p.set(x, y, z, AIR);
                    }
                } else {
                    // Drum wall
                    double angle = (Math.toDegrees(Math.atan2(dv, du)) + 360) % 22.5;
                    boolean windowSector = angle < 8.0 || angle > 14.5;
                    for (int y = 19; y <= 28; y++) {
                        String s;
                        if (y >= 23 && y <= 26 && windowSector) {
                            s = GLASS;
                        } else if (y == 27 && windowSector) {
                            s = stairs(TRIM, Face.NORTH, true); // Window arch head
                        } else {
                            s = ashlar(x, y, z);
                        }
                        p.set(x, y, z, s);
                    }
                }
            }
        }

        // Conical copper patina dome (y = 29..35)
        for (int du = -7; du <= 7; du++) {
            for (int dv = -7; dv <= 7; dv++) {
                double d = Math.hypot(du, dv);
                if (d > 6.4) {
                    continue;
                }
                int x = cx + du;
                int z = cz + dv;
                int domeTop = 29 + (int) Math.round((6.4 - d) * 1.05);

                for (int y = 29; y <= domeTop; y++) {
                    double innerH = Math.sqrt(Math.max(0, d * d + (y - 28) * (y - 28)));
                    if (innerH < rInner) {
                        p.set(x, y, z, AIR);
                    } else if (innerH < rInner + 1.0) {
                        p.set(x, y, z, PLASTER);
                    } else {
                        p.set(x, y, z, COPPER_BLOCK);
                    }
                }
                if (domeTop >= 29 && domeTop <= 35) {
                    p.set(x, domeTop, z, COPPER_BLOCK);
                }
            }
        }

        // Cross atop the conical dome (y = 36..38)
        setRel(p, cu, 36, cv, b("gold_block"));
        setRel(p, cu, 37, cv, b("gold_block"));
        setRel(p, cu - 1, 37, cv, b("gold_block"));
        setRel(p, cu + 1, 37, cv, b("gold_block"));
        setRel(p, cu, 38, cv, b("gold_block"));
    }

    // ------------------------------------------------------------------ facades & windows

    private void facadesAndWindows(Plan p) {
        // Blind arcades and windows on North and South conchs
        for (int s : new int[]{-1, 1}) {
            int conchV = s * 14;
            Face f = s < 0 ? Face.NORTH : Face.SOUTH;
            // 3 arched windows in the curved conch wall
            for (int du : new int[]{-3, 0, 3}) {
                int u = DOME_U + du;
                int v = s < 0 ? CV - 13 : CV + 13;
                for (int y = 6; y <= 9; y++) {
                    p.set(CU + u, y, v, GLASS);
                }
                p.set(CU + u, 10, v, stairs(TRIM, f, true));
            }
        }

        // Eastern facade: 3 apse windows and the famous 2 triangular niches
        for (int y = 6; y <= 11; y++) {
            setRel(p, 38, y, 0, GLASS);
        }
        for (int y = 5; y <= 9; y++) {
            setRel(p, 36, y, -4, GLASS);
            setRel(p, 36, y, 4, GLASS);
        }
        // Triangular niches flanking the central apse (classic Georgian feature)
        for (int v : new int[]{-3, 3}) {
            for (int y = 5; y <= 13; y++) {
                setRel(p, 38, y, v, AIR);
                setRel(p, 37, y, v, ashlar(CU + 37, y, CV + v));
            }
        }

        // Western facade: triple entrance and large west window above the portico
        for (int v = -2; v <= 2; v++) {
            for (int y = 11; y <= 16; y++) {
                boolean frame = Math.abs(v) == 2 || y == 11 || y == 16;
                setRel(p, 1, y, v, frame ? CARVED : GLASS);
            }
        }

        // West portal doors (triple entrance)
        entranceDoor(p, 0, 2, 0, Face.EAST);
        entranceDoor(p, 0, 2, -4, Face.EAST);
        entranceDoor(p, 0, 2, 4, Face.EAST);

        // South portal door (leading from the South Portico into the south conch)
        entranceDoor(p, DOME_U, 2, 14, Face.NORTH);
    }

    private void entranceDoor(Plan p, int u, int y, int v, Face in) {
        setRel(p, u, y, v, door(DOOR, in, false, false));
        setRel(p, u, y + 1, v, door(DOOR, in, true, false));
    }

    // ------------------------------------------------------------------ porticos

    /**
     * The celebrated Southern Portico (იჩქარი), added in the 1030s:
     * open two-tier porch with twin carved arches, columns, vaulted ceiling, and grand steps.
     */
    private void southPortico(Plan p, Terrain t) {
        int u1 = DOME_U - 4; // 20
        int u2 = DOME_U + 4; // 28
        int v1 = 14;
        int v2 = 19;

        // Podium and foundations
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                int x = CU + u;
                int z = CV + v;
                foundation(p, t, x, z, b("stone"));
                p.set(x, 1, z, paving(x, z));
            }
        }

        // Front stone steps leading up from the south plaza
        for (int u = u1; u <= u2; u++) {
            p.set(CU + u, 1, CV + v2 + 1, stairs(TRIM, Face.SOUTH, false));
        }

        // Columns / piers of the portico at the corners and center (u = 20, 24, 28; v = 19)
        int[] colUs = {u1, DOME_U, u2};
        for (int u : colUs) {
            for (int y = 2; y <= 6; y++) {
                setRel(p, u, y, v2, CARVED);
            }
        }
        // Side wall pilasters at v = 15..18 for u1 and u2
        for (int v = 15; v <= 18; v++) {
            for (int y = 2; y <= 6; y++) {
                setRel(p, u1, y, v, ashlar(CU + u1, y, CV + v));
                setRel(p, u2, y, v, ashlar(CU + u2, y, CV + v));
            }
        }

        // Arched openings: twin front arches springing at y = 5
        for (int u = u1 + 1; u <= DOME_U - 1; u++) {
            setRel(p, u, 5, v2, stairs(TRIM, u == u1 + 1 ? Face.WEST : Face.EAST, true));
        }
        for (int u = DOME_U + 1; u <= u2 - 1; u++) {
            setRel(p, u, 5, v2, stairs(TRIM, u == DOME_U + 1 ? Face.WEST : Face.EAST, true));
        }
        // Side arches
        for (int u : new int[]{u1, u2}) {
            for (int v = 16; v <= 17; v++) {
                setRel(p, u, 2, v, AIR);
                setRel(p, u, 3, v, AIR);
                setRel(p, u, 4, v, AIR);
                setRel(p, u, 5, v, stairs(TRIM, v == 16 ? Face.NORTH : Face.SOUTH, true));
            }
        }

        // Portico roof / upper cornice and balustrade (y = 7..8)
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                setRel(p, u, 7, v, LIGHT2);
                boolean edge = (u == u1 || u == u2 || v == v2);
                if (edge) {
                    setRel(p, u, 8, v, WALL);
                }
            }
        }

        // Lantern hanging inside the south portico vault
        setRel(p, DOME_U, 6, 17, lantern(true));

        // Plaque at the south entrance in Georgian and Russian
        setRel(p, DOME_U - 2, 3, v2 + 1, wallSign("dark_oak", Face.SOUTH));
        p.signs.add(new Plan.SignText(CU + DOME_U - 2, 3, CV + v2 + 1, PLAQUE_KA));
        setRel(p, DOME_U + 2, 3, v2 + 1, wallSign("dark_oak", Face.SOUTH));
        p.signs.add(new Plan.SignText(CU + DOME_U + 2, 3, CV + v2 + 1, PLAQUE_RU));
    }

    /** Western entrance portico protecting the main western doors. */
    private void westPortico(Plan p, Terrain t) {
        int u1 = -5;
        int u2 = -1;
        int v1 = -5;
        int v2 = 5;

        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                int x = CU + u;
                int z = CV + v;
                foundation(p, t, x, z, b("stone"));
                p.set(x, 1, z, paving(x, z));

                int av = Math.abs(v);
                int h = 9 - av / 2;
                for (int y = 2; y < h; y++) {
                    boolean corner = (u == u1 && (av == 5 || av == 0));
                    boolean side = (av == 5);
                    String s;
                    if (corner) {
                        s = CARVED;
                    } else if (side && (u == u1 || u == u2)) {
                        s = ashlar(x, y, z);
                    } else if (u == u1 && y == 5) {
                        s = stairs(TRIM, v < 0 ? Face.NORTH : Face.SOUTH, true);
                    } else {
                        s = AIR;
                    }
                    p.set(x, y, z, s);
                }
                Face up = v == 0 ? null : v < 0 ? Face.SOUTH : Face.NORTH;
                p.set(x, h, z, up == null ? slab(COPPER_ROOF_SLAB) : stairs(COPPER_ROOF_STAIRS, up, false));
            }
        }

        // Entrance steps from the west
        for (int v = -3; v <= 3; v++) {
            setRel(p, u1 - 1, 1, v, stairs(TRIM, Face.WEST, false));
        }

        // Lectern with history book at the west entrance
        setRel(p, u1 + 1, 2, 2, b("lectern") + "[facing=east]");
        p.lecterns.add(new int[]{CU + u1 + 1, 2, CV + 2});

        // Lantern hanging in the portico
        setRel(p, -3, 6, 0, lantern(true));

        // Plaque on the west portico
        setRel(p, u1, 3, -2, wallSign("dark_oak", Face.WEST));
        p.signs.add(new Plan.SignText(CU + u1, 3, CV - 2, PLAQUE_KA));
        setRel(p, u1, 3, 2, wallSign("dark_oak", Face.WEST));
        p.signs.add(new Plan.SignText(CU + u1, 3, CV + 2, PLAQUE_RU));
    }

    private static final List<String> PLAQUE_KA = List.of("ბაგრატის", "ტაძარი", "ქუთაისი", "1003");
    private static final List<String> PLAQUE_RU = List.of("Храм Баграта", "Кутаиси, Грузия", "1003 год", "масштаб 1:1");

    // ------------------------------------------------------------------ lighting & yard

    /** Central polykandilion (chandelier) hanging from the dome, and lanterns throughout. */
    private void lights(Plan p) {
        // Central great chandelier under the crossing dome
        for (int y = 17; y <= 27; y++) {
            setRel(p, DOME_U, y, 0, CHAIN);
        }
        setRel(p, DOME_U, 16, 0, lantern(true));
        // 4 surrounding arms of the chandelier
        for (Face f : Face.values()) {
            setRel(p, DOME_U + 2 * f.dx, 17, 2 * f.dz, CHAIN);
            setRel(p, DOME_U + 2 * f.dx, 16, 2 * f.dz, lantern(true));
        }

        // Conchs and nave chandeliers
        int[][] spots = {
                {10, 0},   // West nave
                {DOME_U, -8}, // North conch
                {DOME_U, 8},  // South conch
                {33, 0},   // East sanctuary
                {6, -7},   // North aisle
                {6, 7},    // South aisle
                {16, -7},  // North aisle east
                {16, 7}    // South aisle east
        };
        for (int[] s : spots) {
            int topY = Math.min(16, roofH(s[0], s[1]) - 2);
            for (int y = topY; y > 8; y--) {
                setRel(p, s[0], y, s[1], CHAIN);
            }
            setRel(p, s[0], 8, s[1], lantern(true));
        }
    }

    /** Yard surrounding the cathedral: cypress trees, stone benches, and lantern posts. */
    private void yard(Plan p) {
        // Lantern posts along paths
        int[][] posts = {
                {CU - 5, CV - 8}, {CU - 5, CV + 8},
                {CU + DOME_U - 8, EZ - 4}, {CU + DOME_U + 8, EZ - 4},
                {CU + DOME_U - 8, CV + 20}, {CU + DOME_U + 8, CV + 20},
                {CU + 40, CV - 12}, {CU + 40, CV + 12},
                {6, CV - 4}, {6, CV + 4}
        };
        for (int[] post : posts) {
            p.set(post[0], 1, post[1], b("cobblestone_wall"));
            p.set(post[0], 2, post[1], b("cobblestone_wall"));
            p.set(post[0], 3, post[1], lantern(false));
        }

        // Slender Caucasian/Mediterranean cypress trees
        int[][] trees = {
                {8, CV - 10}, {8, CV + 10},
                {CU - 4, CV - 15}, {CU - 4, CV + 15},
                {CU + 12, CV + 22}, {CU + 36, CV + 22},
                {CU + 44, CV - 10}, {CU + 44, CV + 10},
                {EX - 6, CV - 6}, {EX - 6, CV + 6}
        };
        for (int[] tree : trees) {
            cypress(p, tree[0], tree[1]);
        }
    }

    private static void cypress(Plan p, int x, int z) {
        for (int y = 1; y <= 6; y++) {
            p.set(x, y, z, logY("spruce_log"));
        }
        for (Face f : Face.values()) {
            for (int y = 2; y <= 6; y++) {
                p.set(x + f.dx, y, z + f.dz, CYPRESS);
            }
        }
        p.set(x, 7, z, CYPRESS);
        p.set(x, 8, z, CYPRESS);
    }

    // ------------------------------------------------------------------ book

    @Override
    public Book book() {
        return new Book("Храм Баграта", "Великие здания мира", List.of(
                "Храм Баграта\n(Собор Успения Богородицы)\n\nКутаиси, Грузия, вершина холма Укимериони над рекой Риони."
                        + "\n\nПостроен в масштабе 1:1: один блок — один метр.",
                "Освящён в 1003 году первым царём объединённой Грузии Багратом III. Строительство стало символом "
                        + "золотого века и государственного единства страны.\n\nВ 1089 году здесь в 16-летнем возрасте был "
                        + "помазан на царство святой царь Давид IV Строитель.",
                "Архитектура:\nУсложнённый триконх (три полукруглые апсиды: восток, север и юг) и удлинённый западный рукав. "
                        + "Огромный купол опирается на 4 свободно стоящих крестообразных столпа.\n\n"
                        + "Фасады украшены аркатурой, восточный фасад — двумя глубокими треугольными нишами.",
                "В 1030-х годах к храму пристроили знаменитый Южный портик (ичкари) — шедевр средневековой грузинской "
                        + "каменной резьбы с двойными резными арками и открытой галереей.",
                "В 1692 году османские войска захватили Кутаиси и взорвали пороховой склад внутри храма: купол и кровля рухнули. "
                        + "Более трёх веков собор стоял в виде величественных романтических руин.\n\n"
                        + "В 1994 году включён в список Всемирного наследия ЮНЕСКО.",
                "В 2009–2012 годах собор пережил масштабную реставрацию: были восстановлены исторические своды, 16-гранный барабан "
                        + "и характерный бирюзовый купол из патинированной меди с двухметровым золотым крестом.\n\n"
                        + "Храм вновь стал действующим кафедральным собором.",
                "Что найти внутри:\n"
                        + "— Коронационный трон царя Давида Строителя у юго-западного столпа\n"
                        + "— Синтрон (скамья епископов) в апсиде\n"
                        + "— Западные хоры на втором ярусе с каменной лестницей\n"
                        + "— Древние руины цитадели Укимериони и базилики VI века во дворе."));
    }
}

