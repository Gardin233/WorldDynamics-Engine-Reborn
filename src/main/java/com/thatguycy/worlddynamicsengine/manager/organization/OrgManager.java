package com.thatguycy.worlddynamicsengine.manager.organization;

import com.palmergames.bukkit.towny.object.economy.BankAccount;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.economy.GovernmentTreasury;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.manager.war.WarOccupation;
import com.thatguycy.worlddynamicsengine.model.organization.SovereignSubjectState;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import com.thatguycy.worlddynamicsengine.model.WarLedger;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 协调国家集团、从仆国、战争和和平条约的长期状态，统一校验参战、结算及持久化规则。
 */
public class OrgManager {
    private static final String STORAGE_CATEGORY = "organizations";
    private static final String SUBJECT_STATES_SETTING = "sovereignSubjectStates";
    private static final String ACTIVE_MILITARY_WARS_SETTING = "activeMilitaryWars";
    private static final String PEACE_TREATY_PROPOSALS_SETTING = "peaceTreatyProposals";

    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final DataStorageManager dataStorageManager;
    private final Map<String, WDEorg> organizations;
    private final Map<UUID, Long> independenceWarRespawnLocks;
    private WarLedger warLedger;

    public OrgManager(JavaPlugin plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.dataStorageManager = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getDataStorageManager();
        this.organizations = new LinkedHashMap<>();
        this.independenceWarRespawnLocks = new ConcurrentHashMap<>();
        this.warLedger = dataStorageManager.loadStringKeyed("wars", WarLedger.class)
                .getOrDefault("ledger", new WarLedger());
        loadOrganizations();
    }

    public void loadOrganizations() {
        organizations.clear();
        organizations.putAll(dataStorageManager.loadStringKeyed(STORAGE_CATEGORY, WDEorg.class));
        boolean changed = false;
        for (WDEorg org : organizations.values()) {
            if (org.getSetting(ACTIVE_MILITARY_WARS_SETTING) instanceof Map<?, ?> legacy) {
                legacy.forEach((key, value) -> warLedger.wars.putIfAbsent(String.valueOf(key), value));
                org.getSettings().remove(ACTIVE_MILITARY_WARS_SETTING);
                changed = true;
            }
            changed |= synchronizeSovereignSubjectStates(org);
            changed |= synchronizeIdeologicalAllianceSettings(org);
        }
        if (changed) {
            saveOrganizations();
        }
        plugin.getLogger().info("Total organizations loaded: " + organizations.size());
    }

    public void saveOrganizations() {
        dataStorageManager.saveEntity("wars", "ledger", warLedger);
        dataStorageManager.saveStringKeyed(STORAGE_CATEGORY, organizations);
    }

    private List<WDEorg> warContexts() {
        List<WDEorg> contexts = getAllOrganizations();
        contexts.addFirst(null);
        return contexts;
    }

    private String warScope(WDEorg org) {
        return org == null ? "war" : org.getOrganizationName();
    }

    public WDEorg getOrganization(String organizationName) {
        return organizations.get(organizationName);
    }

    public List<String> getAllOrganizationNames() {
        List<String> orgNames = new ArrayList<>();
        if (!organizations.isEmpty()) {
            orgNames.addAll(organizations.keySet());
        }
        return orgNames;
    }

    public boolean addOrganization(WDEorg organization) {
        if (isMilitaryAlliance(organization)
                && organization.getMemberNationNames().stream().anyMatch(this::isNationNeutral)) {
            return false;
        }
        if (isIdeologicalAlliance(organization)
                && "None".equalsIgnoreCase(getIdeologicalAllianceIdeology(organization))) {
            return false;
        }
        organizations.put(organization.getOrganizationName(), organization);
        if (organization.getTownName() != null && !organization.getTownName().equalsIgnoreCase("None")) {
            townManager.addOrganizationToTown(organization.getTownName(), organization.getOrganizationName());
        }
        saveOrganizations();
        return true;
    }

    public List<WDEorg> getAllOrganizations() {
        return new ArrayList<>(organizations.values());
    }

    public void updateBalance(WDEorg org, double newBalance) {
        if (!organizations.containsKey(org.getOrganizationName())) {
            return;
        }

        org.setBalance(newBalance);
    }

    public void removeOrganization(WDEorg organization) {
        organizations.remove(organization.getOrganizationName(), organization);
        if (organization.getTownName() != null && !organization.getTownName().equalsIgnoreCase("None")) {
            townManager.removeOrganizationFromTown(organization.getTownName(), organization.getOrganizationName());
        }
    }

    public void setBalance(WDEorg org, double newBalance) {
        org.setBalance(newBalance);
        saveOrganizations();
    }

    public boolean organizationExists(String orgName) {
        return organizations.containsKey(orgName);
    }

    public boolean isMemberNation(WDEorg org, String nationName) {
        return org != null && org.hasMemberNation(nationName);
    }

    public boolean addMemberNation(WDEorg org, String nationName) {
        if (org == null || nationName == null) {
            return false;
        }
        if (isOrganizationMembershipBlocked(org, nationName)) {
            rejectNationApplication(org, nationName);
            return false;
        }
        org.addMemberNation(nationName);
        initializeSovereignSubjectStateIfNeeded(org, nationName);
        org.getPendingNationApplications().removeIf(existing -> existing.equalsIgnoreCase(nationName));
        org.getPendingNationVotes().remove(nationName);
        saveOrganizations();
        return true;
    }

    public void removeMemberNation(WDEorg org, String nationName) {
        if (org == null || nationName == null) {
            return;
        }
        org.removeMemberNation(nationName);
        removeSovereignSubjectState(org, nationName);
        saveOrganizations();
    }

    public void removeMemberNationAndAdjust(WDEorg org, String nationName) {
        if (org == null || nationName == null) {
            return;
        }

        boolean removedChairman = org.getChairmanNationName() != null && org.getChairmanNationName().equalsIgnoreCase(nationName);
        org.removeMemberNation(nationName);
        removeSovereignSubjectState(org, nationName);

        if (org.getMemberNationNames().isEmpty()) {
            removeOrganization(org);
            saveOrganizations();
            return;
        }

        if (removedChairman) {
            org.setChairmanNationName(org.getMemberNationNames().iterator().next());
        }
        saveOrganizations();
    }

    public boolean startNationApplication(WDEorg org, String nationName) {
        if (org == null || nationName == null || isOrganizationMembershipBlocked(org, nationName)) {
            return false;
        }
        org.getPendingNationApplications().add(nationName);
        org.getPendingNationVotes().put(nationName, new LinkedHashMap<>());
        saveOrganizations();
        return true;
    }

    public void rejectNationApplication(WDEorg org, String nationName) {
        if (org == null || nationName == null) {
            return;
        }
        org.getPendingNationApplications().removeIf(existing -> existing.equalsIgnoreCase(nationName));
        org.getPendingNationVotes().remove(nationName);
        saveOrganizations();
    }

    public boolean castNationJoinVote(WDEorg org, String applicantNationName, String voterNationName, boolean support, NationManager nationManager) {
        if (org == null || applicantNationName == null || voterNationName == null) {
            return false;
        }
        if (!org.hasMemberNation(voterNationName) || isOrganizationMembershipBlocked(org, voterNationName)) return false;
        Map<String, Boolean> votes = org.getPendingNationVotes().computeIfAbsent(applicantNationName, key -> new LinkedHashMap<>());
        votes.put(voterNationName, support);
        boolean applied = tryResolveNationJoinVote(org, applicantNationName, nationManager);
        saveOrganizations();
        return applied;
    }

    public boolean tryResolveNationJoinVote(WDEorg org, String applicantNationName, NationManager nationManager) {
        if (org == null || applicantNationName == null || !org.hasPendingApplication(applicantNationName)) {
            return false;
        }
        if (isOrganizationMembershipBlocked(org, applicantNationName)) {
            rejectNationApplication(org, applicantNationName);
            return false;
        }

        Map<String, Boolean> votes = org.getPendingNationVotes().getOrDefault(applicantNationName, new LinkedHashMap<>());
        double totalWeight = 0.0D;
        double yesWeight = 0.0D;
        double noWeight = 0.0D;

        for (String memberNationName : org.getMemberNationNames()) {
            if (isOrganizationMembershipBlocked(org, memberNationName)) continue;
            double weight = getNationWeight(org, memberNationName, nationManager);
            totalWeight += weight;

            Boolean vote = findVoteIgnoreCase(votes, memberNationName);
            if (vote == null) {
                continue;
            }

            if (vote) {
                yesWeight += weight;
            } else {
                noWeight += weight;
            }
        }

        if (yesWeight > totalWeight / 2.0D) {
            return addMemberNation(org, applicantNationName);
        }

        if (noWeight >= totalWeight / 2.0D) {
            rejectNationApplication(org, applicantNationName);
        }

        return false;
    }

    public double getNationWeight(WDEorg org, String nationName, NationManager nationManager) {
        if (org != null && org.isSovereignEmpire()) {
            return org.getChairmanNationName() != null && org.getChairmanNationName().equalsIgnoreCase(nationName) ? 1.0D : 0.0D;
        }

        double treasury = getNationTreasury(nationName);
        int population = nationManager.getNationPopulation(nationName);
        if (org == null || org.getOrganizationType() == null) {
            return treasury + (population * 1000.0D);
        }

        return switch (org.getOrganizationType().toUpperCase(java.util.Locale.ROOT)) {
            case "TRADE_ALLIANCE" -> (treasury * 5.0D) + (population * 1000.0D);
            case "MILITARY_ALLIANCE" -> treasury + (population * 1000.0D);
            case "IDEOLOGICAL_ALLIANCE" -> {
                IdeologyPopulationStats stats = getNationIdeologyPopulationStats(nationName, nationManager);
                yield treasury + (stats.sameIdeologyPopulation() * 1000.0D) + (stats.differentIdeologyPopulation() * 500.0D);
            }
            default -> treasury + (population * 1000.0D);
        };
    }

    public double getNationWeight(String nationName, NationManager nationManager) {
        return getNationWeight(null, nationName, nationManager);
    }

    public double getNationTreasury(String nationName) {
        try {
            Nation townyNation = TownyUniverse.getInstance().getNation(nationName);
            if (townyNation == null) {
                return 0.0D;
            }

            Double directValue = readBalanceValue(townyNation);
            if (directValue != null) {
                return directValue;
            }

            Object account = invokeNoArgMethod(townyNation, "getAccount");
            if (account != null) {
                Double accountValue = readBalanceValue(account);
                if (accountValue != null) {
                    return accountValue;
                }
            }
        } catch (Exception ignored) {
        }

        return 0.0D;
    }

    private IdeologyPopulationStats getNationIdeologyPopulationStats(String nationName, NationManager nationManager) {
        WDEnation nation = nationManager.getNation(nationName);
        if (nation == null) {
            return new IdeologyPopulationStats(0, 0);
        }

        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        ResidentManager residentManager = engine.getResidentManager();
        String nationIdeology = normalizeIdeologyName(nation.getReligionName());
        int sameIdeologyPopulation = 0;
        int differentIdeologyPopulation = 0;

        for (String townName : nation.getTownNames()) {
            try {
                Town town = TownyUniverse.getInstance().getTown(townName);
                if (town == null) {
                    continue;
                }

                for (Resident resident : town.getResidents()) {
                    WDEresident wdeResident = residentManager == null ? null : residentManager.getResident(resident.getUUID());
                    String residentIdeology = normalizeIdeologyName(wdeResident == null ? null : wdeResident.getReligionName());
                    if (nationIdeology.equalsIgnoreCase(residentIdeology)) {
                        sameIdeologyPopulation++;
                    } else {
                        differentIdeologyPopulation++;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return new IdeologyPopulationStats(sameIdeologyPopulation, differentIdeologyPopulation);
    }

    private String normalizeIdeologyName(String ideologyName) {
        return ideologyName == null || ideologyName.isBlank() || "none".equalsIgnoreCase(ideologyName) ? "None" : ideologyName;
    }

    /**
     * 汇总同信仰与异信仰人口，供意识形态联盟权重与折扣计算使用。
     */
    private record IdeologyPopulationStats(int sameIdeologyPopulation, int differentIdeologyPopulation) {
    }

    private WDEorg getPrimaryIdeologicalAllianceForNation(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return null;
        }
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        NationManager nationManager = engine.getNationManager();
        if (nationManager == null) {
            return null;
        }

        return organizations.values().stream()
                .filter(this::isIdeologicalAlliance)
                .filter(this::hasIdeologicalAllianceIdeology)
                .filter(org -> org.hasMemberNation(nationName))
                .max(Comparator.comparingInt(org -> getIdeologicalAlliancePopulation(org, nationManager)))
                .orElse(null);
    }

    private int getIdeologicalAlliancePopulation(WDEorg org, NationManager nationManager) {
        int totalPopulation = 0;
        for (String memberNation : org.getMemberNationNames()) {
            totalPopulation += Math.max(0, nationManager.getNationPopulation(memberNation));
        }
        return totalPopulation;
    }

    private double getIdeologicalAllianceSameReligionTreatyMultiplier() {
        return Math.max(0D, getOrganizationConfiguration().getDouble(
                "ideologicalAlliance.peaceTreaty.sameReligionClauseCostMultiplier", 1.50D));
    }

    private double getIdeologicalAllianceHereticTreatyMultiplier() {
        return Math.max(0D, getOrganizationConfiguration().getDouble(
                "ideologicalAlliance.peaceTreaty.hereticClauseCostMultiplier", 0.75D));
    }

    public List<WDEorg> getOrganizationsByTown(String townName) {
        return organizations.values().stream()
                .filter(org -> townName.equalsIgnoreCase(org.getTownName()))
                .toList();
    }

    public List<WDEorg> getOrganizationsByNation(String nationName) {
        return organizations.values().stream()
                .filter(org -> org.hasMemberNation(nationName))
                .toList();
    }

    public WDEorg getSovereignEmpireByNation(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return null;
        }
        return organizations.values().stream()
                .filter(WDEorg::isSovereignEmpire)
                .filter(org -> org.hasMemberNation(nationName))
                .findFirst()
                .orElse(null);
    }

    public boolean isSovereignSubjectNation(WDEorg org, String nationName) {
        if (org == null || nationName == null || !org.isSovereignEmpire() || !org.hasMemberNation(nationName)) {
            return false;
        }
        return org.getChairmanNationName() == null || !org.getChairmanNationName().equalsIgnoreCase(nationName);
    }

    public boolean isGarrisonNation(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return false;
        }

        WDEorg sovereignEmpire = getSovereignEmpireByNation(nationName);
        return sovereignEmpire != null && isGarrisonSubject(sovereignEmpire, nationName);
    }

    public SovereignSubjectState.AutonomyTier getSubjectAutonomyTier(WDEorg org, String nationName) {
        return getSubjectState(org, nationName).getAutonomyTier();
    }

    public int getSubjectSuppression(WDEorg org, String nationName) {
        return getSubjectState(org, nationName).getSuppression();
    }

    public boolean isGarrisonSubject(WDEorg org, String nationName) {
        return getSubjectState(org, nationName).isGarrisonState();
    }

    public boolean canPromoteSubjectAutonomy(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return false;
        }

        SovereignSubjectState state = getSubjectState(org, nationName);
        return state.getSuppression() <= 0
                && !hasActiveIndependenceWar(org, nationName)
                && (state.getAutonomyTier().canPromote() || !isNationInActiveWar(nationName));
    }

    public boolean peacefullyReleaseDominion(WDEorg org, String nationName) {
        if (!canPromoteSubjectAutonomy(org, nationName)
                || getSubjectAutonomyTier(org, nationName) != SovereignSubjectState.AutonomyTier.DOMINION
                || isNationInActiveWar(nationName)) {
            return false;
        }

        // Independence ends membership rather than adding a permanent subject tier.
        removeMemberNationAndAdjust(org, nationName);
        return true;
    }

    public boolean adminSetSubject(WDEorg org, String nationName, String field, String value) {
        if (!isSovereignSubjectNation(org, nationName) || isNationInActiveWar(nationName)) return false;
        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) return false;
        SovereignSubjectState state = states.get(key);
        switch (field) {
            case "tier" -> state.setAutonomyTier(SovereignSubjectState.AutonomyTier.valueOf(value));
            case "suppression" -> {
                int amount = Integer.parseInt(value);
                if (amount < 0 || amount > 100) return false;
                state.setSuppression(amount);
            }
            case "garrison" -> {
                state.setGarrisonState(Boolean.parseBoolean(value));
                if (state.isGarrisonState()) state.setWarParticipationRequired(true);
            }
            case "duty" -> {
                boolean required = Boolean.parseBoolean(value);
                if (state.isGarrisonState() && !required) return false;
                if (required && isNationNeutral(nationName)) return false;
                state.setWarParticipationRequired(required);
            }
            default -> { return false; }
        }
        writeSovereignSubjectStates(org, states);
        enforceSubjectTownTaxCaps(org, nationName);
        saveOrganizations();
        return true;
    }

    public boolean adminSetChairman(WDEorg org, String nationName) {
        if (org == null || !org.hasMemberNation(nationName)
                || org.getMemberNationNames().stream().anyMatch(this::isNationInActiveWar)) return false;
        Nation chairman = resolveTownyNation(nationName);
        if (chairman == null || chairman.getKing() == null) return false;
        org.setChairmanNationName(nationName);
        org.setLeaderUUID(chairman.getKing().getUUID());
        synchronizeSovereignSubjectStates(org);
        for (String member : org.getMemberNationNames()) enforceSubjectTownTaxCaps(org, member);
        saveOrganizations();
        return true;
    }

    public boolean adminEndMilitaryWar(String firstNation, String secondNation) {
        Set<String> keys = new LinkedHashSet<>();
        for (WDEorg org : warContexts()) {
            for (MilitaryWarState war : getActiveMilitaryWars(org)) {
                if (war.containsOpposingNations(firstNation, secondNation)) keys.add(war.getWarKey());
            }
        }
        for (String key : keys) clearMilitaryWarAcrossOrganizations(key);
        saveOrganizations();
        return !keys.isEmpty();
    }

    public boolean adminSetMilitaryScores(String firstNation, String secondNation, double attacker, double defender) {
        if (!Double.isFinite(attacker) || !Double.isFinite(defender) || attacker < 0 || defender < 0) return false;
        boolean changed = false;
        for (WDEorg org : warContexts()) {
            Map<String, MilitaryWarState> wars = readActiveMilitaryWars(org);
            for (var entry : wars.entrySet()) {
                MilitaryWarState war = entry.getValue();
                if (!war.containsOpposingNations(firstNation, secondNation)) continue;
                Map<String, Object> serialized = war.toMap();
                serialized.put("attackerScore", attacker);
                serialized.put("defenderScore", defender);
                entry.setValue(MilitaryWarState.fromMap(war.getWarKey(), serialized));
                Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
                proposals.remove(war.getWarKey());
                writePeaceTreatyProposals(org, proposals);
                changed = true;
            }
            writeActiveMilitaryWars(org, wars);
        }
        if (changed) saveOrganizations();
        return changed;
    }

    public boolean adminResolveIndependence(WDEorg org, String nationName, IndependenceWarOutcome outcome) {
        Map<String, IndependenceWarState> wars = readIndependenceWars(org);
        String key = findIndependenceWarKey(wars, nationName);
        if (key == null) return false;
        IndependenceWarResolutionResult result = resolveIndependenceWar(org, wars, wars.get(key), outcome);
        Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
        proposals.remove("independence:" + nationName);
        writePeaceTreatyProposals(org, proposals);
        saveOrganizations();
        announceIndependenceWarResolution(org, result);
        return true;
    }

    public boolean adminSetIndependenceScores(WDEorg org, String nationName, double rebel, double empire) {
        if (!Double.isFinite(rebel) || !Double.isFinite(empire) || rebel < 0 || empire < 0) return false;
        Map<String, IndependenceWarState> wars = readIndependenceWars(org);
        String key = findIndependenceWarKey(wars, nationName);
        if (key == null) return false;
        Map<String, Object> serialized = wars.get(key).toMap();
        serialized.put("rebelScore", rebel);
        serialized.put("empireScore", empire);
        wars.put(key, IndependenceWarState.fromMap(key, serialized));
        writeIndependenceWars(org, wars);
        Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
        proposals.remove("independence:" + key);
        writePeaceTreatyProposals(org, proposals);
        saveOrganizations();
        return true;
    }

    public boolean canDemoteSubjectAutonomy(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return false;
        }

        SovereignSubjectState state = getSubjectState(org, nationName);
        return state.getSuppression() >= 100 && state.getAutonomyTier().canDemote();
    }

    public SovereignSubjectState.AutonomyTier promoteSubjectAutonomy(WDEorg org, String nationName) {
        if (!canPromoteSubjectAutonomy(org, nationName)) {
            return null;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) {
            return null;
        }

        SovereignSubjectState state = states.get(key);
        SovereignSubjectState.AutonomyTier promotedTier = state.getAutonomyTier().promote();
        if (promotedTier == state.getAutonomyTier()) {
            return null;
        }

        state.setAutonomyTier(promotedTier);
        state.setSuppression(getAutonomyPromotionResetSuppression());
        writeSovereignSubjectStates(org, states);
        saveOrganizations();
        return promotedTier;
    }

    public SovereignSubjectState.AutonomyTier demoteSubjectAutonomy(WDEorg org, String nationName) {
        if (!canDemoteSubjectAutonomy(org, nationName)) {
            return null;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) {
            return null;
        }

        SovereignSubjectState state = states.get(key);
        SovereignSubjectState.AutonomyTier demotedTier = state.getAutonomyTier().demote();
        if (demotedTier == state.getAutonomyTier()) {
            return null;
        }

        state.setAutonomyTier(demotedTier);
        state.setSuppression(getAutonomyDemotionResetSuppression());
        writeSovereignSubjectStates(org, states);
        saveOrganizations();
        enforceSubjectTownTaxCaps(org, nationName);
        return demotedTier;
    }

    public boolean setSubjectGarrisonState(WDEorg org, String nationName, boolean enabled) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return false;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) {
            return false;
        }

        SovereignSubjectState state = states.get(key);
        if (state.isGarrisonState() == enabled) {
            return false;
        }

        state.setGarrisonState(enabled);
        writeSovereignSubjectStates(org, states);
        saveOrganizations();
        return true;
    }

    public boolean isSubjectWarParticipationRequired(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return false;
        }

        SovereignSubjectState state = getSubjectState(org, nationName);
        return state.isGarrisonState() || state.isWarParticipationRequired();
    }

    public boolean setSubjectWarParticipationRequired(WDEorg org, String nationName, boolean required) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return false;
        }
        if (required && isNationNeutral(nationName) && getActiveIndependenceWars(org).isEmpty()) return false;

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) {
            return false;
        }

        SovereignSubjectState state = states.get(key);
        if (state.isWarParticipationRequired() == required) {
            return false;
        }

        state.setWarParticipationRequired(required);
        writeSovereignSubjectStates(org, states);
        saveOrganizations();
        return true;
    }

    public double getChairmanCapability(WDEorg org, NationManager nationManager) {
        if (org == null || !org.isSovereignEmpire()) {
            return 0.0D;
        }
        return getNationWeight(org.getChairmanNationName(), nationManager);
    }

    public double getSubjectCapabilityTotal(WDEorg org, NationManager nationManager, boolean garrisonGroup) {
        if (org == null || !org.isSovereignEmpire()) {
            return 0.0D;
        }

        double total = 0.0D;
        for (String memberNation : org.getMemberNationNames()) {
            if (!isSovereignSubjectNation(org, memberNation)) {
                continue;
            }
            if (isGarrisonSubject(org, memberNation) != garrisonGroup) {
                continue;
            }
            total += getNationWeight(memberNation, nationManager);
        }
        return total;
    }

    public EconomyBridge.Charge getSovereignAutonomyUpgradeCharge() {
        FileConfiguration config = getWarConfiguration();
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;

        String amountText = config.getString("sovereignEmpire.subjectControl.autonomyUpgradeCost.amount", "10000.00");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException ex) {
            amount = new BigDecimal("10000.00");
        }

        String currency = config.getString("sovereignEmpire.subjectControl.autonomyUpgradeCost.currency", "default");
        if (currency == null || currency.isBlank() || "default".equals(currency)) {
            currency = engine.getDefaultEconomyCurrency();
        }
        return new EconomyBridge.Charge(amount.max(BigDecimal.ZERO), currency);
    }

    public double getSubjectTownTaxCap(WDEorg org, String nationName, String normalizedTaxType) {
        if (!isSovereignSubjectNation(org, nationName) || normalizedTaxType == null || normalizedTaxType.isBlank()) {
            return Double.MAX_VALUE;
        }

        SovereignSubjectState.AutonomyTier tier = getSubjectAutonomyTier(org, nationName);
        FileConfiguration config = getOrganizationConfiguration();
        String typeKey = "WEALTH_PERCENTAGE".equalsIgnoreCase(normalizedTaxType) ? "wealthPercentage" : "fixed";
        String path = "sovereignEmpire.subjectControl.taxCaps." + tier.name().toLowerCase(java.util.Locale.ROOT) + "." + typeKey;
        return Math.max(0.0D, config.getDouble(path, Double.MAX_VALUE));
    }

    public EconomyBridge.Charge getForcedTributeCharge(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return new EconomyBridge.Charge(BigDecimal.ZERO, ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getDefaultEconomyCurrency());
        }

        SovereignSubjectState.AutonomyTier tier = getSubjectAutonomyTier(org, nationName);
        FileConfiguration config = getOrganizationConfiguration();
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        String basePath = "sovereignEmpire.forcedTribute.byTier." + tier.name().toLowerCase(java.util.Locale.ROOT);

        String amountText = config.getString(basePath + ".amount", "0.00");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException ex) {
            amount = BigDecimal.ZERO;
        }

        String currency = config.getString(basePath + ".currency", "default");
        if (currency == null || currency.isBlank() || "default".equals(currency)) {
            currency = engine.getDefaultEconomyCurrency();
        }
        return new EconomyBridge.Charge(amount.max(BigDecimal.ZERO), currency);
    }

    public int reduceSubjectSuppression(WDEorg org, String nationName, int amount) {
        if (!isSovereignSubjectNation(org, nationName) || amount <= 0) {
            return -1;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) {
            return -1;
        }

        SovereignSubjectState state = states.get(key);
        int updatedSuppression = clampSuppression(state.getSuppression() - amount);
        state.setSuppression(updatedSuppression);
        writeSovereignSubjectStates(org, states);
        saveOrganizations();
        return updatedSuppression;
    }

    public int enforceSubjectTownTaxCaps(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return 0;
        }

        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        TownManager townManager = engine.getTownManager();
        if (townManager == null) {
            return 0;
        }

        int adjustedTowns = 0;
        for (WDEtown town : townManager.getAllTowns()) {
            if (town == null || town.getNationName() == null || !town.getNationName().equalsIgnoreCase(nationName)) {
                continue;
            }
            if (!town.isResidentTaxManagedByNation()) {
                continue;
            }

            double maxTax = getSubjectTownTaxCap(org, nationName, town.getResidentTaxType());
            if (maxTax == Double.MAX_VALUE || town.getResidentTaxAmount() <= maxTax) {
                continue;
            }

            if (townManager.configureResidentTax(town, town.getResidentTaxType(), maxTax, true)) {
                adjustedTowns++;
            }
        }
        return adjustedTowns;
    }

    public boolean isTradeAlliance(WDEorg org) {
        return org != null && "TRADE_ALLIANCE".equalsIgnoreCase(org.getOrganizationType());
    }

    public boolean isMilitaryAlliance(WDEorg org) {
        return org != null && "MILITARY_ALLIANCE".equalsIgnoreCase(org.getOrganizationType());
    }

    public boolean isIdeologicalAlliance(WDEorg org) {
        return org != null && "IDEOLOGICAL_ALLIANCE".equalsIgnoreCase(org.getOrganizationType());
    }

    public String getIdeologicalAllianceIdeology(WDEorg org) {
        if (!isIdeologicalAlliance(org)) {
            return "None";
        }
        return normalizeIdeologyName(org.getSetting("allianceIdeology") == null
                ? null
                : String.valueOf(org.getSetting("allianceIdeology")));
    }

    public String getNationIdeology(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return "None";
        }
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        NationManager nationManager = engine.getNationManager();
        WDEnation nation = nationManager == null ? null : nationManager.getNation(nationName);
        return normalizeIdeologyName(nation == null ? null : nation.getReligionName());
    }

    public boolean hasIdeologicalAllianceIdeology(WDEorg org) {
        return !"None".equalsIgnoreCase(getIdeologicalAllianceIdeology(org));
    }

    public boolean isNationIdeologyMissing(String nationName) {
        return "None".equalsIgnoreCase(getNationIdeology(nationName));
    }

    public boolean isIdeologicalMembershipBlocked(WDEorg org, String nationName) {
        if (!isIdeologicalAlliance(org)) {
            return false;
        }
        String allianceIdeology = getIdeologicalAllianceIdeology(org);
        String nationIdeology = getNationIdeology(nationName);
        return "None".equalsIgnoreCase(allianceIdeology)
                || "None".equalsIgnoreCase(nationIdeology)
                || !allianceIdeology.equalsIgnoreCase(nationIdeology);
    }

    public int getLargestIdeologicalAlliancePopulationForNation(String nationName, NationManager nationManager) {
        if (nationName == null || nationName.isBlank()) {
            return 0;
        }

        int largestPopulation = 0;
        for (WDEorg org : organizations.values()) {
            if (!isIdeologicalAlliance(org) || !org.hasMemberNation(nationName)) {
                continue;
            }

            int alliancePopulation = 0;
            for (String memberNation : org.getMemberNationNames()) {
                alliancePopulation += Math.max(0, nationManager.getNationPopulation(memberNation));
            }
            largestPopulation = Math.max(largestPopulation, alliancePopulation);
        }
        return largestPopulation;
    }

    public double getIdeologicalAllianceConversionDiscountRate(String nationName, NationManager nationManager) {
        int alliancePopulation = getLargestIdeologicalAlliancePopulationForNation(nationName, nationManager);
        if (alliancePopulation <= 0) {
            return 0.0D;
        }

        FileConfiguration config = getOrganizationConfiguration();
        double perResident = Math.max(0.0D, config.getDouble("ideologicalAlliance.conversionCostDiscountPerResident", 0.001D));
        double maxDiscount = Math.max(0.0D, Math.min(0.95D, config.getDouble("ideologicalAlliance.maxConversionCostDiscount", 0.50D)));
        return Math.min(maxDiscount, alliancePopulation * perResident);
    }

    public Set<String> getTradeSanctionTargets(WDEorg org) {
        Set<String> sanctioned = new LinkedHashSet<>();
        if (org == null) {
            return sanctioned;
        }

        Object rawTargets = org.getSetting("tradeSanctions");
        if (!(rawTargets instanceof Collection<?> collection)) {
            return sanctioned;
        }

        for (Object entry : collection) {
            if (entry != null) {
                sanctioned.add(String.valueOf(entry));
            }
        }
        return sanctioned;
    }

    public void setTradeSanction(WDEorg org, String targetNationName, boolean enabled) {
        if (org == null || targetNationName == null || targetNationName.isBlank()) {
            return;
        }

        Set<String> sanctioned = getTradeSanctionTargets(org);
        if (enabled) {
            sanctioned.add(targetNationName);
        } else {
            sanctioned.removeIf(existing -> existing.equalsIgnoreCase(targetNationName));
        }

        if (sanctioned.isEmpty()) {
            org.getSettings().remove("tradeSanctions");
        } else {
            org.setSetting("tradeSanctions", new ArrayList<>(sanctioned));
        }
        saveOrganizations();
    }

    public boolean isNationTradeSanctionedBy(String targetNationName, String ownerNationName) {
        if (targetNationName == null || ownerNationName == null
                || targetNationName.isBlank() || ownerNationName.isBlank()
                || targetNationName.equalsIgnoreCase(ownerNationName)) {
            return false;
        }

        for (WDEorg org : organizations.values()) {
            if (!isTradeAlliance(org) || !org.hasMemberNation(ownerNationName)) {
                continue;
            }

            if (getTradeSanctionTargets(org).stream().anyMatch(entry -> entry.equalsIgnoreCase(targetNationName))) {
                return true;
            }
        }
        return false;
    }

    public MilitaryWarVoteResult startMilitaryWarVote(WDEorg org, String proposerNationName, String targetNationName, NationManager nationManager) {
        return startMilitaryWarVote(org, proposerNationName, targetNationName, nationManager, WarReason.CONQUEST);
    }

    public MilitaryWarVoteResult startMilitaryWarVote(WDEorg org, String proposerNationName, String targetNationName,
                                                       NationManager nationManager, WarReason reason) {
        if (org == null || proposerNationName == null || targetNationName == null || !isMilitaryAlliance(org)
                || !org.hasMemberNation(proposerNationName) || org.hasMemberNation(targetNationName)) {
            return MilitaryWarVoteResult.invalid();
        }
        if (!validWarReason(reason, targetNationName)) return MilitaryWarVoteResult.invalid();
        if (isNationNeutral(proposerNationName) || isNationNeutral(targetNationName)) {
            return MilitaryWarVoteResult.peaceProtected();
        }
        if (hasActiveWarBetween(proposerNationName, targetNationName)) {
            return MilitaryWarVoteResult.activeWar();
        }
        if (nationManager != null && nationManager.hasActiveWarTruce(proposerNationName, targetNationName)) {
            return MilitaryWarVoteResult.truce();
        }

        Map<String, MilitaryWarProposalState> proposals = readMilitaryWarProposals(org);
        if (findMilitaryWarProposalKey(proposals, targetNationName) != null) {
            return MilitaryWarVoteResult.alreadyPending();
        }

        MilitaryWarProposalState proposal = new MilitaryWarProposalState(targetNationName, proposerNationName);
        proposal.reason = reason;
        proposal.getVotes().put(proposerNationName, true);
        proposals.put(targetNationName, proposal);
        writeMilitaryWarProposals(org, proposals);
        saveOrganizations();
        return resolveMilitaryWarVote(org, proposal, proposals, nationManager);
    }

    public MilitaryWarVoteResult castMilitaryWarVote(WDEorg org, String targetNationName, String voterNationName, boolean support, NationManager nationManager) {
        if (org == null || targetNationName == null || voterNationName == null || !isMilitaryAlliance(org)
                || !org.hasMemberNation(voterNationName)) {
            return MilitaryWarVoteResult.invalid();
        }
        if (isNationNeutral(voterNationName)) return MilitaryWarVoteResult.peaceProtected();

        Map<String, MilitaryWarProposalState> proposals = readMilitaryWarProposals(org);
        String key = findMilitaryWarProposalKey(proposals, targetNationName);
        if (key == null) {
            return MilitaryWarVoteResult.notFound();
        }

        MilitaryWarProposalState proposal = proposals.get(key);
        proposal.getVotes().put(voterNationName, support);
        writeMilitaryWarProposals(org, proposals);
        saveOrganizations();
        return resolveMilitaryWarVote(org, proposal, proposals, nationManager);
    }

    public List<MilitaryWarProposalState> getActiveMilitaryWarProposals(WDEorg org) {
        return new ArrayList<>(readMilitaryWarProposals(org).values());
    }

    public List<MilitaryWarState> getActiveMilitaryWars(WDEorg org) {
        return new ArrayList<>(readActiveMilitaryWars(org).values());
    }

    public List<MilitaryWarState> getWarMenuStates(String nation, String town) {
        List<MilitaryWarState> states = getActiveMilitaryWars(null).stream()
                .filter(w -> w.isAttackerNation(nation) || w.isDefenderNation(nation)
                        || w.isAttackerNation("town:" + town) || w.isDefenderNation("town:" + town))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (WDEorg empire : getAllOrganizations()) {
            for (IndependenceWarState independent : getActiveIndependenceWars(empire)) {
                if (!independent.rebelNationName.equalsIgnoreCase(nation) && !empire.hasMemberNation(nation)) continue;
                Set<String> attackers = new LinkedHashSet<>(empire.getMemberNationNames());
                attackers.remove(independent.rebelNationName);
                MilitaryWarState view = new MilitaryWarState(empire.getChairmanNationName(), independent.rebelNationName,
                        attackers, Set.of(independent.rebelNationName), independent.empireScore, independent.rebelScore, independent.startedAt);
                view.reason = WarReason.INDEPENDENCE;
                states.add(view);
            }
        }
        return states;
    }

    /** 提供玩家可切换专注的战争列表，统一常规战争与独立战争展示。 */
    public record WarFocusOption(String focusKey, String title, WarReason reason,
                                 boolean townWar, boolean independenceWar) {}

    /** 为计分板和战争详情菜单提供统一的战争详细快照。 */
    public record WarFocusDetails(String focusKey, String title, WarReason reason,
                                  boolean townWar, boolean independenceWar, boolean defenderPerspective,
                                  double friendlyScore, double enemyScore,
                                  Map<String, Double> friendlyOccupation, Map<String, Double> enemyOccupation,
                                  Map<String, Double> friendlyKills, Map<String, Double> enemyKills,
                                  List<String> friendlyParticipants, List<String> enemyParticipants,
                                  long startedAt, int elapsedCycles) {}

    public List<WarFocusOption> getWarFocusOptions(String nation, String town) {
        List<WarFocusOption> options = new ArrayList<>();
        String townKey = town == null || town.isBlank() ? "" : "town:" + town;
        for (MilitaryWarState war : getActiveMilitaryWars(null)) {
            if (!war.isAttackerNation(nation) && !war.isDefenderNation(nation)
                    && !war.isAttackerNation(townKey) && !war.isDefenderNation(townKey)) {
                continue;
            }
            options.add(new WarFocusOption(
                    war.getWarKey(),
                    war.getAttackerNationName() + " vs " + war.getDefenderNationName(),
                    war.getReason(),
                    war.getAttackerNationName().startsWith("town:") || war.getDefenderNationName().startsWith("town:"),
                    false));
        }
        for (WDEorg empire : getAllOrganizations()) {
            for (IndependenceWarState war : getActiveIndependenceWars(empire)) {
                if (!war.getRebelNationName().equalsIgnoreCase(nation) && !empire.hasMemberNation(nation)) {
                    continue;
                }
                options.add(new WarFocusOption(
                        buildIndependenceFocusKey(empire.getOrganizationName(), war.getRebelNationName()),
                        empire.getChairmanNationName() + " vs " + war.getRebelNationName(),
                        WarReason.INDEPENDENCE,
                        false,
                        true));
            }
        }
        return options;
    }

    public WarFocusDetails getWarFocusDetails(String focusKey, String nation, String town) {
        if (focusKey == null || focusKey.isBlank()) {
            return null;
        }
        String townKey = town == null || town.isBlank() ? "" : "town:" + town;
        for (MilitaryWarState war : getActiveMilitaryWars(null)) {
            if (!war.getWarKey().equalsIgnoreCase(focusKey)) {
                continue;
            }
            String actor = war.isAttackerNation(nation) || war.isDefenderNation(nation) ? nation : townKey;
            if (actor == null || actor.isBlank()) {
                return null;
            }
            boolean attackerSide = war.isAttackerNation(actor);
            boolean defenderSide = war.isDefenderNation(actor);
            if (!attackerSide && !defenderSide) {
                return null;
            }
            return new WarFocusDetails(
                    war.getWarKey(),
                    war.getAttackerNationName() + " vs " + war.getDefenderNationName(),
                    war.getReason(),
                    war.getAttackerNationName().startsWith("town:") || war.getDefenderNationName().startsWith("town:"),
                    false,
                    defenderSide,
                    attackerSide ? war.getAttackerScore() : war.getDefenderScore(),
                    attackerSide ? war.getDefenderScore() : war.getAttackerScore(),
                    attackerSide ? war.getAttackerOccupation() : war.getDefenderOccupation(),
                    attackerSide ? war.getDefenderOccupation() : war.getAttackerOccupation(),
                    attackerSide ? war.getAttackerKills() : war.getDefenderKills(),
                    attackerSide ? war.getDefenderKills() : war.getAttackerKills(),
                    attackerSide ? orderedNames(war.getAttackerNationNames()) : orderedNames(war.getDefenderNationNames()),
                    attackerSide ? orderedNames(war.getDefenderNationNames()) : orderedNames(war.getAttackerNationNames()),
                    war.getStartedAt(),
                    0);
        }

        for (WDEorg empire : getAllOrganizations()) {
            for (IndependenceWarState war : getActiveIndependenceWars(empire)) {
                if (!buildIndependenceFocusKey(empire.getOrganizationName(), war.getRebelNationName()).equalsIgnoreCase(focusKey)) {
                    continue;
                }
                boolean rebelSide = war.getRebelNationName().equalsIgnoreCase(nation);
                boolean empireSide = !rebelSide && empire.hasMemberNation(nation);
                if (!rebelSide && !empireSide) {
                    return null;
                }
                List<String> empireParticipants = new ArrayList<>(empire.getMemberNationNames());
                empireParticipants.removeIf(member -> member.equalsIgnoreCase(war.getRebelNationName()));
                return new WarFocusDetails(
                        buildIndependenceFocusKey(empire.getOrganizationName(), war.getRebelNationName()),
                        empire.getChairmanNationName() + " vs " + war.getRebelNationName(),
                        WarReason.INDEPENDENCE,
                        false,
                        true,
                        rebelSide,
                        rebelSide ? war.getRebelScore() : war.getEmpireScore(),
                        rebelSide ? war.getEmpireScore() : war.getRebelScore(),
                        rebelSide ? war.getRebelOccupation() : war.getEmpireOccupation(),
                        rebelSide ? war.getEmpireOccupation() : war.getRebelOccupation(),
                        rebelSide ? war.getRebelKills() : war.getEmpireKills(),
                        rebelSide ? war.getEmpireKills() : war.getRebelKills(),
                        rebelSide ? List.of(war.getRebelNationName()) : orderedNames(empireParticipants),
                        rebelSide ? orderedNames(empireParticipants) : List.of(war.getRebelNationName()),
                        war.getStartedAt(),
                        war.getElapsedCycles());
            }
        }
        return null;
    }

    public boolean hasActiveWarBetween(String firstNationName, String secondNationName) {
        if (firstNationName == null || secondNationName == null
                || firstNationName.isBlank() || secondNationName.isBlank()) {
            return false;
        }

        for (MilitaryWarState warState : readActiveMilitaryWars(null).values()) {
                if (!isNationNeutral(firstNationName) && !isNationNeutral(secondNationName)
                        && warState.containsOpposingNations(firstNationName, secondNationName)) {
                    return true;
                }
        }
        for (WDEorg org : getAllOrganizations()) {
            for (IndependenceWarState warState : getActiveIndependenceWars(org)) {
                if (warState.getRebelNationName().equalsIgnoreCase(firstNationName) && org.hasMemberNation(secondNationName)) {
                    return true;
                }
                if (warState.getRebelNationName().equalsIgnoreCase(secondNationName) && org.hasMemberNation(firstNationName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public PeaceTreatyProposalResult proposePeaceTreaty(WDEorg org,
                                                        String actorNationName,
                                                        String targetNationName,
                                                        PeaceTreatyProposalState.ProposalType proposalType,
                                                        List<PeaceTreatyClauseState> clauses,
                                                        NationManager nationManager) {
        if (actorNationName == null || targetNationName == null || proposalType == null || clauses == null || clauses.isEmpty() || nationManager == null) {
            return PeaceTreatyProposalResult.invalid("org_peace_treaty_invalid");
        }

        WarContext context = resolveWarContext(org, actorNationName, targetNationName);
        if (context == null) {
            return PeaceTreatyProposalResult.invalid("org_peace_treaty_no_active_war");
        }

        String proposalAuthorityError = validatePeaceTreatyProposalAuthority(context, actorNationName, targetNationName);
        if (proposalAuthorityError != null) {
            return PeaceTreatyProposalResult.invalid(proposalAuthorityError);
        }

        boolean separatePeace = isSeparatePeaceProposal(context, actorNationName, targetNationName);
        int totalPoints = calculatePeaceTreatyPoints(context, actorNationName, clauses, separatePeace);
        int maxPoints = getPeaceTreatyMaxProposalPoints();
        if (totalPoints <= 0 || totalPoints > maxPoints) {
            return PeaceTreatyProposalResult.invalid("org_peace_treaty_points_exceeded");
        }

        double scoreDifference = getTreatyScoreDifference(context, actorNationName, targetNationName, proposalType);

        String validationError = validatePeaceTreatyClauses(context, actorNationName, targetNationName, proposalType, clauses, nationManager, false);
        if (validationError != null) {
            return PeaceTreatyProposalResult.invalid(validationError);
        }

        PeaceTreatyProposalState proposal = new PeaceTreatyProposalState(
                context.getWarKey(),
                proposalType,
                actorNationName,
                targetNationName,
                totalPoints,
                clauses,
                System.currentTimeMillis());

        if (shouldForceAcceptIndependenceTreaty(context, proposal, scoreDifference)) {
            executePeaceTreaty(context, proposal, nationManager);
            return PeaceTreatyProposalResult.forcedAccepted(proposal, scoreDifference);
        }

        Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
        proposals.put(context.getWarKey(), proposal);
        writePeaceTreatyProposals(org, proposals);
        saveOrganizations();
        return PeaceTreatyProposalResult.proposed(proposal, scoreDifference);
    }

    /**
     * 封装和约菜单可选择的战争对象及其上下文，避免界面自行推断参战关系。
     */
    public record PeaceTreatyMenuTarget(String organization, String targetNation,
                                       List<String> attackers, List<String> defenders) {}

    /**
     * 返回和约预览分差、成本及校验错误，不直接执行条款。
     */
    public record PeaceTreatyMenuPreview(double scoreDifference, int points, String error) {}

    /**
     * 封装待受理和约及所属上下文，供收件菜单展示。
     */
    public record PeaceTreatyMenuIncoming(String organization, PeaceTreatyProposalState proposal) {}

    public List<PeaceTreatyMenuIncoming> getIncomingPeaceTreaties(String actorNation) {
        List<PeaceTreatyMenuIncoming> incoming = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (WDEorg org : warContexts()) {
            for (PeaceTreatyProposalState proposal : readPeaceTreatyProposals(org).values()) {
                if (proposal.getTargetNationName().equalsIgnoreCase(actorNation)
                        && resolveWarContext(org, actorNation, proposal.getProposerNationName()) != null
                        && seen.add(proposal.getWarKey() + ":" + proposal.getProposerNationName())) {
                    incoming.add(new PeaceTreatyMenuIncoming(warScope(org), proposal));
                }
            }
        }
        return incoming;
    }

    public List<PeaceTreatyMenuTarget> getPeaceTreatyMenuTargets(String actorNation, NationManager nations) {
        List<PeaceTreatyMenuTarget> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (WDEorg org : warContexts()) {
            for (WDEnation nation : nations.getAllNations()) {
                String target = nation.getNationName();
                WarContext context = resolveWarContext(org, actorNation, target);
                if (context == null || validatePeaceTreatyProposalAuthority(context, actorNation, target) != null
                        || !seen.add(context.getWarKey() + ":" + target)) continue;
                List<String> attackers;
                List<String> defenders;
                if (context.getMilitaryWarState() != null) {
                    attackers = new ArrayList<>(context.getMilitaryWarState().getAttackerNationNames());
                    defenders = new ArrayList<>(context.getMilitaryWarState().getDefenderNationNames());
                } else {
                    defenders = List.of(context.getIndependenceWarState().getRebelNationName());
                    attackers = new ArrayList<>(org.getMemberNationNames());
                    attackers.removeAll(defenders);
                }
                targets.add(new PeaceTreatyMenuTarget(warScope(org), target,
                        List.copyOf(attackers), List.copyOf(defenders)));
            }
        }
        return targets;
    }

    public PeaceTreatyMenuPreview previewPeaceTreaty(WDEorg org, String actorNation, String targetNation,
                                                    PeaceTreatyProposalState.ProposalType type,
                                                    List<PeaceTreatyClauseState> clauses, NationManager nations) {
        WarContext context = resolveWarContext(org, actorNation, targetNation);
        if (context == null) return new PeaceTreatyMenuPreview(0, 0, "org_peace_treaty_no_active_war");
        double difference = getTreatyScoreDifference(context, actorNation, targetNation, type);
        int points = calculatePeaceTreatyPoints(context, actorNation, clauses,
                isSeparatePeaceProposal(context, actorNation, targetNation));
        String error = validatePeaceTreatyProposalAuthority(context, actorNation, targetNation);
        if (error == null && clauses.isEmpty()) error = "org_peace_treaty_invalid_clause";
        if (error == null && (points <= 0 || points > getPeaceTreatyMaxProposalPoints())) {
            error = "org_peace_treaty_points_exceeded";
        }
        if (error == null) error = validatePeaceTreatyClauses(context, actorNation, targetNation, type, clauses, nations, false);
        return new PeaceTreatyMenuPreview(difference, points, error);
    }

    public PeaceTreatyReviewResult reviewPeaceTreaty(WDEorg org,
                                                     String actorNationName,
                                                     String targetNationName,
                                                     boolean accept,
                                                     NationManager nationManager) {
        if (actorNationName == null || targetNationName == null || nationManager == null) {
            return PeaceTreatyReviewResult.invalid("org_peace_treaty_invalid");
        }

        WarContext context = resolveWarContext(org, actorNationName, targetNationName);
        if (context == null) {
            return PeaceTreatyReviewResult.invalid("org_peace_treaty_no_active_war");
        }

        Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
        PeaceTreatyProposalState proposal = proposals.get(context.getWarKey());
        if (proposal == null) {
            return PeaceTreatyReviewResult.invalid("org_peace_treaty_not_found");
        }

        if (!proposal.getTargetNationName().equalsIgnoreCase(actorNationName)) {
            return PeaceTreatyReviewResult.invalid("org_peace_treaty_only_target_review");
        }

        if (!accept) {
            proposals.remove(context.getWarKey());
            writePeaceTreatyProposals(org, proposals);
            saveOrganizations();
            return PeaceTreatyReviewResult.rejected(proposal);
        }

        String validationError = validatePeaceTreatyClauses(context, proposal.getProposerNationName(), proposal.getTargetNationName(), proposal.getProposalType(), proposal.getClauses(), nationManager, true);
        if (validationError != null) {
            return PeaceTreatyReviewResult.invalid(validationError);
        }

        executePeaceTreaty(context, proposal, nationManager);
        proposals.remove(context.getWarKey());
        writePeaceTreatyProposals(org, proposals);
        saveOrganizations();
        return PeaceTreatyReviewResult.accepted(proposal);
    }

    public int getNationOrganizationCount(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return 0;
        }
        return (int) organizations.values().stream()
                .filter(org -> org.hasMemberNation(nationName))
                .count();
    }

    public boolean isNationAtOrgLimit(String nationName, int nationOrgLimit) {
        return nationOrgLimit >= 0 && getNationOrganizationCount(nationName) >= nationOrgLimit;
    }

    public boolean canNationJoinOrganization(WDEorg org, String nationName, NationManager nationManager) {
        return findMilitaryJoinConflict(org, nationName, nationManager) == null;
    }

    public boolean hasActiveIndependenceWar(WDEorg org, String rebelNationName) {
        return getActiveIndependenceWar(org, rebelNationName) != null;
    }

    public IndependenceWarState getActiveIndependenceWar(WDEorg org, String rebelNationName) {
        if (org == null || rebelNationName == null) {
            return null;
        }

        for (IndependenceWarState state : getActiveIndependenceWars(org)) {
            if (state.getRebelNationName().equalsIgnoreCase(rebelNationName)) {
                return state;
            }
        }
        return null;
    }

    public List<IndependenceWarState> getActiveIndependenceWars(WDEorg org) {
        return new ArrayList<>(readIndependenceWars(org).values());
    }

    public boolean startIndependenceWar(WDEorg org, String rebelNationName) {
        if (org == null || rebelNationName == null || !org.isSovereignEmpire() || !org.hasMemberNation(rebelNationName)) {
            return false;
        }

        Map<String, IndependenceWarState> wars = readIndependenceWars(org);
        if (findIndependenceWarKey(wars, rebelNationName) != null) {
            return false;
        }

        wars.put(rebelNationName, new IndependenceWarState(rebelNationName));
        writeIndependenceWars(org, wars);
        saveOrganizations();
        broadcastServerMessage("war_broadcast_independence_started", rebelNationName, org.getOrganizationName());
        return true;
    }

    public void prepareIndependenceWarHostilities(WDEorg org, String rebelNationName, NationManager nationManager) {
        // Independence wars are now tracked as independent war states rather than persisted diplomatic attitudes.
    }

    public IndependenceWarResolutionResult surrenderIndependenceWar(WDEorg org, String rebelNationName) {
        if (org == null || rebelNationName == null) {
            return null;
        }

        Map<String, IndependenceWarState> wars = readIndependenceWars(org);
        String key = findIndependenceWarKey(wars, rebelNationName);
        if (key == null) {
            return null;
        }

        IndependenceWarState state = wars.get(key);
        state.setChairmanSurrendered(true);
        IndependenceWarResolutionResult result = resolveIndependenceWar(org, wars, state, IndependenceWarOutcome.SUCCESS);
        saveOrganizations();
        announceIndependenceWarResolution(org, result);
        return result;
    }

    public void recordIndependenceWarKill(String killerNationName, String victimNationName) {
        if (killerNationName == null || victimNationName == null || killerNationName.equalsIgnoreCase(victimNationName)) {
            return;
        }

        boolean changed = false;
        for (WDEorg org : getAllOrganizations()) {
            if (!org.isSovereignEmpire()) {
                continue;
            }

            Map<String, IndependenceWarState> wars = readIndependenceWars(org);
            if (wars.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, IndependenceWarState> entry : wars.entrySet()) {
                IndependenceWarState state = entry.getValue();
                if (state.getRebelNationName().equalsIgnoreCase(killerNationName) && org.hasMemberNation(victimNationName)) {
                    if (!victimNationName.equalsIgnoreCase(state.getRebelNationName())) {
                        state.addRebelScore(getRebelKillPoints());
                        state.rebelKills.merge(victimNationName, getRebelKillPoints(), Double::sum);
                        changed = true;
                    }
                } else if (state.getRebelNationName().equalsIgnoreCase(victimNationName) && org.hasMemberNation(killerNationName)) {
                    if (!killerNationName.equalsIgnoreCase(state.getRebelNationName())) {
                        state.addEmpireScore(getEmpireKillPoints());
                        state.empireKills.merge(victimNationName, getEmpireKillPoints(), Double::sum);
                        changed = true;
                    }
                }
            }

            if (changed) {
                writeIndependenceWars(org, wars);
            }
        }

        if (changed) {
            saveOrganizations();
        }
    }

    public void recordWarKill(String killerNationName, String victimNationName) {
        recordMilitaryWarKill(killerNationName, victimNationName);
        recordIndependenceWarKill(killerNationName, victimNationName);
    }

    public void recordMilitaryWarKill(String killerNationName, String victimNationName) {
        if (killerNationName == null || victimNationName == null || killerNationName.equalsIgnoreCase(victimNationName)) {
            return;
        }
        if (isNationNeutral(killerNationName) || isNationNeutral(victimNationName)) return;

        boolean changed = false;
        double killPoints = getMilitaryWarKillPoints();
        if (killPoints <= 0D) {
            return;
        }

        Map<String, MilitaryWarState> wars = readActiveMilitaryWars(null);
        for (Map.Entry<String, MilitaryWarState> entry : wars.entrySet()) {
            MilitaryWarState state = entry.getValue();
            if (state.addKillScore(killerNationName, victimNationName, killPoints)) {
                wars.put(entry.getKey(), state);
                changed = true;
            }
        }
        if (changed) {
            writeActiveMilitaryWars(null, wars);
            saveOrganizations();
        }
    }

    public void processRegularWarCycles() {
        processRegularWarCycles(buildOccupationCensus());
    }

    private void processRegularWarCycles(Map<String, Map<String, int[]>> census) {
        Map<String, MilitaryWarState> wars = readActiveMilitaryWars(null);
        if (wars.isEmpty()) return;
        for (MilitaryWarState war : wars.values()) {
            scoreRegularOccupation(war, census);
        }
        writeActiveMilitaryWars(null, wars);
        saveOrganizations();
    }

    public List<IndependenceWarResolutionResult> processIndependenceWarCycles() {
        return processIndependenceWarCycles(buildOccupationCensus());
    }

    private List<IndependenceWarResolutionResult> processIndependenceWarCycles(Map<String, Map<String, int[]>> census) {
        if (!isIndependenceWarEnabled()) {
            return List.of();
        }

        List<IndependenceWarResolutionResult> results = new ArrayList<>();
        boolean changed = false;
        for (WDEorg org : getAllOrganizations()) {
            if (!org.isSovereignEmpire()) {
                continue;
            }

            Map<String, IndependenceWarState> wars = readIndependenceWars(org);
            if (wars.isEmpty()) {
                continue;
            }

            List<IndependenceWarState> states = new ArrayList<>(wars.values());
            for (IndependenceWarState state : states) {
                state.incrementElapsedCycles();
                applyOccupationCyclePoints(org, state, census);
                changed = true;

                if (state.getElapsedCycles() >= getIndependenceWarMaxCycles()) {
                    IndependenceWarOutcome outcome = determineTimedWarOutcome(state);
                    IndependenceWarResolutionResult result = resolveIndependenceWar(org, wars, state, outcome);
                    results.add(result);
                }
            }

            if (!wars.isEmpty()) {
                writeIndependenceWars(org, wars);
            } else {
                clearIndependenceWars(org);
            }
        }

        if (changed || !results.isEmpty()) {
            saveOrganizations();
        }

        for (IndependenceWarResolutionResult result : results) {
            announceIndependenceWarResolution(result.getOrganization(), result);
        }

        return results;
    }

    public String findMilitaryJoinConflict(WDEorg org, String nationName, NationManager nationManager) {
        if (org == null || nationName == null || !isMilitaryAlliance(org)) {
            return null;
        }

        WDEnation joiningNation = nationManager.getNation(nationName);
        if (joiningNation == null) {
            return nationName;
        }

        for (String memberNationName : org.getMemberNationNames()) {
            if (memberNationName == null || memberNationName.equalsIgnoreCase(nationName)) {
                continue;
            }

            WDEnation memberNation = nationManager.getNation(memberNationName);
            if (memberNation == null) {
                continue;
            }

            if (isUnfriendly(joiningNation, memberNationName) || isUnfriendly(memberNation, nationName)) {
                return memberNationName;
            }
        }
        return null;
    }

    public MilitaryWarResult handleMilitaryAllianceWar(String attackerNationName, String defenderNationName, NationManager nationManager) {
        return handleMilitaryAllianceWar(attackerNationName, defenderNationName, nationManager, WarReason.CONQUEST);
    }

    public MilitaryWarResult handleMilitaryAllianceWar(String attackerNationName, String defenderNationName,
                                                       NationManager nationManager, WarReason reason) {
        MilitaryWarResult result = new MilitaryWarResult();
        if (attackerNationName == null || defenderNationName == null || nationManager == null
                || attackerNationName.equalsIgnoreCase(defenderNationName)) {
            return result;
        }
        if (isNationNeutral(attackerNationName) || isNationNeutral(defenderNationName)) return result;

        List<WDEorg> attackerAlliances = getMilitaryAlliancesByNation(attackerNationName);
        List<WDEorg> defenderAlliances = getMilitaryAlliancesByNation(defenderNationName);
        if (attackerAlliances.stream().anyMatch(a -> a.hasMemberNation(defenderNationName))) return result;
        if (hasActiveWarBetween(attackerNationName, defenderNationName)) return result;
        if (!validWarReason(reason, defenderNationName)) return result;
        if (nationManager.hasActiveWarTruce(attackerNationName, defenderNationName)) return result;
        for (String a : collectWarSide(attackerNationName, attackerAlliances)) {
            for (String d : collectWarSide(defenderNationName, defenderAlliances)) {
                if (!a.equalsIgnoreCase(d) && nationManager.hasActiveWarTruce(a, d)) return result;
            }
        }

        result.attackerAllianceInvolved = !attackerAlliances.isEmpty();
        result.defenderAllianceInvolved = !defenderAlliances.isEmpty();

        if (!attackerAlliances.isEmpty() && !defenderAlliances.isEmpty()) {
            for (WDEorg attackerAlliance : attackerAlliances) {
                for (WDEorg defenderAlliance : defenderAlliances) {
                    for (String overlappingNation : findOverlappingMemberNations(attackerAlliance, defenderAlliance)) {
                        removeMemberNationAndAdjust(attackerAlliance, overlappingNation);
                        result.removedFromAttackerAlliances.add(overlappingNation + " -> " + attackerAlliance.getOrganizationName());
                    }
                }
            }
            attackerAlliances = getMilitaryAlliancesByNation(attackerNationName);
        }

        Set<String> attackerSide = collectWarSide(attackerNationName, attackerAlliances);
        Set<String> defenderSide = collectWarSide(defenderNationName, defenderAlliances);
        defenderSide.removeAll(attackerSide);
        if (defenderSide.isEmpty() || !defenderSide.contains(defenderNationName)) return result;
        for (String attacker : attackerSide) for (String defender : defenderSide) {
            if (nationManager.hasActiveWarTruce(attacker, defender)) return result;
        }

        result.hostilePairsApplied = attackerSide.size() * defenderSide.size();
        MilitaryWarState warState = new MilitaryWarState(
                attackerNationName,
                defenderNationName,
                attackerSide,
                defenderSide,
                System.currentTimeMillis());
        warState.reason = reason;
        applyHostileRelationsBetweenSides(attackerSide, defenderSide, nationManager);
        recordMilitaryWarState(attackerAlliances, warState);
        broadcastMilitaryWarStarted(warState);

        return result;
    }

    public void enableAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveOrganizations();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 5);

        long maintenanceIntervalTicks = getOrganizationMaintenanceIntervalTicks();
        if (maintenanceIntervalTicks > 0L) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    processOrganizationMaintenance();
                }
            }.runTaskTimer(plugin, maintenanceIntervalTicks, maintenanceIntervalTicks);
        }

        long sovereignTributeTicks = getSovereignTributeIntervalTicks();
        if (sovereignTributeTicks > 0L) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    processSovereignTribute();
                }
            }.runTaskTimer(plugin, sovereignTributeTicks, sovereignTributeTicks);
        }

        long sovereignSubjectControlTicks = getSovereignSubjectControlIntervalTicks();
        if (sovereignSubjectControlTicks > 0L) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    processSovereignSubjectControl();
                }
            }.runTaskTimer(plugin, sovereignSubjectControlTicks, sovereignSubjectControlTicks);
        }

        long sovereignWarParticipationTicks = getSovereignWarParticipationCheckTicks();
        if (sovereignWarParticipationTicks > 0L) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    processSovereignWarParticipation();
                }
            }.runTaskTimer(plugin, sovereignWarParticipationTicks, sovereignWarParticipationTicks);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Map<String, Map<String, int[]>> census = buildOccupationCensus();
                processRegularWarCycles(census);
                processIndependenceWarCycles(census);
            }
        }.runTaskTimer(plugin, getIndependenceWarCycleTicks(), getIndependenceWarCycleTicks());
    }

    private Boolean findVoteIgnoreCase(Map<String, Boolean> votes, String nationName) {
        for (Map.Entry<String, Boolean> entry : votes.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(nationName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Double readBalanceValue(Object target) {
        for (String methodName : List.of("getHoldingBalance", "getBalance", "getCachedBalance")) {
            Object rawValue = invokeNoArgMethod(target, methodName);
            if (rawValue instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private Object invokeNoArgMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private void processOrganizationMaintenance() {
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        EconomyBridge.Charge maintenanceCharge = engine.getOrgMaintenanceCostCharge();
        if (maintenanceCharge == null || maintenanceCharge.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal maintenanceInDefaultCurrency = resolveMaintenanceAmountInDefaultCurrency(engine, maintenanceCharge);
        if (maintenanceInDefaultCurrency.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        boolean changed = false;
        for (WDEorg org : organizations.values()) {
            BigDecimal currentBalance = BigDecimal.valueOf(org.getBalance());
            if (currentBalance.compareTo(maintenanceInDefaultCurrency) < 0) {
                plugin.getLogger().warning("Skipped organization maintenance fee for " + org.getOrganizationName()
                        + " because the balance is insufficient. Required=" + maintenanceInDefaultCurrency.toPlainString()
                        + " " + engine.getDefaultEconomyCurrency()
                        + ", Current=" + currentBalance.toPlainString() + " " + engine.getDefaultEconomyCurrency());
                continue;
            }

            BigDecimal newBalance = currentBalance.subtract(maintenanceInDefaultCurrency);
            org.setBalance(newBalance.doubleValue());
            changed = true;
            plugin.getLogger().info("Charged organization maintenance fee from " + org.getOrganizationName()
                    + ": -" + maintenanceInDefaultCurrency.toPlainString() + " " + engine.getDefaultEconomyCurrency()
                    + ", NewBalance=" + newBalance.toPlainString() + " " + engine.getDefaultEconomyCurrency());
        }

        if (changed) {
            saveOrganizations();
        }
    }

    private void processSovereignTribute() {
        if (!isSovereignTributeEnabled()) {
            return;
        }

        EconomyBridge.Charge tributeCharge = getSovereignTributeCharge();
        if (tributeCharge == null || tributeCharge.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        BigDecimal tributeInDefaultCurrency = resolveMaintenanceAmountInDefaultCurrency(engine, tributeCharge);
        if (tributeInDefaultCurrency.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        boolean changed = false;
        for (WDEorg org : organizations.values()) {
            if (!org.isSovereignEmpire()) {
                continue;
            }

            for (String memberNation : org.getMemberNationNames()) {
                if (!isSovereignSubjectNation(org, memberNation) || isGarrisonSubject(org, memberNation)) {
                    continue;
                }

                Nation subjectNation = resolveTownyNation(memberNation);
                if (subjectNation == null) {
                    continue;
                }

                if (!withdrawNationTreasuryCharge(subjectNation, tributeCharge,
                        "WorldDynamicsEngine sovereign empire tribute -> " + org.getOrganizationName())) {
                    plugin.getLogger().warning("Skipped sovereign tribute for subject nation " + memberNation
                            + " in " + org.getOrganizationName()
                            + " because the treasury is insufficient. Required="
                            + formatCharge(tributeCharge));
                    continue;
                }

                org.setBalance(BigDecimal.valueOf(org.getBalance()).add(tributeInDefaultCurrency).doubleValue());
                changed = true;
                plugin.getLogger().info("Collected sovereign tribute from " + memberNation
                        + " to " + org.getOrganizationName()
                        + ": +" + tributeInDefaultCurrency.toPlainString() + " " + engine.getDefaultEconomyCurrency());
            }
        }

        if (changed) {
            saveOrganizations();
        }
    }

    private BigDecimal resolveMaintenanceAmountInDefaultCurrency(com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine,
                                                                 EconomyBridge.Charge maintenanceCharge) {
        if (maintenanceCharge == null) {
            return BigDecimal.ZERO;
        }

        EconomyBridge economyBridge = engine.getEconomyBridge();
        String defaultCurrency = engine.getDefaultEconomyCurrency();
        String configuredCurrency = maintenanceCharge.currency();
        if (configuredCurrency == null || configuredCurrency.isBlank() || configuredCurrency.equals(defaultCurrency)) {
            return maintenanceCharge.amount();
        }

        return economyBridge == null
                ? maintenanceCharge.amount()
                : economyBridge.convert(maintenanceCharge.amount(), configuredCurrency, defaultCurrency);
    }

    private long getOrganizationMaintenanceIntervalTicks() {
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        long intervalHours = engine.getOrganizationMaintenanceIntervalHours();
        if (intervalHours <= 0L) {
            return -1L;
        }
        return intervalHours * 20L * 60L * 60L;
    }

    private boolean isSovereignTributeEnabled() {
        return getOrganizationConfiguration().getBoolean("sovereignEmpire.tribute.enabled", true);
    }

    private long getSovereignTributeIntervalTicks() {
        long intervalHours = Math.max(0L, getOrganizationConfiguration().getLong("sovereignEmpire.tribute.intervalHours", 24L));
        return intervalHours <= 0L ? -1L : intervalHours * 20L * 60L * 60L;
    }

    public EconomyBridge.Charge getSovereignTributeCharge() {
        FileConfiguration config = getOrganizationConfiguration();
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;

        String amountText = config.getString("sovereignEmpire.tribute.amount", "0.00");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException ex) {
            amount = BigDecimal.ZERO;
        }

        String currency = config.getString("sovereignEmpire.tribute.currency", "default");
        if (currency == null || currency.isBlank() || "default".equals(currency)) {
            currency = engine.getDefaultEconomyCurrency();
        }
        return new EconomyBridge.Charge(amount.max(BigDecimal.ZERO), currency);
    }

    private void processSovereignSubjectControl() {
        NationManager nationManager = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager();
        if (nationManager == null) {
            return;
        }

        boolean changed = false;
        for (WDEorg org : organizations.values()) {
            if (!org.isSovereignEmpire()) {
                continue;
            }

            changed |= synchronizeSovereignSubjectStates(org);
            for (String memberNation : org.getMemberNationNames()) {
                if (isSovereignSubjectNation(org, memberNation)) {
                    enforceSubjectTownTaxCaps(org, memberNation);
                }
            }
            double chairmanCapability = getChairmanCapability(org, nationManager);
            changed |= processSubjectSuppressionGroup(org, nationManager, chairmanCapability, false);
            changed |= processSubjectSuppressionGroup(org, nationManager, chairmanCapability, true);
        }

        if (changed) {
            saveOrganizations();
        }
    }

    private void processSovereignWarParticipation() {
        NationManager nationManager = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager();
        if (nationManager == null) {
            return;
        }

        long penaltyDurationTicks = getSovereignWarParticipationCheckTicks();
        if (penaltyDurationTicks <= 0L) {
            return;
        }

        for (WDEorg org : organizations.values()) {
            if (!org.isSovereignEmpire() || !isSovereignEmpireInCombat(org, nationManager)) {
                continue;
            }

            Player chairmanCommander = getOnlineChairmanArmyCommander(org, nationManager);
            if (chairmanCommander == null || !chairmanCommander.isOnline()) {
                continue;
            }

            for (String memberNation : org.getMemberNationNames()) {
                if (!isSovereignSubjectNation(org, memberNation) || !isGarrisonSubject(org, memberNation)) {
                    continue;
                }
                boolean independenceCombat = !getActiveIndependenceWars(org).isEmpty();
                if (isNationNeutral(memberNation) && !independenceCombat) continue;

                if (hasSubjectPlayerNearChairmanCommander(memberNation, chairmanCommander,
                        getSovereignWarParticipationRadius(), independenceCombat)) {
                    continue;
                }

                applySubjectWarParticipationPenalty(memberNation, penaltyDurationTicks, independenceCombat);
            }
        }
    }

    private boolean processSubjectSuppressionGroup(WDEorg org, NationManager nationManager, double chairmanCapability, boolean garrisonGroup) {
        List<String> groupMembers = new ArrayList<>();
        double groupCapability = 0.0D;
        for (String memberNation : org.getMemberNationNames()) {
            if (!isSovereignSubjectNation(org, memberNation)) {
                continue;
            }
            if (isGarrisonSubject(org, memberNation) != garrisonGroup) {
                continue;
            }
            groupMembers.add(memberNation);
            groupCapability += getNationWeight(memberNation, nationManager);
        }

        if (groupMembers.isEmpty() || Double.compare(groupCapability, chairmanCapability) == 0) {
            return false;
        }

        int delta = groupCapability > chairmanCapability
                ? -getSovereignSuppressionDecrease()
                : getSovereignSuppressionIncrease();
        if (delta == 0) {
            return false;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        boolean changed = false;
        for (String nationName : groupMembers) {
            String key = findSubjectStateKey(states, nationName);
            if (key == null) {
                continue;
            }

            SovereignSubjectState state = states.get(key);
            int updatedSuppression = clampSuppression(state.getSuppression() + delta);
            if (updatedSuppression != state.getSuppression()) {
                state.setSuppression(updatedSuppression);
                changed = true;
            }
        }

        if (changed) {
            writeSovereignSubjectStates(org, states);
        }
        return changed;
    }

    private boolean isUnfriendly(WDEnation nation, String targetNationName) {
        if (nation == null || targetNationName == null) {
            return false;
        }

        for (Map.Entry<String, String> entry : nation.getDiplomaticRelations().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(targetNationName)) {
                return "unfriendly".equalsIgnoreCase(entry.getValue());
            }
        }
        return false;
    }

    public List<WDEorg> getMilitaryAlliancesByNation(String nationName) {
        return organizations.values().stream()
                .filter(this::isMilitaryAlliance)
                .filter(org -> org.hasMemberNation(nationName))
                .toList();
    }

    private Set<String> findOverlappingMemberNations(WDEorg first, WDEorg second) {
        Set<String> overlapping = new LinkedHashSet<>();
        if (first == null || second == null) {
            return overlapping;
        }

        for (String firstNation : first.getMemberNationNames()) {
            for (String secondNation : second.getMemberNationNames()) {
                if (firstNation != null && secondNation != null && firstNation.equalsIgnoreCase(secondNation)) {
                    overlapping.add(firstNation);
                }
            }
        }
        return overlapping;
    }

    private Set<String> collectWarSide(String primaryNation, List<WDEorg> alliances) {
        Set<String> nations = new LinkedHashSet<>();
        nations.add(primaryNation);
        for (WDEorg alliance : alliances) {
            nations.addAll(alliance.getMemberNationNames());
        }
        for (WDEorg empire : getAllOrganizations()) {
            if (empire.isSovereignEmpire() && nations.contains(empire.getChairmanNationName())) {
                for (String subject : empire.getMemberNationNames()) {
                    if (isSubjectWarParticipationRequired(empire, subject)) nations.add(subject);
                }
            }
        }
        nations.removeIf(this::isNationNeutral);
        return nations;
    }

    private void applyHostileRelationsBetweenSides(Set<String> attackers, Set<String> defenders, NationManager manager) {
        for (String attacker : attackers) for (String defender : defenders) {
            WDEnation a = manager.getNation(attacker), d = manager.getNation(defender);
            if (a != null) a.setDiplomaticRelation(defender, "unfriendly");
            if (d != null) d.setDiplomaticRelation(attacker, "unfriendly");
        }
        manager.saveNations();
    }

    public boolean startTownWar(Town attacker, Town defender, WarReason reason) {
        var engine = (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        if (!engine.isRegularTownWarParticipationAllowed() || attacker == null || defender == null
                || attacker == defender || attacker.isNeutral() || defender.isNeutral()
                || reason == null || reason == WarReason.INDEPENDENCE || reason == WarReason.DESTROY_THREAT) return false;
        String a = "town:" + attacker.getName(), d = "town:" + defender.getName();
        if (warLedger.townTruces.get(townTruceKey(a, d)) instanceof Map<?, ?> truce) {
            Map<String, Object> entry = new LinkedHashMap<>();
            truce.forEach((key, value) -> entry.put(String.valueOf(key), value));
            if (engine.getWarTruceClock().isActive(entry)) return false;
        }
        if (attacker.getNationOrNull() != null && attacker.getNationOrNull() == defender.getNationOrNull()) return false;
        if (getActiveMilitaryWars(null).stream().anyMatch(w -> w.containsOpposingNations(a, d))) return false;
        MilitaryWarState war = new MilitaryWarState(a, d, Set.of(a), Set.of(d), System.currentTimeMillis());
        war.reason = reason;
        recordMilitaryWarState(List.of(), war);
        broadcastMilitaryWarStarted(war);
        return true;
    }

    public void recordTownWarKill(Player killer, Player victim) {
        Town a = TownyAPI.getInstance().getTown(killer), d = TownyAPI.getInstance().getTown(victim);
        if (a != null && d != null) recordMilitaryWarKill("town:" + a.getName(), "town:" + d.getName());
    }

    public boolean isTownInActiveWar(String name) {
        return getActiveMilitaryWars(null).stream()
                .anyMatch(w -> w.isAttackerNation("town:" + name) || w.isDefenderNation("town:" + name));
    }

    private String townTruceKey(String a, String d) {
        return a.compareTo(d) <= 0 ? a + "|" + d : d + "|" + a;
    }

    public boolean surrenderTownWar(Town surrendering, Town other) {
        if (surrendering == null || other == null) return false;
        String a = "town:" + surrendering.getName(), d = "town:" + other.getName();
        List<String> keys = getActiveMilitaryWars(null).stream().filter(w -> w.containsOpposingNations(a, d))
                .map(MilitaryWarState::getWarKey).toList();
        if (keys.isEmpty()) return false;
        keys.forEach(this::clearMilitaryWarAcrossOrganizations);
        var engine = (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        warLedger.townTruces.put(townTruceKey(a, d), engine.getWarTruceClock().createDeadline(0));
        saveOrganizations();
        return true;
    }

    private boolean isIndependenceWarEnabled() {
        return getOrganizationConfiguration().getBoolean("sovereignEmpire.independenceWar.enabled", true);
    }

    private long getIndependenceWarCycleTicks() {
        long seconds = Math.max(1L, Math.min(86400L, getWarConfiguration().getLong("scoring.cycleSeconds", 300L)));
        return seconds * 20L;
    }

    private int getIndependenceWarMaxCycles() {
        return Math.max(1, getOrganizationConfiguration().getInt("sovereignEmpire.independenceWar.maxCycles", 12));
    }

    private double getRebelKillPoints() {
        return getMilitaryWarKillPoints();
    }

    private double getEmpireKillPoints() {
        return getMilitaryWarKillPoints();
    }

    private int getIndependenceWarSuccessLead() {
        return Math.max(0, getOrganizationConfiguration().getInt("sovereignEmpire.independenceWar.successLead", 100));
    }

    private long getSovereignWarParticipationCheckTicks() {
        long seconds = Math.max(0L, getOrganizationConfiguration().getLong("sovereignEmpire.warParticipation.checkIntervalSeconds", 180L));
        return seconds <= 0L ? -1L : seconds * 20L;
    }

    private double getSovereignWarParticipationRadius() {
        return Math.max(1.0D, getOrganizationConfiguration().getDouble("sovereignEmpire.warParticipation.commanderRadius", 200.0D));
    }

    private long getSovereignSubjectControlIntervalTicks() {
        long intervalHours = Math.max(0L, getOrganizationConfiguration().getLong("sovereignEmpire.subjectControl.intervalHours", 24L));
        return intervalHours <= 0L ? -1L : intervalHours * 20L * 60L * 60L;
    }

    private int getDefaultSubjectSuppression() {
        return clampSuppression(getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.defaultSuppression", 50));
    }

    private int getAutonomyPromotionResetSuppression() {
        return clampSuppression(getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.autonomyPromotionResetSuppression", 90));
    }

    private int getAutonomyDemotionResetSuppression() {
        return clampSuppression(getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.autonomyDemotionResetSuppression", 10));
    }

    private int getSovereignSuppressionDecrease() {
        return Math.max(0, getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.suppressionDecrease", 10));
    }

    private int getSovereignSuppressionIncrease() {
        return Math.max(0, getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.suppressionIncrease", 10));
    }

    public int getForcedLeaderChangeSuppressionReduction() {
        return Math.max(0, getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.forcedLeaderChangeSuppressionReduction", 50));
    }

    public int getForcedTributeSuppressionReduction() {
        return Math.max(0, getOrganizationConfiguration().getInt("sovereignEmpire.subjectControl.forcedTributeSuppressionReduction", 30));
    }

    public int getIndependenceWarRespawnLockSeconds() {
        return Math.max(0, Math.min(3600, getWarConfiguration().getInt("scoring.defenderRespawnLockSeconds", 30)));
    }

    private FileConfiguration getOrganizationConfiguration() {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getOrganizationConfig();
    }

    private FileConfiguration getWarConfiguration() {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getWarConfig();
    }

    private Nation resolveTownyNation(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return null;
        }

        try {
            return TownyUniverse.getInstance().getNation(nationName);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isNationNeutral(String nationName) {
        Nation nation = resolveTownyNation(nationName);
        return nation != null && nation.isNeutral();
    }

    public boolean isMilitaryMembershipBlocked(WDEorg org, String nationName) {
        return isMilitaryAlliance(org) && isNationNeutral(nationName);
    }

    public boolean isOrganizationMembershipBlocked(WDEorg org, String nationName) {
        return isMilitaryMembershipBlocked(org, nationName) || isIdeologicalMembershipBlocked(org, nationName);
    }

    private boolean synchronizeIdeologicalAllianceSettings(WDEorg org) {
        if (!isIdeologicalAlliance(org) || hasIdeologicalAllianceIdeology(org)) {
            return false;
        }

        for (String candidateNation : buildIdeologicalAllianceFounderCandidates(org)) {
            String ideology = getNationIdeology(candidateNation);
            if (!"None".equalsIgnoreCase(ideology)) {
                org.setSetting("allianceIdeology", ideology);
                return true;
            }
        }

        return false;
    }

    private List<String> buildIdeologicalAllianceFounderCandidates(WDEorg org) {
        List<String> candidates = new ArrayList<>();
        if (org == null) {
            return candidates;
        }
        if (org.getNationName() != null && !org.getNationName().isBlank()) {
            candidates.add(org.getNationName());
        }
        if (org.getChairmanNationName() != null && !org.getChairmanNationName().isBlank()
                && candidates.stream().noneMatch(existing -> existing.equalsIgnoreCase(org.getChairmanNationName()))) {
            candidates.add(org.getChairmanNationName());
        }
        for (String memberNation : org.getMemberNationNames()) {
            if (memberNation != null && !memberNation.isBlank()
                    && candidates.stream().noneMatch(existing -> existing.equalsIgnoreCase(memberNation))) {
                candidates.add(memberNation);
            }
        }
        return candidates;
    }

    public boolean hasActiveIndependenceWarBetween(String firstNation, String secondNation) {
        if (firstNation == null || secondNation == null || firstNation.equalsIgnoreCase(secondNation)) return false;
        for (WDEorg org : getAllOrganizations()) {
            for (IndependenceWarState war : getActiveIndependenceWars(org)) {
                if (war.getRebelNationName().equalsIgnoreCase(firstNation) && org.hasMemberNation(secondNation)
                        || war.getRebelNationName().equalsIgnoreCase(secondNation) && org.hasMemberNation(firstNation)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<String> enforceNeutralNation(String nationName) {
        List<String> removedAlliances = new ArrayList<>();
        if (!isNationNeutral(nationName)) return removedAlliances;

        // Remove shared war copies before an empty alliance can disappear from storage.
        Map<String, MilitaryWarState> affectedWars = new LinkedHashMap<>();
        for (WDEorg org : warContexts()) {
            for (MilitaryWarState war : getActiveMilitaryWars(org)) {
                if (war.isAttackerNation(nationName) || war.isDefenderNation(nationName)) {
                    affectedWars.put(war.getWarKey(), war);
                }
            }
        }
        for (MilitaryWarState war : affectedWars.values()) {
            if (war.isWarLeader(nationName)) clearMilitaryWarAcrossOrganizations(war.getWarKey());
            else {
                removeNationFromMilitaryWar(war.getWarKey(), nationName);
                for (WDEorg org : warContexts()) {
                    Map<String, PeaceTreatyProposalState> treaties = readPeaceTreatyProposals(org);
                    PeaceTreatyProposalState treaty = treaties.get(war.getWarKey());
                    if (treaty != null && (nationName.equalsIgnoreCase(treaty.getProposerNationName())
                            || nationName.equalsIgnoreCase(treaty.getTargetNationName()))) {
                        treaties.remove(war.getWarKey());
                        writePeaceTreatyProposals(org, treaties);
                    }
                }
            }
        }
        for (WDEorg org : getAllOrganizations()) {
            Map<String, MilitaryWarProposalState> proposals = readMilitaryWarProposals(org);
            proposals.entrySet().removeIf(entry -> nationName.equalsIgnoreCase(entry.getValue().getProposerNationName())
                    || nationName.equalsIgnoreCase(entry.getValue().getTargetNationName()));
            for (MilitaryWarProposalState proposal : proposals.values()) {
                proposal.getVotes().keySet().removeIf(name -> nationName.equalsIgnoreCase(name));
            }
            writeMilitaryWarProposals(org, proposals);
            if (!isMilitaryAlliance(org)) continue;
            org.getPendingNationApplications().removeIf(name -> nationName.equalsIgnoreCase(name));
            org.getPendingNationVotes().keySet().removeIf(name -> nationName.equalsIgnoreCase(name));
            for (Map<String, Boolean> votes : org.getPendingNationVotes().values()) {
                votes.keySet().removeIf(name -> nationName.equalsIgnoreCase(name));
            }
            if (org.hasMemberNation(nationName)) {
                removedAlliances.add(org.getOrganizationName());
                removeMemberNationAndAdjust(org, nationName);
            }
        }
        saveOrganizations();
        return removedAlliances;
    }

    public void enforceNeutralityOnStartup() {
        Set<String> knownNations = new LinkedHashSet<>();
        for (WDEnation nation : ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                .getNationManager().getAllNations()) knownNations.add(nation.getNationName());
        for (WDEorg org : getAllOrganizations()) {
            knownNations.addAll(org.getMemberNationNames());
            knownNations.addAll(org.getPendingNationApplications());
        }
        for (String nation : knownNations) {
            if (isNationNeutral(nation)) enforceNeutralNation(nation);
        }
    }

    private boolean isSovereignEmpireInCombat(WDEorg org, NationManager nationManager) {
        if (org == null || !org.isSovereignEmpire()) {
            return false;
        }

        if (!getActiveIndependenceWars(org).isEmpty()) {
            return true;
        }

        for (String memberNation : org.getMemberNationNames()) {
            for (WDEorg existing : getAllOrganizations()) {
                for (MilitaryWarState warState : readActiveMilitaryWars(existing).values()) {
                    if (warState.getAttackerNationNames().stream().anyMatch(nation -> nation.equalsIgnoreCase(memberNation))
                            || warState.getDefenderNationNames().stream().anyMatch(nation -> nation.equalsIgnoreCase(memberNation))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Player getOnlineChairmanArmyCommander(WDEorg org, NationManager nationManager) {
        if (org == null || nationManager == null) {
            return null;
        }

        WDEnation chairmanNation = nationManager.getNation(org.getChairmanNationName());
        if (chairmanNation == null) {
            return null;
        }

        String commanderName = chairmanNation.getArmyCommander();
        if (commanderName == null || commanderName.isBlank() || "None".equalsIgnoreCase(commanderName)) {
            return null;
        }
        return Bukkit.getPlayerExact(commanderName);
    }

    private boolean hasSubjectPlayerNearChairmanCommander(String nationName, Player chairmanCommander, double radius,
                                                          boolean independenceCombat) {
        Nation nation = resolveTownyNation(nationName);
        if (nation == null || chairmanCommander == null || !chairmanCommander.isOnline()) {
            return false;
        }

        double radiusSquared = radius * radius;
        for (Player player : TownyAPI.getInstance().getOnlinePlayersInNation(nation)) {
            if (player == null || !player.isOnline() || !player.getWorld().equals(chairmanCommander.getWorld())) {
                continue;
            }
            Town town = TownyAPI.getInstance().getTown(player);
            if (!independenceCombat && town != null && town.isNeutral()) continue;
            if (player.getLocation().distanceSquared(chairmanCommander.getLocation()) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void applySubjectWarParticipationPenalty(String nationName, long durationTicks, boolean independenceCombat) {
        Nation nation = resolveTownyNation(nationName);
        if (nation == null) {
            return;
        }

        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(20L, durationTicks));
        PotionEffect penalty = new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 0, false, false, true);
        for (Player player : TownyAPI.getInstance().getOnlinePlayersInNation(nation)) {
            if (player != null && player.isOnline()) {
                Town town = TownyAPI.getInstance().getTown(player);
                if (!independenceCombat && town != null && town.isNeutral()) continue;
                player.addPotionEffect(penalty, true);
            }
        }
    }

    private boolean withdrawNationTreasuryCharge(Nation nation, EconomyBridge.Charge charge, String reason) {
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        return GovernmentTreasury.withdraw(nation, charge, engine.getEconomyBridge(),
                engine.getDefaultEconomyCurrency(), reason);
    }

    private String formatCharge(EconomyBridge.Charge charge) {
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        EconomyBridge economyBridge = engine.getEconomyBridge();
        if (charge == null) {
            return "0";
        }
        return economyBridge == null
                ? charge.amount().toPlainString() + " " + charge.currency()
                : economyBridge.format(charge);
    }

    public void processPeaceTreatyTributes(NationManager nationManager) {
        if (nationManager == null) {
            return;
        }

        boolean changed = false;
        for (WDEnation sourceNationData : nationManager.getAllNations()) {
            List<Map<String, Object>> obligations = readNationMapList(sourceNationData, "peaceTreatyTributes");
            if (obligations.isEmpty()) {
                continue;
            }

            Nation sourceNation = nationManager.getTownyNation(sourceNationData.getNationName());
            if (sourceNation == null) {
                continue;
            }

            for (Map<String, Object> obligation : obligations) {
                String beneficiaryNationName = String.valueOf(obligation.getOrDefault("beneficiaryNation", ""));
                if (beneficiaryNationName.isBlank()) {
                    continue;
                }

                Nation beneficiaryNation = nationManager.getTownyNation(beneficiaryNationName);
                if (beneficiaryNation == null) {
                    continue;
                }

                BigDecimal baseAmount = getNationTreatyTributeBase(sourceNationData);
                BigDecimal rate = readPercentage(obligation.get("taxRatePercent"), getPeaceTreatyTributeTaxRatePercent());
                BigDecimal amount = baseAmount.multiply(rate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                if (withdrawNationAmount(sourceNation, amount, "WorldDynamicsEngine peace treaty tribute")) {
                    depositNationAmount(beneficiaryNation, amount, "WorldDynamicsEngine peace treaty tribute");
                    changed = true;
                }
            }
        }

        if (changed) {
            nationManager.saveNations();
        }
    }

    private void recordMilitaryWarState(List<WDEorg> orgs, MilitaryWarState warState) {
        if (warState == null) return;
        warLedger.wars.put(warState.getWarKey(), warState.toMap());
        saveOrganizations();
    }

    private Map<String, MilitaryWarState> readActiveMilitaryWars(WDEorg org) {
        Map<String, MilitaryWarState> wars = new LinkedHashMap<>();
        Object rawValue = warLedger.wars;
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return wars;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> warMap)) {
                continue;
            }
            MilitaryWarState state = MilitaryWarState.fromMap(String.valueOf(entry.getKey()), warMap);
            if (org == null || org.getMemberNationNames().stream()
                    .anyMatch(n -> state.isAttackerNation(n) || state.isDefenderNation(n))) {
                wars.put(state.getWarKey(), state);
            }
        }
        return wars;
    }

    private void writeActiveMilitaryWars(WDEorg org, Map<String, MilitaryWarState> wars) {
        for (String key : readActiveMilitaryWars(org).keySet()) warLedger.wars.remove(key);
        if (wars == null) return;
        for (Map.Entry<String, MilitaryWarState> entry : wars.entrySet()) {
            warLedger.wars.put(entry.getKey(), entry.getValue().toMap());
        }
    }

    private Map<String, PeaceTreatyProposalState> readPeaceTreatyProposals(WDEorg org) {
        Map<String, PeaceTreatyProposalState> proposals = new LinkedHashMap<>();
        Object rawValue = org == null ? warLedger.treaties : org.getSetting(PEACE_TREATY_PROPOSALS_SETTING);
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return proposals;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> proposalMap)) {
                continue;
            }
            PeaceTreatyProposalState state = PeaceTreatyProposalState.fromMap(String.valueOf(entry.getKey()), proposalMap);
            proposals.put(state.getWarKey(), state);
        }
        return proposals;
    }

    private void writePeaceTreatyProposals(WDEorg org, Map<String, PeaceTreatyProposalState> proposals) {
        if (org == null) {
            warLedger.treaties.clear();
            if (proposals != null) proposals.forEach((key, value) -> warLedger.treaties.put(key, value.toMap()));
            return;
        }

        if (proposals == null || proposals.isEmpty()) {
            org.getSettings().remove(PEACE_TREATY_PROPOSALS_SETTING);
            return;
        }

        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, PeaceTreatyProposalState> entry : proposals.entrySet()) {
            serialized.put(entry.getKey(), entry.getValue().toMap());
        }
        org.setSetting(PEACE_TREATY_PROPOSALS_SETTING, serialized);
    }

    private WarContext resolveWarContext(WDEorg org, String actorNationName, String targetNationName) {
        if (actorNationName == null || targetNationName == null) {
            return null;
        }

        for (MilitaryWarState warState : readActiveMilitaryWars(org).values()) {
            if (isNationNeutral(actorNationName) || isNationNeutral(targetNationName)) continue;
            Set<String> attackerSide = warState.getAttackerNationNames();
            Set<String> defenderSide = warState.getDefenderNationNames();
            if (attackerSide.contains(actorNationName) && defenderSide.contains(targetNationName)) {
                return WarContext.military(org, warState, attackerSide, defenderSide);
            }
            if (defenderSide.contains(actorNationName) && attackerSide.contains(targetNationName)) {
                return WarContext.military(org, warState, defenderSide, attackerSide);
            }
        }

        for (IndependenceWarState warState : getActiveIndependenceWars(org)) {
            String rebelNation = warState.getRebelNationName();
            Set<String> rebelSide = new LinkedHashSet<>();
            rebelSide.add(rebelNation);
            Set<String> empireSide = new LinkedHashSet<>(org.getMemberNationNames());
            empireSide.removeIf(existing -> existing != null && existing.equalsIgnoreCase(rebelNation));
            if (rebelSide.contains(actorNationName) && empireSide.contains(targetNationName)) {
                return WarContext.independence(org, warState, rebelSide, empireSide);
            }
            if (empireSide.contains(actorNationName) && rebelSide.contains(targetNationName)) {
                return WarContext.independence(org, warState, empireSide, rebelSide);
            }
        }

        return null;
    }

    private String validatePeaceTreatyProposalAuthority(WarContext context, String actorNationName, String targetNationName) {
        if (context == null || actorNationName == null || targetNationName == null) {
            return "common_no_authority";
        }
        if (!context.isNationOnActorSide(actorNationName) || !context.isNationOnCounterpartySide(targetNationName)) {
            return "common_no_authority";
        }
        if (context.getWarType() != WarContext.WarType.MILITARY_ALLIANCE) {
            return null;
        }

        MilitaryWarState warState = context.getMilitaryWarState();
        if (warState == null) {
            return "common_no_authority";
        }

        boolean actorLeader = warState.isWarLeader(actorNationName);
        boolean targetLeader = warState.isWarLeader(targetNationName);
        if (actorLeader && targetLeader) {
            return null;
        }

        if (isNationSovereignSubject(targetNationName)) {
            return "org_peace_treaty_target_subject_forbidden";
        }

        if (actorLeader && !targetLeader) {
            return null;
        }
        if (!actorLeader && targetLeader && !isNationSovereignSubject(actorNationName)) {
            return null;
        }
        return "org_peace_treaty_authority_invalid";
    }

    private boolean isNationSovereignSubject(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return false;
        }

        for (WDEorg org : getAllOrganizations()) {
            if (!org.isSovereignEmpire()) {
                continue;
            }
            if (org.hasMemberNation(nationName) && !nationName.equalsIgnoreCase(org.getChairmanNationName())) {
                return true;
            }
        }
        return false;
    }

    private int calculatePeaceTreatyPoints(WarContext context,
                                           String actorNationName,
                                           List<PeaceTreatyClauseState> clauses,
                                           boolean separatePeace) {
        double total = 0D;
        for (PeaceTreatyClauseState clause : clauses) {
            total += getPeaceTreatyClausePoints(clause);
        }
        if (separatePeace && total > 0) {
            total *= 2;
        }
        return (int) Math.ceil(total * getIdeologicalAlliancePeaceTreatyMultiplier(context, actorNationName));
    }

    private double getIdeologicalAlliancePeaceTreatyMultiplier(WarContext context, String actorNationName) {
        if (context == null || actorNationName == null || context.getWarType() != WarContext.WarType.MILITARY_ALLIANCE) {
            return 1.0D;
        }

        MilitaryWarState warState = context.getMilitaryWarState();
        if (warState == null) {
            return 1.0D;
        }

        WDEorg ideologicalAlliance = getPrimaryIdeologicalAllianceForNation(actorNationName);
        if (ideologicalAlliance == null) {
            return 1.0D;
        }

        String allianceIdeology = getIdeologicalAllianceIdeology(ideologicalAlliance);
        String declaredOnLeaderIdeology = getNationIdeology(warState.getDefenderNationName());
        if ("None".equalsIgnoreCase(allianceIdeology) || "None".equalsIgnoreCase(declaredOnLeaderIdeology)) {
            return 1.0D;
        }

        if (allianceIdeology.equalsIgnoreCase(declaredOnLeaderIdeology)) {
            return getIdeologicalAllianceSameReligionTreatyMultiplier();
        }

        return getIdeologicalAllianceHereticTreatyMultiplier();
    }

    private boolean isSeparatePeaceProposal(WarContext context, String actorNationName, String targetNationName) {
        if (context == null || actorNationName == null || targetNationName == null) {
            return false;
        }
        if (context.getWarType() != WarContext.WarType.MILITARY_ALLIANCE) {
            return false;
        }

        MilitaryWarState warState = context.getMilitaryWarState();
        if (warState == null) {
            return false;
        }
        return !(warState.isWarLeader(actorNationName) && warState.isWarLeader(targetNationName));
    }

    private int getPeaceTreatyClausePoints(PeaceTreatyClauseState clause) {
        if (clause == null || clause.getType() == null) {
            return 0;
        }

        FileConfiguration config = getWarConfiguration();
        return switch (clause.getType()) {
            case REPARATIONS -> Math.max(0, clause.getInt("percent")) * Math.max(0, config.getInt("peaceTreaty.clauses.reparations.pointsPerPercent", 1));
            case FORCE_CONVERSION -> Math.max(0, config.getInt("peaceTreaty.clauses.forceConversionPoints", 15));
            case TRANSFER_TOWN -> Math.max(0, config.getInt("peaceTreaty.clauses.transferTownPoints", 20));
            case DISSOLVE_ALLIANCE -> Math.max(0, config.getInt("peaceTreaty.clauses.dissolveAlliancePoints", 30));
            case EXPEL_FROM_ALLIANCE -> Math.max(0, config.getInt("peaceTreaty.clauses.expelFromAlliancePoints", 15));
            case LIBERATE_SUBJECT -> Math.max(0, config.getInt("peaceTreaty.clauses.liberateSubjectPoints", 25));
            case UNCONDITIONAL_PASSAGE -> Math.max(0, config.getInt("peaceTreaty.clauses.unconditionalPassagePoints", 10));
            case HUMILIATE -> Math.max(0, config.getInt("peaceTreaty.clauses.humiliatePoints", 5));
            case LOOT_CAPITAL -> Math.max(0, config.getInt("peaceTreaty.clauses.lootCapitalPoints", 20));
            case TRIBUTE -> Math.max(0, config.getInt("peaceTreaty.clauses.tributePoints", 25));
            case ALLOW_INDEPENDENCE -> Math.max(0, config.getInt("peaceTreaty.clauses.allowIndependencePoints", 40));
        };
    }

    private int getPeaceTreatyMaxProposalPoints() {
        return Math.max(1, getWarConfiguration().getInt("peaceTreaty.maxProposalPoints", 100));
    }

    private BigDecimal getPeaceTreatyTributeTaxRatePercent() {
        return BigDecimal.valueOf(Math.max(0D, getWarConfiguration().getDouble("peaceTreaty.tribute.taxRatePercent", 5.0D)));
    }

    private double getMilitaryWarKillPoints() {
        double points = getWarConfiguration().getDouble("scoring.killPoints", 1.0D);
        return Double.isFinite(points) ? Math.max(0D, points) : 0D;
    }

    private double getTreatyScoreDifference(WarContext context,
                                            String proposerNationName,
                                            String targetNationName,
                                            PeaceTreatyProposalState.ProposalType proposalType) {
        if (context == null || proposerNationName == null || targetNationName == null || proposalType == null) {
            return 0D;
        }

        String beneficiaryNationName = proposalType == PeaceTreatyProposalState.ProposalType.DEMAND
                ? proposerNationName
                : targetNationName;
        String concedingNationName = proposalType == PeaceTreatyProposalState.ProposalType.DEMAND
                ? targetNationName
                : proposerNationName;
        if (context.getMilitaryWarState() != null
                && isSeparatePeaceProposal(context, proposerNationName, targetNationName)) {
            return context.getMilitaryWarState().localizedDifference(beneficiaryNationName, concedingNationName);
        }
        if (context.getIndependenceWarState() != null) {
            String empireNation = context.getIndependenceWarState().getRebelNationName().equalsIgnoreCase(beneficiaryNationName)
                    ? concedingNationName : beneficiaryNationName;
            if (!empireNation.equalsIgnoreCase(context.getOrganization().getChairmanNationName())) {
                IndependenceWarState state = context.getIndependenceWarState();
                double rebel = state.rebelOccupation.getOrDefault(state.rebelNationName, 0D)
                        + state.rebelOccupation.getOrDefault(empireNation, 0D) + state.rebelKills.getOrDefault(empireNation, 0D);
                double empire = state.empireOccupation.getOrDefault(state.rebelNationName, 0D)
                        + state.empireKills.getOrDefault(state.rebelNationName, 0D);
                return beneficiaryNationName.equalsIgnoreCase(state.rebelNationName) ? rebel - empire : empire - rebel;
            }
        }
        return getWarSideScore(context, beneficiaryNationName) - getWarSideScore(context, concedingNationName);
    }

    private double getWarSideScore(WarContext context, String nationName) {
        if (context == null || nationName == null || nationName.isBlank()) {
            return 0D;
        }

        if (context.getWarType() == WarContext.WarType.MILITARY_ALLIANCE) {
            MilitaryWarState warState = context.getMilitaryWarState();
            if (warState == null) {
                return 0D;
            }
            if (warState.isAttackerNation(nationName)) {
                return warState.getAttackerScore();
            }
            if (warState.isDefenderNation(nationName)) {
                return warState.getDefenderScore();
            }
            return 0D;
        }

        if (context.getWarType() == WarContext.WarType.SOVEREIGN_INDEPENDENCE) {
            IndependenceWarState state = context.getIndependenceWarState();
            if (state == null) {
                return 0D;
            }
            return state.getRebelNationName().equalsIgnoreCase(nationName) ? state.getRebelScore() : state.getEmpireScore();
        }
        return 0D;
    }

    private boolean shouldForceAcceptIndependenceTreaty(WarContext context,
                                                        PeaceTreatyProposalState proposal,
                                                        double scoreDifference) {
        return context != null
                && proposal != null
                && context.getWarType() == WarContext.WarType.SOVEREIGN_INDEPENDENCE
                && isAllowIndependenceOnlyTreaty(proposal.getClauses())
                && isIndependenceBeneficiaryRebel(context, proposal)
                && scoreDifference >= proposal.getTotalPoints();
    }

    private boolean isAllowIndependenceOnlyTreaty(List<PeaceTreatyClauseState> clauses) {
        return clauses != null
                && clauses.size() == 1
                && clauses.getFirst().getType() == PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE;
    }

    private boolean hasAllowIndependenceClause(List<PeaceTreatyClauseState> clauses) {
        if (clauses == null) {
            return false;
        }
        for (PeaceTreatyClauseState clause : clauses) {
            if (clause != null && clause.getType() == PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE) {
                return true;
            }
        }
        return false;
    }

    private boolean isIndependenceBeneficiaryRebel(WarContext context, PeaceTreatyProposalState proposal) {
        if (context == null || proposal == null || context.getIndependenceWarState() == null) {
            return false;
        }
        String beneficiaryNationName = proposal.getProposalType() == PeaceTreatyProposalState.ProposalType.DEMAND
                ? proposal.getProposerNationName()
                : proposal.getTargetNationName();
        return context.getIndependenceWarState().getRebelNationName().equalsIgnoreCase(beneficiaryNationName);
    }

    private String validatePeaceTreatyClauses(WarContext context,
                                              String proposerNationName,
                                              String targetNationName,
                                              PeaceTreatyProposalState.ProposalType proposalType,
                                              List<PeaceTreatyClauseState> clauses,
                                              NationManager nationManager,
                                              boolean runtimeValidation) {
        String beneficiaryNationName = proposalType == PeaceTreatyProposalState.ProposalType.DEMAND
                ? proposerNationName
                : targetNationName;
        String concedingNationName = proposalType == PeaceTreatyProposalState.ProposalType.DEMAND
                ? targetNationName
                : proposerNationName;

        WDEnation beneficiaryNation = nationManager.getNation(beneficiaryNationName);
        WDEnation concedingNation = nationManager.getNation(concedingNationName);
        if (beneficiaryNation == null || concedingNation == null) {
            return "nation_not_found";
        }

        boolean independenceWar = context.getWarType() == WarContext.WarType.SOVEREIGN_INDEPENDENCE;
        boolean rebelBeneficiary = independenceWar
                && context.getIndependenceWarState() != null
                && context.getIndependenceWarState().getRebelNationName().equalsIgnoreCase(beneficiaryNationName);
        boolean hasAllowIndependence = hasAllowIndependenceClause(clauses);
        if (independenceWar && rebelBeneficiary && clauses.stream().anyMatch(clause -> clause.getType() != PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE) && !hasAllowIndependence) {
            return "org_peace_treaty_independence_requires_allow";
        }

        int maxReparationPercent = Math.max(1, getWarConfiguration().getInt("peaceTreaty.clauses.reparations.maxPercent", 20));
        for (PeaceTreatyClauseState clause : clauses) {
            switch (clause.getType()) {
                case REPARATIONS -> {
                    int percent = clause.getInt("percent");
                    if (percent <= 0 || percent > maxReparationPercent) {
                        return "org_peace_treaty_invalid_reparations_percent";
                    }
                }
                case FORCE_CONVERSION -> {
                    String religionName = beneficiaryNation.getReligionName();
                    if (religionName == null || religionName.isBlank() || "None".equalsIgnoreCase(religionName)) {
                        return "org_peace_treaty_force_conversion_requires_religion";
                    }
                }
                case TRANSFER_TOWN -> {
                    String townName = clause.getString("town");
                    String destinationNationName = clause.getString("destinationNation");
                    WDEtown town = townManager.getTown(townName);
                    if (town == null || !town.getNationName().equalsIgnoreCase(concedingNationName)) {
                        return "org_peace_treaty_transfer_town_invalid";
                    }
                    if (!context.getActorSideNations().contains(destinationNationName) && !context.getCounterpartySideNations().contains(destinationNationName)) {
                        return "org_peace_treaty_transfer_destination_invalid";
                    }
                    if (proposalType == PeaceTreatyProposalState.ProposalType.DEMAND && !context.getActorSideNations().contains(destinationNationName)) {
                        return "org_peace_treaty_transfer_destination_invalid";
                    }
                    if (proposalType == PeaceTreatyProposalState.ProposalType.SURRENDER && !context.getCounterpartySideNations().contains(destinationNationName)) {
                        return "org_peace_treaty_transfer_destination_invalid";
                    }
                }
                case DISSOLVE_ALLIANCE -> {
                    boolean canDissolve = getMilitaryAlliancesByNation(concedingNationName).stream()
                            .anyMatch(alliance -> concedingNationName.equalsIgnoreCase(alliance.getChairmanNationName()));
                    if (!canDissolve) {
                        return "org_peace_treaty_dissolve_alliance_invalid";
                    }
                }
                case EXPEL_FROM_ALLIANCE -> {
                    boolean canExpel = organizations.values().stream()
                            .anyMatch(existing -> existing.hasMemberNation(concedingNationName)
                                    && !concedingNationName.equalsIgnoreCase(existing.getChairmanNationName()));
                    if (!canExpel) {
                        return "org_peace_treaty_expel_alliance_invalid";
                    }
                }
                case LIBERATE_SUBJECT -> {
                    String subjectNationName = clause.getString("subjectNation");
                    WDEorg sovereignEmpire = getOrganizationByChairmanSovereignEmpire(concedingNationName);
                    if (sovereignEmpire == null || !isSovereignSubjectNation(sovereignEmpire, subjectNationName)) {
                        return "org_peace_treaty_liberate_subject_invalid";
                    }
                }
                case LOOT_CAPITAL -> {
                    Town capitalTown = resolveCapitalTown(concedingNationName, nationManager);
                    if (capitalTown == null) {
                        return "org_peace_treaty_loot_capital_invalid";
                    }
                }
                case ALLOW_INDEPENDENCE -> {
                    if (!independenceWar || !rebelBeneficiary) {
                        return "org_peace_treaty_allow_independence_invalid";
                    }
                }
                case TRIBUTE, UNCONDITIONAL_PASSAGE, HUMILIATE -> {
                }
            }
        }
        return null;
    }

    private void executePeaceTreaty(WarContext context, PeaceTreatyProposalState proposal, NationManager nationManager) {
        String beneficiaryNationName = proposal.getProposalType() == PeaceTreatyProposalState.ProposalType.DEMAND
                ? proposal.getProposerNationName()
                : proposal.getTargetNationName();
        String concedingNationName = proposal.getProposalType() == PeaceTreatyProposalState.ProposalType.DEMAND
                ? proposal.getTargetNationName()
                : proposal.getProposerNationName();

        Nation beneficiaryNation = nationManager.getTownyNation(beneficiaryNationName);
        Nation concedingNation = nationManager.getTownyNation(concedingNationName);
        WDEnation beneficiaryNationData = nationManager.getNation(beneficiaryNationName);
        WDEnation concedingNationData = nationManager.getNation(concedingNationName);
        if (beneficiaryNation == null || concedingNation == null || beneficiaryNationData == null || concedingNationData == null) {
            return;
        }

        boolean grantsIndependence = false;
        for (PeaceTreatyClauseState clause : proposal.getClauses()) {
            switch (clause.getType()) {
                case REPARATIONS -> applyReparationsClause(concedingNation, beneficiaryNation, clause.getInt("percent"));
                case FORCE_CONVERSION -> {
                    concedingNationData.setReligionName(beneficiaryNationData.getReligionName());
                    concedingNationData.setReligionChosen(true);
                }
                case TRANSFER_TOWN -> {
                    String townName = clause.getString("town");
                    String destinationNationName = clause.getString("destinationNation");
                    townManager.transferTownToNation(townName, destinationNationName);
                }
                case DISSOLVE_ALLIANCE -> dissolveMilitaryAlliancesByChairman(concedingNationName);
                case EXPEL_FROM_ALLIANCE -> expelNationFromAlliances(concedingNationName);
                case LIBERATE_SUBJECT -> {
                    String subjectNationName = clause.getString("subjectNation");
                    WDEorg sovereignEmpire = getOrganizationByChairmanSovereignEmpire(concedingNationName);
                    if (sovereignEmpire != null) {
                        removeMemberNationAndAdjust(sovereignEmpire, subjectNationName);
                    }
                }
                case UNCONDITIONAL_PASSAGE -> nationManager.setUnconditionalPassage(concedingNationName, beneficiaryNationName, true);
                case HUMILIATE -> nationManager.addNationHumiliation(concedingNationName, beneficiaryNationName);
                case LOOT_CAPITAL -> applyLootCapitalClause(concedingNationName, beneficiaryNation, nationManager);
                case TRIBUTE -> addPeaceTreatyTributeObligation(concedingNationData, beneficiaryNationName);
                case ALLOW_INDEPENDENCE -> grantsIndependence = true;
            }
        }

        nationManager.saveNations();
        endWarByTreaty(context, proposal, nationManager, grantsIndependence);
    }

    private void endWarByTreaty(WarContext context, PeaceTreatyProposalState proposal, NationManager nationManager, boolean grantsIndependence) {
        if (context.getWarType() == WarContext.WarType.MILITARY_ALLIANCE) {
            MilitaryWarState warState = context.getMilitaryWarState();
            if (warState == null || proposal == null) {
                return;
            }

            if (warState.isWarLeader(proposal.getProposerNationName()) && warState.isWarLeader(proposal.getTargetNationName())) {
                createWarTrucesBetweenSides(warState.getAttackerNationNames(), warState.getDefenderNationNames(), proposal.getTotalPoints(), nationManager);
                clearMilitaryWarAcrossOrganizations(context.getWarKey());
            } else {
                String exitingNationName = warState.isWarLeader(proposal.getProposerNationName())
                        ? proposal.getTargetNationName()
                        : proposal.getProposerNationName();
                createWarTruce(proposal.getProposerNationName(), proposal.getTargetNationName(), proposal.getTotalPoints(), nationManager);
                removeNationFromMilitaryWar(context.getWarKey(), exitingNationName);
            }
            saveOrganizations();
            return;
        }

        if (context.getWarType() == WarContext.WarType.SOVEREIGN_INDEPENDENCE) {
            IndependenceWarState state = context.getIndependenceWarState();
            if (state == null) {
                return;
            }
            Map<String, IndependenceWarState> wars = readIndependenceWars(context.getOrganization());
            String key = state == null ? null : findIndependenceWarKey(wars, state.getRebelNationName());
            if (grantsIndependence) {
                resolveIndependenceWar(context.getOrganization(), wars, state, IndependenceWarOutcome.SUCCESS);
            } else if (key != null) {
                wars.remove(key);
                if (wars.isEmpty()) {
                    clearIndependenceWars(context.getOrganization());
                } else {
                    writeIndependenceWars(context.getOrganization(), wars);
                }
            }
            createWarTrucesBetweenSides(context.getActorSideNations(), context.getCounterpartySideNations(), proposal.getTotalPoints(), nationManager);
            saveOrganizations();
        }
    }

    private void clearMilitaryWarAcrossOrganizations(String warKey) {
        MilitaryWarState endedWar = readActiveMilitaryWars(null).get(warKey);
        warLedger.wars.remove(warKey);
        warLedger.treaties.remove(warKey);
        for (WDEorg existing : getAllOrganizations()) {
            Map<String, MilitaryWarState> wars = readActiveMilitaryWars(existing);
            if (wars.remove(warKey) != null) {
                writeActiveMilitaryWars(existing, wars);
            }

            Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(existing);
            if (proposals.remove(warKey) != null) {
                writePeaceTreatyProposals(existing, proposals);
            }
        }
        if (endedWar != null) {
            broadcastMilitaryWarEnded(endedWar);
        }
    }

    private void removeNationFromMilitaryWar(String warKey, String exitingNationName) {
        boolean warStillActive = false;
        for (WDEorg existing : warContexts()) {
            Map<String, MilitaryWarState> wars = readActiveMilitaryWars(existing);
            MilitaryWarState state = wars.get(warKey);
            if (state == null) {
                continue;
            }

            if (state.removeParticipant(exitingNationName)) {
                if (state.hasMinimumParticipants()) {
                    wars.put(warKey, state);
                    warStillActive = true;
                } else {
                    wars.remove(warKey);
                }
                writeActiveMilitaryWars(existing, wars);
            }
        }

        if (!warStillActive) {
            clearMilitaryWarAcrossOrganizations(warKey);
        }
    }

    private void createWarTrucesBetweenSides(Set<String> firstSideNations,
                                             Set<String> secondSideNations,
                                             int treatyPoints,
                                             NationManager nationManager) {
        if (nationManager == null || firstSideNations == null || secondSideNations == null) {
            return;
        }

        for (String firstNation : firstSideNations) {
            for (String secondNation : secondSideNations) {
                createWarTruce(firstNation, secondNation, treatyPoints, nationManager);
            }
        }
    }

    private void createWarTruce(String firstNationName, String secondNationName, int treatyPoints, NationManager nationManager) {
        if (nationManager == null || firstNationName == null || secondNationName == null
                || firstNationName.isBlank() || secondNationName.isBlank()
                || firstNationName.equalsIgnoreCase(secondNationName)) {
            return;
        }

        double percentage = Math.min(1.0D, Math.max(0.0D, treatyPoints / (double) getPeaceTreatyMaxProposalPoints()));
        var clock = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getWarTruceClock();
        nationManager.setWarTruce(firstNationName, secondNationName, clock.createDeadline(percentage));
    }

    private void applyReparationsClause(Nation sourceNation, Nation beneficiaryNation, int percent) {
        BigDecimal treasury = getGovernmentHoldingBalance(sourceNation);
        if (treasury.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal amount = treasury.multiply(BigDecimal.valueOf(Math.max(0, percent)))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (withdrawNationAmount(sourceNation, amount, "WorldDynamicsEngine peace treaty reparations")) {
            depositNationAmount(beneficiaryNation, amount, "WorldDynamicsEngine peace treaty reparations");
        }
    }

    private void applyLootCapitalClause(String sourceNationName, Nation beneficiaryNation, NationManager nationManager) {
        Town capitalTown = resolveCapitalTown(sourceNationName, nationManager);
        if (capitalTown == null) {
            return;
        }

        BigDecimal treasury = getGovernmentHoldingBalance(capitalTown);
        BigDecimal amount = treasury.multiply(BigDecimal.valueOf(0.5D)).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (withdrawTownAmount(capitalTown, amount, "WorldDynamicsEngine peace treaty capital plunder")) {
            depositNationAmount(beneficiaryNation, amount, "WorldDynamicsEngine peace treaty capital plunder");
        }
    }

    private void dissolveMilitaryAlliancesByChairman(String chairmanNationName) {
        List<WDEorg> toRemove = new ArrayList<>();
        for (WDEorg org : getAllOrganizations()) {
            if (isMilitaryAlliance(org) && chairmanNationName.equalsIgnoreCase(org.getChairmanNationName())) {
                toRemove.add(org);
            }
        }
        for (WDEorg org : toRemove) {
            removeOrganization(org);
        }
        if (!toRemove.isEmpty()) {
            saveOrganizations();
        }
    }

    private void expelNationFromAlliances(String nationName) {
        boolean changed = false;
        for (WDEorg org : getAllOrganizations()) {
            if (!org.hasMemberNation(nationName) || nationName.equalsIgnoreCase(org.getChairmanNationName())) {
                continue;
            }
            removeMemberNationAndAdjust(org, nationName);
            changed = true;
        }
        if (changed) {
            saveOrganizations();
        }
    }

    private WDEorg getOrganizationByChairmanSovereignEmpire(String chairmanNationName) {
        for (WDEorg org : getAllOrganizations()) {
            if (org.isSovereignEmpire() && chairmanNationName.equalsIgnoreCase(org.getChairmanNationName())) {
                return org;
            }
        }
        return null;
    }

    private void addPeaceTreatyTributeObligation(WDEnation concedingNation, String beneficiaryNationName) {
        List<Map<String, Object>> obligations = readNationMapList(concedingNation, "peaceTreatyTributes");
        Map<String, Object> obligation = new LinkedHashMap<>();
        obligation.put("beneficiaryNation", beneficiaryNationName);
        obligation.put("taxRatePercent", getPeaceTreatyTributeTaxRatePercent().doubleValue());
        obligation.put("appliedAt", System.currentTimeMillis());
        obligations.add(obligation);
        concedingNation.setSetting("peaceTreatyTributes", obligations);
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

    private BigDecimal getNationTreatyTributeBase(WDEnation nation) {
        if (nation == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (String townName : nation.getTownNames()) {
            WDEtown town = townManager.getTown(townName);
            if (town == null) {
                continue;
            }
            total = total.add(BigDecimal.valueOf(Math.max(0D, town.getResidentTaxAmount())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal readPercentage(Object rawValue, BigDecimal fallback) {
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return rawValue == null ? fallback : new BigDecimal(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Town resolveCapitalTown(String nationName, NationManager nationManager) {
        Nation nation = nationManager == null ? null : nationManager.getTownyNation(nationName);
        if (nation == null) {
            return null;
        }
        try {
            return nation.getCapital();
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal getGovernmentHoldingBalance(Object government) {
        try {
            BankAccount account = government instanceof Nation nation ? nation.getAccount()
                    : government instanceof Town town ? town.getAccount()
                    : null;
            return account == null ? BigDecimal.ZERO : BigDecimal.valueOf(Math.max(0D, account.getHoldingBalance()));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private boolean withdrawNationAmount(Nation nation, BigDecimal amount, String reason) {
        return withdrawGovernmentAmount(nation, amount, reason);
    }

    private boolean withdrawTownAmount(Town town, BigDecimal amount, String reason) {
        return withdrawGovernmentAmount(town, amount, reason);
    }

    private boolean depositNationAmount(Nation nation, BigDecimal amount, String reason) {
        return depositGovernmentAmount(nation, amount, reason);
    }

    private boolean withdrawGovernmentAmount(Object government, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        try {
            BankAccount account = government instanceof Nation nation ? nation.getAccount()
                    : government instanceof Town town ? town.getAccount()
                    : null;
            if (account == null || !account.canPayFromHoldings(amount.doubleValue())) {
                return false;
            }
            boolean success = account.withdraw(amount.doubleValue(), reason);
            if (success) {
                if (government instanceof Nation nation) {
                    nation.save();
                } else if (government instanceof Town town) {
                    town.save();
                }
            }
            return success;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean depositGovernmentAmount(Object government, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        try {
            BankAccount account = government instanceof Nation nation ? nation.getAccount()
                    : government instanceof Town town ? town.getAccount()
                    : null;
            if (account == null) {
                return false;
            }
            account.deposit(amount.doubleValue(), reason);
            if (government instanceof Nation nation) {
                nation.save();
            } else if (government instanceof Town town) {
                town.save();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, MilitaryWarProposalState> readMilitaryWarProposals(WDEorg org) {
        Map<String, MilitaryWarProposalState> proposals = new LinkedHashMap<>();
        if (org == null) {
            return proposals;
        }

        Object rawValue = org.getSetting("militaryWarProposals");
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return proposals;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> proposalMap)) {
                continue;
            }
            MilitaryWarProposalState state = MilitaryWarProposalState.fromMap(String.valueOf(entry.getKey()), proposalMap);
            proposals.put(state.getTargetNationName(), state);
        }
        return proposals;
    }

    private void writeMilitaryWarProposals(WDEorg org, Map<String, MilitaryWarProposalState> proposals) {
        if (org == null) {
            return;
        }

        if (proposals == null || proposals.isEmpty()) {
            org.getSettings().remove("militaryWarProposals");
            return;
        }

        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, MilitaryWarProposalState> entry : proposals.entrySet()) {
            serialized.put(entry.getKey(), entry.getValue().toMap());
        }
        org.setSetting("militaryWarProposals", serialized);
    }

    private String findMilitaryWarProposalKey(Map<String, MilitaryWarProposalState> proposals, String targetNationName) {
        for (String key : proposals.keySet()) {
            if (key != null && key.equalsIgnoreCase(targetNationName)) {
                return key;
            }
        }
        return null;
    }

    private MilitaryWarVoteResult resolveMilitaryWarVote(WDEorg org, MilitaryWarProposalState proposal, Map<String, MilitaryWarProposalState> proposals, NationManager nationManager) {
        if (isNationNeutral(proposal.getProposerNationName()) || isNationNeutral(proposal.getTargetNationName())) {
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) proposals.remove(key);
            writeMilitaryWarProposals(org, proposals);
            saveOrganizations();
            return MilitaryWarVoteResult.peaceProtected(proposal);
        }
        double totalWeight = 0.0D;
        double yesWeight = 0.0D;
        double noWeight = 0.0D;

        for (String memberNationName : org.getMemberNationNames()) {
            if (isNationNeutral(memberNationName)) continue;
            double weight = getNationWeight(org, memberNationName, nationManager);
            totalWeight += weight;

            Boolean vote = findVoteIgnoreCase(proposal.getVotes(), memberNationName);
            if (vote == null) {
                continue;
            }
            if (vote) {
                yesWeight += weight;
            } else {
                noWeight += weight;
            }
        }

        if (yesWeight > totalWeight / 2.0D) {
            if (nationManager != null && nationManager.hasActiveWarTruce(
                    proposal.getProposerNationName(), proposal.getTargetNationName())) {
                String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
                if (key != null) {
                    proposals.remove(key);
                    writeMilitaryWarProposals(org, proposals);
                }
                saveOrganizations();
                return MilitaryWarVoteResult.truce();
            }
            if (nationManager != null && nationManager.isNationPeaceProtected(proposal.getTargetNationName())) {
                String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
                if (key != null) {
                    proposals.remove(key);
                    writeMilitaryWarProposals(org, proposals);
                }
                saveOrganizations();
                return MilitaryWarVoteResult.peaceProtected(proposal);
            }
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) {
                proposals.remove(key);
                writeMilitaryWarProposals(org, proposals);
            }
            MilitaryWarResult warResult = handleMilitaryAllianceWar(proposal.getProposerNationName(), proposal.getTargetNationName(),
                    nationManager, proposal.reason);
            if (warResult.getHostilePairsApplied() == 0) return MilitaryWarVoteResult.invalid();
            nationManager.saveNations();
            saveOrganizations();
            return MilitaryWarVoteResult.approved(warResult, proposal);
        }

        if (noWeight >= totalWeight / 2.0D) {
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) {
                proposals.remove(key);
                writeMilitaryWarProposals(org, proposals);
            }
            saveOrganizations();
            return MilitaryWarVoteResult.rejected(proposal);
        }

        return MilitaryWarVoteResult.pending(proposal);
    }

    private Map<String, IndependenceWarState> readIndependenceWars(WDEorg org) {
        Map<String, IndependenceWarState> wars = new LinkedHashMap<>();
        if (org == null) {
            return wars;
        }

        Object rawWars = org.getSetting("independenceWars");
        if (!(rawWars instanceof Map<?, ?> rawMap)) {
            return wars;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> warMap)) {
                continue;
            }

            IndependenceWarState state = IndependenceWarState.fromMap(String.valueOf(entry.getKey()), warMap);
            wars.put(state.getRebelNationName(), state);
        }
        return wars;
    }

    private void writeIndependenceWars(WDEorg org, Map<String, IndependenceWarState> wars) {
        if (org == null) {
            return;
        }

        if (wars == null || wars.isEmpty()) {
            clearIndependenceWars(org);
            return;
        }

        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, IndependenceWarState> entry : wars.entrySet()) {
            serialized.put(entry.getKey(), entry.getValue().toMap());
        }
        org.setSetting("independenceWars", serialized);
    }

    private void clearIndependenceWars(WDEorg org) {
        if (org == null) {
            return;
        }
        org.getSettings().remove("independenceWars");
    }

    private String findIndependenceWarKey(Map<String, IndependenceWarState> wars, String rebelNationName) {
        for (String key : wars.keySet()) {
            if (key != null && key.equalsIgnoreCase(rebelNationName)) {
                return key;
            }
        }
        return null;
    }

    private IndependenceWarOutcome determineTimedWarOutcome(IndependenceWarState state) {
        if (state.isChairmanSurrendered() || state.getRebelScore() - state.getEmpireScore() >= getIndependenceWarSuccessLead()) {
            return IndependenceWarOutcome.SUCCESS;
        }
        if (state.getRebelScore() < state.getEmpireScore()) {
            return IndependenceWarOutcome.FAILURE;
        }
        return IndependenceWarOutcome.STALEMATE;
    }

    private IndependenceWarResolutionResult resolveIndependenceWar(WDEorg org, Map<String, IndependenceWarState> wars, IndependenceWarState state, IndependenceWarOutcome outcome) {
        String key = findIndependenceWarKey(wars, state.getRebelNationName());
        if (key != null) {
            wars.remove(key);
        }

        if (outcome == IndependenceWarOutcome.SUCCESS) {
            boolean removedChairman = org.getChairmanNationName() != null && org.getChairmanNationName().equalsIgnoreCase(state.getRebelNationName());
            org.removeMemberNation(state.getRebelNationName());
            removeSovereignSubjectState(org, state.getRebelNationName());
            if (removedChairman && !org.getMemberNationNames().isEmpty()) {
                org.setChairmanNationName(org.getMemberNationNames().iterator().next());
            }
        }

        if (wars.isEmpty()) {
            clearIndependenceWars(org);
        } else {
            writeIndependenceWars(org, wars);
        }

        return new IndependenceWarResolutionResult(org, state, outcome);
    }

    private void applyOccupationCyclePoints(WDEorg org, IndependenceWarState state, Map<String, Map<String, int[]>> census) {
        Set<String> defenders = Set.of(state.getRebelNationName());
        Set<String> attackers = new LinkedHashSet<>(org.getMemberNationNames());
        attackers.removeIf(n -> n.equalsIgnoreCase(state.getRebelNationName()));
        MilitaryWarState cycle = new MilitaryWarState(org.getChairmanNationName(), state.getRebelNationName(),
                attackers, defenders, state.startedAt);
        scoreRegularOccupation(cycle, census);
        state.addRebelScore(cycle.defenderScore);
        state.addEmpireScore(cycle.attackerScore);
        cycle.attackerOccupation.forEach((key, value) -> state.empireOccupation.merge(key, value, Double::sum));
        cycle.defenderOccupation.forEach((key, value) -> state.rebelOccupation.merge(key, value, Double::sum));
    }

    private Map<String, Map<String, int[]>> buildOccupationCensus() {
        Map<String, Map<String, int[]>> census = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Town home = TownyAPI.getInstance().getTown(player);
            Town current = TownyAPI.getInstance().getTown(player.getLocation());
            if (home == null || current == null) continue;
            Map<String, int[]> counts = census.computeIfAbsent(current.getName(), key -> new LinkedHashMap<>());
            List<String> identities = new ArrayList<>();
            identities.add("town:" + home.getName());
            if (home.getNationOrNull() != null) identities.add(home.getNationOrNull().getName());
            for (String identity : identities) {
                int[] count = counts.computeIfAbsent(identity, key -> new int[2]);
                count[0]++;
                if (!isRespawnLocked(player.getUniqueId())) count[1]++;
            }
        }
        return census;
    }

    public Map<String, Double> getNationOccupationWeights(String nationName) {
        Map<String, Integer> population = new LinkedHashMap<>();
        if (nationName.startsWith("town:")) {
            Town town = TownyAPI.getInstance().getTown(nationName.substring(5));
            if (town != null) population.put(town.getName(), town.getResidents().size());
            return WarOccupation.weights(population, null);
        }
        Nation nation = resolveTownyNation(nationName);
        if (nation == null) return Map.of();
        for (Town town : nation.getTowns()) population.put(town.getName(), town.getResidents().size());
        return WarOccupation.weights(population, nation.getCapital() == null ? null : nation.getCapital().getName());
    }

    private void scoreRegularOccupation(MilitaryWarState war, Map<String, Map<String, int[]>> census) {
        int defenderNations = Math.max(1, war.defenderNationNames.size());
        int attackerNations = Math.max(1, war.attackerNationNames.size());
        for (String owner : war.defenderNationNames) {
            for (var town : getNationOccupationWeights(owner).entrySet()) {
                double share = occupancyShare(town.getKey(), war, census);
                double attackPoints = town.getValue() * share;
                double defendPoints = town.getValue() * (1D - share);
                war.attackerOccupation.merge(owner, attackPoints, Double::sum);
                war.defenderOccupation.merge(owner, defendPoints, Double::sum);
                war.attackerScore += attackPoints / defenderNations;
                war.defenderScore += defendPoints / defenderNations;
            }
        }
        for (String owner : war.attackerNationNames) {
            for (var town : getNationOccupationWeights(owner).entrySet()) {
                // On attacker territory, an empty town earns neither side points.
                if (countOccupiers(town.getKey(), war.defenderNationNames, census, true) == 0) continue;
                double defendPoints = town.getValue() * (1D - occupancyShare(town.getKey(), war, census));
                war.defenderOccupation.merge(owner, defendPoints, Double::sum);
                war.defenderScore += defendPoints / attackerNations;
            }
        }
    }

    private double occupancyShare(String town, MilitaryWarState war, Map<String, Map<String, int[]>> census) {
        return WarOccupation.attackerShare(countOccupiers(town, war.attackerNationNames, census, false),
                countOccupiers(town, war.defenderNationNames, census, true));
    }

    private int countOccupiers(String town, Set<String> side, Map<String, Map<String, int[]>> census, boolean defenders) {
        int total = 0;
        for (var entry : census.getOrDefault(town, Map.of()).entrySet()) {
            if (side.stream().anyMatch(n -> n.equalsIgnoreCase(entry.getKey()))) total += entry.getValue()[defenders ? 1 : 0];
        }
        return total;
    }

    public boolean isWarDefender(Player player) {
        Town home = TownyAPI.getInstance().getTown(player);
        if (home == null) return false;
        String nation = home.getNationOrNull() == null ? null : home.getNationOrNull().getName();
        for (MilitaryWarState war : readActiveMilitaryWars(null).values()) {
            if (war.isDefenderNation(nation) || war.isDefenderNation("town:" + home.getName())) return true;
        }
        for (WDEorg org : getAllOrganizations()) for (IndependenceWarState war : getActiveIndependenceWars(org)) {
            if (war.getRebelNationName().equalsIgnoreCase(nation)) return true;
        }
        return false;
    }

    public boolean validWarReason(WarReason reason, String target) {
        if (reason == null || reason == WarReason.INDEPENDENCE) return false;
        return reason != WarReason.DESTROY_THREAT || getAllOrganizations().stream()
                .anyMatch(org -> (isMilitaryAlliance(org) || org.isSovereignEmpire())
                        && (org.hasMemberNation(target) || org.getOrganizationName().equals(target)));
    }

    private void announceIndependenceWarResolution(WDEorg org, IndependenceWarResolutionResult result) {
        String messageKey = switch (result.getOutcome()) {
            case SUCCESS -> "org_independence_war_success";
            case FAILURE -> "org_independence_war_failure";
            case STALEMATE -> "org_independence_war_stalemate";
        };
        String message = msg(messageKey,
                result.getState().getRebelNationName(),
                org.getOrganizationName(),
                formatWarScore(result.getState().getRebelScore()),
                formatWarScore(result.getState().getEmpireScore()));

        plugin.getLogger().info(message);
        Bukkit.broadcastMessage(message);
    }

    private void broadcastMilitaryWarStarted(MilitaryWarState war) {
        if (war == null) {
            return;
        }
        String key = war.getAttackerNationName().startsWith("town:") || war.getDefenderNationName().startsWith("town:")
                ? "war_broadcast_town_started"
                : "war_broadcast_started";
        broadcastServerMessage(key,
                displayWarParty(war.getAttackerNationName()),
                displayWarParty(war.getDefenderNationName()),
                msg(war.getReason().messageKey()));
    }

    private void broadcastMilitaryWarEnded(MilitaryWarState war) {
        if (war == null) {
            return;
        }
        String key = war.getAttackerNationName().startsWith("town:") || war.getDefenderNationName().startsWith("town:")
                ? "war_broadcast_town_ended"
                : "war_broadcast_ended";
        broadcastServerMessage(key,
                displayWarParty(war.getAttackerNationName()),
                displayWarParty(war.getDefenderNationName()));
    }

    private String displayWarParty(String value) {
        if (value == null) {
            return "";
        }
        return value.startsWith("town:") ? value.substring(5) : value;
    }

    private void broadcastServerMessage(String key, Object... args) {
        Bukkit.broadcastMessage(msg(key, args));
    }

    private String msg(String key, Object... args) {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).formatLocaleMessage(key, args);
    }

    private String formatWarScore(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    public boolean isNationInActiveWar(String nationName) {
        if (nationName == null || nationName.isBlank()) return false;
        Nation nation = resolveTownyNation(nationName);
        if (nation != null && nation.hasActiveWar()) return true;
        // Inspect raw participation, not hostility, which may be filtered by neutrality.
        for (MilitaryWarState war : getActiveMilitaryWars(null)) {
                if (war.isAttackerNation(nationName) || war.isDefenderNation(nationName)) return true;
        }
        return isNationInActiveIndependenceWar(nationName);
    }

    public boolean isNationInActiveIndependenceWar(String nationName) {
        if (nationName == null) {
            return false;
        }

        for (WDEorg org : getAllOrganizations()) {
            if (!org.isSovereignEmpire()) {
                continue;
            }

            for (IndependenceWarState state : getActiveIndependenceWars(org)) {
                if (state.getRebelNationName().equalsIgnoreCase(nationName) || org.hasMemberNation(nationName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void registerRespawnLock(UUID playerId) {
        if (playerId == null) {
            return;
        }
        independenceWarRespawnLocks.put(playerId, System.currentTimeMillis() + (getIndependenceWarRespawnLockSeconds() * 1000L));
    }

    public boolean isRespawnLocked(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        Long expiresAt = independenceWarRespawnLocks.get(playerId);
        if (expiresAt == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expiresAt) {
            independenceWarRespawnLocks.remove(playerId);
            return false;
        }
        return true;
    }

    public long getRespawnLockRemainingSeconds(UUID playerId) {
        if (!isRespawnLocked(playerId)) {
            return 0L;
        }

        Long expiresAt = independenceWarRespawnLocks.get(playerId);
        if (expiresAt == null) {
            return 0L;
        }
        return Math.max(1L, (expiresAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    /**
     * 定义独立战争到期或主动结算时的成功、失败和僵持结果。
     */
    public enum IndependenceWarOutcome {
        SUCCESS,
        FAILURE,
        STALEMATE
    }

    /**
     * 返回独立战争结算所涉及的帝国、战争状态和最终结果。
     */
    public static class IndependenceWarResolutionResult {
        private final WDEorg organization;
        private final IndependenceWarState state;
        private final IndependenceWarOutcome outcome;

        public IndependenceWarResolutionResult(WDEorg organization, IndependenceWarState state, IndependenceWarOutcome outcome) {
            this.organization = organization;
            this.state = state;
            this.outcome = outcome;
        }

        public WDEorg getOrganization() {
            return organization;
        }

        public IndependenceWarState getState() {
            return state;
        }

        public IndependenceWarOutcome getOutcome() {
            return outcome;
        }
    }

    private SovereignSubjectState getSubjectState(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return SovereignSubjectState.defaultState(getDefaultSubjectSuppression());
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key != null) {
            return states.get(key);
        }

        SovereignSubjectState defaultState = SovereignSubjectState.defaultState(getDefaultSubjectSuppression());
        states.put(nationName, defaultState);
        writeSovereignSubjectStates(org, states);
        return defaultState;
    }

    private boolean synchronizeSovereignSubjectStates(WDEorg org) {
        if (org == null || !org.isSovereignEmpire()) {
            return false;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        boolean changed = false;

        int originalSize = states.size();
        states.entrySet().removeIf(entry -> {
            String nationName = entry.getKey();
            return nationName == null
                    || !org.hasMemberNation(nationName)
                    || (org.getChairmanNationName() != null && org.getChairmanNationName().equalsIgnoreCase(nationName));
        });
        if (states.size() != originalSize) {
            changed = true;
        }

        for (String memberNation : org.getMemberNationNames()) {
            if (!isSovereignSubjectNation(org, memberNation)) {
                continue;
            }

            if (findSubjectStateKey(states, memberNation) == null) {
                states.put(memberNation, SovereignSubjectState.defaultState(getDefaultSubjectSuppression()));
                changed = true;
            }
        }

        if (changed) {
            writeSovereignSubjectStates(org, states);
        }
        return changed;
    }

    private void initializeSovereignSubjectStateIfNeeded(WDEorg org, String nationName) {
        if (!isSovereignSubjectNation(org, nationName)) {
            return;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        if (findSubjectStateKey(states, nationName) != null) {
            return;
        }
        states.put(nationName, SovereignSubjectState.defaultState(getDefaultSubjectSuppression()));
        writeSovereignSubjectStates(org, states);
    }

    private void removeSovereignSubjectState(WDEorg org, String nationName) {
        if (org == null || nationName == null || !org.isSovereignEmpire()) {
            return;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        String key = findSubjectStateKey(states, nationName);
        if (key == null) {
            return;
        }

        states.remove(key);
        writeSovereignSubjectStates(org, states);
    }

    private Map<String, SovereignSubjectState> readSovereignSubjectStates(WDEorg org) {
        Map<String, SovereignSubjectState> states = new LinkedHashMap<>();
        if (org == null) {
            return states;
        }

        Object rawStates = org.getSetting(SUBJECT_STATES_SETTING);
        if (!(rawStates instanceof Map<?, ?> rawMap)) {
            return states;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String nationName = String.valueOf(entry.getKey());
            SovereignSubjectState state;
            if (entry.getValue() instanceof Map<?, ?> stateMap) {
                state = SovereignSubjectState.fromMap(stateMap, getDefaultSubjectSuppression());
            } else {
                state = SovereignSubjectState.defaultState(getDefaultSubjectSuppression());
            }
            states.put(nationName, state);
        }
        return states;
    }

    private void writeSovereignSubjectStates(WDEorg org, Map<String, SovereignSubjectState> states) {
        if (org == null) {
            return;
        }

        if (states == null || states.isEmpty()) {
            org.getSettings().remove(SUBJECT_STATES_SETTING);
            return;
        }

        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, SovereignSubjectState> entry : states.entrySet()) {
            serialized.put(entry.getKey(), entry.getValue().toMap());
        }
        org.setSetting(SUBJECT_STATES_SETTING, serialized);
    }

    private String findSubjectStateKey(Map<String, SovereignSubjectState> states, String nationName) {
        for (String key : states.keySet()) {
            if (key != null && key.equalsIgnoreCase(nationName)) {
                return key;
            }
        }
        return null;
    }

    private int clampSuppression(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * 保存独立战争双方、累计成绩和周期状态，支持跨重启继续结算。
     */
    public static class IndependenceWarState {
        private final Map<String, Double> rebelOccupation = new LinkedHashMap<>();
        private final Map<String, Double> empireOccupation = new LinkedHashMap<>();
        private final Map<String, Double> rebelKills = new LinkedHashMap<>();
        private final Map<String, Double> empireKills = new LinkedHashMap<>();
        private final String rebelNationName;
        private double rebelScore;
        private double empireScore;
        private int elapsedCycles;
        private boolean chairmanSurrendered;
        private final long startedAt;

        public IndependenceWarState(String rebelNationName) {
            this(rebelNationName, 0D, 0D, 0, false, System.currentTimeMillis());
        }

        public IndependenceWarState(String rebelNationName, double rebelScore, double empireScore, int elapsedCycles, boolean chairmanSurrendered, long startedAt) {
            this.rebelNationName = rebelNationName;
            this.rebelScore = rebelScore;
            this.empireScore = empireScore;
            this.elapsedCycles = elapsedCycles;
            this.chairmanSurrendered = chairmanSurrendered;
            this.startedAt = startedAt;
        }

        public static IndependenceWarState fromMap(String rebelNationName, Map<?, ?> map) {
            double rebelScore = readDouble(map.get("rebelScore"));
            double empireScore = readDouble(map.get("empireScore"));
            int elapsedCycles = readInt(map.get("elapsedCycles"));
            boolean chairmanSurrendered = readBoolean(map.get("chairmanSurrendered"));
            long startedAt = readLong(map.get("startedAt"), System.currentTimeMillis());
            IndependenceWarState state = new IndependenceWarState(rebelNationName, rebelScore, empireScore, elapsedCycles, chairmanSurrendered, startedAt);
            MilitaryWarState.readScoreMap(map.get("rebelOccupation"), state.rebelOccupation);
            MilitaryWarState.readScoreMap(map.get("empireOccupation"), state.empireOccupation);
            MilitaryWarState.readScoreMap(map.get("rebelKills"), state.rebelKills);
            MilitaryWarState.readScoreMap(map.get("empireKills"), state.empireKills);
            return state;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rebelScore", rebelScore);
            map.put("reason", WarReason.INDEPENDENCE.name());
            map.put("rebelOccupation", new LinkedHashMap<>(rebelOccupation));
            map.put("empireOccupation", new LinkedHashMap<>(empireOccupation));
            map.put("rebelKills", new LinkedHashMap<>(rebelKills));
            map.put("empireKills", new LinkedHashMap<>(empireKills));
            map.put("empireScore", empireScore);
            map.put("elapsedCycles", elapsedCycles);
            map.put("chairmanSurrendered", chairmanSurrendered);
            map.put("startedAt", startedAt);
            return map;
        }

        public String getRebelNationName() {
            return rebelNationName;
        }

        public double getRebelScore() {
            return rebelScore;
        }

        public double getEmpireScore() {
            return empireScore;
        }

        public int getElapsedCycles() {
            return elapsedCycles;
        }

        public void incrementElapsedCycles() {
            this.elapsedCycles++;
        }

        public void addRebelScore(double delta) {
            this.rebelScore += delta;
        }

        public void addEmpireScore(double delta) {
            this.empireScore += delta;
        }

        public void setChairmanSurrendered(boolean chairmanSurrendered) {
            this.chairmanSurrendered = chairmanSurrendered;
        }

        public boolean isChairmanSurrendered() {
            return chairmanSurrendered;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public Map<String, Double> getRebelOccupation() {
            return new LinkedHashMap<>(rebelOccupation);
        }

        public Map<String, Double> getEmpireOccupation() {
            return new LinkedHashMap<>(empireOccupation);
        }

        public Map<String, Double> getRebelKills() {
            return new LinkedHashMap<>(rebelKills);
        }

        public Map<String, Double> getEmpireKills() {
            return new LinkedHashMap<>(empireKills);
        }

        private static int readInt(Object value) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
        }

        private static double readDouble(Object value) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
        }

        private static boolean readBoolean(Object value) {
            return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
        }

        private static long readLong(Object value, long fallback) {
            if (value == null) {
                return fallback;
            }
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
        }
    }

    /**
     * 保存待投票的宣战理由、目标、发起国及成员国表决。
     */
    public static class MilitaryWarProposalState {
        private WarReason reason = WarReason.CONQUEST;
        private final String targetNationName;
        private final String proposerNationName;
        private final Map<String, Boolean> votes;
        private final long createdAt;

        public MilitaryWarProposalState(String targetNationName, String proposerNationName) {
            this(targetNationName, proposerNationName, new LinkedHashMap<>(), System.currentTimeMillis());
        }

        public MilitaryWarProposalState(String targetNationName, String proposerNationName, Map<String, Boolean> votes, long createdAt) {
            this.targetNationName = targetNationName;
            this.proposerNationName = proposerNationName;
            this.votes = votes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(votes);
            this.createdAt = createdAt;
        }

        public static MilitaryWarProposalState fromMap(String targetNationName, Map<?, ?> map) {
            Object rawProposerNationName = map.containsKey("proposerNationName") ? map.get("proposerNationName") : "None";
            String proposerNationName = String.valueOf(rawProposerNationName);
            Map<String, Boolean> votes = new LinkedHashMap<>();
            Object rawVotes = map.get("votes");
            if (rawVotes instanceof Map<?, ?> voteMap) {
                for (Map.Entry<?, ?> entry : voteMap.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        Object value = entry.getValue();
                        boolean parsed = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
                        votes.put(String.valueOf(entry.getKey()), parsed);
                    }
                }
            }
            long createdAt = map.get("createdAt") instanceof Number number ? number.longValue() : System.currentTimeMillis();
            MilitaryWarProposalState state = new MilitaryWarProposalState(targetNationName, proposerNationName, votes, createdAt);
            WarReason reason = WarReason.parse(String.valueOf(map.get("reason")));
            if (reason != null) state.reason = reason;
            return state;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("proposerNationName", proposerNationName);
            map.put("votes", new LinkedHashMap<>(votes));
            map.put("createdAt", createdAt);
            map.put("reason", reason.name());
            return map;
        }

        public WarReason getReason() { return reason; }

        public String getTargetNationName() {
            return targetNationName;
        }

        public String getProposerNationName() {
            return proposerNationName;
        }

        public Map<String, Boolean> getVotes() {
            return votes;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 封装宣战投票的处理状态及通过后可创建的战争结果。
     */
    public static class MilitaryWarVoteResult {
        /**
         * 区分宣战表决的待定、通过、拒绝及无效等处理状态。
         */
        public enum Status {
            INVALID,
            ALREADY_PENDING,
            NOT_FOUND,
            PEACE_PROTECTED,
            ACTIVE_WAR,
            TRUCE,
            PENDING,
            APPROVED,
            REJECTED
        }

        private final Status status;
        private final MilitaryWarProposalState proposal;
        private final MilitaryWarResult warResult;

        private MilitaryWarVoteResult(Status status, MilitaryWarProposalState proposal, MilitaryWarResult warResult) {
            this.status = status;
            this.proposal = proposal;
            this.warResult = warResult;
        }

        public static MilitaryWarVoteResult invalid() {
            return new MilitaryWarVoteResult(Status.INVALID, null, null);
        }

        public static MilitaryWarVoteResult alreadyPending() {
            return new MilitaryWarVoteResult(Status.ALREADY_PENDING, null, null);
        }

        public static MilitaryWarVoteResult notFound() {
            return new MilitaryWarVoteResult(Status.NOT_FOUND, null, null);
        }

        public static MilitaryWarVoteResult peaceProtected() {
            return new MilitaryWarVoteResult(Status.PEACE_PROTECTED, null, null);
        }

        public static MilitaryWarVoteResult peaceProtected(MilitaryWarProposalState proposal) {
            return new MilitaryWarVoteResult(Status.PEACE_PROTECTED, proposal, null);
        }

        public static MilitaryWarVoteResult activeWar() {
            return new MilitaryWarVoteResult(Status.ACTIVE_WAR, null, null);
        }

        public static MilitaryWarVoteResult truce() {
            return new MilitaryWarVoteResult(Status.TRUCE, null, null);
        }

        public static MilitaryWarVoteResult pending(MilitaryWarProposalState proposal) {
            return new MilitaryWarVoteResult(Status.PENDING, proposal, null);
        }

        public static MilitaryWarVoteResult approved(MilitaryWarResult warResult, MilitaryWarProposalState proposal) {
            return new MilitaryWarVoteResult(Status.APPROVED, proposal, warResult);
        }

        public static MilitaryWarVoteResult rejected(MilitaryWarProposalState proposal) {
            return new MilitaryWarVoteResult(Status.REJECTED, proposal, null);
        }

        public Status getStatus() {
            return status;
        }

        public MilitaryWarProposalState getProposal() {
            return proposal;
        }

        public MilitaryWarResult getWarResult() {
            return warResult;
        }
    }

    /**
     * 封装已确定的战争双方及实际参战国家集合。
     */
    public static class MilitaryWarResult {
        private boolean attackerAllianceInvolved;
        private boolean defenderAllianceInvolved;
        private int hostilePairsApplied;
        private final List<String> removedFromAttackerAlliances = new ArrayList<>();

        public boolean isAttackerAllianceInvolved() {
            return attackerAllianceInvolved;
        }

        public boolean isDefenderAllianceInvolved() {
            return defenderAllianceInvolved;
        }

        public int getHostilePairsApplied() {
            return hostilePairsApplied;
        }

        public List<String> getRemovedFromAttackerAlliances() {
            return removedFromAttackerAlliances;
        }

        public boolean hasAllianceEffect() {
            return attackerAllianceInvolved || defenderAllianceInvolved;
        }
    }

    /**
     * 保存常规战争参与方、累计占领和击杀分账，以及退出成员等长期状态。
     */
    public static class MilitaryWarState {
        private WarReason reason = WarReason.CONQUEST;
        private final Map<String, Double> attackerOccupation = new LinkedHashMap<>();
        private final Map<String, Double> defenderOccupation = new LinkedHashMap<>();
        private final Map<String, Double> attackerKills = new LinkedHashMap<>();
        private final Map<String, Double> defenderKills = new LinkedHashMap<>();
        private final String warKey;
        private final String attackerLeaderNationName;
        private final String defenderLeaderNationName;
        private final Set<String> attackerNationNames;
        private final Set<String> defenderNationNames;
        private double attackerScore;
        private double defenderScore;
        private final long startedAt;

        public MilitaryWarState(String attackerLeaderNationName,
                                String defenderLeaderNationName,
                                Collection<String> attackerNationNames,
                                Collection<String> defenderNationNames,
                                long startedAt) {
            this(attackerLeaderNationName, defenderLeaderNationName, attackerNationNames, defenderNationNames, 0D, 0D, startedAt);
        }

        public MilitaryWarState(String attackerLeaderNationName,
                                String defenderLeaderNationName,
                                Collection<String> attackerNationNames,
                                Collection<String> defenderNationNames,
                                double attackerScore,
                                double defenderScore,
                                long startedAt) {
            this.warKey = buildWarKey(attackerLeaderNationName, defenderLeaderNationName);
            this.attackerLeaderNationName = attackerLeaderNationName;
            this.defenderLeaderNationName = defenderLeaderNationName;
            this.attackerNationNames = toNationSet(attackerNationNames, attackerLeaderNationName);
            this.defenderNationNames = toNationSet(defenderNationNames, defenderLeaderNationName);
            this.attackerScore = attackerScore;
            this.defenderScore = defenderScore;
            this.startedAt = startedAt;
        }

        public static MilitaryWarState fromMap(String warKey, Map<?, ?> map) {
            Object rawAttackerNationName = map.containsKey("attackerLeaderNationName")
                    ? map.get("attackerLeaderNationName")
                    : (map.containsKey("attackerNationName") ? map.get("attackerNationName") : "");
            Object rawDefenderNationName = map.containsKey("defenderLeaderNationName")
                    ? map.get("defenderLeaderNationName")
                    : (map.containsKey("defenderNationName") ? map.get("defenderNationName") : "");
            String attackerNationName = String.valueOf(rawAttackerNationName);
            String defenderNationName = String.valueOf(rawDefenderNationName);
            double attackerScore = readDoubleValue(map.get("attackerScore"));
            double defenderScore = readDoubleValue(map.get("defenderScore"));
            long startedAt = map.get("startedAt") instanceof Number number ? number.longValue() : System.currentTimeMillis();
            MilitaryWarState state = new MilitaryWarState(
                    attackerNationName.isBlank() ? parseWarKeyPart(warKey, 1) : attackerNationName,
                    defenderNationName.isBlank() ? parseWarKeyPart(warKey, 2) : defenderNationName,
                    readNationCollection(map.get("attackerNationNames"), attackerNationName),
                    readNationCollection(map.get("defenderNationNames"), defenderNationName),
                    attackerScore,
                    defenderScore,
                    startedAt);
            WarReason reason = WarReason.parse(String.valueOf(map.get("reason")));
            if (reason != null) state.reason = reason;
            readScoreMap(map.get("attackerOccupation"), state.attackerOccupation);
            readScoreMap(map.get("defenderOccupation"), state.defenderOccupation);
            readScoreMap(map.get("attackerKills"), state.attackerKills);
            readScoreMap(map.get("defenderKills"), state.defenderKills);
            return state;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("attackerLeaderNationName", attackerLeaderNationName);
            map.put("defenderLeaderNationName", defenderLeaderNationName);
            map.put("attackerNationNames", new ArrayList<>(attackerNationNames));
            map.put("defenderNationNames", new ArrayList<>(defenderNationNames));
            map.put("attackerScore", attackerScore);
            map.put("defenderScore", defenderScore);
            map.put("startedAt", startedAt);
            map.put("reason", reason.name());
            map.put("attackerOccupation", new LinkedHashMap<>(attackerOccupation));
            map.put("defenderOccupation", new LinkedHashMap<>(defenderOccupation));
            map.put("attackerKills", new LinkedHashMap<>(attackerKills));
            map.put("defenderKills", new LinkedHashMap<>(defenderKills));
            return map;
        }

        public WarReason getReason() { return reason; }

        private static void readScoreMap(Object raw, Map<String, Double> scores) {
            if (raw instanceof Map<?, ?> map) map.forEach((key, value) -> {
                if (value instanceof Number number && Double.isFinite(number.doubleValue()))
                    scores.put(String.valueOf(key), number.doubleValue());
            });
        }

        public double localizedDifference(String beneficiary, String conceding) {
            boolean attacker = isAttackerNation(beneficiary);
            String attackerNation = attacker ? beneficiary : conceding;
            String defenderNation = attacker ? conceding : beneficiary;
            double attack = attackerOccupation.getOrDefault(defenderNation, 0D)
                    + attackerKills.getOrDefault(defenderNation, 0D);
            double defense = defenderOccupation.getOrDefault(defenderNation, 0D)
                    + defenderOccupation.getOrDefault(attackerNation, 0D)
                    + defenderKills.getOrDefault(attackerNation, 0D);
            return attacker ? attack - defense : defense - attack;
        }

        public String getWarKey() {
            return warKey;
        }

        public String getAttackerNationName() {
            return attackerLeaderNationName;
        }

        public String getDefenderNationName() {
            return defenderLeaderNationName;
        }

        public Set<String> getAttackerNationNames() {
            return new LinkedHashSet<>(attackerNationNames);
        }

        public Set<String> getDefenderNationNames() {
            return new LinkedHashSet<>(defenderNationNames);
        }

        public long getStartedAt() {
            return startedAt;
        }

        public double getAttackerScore() {
            return attackerScore;
        }

        public double getDefenderScore() {
            return defenderScore;
        }

        public Map<String, Double> getAttackerOccupation() {
            return new LinkedHashMap<>(attackerOccupation);
        }

        public Map<String, Double> getDefenderOccupation() {
            return new LinkedHashMap<>(defenderOccupation);
        }

        public Map<String, Double> getAttackerKills() {
            return new LinkedHashMap<>(attackerKills);
        }

        public Map<String, Double> getDefenderKills() {
            return new LinkedHashMap<>(defenderKills);
        }

        public boolean isWarLeader(String nationName) {
            return nationName != null && (attackerLeaderNationName.equalsIgnoreCase(nationName)
                    || defenderLeaderNationName.equalsIgnoreCase(nationName));
        }

        public boolean isAttackerNation(String nationName) {
            return containsIgnoreCase(attackerNationNames, nationName);
        }

        public boolean isDefenderNation(String nationName) {
            return containsIgnoreCase(defenderNationNames, nationName);
        }

        public boolean containsOpposingNations(String firstNationName, String secondNationName) {
            return containsIgnoreCase(attackerNationNames, firstNationName) && containsIgnoreCase(defenderNationNames, secondNationName)
                    || containsIgnoreCase(attackerNationNames, secondNationName) && containsIgnoreCase(defenderNationNames, firstNationName);
        }

        public boolean removeParticipant(String nationName) {
            if (nationName == null || nationName.isBlank()) {
                return false;
            }
            if (attackerLeaderNationName.equalsIgnoreCase(nationName) || defenderLeaderNationName.equalsIgnoreCase(nationName)) {
                return false;
            }
            return removeIgnoreCase(attackerNationNames, nationName) || removeIgnoreCase(defenderNationNames, nationName);
        }

        public boolean hasMinimumParticipants() {
            return !attackerNationNames.isEmpty() && !defenderNationNames.isEmpty();
        }

        public boolean addKillScore(String killerNationName, String victimNationName, double delta) {
            if (delta <= 0D || killerNationName == null || victimNationName == null) {
                return false;
            }
            if (isAttackerNation(killerNationName) && isDefenderNation(victimNationName)) {
                attackerScore += delta;
                attackerKills.merge(victimNationName, delta, Double::sum);
                return true;
            }
            if (isDefenderNation(killerNationName) && isAttackerNation(victimNationName)) {
                defenderScore += delta;
                defenderKills.merge(victimNationName, delta, Double::sum);
                return true;
            }
            return false;
        }

        private static Set<String> toNationSet(Collection<String> values, String fallbackNation) {
            Set<String> nations = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        nations.add(value);
                    }
                }
            }
            if ((fallbackNation != null && !fallbackNation.isBlank())
                    && nations.stream().noneMatch(existing -> existing.equalsIgnoreCase(fallbackNation))) {
                nations.add(fallbackNation);
            }
            return nations;
        }

        private static Collection<String> readNationCollection(Object rawValue, String fallbackNation) {
            List<String> values = new ArrayList<>();
            if (rawValue instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    if (entry != null) {
                        values.add(String.valueOf(entry));
                    }
                }
            }
            if (values.isEmpty() && fallbackNation != null && !fallbackNation.isBlank()) {
                values.add(fallbackNation);
            }
            return values;
        }

        private static double readDoubleValue(Object rawValue) {
            if (rawValue == null) {
                return 0D;
            }
            return rawValue instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(rawValue));
        }

        private static boolean containsIgnoreCase(Collection<String> values, String target) {
            if (target == null || values == null) {
                return false;
            }
            for (String value : values) {
                if (value != null && value.equalsIgnoreCase(target)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean removeIgnoreCase(Collection<String> values, String target) {
            if (target == null || values == null) {
                return false;
            }

            String matched = null;
            for (String value : values) {
                if (value != null && value.equalsIgnoreCase(target)) {
                    matched = value;
                    break;
                }
            }
            return matched != null && values.remove(matched);
        }
    }

    /**
     * 保存单条和约类型、参数和成本，供提案校验与条款执行使用。
     */
    public static class PeaceTreatyClauseState {
        /**
         * 定义可执行的和平条款类型，其对象与使用限制由业务校验处理。
         */
        public enum ClauseType {
            REPARATIONS,
            FORCE_CONVERSION,
            TRANSFER_TOWN,
            DISSOLVE_ALLIANCE,
            EXPEL_FROM_ALLIANCE,
            LIBERATE_SUBJECT,
            UNCONDITIONAL_PASSAGE,
            HUMILIATE,
            LOOT_CAPITAL,
            TRIBUTE,
            ALLOW_INDEPENDENCE;

            public static ClauseType fromString(String rawValue) {
                if (rawValue == null) {
                    return null;
                }
                return switch (rawValue.trim().toLowerCase()) {
                    case "reparations", "赔款" -> REPARATIONS;
                    case "forceconversion", "force_convert", "强制改信" -> FORCE_CONVERSION;
                    case "transfertown", "transfer_town", "转移城镇" -> TRANSFER_TOWN;
                    case "dissolvealliance", "dissolve_alliance", "解散同盟" -> DISSOLVE_ALLIANCE;
                    case "expelfromalliance", "expel_from_alliance", "kickalliance", "强制踢出联盟" -> EXPEL_FROM_ALLIANCE;
                    case "liberatesubject", "liberate_subject", "解放仆从国" -> LIBERATE_SUBJECT;
                    case "unconditionalpassage", "unconditional_passage", "passage", "无条件通行权" -> UNCONDITIONAL_PASSAGE;
                    case "humiliate", "羞辱" -> HUMILIATE;
                    case "lootcapital", "loot_capital", "劫掠首都" -> LOOT_CAPITAL;
                    case "tribute", "贡金" -> TRIBUTE;
                    case "allowindependence", "allow_independence", "grantindependence", "允许独立" -> ALLOW_INDEPENDENCE;
                    default -> null;
                };
            }
        }

        private final ClauseType type;
        private final Map<String, Object> parameters;

        public PeaceTreatyClauseState(ClauseType type, Map<String, Object> parameters) {
            this.type = type;
            this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        }

        public static PeaceTreatyClauseState fromMap(Map<?, ?> map) {
            ClauseType type = ClauseType.fromString(String.valueOf(map.get("type")));
            Map<String, Object> parameters = new LinkedHashMap<>();
            Object rawParameters = map.get("parameters");
            if (rawParameters instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        parameters.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            }
            return new PeaceTreatyClauseState(type, parameters);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type == null ? "" : type.name());
            map.put("parameters", new LinkedHashMap<>(parameters));
            return map;
        }

        public ClauseType getType() {
            return type;
        }

        public String getString(String key) {
            Object value = parameters.get(key);
            return value == null ? "" : String.valueOf(value);
        }

        public int getInt(String key) {
            Object value = parameters.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return value == null ? 0 : Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    /**
     * 保存和约提出方、受理方、方向、条款及分数预算。
     */
    public static class PeaceTreatyProposalState {
        /**
         * 定义提出要求或主动让步的提案方向，用于确定受益方和让步方。
         */
        public enum ProposalType {
            DEMAND,
            SURRENDER;

            public static ProposalType fromString(String rawValue) {
                if (rawValue == null) {
                    return null;
                }
                return switch (rawValue.trim().toLowerCase()) {
                    case "demand", "persuade", "劝降" -> DEMAND;
                    case "surrender", "投降" -> SURRENDER;
                    default -> null;
                };
            }
        }

        private final String warKey;
        private final ProposalType proposalType;
        private final String proposerNationName;
        private final String targetNationName;
        private final int totalPoints;
        private final List<PeaceTreatyClauseState> clauses;
        private final long createdAt;

        public PeaceTreatyProposalState(String warKey,
                                        ProposalType proposalType,
                                        String proposerNationName,
                                        String targetNationName,
                                        int totalPoints,
                                        List<PeaceTreatyClauseState> clauses,
                                        long createdAt) {
            this.warKey = warKey;
            this.proposalType = proposalType;
            this.proposerNationName = proposerNationName;
            this.targetNationName = targetNationName;
            this.totalPoints = totalPoints;
            this.clauses = clauses == null ? new ArrayList<>() : new ArrayList<>(clauses);
            this.createdAt = createdAt;
        }

        public static PeaceTreatyProposalState fromMap(String warKey, Map<?, ?> map) {
            ProposalType proposalType = ProposalType.fromString(String.valueOf(map.get("proposalType")));
            Object rawProposerNationName = map.containsKey("proposerNationName") ? map.get("proposerNationName") : "";
            Object rawTargetNationName = map.containsKey("targetNationName") ? map.get("targetNationName") : "";
            String proposerNationName = String.valueOf(rawProposerNationName);
            String targetNationName = String.valueOf(rawTargetNationName);
            int totalPoints = map.get("totalPoints") instanceof Number number ? number.intValue() : 0;
            List<PeaceTreatyClauseState> clauses = new ArrayList<>();
            Object rawClauses = map.get("clauses");
            if (rawClauses instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    if (entry instanceof Map<?, ?> clauseMap) {
                        clauses.add(PeaceTreatyClauseState.fromMap(clauseMap));
                    }
                }
            }
            long createdAt = map.get("createdAt") instanceof Number number ? number.longValue() : System.currentTimeMillis();
            return new PeaceTreatyProposalState(warKey, proposalType, proposerNationName, targetNationName, totalPoints, clauses, createdAt);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("proposalType", proposalType == null ? "" : proposalType.name());
            map.put("proposerNationName", proposerNationName);
            map.put("targetNationName", targetNationName);
            map.put("totalPoints", totalPoints);
            List<Map<String, Object>> serializedClauses = new ArrayList<>();
            for (PeaceTreatyClauseState clause : clauses) {
                serializedClauses.add(clause.toMap());
            }
            map.put("clauses", serializedClauses);
            map.put("createdAt", createdAt);
            return map;
        }

        public String getWarKey() {
            return warKey;
        }

        public ProposalType getProposalType() {
            return proposalType;
        }

        public String getProposerNationName() {
            return proposerNationName;
        }

        public String getTargetNationName() {
            return targetNationName;
        }

        public int getTotalPoints() {
            return totalPoints;
        }

        public List<PeaceTreatyClauseState> getClauses() {
            return clauses;
        }
    }

    /**
     * 封装和约提交成功或被规则拒绝的结果与提示键。
     */
    public static class PeaceTreatyProposalResult {
        /**
         * 区分和约提交成功与前置条件不满足等处理状态。
         */
        public enum Status {
            INVALID,
            PROPOSED,
            FORCED_ACCEPTED
        }

        private final Status status;
        private final String messageKey;
        private final PeaceTreatyProposalState proposal;
        private final double scoreDifference;

        private PeaceTreatyProposalResult(Status status, String messageKey, PeaceTreatyProposalState proposal, double scoreDifference) {
            this.status = status;
            this.messageKey = messageKey;
            this.proposal = proposal;
            this.scoreDifference = scoreDifference;
        }

        public static PeaceTreatyProposalResult proposed(PeaceTreatyProposalState proposal, double scoreDifference) {
            return new PeaceTreatyProposalResult(Status.PROPOSED, null, proposal, scoreDifference);
        }

        public static PeaceTreatyProposalResult forcedAccepted(PeaceTreatyProposalState proposal, double scoreDifference) {
            return new PeaceTreatyProposalResult(Status.FORCED_ACCEPTED, null, proposal, scoreDifference);
        }

        public static PeaceTreatyProposalResult invalid(String messageKey) {
            return new PeaceTreatyProposalResult(Status.INVALID, messageKey, null, 0D);
        }

        public boolean isSuccess() {
            return status != Status.INVALID;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public PeaceTreatyProposalState getProposal() {
            return proposal;
        }

        public double getScoreDifference() {
            return scoreDifference;
        }
    }

    /**
     * 封装和约受理、拒绝或失效的结果及对应提案。
     */
    public static class PeaceTreatyReviewResult {
        /**
         * 区分和约受理完成、拒绝及当前提案不可受理等状态。
         */
        public enum Status {
            INVALID,
            ACCEPTED,
            REJECTED
        }

        private final Status status;
        private final String messageKey;
        private final PeaceTreatyProposalState proposal;

        private PeaceTreatyReviewResult(Status status, String messageKey, PeaceTreatyProposalState proposal) {
            this.status = status;
            this.messageKey = messageKey;
            this.proposal = proposal;
        }

        public static PeaceTreatyReviewResult accepted(PeaceTreatyProposalState proposal) {
            return new PeaceTreatyReviewResult(Status.ACCEPTED, null, proposal);
        }

        public static PeaceTreatyReviewResult rejected(PeaceTreatyProposalState proposal) {
            return new PeaceTreatyReviewResult(Status.REJECTED, null, proposal);
        }

        public static PeaceTreatyReviewResult invalid(String messageKey) {
            return new PeaceTreatyReviewResult(Status.INVALID, messageKey, null);
        }

        public Status getStatus() {
            return status;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public PeaceTreatyProposalState getProposal() {
            return proposal;
        }
    }

    /**
     * 统一描述常规与独立战争的双方视角，供和约校验选择受益方和让步方。
     */
    private static class WarContext {
        /**
         * 区分常规战争与主权帝国独立战争的和约处理上下文。
         */
        enum WarType {
            MILITARY_ALLIANCE,
            SOVEREIGN_INDEPENDENCE
        }

        private final WarType warType;
        private final WDEorg organization;
        private final String warKey;
        private final Set<String> actorSideNations;
        private final Set<String> counterpartySideNations;
        private final MilitaryWarState militaryWarState;
        private final IndependenceWarState independenceWarState;

        private WarContext(WarType warType,
                           WDEorg organization,
                           String warKey,
                           Set<String> actorSideNations,
                           Set<String> counterpartySideNations,
                           MilitaryWarState militaryWarState,
                           IndependenceWarState independenceWarState) {
            this.warType = warType;
            this.organization = organization;
            this.warKey = warKey;
            this.actorSideNations = actorSideNations;
            this.counterpartySideNations = counterpartySideNations;
            this.militaryWarState = militaryWarState;
            this.independenceWarState = independenceWarState;
        }

        static WarContext military(WDEorg organization, MilitaryWarState warState, Set<String> actorSideNations, Set<String> counterpartySideNations) {
            return new WarContext(WarType.MILITARY_ALLIANCE, organization, warState.getWarKey(), actorSideNations, counterpartySideNations, warState, null);
        }

        static WarContext independence(WDEorg organization, IndependenceWarState warState, Set<String> actorSideNations, Set<String> counterpartySideNations) {
            return new WarContext(WarType.SOVEREIGN_INDEPENDENCE, organization, "independence:" + warState.getRebelNationName(), actorSideNations, counterpartySideNations, null, warState);
        }

        public WarType getWarType() {
            return warType;
        }

        public WDEorg getOrganization() {
            return organization;
        }

        public String getWarKey() {
            return warKey;
        }

        public Set<String> getActorSideNations() {
            return actorSideNations;
        }

        public Set<String> getCounterpartySideNations() {
            return counterpartySideNations;
        }

        public String getActorPrimaryNation() {
            return actorSideNations.iterator().next();
        }

        public String getCounterpartyPrimaryNation() {
            return counterpartySideNations.iterator().next();
        }

        public boolean isNationOnActorSide(String nationName) {
            return containsIgnoreCase(actorSideNations, nationName);
        }

        public boolean isNationOnCounterpartySide(String nationName) {
            return containsIgnoreCase(counterpartySideNations, nationName);
        }

        public MilitaryWarState getMilitaryWarState() {
            return militaryWarState;
        }

        public IndependenceWarState getIndependenceWarState() {
            return independenceWarState;
        }

        private boolean containsIgnoreCase(Set<String> values, String target) {
            for (String value : values) {
                if (value != null && value.equalsIgnoreCase(target)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String buildWarKey(String attackerNationName, String defenderNationName) {
        return "military:" + attackerNationName + ":" + defenderNationName;
    }

    private static String buildIndependenceFocusKey(String organizationName, String rebelNationName) {
        return "independence:" + organizationName + ":" + rebelNationName;
    }

    private static List<String> orderedNames(Collection<String> names) {
        List<String> ordered = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    ordered.add(name);
                }
            }
        }
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(ordered);
    }

    private static String parseWarKeyPart(String warKey, int index) {
        if (warKey == null) {
            return "";
        }
        String[] parts = warKey.split(":");
        return parts.length > index ? parts[index] : "";
    }
}
