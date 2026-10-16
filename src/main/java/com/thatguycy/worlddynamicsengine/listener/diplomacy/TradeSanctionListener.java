package com.thatguycy.worlddynamicsengine.listener.diplomacy;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.Location;

/**
 * 拦截被制裁国家的领土进入与传送；禁入豁免按目标成员国的双边战争状态判定。
 */
public class TradeSanctionListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 2000L;

    private final WorldDynamicsEngine plugin;
    private final OrgManager orgManager;
    private final Map<UUID, Long> lastNoticeAt = new ConcurrentHashMap<>();

    public TradeSanctionListener(WorldDynamicsEngine plugin, OrgManager orgManager) {
        this.plugin = plugin;
        this.orgManager = orgManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !changedBlock(event.getFrom(), event.getTo())) {
            return;
        }

        String fromNation = resolveNationName(event.getFrom());
        String toNation = resolveNationName(event.getTo());
        if (sameNation(fromNation, toNation) || !isTradeSanctionBlocked(event.getPlayer(), toNation)) {
            return;
        }

        event.setTo(event.getFrom());
        notifyBlocked(event.getPlayer(), toNation);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }

        String fromNation = resolveNationName(event.getFrom());
        String toNation = resolveNationName(event.getTo());
        if (sameNation(fromNation, toNation) || !isTradeSanctionBlocked(event.getPlayer(), toNation)) {
            return;
        }

        event.setCancelled(true);
        notifyBlocked(event.getPlayer(), toNation);
    }

    private boolean isTradeSanctionBlocked(Player player, String ownerNationName) {
        if (player == null || ownerNationName == null || ownerNationName.isBlank()) {
            return false;
        }

        String playerNationName = resolvePlayerNationName(player);
        if (playerNationName == null || playerNationName.isBlank() || playerNationName.equalsIgnoreCase(ownerNationName)) {
            return false;
        }

        if (!orgManager.isNationTradeSanctionedBy(playerNationName, ownerNationName)) {
            return false;
        }

        return !orgManager.hasActiveWarBetween(playerNationName, ownerNationName);
    }

    private void notifyBlocked(Player player, String ownerNationName) {
        long now = System.currentTimeMillis();
        Long last = lastNoticeAt.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNoticeAt.put(player.getUniqueId(), now);
        player.sendMessage(plugin.formatLocaleMessage("trade_sanction_territory_blocked", ownerNationName));
    }

    private String resolvePlayerNationName(Player player) {
        Town town = TownyAPI.getInstance().getTown(player);
        return town == null || town.getNationOrNull() == null ? null : town.getNationOrNull().getName();
    }

    private String resolveNationName(Location location) {
        Town town = TownyAPI.getInstance().getTown(location);
        return town == null || town.getNationOrNull() == null ? null : town.getNationOrNull().getName();
    }

    private boolean sameNation(String firstNation, String secondNation) {
        if (firstNation == null && secondNation == null) {
            return true;
        }
        return firstNation != null && secondNation != null && firstNation.equalsIgnoreCase(secondNation);
    }

    private boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || from.getWorld() != to.getWorld();
    }
}
