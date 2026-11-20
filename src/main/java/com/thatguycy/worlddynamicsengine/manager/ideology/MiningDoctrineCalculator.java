package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

/** Matches configured vanilla or CraftEngine ores and classifies mining attitudes. */
public final class MiningDoctrineCalculator {
    private static final Set<String> MINING_VIOLATION_ATTITUDES = Set.of(
            "taboo", "fear", "disgust");

    private MiningDoctrineCalculator() {}

    public static boolean isConfiguredOre(Material material, String craftEngineId,
                                          Set<Material> vanillaOres, Set<String> craftEngineOres) {
        if (craftEngineId != null) {
            return craftEngineOres.contains(normalize(craftEngineId));
        }
        return material != null && vanillaOres.contains(material);
    }

    public static Boolean assess(String attitude) {
        String localId = localId(attitude);
        if (MINING_VIOLATION_ATTITUDES.contains(localId)) return false;
        if ("neutral".equals(localId)) return true;
        return null;
    }

    private static String localId(String id) {
        if (id == null || id.isBlank()) return "";
        int separator = id.lastIndexOf('/');
        return normalize(separator < 0 ? id : id.substring(separator + 1));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
