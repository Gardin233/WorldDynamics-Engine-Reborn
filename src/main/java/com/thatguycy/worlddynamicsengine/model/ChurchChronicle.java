package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** Permanent UUID index and event copy for one synodal church timeline. */
@SerializableAs("ChurchChronicle")
public final class ChurchChronicle implements ConfigurationSerializable {
    public final String id;
    public final String churchUuid;
    public final String ideologyUuid;
    public final String ideologyName;
    public String name;
    public long openedAt;
    public long foundedAt;
    public long endedAt;
    public boolean archived;
    public final Map<String, Object> snapshot = new LinkedHashMap<>();
    public final List<Map<String, Object>> events = new ArrayList<>();

    public ChurchChronicle(WDEChurch church) {
        id = UUID.randomUUID().toString();
        churchUuid = church.getChurchUuid().toString();
        ideologyUuid = church.getSourceIdeologyUuid().toString();
        ideologyName = church.getSourceIdeologyName();
        name = church.getChurchName();
        openedAt = System.currentTimeMillis();
        foundedAt = church.getCreatedAt();
    }

    public ChurchChronicle(Map<String, Object> data) {
        id = text(data, "id");
        churchUuid = text(data, "churchUuid");
        ideologyUuid = text(data, "ideologyUuid");
        ideologyName = text(data, "ideologyName");
        name = text(data, "name");
        openedAt = number(data, "openedAt");
        foundedAt = number(data, "foundedAt");
        endedAt = number(data, "endedAt");
        archived = Boolean.TRUE.equals(data.get("archived"));
        copyMap(data.get("snapshot"), snapshot);
        copyEvents(data.get("events"), events);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("churchUuid", churchUuid);
        data.put("ideologyUuid", ideologyUuid);
        data.put("ideologyName", ideologyName);
        data.put("name", name);
        data.put("openedAt", openedAt);
        data.put("foundedAt", foundedAt);
        data.put("endedAt", endedAt);
        data.put("archived", archived);
        data.put("snapshot", new LinkedHashMap<>(snapshot));
        data.put("folder", "history/churches/" + id);
        data.put("events", copyEventsForSerialization(events));
        return data;
    }

    public static ChurchChronicle deserialize(Map<String, Object> data) {
        return new ChurchChronicle(data);
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static void copyMap(Object raw, Map<String, Object> target) {
        if (raw instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key != null) target.put(String.valueOf(key), value);
            });
        }
    }

    private static void copyEvents(Object raw, List<Map<String, Object>> target) {
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    copyMap(map, event);
                    target.add(event);
                }
            }
        }
    }

    private static List<Map<String, Object>> copyEventsForSerialization(List<Map<String, Object>> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> event : source) result.add(new LinkedHashMap<>(event));
        return result;
    }
}
