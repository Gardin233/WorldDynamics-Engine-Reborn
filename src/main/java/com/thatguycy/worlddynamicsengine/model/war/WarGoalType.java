package com.thatguycy.worlddynamicsengine.model.war;

/** Defines the measurable military objective implied by a war reason. */
public enum WarGoalType {
    NONE,
    CAPTURE_TOWN,
    OCCUPY_CAPITAL,
    SHOW_HEGEMONY;

    public static WarGoalType forReason(WarReason reason) {
        if (reason == null) return NONE;
        return switch (reason) {
            case CONQUEST, RECONQUEST -> CAPTURE_TOWN;
            case INDEPENDENCE, HOLY_WAR, EXPORT_REVOLUTION -> OCCUPY_CAPITAL;
            case HUMILIATE_RIVAL, DESTROY_THREAT -> SHOW_HEGEMONY;
            case TRADE_CONFLICT -> NONE;
        };
    }

    public static WarGoalType parse(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
