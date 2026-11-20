package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;

/** Resolves whether a raiding attitude is satisfied by economic war terms or prolonged peace. */
public final class RaidingDoctrineCalculator {
    private RaidingDoctrineCalculator() {}

    public static boolean hasQualifyingEconomicTerms(boolean tribute, int reparationsPercent) {
        return tribute || reparationsPercent >= 5;
    }

    public static Boolean assessEconomicWar(String attitudeId, boolean economicTermsDemanded) {
        String localId = localId(attitudeId);
        return switch (localId) {
            case "valued", "required" -> economicTermsDemanded;
            default -> null;
        };
    }

    public static Boolean assessLongPeace(String attitudeId) {
        return switch (localId(attitudeId)) {
            case "valued", "required" -> false;
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
