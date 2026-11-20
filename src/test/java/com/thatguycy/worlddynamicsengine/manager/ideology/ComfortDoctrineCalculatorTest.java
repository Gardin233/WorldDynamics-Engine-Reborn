package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ComfortDoctrineCalculatorTest {
    @Test
    void calculatesConfiguredCompliantBonus() {
        assertEquals(2, ComfortDoctrineCalculator.calculateCompliantBonus(2, 1));
        assertEquals(12, ComfortDoctrineCalculator.calculateCompliantBonus(3, 4));
    }

    @Test
    void ignoresNeutralOrNonPositiveValues() {
        assertEquals(0, ComfortDoctrineCalculator.calculateCompliantBonus(0, 1));
        assertEquals(0, ComfortDoctrineCalculator.calculateCompliantBonus(1, 0));
        assertEquals(0, ComfortDoctrineCalculator.calculateCompliantBonus(-1, 1));
    }

    @Test
    void capsBonusToMoodRange() {
        assertEquals(100, ComfortDoctrineCalculator.calculateCompliantBonus(10, 100));
    }
}
