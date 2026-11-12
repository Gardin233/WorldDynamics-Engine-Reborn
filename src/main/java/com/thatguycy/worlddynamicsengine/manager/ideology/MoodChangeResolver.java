package com.thatguycy.worlddynamicsengine.manager.ideology;

/** Resolves the per-weight mood change for one doctrine attitude and outcome. */
@FunctionalInterface
public interface MoodChangeResolver {
    int getPerWeightChange(String doctrineId, String attitudeId, boolean compliant);
}
