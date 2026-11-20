package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;

/** Classifies animal breeding against the ranching doctrine attitude. */
public final class RanchingDoctrineCalculator {
    private RanchingDoctrineCalculator() {}

    public static Boolean assess(String attitude) {
        String localId = localId(attitude);
        if ("primary".equals(localId) || "none".equals(localId) || "neutral".equals(localId)) {
            return true;
        }
        if ("oppose".equals(localId)) {
            return false;
        }
        return null;
    }

    private static String localId(String id) {
        if (id == null || id.isBlank()) return "";
        int separator = id.lastIndexOf('/');
        return id.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
    }
}
