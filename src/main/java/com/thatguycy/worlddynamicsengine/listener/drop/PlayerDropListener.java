package com.thatguycy.worlddynamicsengine.listener.drop;

import com.thatguycy.worlddynamicsengine.manager.drop.PlayerDropManager;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 在玩家死亡事件中追加配置掉落，不替换或清除原版掉落列表。
 */
public final class PlayerDropListener implements Listener {
    private final PlayerDropManager playerDropManager;

    public PlayerDropListener(PlayerDropManager playerDropManager) {
        this.playerDropManager = playerDropManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        playerDropManager.addDrops(event);
    }
}
