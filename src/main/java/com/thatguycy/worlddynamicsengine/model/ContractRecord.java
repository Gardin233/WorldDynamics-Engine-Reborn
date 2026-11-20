package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** Durable record of a non-enforceable, player-authored contract proposal. */
@SerializableAs("ContractRecord")
public final class ContractRecord implements ConfigurationSerializable {
    public final String id;
    public final String fromType;
    public final String fromKey;
    public final String fromDisplay;
    public final String toType;
    public final String toKey;
    public final String toDisplay;
    public final String initiatorUuid;
    public final String initiatorName;
    public final long createdAt;
    public final List<String> bodyPages;
    public String status;
    public String decisionUuid;
    public String decisionName;
    public long decidedAt;
    public final Set<String> viewedBy = new LinkedHashSet<>();

    public ContractRecord(String id, String fromType, String fromKey, String fromDisplay,
                          String toType, String toKey, String toDisplay, UUID initiatorUuid,
                          String initiatorName, long createdAt, List<String> bodyPages) {
        this.id = id;
        this.fromType = fromType;
        this.fromKey = fromKey;
        this.fromDisplay = fromDisplay;
        this.toType = toType;
        this.toKey = toKey;
        this.toDisplay = toDisplay;
        this.initiatorUuid = initiatorUuid.toString();
        this.initiatorName = initiatorName;
        this.createdAt = createdAt;
        this.bodyPages = new ArrayList<>(bodyPages);
        this.status = "PENDING";
        this.decisionUuid = "";
        this.decisionName = "";
        this.decidedAt = 0L;
    }

    public ContractRecord(Map<String, Object> data) {
        id = text(data, "id");
        fromType = text(data, "fromType");
        fromKey = text(data, "fromKey");
        fromDisplay = text(data, "fromDisplay");
        toType = text(data, "toType");
        toKey = text(data, "toKey");
        toDisplay = text(data, "toDisplay");
        initiatorUuid = text(data, "initiatorUuid");
        initiatorName = text(data, "initiatorName");
        createdAt = number(data, "createdAt");
        bodyPages = readStringList(data.get("bodyPages"));
        status = data.get("status") == null ? "PENDING" : text(data, "status");
        decisionUuid = text(data, "decisionUuid");
        decisionName = text(data, "decisionName");
        decidedAt = number(data, "decidedAt");
        Object rawViewedBy = data.get("viewedBy");
        if (rawViewedBy instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value != null) viewedBy.add(String.valueOf(value));
            }
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("fromType", fromType);
        data.put("fromKey", fromKey);
        data.put("fromDisplay", fromDisplay);
        data.put("toType", toType);
        data.put("toKey", toKey);
        data.put("toDisplay", toDisplay);
        data.put("initiatorUuid", initiatorUuid);
        data.put("initiatorName", initiatorName);
        data.put("createdAt", createdAt);
        data.put("bodyPages", new ArrayList<>(bodyPages));
        data.put("status", status);
        data.put("decisionUuid", decisionUuid);
        data.put("decisionName", decisionName);
        data.put("decidedAt", decidedAt);
        data.put("viewedBy", new ArrayList<>(viewedBy));
        return data;
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static List<String> readStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> values) {
            for (Object entry : values) {
                if (entry != null) result.add(String.valueOf(entry));
            }
        }
        return result;
    }

    public static ContractRecord deserialize(Map<String, Object> data) {
        return new ContractRecord(data);
    }
}
