package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StrengthFormulaEvaluatorTest {
    @Test
    void evaluatesArithmeticAndCommonFunctions() {
        assertEquals(4, StrengthFormulaEvaluator.calculatePoints(
                "max(2, floor(wealth / 10000)) * 2", 25_001, 100));
        assertEquals(6, StrengthFormulaEvaluator.calculatePoints(
                "ceil(wealth / 10000) + abs(-1)", 40_001, 100));
        assertEquals(2, StrengthFormulaEvaluator.calculatePoints(
                "min(wealth / 1000, 2.9)", 100_000, 100));
    }

    @Test
    void roundsDownAndClampsToConfiguredPointLimit() {
        assertEquals(3, StrengthFormulaEvaluator.calculatePoints("wealth / 3", 10, 100));
        assertEquals(12, StrengthFormulaEvaluator.calculatePoints("wealth * 2", 100, 12));
        assertEquals(0, StrengthFormulaEvaluator.calculatePoints("-wealth", 10, 12));
    }

    @Test
    void reducesTownStrengthByOnlineResidentRatio() {
        assertEquals(10, StrengthFormulaEvaluator.scalePointsForOnlinePopulation(20, 5, 10));
        assertEquals(1, StrengthFormulaEvaluator.scalePointsForOnlinePopulation(10, 1, 6));
        assertEquals(0, StrengthFormulaEvaluator.scalePointsForOnlinePopulation(10, 0, 6));
        assertEquals(10, StrengthFormulaEvaluator.scalePointsForOnlinePopulation(10, 12, 6));
    }

    @Test
    void appliesPositiveEventMultiplierAndRoundsUp() {
        assertEquals(3, StrengthFormulaEvaluator.applyMultiplier(2, 1.25D));
        assertEquals(2, StrengthFormulaEvaluator.applyMultiplier(2, 1.0D));
        assertEquals(0, StrengthFormulaEvaluator.applyMultiplier(2, Double.NaN));
    }

    @Test
    void rejectsInvalidAndNonFiniteFormulas() {
        assertThrows(IllegalArgumentException.class,
                () -> StrengthFormulaEvaluator.calculatePoints("wealth / 0", 10, 12));
        assertThrows(IllegalArgumentException.class,
                () -> StrengthFormulaEvaluator.calculatePoints("wealth + unknown", 10, 12));
        assertThrows(IllegalArgumentException.class,
                () -> StrengthFormulaEvaluator.calculatePoints("1e999", 10, 12));
    }
}
