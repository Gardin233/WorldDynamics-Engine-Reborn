package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetherPortalProtectionRulesTest {
    @Test
    void protectsTheTenChunkRadiusAroundTheScaledOverworldPosition() {
        assertTrue(NetherPortalProtectionRules.isWithinNetherChunkRadius(
                1600.0D, -1600.0D, 22 * 16, -13 * 16, 10));
        assertFalse(NetherPortalProtectionRules.isWithinNetherChunkRadius(
                1600.0D, -1600.0D, 23 * 16, -13 * 16, 10));
    }

    @Test
    void usesRadialDistanceAndHandlesNegativeCoordinates() {
        assertTrue(NetherPortalProtectionRules.isWithinNetherChunkRadius(
                -1600.0D, -1600.0D, -20 * 16, -20 * 16, 10));
        assertFalse(NetherPortalProtectionRules.isWithinNetherChunkRadius(
                0.0D, 0.0D, 8 * 16, 8 * 16, 10));
    }
}
