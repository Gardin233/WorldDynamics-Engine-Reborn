package com.thatguycy.worlddynamicsengine.manager.economy;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Pure validation and attitude rules for player gifts and alms. */
public final class PlayerGiftCalculator {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final Set<String> KINDNESS_ATTITUDES = Set.of("required", "important", "joy");

    private PlayerGiftCalculator() {}

    public static boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.scale() <= 8
                && amount.compareTo(MAX_AMOUNT) <= 0;
    }

    public static boolean recipientIsBelowHalfWealth(BigDecimal recipientWealth, BigDecimal giverWealth) {
        BigDecimal recipient = recipientWealth == null ? BigDecimal.ZERO : recipientWealth.max(BigDecimal.ZERO);
        BigDecimal giver = giverWealth == null ? BigDecimal.ZERO : giverWealth.max(BigDecimal.ZERO);
        return recipient.multiply(BigDecimal.valueOf(2L)).compareTo(giver) < 0;
    }

    public static boolean supportsKindness(String attitude) {
        if (attitude == null || attitude.isBlank()) return false;
        int separator = attitude.lastIndexOf('/');
        String localId = (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
        return KINDNESS_ATTITUDES.contains(localId);
    }
}
