package com.thatguycy.worlddynamicsengine.command.system;

import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.OfflinePlayer;

/**
 * 提供经济余额、CraftEngine 和季节日历等运行时查询，用于管理员核对依赖与配置。
 */
public class DebugCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;

    public DebugCommand(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("wde.debug")) {
            sender.sendMessage(TextColors.RED + msg("debug_no_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(TextColors.RED + msg("debug_usage"));
            return true;
        }

        String subCommand = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("balance".equals(subCommand)) {
            return handleBalance(sender, args);
        }
        if ("craftengine".equals(subCommand)) {
            return handleCraftEngine(sender);
        }
        if ("realisticseasons".equals(subCommand)) {
            var clock = plugin.getWarTruceClock();
            sender.sendMessage(TextColors.GOLD + msg("debug_seasons_header"));
            sender.sendMessage(TextColors.YELLOW + msg("debug_seasons_settings",
                    clock.enabled() ? msg("debug_seasons_enabled") : msg("debug_seasons_disabled"),
                    clock.configuredWorld()));
            var date = clock.currentDate(clock.configuredWorld());
            sender.sendMessage(TextColors.YELLOW + (date == null
                    ? msg("debug_seasons_unavailable") : msg("debug_seasons_date", date.display(), date.dayNumber())));
            return true;
        }

        sender.sendMessage(TextColors.RED + msg("debug_unknown_subcommand"));
        return true;
    }

    private boolean handleCraftEngine(CommandSender sender) {
        var integration = plugin.getCraftEngineIntegration();
        if (integration == null) {
            sender.sendMessage(TextColors.RED + msg("debug_craftengine_unavailable"));
            return true;
        }

        sender.sendMessage(TextColors.GOLD + msg("debug_craftengine_header", integration.getVersion()));
        sender.sendMessage(TextColors.YELLOW + msg("debug_craftengine_loaded_counts",
                integration.getLoadedItemIds().size(),
                integration.getLoadedBlockIds().size()));

        if (sender instanceof Player player) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            String itemId = integration.getItemId(itemInHand);
            sender.sendMessage(TextColors.YELLOW + msg("debug_craftengine_held_item",
                    itemId == null ? msg("debug_craftengine_not_custom") : itemId));

            Block targetBlock = player.getTargetBlockExact(8);
            String blockId = integration.getBlockId(targetBlock);
            sender.sendMessage(TextColors.YELLOW + msg("debug_craftengine_target_block",
                    blockId == null ? msg("debug_craftengine_not_custom") : blockId));
        }
        return true;
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextColors.RED + msg("debug_balance_usage"));
            return true;
        }

        EconomyBridge economyBridge = plugin.getEconomyBridge();
        if (economyBridge == null || !economyBridge.hasProvider()) {
            sender.sendMessage(TextColors.RED + msg("debug_economy_unavailable"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if ((target.getName() == null || target.getName().isBlank()) && !target.isOnline()) {
            sender.sendMessage(TextColors.RED + msg("debug_player_not_found", args[2]));
            return true;
        }

        Player onlineTarget = target.getPlayer();
        String playerName = onlineTarget != null ? onlineTarget.getName() : (target.getName() == null ? args[2] : target.getName());
        String worldName = onlineTarget != null && onlineTarget.getWorld() != null ? onlineTarget.getWorld().getName() : "";

        sender.sendMessage(TextColors.GOLD + msg("debug_balance_header", playerName));
        sender.sendMessage(TextColors.YELLOW + msg("debug_balance_provider_mode")
                + TextColors.WHITE + (economyBridge.hasModernEconomy() ? msg("debug_provider_modern") : msg("debug_provider_legacy")));
        sender.sendMessage(TextColors.YELLOW + msg("debug_balance_default_currency")
                + TextColors.WHITE + plugin.getDefaultEconomyCurrency());
        sender.sendMessage(TextColors.YELLOW + msg("debug_balance_multicurrency")
                + TextColors.WHITE + (economyBridge.supportsMultiCurrency() ? msg("common_enabled") : msg("common_disabled")));

        if (args.length >= 4) {
            String requestedCurrency = args[3];
            sendCurrencyBalanceLine(sender, economyBridge, target, onlineTarget, worldName, requestedCurrency);
            return true;
        }

        List<String> currencies = collectDebugCurrencies(economyBridge);
        if (currencies.isEmpty()) {
            currencies.add(plugin.getDefaultEconomyCurrency());
        }

        for (String currency : currencies) {
            sendCurrencyBalanceLine(sender, economyBridge, target, onlineTarget, worldName, currency);
        }

        BigDecimal totalDefault = onlineTarget != null
                ? economyBridge.getTotalWealthAsDefault(onlineTarget.getUniqueId(), worldName, currencies)
                : economyBridge.getTotalWealthAsDefault(target.getUniqueId(), worldName, currencies);
        sender.sendMessage(TextColors.YELLOW + msg("debug_balance_total_default")
                + TextColors.WHITE + economyBridge.format(totalDefault, plugin.getDefaultEconomyCurrency()));
        return true;
    }

    private void sendCurrencyBalanceLine(CommandSender sender, EconomyBridge economyBridge, OfflinePlayer target, Player onlineTarget, String worldName, String requestedCurrency) {
        String resolvedCurrency = resolveDisplayCurrency(requestedCurrency);
        boolean currencySupported = !economyBridge.hasModernEconomy() || economyBridge.hasCurrency(resolvedCurrency);
        BigDecimal balance = onlineTarget != null
                ? economyBridge.getBalance(onlineTarget, resolvedCurrency)
                : economyBridge.getBalance(target.getUniqueId(), worldName, resolvedCurrency);
        sender.sendMessage(TextColors.YELLOW + msg("debug_balance_currency_line", resolvedCurrency)
                + TextColors.WHITE + economyBridge.format(balance, resolvedCurrency)
                + TextColors.GRAY + " [" + (currencySupported ? msg("debug_currency_supported") : msg("debug_currency_unsupported")) + "]");
    }

    private List<String> collectDebugCurrencies(EconomyBridge economyBridge) {
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        currencies.add(plugin.getDefaultEconomyCurrency());
        currencies.addAll(plugin.getCurrencyExchangeRates().keySet());

        Collection<String> providerCurrencies = economyBridge.currencies();
        if (providerCurrencies != null) {
            currencies.addAll(providerCurrencies);
        }

        return new ArrayList<>(currencies);
    }

    private String resolveDisplayCurrency(String currency) {
        if (currency == null || currency.isBlank() || "default".equals(currency)) {
            return plugin.getDefaultEconomyCurrency();
        }
        return currency.trim();
    }

    private String msg(String key, Object... args) {
        return plugin.formatLocaleMessage(key, args);
    }
}
