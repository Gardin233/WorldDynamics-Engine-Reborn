package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Applies a count-based mood state from different personal ideologies in each Towny town. */
public final class MulticulturalismDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/multiculturalism";
    private static final String PERSISTENT_PREFIX = "doctrine|" + DOCTRINE_ID + "|";

    private record FaithfulResident(UUID uuid, WDEreligion religion, String faithKey) {}

    private final WorldDynamicsEngine plugin;
    private final Set<UUID> residentsWithModifier = new HashSet<>();
    private final Map<UUID, String> notifiedViolations = new HashMap<>();

    public MulticulturalismDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        loadExistingModifiers();
    }

    public void start() {
        long interval = Math.max(1L, Math.min(1200L,
                plugin.getIdeologyConfig().getLong(
                        "mood.doctrineActions.multiculturalism.checkIntervalTicks", 100L)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTownPopulations, 1L, interval);
        Bukkit.getScheduler().runTask(plugin, this::refreshTownPopulations);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::refreshTownPopulations);
    }

    private void loadExistingModifiers() {
        long now = System.currentTimeMillis();
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            boolean hasModifier = resident.getActiveMoodChanges(now).stream()
                    .anyMatch(change -> change.permanent() && change.source().startsWith(PERSISTENT_PREFIX));
            if (hasModifier) residentsWithModifier.add(resident.getUserUUID());
        }
    }

    private void refreshTownPopulations() {
        var config = plugin.getIdeologyConfig();
        if (!plugin.religionEnabled
                || !config.getBoolean("mood.doctrineActions.multiculturalism.enabled", true)) {
            clearAllModifiers();
            return;
        }

        Set<UUID> residentsInTowns = new HashSet<>();
        for (Town town : new ArrayList<>(TownyUniverse.getInstance().getTowns())) {
            applyTown(town, residentsInTowns);
        }
        for (UUID residentUuid : new HashSet<>(residentsWithModifier)) {
            if (!residentsInTowns.contains(residentUuid)) clearResident(residentUuid);
        }
    }

    private void applyTown(Town town, Set<UUID> residentsInTowns) {
        List<Resident> townResidents = new ArrayList<>(town.getResidents());
        List<FaithfulResident> faithfulResidents = new ArrayList<>();
        Map<String, Integer> faithCounts = new HashMap<>();
        Set<UUID> faithfulUuids = new HashSet<>();

        for (Resident townResident : townResidents) {
            UUID uuid = townResident.getUUID();
            residentsInTowns.add(uuid);
            WDEresident profile = plugin.getResidentManager()
                    .getOrCreateResident(uuid, townResident.getName());
            WDEreligion religion = plugin.getReligionManager().getReligion(profile.getReligionName());
            if (religion == null) continue;

            String faithKey = religion.getIdeologyUuid() == null
                    ? religion.getReligionName().trim().toLowerCase(java.util.Locale.ROOT)
                    : religion.getIdeologyUuid().toString();
            faithfulResidents.add(new FaithfulResident(uuid, religion, faithKey));
            faithfulUuids.add(uuid);
            faithCounts.merge(faithKey, 1, Integer::sum);
        }

        int totalFaithfulResidents = faithfulResidents.size();
        for (FaithfulResident faithfulResident : faithfulResidents) {
            int sameFaithResidents = faithCounts.getOrDefault(faithfulResident.faithKey(), 0);
            int differentFaithResidents = totalFaithfulResidents - sameFaithResidents;
            String attitude = faithfulResident.religion().getDoctrineAttitude(DOCTRINE_ID);
            MulticulturalismDoctrineCalculator.Assessment assessment =
                    MulticulturalismDoctrineCalculator.assess(attitude, differentFaithResidents);
            applyMoodState(faithfulResident.uuid(), attitude, assessment);
        }

        for (Resident townResident : townResidents) {
            if (!faithfulUuids.contains(townResident.getUUID())) {
                clearResident(townResident.getUUID());
            }
        }
    }

    private void applyMoodState(UUID residentUuid, String attitude,
                                MulticulturalismDoctrineCalculator.Assessment assessment) {
        Boolean compliant = assessment.compliant();
        if (compliant == null || attitude == null || attitude.isBlank()
                || assessment.differentFaithResidents() <= 0) {
            clearResident(residentUuid);
            return;
        }

        ReligionManager religions = plugin.getReligionManager();
        int weight = Math.max(0, religions.getDoctrineMoodWeight(DOCTRINE_ID, attitude));
        int perPerson = Math.max(0, religions.getDoctrineMoodChangePerWeight(
                DOCTRINE_ID, attitude, compliant, null));
        long magnitude = (long) weight * perPerson * assessment.differentFaithResidents();
        int delta = (int) Math.min(100L, magnitude);
        if (!compliant) delta = -delta;
        String source = new DoctrineMoodEntry(DOCTRINE_ID, attitude, compliant, delta).source();
        plugin.getMoodManager().replacePersistentMoodModifier(residentUuid, PERSISTENT_PREFIX, source, delta);
        if (delta == 0) {
            residentsWithModifier.remove(residentUuid);
        } else {
            residentsWithModifier.add(residentUuid);
        }

        if (compliant) {
            notifiedViolations.remove(residentUuid);
        } else {
            Player player = Bukkit.getPlayer(residentUuid);
            if (player != null && !attitude.equals(notifiedViolations.put(residentUuid, attitude))
                    && plugin.getPlayerMenuManager() != null) {
                plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
            }
        }
    }

    private void clearResident(UUID residentUuid) {
        plugin.getMoodManager().replacePersistentMoodModifier(residentUuid, PERSISTENT_PREFIX, null, 0);
        residentsWithModifier.remove(residentUuid);
        notifiedViolations.remove(residentUuid);
    }

    private void clearAllModifiers() {
        for (UUID residentUuid : new HashSet<>(residentsWithModifier)) {
            clearResident(residentUuid);
        }
    }
}
