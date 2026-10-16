package com.thatguycy.worlddynamicsengine.command.war;

import com.palmergames.bukkit.towny.object.economy.BankAccount;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.Location;
import org.bukkit.Sound;

/**
 * 让符合资格的成员主动集结到在线统帅或镇长；费用由公共金库承担，失败时尝试退款。
 */
public final class RallyCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    private final Set<UUID> pending = new HashSet<>();

    public RallyCommand(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "common_only_players");
            return true;
        }
        if (!player.hasPermission("wde.player")) {
            send(sender, "command_no_permission");
            return true;
        }
        if (args.length != 2 || !Set.of("nation", "town").contains(args[1])) {
            send(sender, "rally_usage");
            return true;
        }
        if (!pending.add(player.getUniqueId())) return true;
        try {
            rally(player, "nation".equals(args[1]));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Rally request failed for " + player.getUniqueId() + ": " + ex.getMessage());
            send(player, "rally_failed");
        } finally {
            pending.remove(player.getUniqueId());
        }
        return true;
    }

    private void rally(Player player, boolean national) {
        FileConfiguration config = national ? plugin.getNationConfig() : plugin.getTownConfig();
        if (!config.getBoolean("rally.enabled", true) || national && !plugin.armyEnabled) {
            send(player, "rally_disabled");
            return;
        }
        if (plugin.getEconomyBridge() == null || !plugin.getEconomyBridge().hasProvider()) {
            send(player, "rally_economy_unavailable");
            return;
        }
        if (locked(player)) {
            send(player, "rally_locked");
            return;
        }
        Town town = TownyAPI.getInstance().getTown(player);
        WDEtown townData = town == null ? null : plugin.getTownManager().getTown(town.getName());
        if (townData == null) {
            send(player, "rally_ineligible");
            return;
        }
        boolean conscript = townData.isConscript(player.getName());
        boolean mayor = town.getMayor() != null && town.getMayor().getUUID().equals(player.getUniqueId());
        if (!eligible(national, conscript, mayor)) {
            send(player, "rally_ineligible");
            return;
        }
        Government payer = town;
        String targetName;
        Nation nation = town.getNationOrNull();
        if (national) {
            var nationData = nation == null ? null : plugin.getNationManager().getNation(nation.getName());
            if (nationData == null) {
                send(player, "rally_ineligible");
                return;
            }
            payer = nation;
            targetName = nationData.getArmyCommander();
        } else {
            targetName = town.getMayor() == null ? null : town.getMayor().getName();
        }
        Player target = targetName == null ? null : plugin.getServer().getPlayerExact(targetName);
        Town targetTown = target == null ? null : TownyAPI.getInstance().getTown(target);
        if (target == null || !target.isOnline() || target.isDead() || targetTown == null
                || (national ? targetTown.getNationOrNull() != nation : targetTown != town)) {
            send(player, "rally_target_unavailable");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId()) || player.isDead() || player.isInsideVehicle()) {
            send(player, "rally_failed");
            return;
        }
        BigDecimal amount;
        try {
            amount = parseCost(config.getString("rally.cost", national ? "250.00" : "100.00"));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid rally.cost in " + (national ? "nation.yml" : "town.yml"));
            send(player, "rally_disabled");
            return;
        }
        Location destination = target.getLocation().clone();
        BankAccount account = payer.getAccount();
        double cost = amount.doubleValue();
        if (account == null || amount.signum() > 0 && (!account.canPayFromHoldings(cost)
                || !account.withdraw(cost, "WDER rally by " + player.getUniqueId()))) {
            send(player, "rally_insufficient", plugin.getEconomyBridge().format(amount));
            return;
        }
        boolean succeeded = false;
        try {
            succeeded = player.teleport(destination, TeleportCause.PLUGIN);
        } finally {
            if (!succeeded && amount.signum() > 0) {
                boolean refunded = false;
                try {
                    refunded = account.deposit(cost, "WDER cancelled rally refund for " + player.getUniqueId());
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("Rally refund error: " + ex.getMessage());
                }
                if (!refunded) {
                    plugin.getLogger().severe("Rally refund failed: payer=" + payer.getName()
                            + ", player=" + player.getUniqueId() + ", amount=" + amount);
                    send(player, "rally_refund_failed");
                }
            }
            try {
                if (payer instanceof Nation paidNation) paidNation.save();
                else if (payer instanceof Town paidTown) paidTown.save();
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Failed to save rally treasury for " + payer.getName() + ": " + ex.getMessage());
            }
        }
        if (!succeeded) {
            send(player, "rally_failed");
            return;
        }
        send(player, "rally_success", target.getName(), plugin.getEconomyBridge().format(amount));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
    }

    private boolean locked(Player player) {
        return plugin.getOrgManager() != null && plugin.getOrgManager().isRespawnLocked(player.getUniqueId())
                || plugin.getBackgroundManager() != null && plugin.getBackgroundManager().shouldLockMovement(player);
    }

    public static boolean eligible(boolean national, boolean conscript, boolean mayor) {
        return conscript || national && mayor;
    }

    public static BigDecimal parseCost(String raw) {
        BigDecimal amount = new BigDecimal(raw);
        if (amount.signum() < 0 || amount.scale() > 8
                || amount.compareTo(new BigDecimal("1000000000000")) > 0) {
            throw new IllegalArgumentException("Invalid rally cost.");
        }
        return amount;
    }

    private void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(plugin.formatLocaleMessage(plugin.userLang, key, args));
    }
}
