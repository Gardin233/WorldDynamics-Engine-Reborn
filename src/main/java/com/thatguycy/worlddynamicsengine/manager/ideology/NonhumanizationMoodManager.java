package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/** Maintains the permanent shame mood modifier required by nonhumanization. */
public final class NonhumanizationMoodManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/nonhumanization";
    private static final String REQUIRED_ATTITUDE = DOCTRINE_ID + "/required";
    private static final String SOURCE_PREFIX = "nonhumanization_shame|";
    private static final String SOURCE = SOURCE_PREFIX + "permanent";
    private static final int SHAME_PENALTY = -15;
    private static final long REFRESH_PERIOD_TICKS = 100L;

    private final WorldDynamicsEngine plugin;
    private BukkitTask refreshTask;

    public NonhumanizationMoodManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (refreshTask != null) refreshTask.cancel();
        refreshAllResidents();
        refreshTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::refreshAllResidents, REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
    }

    public void stop() {
        if (refreshTask == null) return;
        refreshTask.cancel();
        refreshTask = null;
    }

    public void refreshAllResidents() {
        if (plugin.getResidentManager() == null) return;
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            refreshResident(resident.getUserUUID());
        }
    }

    public void refreshReligion(WDEreligion religion) {
        if (religion == null || plugin.getResidentManager() == null) return;
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            if (resident.getReligionName() != null
                    && resident.getReligionName().equalsIgnoreCase(religion.getReligionName())) {
                refreshResident(resident.getUserUUID());
            }
        }
    }

    public void refreshResident(UUID residentUuid) {
        if (residentUuid == null || plugin.getMoodManager() == null
                || plugin.getResidentManager() == null) {
            return;
        }
        WDEresident resident = plugin.getResidentManager().getResident(residentUuid);
        if (resident == null) return;

        String source = shouldApplyShamePenalty(resident) ? SOURCE : null;
        int delta = source == null ? 0 : SHAME_PENALTY;
        plugin.getMoodManager().replacePersistentMoodModifier(
                residentUuid, SOURCE_PREFIX, source, delta);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID residentUuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> refreshResident(residentUuid));
    }

    private boolean shouldApplyShamePenalty(WDEresident resident) {
        if (!plugin.religionEnabled || resident.isDehumanized()
                || plugin.getReligionManager() == null) {
            return false;
        }
        WDEreligion religion = plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || !MentalCollapseManager.isRequiredNonhumanizationAttitude(
                DOCTRINE_ID, religion.getDoctrineAttitude(DOCTRINE_ID))) {
            return false;
        }
        return plugin.getReligionManager().canUseDoctrineAttitude(
                religion, DOCTRINE_ID, REQUIRED_ATTITUDE);
    }
}
