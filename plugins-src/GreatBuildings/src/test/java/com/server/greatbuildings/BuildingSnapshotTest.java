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
                    72633, 48, 2, 1,
                    "fc2d9970e931fdd86ab58b106a2ae407940aa9a307c0a95732612cd08eb84d58")
    );

    @Test
    void catalogAndMetadataAreValid() {
        BuildingCatalog catalog = new BuildingCatalog();
        assertEquals(3, catalog.all().size());
        assertEquals(2, catalog.byCountry(Country.GEORGIA).size());
        assertEquals(1, catalog.byCountry(Country.CYPRUS).size());
        assertNotNull(catalog.byId("BAGRATI"));
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
            assertNotNull(exp, "Missing expected snapshot for " + b.id());
            assertEquals(exp.nonNull(), nonNull, b.id() + " nonNull");
            assertEquals(exp.palette(), palette.size(), b.id() + " palette");
            assertEquals(exp.signs(), plan.signs.size(), b.id() + " signs");
            assertEquals(exp.lecterns(), plan.lecterns.size(), b.id() + " lecterns");
            assertEquals(exp.sha256(), hash, b.id() + " sha256");
        }
    }
}
