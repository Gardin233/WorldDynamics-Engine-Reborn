package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PillagerStrengthCalculatorTest {
    @Test
    void appliesConfiguredWealthFormulaAndBounds() {
        assertEquals(2, PillagerStrengthCalculator.calculate(0, 25_000, 2, 1, 1, 12));
        assertEquals(3, PillagerStrengthCalculator.calculate(49_999, 25_000, 2, 1, 1, 12));
        assertEquals(4, PillagerStrengthCalculator.calculate(50_000, 25_000, 2, 1, 1, 12));
        assertEquals(12, PillagerStrengthCalculator.calculate(1_000_000, 25_000, 2, 1, 1, 12));
    }

    @Test
    void handlesInvalidInputsWithoutExceedingBounds() {
        assertEquals(1, PillagerStrengthCalculator.calculate(Double.NaN, 0, -5, -1, 1, 12));
        assertEquals(5, PillagerStrengthCalculator.calculate(10_000, 100, 2, 1, 5, 3));
    }
}
