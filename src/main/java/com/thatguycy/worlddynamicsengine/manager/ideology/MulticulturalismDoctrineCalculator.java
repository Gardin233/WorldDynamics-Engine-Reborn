package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;
import java.util.Set;

/** Resolves how each resident's multiculturalism attitude reacts to other town faiths. */
public final class MulticulturalismDoctrineCalculator {
    private static final Set<String> PREJUDICED_ATTITUDES =
            Set.of("strong_prejudice", "measured_prejudice", "mild_prejudice");
    private static final Set<String> APPROVING_ATTITUDES =
            Set.of("approve", "highly_approve", "noble");

    public record Assessment(Boolean compliant, int differentFaithResidents) {
        public static Assessment none() {
            return new Assessment(null, 0);
        }
    }

    private MulticulturalismDoctrineCalculator() {}

    public static Assessment assess(String attitudeId, int differentFaithResidents) {
        if (attitudeId == null || differentFaithResidents <= 0) return Assessment.none();
        String localId = localId(attitudeId);
        if (PREJUDICED_ATTITUDES.contains(localId)) {
            return new Assessment(false, differentFaithResidents);
        }
        if (APPROVING_ATTITUDES.contains(localId)) {
            return new Assessment(true, differentFaithResidents);
        }
        return Assessment.none();
    }

    private static String localId(String attitudeId) {
        int separator = attitudeId.lastIndexOf('/');
        return (separator < 0 ? attitudeId : attitudeId.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
    }
}
