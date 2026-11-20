package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProselytizingDoctrineCalculatorTest {
    @Test
    void computesSharedBonusFromCompliantAttitudeValue() {
        assertEquals(3, ProselytizingDoctrineCalculator.calculateJoinBonus(3, 1, 2));
        assertEquals(12, ProselytizingDoctrineCalculator.calculateJoinBonus(3, 4, 5));
    }

    @Test
    void doesNotRewardFirstFollowerOrNonPositiveAttitude() {
        assertEquals(0, ProselytizingDoctrineCalculator.calculateJoinBonus(1, 1, 1));
        assertEquals(0, ProselytizingDoctrineCalculator.calculateJoinBonus(0, 1, 2));
        assertEquals(0, ProselytizingDoctrineCalculator.calculateJoinBonus(1, 0, 2));
    }

    @Test
    void capsBonusToMoodRange() {
        assertEquals(100, ProselytizingDoctrineCalculator.calculateJoinBonus(10, 100, 2));
    }
}
