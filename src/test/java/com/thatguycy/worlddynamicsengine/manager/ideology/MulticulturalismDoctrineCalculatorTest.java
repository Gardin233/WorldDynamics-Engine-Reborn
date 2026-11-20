package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MulticulturalismDoctrineCalculatorTest {
    @Test
    void prejudiceAttitudesAreViolatedOncePerDifferentFaithResident() {
        var assessment = MulticulturalismDoctrineCalculator.assess(
                "namespace:wder/multiculturalism/strong_prejudice", 4);

        assertEquals(false, assessment.compliant());
        assertEquals(4, assessment.differentFaithResidents());
    }

    @Test
    void approvingAttitudesComplyOncePerDifferentFaithResident() {
        var assessment = MulticulturalismDoctrineCalculator.assess("noble", 3);

        assertEquals(true, assessment.compliant());
        assertEquals(3, assessment.differentFaithResidents());
    }

    @Test
    void neutralAttitudeAndHomogeneousTownHaveNoMoodOutcome() {
        assertEquals(MulticulturalismDoctrineCalculator.Assessment.none(),
                MulticulturalismDoctrineCalculator.assess("neutral", 5));
        assertEquals(MulticulturalismDoctrineCalculator.Assessment.none(),
                MulticulturalismDoctrineCalculator.assess("strong_prejudice", 0));
    }
}
