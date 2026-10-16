package com.thatguycy.worlddynamicsengine.command.organization;

import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.economy.GovernmentTreasury;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.organization.SovereignSubjectState;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理主权帝国中面向从仆国的自治、卫戍、贡金、调遣及独立操作。
 */
public class SovereignSubjectCommand {
    private final NationManager nationManager;
    private final OrgManager orgManager;
    private final EconomyBridge economyBridge;

    public SovereignSubjectCommand(NationManager nationManager, OrgManager orgManager, EconomyBridge economyBridge) {
        this.nationManager = nationManager;
        this.orgManager = orgManager;
        this.economyBridge = economyBridge;
    }

    public boolean execute(String action, CommandSender sender, WDEorg org, String[] args) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "promoteautonomy" -> handlePromoteAutonomy(sender, org);
            case "demoteautonomy" -> handleDemoteAutonomy(sender, org, args);
            case "setgarrison" -> handleSetGarrison(sender, org, args);
            case "mobilizewar" -> handleMobilizeWar(sender, org, args);
            case "replaceleader" -> handleReplaceLeader(sender, org, args);
            case "demandtribute" -> handleDemandTribute(sender, org, args);
            default -> false;
        };
    }

    private boolean handlePromoteAutonomy(CommandSender sender, WDEorg org) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }
        if (!requireSovereignEmpire(sender, org)) {
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !orgManager.isSovereignSubjectNation(org, actorNation)) {
            sender.sendMessage(ChatColor.RED + msg("org_only_subject_can_promote_autonomy"));
            return true;
        }
        if (isIndependenceWarActive(sender, org, actorNation)) {
            return true;
        }

        SovereignSubjectState.AutonomyTier currentTier = orgManager.getSubjectAutonomyTier(org, actorNation);
        boolean peacefulIndependence = currentTier == SovereignSubjectState.AutonomyTier.DOMINION;
        if (peacefulIndependence && orgManager.isNationInActiveWar(actorNation)) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_peaceful_independence_blocked_by_war"));
            return true;
        }

        int suppression = orgManager.getSubjectSuppression(org, actorNation);
        if (suppression > 0) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_requires_zero_suppression", suppression));
            return true;
        }

        EconomyBridge.Charge charge = orgManager.getSovereignAutonomyUpgradeCharge();
        Nation townyNation = resolveNation(actorNation);
        if (townyNation == null) {
            sender.sendMessage(ChatColor.RED + msg("org_nation_treasury_unavailable"));
            return true;
        }
        if (!withdrawGovernmentCharge(townyNation, charge, "WorldDynamicsEngine sovereign subject autonomy upgrade")) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_upgrade_cost_insufficient", formatGovernmentCharge(charge)));
            return true;
        }

        if (peacefulIndependence) {
            if (!orgManager.peacefullyReleaseDominion(org, actorNation)) {
                sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_upgrade_failed"));
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + msg("org_subject_peaceful_independence",
                    actorNation, org.getOrganizationName(), formatGovernmentCharge(charge)));
            return true;
        }

        SovereignSubjectState.AutonomyTier upgradedTier = orgManager.promoteSubjectAutonomy(org, actorNation);
        if (upgradedTier == null) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_upgrade_failed"));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + msg("org_subject_autonomy_upgraded",
                actorNation,
                tierName(upgradedTier),
                formatGovernmentCharge(charge)));
        return true;
    }

    private boolean handleDemoteAutonomy(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }
        if (!requireSovereignEmpire(sender, org)) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_demoteautonomy"));
            return true;
        }

        if (!isChairman(player, org)) {
            sender.sendMessage(ChatColor.RED + msg("org_only_chairman_demote_autonomy"));
            return true;
        }

        String targetNation = args[3];
        if (!requireSubjectNation(sender, org, targetNation) || isIndependenceWarActive(sender, org, targetNation)) {
            return true;
        }

        SovereignSubjectState.AutonomyTier currentTier = orgManager.getSubjectAutonomyTier(org, targetNation);
        if (!currentTier.canDemote()) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_already_lowest", targetNation));
            return true;
        }

        int suppression = orgManager.getSubjectSuppression(org, targetNation);
        if (suppression < 100) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_requires_full_suppression", targetNation, suppression));
            return true;
        }

        SovereignSubjectState.AutonomyTier demotedTier = orgManager.demoteSubjectAutonomy(org, targetNation);
        if (demotedTier == null) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_autonomy_demote_failed", targetNation));
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + msg("org_subject_autonomy_demoted", targetNation, tierName(demotedTier)));
        return true;
    }

    private boolean handleSetGarrison(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }
        if (!requireSovereignEmpire(sender, org)) {
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_setgarrison"));
            return true;
        }
        if (!isChairman(player, org)) {
            sender.sendMessage(ChatColor.RED + msg("org_only_chairman_set_garrison"));
            return true;
        }

        String targetNation = args[3];
        if (!requireSubjectNation(sender, org, targetNation)) {
            return true;
        }

        Boolean enabled = parseEnabledState(args[4]);
        if (enabled == null) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_setgarrison"));
            return true;
        }

        if (!orgManager.setSubjectGarrisonState(org, targetNation, enabled)) {
            sender.sendMessage(ChatColor.YELLOW + msg("org_subject_garrison_unchanged",
                    targetNation,
                    enabled ? msg("common_enabled") : msg("common_disabled")));
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + msg("org_subject_garrison_updated",
                targetNation,
                enabled ? msg("common_enabled") : msg("common_disabled")));
        return true;
    }

    private boolean handleMobilizeWar(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }
        if (!requireSovereignEmpire(sender, org)) {
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_mobilizewar"));
            return true;
        }
        if (!isChairman(player, org)) {
            sender.sendMessage(ChatColor.RED + msg("org_only_chairman_mobilize_subject_war"));
            return true;
        }

        String targetNation = args[3];
        if (!requireSubjectNation(sender, org, targetNation) || isIndependenceWarActive(sender, org, targetNation)) {
            return true;
        }

        Boolean enabled = parseEnabledState(args[4]);
        if (enabled == null) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_mobilizewar"));
            return true;
        }
        if (enabled && orgManager.isNationNeutral(targetNation)) {
            sender.sendMessage(ChatColor.RED + msg("org_neutral_war_forbidden"));
            return true;
        }

        if (orgManager.isGarrisonSubject(org, targetNation) && !enabled) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_war_duty_garrison_forced", targetNation));
            return true;
        }

        if (!orgManager.setSubjectWarParticipationRequired(org, targetNation, enabled)) {
            sender.sendMessage(ChatColor.YELLOW + msg("org_subject_war_duty_unchanged",
                    targetNation,
                    enabled ? msg("common_enabled") : msg("common_disabled")));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + msg("org_subject_war_duty_updated",
                targetNation,
                enabled ? msg("common_enabled") : msg("common_disabled")));
        return true;
    }

    private boolean handleReplaceLeader(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }
        if (!requireSovereignEmpire(sender, org)) {
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_replaceleader"));
            return true;
        }
        if (!isChairman(player, org)) {
            sender.sendMessage(ChatColor.RED + msg("org_only_chairman_replace_subject_leader"));
            return true;
        }

        String targetNation = args[3];
        String newLeaderName = args[4];
        if (!requireSubjectNation(sender, org, targetNation) || isIndependenceWarActive(sender, org, targetNation)) {
            return true;
        }

        WDEnation subjectNation = nationManager.getNation(targetNation);
        if (subjectNation == null) {
            sender.sendMessage(ChatColor.RED + msg("nation_not_found_with_name", targetNation));
            return true;
        }
        if (!nationManager.isNationResidentCandidate(subjectNation, newLeaderName)) {
            sender.sendMessage(ChatColor.RED + msg("nation_candidate_must_be_nation_resident"));
            return true;
        }
        if (!nationManager.forceNationLeader(subjectNation, newLeaderName)) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_replace_leader_failed", targetNation, newLeaderName));
            return true;
        }

        int suppression = orgManager.reduceSubjectSuppression(
                org, targetNation, orgManager.getForcedLeaderChangeSuppressionReduction());
        sender.sendMessage(ChatColor.GREEN + msg("org_subject_replace_leader_success",
                targetNation, newLeaderName, Math.max(0, suppression)));
        return true;
    }

    private boolean handleDemandTribute(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }
        if (!requireSovereignEmpire(sender, org)) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_demandtribute"));
            return true;
        }
        if (!isChairman(player, org)) {
            sender.sendMessage(ChatColor.RED + msg("org_only_chairman_demand_tribute"));
            return true;
        }

        String targetNation = args[3];
        if (!requireSubjectNation(sender, org, targetNation) || isIndependenceWarActive(sender, org, targetNation)) {
            return true;
        }

        EconomyBridge.Charge charge = orgManager.getForcedTributeCharge(org, targetNation);
        if (charge.amount().compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_forced_tribute_unconfigured", targetNation));
            return true;
        }

        Nation townyNation = resolveNation(targetNation);
        if (townyNation == null) {
            sender.sendMessage(ChatColor.RED + msg("org_nation_treasury_unavailable"));
            return true;
        }
        if (!withdrawGovernmentCharge(townyNation, charge, "WorldDynamicsEngine sovereign forced tribute")) {
            sender.sendMessage(ChatColor.RED + msg("org_subject_forced_tribute_insufficient",
                    targetNation, formatGovernmentCharge(charge)));
            return true;
        }

        BigDecimal defaultCurrencyAmount = resolveGovernmentChargeAmount(charge);
        org.setBalance(BigDecimal.valueOf(org.getBalance()).add(defaultCurrencyAmount).doubleValue());
        orgManager.saveOrganizations();
        int suppression = orgManager.reduceSubjectSuppression(
                org, targetNation, orgManager.getForcedTributeSuppressionReduction());
        sender.sendMessage(ChatColor.GREEN + msg("org_subject_forced_tribute_success",
                targetNation, formatGovernmentCharge(charge), Math.max(0, suppression)));
        return true;
    }

    private boolean requireSovereignEmpire(CommandSender sender, WDEorg org) {
        if (org.isSovereignEmpire()) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + msg("org_only_sovereign_subject_control"));
        return false;
    }

    private boolean requireSubjectNation(CommandSender sender, WDEorg org, String nationName) {
        if (orgManager.isSovereignSubjectNation(org, nationName)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + msg("org_subject_not_found", nationName));
        return false;
    }

    private boolean isIndependenceWarActive(CommandSender sender, WDEorg org, String nationName) {
        if (!orgManager.hasActiveIndependenceWar(org, nationName)) {
            return false;
        }
        sender.sendMessage(ChatColor.RED + msg("org_subject_control_blocked_by_war", nationName));
        return true;
    }

    private boolean isChairman(Player player, WDEorg org) {
        String actorNation = getAuthorizedNationName(player);
        return actorNation != null && actorNation.equalsIgnoreCase(org.getChairmanNationName());
    }

    private String tierName(SovereignSubjectState.AutonomyTier tier) {
        return msg("org_subject_tier_" + tier.name().toLowerCase(Locale.ROOT));
    }

    private Player requirePlayer(CommandSender sender, String messageKey) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ChatColor.RED + msg(messageKey));
        return null;
    }

    private boolean requireOrganization(CommandSender sender, WDEorg org) {
        if (org != null) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + WorldDynamicsEngine.getInstance()
                .getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "orgnotfound"));
        return false;
    }

    private String getAuthorizedNationName(Player player) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            return NationAuthority.authorizedNationName(resident, player.getName(), nationManager);
        } catch (Exception ignored) {
        }
        return null;
    }

    private Nation resolveNation(String nationName) {
        try {
            return TownyUniverse.getInstance().getNation(nationName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean withdrawGovernmentCharge(Government government, EconomyBridge.Charge charge, String reason) {
        return GovernmentTreasury.withdraw(government, charge, economyBridge,
                WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency(), reason);
    }

    private BigDecimal resolveGovernmentChargeAmount(EconomyBridge.Charge charge) {
        return GovernmentTreasury.amountInDefaultCurrency(charge, economyBridge,
                WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency());
    }

    private String formatGovernmentCharge(EconomyBridge.Charge charge) {
        return GovernmentTreasury.format(charge, economyBridge,
                WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency());
    }

    private Boolean parseEnabledState(String value) {
        if ("enabled".equalsIgnoreCase(value) || "enable".equalsIgnoreCase(value) || "启用".equals(value)) {
            return true;
        }
        if ("disabled".equalsIgnoreCase(value) || "disable".equalsIgnoreCase(value) || "禁用".equals(value)) {
            return false;
        }
        return null;
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }
}
