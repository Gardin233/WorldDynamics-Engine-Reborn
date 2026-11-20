package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** 永久城镇史书索引及结构化事件副本；城镇解散后仅归档，不删除历史。 */
@SerializableAs("TownChronicle")
public final class TownChronicle implements ConfigurationSerializable {
    public final String id;
    public final String townUuid;
    public String name;
    public long openedAt;
    public long foundedAt;
    public long endedAt;
    public boolean archived;
    public final Map<String, Object> snapshot = new LinkedHashMap<>();
    public final List<Map<String, Object>> events = new ArrayList<>();

    public TownChronicle(String townUuid, String name, long foundedAt) {
        this.id = UUID.randomUUID().toString();
        this.townUuid = townUuid;
        this.name = name;
        this.openedAt = System.currentTimeMillis();
        this.foundedAt = foundedAt;
    }

    public TownChronicle(Map<String, Object> data) {
        id = text(data, "id");
        townUuid = text(data, "townUuid");
        name = text(data, "name");
        openedAt = number(data, "openedAt");
        foundedAt = number(data, "foundedAt");
        endedAt = number(data, "endedAt");
        archived = Boolean.TRUE.equals(data.get("archived"));
        if (data.get("snapshot") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> snapshot.put(String.valueOf(key), value));
        }
        if (data.get("events") instanceof Collection<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> map) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    map.forEach((key, value) -> event.put(String.valueOf(key), value));
                    events.add(event);
                }
            }
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("townUuid", townUuid);
        data.put("name", name);
        data.put("openedAt", openedAt);
        data.put("foundedAt", foundedAt);
        data.put("endedAt", endedAt);
        data.put("archived", archived);
        data.put("folder", "history/towns/" + id);
        data.put("snapshot", new LinkedHashMap<>(snapshot));
        data.put("events", new ArrayList<>(events));
        return data;
    }

    public static TownChronicle deserialize(Map<String, Object> data) {
        return new TownChronicle(data);
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
