package com.thatguycy.worlddynamicsengine.listener.inn;

import com.thatguycy.worlddynamicsengine.manager.inn.InnManager;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 衔接床位交互、入睡和床位或告示牌变动事件，维护旅馆收费与定价记录。
 */
public final class InnListener implements Listener {
    private final InnManager manager;

    public InnListener(InnManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        if (event.useBed() == Event.Result.DENY
                || event.useBed() != Event.Result.ALLOW
                && event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        if (!manager.beginSleep(event.getPlayer(), event.getBed())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.supportsProtectedBlock(event.getBlock())
                || manager.protectedBlock(event.getBlock()) && !manager.canModify(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            manager.message(event.getPlayer(), "edit_denied");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        manager.removeBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignEdit(SignChangeEvent event) {
        if (manager.protectedBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof org.bukkit.block.Sign
                && manager.protectedBlock(event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> manager.protectedBlock(b) || manager.supportsProtectedBlock(b));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> manager.protectedBlock(b) || manager.supportsProtectedBlock(b));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> manager.protectedBlock(b)
                || manager.supportsProtectedBlock(b)
                || manager.protectedBlock(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> manager.protectedBlock(b)
                || manager.supportsProtectedBlock(b))) event.setCancelled(true);
    }
}
