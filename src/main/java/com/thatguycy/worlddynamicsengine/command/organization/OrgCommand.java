package com.thatguycy.worlddynamicsengine.command.organization;

import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.command.organization.PeaceTreatyCommand;
import com.thatguycy.worlddynamicsengine.command.organization.SovereignSubjectCommand;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.economy.GovernmentTreasury;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

/**
 * 处理国家集团创建、准入、表决和账户操作，委派和约与从仆国专用指令。
 */
public class OrgCommand implements CommandExecutor {

    private final NationManager nationManager;
    private final TownManager townManager;
    private final OrgManager orgManager;
    private final EconomyBridge economyBridge;
    private final SovereignSubjectCommand sovereignSubjectCommand;
    private final PeaceTreatyCommand peaceTreatyCommand;

    public OrgCommand(NationManager nationManager, TownManager townManager, OrgManager orgManager, EconomyBridge economyBridge) {
        this.nationManager = nationManager;
        this.townManager = townManager;
        this.orgManager = orgManager;
        this.economyBridge = economyBridge;
        this.sovereignSubjectCommand = new SovereignSubjectCommand(nationManager, orgManager, economyBridge);
        this.peaceTreatyCommand = new PeaceTreatyCommand(nationManager, orgManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            WorldDynamicsEngine.getInstance().getPlayerMenuManager().openOrganizationBrowser(player, null, 0);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(TextColors.RED + msg("org_usage_root"));
            return true;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "menu":
                if (sender instanceof Player player) {
                    if (args.length >= 3) WorldDynamicsEngine.getInstance().getPlayerMenuManager().openOrganization(player, args[2]);
                    else WorldDynamicsEngine.getInstance().getPlayerMenuManager().openOrganizationBrowser(player, null, 0);
                } else sender.sendMessage(TextColors.RED + msg("common_only_players"));
                return true;
            case "info":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_info"));
                    return true;
                }
                return displayOrgInfo(sender, args[2], orgManager.getOrganization(args[2]));
            case "create":
                if (!WorldDynamicsEngine.getInstance().orgEnabled) {
                    sender.sendMessage(TextColors.RED + msg("org_disabled"));
                    return true;
                }
                return handleCreateOrg(sender, args, WorldDynamicsEngine.getInstance());
            case "dissolve":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_dissolve"));
                    return true;
                }
                return handleDissolve(sender, orgManager.getOrganization(args[2]));
            case "applynation":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_applynation"));
                    return true;
                }
                return handleApplyNation(sender, orgManager.getOrganization(args[2]));
            case "votejoin":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_votejoin"));
                    return true;
                }
                return handleVoteJoin(sender, orgManager.getOrganization(args[2]), args);
            case "reviewjoin":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_reviewjoin"));
                    return true;
                }
                return handleReviewJoin(sender, orgManager.getOrganization(args[2]), args);
            case "deposit":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_deposit"));
                    return true;
                }
                return handleDeposit(sender, orgManager.getOrganization(args[2]), args);
            case "withdraw":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_withdraw"));
                    return true;
                }
                return handleWithdraw(sender, orgManager.getOrganization(args[2]), args);
            case "leave":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_leave"));
                    return true;
                }
                return handleLeave(sender, orgManager.getOrganization(args[2]));
            case "grantindependence":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_grantindependence"));
                    return true;
                }
                return handleGrantIndependence(sender, orgManager.getOrganization(args[2]), args);
            case "peacetreaty":
                if (args.length <= 3 && sender instanceof Player player) {
                    WorldDynamicsEngine.getInstance().getPlayerMenuManager()
                            .openPeaceTreaty(player, args.length == 3 ? args[2] : null);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_peacetreaty"));
                    return true;
                }
                return peaceTreatyCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "reviewtreaty":
                if (args.length <= 3 && sender instanceof Player player) {
                    WorldDynamicsEngine.getInstance().getPlayerMenuManager()
                            .openPeaceInbox(player, args.length == 3 ? args[2] : null);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_reviewtreaty"));
                    return true;
                }
                return peaceTreatyCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "promoteautonomy":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_promoteautonomy"));
                    return true;
                }
                return sovereignSubjectCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "demoteautonomy":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_demoteautonomy"));
                    return true;
                }
                return sovereignSubjectCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "setgarrison":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_setgarrison"));
                    return true;
                }
                return sovereignSubjectCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "mobilizewar":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_mobilizewar"));
                    return true;
                }
                return sovereignSubjectCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "replaceleader":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_replaceleader"));
                    return true;
                }
                return sovereignSubjectCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "demandtribute":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_demandtribute"));
                    return true;
                }
                return sovereignSubjectCommand.execute(subcommand, sender, orgManager.getOrganization(args[2]), args);
            case "declarewar":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_declarewar"));
                    return true;
                }
                return handleDeclareWar(sender, orgManager.getOrganization(args[2]), args);
            case "votewar":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_votewar"));
                    return true;
                }
                return handleVoteWar(sender, orgManager.getOrganization(args[2]), args);
            case "sanction":
                if (args.length < 3) {
                    sender.sendMessage(TextColors.RED + msg("org_usage_sanction"));
                    return true;
                }
                return handleTradeSanction(sender, orgManager.getOrganization(args[2]), args);
            default:
                sender.sendMessage(TextColors.RED + msg("org_usage_subcommands"));
                return true;
        }
    }

    private boolean displayOrgInfo(CommandSender sender, String orgName, WDEorg org) {
        if (org == null) {
            sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "orgnotfound"));
            return true;
        }

        sender.sendMessage(TextColors.GOLD + msg("org_info_header", orgName));
        sender.sendMessage(TextColors.YELLOW + msg("org_info_type") + TextColors.WHITE + org.getOrganizationType());
        sender.sendMessage(TextColors.YELLOW + msg("org_info_chairman_nation") + TextColors.WHITE + org.getChairmanNationName());
        sender.sendMessage(TextColors.YELLOW + msg("org_info_founder_nation") + TextColors.WHITE + org.getNationName());
        sender.sendMessage(TextColors.YELLOW + msg("org_info_leader") + TextColors.WHITE + getPlayerNameFromUUID(org.getLeaderUUID()));
        sender.sendMessage(TextColors.YELLOW + msg("org_info_nation_anchor") + TextColors.WHITE + org.getNationName());
        sender.sendMessage(TextColors.YELLOW + msg("org_info_town_anchor") + TextColors.WHITE + org.getTownName());
        if (orgManager.isIdeologicalAlliance(org)) {
            sender.sendMessage(TextColors.YELLOW + msg("org_info_alliance_ideology")
                    + TextColors.WHITE + orgManager.getIdeologicalAllianceIdeology(org));
        }
        if (org.isSovereignEmpire()) {
            double chairmanCapability = orgManager.getChairmanCapability(org, nationManager);
            double regularSubjectCapability = orgManager.getSubjectCapabilityTotal(org, nationManager, false);
            double garrisonSubjectCapability = orgManager.getSubjectCapabilityTotal(org, nationManager, true);
            sender.sendMessage(TextColors.YELLOW + msg("org_info_sovereign_capability_compare")
                    + TextColors.WHITE
                    + msg("org_info_sovereign_capability_compare_value",
                    formatCapability(chairmanCapability),
                    formatCapability(regularSubjectCapability),
                    formatCapability(garrisonSubjectCapability)));
        }

        List<String> memberNationLines = new ArrayList<>();
        for (String memberNation : org.getMemberNationNames()) {
            if (org.isSovereignEmpire() && !orgManager.isSovereignSubjectNation(org, memberNation)) continue;
            memberNationLines.add(formatMemberNationInfo(org, memberNation));
        }
        String memberLabelKey = "SOVEREIGN_EMPIRE".equalsIgnoreCase(org.getOrganizationType())
                ? "org_info_vassal_nations"
                : "org_info_member_nations";
        sender.sendMessage(TextColors.YELLOW + msg(memberLabelKey) + TextColors.WHITE + (memberNationLines.isEmpty() ? msg("common_none") : String.join(", ", memberNationLines)));

        if (!org.getPendingNationApplications().isEmpty()) {
            List<String> pendingInfo = new ArrayList<>();
            for (String applicant : org.getPendingNationApplications()) {
                if (org.isSovereignEmpire()) {
                    pendingInfo.add(applicant + " [" + msg("org_info_pending_chairman_review_status") + "]");
                } else {
                    Map<String, Boolean> votes = org.getPendingNationVotes().getOrDefault(applicant, java.util.Collections.emptyMap());
                    pendingInfo.add(applicant + " [" + summarizeWeightedVotes(org, votes) + "]");
                }
            }
            sender.sendMessage(TextColors.YELLOW + msg("org_info_pending_nation_applications") + TextColors.WHITE + String.join(", ", pendingInfo));
        }

        List<OrgManager.MilitaryWarProposalState> warProposals = orgManager.getActiveMilitaryWarProposals(org);
        if (!warProposals.isEmpty()) {
            List<String> warProposalLines = new ArrayList<>();
            for (OrgManager.MilitaryWarProposalState proposal : warProposals) {
                warProposalLines.add(msg("org_info_pending_war_entry",
                        proposal.getTargetNationName(),
                        proposal.getProposerNationName(),
                        summarizeWeightedVotes(org, proposal.getVotes())));
            }
            sender.sendMessage(TextColors.YELLOW + msg("org_info_pending_war_votes") + TextColors.WHITE + String.join(" | ", warProposalLines));
        }

        if (orgManager.isTradeAlliance(org)) {
            java.util.Set<String> sanctions = orgManager.getTradeSanctionTargets(org);
            if (!sanctions.isEmpty()) {
                sender.sendMessage(TextColors.YELLOW + msg("org_info_trade_sanctions") + TextColors.WHITE + String.join(", ", sanctions));
            }
        }

        List<OrgManager.MilitaryWarState> activeWars = orgManager.getActiveMilitaryWars(org);
        if (!activeWars.isEmpty()) {
            List<String> warLines = new ArrayList<>();
            for (OrgManager.MilitaryWarState state : activeWars) {
                warLines.add(state.getAttackerNationName() + " / " + state.getDefenderNationName()
                        + " [" + msg(state.getReason().messageKey()) + "] "
                        + formatWarScore(state.getAttackerScore()) + " / " + formatWarScore(state.getDefenderScore()));
            }
            sender.sendMessage(TextColors.YELLOW + msg("org_info_independence_wars") + TextColors.WHITE + String.join(" | ", warLines));
        }
        sender.sendMessage(TextColors.YELLOW + msg("org_info_balance") + TextColors.WHITE + formatLedgerAmount(org.getBalance()));
        sender.sendMessage(TextColors.YELLOW + msg("org_info_maintenance_fee") + TextColors.WHITE + formatOrganizationMaintenanceInfo());
        if (org.isSovereignEmpire()) {
            sender.sendMessage(TextColors.YELLOW + msg("org_info_sovereign_tribute") + TextColors.WHITE + formatSovereignTributeInfo());
        }
        sender.sendMessage(TextColors.YELLOW + msg("org_info_flags") + TextColors.WHITE + String.join(", ", org.getFlags()));
        return true;
    }

    private boolean handleCreateOrg(CommandSender sender, String[] args, WorldDynamicsEngine plugin) {
        Player player = requirePlayer(sender, "org_only_players_create");
        if (player == null) {
            return true;
        }

        if (!hasNationAuthority(player, nationManager)) {
            sender.sendMessage(TextColors.RED + plugin.getLocaleMessage(plugin.userLang, "notallowed"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("org_usage_create"));
            return true;
        }

        Resident resident;
        try {
            resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
        } catch (Exception e) {
            sender.sendMessage(TextColors.RED + msg("org_towny_resident_missing"));
            return true;
        }

        String orgName = args[2];
        String organizationType = normalizeOrganizationType(args[3]);
        List<String> validTypes = plugin.getValidOrganizationTypes();
        if (!validTypes.contains(organizationType)) {
            sender.sendMessage(TextColors.RED + msg("org_invalid_type", String.join(", ", validTypes)));
            return true;
        }

        String townName;
        String nationName;
        try {
            if (resident == null || !resident.hasTown() || !resident.getTown().hasNation()) {
                sender.sendMessage(TextColors.RED + msg("org_must_have_town_and_nation"));
                return true;
            }

            townName = resident.getTown().getName();
            nationName = resident.getTown().getNation().getName();
        } catch (Exception e) {
            sender.sendMessage(TextColors.RED + msg("org_town_or_nation_missing"));
            return true;
        }

        if ("MILITARY_ALLIANCE".equals(organizationType) && orgManager.isNationNeutral(nationName)) {
            sender.sendMessage(TextColors.RED + msg("org_neutral_military_forbidden", nationName));
            return true;
        }
        if ("IDEOLOGICAL_ALLIANCE".equals(organizationType) && orgManager.isNationIdeologyMissing(nationName)) {
            sender.sendMessage(TextColors.RED + msg("org_ideological_founder_requires_ideology", nationName));
            return true;
        }
        if (orgManager.isNationAtOrgLimit(nationName, plugin.nationOrgLimit)) {
            sender.sendMessage(TextColors.RED + msg("org_nation_limit_reached", plugin.nationOrgLimit));
            return true;
        }

        if (orgManager.getOrganization(orgName) != null) {
            sender.sendMessage(TextColors.RED + plugin.getLocaleMessage(plugin.userLang, "alreadyexists"));
            return true;
        }

        EconomyBridge.Charge cost = plugin.getOrgFormationCostCharge();
        Nation townyNation = resolveNation(nationName);
        if (townyNation == null) {
            sender.sendMessage(TextColors.RED + msg("org_nation_treasury_unavailable"));
            return true;
        }
        if (!withdrawGovernmentCharge(townyNation, cost, "WorldDynamicsEngine organization formation")) {
            sender.sendMessage(TextColors.RED + msg("org_not_enough_money_cost", formatGovernmentCharge(cost)));
            return true;
        }

        WDEtown town = townManager.getTown(townName);
        if (town == null) {
            sender.sendMessage(TextColors.RED + msg("org_town_data_not_ready"));
            return true;
        }

        WDEorg newOrg = new WDEorg(orgName, player.getUniqueId(), nationName, townName, organizationType);
        if ("IDEOLOGICAL_ALLIANCE".equals(organizationType)) {
            newOrg.setSetting("allianceIdeology", orgManager.getNationIdeology(nationName));
        }
        if (!orgManager.addOrganization(newOrg)) {
            sender.sendMessage(TextColors.RED + msg("org_ideological_missing_alliance_ideology"));
            return true;
        }
        sender.sendMessage(TextColors.GREEN + plugin.getLocaleMessage(plugin.userLang, "orgcreated"));
        sender.sendMessage(TextColors.GREEN + msg("org_chairman_initialized", nationName));
        return true;
    }

    private boolean handleApplyNation(CommandSender sender, WDEorg org) {
        Player player = requirePlayer(sender, "org_only_players_applynation");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        String applicantNation = getAuthorizedNationName(player);
        if (applicantNation == null) {
            sender.sendMessage(TextColors.RED + msg("org_need_nation_authority_apply"));
            return true;
        }
        if (orgManager.isMilitaryMembershipBlocked(org, applicantNation)) {
            sender.sendMessage(TextColors.RED + msg("org_neutral_military_forbidden", applicantNation));
            return true;
        }
        if (sendIdeologicalAllianceBlockMessage(sender, org, applicantNation)) {
            return true;
        }

        if (org.hasMemberNation(applicantNation)) {
            sender.sendMessage(TextColors.RED + msg("org_nation_already_member"));
            return true;
        }

        if (org.hasPendingApplication(applicantNation)) {
            sender.sendMessage(TextColors.RED + msg("org_nation_already_pending"));
            return true;
        }

        if (orgManager.isNationAtOrgLimit(applicantNation, WorldDynamicsEngine.getInstance().nationOrgLimit)) {
            sender.sendMessage(TextColors.RED + msg("org_nation_limit_reached", WorldDynamicsEngine.getInstance().nationOrgLimit));
            return true;
        }

        String conflictingNation = orgManager.findMilitaryJoinConflict(org, applicantNation, nationManager);
        if (conflictingNation != null) {
            sender.sendMessage(TextColors.RED + msg("org_military_join_conflict", conflictingNation));
            return true;
        }

        orgManager.startNationApplication(org, applicantNation);
        if (org.isSovereignEmpire()) {
            sender.sendMessage(TextColors.GREEN + msg("org_application_submitted_chairman_review"));
        } else {
            sender.sendMessage(TextColors.GREEN + msg("org_application_submitted_weighted_vote"));
        }
        return true;
    }

    private boolean handleVoteJoin(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_votejoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (org.isSovereignEmpire()) {
            sender.sendMessage(TextColors.RED + msg("org_sovereign_use_reviewjoin"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(TextColors.RED + msg("org_usage_votejoin"));
            return true;
        }

        String voterNation = getAuthorizedNationName(player);
        if (voterNation == null || !org.hasMemberNation(voterNation)) {
            sender.sendMessage(TextColors.RED + msg("org_must_represent_member_nation"));
            return true;
        }

        String applicantNation = args[3];
        if (orgManager.isMilitaryMembershipBlocked(org, applicantNation)) {
            orgManager.rejectNationApplication(org, applicantNation);
            sender.sendMessage(TextColors.RED + msg("org_neutral_military_forbidden", applicantNation));
            return true;
        }
        if (sendIdeologicalAllianceBlockMessage(sender, org, applicantNation)) {
            orgManager.rejectNationApplication(org, applicantNation);
            return true;
        }
        if (!org.hasPendingApplication(applicantNation)) {
            sender.sendMessage(TextColors.RED + msg("org_pending_application_not_found"));
            return true;
        }

        String conflictingNation = orgManager.findMilitaryJoinConflict(org, applicantNation, nationManager);
        if (conflictingNation != null) {
            orgManager.rejectNationApplication(org, applicantNation);
            sender.sendMessage(TextColors.RED + msg("org_military_join_conflict", conflictingNation));
            return true;
        }

        Boolean support = parseBooleanVote(args[4]);
        if (support == null) {
            sender.sendMessage(TextColors.RED + msg("common_vote_yes_no"));
            return true;
        }

        boolean admitted = orgManager.castNationJoinVote(org, applicantNation, voterNation, support, nationManager);
        if (admitted) {
            sender.sendMessage(TextColors.GREEN + msg("org_vote_passed_nation_admitted", applicantNation));
        } else {
            sender.sendMessage(TextColors.GREEN + msg("org_weighted_vote_recorded", voterNation));
        }
        return true;
    }

    private boolean handleReviewJoin(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(TextColors.RED + msg("org_usage_reviewjoin"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !actorNation.equalsIgnoreCase(org.getChairmanNationName())) {
            sender.sendMessage(TextColors.RED + msg("org_only_chairman_review"));
            return true;
        }

        String applicantNation = args[3];
        if (!org.hasPendingApplication(applicantNation)) {
            sender.sendMessage(TextColors.RED + msg("org_pending_application_not_found"));
            return true;
        }

        if ("accept".equalsIgnoreCase(args[4]) && sendIdeologicalAllianceBlockMessage(sender, org, applicantNation)) {
            orgManager.rejectNationApplication(org, applicantNation);
            return true;
        }
        String conflictingNation = orgManager.findMilitaryJoinConflict(org, applicantNation, nationManager);
        if ("accept".equalsIgnoreCase(args[4]) && conflictingNation != null) {
            orgManager.rejectNationApplication(org, applicantNation);
            sender.sendMessage(TextColors.RED + msg("org_military_join_conflict", conflictingNation));
            return true;
        }

        String action = args[4].toLowerCase(Locale.ROOT);
        if (org.isSovereignEmpire()) {
            if ("accept".equals(action)) {
                orgManager.addMemberNation(org, applicantNation);
                sender.sendMessage(TextColors.GREEN + msg("org_nation_admitted_by_decree", applicantNation));
            } else if ("reject".equals(action)) {
                orgManager.rejectNationApplication(org, applicantNation);
                sender.sendMessage(TextColors.GREEN + msg("org_application_rejected"));
            } else {
                sender.sendMessage(TextColors.RED + msg("org_action_accept_or_reject"));
            }
            return true;
        }

        sender.sendMessage(TextColors.RED + msg("org_use_votejoin_instead"));
        return true;
    }

    private boolean handleDissolve(CommandSender sender, WDEorg org) {
        Player player = requirePlayer(sender, "org_only_players_dissolve");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        boolean chairmanAuthority = actorNation != null && actorNation.equalsIgnoreCase(org.getChairmanNationName());
        if (!org.getLeaderUUID().equals(player.getUniqueId()) && !chairmanAuthority) {
            sender.sendMessage(TextColors.RED + msg("org_not_leader_or_chairman"));
            return true;
        }

        orgManager.removeOrganization(org);
        orgManager.saveOrganizations();
        sender.sendMessage(TextColors.GREEN + msg("orgdeleted"));
        return true;
    }

    private boolean handleLeave(CommandSender sender, WDEorg org) {
        Player player = requirePlayer(sender, "org_only_players_leave");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !org.hasMemberNation(actorNation)) {
            sender.sendMessage(TextColors.RED + msg("org_only_nations_can_leave"));
            return true;
        }

        return handleNationLeave(sender, org, actorNation);
    }

    private boolean handleNationLeave(CommandSender sender, WDEorg org, String actorNation) {
        if (org.getMemberNationNames().size() <= 1) {
            sender.sendMessage(TextColors.RED + msg("org_final_member_cannot_leave"));
            return true;
        }

        if (org.isSovereignEmpire()) {
            if (actorNation.equalsIgnoreCase(org.getChairmanNationName())) {
                sender.sendMessage(TextColors.RED + msg("org_sovereign_chairman_cannot_leave"));
                return true;
            }

            if (orgManager.hasActiveIndependenceWar(org, actorNation)) {
                sender.sendMessage(TextColors.RED + msg("org_independence_war_already_active", actorNation));
                return true;
            }

            WorldDynamicsEngine.getInstance().getPlayerMenuManager().openWarMenu((Player) sender);
            return true;
        }

        orgManager.removeMemberNationAndAdjust(org, actorNation);
        sender.sendMessage(TextColors.GREEN + msg("org_nation_removed", actorNation));
        return true;
    }

    private boolean handleWithdraw(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_withdraw");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        boolean chairmanAuthority = actorNation != null && actorNation.equalsIgnoreCase(org.getChairmanNationName());
        if (!org.getLeaderUUID().equals(player.getUniqueId()) && !chairmanAuthority) {
            sender.sendMessage(TextColors.RED + msg("org_not_leader_or_chairman"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("org_specify_withdraw_amount"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextColors.RED + msg("org_invalid_withdraw_amount"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(TextColors.RED + msg("org_withdraw_amount_positive"));
            return true;
        }

        if (amount > org.getBalance()) {
            sender.sendMessage(TextColors.RED + msg("org_insufficient_funds"));
            return true;
        }

        if (economyBridge == null || !economyBridge.deposit(player, BigDecimal.valueOf(amount)).success()) {
            sender.sendMessage(TextColors.RED + msg("org_withdraw_error"));
            return true;
        }

        orgManager.setBalance(org, org.getBalance() - amount);
        sender.sendMessage(TextColors.GREEN + msg("org_withdraw_success"));
        return true;
    }

    private boolean handleDeposit(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_deposit");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("org_specify_deposit_amount"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextColors.RED + msg("org_invalid_deposit_amount"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(TextColors.RED + msg("org_deposit_amount_positive"));
            return true;
        }

        if (economyBridge == null || !economyBridge.withdraw(player, new EconomyBridge.Charge(BigDecimal.valueOf(amount), WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency())).success()) {
            sender.sendMessage(TextColors.RED + msg("org_not_enough_funds"));
            return true;
        }

        orgManager.setBalance(org, org.getBalance() + amount);
        sender.sendMessage(TextColors.GREEN + msg("org_deposit_success"));
        return true;
    }

    private String getPlayerNameFromUUID(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        return TextColors.GRAY + msg("org_offline_user");
    }

    private Player requirePlayer(CommandSender sender, String messageKey) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextColors.RED + msg(messageKey));
            return null;
        }
        return (Player) sender;
    }

    private boolean requireOrganization(CommandSender sender, WDEorg org) {
        if (org == null) {
            sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "orgnotfound"));
            return false;
        }
        return true;
    }

    private boolean hasNationAuthority(CommandSender sender, NationManager nationManager) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getName());
            return NationAuthority.authorizedNationName(resident, player.getName(), nationManager) != null;
        } catch (Exception e) {
            return false;
        }
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
        if (nationName == null || nationName.isBlank()) {
            return null;
        }

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

    private String formatGovernmentCharge(EconomyBridge.Charge charge) {
        return GovernmentTreasury.format(charge, economyBridge,
                WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency());
    }

    private String normalizeOrganizationType(String rawValue) {
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "贸易联盟" -> "TRADE_ALLIANCE";
            case "军事同盟" -> "MILITARY_ALLIANCE";
            case "主权帝国" -> "SOVEREIGN_EMPIRE";
            case "意识形态联盟" -> "IDEOLOGICAL_ALLIANCE";
            default -> normalized;
        };
    }

    private boolean sendIdeologicalAllianceBlockMessage(CommandSender sender, WDEorg org, String nationName) {
        if (!orgManager.isIdeologicalAlliance(org)) {
            return false;
        }
        if (!orgManager.hasIdeologicalAllianceIdeology(org)) {
            sender.sendMessage(TextColors.RED + msg("org_ideological_missing_alliance_ideology"));
            return true;
        }
        if (orgManager.isNationIdeologyMissing(nationName)) {
            sender.sendMessage(TextColors.RED + msg("org_ideological_nation_requires_ideology", nationName));
            return true;
        }
        if (orgManager.isIdeologicalMembershipBlocked(org, nationName)) {
            sender.sendMessage(TextColors.RED + msg("org_ideological_membership_requires_same_ideology",
                    nationName, orgManager.getIdeologicalAllianceIdeology(org)));
            return true;
        }
        return false;
    }

    private Boolean parseBooleanVote(String rawValue) {
        if ("yes".equalsIgnoreCase(rawValue) || "accept".equalsIgnoreCase(rawValue) || "同意".equals(rawValue)) {
            return true;
        }
        if ("no".equalsIgnoreCase(rawValue) || "reject".equalsIgnoreCase(rawValue) || "反对".equals(rawValue)) {
            return false;
        }
        return null;
    }

    private String summarizeWeightedVotes(WDEorg org, Map<String, Boolean> votes) {
        double yesWeight = 0.0D;
        double noWeight = 0.0D;
        for (String memberNation : org.getMemberNationNames()) {
            Boolean vote = null;
            for (Map.Entry<String, Boolean> entry : votes.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(memberNation)) {
                    vote = entry.getValue();
                    break;
                }
            }

            if (vote == null) {
                continue;
            }

            double weight = orgManager.getNationWeight(org, memberNation, nationManager);
            if (vote) {
                yesWeight += weight;
            } else {
                noWeight += weight;
            }
        }
        return msg("common_vote_summary", String.format(Locale.US, "%.0f", yesWeight), String.format(Locale.US, "%.0f", noWeight));
    }

    private String formatMemberNationInfo(WDEorg org, String memberNation) {
        if (org.isSovereignEmpire()) {
            if (memberNation != null && memberNation.equalsIgnoreCase(org.getChairmanNationName())) {
                return memberNation + " (" + msg("org_info_authority_chairman") + ")";
            }

            return memberNation + " ("
                    + msg("org_subject_tier_" + orgManager.getSubjectAutonomyTier(org, memberNation).name().toLowerCase(Locale.ROOT))
                    + " / "
                    + msg("org_info_subject_suppression_value", orgManager.getSubjectSuppression(org, memberNation))
                    + " / "
                    + msg(orgManager.isGarrisonSubject(org, memberNation) ? "org_subject_group_garrison" : "org_subject_group_regular")
                    + " / "
                    + msg(orgManager.isSubjectWarParticipationRequired(org, memberNation)
                    ? "org_subject_war_status_required"
                    : "org_subject_war_status_optional")
                    + ")";
        }

        double weight = orgManager.getNationWeight(org, memberNation, nationManager);
        return memberNation + " (" + String.format(Locale.US, "%.0f", weight) + ")";
    }

    private String formatLedgerAmount(double amount) {
        if (economyBridge == null) {
            return String.format(Locale.US, "%.2f", amount);
        }
        return economyBridge.format(BigDecimal.valueOf(amount), WorldDynamicsEngine.getInstance().getDefaultEconomyCurrency());
    }

    private String formatOrganizationMaintenanceInfo() {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        EconomyBridge.Charge maintenanceCharge = plugin.getOrgMaintenanceCostCharge();
        long intervalHours = plugin.getOrganizationMaintenanceIntervalHours();
        if (maintenanceCharge == null || maintenanceCharge.amount().compareTo(BigDecimal.ZERO) <= 0 || intervalHours <= 0L) {
            return msg("common_disabled");
        }

        String amountText = economyBridge == null
                ? maintenanceCharge.amount().toPlainString() + " " + maintenanceCharge.currency()
                : economyBridge.format(maintenanceCharge.amount(), maintenanceCharge.currency());
        return msg("org_info_maintenance_fee_value", amountText, intervalHours);
    }

    private String formatSovereignTributeInfo() {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        boolean enabled = plugin.getOrganizationConfig().getBoolean("sovereignEmpire.tribute.enabled", true);
        long intervalHours = Math.max(0L, plugin.getOrganizationConfig().getLong("sovereignEmpire.tribute.intervalHours", 24L));
        EconomyBridge.Charge tributeCharge = orgManager.getSovereignTributeCharge();
        if (!enabled || tributeCharge == null || tributeCharge.amount().compareTo(BigDecimal.ZERO) <= 0 || intervalHours <= 0L) {
            return msg("common_disabled");
        }

        String amountText = economyBridge == null
                ? tributeCharge.amount().toPlainString() + " " + tributeCharge.currency()
                : economyBridge.format(tributeCharge.amount(), tributeCharge.currency());
        return msg("org_info_sovereign_tribute_value", amountText, intervalHours);
    }

    private String formatCapability(double amount) {
        return String.format(Locale.US, "%.0f", amount);
    }

    private String formatWarScore(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private boolean handleGrantIndependence(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_reviewjoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (!org.isSovereignEmpire()) {
            sender.sendMessage(TextColors.RED + msg("org_grant_independence_only_sovereign"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("org_usage_grantindependence"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !actorNation.equalsIgnoreCase(org.getChairmanNationName())) {
            sender.sendMessage(TextColors.RED + msg("org_only_chairman_grant_independence"));
            return true;
        }

        String rebelNation = args[3];
        OrgManager.IndependenceWarResolutionResult result = orgManager.surrenderIndependenceWar(org, rebelNation);
        if (result == null) {
            sender.sendMessage(TextColors.RED + msg("org_independence_war_not_found", rebelNation));
            return true;
        }

        sender.sendMessage(TextColors.GREEN + msg("org_grant_independence_success", rebelNation, org.getOrganizationName()));
        return true;
    }

    private boolean handleDeclareWar(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_votejoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (!orgManager.isMilitaryAlliance(org)) {
            sender.sendMessage(TextColors.RED + msg("org_war_vote_only_military"));
            return true;
        }

        if (args.length != 5) {
            sender.sendMessage(TextColors.RED + msg("war_reason_required"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !org.hasMemberNation(actorNation)) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        String targetNation = args[3];
        var reason = com.thatguycy.worlddynamicsengine.model.war.WarReason.parse(args[4]);
        String reasonError = orgManager.warReasonFailure(reason, actorNation, targetNation);
        if (reasonError != null) {
            sender.sendMessage(TextColors.RED + msg(reasonError));
            return true;
        }
        if (org.hasMemberNation(targetNation)) {
            sender.sendMessage(TextColors.RED + msg("org_war_vote_target_member"));
            return true;
        }

        if (nationManager.getNation(targetNation) == null) {
            sender.sendMessage(TextColors.RED + msg("nation_not_found_with_name", targetNation));
            return true;
        }
        if (orgManager.isNationNeutral(actorNation) || nationManager.isNationPeaceProtected(targetNation)) {
            sender.sendMessage(TextColors.RED + msg("org_neutral_war_forbidden"));
            return true;
        }

        OrgManager.MilitaryWarVoteResult result = orgManager.startMilitaryWarVote(org, actorNation, targetNation, nationManager, reason);
        return handleMilitaryWarVoteResult(sender, org, targetNation, result, true);
    }

    private boolean handleVoteWar(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_votejoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (!orgManager.isMilitaryAlliance(org)) {
            sender.sendMessage(TextColors.RED + msg("org_war_vote_only_military"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(TextColors.RED + msg("org_usage_votewar"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !org.hasMemberNation(actorNation)) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        Boolean vote = parseBooleanVote(args[4]);
        if (vote == null) {
            sender.sendMessage(TextColors.RED + msg("common_vote_yes_no"));
            return true;
        }

        OrgManager.MilitaryWarVoteResult result = orgManager.castMilitaryWarVote(org, args[3], actorNation, vote, nationManager);
        return handleMilitaryWarVoteResult(sender, org, args[3], result, false);
    }

    private boolean handleMilitaryWarVoteResult(CommandSender sender, WDEorg org, String targetNation, OrgManager.MilitaryWarVoteResult result, boolean startedByProposal) {
        if (result == null) {
            sender.sendMessage(TextColors.RED + msg("org_war_vote_not_found", targetNation));
            return true;
        }

        switch (result.getStatus()) {
            case INVALID -> sender.sendMessage(TextColors.RED + (result.getFailureMessageKey() != null
                    ? msg(result.getFailureMessageKey()) : org.hasMemberNation(targetNation)
                    ? msg("war_target_same_military_alliance", targetNation)
                    : msg("org_war_vote_invalid")));
            case ALREADY_PENDING -> sender.sendMessage(TextColors.RED + msg("org_war_vote_already_pending", targetNation));
            case NOT_FOUND -> sender.sendMessage(TextColors.RED + msg("org_war_vote_not_found", targetNation));
            case PEACE_PROTECTED -> sender.sendMessage(TextColors.RED + (nationManager.isNationPeaceProtected(targetNation)
                    ? msg("diplomacy_target_nation_peace_protected", targetNation)
                    : msg("org_neutral_war_forbidden")));
            case ACTIVE_WAR -> sender.sendMessage(TextColors.RED + msg("org_war_vote_already_active", targetNation));
            case TRUCE -> sender.sendMessage(TextColors.RED + msg("org_war_vote_target_truce", targetNation));
            case PENDING -> sender.sendMessage(TextColors.GREEN + (startedByProposal
                    ? msg("org_war_vote_started", targetNation, summarizeWeightedVotes(org, result.getProposal().getVotes()))
                    : msg("org_war_vote_recorded", targetNation, summarizeWeightedVotes(org, result.getProposal().getVotes()))));
            case APPROVED -> {
                sender.sendMessage(TextColors.GREEN + msg("org_war_vote_approved", targetNation));
                if (result.getWarResult() != null && !result.getWarResult().getRemovedFromAttackerAlliances().isEmpty()) {
                    sender.sendMessage(TextColors.YELLOW + msg("diplomacy_military_overlap_removed", String.join(", ", result.getWarResult().getRemovedFromAttackerAlliances())));
                }
            }
            case REJECTED -> sender.sendMessage(TextColors.YELLOW + msg("org_war_vote_rejected", targetNation));
        }
        return true;
    }

    private boolean handleTradeSanction(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender, "org_only_players_votejoin");
        if (player == null || !requireOrganization(sender, org)) {
            return true;
        }

        if (!orgManager.isTradeAlliance(org)) {
            sender.sendMessage(TextColors.RED + msg("org_sanction_only_trade"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(TextColors.RED + msg("org_usage_sanction"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null || !org.hasMemberNation(actorNation)) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        String targetNation = args[3];
        if (nationManager.getNation(targetNation) == null) {
            sender.sendMessage(TextColors.RED + msg("nation_not_found_with_name", targetNation));
            return true;
        }

        boolean enabled;
        if ("enabled".equalsIgnoreCase(args[4])) {
            enabled = true;
        } else if ("disabled".equalsIgnoreCase(args[4])) {
            enabled = false;
        } else {
            sender.sendMessage(TextColors.RED + msg("org_usage_sanction"));
            return true;
        }

        orgManager.setTradeSanction(org, targetNation, enabled);
        sender.sendMessage(TextColors.GREEN + msg("org_trade_sanction_set",
                targetNation,
                enabled ? msg("common_enabled") : msg("common_disabled")));
        sender.sendMessage(TextColors.YELLOW + msg("org_trade_sanction_placeholder"));
        return true;
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }
}
