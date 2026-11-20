package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;

/** Resolves whether a field battle is favored by the configured doctrine attitude. */
public final class FieldBattleDoctrineCalculator {
    private FieldBattleDoctrineCalculator() {}

    public static boolean earnsWildernessBonus(String attitudeId, boolean wilderness) {
        if (!wilderness || attitudeId == null || attitudeId.isBlank()) return false;
        String normalized = attitudeId.trim().toLowerCase(Locale.ROOT);
        int separator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        String localId = separator < 0 ? normalized : normalized.substring(separator + 1);
        return localId.equals("enjoy");
    }
}
