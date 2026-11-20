package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoughingItDoctrineCalculatorTest {
    @Test
    void acceptingAttitudeCompliesWhenSleepingOutdoors() {
        assertTrue(RoughingItDoctrineCalculator.assessOutdoorSleep(
                "namespace:wder/roughing_it/accept", true));
    }

    @Test
    void indoorSleepAndOtherAttitudesDoNotTrigger() {
        assertNull(RoughingItDoctrineCalculator.assessOutdoorSleep("accept", false));
        assertNull(RoughingItDoctrineCalculator.assessOutdoorSleep("neutral", true));
        assertNull(RoughingItDoctrineCalculator.assessOutdoorSleep(null, true));
    }
}
