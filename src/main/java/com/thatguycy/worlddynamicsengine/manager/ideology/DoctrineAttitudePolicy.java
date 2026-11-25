package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import java.util.Collection;
import java.util.Locale;

/** Evaluates registered doctrine choices without reading or changing culture/player state. */
public final class DoctrineAttitudePolicy {
    private static final String NONHUMANIZATION_DOCTRINE = "namespace:wder/nonhumanization";
    private static final String NONHUMANIZATION_MEME = "nonhuman_alienation";
    private static final String DOCTRINE_ID_PATTERN = "^[a-z0-9_.-]+:(?:wde|wder)/[a-z0-9_.-]+$";
    private static final String ATTITUDE_ID_PATTERN = "^[a-z0-9_.-]+:(?:wde|wder)/[a-z0-9_.-]+/[a-z0-9_.-]+$";
    private static final String LOCAL_ATTITUDE_ID_PATTERN = "^[a-z0-9_.-]+$";

    private DoctrineAttitudePolicy() {}

    public static String normalizeAttitudeId(String doctrineId, String rawAttitudeId) {
        if (rawAttitudeId == null) return null;
        String normalized = rawAttitudeId.trim().toLowerCase();
        if (normalized.matches(ATTITUDE_ID_PATTERN)) return normalized;
        if (doctrineId == null || !doctrineId.matches(DOCTRINE_ID_PATTERN)
                || !normalized.matches(LOCAL_ATTITUDE_ID_PATTERN)) return null;
        return doctrineId + "/" + normalized;
    }

    /**
     * Meme IDs must already be resolved against the registry.
     * Null means no culture context (definition-only query); an empty collection means no active memes.
     */
    public static boolean canUse(IdeologyDoctrineDefinition doctrine, String attitudeId,
                                 Collection<String> configuredMemes) {
        String normalized = doctrine == null ? null : normalizeAttitudeId(doctrine.getId(), attitudeId);
        if (normalized == null || doctrine.getAttitude(normalized) == null) return false;
        if (configuredMemes == null) return true;
        if (!canUseNonhumanizationAttitude(doctrine.getId(), normalized, configuredMemes)) return false;
        for (String memeId : configuredMemes) {
            var constraint = doctrine.getMemeConstraint(memeId);
            if (constraint != null && constraint.blocks(normalized)) return false;
        }
        return true;
    }

    /** Preserves saved meme order and each constraint's default candidate order. */
    public static String resolveDefault(IdeologyDoctrineDefinition doctrine, Collection<String> configuredMemes) {
        if (doctrine == null) return null;
        if (configuredMemes != null) {
            for (String memeId : configuredMemes) {
                var constraint = doctrine.getMemeConstraint(memeId);
                if (constraint == null) continue;
                for (String candidate : constraint.getDefaultAttitudeIds()) {
                    if (doctrine.getAttitude(candidate) != null) return candidate;
                }
            }
        }
        // Default selection and legality remain separate, as in the existing manager.
        return doctrine.getDefaultAttitudeId();
    }

    static boolean canUseNonhumanizationAttitude(String doctrineId, String attitudeId,
                                                 Collection<String> configuredMemes) {
        String normalized = attitudeId == null ? "" : attitudeId.trim().toLowerCase(Locale.ROOT);
        if (!NONHUMANIZATION_DOCTRINE.equals(IdeologyDoctrineDefinition.normalizeId(doctrineId))
                || normalized.isEmpty()
                || (NONHUMANIZATION_DOCTRINE + "/neutral").equals(normalized)
                || "neutral".equals(normalized)) return true;
        return configuredMemes != null && configuredMemes.stream()
                .anyMatch(memeId -> NONHUMANIZATION_MEME.equals(IdeologyMemeDefinition.normalizeId(memeId)));
    }
}
