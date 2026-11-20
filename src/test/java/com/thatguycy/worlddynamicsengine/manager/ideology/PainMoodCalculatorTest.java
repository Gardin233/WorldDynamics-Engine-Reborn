package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PainMoodCalculatorTest {
    private static final List<Double> THRESHOLDS = List.of(0.75, 0.50, 0.25, 0.10);
    private static final List<Integer> STEPS = List.of(2, 4, 6, 10);

    @Test
    void idealAttitudeReversesLowHealthPenaltiesAndPenalizesHealthyState() {
        assertMood(20, "ideal", "pain:ideal:unease", -2);
        assertMood(15, "ideal", "pain:ideal:unease", -2);
        assertMood(14.98, "ideal", "pain:ideal:sweet_pain:75", 2);
        assertMood(10, "ideal", "pain:ideal:sweet_pain:75", 2);
        assertMood(9.98, "ideal", "pain:ideal:sweet_pain:50", 4);
        assertMood(5, "ideal", "pain:ideal:sweet_pain:50", 4);
        assertMood(4.98, "ideal", "pain:ideal:sweet_pain:25", 6);
        assertMood(2, "ideal", "pain:ideal:sweet_pain:25", 6);
        assertMood(1.98, "ideal", "pain:ideal:sweet_pain:10", 10);
    }

    @Test
    void neutralAndUnaffiliatedPlayersReceiveOnlyTheConfiguredLowHealthPenalties() {
        assertMood(20, "none", "", 0);
        assertMood(15, "none", "", 0);
        assertMood(14.98, "none", "pain:none:under_75", -2);
        assertMood(9.98, "none", "pain:none:under_50", -4);
        assertMood(4.98, null, "pain:none:under_25", -6);
        assertMood(1.98, null, "pain:none:under_10", -10);
    }

    @Test
    void customThresholdsAndValuesAreAppliedWithoutStackingTiers() {
        var state = PainMoodCalculator.calculate(15, 20, "ideal",
                List.of(0.80, 0.60, 0.30, 0.05),
                List.of(1, 3, 5, 8), List.of(1, 3, 5, 8), 7);

        assertEquals("pain:ideal:sweet_pain:80", state.source());
        assertEquals(1, state.delta());
    }

    @Test
    void unsupportedAttitudeAndInvalidHealthHaveNoPainMoodState() {
        var unsupported = PainMoodCalculator.calculate(1, 20, "disgust",
                THRESHOLDS, STEPS, STEPS, 2);
        var invalidHealth = PainMoodCalculator.calculate(1, 0, "ideal",
                THRESHOLDS, STEPS, STEPS, 2);

        assertEquals(PainMoodCalculator.MoodState.none(), unsupported);
        assertEquals(PainMoodCalculator.MoodState.none(), invalidHealth);
    }

    private static void assertMood(double health, String attitude, String source, int delta) {
        var state = PainMoodCalculator.calculate(
                health, 20, attitude, THRESHOLDS, STEPS, STEPS, 2);
        assertEquals(source, state.source());
        assertEquals(delta, state.delta());
    }
}
