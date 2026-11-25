package com.thatguycy.worlddynamicsengine.manager.organization;

import com.palmergames.bukkit.towny.object.economy.BankAccount;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.manager.war.WarOccupation;
import com.thatguycy.worlddynamicsengine.model.organization.SovereignSubjectState;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalState;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalType;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import com.thatguycy.worlddynamicsengine.model.WarLedger;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
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
import org.bukkit.scheduler.BukkitRunnable;

/** Owns war state, war progression and peace treaty workflows. */
public final class WarManager {
    private static final String ACTIVE_MILITARY_WARS_SETTING = "activeMilitaryWars";
    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final OrgManager organizations;
    private final DataStorageManager storage;
    private final Map<UUID, Long> independenceWarRespawnLocks = new ConcurrentHashMap<>();
    private final Set<String> observedWarGoalControls = new LinkedHashSet<>();
    private final WarLedger warLedger;
    private final WarStateStore warStateStore;

    WarManager(JavaPlugin plugin, TownManager townManager, OrgManager organizations, DataStorageManager storage) {
        this(plugin, townManager, organizations, storage,
                storage.loadStringKeyed("wars", WarLedger.class).getOrDefault("ledger", new WarLedger()));
    }

    WarManager(JavaPlugin plugin, TownManager townManager, OrgManager organizations, DataStorageManager storage,
               WarLedger warLedger) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.organizations = organizations;
        this.storage = storage;
        this.warLedger = warLedger;
        this.warStateStore = new WarStateStore(warLedger);
    }

    void saveLedger() {
        storage.saveEntity("wars", "ledger", warLedger);
    }

    boolean migrateOrganizationWars(WDEorg org) {
        boolean changed = false;
        if (org.getSetting(ACTIVE_MILITARY_WARS_SETTING) instanceof Map<?, ?> legacy) {
            legacy.forEach((key, value) -> warLedger.wars.putIfAbsent(String.valueOf(key), value));
            org.getSettings().remove(ACTIVE_MILITARY_WARS_SETTING);
            changed = true;
        }
        for (IndependenceWarState legacy : readIndependenceWars(org).values()) {
            String migratedWarKey = buildWarKey(org.getChairmanNationName(), legacy.rebelNationName);
            Set<String> defenders = new LinkedHashSet<>(org.getMemberNationNames());
            defenders.remove(legacy.rebelNationName);
            if (!defenders.isEmpty()) {
                MilitaryWarState war = new MilitaryWarState(org.getChairmanNationName(), legacy.rebelNationName,
                        defenders, Set.of(legacy.rebelNationName), legacy.empireScore, legacy.rebelScore, legacy.startedAt);
                war.reason = WarReason.INDEPENDENCE;
                war.attackerOccupation.putAll(legacy.empireOccupation);
                war.defenderOccupation.putAll(legacy.rebelOccupation);
                war.attackerKills.putAll(legacy.empireKills);
                war.defenderKills.putAll(legacy.rebelKills);
                boolean forcedTreatyAllowed = defenders.size() == 1
                        && organizations.getMilitaryAlliancesByNation(org.getChairmanNationName()).isEmpty()
                        && organizations.getMilitaryAlliancesByNation(legacy.rebelNationName).isEmpty();
                war.setWarGoal(createWarGoalState(WarReason.INDEPENDENCE,
                        legacy.rebelNationName, org.getChairmanNationName(), null, null,
                        defenders, Set.of(legacy.rebelNationName), forcedTreatyAllowed,
                        ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager()));
                warLedger.wars.putIfAbsent(war.getWarKey(), war.toMap());
            }
            readPeaceTreatyProposals(org).entrySet().stream()
                    .filter(entry -> entry.getKey().equals("independence:" + legacy.rebelNationName))
                    .forEach(entry -> {
                        var proposal = entry.getValue();
                        warLedger.treaties.put(migratedWarKey,
                                migrateIndependenceProposal(proposal, migratedWarKey,
                                        legacy.rebelNationName, org.getChairmanNationName()).toMap());
                    });
        }
        if (org.getSettings().remove("independenceWars") != null) changed = true;
        var oldTreaties = readPeaceTreatyProposals(org);
        if (oldTreaties.keySet().removeIf(key -> key.startsWith("independence:"))) {
            writePeaceTreatyProposals(org, oldTreaties);
            changed = true;
        }
        return changed;
    }

    void startGoalTracking() {
        long warGoalCheckTicks = Math.max(1L, getWarConfiguration().getLong("warGoals.progressCheckSeconds", 5L)) * 20L;
        processWarGoalProgress(buildOccupationCensus());
        new BukkitRunnable() {
            @Override
            public void run() {
                processWarGoalProgress(buildOccupationCensus());
            }
        }.runTaskTimer(plugin, warGoalCheckTicks, warGoalCheckTicks);
    }

    void startWarCycles() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Map<String, Map<String, int[]>> census = buildOccupationCensus();
                processRegularWarCycles(census);
            }
        }.runTaskTimer(plugin, getIndependenceWarCycleTicks(), getIndependenceWarCycleTicks());
    }

    void migrateLegacyPeaceTreatyTributes() {
        NationManager nationManager =
                ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager();
        if (nationManager != null && PeaceTreatyTributes.migrate(nationManager.getAllNations())) {
            nationManager.saveNations();
        }
    }

    boolean migrateLegacyIndependenceWarSides() {
        boolean changed = false;
        for (Map.Entry<String, MilitaryWarState> entry : readActiveMilitaryWars(null).entrySet()) {
            MilitaryWarState legacy = entry.getValue();
            if (legacy.reason != WarReason.INDEPENDENCE || !usesLegacyIndependenceSides(legacy)) continue;

            String rebelNation = legacy.getAttackerNationName();
            String sovereignNation = legacy.getDefenderNationName();
            Set<String> attackerSide = legacy.getDefenderNationNames();
            Set<String> defenderSide = legacy.getAttackerNationNames();
            MilitaryWarState migrated = new MilitaryWarState(sovereignNation, rebelNation,
                    attackerSide, defenderSide, legacy.getDefenderScore(), legacy.getAttackerScore(),
                    legacy.getStartedAt());
            migrated.reason = WarReason.INDEPENDENCE;
            migrated.attackerEconomicTermsDemanded = legacy.defenderEconomicTermsDemanded;
            migrated.defenderEconomicTermsDemanded = legacy.attackerEconomicTermsDemanded;
            migrated.attackerOccupation.putAll(legacy.getDefenderOccupation());
            migrated.defenderOccupation.putAll(legacy.getAttackerOccupation());
            migrated.attackerKills.putAll(legacy.getDefenderKills());
            migrated.defenderKills.putAll(legacy.getAttackerKills());
            boolean forcedTreatyAllowed = attackerSide.size() == 1 && defenderSide.size() == 1
                    && organizations.getMilitaryAlliancesByNation(rebelNation).isEmpty()
                    && organizations.getMilitaryAlliancesByNation(sovereignNation).isEmpty();
            migrated.setWarGoal(createWarGoalState(WarReason.INDEPENDENCE, rebelNation,
                    sovereignNation, null, null, attackerSide, defenderSide,
                    forcedTreatyAllowed, ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                            .getNationManager()));

            String oldKey = entry.getKey();
            String newKey = migrated.getWarKey();
            warLedger.wars.remove(oldKey);
            warLedger.wars.put(newKey, migrated.toMap());
            migrateIndependenceTreatyKey(oldKey, newKey, rebelNation, sovereignNation);
            changed = true;
        }
        return changed;
    }

    private boolean usesLegacyIndependenceSides(MilitaryWarState war) {
        String oldAttacker = war.getAttackerNationName();
        if (oldAttacker.startsWith("town:")) return true;
        WDEorg empire = organizations.getSovereignEmpireByNation(oldAttacker);
        return empire != null && empire.getChairmanNationName().equalsIgnoreCase(war.getDefenderNationName());
    }

    private void migrateIndependenceTreatyKey(String oldKey, String newKey,
            String rebelNation, String sovereignNation) {
        Object globalRaw = warLedger.treaties.remove(oldKey);
        if (globalRaw instanceof Map<?, ?> rawMap) {
            PeaceTreatyProposalState proposal = PeaceTreatyProposalState.fromMap(oldKey, rawMap);
            warLedger.treaties.put(newKey,
                    migrateIndependenceProposal(proposal, newKey, rebelNation, sovereignNation).toMap());
        }
        for (WDEorg org : organizations.getAllOrganizations()) {
            Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
            PeaceTreatyProposalState proposal = proposals.remove(oldKey);
            if (proposal == null) continue;
            proposals.put(newKey, migrateIndependenceProposal(proposal, newKey, rebelNation, sovereignNation));
            writePeaceTreatyProposals(org, proposals);
        }
    }

    private PeaceTreatyProposalState migrateIndependenceProposal(PeaceTreatyProposalState proposal,
            String newKey, String rebelNation, String sovereignNation) {
        boolean proposedByRebel = proposal.getProposerNationName().equalsIgnoreCase(rebelNation)
                && proposal.getTargetNationName().equalsIgnoreCase(sovereignNation);
        PeaceTreatyProposalState.ProposalType type = proposal.getProposalType();
        String proposer = proposal.getProposerNationName();
        String target = proposal.getTargetNationName();
        if (proposedByRebel) {
            proposer = sovereignNation;
            target = rebelNation;
            type = type == PeaceTreatyProposalState.ProposalType.DEMAND
                    ? PeaceTreatyProposalState.ProposalType.SURRENDER
                    : PeaceTreatyProposalState.ProposalType.DEMAND;
        }
        long createdAt = ((Number) proposal.toMap().get("createdAt")).longValue();
        return new PeaceTreatyProposalState(newKey, type, proposer, target,
                proposal.getTotalPoints(), proposal.getClauses(), createdAt);
    }

    private List<WDEorg> warContexts() {
        List<WDEorg> contexts = organizations.getAllOrganizations();
        contexts.addFirst(null);
        return contexts;
    }

    private String warScope(WDEorg org) {
        return org == null ? "war" : org.getOrganizationName();
    }

    public boolean adminEndMilitaryWar(String firstNation, String secondNation) {
        Set<String> keys = new LinkedHashSet<>();
        for (WDEorg org : warContexts()) {
            for (MilitaryWarState war : getActiveMilitaryWars(org)) {
                if (war.containsOpposingNations(firstNation, secondNation)) keys.add(war.getWarKey());
            }
        }
        for (String key : keys) clearMilitaryWarAcrossOrganizations(key);
        organizations.saveOrganizations();
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
        if (changed) organizations.saveOrganizations();
        return changed;
    }

    public boolean adminResolveIndependence(WDEorg org, String nationName, IndependenceWarOutcome outcome) {
        if (!hasActiveIndependenceWar(org, nationName)) return false;
        if (outcome == IndependenceWarOutcome.SUCCESS) return surrenderIndependenceWar(org, nationName) != null;
        return adminEndMilitaryWar(nationName, org.getChairmanNationName());
    }

    public boolean adminSetIndependenceScores(WDEorg org, String nationName, double rebel, double empire) {
        return hasActiveIndependenceWar(org, nationName)
                && adminSetMilitaryScores(nationName, org.getChairmanNationName(), rebel, empire);
    }

    public MilitaryWarVoteResult startMilitaryWarVote(WDEorg org, String proposerNationName, String targetNationName, NationManager nationManager) {
        return startMilitaryWarVote(org, proposerNationName, targetNationName, nationManager, WarReason.CONQUEST);
    }

    public MilitaryWarVoteResult startMilitaryWarVote(WDEorg org, String proposerNationName, String targetNationName,
                                                       NationManager nationManager, WarReason reason) {
        return startMilitaryWarVote(org, proposerNationName, targetNationName, nationManager, reason, null, null);
    }

    public MilitaryWarVoteResult startMilitaryWarVote(WDEorg org, String proposerNationName,
            String targetNationName, NationManager nationManager, WarReason reason,
            String goalTownName, String goalOrganizationName) {
        if (org == null || proposerNationName == null || targetNationName == null || !organizations.isMilitaryAlliance(org)
                || !org.hasMemberNation(proposerNationName) || org.hasMemberNation(targetNationName)) {
            return MilitaryWarVoteResult.invalid();
        }
        String reasonError = warReasonFailure(reason, proposerNationName, targetNationName);
        if (reasonError != null) return MilitaryWarVoteResult.invalid(reasonError);
        String goalError = validateWarGoalSelection(reason, proposerNationName, targetNationName,
                goalTownName, goalOrganizationName);
        if (goalError != null) return MilitaryWarVoteResult.invalid(goalError);
        if (organizations.isNationNeutral(proposerNationName) || organizations.isNationNeutral(targetNationName)) {
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

        MilitaryWarProposalState proposal = new MilitaryWarProposalState(targetNationName, proposerNationName,
                goalTownName, goalOrganizationName);
        proposal.reason = reason;
        proposal.getVotes().put(proposerNationName, true);
        proposals.put(targetNationName, proposal);
        writeMilitaryWarProposals(org, proposals);
        organizations.saveOrganizations();
        return resolveMilitaryWarVote(org, proposal, proposals, nationManager);
    }

    public MilitaryWarVoteResult castMilitaryWarVote(WDEorg org, String targetNationName, String voterNationName, boolean support, NationManager nationManager) {
        if (org == null || targetNationName == null || voterNationName == null || !organizations.isMilitaryAlliance(org)
                || !org.hasMemberNation(voterNationName)) {
            return MilitaryWarVoteResult.invalid();
        }
        if (organizations.isNationNeutral(voterNationName)) return MilitaryWarVoteResult.peaceProtected();

        Map<String, MilitaryWarProposalState> proposals = readMilitaryWarProposals(org);
        String key = findMilitaryWarProposalKey(proposals, targetNationName);
        if (key == null) {
            return MilitaryWarVoteResult.notFound();
        }

        MilitaryWarProposalState proposal = proposals.get(key);
        proposal.getVotes().put(voterNationName, support);
        writeMilitaryWarProposals(org, proposals);
        organizations.saveOrganizations();
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
        for (WDEorg empire : organizations.getAllOrganizations()) {
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

    public WarGoalDisplay getWarGoalDisplay(String warKey) {
        MilitaryWarState war = readActiveMilitaryWars(null).get(warKey);
        if (war == null || war.getWarGoal().getType() == WarGoalType.NONE) return null;
        WarGoalState goal = war.getWarGoal();
        long now = System.currentTimeMillis();
        long holdMillis = Math.max(0L, getWarConfiguration().getLong("warGoals.holdSeconds", 1800L)) * 1000L;
        long required = goal.getType() == WarGoalType.SHOW_HEGEMONY
                ? (long) Math.ceil(goal.getEnemyPopulationAtStart()
                        * Math.max(0D, getWarConfiguration().getDouble("warGoals.dominancePopulationMultiplier", 1D)))
                : holdMillis;
        boolean met = goal.getConditionSince() > 0L;
        long progress = goal.getType() == WarGoalType.SHOW_HEGEMONY
                ? goal.getAttackerDefeatedPlayers()
                : met ? Math.max(0L, now - goal.getConditionSince()) : 0L;
        long holdRemaining = met ? Math.max(0L, holdMillis - Math.max(0L, now - goal.getConditionSince())) : holdMillis;
        String target = switch (goal.getType()) {
            case CAPTURE_TOWN, OCCUPY_CAPITAL -> goal.getTargetTownName();
            case SHOW_HEGEMONY -> goal.getTargetOrganizationName().isBlank()
                    ? goal.getTargetNationName() : goal.getTargetOrganizationName();
            case NONE -> "";
        };
        return new WarGoalDisplay(goal.getType(), target, progress, required, holdRemaining, met,
                goal.isReady(now, holdMillis), goal.isForcedTreatyAllowed());
    }

    public void renameWarGoalTown(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.isBlank() || newName.isBlank()) return;
        boolean changed = false;
        for (MilitaryWarState war : readActiveMilitaryWars(null).values()) {
            if (war.getWarGoal().getTargetTownName().equalsIgnoreCase(oldName)) {
                war.getWarGoal().setTargetTownName(newName);
                warLedger.wars.put(war.getWarKey(), war.toMap());
                changed = true;
            }
        }
        if (changed) organizations.saveOrganizations();
    }

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
        for (WDEorg empire : organizations.getAllOrganizations()) {
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

    public List<WarFocusDetails> getVisibleWarDetails(String nation, String town) {
        return getWarFocusOptions(nation, town).stream()
                .map(option -> getWarFocusDetails(option.focusKey(), nation, town))
                .filter(java.util.Objects::nonNull)
                .toList();
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
            String actor = war.isAttackerNation(townKey) || war.isDefenderNation(townKey) ? townKey : nation;
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

        for (WDEorg empire : organizations.getAllOrganizations()) {
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
                if (!organizations.isNationNeutral(firstNationName) && !organizations.isNationNeutral(secondNationName)
                        && warState.containsOpposingNations(firstNationName, secondNationName)) {
                    return true;
                }
        }
        for (WDEorg org : organizations.getAllOrganizations()) {
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
        int submittedClauseCount = clauses.size();
        clauses = normalizePeaceTreatyClauses(actorNationName, targetNationName, proposalType, clauses, nationManager);

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

        Map<String, PeaceTreatyProposalState> proposals = readPeaceTreatyProposals(org);
        proposals.put(context.getWarKey(), proposal);
        writePeaceTreatyProposals(org, proposals);
        organizations.saveOrganizations();
        if (proposalType == PeaceTreatyProposalState.ProposalType.DEMAND
                && PeaceTreatyRules.hasQualifyingRaidingTerms(clauses)) {
            MilitaryWarState updatedWar = recordEconomicTermsDemand(context.getWarKey(), actorNationName);
            if (updatedWar != null) {
                var warMood = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                        .getWarMoodDoctrineManager();
                if (warMood != null) warMood.recordEconomicWarDemand(updatedWar, actorNationName);
            }
        }
        if (submittedClauseCount == 1) {
            processWarGoalProgress(buildOccupationCensus());
            context = resolveWarContext(org, actorNationName, targetNationName);
        }
        if (isReadyWarGoalDemand(context, proposal, submittedClauseCount)
                || isReadyDefenderIndependenceDemand(context, proposal,
                        submittedClauseCount, scoreDifference)) {
            PeaceTreatyReviewResult result = reviewPeaceTreaty(
                    org, targetNationName, actorNationName, true, nationManager);
            if (result.getStatus() == PeaceTreatyReviewResult.Status.ACCEPTED) {
                return PeaceTreatyProposalResult.forcedAccepted(proposal, scoreDifference);
            }
        }
        return PeaceTreatyProposalResult.proposed(proposal, scoreDifference);
    }

    private MilitaryWarState recordEconomicTermsDemand(String warKey, String beneficiaryNationName) {
        Map<String, MilitaryWarState> wars = readActiveMilitaryWars(null);
        MilitaryWarState war = wars.get(warKey);
        if (war == null || !war.markEconomicTermsDemanded(beneficiaryNationName)) {
            return null;
        }
        wars.put(warKey, war);
        writeActiveMilitaryWars(null, wars);
        organizations.saveOrganizations();
        return war;
    }

    private boolean isReadyWarGoalDemand(WarContext context, PeaceTreatyProposalState proposal,
            int submittedClauseCount) {
        MilitaryWarState war = context == null ? null : context.getMilitaryWarState();
        PeaceTreatyProposalState.ProposalType expectedType = war != null && war.reason == WarReason.INDEPENDENCE
                ? PeaceTreatyProposalState.ProposalType.SURRENDER
                : PeaceTreatyProposalState.ProposalType.DEMAND;
        if (war == null || proposal.getProposalType() != expectedType
                || !war.getAttackerNationName().equalsIgnoreCase(proposal.getProposerNationName())
                || !war.getDefenderNationName().equalsIgnoreCase(proposal.getTargetNationName())
                || submittedClauseCount != 1
                || proposal.getClauses().size() != 1) {
            return false;
        }
        WarGoalState goal = war.getWarGoal();
        long holdMillis = Math.max(0L, getWarConfiguration().getLong("warGoals.holdSeconds", 1800L)) * 1000L;
        if (!goal.isReady(System.currentTimeMillis(), holdMillis)
                || !goal.isForcedTreatyAllowed()) {
            return false;
        }
        return PeaceTreatyRules.matchesWarGoal(war.reason, goal, war.getAttackerNationName(),
                proposal.getClauses().getFirst());
    }

    private boolean isReadyDefenderIndependenceDemand(WarContext context,
            PeaceTreatyProposalState proposal, int submittedClauseCount, double scoreDifference) {
        MilitaryWarState war = context == null ? null : context.getMilitaryWarState();
        if (war == null || war.reason != WarReason.INDEPENDENCE
                || proposal.getProposalType() != PeaceTreatyProposalState.ProposalType.DEMAND
                || !war.getDefenderNationName().equalsIgnoreCase(proposal.getProposerNationName())
                || !war.getAttackerNationName().equalsIgnoreCase(proposal.getTargetNationName())
                || submittedClauseCount != 1 || proposal.getClauses().size() != 1
                || proposal.getClauses().getFirst().getType()
                        != PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE) {
            return false;
        }
        WarGoalState goal = war.getWarGoal();
        double requiredDifference = Math.max(0D,
                getWarConfiguration().getDouble("warGoals.defenderIndependenceScoreDifference", 40D));
        return goal.canDefenderDemandIndependence(scoreDifference, requiredDifference);
    }

    public boolean canDefenderForceIndependence(String warKey) {
        MilitaryWarState war = readActiveMilitaryWars(null).get(warKey);
        if (war == null || war.reason != WarReason.INDEPENDENCE) return false;
        double difference = war.getDefenderScore() - war.getAttackerScore();
        double requiredDifference = Math.max(0D,
                getWarConfiguration().getDouble("warGoals.defenderIndependenceScoreDifference", 40D));
        return war.getWarGoal().canDefenderDemandIndependence(difference, requiredDifference);
    }

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
            List<String> candidates = nations.getAllNations().stream().map(WDEnation::getNationName)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            for (MilitaryWarState war : getActiveMilitaryWars(null)) {
                for (String participant : war.getAttackerNationNames()) if (participant.startsWith("town:")) candidates.add(participant);
                for (String participant : war.getDefenderNationNames()) if (participant.startsWith("town:")) candidates.add(participant);
            }
            for (String target : candidates) {
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
        clauses = normalizePeaceTreatyClauses(actorNation, targetNation, type, clauses, nations);
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

    public boolean willPeaceTreatyDissolveNation(String actor, String target,
                                                PeaceTreatyProposalState.ProposalType direction,
                                                List<PeaceTreatyClauseState> clauses, NationManager nations) {
        String conceding = direction == PeaceTreatyProposalState.ProposalType.DEMAND ? target : actor;
        Nation nation = nations.getTownyNation(conceding);
        if (nation == null || nation.getTowns().isEmpty()) return false;
        Set<String> ceded = new LinkedHashSet<>();
        for (var clause : clauses) {
            if (clause.getType() != PeaceTreatyClauseState.ClauseType.TRANSFER_TOWN) continue;
            Town town = TownyAPI.getInstance().getTown(clause.getString("town"));
            Nation destination = nations.getTownyNation(clause.getString("destinationNation"));
            if (town != null && town.getNationOrNull() == nation && destination != null && destination != nation) {
                ceded.add(town.getName());
            }
        }
        return nation.getTowns().stream().allMatch(town -> ceded.contains(town.getName()));
    }

    public List<PeaceTreatyClauseState> normalizePeaceTreatyClauses(String actor, String target,
            PeaceTreatyProposalState.ProposalType direction, List<PeaceTreatyClauseState> clauses, NationManager nations) {
        if (!willPeaceTreatyDissolveNation(actor, target, direction, clauses, nations)) return new ArrayList<>(clauses);
        return clauses.stream().filter(clause -> clause.getType() != PeaceTreatyClauseState.ClauseType.TRIBUTE
                && clause.getType() != PeaceTreatyClauseState.ClauseType.UNCONDITIONAL_PASSAGE).toList();
    }

    public boolean canIncludePeaceTreatyMenuClause(WDEorg org, String actor, String target,
                                                   PeaceTreatyProposalState.ProposalType direction,
                                                   List<PeaceTreatyClauseState> selected,
                                                   PeaceTreatyClauseState candidate, NationManager nations) {
        List<PeaceTreatyClauseState> prospective = new ArrayList<>(selected);
        prospective.removeIf(clause -> clause.getType() == candidate.getType());
        prospective.add(candidate);
        return previewPeaceTreaty(org, actor, target, direction, prospective, nations).error() == null;
    }

    public List<PeaceTreatyMenuClauseOption> getPeaceTreatyMenuClauseOptions(
            WDEorg org, String actor, String target, PeaceTreatyProposalState.ProposalType direction,
            List<PeaceTreatyClauseState> selected, NationManager nations) {
        WarContext context = resolveWarContext(org, actor, target);
        if (context == null || validatePeaceTreatyProposalAuthority(context, actor, target) != null) return List.of();
        String conceding = direction == PeaceTreatyProposalState.ProposalType.DEMAND ? target : actor;
        Set<String> beneficiarySide = direction == PeaceTreatyProposalState.ProposalType.DEMAND
                ? context.getActorSideNations() : context.getCounterpartySideNations();
        List<PeaceTreatyMenuClauseOption> options = new ArrayList<>();
        for (PeaceTreatyClauseState.ClauseType type : PeaceTreatyClauseState.ClauseType.values()) {
            if ((type == PeaceTreatyClauseState.ClauseType.TRIBUTE
                    || type == PeaceTreatyClauseState.ClauseType.UNCONDITIONAL_PASSAGE)
                    && willPeaceTreatyDissolveNation(actor, target, direction, selected, nations)) continue;
            List<String> towns = new ArrayList<>(), destinations = new ArrayList<>(), subjects = new ArrayList<>();
            int maxPercent = 0;
            boolean available = false;
            switch (type) {
                case TRIBUTE -> {
                    var candidate = new PeaceTreatyClauseState(type, Map.of("amount", "1"));
                    available = canIncludePeaceTreatyMenuClause(
                            org, actor, target, direction, selected, candidate, nations);
                }
                case REPARATIONS -> {
                    int limit = Math.max(1, getWarConfiguration().getInt("peaceTreaty.clauses.reparations.maxPercent", 20));
                    for (int percent = 1; percent <= limit; percent++) {
                        var candidate = new PeaceTreatyClauseState(type, Map.of("percent", percent));
                        if (!canIncludePeaceTreatyMenuClause(org, actor, target, direction, selected, candidate, nations)) break;
                        maxPercent = percent;
                    }
                    available = maxPercent > 0;
                }
                case TRANSFER_TOWN -> {
                    Nation source = nations.getTownyNation(conceding);
                    for (Town town : source == null ? List.<Town>of() : source.getTowns()) {
                        for (String destination : beneficiarySide) {
                            if (nations.getNation(destination) == null || nations.getTownyNation(destination) == null) continue;
                            var candidate = new PeaceTreatyClauseState(type,
                                    Map.of("town", town.getName(), "destinationNation", destination));
                            if (canIncludePeaceTreatyMenuClause(org, actor, target, direction, selected, candidate, nations)) {
                                if (!towns.contains(town.getName())) towns.add(town.getName());
                                if (!destinations.contains(destination)) destinations.add(destination);
                            }
                        }
                    }
                    available = !towns.isEmpty() && !destinations.isEmpty();
                }
                case LIBERATE_SUBJECT -> {
                    WDEorg empire = getOrganizationByChairmanSovereignEmpire(conceding);
                    if (empire != null) {
                        for (String subject : empire.getMemberNationNames()) {
                            if (!organizations.isSovereignSubjectNation(empire, subject) || nations.getNation(subject) == null
                                    || nations.getTownyNation(subject) == null) continue;
                            var candidate = new PeaceTreatyClauseState(type, Map.of("subjectNation", subject));
                            if (canIncludePeaceTreatyMenuClause(org, actor, target, direction, selected, candidate, nations)) {
                                subjects.add(subject);
                            }
                        }
                    }
                    available = !subjects.isEmpty();
                }
                case FORCE_DEMOCRATIZATION -> {
                    MilitaryWarState war = context.getMilitaryWarState();
                    WDEnation nation = nations.getNation(conceding);
                    if (war != null && war.reason == WarReason.EXPORT_REVOLUTION && nation != null) {
                        subjects.addAll(getDemocratizationCandidates(nation, war.getWarGoal().getExcludedKingUuid()));
                    }
                    available = !subjects.isEmpty();
                }
                case DISSOLVE_THREAT_ORGANIZATION -> {
                    MilitaryWarState war = context.getMilitaryWarState();
                    if (war != null && war.reason == WarReason.DESTROY_THREAT) {
                        WDEorg threat = organizations.getAllOrganizations().stream()
                                .filter(existing -> existing.getOrganizationName().equalsIgnoreCase(
                                        war.getWarGoal().getTargetOrganizationName()))
                                .findFirst().orElse(null);
                        if (threat != null && threat.getChairmanNationName().equalsIgnoreCase(conceding)) {
                            subjects.add(threat.getOrganizationName());
                        }
                    }
                    available = !subjects.isEmpty();
                }
                default -> available = canIncludePeaceTreatyMenuClause(org, actor, target, direction, selected,
                        new PeaceTreatyClauseState(type, Map.of()), nations);
            }
            if (available) {
                towns.sort(String.CASE_INSENSITIVE_ORDER);
                destinations.sort(String.CASE_INSENSITIVE_ORDER);
                subjects.sort(String.CASE_INSENSITIVE_ORDER);
                options.add(new PeaceTreatyMenuClauseOption(type, List.copyOf(towns), List.copyOf(destinations),
                        List.copyOf(subjects), maxPercent));
            }
        }
        return options;
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
            organizations.saveOrganizations();
            return PeaceTreatyReviewResult.rejected(proposal);
        }

        var effectiveClauses = normalizePeaceTreatyClauses(proposal.getProposerNationName(), proposal.getTargetNationName(),
                proposal.getProposalType(), proposal.getClauses(), nationManager);
        if (effectiveClauses.size() != proposal.getClauses().size()) {
            proposal = new PeaceTreatyProposalState(proposal.getWarKey(), proposal.getProposalType(),
                    proposal.getProposerNationName(), proposal.getTargetNationName(),
                    calculatePeaceTreatyPoints(context, proposal.getProposerNationName(), effectiveClauses,
                            isSeparatePeaceProposal(context, proposal.getProposerNationName(), proposal.getTargetNationName())),
                    effectiveClauses, ((Number) proposal.toMap().get("createdAt")).longValue());
        }
        String validationError = validatePeaceTreatyClauses(context, proposal.getProposerNationName(), proposal.getTargetNationName(), proposal.getProposalType(), proposal.getClauses(), nationManager, true);
        if (validationError != null) {
            return PeaceTreatyReviewResult.invalid(validationError);
        }

        var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
        boolean executed;
        if (history != null) history.beginSettlement();
        try {
            executed = executePeaceTreaty(context, proposal, nationManager);
            if (history != null) history.treaty(proposal, executed);
        } catch (RuntimeException ex) {
            if (history != null) history.treaty(proposal, false);
            throw ex;
        } finally {
            if (history != null) history.endSettlement();
        }
        if (!executed) {
            return PeaceTreatyReviewResult.invalid("org_peace_treaty_transfer_town_invalid");
        }
        proposals.remove(context.getWarKey());
        writePeaceTreatyProposals(org, proposals);
        organizations.saveOrganizations();
        return PeaceTreatyReviewResult.accepted(proposal);
    }

    public boolean hasActiveIndependenceWar(WDEorg org, String rebelNationName) {
        return org != null && getActiveMilitaryWars(null).stream().anyMatch(war -> war.reason == WarReason.INDEPENDENCE
                && war.getDefenderNationName().equalsIgnoreCase(rebelNationName)
                && war.getAttackerNationName().equalsIgnoreCase(org.getChairmanNationName()));
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
        return List.of();
    }

    public boolean startIndependenceWar(WDEorg org, String rebelNationName) {
        return org != null && handleMilitaryAllianceWar(rebelNationName, org.getChairmanNationName(),
                ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager(),
                WarReason.INDEPENDENCE).isSuccess();
    }

    public void prepareIndependenceWarHostilities(WDEorg org, String rebelNationName, NationManager nationManager) {
        // Independence wars are now tracked as independent war states rather than persisted diplomatic attitudes.
    }

    public IndependenceWarResolutionResult surrenderIndependenceWar(WDEorg org, String rebelNationName) {
        if (!hasActiveIndependenceWar(org, rebelNationName)) return null;
        MilitaryWarState war = getActiveMilitaryWars(null).stream().filter(w -> w.reason == WarReason.INDEPENDENCE
                && w.getDefenderNationName().equalsIgnoreCase(rebelNationName)).findFirst().orElse(null);
        var nations = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager();
        var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
        if (history != null) {
            history.record(rebelNationName, "SUBJECT_LIBERATED", Map.of("beneficiary", rebelNationName,
                    "conceding", org.getChairmanNationName(), "war", war.getWarKey()));
            history.record(org.getChairmanNationName(), "WAR_DEFEAT",
                    Map.of("opponent", rebelNationName, "war", war.getWarKey()));
        }
        organizations.removeMemberNationAndAdjust(org, rebelNationName);
        createWarTrucesBetweenSides(war.getAttackerNationNames(), war.getDefenderNationNames(), 40, nations);
        clearMilitaryWarAcrossOrganizations(war.getWarKey());
        organizations.saveOrganizations();
        return new IndependenceWarResolutionResult(org, new IndependenceWarState(rebelNationName), IndependenceWarOutcome.SUCCESS);
    }

    public void recordIndependenceWarKill(String killerNationName, String victimNationName) {
        if (killerNationName == null || victimNationName == null || killerNationName.equalsIgnoreCase(victimNationName)) {
            return;
        }

        boolean changed = false;
        for (WDEorg org : organizations.getAllOrganizations()) {
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
            organizations.saveOrganizations();
        }
    }

    public void recordWarKill(String killerNationName, String victimNationName) {
        recordMilitaryWarKill(killerNationName, victimNationName);
    }

    public void recordMilitaryWarKill(String killerNationName, String victimNationName) {
        if (killerNationName == null || victimNationName == null || killerNationName.equalsIgnoreCase(victimNationName)) {
            return;
        }
        if (organizations.isNationNeutral(killerNationName) || organizations.isNationNeutral(victimNationName)) return;

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
            organizations.saveOrganizations();
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
        organizations.saveOrganizations();
    }

    public String findMilitaryJoinConflict(WDEorg org, String nationName, NationManager nationManager) {
        if (org == null || nationName == null || !organizations.isMilitaryAlliance(org)) {
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

            if (organizations.isUnfriendly(joiningNation, memberNationName) || organizations.isUnfriendly(memberNation, nationName)) {
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
        return handleMilitaryAllianceWar(attackerNationName, defenderNationName, nationManager, reason, null, null);
    }

    public MilitaryWarResult handleMilitaryAllianceWar(String attackerNationName, String defenderNationName,
            NationManager nationManager, WarReason reason, String goalTownName, String goalOrganizationName) {
        MilitaryWarResult result = new MilitaryWarResult();
        if (attackerNationName == null || defenderNationName == null || nationManager == null
                || attackerNationName.equalsIgnoreCase(defenderNationName)) {
            result.fail("war_declaration_blocked");
            return result;
        }
        if (organizations.isNationNeutral(attackerNationName)) {
            result.fail("org_neutral_war_forbidden");
            return result;
        }
        if (organizations.isNationNeutral(defenderNationName)) {
            result.fail("diplomacy_target_nation_peace_protected", defenderNationName);
            return result;
        }

        List<WDEorg> attackerAlliances = organizations.getMilitaryAlliancesByNation(attackerNationName);
        List<WDEorg> defenderAlliances = organizations.getMilitaryAlliancesByNation(defenderNationName);
        if (attackerAlliances.stream().anyMatch(a -> a.hasMemberNation(defenderNationName))) {
            result.fail("war_target_same_military_alliance", defenderNationName);
            return result;
        }
        if (hasActiveWarBetween(attackerNationName, defenderNationName)) {
            result.fail("org_war_vote_already_active", defenderNationName);
            return result;
        }
        String reasonError = warReasonFailure(reason, attackerNationName, defenderNationName);
        if (reasonError != null) {
            result.fail(reasonError);
            return result;
        }
        String goalError = validateWarGoalSelection(reason, attackerNationName, defenderNationName,
                goalTownName, goalOrganizationName);
        if (goalError != null) {
            result.fail(goalError);
            return result;
        }
        if (nationManager.hasActiveWarTruce(attackerNationName, defenderNationName)) {
            result.fail("org_war_vote_target_truce", defenderNationName);
            return result;
        }
        for (String a : declarationSide(attackerNationName, attackerAlliances, reason, true, attackerNationName)) {
            for (String d : declarationSide(defenderNationName, defenderAlliances, reason, false, attackerNationName)) {
                if (!a.equalsIgnoreCase(d) && nationManager.hasActiveWarTruce(a, d)) {
                    result.fail("org_war_vote_target_truce", defenderNationName);
                    return result;
                }
            }
        }

        result.attackerAllianceInvolved = reason == WarReason.INDEPENDENCE
                ? !defenderAlliances.isEmpty() : !attackerAlliances.isEmpty();
        result.defenderAllianceInvolved = reason == WarReason.INDEPENDENCE
                ? !attackerAlliances.isEmpty() : !defenderAlliances.isEmpty();

        if (!attackerAlliances.isEmpty() && !defenderAlliances.isEmpty()) {
            for (WDEorg attackerAlliance : attackerAlliances) {
                for (WDEorg defenderAlliance : defenderAlliances) {
                    for (String overlappingNation : organizations.findOverlappingMemberNations(attackerAlliance, defenderAlliance)) {
                        organizations.removeMemberNationAndAdjust(attackerAlliance, overlappingNation);
                        result.removedFromAttackerAlliances.add(overlappingNation + " -> " + attackerAlliance.getOrganizationName());
                    }
                }
            }
            attackerAlliances = organizations.getMilitaryAlliancesByNation(attackerNationName);
        }

        Set<String> declaredSide = declarationSide(attackerNationName, attackerAlliances,
                reason, true, attackerNationName);
        Set<String> targetSide = declarationSide(defenderNationName, defenderAlliances,
                reason, false, attackerNationName);
        Set<String> attackerSide = reason == WarReason.INDEPENDENCE ? targetSide : declaredSide;
        Set<String> defenderSide = reason == WarReason.INDEPENDENCE ? declaredSide : targetSide;
        defenderSide.removeAll(attackerSide);
        String warDefenderLeader = reason == WarReason.INDEPENDENCE ? attackerNationName : defenderNationName;
        String warAttackerLeader = reason == WarReason.INDEPENDENCE ? defenderNationName : attackerNationName;
        if (defenderSide.isEmpty() || !defenderSide.contains(warDefenderLeader)) {
            result.fail("war_target_same_military_alliance", defenderNationName);
            return result;
        }
        for (String attacker : attackerSide) for (String defender : defenderSide) {
            if (nationManager.hasActiveWarTruce(attacker, defender)) {
                result.fail("org_war_vote_target_truce", defenderNationName);
                return result;
            }
        }

        result.hostilePairsApplied = attackerSide.size() * defenderSide.size();
        MilitaryWarState warState = new MilitaryWarState(
                warAttackerLeader,
                warDefenderLeader,
                attackerSide,
                defenderSide,
                System.currentTimeMillis());
        warState.reason = reason;
        warState.setWarGoal(createWarGoalState(reason, attackerNationName, defenderNationName,
                goalTownName, goalOrganizationName, attackerSide, defenderSide,
                attackerAlliances.isEmpty() && defenderAlliances.isEmpty()
                        && attackerSide.size() == 1 && defenderSide.size() == 1,
                nationManager));
        applyHostileRelationsBetweenSides(attackerSide, defenderSide, nationManager);
        recordMilitaryWarState(reason == WarReason.INDEPENDENCE ? defenderAlliances : attackerAlliances, warState);
        broadcastMilitaryWarStarted(warState);

        return result;
    }

    private Set<String> declarationSide(String nation, List<WDEorg> alliances, WarReason reason,
                                        boolean attacker, String rebel) {
        if (reason != WarReason.INDEPENDENCE) return collectWarSide(nation, alliances);
        if (attacker) return new LinkedHashSet<>(Set.of(rebel));
        Set<String> side = collectWarSide(nation, alliances);
        side.removeIf(member -> member.equalsIgnoreCase(rebel));
        return side;
    }

    public MilitaryWarResult declareTownIndependence(Town town, String target, NationManager nations) {
        MilitaryWarResult result = new MilitaryWarResult();
        var engine = (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        if (!engine.isRegularTownWarParticipationAllowed()) {
            result.fail("war_town_regular_disabled"); return result;
        }
        String actor = town == null ? null : "town:" + town.getName();
        String error = warReasonFailure(WarReason.INDEPENDENCE, actor, target);
        if (error != null) { result.fail(error); return result; }
        if (town.isNeutral() || organizations.isNationNeutral(target)) {
            result.fail("org_neutral_war_forbidden"); return result;
        }
        if (hasActiveWarBetween(actor, target)) {
            result.fail("org_war_vote_already_active", target); return result;
        }
        if (nations.hasActiveWarTruce(actor, target)) {
            result.fail("org_war_vote_target_truce", target); return result;
        }
        Set<String> attackers = collectWarSide(target, organizations.getMilitaryAlliancesByNation(target));
        for (String attacker : attackers) {
            if (nations.hasActiveWarTruce(actor, attacker)) {
                result.fail("org_war_vote_target_truce", attacker); return result;
            }
        }
        MilitaryWarState war = new MilitaryWarState(target, actor, attackers, Set.of(actor), System.currentTimeMillis());
        war.reason = WarReason.INDEPENDENCE;
        war.setWarGoal(createWarGoalState(WarReason.INDEPENDENCE, actor, target, null, null,
                attackers, Set.of(actor),
                organizations.getMilitaryAlliancesByNation(target).isEmpty() && attackers.size() == 1, nations));
        recordMilitaryWarState(List.of(), war);
        broadcastMilitaryWarStarted(war);
        result.hostilePairsApplied = attackers.size();
        return result;
    }

    private void processWarGoalProgress(Map<String, Map<String, int[]>> census) {
        long now = System.currentTimeMillis();
        double controlThreshold = Math.max(0D, Math.min(100D,
                getWarConfiguration().getDouble("warGoals.controlThresholdPercent", 75D))) / 100D;
        boolean changed = false;
        Map<String, MilitaryWarState> wars = readActiveMilitaryWars(null);
        for (MilitaryWarState war : wars.values()) {
            WarGoalState goal = war.getWarGoal();
            if (goal.getType() == WarGoalType.NONE) continue;
            if (observedWarGoalControls.add(war.getWarKey()) && goal.getConditionSince() > 0L) {
                goal.resetCondition();
                warLedger.wars.put(war.getWarKey(), war.toMap());
                changed = true;
            }
            boolean satisfied = switch (goal.getType()) {
                case CAPTURE_TOWN, OCCUPY_CAPITAL -> isTownControlledByAttackers(war, goal, census, controlThreshold);
                case SHOW_HEGEMONY -> hasAttackerAchievedDominance(war, goal);
                case NONE -> false;
            };
            if (goal.updateCondition(satisfied, now)) {
                warLedger.wars.put(war.getWarKey(), war.toMap());
                changed = true;
            }
        }
        if (changed) organizations.saveOrganizations();
    }

    private boolean isTownControlledByAttackers(MilitaryWarState war, WarGoalState goal,
            Map<String, Map<String, int[]>> census, double threshold) {
        String townName = goal.getTargetTownName();
        Town town = townName == null || townName.isBlank() ? null : TownyAPI.getInstance().getTown(townName);
        if (town == null) return false;
        boolean targetStillBelongsToDefender = war.isDefenderNation("town:" + town.getName())
                || town.getNationOrNull() != null && war.isDefenderNation(town.getNationOrNull().getName());
        if (!targetStillBelongsToDefender) return false;
        int attackers = countOccupiers(town.getName(), war.getAttackerNationNames(),
                war.getDefenderNationNames(), census, false);
        int defenders = countOccupiers(town.getName(), war.getDefenderNationNames(),
                war.getAttackerNationNames(), census, true);
        return WarOccupation.attackerShare(attackers, defenders) > threshold;
    }

    private boolean hasAttackerAchievedDominance(MilitaryWarState war, WarGoalState goal) {
        double multiplier = Math.max(0D,
                getWarConfiguration().getDouble("warGoals.dominancePopulationMultiplier", 1D));
        long required = (long) Math.ceil(goal.getEnemyPopulationAtStart() * multiplier);
        return goal.getEnemyPopulationAtStart() > 0L
                && goal.getAttackerDefeatedPlayers() >= required;
    }

    private Set<String> collectWarSide(String primaryNation, List<WDEorg> alliances) {
        Set<String> nations = new LinkedHashSet<>();
        nations.add(primaryNation);
        for (WDEorg alliance : alliances) {
            nations.addAll(alliance.getMemberNationNames());
        }
        for (WDEorg empire : organizations.getAllOrganizations()) {
            if (empire.isSovereignEmpire() && nations.contains(empire.getChairmanNationName())) {
                for (String subject : empire.getMemberNationNames()) {
                    if (organizations.isSubjectWarParticipationRequired(empire, subject)) nations.add(subject);
                }
            }
        }
        nations.removeIf(organizations::isNationNeutral);
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

    public TownWarStartResult startTownWarDetailed(Town attacker, Town defender, WarReason reason) {
        var engine = (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        if (!engine.isRegularTownWarParticipationAllowed() || attacker == null || defender == null
                || attacker == defender || reason == null || reason == WarReason.INDEPENDENCE
                || reason == WarReason.DESTROY_THREAT) {
            if (!engine.isRegularTownWarParticipationAllowed()) {
                return TownWarStartResult.failed("war_town_regular_disabled");
            }
            if (attacker == null || defender == null) {
                return TownWarStartResult.failed("town_not_found_generic");
            }
            return TownWarStartResult.failed("war_reason_invalid");
        }
        if (attacker.isNeutral() || defender.isNeutral()) {
            return TownWarStartResult.failed("war_town_neutral_blocked");
        }
        var aNation = attacker.getNationOrNull();
        var dNation = defender.getNationOrNull();
        String reasonError = warReasonFailure(reason, aNation == null ? null : aNation.getName(),
                dNation == null ? null : dNation.getName());
        if (reasonError != null) return TownWarStartResult.failed(reasonError);
        String goalTownName = WarGoalType.forReason(reason) == WarGoalType.CAPTURE_TOWN
                ? defender.getName() : null;
        String goalDefenderId = dNation == null ? "town:" + defender.getName() : dNation.getName();
        String goalError = validateWarGoalSelection(reason, aNation == null ? null : aNation.getName(),
                goalDefenderId, goalTownName, null);
        if (goalError != null) return TownWarStartResult.failed(goalError);
        String a = "town:" + attacker.getName(), d = "town:" + defender.getName();
        if (warLedger.townTruces.get(townTruceKey(a, d)) instanceof Map<?, ?> truce) {
            Map<String, Object> entry = new LinkedHashMap<>();
            truce.forEach((key, value) -> entry.put(String.valueOf(key), value));
            if (engine.getWarTruceClock().isActive(entry)) {
                return TownWarStartResult.failed("war_town_target_truce", defender.getName());
            }
        }
        if (attacker.getNationOrNull() != null && attacker.getNationOrNull() == defender.getNationOrNull()) {
            return TownWarStartResult.failed("war_town_same_nation");
        }
        if (getActiveMilitaryWars(null).stream().anyMatch(w -> w.containsOpposingNations(a, d))) {
            return TownWarStartResult.failed("war_town_already_active", defender.getName());
        }
        MilitaryWarState war = new MilitaryWarState(a, d, Set.of(a), Set.of(d), System.currentTimeMillis());
        war.reason = reason;
        war.setWarGoal(createWarGoalState(reason, aNation == null ? a : aNation.getName(),
                goalDefenderId, goalTownName, null,
                Set.of(a), Set.of(d), false, ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationManager()));
        recordMilitaryWarState(List.of(), war);
        broadcastMilitaryWarStarted(war);
        return TownWarStartResult.success();
    }

    public boolean startTownWar(Town attacker, Town defender, WarReason reason) {
        return startTownWarDetailed(attacker, defender, reason).isStarted();
    }

    public void recordTownWarKill(Player killer, Player victim) {
        Town a = TownyAPI.getInstance().getTown(killer), d = TownyAPI.getInstance().getTown(victim);
        if (a != null && d != null) recordMilitaryWarKill("town:" + a.getName(), "town:" + d.getName());
    }

    public void recordUnifiedWarKill(Player killer, Player victim) {
        Town a = TownyAPI.getInstance().getTown(killer), d = TownyAPI.getInstance().getTown(victim);
        if (a == null || d == null || a.isNeutral() || d.isNeutral()) return;
        double points = getMilitaryWarKillPoints();
        boolean changed = false;
        for (MilitaryWarState war : getActiveMilitaryWars(null)) {
            String killerId = participantIdentity(war, a);
            String victimId = participantIdentity(war, d);
            if (killerId == null || victimId == null || !war.containsOpposingNations(killerId, victimId)) continue;
            if (war.isAttackerNation(killerId)) {
                war.attackerScore += points;
                war.attackerKills.merge(victimId, points, Double::sum);
                if (war.getWarGoal().getType() == WarGoalType.SHOW_HEGEMONY) {
                    war.getWarGoal().recordAttackerDefeat(victim.getUniqueId().toString());
                }
            } else {
                war.defenderScore += points;
                war.defenderKills.merge(victimId, points, Double::sum);
            }
            warLedger.wars.put(war.getWarKey(), war.toMap());
            changed = true;
        }
        if (changed) organizations.saveOrganizations();
    }

    private String participantIdentity(MilitaryWarState war, Town town) {
        String identity = "town:" + town.getName();
        if (war.isAttackerNation(identity) || war.isDefenderNation(identity)) return identity;
        String nation = town.getNationOrNull() == null ? null : town.getNationOrNull().getName();
        return war.isAttackerNation(nation) || war.isDefenderNation(nation) ? nation : null;
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
        organizations.saveOrganizations();
        return true;
    }

    private long getIndependenceWarCycleTicks() {
        long seconds = Math.max(1L, Math.min(86400L, getWarConfiguration().getLong("scoring.cycleSeconds", 300L)));
        return seconds * 20L;
    }

    private double getRebelKillPoints() {
        return getMilitaryWarKillPoints();
    }

    private double getEmpireKillPoints() {
        return getMilitaryWarKillPoints();
    }

    public int getIndependenceWarRespawnLockSeconds() {
        return Math.max(0, Math.min(3600, getWarConfiguration().getInt("scoring.defenderRespawnLockSeconds", 30)));
    }

    FileConfiguration getWarConfiguration() {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getWarConfig();
    }

    public boolean hasActiveIndependenceWarBetween(String firstNation, String secondNation) {
        if (firstNation == null || secondNation == null || firstNation.equalsIgnoreCase(secondNation)) return false;
        return getActiveMilitaryWars(null).stream().anyMatch(war -> war.reason == WarReason.INDEPENDENCE
                && war.containsOpposingNations(firstNation, secondNation));
    }

    public void processPeaceTreatyTributes(NationManager nationManager) {
        if (nationManager != null && PeaceTreatyTributes.collect(nationManager.getAllNations(),
                nationManager::getTownyNation, this::withdrawNationAmount, this::depositNationAmount)) {
            nationManager.saveNations();
        }
    }

    private void recordMilitaryWarState(List<WDEorg> orgs, MilitaryWarState warState) {
        if (warState == null) return;
        warLedger.wars.put(warState.getWarKey(), warState.toMap());
        organizations.saveOrganizations();
        var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
        if (history != null) history.warStarted(warState);
        var warMood = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                .getWarMoodDoctrineManager();
        if (warMood != null) warMood.refreshWarParticipants(warState);
    }

    private Map<String, MilitaryWarState> readActiveMilitaryWars(WDEorg org) {
        return warStateStore.readActiveWars(org);
    }

    private void writeActiveMilitaryWars(WDEorg org, Map<String, MilitaryWarState> wars) {
        warStateStore.writeActiveWars(org, wars);
    }

    private Map<String, PeaceTreatyProposalState> readPeaceTreatyProposals(WDEorg org) {
        return warStateStore.readTreaties(org);
    }

    private void writePeaceTreatyProposals(WDEorg org, Map<String, PeaceTreatyProposalState> proposals) {
        warStateStore.writeTreaties(org, proposals);
    }

    private WarContext resolveWarContext(WDEorg org, String actorNationName, String targetNationName) {
        if (actorNationName == null || targetNationName == null) {
            return null;
        }

        for (MilitaryWarState warState : readActiveMilitaryWars(org).values()) {
            if (organizations.isNationNeutral(actorNationName) || organizations.isNationNeutral(targetNationName)) continue;
            Set<String> attackerSide = warState.getAttackerNationNames();
            Set<String> defenderSide = warState.getDefenderNationNames();
            if (attackerSide.contains(actorNationName) && defenderSide.contains(targetNationName)) {
                return WarContext.military(org, warState, attackerSide, defenderSide);
            }
            if (defenderSide.contains(actorNationName) && attackerSide.contains(targetNationName)) {
                return WarContext.military(org, warState, defenderSide, attackerSide);
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
        if (warState.reason == WarReason.INDEPENDENCE) {
            return actorLeader && targetLeader ? null : "org_peace_treaty_authority_invalid";
        }
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

        for (WDEorg org : organizations.getAllOrganizations()) {
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
        return PeaceTreatyRules.points(clauses, getWarConfiguration(), separatePeace,
                () -> getIdeologicalAlliancePeaceTreatyMultiplier(context, actorNationName));
    }

    private double getIdeologicalAlliancePeaceTreatyMultiplier(WarContext context, String actorNationName) {
        if (context == null || actorNationName == null || context.getWarType() != WarContext.WarType.MILITARY_ALLIANCE) {
            return 1.0D;
        }

        MilitaryWarState warState = context.getMilitaryWarState();
        if (warState == null) {
            return 1.0D;
        }

        WDEorg ideologicalAlliance = organizations.getPrimaryIdeologicalAllianceForNation(actorNationName);
        if (ideologicalAlliance == null) {
            return 1.0D;
        }

        String allianceIdeology = organizations.getIdeologicalAllianceIdeology(ideologicalAlliance);
        String declaredOnLeaderIdeology = organizations.getNationIdeology(warState.getDefenderNationName());
        if ("None".equalsIgnoreCase(allianceIdeology) || "None".equalsIgnoreCase(declaredOnLeaderIdeology)) {
            return 1.0D;
        }

        if (allianceIdeology.equalsIgnoreCase(declaredOnLeaderIdeology)) {
            return organizations.getIdeologicalAllianceSameReligionTreatyMultiplier();
        }

        return organizations.getIdeologicalAllianceHereticTreatyMultiplier();
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

    private int getPeaceTreatyMaxProposalPoints() {
        return Math.max(1, getWarConfiguration().getInt("peaceTreaty.maxProposalPoints", 100));
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

        if (proposerNationName.startsWith("town:") || targetNationName.startsWith("town:")) {
            MilitaryWarState war = context.getMilitaryWarState();
            for (PeaceTreatyClauseState clause : clauses) {
                if (clause.getType() != PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE) {
                    return "org_peace_treaty_invalid_clause";
                }
                if (clause.getType() == PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE
                        && (war == null || war.reason != WarReason.INDEPENDENCE
                        || !war.getDefenderNationName().equalsIgnoreCase(beneficiaryNationName)
                        || warReasonFailure(WarReason.INDEPENDENCE, beneficiaryNationName, concedingNationName) != null)) {
                    return "org_peace_treaty_allow_independence_invalid";
                }
            }
            return null;
        }
        WDEnation beneficiaryNation = nationManager.getNation(beneficiaryNationName);
        WDEnation concedingNation = nationManager.getNation(concedingNationName);
        if (beneficiaryNation == null || concedingNation == null) {
            return "nation_not_found";
        }

        boolean independenceWar = context.getMilitaryWarState() != null
                && context.getMilitaryWarState().reason == WarReason.INDEPENDENCE;
        boolean rebelBeneficiary = independenceWar
                && context.getMilitaryWarState().getDefenderNationName().equalsIgnoreCase(beneficiaryNationName);
        boolean hasAllowIndependence = hasAllowIndependenceClause(clauses);

        int maxReparationPercent = Math.max(1, getWarConfiguration().getInt("peaceTreaty.clauses.reparations.maxPercent", 20));
        for (PeaceTreatyClauseState clause : clauses) {
            switch (clause.getType()) {
                case REPARATIONS -> {
                    int percent = clause.getInt("percent");
                    if (percent <= 0 || percent > maxReparationPercent) {
                        return "org_peace_treaty_invalid_reparations_percent";
                    }
                }
                case TRIBUTE -> {
                    BigDecimal amount = PeaceTreatyTributes.parseAmount(clause.getString("amount"));
                    if (amount.signum() <= 0) {
                        return "org_peace_treaty_tribute_amount_invalid";
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
                    Town town = TownyAPI.getInstance().getTown(townName);
                    if (town == null || town.getNationOrNull() == null
                            || !town.getNationOrNull().getName().equalsIgnoreCase(concedingNationName)) {
                        return "org_peace_treaty_transfer_town_invalid";
                    }
                    if (nationManager.getTownyNation(destinationNationName) == null) {
                        return "org_peace_treaty_transfer_destination_invalid";
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
                    boolean canDissolve = organizations.getMilitaryAlliancesByNation(concedingNationName).stream()
                            .anyMatch(alliance -> concedingNationName.equalsIgnoreCase(alliance.getChairmanNationName()));
                    if (!canDissolve) {
                        return "org_peace_treaty_dissolve_alliance_invalid";
                    }
                }
                case EXPEL_FROM_ALLIANCE -> {
                    boolean canExpel = organizations.getAllOrganizations().stream()
                            .anyMatch(existing -> existing.hasMemberNation(concedingNationName)
                                    && !concedingNationName.equalsIgnoreCase(existing.getChairmanNationName()));
                    if (!canExpel) {
                        return "org_peace_treaty_expel_alliance_invalid";
                    }
                }
                case LIBERATE_SUBJECT -> {
                    String subjectNationName = clause.getString("subjectNation");
                    WDEorg sovereignEmpire = getOrganizationByChairmanSovereignEmpire(concedingNationName);
                    if (sovereignEmpire == null || !organizations.isSovereignSubjectNation(sovereignEmpire, subjectNationName)) {
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
                    if (!independenceWar || !rebelBeneficiary
                            || warReasonFailure(WarReason.INDEPENDENCE, beneficiaryNationName, concedingNationName) != null) {
                        return "org_peace_treaty_allow_independence_invalid";
                    }
                }
                case FORCE_DEMOCRATIZATION -> {
                    MilitaryWarState war = context.getMilitaryWarState();
                    String president = clause.getString("president");
                    Resident candidate = president.isBlank() ? null : TownyUniverse.getInstance().getResident(president);
                    Town candidateTown = getResidentTown(candidate);
                    if (war == null || war.reason != WarReason.EXPORT_REVOLUTION
                            || proposalType != PeaceTreatyProposalState.ProposalType.DEMAND
                            || !war.getAttackerNationName().equalsIgnoreCase(beneficiaryNationName)
                            || candidate == null || candidateTown == null
                            || candidateTown.getNationOrNull() == null
                            || !candidateTown.getNationOrNull().getName().equalsIgnoreCase(concedingNationName)
                            || !candidateTown.isMayor(candidate)
                            || candidate.getUUID().toString().equalsIgnoreCase(war.getWarGoal().getExcludedKingUuid())) {
                        return "org_peace_treaty_democratization_invalid";
                    }
                }
                case DISSOLVE_THREAT_ORGANIZATION -> {
                    MilitaryWarState war = context.getMilitaryWarState();
                    WDEorg targetOrganization = organizations.getAllOrganizations().stream()
                            .filter(existing -> existing.getOrganizationName().equalsIgnoreCase(clause.getString("organization")))
                            .findFirst().orElse(null);
                    if (war == null || war.reason != WarReason.DESTROY_THREAT
                            || targetOrganization == null
                            || !targetOrganization.getOrganizationName().equalsIgnoreCase(
                                    war.getWarGoal().getTargetOrganizationName())
                            || !concedingNationName.equalsIgnoreCase(targetOrganization.getChairmanNationName())
                            || !(organizations.isMilitaryAlliance(targetOrganization) || targetOrganization.isSovereignEmpire())) {
                        return "org_peace_treaty_dissolve_threat_invalid";
                    }
                }
                case FORCE_VASSALIZE -> {
                    if (!canForceVassalize(beneficiaryNationName, concedingNationName, nationManager)
                            || willPeaceTreatyDissolveNation(proposerNationName, targetNationName,
                                    proposalType, clauses, nationManager)
                            || hasAllowIndependence) {
                        return "org_peace_treaty_force_vassalize_invalid";
                    }
                }
                case UNCONDITIONAL_PASSAGE, HUMILIATE -> { }
            }
        }
        return null;
    }

    private boolean executePeaceTreaty(WarContext context, PeaceTreatyProposalState proposal, NationManager nationManager) {
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
        if (proposal.getProposerNationName().startsWith("town:") || proposal.getTargetNationName().startsWith("town:")) {
            if (proposal.getClauses().stream().anyMatch(clause -> clause.getType() == PeaceTreatyClauseState.ClauseType.ALLOW_INDEPENDENCE)) {
                Town town = TownyAPI.getInstance().getTown(beneficiaryNationName.substring(5));
                if (town == null) return false;
                town.removeNation();
                if (town.getNationOrNull() != null) return false;
                town.save();
                townManager.updateTownNation(town.getName(), null);
            }
            endWarByTreaty(context, proposal, nationManager, false);
            return true;
        }
        if (beneficiaryNation == null || concedingNation == null || beneficiaryNationData == null || concedingNationData == null) {
            return false;
        }

        boolean grantsIndependence = false;
        for (PeaceTreatyClauseState clause : proposal.getClauses()) {
            if (clause.getType() == PeaceTreatyClauseState.ClauseType.TRANSFER_TOWN) continue;
            switch (clause.getType()) {
                case REPARATIONS -> applyReparationsClause(concedingNation, beneficiaryNation, clause.getInt("percent"));
                case FORCE_CONVERSION -> {
                    concedingNationData.setReligionName(beneficiaryNationData.getReligionName());
                    concedingNationData.setReligionChosen(true);
                }
                case TRANSFER_TOWN -> { }
                case DISSOLVE_ALLIANCE -> dissolveMilitaryAlliancesByChairman(concedingNationName);
                case EXPEL_FROM_ALLIANCE -> expelNationFromAlliances(concedingNationName);
                case LIBERATE_SUBJECT -> {
                    String subjectNationName = clause.getString("subjectNation");
                    WDEorg sovereignEmpire = getOrganizationByChairmanSovereignEmpire(concedingNationName);
                    if (sovereignEmpire != null) {
                        organizations.removeMemberNationAndAdjust(sovereignEmpire, subjectNationName);
                    }
                }
                case UNCONDITIONAL_PASSAGE -> nationManager.setUnconditionalPassage(concedingNationName, beneficiaryNationName, true);
                case HUMILIATE -> nationManager.addNationHumiliation(concedingNationName, beneficiaryNationName);
                case LOOT_CAPITAL -> applyLootCapitalClause(concedingNationName, beneficiaryNation, nationManager);
                case TRIBUTE -> addPeaceTreatyTributeObligation(concedingNationData,
                        beneficiaryNationName, new BigDecimal(clause.getString("amount")));
                case ALLOW_INDEPENDENCE -> grantsIndependence = true;
                case FORCE_DEMOCRATIZATION -> {
                    long durationDays = Math.max(0L,
                            getWarConfiguration().getLong("warGoals.regimeLock.durationDays", 100L));
                    String clock = getWarConfiguration().getString("warGoals.regimeLock.clock", "REAL_TIME");
                    String world = getWarConfiguration().getString("warGoals.regimeLock.world", "world");
                    if (!nationManager.forceNationDemocracyPresident(concedingNationData,
                            clause.getString("president"), durationDays, clock, world)) {
                        return false;
                    }
                }
                case DISSOLVE_THREAT_ORGANIZATION -> {
                    WDEorg targetOrganization = organizations.getAllOrganizations().stream()
                            .filter(existing -> existing.getOrganizationName().equalsIgnoreCase(clause.getString("organization")))
                            .findFirst().orElse(null);
                    if (targetOrganization == null) return false;
                    organizations.removeOrganization(targetOrganization);
                }
                case FORCE_VASSALIZE -> { }
            }
        }

        nationManager.saveNations();
        // 先处理原首都条款，割让成功后再结束战争，不将失败割让报告为和约生效。
        for (PeaceTreatyClauseState clause : proposal.getClauses()) {
            if (clause.getType() != PeaceTreatyClauseState.ClauseType.TRANSFER_TOWN) continue;
            String townName = clause.getString("town");
            if (!townManager.transferTownToNation(townName, clause.getString("destinationNation"))) {
                plugin.getLogger().severe("Peace treaty town transfer failed: " + townName);
                return false;
            }
        }
        endWarByTreaty(context, proposal, nationManager, grantsIndependence);
        if (grantsIndependence) {
            WDEorg empire = organizations.getSovereignEmpireByNation(beneficiaryNationName);
            if (organizations.isSovereignSubjectNation(empire, beneficiaryNationName)) organizations.removeMemberNationAndAdjust(empire, beneficiaryNationName);
        }
        if (proposal.getClauses().stream().anyMatch(clause ->
                clause.getType() == PeaceTreatyClauseState.ClauseType.FORCE_VASSALIZE)
                && !forceVassalize(beneficiaryNationName, concedingNationName, nationManager)) {
            plugin.getLogger().severe("Peace treaty vassalization failed: " + concedingNationName);
            return false;
        }
        if (nationManager.getTownyNation(concedingNationName) == null) {
            for (WDEorg organization : organizations.getAllOrganizations()) {
                if (organization.hasMemberNation(concedingNationName)) {
                    organizations.removeMemberNationAndAdjust(organization, concedingNationName);
                }
            }
        }
        return true;
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
            organizations.saveOrganizations();
            return;
        }

    }

    void clearWarsForNeutralNation(String nationName) {
        Map<String, MilitaryWarState> affectedWars = new LinkedHashMap<>();
        for (WDEorg org : warContexts()) {
            for (MilitaryWarState war : getActiveMilitaryWars(org)) {
                if (war.isAttackerNation(nationName) || war.isDefenderNation(nationName)) {
                    affectedWars.put(war.getWarKey(), war);
                }
            }
        }
        for (MilitaryWarState war : affectedWars.values()) {
            if (war.isWarLeader(nationName)) {
                clearMilitaryWarAcrossOrganizations(war.getWarKey());
            } else {
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
    }

    void clearWarProposalsForNeutralNation(WDEorg org, String nationName) {
        Map<String, MilitaryWarProposalState> proposals = readMilitaryWarProposals(org);
        proposals.entrySet().removeIf(entry -> nationName.equalsIgnoreCase(entry.getValue().getProposerNationName())
                || nationName.equalsIgnoreCase(entry.getValue().getTargetNationName()));
        for (MilitaryWarProposalState proposal : proposals.values()) {
            proposal.getVotes().keySet().removeIf(name -> nationName.equalsIgnoreCase(name));
        }
        writeMilitaryWarProposals(org, proposals);
    }

    private void clearMilitaryWarAcrossOrganizations(String warKey) {
        MilitaryWarState endedWar = readActiveMilitaryWars(null).get(warKey);
        warLedger.wars.remove(warKey);
        warLedger.treaties.remove(warKey);
        for (WDEorg existing : organizations.getAllOrganizations()) {
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
            var history = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getNationHistoryManager();
            if (history != null) history.warEnded(endedWar);
            broadcastMilitaryWarEnded(endedWar);
            var warMood = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                    .getWarMoodDoctrineManager();
            if (warMood != null) warMood.recordEconomicWarEnded(endedWar);
        }
    }

    private void removeNationFromMilitaryWar(String warKey, String exitingNationName) {
        boolean warStillActive = false;
        boolean exitingSideHadEconomicTerms = false;
        MilitaryWarState warBeforeRemoval = null;
        for (WDEorg existing : warContexts()) {
            Map<String, MilitaryWarState> wars = readActiveMilitaryWars(existing);
            MilitaryWarState state = wars.get(warKey);
            if (state == null) {
                continue;
            }

            boolean exitingAttacker = state.isAttackerNation(exitingNationName);
            boolean exitingDefender = state.isDefenderNation(exitingNationName);
            MilitaryWarState snapshot = MilitaryWarState.fromMap(state.getWarKey(), state.toMap());
            if (state.removeParticipant(exitingNationName)) {
                if (warBeforeRemoval == null) warBeforeRemoval = snapshot;
                exitingSideHadEconomicTerms |= exitingAttacker && snapshot.hasAttackerEconomicTermsDemanded()
                        || exitingDefender && snapshot.hasDefenderEconomicTermsDemanded();
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
        var warMood = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                .getWarMoodDoctrineManager();
        if (warMood != null) {
            if (!warStillActive && warBeforeRemoval != null) {
                warMood.recordEconomicWarEnded(warBeforeRemoval);
            } else if (warStillActive && exitingSideHadEconomicTerms) {
                warMood.recordEconomicWarEndedForParticipant(exitingNationName, true);
            }
            warMood.refreshAllTowns();
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
        for (WDEorg org : organizations.getAllOrganizations()) {
            if (organizations.isMilitaryAlliance(org) && chairmanNationName.equalsIgnoreCase(org.getChairmanNationName())) {
                toRemove.add(org);
            }
        }
        for (WDEorg org : toRemove) {
            organizations.removeOrganization(org);
        }
        if (!toRemove.isEmpty()) {
            organizations.saveOrganizations();
        }
    }

    private void expelNationFromAlliances(String nationName) {
        boolean changed = false;
        for (WDEorg org : organizations.getAllOrganizations()) {
            if (!org.hasMemberNation(nationName) || nationName.equalsIgnoreCase(org.getChairmanNationName())) {
                continue;
            }
            organizations.removeMemberNationAndAdjust(org, nationName);
            changed = true;
        }
        if (changed) {
            organizations.saveOrganizations();
        }
    }

    private WDEorg getOrganizationByChairmanSovereignEmpire(String chairmanNationName) {
        for (WDEorg org : organizations.getAllOrganizations()) {
            if (org.isSovereignEmpire() && chairmanNationName.equalsIgnoreCase(org.getChairmanNationName())) {
                return org;
            }
        }
        return null;
    }

    private boolean canForceVassalize(String beneficiary, String conceding, NationManager nations) {
        WDEorg empire = getOrganizationByChairmanSovereignEmpire(beneficiary);
        if (empire == null || beneficiary.equalsIgnoreCase(conceding) || empire.hasMemberNation(conceding)
                || nations.getTownyNation(conceding) == null
                || getOrganizationByChairmanSovereignEmpire(conceding) != null) return false;
        return true;
    }

    private boolean forceVassalize(String beneficiary, String conceding, NationManager nations) {
        if (!canForceVassalize(beneficiary, conceding, nations)) return false;
        WDEorg empire = getOrganizationByChairmanSovereignEmpire(beneficiary);
        for (WDEorg previous : organizations.getAllOrganizations()) {
            if (previous.isSovereignEmpire() && previous.hasMemberNation(conceding)) {
                organizations.removeMemberNationAndAdjust(previous, conceding);
            }
        }
        if (!organizations.addMemberNation(empire, conceding)) return false;
        Map<String, SovereignSubjectState> states = organizations.readSovereignSubjectStates(empire);
        states.put(conceding, new SovereignSubjectState(SovereignSubjectState.AutonomyTier.PUPPET,
                organizations.getDefaultSubjectSuppression(), false, false));
        organizations.writeSovereignSubjectStates(empire, states);
        organizations.saveOrganizations();
        return true;
    }

    private void addPeaceTreatyTributeObligation(WDEnation concedingNation, String beneficiaryNationName,
                                                  BigDecimal fixedAmount) {
        PeaceTreatyTributes.add(concedingNation, beneficiaryNationName, fixedAmount, System.currentTimeMillis());
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
        String reasonError = warReasonFailure(proposal.reason, proposal.getProposerNationName(), proposal.getTargetNationName());
        if (reasonError != null) {
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) proposals.remove(key);
            writeMilitaryWarProposals(org, proposals);
            organizations.saveOrganizations();
            return MilitaryWarVoteResult.invalid(reasonError);
        }
        if (organizations.isNationNeutral(proposal.getProposerNationName()) || organizations.isNationNeutral(proposal.getTargetNationName())) {
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) proposals.remove(key);
            writeMilitaryWarProposals(org, proposals);
            organizations.saveOrganizations();
            return MilitaryWarVoteResult.peaceProtected(proposal);
        }
        double totalWeight = 0.0D;
        double yesWeight = 0.0D;
        double noWeight = 0.0D;

        for (String memberNationName : org.getMemberNationNames()) {
            if (organizations.isNationNeutral(memberNationName)) continue;
            double weight = organizations.getNationWeight(org, memberNationName, nationManager);
            totalWeight += weight;

            Boolean vote = organizations.findVoteIgnoreCase(proposal.getVotes(), memberNationName);
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
                organizations.saveOrganizations();
                return MilitaryWarVoteResult.truce();
            }
            if (nationManager != null && nationManager.isNationPeaceProtected(proposal.getTargetNationName())) {
                String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
                if (key != null) {
                    proposals.remove(key);
                    writeMilitaryWarProposals(org, proposals);
                }
                organizations.saveOrganizations();
                return MilitaryWarVoteResult.peaceProtected(proposal);
            }
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) {
                proposals.remove(key);
                writeMilitaryWarProposals(org, proposals);
            }
            MilitaryWarResult warResult = handleMilitaryAllianceWar(proposal.getProposerNationName(), proposal.getTargetNationName(),
                    nationManager, proposal.reason, proposal.getGoalTownName(), proposal.getGoalOrganizationName());
            if (warResult.getHostilePairsApplied() == 0) return MilitaryWarVoteResult.invalid(warResult.getFailureMessageKey());
            nationManager.saveNations();
            organizations.saveOrganizations();
            return MilitaryWarVoteResult.approved(warResult, proposal);
        }

        if (noWeight >= totalWeight / 2.0D) {
            String key = findMilitaryWarProposalKey(proposals, proposal.getTargetNationName());
            if (key != null) {
                proposals.remove(key);
                writeMilitaryWarProposals(org, proposals);
            }
            organizations.saveOrganizations();
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

    private IndependenceWarResolutionResult resolveIndependenceWar(WDEorg org, Map<String, IndependenceWarState> wars, IndependenceWarState state, IndependenceWarOutcome outcome) {
        String key = findIndependenceWarKey(wars, state.getRebelNationName());
        if (key != null) {
            wars.remove(key);
        }

        if (outcome == IndependenceWarOutcome.SUCCESS) {
            boolean removedChairman = org.getChairmanNationName() != null && org.getChairmanNationName().equalsIgnoreCase(state.getRebelNationName());
            org.removeMemberNation(state.getRebelNationName());
            organizations.removeSovereignSubjectState(org, state.getRebelNationName());
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
        Nation nation = organizations.resolveTownyNation(nationName);
        if (nation == null) return Map.of();
        for (Town town : nation.getTowns()) population.put(town.getName(), town.getResidents().size());
        return WarOccupation.weights(population, nation.getCapital() == null ? null : nation.getCapital().getName());
    }

    private void scoreRegularOccupation(MilitaryWarState war, Map<String, Map<String, int[]>> census) {
        int defenderNations = Math.max(1, war.defenderNationNames.size());
        int attackerNations = Math.max(1, war.attackerNationNames.size());
        for (String owner : war.defenderNationNames) {
            for (var town : occupationWeightsForSide(owner, war.attackerNationNames).entrySet()) {
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
            for (var town : occupationWeightsForSide(owner, war.defenderNationNames).entrySet()) {
                // On attacker territory, an empty town earns neither side points.
                if (countOccupiers(town.getKey(), war.defenderNationNames, war.attackerNationNames, census, true) == 0) continue;
                double defendPoints = town.getValue() * (1D - occupancyShare(town.getKey(), war, census));
                war.defenderOccupation.merge(owner, defendPoints, Double::sum);
                war.defenderScore += defendPoints / attackerNations;
            }
        }
    }

    private Map<String, Double> occupationWeightsForSide(String owner, Set<String> opposing) {
        if (owner.startsWith("town:") || opposing.stream().noneMatch(id -> id.startsWith("town:"))) {
            return getNationOccupationWeights(owner);
        }
        Nation nation = organizations.resolveTownyNation(owner);
        if (nation == null) return Map.of();
        Map<String, Integer> population = new LinkedHashMap<>();
        for (Town town : nation.getTowns()) {
            if (!opposing.contains("town:" + town.getName())) population.put(town.getName(), town.getNumResidents());
        }
        return com.thatguycy.worlddynamicsengine.manager.war.WarOccupation.weights(population,
                nation.getCapital() == null ? null : nation.getCapital().getName());
    }

    private double occupancyShare(String town, MilitaryWarState war, Map<String, Map<String, int[]>> census) {
        return WarOccupation.attackerShare(countOccupiers(town, war.attackerNationNames, war.defenderNationNames, census, false),
                countOccupiers(town, war.defenderNationNames, war.attackerNationNames, census, true));
    }

    private int countOccupiers(String town, Set<String> side, Map<String, Map<String, int[]>> census, boolean defenders) {
        int total = 0;
        for (var entry : census.getOrDefault(town, Map.of()).entrySet()) {
            if (side.stream().anyMatch(n -> n.equalsIgnoreCase(entry.getKey()))) total += entry.getValue()[defenders ? 1 : 0];
        }
        return total;
    }

    private int countOccupiers(String town, Set<String> side, Set<String> opposing,
                               Map<String, Map<String, int[]>> census, boolean defenders) {
        int total = countOccupiers(town, side, census, defenders);
        for (String identity : opposing) {
            if (!identity.startsWith("town:")) continue;
            Town rebelTown = TownyAPI.getInstance().getTown(identity.substring(5));
            if (rebelTown != null && rebelTown.getNationOrNull() != null
                    && side.contains(rebelTown.getNationOrNull().getName())) {
                int[] count = census.getOrDefault(town, Map.of()).get(identity);
                if (count != null) total -= count[defenders ? 1 : 0];
            }
        }
        return Math.max(0, total);
    }

    public boolean isWarDefender(Player player) {
        Town home = TownyAPI.getInstance().getTown(player);
        if (home == null) return false;
        String nation = home.getNationOrNull() == null ? null : home.getNationOrNull().getName();
        for (MilitaryWarState war : readActiveMilitaryWars(null).values()) {
            if (war.isAttackerNation("town:" + home.getName())) continue;
            if (war.isDefenderNation(nation) || war.isDefenderNation("town:" + home.getName())) return true;
        }
        for (WDEorg org : organizations.getAllOrganizations()) for (IndependenceWarState war : getActiveIndependenceWars(org)) {
            if (war.getRebelNationName().equalsIgnoreCase(nation)) return true;
        }
        return false;
    }

    public boolean validWarReason(WarReason reason, String target) {
        if (reason == null) return false;
        return reason != WarReason.DESTROY_THREAT || organizations.getAllOrganizations().stream()
                .anyMatch(org -> (organizations.isMilitaryAlliance(org) || org.isSovereignEmpire())
                        && org.getChairmanNationName() != null && org.getChairmanNationName().equalsIgnoreCase(target));
    }

    public String warReasonFailure(WarReason reason, String actor, String target) {
        if (reason == WarReason.DESTROY_THREAT && !validWarReason(reason, target)) {
            return "war_reason_requires_threat_chairman";
        }
        if (!validWarReason(reason, target)) return "war_reason_invalid";
        var engine = (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        NationManager nations = engine.getNationManager();
        WDEnation attacker = nations.getNation(actor), defender = nations.getNation(target);
        return switch (reason) {
            case INDEPENDENCE -> {
                if (actor != null && actor.startsWith("town:")) {
                    Town town = TownyAPI.getInstance().getTown(actor.substring(5));
                    yield town != null && town.getNationOrNull() != null
                            && town.getNationOrNull().getName().equalsIgnoreCase(target)
                            ? null : "war_reason_independence_invalid";
                }
                WDEorg empire = organizations.getSovereignEmpireByNation(actor);
                yield organizations.isSovereignSubjectNation(empire, actor) && empire.getChairmanNationName().equalsIgnoreCase(target)
                        ? null : "war_reason_independence_invalid";
            }
            case HOLY_WAR -> {
                String ownIdeology = attacker == null ? null : attacker.getReligionName();
                String enemyIdeology = defender == null ? null : defender.getReligionName();
                boolean eligible = ownIdeology != null && !ownIdeology.isBlank() && !"None".equalsIgnoreCase(ownIdeology)
                        && enemyIdeology != null && !enemyIdeology.isBlank() && !"None".equalsIgnoreCase(enemyIdeology)
                        && !ownIdeology.equalsIgnoreCase(enemyIdeology);
                yield eligible ? null : "war_reason_requires_different_ideologies";
            }
            case EXPORT_REVOLUTION -> attacker != null && "DEMOCRACY".equalsIgnoreCase(attacker.getGovernmentType())
                    ? null : "war_reason_requires_democracy";
            case RECONQUEST -> {
                Nation enemy = nations.getTownyNation(target);
                boolean eligible = actor != null && enemy != null && enemy.getTowns().stream()
                        .map(town -> townManager.getTown(town.getName())).filter(java.util.Objects::nonNull)
                        .anyMatch(town -> com.thatguycy.worlddynamicsengine.manager.war.TownOwnershipHistory.qualifies(engine, town, actor));
                yield eligible ? null : "war_reason_requires_lost_town";
            }
            case TRADE_CONFLICT -> organizations.isNationTradeSanctionedBy(actor, target) ? null : "war_reason_requires_trade_blockade";
            case HUMILIATE_RIVAL -> hostileToward(attacker, defender, nations) && hostileToward(defender, attacker, nations)
                    ? null : "war_reason_requires_mutual_hostility";
            default -> null;
        };
    }

    public List<String> getWarGoalTownOptions(WarReason reason, String attackerNationName, String targetNationName) {
        Nation target = organizations.resolveTownyNation(targetNationName);
        if (target == null || WarGoalType.forReason(reason) != WarGoalType.CAPTURE_TOWN) return List.of();
        com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
        return target.getTowns().stream()
                .map(Town::getName)
                .filter(name -> {
                    if (reason != WarReason.RECONQUEST) return true;
                    WDEtown record = townManager.getTown(name);
                    return record != null && com.thatguycy.worlddynamicsengine.manager.war.TownOwnershipHistory
                            .qualifies(engine, record, attackerNationName);
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> getThreatOrganizationOptions(String targetNationName) {
        if (targetNationName == null || targetNationName.isBlank()) return List.of();
        return organizations.getAllOrganizations().stream()
                .filter(org -> (organizations.isMilitaryAlliance(org) || org.isSovereignEmpire())
                        && targetNationName.equalsIgnoreCase(org.getChairmanNationName()))
                .map(WDEorg::getOrganizationName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> getDemocratizationCandidates(WDEnation nation, String excludedKingUuid) {
        Nation townyNation = organizations.resolveTownyNation(nation.getNationName());
        if (townyNation == null) return List.of();
        return nationManagerCandidates(nation).stream()
                .filter(name -> {
                    Resident resident = TownyUniverse.getInstance().getResident(name);
                    Town residentTown = getResidentTown(resident);
                    return resident != null && residentTown != null
                            && residentTown.getNationOrNull() != null
                            && residentTown.getNationOrNull().getName().equalsIgnoreCase(nation.getNationName())
                            && residentTown.isMayor(resident)
                            && (excludedKingUuid == null || excludedKingUuid.isBlank()
                                    || !resident.getUUID().toString().equalsIgnoreCase(excludedKingUuid));
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Town getResidentTown(Resident resident) {
        if (resident == null || !resident.hasTown()) return null;
        try {
            return resident.getTown();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> nationManagerCandidates(WDEnation nation) {
        return ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin)
                .getNationManager().getDemocracyLeadershipCandidates(nation);
    }

    public String validateWarGoalSelection(WarReason reason, String attackerNationName,
            String targetNationName, String goalTownName, String goalOrganizationName) {
        WarGoalType goalType = WarGoalType.forReason(reason);
        if (goalType == WarGoalType.NONE) {
            return reason == WarReason.TRADE_CONFLICT ? null : "war_goal_reason_unsupported";
        }
        if (goalType == WarGoalType.CAPTURE_TOWN) {
            if (goalTownName == null || goalTownName.isBlank()) return "war_goal_town_required";
            Town goalTown = TownyAPI.getInstance().getTown(goalTownName);
            if (goalTown == null) return "war_goal_town_not_target";
            if (targetNationName != null && targetNationName.startsWith("town:")) {
                if (!goalTown.getName().equalsIgnoreCase(targetNationName.substring(5))) {
                    return "war_goal_town_not_target";
                }
            } else if (goalTown.getNationOrNull() == null
                    || !goalTown.getNationOrNull().getName().equalsIgnoreCase(targetNationName)) {
                return "war_goal_town_not_target";
            }
            if (reason == WarReason.RECONQUEST) {
                WDEtown town = townManager.getTown(goalTown.getName());
                com.thatguycy.worlddynamicsengine.WorldDynamicsEngine engine =
                        (com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin;
                if (town == null || !com.thatguycy.worlddynamicsengine.manager.war.TownOwnershipHistory
                        .qualifies(engine, town, attackerNationName)) {
                    return "war_goal_town_not_reconquest";
                }
            }
            return null;
        }
        if (goalType == WarGoalType.OCCUPY_CAPITAL) {
            String capitalOwner = reason == WarReason.INDEPENDENCE
                    ? attackerNationName : targetNationName;
            if (capitalOwner != null && capitalOwner.startsWith("town:")) {
                return TownyAPI.getInstance().getTown(capitalOwner.substring(5)) == null
                        ? "war_goal_target_capital_missing" : null;
            }
            Nation target = organizations.resolveTownyNation(capitalOwner);
            return target == null || target.getCapital() == null
                    ? "war_goal_target_capital_missing" : null;
        }
        if (reason == WarReason.DESTROY_THREAT) {
            List<String> candidates = getThreatOrganizationOptions(targetNationName);
            if (goalOrganizationName == null || goalOrganizationName.isBlank()) {
                return candidates.size() == 1 ? null : "war_goal_organization_required";
            }
            WDEorg organization = organizations.getOrganization(goalOrganizationName);
            return organization != null && (organizations.isMilitaryAlliance(organization) || organization.isSovereignEmpire())
                    && targetNationName.equalsIgnoreCase(organization.getChairmanNationName())
                    ? null : "war_goal_organization_not_target";
        }
        return null;
    }

    private WarGoalState createWarGoalState(WarReason reason, String attackerNationName,
            String targetNationName, String goalTownName, String goalOrganizationName,
            Set<String> attackerSide, Set<String> defenderSide, boolean forcedTreatyAllowed,
            NationManager nationManager) {
        WarGoalType type = WarGoalType.forReason(reason);
        if (type == WarGoalType.NONE) return WarGoalState.none();

        String targetTown = goalTownName;
        String targetNationNameForGoal = targetNationName;
        if (type == WarGoalType.OCCUPY_CAPITAL) {
            String capitalOwner = reason == WarReason.INDEPENDENCE
                    ? attackerNationName : targetNationName;
            if (capitalOwner != null && capitalOwner.startsWith("town:")) {
                targetTown = capitalOwner.substring(5);
            } else {
                Nation target = organizations.resolveTownyNation(capitalOwner);
                targetTown = target == null || target.getCapital() == null ? "" : target.getCapital().getName();
            }
            if (reason == WarReason.INDEPENDENCE) targetNationNameForGoal = attackerNationName;
        }
        if (reason != WarReason.INDEPENDENCE && attackerNationName != null && attackerNationName.startsWith("town:")
                && targetNationName != null && !targetNationName.startsWith("town:")) {
            targetNationNameForGoal = targetNationName;
        }
        String threatOrganization = goalOrganizationName;
        if (reason == WarReason.DESTROY_THREAT && (threatOrganization == null || threatOrganization.isBlank())) {
            List<String> candidates = getThreatOrganizationOptions(targetNationName);
            if (candidates.size() == 1) threatOrganization = candidates.getFirst();
        }

        Nation defender = organizations.resolveTownyNation(targetNationName);
        String excludedKingUuid = defender == null || defender.getKing() == null
                ? "" : defender.getKing().getUUID().toString();
        long enemyPopulation = 0L;
        for (String participant : defenderSide) {
            if (participant.startsWith("town:")) {
                Town town = TownyAPI.getInstance().getTown(participant.substring(5));
                if (town != null) enemyPopulation += town.getResidents().size();
            } else if (nationManager != null) {
                enemyPopulation += Math.max(0, nationManager.getNationPopulation(participant));
            }
        }
        if (enemyPopulation <= 0L && targetNationNameForGoal != null && nationManager != null) {
            enemyPopulation = Math.max(0, nationManager.getNationPopulation(targetNationNameForGoal));
        }
        return new WarGoalState(type, targetTown, targetNationNameForGoal, threatOrganization,
                excludedKingUuid, enemyPopulation, forcedTreatyAllowed);
    }

    private boolean hostileToward(WDEnation actor, WDEnation target, NationManager nations) {
        if (actor == null || target == null) return false;
        Nation a = nations.getTownyNation(actor.getNationName()), d = nations.getTownyNation(target.getNationName());
        return a != null && d != null && a.hasEnemy(d) || actor.getDiplomaticRelations().entrySet().stream()
                .anyMatch(entry -> entry.getKey().equalsIgnoreCase(target.getNationName()) && "unfriendly".equalsIgnoreCase(entry.getValue()));
    }

    private void announceIndependenceWarResolution(WDEorg org, IndependenceWarResolutionResult result) {
        String messageKey = switch (result.getOutcome()) {
            case SUCCESS -> "org_independence_war_success";
            case FAILURE -> "org_independence_war_failure";
            case STALEMATE -> "org_independence_war_stalemate";
        };
        String message = organizations.msg(messageKey,
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
                organizations.msg(war.getReason().messageKey()));
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
        Bukkit.broadcastMessage(organizations.msg(key, args));
    }

    private String formatWarScore(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    public boolean isNationInActiveWar(String nationName) {
        if (nationName == null || nationName.isBlank()) return false;
        Nation nation = organizations.resolveTownyNation(nationName);
        if (nation != null && nation.hasActiveWar()) return true;
        // Inspect raw participation, not hostility, which may be filtered by neutrality.
        for (MilitaryWarState war : getActiveMilitaryWars(null)) {
                if (war.isAttackerNation(nationName) || war.isDefenderNation(nationName)) return true;
        }
        return isNationInActiveIndependenceWar(nationName);
    }

    public boolean isNationInActiveIndependenceWar(String nationName) {
        if (getActiveMilitaryWars(null).stream().anyMatch(war -> war.reason == WarReason.INDEPENDENCE
                && (war.isAttackerNation(nationName) || war.isDefenderNation(nationName)))) return true;
        if (nationName == null) {
            return false;
        }

        for (WDEorg org : organizations.getAllOrganizations()) {
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

    public record WarGoalDisplay(WarGoalType type, String target, long progress, long required,
                                 long holdRemainingMillis, boolean conditionMet, boolean forcedReady,
                                 boolean forcedTreatyAllowed) {}

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

    /**
     * 菜单候选项复用提交校验，参数候选也必须能组成合法条款。
     */
    public record PeaceTreatyMenuClauseOption(PeaceTreatyClauseState.ClauseType type,
                                              List<String> towns, List<String> destinations,
                                              List<String> subjects, int maxPercent) {}

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

    /**
     * 保存独立战争双方、累计成绩和周期状态，支持跨重启继续结算。
     */
    public static class IndependenceWarState {
        final Map<String, Double> rebelOccupation = new LinkedHashMap<>();
        final Map<String, Double> empireOccupation = new LinkedHashMap<>();
        final Map<String, Double> rebelKills = new LinkedHashMap<>();
        final Map<String, Double> empireKills = new LinkedHashMap<>();
        final String rebelNationName;
        double rebelScore;
        double empireScore;
        private int elapsedCycles;
        private boolean chairmanSurrendered;
        final long startedAt;

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
        WarReason reason = WarReason.CONQUEST;
        private final String targetNationName;
        private final String proposerNationName;
        private final String goalTownName;
        private final String goalOrganizationName;
        private final Map<String, Boolean> votes;
        private final long createdAt;

        public MilitaryWarProposalState(String targetNationName, String proposerNationName) {
            this(targetNationName, proposerNationName, null, null, new LinkedHashMap<>(), System.currentTimeMillis());
        }

        public MilitaryWarProposalState(String targetNationName, String proposerNationName, Map<String, Boolean> votes, long createdAt) {
            this(targetNationName, proposerNationName, null, null, votes, createdAt);
        }

        public MilitaryWarProposalState(String targetNationName, String proposerNationName,
                String goalTownName, String goalOrganizationName) {
            this(targetNationName, proposerNationName, goalTownName, goalOrganizationName,
                    new LinkedHashMap<>(), System.currentTimeMillis());
        }

        private MilitaryWarProposalState(String targetNationName, String proposerNationName,
                String goalTownName, String goalOrganizationName, Map<String, Boolean> votes, long createdAt) {
            this.targetNationName = targetNationName;
            this.proposerNationName = proposerNationName;
            this.goalTownName = goalTownName == null ? "" : goalTownName;
            this.goalOrganizationName = goalOrganizationName == null ? "" : goalOrganizationName;
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
            Object rawGoalTown = map.get("goalTownName");
            Object rawGoalOrganization = map.get("goalOrganizationName");
            MilitaryWarProposalState state = new MilitaryWarProposalState(targetNationName, proposerNationName,
                    rawGoalTown == null ? null : String.valueOf(rawGoalTown),
                    rawGoalOrganization == null ? null : String.valueOf(rawGoalOrganization), votes, createdAt);
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
            map.put("goalTownName", goalTownName);
            map.put("goalOrganizationName", goalOrganizationName);
            return map;
        }

        public WarReason getReason() { return reason; }

        public String getTargetNationName() {
            return targetNationName;
        }

        public String getProposerNationName() {
            return proposerNationName;
        }

        public String getGoalTownName() {
            return goalTownName;
        }

        public String getGoalOrganizationName() {
            return goalOrganizationName;
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
        private String failureMessageKey;

        private MilitaryWarVoteResult(Status status, MilitaryWarProposalState proposal, MilitaryWarResult warResult) {
            this.status = status;
            this.proposal = proposal;
            this.warResult = warResult;
        }

        public static MilitaryWarVoteResult invalid() {
            return new MilitaryWarVoteResult(Status.INVALID, null, null);
        }

        public static MilitaryWarVoteResult invalid(String key) {
            var result = invalid();
            result.failureMessageKey = key;
            return result;
        }

        public String getFailureMessageKey() { return failureMessageKey; }

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
        boolean attackerAllianceInvolved;
        boolean defenderAllianceInvolved;
        int hostilePairsApplied;
        final List<String> removedFromAttackerAlliances = new ArrayList<>();
        private String failureMessageKey = "war_declaration_blocked";
        private Object[] failureMessageArgs = new Object[0];

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

        public String getFailureMessageKey() {
            return failureMessageKey;
        }

        public Object[] getFailureMessageArgs() {
            return failureMessageArgs;
        }

        public boolean isSuccess() {
            return hostilePairsApplied > 0;
        }

        public boolean hasAllianceEffect() {
            return attackerAllianceInvolved || defenderAllianceInvolved;
        }

        void fail(String messageKey, Object... args) {
            this.failureMessageKey = messageKey == null || messageKey.isBlank() ? "war_declaration_blocked" : messageKey;
            this.failureMessageArgs = args == null ? new Object[0] : args;
        }
    }

    public static class TownWarStartResult {
        private final boolean started;
        private final String failureMessageKey;
        private final Object[] failureMessageArgs;

        private TownWarStartResult(boolean started, String failureMessageKey, Object[] failureMessageArgs) {
            this.started = started;
            this.failureMessageKey = failureMessageKey;
            this.failureMessageArgs = failureMessageArgs == null ? new Object[0] : failureMessageArgs;
        }

        public static TownWarStartResult success() {
            return new TownWarStartResult(true, null, new Object[0]);
        }

        public static TownWarStartResult failed(String messageKey, Object... args) {
            return new TownWarStartResult(false, messageKey, args);
        }

        public boolean isStarted() {
            return started;
        }

        public String getFailureMessageKey() {
            return failureMessageKey;
        }

        public Object[] getFailureMessageArgs() {
            return failureMessageArgs;
        }
    }

    /**
     * 保存常规战争参与方、累计占领和击杀分账，以及退出成员等长期状态。
     */
    public static class MilitaryWarState {
        WarReason reason = WarReason.CONQUEST;
        private WarGoalState warGoal = WarGoalState.none();
        final Map<String, Double> attackerOccupation = new LinkedHashMap<>();
        final Map<String, Double> defenderOccupation = new LinkedHashMap<>();
        final Map<String, Double> attackerKills = new LinkedHashMap<>();
        final Map<String, Double> defenderKills = new LinkedHashMap<>();
        private final String warKey;
        private final String attackerLeaderNationName;
        private final String defenderLeaderNationName;
        final Set<String> attackerNationNames;
        final Set<String> defenderNationNames;
        double attackerScore;
        double defenderScore;
        boolean attackerEconomicTermsDemanded;
        boolean defenderEconomicTermsDemanded;
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
            state.attackerEconomicTermsDemanded = Boolean.TRUE.equals(map.get("attackerEconomicTermsDemanded"));
            state.defenderEconomicTermsDemanded = Boolean.TRUE.equals(map.get("defenderEconomicTermsDemanded"));
            WarReason reason = WarReason.parse(String.valueOf(map.get("reason")));
            if (reason != null) state.reason = reason;
            state.warGoal = WarGoalState.fromMap(map.get("warGoal"));
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
            map.put("attackerEconomicTermsDemanded", attackerEconomicTermsDemanded);
            map.put("defenderEconomicTermsDemanded", defenderEconomicTermsDemanded);
            map.put("startedAt", startedAt);
            map.put("reason", reason.name());
            map.put("warGoal", warGoal.toMap());
            map.put("attackerOccupation", new LinkedHashMap<>(attackerOccupation));
            map.put("defenderOccupation", new LinkedHashMap<>(defenderOccupation));
            map.put("attackerKills", new LinkedHashMap<>(attackerKills));
            map.put("defenderKills", new LinkedHashMap<>(defenderKills));
            return map;
        }

        public WarReason getReason() { return reason; }

        public WarGoalState getWarGoal() {
            return warGoal;
        }

        public void setWarGoal(WarGoalState warGoal) {
            this.warGoal = warGoal == null ? WarGoalState.none() : warGoal;
        }

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

        public boolean hasAttackerEconomicTermsDemanded() {
            return attackerEconomicTermsDemanded;
        }

        public boolean hasDefenderEconomicTermsDemanded() {
            return defenderEconomicTermsDemanded;
        }

        public boolean markEconomicTermsDemanded(String beneficiaryNationName) {
            if (isAttackerNation(beneficiaryNationName)) {
                attackerEconomicTermsDemanded = true;
                return true;
            }
            if (isDefenderNation(beneficiaryNationName)) {
                defenderEconomicTermsDemanded = true;
                return true;
            }
            return false;
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
            FORCE_VASSALIZE,
            ALLOW_INDEPENDENCE,
            FORCE_DEMOCRATIZATION,
            DISSOLVE_THREAT_ORGANIZATION;

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
                    case "forcevassalize", "force_vassalize", "强制附庸" -> FORCE_VASSALIZE;
                    case "allowindependence", "allow_independence", "grantindependence", "允许独立" -> ALLOW_INDEPENDENCE;
                    case "forcedemocratization", "force_democratization", "强制民主化" -> FORCE_DEMOCRATIZATION;
                    case "dissolvethreatorganization", "dissolve_threat_organization", "取缔非法组织" -> DISSOLVE_THREAT_ORGANIZATION;
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

    static String buildWarKey(String attackerNationName, String defenderNationName) {
        return "military:" + attackerNationName + ":" + defenderNationName;
    }

    private static String parseWarKeyPart(String warKey, int index) {
        if (warKey == null) {
            return "";
        }
        String[] parts = warKey.split(":");
        return parts.length > index ? parts[index] : "";
    }
}
