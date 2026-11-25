package com.thatguycy.worlddynamicsengine.manager.organization;

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
import com.thatguycy.worlddynamicsengine.model.organization.SovereignSubjectState;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalState;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalType;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
 * 管理国家集团及附属关系，并与 WarManager 协调跨域操作。
 */
public class OrgManager {
    private static final String STORAGE_CATEGORY = "organizations";
    private static final String SUBJECT_STATES_SETTING = "sovereignSubjectStates";

    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final DataStorageManager dataStorageManager;
    private final Map<String, WDEorg> organizations;
    private final WarManager warManager;

    public OrgManager(JavaPlugin plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.dataStorageManager = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getDataStorageManager();
        this.organizations = new LinkedHashMap<>();
        this.warManager = new WarManager(plugin, townManager, this, dataStorageManager);
        loadOrganizations();
        warManager.migrateLegacyPeaceTreatyTributes();
    }

    public WarManager getWarManager() {
        return warManager;
    }

    public void loadOrganizations() {
        organizations.clear();
        organizations.putAll(dataStorageManager.loadStringKeyed(STORAGE_CATEGORY, WDEorg.class));
        boolean changed = false;
        for (WDEorg org : organizations.values()) {
            changed |= warManager.migrateOrganizationWars(org);
            changed |= synchronizeSovereignSubjectStates(org);
            changed |= synchronizeIdeologicalAllianceSettings(org);
        }
        changed |= warManager.migrateLegacyIndependenceWarSides();
        if (changed) {
            saveOrganizations();
        }
        plugin.getLogger().info("Total organizations loaded: " + organizations.size());
    }

    public void saveOrganizations() {
        warManager.saveLedger();
        dataStorageManager.saveStringKeyed(STORAGE_CATEGORY, organizations);
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

    WDEorg getPrimaryIdeologicalAllianceForNation(String nationName) {
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

    double getIdeologicalAllianceSameReligionTreatyMultiplier() {
        return Math.max(0D, getOrganizationConfiguration().getDouble(
                "ideologicalAlliance.peaceTreaty.sameReligionClauseCostMultiplier", 1.50D));
    }

    double getIdeologicalAllianceHereticTreatyMultiplier() {
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
                && !warManager.hasActiveIndependenceWar(org, nationName)
                && (state.getAutonomyTier().canPromote() || !warManager.isNationInActiveWar(nationName));
    }

    public boolean peacefullyReleaseDominion(WDEorg org, String nationName) {
        if (!canPromoteSubjectAutonomy(org, nationName)
                || getSubjectAutonomyTier(org, nationName) != SovereignSubjectState.AutonomyTier.DOMINION
                || warManager.isNationInActiveWar(nationName)) {
            return false;
        }

        // Independence ends membership rather than adding a permanent subject tier.
        removeMemberNationAndAdjust(org, nationName);
        return true;
    }

    public boolean adminSetSubject(WDEorg org, String nationName, String field, String value) {
        if (!isSovereignSubjectNation(org, nationName) || warManager.isNationInActiveWar(nationName)) return false;
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
                || org.getMemberNationNames().stream().anyMatch(warManager::isNationInActiveWar)) return false;
        Nation chairman = resolveTownyNation(nationName);
        if (chairman == null || chairman.getKing() == null) return false;
        org.setChairmanNationName(nationName);
        org.setLeaderUUID(chairman.getKing().getUUID());
        synchronizeSovereignSubjectStates(org);
        for (String member : org.getMemberNationNames()) enforceSubjectTownTaxCaps(org, member);
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
        if (required && isNationNeutral(nationName) && warManager.getActiveIndependenceWars(org).isEmpty()) return false;

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

    public double getWeightedSubjectCapabilityTotal(WDEorg org, NationManager nationManager) {
        if (org == null || !org.isSovereignEmpire()) {
            return 0.0D;
        }

        double total = 0.0D;
        for (String memberNation : org.getMemberNationNames()) {
            if (!isSovereignSubjectNation(org, memberNation)) {
                continue;
            }
            double multiplier = isGarrisonSubject(org, memberNation) ? 0.75D : 1.0D;
            total += getNationWeight(memberNation, nationManager) * multiplier;
        }
        return total;
    }

    public EconomyBridge.Charge getSovereignAutonomyUpgradeCharge() {
        FileConfiguration config = warManager.getWarConfiguration();
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
        if (!isSovereignSubjectNation(org, nationName)
                || !"FIXED".equalsIgnoreCase(normalizedTaxType)) {
            return Double.MAX_VALUE;
        }

        SovereignSubjectState.AutonomyTier tier = getSubjectAutonomyTier(org, nationName);
        FileConfiguration config = getOrganizationConfiguration();
        String path = "sovereignEmpire.subjectControl.taxCaps."
                + tier.name().toLowerCase(java.util.Locale.ROOT) + ".fixed";
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
        return warManager.findMilitaryJoinConflict(org, nationName, nationManager) == null;
    }

    public void enableAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveOrganizations();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 5);

        warManager.startGoalTracking();

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

        warManager.startWarCycles();
    }

    Boolean findVoteIgnoreCase(Map<String, Boolean> votes, String nationName) {
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
            changed |= processSubjectSuppression(org, nationManager, chairmanCapability);
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
                boolean independenceCombat = !warManager.getActiveIndependenceWars(org).isEmpty();
                if (isNationNeutral(memberNation) && !independenceCombat) continue;

                if (hasSubjectPlayerNearChairmanCommander(memberNation, chairmanCommander,
                        getSovereignWarParticipationRadius(), independenceCombat)) {
                    continue;
                }

                applySubjectWarParticipationPenalty(memberNation, penaltyDurationTicks, independenceCombat);
            }
        }
    }

    private boolean processSubjectSuppression(WDEorg org, NationManager nationManager, double chairmanCapability) {
        List<String> subjectNations = new ArrayList<>();
        for (String memberNation : org.getMemberNationNames()) {
            if (isSovereignSubjectNation(org, memberNation)) {
                subjectNations.add(memberNation);
            }
        }

        double subjectCapability = getWeightedSubjectCapabilityTotal(org, nationManager);
        if (subjectNations.isEmpty() || Double.compare(subjectCapability, chairmanCapability) == 0) {
            return false;
        }

        int delta = subjectCapability > chairmanCapability
                ? -getSovereignSuppressionDecrease()
                : getSovereignSuppressionIncrease();
        if (delta == 0) {
            return false;
        }

        Map<String, SovereignSubjectState> states = readSovereignSubjectStates(org);
        boolean changed = false;
        for (String nationName : subjectNations) {
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

    boolean isUnfriendly(WDEnation nation, String targetNationName) {
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

    Set<String> findOverlappingMemberNations(WDEorg first, WDEorg second) {
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

    int getDefaultSubjectSuppression() {
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

    private FileConfiguration getOrganizationConfiguration() {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getOrganizationConfig();
    }

    Nation resolveTownyNation(String nationName) {
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

    public List<String> enforceNeutralNation(String nationName) {
        List<String> removedAlliances = new ArrayList<>();
        if (!isNationNeutral(nationName)) return removedAlliances;

        // Clear shared wars before an empty alliance can disappear from storage.
        warManager.clearWarsForNeutralNation(nationName);
        for (WDEorg org : getAllOrganizations()) {
            warManager.clearWarProposalsForNeutralNation(org, nationName);
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

        if (!warManager.getActiveIndependenceWars(org).isEmpty()) {
            return true;
        }

        for (String memberNation : org.getMemberNationNames()) {
            for (WDEorg existing : getAllOrganizations()) {
                for (WarManager.MilitaryWarState warState : warManager.getActiveMilitaryWars(existing)) {
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

    String msg(String key, Object... args) {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).formatLocaleMessage(key, args);
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

    void removeSovereignSubjectState(WDEorg org, String nationName) {
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

    Map<String, SovereignSubjectState> readSovereignSubjectStates(WDEorg org) {
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

    void writeSovereignSubjectStates(WDEorg org, Map<String, SovereignSubjectState> states) {
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

}
