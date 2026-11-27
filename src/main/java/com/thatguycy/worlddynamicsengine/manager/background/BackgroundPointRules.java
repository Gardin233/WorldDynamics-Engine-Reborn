package com.thatguycy.worlddynamicsengine.manager.background;

import java.util.Map;

/** Shared validation rules for the five skill points assigned by each background stage. */
public final class BackgroundPointRules {
    public static final int REQUIRED_POINTS = 5;

    private BackgroundPointRules() {
    }

    public static boolean isValid(Map<String, Integer> skillPoints) {
        if (skillPoints == null || skillPoints.isEmpty()) {
            return false;
        }

        int total = 0;
        for (Map.Entry<String, Integer> entry : skillPoints.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() < 0) {
                return false;
            }
            total += entry.getValue();
            if (total > REQUIRED_POINTS) {
                return false;
            }
        }
        return total == REQUIRED_POINTS;
    }
}
