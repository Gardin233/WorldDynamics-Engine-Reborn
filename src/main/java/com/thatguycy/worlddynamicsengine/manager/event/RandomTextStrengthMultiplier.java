package com.thatguycy.worlddynamicsengine.manager.event;

import java.util.Random;

/** Rolls a bounded multiplier for random text event strength. */
public final class RandomTextStrengthMultiplier {
    private static final double DEFAULT_MINIMUM = 0.75D;
    private static final double DEFAULT_MAXIMUM = 1.25D;

    private RandomTextStrengthMultiplier() {
    }

    public static double roll(double minimum, double maximum, Random random) {
        double min = Double.isFinite(minimum) ? Math.max(0.0D, minimum) : DEFAULT_MINIMUM;
        double max = Double.isFinite(maximum) ? Math.max(0.0D, maximum) : DEFAULT_MAXIMUM;
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }
        if (min == max) {
            return min;
        }
        Random source = random == null ? new Random() : random;
        return min + source.nextDouble() * (max - min);
    }
}
