package com.thatguycy.worlddynamicsengine.command;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.command.admin.AdminTabCompleter;
import com.thatguycy.worlddynamicsengine.command.system.HelpCommand;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * 根据命令上下文补全国家、城镇、集团、意识形态和背景参数，并过滤受权限限制的入口。
 */
public class MyTabCompleter implements TabCompleter {

    private OrgManager orgManager;
    private ReligionManager religionManager;

    public MyTabCompleter(OrgManager orgManager, ReligionManager religionManager) {
        this.orgManager = orgManager;
        this.religionManager = religionManager;
    }
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> onlinePlayerNames = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());

        if (cmd.getName().equalsIgnoreCase("wde")) {
            String rootArg = args.length > 0 ? args[0].toLowerCase() : "";
            if (args.length > 1 && !CommandPermissions.root(sender, rootArg)) return List.of();
            if (rootArg.equals("admin") && args.length > 1) return AdminTabCompleter.complete(sender, args);
            if (args.length == 1) {
                completions.add("help");
                completions.add("docs");
                if (CommandPermissions.root(sender, "debug")) completions.add("debug");
                if (CommandPermissions.root(sender, "reload")) completions.add("reload");
                if (CommandPermissions.anyAdmin(sender)) completions.add("admin");
                completions.add("background");
                completions.add("menu");
                completions.add("inn");
                completions.add("rally");
                completions.add("war");
                completions.add("town");
                completions.add("nation");
                completions.add("diplomacy");
                completions.add("org"); // Added 'org'
                completions.add("ideology");
                // Add more first-level options as needed
            } else if (rootArg.equals("war")) {
                if (args.length == 2) completions.addAll(Arrays.asList("menu", "declare", "surrender"));
                else if (args.length == 3) completions.addAll(Arrays.asList("nation", "town"));
                else if (args.length == 4) {
                    if ("town".equalsIgnoreCase(args[2])) {
                        completions.addAll(WorldDynamicsEngine.getInstance().getTownManager().getAllTowns().stream()
                                .map(com.thatguycy.worlddynamicsengine.model.WDEtown::getTownName).toList());
                    } else completions.addAll(WorldDynamicsEngine.getInstance().getNationManager().getAllNations().stream()
                            .map(com.thatguycy.worlddynamicsengine.model.WDEnation::getNationName).toList());
                } else if (args.length == 5) {
                    Arrays.stream(com.thatguycy.worlddynamicsengine.model.war.WarReason.values())
                            .filter(r -> r != com.thatguycy.worlddynamicsengine.model.war.WarReason.INDEPENDENCE)
                            .map(r -> r.name().toLowerCase(java.util.Locale.ROOT)).forEach(completions::add);
                }
            } else if (rootArg.equals("help") && args.length == 2) {
                for (int page = 1; page <= HelpCommand.getPageCount(WorldDynamicsEngine.getInstance(), sender); page++) {
                    completions.add(String.valueOf(page));
                }
            } else if (rootArg.equals("inn") && args.length == 2) {
                completions.add("set");
            } else if (rootArg.equals("rally") && args.length == 2) {
                completions.addAll(Arrays.asList("nation", "town"));
            } else if (rootArg.equals("inn") && args.length == 3) {
                completions.addAll(Arrays.asList("10", "25", "50"));
            } else if (rootArg.equals("inn") && args.length == 4) {
                completions.add("default");
                completions.addAll(WorldDynamicsEngine.getInstance().getEconomyBridge().currencies());
            } else if (rootArg.equals("debug") && args.length == 2) {
                completions.addAll(Arrays.asList("balance", "craftengine", "realisticseasons"));
            } else if (rootArg.equals("background") && args.length == 2) {
                completions.addAll(Arrays.asList("menu", "list", "info", "choose", "status"));
            } else if (rootArg.equals("background") && args.length == 3
                    && (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("choose"))) {
                completions.addAll(Arrays.asList("childhood", "adulthood", "儿童时期", "成年时期"));
            } else if (rootArg.equals("background") && args.length == 4
                    && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("choose"))) {
                BackgroundManager backgroundManager = WorldDynamicsEngine.getInstance().getBackgroundManager();
                RoleBackgroundStage stage = RoleBackgroundStage.fromString(args[2]);
                if (backgroundManager != null && stage != null) {
                    completions.addAll(backgroundManager.getBackgroundIds(stage));
                }
            } else if (rootArg.equals("debug") && args.length == 3 && args[1].equalsIgnoreCase("balance")) {
                completions.addAll(onlinePlayerNames);
            } else if (rootArg.equals("debug") && args.length == 4 && args[1].equalsIgnoreCase("balance")) {
                completions.add("default");
                completions.addAll(WorldDynamicsEngine.getInstance().getCurrencyExchangeRates().keySet());
                if (WorldDynamicsEngine.getInstance().getEconomyBridge() != null) {
                    completions.addAll(WorldDynamicsEngine.getInstance().getEconomyBridge().currencies());
                }
            } else if (rootArg.equals("ideology") && args.length == 2) {
                completions.addAll(Arrays.asList("menu", "create", "info", "list", "setplayer", "setnation", "settown", "setmemes", "abdicate", "excommunicate", "treasury", "donate", "settithe"));
            } else if (rootArg.equals("ideology") && args.length == 3) {
                switch (args[1].toLowerCase()) {
                    case "info":
                    case "setmemes":
                    case "abdicate":
                    case "excommunicate":
                    case "treasury":
                    case "donate":
                    case "settithe":
                        completions.addAll(religionManager.getAllReligionNames());
                        break;
                    case "setplayer":
                        completions.add("none");
                        completions.addAll(religionManager.getAllReligionNames());
                        completions.addAll(onlinePlayerNames);
                        break;
                    case "setnation":
                        TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
                        break;
                    case "settown":
                        TownyUniverse.getInstance().getTowns().forEach(town -> completions.add(town.getName()));
                        break;
                }
            } else if (rootArg.equals("ideology") && args.length == 4) {
                if (args[1].equalsIgnoreCase("setnation") || args[1].equalsIgnoreCase("settown") || args[1].equalsIgnoreCase("setplayer")) {
                    completions.add("none");
                    completions.addAll(religionManager.getAllReligionNames());
                } else if (args[1].equalsIgnoreCase("setmemes")) {
                    completions.addAll(religionManager.getValidMemes());
                } else if (args[1].equalsIgnoreCase("abdicate")) {
                    completions.addAll(onlinePlayerNames);
                } else if (args[1].equalsIgnoreCase("excommunicate")) {
                    completions.addAll(Arrays.asList("player", "town", "nation"));
                } else if (args[1].equalsIgnoreCase("settithe")) {
                    completions.addAll(Arrays.asList("fixed", "wealth_percentage"));
                } else if (args[1].equalsIgnoreCase("donate")) {
                    completions.addAll(Arrays.asList("100", "1000"));
                }
            } else if (rootArg.equals("ideology") && args.length == 5 && args[1].equalsIgnoreCase("settithe")) {
                completions.addAll("wealth_percentage".equalsIgnoreCase(args[3])
                        ? Arrays.asList("0", "5", "10", "100") : Arrays.asList("0", "100", "1000"));
            } else if (rootArg.equals("ideology") && args.length >= 5 && args.length <= 7 && args[1].equalsIgnoreCase("setmemes")) {
                completions.addAll(religionManager.getValidMemes());
            } else if (rootArg.equals("ideology") && args.length == 5 && args[1].equalsIgnoreCase("excommunicate")) {
                switch (args[3].toLowerCase()) {
                    case "player":
                        completions.addAll(onlinePlayerNames);
                        break;
                    case "town":
                        TownyUniverse.getInstance().getTowns().forEach(town -> completions.add(town.getName()));
                        break;
                    case "nation":
                        TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
                        break;
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("town")) {
                completions.add("menu");
                TownyUniverse.getInstance().getTowns().forEach(town -> completions.add(town.getName()));
            } else if (args[0].equalsIgnoreCase("town") && args.length == 3) {
                completions.addAll(Arrays.asList("menu", "setgovtype", "setleader", "nominateleader", "voteleader", "setelectiondays", "setconscript", "setconscriptprivilege"));
            } else if (args[0].equalsIgnoreCase("town") && args.length == 4) {
                switch (args[2].toLowerCase()) {
                    case "setgovtype":
                        completions.addAll(WorldDynamicsEngine.getInstance().getValidTownGovernmentTypes());
                        completions.addAll(Arrays.asList("独裁", "共和"));
                        break;
                    case "setleader":
                    case "nominateleader":
                    case "setconscript":
                    case "setconscriptprivilege":
                        completions.addAll(onlinePlayerNames);
                        break;
                    case "voteleader":
                        com.thatguycy.worlddynamicsengine.model.WDEtown wdeTown = WorldDynamicsEngine.getInstance().getTownManager().getTown(args[1]);
                        if (wdeTown != null && "REPUBLIC".equalsIgnoreCase(wdeTown.getGovernmentType()) && !wdeTown.getTownLeaderCandidates().isEmpty()) {
                            completions.addAll(wdeTown.getTownLeaderCandidates());
                        } else {
                            completions.addAll(Arrays.asList("yes", "no"));
                        }
                        break;
                }
            } else if (args[0].equalsIgnoreCase("town") && args.length == 5) {
                switch (args[2].toLowerCase()) {
                    case "setconscript":
                        completions.addAll(Arrays.asList("enabled", "disabled", "启用", "禁用"));
                        break;
                    case "setconscriptprivilege":
                        completions.addAll(Arrays.asList("taxfree", "免税"));
                        break;
                }
            } else if (args[0].equalsIgnoreCase("town") && args.length == 6 && args[2].equalsIgnoreCase("setconscriptprivilege")) {
                completions.addAll(Arrays.asList("enabled", "disabled", "启用", "禁用"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("nation")) {
                completions.add("menu");
                TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
            } else if (args[0].equalsIgnoreCase("nation") && args.length == 3) {
                completions.addAll(Arrays.asList("menu", "setgovtype", "setgovleader", "startelection", "addgovmember", "kickgovmember",
                        "appointarmycommander", "votearmycommander", "enlistarmymember", "dischargearmymember", "tpconscript", "tpchaircommander", "settowntax", "voteleader",
                        "adddiplomat", "removediplomat"));
            } else if (args[0].equalsIgnoreCase("nation") && args.length == 4) {
                switch (args[2].toLowerCase()) {
                    case "setgovtype":
                        completions.addAll(WorldDynamicsEngine.getInstance().getValidGovernmentTypes());
                        completions.addAll(Arrays.asList("独裁", "民主", "贵族议会"));
                        break;
                    case "voteleader":
                        com.thatguycy.worlddynamicsengine.model.WDEnation voteNation = WorldDynamicsEngine.getInstance().getNationManager().getNation(args[1]);
                        if (voteNation != null && "DEMOCRACY".equalsIgnoreCase(voteNation.getGovernmentType()) && !voteNation.getGovernmentLeaderCandidates().isEmpty()) {
                            completions.addAll(voteNation.getGovernmentLeaderCandidates());
                        } else {
                            completions.addAll(Arrays.asList("yes", "no"));
                        }
                        break;
                    case "votearmycommander":
                        com.thatguycy.worlddynamicsengine.model.WDEnation armyVoteNation = WorldDynamicsEngine.getInstance().getNationManager().getNation(args[1]);
                        if (armyVoteNation != null && "DEMOCRACY".equalsIgnoreCase(armyVoteNation.getGovernmentType()) && !armyVoteNation.getArmyCommanderCandidates().isEmpty()) {
                            completions.addAll(armyVoteNation.getArmyCommanderCandidates());
                        } else {
                            completions.addAll(Arrays.asList("yes", "no"));
                        }
                        break;
                    case "setgovleader":
                    case "addgovmember":
                    case "kickgovmember":
                    case "appointarmycommander":
                    case "enlistarmymember":
                    case "dischargearmymember":
                    case "tpconscript":
                    case "adddiplomat":
                    case "removediplomat":
                        completions.addAll(onlinePlayerNames);
                        break;
                    case "settowntax":
                        TownyUniverse.getInstance().getTowns().forEach(town -> completions.add(town.getName()));
                        break;
                }
            } else if (args[0].equalsIgnoreCase("nation") && args.length == 5 && args[2].equalsIgnoreCase("settowntax")) {
                completions.addAll(Arrays.asList("fixed", "wealth_percentage", "定额", "总财产比例"));
            } else if (args[0].equalsIgnoreCase("diplomacy") && args.length == 2) {
                completions.addAll(Arrays.asList("setrelation", "trading", "viewtrading", "relations", "visit"));
            } else if (args[0].equalsIgnoreCase("diplomacy") && args.length == 3) {
                if (args[1].equalsIgnoreCase("setrelation") || args[1].equalsIgnoreCase("trading")) {
                    TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
                } else if (args[1].equalsIgnoreCase("visit")) {
                    TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
                }
            } else if (args[0].equalsIgnoreCase("diplomacy") && args.length == 4) {
                if (args[1].equalsIgnoreCase("setrelation")) {
                    completions.addAll(Arrays.asList("friendly", "neutral", "unfriendly"));
                } else if (args[1].equalsIgnoreCase("trading")) {
                    completions.addAll(Arrays.asList("enabled", "disabled"));
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("org")) {
                completions.addAll(Arrays.asList("menu", "create", "dissolve", "applynation", "votejoin", "reviewjoin", "declarewar", "votewar", "sanction", "deposit", "withdraw", "leave", "grantindependence", "peacetreaty", "reviewtreaty", "promoteautonomy", "demoteautonomy", "setgarrison", "mobilizewar", "replaceleader", "demandtribute", "info"));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("org")) {
                if (args[1].equalsIgnoreCase("peacetreaty") || args[1].equalsIgnoreCase("reviewtreaty")) completions.add("war");
                if (!args[1].equalsIgnoreCase("create")) {
                    try {
                        completions.addAll(orgManager.getAllOrganizationNames());
                    } catch (Exception ignored) {
                    }
                }
            } else if (args.length == 4 && args[0].equalsIgnoreCase("org")) {
                switch (args[1].toLowerCase()) {
                    case "create":
                        completions.addAll(WorldDynamicsEngine.getInstance().getValidOrganizationTypes());
                        completions.addAll(Arrays.asList("贸易联盟", "军事同盟", "主权帝国", "意识形态联盟"));
                        break;
                    case "votejoin":
                    case "reviewjoin":
                        try {
                            orgManager.getAllOrganizations().stream()
                                    .filter(org -> org.getOrganizationName().equalsIgnoreCase(args[2]))
                                    .findFirst()
                                    .ifPresent(org -> completions.addAll(org.getPendingNationApplications()));
                        } catch (Exception ignored) {
                        }
                        break;
                    case "declarewar":
                    case "sanction":
                    case "votewar":
                        TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
                        break;
                    case "grantindependence":
                        try {
                            orgManager.getAllOrganizations().stream()
                                    .filter(org -> org.getOrganizationName().equalsIgnoreCase(args[2]))
                                    .findFirst()
                                    .ifPresent(org -> orgManager.getActiveIndependenceWars(org)
                                            .forEach(war -> completions.add(war.getRebelNationName())));
                        } catch (Exception ignored) {
                        }
                        break;
                    case "peacetreaty":
                    case "reviewtreaty":
                        TownyUniverse.getInstance().getNations().forEach(nation -> completions.add(nation.getName()));
                        break;
                    case "demoteautonomy":
                    case "setgarrison":
                    case "mobilizewar":
                    case "replaceleader":
                    case "demandtribute":
                        try {
                            orgManager.getAllOrganizations().stream()
                                    .filter(org -> org.getOrganizationName().equalsIgnoreCase(args[2]))
                                    .findFirst()
                                    .ifPresent(org -> org.getMemberNationNames().stream()
                                            .filter(memberNation -> memberNation != null && !memberNation.equalsIgnoreCase(org.getChairmanNationName()))
                                            .forEach(completions::add));
                        } catch (Exception ignored) {
                        }
                        break;
                }
            } else if (args.length == 5 && args[0].equalsIgnoreCase("org")) {
                if (args[1].equalsIgnoreCase("declarewar")) {
                    Arrays.stream(com.thatguycy.worlddynamicsengine.model.war.WarReason.values())
                            .filter(r -> orgManager.validWarReason(r, args[3]))
                            .map(r -> r.name().toLowerCase(java.util.Locale.ROOT)).forEach(completions::add);
                } else if (args[1].equalsIgnoreCase("votejoin")) {
                    completions.addAll(Arrays.asList("yes", "no"));
                } else if (args[1].equalsIgnoreCase("reviewjoin")) {
                    completions.addAll(Arrays.asList("accept", "reject"));
                } else if (args[1].equalsIgnoreCase("votewar")) {
                    completions.addAll(Arrays.asList("yes", "no"));
                } else if (args[1].equalsIgnoreCase("peacetreaty")) {
                    completions.addAll(Arrays.asList("demand", "surrender", "劝降", "投降"));
                } else if (args[1].equalsIgnoreCase("reviewtreaty")) {
                    completions.addAll(Arrays.asList("accept", "reject"));
                } else if (args[1].equalsIgnoreCase("sanction")) {
                    completions.addAll(Arrays.asList("enabled", "disabled"));
                } else if (args[1].equalsIgnoreCase("setgarrison") || args[1].equalsIgnoreCase("mobilizewar")) {
                    completions.addAll(Arrays.asList("enabled", "disabled", "启用", "禁用"));
                } else if (args[1].equalsIgnoreCase("replaceleader")) {
                    try {
                        String nationName = args[3];
                        TownyUniverse.getInstance().getResidents().forEach(resident -> {
                            try {
                                if (resident.hasTown() && resident.getTown().hasNation() && resident.getTown().getNation().getName().equalsIgnoreCase(nationName)) {
                                    completions.add(resident.getName());
                                }
                            } catch (Exception ignored) {
                            }
                        });
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (args.length == 1) completions.removeIf(value -> !CommandPermissions.root(sender, value));
        String partial = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return completions.stream().filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(partial)).distinct().toList();
    }

}
