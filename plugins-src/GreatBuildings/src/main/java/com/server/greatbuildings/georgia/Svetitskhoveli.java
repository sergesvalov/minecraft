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
import static com.server.greatbuildings.States.facing;
import static com.server.greatbuildings.States.lantern;
import static com.server.greatbuildings.States.leaves;
import static com.server.greatbuildings.States.logY;
import static com.server.greatbuildings.States.slab;
import static com.server.greatbuildings.States.stairs;
import static com.server.greatbuildings.States.wallSign;

/**
 * Svetitskhoveli Cathedral in Mtskheta (Georgia) inside its fortress enclosure, 1 block = 1 metre,
 * oriented like the real one: the altar to the east, the main gate of the enclosure to the south.
 *
 * <p>Sources (2026-11): Pravoslavnaya Entsiklopediya, "Светицховели" (pravenc.ru/text/2662044.html),
 * Wikipedia (en, ru). Taken from them: length with the entrance portico 55.4 m, width at the east facade 25 m,
 * outer height 48.5 m; cross-domed church elongated west to east, dome on 4 piers, faceted drum with a conical
 * dome; three entrances (west main, south, north); deep apse; east facade with five blind arches rising to the
 * centre and two niches triangular in plan; facades of light and dark yellow-red sandstone with green tuff,
 * north facade almost all green tuff, green drum and dome, red stone around the apse window; blind arcading as
 * the main facade ornament. Inside: the Life-giving Pillar under the large arch on the south side of the west
 * arm, the small domed chapel copying the Holy Sepulchre at the south wall, the font by the entrance, royal tombs
 * before the altar. Enclosure: near-square, 121.6 x 110.5 m, two-tier walls (solid below, fighting walkway
 * above) of cut and river stone with brick on top, 8 towers (6 round, 2 square), gate on the south side,
 * gate with a belfry on the west side.
 *
 * <p>Not in the sources, so chosen to fit the known dimensions: roof and wall heights, drum diameter (13 m) and
 * 12 drum windows (usual for Georgian domes), walls 5 m + parapet, tower sizes. Where a material isn't known
 * it follows the others: roofs, floor and the portico are sandstone like the walls.
 *
 * <p>Plan coordinates: x to the east, z to the south, y = 0 is the yard level. Outer faces of the enclosure
 * walls at x = 0..{@value #EX}, z = 0..{@value #EZ}. The cathedral is drawn in its own coordinates
 * u = x - CU (west to east) and v = z - CV (north to south, 0 on the axis).
 */
public final class Svetitskhoveli implements Building {

    static final int EX = 121, EZ = 110;
    private static final int TOP = 50;
    private static final int CU = 34, CV = 55;
    /** u of the dome centre (crossing). */
    private static final int DOME_U = 38;
    private static final int GATE_X = 61;
    private static final int WGATE_Z = 55;

    private static final String AIR = Plan.AIR;
    private static final String LIGHT = b("smooth_sandstone");
    private static final String LIGHT2 = b("cut_sandstone");
    /** Dark yellow-red sandstone. */
    private static final String DARK = b("terracotta");
    /** Green Tedzami tuff. */
    private static final String TUFF = b("green_terracotta");
    /** Red stone around the apse window. */
    private static final String RED = b("polished_granite");
    /** Frescoed plaster inside. */
    private static final String PLASTER = b("calcite");
    private static final String CARVED = b("chiseled_sandstone");
    private static final String GLASS = b("glass");
    private static final String BRICK = b("bricks");
    private static final String TRIM = "smooth_sandstone_stairs";
    private static final String ROOF_STAIRS = "smooth_sandstone_stairs";
    private static final String ROOF_SLAB = "smooth_sandstone_slab";
    private static final String DOOR = "dark_oak_door";
    private static final String CHAIN = b("iron_chain") + "[axis=y]";
    private static final String CYPRESS = leaves("spruce_leaves");

    @Override
    public String id() {
        return "svetitskhoveli";
    }

    @Override
    public Country country() {
        return Country.GEORGIA;
    }

    @Override
    public String title() {
        return "Светицховели";
    }

    @Override
    public String where() {
        return "Мцхета, Грузия, рядом с Тбилиси";
    }

    @Override
    public int[] bounds() {
        return new int[]{-6, -30, -6, 127, TOP, 116};
    }

    @Override
    public int[] anchor() {
        return new int[]{GATE_X, EZ + 8};
    }

    @Override
    public void draw(Plan p, Terrain t) {
        site(p, t);
        paths(p);
        enclosure(p, t);
        cathedral(p, t);
        portico(p, t);
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

    /** Facade masonry: light and dark sandstone with green tuff; the north facade is mostly tuff. */
    private static String ashlar(int x, int y, int z, boolean north) {
        int r = hash(x, y, z) % 100;
        if (north) {
            return r < 80 ? TUFF : r < 94 ? LIGHT : DARK;
        }
        return r < 62 ? LIGHT : r < 88 ? LIGHT2 : r < 94 ? DARK : TUFF;
    }

    /** Drum: green tuff with a few sandstone blocks. */
    private static String drumStone(int x, int y, int z) {
        return hash(x, y, z) % 100 < 90 ? TUFF : LIGHT2;
    }

    /** Enclosure walls: cut stone mixed with river stone. */
    private static String rubble(int x, int y, int z) {
        int r = hash(x, y, z) % 100;
        return b(r < 30 ? "cobblestone" : r < 50 ? "stone" : r < 65 ? "andesite" : r < 82 ? "smooth_sandstone"
                : "mossy_cobblestone");
    }

    private static String paving(int x, int z) {
        int r = hash(x, 0, z) % 100;
        return b(r < 55 ? "stone_bricks" : r < 70 ? "cracked_stone_bricks" : r < 85 ? "polished_andesite" : "cobblestone");
    }

    private static String fresco(int x, int y, int z) {
        int r = hash(x, y, z) % 100;
        return b(r < 40 ? "white_terracotta" : r < 55 ? "yellow_terracotta" : r < 72 ? "light_blue_terracotta"
                : r < 88 ? "orange_terracotta" : "red_terracotta");
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

    // ------------------------------------------------------------------ yard

    /** Levels the yard: air above, grass at y = 0, earth down to the real ground. Paved aprons outside the gates. */
    private void site(Plan p, Terrain t) {
        for (int x = 0; x <= EX; x++) {
            for (int z = 0; z <= EZ; z++) {
                clearColumn(p, x, z);
                p.set(x, 0, z, b("grass_block"));
                foundation(p, t, x, z, b("dirt"));
            }
        }
        for (int x = GATE_X - 3; x <= GATE_X + 3; x++) {
            for (int z = EZ + 1; z <= EZ + 6; z++) {
                apron(p, t, x, z);
            }
        }
        for (int x = -6; x <= -1; x++) {
            for (int z = WGATE_Z - 3; z <= WGATE_Z + 3; z++) {
                apron(p, t, x, z);
            }
        }
    }

    private void apron(Plan p, Terrain t, int x, int z) {
        clearColumn(p, x, z);
        p.set(x, 0, z, paving(x, z));
        foundationRubble(p, t, x, z);
    }

    private void paths(Plan p) {
        for (int x = 30; x <= 92; x++) {
            for (int z = 38; z <= 72; z++) {
                p.set(x, 0, z, paving(x, z));
            }
        }
        for (int x = GATE_X - 2; x <= GATE_X + 2; x++) {
            for (int z = 73; z <= EZ - 3; z++) {
                p.set(x, 0, z, paving(x, z));
            }
        }
        for (int x = 3; x <= 29; x++) {
            for (int z = WGATE_Z - 2; z <= WGATE_Z + 2; z++) {
                p.set(x, 0, z, paving(x, z));
            }
        }
    }

    /** Lantern posts along the paths and cypresses. */
    private void yard(Plan p) {
        int[][] posts = {{58, 80}, {64, 80}, {58, 92}, {64, 92}, {58, 103}, {64, 103}, {10, 52}, {10, 58},
                {22, 52}, {22, 58}, {29, 37}, {93, 37}, {29, 73}, {93, 73}, {100, 55}, {61, 20}};
        for (int[] post : posts) {
            p.set(post[0], 1, post[1], b("cobblestone_wall"));
            p.set(post[0], 2, post[1], b("cobblestone_wall"));
            p.set(post[0], 3, post[1], lantern(false));
        }
        int[][] trees = {{55, 76}, {67, 76}, {55, 87}, {67, 87}, {55, 98}, {67, 98}, {16, 50}, {16, 60},
                {26, 49}, {26, 61}, {9, 9}, {112, 9}, {9, 101}, {112, 101}, {104, 30}, {104, 80}, {40, 20}, {82, 20},
                {40, 90}, {82, 90}};
        for (int[] tree : trees) {
            cypress(p, tree[0], tree[1]);
        }
    }

    private static void cypress(Plan p, int x, int z) {
        for (int y = 1; y <= 7; y++) {
            p.set(x, y, z, logY("spruce_log"));
        }
        for (Face f : Face.values()) {
            for (int y = 2; y <= 7; y++) {
                p.set(x + f.dx, y, z + f.dz, CYPRESS);
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                for (int y = 3; y <= 5; y++) {
                    p.set(x + dx, y, z + dz, CYPRESS);
                }
            }
        }
        p.set(x, 8, z, CYPRESS);
        p.set(x, 9, z, CYPRESS);
    }

    // ------------------------------------------------------------------ enclosure

    private static int wallLayer(int x, int z) {
        return Math.min(Math.min(x, EX - x), Math.min(z, EZ - z));
    }

    /** Top of the lower tier, the fighting walkway: the two inner layers of the wall. */
    private static boolean isWalkway(int x, int z) {
        if (x < 0 || z < 0 || x > EX || z > EZ) {
            return false;
        }
        int layer = wallLayer(x, z);
        return layer == 1 || layer == 2;
    }

    private void enclosure(Plan p, Terrain t) {
        for (int x = 0; x <= EX; x++) {
            for (int z = 0; z <= EZ; z++) {
                int layer = wallLayer(x, z);
                if (layer > 2) {
                    continue;
                }
                foundationRubble(p, t, x, z);
                p.set(x, 0, z, rubble(x, 0, z));
                // Lower tier, 5 m, solid; a brick course along its top on the outside
                for (int y = 1; y <= 5; y++) {
                    p.set(x, y, z, layer == 0 && y == 5 ? BRICK : rubble(x, y, z));
                }
                if (layer == 0) {
                    // Upper tier: parapet with gun loopholes and brick merlons
                    int along = (z == 0 || z == EZ) ? x : z;
                    p.set(x, 6, z, rubble(x, 6, z));
                    p.set(x, 7, z, along % 4 == 2 ? AIR : rubble(x, 7, z));
                    p.set(x, 8, z, BRICK);
                    p.set(x, 9, z, along % 2 == 0 ? BRICK : AIR);
                }
            }
        }
        // Stairs up to the walkway on both sides of the south gate
        for (int k = 0; k <= 4; k++) {
            int y = 1 + k;
            int east = GATE_X + 6 + k;
            int west = GATE_X - 6 - k;
            for (int yy = 1; yy < y; yy++) {
                p.set(east, yy, EZ - 3, rubble(east, yy, EZ - 3));
                p.set(west, yy, EZ - 3, rubble(west, yy, EZ - 3));
            }
            p.set(east, y, EZ - 3, stairs("cobblestone_stairs", Face.EAST, false));
            p.set(west, y, EZ - 3, stairs("cobblestone_stairs", Face.WEST, false));
        }

        // 8 towers: 6 round (corners, north and east walls), 2 square gate towers (south and west)
        roundTower(p, t, 0, 0, 4.5);
        roundTower(p, t, EX, 0, 4.5);
        roundTower(p, t, 0, EZ, 4.5);
        roundTower(p, t, EX, EZ, 4.5);
        roundTower(p, t, GATE_X, 0, 3.8);
        roundTower(p, t, EX, WGATE_Z, 3.8);
        gateTower(p, t, GATE_X, EZ, true, false);
        gateTower(p, t, 0, WGATE_Z, false, true);
        belfry(p, 0, WGATE_Z);

        // Plaques at both gates: Georgian on the left of the south gate and at the west gate, Russian on the right
        p.set(GATE_X - 3, 3, EZ + 5, wallSign("dark_oak", Face.SOUTH));
        p.signs.add(new Plan.SignText(GATE_X - 3, 3, EZ + 5, PLAQUE_KA));
        p.set(GATE_X + 3, 3, EZ + 5, wallSign("dark_oak", Face.SOUTH));
        p.signs.add(new Plan.SignText(GATE_X + 3, 3, EZ + 5, PLAQUE_RU));
        p.set(-5, 3, WGATE_Z - 3, wallSign("dark_oak", Face.WEST));
        p.signs.add(new Plan.SignText(-5, 3, WGATE_Z - 3, PLAQUE_KA));
        p.set(-5, 3, WGATE_Z + 3, wallSign("dark_oak", Face.WEST));
        p.signs.add(new Plan.SignText(-5, 3, WGATE_Z + 3, PLAQUE_RU));
    }

    /** სვეტიცხოველი doesn't fit one sign line; split where the word joins: სვეტი (pillar) + ცხოველი (living). */
    private static final List<String> PLAQUE_KA = List.of("სვეტი-", "ცხოველი", "მცხეთა", "1010–1029");
    private static final List<String> PLAQUE_RU = List.of("Светицховели", "Мцхета, Грузия", "1010–1029", "масштаб 1:1");

    /** Round tower on the wall: solid below the walkway, a room at walkway level, brick top with merlons. */
    private void roundTower(Plan p, Terrain t, int cx, int cz, double r) {
        int reach = (int) Math.ceil(r);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                clearColumn(p, x, z);
                foundationRubble(p, t, x, z);
                p.set(x, 0, z, rubble(x, 0, z));
                boolean ring = d > r - 1.6;
                int sector = (int) Math.floor((Math.toDegrees(Math.atan2(dz, dx)) + 371.25) / 22.5) % 16;
                for (int y = 1; y <= 11; y++) {
                    String s;
                    if (ring) {
                        s = y >= 8 ? BRICK : rubble(x, y, z);
                        if (y == 9 && sector % 2 == 0) {
                            s = AIR; // loophole
                        }
                    } else {
                        s = y <= 5 ? rubble(x, y, z) : y == 11 ? b("stone_bricks") : AIR;
                    }
                    p.set(x, y, z, s);
                }
                p.set(x, 12, z, ring && sector % 2 == 0 ? BRICK : AIR);
                if (ring && isWalkway(x, z)) {
                    p.set(x, 6, z, AIR);
                    p.set(x, 7, z, AIR);
                }
            }
        }
        p.set(cx, 6, cz, lantern(false));
    }

    /** Square gate tower 9 x 9 with an arched passage 5 wide. The west one is faced with sandstone. */
    private void gateTower(Plan p, Terrain t, int cx, int cz, boolean passageAlongZ, boolean faced) {
        int top = 12;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                clearColumn(p, x, z);
                foundationRubble(p, t, x, z);
                int across = passageAlongZ ? dx : dz;
                int a = Math.abs(across);
                boolean passage = a <= 2;
                p.set(x, 0, z, passage ? paving(x, z) : rubble(x, 0, z));
                for (int y = 1; y <= top; y++) {
                    String s = faced ? ashlar(x, y, z, false) : y >= 9 ? BRICK : rubble(x, y, z);
                    if (passage) {
                        Face side = passageAlongZ ? (across < 0 ? Face.WEST : Face.EAST)
                                : (across < 0 ? Face.NORTH : Face.SOUTH);
                        if (y <= 4) {
                            s = AIR;
                        } else if (y == 5) {
                            s = a == 2 ? stairs(TRIM, side, true) : AIR;
                        } else if (y == 6 && a <= 1) {
                            s = a == 1 ? stairs(TRIM, side, true) : AIR;
                        }
                    }
                    p.set(x, y, z, s);
                }
                boolean perimeter = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                p.set(x, top + 1, z, perimeter && (dx + dz) % 2 == 0 ? (faced ? LIGHT2 : BRICK) : AIR);
            }
        }
    }

    /** Open belfry on top of the west gate tower: four piers, arches, stepped roof, a bell. */
    private void belfry(Plan p, int cx, int cz) {
        int base = 13;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                boolean pier = ax >= 2 && az >= 2;
                boolean perimeter = ax == 3 || az == 3;
                for (int y = base; y <= base + 4; y++) {
                    String s = AIR;
                    if (pier) {
                        s = ashlar(x, y, z, false);
                    } else if (perimeter && y == base + 4) {
                        int along = ax == 3 ? dz : dx;
                        if (Math.abs(along) == 1) {
                            Face side = ax == 3 ? (along < 0 ? Face.NORTH : Face.SOUTH) : (along < 0 ? Face.WEST : Face.EAST);
                            s = stairs(TRIM, side, true);
                        }
                    }
                    p.set(x, y, z, s);
                }
                p.set(x, base + 5, z, LIGHT);
            }
        }
        for (int k = 0; k <= 3; k++) {
            int r = 3 - k;
            int y = base + 6 + k;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    String s;
                    if (r == 0) {
                        s = slab(ROOF_SLAB);
                    } else if (Math.abs(dz) == r) {
                        s = stairs(ROOF_STAIRS, dz < 0 ? Face.SOUTH : Face.NORTH, false);
                    } else {
                        s = stairs(ROOF_STAIRS, dx < 0 ? Face.EAST : Face.WEST, false);
                    }
                    p.set(cx + dx, y, cz + dz, s);
                }
            }
        }
        p.set(cx, base + 4, cz, b("bell") + "[attachment=ceiling]");
    }

    // ------------------------------------------------------------------ cathedral massing

    private static void set(Plan p, int u, int y, int v, String state) {
        p.set(CU + u, y, CV + v, state);
    }

    /** Main body u 4..54 (51 m) x v -12..12 (25 m); the transept arms stick out 1 m to the north and south. */
    private static boolean inBody(int u, int v) {
        int av = Math.abs(v);
        return (u >= 4 && u <= 54 && av <= 12) || (u >= 33 && u <= 43 && av <= 13);
    }

    /**
     * y of the roof piece. The arms of the cross (nave west-east, transept north-south, 11 m wide) have gable
     * roofs with the eaves at 22 and the ridge at 27; the corner parts are lower, with lean-to roofs from 20 at the
     * arms down to 14 at the outer walls.
     */
    private static int roofH(int u, int v) {
        int av = Math.abs(v);
        int h = Integer.MIN_VALUE;
        if (av <= 5) {
            h = Math.max(h, 27 - av);
        }
        if (u >= 33 && u <= 43) {
            h = Math.max(h, 27 - Math.abs(u - DOME_U));
        }
        if (av >= 6 && av <= 12) {
            h = Math.max(h, 26 - av);
        }
        return h;
    }

    /** 1 = outer layer of the wall, 2 = inner layer, 3 = inside. */
    private static int depth(int u, int v) {
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

    private static Face outward(int u, int v) {
        for (Face f : Face.values()) {
            if (!inBody(u + f.dx, v + f.dz)) {
                return f;
            }
        }
        return null;
    }

    /** Side on which the block at (u, y, v) would be open to the sky (above a lower neighbouring roof), or null. */
    private static Face exposed(int u, int v, int y) {
        for (Face f : Face.values()) {
            int nu = u + f.dx;
            int nv = v + f.dz;
            if (!inBody(nu, nv) || y >= roofH(nu, nv)) {
                return f;
            }
        }
        return null;
    }

    private void cathedral(Plan p, Terrain t) {
        for (int u = 4; u <= 54; u++) {
            for (int v = -13; v <= 13; v++) {
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
                        s = ashlar(x, y, z, open == Face.NORTH);
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
        // Order matters: facades cut windows through the solid east end, the dome clears the crossing
        apse(p);
        facades(p);
        dome(p);
        interior(p);
        lights(p);
    }

    /** Stairs climbing towards the highest neighbouring roof, a slab on the ridge. */
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
        return up != null ? stairs(ROOF_STAIRS, up, false) : slab(ROOF_SLAB);
    }

    // ------------------------------------------------------------------ facades

    /** (u, v) of a facade cell: {@code plane} is v on north/south facades and u on east/west ones. */
    private static int[] cell(Face f, int plane, int along) {
        return f == Face.NORTH || f == Face.SOUTH ? new int[]{along, plane} : new int[]{plane, along};
    }

    /**
     * Blind arch: the outer layer of the wall is recessed from y = base up to a round arch made of upside-down
     * stairs, as high as the roof above allows. Returns the y where the arch springs.
     */
    private int blindArch(Plan p, Face f, int plane, int from, int to, int base) {
        int w = to - from + 1;
        if (w < 2) {
            return base;
        }
        int rows = (w - 2) / 2;
        int spring = Integer.MAX_VALUE;
        for (int i = 0; i < w; i++) {
            int[] c = cell(f, plane, from + i);
            spring = Math.min(spring, roofH(c[0], c[1]) - 2 - Math.min(Math.min(i, w - 1 - i), rows));
        }
        Face low = f == Face.NORTH || f == Face.SOUTH ? Face.WEST : Face.NORTH;
        for (int i = 0; i < w; i++) {
            int[] o = cell(f, plane, from + i);
            int iu = o[0] - f.dx;
            int iv = o[1] - f.dz;
            int k = Math.min(i, w - 1 - i);
            for (int y = base; y <= spring + rows; y++) {
                int row = y - spring;
                String outer;
                if (row < 0 || k > row) {
                    outer = AIR;
                } else if (k == row) {
                    outer = stairs(TRIM, i < w - 1 - i ? low : low.opposite(), true);
                } else {
                    continue;
                }
                set(p, o[0], y, o[1], outer);
                if (outer.equals(AIR)) {
                    set(p, iu, y, iv, ashlar(CU + iu, y, CV + iv, f == Face.NORTH));
                }
            }
        }
        return spring;
    }

    /** Row of blind arches about 3 m wide between 1 m pilasters, optionally with a narrow window in each. */
    private void arcade(Plan p, Face f, int plane, int from, int to, boolean windows) {
        int n = Math.max(1, (int) Math.round((to - from) / 4.0));
        int prev = from;
        for (int i = 1; i <= n; i++) {
            int pilaster = from + (int) Math.round(i * (to - from) / (double) n);
            int a = prev + 1;
            int c = pilaster - 1;
            int spring = blindArch(p, f, plane, a, c, 3);
            if (windows && c - a == 2 && spring >= 10) {
                int[] o = cell(f, plane, a + 1);
                for (int y = 6; y <= 8; y++) {
                    set(p, o[0] - f.dx, y, o[1] - f.dz, GLASS);
                }
            }
            prev = pilaster;
        }
    }

    private void facades(Plan p) {
        // Long north and south sides, and the transept fronts with one big arch up into the gable
        for (Face f : new Face[]{Face.NORTH, Face.SOUTH}) {
            int s = f == Face.NORTH ? -1 : 1;
            arcade(p, f, 12 * s, 4, 32, true);
            arcade(p, f, 12 * s, 44, 54, false);
            blindArch(p, f, 13 * s, 35, 41, 3);
            // Big window with a carved frame, door below it, steps
            int inner = 12 * s;
            for (int u = 36; u <= 40; u++) {
                for (int y = 12; y <= 20; y++) {
                    boolean frame = u == 36 || u == 40 || y == 12 || y == 20;
                    set(p, u, y, inner, frame ? CARVED : GLASS);
                }
            }
            entrance(p, DOME_U, 2, 13 * s, inner, f.opposite());
        }

        // West and east fronts: five arches rising to the centre
        int[][] bays = {{-10, -8}, {-6, -4}, {-2, 2}, {4, 6}, {8, 10}};
        int[] eastSprings = new int[bays.length];
        for (int i = 0; i < bays.length; i++) {
            blindArch(p, Face.WEST, 4, bays[i][0], bays[i][1], 3);
            eastSprings[i] = blindArch(p, Face.EAST, 54, bays[i][0], bays[i][1], 3);
        }
        // West: big window above the portico, narrow windows in the side bays, the main door
        for (int v = -2; v <= 2; v++) {
            for (int y = 15; y <= 22; y++) {
                boolean frame = Math.abs(v) == 2 || y == 15 || y == 22;
                set(p, 5, y, v, frame ? CARVED : GLASS);
            }
        }
        for (int v : new int[]{-9, -5, 5, 9}) {
            for (int y = 8; y <= 10; y++) {
                set(p, 5, y, v, GLASS);
            }
        }
        set(p, 4, 2, 0, AIR);
        set(p, 5, 2, 0, door(DOOR, Face.EAST, false, false));
        set(p, 5, 3, 0, door(DOOR, Face.EAST, true, false));

        // East: apse window framed in red stone, deep through the thick east wall
        for (int v = -2; v <= 2; v++) {
            for (int y = 6; y <= 12; y++) {
                boolean frame = Math.abs(v) == 2 || y == 6 || y == 12;
                set(p, 53, y, v, frame ? RED : GLASS);
                if (!frame) {
                    for (int u = 50; u <= 52; u++) {
                        set(p, u, y, v, AIR);
                    }
                }
            }
        }
        // East: the two niches, triangular in plan, in the middle-height arches
        for (int i : new int[]{1, 3}) {
            int c = (bays[i][0] + bays[i][1]) / 2;
            for (int y = 3; y <= eastSprings[i] - 2; y++) {
                for (int v = c - 1; v <= c + 1; v++) {
                    set(p, 53, y, v, AIR);
                    // Stone lining of the niche (the wall behind is plaster inside the church)
                    set(p, 52, y, v, ashlar(CU + 52, y, CV + v, false));
                }
                if (y <= eastSprings[i] - 3) {
                    set(p, 52, y, c, AIR);
                    set(p, 51, y, c, ashlar(CU + 51, y, CV + c, false));
                }
            }
        }
        // East: windows of the side apses in the outer arches
        for (int v : new int[]{-9, 9}) {
            for (int y = 7; y <= 9; y++) {
                set(p, 53, y, v, GLASS);
                set(p, 52, y, v, AIR);
                set(p, 51, y, v, AIR);
            }
        }
    }

    /** Door in the inner wall layer behind a portal in the outer layer, and a step in front. */
    private void entrance(Plan p, int u, int y, int outerV, int innerV, Face in) {
        set(p, u, y, outerV, AIR);
        set(p, u, y, innerV, door(DOOR, in, false, false));
        set(p, u, y + 1, innerV, door(DOOR, in, true, false));
        for (int du = -1; du <= 1; du++) {
            set(p, u + du, 1, outerV - in.dz, stairs("smooth_sandstone_stairs", in, false));
        }
    }

    /** Open entrance portico in front of the west door: 4 m deep, 11 m wide, arched front and sides. */
    private void portico(Plan p, Terrain t) {
        for (int u = 0; u <= 3; u++) {
            for (int v = -5; v <= 5; v++) {
                int x = CU + u;
                int z = CV + v;
                int av = Math.abs(v);
                int h = 15 - av;
                foundation(p, t, x, z, b("stone"));
                p.set(x, 0, z, LIGHT2);
                p.set(x, 1, z, av <= 3 ? floor(x, z) : ashlar(x, 1, z, false));
                for (int y = 2; y < h; y++) {
                    String s;
                    if (y >= 10) {
                        s = ashlar(x, y, z, v < 0 && av == 5);
                    } else if (av <= 3) {
                        // Barrel vault 7 wide springing at y 7
                        int k = 3 - av;
                        int row = y - 7;
                        if (row < 0 || k > row) {
                            s = AIR;
                        } else if (k == row) {
                            s = stairs(TRIM, v < 0 ? Face.NORTH : Face.SOUTH, true);
                        } else {
                            s = ashlar(x, y, z, false);
                        }
                    } else if (u >= 2 && y <= 5) {
                        s = AIR; // side arches
                    } else {
                        s = ashlar(x, y, z, v < 0);
                    }
                    p.set(x, y, z, s);
                }
                Face up = v == 0 ? null : v < 0 ? Face.SOUTH : Face.NORTH;
                p.set(x, h, z, up == null ? slab(ROOF_SLAB) : stairs(ROOF_STAIRS, up, false));
            }
        }
        for (int v = -3; v <= 3; v++) {
            set(p, -1, 1, v, stairs("smooth_sandstone_stairs", Face.EAST, false));
        }
        set(p, 2, 9, 0, lantern(true));
        // Georgian name on the portico gable, above the arch of the main entrance
        set(p, -1, 11, 0, wallSign("dark_oak", Face.WEST));
        p.signs.add(new Plan.SignText(CU - 1, 11, CV, PLAQUE_KA));
    }

    // ------------------------------------------------------------------ inside

    private void interior(Plan p) {
        // Four piers under the dome and one more pair in the west arm (two unequal arches on each side)
        for (int[] us : new int[][]{{20, 21}, {32, 33}, {43, 44}}) {
            for (int u : us) {
                for (int v : new int[]{-6, -5, 5, 6}) {
                    for (int y = 2; y <= roofH(u, v) - 2; y++) {
                        set(p, u, y, v, y <= 3 ? LIGHT2 : PLASTER);
                    }
                }
            }
        }
        // Walls above the arches between the nave and the aisles, and between the transept and the aisles
        for (int v : new int[]{-6, -5, 5, 6}) {
            arcadeWall(p, 6, 19, v, true);
            arcadeWall(p, 22, 31, v, true);
            arcadeWall(p, 45, 46, v, true);
        }
        for (int u : new int[]{32, 33, 43, 44}) {
            arcadeWall(p, 7, 10, u, false);
            arcadeWall(p, -10, -7, u, false);
        }

        lifeGivingPillar(p);
        sepulchreChapel(p);

        // Font to the right of the west entrance, lectern with the history to the left
        set(p, 7, 2, 3, b("water_cauldron") + "[level=3]");
        set(p, 7, 2, -3, facing("lectern", Face.WEST));
        p.lecterns.add(new int[]{CU + 7, 2, CV - 3});

        // Royal tombs before the altar; Vakhtang Gorgasali's in the middle with a candle
        for (int v = -4; v <= 4; v += 2) {
            set(p, 40, 1, v, b("polished_diorite"));
            set(p, 41, 1, v, b("polished_diorite"));
        }
        set(p, 41, 2, 0, candles(4));

        // Low stone altar screen with the royal doors in the middle
        for (int v = -4; v <= 4; v++) {
            if (v != 0) {
                set(p, 44, 2, v, CARVED);
                set(p, 44, 3, v, slab(ROOF_SLAB));
            }
        }
    }

    /** Wall from y 14 up to the vault along a row of arches; upside-down stairs at the springing next to supports. */
    private void arcadeWall(Plan p, int from, int to, int fixed, boolean alongU) {
        for (int a = from; a <= to; a++) {
            int u = alongU ? a : fixed;
            int v = alongU ? fixed : a;
            for (int y = 14; y <= roofH(u, v) - 2; y++) {
                set(p, u, y, v, PLASTER);
            }
            Face toSupport = null;
            if (a == from) {
                toSupport = alongU ? Face.WEST : Face.NORTH;
            } else if (a == to) {
                toSupport = alongU ? Face.EAST : Face.SOUTH;
            }
            if (toSupport != null) {
                set(p, u, 13, v, stairs(TRIM, toSupport, true));
            }
        }
    }

    /** The Life-giving Pillar: painted tower-like canopy under the big arch on the south side of the west arm. */
    private void lifeGivingPillar(Plan p) {
        for (int u = 11; u <= 14; u++) {
            for (int v = 4; v <= 7; v++) {
                boolean corner = (u == 11 || u == 14) && (v == 4 || v == 7);
                for (int y = 2; y <= 9; y++) {
                    set(p, u, y, v, corner ? LIGHT2 : fresco(CU + u, y, CV + v));
                }
                set(p, u, 10, v, LIGHT2);
                boolean edge = u == 11 || u == 14 || v == 4 || v == 7;
                if (edge) {
                    Face in = v == 4 ? Face.SOUTH : v == 7 ? Face.NORTH : u == 11 ? Face.EAST : Face.WEST;
                    set(p, u, 11, v, stairs(TRIM, in, false));
                } else {
                    set(p, u, 11, v, LIGHT2);
                    set(p, u, 12, v, slab(ROOF_SLAB));
                }
            }
        }
        set(p, 12, 4, 3, wallSign("dark_oak", Face.NORTH));
        p.signs.add(new Plan.SignText(CU + 12, 4, CV + 3, List.of("", "Животворящий", "Столп", "")));
    }

    /** Small domed chapel copying the Holy Sepulchre, at the south wall. */
    private void sepulchreChapel(Plan p) {
        for (int u = 24; u <= 29; u++) {
            for (int v = 7; v <= 10; v++) {
                boolean edgeU = u == 24 || u == 29;
                boolean edgeV = v == 7 || v == 10;
                for (int y = 2; y <= 6; y++) {
                    set(p, u, y, v, edgeU && edgeV ? CARVED : edgeU || edgeV ? LIGHT2 : AIR);
                }
                set(p, u, 7, v, LIGHT);
            }
        }
        for (int u = 26; u <= 27; u++) {
            for (int y = 2; y <= 4; y++) {
                set(p, u, y, 7, AIR);
            }
            for (int v = 8; v <= 9; v++) {
                set(p, u, 8, v, LIGHT2);
                set(p, u, 9, v, slab(ROOF_SLAB));
            }
        }
        set(p, 27, 2, 9, candles(3));
        set(p, 25, 4, 6, wallSign("dark_oak", Face.NORTH));
        p.signs.add(new Plan.SignText(CU + 25, 4, CV + 6, List.of("Часовня", "Гроба", "Господня", "")));
    }

    /**
     * Thick east end: the deep central apse with a half dome and the bishops' bench along its wall, and the two
     * small side apses.
     */
    private void apse(Plan p) {
        double r = 4.6;
        double rs = 2.6;
        for (int u = 47; u <= 53; u++) {
            for (int v = -11; v <= 11; v++) {
                if (depth(u, v) < 3) {
                    continue;
                }
                double dc = (u - 46) * (u - 46) + v * v;
                double ds = (u - 48) * (u - 48) + (Math.abs(v) - 9) * (Math.abs(v) - 9);
                for (int y = 2; y <= roofH(u, v) - 2; y++) {
                    boolean open;
                    if (dc <= r * r) {
                        open = y <= 12 || dc + (y - 12) * (y - 12) <= r * r;
                    } else if (ds <= rs * rs) {
                        open = y <= 8 || ds + (y - 8) * (y - 8) <= rs * rs;
                    } else {
                        open = false;
                    }
                    if (!open) {
                        set(p, u, y, v, PLASTER);
                    }
                }
            }
        }
        // Altar and the bench of seats along the apse wall
        set(p, 46, 2, 0, CARVED);
        set(p, 46, 3, 0, candles(3));
        for (int u = 46; u <= 51; u++) {
            for (int v = -5; v <= 5; v++) {
                double d = Math.hypot(u - 46, v);
                if (d > 3.5 && d <= r) {
                    Face back = Math.abs(u - 46) >= Math.abs(v) ? Face.EAST : v > 0 ? Face.SOUTH : Face.NORTH;
                    set(p, u, 2, v, stairs(TRIM, back, false));
                }
            }
        }
    }

    /**
     * Faceted drum of green tuff, 13 m across, from the roofs at y 20 to 36, with 12 windows in a blind arcade;
     * conical dome up to 45 with a hemispherical vault inside; cross to 49 (outer height 48.5 m).
     */
    private void dome(Plan p) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 7.2) {
                    continue;
                }
                int u = DOME_U + dx;
                int v = dz;
                int x = CU + u;
                int z = CV + v;
                if (d <= 6.4) {
                    boolean ring = d > 4.2;
                    int layer = d > 5.6 ? 0 : d > 4.9 ? 1 : 2;
                    double angle = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 30;
                    double fromAxis = Math.min(angle, 30 - angle);
                    for (int y = ring ? 20 : 2; y <= 36; y++) {
                        String s;
                        if (!ring) {
                            s = AIR;
                        } else if (layer == 2) {
                            s = PLASTER;
                        } else {
                            s = drumStone(x, y, z);
                            if (layer == 0 && ((y >= 24 && y <= 32 && fromAxis <= 10) || (y == 33 && fromAxis < 6))) {
                                s = AIR; // blind arcade
                            }
                            if (layer == 1 && y >= 26 && y <= 31 && fromAxis < 6) {
                                s = GLASS;
                            }
                        }
                        if (ring && layer == 2 && y >= 26 && y <= 31 && fromAxis < 6) {
                            s = AIR;
                        }
                        p.set(x, y, z, s);
                    }
                }
                int top = 37 + (int) Math.floor((7.2 - d) * 1.15);
                for (int y = 37; y <= top; y++) {
                    double inner = Math.sqrt(d * d + (y - 36) * (y - 36));
                    p.set(x, y, z, inner < 4.2 ? AIR : inner < 5.2 ? PLASTER : TUFF);
                }
            }
        }
        for (int y = 46; y <= 49; y++) {
            set(p, DOME_U, y, 0, b("gold_block"));
        }
        set(p, DOME_U - 1, 48, 0, b("gold_block"));
        set(p, DOME_U + 1, 48, 0, b("gold_block"));
    }

    /** Big chandelier under the dome, lanterns on chains in the arms and aisles (no dark corners for mobs). */
    private void lights(Plan p) {
        for (int y = 24; y <= 35; y++) {
            set(p, DOME_U, y, 0, CHAIN);
        }
        set(p, DOME_U, 23, 0, lantern(true));
        for (Face f : Face.values()) {
            set(p, DOME_U + 3 * f.dx, 24, 3 * f.dz, CHAIN);
            set(p, DOME_U + 3 * f.dx, 23, 3 * f.dz, CHAIN);
            set(p, DOME_U + 3 * f.dx, 22, 3 * f.dz, lantern(true));
        }
        int[][] spots = {{8, 0}, {14, 0}, {26, 0}, {9, -9}, {9, 9}, {18, -9}, {18, 9}, {31, -9}, {31, 9},
                {DOME_U, -9}, {DOME_U, 9}, {45, -9}, {45, 9}, {45, 0}};
        for (int[] s : spots) {
            int topY = roofH(s[0], s[1]) - 2;
            for (int y = topY; y > 10; y--) {
                set(p, s[0], y, s[1], CHAIN);
            }
            set(p, s[0], 10, s[1], lantern(true));
        }
    }

    // ------------------------------------------------------------------ book

    @Override
    public Book book() {
        return new Book("Светицховели", "Великие здания мира", List.of(
                "Светицховели\n\nКафедральный собор в Мцхете — древней столице Грузии, у слияния Арагви и Куры, "
                        + "рядом с Тбилиси.\n\nПостроен в масштабе 1:1: один блок — один метр.",
                "«Светицховели» значит «Животворящий столп».\n\nПо преданию, здесь похоронена Сидония с хитоном "
                        + "Христа. На её могиле вырос кедр, а один из столпов из него сам поднялся в воздух и "
                        + "опустился по молитве святой Нино.",
                "Первую, деревянную церковь поставили в IV веке при царе Мириане. В V веке царь Вахтанг Горгасали "
                        + "построил каменную базилику. Нынешний собор возвёл в 1010–1029 годах зодчий Арсукисдзе.",
                "На восточном фасаде — рельеф «рука зодчего с угольником» и надпись с его именем. Фасады украшены "
                        + "аркатурой. Камень: светлый и тёмный песчаник и зелёный тедзамский туф.",
                "Длина с портиком 55,4 м, ширина 25 м, высота 48,5 м. Крестово-купольный храм: купол на четырёх "
                        + "столпах, барабан и конус из зелёного туфа. Три входа: западный (главный), южный и северный.",
                "Внутри найди:\n— Животворящий столп (южная сторона)\n— часовню Гроба Господня\n— купель у входа\n"
                        + "— гробницы царей перед алтарём, в том числе Вахтанга Горгасали и Ираклия II.",
                "Ограда 121,6 × 110,5 м с восемью башнями (6 круглых и 2 квадратные) в нынешнем виде — XVIII век, "
                        + "царь Ираклий II. Ворота на юге, на западе — ворота с колокольней.\n\nС 1994 года собор — "
                        + "объект Всемирного наследия ЮНЕСКО."));
    }
}
