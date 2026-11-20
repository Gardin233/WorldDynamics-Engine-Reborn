package com.thatguycy.worlddynamicsengine.manager.ideology;

/** Calculates the shared positive mood reward when an ideology gains a follower. */
public final class ProselytizingDoctrineCalculator {
    private ProselytizingDoctrineCalculator() {}

    public static int calculateJoinBonus(int attitudeWeight, int compliantMoodChange, int followerCount) {
        if (followerCount < 2 || attitudeWeight <= 0 || compliantMoodChange <= 0) return 0;
        long bonus = (long) attitudeWeight * compliantMoodChange;
        return (int) Math.min(100L, bonus);
    }
}
