package com.thatguycy.worlddynamicsengine.manager.governance;

import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.manager.war.TownOwnershipHistory;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 维护城镇治理、共和选举、税制与征召名单，并同步 Towny 镇长及国家归属。
 */
public class TownManager {
    private static final String STORAGE_CATEGORY = "towns";

    private final WorldDynamicsEngine plugin;
    private final DataStorageManager dataStorageManager;
    private final Map<String, WDEtown> towns;

    public TownManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.dataStorageManager = plugin.getDataStorageManager();
        this.towns = new LinkedHashMap<>();
        loadTowns();
    }

    public void loadTowns() {
        towns.clear();
        towns.putAll(dataStorageManager.loadStringKeyed(STORAGE_CATEGORY, WDEtown.class));
    }

    public void saveTowns() {
        dataStorageManager.saveStringKeyed(STORAGE_CATEGORY, towns);
    }

    public void importExistingTownyTowns() {
        for (Town town : new ArrayList<>(TownyUniverse.getInstance().getTowns())) {
            registerTown(town);
            Nation nation = town.getNationOrNull();
            WDEtown record = towns.get(town.getName());
            TownOwnershipHistory.change(plugin, record, nation == null ? "None" : nation.getName());
            record.setNationName(nation == null ? "None" : nation.getName());
        }
        saveTowns();
    }

    public void registerTown(Town townyTown) {
        if (towns.containsKey(townyTown.getName())) {
            return;
        }
        Nation nation = townyTown.getNationOrNull();
        WDEtown town = new WDEtown(townyTown.getName(), nation == null ? "None" : nation.getName());
        towns.put(townyTown.getName(), town);
        TownOwnershipHistory.change(plugin, town, town.getNationName());
        syncTownLeadership(townyTown);
        syncTownTaxPolicy(townyTown);
    }

    public void removeTown(String name) {
        towns.remove(name);
        saveTowns();
    }

    public void renameTown(String oldName, String newName) {
        WDEtown town = towns.remove(oldName);
        if (town != null) {
            town.setTownName(newName);
            towns.put(newName, town);
            saveTowns();
        }
    }

    public void updateTownNation(String townName, String nationName) {
        WDEtown town = towns.get(townName);
        if (town != null) {
            TownOwnershipHistory.change(plugin, town, nationName);
            town.setNationName(nationName == null ? "None" : nationName);
            saveTowns();
        }
    }

    public WDEtown getTown(String townName) {
        return towns.get(townName);
    }

    public List<WDEtown> getAllTowns() {
        return new ArrayList<>(towns.values());
    }

    public void addOrganizationToTown(String townName, String organizationName) {
        WDEtown town = towns.get(townName);
        if (town == null) {
            return;
        }
        town.addOrganization(organizationName);
        saveTowns();
    }

    public void removeOrganizationFromTown(String townName, String organizationName) {
        WDEtown town = towns.get(townName);
        if (town == null) {
            return;
        }
        town.removeOrganization(organizationName);
        saveTowns();
    }

    public boolean transferTownToNation(String townName, String destinationNationName) {
        if (townName == null || townName.isBlank() || destinationNationName == null || destinationNationName.isBlank()) {
            return false;
        }

        try {
            Town townyTown = TownyUniverse.getInstance().getTown(townName);
            Nation destinationNation = TownyUniverse.getInstance().getNation(destinationNationName);
            if (plugin.getNationManager().getNation(destinationNationName) == null
                    || townyTown == null || destinationNation == null) {
                return false;
            }
            registerTown(townyTown);
            WDEtown town = towns.get(townName);

            if (townyTown.hasNation()) {
                Nation currentNation = townyTown.getNationOrNull();
                if (currentNation != null && currentNation.getName().equalsIgnoreCase(destinationNationName)) {
                    return false;
                }
                if (currentNation != null && townyTown.isCapital() && currentNation.getTowns().size() > 1) {
                    Town successor = currentNation.getTowns().stream().filter(candidate -> candidate != townyTown)
                            .sorted(Comparator.comparingInt(Town::getNumResidents).reversed()
                                    .thenComparing(Town::getName, String.CASE_INSENSITIVE_ORDER))
                            .findFirst().orElse(null);
                    if (successor == null || successor.getMayor() == null) return false;
                    Resident newKing = successor.getMayor();
                    currentNation.setCapital(successor);
                    try {
                        currentNation.setKing(newKing);
                        if (!newKing.equals(currentNation.getKing())) {
                            currentNation.setCapital(townyTown);
                            return false;
                        }
                    } catch (Exception e) {
                        currentNation.setCapital(townyTown);
                        throw e;
                    }
                    currentNation.save();
                    var nationData = plugin.getNationManager().getNation(currentNation.getName());
                    if (nationData != null) {
                        nationData.setGovernmentLeader(newKing.getName());
                        nationData.setPendingGovernmentLeader("None");
                        nationData.setGovernmentLeaderVotes(new HashMap<>());
                        nationData.setGovernmentLeaderCandidates(new ArrayList<>());
                        nationData.setGovernmentLeaderBallots(new HashMap<>());
                    }
                }
                townyTown.removeNation();
                if (currentNation != null && townyTown.getNationOrNull() == currentNation) return false;
            }

            townyTown.setNation(destinationNation, true);
            townyTown.save();
            destinationNation.save();

            TownOwnershipHistory.change(plugin, town, destinationNationName);
            town.setNationName(destinationNationName);
            saveTowns();
            plugin.getNationManager().refreshTownHierarchy();
            return true;
        } catch (AlreadyRegisteredException ignored) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to transfer town " + townName + " to nation " + destinationNationName + ": " + e.getMessage());
            return false;
        }
    }

    public void enableAutoSave() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::processRepublicElections, 6000L, 6000L);
    }

    public void syncTownLeadership(Town townyTown) {
        WDEtown town = towns.get(townyTown.getName());
        if (town == null) {
            return;
        }

        try {
            Resident mayor = townyTown.getMayor();
            if (mayor != null && (town.getTownLeader() == null || town.getTownLeader().equalsIgnoreCase("None"))) {
                town.setTownLeader(mayor.getName());
            }
        } catch (Exception ignored) {
        }
    }

    public int countEligibleTownVoters(String townName) {
        try {
            Town town = TownyUniverse.getInstance().getTown(townName);
            if (town == null) {
                return 0;
            }
            return town.getResidents().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isTownResident(String townName, UUID playerUuid) {
        try {
            Town town = TownyUniverse.getInstance().getTown(townName);
            if (town == null) {
                return false;
            }

            for (Resident resident : town.getResidents()) {
                if (playerUuid.equals(resident.getUUID())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public int getTownPopulation(String townName) {
        return countEligibleTownVoters(townName);
    }

    public int getTownConscriptLimit(WDEtown town) {
        if (town == null) {
            return 0;
        }

        int population = getTownPopulation(town.getTownName());
        if (population <= 0) {
            return 0;
        }

        double ratio = 0.5D;
        try {
            if (plugin.getTownConfig() != null) {
                ratio = Math.max(0D, plugin.getTownConfig().getDouble("townConscriptLimitRatio", 0.5D));
            }
        } catch (Exception ignored) {
        }

        OrgManager orgManager = plugin.getOrgManager();
        if (orgManager != null && orgManager.isGarrisonNation(town.getNationName())) {
            ratio = 1.0D;
        }

        int limit = (int) Math.floor(population * ratio);
        if (ratio > 0D && limit <= 0) {
            limit = 1;
        }
        return Math.min(population, limit);
    }

    public void startTownElection(WDEtown town, String candidateName) {
        if (town == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!town.hasActiveElection()) {
            town.setPendingTownLeader("None");
            town.setTownLeaderVotes(new HashMap<>());
            town.setTownLeaderCandidates(new ArrayList<>());
            town.setTownLeaderBallots(new LinkedHashMap<>());
            town.setElectionEndAt(now + getElectionDurationMillis());
        }

        if (candidateName != null && !candidateName.isBlank()
                && town.getTownLeaderCandidates().stream().noneMatch(existing -> existing.equalsIgnoreCase(candidateName))) {
            town.getTownLeaderCandidates().add(candidateName);
        }
        saveTowns();
    }

    public boolean castTownLeaderVote(WDEtown town, String voterKey, boolean support) {
        town.getTownLeaderVotes().put(voterKey, support);
        boolean applied = tryResolveTownElection(town);
        saveTowns();
        return applied;
    }

    public boolean castTownLeaderVote(WDEtown town, String voterKey, String candidateName) {
        if (town == null || candidateName == null || candidateName.isBlank()) {
            return false;
        }

        town.getTownLeaderBallots().put(voterKey, candidateName);
        boolean applied = tryResolveTownElection(town);
        saveTowns();
        return applied;
    }

    public boolean tryResolveTownElection(WDEtown town) {
        if (town == null || town.getPendingTownLeader() == null || town.getPendingTownLeader().equalsIgnoreCase("None")) {
            if (town.getTownLeaderCandidates() == null || town.getTownLeaderCandidates().isEmpty()) {
                return false;
            }
        }

        int eligibleVoters = countEligibleTownVoters(town.getTownName());
        if (eligibleVoters <= 0) {
            return false;
        }

        if (town.getTownLeaderCandidates() != null && !town.getTownLeaderCandidates().isEmpty()) {
            return tryResolveRepublicElection(town, eligibleVoters);
        }

        long yesVotes = town.getTownLeaderVotes().values().stream().filter(Boolean::booleanValue).count();
        long noVotes = town.getTownLeaderVotes().values().stream().filter(vote -> !vote).count();
        int requiredVotes = (eligibleVoters / 2) + 1;

        if (yesVotes >= requiredVotes) {
            town.setTownLeader(town.getPendingTownLeader());
            applyTownLeaderToTowny(town);
            town.setPendingTownLeader("None");
            town.setTownLeaderVotes(new HashMap<>());
            town.setLastElectionAt(System.currentTimeMillis());
            town.setElectionEndAt(0L);
            return true;
        }

        if (noVotes >= requiredVotes || (System.currentTimeMillis() > town.getElectionEndAt() && yesVotes < requiredVotes)) {
            town.setPendingTownLeader("None");
            town.setTownLeaderVotes(new HashMap<>());
            town.setLastElectionAt(System.currentTimeMillis());
            town.setElectionEndAt(0L);
        }

        return false;
    }

    public void processRepublicElections() {
        long now = System.currentTimeMillis();
        for (WDEtown town : towns.values()) {
            if (!"REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) {
                continue;
            }

            if (town.hasActiveElection()) {
                tryResolveTownElection(town);
                continue;
            }

            long nextElectionAt = town.getLastElectionAt() + (Math.max(1, town.getElectionIntervalDays()) * 86400000L);
            if (now >= nextElectionAt) {
                town.setPendingTownLeader("None");
                town.setTownLeaderVotes(new HashMap<>());
                town.setTownLeaderCandidates(new ArrayList<>());
                town.setTownLeaderBallots(new LinkedHashMap<>());
                town.setElectionEndAt(now + getElectionDurationMillis());
            }
        }
        saveTowns();
    }

    public boolean applyTownLeaderToTowny(WDEtown town) {
        try {
            Town townyTown = TownyUniverse.getInstance().getTown(town.getTownName());
            Resident newMayor = TownyUniverse.getInstance().getResident(town.getTownLeader());
            if (townyTown == null || newMayor == null || !townyTown.hasResident(newMayor)) {
                return false;
            }

            townyTown.setMayor(newMayor);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply town leader to Towny for " + town.getTownName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean configureResidentTax(WDEtown town, String taxType, double amount, boolean managedByNation) {
        if (town == null) {
            return false;
        }

        town.setResidentTaxManagedByNation(managedByNation);
        town.setResidentTaxType(taxType);
        town.setResidentTaxAmount(amount);
        boolean applied = applyStoredResidentTaxToTowny(town);
        saveTowns();
        return applied;
    }

    public boolean applyStoredResidentTaxToTowny(WDEtown town) {
        if (town == null || !town.isResidentTaxManagedByNation()) {
            return false;
        }

        try {
            Town townyTown = TownyUniverse.getInstance().getTown(town.getTownName());
            if (townyTown == null) {
                return false;
            }

            boolean percentage = "WEALTH_PERCENTAGE".equalsIgnoreCase(town.getResidentTaxType());
            townyTown.setTaxPercentage(percentage);
            townyTown.setTaxes(Math.max(0D, town.getResidentTaxAmount()));
            townyTown.save();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply resident tax policy to Towny for " + town.getTownName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isConscriptTaxExempt(String townName, String playerName) {
        WDEtown town = getTown(townName);
        return town != null && town.isConscriptTaxExempt(playerName);
    }

    public void syncConscriptData(Town townyTown) {
        WDEtown town = towns.get(townyTown.getName());
        if (town == null) {
            return;
        }

        java.util.LinkedHashMap<String, Boolean> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : town.getConscripts().entrySet()) {
            String playerName = entry.getKey();
            if (playerName == null || playerName.isBlank()) {
                continue;
            }
            try {
                Resident resident = TownyUniverse.getInstance().getResident(playerName);
                if (resident != null && townyTown.hasResident(resident)) {
                    normalized.put(resident.getName(), Boolean.TRUE.equals(entry.getValue()));
                }
            } catch (Exception ignored) {
            }
        }

        int limit = getTownConscriptLimit(town);
        java.util.LinkedHashMap<String, Boolean> trimmed = new java.util.LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Boolean> entry : normalized.entrySet()) {
            if (count >= limit) {
                break;
            }
            trimmed.put(entry.getKey(), entry.getValue());
            count++;
        }
        town.setConscripts(trimmed);
    }

    private boolean tryResolveRepublicElection(WDEtown town, int eligibleVoters) {
        Map<String, Integer> voteCounts = countTownLeaderVotes(town);
        int requiredVotes = (eligibleVoters / 2) + 1;
        String majorityWinner = null;

        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() >= requiredVotes) {
                majorityWinner = entry.getKey();
                break;
            }
        }

        if (majorityWinner != null) {
            town.setTownLeader(majorityWinner);
            applyTownLeaderToTowny(town);
            clearTownElection(town);
            town.setLastElectionAt(System.currentTimeMillis());
            return true;
        }

        if (System.currentTimeMillis() <= town.getElectionEndAt()) {
            return false;
        }

        String topCandidate = null;
        int topVotes = -1;
        boolean tied = false;
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > topVotes) {
                topCandidate = entry.getKey();
                topVotes = entry.getValue();
                tied = false;
            } else if (entry.getValue() == topVotes) {
                tied = true;
            }
        }

        if (!tied && topCandidate != null && topVotes > 0) {
            town.setTownLeader(topCandidate);
            applyTownLeaderToTowny(town);
            clearTownElection(town);
            town.setLastElectionAt(System.currentTimeMillis());
            return true;
        }

        clearTownElection(town);
        town.setLastElectionAt(System.currentTimeMillis());
        return false;
    }

    public Map<String, Integer> countTownLeaderVotes(WDEtown town) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (town == null) {
            return counts;
        }

        List<String> candidates = town.getTownLeaderCandidates();
        if (candidates != null) {
            for (String candidate : candidates) {
                counts.put(candidate, 0);
            }
        }

        if (town.getTownLeaderBallots() != null) {
            for (String candidate : town.getTownLeaderBallots().values()) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }

                String matchedKey = counts.keySet().stream()
                        .filter(existing -> existing.equalsIgnoreCase(candidate))
                        .findFirst()
                        .orElse(candidate);
                counts.put(matchedKey, counts.getOrDefault(matchedKey, 0) + 1);
            }
        }
        return counts;
    }

    private void clearTownElection(WDEtown town) {
        town.setPendingTownLeader("None");
        town.setTownLeaderVotes(new HashMap<>());
        town.setTownLeaderCandidates(new ArrayList<>());
        town.setTownLeaderBallots(new LinkedHashMap<>());
        town.setElectionEndAt(0L);
    }

    public void syncTownTaxPolicy(Town townyTown) {
        WDEtown town = towns.get(townyTown.getName());
        if (town == null) {
            return;
        }

        if (town.isResidentTaxManagedByNation()) {
            applyStoredResidentTaxToTowny(town);
            return;
        }

        town.setResidentTaxType(townyTown.isTaxPercentage() ? "WEALTH_PERCENTAGE" : "FIXED");
        town.setResidentTaxAmount(Math.max(0D, townyTown.getTaxes()));
    }

    private long getElectionDurationMillis() {
        int durationDays = plugin.getTownElectionDurationDays();
        return durationDays * 86400000L;
    }

}
