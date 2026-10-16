package com.thatguycy.worlddynamicsengine.command.inn;

import com.thatguycy.worlddynamicsengine.manager.inn.InnManager;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理 Towny 旅馆床位定价指令，实际地块权限、币种和告示牌处理交由旅馆管理器。
 */
public final class InnCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    private final InnManager manager;

    public InnCommand(WorldDynamicsEngine plugin, InnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleMessage("inn_player_only"));
            return true;
        }
        if (args.length < 3 || args.length > 4 || !args[1].equalsIgnoreCase("set")) {
            manager.message(player, "usage");
            return true;
        }
        var target = player.getTargetBlockExact(6);
        if (target == null) {
            manager.message(player, "edit_denied");
            return true;
        }
        String currency = args.length == 4 ? args[3] : plugin.getEconomyBridge().getDefaultCurrency();
        if (currency.equals("default")) currency = plugin.getEconomyBridge().getDefaultCurrency();
        try {
            manager.setPrice(player, target, new BigDecimal(args[2]), currency);
        } catch (NumberFormatException e) {
            manager.message(player, "invalid_price");
        }
        return true;
    }
}
