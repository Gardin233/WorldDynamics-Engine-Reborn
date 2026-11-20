package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** Permanent UUID index and event copy for one ideology timeline. */
@SerializableAs("IdeologyChronicle")
public final class IdeologyChronicle implements ConfigurationSerializable {
    public final String id;
    public final String ideologyUuid;
    public final String parentIdeologyUuid;
    public final String parentChurchUuid;
    public String name;
    public long openedAt;
    public long foundedAt;
    public long endedAt;
    public boolean archived;
    public final Map<String, Object> snapshot = new LinkedHashMap<>();
    public final Map<String, String> proposalStates = new LinkedHashMap<>();
    public final List<Map<String, Object>> events = new ArrayList<>();

    public IdeologyChronicle(WDEreligion ideology) {
        id = UUID.randomUUID().toString();
        ideologyUuid = ideology.getIdeologyUuid().toString();
        parentIdeologyUuid = uuid(ideology.getParentIdeologyUuid());
        parentChurchUuid = uuid(ideology.getParentChurchUuid());
        name = ideology.getReligionName();
        openedAt = System.currentTimeMillis();
        foundedAt = ideology.getCreatedAt();
    }

    public IdeologyChronicle(Map<String, Object> data) {
        id = text(data, "id");
        ideologyUuid = text(data, "ideologyUuid");
        parentIdeologyUuid = text(data, "parentIdeologyUuid");
        parentChurchUuid = text(data, "parentChurchUuid");
        name = text(data, "name");
        openedAt = number(data, "openedAt");
        foundedAt = number(data, "foundedAt");
        endedAt = number(data, "endedAt");
        archived = Boolean.TRUE.equals(data.get("archived"));
        copyMap(data.get("snapshot"), snapshot);
        copyStringMap(data.get("proposalStates"), proposalStates);
        copyEvents(data.get("events"), events);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("ideologyUuid", ideologyUuid);
        data.put("parentIdeologyUuid", parentIdeologyUuid);
        data.put("parentChurchUuid", parentChurchUuid);
        data.put("name", name);
        data.put("openedAt", openedAt);
        data.put("foundedAt", foundedAt);
        data.put("endedAt", endedAt);
        data.put("archived", archived);
        data.put("snapshot", new LinkedHashMap<>(snapshot));
        data.put("proposalStates", new LinkedHashMap<>(proposalStates));
        data.put("folder", "history/ideologies/" + id);
        data.put("events", copyEventsForSerialization(events));
        return data;
    }

    public static IdeologyChronicle deserialize(Map<String, Object> data) {
        return new IdeologyChronicle(data);
    }

    private static String uuid(UUID value) {
        return value == null ? "" : value.toString();
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

    private static void copyStringMap(Object raw, Map<String, String> target) {
        if (raw instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key != null && value != null) target.put(String.valueOf(key), String.valueOf(value));
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
