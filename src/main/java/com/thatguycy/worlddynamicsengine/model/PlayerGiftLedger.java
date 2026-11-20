package com.thatguycy.worlddynamicsengine.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** Stores only pair cooldown timestamps; player names and request contents are not persisted. */
@SerializableAs("PlayerGiftLedger")
public final class PlayerGiftLedger implements ConfigurationSerializable {
    private final Map<String, Long> requestCooldowns = new LinkedHashMap<>();
    private final Map<String, Long> kindnessGiftCooldowns = new LinkedHashMap<>();
    private final Map<String, Long> refusalMoodCooldowns = new LinkedHashMap<>();

    public PlayerGiftLedger() {}

    public PlayerGiftLedger(Map<String, Object> data) {
        copyLongMap(data.get("requestCooldowns"), requestCooldowns);
        copyLongMap(data.get("kindnessGiftCooldowns"), kindnessGiftCooldowns);
        copyLongMap(data.get("refusalMoodCooldowns"), refusalMoodCooldowns);
    }

    public long getRequestCooldownUntil(String pairKey) {
        return requestCooldowns.getOrDefault(pairKey, 0L);
    }

    public void setRequestCooldownUntil(String pairKey, long timestamp) {
        requestCooldowns.put(pairKey, Math.max(0L, timestamp));
    }

    public long getKindnessGiftCooldownUntil(String pairKey) {
        return kindnessGiftCooldowns.getOrDefault(pairKey, 0L);
    }

    public void setKindnessGiftCooldownUntil(String pairKey, long timestamp) {
        kindnessGiftCooldowns.put(pairKey, Math.max(0L, timestamp));
    }

    public long getRefusalMoodCooldownUntil(String pairKey) {
        return refusalMoodCooldowns.getOrDefault(pairKey, 0L);
    }

    public void setRefusalMoodCooldownUntil(String pairKey, long timestamp) {
        refusalMoodCooldowns.put(pairKey, Math.max(0L, timestamp));
    }

    public void pruneExpired(long now) {
        requestCooldowns.values().removeIf(timestamp -> timestamp <= now);
        kindnessGiftCooldowns.values().removeIf(timestamp -> timestamp <= now);
        refusalMoodCooldowns.values().removeIf(timestamp -> timestamp <= now);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestCooldowns", new LinkedHashMap<>(requestCooldowns));
        data.put("kindnessGiftCooldowns", new LinkedHashMap<>(kindnessGiftCooldowns));
        data.put("refusalMoodCooldowns", new LinkedHashMap<>(refusalMoodCooldowns));
        return data;
    }

    public static PlayerGiftLedger deserialize(Map<String, Object> data) {
        return new PlayerGiftLedger(data);
    }

    private static void copyLongMap(Object raw, Map<String, Long> target) {
        if (!(raw instanceof Map<?, ?> values)) return;
        values.forEach((key, value) -> {
            if (key != null && value instanceof Number number) {
                target.put(String.valueOf(key), Math.max(0L, number.longValue()));
            }
        });
    }
}
