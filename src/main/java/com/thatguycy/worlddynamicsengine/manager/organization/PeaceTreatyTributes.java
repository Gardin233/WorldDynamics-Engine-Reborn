package com.thatguycy.worlddynamicsengine.manager.organization;

import com.thatguycy.worlddynamicsengine.model.WDEnation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Owns recurring tribute obligations; the caller provides the treasury and saves changes. */
final class PeaceTreatyTributes {
    private static final String KEY = "peaceTreatyTributes";
    private static final String REASON = "WorldDynamicsEngine peace treaty tribute";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private PeaceTreatyTributes() {}

    static boolean migrate(Collection<WDEnation> nations) {
        boolean changed = false;
        for (WDEnation nation : nations) {
            Object raw = nation.getSetting(KEY);
            if (!(raw instanceof Collection<?> obligations)) continue;
            List<Map<String, Object>> migrated = new ArrayList<>();
            boolean nationChanged = false;
            for (Object entry : obligations) {
                if (!(entry instanceof Map<?, ?> values)) continue;
                Map<String, Object> obligation = new LinkedHashMap<>();
                values.forEach((key, value) -> {
                    if (key != null && value != null && !"taxRatePercent".equals(String.valueOf(key))) {
                        obligation.put(String.valueOf(key), value);
                    }
                });
                if (values.containsKey("taxRatePercent")) {
                    obligation.putIfAbsent("fixedAmount", "0");
                    nationChanged = true;
                }
                migrated.add(obligation);
            }
            if (nationChanged) {
                nation.setSetting(KEY, migrated);
                changed = true;
            }
        }
        return changed;
    }

    static void add(WDEnation nation, String beneficiary, BigDecimal amount, long appliedAt) {
        var obligations = read(nation);
        Map<String, Object> obligation = new LinkedHashMap<>();
        obligation.put("beneficiaryNation", beneficiary);
        obligation.put("fixedAmount", amount.toPlainString());
        obligation.put("appliedAt", appliedAt);
        obligations.add(obligation);
        nation.setSetting(KEY, obligations);
    }

    static BigDecimal parseAmount(Object rawValue) {
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(rawValue));
            return amount.signum() < 0 || amount.scale() > 8 || amount.compareTo(MAX_AMOUNT) > 0
                    ? BigDecimal.ZERO : amount;
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    @FunctionalInterface
    interface TreasuryOperation<T> {
        boolean apply(T nation, BigDecimal amount, String reason);
    }

    static <T> boolean collect(Collection<WDEnation> nations, Function<String, T> resolve,
                               TreasuryOperation<T> withdraw, TreasuryOperation<T> deposit) {
        boolean changed = false;
        for (WDEnation nation : nations) {
            var obligations = read(nation);
            if (obligations.isEmpty()) continue;
            T source = resolve.apply(nation.getNationName());
            if (source == null) continue;
            for (var obligation : obligations) {
                String name = String.valueOf(obligation.getOrDefault("beneficiaryNation", ""));
                if (name.isBlank()) continue;
                T beneficiary = resolve.apply(name);
                if (beneficiary == null) continue;
                BigDecimal amount = parseAmount(obligation.get("fixedAmount"));
                if (amount.signum() <= 0) continue;
                if (withdraw.apply(source, amount, REASON)) {
                    // Preserve the existing save-on-debit contract, even if the deposit fails.
                    deposit.apply(beneficiary, amount, REASON);
                    changed = true;
                }
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> read(WDEnation nation) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (nation != null && nation.getSetting(KEY) instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry instanceof Map<?, ?> map) values.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return values;
    }
}
