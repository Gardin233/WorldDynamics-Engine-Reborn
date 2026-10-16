package com.thatguycy.worlddynamicsengine.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * 保存常规战争、条约和城镇停战的中央快照，避免同场战争在多个集团中重复结算。
 */
@SerializableAs("WarLedger")
public final class WarLedger implements ConfigurationSerializable {
    public final Map<String, Object> wars = new LinkedHashMap<>();
    public final Map<String, Object> treaties = new LinkedHashMap<>();
    public final Map<String, Object> townTruces = new LinkedHashMap<>();
    public WarLedger() {}
    public WarLedger(Map<String, Object> data) {
        copy(data.get("wars"), wars);
        copy(data.get("treaties"), treaties);
        copy(data.get("townTruces"), townTruces);
    }
    private static void copy(Object raw, Map<String, Object> target) {
        if (raw instanceof Map<?, ?> map) map.forEach((key, value) -> target.put(String.valueOf(key), value));
    }
    @Override public Map<String, Object> serialize() {
        return Map.of("wars", new LinkedHashMap<>(wars), "treaties", new LinkedHashMap<>(treaties),
                "townTruces", new LinkedHashMap<>(townTruces));
    }
}
