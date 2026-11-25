package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.manager.organization.WarManager;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;

/** Maintains each town's war and prolonged-peace mood state. */
public final class WarMoodDoctrineManager {
    private static final String DOCTRINE_ID = "namespace:wde/war";
    private static final String RAIDING_DOCTRINE_ID = "namespace:wder/raiding";
    private static final String CONFIG_PATH = "mood.doctrineActions.war.";
    private static final String RAIDING_CONFIG_PATH = "mood.doctrineActions.raiding.";
    private static final String MOOD_SOURCE_PREFIX = "doctrine|" + DOCTRINE_ID + "|";
    private static final String RAIDING_MOOD_SOURCE_PREFIX = "doctrine|" + RAIDING_DOCTRINE_ID + "|";
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final WorldDynamicsEngine plugin;
    private final Set<UUID> residentsWithPersistentMood = new HashSet<>();

    public WarMoodDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        loadExistingMoodModifiers();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllTowns, 20L, 1200L);
        Bukkit.getScheduler().runTask(plugin, this::refreshAllTowns);
    }

    public void refreshWarParticipants(WarManager.MilitaryWarState war) {
        if (war == null) return;
        Map<String, Town> towns = new LinkedHashMap<>();
        for (String participant : war.getAttackerNationNames()) {
            collectTowns(participant, towns);
        }
        for (String participant : war.getDefenderNationNames()) {
            collectTowns(participant, towns);
        }
        refreshTowns(towns.values(), false);
    }

    public void recordEconomicWarDemand(WarManager.MilitaryWarState war, String beneficiaryNationName) {
        if (war == null || beneficiaryNationName == null) return;
        boolean attackerSide = war.isAttackerNation(beneficiaryNationName);
        boolean defenderSide = war.isDefenderNation(beneficiaryNationName);
        if (!attackerSide && !defenderSide) return;

        Map<String, Town> towns = new LinkedHashMap<>();
        Set<String> participants = attackerSide ? war.getAttackerNationNames() : war.getDefenderNationNames();
        for (String participant : participants) collectTowns(participant, towns);
        refreshTowns(towns.values(), false);
    }

    public void recordEconomicWarEnded(WarManager.MilitaryWarState war) {
        if (war == null) return;
        Map<String, Town> participants = new LinkedHashMap<>();
        Map<String, Town> economicParticipants = new LinkedHashMap<>();
        for (String participant : war.getAttackerNationNames()) {
            collectTowns(participant, participants);
            if (war.hasAttackerEconomicTermsDemanded()) collectTowns(participant, economicParticipants);
        }
        for (String participant : war.getDefenderNationNames()) {
            collectTowns(participant, participants);
            if (war.hasDefenderEconomicTermsDemanded()) collectTowns(participant, economicParticipants);
        }
        long now = System.currentTimeMillis();
        for (Town town : economicParticipants.values()) {
            WDEtown record = plugin.getTownManager().getTown(town.getName());
            if (record == null) {
                plugin.getTownManager().registerTown(town);
                record = plugin.getTownManager().getTown(town.getName());
            }
            if (record != null) record.setLastEconomicWarAt(now);
        }
        if (!economicParticipants.isEmpty()) plugin.getTownManager().saveTowns();
        refreshTowns(participants.values(), false);
    }

    public void recordEconomicWarEndedForParticipant(String participant, boolean economicTermsDemanded) {
        Map<String, Town> towns = new LinkedHashMap<>();
        collectTowns(participant, towns);
        if (towns.isEmpty()) return;
        if (economicTermsDemanded) {
            long now = System.currentTimeMillis();
            for (Town town : towns.values()) {
                WDEtown record = plugin.getTownManager().getTown(town.getName());
                if (record == null) {
                    plugin.getTownManager().registerTown(town);
                    record = plugin.getTownManager().getTown(town.getName());
                }
                if (record != null) record.setLastEconomicWarAt(now);
            }
            plugin.getTownManager().saveTowns();
        }
        refreshTowns(towns.values(), false);
    }

    public void refreshAllTowns() {
        refreshTowns(new ArrayList<>(TownyUniverse.getInstance().getTowns()), true);
    }

    private void refreshTowns(Collection<Town> towns, boolean clearResidentsOutsideTowns) {
        long now = System.currentTimeMillis();
        long longPeaceThresholdMillis = getLongPeaceThresholdMillis();
        List<WarManager.MilitaryWarState> activeWars = plugin.getOrgManager().getWarManager().getActiveMilitaryWars(null);
        Set<UUID> residentsInTowns = new HashSet<>();
        boolean townRecordsChanged = false;

        for (Town town : towns) {
            if (town == null) continue;
            WDEtown townRecord = plugin.getTownManager().getTown(town.getName());
            if (townRecord == null) {
                plugin.getTownManager().registerTown(town);
                townRecord = plugin.getTownManager().getTown(town.getName());
                townRecordsChanged = true;
            }
            if (townRecord == null) continue;

            boolean atWar = isTownInActiveWar(town, activeWars);
            long peaceSince = townRecord.getWarPeaceSince();
            if (atWar && peaceSince != 0L) {
                townRecord.setWarPeaceSince(0L);
                townRecordsChanged = true;
                peaceSince = 0L;
            } else if (!atWar && peaceSince == 0L) {
                peaceSince = now;
                townRecord.setWarPeaceSince(peaceSince);
                townRecordsChanged = true;
            }

            boolean longPeace = !atWar && now - peaceSince >= longPeaceThresholdMillis;
            boolean economicTermsInWar = atWar && hasEconomicTermsInWar(town, activeWars);
            for (var townResident : town.getResidents()) {
                UUID residentUuid = townResident.getUUID();
                residentsInTowns.add(residentUuid);
                refreshResidentMood(residentUuid, atWar, longPeace,
                        economicTermsInWar, townRecord.getLastEconomicWarAt(), now);
            }
        }

        if (townRecordsChanged) plugin.getTownManager().saveTowns();
        if (clearResidentsOutsideTowns) clearOrphanedMoodModifiers(residentsInTowns);
    }

    private void collectTowns(String participant, Map<String, Town> towns) {
        if (participant == null || participant.isBlank()) return;
        if (participant.regionMatches(true, 0, "town:", 0, 5)) {
            Town town = TownyUniverse.getInstance().getTown(participant.substring(5));
            addTown(town, towns);
            return;
        }

        for (Town town : TownyUniverse.getInstance().getTowns()) {
            var nation = town.getNationOrNull();
            if (nation != null && nation.getName().equalsIgnoreCase(participant)) {
                addTown(town, towns);
            }
        }
    }

    private static void addTown(Town town, Map<String, Town> towns) {
        if (town != null) towns.putIfAbsent(town.getName().toLowerCase(java.util.Locale.ROOT), town);
    }

    private boolean isTownInActiveWar(Town town, List<WarManager.MilitaryWarState> activeWars) {
        if (town.hasActiveWar()) return true;
        var nation = town.getNationOrNull();
        if (nation != null && nation.hasActiveWar()) return true;
        String townIdentity = "town:" + town.getName();
        String nationName = nation == null ? null : nation.getName();
        return activeWars.stream().anyMatch(war ->
                war.isAttackerNation(townIdentity) || war.isDefenderNation(townIdentity)
                        || war.isAttackerNation(nationName) || war.isDefenderNation(nationName));
    }

    private boolean hasEconomicTermsInWar(Town town, List<WarManager.MilitaryWarState> activeWars) {
        String townIdentity = "town:" + town.getName();
        var townNation = town.getNationOrNull();
        String nationName = townNation == null ? null : townNation.getName();
        boolean foundActiveWar = false;

        for (WarManager.MilitaryWarState war : activeWars) {
            boolean attacker = war.isAttackerNation(townIdentity) || war.isAttackerNation(nationName);
            boolean defender = war.isDefenderNation(townIdentity) || war.isDefenderNation(nationName);
            if (!attacker && !defender) continue;
            foundActiveWar = true;
            if (attacker && !war.hasAttackerEconomicTermsDemanded()) return false;
            if (defender && !war.hasDefenderEconomicTermsDemanded()) return false;
        }
        return foundActiveWar;
    }

    private void refreshResidentMood(UUID residentUuid, boolean atWar, boolean longPeace,
            boolean economicTermsInWar, long lastEconomicWarAt, long now) {
        WDEresident resident = plugin.getResidentManager().getResident(residentUuid);
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        String warAttitude = religion == null ? null : religion.getDoctrineAttitude(DOCTRINE_ID);
        Boolean warCompliant = null;
        if (plugin.religionEnabled && isEnabled() && religion != null) {
            warCompliant = atWar ? WarMoodDoctrineCalculator.assessWarEntry(warAttitude)
                    : longPeace ? WarMoodDoctrineCalculator.assessLongPeace(warAttitude) : null;
        }

        boolean longEconomicPeace = !atWar
                && now - lastEconomicWarAt >= getRaidingPeaceThresholdMillis();
        String raidingAttitude = religion == null
                ? null : religion.getDoctrineAttitude(RAIDING_DOCTRINE_ID);
        Boolean raidingCompliant = null;
        if (plugin.religionEnabled && isRaidingEnabled() && religion != null) {
            raidingCompliant = atWar
                    ? RaidingDoctrineCalculator.assessEconomicWar(raidingAttitude, economicTermsInWar)
                    : longEconomicPeace ? RaidingDoctrineCalculator.assessLongPeace(raidingAttitude) : null;
        }

        boolean hasWarMood = replacePersistentModifier(
                residentUuid, DOCTRINE_ID, MOOD_SOURCE_PREFIX, warAttitude, warCompliant);
        boolean hasRaidingMood = replacePersistentModifier(
                residentUuid, RAIDING_DOCTRINE_ID, RAIDING_MOOD_SOURCE_PREFIX,
                raidingAttitude, raidingCompliant);
        if (hasWarMood || hasRaidingMood) residentsWithPersistentMood.add(residentUuid);
        else residentsWithPersistentMood.remove(residentUuid);
    }

    private boolean replacePersistentModifier(UUID residentUuid, String doctrineId,
            String sourcePrefix, String attitude, Boolean compliant) {
        String source = null;
        int delta = 0;
        if (compliant != null) {
            int weight = Math.max(0, plugin.getReligionManager()
                    .getDoctrineMoodWeight(doctrineId, attitude));
            int perWeight = Math.max(0, plugin.getReligionManager()
                    .getDoctrineMoodChangePerWeight(doctrineId, attitude, compliant, null));
            int magnitude = (int) Math.min(100L, (long) weight * perWeight);
            if (magnitude > 0) {
                source = new DoctrineMoodEntry(doctrineId, attitude, compliant, magnitude).source();
                delta = compliant ? magnitude : -magnitude;
            }
        }
        plugin.getMoodManager().replacePersistentMoodModifier(
                residentUuid, sourcePrefix, source, delta);
        return source != null;
    }

    private void clearOrphanedMoodModifiers(Set<UUID> residentsInTowns) {
        for (UUID residentUuid : new HashSet<>(residentsWithPersistentMood)) {
            if (residentsInTowns.contains(residentUuid)) continue;
            plugin.getMoodManager().replacePersistentMoodModifier(
                    residentUuid, MOOD_SOURCE_PREFIX, null, 0);
            plugin.getMoodManager().replacePersistentMoodModifier(
                    residentUuid, RAIDING_MOOD_SOURCE_PREFIX, null, 0);
            residentsWithPersistentMood.remove(residentUuid);
        }
    }

    private void loadExistingMoodModifiers() {
        long now = System.currentTimeMillis();
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            if (resident.getActiveMoodChanges(now).stream()
                    .anyMatch(change -> change.permanent()
                            && (change.source().startsWith(MOOD_SOURCE_PREFIX)
                                    || change.source().startsWith(RAIDING_MOOD_SOURCE_PREFIX)))) {
                residentsWithPersistentMood.add(resident.getUserUUID());
            }
        }
    }

    private boolean isEnabled() {
        var config = plugin.getIdeologyConfig();
        return config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
    }

    private boolean isRaidingEnabled() {
        var config = plugin.getIdeologyConfig();
        return config == null || config.getBoolean(RAIDING_CONFIG_PATH + "enabled", true);
    }

    private long getLongPeaceThresholdMillis() {
        var config = plugin.getIdeologyConfig();
        int days = config == null ? 30 : config.getInt(CONFIG_PATH + "longPeaceDays", 30);
        return Math.max(1L, Math.min(3650L, days)) * DAY_MILLIS;
    }

    private long getRaidingPeaceThresholdMillis() {
        var config = plugin.getIdeologyConfig();
        int days = config == null ? 30 : config.getInt(RAIDING_CONFIG_PATH + "longPeaceDays", 30);
        return Math.max(1L, Math.min(3650L, days)) * DAY_MILLIS;
    }
}
