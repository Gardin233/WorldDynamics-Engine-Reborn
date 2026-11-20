package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;

/** Rewards a completed outdoor sleep for players whose roughing-it attitude accepts it. */
public final class RoughingItDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/roughing_it";
    private static final String CONFIG_PATH = "mood.doctrineActions.roughingIt.enabled";
    private static final long SLEEP_ATTEMPT_MAX_AGE_MILLIS = 30L * 60L * 1000L;

    private record SleepAttempt(boolean outdoors, long startedAt) {}

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, SleepAttempt> sleepAttempts = new HashMap<>();

    public RoughingItDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.useBed() == Event.Result.DENY
                || event.useBed() != Event.Result.ALLOW
                && event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }
        sleepAttempts.put(event.getPlayer().getUniqueId(),
                new SleepAttempt(isOpenToSky(event.getBed().getLocation()), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleepCompleted(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() != Statistic.SLEEP_IN_BED
                || event.getNewValue() <= event.getPreviousValue()) {
            return;
        }
        SleepAttempt attempt = sleepAttempts.remove(event.getPlayer().getUniqueId());
        if (attempt == null || !attempt.outdoors()
                || System.currentTimeMillis() - attempt.startedAt() > SLEEP_ATTEMPT_MAX_AGE_MILLIS) {
            return;
        }
        applyOutdoorSleep(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        sleepAttempts.remove(event.getPlayer().getUniqueId());
    }

    private void applyOutdoorSleep(Player player) {
        var config = plugin.getIdeologyConfig();
        if (config != null && !config.getBoolean(CONFIG_PATH, true)) return;
        if (!plugin.religionEnabled || player == null || !player.isOnline() || player.isDead()
                || plugin.getMoodManager() == null
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            return;
        }

        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) return;

        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        Boolean compliant = RoughingItDoctrineCalculator.assessOutdoorSleep(attitude, true);
        if (compliant != null) {
            plugin.getMoodManager().applyDoctrineOutcome(player, DOCTRINE_ID, attitude, compliant);
        }
    }

    private static boolean isOpenToSky(Location bedLocation) {
        World world = bedLocation.getWorld();
        if (world == null || !world.hasSkyLight() || world.hasCeiling()) return false;
        return world.getHighestBlockAt(bedLocation, HeightMap.MOTION_BLOCKING).getY()
                <= bedLocation.getBlockY();
    }
}
