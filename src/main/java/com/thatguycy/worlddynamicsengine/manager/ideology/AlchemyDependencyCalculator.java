package com.thatguycy.worlddynamicsengine.manager.ideology;

/** Maps the current alchemical satisfaction to its displayed dependency stage. */
public final class AlchemyDependencyCalculator {
    public enum Stage {
        SATISFIED("satisfied"),
        DEFICIENT("deficient"),
        CRAVING("craving"),
        MADNESS("madness");

        private final String id;

        Stage(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private AlchemyDependencyCalculator() {}

    public static Stage stageAt(double satisfaction, int satisfiedMinimum,
                                int deficientMinimum, int cravingMinimum) {
        if (satisfaction >= clampThreshold(satisfiedMinimum)) return Stage.SATISFIED;
        if (satisfaction >= clampThreshold(deficientMinimum)) return Stage.DEFICIENT;
        if (satisfaction >= clampThreshold(cravingMinimum)) return Stage.CRAVING;
        return Stage.MADNESS;
    }

    public static int dependencyChancePercent(int consecutiveDrinks, int baseChancePercent,
                                              int increasePerDrinkPercent, int maximumChancePercent) {
        int maximum = clampThreshold(maximumChancePercent);
        int base = Math.min(maximum, clampThreshold(baseChancePercent));
        int drinkCount = Math.max(1, consecutiveDrinks);
        long chance = (long) base
                + (long) (drinkCount - 1) * Math.max(0, increasePerDrinkPercent);
        return (int) Math.min(maximum, chance);
    }

    private static int clampThreshold(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
