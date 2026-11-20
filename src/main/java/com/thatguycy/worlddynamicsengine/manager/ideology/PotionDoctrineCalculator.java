package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import org.bukkit.potion.PotionEffectTypeCategory;

/** Classifies consumed potions against the configured potion-doctrine attitudes. */
public final class PotionDoctrineCalculator {
    private static final Set<String> INEBRIATION_ATTITUDES = Set.of("medical_or_social", "required");

    public record EffectProfile(boolean beneficial, boolean debuff) {}

    public record Assessment(Boolean compliant, boolean earnsInebriationBonus) {}

    private PotionDoctrineCalculator() {}

    public static EffectProfile inspectEffects(Collection<PotionEffectTypeCategory> categories) {
        boolean beneficial = false;
        boolean debuff = false;
        if (categories != null) {
            for (PotionEffectTypeCategory category : categories) {
                if (category == PotionEffectTypeCategory.BENEFICIAL) beneficial = true;
                if (category == PotionEffectTypeCategory.HARMFUL) debuff = true;
            }
        }
        return new EffectProfile(beneficial, debuff);
    }

    public static boolean matchesInebriationPotion(String vanillaPotionType, String craftEngineItemId,
                                                   Set<String> vanillaPotionTypes,
                                                   Set<String> craftEngineItems) {
        return matchesCraftEngineId(vanillaPotionTypes, vanillaPotionType)
                || matchesCraftEngineId(craftEngineItems, craftEngineItemId);
    }

    public static boolean matchesCraftEngineId(Set<String> configuredIds, String itemId) {
        return containsNormalized(configuredIds, itemId);
    }

    public static Assessment assess(String attitude, EffectProfile effects, boolean inebriationPotion) {
        String localId = localId(attitude);
        boolean debuff = effects != null && effects.debuff();
        Boolean compliant = switch (localId) {
            case "forbidden" -> false;
            case "medical_only" -> effects != null && effects.beneficial() && !debuff;
            case "medical_or_social", "required" -> true;
            default -> null;
        };
        return new Assessment(compliant,
                inebriationPotion && INEBRIATION_ATTITUDES.contains(localId));
    }

    private static boolean containsNormalized(Set<String> values, String value) {
        return values != null && value != null
                && values.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
    }
}
