package com.thatguycy.worlddynamicsengine.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/** 外交信函事实存档，使用国家 UUID 区分同名重建的国家。 */
@SerializableAs("DiplomaticLetter")
public final class DiplomaticLetter implements ConfigurationSerializable {
    public final Map<String, Object> data;
    public DiplomaticLetter(Map<String, Object> data) { this.data = new LinkedHashMap<>(data); }
    @Override public Map<String, Object> serialize() { return new LinkedHashMap<>(data); }
    public String value(String key) { return String.valueOf(data.getOrDefault(key, "")); }
}
