package com.thatguycy.worlddynamicsengine.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * 居民持久化实体，保存 UUID、个人信仰、背景选择及个人战争界面偏好。
 */
@SerializableAs("WDEresident")
public class WDEresident implements ConfigurationSerializable {
    private UUID userUUID;
    private String username;
    private String religionName;
    private UUID churchUuid;
    private long churchChangedAt;
    private boolean religionChosen;
    private String childhoodBackgroundId;
    private String adulthoodBackgroundId;
    private String focusedWarKey;
    private Set<String> appliedBackgroundBonusIds;
    private Set<String> flags;

    public WDEresident(UUID userUUID, String username) {
        this.userUUID = userUUID;
        this.username = username;
        this.religionName = "None";
        this.churchUuid = null;
        this.churchChangedAt = 0L;
        this.religionChosen = false;
        this.childhoodBackgroundId = "";
        this.adulthoodBackgroundId = "";
        this.focusedWarKey = "";
        this.appliedBackgroundBonusIds = new HashSet<>();
        this.flags = new HashSet<>();
    }

    // Constructor used for deserialization
    public WDEresident(Map<String, Object> map) {
        this.userUUID = UUID.fromString(String.valueOf(map.get("userUUID")));
        this.username = String.valueOf(map.getOrDefault("username", "Unknown"));
        this.religionName = String.valueOf(map.getOrDefault("religionName", "None"));
        String churchUuidValue = normalizeStringValue(map.get("churchUuid"));
        this.churchUuid = churchUuidValue.isBlank() ? null : UUID.fromString(churchUuidValue);
        Object rawChurchChangedAt = map.get("churchChangedAt");
        this.churchChangedAt = rawChurchChangedAt instanceof Number number ? number.longValue() : 0L;
        Object rawReligionChosen = map.get("religionChosen");
        this.religionChosen = rawReligionChosen instanceof Boolean
                ? (Boolean) rawReligionChosen
                : !isNoneReligion(this.religionName);
        this.childhoodBackgroundId = normalizeStringValue(map.get("childhoodBackgroundId"));
        this.adulthoodBackgroundId = normalizeStringValue(map.get("adulthoodBackgroundId"));
        this.focusedWarKey = normalizeStringValue(map.get("focusedWarKey"));
        this.appliedBackgroundBonusIds = readStringSet(map.get("appliedBackgroundBonusIds"));
        this.flags = readStringSet(map.get("flags"));
    }

    // Method for serialization
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("userUUID", userUUID.toString());
        map.put("username", username);
        map.put("religionName", religionName);
        map.put("churchUuid", churchUuid == null ? "" : churchUuid.toString());
        map.put("churchChangedAt", churchChangedAt);
        map.put("religionChosen", religionChosen);
        map.put("childhoodBackgroundId", childhoodBackgroundId);
        map.put("adulthoodBackgroundId", adulthoodBackgroundId);
        map.put("focusedWarKey", focusedWarKey);
        map.put("appliedBackgroundBonusIds", appliedBackgroundBonusIds);
        map.put("flags", flags);
        return map;
    }


    // Getters and Setters
    public UUID getUserUUID() {
        return userUUID;
    }

    public void setUserUUID(UUID userUUID) {
        this.userUUID = userUUID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getFlags() {
        return flags;
    }

    public void setFlags(Set<String> flags) {
        this.flags = flags;
    }

    public String getReligionName() {
        return religionName;
    }

    public void setReligionName(String religionName) {
        if (this.religionName == null || religionName == null
                || !this.religionName.equalsIgnoreCase(religionName)) {
            churchUuid = null;
        }
        this.religionName = religionName;
    }

    public UUID getChurchUuid() {
        return churchUuid;
    }

    public void setChurchUuid(UUID churchUuid, long changedAt) {
        this.churchUuid = churchUuid;
        this.churchChangedAt = changedAt;
    }

    public long getChurchChangedAt() {
        return churchChangedAt;
    }

    public boolean isReligionChosen() {
        return religionChosen;
    }

    public void setReligionChosen(boolean religionChosen) {
        this.religionChosen = religionChosen;
    }

    public String getChildhoodBackgroundId() {
        return childhoodBackgroundId;
    }

    public void setChildhoodBackgroundId(String childhoodBackgroundId) {
        this.childhoodBackgroundId = normalizeStringValue(childhoodBackgroundId);
    }

    public String getAdulthoodBackgroundId() {
        return adulthoodBackgroundId;
    }

    public void setAdulthoodBackgroundId(String adulthoodBackgroundId) {
        this.adulthoodBackgroundId = normalizeStringValue(adulthoodBackgroundId);
    }

    public Set<String> getAppliedBackgroundBonusIds() {
        return appliedBackgroundBonusIds;
    }

    public void setAppliedBackgroundBonusIds(Set<String> appliedBackgroundBonusIds) {
        this.appliedBackgroundBonusIds = appliedBackgroundBonusIds == null
                ? new HashSet<>()
                : new HashSet<>(appliedBackgroundBonusIds);
    }

    public String getFocusedWarKey() {
        return focusedWarKey;
    }

    public void setFocusedWarKey(String focusedWarKey) {
        this.focusedWarKey = normalizeStringValue(focusedWarKey);
    }

    public boolean hasSelectedChildhoodBackground() {
        return !childhoodBackgroundId.isBlank();
    }

    public boolean hasSelectedAdulthoodBackground() {
        return !adulthoodBackgroundId.isBlank();
    }

    public boolean hasAppliedBackgroundBonus(String backgroundId) {
        return backgroundId != null && appliedBackgroundBonusIds.contains(backgroundId.toLowerCase());
    }

    public void markBackgroundBonusApplied(String backgroundId) {
        if (backgroundId != null && !backgroundId.isBlank()) {
            appliedBackgroundBonusIds.add(backgroundId.toLowerCase());
        }
    }

    // Helper methods
    public void addFlag(String flag) {
        flags.add(flag);
    }

    public void removeFlag(String flag) {
        flags.remove(flag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public static WDEresident deserialize(Map<String, Object> map) {
        return new WDEresident(map);
    }

    private Set<String> readStringSet(Object rawValue) {
        Set<String> values = new HashSet<>();
        if (rawValue instanceof Collection<?>) {
            for (Object entry : (Collection<?>) rawValue) {
                if (entry != null) {
                    values.add(String.valueOf(entry));
                }
            }
        }
        return values;
    }

    private boolean isNoneReligion(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value);
    }

    private String normalizeStringValue(Object rawValue) {
        return rawValue == null ? "" : String.valueOf(rawValue).trim();
    }
}
