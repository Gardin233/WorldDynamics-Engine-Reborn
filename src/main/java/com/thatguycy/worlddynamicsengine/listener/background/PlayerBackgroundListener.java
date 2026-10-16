package com.thatguycy.worlddynamicsengine.listener.background;

import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 处理玩家登录后的背景引导、待发奖励，以及强制背景选择期间的移动限制。
 */
public class PlayerBackgroundListener implements Listener {
    private final WorldDynamicsEngine plugin;
    private final BackgroundManager backgroundManager;
    private final ResidentManager residentManager;

    public PlayerBackgroundListener(WorldDynamicsEngine plugin, BackgroundManager backgroundManager, ResidentManager residentManager) {
        this.plugin = plugin;
        this.backgroundManager = backgroundManager;
        this.residentManager = residentManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        residentManager.getOrCreateResident(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            backgroundManager.applyPendingBonuses(event.getPlayer());
            backgroundManager.promptSelectionIfNeeded(event.getPlayer());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!backgroundManager.shouldLockMovement(event.getPlayer()) || event.getTo() == null) {
            return;
        }

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }
}
