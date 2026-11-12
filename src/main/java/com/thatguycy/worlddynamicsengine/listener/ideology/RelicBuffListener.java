package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineBuffManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.RelicManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffectType;

/** 按玩家背包中的圣物状态更新个人信仰速度效果。 */
public final class RelicBuffListener implements Listener {
    private static final String BUFF_SOURCE = "wde:religious_relics";
    private final WorldDynamicsEngine plugin;
    private final RelicManager relics;
    private final ResidentManager residents;
    private final ReligionManager religions;
    private final DoctrineBuffManager buffs;
    private final Set<UUID> pending = new HashSet<>();

    public RelicBuffListener(WorldDynamicsEngine plugin, RelicManager relics,
                             ResidentManager residents, ReligionManager religions,
                             DoctrineBuffManager buffs) {
        this.plugin = plugin;
        this.relics = relics;
        this.residents = residents;
        this.religions = religions;
        this.buffs = buffs;
    }

    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) scheduleRefresh(player);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) refreshPlayer(player);
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) scheduleRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) scheduleRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) scheduleRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    private void scheduleRefresh(Player player) {
        UUID id = player.getUniqueId();
        if (!pending.add(id)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(id);
            if (player.isOnline()) refreshPlayer(player);
        });
    }

    private void refreshPlayer(Player player) {
        if (!plugin.religionEnabled || !player.isOnline() || player.isDead()
                || relics.getSpeedLevel() <= 0) {
            buffs.removeSource(player, BUFF_SOURCE);
            return;
        }

        WDEresident resident = residents.getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : religions.getReligion(resident.getReligionName());
        if (religion == null) {
            buffs.removeSource(player, BUFF_SOURCE);
            return;
        }
        if (!relics.hasRelic(player.getInventory())) {
            buffs.removeSource(player, BUFF_SOURCE);
            return;
        }
        buffs.replacePersistent(player, BUFF_SOURCE,
                Map.of(PotionEffectType.SPEED, relics.getSpeedLevel()));
    }
}
