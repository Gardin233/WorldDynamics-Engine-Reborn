package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.MoodManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;

/** Tracks armor-doctrine compliance changes without repeatedly rewarding unchanged equipment. */
public final class ArmorDoctrineListener implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/armor_wearing";
    private final WorldDynamicsEngine plugin;
    private final ResidentManager residents;
    private final ReligionManager religions;
    private final MoodManager moods;
    private final Set<UUID> pending = new HashSet<>();
    private final java.util.Map<UUID, ArmorState> lastStates = new HashMap<>();

    private record ArmorState(String religion, String attitude, boolean compliant) {}

    public ArmorDoctrineListener(WorldDynamicsEngine plugin, ResidentManager residents,
                                 ReligionManager religions, MoodManager moods) {
        this.plugin = plugin;
        this.residents = residents;
        this.religions = religions;
        this.moods = moods;
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastStates.remove(event.getPlayer().getUniqueId());
        pending.remove(event.getPlayer().getUniqueId());
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
            lastStates.remove(player.getUniqueId());
            return;
        }
        WDEresident resident = residents.getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : religions.getReligion(resident.getReligionName());
        if (religion == null) {
            lastStates.remove(player.getUniqueId());
            return;
        }

        if (religions.getDoctrine(DOCTRINE_ID) == null) {
            lastStates.remove(player.getUniqueId());
            return;
        }
        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        if (attitude == null || attitude.endsWith("/unrestricted")) {
            lastStates.remove(player.getUniqueId());
            return;
        }
        Boolean compliant = isCompliant(player.getInventory(), attitude);
        if (compliant == null) {
            lastStates.remove(player.getUniqueId());
            return;
        }
        ArmorState current = new ArmorState(religion.getReligionName(), attitude, compliant);
        ArmorState previous = lastStates.put(player.getUniqueId(), current);
        if (previous != null
                && previous.religion().equalsIgnoreCase(current.religion())
                && previous.attitude().equalsIgnoreCase(current.attitude())
                && previous.compliant() != current.compliant()) {
            moods.applyDoctrineOutcome(player, DOCTRINE_ID, attitude, compliant);
        }
        if (!compliant) {
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
