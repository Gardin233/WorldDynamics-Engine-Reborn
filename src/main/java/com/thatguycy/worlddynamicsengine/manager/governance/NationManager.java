package com.thatguycy.worlddynamicsengine.manager.governance;

import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.manager.war.WarTruceClock;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 维护国家治理档案、政体与选举，并在领袖变更时同步 Towny 国王及符合规则的首都。
 */
public class NationManager {
    private static final String STORAGE_CATEGORY = "nations";

    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final DataStorageManager dataStorageManager;
    private final Map<String, WDEnation> nations;

    public NationManager(JavaPlugin plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.dataStorageManager = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getDataStorageManager();
        this.nations = new LinkedHashMap<>();
        loadNations();
    }

    public void loadNations() {
        nations.clear();
        nations.putAll(dataStorageManager.loadStringKeyed(STORAGE_CATEGORY, WDEnation.class));
        plugin.getLogger().info("Total nations loaded: " + nations.size());
    }

    public void saveNations() {
        dataStorageManager.saveStringKeyed(STORAGE_CATEGORY, nations);
        var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
        if (history != null) nations.values().forEach(history::observe);
    }

    public void updateNation(WDEnation nation) {
        if (nation != null && nations.containsKey(nation.getNationName())) {
            nations.put(nation.getNationName(), nation);
            saveNations();
        }
    }

    public void importExistingTownyNations() {
        for (Nation nation : new ArrayList<>(TownyUniverse.getInstance().getNations())) {
            registerNation(nation);
        }
        refreshTownHierarchy();
    }

    public void registerNation(Nation townyNation) {
        if (nations.containsKey(townyNation.getName())) {
            return;
        }
        WDEnation nation = new WDEnation(townyNation.getName());
        if (townyNation.hasKing()) {
            nation.setGovernmentLeader(townyNation.getKing().getName());
        }
        nation.setElectionIntervalDays(getDefaultNationElectionIntervalDays());
        nations.put(townyNation.getName(), nation);
    }

    public void removeNation(String name) {
        var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
        if (history != null) history.archive(name);
        nations.remove(name);
        for (WDEnation remaining : nations.values()) {
            Set<String> passage = readNationStringSet(remaining, "unconditionalPassageTargets");
            passage.removeIf(target -> name.equalsIgnoreCase(target));
            if (passage.isEmpty()) remaining.getSettings().remove("unconditionalPassageTargets");
            else remaining.setSetting("unconditionalPassageTargets", new ArrayList<>(passage));
            List<Map<String, Object>> tributes = readNationMapList(remaining, "peaceTreatyTributes");
            tributes.removeIf(entry -> name.equalsIgnoreCase(String.valueOf(entry.get("beneficiaryNation"))));
            if (tributes.isEmpty()) remaining.getSettings().remove("peaceTreatyTributes");
            else remaining.setSetting("peaceTreatyTributes", tributes);
        }
        for (WDEtown town : townManager.getAllTowns()) {
            if (name.equalsIgnoreCase(town.getNationName())) townManager.updateTownNation(town.getTownName(), "None");
        }
        townManager.saveTowns();
        refreshTownHierarchy();
    }

    public void renameNation(String oldName, String newName) {
        var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
        if (history != null) history.rename(oldName, newName);
        WDEnation nation = nations.remove(oldName);
        if (nation != null) {
            nation.setNationName(newName);
            nations.put(newName, nation);
        }
        for (WDEtown town : townManager.getAllTowns()) {
            com.thatguycy.worlddynamicsengine.manager.war.TownOwnershipHistory.renameNation(town, oldName, newName);
            if (oldName.equalsIgnoreCase(town.getNationName())) town.setNationName(newName);
        }
        townManager.saveTowns();
        refreshTownHierarchy();
    }

    public void refreshTownHierarchy() {
        rebuildTownHierarchy();
        saveNations();
    }

    public void addNation(String name, WDEnation nation) {
        nations.put(name, nation);
        saveNations();
    }

    public WDEnation getNation(String name) {
        return nations.get(name);
    }

    public Nation getTownyNation(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return null;
        }

        try {
            return TownyUniverse.getInstance().getNation(nationName);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isNationPeaceProtected(String nationName) {
        Nation townyNation = getTownyNation(nationName);
        return townyNation != null && townyNation.isNeutral();
    }

    public boolean hasUnconditionalPassage(String ownerNationName, String visitorNationName) {
        WDEnation ownerNation = getNation(ownerNationName);
        if (ownerNation == null || visitorNationName == null || visitorNationName.isBlank()) {
            return false;
        }

        for (String entry : readNationStringSet(ownerNation, "unconditionalPassageTargets")) {
            if (entry.equalsIgnoreCase(visitorNationName)) {
                return true;
            }
        }
        return false;
    }

    public void setUnconditionalPassage(String ownerNationName, String visitorNationName, boolean enabled) {
        WDEnation ownerNation = getNation(ownerNationName);
        if (ownerNation == null || visitorNationName == null || visitorNationName.isBlank()) {
            return;
        }

        Set<String> targets = readNationStringSet(ownerNation, "unconditionalPassageTargets");
        if (enabled) {
            targets.add(visitorNationName);
        } else {
            targets.removeIf(existing -> existing.equalsIgnoreCase(visitorNationName));
        }

        if (targets.isEmpty()) {
            ownerNation.getSettings().remove("unconditionalPassageTargets");
        } else {
            ownerNation.setSetting("unconditionalPassageTargets", new ArrayList<>(targets));
        }
        saveNations();
    }

    public void addNationHumiliation(String nationName, String sourceNationName) {
        WDEnation nation = getNation(nationName);
        if (nation == null || sourceNationName == null || sourceNationName.isBlank()) {
            return;
        }

        List<Map<String, Object>> humiliations = readNationMapList(nation, "humiliations");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sourceNation", sourceNationName);
        entry.put("appliedAt", System.currentTimeMillis());
        humiliations.add(entry);
        nation.setSetting("humiliations", humiliations);
        saveNations();
    }

    public boolean hasActiveWarTruce(String firstNationName, String secondNationName) {
        return getWarTruceEndAt(firstNationName, secondNationName) > System.currentTimeMillis();
    }

    public long getWarTruceEndAt(String firstNationName, String secondNationName) {
        WDEnation firstNation = getNation(firstNationName);
        if (firstNation == null || secondNationName == null || secondNationName.isBlank()) {
            return 0L;
        }

        WarTruceClock clock = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getWarTruceClock();
        long endAt = 0L;
        List<Map<String, Object>> truceEntries = readNationMapList(firstNation, "warTruces");
        List<Map<String, Object>> retainedEntries = new ArrayList<>();
        boolean changed = false;
        for (Map<String, Object> entry : truceEntries) {
            String targetNation = String.valueOf(entry.getOrDefault("targetNation", ""));
            long targetEndAt = clock.estimatedEndAt(entry);
            if (targetEndAt > System.currentTimeMillis()) {
                retainedEntries.add(entry);
                if (targetNation.equalsIgnoreCase(secondNationName)) {
                    endAt = Math.max(endAt, targetEndAt);
                }
            } else {
                changed = true;
            }
        }

        if (changed) {
            if (retainedEntries.isEmpty()) {
                firstNation.getSettings().remove("warTruces");
            } else {
                firstNation.setSetting("warTruces", retainedEntries);
            }
            saveNations();
        }
        return endAt;
    }

    public void setWarTruce(String firstNationName, String secondNationName, long endAt) {
        setWarTruce(firstNationName, secondNationName, Map.of("clock", "REAL_TIME", "endAt", endAt));
    }

    public void setWarTruce(String firstNationName, String secondNationName, Map<String, Object> deadline) {
        if (firstNationName == null || secondNationName == null
                || firstNationName.isBlank() || secondNationName.isBlank()
                || firstNationName.equalsIgnoreCase(secondNationName)) {
            return;
        }

        upsertWarTruceEntry(firstNationName, secondNationName, deadline);
        upsertWarTruceEntry(secondNationName, firstNationName, deadline);
        saveNations();
    }

    public void purgeExpiredWarTruces() {
        WarTruceClock clock = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getWarTruceClock();
        boolean changed = false;
        for (WDEnation nation : nations.values()) {
            List<Map<String, Object>> entries = readNationMapList(nation, "warTruces");
            if (entries.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> retained = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                if (clock.isActive(entry)) {
                    retained.add(entry);
                }
            }

            if (retained.size() != entries.size()) {
                changed = true;
                if (retained.isEmpty()) {
                    nation.getSettings().remove("warTruces");
                } else {
                    nation.setSetting("warTruces", retained);
                }
            }
        }

        if (changed) {
            saveNations();
        }
    }

    public List<WDEnation> getAllNations() {
        return nations.values().stream().toList();
    }

    public int getNationPopulation(String nationName) {
        WDEnation nation = nations.get(nationName);
        if (nation == null) {
            return 0;
        }

        int residentCount = 0;
        for (String townName : nation.getTownNames()) {
            try {
                Town town = TownyUniverse.getInstance().getTown(townName);
                if (town != null) {
                    residentCount += town.getResidents().size();
                }
            } catch (Exception ignored) {
            }
        }
        return residentCount;
    }

    public int countEligibleNationVoters(WDEnation nation) {
        if (nation == null) {
            return 0;
        }

        if ("NOBLE_COUNCIL".equalsIgnoreCase(nation.getGovernmentType())) {
            int mayorCount = 0;
            for (String townName : nation.getTownNames()) {
                WDEtown town = townManager.getTown(townName);
                if (town != null && town.getTownLeader() != null && !town.getTownLeader().equalsIgnoreCase("None")) {
                    mayorCount++;
                }
            }
            return mayorCount;
        }

        return getNationPopulation(nation.getNationName());
    }

    public boolean isEligibleNationVoter(WDEnation nation, UUID playerUuid) {
        if (nation == null) {
            return false;
        }

        for (String townName : nation.getTownNames()) {
            if ("NOBLE_COUNCIL".equalsIgnoreCase(nation.getGovernmentType())) {
                WDEtown town = townManager.getTown(townName);
                if (town == null || town.getTownLeader() == null) {
                    continue;
                }
                try {
                    Resident resident = TownyUniverse.getInstance().getResident(playerUuid);
                    if (resident != null && town.getTownLeader().equalsIgnoreCase(resident.getName())) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            } else {
                try {
                    Town town = TownyUniverse.getInstance().getTown(townName);
                    if (town == null) {
                        continue;
                    }
                    for (Resident resident : town.getResidents()) {
                        if (playerUuid.equals(resident.getUUID())) {
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return false;
    }

    public void startNationLeadershipVote(WDEnation nation, String candidateName) {
        if (nation == null) {
            return;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
            startDemocracyLeadershipElection(nation, null);
            return;
        }

        nation.setPendingGovernmentLeader(candidateName);
        nation.setGovernmentLeaderVotes(new HashMap<>());
        nation.setGovernmentLeaderCandidates(new ArrayList<>());
        nation.setGovernmentLeaderBallots(new HashMap<>());
        nation.setElectionEndAt(0L);
        saveNations();
    }

    public void startArmyCommanderVote(WDEnation nation, String candidateName) {
        if (nation == null) {
            return;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
            if (!nation.hasActiveArmyCommanderVote()) {
                nation.setPendingArmyCommander("None");
                nation.setArmyCommanderVotes(new HashMap<>());
                nation.setArmyCommanderCandidates(new ArrayList<>());
                nation.setArmyCommanderBallots(new HashMap<>());
                nation.setArmyElectionEndAt(System.currentTimeMillis() + getNationElectionDurationMillis());
            }
            if (candidateName != null && !candidateName.isBlank()
                    && nation.getArmyCommanderCandidates().stream().noneMatch(existing -> existing.equalsIgnoreCase(candidateName))) {
                nation.getArmyCommanderCandidates().add(candidateName);
            }
            saveNations();
            return;
        }

        nation.setPendingArmyCommander(candidateName);
        nation.setArmyCommanderVotes(new HashMap<>());
        nation.setArmyCommanderCandidates(new ArrayList<>());
        nation.setArmyCommanderBallots(new HashMap<>());
        nation.setArmyElectionEndAt(0L);
        saveNations();
    }

    public boolean castNationLeaderVote(WDEnation nation, String voterKey, boolean support) {
        nation.getGovernmentLeaderVotes().put(voterKey, support);
        boolean applied = tryResolveNationLeadershipVote(nation);
        saveNations();
        return applied;
    }

    public boolean castNationLeaderVote(WDEnation nation, String voterKey, String candidateName) {
        if (nation == null || candidateName == null || candidateName.isBlank()) {
            return false;
        }
        nation.getGovernmentLeaderBallots().put(voterKey, candidateName);
        boolean applied = tryResolveNationLeadershipVote(nation);
        saveNations();
        return applied;
    }

    public boolean castArmyCommanderVote(WDEnation nation, String voterKey, boolean support) {
        nation.getArmyCommanderVotes().put(voterKey, support);
        boolean applied = tryResolveArmyCommanderVote(nation);
        saveNations();
        return applied;
    }

    public boolean castArmyCommanderVote(WDEnation nation, String voterKey, String candidateName) {
        if (nation == null || candidateName == null || candidateName.isBlank()) {
            return false;
        }
        nation.getArmyCommanderBallots().put(voterKey, candidateName);
        boolean applied = tryResolveArmyCommanderVote(nation);
        saveNations();
        return applied;
    }

    public boolean tryResolveNationLeadershipVote(WDEnation nation) {
        if (nation == null) {
            return false;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType()) && nation.getGovernmentLeaderCandidates() != null
                && !nation.getGovernmentLeaderCandidates().isEmpty()) {
            return tryResolveDemocracyLeadershipElection(nation);
        }

        if (nation.getPendingGovernmentLeader() == null || nation.getPendingGovernmentLeader().equalsIgnoreCase("None")) {
            return false;
        }

        int eligibleVoters = countEligibleNationVoters(nation);
        if (eligibleVoters <= 0) {
            return false;
        }

        long yesVotes = nation.getGovernmentLeaderVotes().values().stream().filter(Boolean::booleanValue).count();
        long noVotes = nation.getGovernmentLeaderVotes().values().stream().filter(vote -> !vote).count();
        int requiredVotes = (eligibleVoters / 2) + 1;

        if (yesVotes >= requiredVotes) {
            nation.setGovernmentLeader(nation.getPendingGovernmentLeader());
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
                applyDemocracyLeadershipToTowny(nation, nation.getPendingGovernmentLeader());
                nation.setLastElectionAt(System.currentTimeMillis());
                nation.setElectionEndAt(0L);
            }
            nation.setPendingGovernmentLeader("None");
            nation.setGovernmentLeaderVotes(new HashMap<>());
            return true;
        }

        if (noVotes >= requiredVotes || isDemocracyVoteExpired(nation)) {
            nation.setPendingGovernmentLeader("None");
            nation.setGovernmentLeaderVotes(new HashMap<>());
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
                nation.setLastElectionAt(System.currentTimeMillis());
                nation.setElectionEndAt(0L);
            }
        }

        return false;
    }

    public boolean tryResolveArmyCommanderVote(WDEnation nation) {
        if (nation == null) {
            return false;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType()) && nation.getArmyCommanderCandidates() != null
                && !nation.getArmyCommanderCandidates().isEmpty()) {
            return tryResolveDemocracyArmyCommanderElection(nation);
        }

        if (nation.getPendingArmyCommander() == null || nation.getPendingArmyCommander().equalsIgnoreCase("None")) {
            return false;
        }

        int eligibleVoters = countEligibleNationVoters(nation);
        if (eligibleVoters <= 0) {
            return false;
        }

        long yesVotes = nation.getArmyCommanderVotes().values().stream().filter(Boolean::booleanValue).count();
        long noVotes = nation.getArmyCommanderVotes().values().stream().filter(vote -> !vote).count();
        int requiredVotes = (eligibleVoters / 2) + 1;

        if (yesVotes >= requiredVotes) {
            nation.setArmyCommander(nation.getPendingArmyCommander());
            nation.setPendingArmyCommander("None");
            nation.setArmyCommanderVotes(new HashMap<>());
            return true;
        }

        if (noVotes >= requiredVotes) {
            nation.setPendingArmyCommander("None");
            nation.setArmyCommanderVotes(new HashMap<>());
        }

        return false;
    }

    public Location getCapitalSpawnLocation(WDEnation destinationNation) {
        try {
            TownyUniverse townyUniverse = TownyUniverse.getInstance();
            com.palmergames.bukkit.towny.object.Nation townyNation = townyUniverse.getNation(destinationNation.getNationName());

            if (townyNation != null) {
                Town capitalTown = townyNation.getCapital();
                if (capitalTown.hasSpawn()) {
                    return capitalTown.getSpawn();
                } else {
                    plugin.getLogger().warning("The capital town of " + destinationNation.getNationName() + " does not have a spawn location set.");
                }
            } else {
                plugin.getLogger().warning("Nation not found in Towny: " + destinationNation.getNationName());
            }
        } catch (TownyException e) {
            plugin.getLogger().warning("Error getting capital spawn location for " + destinationNation.getNationName() + ": " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error while getting capital spawn location: " + e.getMessage());
        }
        return null;
    }

    public void enableAutoSave() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::saveNations, 12000L, 12000L);
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::processDemocracyElections, 6000L, 6000L);
    }

    public boolean isNationResidentCandidate(WDEnation nation, String candidateName) {
        return findResidentTownName(nation, candidateName) != null;
    }

    public String findTownLedByCandidate(WDEnation nation, String candidateName) {
        if (nation == null || candidateName == null || candidateName.isBlank()) {
            return null;
        }

        for (String townName : nation.getTownNames()) {
            WDEtown town = townManager.getTown(townName);
            if (town != null && town.getTownLeader() != null && town.getTownLeader().equalsIgnoreCase(candidateName)) {
                return town.getTownName();
            }
        }

        return null;
    }

    public String findResidentTownName(WDEnation nation, String candidateName) {
        if (nation == null || candidateName == null || candidateName.isBlank()) {
            return null;
        }

        for (String townName : nation.getTownNames()) {
            try {
                Town town = TownyUniverse.getInstance().getTown(townName);
                Resident resident = TownyUniverse.getInstance().getResident(candidateName);
                if (town != null && resident != null && town.hasResident(resident)) {
                    return town.getName();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public void processDemocracyElections() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (WDEnation nation : nations.values()) {
            if (!"DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
                continue;
            }

            if (nation.hasActiveArmyCommanderVote()) {
                boolean hadActiveArmyVote = nation.hasActiveArmyCommanderVote();
                if (tryResolveArmyCommanderVote(nation)) {
                    changed = true;
                } else if (nation.getArmyElectionEndAt() > 0L && System.currentTimeMillis() > nation.getArmyElectionEndAt()) {
                    clearDemocracyArmyCommanderElection(nation);
                    changed = true;
                } else if (hadActiveArmyVote && !nation.hasActiveArmyCommanderVote()) {
                    changed = true;
                }
            }

            if (nation.hasActiveElection()) {
                boolean hadActiveLeadershipVote = nation.hasActiveElection();
                if (tryResolveNationLeadershipVote(nation)) {
                    changed = true;
                } else if (isDemocracyVoteExpired(nation)) {
                    clearDemocracyLeadershipElection(nation);
                    nation.setLastElectionAt(now);
                    changed = true;
                } else if (hadActiveLeadershipVote && !nation.hasActiveElection()) {
                    changed = true;
                }
                continue;
            }

            long nextElectionAt = nation.getLastElectionAt() + (Math.max(1, nation.getElectionIntervalDays()) * 86400000L);
            if (now < nextElectionAt) {
                continue;
            }

            List<String> candidates = getDemocracyLeadershipCandidates(nation, null);
            if (candidates.isEmpty()) {
                nation.setLastElectionAt(now);
                changed = true;
                continue;
            }

            nation.setPendingGovernmentLeader("None");
            nation.setGovernmentLeaderVotes(new HashMap<>());
            nation.setGovernmentLeaderCandidates(candidates);
            nation.setGovernmentLeaderBallots(new HashMap<>());
            nation.setElectionEndAt(now + getNationElectionDurationMillis());
            changed = true;
        }

        if (changed) {
            saveNations();
        }
    }

    private void rebuildTownHierarchy() {
        for (WDEnation nation : nations.values()) {
            nation.setTownNames(new ArrayList<>());
        }

        for (WDEtown town : townManager.getAllTowns()) {
            if (town.getNationName() == null || town.getNationName().equalsIgnoreCase("None")) {
                continue;
            }

            WDEnation nation = nations.get(town.getNationName());
            if (nation != null && !nation.getTownNames().contains(town.getTownName())) {
                nation.getTownNames().add(town.getTownName());
            }
        }
    }

    private boolean isDemocracyVoteExpired(WDEnation nation) {
        return "DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())
                && nation.getElectionEndAt() > 0L
                && System.currentTimeMillis() > nation.getElectionEndAt();
    }

    private Set<String> readNationStringSet(WDEnation nation, String key) {
        Set<String> values = new LinkedHashSet<>();
        if (nation == null) {
            return values;
        }

        Object rawValue = nation.getSetting(key);
        if (rawValue instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry != null) {
                    values.add(String.valueOf(entry));
                }
            }
        }
        return values;
    }

    private void upsertWarTruceEntry(String ownerNationName, String targetNationName, Map<String, Object> deadline) {
        WDEnation ownerNation = getNation(ownerNationName);
        if (ownerNation == null) {
            return;
        }

        List<Map<String, Object>> entries = readNationMapList(ownerNation, "warTruces");
        boolean updated = false;
        for (Map<String, Object> entry : entries) {
            String existingTarget = String.valueOf(entry.getOrDefault("targetNation", ""));
            if (existingTarget.equalsIgnoreCase(targetNationName)) {
                entry.clear();
                entry.putAll(deadline);
                entry.put("targetNation", targetNationName);
                updated = true;
                break;
            }
        }

        if (!updated) {
            Map<String, Object> entry = new LinkedHashMap<>(deadline);
            entry.put("targetNation", targetNationName);
            entries.add(entry);
        }

        ownerNation.setSetting("warTruces", entries);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readNationMapList(WDEnation nation, String key) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (nation == null) {
            return values;
        }

        Object rawValue = nation.getSetting(key);
        if (!(rawValue instanceof Collection<?> collection)) {
            return values;
        }

        for (Object entry : collection) {
            if (entry instanceof Map<?, ?> map) {
                values.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return values;
    }

    private long getNationElectionDurationMillis() {
        int days = 2;
        try {
            org.bukkit.configuration.file.FileConfiguration nationConfig = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationConfig();
            if (nationConfig != null) {
                days = Math.max(1, nationConfig.getInt("nationElectionDurationDays", 2));
            }
        } catch (Exception ignored) {
        }
        return days * 86400000L;
    }

    public int getDefaultNationElectionIntervalDays() {
        try {
            org.bukkit.configuration.file.FileConfiguration nationConfig = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationConfig();
            if (nationConfig != null) {
                return Math.max(1, nationConfig.getInt("nationElectionIntervalDays", 7));
            }
        } catch (Exception ignored) {
        }
        return 7;
    }

    public boolean applyDemocracyLeadershipToTowny(WDEnation nation, String leaderName) {
        try {
            Nation townyNation = TownyUniverse.getInstance().getNation(nation.getNationName());
            Resident newKing = TownyUniverse.getInstance().getResident(leaderName);
            String leaderTownName = findResidentTownName(nation, leaderName);
            WDEtown leaderTownData = leaderTownName == null ? null : townManager.getTown(leaderTownName);
            Town leaderTown = leaderTownName == null ? null : TownyUniverse.getInstance().getTown(leaderTownName);
            if (townyNation == null || newKing == null || leaderTown == null || leaderTownData == null) {
                return false;
            }
            if (townyNation.getKing() != null && townyNation.getKing().getName().equalsIgnoreCase(leaderName)) {
                return false;
            }

            leaderTownData.setTownLeader(leaderName);
            townManager.applyTownLeaderToTowny(leaderTownData);
            if (!leaderTown.hasResident(newKing) || !leaderTown.isMayor(newKing)) {
                return false;
            }

            townyNation.setCapital(leaderTown);
            townyNation.setKing(newKing);
            townManager.saveTowns();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply democratic nation leadership to Towny for " + nation.getNationName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean forceNationLeader(WDEnation nation, String leaderName) {
        if (nation == null || leaderName == null || leaderName.isBlank()) {
            return false;
        }

        try {
            Nation townyNation = TownyUniverse.getInstance().getNation(nation.getNationName());
            Resident newKing = TownyUniverse.getInstance().getResident(leaderName);
            String leaderTownName = findResidentTownName(nation, leaderName);
            WDEtown leaderTownData = leaderTownName == null ? null : townManager.getTown(leaderTownName);
            Town leaderTown = leaderTownName == null ? null : TownyUniverse.getInstance().getTown(leaderTownName);
            if (townyNation == null || newKing == null || leaderTown == null || leaderTownData == null) {
                return false;
            }

            leaderTownData.setTownLeader(leaderName);
            townManager.applyTownLeaderToTowny(leaderTownData);
            if (!leaderTown.hasResident(newKing) || !leaderTown.isMayor(newKing)) {
                return false;
            }

            townyNation.setCapital(leaderTown);
            townyNation.setKing(newKing);
            nation.setGovernmentLeader(leaderName);
            nation.setPendingGovernmentLeader("None");
            nation.setGovernmentLeaderVotes(new HashMap<>());
            nation.setGovernmentLeaderCandidates(new ArrayList<>());
            nation.setGovernmentLeaderBallots(new HashMap<>());
            townManager.saveTowns();
            saveNations();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to force nation leadership for " + nation.getNationName() + ": " + e.getMessage());
            return false;
        }
    }

    public List<String> getDemocracyLeadershipCandidates(WDEnation nation) {
        return getDemocracyLeadershipCandidates(nation, null);
    }

    public List<String> getDemocracyLeadershipCandidates(WDEnation nation, String excludedCandidate) {
        List<String> candidates = new ArrayList<>();
        if (nation == null) {
            return candidates;
        }

        for (String townName : nation.getTownNames()) {
            WDEtown town = townManager.getTown(townName);
            if (town == null || town.getTownLeader() == null || town.getTownLeader().equalsIgnoreCase("None")) {
                continue;
            }
            if (excludedCandidate != null && town.getTownLeader().equalsIgnoreCase(excludedCandidate)) {
                continue;
            }
            if (candidates.stream().noneMatch(existing -> existing.equalsIgnoreCase(town.getTownLeader()))) {
                candidates.add(town.getTownLeader());
            }
        }
        return candidates;
    }

    public Map<String, Integer> countGovernmentLeaderBallots(WDEnation nation) {
        return countCandidateVotes(nation == null ? List.of() : nation.getGovernmentLeaderCandidates(),
                nation == null ? null : nation.getGovernmentLeaderBallots());
    }

    public Map<String, Integer> countArmyCommanderBallots(WDEnation nation) {
        return countCandidateVotes(nation == null ? List.of() : nation.getArmyCommanderCandidates(),
                nation == null ? null : nation.getArmyCommanderBallots());
    }

    public boolean startEarlyDemocracyLeadershipElection(WDEnation nation, String excludedCandidate) {
        return startDemocracyLeadershipElection(nation, excludedCandidate);
    }

    private boolean startDemocracyLeadershipElection(WDEnation nation, String excludedCandidate) {
        List<String> candidates = getDemocracyLeadershipCandidates(nation, excludedCandidate);
        if (candidates.isEmpty()) {
            return false;
        }

        nation.setPendingGovernmentLeader("None");
        nation.setGovernmentLeaderVotes(new HashMap<>());
        nation.setGovernmentLeaderCandidates(candidates);
        nation.setGovernmentLeaderBallots(new HashMap<>());
        nation.setElectionEndAt(System.currentTimeMillis() + getNationElectionDurationMillis());
        saveNations();
        return true;
    }

    private boolean tryResolveDemocracyLeadershipElection(WDEnation nation) {
        int eligibleVoters = countEligibleNationVoters(nation);
        if (eligibleVoters <= 0) {
            return false;
        }

        Map<String, Integer> counts = countGovernmentLeaderBallots(nation);
        int requiredVotes = (eligibleVoters / 2) + 1;
        String majorityWinner = findMajorityWinner(counts, requiredVotes);
        if (majorityWinner != null) {
            nation.setGovernmentLeader(majorityWinner);
            applyDemocracyLeadershipToTowny(nation, majorityWinner);
            clearDemocracyLeadershipElection(nation);
            nation.setLastElectionAt(System.currentTimeMillis());
            return true;
        }

        if (System.currentTimeMillis() <= nation.getElectionEndAt()) {
            return false;
        }

        String topCandidate = findTopCandidate(counts);
        if (topCandidate != null) {
            nation.setGovernmentLeader(topCandidate);
            applyDemocracyLeadershipToTowny(nation, topCandidate);
            clearDemocracyLeadershipElection(nation);
            nation.setLastElectionAt(System.currentTimeMillis());
            return true;
        }

        clearDemocracyLeadershipElection(nation);
        nation.setLastElectionAt(System.currentTimeMillis());
        return false;
    }

    private boolean tryResolveDemocracyArmyCommanderElection(WDEnation nation) {
        int eligibleVoters = countEligibleNationVoters(nation);
        if (eligibleVoters <= 0) {
            return false;
        }

        Map<String, Integer> counts = countArmyCommanderBallots(nation);
        int requiredVotes = (eligibleVoters / 2) + 1;
        String majorityWinner = findMajorityWinner(counts, requiredVotes);
        if (majorityWinner != null) {
            nation.setArmyCommander(majorityWinner);
            clearDemocracyArmyCommanderElection(nation);
            return true;
        }

        if (System.currentTimeMillis() <= nation.getArmyElectionEndAt()) {
            return false;
        }

        String topCandidate = findTopCandidate(counts);
        if (topCandidate != null) {
            nation.setArmyCommander(topCandidate);
            clearDemocracyArmyCommanderElection(nation);
            return true;
        }

        clearDemocracyArmyCommanderElection(nation);
        return false;
    }

    private void clearDemocracyLeadershipElection(WDEnation nation) {
        nation.setPendingGovernmentLeader("None");
        nation.setGovernmentLeaderVotes(new HashMap<>());
        nation.setGovernmentLeaderCandidates(new ArrayList<>());
        nation.setGovernmentLeaderBallots(new HashMap<>());
        nation.setElectionEndAt(0L);
    }

    private void clearDemocracyArmyCommanderElection(WDEnation nation) {
        nation.setPendingArmyCommander("None");
        nation.setArmyCommanderVotes(new HashMap<>());
        nation.setArmyCommanderCandidates(new ArrayList<>());
        nation.setArmyCommanderBallots(new HashMap<>());
        nation.setArmyElectionEndAt(0L);
    }

    private Map<String, Integer> countCandidateVotes(List<String> candidates, Map<String, String> ballots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (candidates != null) {
            for (String candidate : candidates) {
                counts.put(candidate, 0);
            }
        }

        if (ballots != null) {
            for (String votedCandidate : ballots.values()) {
                if (votedCandidate == null || votedCandidate.isBlank()) {
                    continue;
                }
                String matchedKey = counts.keySet().stream()
                        .filter(existing -> existing.equalsIgnoreCase(votedCandidate))
                        .findFirst()
                        .orElse(votedCandidate);
                counts.put(matchedKey, counts.getOrDefault(matchedKey, 0) + 1);
            }
        }
        return counts;
    }

    private String findMajorityWinner(Map<String, Integer> counts, int requiredVotes) {
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= requiredVotes) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findTopCandidate(Map<String, Integer> counts) {
        String topCandidate = null;
        int topVotes = -1;
        boolean tied = false;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > topVotes) {
                topCandidate = entry.getKey();
                topVotes = entry.getValue();
                tied = false;
            } else if (entry.getValue() == topVotes) {
                tied = true;
            }
        }
        return tied || topVotes <= 0 ? null : topCandidate;
    }

}
