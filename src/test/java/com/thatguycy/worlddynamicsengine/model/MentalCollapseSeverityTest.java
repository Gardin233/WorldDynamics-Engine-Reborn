package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thatguycy.worlddynamicsengine.manager.ideology.MentalCollapseManager;
import org.junit.jupiter.api.Test;

class MentalCollapseSeverityTest {
    @Test
    void remainingMoodSelectsTheExpectedSeverityAtEachBoundary() {
        assertEquals(MentalCollapseManager.Severity.MILD,
                MentalCollapseManager.getSeverityForMood(49, 34, 17));
        assertEquals(MentalCollapseManager.Severity.MILD,
                MentalCollapseManager.getSeverityForMood(34, 34, 17));
        assertEquals(MentalCollapseManager.Severity.MODERATE,
                MentalCollapseManager.getSeverityForMood(33, 34, 17));
        assertEquals(MentalCollapseManager.Severity.MODERATE,
                MentalCollapseManager.getSeverityForMood(17, 34, 17));
        assertEquals(MentalCollapseManager.Severity.SEVERE,
                MentalCollapseManager.getSeverityForMood(16, 34, 17));
        assertEquals(MentalCollapseManager.Severity.SEVERE,
                MentalCollapseManager.getSeverityForMood(0, 34, 17));
    }
}
