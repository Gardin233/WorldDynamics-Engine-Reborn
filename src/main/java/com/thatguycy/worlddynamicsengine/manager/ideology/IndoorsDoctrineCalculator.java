package com.thatguycy.worlddynamicsengine.manager.ideology;

/** Classifies the player's current position using its sky light level. */
public final class IndoorsDoctrineCalculator {
    public static final int INDOORS_SKY_LIGHT_LIMIT = 10;

    private IndoorsDoctrineCalculator() {}

    public static Boolean assess(int skyLight, String attitude) {
        if (!"love".equals(localId(attitude))) return null;
        return Math.max(0, Math.min(15, skyLight)) < INDOORS_SKY_LIGHT_LIMIT;
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(java.util.Locale.ROOT);
    }
}
