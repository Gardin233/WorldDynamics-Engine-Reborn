package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.palmergames.bukkit.towny.event.NewDayEvent;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 接入 Towny 每日结算事件，触发个人信仰的什一税征收。
 */
public final class IdeologyTreasuryListener implements Listener {
    private final WorldDynamicsEngine plugin;

    public IdeologyTreasuryListener(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNewDay(NewDayEvent event) {
        // Economy calls and ideology mutations run on the server thread, after Towny's daily collection.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getIdeologyTreasuryManager().collectDailyTithes());
    }
}
