package com.server.greatbuildings;

import com.server.greatbuildings.model.Face;
import com.server.greatbuildings.model.Plan;
import com.server.greatbuildings.model.States;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanTest {

    @Test
    void boundsAndCoordinatesRoundTrip() {
        Plan plan = new Plan(-2, -1, -3, 2, 3, 1);
        assertEquals(5 * 5 * 5, plan.volume());

        int idx = 0;
        for (int y = plan.minY; y <= plan.maxY; y++) {
            for (int z = plan.minZ; z <= plan.maxZ; z++) {
                for (int x = plan.minX; x <= plan.maxX; x++) {
                    assertEquals(x, plan.x(idx));
                    assertEquals(y, plan.y(idx));
                    assertEquals(z, plan.z(idx));
                    idx++;
                }
            }
        }
        assertEquals(plan.volume(), idx);
    }

    @Test
    void setFillAndOutOfBoundsAreSafe() {
        Plan plan = new Plan(0, 0, 0, 3, 3, 3);
        plan.set(-1, 0, 0, States.b("stone"));
        plan.set(4, 0, 0, States.b("stone"));
        plan.set(0, 0, 0, null);
        assertNull(plan.get(0, 0, 0));

        plan.fill(1, 1, 1, 2, 2, 2, States.b("oak_planks"));
        assertEquals("minecraft:oak_planks", plan.get(1, 1, 1));
        assertEquals("minecraft:oak_planks", plan.get(2, 2, 2));
        assertNull(plan.get(0, 1, 1));
    }

    @Test
    void doubleDoorAndSignAndLectern() {
        Plan plan = new Plan(0, 0, 0, 5, 5, 5);
        plan.doubleDoor(1, 1, 0, "dark_oak_door", Face.SOUTH);
        assertTrue(plan.get(1, 1, 0).contains("half=lower,hinge=right"));
        assertTrue(plan.get(2, 1, 0).contains("half=lower,hinge=left"));
        assertTrue(plan.get(1, 2, 0).contains("half=upper,hinge=right"));
        assertTrue(plan.get(2, 2, 0).contains("half=upper,hinge=left"));

        plan.signs.add(new Plan.SignText(3, 2, 1, List.of("Line 1", "Line 2")));
        assertEquals(1, plan.signs.size());
        assertEquals("Line 1", plan.signs.get(0).lines().get(0));

        plan.lecterns.add(new int[]{4, 1, 4});
        assertEquals(1, plan.lecterns.size());
        assertArrayEquals(new int[]{4, 1, 4}, plan.lecterns.get(0));
    }
}
