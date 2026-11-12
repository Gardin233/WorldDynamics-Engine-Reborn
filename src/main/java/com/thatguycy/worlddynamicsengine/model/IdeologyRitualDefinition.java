package com.thatguycy.worlddynamicsengine.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/** 保存意识形态已注册的仪式定义；实际举行状态只保存在运行期内存中。 */
@SerializableAs("IdeologyRitualDefinition")
public final class IdeologyRitualDefinition implements ConfigurationSerializable {
    private final UUID ritualUuid;
    private final UUID ideologyUuid;
    private final String ideologyName;
    private final String templateId;
    private final String ritualName;
    private final UUID createdByUuid;
    private final String createdByName;
    private final long createdAt;
    private final BigDecimal creationCost;

    public IdeologyRitualDefinition(UUID ritualUuid, UUID ideologyUuid, String ideologyName,
            String templateId, String ritualName, UUID createdByUuid, String createdByName,
            long createdAt, BigDecimal creationCost) {
        if (ritualUuid == null || ideologyUuid == null || ideologyName == null || templateId == null
                || ritualName == null || createdByUuid == null || createdByName == null
                || creationCost == null) {
            throw new IllegalArgumentException("Ritual identity and creator data are required.");
        }
        this.ritualUuid = ritualUuid;
        this.ideologyUuid = ideologyUuid;
        this.ideologyName = ideologyName;
        this.templateId = templateId;
        this.ritualName = ritualName;
        this.createdByUuid = createdByUuid;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
        this.creationCost = creationCost.max(BigDecimal.ZERO);
    }

    public IdeologyRitualDefinition(Map<String, Object> map) {
        ritualUuid = UUID.fromString(String.valueOf(map.get("ritualUuid")));
        ideologyUuid = UUID.fromString(String.valueOf(map.get("ideologyUuid")));
        ideologyName = String.valueOf(map.getOrDefault("ideologyName", "Unknown"));
        templateId = String.valueOf(map.getOrDefault("templateId", ""));
        ritualName = String.valueOf(map.getOrDefault("ritualName", "Unknown"));
        createdByUuid = UUID.fromString(String.valueOf(map.get("createdByUuid")));
        createdByName = String.valueOf(map.getOrDefault("createdByName", "Unknown"));
        Object rawCreatedAt = map.get("createdAt");
        createdAt = rawCreatedAt instanceof Number number ? number.longValue() : System.currentTimeMillis();
        creationCost = readNonNegativeDecimal(map.get("creationCost"));
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ritualUuid", ritualUuid.toString());
        map.put("ideologyUuid", ideologyUuid.toString());
        map.put("ideologyName", ideologyName);
        map.put("templateId", templateId);
        map.put("ritualName", ritualName);
        map.put("createdByUuid", createdByUuid.toString());
        map.put("createdByName", createdByName);
        map.put("createdAt", createdAt);
        map.put("creationCost", creationCost.toPlainString());
        return map;
    }

    public UUID getRitualUuid() {
        return ritualUuid;
    }

    public UUID getIdeologyUuid() {
        return ideologyUuid;
    }

    public String getIdeologyName() {
        return ideologyName;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getRitualName() {
        return ritualName;
    }

    public UUID getCreatedByUuid() {
        return createdByUuid;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getCreationCost() {
        return creationCost;
    }

    public static IdeologyRitualDefinition deserialize(Map<String, Object> map) {
        return new IdeologyRitualDefinition(map);
    }

    private static BigDecimal readNonNegativeDecimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value)).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }
}
