package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IndoorsDoctrineCalculatorTest {
    @Test
    void skyLightBelowTenIsIndoorForLoveAttitude() {
        assertTrue(IndoorsDoctrineCalculator.assess(0, "love"));
        assertTrue(IndoorsDoctrineCalculator.assess(9, "namespace:wder/indoors/love"));
    }

    @Test
    void skyLightTenOrHigherIsOutdoorForLoveAttitude() {
        assertFalse(IndoorsDoctrineCalculator.assess(10, "love"));
        assertFalse(IndoorsDoctrineCalculator.assess(15, "love"));
    }

    @Test
    void unsupportedAttitudeDoesNotCreateAMoodOutcome() {
        assertNull(IndoorsDoctrineCalculator.assess(5, "neutral"));
        assertNull(IndoorsDoctrineCalculator.assess(5, null));
    }
}
