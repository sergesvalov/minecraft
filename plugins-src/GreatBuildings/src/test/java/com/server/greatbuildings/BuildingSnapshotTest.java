package com.server.greatbuildings;

import com.server.greatbuildings.buildings.BuildingCatalog;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Country;
import com.server.greatbuildings.model.Face;
import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.Terrain;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuildingSnapshotTest {

    private static final Terrain FLAT_TERRAIN = (x, z) -> -1;

    private record ExpectedSnapshot(int nonNull, int palette, int signs, int lecterns, String sha256) {
    }

    private static final Map<String, ExpectedSnapshot> EXPECTED = Map.of(
            "svetitskhoveli", new ExpectedSnapshot(
                    707268, 62, 7, 1,
                    "e138ba36c2037777bfe34050753520857d925eb3921eeada5d33f202bbaff1ee"),
            "bagrati", new ExpectedSnapshot(
                    173061, 53, 4, 1,
                    "1b0ce20ccd0db4a53b8a352eb2a74057152a319b2aa6ad9f78c684c27cbf3b0d"),
            "paphos", new ExpectedSnapshot(
                    95912, 51, 2, 1,
                    "e0514709672da6bb4ae81dfa29682564a87cdb0cfb2ea272322a5d83e2ced3fc"),
            "kourion", new ExpectedSnapshot(
                    88987, 26, 2, 1,
                    "26a06fbac06e11b33a3c10918bfccfd13e1d836c08893d1a4f4f9361458f64b0"),
            "apollo", new ExpectedSnapshot(
                    24025, 31, 2, 1,
                    "9878543da38845b86f05b200cfc906a170874dd61cb5e6b590920978a35edb6f"),
            "temple", new ExpectedSnapshot(
                    650536, 38, 2, 1,
                    "99c72e8d12186c71626f0b3809ba1b386597af126682b9244dcc929c2f90825b")
    );

    @Test
    void catalogAndMetadataAreValid() {
        BuildingCatalog catalog = new BuildingCatalog();
        assertEquals(6, catalog.all().size());
        assertEquals(2, catalog.byCountry(Country.GEORGIA).size());
        assertEquals(3, catalog.byCountry(Country.CYPRUS).size());
        assertEquals(1, catalog.byCountry(Country.ISRAEL).size());
        assertNotNull(catalog.byId("BAGRATI"));
        assertNotNull(catalog.byId("KOURION"));
        assertNotNull(catalog.byId("APOLLO"));
        assertNotNull(catalog.byId("TEMPLE"));
        assertNull(catalog.byId("unknown"));

        for (Building b : catalog.all()) {
            int[] bounds = b.bounds();
            assertEquals(6, bounds.length, b.id());
            assertTrue(bounds[0] <= bounds[3] && bounds[1] <= bounds[4] && bounds[2] <= bounds[5], b.id());

            int[] anchor = b.anchor();
            assertEquals(2, anchor.length, b.id());
            boolean insideFootprint = anchor[0] >= bounds[0] && anchor[0] <= bounds[3]
                    && anchor[1] >= bounds[2] && anchor[1] <= bounds[5];
            assertFalse(insideFootprint, b.id() + " anchor must be outside bounds");
            int gap = anchor[1] - bounds[5];
            assertTrue(gap >= 2 && gap <= 6, b.id() + " anchor gap before player face should be 2..6 blocks, got " + gap);

            Building.Book book = b.book();
            assertNotNull(book, b.id());
            assertFalse(book.title().isBlank(), b.id());
            assertFalse(book.author().isBlank(), b.id());
            assertFalse(book.pages().isEmpty(), b.id());
        }
    }

    @Test
    void paphosHasSymmetricDoubleDoorPortalAndScale() {
        Building paphos = new BuildingCatalog().byId("paphos");
        Plan plan = new Plan(paphos.bounds());
        paphos.draw(plan, FLAT_TERRAIN);

        // Double door leaves at x = 27..28, z = 26, y = 2..3
        assertTrue(plan.get(27, 2, 26).contains("dark_oak_door"));
        assertTrue(plan.get(28, 2, 26).contains("dark_oak_door"));
        assertTrue(plan.get(27, 3, 26).contains("dark_oak_door"));
        assertTrue(plan.get(28, 3, 26).contains("dark_oak_door"));

        // Solid jambs flanking the double door across the full 3-block wall thickness (no side holes!)
        for (int z = 25; z <= 27; z++) {
            for (int y = 2; y <= 3; y++) {
                assertNotEquals(Plan.AIR, plan.get(26, y, z), "Left jamb hole at z=" + z + " y=" + y);
                assertNotEquals(Plan.AIR, plan.get(29, y, z), "Right jamb hole at z=" + z + " y=" + y);
            }
        }
        // Solid lintel above the door leaves
        assertNotEquals(Plan.AIR, plan.get(27, 4, 26));
        assertNotEquals(Plan.AIR, plan.get(28, 4, 26));

        // Main footprint 40 x 20 m (x = 8..47, z = 8..27) surrounded by sea/moat
        assertNotEquals("minecraft:water", plan.get(8, 1, 8));
        assertNotEquals("minecraft:water", plan.get(47, 1, 27));
        assertEquals("minecraft:water", plan.get(7, 0, 8));
        assertEquals("minecraft:water", plan.get(48, 0, 27));
    }

    @Test
    void kourionHasOrchestraCaveaAndStage() {
        Building kourion = new BuildingCatalog().byId("kourion");
        Plan plan = new Plan(kourion.bounds());
        kourion.draw(plan, FLAT_TERRAIN);

        // Semicircular orchestra center at (36, 0, 36)
        assertEquals("minecraft:calcite", plan.get(36, 0, 36));
        // Outer cavea perimeter wall at radius 30..31 north of orchestra (36, 16, 6)
        assertNotEquals(Plan.AIR, plan.get(36, 16, 6));
        // Raised stage platform south of orchestra (36, 2, 40)
        assertEquals("minecraft:cut_sandstone", plan.get(36, 2, 40));
    }

    @Test
    void apolloHasPedestalBronzeStatueAndGoldenAttributes() {
        Building apollo = new BuildingCatalog().byId("apollo");
        Plan plan = new Plan(apollo.bounds());
        apollo.draw(plan, FLAT_TERRAIN);

        // Marble pedestal at center (15, 3, 15)
        assertEquals("minecraft:quartz_block", plan.get(15, 3, 15));
        // Bronze torso at (15, 14, 15)
        assertEquals("minecraft:waxed_copper_block", plan.get(15, 14, 15));
        // Golden laurel crown at (15, 21, 15), bow at (12, 15, 18), lyre at (18, 15, 17)
        assertEquals("minecraft:gold_block", plan.get(15, 21, 15));
        assertEquals("minecraft:gold_block", plan.get(12, 15, 18));
        assertEquals("minecraft:gold_block", plan.get(18, 15, 17));
    }

    @Test
    void allStairsAreAtLeastTwoBlocksWideAndWalkable() {
        BuildingCatalog catalog = new BuildingCatalog();

        // Paphos: 2-block staircase (x = 25..26, z = 14..23) + mud-brick limestone + unobstructed doors
        Plan paphos = new Plan(catalog.byId("paphos").bounds());
        catalog.byId("paphos").draw(paphos, FLAT_TERRAIN);
        assertTrue(paphos.get(25, 5, 17).contains("stairs"));
        assertTrue(paphos.get(26, 5, 17).contains("stairs"));
        assertEquals(Plan.AIR, paphos.get(25, 7, 17));
        assertEquals(Plan.AIR, paphos.get(26, 7, 17));
        assertTrue(paphos.palette().contains("minecraft:mud_bricks"));
        assertTrue(paphos.get(22, 2, 12).contains("dark_oak_door"));
        assertTrue(paphos.get(22, 2, 13).contains("dark_oak_door"));

        // Svetitskhoveli: 2-block enclosure stairs (z = 106..107)
        Plan svet = new Plan(catalog.byId("svetitskhoveli").bounds());
        catalog.byId("svetitskhoveli").draw(svet, FLAT_TERRAIN);
        assertTrue(svet.get(69, 3, 106).contains("stairs"));
        assertTrue(svet.get(69, 3, 107).contains("stairs"));

        // Bagrati: 2-block choir gallery stairs (v = 6..7 -> z = 36..37)
        Plan bagrati = new Plan(catalog.byId("bagrati").bounds());
        catalog.byId("bagrati").draw(bagrati, FLAT_TERRAIN);
        assertTrue(bagrati.get(23, 4, 36).contains("stairs"));
        assertTrue(bagrati.get(23, 4, 37).contains("stairs"));
        assertEquals(Plan.AIR, bagrati.get(23, 6, 36));
        assertEquals(Plan.AIR, bagrati.get(23, 6, 37));

        // Kourion: radial stairways at least 2 blocks wide
        Plan kourion = new Plan(catalog.byId("kourion").bounds());
        catalog.byId("kourion").draw(kourion, FLAT_TERRAIN);
        int northStairY = 4; // r = 14 -> seatHeight = 4
        assertTrue(kourion.get(36, northStairY, 22).contains("stone_brick_stairs")
                && (kourion.get(35, northStairY, 22).contains("stone_brick_stairs")
                || kourion.get(37, northStairY, 22).contains("stone_brick_stairs")));
    }

    @Test
    void jerusalemTempleHasUlamAltarMenorahAndHolyOfHolies() {
        Building temple = new BuildingCatalog().byId("temple");
        Plan plan = new Plan(temple.bounds());
        temple.draw(plan, FLAT_TERRAIN);

        // Monumental Ulam facade at y = 54 with gold cornice
        assertEquals("minecraft:gold_block", plan.get(36, 54, 46));
        // Altar of Burnt Offering (unhewn stone + perpetual fire at y = 9)
        assertEquals("minecraft:fire", plan.get(35, 9, 64));
        // Golden Menorah inside Hekhal
        assertEquals("minecraft:gold_block", plan.get(34, 10, 29));
        // Double Veil passage 2 blocks wide (x = 36..37)
        assertEquals(Plan.AIR, plan.get(36, 8, 19));
        assertEquals(Plan.AIR, plan.get(37, 8, 19));
        // Foundation Stone in Holy of Holies
        assertEquals("minecraft:andesite", plan.get(36, 8, 13));
        assertTrue(plan.palette().contains("minecraft:mud_bricks"));
    }

    @Test
    void allDoorsAndPassagesAreWalkable() {
        BuildingCatalog catalog = new BuildingCatalog();

        // Svetitskhoveli: 3 main double doors
        Plan svet = new Plan(catalog.byId("svetitskhoveli").bounds());
        catalog.byId("svetitskhoveli").draw(svet, FLAT_TERRAIN);
        assertWalkableDoubleDoor(svet, 39, 2, 55, Face.EAST);
        assertWalkableDoubleDoor(svet, 72, 2, 67, Face.NORTH);
        assertWalkableDoubleDoor(svet, 72, 2, 43, Face.SOUTH);

        // Bagrati: 3 west doors + south portal door with full jambs/lintel and outward-descending steps
        Plan bagrati = new Plan(catalog.byId("bagrati").bounds());
        catalog.byId("bagrati").draw(bagrati, FLAT_TERRAIN);
        assertWalkableDoubleDoor(bagrati, 18, 2, 26, Face.EAST);
        assertWalkableDoubleDoor(bagrati, 18, 2, 30, Face.EAST);
        assertWalkableDoubleDoor(bagrati, 18, 2, 33, Face.EAST);
        assertWalkableDoubleDoor(bagrati, 42, 2, 44, Face.NORTH);
        for (int y = 2; y <= 6; y++) {
            assertNotEquals(Plan.AIR, bagrati.get(41, y, 44), "Left jamb missing at y=" + y);
            assertNotEquals(Plan.AIR, bagrati.get(44, y, 44), "Right jamb missing at y=" + y);
        }
        assertNotEquals(Plan.AIR, bagrati.get(42, 4, 44));
        assertNotEquals(Plan.AIR, bagrati.get(43, 4, 44));
        assertTrue(bagrati.get(42, 1, 50).contains("facing=north"));
        assertTrue(bagrati.get(12, 1, 30).contains("facing=east"));

        // Paphos: main gate, 3 west cells, 2 east rooms, upper keep door, and full staircase to roof
        Plan paphos = new Plan(catalog.byId("paphos").bounds());
        catalog.byId("paphos").draw(paphos, FLAT_TERRAIN);
        assertWalkableDoubleDoor(paphos, 27, 2, 26, Face.NORTH);
        for (int z : new int[]{12, 17, 22}) {
            assertWalkableDoubleDoor(paphos, 22, 2, z, Face.WEST);
        }
        for (int z : new int[]{13, 21}) {
            assertWalkableDoubleDoor(paphos, 33, 2, z, Face.EAST);
        }
        assertWalkableDoubleDoor(paphos, 27, 12, 17, Face.NORTH);
        for (int x = 25; x <= 26; x++) {
            for (int i = 0; i <= 9; i++) {
                int z = 14 + i;
                int y = 2 + i;
                assertTrue(paphos.get(x, y, z).contains("stairs"), "Missing stair at " + x + "," + y + "," + z);
                assertEquals(Plan.AIR, paphos.get(x, y + 1, z), "Blocked headroom+1 at " + x + "," + y + "," + z);
                assertEquals(Plan.AIR, paphos.get(x, y + 2, z), "Blocked headroom+2 at " + x + "," + y + "," + z);
            }
            assertNotEquals(Plan.AIR, paphos.get(x, 11, 24));
            assertEquals(Plan.AIR, paphos.get(x, 12, 24));
            assertEquals(Plan.AIR, paphos.get(x, 13, 24));
        }

        // Jerusalem Temple: side chamber double doors, flush steps at z=50, and 2-block multi-floor stairs
        Plan temple = new Plan(catalog.byId("temple").bounds());
        catalog.byId("temple").draw(temple, FLAT_TERRAIN);
        assertWalkableDoubleDoor(temple, 28, 7, 40, Face.NORTH);
        assertWalkableDoubleDoor(temple, 44, 7, 40, Face.NORTH);
        assertEquals(Plan.AIR, temple.get(36, 7, 50));
        for (int doorX : new int[]{28, 44}) {
            for (int x = doorX; x <= doorX + 1; x++) {
                for (int i = 0; i <= 6; i++) {
                    int z = 32 - i;
                    int y = 7 + i;
                    assertTrue(temple.get(x, y, z).contains("stairs"));
                    assertEquals(Plan.AIR, temple.get(x, y + 1, z));
                    assertEquals(Plan.AIR, temple.get(x, y + 2, z));
                }
                for (int i = 0; i <= 5; i++) {
                    int z = 22 - i;
                    int y = 14 + i;
                    assertTrue(temple.get(x, y, z).contains("stairs"));
                    assertEquals(Plan.AIR, temple.get(x, y + 1, z));
                    assertEquals(Plan.AIR, temple.get(x, y + 2, z));
                }
            }
        }
    }

    private static void assertWalkableDoubleDoor(Plan plan, int x, int y, int z, Face intoRoom) {
        boolean alongX = intoRoom == Face.NORTH || intoRoom == Face.SOUTH;
        int x2 = alongX ? x + 1 : x;
        int z2 = alongX ? z : z + 1;

        String leftLower = plan.get(x, y, z);
        String rightLower = plan.get(x2, y, z2);
        String leftUpper = plan.get(x, y + 1, z);
        String rightUpper = plan.get(x2, y + 1, z2);
        assertNotNull(leftLower);
        assertNotNull(rightLower);
        assertTrue(leftLower.contains("_door") && leftUpper.contains("_door"),
                "Missing first door leaf at " + x + "," + y + "," + z);
        assertTrue(rightLower.contains("_door") && rightUpper.contains("_door"),
                "Missing second door leaf at " + x2 + "," + y + "," + z2);
        assertTrue((leftLower.contains("hinge=left") && rightLower.contains("hinge=right"))
                        || (leftLower.contains("hinge=right") && rightLower.contains("hinge=left")),
                "Door hinges must be opposite at " + x + "," + y + "," + z);

        int[][] leafCoords = {{x, z}, {x2, z2}};
        for (int[] lc : leafCoords) {
            int lx = lc[0], lz = lc[1];
            int outX = lx - intoRoom.dx, outZ = lz - intoRoom.dz;
            int inX = lx + intoRoom.dx, inZ = lz + intoRoom.dz;

            assertSolidFloor(plan, lx, y - 1, lz, "under door leaf");
            assertSolidFloor(plan, outX, y - 1, outZ, "outside door");
            assertSolidFloor(plan, inX, y - 1, inZ, "inside door");

            assertEquals(Plan.AIR, plan.get(outX, y, outZ), "Blocked outside lower at " + outX + "," + y + "," + outZ);
            assertEquals(Plan.AIR, plan.get(outX, y + 1, outZ), "Blocked outside upper at " + outX + "," + (y + 1) + "," + outZ);
            assertEquals(Plan.AIR, plan.get(inX, y, inZ), "Blocked inside lower at " + inX + "," + y + "," + inZ);
            assertEquals(Plan.AIR, plan.get(inX, y + 1, inZ), "Blocked inside upper at " + inX + "," + (y + 1) + "," + inZ);
        }
    }

    private static void assertSolidFloor(Plan plan, int x, int y, int z, String where) {
        String s = plan.get(x, y, z);
        assertNotNull(s, "Null floor " + where + " at " + x + "," + y + "," + z);
        assertNotEquals(Plan.AIR, s, "Air floor " + where + " at " + x + "," + y + "," + z);
        assertNotEquals("minecraft:water", s, "Water floor " + where + " at " + x + "," + y + "," + z);
    }

    @Test
    void refactoredBuildingsMatchExactBaselineSnapshots() throws Exception {
        BuildingCatalog catalog = new BuildingCatalog();
        for (Building b : catalog.all()) {
            Plan plan = new Plan(b.bounds());
            b.draw(plan, FLAT_TERRAIN);

            int nonNull = 0;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<String> palette = plan.palette();
            for (int i = 0; i < plan.volume(); i++) {
                int raw = plan.rawAt(i);
                if (raw != 0) {
                    nonNull++;
                    md.update((i + ":" + palette.get(raw - 1) + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            for (Plan.SignText sign : plan.signs) {
                md.update(("SIGN:" + sign.x() + "," + sign.y() + "," + sign.z() + ":" + String.join("|", sign.lines()) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            for (int[] lec : plan.lecterns) {
                md.update(("LECTERN:" + lec[0] + "," + lec[1] + "," + lec[2] + "\n").getBytes(StandardCharsets.UTF_8));
            }
            String hash = HexFormat.of().formatHex(md.digest());

            ExpectedSnapshot exp = EXPECTED.get(b.id());
            if (exp == null) {
                System.out.printf("NEW SNAPSHOT %s nonNull=%d palette=%d signs=%d lecterns=%d sha256=%s%n",
                        b.id(), nonNull, palette.size(), plan.signs.size(), plan.lecterns.size(), hash);
                continue;
            }
            assertEquals(exp.nonNull(), nonNull, b.id() + " nonNull");
            assertEquals(exp.palette(), palette.size(), b.id() + " palette");
            assertEquals(exp.signs(), plan.signs.size(), b.id() + " signs");
            assertEquals(exp.lecterns(), plan.lecterns.size(), b.id() + " lecterns");
            assertEquals(exp.sha256(), hash, b.id() + " sha256");
        }
    }
}
