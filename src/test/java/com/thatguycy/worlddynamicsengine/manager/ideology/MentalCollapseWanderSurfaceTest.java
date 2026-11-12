package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MentalCollapseWanderSurfaceTest {
    @Test
    void acceptsNonFullGrassPathAsAWanderFloor() {
        assertTrue(MentalCollapseManager.isSafeWanderFloorType(Material.DIRT_PATH));
    }

    @Test
    void rejectsAirLiquidsAndExistingHazardsAsWanderFloors() {
        assertFalse(MentalCollapseManager.isSafeWanderFloorType(Material.AIR));
        assertFalse(MentalCollapseManager.isSafeWanderFloorType(Material.WATER));
        assertFalse(MentalCollapseManager.isSafeWanderFloorType(Material.LAVA));
        assertFalse(MentalCollapseManager.isSafeWanderFloorType(Material.CACTUS));
    }

    @Test
    void roundsPartialBlockSurfaceUpToTheFeetSpaceAboveIt() {
        assertEquals(64, MentalCollapseManager.getWanderFeetBlockY(63.9375));
        assertEquals(64, MentalCollapseManager.getWanderFeetBlockY(64.0));
    }
}
