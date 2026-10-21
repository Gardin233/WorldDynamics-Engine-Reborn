package com.thatguycy.worlddynamicsengine.command.diplomacy;

import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.Arrays;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 处理国家外交态度、贸易开关及访问传送，并校验战争、制裁和访问费用。
 */
public class DiplomacyCommand implements CommandExecutor {
    private final NationManager nationManager;
    private final ResidentManager residentManager;
    private final EconomyBridge economyBridge;
    private final OrgManager orgManager;

    public DiplomacyCommand(NationManager nationManager, ResidentManager residentManager, EconomyBridge economyBridge, OrgManager orgManager) {
        this.nationManager = nationManager;
        this.residentManager = residentManager;
        this.economyBridge = economyBridge;
        this.orgManager = orgManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_unknown_command"));
            return true;
        }

        if (!command.getName().equalsIgnoreCase("wde") || !args[0].equalsIgnoreCase("diplomacy")) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_unknown_command"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_insufficient_arguments"));
            return true;
        }

        if (!WorldDynamicsEngine.getInstance().govEnabled) {
            sender.sendMessage(ChatColor.RED + msg("unknownsub"));
            return true;
        }

        String nationName = args[2];
        switch (args[1].toLowerCase()) {
            case "viewtrading":
                return handleViewTradingRelations(sender, nationName, WorldDynamicsEngine.getInstance());
            case "relations":
                return handleViewDiplomaticRelations(sender, nationName, WorldDynamicsEngine.getInstance());
            case "setrelation":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + msg("diplomacy_usage_setrelation"));
                    return true;
                }
                return handleSetRelation(sender, nationName, args, WorldDynamicsEngine.getInstance());
            case "trading":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + msg("diplomacy_usage_trading"));
                    return true;
                }
                return handleSetTrading(sender, nationName, args, WorldDynamicsEngine.getInstance());
            case "visit":
                return handleVisit(sender, nationName, nationManager, residentManager, economyBridge);
            case "letter":
            case "letters":
            case "readletter":
                return handleLetters(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + msg("unknownsub"));
                return true;
        }
    }

    private boolean handleSetRelation(CommandSender sender, String targetNationName, String[] args, JavaPlugin plugin) {
        Nation senderNation = getNationByResident(sender.getName());
        if (senderNation == null) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_must_be_in_nation"));
            return true;
        }

        if (!hasDiplomaticAuthority(sender)) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_no_authority_relation"));
            return true;
        }

        if (nationManager.getNation(targetNationName) == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_with_name", targetNationName));
            return true;
        }

        String relation = args[3].toLowerCase();
        if (!Arrays.asList("friendly", "neutral", "unfriendly").contains(relation)) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_invalid_relation"));
            return true;
        }

        if ("unfriendly".equalsIgnoreCase(relation) && orgManager != null && !orgManager.getMilitaryAlliancesByNation(senderNation.getName()).isEmpty()) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_military_alliance_requires_vote"));
            return true;
        }

        if ("unfriendly".equals(relation)) {
            if (args.length != 5) {
                sender.sendMessage(ChatColor.RED + msg("war_reason_required"));
                return true;
            }
            var reason = com.thatguycy.worlddynamicsengine.model.war.WarReason.parse(args[4]);
            String reasonError = orgManager.warReasonFailure(reason, senderNation.getName(), targetNationName);
            if (reasonError != null) {
                sender.sendMessage(ChatColor.RED + msg(reasonError));
                return true;
            }
            var warResult = orgManager.handleMilitaryAllianceWar(senderNation.getName(), targetNationName, nationManager, reason);
            if (!warResult.isSuccess()) {
                sender.sendMessage(ChatColor.RED + msg(warResult.getFailureMessageKey(), warResult.getFailureMessageArgs()));
                return true;
            }
        }
        if (!"unfriendly".equalsIgnoreCase(relation)
                && orgManager != null
                && orgManager.hasActiveWarBetween(senderNation.getName(), targetNationName)) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_relation_locked_by_war", targetNationName));
            return true;
        }

        WDEnation senderWdeNation = nationManager.getNation(senderNation.getName());
        if (senderWdeNation != null) {
            senderWdeNation.setDiplomaticRelation(targetNationName, relation);
            sender.sendMessage(ChatColor.GREEN + msg("diplomacy_relation_set", relation, targetNationName));
            nationManager.saveNations();
        } else {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_own_nation_data_missing"));
        }

        return true;
    }

    private boolean handleLetters(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("common_only_players"));
            return true;
        }
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        Nation own = getNationByResident(player.getName());
        if (own == null || !hasDiplomaticAuthority(sender)) {
            sender.sendMessage(msg("diplomacy_no_authority_relation"));
            return true;
        }
        var storage = plugin.getDataStorageManager();
        String category = "diplomatic_letters";
        if (!args[1].equalsIgnoreCase("letter")) {
            var letters = storage.loadStringKeyed(category, com.thatguycy.worlddynamicsengine.model.DiplomaticLetter.class);
            if (args[1].equalsIgnoreCase("readletter")) {
                var letter = letters.get(args[2]);
                if (letter == null || !java.util.List.of(letter.value("senderNationUuid"), letter.value("targetNationUuid"))
                        .contains(own.getUUID().toString())) {
                    sender.sendMessage(msg("diplomacy_letter_not_found"));
                    return true;
                }
                int page;
                try { page = args.length > 3 ? Integer.parseInt(args[3]) : 1; }
                catch (NumberFormatException ex) { sender.sendMessage(msg("diplomacy_letter_usage")); return true; }
                String body = letter.value("body");
                int pages = Math.max(1, (body.length() + 399) / 400);
                if (page < 1 || page > pages) { sender.sendMessage(msg("diplomacy_letter_usage")); return true; }
                sender.sendMessage(msg("diplomacy_letter_header", letter.value("sender"), letter.value("target"),
                        msg("diplomacy_letter_type_" + letter.value("type").toLowerCase(java.util.Locale.ROOT)), page, pages));
                sender.sendMessage(body.substring((page - 1) * 400, Math.min(page * 400, body.length())));
            } else {
                int page;
                try { page = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { sender.sendMessage(msg("diplomacy_letter_usage")); return true; }
                var visible = letters.values().stream().filter(letter ->
                        own.getUUID().toString().equals(letter.value("senderNationUuid"))
                                || own.getUUID().toString().equals(letter.value("targetNationUuid")))
                        .sorted(java.util.Comparator.comparingLong((com.thatguycy.worlddynamicsengine.model.DiplomaticLetter letter) ->
                                ((Number) letter.data.get("sentAt")).longValue()).reversed()).toList();
                int pages = Math.max(1, (visible.size() + 7) / 8);
                if (page < 1 || page > pages) { sender.sendMessage(msg("diplomacy_letter_usage")); return true; }
                sender.sendMessage(msg("diplomacy_letter_list", page, pages));
                visible.subList((page - 1) * 8, Math.min(page * 8, visible.size())).forEach(letter ->
                        sender.sendMessage(msg("diplomacy_letter_entry", letter.value("id"), letter.value("sender"),
                                letter.value("target"), msg("diplomacy_letter_type_" + letter.value("type").toLowerCase(java.util.Locale.ROOT)))));
            }
            return true;
        }
        if (args.length < 5) { sender.sendMessage(msg("diplomacy_letter_usage")); return true; }
        Nation target = nationManager.getTownyNation(args[2]);
        if (target == null || target.getUUID().equals(own.getUUID())) {
            sender.sendMessage(msg("diplomacy_letter_target_invalid"));
            return true;
        }
        String type = switch (args[3].toLowerCase(java.util.Locale.ROOT)) {
            case "insult", "侮辱" -> "INSULT";
            case "notice", "通知" -> "NOTICE";
            case "request", "请求" -> "REQUEST";
            default -> "";
        };
        String body = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();
        if (type.isEmpty() || body.isEmpty() || body.length() > 4000 || body.chars().anyMatch(Character::isISOControl)) {
            sender.sendMessage(msg("diplomacy_letter_usage"));
            return true;
        }
        java.math.BigDecimal amount;
        String currency = plugin.getConfig().getString("diplomacy.letters.cost.currency", "default");
        try {
            amount = new java.math.BigDecimal(plugin.getConfig().getString("diplomacy.letters.cost.amount", "100.00"));
            if (amount.signum() < 0 || amount.scale() > 8
                    || amount.compareTo(new java.math.BigDecimal("1000000000000")) > 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            sender.sendMessage(msg("diplomacy_letter_config_invalid"));
            return true;
        }
        if (economyBridge == null || !economyBridge.hasProvider()) {
            sender.sendMessage(msg("diplomacy_economy_provider_missing"));
            return true;
        }
        if (!economyBridge.supportsExactCurrency(currency)) {
            sender.sendMessage(msg("diplomacy_letter_config_invalid"));
            return true;
        }
        var cost = new EconomyBridge.Charge(amount, currency);
        if (!economyBridge.has(player, cost) || !economyBridge.withdraw(player, cost).success()) {
            sender.sendMessage(msg("diplomacy_letter_insufficient", economyBridge.format(cost)));
            return true;
        }
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("id", java.util.UUID.randomUUID().toString());
        values.put("senderNationUuid", own.getUUID().toString());
        values.put("targetNationUuid", target.getUUID().toString());
        values.put("sender", own.getName());
        values.put("target", target.getName());
        values.put("author", player.getName());
        values.put("authorUuid", player.getUniqueId().toString());
        values.put("type", type);
        values.put("body", body);
        values.put("sentAt", System.currentTimeMillis());
        try {
            storage.saveEntity(category, String.valueOf(values.get("id")),
                    new com.thatguycy.worlddynamicsengine.model.DiplomaticLetter(values));
        } catch (RuntimeException ex) {
            if (!economyBridge.deposit(player, amount, currency).success()) {
                plugin.getLogger().severe("Diplomatic letter refund failed for " + player.getUniqueId());
            }
            plugin.getLogger().severe("Diplomatic letter storage failed: " + ex.getMessage());
            sender.sendMessage(msg("diplomacy_letter_storage_failed"));
            return true;
        }
        if (plugin.getNationHistoryManager() != null) {
            try {
                plugin.getNationHistoryManager().record(own.getName(), "DIPLOMATIC_LETTER_" + type, values);
                plugin.getNationHistoryManager().record(target.getName(), "DIPLOMATIC_LETTER_" + type, values);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Diplomatic letter history failed, letter=" + values.get("id") + ": " + ex.getMessage());
                sender.sendMessage(msg("diplomacy_letter_history_failed"));
            }
        }
        sender.sendMessage(msg("diplomacy_letter_sent", target.getName(), economyBridge.format(cost), values.get("id")));
        for (Player receiver : org.bukkit.Bukkit.getOnlinePlayers()) {
            Nation receiverNation = getNationByResident(receiver.getName());
            if (receiverNation != null && receiverNation.getUUID().equals(target.getUUID())) {
                receiver.sendMessage(msg("diplomacy_letter_received", own.getName(),
                        msg("diplomacy_letter_type_" + type.toLowerCase(java.util.Locale.ROOT)), values.get("id")));
            }
        }
        return true;
    }

    private boolean handleSetTrading(CommandSender sender, String targetNationName, String[] args, JavaPlugin plugin) {
        Nation senderNation = getNationByResident(sender.getName());
        if (senderNation == null) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_must_be_in_nation"));
            return true;
        }

        if (!hasDiplomaticAuthority(sender)) {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_no_authority_trading"));
            return true;
        }

        if (nationManager.getNation(targetNationName) == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_with_name", targetNationName));
            return true;
        }

        boolean tradingEnabled = args[3].equalsIgnoreCase("enabled");
        WDEnation senderWdeNation = nationManager.getNation(senderNation.getName());
        if (senderWdeNation != null) {
            senderWdeNation.setTradingStatus(targetNationName, tradingEnabled);
            sender.sendMessage(ChatColor.GREEN + msg("diplomacy_trading_set", tradingEnabled ? msg("common_enabled") : msg("common_disabled"), targetNationName));
            nationManager.saveNations();
        } else {
            sender.sendMessage(ChatColor.RED + msg("diplomacy_own_nation_data_missing"));
        }

        return true;
    }

    public Nation getNationByResident(String residentName) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(residentName);
            if (resident != null) {
                if (resident.hasTown()) {
                    Town town = resident.getTown();
                    if (town.hasNation()) {
                        return town.getNation();
                    }
                }
            }
        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean hasDiplomaticAuthority(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;
        String senderName = player.getName();

        try {
            Resident resident = TownyUniverse.getInstance().getResident(senderName);
            if (resident != null && resident.hasTown() && resident.getTown().hasNation()) {
                Nation senderNation = resident.getTown().getNation();
                if (senderName.equalsIgnoreCase(senderNation.getKing().getName())) {
                    return true;
                }

                WDEnation wdeNation = nationManager.getNation(senderNation.getName());
                if (wdeNation != null) {
                    if (senderName.equalsIgnoreCase(wdeNation.getGovernmentLeader()) ||
                            senderName.equalsIgnoreCase(wdeNation.getArmyCommander())) {
                        return true;
                    }
                }
            }
        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean handleViewTradingRelations(CommandSender sender, String nationName, JavaPlugin plugin) {
        WDEnation nation = nationManager.getNation(nationName);
        if (nation == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_with_name", nationName));
            return true;
        }

        Map<String, Boolean> tradingStatus = nation.getTradingStatus();
        if (tradingStatus == null || tradingStatus.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + msg("diplomacy_no_trading_relations", nationName));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + msg("diplomacy_trading_relations_header", nationName));
        tradingStatus.forEach((otherNation, status) -> {
            String statusText = status ? msg("common_enabled") : msg("common_disabled");
            sender.sendMessage(ChatColor.GOLD + otherNation + ": " + statusText);
        });

        return true;
    }

    private boolean handleViewDiplomaticRelations(CommandSender sender, String nationName, JavaPlugin plugin) {
        WDEnation nation = nationManager.getNation(nationName);
        if (nation == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_with_name", nationName));
            return true;
        }

        Map<String, String> diplomaticRelations = nation.getDiplomaticRelations();
        if (diplomaticRelations == null || diplomaticRelations.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + msg("diplomacy_no_relations", nationName));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + msg("diplomacy_relations_header", nationName));
        diplomaticRelations.forEach((otherNation, relation) -> {
            sender.sendMessage(ChatColor.GOLD + otherNation + ": " + relation);
        });

        return true;
    }

    public VisitResult checkVisit(String diplomatNationName, String destinationNationName, NationManager nationManager) {
        WDEnation diplomatNation = nationManager.getNation(diplomatNationName);
        WDEnation destinationNation = nationManager.getNation(destinationNationName);
        EconomyBridge.Charge zeroCharge = WorldDynamicsEngine.getInstance().getEconomyBridge() != null
                ? WorldDynamicsEngine.getInstance().getEconomyBridge().charge(java.math.BigDecimal.ZERO, WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency())
                : new EconomyBridge.Charge(java.math.BigDecimal.ZERO, WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency());

        if (diplomatNation == null || destinationNation == null) {
            return new VisitResult(false, zeroCharge, "diplomacy_visit_not_permitted");
        }
        if (orgManager != null && orgManager.hasActiveWarBetween(diplomatNationName, destinationNationName)) {
            return new VisitResult(false, zeroCharge, "diplomacy_visit_not_permitted");
        }
        if (orgManager != null && orgManager.isNationTradeSanctionedBy(diplomatNationName, destinationNationName)) {
            return new VisitResult(false, zeroCharge, "diplomacy_visit_blocked_by_trade_sanction");
        }

        String statusToDestination = diplomatNation.getDiplomaticRelations().get(destinationNationName);
        String statusFromDestination = destinationNation.getDiplomaticRelations().get(diplomatNationName);
        boolean hasUnconditionalPassage = nationManager.hasUnconditionalPassage(destinationNationName, diplomatNationName);

        if (!hasUnconditionalPassage && ("unfriendly".equalsIgnoreCase(statusToDestination) || "unfriendly".equalsIgnoreCase(statusFromDestination))) {
            return new VisitResult(false, zeroCharge, "diplomacy_visit_not_permitted");
        }

        EconomyBridge.Charge price = hasUnconditionalPassage
                ? zeroCharge
                : WorldDynamicsEngine.getInstance().getDiplomacyVisitCostNeutralNoneCharge();
        if ("friendly".equalsIgnoreCase(statusToDestination) && "friendly".equalsIgnoreCase(statusFromDestination)) {
            price = WorldDynamicsEngine.getInstance().getDiplomacyVisitCostFriendlyCharge();
        } else if (Boolean.TRUE.equals(diplomatNation.getTradingStatus().get(destinationNationName))
                && Boolean.TRUE.equals(destinationNation.getTradingStatus().get(diplomatNationName))) {
            price = zeroCharge;
        }

        return new VisitResult(true, price, null);
    }

    /**
     * 封装外交访问是否成功、实际费用或失败原因，供调用方展示。
     */
    public static class VisitResult {
        private final boolean allowed;
        private final EconomyBridge.Charge price;
        private final String messageKey;

        public VisitResult(boolean allowed, EconomyBridge.Charge price, String messageKey) {
            this.allowed = allowed;
            this.price = price;
            this.messageKey = messageKey == null || messageKey.isBlank() ? "diplomacy_visit_not_permitted" : messageKey;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public EconomyBridge.Charge getPrice() {
            return price;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    public boolean handleVisit(CommandSender sender, String destinationNationName, NationManager nationManager, ResidentManager residentManager, EconomyBridge economyBridge) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + msg("common_only_players"));
            return true;
        }

        Player player = (Player) sender;
        if (economyBridge == null) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_economy_provider_missing"));
            return true;
        }

        Resident townyResident = TownyUniverse.getInstance().getResident(player.getUniqueId());
        if (townyResident == null) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_towny_data_missing"));
            return true;
        }

        Town playerTown;
        Nation playerNation;
        try {
            if (!townyResident.hasTown()) {
                player.sendMessage(ChatColor.RED + msg("diplomacy_must_be_in_nation"));
                return true;
            }

            playerTown = townyResident.getTown();
            if (!playerTown.hasNation()) {
                player.sendMessage(ChatColor.RED + msg("diplomacy_must_be_in_nation"));
                return true;
            }

            playerNation = playerTown.getNation();
        } catch (NotRegisteredException e) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_towny_nation_missing"));
            return true;
        }

        if (playerNation == null) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_must_be_in_nation"));
            return true;
        }

        String playerNationName = playerNation.getName();
        WDEnation destinationNation = nationManager.getNation(destinationNationName);
        if (destinationNation == null) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_destination_nation_not_found"));
            return true;
        }

        Location capitalSpawnLocation = nationManager.getCapitalSpawnLocation(destinationNation);
        if (capitalSpawnLocation == null) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_capital_spawn_missing"));
            return true;
        }

        // Check visit cost and if player can afford it
        VisitResult visitResult = checkVisit(playerNationName, destinationNationName, nationManager);
        EconomyBridge.Charge cost = visitResult.getPrice();
        if (!visitResult.isAllowed()) {
            player.sendMessage(ChatColor.RED + msg(visitResult.getMessageKey()));
            return true;
        }

        if (!economyBridge.has(player, cost)) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_not_enough_money_visit", economyBridge.format(cost)));
            return true;
        }

        if (!economyBridge.withdraw(player, cost).success()) {
            player.sendMessage(ChatColor.RED + msg("diplomacy_not_enough_money_visit", economyBridge.format(cost)));
            return true;
        }
        player.sendMessage(ChatColor.GREEN + msg("diplomacy_visit_success", destinationNationName, economyBridge.format(cost)));
        player.teleport(capitalSpawnLocation);
        return true;
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }
}
