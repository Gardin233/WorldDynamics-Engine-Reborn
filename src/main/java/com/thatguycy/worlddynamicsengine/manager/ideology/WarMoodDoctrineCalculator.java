package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;

/** Maps the war doctrine's attitude to its mood outcome when a war starts. */
public final class WarMoodDoctrineCalculator {
    private WarMoodDoctrineCalculator() {}

    public static Boolean assessWarEntry(String attitudeId) {
        return switch (localId(attitudeId)) {
            case "want" -> true;
            case "hate" -> false;
            default -> null;
        };
    }

    public static Boolean assessLongPeace(String attitudeId) {
        return switch (localId(attitudeId)) {
            case "want" -> false;
            case "hate" -> true;
            default -> null;
        };
    }

    private static String localId(String attitudeId) {
        if (attitudeId == null || attitudeId.isBlank()) return "";
        String normalized = attitudeId.trim().toLowerCase(Locale.ROOT);
        int separator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }
}
