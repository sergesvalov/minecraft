package com.server.halloumi;

import com.server.halloumi.model.Tavern;
import com.server.halloumi.model.TavernPalette;
import com.server.halloumi.npc.ResidentRoutes;
import com.server.halloumi.npc.Seats;
import com.server.halloumi.tavern.OldTaverns;
import com.server.halloumi.tavern.TavernLayout;
import com.server.halloumi.tavern.TavernPopulator;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TavernLogicTest {

    @Test
    void tavernLayoutInvariantsAndArea() {
        assertFalse(TavernLayout.CHAIRS.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> TavernLayout.CHAIRS.add(new TavernLayout.Chair(0, 1, 0, BlockFace.NORTH)));
        long diningCount = TavernLayout.CHAIRS.stream().filter(ResidentRoutes::isDiningChair).count();
        assertEquals(32, diningCount);

        int[] area = TavernLayout.area(100, 200);
        assertArrayEquals(new int[]{99, 199, 130, 233}, area);
    }

    @Test
    void oldTavernsAreasCoverSingleAndGuesthouseVariants() {
        Tavern simple = new Tavern("world", 10, 64, 20, false, Tavern.State.OLD);
        assertArrayEquals(new int[]{9, 19, 21, 29}, OldTaverns.area(simple));

        Tavern east = new Tavern("world", 10, 64, 20, false, Tavern.State.OLD_EAST);
        int[] eastArea = OldTaverns.area(east);
        assertTrue(eastArea[2] > 21);
        assertTrue(eastArea[3] >= 39);

        Tavern west = new Tavern("world", 10, 64, 20, false, Tavern.State.OLD_WEST);
        int[] westArea = OldTaverns.area(west);
        assertTrue(westArea[0] < 9);
    }

    @Test
    void populatorCandidateSelectionIsDeterministicAndRespectsCells() {
        assertFalse(TavernPopulator.isCandidateChunk(12345L, 0, 0, 48, 0.0));

        // Exactly one candidate per cell when chance = 1.0, strictly inside [0, spacing - 8)
        int hits = 0;
        for (int cx = 0; cx < 48; cx++) {
            for (int cz = 0; cz < 48; cz++) {
                boolean hit = TavernPopulator.isCandidateChunk(987654321L, cx, cz, 48, 1.0);
                assertEquals(hit, TavernPopulator.isCandidateChunk(987654321L, cx, cz, 48, 1.0));
                if (hit) {
                    assertTrue(cx < 40 && cz < 40, "Candidate should respect border separation");
                    hits++;
                }
            }
        }
        assertEquals(1, hits);
    }

    @Test
    void residentRoutesAndSeatYawWorkWithoutBukkitServer() {
        assertFalse(ResidentRoutes.isNight(0));
        assertFalse(ResidentRoutes.isNight(12599));
        assertTrue(ResidentRoutes.isNight(12600));
        assertTrue(ResidentRoutes.isNight(23299));
        assertFalse(ResidentRoutes.isNight(23300));

        assertEquals("taiga", ResidentRoutes.villagerTypeKey(TavernPalette.SPRUCE));
        assertEquals("desert", ResidentRoutes.villagerTypeKey(TavernPalette.SANDSTONE));
        assertEquals("plains", ResidentRoutes.villagerTypeKey(null));

        assertEquals(7.5, ResidentRoutes.chairApproachZ(8));
        assertEquals(11.5, ResidentRoutes.chairApproachZ(10));
        assertEquals(13.5, ResidentRoutes.chairApproachZ(14));
        assertEquals(17.5, ResidentRoutes.chairApproachZ(16));

        List<Vector> route = ResidentRoutes.groundRoute(new Vector(6.5, 1, 7.5), new Vector(20.5, 1, 17.5));
        assertEquals(List.of(
                new Vector(15.0, 1, 7.5),
                new Vector(15.0, 1, 17.5),
                new Vector(20.5, 1, 17.5)
        ), route);

        assertEquals(0f, Seats.yaw(BlockFace.SOUTH));
        assertEquals(90f, Seats.yaw(BlockFace.WEST));
        assertEquals(180f, Seats.yaw(BlockFace.NORTH));
        assertEquals(-90f, Seats.yaw(BlockFace.EAST));
    }
}
