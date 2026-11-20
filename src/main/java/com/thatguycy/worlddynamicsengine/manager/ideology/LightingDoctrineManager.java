package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Maintains one persistent mood modifier based on the player's current illumination. */
public final class LightingDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/lighting";

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, String> notifiedViolations = new HashMap<>();

    public LightingDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = Math.max(1L,
                Math.min(1200L, plugin.getIdeologyConfig()
                        .getLong("mood.doctrineActions.lighting.checkIntervalTicks", 20L)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnlinePlayers, 1L, interval);
        Bukkit.getScheduler().runTask(plugin, this::refreshOnlinePlayers);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled() || !event.hasChangedBlock()) return;
        refreshPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer());
    }

    public void refreshPlayer(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return;
        if (!plugin.getIdeologyConfig().getBoolean("mood.doctrineActions.lighting.enabled", true)
                || !plugin.religionEnabled || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            clearPlayer(player);
            return;
        }

        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) {
            clearPlayer(player);
            return;
        }

        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        if (attitude == null || attitude.isBlank()) {
            clearPlayer(player);
            return;
        }

        Block block = player.getLocation().getBlock();
        LightingDoctrineCalculator.Assessment assessment = LightingDoctrineCalculator.assess(
                block.getLightFromBlocks(), block.getLightFromSky(),
                !block.getWorld().isDayTime(), attitude);
        Boolean compliant = assessment.compliant();
        if (compliant == null) {
            clearPlayer(player);
            return;
        }

        plugin.getMoodManager().setPersistentDoctrineOutcome(player, DOCTRINE_ID, attitude, compliant);
        if (compliant) {
            notifiedViolations.remove(player.getUniqueId());
        } else if (!attitude.equals(notifiedViolations.put(player.getUniqueId(), attitude))
                && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
        }
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    private void clearPlayer(Player player) {
        if (player == null) return;
        plugin.getMoodManager().setPersistentDoctrineOutcome(player, DOCTRINE_ID, null, null);
        notifiedViolations.remove(player.getUniqueId());
    }
}
