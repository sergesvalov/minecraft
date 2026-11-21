package com.server.greatbuildings;

import com.server.greatbuildings.buildings.BuildingCatalog;
import com.server.greatbuildings.engine.Placement;
import com.server.greatbuildings.model.Building;
import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementTest {

    @Test
    void mapsYawToCardinalRotations() {
        assertEquals(StructureRotation.NONE, Placement.rotationFromYaw(180f));
        assertEquals(StructureRotation.NONE, Placement.rotationFromYaw(-180f));
        assertEquals(StructureRotation.CLOCKWISE_90, Placement.rotationFromYaw(-90f));
        assertEquals(StructureRotation.CLOCKWISE_90, Placement.rotationFromYaw(270f));
        assertEquals(StructureRotation.CLOCKWISE_180, Placement.rotationFromYaw(0f));
        assertEquals(StructureRotation.COUNTERCLOCKWISE_90, Placement.rotationFromYaw(90f));
    }

    @Test
    void compassDirectionMapsAllEightSectors() {
        assertEquals("на севере", Placement.compassDirection(0, -100));
        assertEquals("на северо-востоке", Placement.compassDirection(100, -100));
        assertEquals("на востоке", Placement.compassDirection(100, 0));
        assertEquals("на юго-востоке", Placement.compassDirection(100, 100));
        assertEquals("на юге", Placement.compassDirection(0, 100));
        assertEquals("на юго-западе", Placement.compassDirection(-100, 100));
        assertEquals("на западе", Placement.compassDirection(-100, 0));
        assertEquals("на северо-западе", Placement.compassDirection(-100, -100));
    }

    @Test
    void rotatesBuildingInFrontOfPlayerForAllFourFacings() {
        Building apollo = new BuildingCatalog().byId("apollo");
        int px = 100;
        int pz = 200;

        // Facing North: building lies at z < pz
        int[] north = Placement.worldBounds(apollo, px, pz, StructureRotation.NONE);
        assertTrue(north[3] < pz);

        // Facing East: building lies at x > px
        int[] east = Placement.worldBounds(apollo, px, pz, StructureRotation.CLOCKWISE_90);
        assertTrue(east[0] > px);

        // Facing South: building lies at z > pz
        int[] south = Placement.worldBounds(apollo, px, pz, StructureRotation.CLOCKWISE_180);
        assertTrue(south[1] > pz);

        // Facing West: building lies at x < px
        int[] west = Placement.worldBounds(apollo, px, pz, StructureRotation.COUNTERCLOCKWISE_90);
        assertTrue(west[2] < px);
    }
}
