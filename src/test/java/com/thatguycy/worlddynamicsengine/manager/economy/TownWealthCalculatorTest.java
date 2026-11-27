package com.thatguycy.worlddynamicsengine.manager.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownWealthCalculatorTest {
    @Test
    void combinesPopulationLandAndResidentBalances() {
        BigDecimal wealth = TownWealthCalculator.calculate(
                10,
                new BigDecimal("15.00"),
                12,
                new BigDecimal("25.50"),
                List.of(new BigDecimal("100.25"), new BigDecimal("49.75")));

        assertEquals(new BigDecimal("606.00"), wealth);
    }

    @Test
    void clampsNegativeInputsAndRoundsToCurrencyPrecision() {
        BigDecimal wealth = TownWealthCalculator.calculate(
                -2,
                new BigDecimal("100.00"),
                4,
                new BigDecimal("-2.00"),
                List.of(new BigDecimal("-10.00"), new BigDecimal("2.345")));

        assertEquals(new BigDecimal("2.35"), wealth);
    }
}
