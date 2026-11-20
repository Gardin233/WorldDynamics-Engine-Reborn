package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.List;

/** Maps current health and the pain attitude to one replaceable mood state. */
public final class PainMoodCalculator {
    public record MoodState(String source, int delta) {
        public static MoodState none() {
            return new MoodState("", 0);
        }
    }

    private PainMoodCalculator() {}

    public static MoodState calculate(double health, double maxHealth, String attitude,
                                      List<Double> thresholds, List<Integer> idealBonuses,
                                      List<Integer> neutralPenalties, int idealHealthyPenalty) {
        boolean ideal = "ideal".equalsIgnoreCase(attitude);
        boolean neutral = attitude == null || attitude.isBlank()
                || "none".equalsIgnoreCase(attitude);
        if (!ideal && !neutral || !Double.isFinite(health) || !Double.isFinite(maxHealth)
                || maxHealth <= 0 || thresholds == null || thresholds.isEmpty()) {
            return MoodState.none();
        }

        double healthRatio = Math.max(0, health) / maxHealth;
        Double highHealthThreshold = thresholds.getFirst();
        if (ideal && highHealthThreshold != null && healthRatio >= highHealthThreshold) {
            int amount = Math.max(0, Math.min(100, idealHealthyPenalty));
            return amount == 0 ? MoodState.none()
                    : new MoodState("pain:ideal:unease", -amount);
        }

        for (int index = thresholds.size() - 1; index >= 0; index--) {
            Double threshold = thresholds.get(index);
            if (threshold == null || !Double.isFinite(threshold) || threshold <= 0
                    || threshold >= 1 || healthRatio >= threshold) {
                continue;
            }
            List<Integer> values = ideal ? idealBonuses : neutralPenalties;
            if (values == null || index >= values.size() || values.get(index) == null) {
                return MoodState.none();
            }
            int amount = Math.max(0, Math.min(100, values.get(index)));
            if (amount == 0) return MoodState.none();
            String source = ideal
                    ? "pain:ideal:sweet_pain:" + (int) Math.round(threshold * 100)
                    : "pain:none:under_" + (int) Math.round(threshold * 100);
            return new MoodState(source, ideal ? amount : -amount);
        }
        return MoodState.none();
    }
}
