package com.thatguycy.worlddynamicsengine.model.war;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persisted objective, population snapshot, and continuous-control timer for one war. */
public final class WarGoalState {
    private final WarGoalType type;
    private String targetTownName;
    private String targetNationName;
    private String targetOrganizationName;
    private String excludedKingUuid;
    private long enemyPopulationAtStart;
    private long attackerDefeatedPlayers;
    private final Set<String> defeatedPlayerUuids = new LinkedHashSet<>();
    private long conditionSince;
    private boolean forcedTreatyAllowed;

    public WarGoalState(WarGoalType type, String targetTownName, String targetNationName,
            String targetOrganizationName, String excludedKingUuid, long enemyPopulationAtStart,
            boolean forcedTreatyAllowed) {
        this.type = type == null ? WarGoalType.NONE : type;
        this.targetTownName = normalize(targetTownName);
        this.targetNationName = normalize(targetNationName);
        this.targetOrganizationName = normalize(targetOrganizationName);
        this.excludedKingUuid = normalize(excludedKingUuid);
        this.enemyPopulationAtStart = Math.max(0L, enemyPopulationAtStart);
        this.attackerDefeatedPlayers = 0L;
        this.conditionSince = 0L;
        this.forcedTreatyAllowed = forcedTreatyAllowed;
    }

    private WarGoalState(Map<?, ?> map) {
        type = WarGoalType.parse(String.valueOf(map.get("type")));
        targetTownName = normalize(String.valueOf(value(map, "targetTownName", "")));
        targetNationName = normalize(String.valueOf(value(map, "targetNationName", "")));
        targetOrganizationName = normalize(String.valueOf(value(map, "targetOrganizationName", "")));
        excludedKingUuid = normalize(String.valueOf(value(map, "excludedKingUuid", "")));
        enemyPopulationAtStart = readLong(map.get("enemyPopulationAtStart"));
        attackerDefeatedPlayers = readLong(map.get("attackerDefeatedPlayers"));
        Object rawDefeatedPlayers = map.get("defeatedPlayerUuids");
        if (rawDefeatedPlayers instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    defeatedPlayerUuids.add(String.valueOf(value));
                }
            }
        }
        attackerDefeatedPlayers = Math.max(attackerDefeatedPlayers, defeatedPlayerUuids.size());
        conditionSince = readLong(map.get("conditionSince"));
        forcedTreatyAllowed = Boolean.parseBoolean(String.valueOf(value(map, "forcedTreatyAllowed", false)));
    }

    public static WarGoalState none() {
        return new WarGoalState(WarGoalType.NONE, null, null, null, null, 0L, false);
    }

    public static WarGoalState fromMap(Object raw) {
        return raw instanceof Map<?, ?> map ? new WarGoalState(map) : none();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        map.put("targetTownName", targetTownName);
        map.put("targetNationName", targetNationName);
        map.put("targetOrganizationName", targetOrganizationName);
        map.put("excludedKingUuid", excludedKingUuid);
        map.put("enemyPopulationAtStart", enemyPopulationAtStart);
        map.put("attackerDefeatedPlayers", attackerDefeatedPlayers);
        map.put("defeatedPlayerUuids", new java.util.ArrayList<>(defeatedPlayerUuids));
        map.put("conditionSince", conditionSince);
        map.put("forcedTreatyAllowed", forcedTreatyAllowed);
        return map;
    }

    public boolean updateCondition(boolean satisfied, long now) {
        long next = satisfied ? (conditionSince > 0L ? conditionSince : Math.max(1L, now)) : 0L;
        if (next == conditionSince) return false;
        conditionSince = next;
        return true;
    }

    public void resetCondition() {
        conditionSince = 0L;
    }

    public boolean recordAttackerDefeat(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank() || !defeatedPlayerUuids.add(playerUuid)) {
            return false;
        }
        if (attackerDefeatedPlayers < Long.MAX_VALUE) attackerDefeatedPlayers++;
        return true;
    }

    public WarGoalType getType() {
        return type;
    }

    public String getTargetTownName() {
        return targetTownName;
    }

    public void setTargetTownName(String targetTownName) {
        this.targetTownName = normalize(targetTownName);
    }

    public String getTargetNationName() {
        return targetNationName;
    }

    public String getTargetOrganizationName() {
        return targetOrganizationName;
    }

    public void setTargetOrganizationName(String targetOrganizationName) {
        this.targetOrganizationName = normalize(targetOrganizationName);
    }

    public String getExcludedKingUuid() {
        return excludedKingUuid;
    }

    public long getEnemyPopulationAtStart() {
        return enemyPopulationAtStart;
    }

    public long getAttackerDefeatedPlayers() {
        return attackerDefeatedPlayers;
    }

    public long getConditionSince() {
        return conditionSince;
    }

    public boolean isForcedTreatyAllowed() {
        return forcedTreatyAllowed;
    }

    public boolean isReady(long now, long requiredHoldMillis) {
        return forcedTreatyAllowed && conditionSince > 0L && now - conditionSince > requiredHoldMillis;
    }

    public boolean canDefenderDemandIndependence(double scoreDifference, double requiredDifference) {
        return type == WarGoalType.OCCUPY_CAPITAL
                && forcedTreatyAllowed
                && conditionSince == 0L
                && Double.isFinite(scoreDifference)
                && Double.isFinite(requiredDifference)
                && scoreDifference >= Math.max(0D, requiredDifference);
    }

    private static long readLong(Object value) {
        try {
            return value instanceof Number number ? Math.max(0L, number.longValue())
                    : Math.max(0L, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Object value(Map<?, ?> map, String key, Object fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value)
                ? "" : value;
    }
}
