package com.thatguycy.worlddynamicsengine.command.governance;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.List;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理城镇治理、选举与征召兵指令，依据城镇归属、政体和职位检查操作资格。
 */
public class TownCommand implements CommandExecutor {
    private final TownManager townManager;

    public TownCommand(TownManager townManager) {
        this.townManager = townManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && (args.length == 1 || args.length == 2 && "menu".equalsIgnoreCase(args[1]))) {
            WorldDynamicsEngine.getInstance().getPlayerMenuManager().openTownManagement(player, null);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(TextColors.RED + msg("town_usage_root"));
            return true;
        }

        String townName = args[1];
        WDEtown town = townManager.getTown(townName);
        if (args.length == 2) {
            return displayTownInfo(sender, townName, town);
        }

        String subcommand = args[2].toLowerCase();
        switch (subcommand) {
            case "menu":
                if (sender instanceof Player player) {
                    WorldDynamicsEngine.getInstance().getPlayerMenuManager().openTownManagement(player, townName);
                } else {
                    sender.sendMessage(TextColors.RED + msg("common_only_players"));
                }
                return true;
            case "setgovtype":
                return handleSetTownGovernmentType(sender, town, args);
            case "setleader":
                return handleSetTownLeader(sender, town, args);
            case "nominateleader":
                return handleNominateTownLeader(sender, town, args);
            case "voteleader":
                return handleVoteTownLeader(sender, town, args);
            case "setelectiondays":
                return handleSetElectionDays(sender, town, args);
            case "setconscript":
                return handleSetConscript(sender, town, args);
            case "setconscriptprivilege":
                return handleSetConscriptPrivilege(sender, town, args);
            default:
                sender.sendMessage(TextColors.RED + msg("town_unknown_subcommand"));
                return true;
        }
    }

    private boolean displayTownInfo(CommandSender sender, String townName, WDEtown town) {
        if (town == null) {
            sender.sendMessage(TextColors.RED + msg("town_not_found", townName));
            return true;
        }

        sender.sendMessage(TextColors.GOLD + msg("town_info_header", townName));
        sender.sendMessage(TextColors.YELLOW + msg("town_info_government_type") + TextColors.WHITE + formatTownGovernmentType(town.getGovernmentType()));
        sender.sendMessage(TextColors.YELLOW + msg("town_info_religion") + TextColors.WHITE + town.getReligionName());
        sender.sendMessage(TextColors.YELLOW + msg("town_info_leader") + TextColors.WHITE + town.getTownLeader());
        sender.sendMessage(TextColors.YELLOW + msg("town_info_parent_nation") + TextColors.WHITE + town.getNationName());
        sender.sendMessage(TextColors.YELLOW + msg("town_info_organizations") + TextColors.WHITE + (town.getOrganizationNames().isEmpty() ? msg("common_none") : String.join(", ", town.getOrganizationNames())));
        sender.sendMessage(TextColors.YELLOW + msg("town_info_resident_tax_type") + TextColors.WHITE + msg("town_tax_type_" + town.getResidentTaxType().toLowerCase()));
        sender.sendMessage(TextColors.YELLOW + msg("town_info_resident_tax_amount") + TextColors.WHITE + formatTaxAmount(town));
        sender.sendMessage(TextColors.YELLOW + msg("town_info_conscript_limit") + TextColors.WHITE + town.getConscripts().size() + "/" + townManager.getTownConscriptLimit(town));
        sender.sendMessage(TextColors.YELLOW + msg("town_info_conscripts") + TextColors.WHITE + formatConscripts(town));
        boolean showElections = !"DICTATORSHIP".equalsIgnoreCase(town.getGovernmentType());
        if (showElections) {
            sender.sendMessage(TextColors.YELLOW + msg("town_info_election_interval_days") + TextColors.WHITE + town.getElectionIntervalDays());
        }
        if (showElections && town.hasActiveElection()) {
            if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) {
                sender.sendMessage(TextColors.YELLOW + msg("town_info_pending_leader") + TextColors.WHITE + formatCandidateList(town.getTownLeaderCandidates()));
                sender.sendMessage(TextColors.YELLOW + msg("town_info_votes") + TextColors.WHITE + summarizeCandidateVotes(townManager.countTownLeaderVotes(town)));
            } else {
                sender.sendMessage(TextColors.YELLOW + msg("town_info_pending_leader") + TextColors.WHITE + town.getPendingTownLeader());
                sender.sendMessage(TextColors.YELLOW + msg("town_info_votes") + TextColors.WHITE + summarizeVotes(town.getTownLeaderVotes()));
            }
        }
        return true;
    }

    private boolean handleSetTownGovernmentType(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("town_usage_setgovtype"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null || !hasTownAuthority(player, town)) {
            sender.sendMessage(TextColors.RED + msg("town_no_authority_govtype"));
            return true;
        }

        String normalized = normalizeTownGovernmentType(args[3]);
        List<String> validTypes = WorldDynamicsEngine.getInstance().getValidTownGovernmentTypes();
        String validTypeDisplay = validTypes.stream()
                .map(this::formatTownGovernmentType)
                .collect(java.util.stream.Collectors.joining(", "));
        if (!validTypes.contains(normalized)) {
            sender.sendMessage(TextColors.RED + msg("town_invalid_govtype", validTypeDisplay));
            return true;
        }

        town.setGovernmentType(normalized);
        if ("REPUBLIC".equalsIgnoreCase(normalized)) {
            town.setElectionIntervalDays(WorldDynamicsEngine.getInstance().getDefaultTownElectionIntervalDays());
            town.setLastElectionAt(System.currentTimeMillis());
            town.setElectionEndAt(0L);
            town.setPendingTownLeader("None");
            town.setTownLeaderVotes(new java.util.HashMap<>());
            town.setTownLeaderCandidates(new java.util.ArrayList<>());
            town.setTownLeaderBallots(new java.util.LinkedHashMap<>());
        } else {
            town.setPendingTownLeader("None");
            town.setTownLeaderVotes(new java.util.HashMap<>());
            town.setTownLeaderCandidates(new java.util.ArrayList<>());
            town.setTownLeaderBallots(new java.util.LinkedHashMap<>());
            town.setElectionEndAt(0L);
        }
        townManager.saveTowns();
        sender.sendMessage(TextColors.GREEN + msg("town_govtype_set", formatTownGovernmentType(normalized)));
        return true;
    }

    private boolean handleSetTownLeader(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("town_usage_setleader"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null || !hasTownAuthority(player, town)) {
            sender.sendMessage(TextColors.RED + msg("town_no_authority_leader"));
            return true;
        }

        if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) {
            return handleNominateTownLeader(sender, town, new String[]{args[0], args[1], "nominateleader", args[3]});
        }

        if (!isResidentOfTown(town.getTownName(), args[3])) {
            sender.sendMessage(TextColors.RED + msg("town_selected_player_not_resident"));
            return true;
        }

        town.setTownLeader(args[3]);
        town.setPendingTownLeader("None");
        town.setTownLeaderVotes(new java.util.HashMap<>());
        town.setTownLeaderCandidates(new java.util.ArrayList<>());
        town.setTownLeaderBallots(new java.util.LinkedHashMap<>());
        town.setElectionEndAt(0L);
        townManager.applyTownLeaderToTowny(town);
        townManager.saveTowns();
        sender.sendMessage(TextColors.GREEN + msg("town_leader_set", args[3]));
        return true;
    }

    private boolean handleNominateTownLeader(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("town_usage_nominateleader"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (!"REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) {
            sender.sendMessage(TextColors.RED + msg("town_not_republic"));
            return true;
        }

        long nextElectionAt = town.getLastElectionAt() + (Math.max(1, town.getElectionIntervalDays()) * 86400000L);
        if (!town.hasActiveElection() && System.currentTimeMillis() < nextElectionAt) {
            sender.sendMessage(TextColors.RED + msg("town_not_in_election_period"));
            return true;
        }

        if (!townManager.isTownResident(town.getTownName(), player.getUniqueId())) {
            sender.sendMessage(TextColors.RED + msg("town_only_residents_nominate"));
            return true;
        }

        if (!isResidentOfTown(town.getTownName(), args[3])) {
            sender.sendMessage(TextColors.RED + msg("town_nominated_player_not_resident"));
            return true;
        }

        townManager.startTownElection(town, args[3]);
        sender.sendMessage(TextColors.GREEN + msg("town_candidate_set", args[3], formatCandidateList(town.getTownLeaderCandidates())));
        return true;
    }

    private boolean handleVoteTownLeader(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("town_usage_voteleader"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (!town.hasActiveElection()) {
            sender.sendMessage(TextColors.RED + msg("town_no_active_election"));
            return true;
        }

        if (!townManager.isTownResident(town.getTownName(), player.getUniqueId())) {
            sender.sendMessage(TextColors.RED + msg("town_only_residents_vote"));
            return true;
        }

        if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) {
            String candidateName = findMatchingCandidate(town.getTownLeaderCandidates(), args[3]);
            if (candidateName == null) {
                sender.sendMessage(TextColors.RED + msg("town_vote_candidate_invalid", formatCandidateList(town.getTownLeaderCandidates())));
                return true;
            }

            boolean applied = townManager.castTownLeaderVote(town, player.getUniqueId().toString(), candidateName);
            if (applied) {
                sender.sendMessage(TextColors.GREEN + msg("town_election_passed", town.getTownLeader()));
            } else {
                sender.sendMessage(TextColors.GREEN + msg("town_vote_recorded", candidateName));
            }
            return true;
        }

        boolean support;
        if ("yes".equalsIgnoreCase(args[3])) {
            support = true;
        } else if ("no".equalsIgnoreCase(args[3])) {
            support = false;
        } else {
            sender.sendMessage(TextColors.RED + msg("common_vote_yes_no"));
            return true;
        }

        boolean applied = townManager.castTownLeaderVote(town, player.getUniqueId().toString(), support);
        if (applied) {
            sender.sendMessage(TextColors.GREEN + msg("town_election_passed", town.getTownLeader()));
        } else {
            sender.sendMessage(TextColors.GREEN + msg("town_vote_recorded", town.getPendingTownLeader()));
        }
        return true;
    }

    private boolean handleSetElectionDays(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("town_usage_setelectiondays"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null || !hasTownAuthority(player, town)) {
            sender.sendMessage(TextColors.RED + msg("town_no_authority_election_interval"));
            return true;
        }

        int days;
        try {
            days = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextColors.RED + msg("town_election_interval_integer"));
            return true;
        }

        if (days <= 0) {
            sender.sendMessage(TextColors.RED + msg("town_election_interval_gt_zero"));
            return true;
        }

        town.setElectionIntervalDays(days);
        townManager.saveTowns();
        sender.sendMessage(TextColors.GREEN + msg("town_election_interval_updated", days));
        return true;
    }

    private boolean handleSetConscript(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 5) {
            sender.sendMessage(TextColors.RED + msg("town_usage_setconscript"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null || !hasDirectTownAuthority(player, town)) {
            sender.sendMessage(TextColors.RED + msg("town_no_authority_conscript"));
            return true;
        }

        String targetName = args[3];
        Boolean enabled = parseEnabled(args[4]);
        if (enabled == null) {
            sender.sendMessage(TextColors.RED + msg("common_enabled_disabled"));
            return true;
        }

        if (!isResidentOfTown(town.getTownName(), targetName)) {
            sender.sendMessage(TextColors.RED + msg("town_selected_player_not_resident"));
            return true;
        }

        if (enabled) {
            if (!town.isConscript(targetName) && town.getConscripts().size() >= townManager.getTownConscriptLimit(town)) {
                sender.sendMessage(TextColors.RED + msg("town_conscript_limit_reached", townManager.getTownConscriptLimit(town)));
                return true;
            }
            town.setConscript(targetName, town.isConscriptTaxExempt(targetName));
            sender.sendMessage(TextColors.GREEN + msg("town_conscript_set", targetName));
        } else {
            if (!town.isConscript(targetName)) {
                sender.sendMessage(TextColors.RED + msg("town_conscript_not_found", targetName));
                return true;
            }
            town.removeConscript(targetName);
            sender.sendMessage(TextColors.GREEN + msg("town_conscript_removed", targetName));
        }

        townManager.saveTowns();
        return true;
    }

    private boolean handleSetConscriptPrivilege(CommandSender sender, WDEtown town, String[] args) {
        if (town == null || args.length < 6) {
            sender.sendMessage(TextColors.RED + msg("town_usage_setconscriptprivilege"));
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null || !hasDirectTownAuthority(player, town)) {
            sender.sendMessage(TextColors.RED + msg("town_no_authority_conscript"));
            return true;
        }

        String targetName = args[3];
        if (!town.isConscript(targetName)) {
            sender.sendMessage(TextColors.RED + msg("town_conscript_not_found", targetName));
            return true;
        }

        if (!"taxfree".equalsIgnoreCase(args[4]) && !"免税".equalsIgnoreCase(args[4])) {
            sender.sendMessage(TextColors.RED + msg("town_invalid_conscript_privilege"));
            return true;
        }

        Boolean enabled = parseEnabled(args[5]);
        if (enabled == null) {
            sender.sendMessage(TextColors.RED + msg("common_enabled_disabled"));
            return true;
        }

        town.setConscriptTaxExempt(targetName, enabled);
        townManager.saveTowns();
        sender.sendMessage(TextColors.GREEN + msg("town_conscript_privilege_updated", targetName, msg("town_conscript_privilege_taxfree"), enabled ? msg("common_enabled") : msg("common_disabled")));
        return true;
    }

    private boolean hasTownAuthority(Player player, WDEtown town) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                return false;
            }

            Town playerTown = resident.getTown();
            if (!playerTown.getName().equalsIgnoreCase(town.getTownName())) {
                if (playerTown.hasNation()) {
                    Nation nation = playerTown.getNation();
                    Town targetTown = TownyUniverse.getInstance().getTown(town.getTownName());
                    return nation.isKing(resident) && targetTown != null && targetTown.getNationOrNull() == nation;
                }
                return false;
            }

            if (town.getTownLeader() != null && town.getTownLeader().equalsIgnoreCase(player.getName())) {
                return true;
            }

            try {
                return playerTown.getMayor() != null && playerTown.getMayor().getUUID().equals(player.getUniqueId());
            } catch (Exception ignored) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasDirectTownAuthority(Player player, WDEtown town) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) {
                return false;
            }

            Town playerTown = resident.getTown();
            if (!playerTown.getName().equalsIgnoreCase(town.getTownName())) {
                return false;
            }

            if (town.getTownLeader() != null && town.getTownLeader().equalsIgnoreCase(player.getName())) {
                return true;
            }

            return playerTown.getMayor() != null && playerTown.getMayor().getUUID().equals(player.getUniqueId());
        } catch (Exception ignored) {
            return false;
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextColors.RED + msg("common_only_players"));
            return null;
        }
        return (Player) sender;
    }

    private String normalizeTownGovernmentType(String rawValue) {
        String normalized = rawValue.trim().toUpperCase();
        return switch (normalized) {
            case "独裁" -> "DICTATORSHIP";
            case "民主", "共和", "REPUBLIC" -> "REPUBLIC";
            default -> normalized;
        };
    }

    private String formatTownGovernmentType(String governmentType) {
        if (governmentType == null || governmentType.isBlank()) {
            return msg("common_none");
        }

        return switch (governmentType.trim().toUpperCase()) {
            case "DICTATORSHIP" -> msg("gov_type_dictatorship");
            case "REPUBLIC" -> msg("gov_type_republic");
            default -> governmentType;
        };
    }

    private String summarizeVotes(java.util.Map<String, Boolean> votes) {
        long yesVotes = votes.values().stream().filter(Boolean::booleanValue).count();
        long noVotes = votes.values().stream().filter(vote -> !vote).count();
        return msg("common_vote_summary", yesVotes, noVotes);
    }

    private String summarizeCandidateVotes(java.util.Map<String, Integer> votes) {
        if (votes == null || votes.isEmpty()) {
            return msg("common_none");
        }

        List<String> parts = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> entry : votes.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", parts);
    }

    private String formatCandidateList(List<String> candidates) {
        return candidates == null || candidates.isEmpty() ? msg("common_none") : String.join(", ", candidates);
    }

    private String findMatchingCandidate(List<String> candidates, String rawCandidate) {
        if (candidates == null || rawCandidate == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(rawCandidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String formatConscripts(WDEtown town) {
        if (town.getConscripts().isEmpty()) {
            return msg("common_none");
        }

        List<String> values = new ArrayList<>();
        for (java.util.Map.Entry<String, Boolean> entry : town.getConscripts().entrySet()) {
            String suffix = Boolean.TRUE.equals(entry.getValue()) ? " [" + msg("town_conscript_privilege_taxfree") + "]" : "";
            values.add(entry.getKey() + suffix);
        }
        return String.join(", ", values);
    }

    private String formatTaxAmount(WDEtown town) {
        if ("WEALTH_PERCENTAGE".equalsIgnoreCase(town.getResidentTaxType())) {
            return String.format(java.util.Locale.US, "%.4f%%", town.getResidentTaxAmount());
        }
        return String.format(java.util.Locale.US, "%.2f", town.getResidentTaxAmount());
    }

    private Boolean parseEnabled(String rawValue) {
        return switch (rawValue.toLowerCase(java.util.Locale.ROOT)) {
            case "enabled", "enable", "true", "on", "yes", "开启", "启用", "是" -> true;
            case "disabled", "disable", "false", "off", "no", "关闭", "禁用", "否" -> false;
            default -> null;
        };
    }

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }

    private boolean isResidentOfTown(String townName, String playerName) {
        try {
            Town town = TownyUniverse.getInstance().getTown(townName);
            Resident resident = TownyUniverse.getInstance().getResident(playerName);
            return town != null && resident != null && town.hasResident(resident);
        } catch (Exception e) {
            return false;
        }
    }
}
