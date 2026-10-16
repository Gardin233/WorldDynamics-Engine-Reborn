package com.thatguycy.worlddynamicsengine.command.ideology;

import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.economy.GovernmentTreasury;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyTreasuryManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理 /wde ideology 指令，校验唯一信仰归属、领袖资格、改信费用和修改冷却。
 */
public class ReligionCommand implements CommandExecutor {
    private final NationManager nationManager;
    private final TownManager townManager;
    private final ResidentManager residentManager;
    private final ReligionManager religionManager;
    private final EconomyBridge economyBridge;

    public ReligionCommand(NationManager nationManager, TownManager townManager, ResidentManager residentManager, ReligionManager religionManager, EconomyBridge economyBridge) {
        this.nationManager = nationManager;
        this.townManager = townManager;
        this.residentManager = residentManager;
        this.religionManager = religionManager;
        this.economyBridge = economyBridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        if (!plugin.religionEnabled) {
            sender.sendMessage(ChatColor.RED + msg("religion_disabled"));
            return true;
        }

        if (sender instanceof Player player && (args.length < 2 || args[1].equalsIgnoreCase("menu"))) {
            plugin.getPlayerMenuManager().openIdeology(player);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_root"));
            return true;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "create" -> handleCreate(sender, args, plugin);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "setplayer" -> handleSetPlayerReligion(sender, args, plugin);
            case "setnation" -> handleSetNationReligion(sender, args);
            case "settown" -> handleSetTownReligion(sender, args);
            case "abdicate" -> handleAbdicate(sender, args);
            case "excommunicate" -> handleExcommunicate(sender, args);
            case "setmemes" -> handleSetMemes(sender, args);
            case "donate" -> handleDonate(sender, args, plugin);
            case "settithe" -> handleSetTithe(sender, args, plugin);
            case "treasury" -> {
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + msg("religion_usage_treasury"));
                } else if (religionManager.getReligion(args[2]) == null) {
                    sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
                } else if (sender instanceof Player player) {
                    plugin.getPlayerMenuManager().openIdeologyTreasury(player, args[2]);
                } else {
                    handleInfo(sender, args);
                }
                yield true;
            }
            case "withdraw" -> {
                sender.sendMessage(ChatColor.RED + msg("religion_treasury_withdraw_forbidden"));
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + msg("religion_usage_root"));
                yield true;
            }
        };
    }

    private boolean handleDonate(CommandSender sender, String[] args, WorldDynamicsEngine plugin) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_donate"));
            return true;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + msg("religion_treasury_invalid_amount"));
            return true;
        }
        IdeologyTreasuryManager.DonationResult result = plugin.getIdeologyTreasuryManager().donate(player, args[2], amount);
        if (result.status() == IdeologyTreasuryManager.DonationStatus.SUCCESS) {
            sender.sendMessage(ChatColor.GREEN + msg("religion_donation_success", args[2], economyBridge.format(amount)));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.3f);
        } else {
            String key = switch (result.status()) {
                case INVALID_AMOUNT -> "religion_treasury_invalid_amount";
                case NOT_FOUND -> "religion_not_found";
                case UNAVAILABLE -> "religion_treasury_unavailable";
                case INSUFFICIENT_FUNDS -> "religion_donation_insufficient";
                default -> "religion_treasury_storage_error";
            };
            sender.sendMessage(ChatColor.RED + msg(key));
        }
        return true;
    }

    private boolean handleSetTithe(CommandSender sender, String[] args, WorldDynamicsEngine plugin) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (args.length != 5) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_settithe"));
            return true;
        }
        WDEreligion religion = religionManager.getReligion(args[2]);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }
        if (!isPontiff(player, religion)) {
            sender.sendMessage(ChatColor.RED + msg("religion_only_pontiff"));
            return true;
        }
        try {
            WDEreligion.TitheType type = WDEreligion.TitheType.valueOf(args[3].toUpperCase(Locale.ROOT));
            BigDecimal amount = new BigDecimal(args[4]);
            if (!religionManager.configureTithe(religion.getReligionName(), player.getUniqueId(), type, amount)) {
                sender.sendMessage(ChatColor.RED + msg("religion_only_pontiff"));
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + msg("religion_tithe_updated", religion.getReligionName(),
                    msg("religion_tithe_type_" + type.name().toLowerCase(Locale.ROOT)),
                    type == WDEreligion.TitheType.WEALTH_PERCENTAGE ? amount.toPlainString() + "%" : economyBridge.format(amount)));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + msg("religion_tithe_invalid"));
        } catch (IllegalStateException ex) {
            sender.sendMessage(ChatColor.RED + msg("religion_treasury_storage_error"));
            plugin.getLogger().severe("Failed to save ideology tithe: " + ex.getMessage());
        }
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args, WorldDynamicsEngine plugin) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + msg("religion_only_players_create"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_create"));
            return true;
        }

        if (religionManager.isReligionLeader(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + msg("religion_leader_cannot_create"));
            return true;
        }

        String religionName = args[2].trim();
        if (!isValidReligionName(religionName)) {
            sender.sendMessage(ChatColor.RED + msg("religion_invalid_name"));
            return true;
        }

        if (religionManager.religionExists(religionName)) {
            sender.sendMessage(ChatColor.RED + msg("religion_already_exists"));
            return true;
        }

        EconomyBridge.Charge cost = plugin.getReligionCreationCostCharge();
        if (economyBridge == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_economy_provider_missing"));
            return true;
        }
        if (economyBridge != null && !economyBridge.has(player, cost)) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_enough_money", economyBridge.format(cost)));
            return true;
        }

        if (economyBridge != null && !economyBridge.withdraw(player, cost).success()) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_enough_money", economyBridge.format(cost)));
            return true;
        }

        WDEreligion religion = new WDEreligion(religionName, player.getUniqueId(), player.getName(), System.currentTimeMillis());
        religionManager.addReligion(religion);
        WDEresident creator = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        creator.setReligionName(religion.getReligionName());
        creator.setReligionChosen(true);
        residentManager.saveResidents();
        sender.sendMessage(ChatColor.GREEN + msg("religion_created", religionName));
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_info"));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(args[2]);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + msg("religion_info_header", religion.getReligionName()));
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_founder") + ChatColor.WHITE + religion.getFounderName());
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_pontiff") + ChatColor.WHITE + religion.getPontiffName());
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_treasury") + ChatColor.WHITE
                + economyBridge.format(religion.getTreasuryBalance()));
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_tithe") + ChatColor.WHITE
                + msg("religion_tithe_type_" + religion.getTitheType().name().toLowerCase(Locale.ROOT)) + " "
                + (religion.getTitheType() == WDEreligion.TitheType.WEALTH_PERCENTAGE
                        ? religion.getTitheAmount().toPlainString() + "%" : economyBridge.format(religion.getTitheAmount())));
        sender.sendMessage(ChatColor.GRAY + msg("religion_treasury_withdraw_forbidden"));
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_created_at") + ChatColor.WHITE + formatTime(religion.getCreatedAt()));
        List<String> configuredMemes = religionManager.getConfiguredMemes(religion.getMemes());
        Map<String, String> configuredDoctrineAttitudes = religionManager.getConfiguredDoctrineAttitudes(religion);
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_memes") + ChatColor.WHITE + formatMemeList(configuredMemes));
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_doctrines") + ChatColor.WHITE + formatDoctrineList(religion));
        if (!configuredMemes.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_info_meme_descriptions"));
            for (String meme : configuredMemes) {
                sender.sendMessage(ChatColor.GRAY + msg("religion_info_meme_entry", religionManager.getMemeDisplayName(meme), religionManager.getMemeDescription(meme)));
            }
        }
        if (!configuredDoctrineAttitudes.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_info_doctrine_descriptions"));
            for (Map.Entry<String, String> entry : configuredDoctrineAttitudes.entrySet()) {
                sender.sendMessage(ChatColor.GRAY + msg(
                        "religion_info_doctrine_entry",
                        religionManager.getDoctrineDisplayName(entry.getKey()),
                        religionManager.getDoctrineAttitudeDisplayName(entry.getKey(), entry.getValue()),
                        religionManager.getDoctrineAttitudeLore(entry.getKey(), entry.getValue())
                ));
            }
        }
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_excommunicated_players") + ChatColor.WHITE + formatExcommunicatedPlayers(religion.getExcommunicatedPlayers()));
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_excommunicated_towns") + ChatColor.WHITE + formatNameList(religion.getExcommunicatedTowns()));
        sender.sendMessage(ChatColor.YELLOW + msg("religion_info_excommunicated_nations") + ChatColor.WHITE + formatNameList(religion.getExcommunicatedNations()));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> names = religionManager.getAllReligionNames();
        sender.sendMessage(ChatColor.GOLD + msg("religion_list_header", names.size()) + ChatColor.WHITE + (names.isEmpty() ? msg("common_none") : String.join(", ", names)));
        return true;
    }

    private boolean handleSetPlayerReligion(CommandSender sender, String[] args, WorldDynamicsEngine plugin) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_setplayer"));
            return true;
        }

        if (args.length >= 4) {
            return handleForceSetPlayerReligion(player, args, plugin);
        }

        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        String requestedReligion = args[2];
        String currentReligion = normalizeReligionName(resident.getReligionName());

        if ("none".equalsIgnoreCase(requestedReligion)) {
            sender.sendMessage(ChatColor.RED + msg("religion_player_clear_disabled"));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(requestedReligion);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        if (matchesReligion(currentReligion, religion.getReligionName())) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_player_already_set", religion.getReligionName()));
            return true;
        }

        boolean firstFree = isFirstReligionSelection(resident.isReligionChosen(), currentReligion, religion.getReligionName());
        if (requiresPaidConversion(resident.isReligionChosen(), currentReligion, religion.getReligionName())) {
            EconomyBridge.Charge cost = applyIdeologicalAllianceDiscount(resolveResidentNationName(player), plugin.getReligionPlayerConversionCostCharge());
            if (economyBridge == null) {
                sender.sendMessage(ChatColor.RED + msg("religion_economy_provider_missing"));
                return true;
            }
            if (!economyBridge.has(player, cost) || !economyBridge.withdraw(player, cost).success()) {
                sender.sendMessage(ChatColor.RED + msg("religion_player_not_enough_money", economyBridge.format(cost)));
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + msg("religion_player_conversion_paid", economyBridge.format(cost)));
        } else if (firstFree) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_first_selection_free"));
        }

        resident.setReligionName(religion.getReligionName());
        resident.setReligionChosen(true);
        residentManager.saveResidents();
        sender.sendMessage(ChatColor.GREEN + msg("religion_player_set", religion.getReligionName()));
        return true;
    }

    private boolean handleForceSetPlayerReligion(Player actor, String[] args, WorldDynamicsEngine plugin) {
        if (args.length < 4) {
            actor.sendMessage(ChatColor.RED + msg("religion_usage_setplayer_force"));
            return true;
        }

        Resident targetResident = resolveResident(args[2]);
        if (targetResident == null) {
            actor.sendMessage(ChatColor.RED + msg("player_not_found"));
            return true;
        }

        Town targetTown = resolveResidentTown(targetResident);
        if (targetTown == null) {
            actor.sendMessage(ChatColor.RED + msg("religion_player_must_have_town"));
            return true;
        }

        WDEtown targetTownData = townManager.getTown(targetTown.getName());
        if (targetTownData == null || !hasDirectTownAuthority(actor, targetTownData)) {
            actor.sendMessage(ChatColor.RED + msg("religion_no_authority_force_player"));
            return true;
        }

        String requestedReligion = args[3];
        WDEresident resident = residentManager.getOrCreateResident(targetResident.getUUID(), targetResident.getName());
        String currentReligion = normalizeReligionName(resident.getReligionName());
        String townReligion = normalizeReligionName(targetTownData.getReligionName());
        String nationReligion = normalizeReligionName(resolveNationReligion(targetTownData));

        if (!isAllowedForcedResidentReligion(requestedReligion, townReligion, nationReligion)) {
            actor.sendMessage(ChatColor.RED + msg("religion_forced_player_invalid_religion", formatAllowedForcedResidentReligions(townReligion, nationReligion)));
            return true;
        }

        if ("none".equalsIgnoreCase(requestedReligion)) {
            if (isNoneReligion(currentReligion)) {
                actor.sendMessage(ChatColor.YELLOW + msg("religion_player_already_set_target", targetResident.getName(), msg("common_none")));
                return true;
            }

            String targetNationName = resolveTownNationName(targetTown);
            EconomyBridge.Charge forceCost = applyIdeologicalAllianceDiscount(targetNationName, plugin.getReligionForcedPlayerConversionCostCharge());
            if (!withdrawGovernmentCharge(targetTown, forceCost, "WorldDynamicsEngine religion force player conversion")) {
                actor.sendMessage(ChatColor.RED + msg("religion_forced_player_not_enough_treasury", formatGovernmentCharge(forceCost)));
                return true;
            }

            resident.setReligionName("None");
            residentManager.saveResidents();
            actor.sendMessage(ChatColor.YELLOW + msg("religion_forced_player_conversion_paid", formatGovernmentCharge(forceCost)));
            actor.sendMessage(ChatColor.GREEN + msg("religion_forced_player_cleared", targetResident.getName()));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(requestedReligion);
        if (religion == null && !matchesReligion(requestedReligion, townReligion) && !matchesReligion(requestedReligion, nationReligion)) {
            actor.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        String resolvedReligionName = religion != null ? religion.getReligionName()
                : matchesReligion(requestedReligion, townReligion) ? townReligion : nationReligion;

        if (matchesReligion(currentReligion, resolvedReligionName)) {
            actor.sendMessage(ChatColor.YELLOW + msg("religion_player_already_set_target", targetResident.getName(), resolvedReligionName));
            return true;
        }

        String targetNationName = resolveTownNationName(targetTown);
        EconomyBridge.Charge forceCost = applyIdeologicalAllianceDiscount(targetNationName, plugin.getReligionForcedPlayerConversionCostCharge());
        if (!withdrawGovernmentCharge(targetTown, forceCost, "WorldDynamicsEngine religion force player conversion")) {
            actor.sendMessage(ChatColor.RED + msg("religion_forced_player_not_enough_treasury", formatGovernmentCharge(forceCost)));
            return true;
        }

        resident.setReligionName(resolvedReligionName);
        resident.setReligionChosen(true);
        residentManager.saveResidents();
        actor.sendMessage(ChatColor.YELLOW + msg("religion_forced_player_conversion_paid", formatGovernmentCharge(forceCost)));
        actor.sendMessage(ChatColor.GREEN + msg("religion_forced_player_set", targetResident.getName(), resolvedReligionName));
        return true;
    }

    private boolean handleSetNationReligion(CommandSender sender, String[] args) {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_setnation"));
            return true;
        }

        WDEnation nation = nationManager.getNation(args[2]);
        if (nation == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_generic"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null || !hasNationAuthority(player, nation)) {
            sender.sendMessage(ChatColor.RED + msg("religion_no_authority_set_nation"));
            return true;
        }

        String religionName = args[3];
        if ("none".equalsIgnoreCase(religionName)) {
            nation.setReligionName("None");
            nationManager.saveNations();
            sender.sendMessage(ChatColor.GREEN + msg("religion_nation_cleared"));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(religionName);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        String currentReligion = normalizeReligionName(nation.getReligionName());
        if (matchesReligion(currentReligion, religion.getReligionName())) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_nation_already_set", religion.getReligionName()));
            return true;
        }

        boolean firstFree = isFirstReligionSelection(nation.isReligionChosen(), currentReligion, religion.getReligionName());
        if (requiresPaidConversion(nation.isReligionChosen(), currentReligion, religion.getReligionName())) {
            Nation townyNation = resolveNation(nation.getNationName());
            if (townyNation == null) {
                sender.sendMessage(ChatColor.RED + msg("religion_nation_treasury_unavailable"));
                return true;
            }

            EconomyBridge.Charge cost = applyIdeologicalAllianceDiscount(nation.getNationName(), plugin.getReligionNationConversionCostCharge());
            if (!withdrawGovernmentCharge(townyNation, cost, "WorldDynamicsEngine religion nation conversion")) {
                sender.sendMessage(ChatColor.RED + msg("religion_nation_not_enough_treasury", formatGovernmentCharge(cost)));
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + msg("religion_nation_conversion_paid", formatGovernmentCharge(cost)));
        } else if (firstFree) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_first_selection_free"));
        }

        nation.setReligionName(religion.getReligionName());
        nation.setReligionChosen(true);
        nationManager.saveNations();
        sender.sendMessage(ChatColor.GREEN + msg("religion_nation_set", religion.getReligionName()));
        return true;
    }

    private boolean handleSetTownReligion(CommandSender sender, String[] args) {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_settown"));
            return true;
        }

        WDEtown town = townManager.getTown(args[2]);
        if (town == null) {
            sender.sendMessage(ChatColor.RED + msg("town_not_found_generic"));
            return true;
        }

        Player player = requirePlayer(sender);
        boolean directTownAuthority = player != null && hasDirectTownAuthority(player, town);
        boolean nationAuthority = player != null && hasNationAuthorityOverTown(player, town);
        boolean forcedByNation = !directTownAuthority && nationAuthority;
        if (player == null || (!directTownAuthority && !nationAuthority)) {
            sender.sendMessage(ChatColor.RED + msg("religion_no_authority_set_town"));
            return true;
        }

        String religionName = args[3];
        if ("none".equalsIgnoreCase(religionName)) {
            if (forcedByNation) {
                String nationReligion = normalizeReligionName(resolveNationReligion(town));
                if (!isNoneReligion(nationReligion)) {
                    sender.sendMessage(ChatColor.RED + msg("religion_forced_town_invalid_religion", nationReligion));
                    return true;
                }
            }

            if (isNoneReligion(town.getReligionName())) {
                sender.sendMessage(ChatColor.YELLOW + msg("religion_town_already_set", msg("common_none")));
                return true;
            }

            if (forcedByNation) {
                Nation forcingNation = resolveNation(town.getNationName());
                if (forcingNation == null) {
                    sender.sendMessage(ChatColor.RED + msg("religion_nation_treasury_unavailable"));
                    return true;
                }

                EconomyBridge.Charge forceCost = applyIdeologicalAllianceDiscount(town.getNationName(), plugin.getReligionForcedTownConversionCostCharge());
                if (!withdrawGovernmentCharge(forcingNation, forceCost, "WorldDynamicsEngine religion force town conversion")) {
                    sender.sendMessage(ChatColor.RED + msg("religion_forced_town_not_enough_treasury", formatGovernmentCharge(forceCost)));
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + msg("religion_forced_town_conversion_paid", formatGovernmentCharge(forceCost)));
            }

            town.setReligionName("None");
            townManager.saveTowns();
            sender.sendMessage(ChatColor.GREEN + (forcedByNation
                    ? msg("religion_forced_town_cleared", town.getTownName())
                    : msg("religion_town_cleared")));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(religionName);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        String currentReligion = normalizeReligionName(town.getReligionName());
        if (matchesReligion(currentReligion, religion.getReligionName())) {
            sender.sendMessage(ChatColor.YELLOW + msg("religion_town_already_set", religion.getReligionName()));
            return true;
        }

        if (forcedByNation) {
            String nationReligion = normalizeReligionName(resolveNationReligion(town));
            if (!matchesReligion(religion.getReligionName(), nationReligion)) {
                sender.sendMessage(ChatColor.RED + msg("religion_forced_town_invalid_religion", nationReligion));
                return true;
            }

            Nation forcingNation = resolveNation(town.getNationName());
            if (forcingNation == null) {
                sender.sendMessage(ChatColor.RED + msg("religion_nation_treasury_unavailable"));
                return true;
            }

            EconomyBridge.Charge forceCost = applyIdeologicalAllianceDiscount(town.getNationName(), plugin.getReligionForcedTownConversionCostCharge());
            if (!withdrawGovernmentCharge(forcingNation, forceCost, "WorldDynamicsEngine religion force town conversion")) {
                sender.sendMessage(ChatColor.RED + msg("religion_forced_town_not_enough_treasury", formatGovernmentCharge(forceCost)));
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + msg("religion_forced_town_conversion_paid", formatGovernmentCharge(forceCost)));
        } else {
            boolean firstFree = isFirstReligionSelection(town.isReligionChosen(), currentReligion, religion.getReligionName());
            if (requiresPaidConversion(town.isReligionChosen(), currentReligion, religion.getReligionName())) {
                Town townyTown = resolveTown(town.getTownName());
                if (townyTown == null) {
                    sender.sendMessage(ChatColor.RED + msg("religion_town_treasury_unavailable"));
                    return true;
                }

                EconomyBridge.Charge cost = applyIdeologicalAllianceDiscount(town.getNationName(), plugin.getReligionTownConversionCostCharge());
                if (!withdrawGovernmentCharge(townyTown, cost, "WorldDynamicsEngine religion town conversion")) {
                    sender.sendMessage(ChatColor.RED + msg("religion_town_not_enough_treasury", formatGovernmentCharge(cost)));
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + msg("religion_town_conversion_paid", formatGovernmentCharge(cost)));
            } else if (firstFree) {
                sender.sendMessage(ChatColor.YELLOW + msg("religion_first_selection_free"));
            }
        }

        town.setReligionName(religion.getReligionName());
        town.setReligionChosen(true);
        townManager.saveTowns();
        sender.sendMessage(ChatColor.GREEN + (forcedByNation
                ? msg("religion_forced_town_set", town.getTownName(), religion.getReligionName())
                : msg("religion_town_set", religion.getReligionName())));
        return true;
    }

    private boolean handleAbdicate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_abdicate"));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(args[2]);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        if (!isPontiff(player, religion)) {
            sender.sendMessage(ChatColor.RED + msg("religion_only_pontiff"));
            return true;
        }

        Resident targetResident = resolveResident(args[3]);
        if (targetResident == null) {
            sender.sendMessage(ChatColor.RED + msg("player_not_found"));
            return true;
        }

        UUID targetUuid = targetResident.getUUID();
        if (targetUuid.equals(religion.getPontiffUuid())) {
            sender.sendMessage(ChatColor.RED + msg("religion_target_already_pontiff"));
            return true;
        }

        if (religion.isPlayerExcommunicated(targetUuid)) {
            sender.sendMessage(ChatColor.RED + msg("religion_target_excommunicated"));
            return true;
        }

        if (!belongsToReligion(targetResident, religion.getReligionName())) {
            sender.sendMessage(ChatColor.RED + msg("religion_target_not_same_faith_player"));
            return true;
        }

        religion.setPontiff(targetUuid, targetResident.getName());
        religionManager.saveReligion(religion);
        sender.sendMessage(ChatColor.GREEN + msg("religion_abdicated", targetResident.getName(), religion.getReligionName()));
        return true;
    }

    private boolean handleExcommunicate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_excommunicate"));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(args[2]);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        if (!isPontiff(player, religion)) {
            sender.sendMessage(ChatColor.RED + msg("religion_only_pontiff"));
            return true;
        }

        String targetType = args[3].toLowerCase(Locale.ROOT);
        String targetName = args[4];
        switch (targetType) {
            case "player":
                return handlePlayerExcommunication(sender, religion, targetName);
            case "town":
                return handleTownExcommunication(sender, religion, targetName);
            case "nation":
                return handleNationExcommunication(sender, religion, targetName);
            default:
                sender.sendMessage(ChatColor.RED + msg("religion_usage_excommunicate"));
                return true;
        }
    }

    private boolean handleSetMemes(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 4 || args.length > 7) {
            sender.sendMessage(ChatColor.RED + msg("religion_usage_setmemes"));
            return true;
        }

        WDEreligion religion = religionManager.getReligion(args[2]);
        if (religion == null) {
            sender.sendMessage(ChatColor.RED + msg("religion_not_found"));
            return true;
        }

        if (!isPontiff(player, religion)) {
            sender.sendMessage(ChatColor.RED + msg("religion_only_pontiff_set_memes"));
            return true;
        }

        if (religionManager.isMemeModificationBlocked(religion)) {
            sender.sendMessage(ChatColor.RED + msg("religion_memes_cooldown",
                    religionManager.describeMemeModificationCooldown(WorldDynamicsEngine.getInstance().userLang, religion)));
            return true;
        }

        Set<String> selectedMemes = new LinkedHashSet<>();
        for (int i = 3; i < args.length; i++) {
            String normalizedMeme = religionManager.normalizeMeme(args[i]);
            if (normalizedMeme == null) {
                sender.sendMessage(ChatColor.RED + msg("religion_invalid_meme", args[i], String.join("、", religionManager.getValidMemes())));
                return true;
            }

            if (!selectedMemes.add(normalizedMeme)) {
                sender.sendMessage(ChatColor.RED + msg("religion_duplicate_meme", religionManager.getMemeDisplayName(normalizedMeme)));
                return true;
            }

            String conflictMeme = religionManager.findExclusiveMemeConflict(normalizedMeme, selectedMemes);
            if (conflictMeme != null) {
                sender.sendMessage(ChatColor.RED + msg(
                        "religion_mutually_exclusive_meme",
                        religionManager.getMemeDisplayName(normalizedMeme),
                        religionManager.getMemeDisplayName(conflictMeme)
                ));
                return true;
            }
        }

        if (selectedMemes.isEmpty() || selectedMemes.size() > 4) {
            sender.sendMessage(ChatColor.RED + msg("religion_memes_must_be_four"));
            return true;
        }

        religion.setMemes(selectedMemes);
        religionManager.reconcileDoctrineAttitudes(religion);
        religionManager.markMemeModified(religion);
        religionManager.saveReligion(religion);
        sender.sendMessage(ChatColor.GREEN + msg("religion_memes_set", religion.getReligionName(), formatMemeList(religion.getMemes())));
        return true;
    }

    private boolean handlePlayerExcommunication(CommandSender sender, WDEreligion religion, String playerName) {
        Resident resident = resolveResident(playerName);
        if (resident == null) {
            sender.sendMessage(ChatColor.RED + msg("player_not_found"));
            return true;
        }

        if (resident.getUUID().equals(religion.getPontiffUuid())) {
            sender.sendMessage(ChatColor.RED + msg("religion_cannot_excommunicate_pontiff"));
            return true;
        }

        if (!belongsToReligion(resident, religion.getReligionName())) {
            sender.sendMessage(ChatColor.RED + msg("religion_target_not_same_faith_player"));
            return true;
        }

        if (religion.isPlayerExcommunicated(resident.getUUID())) {
            sender.sendMessage(ChatColor.RED + msg("religion_player_already_excommunicated"));
            return true;
        }

        religion.excommunicatePlayer(resident.getUUID(), resident.getName());
        religionManager.saveReligion(religion);
        sender.sendMessage(ChatColor.GREEN + msg("religion_player_excommunicated", resident.getName(), religion.getReligionName()));
        return true;
    }

    private boolean handleTownExcommunication(CommandSender sender, WDEreligion religion, String townName) {
        WDEtown town = townManager.getTown(townName);
        if (town == null) {
            sender.sendMessage(ChatColor.RED + msg("town_not_found_generic"));
            return true;
        }

        if (!matchesReligion(town.getReligionName(), religion.getReligionName())) {
            sender.sendMessage(ChatColor.RED + msg("religion_target_not_same_faith_town"));
            return true;
        }

        if (religion.isTownExcommunicated(town.getTownName())) {
            sender.sendMessage(ChatColor.RED + msg("religion_town_already_excommunicated"));
            return true;
        }

        religion.excommunicateTown(town.getTownName());
        religionManager.saveReligion(religion);
        sender.sendMessage(ChatColor.GREEN + msg("religion_town_excommunicated", town.getTownName(), religion.getReligionName()));
        return true;
    }

    private boolean handleNationExcommunication(CommandSender sender, WDEreligion religion, String nationName) {
        WDEnation nation = nationManager.getNation(nationName);
        if (nation == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_generic"));
            return true;
        }

        if (!matchesReligion(nation.getReligionName(), religion.getReligionName())) {
            sender.sendMessage(ChatColor.RED + msg("religion_target_not_same_faith_nation"));
            return true;
        }

        if (religion.isNationExcommunicated(nation.getNationName())) {
            sender.sendMessage(ChatColor.RED + msg("religion_nation_already_excommunicated"));
            return true;
        }

        religion.excommunicateNation(nation.getNationName());
        religionManager.saveReligion(religion);
        sender.sendMessage(ChatColor.GREEN + msg("religion_nation_excommunicated", nation.getNationName(), religion.getReligionName()));
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + msg("common_only_players"));
            return null;
        }
        return player;
    }

    private boolean hasNationAuthority(Player player, WDEnation nation) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            Nation townyNation = TownyUniverse.getInstance().getNation(nation.getNationName());
            if (resident == null || townyNation == null) {
                return false;
            }

            if (townyNation.isKing(resident)) {
                return true;
            }

            return nation.getGovernmentLeader() != null && nation.getGovernmentLeader().equalsIgnoreCase(player.getName());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasTownAuthority(Player player, WDEtown town) {
        return hasDirectTownAuthority(player, town) || hasNationAuthorityOverTown(player, town);
    }

    private boolean hasDirectTownAuthority(Player player, WDEtown town) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                return false;
            }

            Town playerTown = resident.getTown();
            if (playerTown.getName().equalsIgnoreCase(town.getTownName())) {
                if (town.getTownLeader() != null && town.getTownLeader().equalsIgnoreCase(player.getName())) {
                    return true;
                }
                return playerTown.getMayor() != null && playerTown.getMayor().getUUID().equals(player.getUniqueId());
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean hasNationAuthorityOverTown(Player player, WDEtown town) {
        if (town == null || town.getNationName() == null || isNoneReligion(town.getNationName()) || "none".equalsIgnoreCase(town.getNationName())) {
            return false;
        }

        WDEnation nation = nationManager.getNation(town.getNationName());
        if (nation == null) {
            return false;
        }
        return hasNationAuthority(player, nation);
    }

    private boolean isValidReligionName(String religionName) {
        return religionName != null && religionName.matches("[\\p{L}\\p{N}_-]{2,24}");
    }

    private boolean isPontiff(Player player, WDEreligion religion) {
        return player != null && religion != null && player.getUniqueId().equals(religion.getPontiffUuid());
    }

    private Resident resolveResident(String playerName) {
        try {
            return TownyUniverse.getInstance().getResident(playerName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Town resolveTown(String townName) {
        try {
            return TownyUniverse.getInstance().getTown(townName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Nation resolveNation(String nationName) {
        try {
            return TownyUniverse.getInstance().getNation(nationName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Town resolveResidentTown(Resident resident) {
        try {
            return resident != null && resident.hasTown() ? resident.getTown() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveNationReligion(WDEtown town) {
        if (town == null || town.getNationName() == null || town.getNationName().isBlank() || "none".equalsIgnoreCase(town.getNationName())) {
            return "None";
        }
        WDEnation nation = nationManager.getNation(town.getNationName());
        return nation == null ? "None" : normalizeReligionName(nation.getReligionName());
    }

    private boolean isAllowedForcedResidentReligion(String requestedReligion, String townReligion, String nationReligion) {
        String normalizedRequested = normalizeReligionName(requestedReligion);
        return matchesReligion(normalizedRequested, townReligion)
                || matchesReligion(normalizedRequested, nationReligion)
                || (isNoneReligion(normalizedRequested) && (isNoneReligion(townReligion) || isNoneReligion(nationReligion)));
    }

    private String formatAllowedForcedResidentReligions(String townReligion, String nationReligion) {
        List<String> allowed = new ArrayList<>();
        if (!isNoneReligion(townReligion)) {
            allowed.add(townReligion + "（城镇）");
        }
        if (!isNoneReligion(nationReligion) && allowed.stream().noneMatch(entry -> entry.startsWith(nationReligion + "（"))) {
            allowed.add(nationReligion + "（国家）");
        }
        if (allowed.isEmpty()) {
            allowed.add(msg("common_none"));
        }
        return String.join("、", allowed);
    }

    private boolean belongsToReligion(Resident resident, String religionName) {
        return matchesReligion(resolveResidentReligion(resident), religionName);
    }

    private String resolveResidentReligion(Resident resident) {
        if (resident == null) {
            return "None";
        }

        WDEresident wdeResident = residentManager.getOrCreateResident(resident.getUUID(), resident.getName());
        return normalizeReligionName(wdeResident.getReligionName());
    }

    private boolean matchesReligion(String currentReligion, String expectedReligion) {
        return !isNoneReligion(currentReligion) && currentReligion.equalsIgnoreCase(expectedReligion);
    }

    private String normalizeReligionName(String religionName) {
        return isNoneReligion(religionName) ? "None" : religionName;
    }

    private boolean isNoneReligion(String religionName) {
        return religionName == null || religionName.isBlank() || "none".equalsIgnoreCase(religionName);
    }

    private boolean isFirstReligionSelection(boolean religionChosen, String currentReligion, String targetReligion) {
        return !religionChosen && isNoneReligion(currentReligion) && !isNoneReligion(targetReligion);
    }

    private boolean requiresPaidConversion(boolean religionChosen, String currentReligion, String targetReligion) {
        return religionChosen && !isNoneReligion(targetReligion) && !matchesReligion(currentReligion, targetReligion);
    }

    public EconomyBridge.Charge getPlayerConversionCharge(Player player) {
        return applyIdeologicalAllianceDiscount(resolveResidentNationName(player),
                WorldDynamicsEngine.getInstance().getReligionPlayerConversionCostCharge());
    }

    private EconomyBridge.Charge applyIdeologicalAllianceDiscount(String nationName, EconomyBridge.Charge baseCharge) {
        if (baseCharge == null || baseCharge.amount() == null || nationName == null || nationName.isBlank()) {
            return baseCharge;
        }

        com.thatguycy.worlddynamicsengine.manager.organization.OrgManager orgManager = WorldDynamicsEngine.getInstance().getOrgManager();
        if (orgManager == null) {
            return baseCharge;
        }

        double discountRate = orgManager.getIdeologicalAllianceConversionDiscountRate(nationName, nationManager);
        if (discountRate <= 0.0D) {
            return baseCharge;
        }

        BigDecimal multiplier = BigDecimal.ONE.subtract(BigDecimal.valueOf(discountRate));
        if (multiplier.compareTo(BigDecimal.ZERO) < 0) {
            multiplier = BigDecimal.ZERO;
        }

        BigDecimal discountedAmount = baseCharge.amount().multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP);
        return new EconomyBridge.Charge(discountedAmount.max(BigDecimal.ZERO), baseCharge.currency());
    }

    private String resolveResidentNationName(Player player) {
        if (player == null) {
            return null;
        }

        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown() || !resident.getTown().hasNation()) {
                return null;
            }
            return resident.getTown().getNation().getName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveTownNationName(Town town) {
        if (town == null || !town.hasNation()) {
            return null;
        }

        try {
            Nation nation = town.getNation();
            return nation == null ? null : nation.getName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean withdrawGovernmentCharge(Government government, EconomyBridge.Charge charge, String reason) {
        return GovernmentTreasury.withdraw(government, charge, economyBridge,
                WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency(), reason);
    }

    private String formatGovernmentCharge(EconomyBridge.Charge charge) {
        return GovernmentTreasury.format(charge, economyBridge,
                WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency());
    }

    private String formatExcommunicatedPlayers(Map<String, String> players) {
        if (players == null || players.isEmpty()) {
            return msg("common_none");
        }
        return String.join(", ", new ArrayList<>(players.values()));
    }

    private String formatNameList(Iterable<String> values) {
        List<String> names = new ArrayList<>();
        for (String value : values) {
            names.add(value);
        }
        return names.isEmpty() ? msg("common_none") : String.join(", ", names);
    }

    private String formatMemeList(Iterable<String> values) {
        List<String> names = new ArrayList<>();
        for (String value : values) {
            if (religionManager.getMeme(value) != null) {
                names.add(religionManager.getMemeDisplayName(value));
            }
        }
        return names.isEmpty() ? msg("common_none") : String.join("、", names);
    }

    private String formatDoctrineList(WDEreligion religion) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : religionManager.getConfiguredDoctrineAttitudes(religion).entrySet()) {
            values.add(religionManager.getDoctrineDisplayName(entry.getKey()) + " -> "
                    + religionManager.getDoctrineAttitudeDisplayName(entry.getKey(), entry.getValue()));
        }
        return values.isEmpty() ? msg("common_none") : String.join("、", values);
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }
}
