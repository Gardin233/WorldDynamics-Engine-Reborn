package com.thatguycy.worlddynamicsengine.manager.ideology;

/** Calculates an attitude's configured positive mood change. */
public final class ComfortDoctrineCalculator {
    private ComfortDoctrineCalculator() {}

    public static int calculateCompliantBonus(int attitudeWeight, int compliantMoodChange) {
        if (attitudeWeight <= 0 || compliantMoodChange <= 0) return 0;
        long bonus = (long) attitudeWeight * compliantMoodChange;
        return (int) Math.min(100L, bonus);
    }
}
