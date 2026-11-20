package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;
import org.bukkit.Material;

/** Classifies animal feeding and killing against animal-resonance attitudes. */
public final class AnimalResonanceDoctrineCalculator {
    private AnimalResonanceDoctrineCalculator() {}

    public static Boolean assessFeeding(String attitude) {
        return "powerful".equals(localId(attitude)) ? Boolean.TRUE : null;
    }

    public static Boolean assessKilling(String attitude) {
        return "powerful".equals(localId(attitude)) ? Boolean.FALSE : null;
    }

    public static boolean itemWasConsumed(Material beforeType, int beforeAmount,
                                          Material afterType, int afterAmount) {
        if (beforeType == null || beforeType == Material.AIR || beforeAmount <= 0) return false;
        return afterType == null || afterType == Material.AIR
                || afterType != beforeType || afterAmount < beforeAmount;
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
    }
}
