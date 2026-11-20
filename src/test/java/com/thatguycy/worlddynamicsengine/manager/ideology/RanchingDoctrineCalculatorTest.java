package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RanchingDoctrineCalculatorTest {
    @Test
    void primaryAndNeutralAttitudesAcceptBreeding() {
        assertTrue(RanchingDoctrineCalculator.assess("namespace:wder/ranching/primary"));
        assertTrue(RanchingDoctrineCalculator.assess("namespace:wder/ranching/none"));
        assertTrue(RanchingDoctrineCalculator.assess("neutral"));
    }

    @Test
    void opposingAttitudeTreatsBreedingAsViolation() {
        assertFalse(RanchingDoctrineCalculator.assess("namespace:wder/ranching/oppose"));
    }

    @Test
    void unknownAttitudesDoNotProduceMoodChanges() {
        assertNull(RanchingDoctrineCalculator.assess(null));
        assertNull(RanchingDoctrineCalculator.assess("namespace:wder/ranching/custom"));
    }
}
