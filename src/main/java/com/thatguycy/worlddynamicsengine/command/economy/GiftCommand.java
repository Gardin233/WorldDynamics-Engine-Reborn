package com.thatguycy.worlddynamicsengine.command.economy;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.economy.PlayerGiftManager;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import java.math.BigDecimal;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles direct gifts and player-to-player alms requests. */
public final class GiftCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    private final PlayerGiftManager manager;

    public GiftCommand(WorldDynamicsEngine plugin, PlayerGiftManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "common_only_players");
            return true;
        }
        if (args.length == 0) {
            send(player, "gift_usage");
            return true;
        }
        if (args[0].equalsIgnoreCase("gift")) {
            return gift(player, args);
        }
        if (args[0].equalsIgnoreCase("alms")) {
            return alms(player, args);
        }
        send(player, "gift_usage");
        return true;
    }

    private boolean gift(Player giver, String[] args) {
        if (args.length < 3 || args.length > 4) {
            send(giver, "gift_usage");
            return true;
        }
        Player recipient = findOnline(args[1]);
        if (recipient == null) {
            send(giver, "gift_target_not_online", args[1]);
            return true;
        }
        BigDecimal amount = parseAmount(giver, args[2]);
        if (amount == null) return true;

        PlayerGiftManager.TransferResult result = manager.gift(
                giver, recipient, amount, args.length == 4 ? args[3] : null);
        handleTransferResult(giver, recipient, result);
        return true;
    }

    private boolean alms(Player player, String[] args) {
        if (args.length == 2 && !isResponseAction(args[1])) {
            Player recipient = findOnline(args[1]);
            if (recipient == null) {
                send(player, "gift_target_not_online", args[1]);
                return true;
            }
            if (recipient.getUniqueId().equals(player.getUniqueId())) {
                send(player, "gift_self");
                return true;
            }
            PlayerGiftManager.RequestResult result = manager.requestAlms(player, recipient);
            switch (result.status()) {
                case SENT -> {
                    long minutes = Math.max(1L,
                            (result.expiresAt() - System.currentTimeMillis() + 59_999L) / 60_000L);
                    send(player, "gift_alms_request_sent", recipient.getName(), minutes);
                    send(recipient, "gift_alms_request_received", player.getName(), minutes);
                }
                case NOT_LOW_ASSET -> send(player, "gift_alms_not_low_asset", recipient.getName());
                case COOLDOWN -> send(player, "gift_alms_request_cooldown",
                        Math.max(1L, (result.retryAfterMillis() + 999L) / 1000L));
                case ALREADY_PENDING -> send(player, "gift_alms_already_pending", recipient.getName());
                case DISABLED -> send(player, "gift_disabled");
                case ECONOMY_UNAVAILABLE -> send(player, "gift_economy_unavailable");
            }
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("accept")) {
            return acceptAlms(player, args);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("reject")) {
            return rejectAlms(player, args[2]);
        }
        send(player, "gift_alms_usage");
        return true;
    }

    private boolean acceptAlms(Player giver, String[] args) {
        if (args.length < 4 || args.length > 5) {
            send(giver, "gift_alms_accept_usage");
            return true;
        }
        Player requester = findOnline(args[2]);
        if (requester == null) {
            send(giver, "gift_target_not_online", args[2]);
            return true;
        }
        BigDecimal amount = parseAmount(giver, args[3]);
        if (amount == null) return true;

        PlayerGiftManager.TransferResult result = manager.acceptAlms(
                giver, requester, amount, args.length == 5 ? args[4] : null);
        handleTransferResult(giver, requester, result);
        return true;
    }

    private boolean rejectAlms(Player recipient, String requesterName) {
        Player requester = findOnline(requesterName);
        if (requester == null) {
            send(recipient, "gift_target_not_online", requesterName);
            return true;
        }
        switch (manager.refuseAlms(recipient, requester)) {
            case PENALTY_APPLIED -> {
                send(recipient, "gift_alms_rejected_penalty", requester.getName());
                send(requester, "gift_alms_rejected", recipient.getName());
            }
            case NO_PENALTY -> {
                send(recipient, "gift_alms_rejected", requester.getName());
                send(requester, "gift_alms_rejected", recipient.getName());
            }
            case NO_PENDING -> send(recipient, "gift_alms_not_found", requester.getName());
            case EXPIRED -> send(recipient, "gift_alms_expired", requester.getName());
            case REQUESTER_OFFLINE -> send(recipient, "gift_target_not_online", requester.getName());
        }
        return true;
    }

    private void handleTransferResult(Player giver, Player recipient,
                                      PlayerGiftManager.TransferResult result) {
        switch (result.status()) {
            case SUCCESS -> {
                String formatted = plugin.getEconomyBridge().format(
                        plugin.getEconomyBridge().charge(result.amount(), result.currency()));
                send(giver, "gift_success", recipient.getName(), formatted);
                send(recipient, "gift_received", giver.getName(), formatted);
            }
            case INVALID_AMOUNT -> send(giver, "gift_invalid_amount");
            case SELF -> send(giver, "gift_self");
            case DISABLED -> send(giver, "gift_disabled");
            case ECONOMY_UNAVAILABLE -> send(giver, "gift_economy_unavailable");
            case INVALID_CURRENCY -> send(giver, "gift_currency_unavailable", result.currency());
            case INSUFFICIENT_FUNDS -> send(giver, "gift_insufficient_funds");
            case TRANSFER_FAILED -> send(giver, "gift_transfer_failed");
            case REFUND_FAILED -> send(giver, "gift_refund_failed");
            case NO_PENDING -> send(giver, "gift_alms_not_found", recipient.getName());
            case REQUEST_EXPIRED -> send(giver, "gift_alms_expired", recipient.getName());
        }
    }

    private BigDecimal parseAmount(Player player, String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            send(player, "gift_invalid_amount");
            return null;
        }
    }

    private Player findOnline(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    private static boolean isResponseAction(String action) {
        return action.equalsIgnoreCase("accept") || action.equalsIgnoreCase("reject");
    }

    private void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage(plugin.userLang, key, args)));
    }
}
