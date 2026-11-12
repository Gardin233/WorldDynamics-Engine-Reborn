package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thatguycy.worlddynamicsengine.manager.ideology.MoodManager;
import org.junit.jupiter.api.Test;

class MoodChanceTest {
    @Test
    void chanceIsZeroAtNeutralAndTwoPercentPerPointFromFifty() {
        assertEquals(0.0, MoodManager.getOutcomeChancePercent(50), 0.0001);
        assertEquals(2.0, MoodManager.getOutcomeChancePercent(49), 0.0001);
        assertEquals(2.0, MoodManager.getOutcomeChancePercent(51), 0.0001);
        assertEquals(100.0, MoodManager.getOutcomeChancePercent(0), 0.0001);
        assertEquals(100.0, MoodManager.getOutcomeChancePercent(100), 0.0001);
    }

    @Test
    void chanceCalculationClampsInvalidMoodValues() {
        assertEquals(100.0, MoodManager.getOutcomeChancePercent(-20), 0.0001);
        assertEquals(100.0, MoodManager.getOutcomeChancePercent(120), 0.0001);
    }
}
