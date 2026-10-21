package com.thatguycy.worlddynamicsengine.model;

import java.util.*;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** 史书永久索引及结构化事件副本；解散只归档，不删除索引。 */
@SerializableAs("NationChronicle")
public final class NationChronicle implements ConfigurationSerializable {
    public final String id;
    public final String nationUuid;
    public String name;
    public long openedAt;
    public long foundedAt;
    public long endedAt;
    public boolean archived;
    public final Map<String, Object> snapshot = new LinkedHashMap<>();
    public final List<Map<String, Object>> events = new ArrayList<>();

    public NationChronicle(String nationUuid, String name, long foundedAt) {
        this.id = UUID.randomUUID().toString();
        this.nationUuid = nationUuid;
        this.name = name;
        this.openedAt = System.currentTimeMillis();
        this.foundedAt = foundedAt;
    }

    public NationChronicle(Map<String, Object> data) {
        id = String.valueOf(data.get("id"));
        nationUuid = String.valueOf(data.get("nationUuid"));
        name = String.valueOf(data.get("name"));
        openedAt = ((Number) data.get("openedAt")).longValue();
        foundedAt = ((Number) data.get("foundedAt")).longValue();
        endedAt = ((Number) data.get("endedAt")).longValue();
        archived = Boolean.TRUE.equals(data.get("archived"));
        if (data.get("snapshot") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> snapshot.put(String.valueOf(key), value));
        }
        if (data.get("events") instanceof Collection<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    map.forEach((key, value) -> event.put(String.valueOf(key), value));
                    events.add(event);
                }
            }
        }
    }

    @Override public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("nationUuid", nationUuid);
        data.put("name", name);
        data.put("openedAt", openedAt);
        data.put("foundedAt", foundedAt);
        data.put("endedAt", endedAt);
        data.put("archived", archived);
        data.put("folder", "history/" + id);
        data.put("snapshot", new LinkedHashMap<>(snapshot));
        data.put("events", new ArrayList<>(events));
        return data;
    }
}
