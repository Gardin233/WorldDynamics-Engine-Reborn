package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DisplayOfHeadsDoctrineCalculatorTest {
    @Test
    void recognizesVanillaHeadsAndSkullsButNotUnrelatedHeadBlocks() {
        assertTrue(DisplayOfHeadsDoctrineCalculator.isHeadBlock(Material.PLAYER_HEAD));
        assertTrue(DisplayOfHeadsDoctrineCalculator.isHeadBlock(Material.PIGLIN_WALL_HEAD));
        assertTrue(DisplayOfHeadsDoctrineCalculator.isHeadBlock(Material.WITHER_SKELETON_SKULL));
        assertFalse(DisplayOfHeadsDoctrineCalculator.isHeadBlock(Material.PISTON_HEAD));
    }

    @Test
    void headPresenceCompliesWithCravingAndViolatesDisgust() {
        assertTrue(DisplayOfHeadsDoctrineCalculator.assess("crave", true));
        assertFalse(DisplayOfHeadsDoctrineCalculator.assess("crave", false));
        assertFalse(DisplayOfHeadsDoctrineCalculator.assess("disgust", true));
        assertTrue(DisplayOfHeadsDoctrineCalculator.assess("disgust", false));
        assertNull(DisplayOfHeadsDoctrineCalculator.assess("neutral", true));
    }
}
