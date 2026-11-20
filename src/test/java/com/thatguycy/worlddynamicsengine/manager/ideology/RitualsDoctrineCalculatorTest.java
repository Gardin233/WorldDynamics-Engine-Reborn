package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RitualsDoctrineCalculatorTest {
    @Test
    void promoteAttitudeIsRewardedForParticipationAndPenalizedForAbsence() {
        assertTrue(RitualsDoctrineCalculator.assessParticipation(
                "namespace:wder/rituals/promote"));
        assertFalse(RitualsDoctrineCalculator.assessLongAbsence(
                "namespace:wder/rituals/promote"));
    }

    @Test
    void opposingAttitudesViolateParticipationButAreNotPenalizedForAbsence() {
        for (String attitude : new String[] {"disgust", "abhor"}) {
            assertFalse(RitualsDoctrineCalculator.assessParticipation(attitude));
            assertNull(RitualsDoctrineCalculator.assessLongAbsence(attitude));
        }
    }

    @Test
    void unknownAndNeutralAttitudesDoNotProduceOutcomes() {
        assertNull(RitualsDoctrineCalculator.assessParticipation(null));
        assertNull(RitualsDoctrineCalculator.assessParticipation("none"));
        assertNull(RitualsDoctrineCalculator.assessLongAbsence("custom"));
    }
}
