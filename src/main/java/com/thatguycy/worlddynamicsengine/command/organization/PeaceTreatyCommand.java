package com.thatguycy.worlddynamicsengine.command.organization;

import com.thatguycy.worlddynamicsengine.manager.organization.WarManager;
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
import com.thatguycy.worlddynamicsengine.util.TextColors;
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
            sender.sendMessage(TextColors.RED + msg("org_usage_peacetreaty"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        String targetNation = args[3];
        WarManager.PeaceTreatyProposalState.ProposalType proposalType = WarManager.PeaceTreatyProposalState.ProposalType.fromString(args[4]);
        if (proposalType == null) {
            sender.sendMessage(TextColors.RED + msg("org_usage_peacetreaty"));
            return true;
        }

        List<WarManager.PeaceTreatyClauseState> clauses = parseClauses(String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)));
        if (clauses.isEmpty()) {
            sender.sendMessage(TextColors.RED + msg("org_peace_treaty_invalid_clause"));
            return true;
        }

        WarManager.PeaceTreatyProposalResult result = orgManager.getWarManager().proposePeaceTreaty(org, actorNation, targetNation, proposalType, clauses, nationManager);
        if (!result.isSuccess()) {
            sender.sendMessage(TextColors.RED + msg(result.getMessageKey()));
            return true;
        }

        String scoreDifferenceText = String.format(Locale.US, "%.2f", result.getScoreDifference());
        if (result.getStatus() == WarManager.PeaceTreatyProposalResult.Status.FORCED_ACCEPTED) {
            sender.sendMessage(TextColors.GREEN + msg("org_peace_treaty_forced_accepted",
                    targetNation,
                    result.getProposal().getTotalPoints(),
                    scoreDifferenceText));
            return true;
        }

        sender.sendMessage(TextColors.GREEN + msg("org_peace_treaty_proposed",
                targetNation,
                proposalType == WarManager.PeaceTreatyProposalState.ProposalType.DEMAND ? msg("org_peace_treaty_type_demand") : msg("org_peace_treaty_type_surrender"),
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
            sender.sendMessage(TextColors.RED + msg("org_usage_reviewtreaty"));
            return true;
        }

        String actorNation = getAuthorizedNationName(player);
        if (actorNation == null) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        String targetNation = args[3];
        Boolean accept = parseReviewDecision(args[4]);
        if (accept == null) {
            sender.sendMessage(TextColors.RED + msg("org_usage_reviewtreaty"));
            return true;
        }

        WarManager.PeaceTreatyReviewResult result = orgManager.getWarManager().reviewPeaceTreaty(org, actorNation, targetNation, accept, nationManager);
        if (result.getStatus() == WarManager.PeaceTreatyReviewResult.Status.INVALID) {
            sender.sendMessage(TextColors.RED + msg(result.getMessageKey()));
            return true;
        }

        sender.sendMessage(TextColors.GREEN + msg(result.getStatus() == WarManager.PeaceTreatyReviewResult.Status.ACCEPTED
                ? "org_peace_treaty_accepted"
                : "org_peace_treaty_rejected", targetNation));
        return true;
    }

    private List<WarManager.PeaceTreatyClauseState> parseClauses(String rawText) {
        List<WarManager.PeaceTreatyClauseState> clauses = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return clauses;
        }

        String[] clauseTexts = rawText.split(";");
        for (String clauseText : clauseTexts) {
            String[] parts = clauseText.trim().split(":");
            WarManager.PeaceTreatyClauseState.ClauseType type =
                    WarManager.PeaceTreatyClauseState.ClauseType.fromString(parts[0]);
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
                case TRIBUTE -> {
                    if (parts.length >= 2) {
                        parameters.put("amount", parts[1]);
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
                case FORCE_DEMOCRATIZATION -> {
                    if (parts.length >= 2) {
                        parameters.put("president", parts[1]);
                    }
                }
                case DISSOLVE_THREAT_ORGANIZATION -> {
                    if (parts.length >= 2) {
                        parameters.put("organization", parts[1]);
                    }
                }
                case FORCE_CONVERSION, DISSOLVE_ALLIANCE, EXPEL_FROM_ALLIANCE, UNCONDITIONAL_PASSAGE, HUMILIATE, LOOT_CAPITAL, FORCE_VASSALIZE, ALLOW_INDEPENDENCE -> {
                }
            }

            clauses.add(new WarManager.PeaceTreatyClauseState(type, parameters));
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
        sender.sendMessage(TextColors.RED + msg("common_only_players"));
        return null;
    }

    private boolean requireOrganization(CommandSender sender, WDEorg org) {
        if (org != null) {
            return true;
        }
        sender.sendMessage(TextColors.RED + msg("org_not_found"));
        return false;
    }

    private String getAuthorizedNationName(Player player) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                return null;
            }
            Nation nation = resident.getTown().getNationOrNull();
            if (resident.getTown().getMayor() != null && resident.getTown().getMayor().equals(resident)
                    && orgManager.getWarManager().isTownInActiveWar(resident.getTown().getName())) {
                return "town:" + resident.getTown().getName();
            }
            return nation == null ? null : nation.getName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }
}
