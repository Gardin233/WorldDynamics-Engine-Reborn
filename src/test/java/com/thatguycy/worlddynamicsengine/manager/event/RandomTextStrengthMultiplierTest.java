package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RandomTextStrengthMultiplierTest {
    @Test
    void rollsDeterministicallyWithinConfiguredBounds() {
        double first = RandomTextStrengthMultiplier.roll(0.75D, 1.25D, new Random(42L));
        double repeated = RandomTextStrengthMultiplier.roll(0.75D, 1.25D, new Random(42L));

        assertEquals(first, repeated);
        assertTrue(first >= 0.75D && first < 1.25D);
    }

    @Test
    void normalizesReversedAndInvalidBounds() {
        assertEquals(1.0D,
                RandomTextStrengthMultiplier.roll(1.0D, 1.0D, new Random(1L)));
        assertTrue(RandomTextStrengthMultiplier.roll(2.0D, 1.0D, new Random(1L)) >= 1.0D);
        assertTrue(RandomTextStrengthMultiplier.roll(Double.NaN, Double.NaN, new Random(1L))
                >= 0.75D);
    }
}
