package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Maintains a persistent mood modifier from nearby vanilla skull blocks. */
public final class DisplayOfHeadsDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/display_of_heads";
    private final WorldDynamicsEngine plugin;
    private final Map<UUID, String> notifiedViolations = new HashMap<>();

    public DisplayOfHeadsDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var config = plugin.getIdeologyConfig();
        long interval = Math.max(1L, Math.min(1200L,
                config == null ? 40L
                        : config.getLong("mood.doctrineActions.displayOfHeads.checkIntervalTicks", 40L)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnlinePlayers, 1L, interval);
        Bukkit.getScheduler().runTask(plugin, this::refreshOnlinePlayers);
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
        if (!plugin.getIdeologyConfig().getBoolean(
                    "mood.doctrineActions.displayOfHeads.enabled", true)
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
        Boolean compliant = DisplayOfHeadsDoctrineCalculator.assess(attitude,
                hasNearbyHead(player, getRadiusBlocks()));
        if (compliant == null) {
            clearPlayer(player);
            return;
        }

        plugin.getMoodManager().setPersistentDoctrineOutcome(player, DOCTRINE_ID, attitude, compliant);
        UUID uuid = player.getUniqueId();
        if (compliant) {
            notifiedViolations.remove(uuid);
        } else if (!attitude.equals(notifiedViolations.put(uuid, attitude))
                && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
        }
    }

    private int getRadiusBlocks() {
        var config = plugin.getIdeologyConfig();
        return Math.max(1, Math.min(16, config == null
                ? 8 : config.getInt("mood.doctrineActions.displayOfHeads.radiusBlocks", 8)));
    }

    private boolean hasNearbyHead(Player player, int radius) {
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerY = player.getLocation().getBlockY();
        int centerZ = player.getLocation().getBlockZ();
        int radiusSquared = radius * radius;
        int minimumY = Math.max(world.getMinHeight(), centerY - radius);
        int maximumY = Math.min(world.getMaxHeight() - 1, centerY + radius);

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            int dx = x - centerX;
            for (int y = minimumY; y <= maximumY; y++) {
                int dy = y - centerY;
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int dz = z - centerZ;
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    if (DisplayOfHeadsDoctrineCalculator.isHeadBlock(world.getBlockAt(x, y, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
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
