package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * 城镇治理持久化实体，保存国家归属、政体、选举、居民税制和征召兵名单。
 */
@SerializableAs("WDEtown")
public class WDEtown implements ConfigurationSerializable {
    private String townName;
    private String nationName;
    private String religionName;
    private boolean religionChosen;
    private String governmentType;
    private String townLeader;
    private long lastElectionAt;
    private int electionIntervalDays;
    private long electionEndAt;
    private String pendingTownLeader;
    private Map<String, Boolean> townLeaderVotes;
    private List<String> townLeaderCandidates;
    private Map<String, String> townLeaderBallots;
    private List<String> organizationNames;
    private boolean residentTaxManagedByNation;
    private String residentTaxType;
    private double residentTaxAmount;
    private Map<String, Boolean> conscripts;
    private final Map<String, Object> ownershipHistory = new LinkedHashMap<>();

    public WDEtown(String townName, String nationName) {
        this.townName = townName;
        this.nationName = nationName;
        this.religionName = "None";
        this.religionChosen = false;
        this.governmentType = "DICTATORSHIP";
        this.townLeader = "None";
        this.lastElectionAt = System.currentTimeMillis();
        this.electionIntervalDays = 7;
        this.electionEndAt = 0L;
        this.pendingTownLeader = "None";
        this.townLeaderVotes = new LinkedHashMap<>();
        this.townLeaderCandidates = new ArrayList<>();
        this.townLeaderBallots = new LinkedHashMap<>();
        this.organizationNames = new ArrayList<>();
        this.residentTaxManagedByNation = false;
        this.residentTaxType = "FIXED";
        this.residentTaxAmount = 0.0D;
        this.conscripts = new LinkedHashMap<>();
    }

    public WDEtown(Map<String, Object> map) {
        if (map.get("ownershipHistory") instanceof Map<?, ?> history) {
            history.forEach((key, value) -> ownershipHistory.put(String.valueOf(key), value));
        }
        this.townName = String.valueOf(map.getOrDefault("townName", "Unknown"));
        this.nationName = String.valueOf(map.getOrDefault("nationName", "None"));
        this.religionName = String.valueOf(map.getOrDefault("religionName", "None"));
        Object rawReligionChosen = map.get("religionChosen");
        this.religionChosen = rawReligionChosen instanceof Boolean
                ? (Boolean) rawReligionChosen
                : !isNoneReligion(this.religionName);
        this.governmentType = String.valueOf(map.getOrDefault("governmentType", "DICTATORSHIP"));
        this.townLeader = String.valueOf(map.getOrDefault("townLeader", "None"));
        Object rawLastElectionAt = map.get("lastElectionAt");
        this.lastElectionAt = rawLastElectionAt instanceof Number ? ((Number) rawLastElectionAt).longValue() : System.currentTimeMillis();
        Object rawElectionIntervalDays = map.get("electionIntervalDays");
        this.electionIntervalDays = rawElectionIntervalDays instanceof Number ? ((Number) rawElectionIntervalDays).intValue() : 7;
        Object rawElectionEndAt = map.get("electionEndAt");
        this.electionEndAt = rawElectionEndAt instanceof Number ? ((Number) rawElectionEndAt).longValue() : 0L;
        this.pendingTownLeader = String.valueOf(map.getOrDefault("pendingTownLeader", "None"));
        this.townLeaderVotes = readBooleanMap(map.get("townLeaderVotes"));
        this.townLeaderCandidates = readStringList(map.get("townLeaderCandidates"));
        this.townLeaderBallots = readStringMap(map.get("townLeaderBallots"));
        this.organizationNames = readStringList(map.get("organizationNames"));
        Object rawResidentTaxManagedByNation = map.get("residentTaxManagedByNation");
        this.residentTaxManagedByNation = rawResidentTaxManagedByNation instanceof Boolean && (Boolean) rawResidentTaxManagedByNation;
        this.residentTaxType = String.valueOf(map.getOrDefault("residentTaxType", "FIXED"));
        Object rawResidentTaxAmount = map.get("residentTaxAmount");
        this.residentTaxAmount = rawResidentTaxAmount instanceof Number ? ((Number) rawResidentTaxAmount).doubleValue() : 0.0D;
        this.conscripts = readBooleanMap(map.get("conscripts"));
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("townName", townName);
        map.put("nationName", nationName);
        map.put("religionName", religionName);
        map.put("religionChosen", religionChosen);
        map.put("governmentType", governmentType);
        map.put("townLeader", townLeader);
        map.put("lastElectionAt", lastElectionAt);
        map.put("electionIntervalDays", electionIntervalDays);
        map.put("electionEndAt", electionEndAt);
        map.put("pendingTownLeader", pendingTownLeader);
        map.put("townLeaderVotes", townLeaderVotes);
        map.put("townLeaderCandidates", townLeaderCandidates);
        map.put("townLeaderBallots", townLeaderBallots);
        map.put("organizationNames", organizationNames);
        map.put("residentTaxManagedByNation", residentTaxManagedByNation);
        map.put("residentTaxType", residentTaxType);
        map.put("residentTaxAmount", residentTaxAmount);
        map.put("conscripts", conscripts);
        map.put("ownershipHistory", new LinkedHashMap<>(ownershipHistory));
        return map;
    }

    public Map<String, Object> getOwnershipHistory() {
        return ownershipHistory;
    }

    public static WDEtown deserialize(Map<String, Object> map) {
        return new WDEtown(map);
    }

    public String getTownName() {
        return townName;
    }

    public void setTownName(String townName) {
        this.townName = townName;
    }

    public String getNationName() {
        return nationName;
    }

    public void setNationName(String nationName) {
        this.nationName = nationName;
    }

    public String getReligionName() {
        return religionName;
    }

    public void setReligionName(String religionName) {
        this.religionName = religionName;
    }

    public boolean isReligionChosen() {
        return religionChosen;
    }

    public void setReligionChosen(boolean religionChosen) {
        this.religionChosen = religionChosen;
    }

    public String getGovernmentType() {
        return governmentType;
    }

    public void setGovernmentType(String governmentType) {
        this.governmentType = governmentType;
    }

    public String getTownLeader() {
        return townLeader;
    }

    public void setTownLeader(String townLeader) {
        this.townLeader = townLeader;
    }

    public long getLastElectionAt() {
        return lastElectionAt;
    }

    public void setLastElectionAt(long lastElectionAt) {
        this.lastElectionAt = lastElectionAt;
    }

    public int getElectionIntervalDays() {
        return electionIntervalDays;
    }

    public void setElectionIntervalDays(int electionIntervalDays) {
        this.electionIntervalDays = electionIntervalDays;
    }

    public long getElectionEndAt() {
        return electionEndAt;
    }

    public void setElectionEndAt(long electionEndAt) {
        this.electionEndAt = electionEndAt;
    }

    public String getPendingTownLeader() {
        return pendingTownLeader;
    }

    public void setPendingTownLeader(String pendingTownLeader) {
        this.pendingTownLeader = pendingTownLeader;
    }

    public Map<String, Boolean> getTownLeaderVotes() {
        return townLeaderVotes;
    }

    public void setTownLeaderVotes(Map<String, Boolean> townLeaderVotes) {
        this.townLeaderVotes = townLeaderVotes;
    }

    public List<String> getTownLeaderCandidates() {
        return townLeaderCandidates;
    }

    public void setTownLeaderCandidates(List<String> townLeaderCandidates) {
        this.townLeaderCandidates = townLeaderCandidates == null ? new ArrayList<>() : new ArrayList<>(townLeaderCandidates);
    }

    public Map<String, String> getTownLeaderBallots() {
        return townLeaderBallots;
    }

    public void setTownLeaderBallots(Map<String, String> townLeaderBallots) {
        this.townLeaderBallots = townLeaderBallots == null ? new LinkedHashMap<>() : new LinkedHashMap<>(townLeaderBallots);
    }

    public List<String> getOrganizationNames() {
        return organizationNames;
    }

    public void setOrganizationNames(List<String> organizationNames) {
        this.organizationNames = organizationNames;
    }

    public void addOrganization(String organizationName) {
        if (!organizationNames.contains(organizationName)) {
            organizationNames.add(organizationName);
        }
    }

    public void removeOrganization(String organizationName) {
        organizationNames.remove(organizationName);
    }

    public boolean hasActiveElection() {
        boolean hasSingleCandidateVote = pendingTownLeader != null && !pendingTownLeader.equalsIgnoreCase("None");
        boolean hasMultiCandidateVote = townLeaderCandidates != null && !townLeaderCandidates.isEmpty();
        return (hasSingleCandidateVote || hasMultiCandidateVote) && electionEndAt > System.currentTimeMillis();
    }

    public boolean isResidentTaxManagedByNation() {
        return residentTaxManagedByNation;
    }

    public void setResidentTaxManagedByNation(boolean residentTaxManagedByNation) {
        this.residentTaxManagedByNation = residentTaxManagedByNation;
    }

    public String getResidentTaxType() {
        return residentTaxType;
    }

    public void setResidentTaxType(String residentTaxType) {
        this.residentTaxType = residentTaxType;
    }

    public double getResidentTaxAmount() {
        return residentTaxAmount;
    }

    public void setResidentTaxAmount(double residentTaxAmount) {
        this.residentTaxAmount = residentTaxAmount;
    }

    public Map<String, Boolean> getConscripts() {
        return conscripts;
    }

    public void setConscripts(Map<String, Boolean> conscripts) {
        this.conscripts = conscripts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(conscripts);
    }

    public boolean isConscript(String playerName) {
        return playerName != null && conscripts.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(playerName));
    }

    public boolean isConscriptTaxExempt(String playerName) {
        if (playerName == null) {
            return false;
        }
        for (Map.Entry<String, Boolean> entry : conscripts.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(playerName)) {
                return Boolean.TRUE.equals(entry.getValue());
            }
        }
        return false;
    }

    public void setConscript(String playerName, boolean taxExempt) {
        removeConscript(playerName);
        if (playerName != null && !playerName.isBlank()) {
            conscripts.put(playerName, taxExempt);
        }
    }

    public void removeConscript(String playerName) {
        if (playerName == null) {
            return;
        }
        conscripts.entrySet().removeIf(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(playerName));
    }

    public void setConscriptTaxExempt(String playerName, boolean taxExempt) {
        if (playerName == null) {
            return;
        }
        String matchedKey = null;
        for (String existing : conscripts.keySet()) {
            if (existing != null && existing.equalsIgnoreCase(playerName)) {
                matchedKey = existing;
                break;
            }
        }
        if (matchedKey != null) {
            conscripts.put(matchedKey, taxExempt);
        }
    }

    private List<String> readStringList(Object rawValue) {
        List<String> values = new ArrayList<>();
        if (rawValue instanceof Collection<?>) {
            for (Object entry : (Collection<?>) rawValue) {
                if (entry != null) {
                    values.add(String.valueOf(entry));
                }
            }
        }
        return values;
    }

    private Map<String, Boolean> readBooleanMap(Object rawValue) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        if (rawValue instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawValue).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    Object value = entry.getValue();
                    boolean parsedValue = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
                    values.put(String.valueOf(entry.getKey()), parsedValue);
                }
            }
        }
        return values;
    }

    private Map<String, String> readStringMap(Object rawValue) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawValue instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return values;
    }

    private boolean isNoneReligion(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value);
    }
}
