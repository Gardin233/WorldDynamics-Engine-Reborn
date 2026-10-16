package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import org.bukkit.block.data.type.Cake;
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.Material;

/**
 * 在成功进食后触发饮食戒律判定；蛋糕需确认实际吃下一口，取消事件不奖惩。
 */
public final class FoodDoctrineListener implements Listener {
    private final WorldDynamicsEngine plugin;
    public FoodDoctrineListener(WorldDynamicsEngine plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        var item = event.getItem().clone();
        // Apply after vanilla consumption, including milk's effect removal.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled()) plugin.getFoodDoctrineManager().apply(event.getPlayer(),
                    plugin.getFoodDoctrineManager().classify(item));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCake(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY
                || block == null || block.getType() != Material.CAKE || !(block.getBlockData() instanceof Cake cake)) return;
        int bites = cake.getBites();
        int food = event.getPlayer().getFoodLevel();
        String ceId = plugin.getCraftEngineIntegration() == null ? null : plugin.getCraftEngineIntegration().getBlockId(block);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.useInteractedBlock() == Event.Result.DENY || event.getPlayer().getFoodLevel() <= food) return;
            if (block.getBlockData() instanceof Cake after && after.getBites() > bites
                    || bites == cake.getMaximumBites() && block.getType().isAir()) {
                var category = plugin.getFoodDoctrineManager().classifyId(Material.CAKE, ceId);
                plugin.getFoodDoctrineManager().apply(event.getPlayer(), category);
            }
        });
    }
}
