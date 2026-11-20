package com.thatguycy.worlddynamicsengine.manager.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlayerGiftCalculatorTest {
    @Test
    void usesStrictHalfWealthBoundary() {
        assertTrue(PlayerGiftCalculator.recipientIsBelowHalfWealth(
                new BigDecimal("49.99"), new BigDecimal("100")));
        assertFalse(PlayerGiftCalculator.recipientIsBelowHalfWealth(
                new BigDecimal("50"), new BigDecimal("100")));
        assertFalse(PlayerGiftCalculator.recipientIsBelowHalfWealth(
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void acceptsOnlySupportedKindnessAttitudes() {
        assertTrue(PlayerGiftCalculator.supportsKindness("namespace:wder/kindness/required"));
        assertTrue(PlayerGiftCalculator.supportsKindness("important"));
        assertTrue(PlayerGiftCalculator.supportsKindness("joy"));
        assertFalse(PlayerGiftCalculator.supportsKindness("neutral"));
        assertFalse(PlayerGiftCalculator.supportsKindness("custom"));
    }

    @Test
    void validatesTransferAmountPrecisionAndBounds() {
        assertTrue(PlayerGiftCalculator.isValidAmount(new BigDecimal("0.00000001")));
        assertFalse(PlayerGiftCalculator.isValidAmount(BigDecimal.ZERO));
        assertFalse(PlayerGiftCalculator.isValidAmount(new BigDecimal("1.000000001")));
        assertFalse(PlayerGiftCalculator.isValidAmount(new BigDecimal("1000000000000.01")));
    }
}
