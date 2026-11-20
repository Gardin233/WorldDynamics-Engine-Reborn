package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;

/** Classifies ritual participation and inactivity against the ritual attitude. */
public final class RitualsDoctrineCalculator {
    private RitualsDoctrineCalculator() {}

    public static Boolean assessParticipation(String attitude) {
        return switch (localId(attitude)) {
            case "promote" -> Boolean.TRUE;
            case "disgust", "abhor" -> Boolean.FALSE;
            default -> null;
        };
    }

    public static Boolean assessLongAbsence(String attitude) {
        return "promote".equals(localId(attitude)) ? Boolean.FALSE : null;
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
    }
}
