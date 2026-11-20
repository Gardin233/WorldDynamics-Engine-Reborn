package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Tracks ritual participation rewards and per-resident long-absence mood penalties. */
public final class RitualMoodDoctrineManager {
    private static final String DOCTRINE_ID = "namespace:wder/rituals";
    private static final String CONFIG_PATH = "mood.doctrineActions.rituals.";
    private static final String ABSENCE_MOOD_PREFIX = "ritual_absence|";
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final WorldDynamicsEngine plugin;
    private final Set<UUID> residentsWithAbsencePenalty = new HashSet<>();

    public RitualMoodDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        loadExistingPenalties();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshResidents, 20L, 1200L);
        Bukkit.getScheduler().runTask(plugin, this::refreshResidents);
    }

    /** Called only after a ritual session has completed rather than being cancelled. */
    public void onRitualCompleted(List<Player> participants) {
        if (participants == null || participants.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Player player : participants) {
            if (player == null || !player.isOnline()) continue;
            try {
                WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
                if (resident == null) continue;

                resident.setLastRitualParticipationAt(now);
                plugin.getResidentManager().saveResident(resident);
                clearAbsencePenalty(resident.getUserUUID());

                if (!isEnabled() || !plugin.religionEnabled) continue;
                WDEreligion religion = plugin.getReligionManager().getReligion(resident.getReligionName());
                if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) continue;

                String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
                Boolean compliant = RitualsDoctrineCalculator.assessParticipation(attitude);
                if (compliant == null) continue;

                int magnitude = getMoodMagnitude(attitude, compliant);
                if (magnitude <= 0) continue;
                String source = compliant ? "ritual_satisfaction"
                        : new DoctrineMoodEntry(DOCTRINE_ID, attitude, false, magnitude).source();
                long duration = getSatisfactionDurationMillis();
                boolean applied = plugin.getMoodManager().applyTemporaryMoodChange(
                        player.getUniqueId(), source, compliant ? magnitude : -magnitude, duration);
                if (compliant && applied) {
                    player.sendMessage(plugin.formatLocaleMessage("ritual_satisfaction_awarded",
                            magnitude, getSatisfactionDurationMinutes()));
                } else if (!compliant && plugin.getPlayerMenuManager() != null) {
                    plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to apply ritual mood for " + player.getUniqueId()
                        + ": " + ex.getMessage());
            }
        }
    }

    public void refreshResidents() {
        if (!isEnabled() || !plugin.religionEnabled) {
            clearAllAbsencePenalties();
            return;
        }
        long now = System.currentTimeMillis();
        long absenceThreshold = getLongAbsenceDays() * DAY_MILLIS;
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            try {
                WDEreligion religion = plugin.getReligionManager().getReligion(resident.getReligionName());
                String attitude = religion == null ? null : religion.getDoctrineAttitude(DOCTRINE_ID);
                Boolean compliant = RitualsDoctrineCalculator.assessLongAbsence(attitude);
                if (religion == null || compliant == null
                        || now - resident.getLastRitualParticipationAt() < absenceThreshold) {
                    clearAbsencePenalty(resident.getUserUUID());
                    continue;
                }

                int magnitude = getMoodMagnitude(attitude, false);
                if (magnitude <= 0) {
                    clearAbsencePenalty(resident.getUserUUID());
                    continue;
                }
                String source = ABSENCE_MOOD_PREFIX + religion.getIdeologyUuid();
                boolean newlyPenalized = !residentsWithAbsencePenalty.contains(resident.getUserUUID());
                plugin.getMoodManager().replacePersistentMoodModifier(
                        resident.getUserUUID(), ABSENCE_MOOD_PREFIX, source, -magnitude);
                residentsWithAbsencePenalty.add(resident.getUserUUID());
                Player player = Bukkit.getPlayer(resident.getUserUUID());
                if (newlyPenalized && player != null && plugin.getPlayerMenuManager() != null) {
                    plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to refresh ritual absence mood for "
                        + resident.getUserUUID() + ": " + ex.getMessage());
            }
        }
    }

    private void loadExistingPenalties() {
        long now = System.currentTimeMillis();
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            boolean hasPenalty = resident.getActiveMoodChanges(now).stream()
                    .anyMatch(change -> change.permanent()
                            && change.source().startsWith(ABSENCE_MOOD_PREFIX));
            if (hasPenalty) residentsWithAbsencePenalty.add(resident.getUserUUID());
        }
    }

    private void clearAllAbsencePenalties() {
        for (UUID residentUuid : new HashSet<>(residentsWithAbsencePenalty)) {
            try {
                clearAbsencePenalty(residentUuid);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to clear ritual absence mood for "
                        + residentUuid + ": " + ex.getMessage());
            }
        }
    }

    private void clearAbsencePenalty(UUID residentUuid) {
        plugin.getMoodManager().replacePersistentMoodModifier(
                residentUuid, ABSENCE_MOOD_PREFIX, null, 0);
        residentsWithAbsencePenalty.remove(residentUuid);
    }

    private int getMoodMagnitude(String attitude, boolean compliant) {
        int weight = Math.max(0, plugin.getReligionManager().getDoctrineMoodWeight(DOCTRINE_ID, attitude));
        int perWeight = Math.max(0, plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                DOCTRINE_ID, attitude, compliant, null));
        return (int) Math.min(100L, (long) weight * perWeight);
    }

    private boolean isEnabled() {
        var config = plugin.getIdeologyConfig();
        return config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
    }

    private long getLongAbsenceDays() {
        var config = plugin.getIdeologyConfig();
        int days = config == null ? 30 : config.getInt(CONFIG_PATH + "longAbsenceDays", 30);
        return Math.max(1L, Math.min(3650L, days));
    }

    private int getSatisfactionDurationMinutes() {
        var config = plugin.getIdeologyConfig();
        int minutes = config == null
                ? 30 : config.getInt(CONFIG_PATH + "satisfactionDurationMinutes", 30);
        return Math.max(1, Math.min(1440, minutes));
    }

    private long getSatisfactionDurationMillis() {
        return getSatisfactionDurationMinutes() * 60_000L;
    }
}
