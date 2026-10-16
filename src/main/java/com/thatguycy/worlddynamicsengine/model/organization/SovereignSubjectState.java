package com.thatguycy.worlddynamicsengine.model.organization;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 承载单个从仆国的自治等级、镇压度、卫戍身份与参战义务，并支持映射存取。
 */
public class SovereignSubjectState {
    private AutonomyTier autonomyTier;
    private int suppression;
    private boolean garrisonState;
    private boolean warParticipationRequired;

    public SovereignSubjectState(AutonomyTier autonomyTier, int suppression, boolean garrisonState, boolean warParticipationRequired) {
        this.autonomyTier = autonomyTier == null ? AutonomyTier.PROTECTORATE : autonomyTier;
        this.suppression = clampSuppression(suppression);
        this.garrisonState = garrisonState;
        this.warParticipationRequired = warParticipationRequired;
    }

    public static SovereignSubjectState defaultState(int defaultSuppression) {
        return new SovereignSubjectState(AutonomyTier.PROTECTORATE, defaultSuppression, false, false);
    }

    public static SovereignSubjectState fromMap(Map<?, ?> map, int defaultSuppression) {
        Object rawAutonomyTier = map.containsKey("autonomyTier") ? map.get("autonomyTier") : "PROTECTORATE";
        AutonomyTier autonomyTier = AutonomyTier.fromString(String.valueOf(rawAutonomyTier));
        int suppression = map.get("suppression") instanceof Number number
                ? number.intValue()
                : defaultSuppression;
        boolean garrisonState = map.get("garrisonState") instanceof Boolean bool
                ? bool
                : Boolean.parseBoolean(String.valueOf(map.containsKey("garrisonState") ? map.get("garrisonState") : false));
        boolean warParticipationRequired = map.get("warParticipationRequired") instanceof Boolean bool
                ? bool
                : Boolean.parseBoolean(String.valueOf(map.containsKey("warParticipationRequired") ? map.get("warParticipationRequired") : false));
        return new SovereignSubjectState(autonomyTier, suppression, garrisonState, warParticipationRequired);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("autonomyTier", autonomyTier.name());
        map.put("suppression", suppression);
        map.put("garrisonState", garrisonState);
        map.put("warParticipationRequired", warParticipationRequired);
        return map;
    }

    public AutonomyTier getAutonomyTier() {
        return autonomyTier;
    }

    public void setAutonomyTier(AutonomyTier autonomyTier) {
        this.autonomyTier = autonomyTier == null ? AutonomyTier.PROTECTORATE : autonomyTier;
    }

    public int getSuppression() {
        return suppression;
    }

    public void setSuppression(int suppression) {
        this.suppression = clampSuppression(suppression);
    }

    public boolean isGarrisonState() {
        return garrisonState;
    }

    public void setGarrisonState(boolean garrisonState) {
        this.garrisonState = garrisonState;
    }

    public boolean isWarParticipationRequired() {
        return warParticipationRequired;
    }

    public void setWarParticipationRequired(boolean warParticipationRequired) {
        this.warParticipationRequired = warParticipationRequired;
    }

    private static int clampSuppression(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * 定义从仆国自治层级及可执行的升级、降级方向。
     */
    public enum AutonomyTier {
        DOMINION,
        PROTECTORATE,
        PUPPET;

        public boolean canPromote() {
            return this != DOMINION;
        }

        public boolean canDemote() {
            return this != PUPPET;
        }

        public AutonomyTier promote() {
            return switch (this) {
                case PUPPET -> PROTECTORATE;
                case PROTECTORATE -> DOMINION;
                case DOMINION -> DOMINION;
            };
        }

        public AutonomyTier demote() {
            return switch (this) {
                case DOMINION -> PROTECTORATE;
                case PROTECTORATE -> PUPPET;
                case PUPPET -> PUPPET;
            };
        }

        public static AutonomyTier fromString(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return PROTECTORATE;
            }

            try {
                return AutonomyTier.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return PROTECTORATE;
            }
        }
    }
}
