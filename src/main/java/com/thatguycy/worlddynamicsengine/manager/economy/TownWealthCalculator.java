package com.thatguycy.worlddynamicsengine.manager.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure aggregation of a town's population, land, and resident account balances. */
public final class TownWealthCalculator {
    private TownWealthCalculator() {}

    public static BigDecimal calculate(int population, BigDecimal valuePerResident,
                                       int plotCount, BigDecimal valuePerPlot,
                                       Iterable<BigDecimal> residentBalances) {
        BigDecimal total = nonNegative(valuePerResident).multiply(BigDecimal.valueOf(Math.max(0, population)))
                .add(nonNegative(valuePerPlot).multiply(BigDecimal.valueOf(Math.max(0, plotCount))));
        if (residentBalances != null) {
            for (BigDecimal balance : residentBalances) {
                total = total.add(nonNegative(balance));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
