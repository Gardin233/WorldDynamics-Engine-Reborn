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
 * 国家治理持久化实体，保存政体、职位、选举、下属城镇和外交扩展状态。
 */
@SerializableAs("WDEnation")
public class WDEnation implements ConfigurationSerializable {
    private String nationName;

    private String governmentType;
    private String religionName;
    private boolean religionChosen;
    private String governmentLeader;
    private List<String> governmentMembers;
    private String armyCommander;
    private List<String> armyMembers;
    private List<String> townNames;
    private String pendingGovernmentLeader;
    private Map<String, Boolean> governmentLeaderVotes;
    private List<String> governmentLeaderCandidates;
    private Map<String, String> governmentLeaderBallots;
    private String pendingArmyCommander;
    private Map<String, Boolean> armyCommanderVotes;
    private List<String> armyCommanderCandidates;
    private Map<String, String> armyCommanderBallots;
    private long lastElectionAt;
    private int electionIntervalDays;
    private long electionEndAt;
    private long armyElectionEndAt;
    private Map<String, String> diplomaticRelations;
    private Map<String, Boolean> tradingStatus;
    private Map<String, Object> settings;
    private String diplomaticStatus;
    private String resourceControl;
    private String economicPolicies;
    private String culturalInfluence;
    private String militaryStrength;
    private String technologicalLevel;
    private String environmentalPolicies;
    private List<String> espionageNetwork;
    private List<String> historicalEvents;

    public WDEnation(String nationName) {
        this.nationName = nationName;
        this.governmentType = "DICTATORSHIP";
        this.religionName = "None";
        this.religionChosen = false;
        this.governmentLeader = "None"; // Default value
        this.governmentMembers = new ArrayList<>(); // Empty list
        this.armyCommander = "None";
        this.armyMembers = new ArrayList<>();
        this.townNames = new ArrayList<>();
        this.pendingGovernmentLeader = "None";
        this.governmentLeaderVotes = new HashMap<>();
        this.governmentLeaderCandidates = new ArrayList<>();
        this.governmentLeaderBallots = new HashMap<>();
        this.pendingArmyCommander = "None";
        this.armyCommanderVotes = new HashMap<>();
        this.armyCommanderCandidates = new ArrayList<>();
        this.armyCommanderBallots = new HashMap<>();
        this.lastElectionAt = System.currentTimeMillis();
        this.electionIntervalDays = 7;
        this.electionEndAt = 0L;
        this.armyElectionEndAt = 0L;
        this.diplomaticRelations = new HashMap<>(); // Initialize with an empty map
        this.tradingStatus = new HashMap<>(); // Initialize with an empty map
        this.settings = new LinkedHashMap<>();
    }

    public WDEnation(Map<String, Object> map) {
        this.nationName = String.valueOf(map.get("nationName"));
        this.governmentType = String.valueOf(map.getOrDefault("GovernmentType", "DICTATORSHIP"));
        if (this.governmentType.isBlank() || "None".equalsIgnoreCase(this.governmentType)) {
            this.governmentType = "DICTATORSHIP";
        }
        this.religionName = String.valueOf(map.getOrDefault("ReligionName", "None"));
        Object rawReligionChosen = map.get("ReligionChosen");
        this.religionChosen = rawReligionChosen instanceof Boolean
                ? (Boolean) rawReligionChosen
                : !isNoneReligion(this.religionName);
        this.governmentLeader = String.valueOf(map.getOrDefault("GovernmentLeader", "None"));
        this.governmentMembers = readStringList(map.get("GovernmentMembers"));
        this.armyCommander = String.valueOf(map.getOrDefault("ArmyCommander", "None"));
        this.armyMembers = readStringList(map.get("ArmyMembers"));
        this.townNames = readStringList(map.get("TownNames"));
        this.pendingGovernmentLeader = String.valueOf(map.getOrDefault("PendingGovernmentLeader", "None"));
        this.governmentLeaderVotes = readBooleanMap(map.get("GovernmentLeaderVotes"));
        this.governmentLeaderCandidates = readStringList(map.get("GovernmentLeaderCandidates"));
        this.governmentLeaderBallots = readStringMap(map.get("GovernmentLeaderBallots"));
        this.pendingArmyCommander = String.valueOf(map.getOrDefault("PendingArmyCommander", "None"));
        this.armyCommanderVotes = readBooleanMap(map.get("ArmyCommanderVotes"));
        this.armyCommanderCandidates = readStringList(map.get("ArmyCommanderCandidates"));
        this.armyCommanderBallots = readStringMap(map.get("ArmyCommanderBallots"));
        Object rawLastElectionAt = map.get("LastElectionAt");
        this.lastElectionAt = rawLastElectionAt instanceof Number ? ((Number) rawLastElectionAt).longValue() : System.currentTimeMillis();
        Object rawElectionIntervalDays = map.get("ElectionIntervalDays");
        this.electionIntervalDays = rawElectionIntervalDays instanceof Number ? Math.max(1, ((Number) rawElectionIntervalDays).intValue()) : 7;
        Object rawElectionEndAt = map.get("ElectionEndAt");
        this.electionEndAt = rawElectionEndAt instanceof Number ? ((Number) rawElectionEndAt).longValue() : 0L;
        Object rawArmyElectionEndAt = map.get("ArmyElectionEndAt");
        this.armyElectionEndAt = rawArmyElectionEndAt instanceof Number ? ((Number) rawArmyElectionEndAt).longValue() : 0L;
        this.diplomaticRelations = readStringMap(map.get("DiplomaticRelations"));
        this.tradingStatus = readBooleanMap(map.get("TradingStatus"));
        this.settings = map.get("settings") instanceof Map<?, ?> ? new LinkedHashMap<>((Map<String, Object>) map.get("settings")) : new LinkedHashMap<>();
    }

    @NotNull
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("nationName", nationName);
        map.put("GovernmentType", governmentType);
        map.put("ReligionName", religionName);
        map.put("ReligionChosen", religionChosen);
        map.put("GovernmentLeader", governmentLeader);
        map.put("GovernmentMembers", governmentMembers);
        map.put("ArmyCommander", armyCommander);
        map.put("ArmyMembers", armyMembers);
        map.put("TownNames", townNames);
        map.put("PendingGovernmentLeader", pendingGovernmentLeader);
        map.put("GovernmentLeaderVotes", governmentLeaderVotes);
        map.put("GovernmentLeaderCandidates", governmentLeaderCandidates);
        map.put("GovernmentLeaderBallots", governmentLeaderBallots);
        map.put("PendingArmyCommander", pendingArmyCommander);
        map.put("ArmyCommanderVotes", armyCommanderVotes);
        map.put("ArmyCommanderCandidates", armyCommanderCandidates);
        map.put("ArmyCommanderBallots", armyCommanderBallots);
        map.put("LastElectionAt", lastElectionAt);
        map.put("ElectionIntervalDays", electionIntervalDays);
        map.put("ElectionEndAt", electionEndAt);
        map.put("ArmyElectionEndAt", armyElectionEndAt);
        map.put("DiplomaticRelations", diplomaticRelations);
        map.put("TradingStatus", tradingStatus);
        map.put("settings", settings);
        return map;
    }



    public static WDEnation deserialize(Map<String, Object> map) {
        return new WDEnation(map);
    }

    public String getGovernmentType() {
        return this.governmentType;
    }

    public String getGovernmentLeader() {
        return this.governmentLeader;
    }

    public String getReligionName() {
        return religionName;
    }

    public List<String> getGovernmentMembers() {
        return this.governmentMembers;
    }

    public String getArmyLeader() {
        return this.armyCommander;
    }

    public List<String> getArmyMembers() {
        return this.armyMembers;
    }

    public List<String> getTownNames() {
        return this.townNames;
    }

    public String getPendingGovernmentLeader() {
        return pendingGovernmentLeader;
    }

    public void setPendingGovernmentLeader(String pendingGovernmentLeader) {
        this.pendingGovernmentLeader = pendingGovernmentLeader;
    }

    public Map<String, Boolean> getGovernmentLeaderVotes() {
        return governmentLeaderVotes;
    }

    public void setGovernmentLeaderVotes(Map<String, Boolean> governmentLeaderVotes) {
        this.governmentLeaderVotes = governmentLeaderVotes;
    }

    public List<String> getGovernmentLeaderCandidates() {
        return governmentLeaderCandidates;
    }

    public void setGovernmentLeaderCandidates(List<String> governmentLeaderCandidates) {
        this.governmentLeaderCandidates = governmentLeaderCandidates == null ? new ArrayList<>() : new ArrayList<>(governmentLeaderCandidates);
    }

    public Map<String, String> getGovernmentLeaderBallots() {
        return governmentLeaderBallots;
    }

    public void setGovernmentLeaderBallots(Map<String, String> governmentLeaderBallots) {
        this.governmentLeaderBallots = governmentLeaderBallots == null ? new HashMap<>() : new HashMap<>(governmentLeaderBallots);
    }

    public String getPendingArmyCommander() {
        return pendingArmyCommander;
    }

    public void setPendingArmyCommander(String pendingArmyCommander) {
        this.pendingArmyCommander = pendingArmyCommander;
    }

    public Map<String, Boolean> getArmyCommanderVotes() {
        return armyCommanderVotes;
    }

    public void setArmyCommanderVotes(Map<String, Boolean> armyCommanderVotes) {
        this.armyCommanderVotes = armyCommanderVotes;
    }

    public List<String> getArmyCommanderCandidates() {
        return armyCommanderCandidates;
    }

    public void setArmyCommanderCandidates(List<String> armyCommanderCandidates) {
        this.armyCommanderCandidates = armyCommanderCandidates == null ? new ArrayList<>() : new ArrayList<>(armyCommanderCandidates);
    }

    public Map<String, String> getArmyCommanderBallots() {
        return armyCommanderBallots;
    }

    public void setArmyCommanderBallots(Map<String, String> armyCommanderBallots) {
        this.armyCommanderBallots = armyCommanderBallots == null ? new HashMap<>() : new HashMap<>(armyCommanderBallots);
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

    public long getArmyElectionEndAt() {
        return armyElectionEndAt;
    }

    public void setArmyElectionEndAt(long armyElectionEndAt) {
        this.armyElectionEndAt = armyElectionEndAt;
    }

    public boolean hasActiveElection() {
        return (pendingGovernmentLeader != null && !pendingGovernmentLeader.equalsIgnoreCase("None"))
                || (governmentLeaderCandidates != null && !governmentLeaderCandidates.isEmpty() && electionEndAt > System.currentTimeMillis());
    }

    public boolean hasActiveArmyCommanderVote() {
        return (pendingArmyCommander != null && !pendingArmyCommander.equalsIgnoreCase("None"))
                || (armyCommanderCandidates != null && !armyCommanderCandidates.isEmpty() && armyElectionEndAt > System.currentTimeMillis());
    }

    public String getNationName() {
        return this.nationName;
    }
    public void setNationName(String nationName) {
        this.nationName = nationName;
    }

    public void setGovernmentType(String govType) {
        this.governmentType = govType;
    }

    public void setGovernmentLeader(String governmentLeader) {
        this.governmentLeader = governmentLeader;
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

    public void setGovernmentMembers(List<String> governmentMembers) {
        this.governmentMembers = governmentMembers;
    }

    public String getArmyCommander() {
        return armyCommander;
    }

    public void setArmyCommander(String armyCommander) {
        this.armyCommander = armyCommander;
    }

    public void setArmyMembers(List<String> armyMembers) {
        this.armyMembers = armyMembers;
    }

    public void setTownNames(List<String> townNames) {
        this.townNames = townNames;
    }

    // Getter for diplomatic relations
    public Map<String, String> getDiplomaticRelations() {
        return this.diplomaticRelations;
    }

    // Setter for a single diplomatic relation
    public void setDiplomaticRelation(String nationName, String relationStatus) {
        if (this.diplomaticRelations == null) {
            this.diplomaticRelations = new HashMap<>();
        }
        this.diplomaticRelations.put(nationName, relationStatus);
    }

    // Getter for trading status
    public Map<String, Boolean> getTradingStatus() {
        return this.tradingStatus;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    }

    public Object getSetting(String key) {
        return settings.get(key);
    }

    public void setSetting(String key, Object value) {
        settings.put(key, value);
    }

    // Setter for a single trading status
    public void setTradingStatus(String nationName, boolean status) {
        if (this.tradingStatus == null) {
            this.tradingStatus = new HashMap<>();
        }
        this.tradingStatus.put(nationName, status);
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

    private Map<String, String> readStringMap(Object rawValue) {
        Map<String, String> values = new HashMap<>();
        if (rawValue instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawValue).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return values;
    }

    private Map<String, Boolean> readBooleanMap(Object rawValue) {
        Map<String, Boolean> values = new HashMap<>();
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

    private boolean isNoneReligion(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value);
    }
}
