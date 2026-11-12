package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyRitualManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** 监听仪式核心参与者离线，确保仪式中断规则即时生效。 */
public final class IdeologyRitualListener implements Listener {
    private final IdeologyRitualManager rituals;

    public IdeologyRitualListener(IdeologyRitualManager rituals) {
        this.rituals = rituals;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (rituals != null) {
            rituals.cancelForQuit(event.getPlayer().getUniqueId());
        }
    }
}
