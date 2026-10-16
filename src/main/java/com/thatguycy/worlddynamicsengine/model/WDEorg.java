package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * 国家集团持久化实体，保存成员国、准入表决、默认货币余额及集团扩展设置。
 */
@SerializableAs("WDEorg")
public class WDEorg implements ConfigurationSerializable {
    private String organizationName;
    private String organizationType;
    private String nationName;
    private String townName;
    private String chairmanNationName;
    private UUID leaderUUID;
    private Set<String> memberNationNames;
    private Set<String> pendingNationApplications;
    private Map<String, Map<String, Boolean>> pendingNationVotes;
    private double balance;
    private Set<String> flags;
    private Map<String, Object> settings;


    public WDEorg(String organizationName, UUID leaderUUID, String nationName, String townName, String organizationType) {
        this.organizationName = organizationName;
        this.organizationType = organizationType;
        this.nationName = nationName;
        this.townName = townName;
        this.chairmanNationName = nationName;
        this.leaderUUID = leaderUUID;
        this.memberNationNames = new LinkedHashSet<>();
        this.memberNationNames.add(nationName);
        this.pendingNationApplications = new LinkedHashSet<>();
        this.pendingNationVotes = new LinkedHashMap<>();
        this.balance = 0.0;
        this.flags = new LinkedHashSet<>();
        this.settings = new HashMap<>();
    }

    // Constructor used for deserialization
    public WDEorg(Map<String, Object> map) {
        this.organizationName = (String) map.get("organizationName");
        this.organizationType = String.valueOf(map.getOrDefault("organizationType", "TRADE_ALLIANCE"));
        this.nationName = String.valueOf(map.getOrDefault("nationName", "None"));
        this.townName = String.valueOf(map.getOrDefault("townName", "None"));
        this.chairmanNationName = String.valueOf(map.getOrDefault("chairmanNationName", this.nationName));
        this.leaderUUID = UUID.fromString(String.valueOf(map.get("leaderUUID")));
        this.memberNationNames = readStringSet(map.get("memberNationNames"));
        if (this.memberNationNames.isEmpty() && !this.nationName.equalsIgnoreCase("None")) {
            this.memberNationNames.add(this.nationName);
        }
        this.pendingNationApplications = readStringSet(map.get("pendingNationApplications"));
        this.pendingNationVotes = readNestedBooleanMap(map.get("pendingNationVotes"));
        Object rawBalance = map.get("balance");
        this.balance = rawBalance instanceof Number ? ((Number) rawBalance).doubleValue() : 0.0D;
        this.flags = readStringSet(map.get("flags"));
        this.settings = map.get("settings") instanceof Map<?, ?> ? new HashMap<>((Map<String, Object>) map.get("settings")) : new HashMap<>();
    }

    // Method for serialization
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("organizationName", organizationName);
        map.put("organizationType", organizationType);
        map.put("nationName", nationName);
        map.put("townName", townName);
        map.put("chairmanNationName", chairmanNationName);
        map.put("leaderUUID", leaderUUID.toString());
        map.put("memberNationNames", new ArrayList<>(memberNationNames));
        map.put("pendingNationApplications", new ArrayList<>(pendingNationApplications));
        map.put("pendingNationVotes", pendingNationVotes);
        map.put("balance", balance);
        map.put("flags", new ArrayList<>(flags));
        map.put("settings", settings);
        return map;
    }

    // Getters and Setters
    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationType() {
        return organizationType;
    }

    public void setOrganizationType(String organizationType) {
        this.organizationType = organizationType;
    }

    public String getNationName() {
        return nationName;
    }

    public void setNationName(String nationName) {
        this.nationName = nationName;
    }

    public String getTownName() {
        return townName;
    }

    public void setTownName(String townName) {
        this.townName = townName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getChairmanNationName() {
        return chairmanNationName;
    }

    public void setChairmanNationName(String chairmanNationName) {
        this.chairmanNationName = chairmanNationName;
    }

    public UUID getLeaderUUID() {
        return leaderUUID;
    }

    public void setLeaderUUID(UUID leaderUUID) {
        this.leaderUUID = leaderUUID;
    }

    public Set<String> getMemberNationNames() {
        return memberNationNames;
    }

    public void setMemberNationNames(Set<String> memberNationNames) {
        this.memberNationNames = memberNationNames;
    }

    public Set<String> getPendingNationApplications() {
        return pendingNationApplications;
    }

    public void setPendingNationApplications(Set<String> pendingNationApplications) {
        this.pendingNationApplications = pendingNationApplications;
    }

    public Map<String, Map<String, Boolean>> getPendingNationVotes() {
        return pendingNationVotes;
    }

    public void setPendingNationVotes(Map<String, Map<String, Boolean>> pendingNationVotes) {
        this.pendingNationVotes = pendingNationVotes;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public Set<String> getFlags() {
        return flags;
    }

    public void setFlags(Set<String> flags) {
        this.flags = flags;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public void addFlag(String flag) {
        flags.add(flag);
    }

    public void removeFlag(String flag) {
        flags.remove(flag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void addMemberNation(String nationName) {
        if (nationName != null) {
            memberNationNames.add(nationName);
        }
    }

    public void removeMemberNation(String nationName) {
        if (nationName != null) {
            memberNationNames.remove(nationName);
            pendingNationApplications.remove(nationName);
            pendingNationVotes.remove(nationName);
        }
    }

    public boolean hasMemberNation(String nationName) {
        return nationName != null && memberNationNames.stream().anyMatch(existing -> existing.equalsIgnoreCase(nationName));
    }

    public boolean hasPendingApplication(String nationName) {
        return nationName != null && pendingNationApplications.stream().anyMatch(existing -> existing.equalsIgnoreCase(nationName));
    }

    public boolean isSovereignEmpire() {
        return "SOVEREIGN_EMPIRE".equalsIgnoreCase(organizationType);
    }

    public Object getSetting(String key) {
        return settings.get(key);
    }

    public void setSetting(String key, Object value) {
        settings.put(key, value);
    }

    public static WDEorg deserialize(Map<String, Object> map) {
        return new WDEorg(map);
    }

    private Set<String> readStringSet(Object rawValue) {
        Set<String> values = new LinkedHashSet<>();
        if (rawValue instanceof Collection<?>) {
            for (Object entry : (Collection<?>) rawValue) {
                if (entry != null) {
                    values.add(String.valueOf(entry));
                }
            }
        }
        return values;
    }

    private Map<String, Map<String, Boolean>> readNestedBooleanMap(Object rawValue) {
        Map<String, Map<String, Boolean>> values = new LinkedHashMap<>();
        if (!(rawValue instanceof Map<?, ?> outerMap)) {
            return values;
        }

        for (Map.Entry<?, ?> outerEntry : outerMap.entrySet()) {
            if (outerEntry.getKey() == null || !(outerEntry.getValue() instanceof Map<?, ?> innerMap)) {
                continue;
            }

            Map<String, Boolean> voteMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                if (innerEntry.getKey() != null && innerEntry.getValue() != null) {
                    Object value = innerEntry.getValue();
                    boolean parsedValue = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
                    voteMap.put(String.valueOf(innerEntry.getKey()), parsedValue);
                }
            }
            values.put(String.valueOf(outerEntry.getKey()), voteMap);
        }
        return values;
    }
}
