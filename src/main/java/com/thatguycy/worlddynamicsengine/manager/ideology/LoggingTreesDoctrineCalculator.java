package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

/** Matches configured tree resources and classifies logging and planting attitudes. */
public final class LoggingTreesDoctrineCalculator {
    private static final Set<String> LOGGING_VIOLATION_ATTITUDES = Set.of(
            "taboo", "fear", "disgust");

    private LoggingTreesDoctrineCalculator() {}

    public static boolean isConfiguredWood(Material material, String craftEngineBlockId,
                                            Set<Material> vanillaWood,
                                            Set<String> craftEngineWood) {
        if (craftEngineBlockId != null) {
            return craftEngineWood.contains(normalize(craftEngineBlockId));
        }
        return material != null && vanillaWood.contains(material);
    }

    public static boolean isConfiguredSapling(Material itemMaterial, String craftEngineItemId,
                                               Material placedMaterial, String craftEngineBlockId,
                                               Set<Material> vanillaSaplings,
                                               Set<String> craftEngineSaplings) {
        if (craftEngineItemId != null || craftEngineBlockId != null) {
            return craftEngineSaplings.contains(normalize(craftEngineItemId))
                    || craftEngineSaplings.contains(normalize(craftEngineBlockId));
        }
        return vanillaSaplings.contains(itemMaterial) || vanillaSaplings.contains(placedMaterial);
    }

    public static Boolean assessLogging(String attitude) {
        String localId = localId(attitude);
        if (LOGGING_VIOLATION_ATTITUDES.contains(localId)) return false;
        if ("neutral".equals(localId)) return true;
        return null;
    }

    public static Boolean assessPlanting(String attitude) {
        String localId = localId(attitude);
        if ("crave".equals(localId) || "neutral".equals(localId)) return true;
        return null;
    }

    private static String localId(String id) {
        if (id == null || id.isBlank()) return "";
        int separator = id.lastIndexOf('/');
        return normalize(separator < 0 ? id : id.substring(separator + 1));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
