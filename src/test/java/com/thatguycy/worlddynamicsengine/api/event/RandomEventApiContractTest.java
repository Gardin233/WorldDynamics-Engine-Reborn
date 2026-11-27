package com.thatguycy.worlddynamicsengine.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RandomEventApiContractTest {
    @Test
    void eventDefaultsAreSuitableForOneShotEvents() {
        RandomEvent event = new RandomEvent() {
            @Override
            public String id() {
                return "test_event";
            }

            @Override
            public RandomEventOccurrence execute(RandomEventContext context) {
                return new RandomEventOccurrence() {};
            }
        };

        assertEquals(1.0D, event.defaultWeight());
        assertEquals(Double.NEGATIVE_INFINITY, event.minimumTownWealth());
        assertFalse(event.requiresStrengthPoints());
        assertEquals(1.0D, event.strengthMultiplier());
    }

    @Test
    void occurrenceDefaultsDoNotRemainActive() {
        RandomEventOccurrence occurrence = new RandomEventOccurrence() {};

        assertFalse(occurrence.isOngoing());
        assertFalse(occurrence.wasSuccessful());
        assertFalse(occurrence.finishesWhenSuccessful());
        assertTrue(occurrence.endsWhenTownEmpty());
    }
}
