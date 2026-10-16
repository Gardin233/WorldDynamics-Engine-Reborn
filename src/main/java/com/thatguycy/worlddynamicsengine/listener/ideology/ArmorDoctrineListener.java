package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineBuffManager;
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
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

/** 通过 Paper 护甲变更、登录和耐久损坏事件更新护甲戒律，不周期扫描装备。 */
public final class ArmorDoctrineListener implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/armor_wearing";
    private final WorldDynamicsEngine plugin;
    private final ResidentManager residents;
    private final ReligionManager religions;
    private final DoctrineBuffManager buffs;
    private final Set<UUID> pending = new HashSet<>();

    public ArmorDoctrineListener(WorldDynamicsEngine plugin, ResidentManager residents,
                                 ReligionManager religions, DoctrineBuffManager buffs) {
        this.plugin = plugin;
        this.residents = residents;
        this.religions = religions;
        this.buffs = buffs;
        buffs.registerPersistentRefresher(DOCTRINE_ID, this::refreshPlayer);
    }

    /** 给插件启用或重载时已在线的玩家建立初始贡献。 */
    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) scheduleRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorBreak(PlayerItemBreakEvent event) {
        // Covers breaking custom or non-standard head-slot equipment as well.
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
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
        if (!plugin.religionEnabled || !player.isOnline() || player.isDead()) {
            buffs.removeSource(player, DOCTRINE_ID);
            return;
        }
        WDEresident resident = residents.getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : religions.getReligion(resident.getReligionName());
        if (religion == null) {
            buffs.removeSource(player, DOCTRINE_ID);
            return;
        }

        if (religions.getDoctrine(DOCTRINE_ID) == null) {
            buffs.removeSource(player, DOCTRINE_ID);
            return;
        }
        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        if (attitude == null || attitude.endsWith("/unrestricted")) {
            buffs.removeSource(player, DOCTRINE_ID);
            return;
        }
        Boolean compliant = isCompliant(player.getInventory(), attitude);
        if (compliant == null) {
            buffs.removeSource(player, DOCTRINE_ID);
        } else if (compliant) {
            buffs.replacePersistent(player, DOCTRINE_ID,
                    Map.of(PotionEffectType.HASTE, 1, PotionEffectType.SPEED, 1));
        } else {
            buffs.replacePersistent(player, DOCTRINE_ID,
                    Map.of(PotionEffectType.SLOWNESS, 1, PotionEffectType.MINING_FATIGUE, 1));
            if (plugin.getPlayerMenuManager() != null) {
                plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
            }
        }
    }

    private Boolean isCompliant(PlayerInventory inventory, String attitude) {
        boolean helmet = hasArmor(inventory.getHelmet());
        boolean chestplate = hasArmor(inventory.getChestplate());
        boolean leggings = hasArmor(inventory.getLeggings());
        boolean boots = hasArmor(inventory.getBoots());
        if (attitude.endsWith("/naked")) return !helmet && !chestplate && !leggings && !boots;
        if (attitude.endsWith("/pants_only")) return !helmet && !chestplate && leggings && !boots;
        if (attitude.endsWith("/fully_covered")) return helmet && chestplate && leggings && boots;
        return null;
    }

    private boolean hasArmor(org.bukkit.inventory.ItemStack item) {
        return item != null && !item.getType().isAir();
    }

}
