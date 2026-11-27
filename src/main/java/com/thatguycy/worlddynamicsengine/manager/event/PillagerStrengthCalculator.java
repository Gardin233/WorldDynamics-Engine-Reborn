package com.thatguycy.worlddynamicsengine.manager.event;

import java.math.BigDecimal;

/** Legacy step-based helper retained for compatibility; runtime events use StrengthFormulaEvaluator. */
@Deprecated
public final class PillagerStrengthCalculator {
    private PillagerStrengthCalculator() {
    }

    public static int calculate(double wealth, double wealthPerStep, int baseCount,
                                int pillagersPerStep, int minimum, int maximum) {
        int safeMinimum = Math.max(1, minimum);
        int safeMaximum = Math.max(safeMinimum, maximum);
        int safeBase = Math.max(0, baseCount);
        int safePerStep = Math.max(0, pillagersPerStep);
        double safeWealth = Double.isFinite(wealth) ? Math.max(0.0D, wealth) : 0.0D;
        double safeWealthPerStep = Double.isFinite(wealthPerStep) ? Math.max(0.0D, wealthPerStep) : 0.0D;

        long count = safeBase;
        if (safeWealthPerStep > 0.0D && safePerStep > 0) {
            BigDecimal steps = BigDecimal.valueOf(safeWealth)
                    .divideToIntegralValue(BigDecimal.valueOf(safeWealthPerStep));
            try {
                count = Math.addExact(count, Math.multiplyExact(steps.longValueExact(), safePerStep));
            } catch (ArithmeticException ignored) {
                count = safeMaximum;
            }
        }
        return (int) Math.max(safeMinimum, Math.min(safeMaximum, count));
    }
}
