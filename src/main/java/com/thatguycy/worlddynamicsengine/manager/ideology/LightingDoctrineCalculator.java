package com.thatguycy.worlddynamicsengine.manager.ideology;

/** Classifies block and sky light into the configured lighting-doctrine outcomes. */
public final class LightingDoctrineCalculator {
    public static final int ATTITUDE_THRESHOLD = 7;

    public enum LightBand {
        NO_LIGHT,
        DIM,
        THRESHOLD,
        BRIGHT
    }

    public record Assessment(int effectiveLight, LightBand band, Boolean compliant) {}

    private LightingDoctrineCalculator() {}

    public static Assessment assess(int blockLight, int skyLight, boolean night, String attitude) {
        int effectiveLight = Math.max(clampLight(blockLight), night ? 0 : clampLight(skyLight));
        LightBand band = effectiveLight == 0 ? LightBand.NO_LIGHT
                : effectiveLight < ATTITUDE_THRESHOLD ? LightBand.DIM
                : effectiveLight == ATTITUDE_THRESHOLD ? LightBand.THRESHOLD : LightBand.BRIGHT;
        if (effectiveLight == ATTITUDE_THRESHOLD) {
            return new Assessment(effectiveLight, band, null);
        }

        String localAttitude = localId(attitude);
        Boolean compliant = effectiveLight == ATTITUDE_THRESHOLD ? null : switch (localAttitude) {
            case "dim" -> effectiveLight < ATTITUDE_THRESHOLD;
            case "crave" -> effectiveLight > ATTITUDE_THRESHOLD;
            default -> null;
        };
        return new Assessment(effectiveLight, band, compliant);
    }

    private static int clampLight(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(java.util.Locale.ROOT);
    }
}
