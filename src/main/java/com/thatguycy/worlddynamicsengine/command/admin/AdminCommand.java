package com.thatguycy.worlddynamicsengine.command.admin;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.command.CommandPermissions;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import com.thatguycy.worlddynamicsengine.model.organization.SovereignSubjectState;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.*;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

/**
 * 统一处理管理员模块指令，执行动作权限、参数和确认校验，并记录操作结果。
 */
public final class AdminCommand implements CommandExecutor {
    /**
     * 描述管理员动作的模块、名称和参数格式，供帮助与补全共享。
     */
    public record Entry(String module, String action, String parameters) {
        public String usage() { return "/wde admin " + module + " " + action + " " + parameters; }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("system", "save", ""),
            new Entry("system", "reload", ""),
            new Entry("system", "sync", ""),
            new Entry("system", "module", "<gov|org|army|ideology|background|drops|inn> <enabled|disabled>"),
            new Entry("nation", "info", "<nation>"),
            new Entry("nation", "setgov", "<nation> <DICTATORSHIP|DEMOCRACY|NOBLE_COUNCIL>"),
            new Entry("nation", "setleader", "<nation> <player>"),
            new Entry("nation", "setcommander", "<nation> <player|none>"),
            new Entry("nation", "govmember", "<nation> <player> <add|remove>"),
            new Entry("nation", "armymember", "<nation> <player> <add|remove>"),
            new Entry("nation", "diplomat", "<nation> <player> <enabled|disabled>"),
            new Entry("nation", "election", "<nation> <start|cancel|cancelarmy>"),
            new Entry("nation", "relation", "<nation> <target> <friendly|neutral|unfriendly>"),
            new Entry("nation", "trading", "<nation> <target> <enabled|disabled>"),
            new Entry("town", "info", "<town>"),
            new Entry("town", "setgov", "<town> <DICTATORSHIP|REPUBLIC>"),
            new Entry("town", "setleader", "<town> <player>"),
            new Entry("town", "conscript", "<town> <player> <enabled|disabled>"),
            new Entry("town", "taxfree", "<town> <player> <enabled|disabled>"),
            new Entry("town", "tax", "<town> <fixed|wealth_percentage> <amount>"),
            new Entry("town", "election", "<town> <start|cancel> [candidate]"),
            new Entry("town", "electiondays", "<town> <days>"),
            new Entry("town", "transfer", "<town> <nation> confirm"),
            new Entry("org", "info", "<org>"),
            new Entry("org", "create", "<name> <type> <chairmanNation>"),
            new Entry("org", "dissolve", "<org> confirm"),
            new Entry("org", "addnation", "<org> <nation>"),
            new Entry("org", "removenation", "<org> <nation> confirm"),
            new Entry("org", "chairman", "<org> <nation> confirm"),
            new Entry("org", "sanction", "<org> <nation> <enabled|disabled>"),
            new Entry("org", "subject", "<org> <nation> <tier|suppression|garrison|duty> <value>"),
            new Entry("org", "independence", "<org> <nation> confirm"),
            new Entry("war", "start", "<attackerNation> <defenderNation> <reason> confirm"),
            new Entry("war", "end", "<nation> <opponent> confirm"),
            new Entry("war", "score", "<nation> <opponent> <attackerScore> <defenderScore>"),
            new Entry("war", "independencestart", "<org> <subject> confirm"),
            new Entry("war", "independenceend", "<org> <subject> <SUCCESS|FAILURE|STALEMATE> confirm"),
            new Entry("war", "independencescore", "<org> <subject> <rebelScore> <empireScore>"),
            new Entry("war", "truce", "<nation> <opponent> <hours|0>"),
            new Entry("war", "reviewtreaty", "<org> <receiverNation> <opponent> <accept|reject> confirm"),
            new Entry("ideology", "create", "<name> <founderPlayer>"),
            new Entry("ideology", "delete", "<ideology> confirm"),
            new Entry("ideology", "leader", "<ideology> <player>"),
            new Entry("ideology", "assign", "<player|town|nation> <target> <ideology|none>"),
            new Entry("ideology", "firstfree", "<player|town|nation> <target> <enabled|disabled>"),
            new Entry("ideology", "memes", "<ideology> <meme1> [meme2] [meme3] [meme4]"),
            new Entry("ideology", "doctrine", "<ideology> <doctrine> <attitude>"),
            new Entry("background", "info", "<player>"),
            new Entry("background", "set", "<player> <childhood|adulthood> <id> confirm"),
            new Entry("background", "reset", "<player> <childhood|adulthood|all> confirm"),
            new Entry("economy", "orgbalance", "<org> <amount>")
    );

    private final WorldDynamicsEngine plugin;
    private final AdminAuditLogger audit;

    public AdminCommand(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.audit = new AdminAuditLogger(plugin.getLogger());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandPermissions.anyAdmin(sender)) {
            send(sender, "command_no_permission");
            audit.record(sender, args, "DENIED");
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            try {
                if (args.length > 3) throw new IllegalArgumentException();
                int page = args.length == 3 ? Integer.parseInt(args[2]) : 1;
                help(sender, page);
            } catch (IllegalArgumentException ex) { send(sender, "admin_invalid_argument"); }
            return true;
        }
        String module = args[1].toLowerCase(Locale.ROOT);
        if (!CommandPermissions.admin(sender, module)) {
            send(sender, "command_no_permission");
            audit.record(sender, args, "DENIED");
            return true;
        }
        try {
            require(args.length >= 3, "admin_invalid_argument");
            String action = args[2].toLowerCase(Locale.ROOT);
            require(ENTRIES.stream().anyMatch(e -> e.module().equals(module) && e.action().equals(action)), "admin_invalid_argument");
            audit.record(sender, args, "REQUESTED");
            switch (module) {
                case "system" -> system(action, args);
                case "nation" -> nation(sender, action, args);
                case "town" -> town(sender, action, args);
                case "org" -> organization(sender, action, args);
                case "war" -> war(action, args);
                case "ideology" -> ideology(action, args);
                case "background" -> background(sender, action, args);
                case "economy" -> {
                    size(args, 5);
                    plugin.getOrgManager().setBalance(org(args[3]), number(args[4], Double.MAX_VALUE));
                }
                default -> throw new AdminFailure("admin_invalid_argument");
            }
            if (!action.equals("info")) send(sender, "admin_success");
            audit.record(sender, args, "SUCCESS");
        } catch (AdminFailure ex) {
            send(sender, ex.getMessage());
            audit.record(sender, args, "REJECTED " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            send(sender, "admin_invalid_argument");
            audit.record(sender, args, "INVALID_ARGUMENT");
        } catch (Exception ex) {
            send(sender, "admin_failed");
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Admin operation failed", ex);
            audit.record(sender, args, "FAILED");
        }
        return true;
    }

    private void help(CommandSender sender, int page) {
        List<Entry> visible = ENTRIES.stream().filter(e -> CommandPermissions.admin(sender, e.module())).toList();
        int pages = Math.max(1, (visible.size() + 7) / 8);
        require(page >= 1 && page <= pages, "admin_invalid_argument");
        send(sender, "admin_help_header", page, pages);
        visible.stream().skip((page - 1L) * 8).limit(8).forEach(e -> sender.sendMessage(ChatColor.YELLOW + e.usage()));
        if (page < pages) sender.sendMessage(ChatColor.AQUA + "/wde admin help " + (page + 1));
    }

    private void system(String action, String[] a) {
        if (action.equals("module")) {
            size(a, 5);
            boolean enabled = enabled(a[4]);
            String file;
            String key;
            switch (a[3].toLowerCase(Locale.ROOT)) {
                case "gov", "org", "army" -> { file = "config.yml"; key = a[3].toLowerCase(Locale.ROOT) + "Enabled"; }
                case "ideology" -> { file = "ideology.yml"; key = "religionEnabled"; }
                case "background" -> { file = "backgrounds.yml"; key = "enabled"; }
                case "drops" -> { file = "drops.yml"; key = "playerDrops.enabled"; }
                case "inn" -> { file = "inn.yml"; key = "enabled"; }
                default -> throw new AdminFailure("admin_invalid_argument");
            }
            if (file.equals("config.yml")) {
                plugin.reloadConfig();
                plugin.getConfig().set(key, enabled);
                plugin.saveConfig();
            } else {
                var target = new java.io.File(plugin.getDataFolder(), file);
                var config = new org.bukkit.configuration.file.YamlConfiguration();
                config.options().parseComments(true);
                try {
                    config.load(target);
                    config.set(key, enabled);
                    config.save(target);
                } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
                    throw new AdminFailure("admin_failed");
                }
            }
            plugin.reloadPluginState();
            return;
        }
        size(a, 3);
        switch (action) {
            case "save" -> saveAll();
            case "reload" -> plugin.reloadPluginState();
            case "sync" -> {
                plugin.getTownManager().importExistingTownyTowns();
                plugin.getNationManager().importExistingTownyNations();
                plugin.getResidentManager().syncWithTowny();
                saveAll();
            }
            default -> throw new AdminFailure("admin_invalid_argument");
        }
    }

    private void nation(CommandSender sender, String action, String[] a) {
        require(a.length >= 4, "admin_invalid_argument");
        WDEnation n = nation(a[3]);
        switch (action) {
            case "info" -> {
                size(a, 4);
                send(sender, "admin_nation_info", n.getNationName(), n.getGovernmentType(), n.getGovernmentLeader(), n.getArmyCommander(), n.getReligionName());
                return;
            }
            case "setgov" -> {
                size(a, 5);
                String type = a[4].toUpperCase(Locale.ROOT);
                require(List.of("DICTATORSHIP", "DEMOCRACY", "NOBLE_COUNCIL").contains(type), "admin_invalid_argument");
                n.setGovernmentType(type); cancelNationElection(n); cancelArmyElection(n);
                n.setLastElectionAt(System.currentTimeMillis());
                n.setElectionIntervalDays(plugin.getNationManager().getDefaultNationElectionIntervalDays());
            }
            case "setleader" -> {
                size(a, 5);
                require(plugin.getNationManager().forceNationLeader(n, resident(a[4]).getName()), "admin_invalid_candidate");
                cancelNationElection(n);
                n.setLastElectionAt(System.currentTimeMillis());
            }
            case "setcommander" -> {
                size(a, 5);
                String name = none(a[4]) ? "None" : nationalResident(n, a[4]).getName();
                n.setArmyCommander(name); cancelArmyElection(n);
            }
            case "govmember", "armymember" -> {
                size(a, 6);
                boolean add = choice(a[5], "add", "remove");
                Resident target = resident(a[4]);
                if (add) nationalResident(n, target.getName());
                List<String> list = action.equals("govmember") ? n.getGovernmentMembers() : n.getArmyMembers();
                list.removeIf(name -> name.equalsIgnoreCase(target.getName()));
                if (add) list.add(target.getName());
            }
            case "diplomat" -> {
                size(a, 6);
                Resident target = nationalResident(n, a[4]);
                WDEresident data = plugin.getResidentManager().getOrCreateResident(target.getUUID(), target.getName());
                if (enabled(a[5])) data.addFlag("diplomat"); else data.removeFlag("diplomat");
                plugin.getResidentManager().saveResidents();
            }
            case "election" -> {
                size(a, 5);
                switch (a[4].toLowerCase(Locale.ROOT)) {
                    case "start" -> {
                        require(n.getGovernmentType().equalsIgnoreCase("DEMOCRACY"), "admin_invalid_argument");
                        require(plugin.getNationManager().startEarlyDemocracyLeadershipElection(n, null), "admin_operation_blocked");
                    }
                    case "cancel" -> { cancelNationElection(n); n.setLastElectionAt(System.currentTimeMillis()); }
                    case "cancelarmy" -> cancelArmyElection(n);
                    default -> throw new AdminFailure("admin_invalid_argument");
                }
            }
            case "relation", "trading" -> {
                size(a, 6);
                WDEnation target = nation(a[4]);
                require(n != target, "admin_invalid_argument");
                if (action.equals("relation")) {
                    String relation = a[5].toLowerCase(Locale.ROOT);
                    require(List.of("friendly", "neutral", "unfriendly").contains(relation), "admin_invalid_argument");
                    n.setDiplomaticRelation(target.getNationName(), relation);
                } else n.setTradingStatus(target.getNationName(), enabled(a[5]));
            }
            default -> throw new AdminFailure("admin_invalid_argument");
        }
        plugin.getNationManager().saveNations();
    }

    private void town(CommandSender sender, String action, String[] a) {
        require(a.length >= 4, "admin_invalid_argument");
        WDEtown t = town(a[3]);
        switch (action) {
            case "info" -> {
                size(a, 4); send(sender, "admin_town_info", t.getTownName(), t.getGovernmentType(), t.getTownLeader(), t.getNationName(), t.getReligionName());
                return;
            }
            case "setgov" -> {
                size(a, 5); String type = a[4].toUpperCase(Locale.ROOT);
                require(List.of("DICTATORSHIP", "REPUBLIC").contains(type), "admin_invalid_argument");
                t.setGovernmentType(type); cancelTownElection(t); t.setLastElectionAt(System.currentTimeMillis());
                t.setElectionIntervalDays(plugin.getDefaultTownElectionIntervalDays());
            }
            case "setleader" -> {
                size(a, 5); Resident r = townResident(t, a[4]);
                String previous = t.getTownLeader();
                t.setTownLeader(r.getName());
                if (!plugin.getTownManager().applyTownLeaderToTowny(t)) {
                    t.setTownLeader(previous); throw new AdminFailure("admin_invalid_candidate");
                }
                cancelTownElection(t); t.setLastElectionAt(System.currentTimeMillis());
            }
            case "conscript", "taxfree" -> {
                size(a, 6);
                boolean enabled = enabled(a[5]);
                Resident r = enabled ? townResident(t, a[4]) : resident(a[4]);
                if (action.equals("conscript")) {
                    if (enabled) t.setConscript(r.getName(), t.isConscriptTaxExempt(r.getName())); else t.removeConscript(r.getName());
                } else {
                    require(t.isConscript(r.getName()), "admin_invalid_candidate");
                    t.setConscriptTaxExempt(r.getName(), enabled);
                }
            }
            case "tax" -> {
                size(a, 6); String type = a[4].toLowerCase(Locale.ROOT);
                require(List.of("fixed", "wealth_percentage").contains(type), "admin_invalid_argument");
                require(plugin.getTownManager().configureResidentTax(t, type, number(a[5], type.equals("fixed") ? Double.MAX_VALUE : 100), true), "admin_operation_blocked");
            }
            case "electiondays" -> { size(a, 5); t.setElectionIntervalDays(integer(a[4], 1, 36500)); }
            case "election" -> {
                require(a.length >= 5, "admin_invalid_argument");
                if (a[4].equalsIgnoreCase("cancel")) {
                    size(a, 5); cancelTownElection(t); t.setLastElectionAt(System.currentTimeMillis());
                } else {
                    size(a, 6); require(a[4].equalsIgnoreCase("start") && t.getGovernmentType().equalsIgnoreCase("REPUBLIC"), "admin_invalid_argument");
                    plugin.getTownManager().startTownElection(t, townResident(t, a[5]).getName());
                }
            }
            case "transfer" -> {
                size(a, 6); confirm(a[5]);
                WDEnation destination = nation(a[4]);
                require(!plugin.getOrgManager().isNationInActiveWar(t.getNationName())
                        && !plugin.getOrgManager().isNationInActiveWar(destination.getNationName()), "admin_operation_blocked");
                require(plugin.getTownManager().transferTownToNation(t.getTownName(), destination.getNationName()), "admin_operation_blocked");
            }
            default -> throw new AdminFailure("admin_invalid_argument");
        }
        plugin.getTownManager().saveTowns();
    }

    private void organization(CommandSender sender, String action, String[] a) {
        var manager = plugin.getOrgManager();
        if (action.equals("create")) {
            size(a, 6); validName(a[3]);
            require(!manager.organizationExists(a[3]), "admin_operation_blocked");
            String type = a[4].toUpperCase(Locale.ROOT);
            require(List.of("TRADE_ALLIANCE", "MILITARY_ALLIANCE", "SOVEREIGN_EMPIRE", "IDEOLOGICAL_ALLIANCE").contains(type), "admin_invalid_argument");
            WDEnation chair = nation(a[5]);
            var towny = plugin.getNationManager().getTownyNation(chair.getNationName());
            require(towny != null && towny.getKing() != null, "admin_invalid_candidate");
            require(!type.equals("SOVEREIGN_EMPIRE") || manager.getSovereignEmpireByNation(chair.getNationName()) == null, "admin_operation_blocked");
            WDEorg created = new WDEorg(a[3], towny.getKing().getUUID(), chair.getNationName(), "None", type);
            if (type.equals("IDEOLOGICAL_ALLIANCE")) {
                require(!none(chair.getReligionName()), "admin_operation_blocked");
                created.setSetting("allianceIdeology", chair.getReligionName());
            }
            require(manager.addOrganization(created), "admin_operation_blocked");
            return;
        }
        require(a.length >= 4, "admin_invalid_argument");
        WDEorg o = org(a[3]);
        switch (action) {
            case "info" -> {
                size(a, 4); send(sender, "admin_org_info", o.getOrganizationName(), o.getOrganizationType(), o.getChairmanNationName(), String.join(", ", o.getMemberNationNames()), o.getBalance()); return;
            }
            case "dissolve" -> {
                size(a, 5); confirm(a[4]);
                require(o.getMemberNationNames().stream().noneMatch(manager::isNationInActiveWar), "admin_operation_blocked");
                manager.removeOrganization(o);
            }
            case "addnation" -> {
                size(a, 5); WDEnation n = nation(a[4]);
                require(!manager.isNationInActiveWar(n.getNationName()), "admin_operation_blocked");
                require(!o.isSovereignEmpire() || manager.getSovereignEmpireByNation(n.getNationName()) == null, "admin_operation_blocked");
                require(manager.canNationJoinOrganization(o, n.getNationName(), plugin.getNationManager())
                        && manager.addMemberNation(o, n.getNationName()), "admin_operation_blocked");
            }
            case "removenation", "independence", "chairman" -> {
                size(a, 6); confirm(a[5]); WDEnation n = nation(a[4]);
                require(o.hasMemberNation(n.getNationName()), "admin_not_found");
                if (action.equals("chairman")) require(manager.adminSetChairman(o, n.getNationName()), "admin_operation_blocked");
                else {
                    require(!o.getChairmanNationName().equalsIgnoreCase(n.getNationName())
                            && !manager.isNationInActiveWar(n.getNationName()), "admin_operation_blocked");
                    if (action.equals("independence")) require(o.isSovereignEmpire(), "admin_invalid_argument");
                    manager.removeMemberNationAndAdjust(o, n.getNationName());
                }
            }
            case "sanction" -> {
                size(a, 6); require(manager.isTradeAlliance(o), "admin_invalid_argument");
                manager.setTradeSanction(o, nation(a[4]).getNationName(), enabled(a[5]));
            }
            case "subject" -> {
                size(a, 7); String field = a[5].toLowerCase(Locale.ROOT);
                String value = a[6];
                switch (field) {
                    case "tier" -> value = SovereignSubjectState.AutonomyTier.valueOf(value.toUpperCase(Locale.ROOT)).name();
                    case "suppression" -> value = String.valueOf(integer(value, 0, 100));
                    case "garrison", "duty" -> value = String.valueOf(enabled(value));
                    default -> throw new AdminFailure("admin_invalid_argument");
                }
                require(manager.adminSetSubject(o, nation(a[4]).getNationName(), field, value), "admin_operation_blocked");
            }
            default -> throw new AdminFailure("admin_invalid_argument");
        }
        manager.saveOrganizations();
    }

    private void war(String action, String[] a) {
        var manager = plugin.getOrgManager();
        switch (action) {
            case "start", "end" -> {
                size(a, action.equals("start") ? 7 : 6); confirm(a[a.length - 1]);
                String first = nation(a[3]).getNationName(), second = nation(a[4]).getNationName();
                require(!first.equalsIgnoreCase(second), "admin_invalid_argument");
                if (action.equals("end")) require(manager.adminEndMilitaryWar(first, second), "admin_not_found");
                else {
                    require(!manager.hasActiveWarBetween(first, second)
                            && !plugin.getNationManager().hasActiveWarTruce(first, second), "admin_operation_blocked");
                    var reason = com.thatguycy.worlddynamicsengine.model.war.WarReason.parse(a[5]);
                    require(manager.validWarReason(reason, second), "admin_invalid_argument");
                    require(manager.handleMilitaryAllianceWar(first, second, plugin.getNationManager(), reason)
                            .getHostilePairsApplied() > 0, "admin_operation_blocked");
                    manager.saveOrganizations();
                }
            }
            case "score" -> {
                size(a, 7); require(manager.adminSetMilitaryScores(nation(a[3]).getNationName(), nation(a[4]).getNationName(),
                        number(a[5], Double.MAX_VALUE), number(a[6], Double.MAX_VALUE)), "admin_not_found");
            }
            case "truce" -> {
                size(a, 6); WDEnation first = nation(a[3]), second = nation(a[4]);
                require(first != second, "admin_invalid_argument");
                double hours = number(a[5], 876000);
                plugin.getNationManager().setWarTruce(first.getNationName(), second.getNationName(),
                        hours == 0 ? 0 : System.currentTimeMillis() + (long) (hours * 3600000));
            }
            case "independencestart" -> {
                size(a, 6); confirm(a[5]); WDEorg o = org(a[3]); String subject = nation(a[4]).getNationName();
                require(manager.isSovereignSubjectNation(o, subject) && manager.startIndependenceWar(o, subject), "admin_operation_blocked");
            }
            case "independenceend" -> {
                size(a, 7); confirm(a[6]);
                require(manager.adminResolveIndependence(org(a[3]), nation(a[4]).getNationName(),
                        OrgManager.IndependenceWarOutcome.valueOf(a[5].toUpperCase(Locale.ROOT))), "admin_not_found");
            }
            case "independencescore" -> {
                size(a, 7);
                require(manager.adminSetIndependenceScores(org(a[3]), nation(a[4]).getNationName(),
                        number(a[5], Double.MAX_VALUE), number(a[6], Double.MAX_VALUE)), "admin_not_found");
            }
            case "reviewtreaty" -> {
                size(a, 8); confirm(a[7]);
                var result = manager.reviewPeaceTreaty(org(a[3]), nation(a[4]).getNationName(), nation(a[5]).getNationName(),
                        choice(a[6], "accept", "reject"), plugin.getNationManager());
                require(result.getStatus() != OrgManager.PeaceTreatyReviewResult.Status.INVALID, "admin_operation_blocked");
            }
            default -> throw new AdminFailure("admin_invalid_argument");
        }
    }

    private void ideology(String action, String[] a) {
        var manager = plugin.getReligionManager();
        switch (action) {
            case "create" -> {
                size(a, 5); validName(a[3]); Resident founder = resident(a[4]);
                require(!manager.religionExists(a[3]), "admin_operation_blocked");
                require(!manager.isReligionLeader(founder.getUUID()), "religion_leader_cannot_create");
                manager.addReligion(new WDEreligion(a[3], founder.getUUID(), founder.getName(), System.currentTimeMillis()));
            }
            case "delete" -> {
                size(a, 5); confirm(a[4]); WDEreligion r = religion(a[3]);
                String name = r.getReligionName();
                require(plugin.getResidentManager().getAllResidents().stream().noneMatch(p -> name.equalsIgnoreCase(p.getReligionName()))
                        && plugin.getTownManager().getAllTowns().stream().noneMatch(t -> name.equalsIgnoreCase(t.getReligionName()))
                        && plugin.getNationManager().getAllNations().stream().noneMatch(n -> name.equalsIgnoreCase(n.getReligionName()))
                        && plugin.getOrgManager().getAllOrganizations().stream().noneMatch(o -> name.equalsIgnoreCase(plugin.getOrgManager().getIdeologicalAllianceIdeology(o))), "admin_in_use");
                manager.removeReligion(r);
            }
            case "leader" -> {
                size(a, 5); WDEreligion r = religion(a[3]); Resident player = resident(a[4]);
                r.setPontiff(player.getUUID(), player.getName()); manager.saveReligion(r);
            }
            case "assign", "firstfree" -> {
                size(a, 6); String targetType = a[3].toLowerCase(Locale.ROOT);
                require(List.of("player", "town", "nation").contains(targetType), "admin_invalid_argument");
                String name = action.equals("assign") ? (none(a[5]) ? "None" : religion(a[5]).getReligionName()) : null;
                boolean chosen = action.equals("firstfree") ? !enabled(a[5]) : !none(name);
                switch (targetType) {
                    case "player" -> {
                        WDEresident r = playerData(a[4]);
                        if (name != null) r.setReligionName(name);
                        if (!action.equals("assign") || chosen) r.setReligionChosen(chosen);
                        plugin.getResidentManager().saveResidents();
                    }
                    case "town" -> {
                        WDEtown t = town(a[4]);
                        if (name != null) t.setReligionName(name);
                        if (!action.equals("assign") || chosen) t.setReligionChosen(chosen);
                        plugin.getTownManager().saveTowns();
                    }
                    case "nation" -> {
                        WDEnation n = nation(a[4]);
                        if (name != null) {
                            require(plugin.getOrgManager().getOrganizationsByNation(n.getNationName()).stream()
                                    .filter(plugin.getOrgManager()::isIdeologicalAlliance)
                                    .allMatch(o -> name.equalsIgnoreCase(plugin.getOrgManager().getIdeologicalAllianceIdeology(o))), "admin_in_use");
                            n.setReligionName(name);
                        }
                        if (!action.equals("assign") || chosen) n.setReligionChosen(chosen);
                        plugin.getNationManager().saveNations();
                    }
                    default -> throw new AdminFailure("admin_invalid_argument");
                }
            }
            case "memes" -> {
                require(a.length >= 5 && a.length <= 8, "admin_invalid_argument"); WDEreligion r = religion(a[3]);
                require(!manager.isMemeModificationBlocked(r), "admin_operation_blocked");
                Set<String> selected = new LinkedHashSet<>();
                for (int i = 4; i < a.length; i++) {
                    require(manager.isValidMeme(a[i]), "admin_invalid_argument");
                    String meme = manager.normalizeMeme(a[i]);
                    require(manager.findExclusiveMemeConflict(meme, selected) == null, "admin_operation_blocked");
                    selected.add(meme);
                }
                r.setMemes(selected); manager.markMemeModified(r); manager.saveReligion(r);
            }
            case "doctrine" -> {
                size(a, 6); WDEreligion r = religion(a[3]);
                var doctrine = manager.getDoctrine(a[4]);
                require(doctrine != null, "admin_not_found");
                require(!manager.isDoctrineModificationBlocked(r, doctrine.getId()), "admin_operation_blocked");
                String attitude = doctrine.getAttitudes().keySet().stream()
                        .filter(id -> id.equalsIgnoreCase(a[5]) || id.equalsIgnoreCase(doctrine.getId() + "/" + a[5]))
                        .findFirst().orElse(null);
                require(attitude != null && manager.canUseDoctrineAttitude(r, doctrine.getId(), attitude), "admin_operation_blocked");
                manager.setDoctrineAttitude(r, doctrine.getId(), attitude);
                manager.markDoctrineModified(r, doctrine.getId());
                manager.saveReligion(r);
            }
            default -> throw new AdminFailure("admin_invalid_argument");
        }
    }

    private void background(CommandSender sender, String action, String[] a) {
        require(a.length >= 4, "admin_invalid_argument"); WDEresident r = playerData(a[3]);
        if (action.equals("info")) {
            size(a, 4); send(sender, "admin_background_info", r.getUsername(), r.getChildhoodBackgroundId(), r.getAdulthoodBackgroundId()); return;
        }
        if (action.equals("reset")) {
            size(a, 6); confirm(a[5]);
            switch (a[4].toLowerCase(Locale.ROOT)) {
                case "childhood", "all" -> { r.setChildhoodBackgroundId(""); r.setAdulthoodBackgroundId(""); }
                case "adulthood" -> r.setAdulthoodBackgroundId("");
                default -> throw new AdminFailure("admin_invalid_argument");
            }
        } else {
            size(a, 7); confirm(a[6]);
            RoleBackgroundStage stage = RoleBackgroundStage.fromString(a[4]);
            require(stage != null && plugin.getBackgroundManager().getDefinition(stage, a[5]) != null, "admin_not_found");
            if (stage == RoleBackgroundStage.CHILDHOOD) r.setChildhoodBackgroundId(a[5]);
            else {
                require(r.hasSelectedChildhoodBackground(), "admin_operation_blocked");
                r.setAdulthoodBackgroundId(a[5]);
            }
            // Administrative corrections do not award skills; preserve prior award history.
            r.markBackgroundBonusApplied(a[5]);
        }
        plugin.getResidentManager().saveResidents();
    }

    private void saveAll() {
        plugin.getTownManager().saveTowns(); plugin.getNationManager().saveNations();
        plugin.getResidentManager().saveResidents(); plugin.getOrgManager().saveOrganizations();
        plugin.getReligionManager().saveReligions();
    }

    private WDEnation nation(String name) {
        WDEnation n = plugin.getNationManager().getAllNations().stream().filter(v -> v.getNationName().equalsIgnoreCase(name)).findFirst().orElse(null);
        require(n != null && plugin.getNationManager().getTownyNation(n.getNationName()) != null, "admin_not_found"); return n;
    }
    private WDEtown town(String name) {
        WDEtown t = plugin.getTownManager().getAllTowns().stream().filter(v -> v.getTownName().equalsIgnoreCase(name)).findFirst().orElse(null);
        require(t != null, "admin_not_found"); return t;
    }
    private WDEorg org(String name) {
        WDEorg o = plugin.getOrgManager().getAllOrganizations().stream().filter(v -> v.getOrganizationName().equalsIgnoreCase(name)).findFirst().orElse(null);
        require(o != null, "admin_not_found"); return o;
    }
    private WDEreligion religion(String name) {
        WDEreligion r = plugin.getReligionManager().getReligion(name);
        require(r != null, "admin_not_found"); return r;
    }
    private Resident resident(String name) {
        Resident r = TownyUniverse.getInstance().getResidents().stream()
                .filter(v -> v.getName().equalsIgnoreCase(name) || v.getUUID().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
        require(r != null, "admin_not_found"); return r;
    }
    private WDEresident playerData(String name) {
        WDEresident data = plugin.getResidentManager().getAllResidents().stream()
                .filter(v -> v.getUsername().equalsIgnoreCase(name) || v.getUserUUID().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (data != null) return data;
        Resident r = resident(name);
        return plugin.getResidentManager().getOrCreateResident(r.getUUID(), r.getName());
    }
    private Resident nationalResident(WDEnation n, String name) {
        Resident r = resident(name);
        require(plugin.getNationManager().isNationResidentCandidate(n, r.getName()), "admin_invalid_candidate"); return r;
    }
    private Resident townResident(WDEtown t, String name) {
        Resident r = resident(name);
        require(plugin.getTownManager().isTownResident(t.getTownName(), r.getUUID()), "admin_invalid_candidate"); return r;
    }
    private void cancelNationElection(WDEnation n) {
        n.setPendingGovernmentLeader("None"); n.getGovernmentLeaderVotes().clear();
        n.getGovernmentLeaderCandidates().clear(); n.getGovernmentLeaderBallots().clear(); n.setElectionEndAt(0);
    }
    private void cancelArmyElection(WDEnation n) {
        n.setPendingArmyCommander("None"); n.getArmyCommanderVotes().clear();
        n.getArmyCommanderCandidates().clear(); n.getArmyCommanderBallots().clear(); n.setArmyElectionEndAt(0);
    }
    private void cancelTownElection(WDEtown t) {
        t.setPendingTownLeader("None"); t.getTownLeaderVotes().clear();
        t.getTownLeaderCandidates().clear(); t.getTownLeaderBallots().clear(); t.setElectionEndAt(0);
    }
    private boolean none(String value) { return value == null || value.equalsIgnoreCase("none"); }
    private boolean enabled(String value) {
        if (value.equalsIgnoreCase("enabled") || value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("disabled") || value.equalsIgnoreCase("false")) return false;
        throw new AdminFailure("admin_invalid_argument");
    }
    private boolean choice(String value, String yes, String no) {
        require(value.equalsIgnoreCase(yes) || value.equalsIgnoreCase(no), "admin_invalid_argument");
        return value.equalsIgnoreCase(yes);
    }
    private double number(String value, double max) {
        double n = Double.parseDouble(value);
        require(Double.isFinite(n) && n >= 0 && n <= max, "admin_invalid_argument"); return n;
    }
    private int integer(String value, int min, int max) {
        int n = Integer.parseInt(value); require(n >= min && n <= max, "admin_invalid_argument"); return n;
    }
    private void validName(String name) { require(name.matches("[\\p{L}\\p{N}_-]{2,24}") && !none(name), "admin_invalid_argument"); }
    private void size(String[] a, int length) { require(a.length == length, "admin_invalid_argument"); }
    private void confirm(String value) { require(value.equalsIgnoreCase("confirm"), "admin_confirmation_required"); }
    private void require(boolean condition, String key) { if (!condition) throw new AdminFailure(key); }
    private void send(CommandSender sender, String key, Object... values) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.formatLocaleMessage(key, values)));
    }
    /**
     * 在管理员命令内部携带可展示的校验失败原因，不把业务拒绝视为服务端故障。
     */
    private static final class AdminFailure extends RuntimeException {
        private AdminFailure(String key) { super(key); }
    }
}
