package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineBuffManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 清理玩家死亡或离线时的戒律贡献，避免重生及重连继承过期效果。 */
public final class DoctrineBuffListener implements Listener {
    private final DoctrineBuffManager manager;

    public DoctrineBuffListener(DoctrineBuffManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        manager.clearPlayer(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearPlayer(event.getPlayer());
    }
}
