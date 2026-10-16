package com.thatguycy.worlddynamicsengine.command.organization;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理和平条约的提出与受理，解析条款和方向，并调用集团管理器执行战争规则校验。
 */
public class PeaceTreatyCommand {
    private final NationManager nationManager;
    private final OrgManager orgManager;

    public PeaceTreatyCommand(NationManager nationManager, OrgManager orgManager) {
        this.nationManager = nationManager;
        this.orgManager = orgManager;
    }

    public boolean execute(String action, CommandSender sender, WDEorg org, String[] args) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "peacetreaty" -> handlePropose(sender, org, args);
            case "reviewtreaty" -> handleReview(sender, org, args);
            default -> false;
        };
    }

    private boolean handlePropose(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || org == null && (args.length < 3 || !"war".equals(args[2])) && !requireOrganization(sender, org)) {
            return true;
        }
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_peacetreaty"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null) {
            sender.sendMessage(ChatColor.RED + msg("common_no_authority"));
            return true;
        }

        String targetNation = args[3];
        OrgManager.PeaceTreatyProposalState.ProposalType proposalType = OrgManager.PeaceTreatyProposalState.ProposalType.fromString(args[4]);
        if (proposalType == null) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_peacetreaty"));
            return true;
        }

        List<OrgManager.PeaceTreatyClauseState> clauses = parseClauses(String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)));
        if (clauses.isEmpty()) {
            sender.sendMessage(ChatColor.RED + msg("org_peace_treaty_invalid_clause"));
            return true;
        }

        OrgManager.PeaceTreatyProposalResult result = orgManager.proposePeaceTreaty(org, actorNation, targetNation, proposalType, clauses, nationManager);
        if (!result.isSuccess()) {
            sender.sendMessage(ChatColor.RED + msg(result.getMessageKey()));
            return true;
        }

        String scoreDifferenceText = String.format(Locale.US, "%.2f", result.getScoreDifference());
        if (result.getStatus() == OrgManager.PeaceTreatyProposalResult.Status.FORCED_ACCEPTED) {
            sender.sendMessage(ChatColor.GREEN + msg("org_peace_treaty_forced_accepted",
                    targetNation,
                    result.getProposal().getTotalPoints(),
                    scoreDifferenceText));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + msg("org_peace_treaty_proposed",
                targetNation,
                proposalType == OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND ? msg("org_peace_treaty_type_demand") : msg("org_peace_treaty_type_surrender"),
                result.getProposal().getTotalPoints(),
                scoreDifferenceText));
        return true;
    }

    private boolean handleReview(CommandSender sender, WDEorg org, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || org == null && (args.length < 3 || !"war".equals(args[2])) && !requireOrganization(sender, org)) {
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_reviewtreaty"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null) {
            sender.sendMessage(ChatColor.RED + msg("common_no_authority"));
            return true;
        }

        String targetNation = args[3];
        Boolean accept = parseReviewDecision(args[4]);
        if (accept == null) {
            sender.sendMessage(ChatColor.RED + msg("org_usage_reviewtreaty"));
            return true;
        }

        OrgManager.PeaceTreatyReviewResult result = orgManager.reviewPeaceTreaty(org, actorNation, targetNation, accept, nationManager);
        if (result.getStatus() == OrgManager.PeaceTreatyReviewResult.Status.INVALID) {
            sender.sendMessage(ChatColor.RED + msg(result.getMessageKey()));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + msg(result.getStatus() == OrgManager.PeaceTreatyReviewResult.Status.ACCEPTED
                ? "org_peace_treaty_accepted"
                : "org_peace_treaty_rejected", targetNation));
        return true;
    }

    private List<OrgManager.PeaceTreatyClauseState> parseClauses(String rawText) {
        List<OrgManager.PeaceTreatyClauseState> clauses = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return clauses;
        }

        String[] clauseTexts = rawText.split(";");
        for (String clauseText : clauseTexts) {
            String[] parts = clauseText.trim().split(":");
            OrgManager.PeaceTreatyClauseState.ClauseType type =
                    OrgManager.PeaceTreatyClauseState.ClauseType.fromString(parts[0]);
            if (type == null) {
                continue;
            }

            Map<String, Object> parameters = new LinkedHashMap<>();
            switch (type) {
                case REPARATIONS -> {
                    if (parts.length >= 2) {
                        parameters.put("percent", parts[1]);
                    }
                }
                case TRANSFER_TOWN -> {
                    if (parts.length >= 3) {
                        parameters.put("town", parts[1]);
                        parameters.put("destinationNation", parts[2]);
                    }
                }
                case LIBERATE_SUBJECT -> {
                    if (parts.length >= 2) {
                        parameters.put("subjectNation", parts[1]);
                    }
                }
                case FORCE_CONVERSION, DISSOLVE_ALLIANCE, EXPEL_FROM_ALLIANCE, UNCONDITIONAL_PASSAGE, HUMILIATE, LOOT_CAPITAL, TRIBUTE, ALLOW_INDEPENDENCE -> {
                }
            }

            clauses.add(new OrgManager.PeaceTreatyClauseState(type, parameters));
        }

        return clauses;
    }

    private Boolean parseReviewDecision(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (rawValue.toLowerCase(Locale.ROOT)) {
            case "accept", "yes", "同意", "接受" -> true;
            case "reject", "no", "拒绝" -> false;
            default -> null;
        };
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(ChatColor.RED + msg("common_only_players"));
        return null;
    }

    private boolean requireOrganization(CommandSender sender, WDEorg org) {
        if (org != null) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + msg("org_not_found"));
        return false;
    }

    private String getAuthorizedNationName(Player player) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                return null;
            }
            Nation nation = resident.getTown().getNationOrNull();
            return nation == null ? null : nation.getName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }
}
