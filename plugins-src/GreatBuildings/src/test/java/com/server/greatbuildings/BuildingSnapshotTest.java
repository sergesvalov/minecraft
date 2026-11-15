package com.server.greatbuildings;

import com.server.greatbuildings.buildings.BuildingCatalog;
import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Country;
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
                    "e603c217181d0ded42c6b7fb1937ed99ff4ad362ecb82fe1628363a1d29eeb97"),
            "bagrati", new ExpectedSnapshot(
                    173061, 53, 4, 1,
                    "47c6191897765f7a3cf52b1f735dd3d62708a4df50d52cc541fd949591ee8c77"),
            "paphos", new ExpectedSnapshot(
                    95912, 47, 2, 1,
                    "de7abb23906dbabd048ffde65ee99e2709961018de2915599b3f2f02a84d71f6")
    );

    @Test
    void catalogAndMetadataAreValid() {
        BuildingCatalog catalog = new BuildingCatalog();
        assertEquals(5, catalog.all().size());
        assertEquals(2, catalog.byCountry(Country.GEORGIA).size());
        assertEquals(3, catalog.byCountry(Country.CYPRUS).size());
        assertNotNull(catalog.byId("BAGRATI"));
        assertNotNull(catalog.byId("KOURION"));
        assertNotNull(catalog.byId("APOLLO"));
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
