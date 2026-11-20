package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightingDoctrineCalculatorTest {
    @Test
    void daylightUsesTheGreaterOfBlockAndSkyLight() {
        var skyLit = LightingDoctrineCalculator.assess(0, 15, false, "crave");
        var blockLit = LightingDoctrineCalculator.assess(12, 3, false, "crave");

        assertEquals(15, skyLit.effectiveLight());
        assertEquals(LightingDoctrineCalculator.LightBand.BRIGHT, skyLit.band());
        assertTrue(skyLit.compliant());
        assertEquals(12, blockLit.effectiveLight());
        assertTrue(blockLit.compliant());
    }

    @Test
    void nightIgnoresSkyLightAndUsesOnlyBlockLight() {
        var darkNight = LightingDoctrineCalculator.assess(0, 15, true, "crave");
        var dimNight = LightingDoctrineCalculator.assess(6, 15, true, "dim");
        var brightNight = LightingDoctrineCalculator.assess(8, 0, true, "crave");

        assertEquals(0, darkNight.effectiveLight());
        assertEquals(LightingDoctrineCalculator.LightBand.NO_LIGHT, darkNight.band());
        assertEquals(false, darkNight.compliant());
        assertEquals(6, dimNight.effectiveLight());
        assertTrue(dimNight.compliant());
        assertEquals(8, brightNight.effectiveLight());
        assertTrue(brightNight.compliant());
    }

    @Test
    void thresholdSevenIsNeutralForBothDimAndCrave() {
        var dim = LightingDoctrineCalculator.assess(7, 15, true, "dim");
        var crave = LightingDoctrineCalculator.assess(7, 0, false, "crave");
        var neutral = LightingDoctrineCalculator.assess(7, 7, false, "neutral");

        assertEquals(LightingDoctrineCalculator.LightBand.THRESHOLD, dim.band());
        assertNull(dim.compliant());
        assertNull(crave.compliant());
        assertNull(neutral.compliant());
    }

    @Test
    void zeroIsNoLightAndRemainsBelowDimAttitudesThreshold() {
        var noLight = LightingDoctrineCalculator.assess(0, 0, false, "dim");

        assertEquals(0, noLight.effectiveLight());
        assertEquals(LightingDoctrineCalculator.LightBand.NO_LIGHT, noLight.band());
        assertTrue(noLight.compliant());
    }
}
