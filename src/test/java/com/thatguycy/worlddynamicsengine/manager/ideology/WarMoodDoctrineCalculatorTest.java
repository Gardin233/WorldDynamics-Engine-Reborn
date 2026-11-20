package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WarMoodDoctrineCalculatorTest {
    @Test
    void mapsWarAttitudesToTheirWarEntryOutcome() {
        assertEquals(Boolean.TRUE, WarMoodDoctrineCalculator.assessWarEntry("want"));
        assertEquals(Boolean.FALSE, WarMoodDoctrineCalculator.assessWarEntry("hate"));
        assertNull(WarMoodDoctrineCalculator.assessWarEntry("neutral"));
    }

    @Test
    void reversesWarAttitudesAfterLongPeace() {
        assertEquals(Boolean.FALSE, WarMoodDoctrineCalculator.assessLongPeace("want"));
        assertEquals(Boolean.TRUE, WarMoodDoctrineCalculator.assessLongPeace("hate"));
        assertNull(WarMoodDoctrineCalculator.assessLongPeace("neutral"));
    }

    @Test
    void acceptsNamespacedAttitudeIds() {
        assertEquals(Boolean.TRUE, WarMoodDoctrineCalculator.assessWarEntry(
                "namespace:wde/war/want"));
        assertEquals(Boolean.FALSE, WarMoodDoctrineCalculator.assessWarEntry(
                "namespace:wde/war/hate"));
    }

    @Test
    void ignoresUnknownOrMissingAttitudes() {
        assertNull(WarMoodDoctrineCalculator.assessWarEntry(null));
        assertNull(WarMoodDoctrineCalculator.assessWarEntry(""));
        assertNull(WarMoodDoctrineCalculator.assessWarEntry("unknown"));
    }
}
