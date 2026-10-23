package com.thatguycy.worlddynamicsengine.command.governance;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

/**
 * 处理国家政体、职位、选举、税制和军队指令，并核验当前 Towny 归属及操作角色。
 */
public class NationCommand implements CommandExecutor {

    private NationManager nationManager;
    private ResidentManager residentManager;

    public NationCommand(NationManager nationManager, ResidentManager residentManager) {
        this.nationManager = nationManager;
        this.residentManager = residentManager;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && (args.length == 1 || args.length == 2 && "menu".equalsIgnoreCase(args[1]))) {
            WorldDynamicsEngine.getInstance().getPlayerMenuManager().openNationManagement(player, null);
            return true;
        }
        // Check if the nation subcommand and nation name are provided
        if (args.length < 2) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_root"));
            return true;
        }

        // args[0] is 'nation', args[1] should be the nation name
        String nationName = args[1];
        WDEnation nation = nationManager.getNation(nationName);

        // If only the nation name is provided, display nation info
        if (args.length == 2) {
            return displayNationInfo(sender, nationName, nation);
        }

        // Handling cases with additional subcommands
        else if (args.length > 2) {
            switch (args[2].toLowerCase()) {
                case "menu":
                    if (sender instanceof Player player) {
                        WorldDynamicsEngine.getInstance().getPlayerMenuManager().openNationManagement(player, nationName);
                    } else {
                        sender.sendMessage(TextColors.RED + msg("common_only_players"));
                    }
                    return true;
                case "setgovtype":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleSetGovType(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "setgovleader":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleSetGovLeader(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "startelection":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleStartDemocracyElection(sender, nation);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "voteleader":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleVoteGovLeader(sender, nation, args);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "votearmycommander":
                    if (WorldDynamicsEngine.getInstance().armyEnabled) {
                        return handleVoteArmyCommander(sender, nation, args);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "addgovmember":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleAddGovMember(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "kickgovmember":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleKickGovMember(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "appointarmycommander":
                    if (WorldDynamicsEngine.getInstance().armyEnabled) {
                        return handleAppointArmyCommander(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "enlistarmymember":
                    if (WorldDynamicsEngine.getInstance().armyEnabled) {
                        return handleEnlistArmyMember(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "dischargearmymember":
                    if (WorldDynamicsEngine.getInstance().armyEnabled) {
                        return handleDischargeArmyMember(sender, nation, args, WorldDynamicsEngine.getInstance());
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "tpconscript":
                    if (WorldDynamicsEngine.getInstance().armyEnabled) {
                        return handleTeleportToConscript(sender, nation, args);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "tpchaircommander":
                    if (WorldDynamicsEngine.getInstance().armyEnabled) {
                        return handleTeleportToChairmanCommander(sender, nation, args);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "adddiplomat":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        if (args.length < 4) {
                            sender.sendMessage(TextColors.RED + msg("nation_usage_adddiplomat"));
                            return true;
                        }
                        if (!hasNationAuthority(sender, nationManager)) {
                            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
                            return true;
                        }
                        return handleAddDiplomat(sender, args[3], residentManager);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "removediplomat":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        if (args.length < 4) {
                            sender.sendMessage(TextColors.RED + msg("nation_usage_removediplomat"));
                            return true;
                        }
                        if (!hasNationAuthority(sender, nationManager)) {
                            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
                            return true;
                        }
                        return handleRemoveDiplomat(sender, args[3], residentManager);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                case "settowntax":
                    if (WorldDynamicsEngine.getInstance().govEnabled) {
                        return handleSetTownTax(sender, nation, args);
                    } else {
                        sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                        return true;
                    }
                default:
                    sender.sendMessage(TextColors.RED + WorldDynamicsEngine.getInstance().getLocaleMessage(WorldDynamicsEngine.getInstance().userLang, "unknownsub"));
                    return true;
            }
        }

        return true;
    }


    private boolean displayNationInfo(CommandSender sender, String nationName, WDEnation nation) {
        if (nation == null) {
            sender.sendMessage(TextColors.RED + msg("nation_not_found_with_name", nationName));
            return true;
        }

        // Display nation information in a format similar to Towny's /t and /n
        sender.sendMessage(TextColors.GOLD + msg("nation_info_header", nationName));
        sender.sendMessage(TextColors.YELLOW + msg("nation_info_government_type") + TextColors.WHITE + formatNationGovernmentType(nation.getGovernmentType()));
        sender.sendMessage(TextColors.YELLOW + msg("nation_info_religion") + TextColors.WHITE + nation.getReligionName());
        sender.sendMessage(TextColors.YELLOW + msg("nation_info_government_leader") + TextColors.WHITE + nation.getGovernmentLeader());

        // Formatting the list of government members
        String membersList = String.join(", ", nation.getGovernmentMembers());
        sender.sendMessage(TextColors.YELLOW + msg("nation_info_government_members") + TextColors.WHITE + (membersList.isEmpty() ? msg("common_none") : membersList));

        sender.sendMessage(TextColors.YELLOW + msg("nation_info_army_commander") + TextColors.WHITE + nation.getArmyLeader());

        // Formatting the list of government members
        String armyMembersList = String.join(", ", nation.getArmyMembers());
        sender.sendMessage(TextColors.YELLOW + msg("nation_info_army_members") + TextColors.WHITE + (armyMembersList.isEmpty() ? msg("common_none") : armyMembersList));
        String townList = String.join(", ", nation.getTownNames());
        sender.sendMessage(TextColors.YELLOW + msg("nation_info_towns") + TextColors.WHITE + (townList.isEmpty() ? msg("common_none") : townList));
        boolean showElections = !"DICTATORSHIP".equalsIgnoreCase(nation.getGovernmentType());
        if (showElections && nation.hasActiveElection()) {
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType()) && !nation.getGovernmentLeaderCandidates().isEmpty()) {
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_pending_leader_vote") + TextColors.WHITE + formatCandidateList(nation.getGovernmentLeaderCandidates()));
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_current_votes") + TextColors.WHITE + summarizeCandidateVotes(nationManager.countGovernmentLeaderBallots(nation)));
            } else if (nation.getPendingGovernmentLeader() != null && !nation.getPendingGovernmentLeader().equalsIgnoreCase("None")) {
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_pending_leader_vote") + TextColors.WHITE + nation.getPendingGovernmentLeader());
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_current_votes") + TextColors.WHITE + summarizeVotes(nation.getGovernmentLeaderVotes()));
            }
        }
        if (showElections && nation.hasActiveArmyCommanderVote()) {
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType()) && !nation.getArmyCommanderCandidates().isEmpty()) {
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_pending_army_commander_vote") + TextColors.WHITE + formatCandidateList(nation.getArmyCommanderCandidates()));
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_current_votes") + TextColors.WHITE + summarizeCandidateVotes(nationManager.countArmyCommanderBallots(nation)));
            } else if (nation.getPendingArmyCommander() != null && !nation.getPendingArmyCommander().equalsIgnoreCase("None")) {
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_pending_army_commander_vote") + TextColors.WHITE + nation.getPendingArmyCommander());
                sender.sendMessage(TextColors.YELLOW + msg("nation_info_current_votes") + TextColors.WHITE + summarizeVotes(nation.getArmyCommanderVotes()));
            }
        }

        return true;
    }

    private boolean handleSetGovType(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_setgovtype"));
            return true;
        }

        Resident resident = TownyUniverse.getInstance().getResident(sender.getName());
        if (resident == null) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_resident"));
            return true;
        }

        Nation townyNation = TownyUniverse.getInstance().getNation(args[1]);
        if (townyNation == null) {
            sender.sendMessage(TextColors.RED + msg("nation_not_found_in_towny"));
            sender.sendMessage(TextColors.RED + args[1]);
            return true;
        }

        if (!resident.equals(townyNation.getKing())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_leader"));
            return true;
        }

        String govType = normalizeNationGovernmentType(args[3]);
        List<String> validGovTypes = plugin.getValidGovernmentTypes();
        String validGovTypeDisplay = validGovTypes.stream()
                .map(this::formatNationGovernmentType)
                .collect(java.util.stream.Collectors.joining(", "));

        // Check if the provided government type is valid
        if (!validGovTypes.contains(govType.toUpperCase())) {
            sender.sendMessage(TextColors.RED + msg("nation_invalid_govtype", validGovTypeDisplay));
            return true;
        }

        // Set the government type
        nation.setGovernmentType(govType);
        nation.setPendingGovernmentLeader("None");
        nation.setGovernmentLeaderVotes(new java.util.HashMap<>());
        nation.setGovernmentLeaderCandidates(new java.util.ArrayList<>());
        nation.setGovernmentLeaderBallots(new java.util.HashMap<>());
        nation.setPendingArmyCommander("None");
        nation.setArmyCommanderVotes(new java.util.HashMap<>());
        nation.setArmyCommanderCandidates(new java.util.ArrayList<>());
        nation.setArmyCommanderBallots(new java.util.HashMap<>());
        nation.setArmyElectionEndAt(0L);
        if ("DEMOCRACY".equalsIgnoreCase(govType)) {
            nation.setElectionIntervalDays(nationManager.getDefaultNationElectionIntervalDays());
            nation.setLastElectionAt(System.currentTimeMillis());
            nation.setElectionEndAt(0L);
        } else {
            nation.setElectionEndAt(0L);
        }
        sender.sendMessage(TextColors.GREEN + msg("nation_govtype_set", formatNationGovernmentType(govType), nation.getNationName()));

        // Save the updated nation information
        nationManager.saveNations();

        return true;
    }

    private boolean handleSetGovLeader(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_setgovleader"));
            return true;
        }

        Resident resident = TownyUniverse.getInstance().getResident(sender.getName());
        Nation townyNation = TownyUniverse.getInstance().getNation(nation.getNationName());
        if (townyNation == null || resident == null || !resident.equals(townyNation.getKing())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_king"));
            return true;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
            sender.sendMessage(TextColors.RED + msg("nation_democracy_setgovleader_disabled"));
            return true;
        }

        String newLeaderName = args[3];
        if (!nationManager.isNationResidentCandidate(nation, newLeaderName)) {
            sender.sendMessage(TextColors.RED + msg("nation_candidate_must_be_nation_resident"));
            return true;
        }

        if ("DICTATORSHIP".equalsIgnoreCase(nation.getGovernmentType())) {
            nation.setGovernmentLeader(newLeaderName);
            nation.setPendingGovernmentLeader("None");
            nation.setGovernmentLeaderVotes(new java.util.HashMap<>());
            nation.setGovernmentLeaderCandidates(new java.util.ArrayList<>());
            nation.setGovernmentLeaderBallots(new java.util.HashMap<>());
            sender.sendMessage(TextColors.GREEN + msg("nation_govleader_set", newLeaderName, nation.getNationName()));
        } else {
            nationManager.startNationLeadershipVote(nation, newLeaderName);
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
                sender.sendMessage(TextColors.GREEN + msg("nation_democracy_election_started", formatCandidateList(nation.getGovernmentLeaderCandidates())));
            } else {
                sender.sendMessage(TextColors.GREEN + msg("nation_noble_vote_started", newLeaderName));
            }
        }

        nationManager.saveNations();
        return true;
    }

    private boolean handleStartDemocracyElection(CommandSender sender, WDEnation nation) {
        if (nation == null) {
            sender.sendMessage(TextColors.RED + msg("nation_not_found"));
            return true;
        }

        if (!"DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
            sender.sendMessage(TextColors.RED + msg("nation_start_election_democracy_only"));
            return true;
        }

        Resident resident = TownyUniverse.getInstance().getResident(sender.getName());
        Nation townyNation = TownyUniverse.getInstance().getNation(nation.getNationName());
        boolean isTownyKing = townyNation != null && resident != null && resident.equals(townyNation.getKing());
        boolean isGovernmentLeader = nation.getGovernmentLeader() != null && nation.getGovernmentLeader().equalsIgnoreCase(sender.getName());
        if (!isTownyKing && !isGovernmentLeader) {
            sender.sendMessage(TextColors.RED + msg("nation_start_election_only_leader"));
            return true;
        }

        if (nation.hasActiveElection()) {
            sender.sendMessage(TextColors.RED + msg("nation_election_already_active"));
            return true;
        }

        String excludedLeader = sender.getName();
        if (!nationManager.startEarlyDemocracyLeadershipElection(nation, excludedLeader)) {
            sender.sendMessage(TextColors.RED + msg("nation_no_democracy_candidates_after_exclusion"));
            return true;
        }

        sender.sendMessage(TextColors.GREEN + msg("nation_democracy_early_election_started", excludedLeader, formatCandidateList(nation.getGovernmentLeaderCandidates())));
        return true;
    }

    private boolean handleVoteArmyCommander(CommandSender sender, WDEnation nation, String[] args) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_votearmycommander"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextColors.RED + msg("nation_only_players_vote"));
            return true;
        }

        if (!nation.hasActiveArmyCommanderVote()) {
            sender.sendMessage(TextColors.RED + msg("nation_no_active_army_vote"));
            return true;
        }

        if (!nationManager.isEligibleNationVoter(nation, player.getUniqueId())) {
            sender.sendMessage(TextColors.RED + msg("nation_not_eligible_vote"));
            return true;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
            String candidateName = findMatchingCandidate(nation.getArmyCommanderCandidates(), args[3]);
            if (candidateName == null) {
                sender.sendMessage(TextColors.RED + msg("nation_vote_candidate_invalid", formatCandidateList(nation.getArmyCommanderCandidates())));
                return true;
            }

            boolean applied = nationManager.castArmyCommanderVote(nation, player.getUniqueId().toString(), candidateName);
            if (applied) {
                sender.sendMessage(TextColors.GREEN + msg("nation_army_vote_passed", nation.getArmyCommander()));
            } else {
                sender.sendMessage(TextColors.GREEN + msg("nation_army_vote_recorded", candidateName));
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

        boolean applied = nationManager.castArmyCommanderVote(nation, player.getUniqueId().toString(), support);
        if (applied) {
            sender.sendMessage(TextColors.GREEN + msg("nation_army_vote_passed", nation.getArmyCommander()));
        } else {
            sender.sendMessage(TextColors.GREEN + msg("nation_army_vote_recorded", nation.getPendingArmyCommander()));
        }
        return true;
    }

    private boolean handleVoteGovLeader(CommandSender sender, WDEnation nation, String[] args) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_voteleader"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextColors.RED + msg("nation_only_players_vote"));
            return true;
        }

        if (!nation.hasActiveElection()) {
            sender.sendMessage(TextColors.RED + msg("nation_no_active_vote"));
            return true;
        }

        if (!nationManager.isEligibleNationVoter(nation, player.getUniqueId())) {
            sender.sendMessage(TextColors.RED + msg("nation_not_eligible_vote"));
            return true;
        }

        if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
            String candidateName = findMatchingCandidate(nation.getGovernmentLeaderCandidates(), args[3]);
            if (candidateName == null) {
                sender.sendMessage(TextColors.RED + msg("nation_vote_candidate_invalid", formatCandidateList(nation.getGovernmentLeaderCandidates())));
                return true;
            }

            boolean applied = nationManager.castNationLeaderVote(nation, player.getUniqueId().toString(), candidateName);
            if (applied) {
                String capitalTownName = nationManager.findResidentTownName(nation, nation.getGovernmentLeader());
                sender.sendMessage(TextColors.GREEN + msg("nation_vote_passed_democracy", nation.getGovernmentLeader(), capitalTownName == null ? msg("common_none") : capitalTownName));
            } else {
                sender.sendMessage(TextColors.GREEN + msg("nation_vote_recorded", candidateName));
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

        boolean applied = nationManager.castNationLeaderVote(nation, player.getUniqueId().toString(), support);
        if (applied) {
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
                String capitalTownName = nationManager.findTownLedByCandidate(nation, nation.getGovernmentLeader());
                sender.sendMessage(TextColors.GREEN + msg("nation_vote_passed_democracy", nation.getGovernmentLeader(), capitalTownName == null ? msg("common_none") : capitalTownName));
            } else {
                sender.sendMessage(TextColors.GREEN + msg("nation_vote_passed", nation.getGovernmentLeader()));
            }
        } else {
            sender.sendMessage(TextColors.GREEN + msg("nation_vote_recorded", nation.getPendingGovernmentLeader()));
        }
        return true;
    }

    private boolean handleAddGovMember(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_addgovmember"));
            return true;
        }

        if (!sender.getName().equalsIgnoreCase(nation.getGovernmentLeader())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_government_leader"));
            return true;
        }

        String memberName = args[3];
        List<String> members = nation.getGovernmentMembers();
        if (!members.contains(memberName)) {
            members.add(memberName);
            nation.setGovernmentMembers(members);
            sender.sendMessage(TextColors.GREEN + msg("nation_govmember_added", memberName));
        } else {
            sender.sendMessage(TextColors.RED + msg("nation_member_already_government"));
        }

        nationManager.saveNations();
        return true;
    }


    private boolean handleKickGovMember(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_kickgovmember"));
            return true;
        }

        if (!sender.getName().equalsIgnoreCase(nation.getGovernmentLeader())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_government_leader"));
            return true;
        }

        String memberName = args[3];
        List<String> members = nation.getGovernmentMembers();
        if (members.contains(memberName)) {
            members.remove(memberName);
            nation.setGovernmentMembers(members);
            sender.sendMessage(TextColors.GREEN + msg("nation_govmember_removed", memberName));
        } else {
            sender.sendMessage(TextColors.RED + msg("nation_member_not_government"));
        }

        nationManager.saveNations();
        return true;
    }


    private boolean handleAppointArmyCommander(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_appointarmycommander"));
            return true;
        }

        Resident resident = TownyUniverse.getInstance().getResident(sender.getName());
        Nation townyNation = TownyUniverse.getInstance().getNation(nation.getNationName());
        if (townyNation == null || resident == null || !resident.equals(townyNation.getKing())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_king"));
            return true;
        }

        String commanderName = args[3];
        if (!nationManager.isNationResidentCandidate(nation, commanderName)) {
            sender.sendMessage(TextColors.RED + msg("nation_candidate_must_be_nation_resident"));
            return true;
        }

        if ("DICTATORSHIP".equalsIgnoreCase(nation.getGovernmentType())) {
            nation.setArmyCommander(commanderName);
            nation.setPendingArmyCommander("None");
            nation.setArmyCommanderVotes(new java.util.HashMap<>());
            nation.setArmyCommanderCandidates(new java.util.ArrayList<>());
            nation.setArmyCommanderBallots(new java.util.HashMap<>());
            nation.setArmyElectionEndAt(0L);
            sender.sendMessage(TextColors.GREEN + msg("nation_army_commander_appointed", commanderName));
        } else {
            nationManager.startArmyCommanderVote(nation, commanderName);
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) {
                sender.sendMessage(TextColors.GREEN + msg("nation_army_commander_democracy_started", commanderName, formatCandidateList(nation.getArmyCommanderCandidates())));
            } else {
                sender.sendMessage(TextColors.GREEN + msg("nation_army_commander_vote_started", commanderName));
            }
        }

        nationManager.saveNations();
        return true;
    }

    private boolean handleEnlistArmyMember(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_enlistarmymember"));
            return true;
        }

        if (!sender.getName().equalsIgnoreCase(nation.getArmyCommander())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_army_commander"));
            return true;
        }

        String memberName = args[3];
        List<String> armyMembers = nation.getArmyMembers();
        if (!armyMembers.contains(memberName)) {
            armyMembers.add(memberName);
            nation.setArmyMembers(armyMembers);
            sender.sendMessage(TextColors.GREEN + msg("nation_army_member_enlisted", memberName));
        } else {
            sender.sendMessage(TextColors.RED + msg("nation_member_already_army"));
        }

        nationManager.saveNations();
        return true;
    }


    private boolean handleDischargeArmyMember(CommandSender sender, WDEnation nation, String[] args, WorldDynamicsEngine plugin) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_dischargearmymember"));
            return true;
        }

        if (!sender.getName().equalsIgnoreCase(nation.getArmyCommander())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_army_commander"));
            return true;
        }

        String memberName = args[3];
        List<String> armyMembers = nation.getArmyMembers();
        if (armyMembers.contains(memberName)) {
            armyMembers.remove(memberName);
            nation.setArmyMembers(armyMembers);
            sender.sendMessage(TextColors.GREEN + msg("nation_army_member_discharged", memberName));
        } else {
            sender.sendMessage(TextColors.RED + msg("nation_member_not_army"));
        }

        nationManager.saveNations();
        return true;
    }

    private boolean handleTeleportToConscript(CommandSender sender, WDEnation nation, String[] args) {
        if (nation == null || args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_tpconscript"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextColors.RED + msg("common_only_players"));
            return true;
        }

        String actorNationName = getAuthorizedNationName(sender);
        if (actorNationName == null) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        OrgManager orgManager = WorldDynamicsEngine.getInstance().getOrgManager();
        WDEorg sovereignEmpire = orgManager != null ? orgManager.getSovereignEmpireByNation(nation.getNationName()) : null;
        boolean sameNationCommander = actorNationName.equalsIgnoreCase(nation.getNationName()) && player.getName().equalsIgnoreCase(nation.getArmyCommander());
        boolean chairmanCommanderDispatch = sovereignEmpire != null
                && sovereignEmpire.isSovereignEmpire()
                && !actorNationName.equalsIgnoreCase(nation.getNationName())
                && actorNationName.equalsIgnoreCase(sovereignEmpire.getChairmanNationName());

        if (!sameNationCommander && !chairmanCommanderDispatch) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_army_commander"));
            return true;
        }

        if (chairmanCommanderDispatch) {
            WDEnation chairmanNation = nationManager.getNation(actorNationName);
            if (chairmanNation == null || !player.getName().equalsIgnoreCase(chairmanNation.getArmyCommander())) {
                sender.sendMessage(TextColors.RED + msg("nation_sovereign_only_chairman_commander_dispatch"));
                return true;
            }
        }

        Player target = WorldDynamicsEngine.getInstance().getServer().getPlayerExact(args[3]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(TextColors.RED + msg("player_not_found"));
            return true;
        }

        try {
            Resident targetResident = TownyUniverse.getInstance().getResident(target.getUniqueId());
            if (targetResident == null || !targetResident.hasTown()) {
                sender.sendMessage(TextColors.RED + msg("nation_conscript_target_invalid"));
                return true;
            }

            Town targetTown = targetResident.getTown();
            TownManager townManager = WorldDynamicsEngine.getInstance().getTownManager();
            WDEtown wdeTown = townManager != null ? townManager.getTown(targetTown.getName()) : null;
            boolean validSameNationConscript = wdeTown != null
                    && nation.getNationName().equalsIgnoreCase(wdeTown.getNationName())
                    && !chairmanCommanderDispatch
                    && wdeTown.isConscript(target.getName());
            boolean validChairmanDispatchConscript = wdeTown != null
                    && chairmanCommanderDispatch
                    && sovereignEmpire != null
                    && sovereignEmpire.hasMemberNation(wdeTown.getNationName())
                    && !sovereignEmpire.getChairmanNationName().equalsIgnoreCase(wdeTown.getNationName())
                    && wdeTown.isConscript(target.getName());
            if (!validSameNationConscript && !validChairmanDispatchConscript) {
                sender.sendMessage(TextColors.RED + msg("nation_conscript_target_invalid"));
                return true;
            }

            player.teleport(target.getLocation());
            sender.sendMessage(TextColors.GREEN + msg(chairmanCommanderDispatch
                    ? "nation_tp_vassal_conscript_success"
                    : "nation_tpconscript_success", target.getName(), targetTown.getName()));
            return true;
        } catch (Exception e) {
            sender.sendMessage(TextColors.RED + msg("nation_conscript_target_invalid"));
            return true;
        }
    }

    private boolean handleTeleportToChairmanCommander(CommandSender sender, WDEnation nation, String[] args) {
        if (nation == null) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_tpchaircommander"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextColors.RED + msg("common_only_players"));
            return true;
        }

        String actorNationName = getAuthorizedNationName(sender);
        if (actorNationName == null || !actorNationName.equalsIgnoreCase(nation.getNationName())) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        if (!player.getName().equalsIgnoreCase(nation.getArmyCommander())) {
            sender.sendMessage(TextColors.RED + msg("nation_must_be_army_commander"));
            return true;
        }

        OrgManager orgManager = WorldDynamicsEngine.getInstance().getOrgManager();
        WDEorg sovereignEmpire = orgManager != null ? orgManager.getSovereignEmpireByNation(nation.getNationName()) : null;
        if (sovereignEmpire == null || !sovereignEmpire.isSovereignEmpire() || sovereignEmpire.getChairmanNationName().equalsIgnoreCase(nation.getNationName())) {
            sender.sendMessage(TextColors.RED + msg("nation_not_vassal_state"));
            return true;
        }

        WDEnation chairmanNation = nationManager.getNation(sovereignEmpire.getChairmanNationName());
        if (chairmanNation == null || chairmanNation.getArmyCommander() == null || chairmanNation.getArmyCommander().equalsIgnoreCase("None")) {
            sender.sendMessage(TextColors.RED + msg("nation_chairman_commander_unavailable"));
            return true;
        }

        Player chairmanCommander = WorldDynamicsEngine.getInstance().getServer().getPlayerExact(chairmanNation.getArmyCommander());
        if (chairmanCommander == null || !chairmanCommander.isOnline()) {
            sender.sendMessage(TextColors.RED + msg("nation_chairman_commander_unavailable"));
            return true;
        }

        player.teleport(chairmanCommander.getLocation());
        sender.sendMessage(TextColors.GREEN + msg("nation_tpchaircommander_success", chairmanCommander.getName(), sovereignEmpire.getChairmanNationName()));
        return true;
    }

    private boolean handleAddDiplomat(CommandSender sender, String username, ResidentManager residentManager) {
        OfflinePlayer targetPlayer = WorldDynamicsEngine.getInstance().getServer().getOfflinePlayer(username);
        if (targetPlayer.getUniqueId() == null) {
            sender.sendMessage(TextColors.RED + msg("player_not_found"));
            return true;
        }

        UUID uuid = targetPlayer.getUniqueId();
        WDEresident resident = residentManager.getResident(uuid);
        if (resident == null) {
            resident = new WDEresident(uuid, username); // Create a new resident if not found
            residentManager.addResident(resident);
        }

        resident.addFlag("diplomat");
        residentManager.saveResidents(); // Save changes
        sender.sendMessage(TextColors.GREEN + msg("nation_diplomat_added", username));
        return true;
    }


    private boolean handleRemoveDiplomat(CommandSender sender, String username, ResidentManager residentManager) {
        OfflinePlayer targetPlayer = WorldDynamicsEngine.getInstance().getServer().getOfflinePlayer(username);
        if (targetPlayer.getUniqueId() == null) {
            sender.sendMessage(TextColors.RED + msg("player_not_found"));
            return true;
        }

        UUID uuid = targetPlayer.getUniqueId();
        WDEresident resident = residentManager.getResident(uuid);
        if (resident == null) {
            sender.sendMessage(TextColors.RED + msg("nation_resident_data_not_found", username));
            return true;
        }

        resident.removeFlag("diplomat");
        residentManager.saveResidents(); // Save changes
        sender.sendMessage(TextColors.GREEN + msg("nation_diplomat_removed", username));
        return true;
    }

    private boolean handleSetTownTax(CommandSender sender, WDEnation nation, String[] args) {
        if (nation == null || args.length < 6) {
            sender.sendMessage(TextColors.RED + msg("nation_usage_settowntax"));
            return true;
        }

        if (!hasNationAuthority(sender, nationManager)) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        String actorNationName = getAuthorizedNationName(sender);
        if (actorNationName == null || !actorNationName.equalsIgnoreCase(nation.getNationName())) {
            sender.sendMessage(TextColors.RED + msg("common_no_authority"));
            return true;
        }

        String townName = args[3];
        TownManager townManager = WorldDynamicsEngine.getInstance().getTownManager();
        WDEtown town = townManager != null ? townManager.getTown(townName) : null;
        if (town == null) {
            sender.sendMessage(TextColors.RED + msg("town_not_found", townName));
            return true;
        }

        if (!nation.getNationName().equalsIgnoreCase(town.getNationName())) {
            sender.sendMessage(TextColors.RED + msg("nation_town_not_subordinate", townName, nation.getNationName()));
            return true;
        }

        String normalizedTaxType = normalizeTownTaxType(args[4]);
        if (normalizedTaxType == null) {
            sender.sendMessage(TextColors.RED + msg("nation_invalid_town_tax_type"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextColors.RED + msg("nation_town_tax_amount_number"));
            return true;
        }

        if (amount < 0D) {
            sender.sendMessage(TextColors.RED + msg("nation_town_tax_amount_non_negative"));
            return true;
        }

        OrgManager orgManager = WorldDynamicsEngine.getInstance().getOrgManager();
        WDEorg sovereignEmpire = orgManager != null ? orgManager.getSovereignEmpireByNation(nation.getNationName()) : null;
        if (orgManager != null
                && sovereignEmpire != null
                && sovereignEmpire.isSovereignEmpire()
                && orgManager.isSovereignSubjectNation(sovereignEmpire, nation.getNationName())) {
            double maxTax = orgManager.getSubjectTownTaxCap(sovereignEmpire, nation.getNationName(), normalizedTaxType);
            if (maxTax != Double.MAX_VALUE && amount > maxTax) {
                sender.sendMessage(TextColors.RED + msg("nation_subject_town_tax_cap_exceeded",
                        formatTownTaxAmount(normalizedTaxType, amount),
                        msg("org_subject_tier_" + orgManager.getSubjectAutonomyTier(sovereignEmpire, nation.getNationName()).name().toLowerCase()),
                        formatTownTaxAmount(normalizedTaxType, maxTax)));
                return true;
            }
        }

        if (townManager == null || !townManager.configureResidentTax(town, normalizedTaxType, amount, true)) {
            sender.sendMessage(TextColors.RED + msg("nation_town_tax_apply_failed", townName));
            return true;
        }

        sender.sendMessage(TextColors.GREEN + msg("nation_town_tax_updated", townName, msg("town_tax_type_" + normalizedTaxType.toLowerCase()), formatTownTaxAmount(normalizedTaxType, amount)));
        return true;
    }

    private boolean hasNationAuthority(CommandSender sender, NationManager nationManager) {
        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getName());
            if (resident == null || !resident.hasTown()) {
                return false;
            }

            Town town = resident.getTown();
            if (town.hasNation()) {
                Nation nation = town.getNation();
                WDEnation wdeNation = nationManager.getNation(nation.getName());

                // Check if player is the Nation Leader in Towny
                if (nation.isKing(resident)) {
                    return true;
                }

                // Check if player is the Government Leader or Army Leader in WDEnation
                String playerName = player.getName();
                if (wdeNation != null) {
                    if (wdeNation.getGovernmentLeader().equalsIgnoreCase(playerName) ||
                            wdeNation.getArmyLeader().equalsIgnoreCase(playerName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private String getAuthorizedNationName(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }

        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getName());
            if (resident == null || !resident.hasTown()) {
                return null;
            }

            Town town = resident.getTown();
            if (!town.hasNation()) {
                return null;
            }

            Nation nation = town.getNation();
            WDEnation wdeNation = nationManager.getNation(nation.getName());
            if (nation.isKing(resident)) {
                return nation.getName();
            }

            String playerName = player.getName();
            if (wdeNation != null && (wdeNation.getGovernmentLeader().equalsIgnoreCase(playerName)
                    || wdeNation.getArmyLeader().equalsIgnoreCase(playerName))) {
                return nation.getName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeNationGovernmentType(String rawValue) {
        String normalized = rawValue.trim().toUpperCase();
        return switch (normalized) {
            case "独裁" -> "DICTATORSHIP";
            case "民主" -> "DEMOCRACY";
            case "贵族议会", "贵族", "议会" -> "NOBLE_COUNCIL";
            default -> normalized;
        };
    }

    private String formatNationGovernmentType(String governmentType) {
        if (governmentType == null || governmentType.isBlank()) {
            return msg("common_none");
        }

        return switch (governmentType.trim().toUpperCase()) {
            case "DICTATORSHIP" -> msg("gov_type_dictatorship");
            case "DEMOCRACY" -> msg("gov_type_democracy");
            case "NOBLE_COUNCIL" -> msg("gov_type_noble_council");
            default -> governmentType;
        };
    }

    private String normalizeTownTaxType(String rawValue) {
        String normalized = rawValue.trim().toUpperCase();
        return switch (normalized) {
            case "FIXED", "定额", "定额税" -> "FIXED";
            case "WEALTH_PERCENTAGE", "PERCENTAGE", "总财产比例", "比例", "比例税" -> "WEALTH_PERCENTAGE";
            default -> null;
        };
    }

    private String formatTownTaxAmount(String taxType, double amount) {
        if ("WEALTH_PERCENTAGE".equalsIgnoreCase(taxType)) {
            return String.format(java.util.Locale.US, "%.4f%%", amount);
        }
        return String.format(java.util.Locale.US, "%.2f", amount);
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

    private String msg(String key, Object... args) {
        return WorldDynamicsEngine.getInstance().formatLocaleMessage(key, args);
    }


}
