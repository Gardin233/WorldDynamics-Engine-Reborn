package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PhantomMigrationCompositionTest {
    @Test
    void phantomCountScalesWithStrengthAndHonorsMinimumAndMaximum() {
        assertEquals(3, PhantomMigrationComposition.phantomCount(1, 0.5D, 3, 40));
        assertEquals(10, PhantomMigrationComposition.phantomCount(20, 0.5D, 3, 40));
        assertEquals(40, PhantomMigrationComposition.phantomCount(100, 0.5D, 3, 40));
    }

    @Test
    void invalidStrengthOrRateDisablesMigration() {
        assertEquals(0, PhantomMigrationComposition.phantomCount(0, 0.5D, 3, 40));
        assertEquals(0, PhantomMigrationComposition.phantomCount(10, 0.0D, 3, 40));
        assertEquals(0, PhantomMigrationComposition.phantomCount(10, Double.NaN, 3, 40));
        assertEquals(0, PhantomMigrationComposition.phantomCount(10, 0.5D, 3, 0));
    }
}
