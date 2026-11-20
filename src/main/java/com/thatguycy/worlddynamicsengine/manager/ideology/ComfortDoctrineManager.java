package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;

/** Tracks sleep-related comfort doctrine mood for players with the indifferent attitude. */
public final class ComfortDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/comfort";
    private static final String CONFIG_PATH = "mood.doctrineActions.comfort.";
    private static final String NO_SLEEP_MOOD_PREFIX = "doctrine|" + DOCTRINE_ID + "|";
    private static final long TICKS_PER_MINECRAFT_DAY = 24_000L;

    private final WorldDynamicsEngine plugin;

    public ComfortDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnlinePlayers, 20L, 1200L);
        Bukkit.getScheduler().runTask(plugin, this::refreshOnlinePlayers);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatisticIncrement(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() != Statistic.SLEEP_IN_BED
                || event.getNewValue() <= event.getPreviousValue()) {
            return;
        }
        onSuccessfulSleep(event.getPlayer());
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    private void refreshPlayer(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return;

        long playedTicks = getPlayedTicks(player);
        if (!isEnabled() || !plugin.religionEnabled) {
            clearPersistentMood(player);
            stopTrackingIfNeeded(resident);
            return;
        }

        String attitude = getAttitude(resident);
        if (!"indifferent".equals(attitude)
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            clearPersistentMood(player);
            stopTrackingIfNeeded(resident);
            return;
        }

        if (!resident.isComfortSleepTracking()
                || resident.getLastComfortSleepPlayedTicks() < 0L
                || playedTicks < resident.getLastComfortSleepPlayedTicks()) {
            resident.startComfortSleepTracking(playedTicks);
            plugin.getResidentManager().saveResident(resident);
            clearPersistentMood(player);
            return;
        }

        long thresholdTicks = getNoSleepThresholdDays() * TICKS_PER_MINECRAFT_DAY;
        boolean hasStayedAwake = playedTicks - resident.getLastComfortSleepPlayedTicks() >= thresholdTicks;
        if (hasStayedAwake) {
            int weight = plugin.getReligionManager().getDoctrineMoodWeight(DOCTRINE_ID, attitude);
            int compliantMoodChange = plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                    DOCTRINE_ID, attitude, true, null);
            int bonus = ComfortDoctrineCalculator.calculateCompliantBonus(weight, compliantMoodChange);
            String source = bonus <= 0 ? null : new DoctrineMoodEntry(
                    DOCTRINE_ID, attitude, true, bonus).source();
            plugin.getMoodManager().replacePersistentMoodModifier(
                    player.getUniqueId(), NO_SLEEP_MOOD_PREFIX, source, bonus);
        } else {
            clearPersistentMood(player);
        }
    }

    private void onSuccessfulSleep(Player player) {
        if (player == null || !player.isOnline()) return;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return;

        clearPersistentMood(player);
        String attitude = isEnabled() && plugin.religionEnabled ? getAttitude(resident) : null;
        if (!"indifferent".equals(attitude)
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            stopTrackingIfNeeded(resident);
            return;
        }

        resident.recordComfortSleep(getPlayedTicks(player));
        plugin.getResidentManager().saveResident(resident);
        plugin.getMoodManager().applyDoctrineOutcome(player, DOCTRINE_ID, attitude, false);
    }

    private String getAttitude(WDEresident resident) {
        WDEreligion religion = plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) return null;
        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        if (attitude == null || attitude.isBlank()) return null;
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void clearPersistentMood(Player player) {
        plugin.getMoodManager().replacePersistentMoodModifier(
                player.getUniqueId(), NO_SLEEP_MOOD_PREFIX, null, 0);
    }

    private void stopTrackingIfNeeded(WDEresident resident) {
        if (!resident.isComfortSleepTracking()
                && resident.getLastComfortSleepPlayedTicks() < 0L) {
            return;
        }
        resident.stopComfortSleepTracking();
        plugin.getResidentManager().saveResident(resident);
    }

    private boolean isEnabled() {
        var config = plugin.getIdeologyConfig();
        return config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
    }

    private long getNoSleepThresholdDays() {
        var config = plugin.getIdeologyConfig();
        int days = config == null ? 3 : config.getInt(CONFIG_PATH + "noSleepThresholdDays", 3);
        return Math.max(1L, Math.min(3650L, days));
    }

    private static long getPlayedTicks(Player player) {
        return Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }
}
