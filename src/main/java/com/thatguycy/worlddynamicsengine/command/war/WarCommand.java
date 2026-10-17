package com.thatguycy.worlddynamicsengine.command.war;

import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 提供统一战争入口，处理理由校验、国家或城镇宣战，并让军事联盟成员遵守投票流程。
 */
public final class WarCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    public WarCommand(WorldDynamicsEngine plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { send(sender, "common_only_players"); return true; }
        if (!player.hasPermission("wde.player")) { send(sender, "command_no_permission"); return true; }
        if (args.length >= 2 && "focus".equalsIgnoreCase(args[1])) {
            return handleFocus(player, args);
        }
        if (args.length == 1 || args.length == 2 && "menu".equalsIgnoreCase(args[1])) {
            plugin.getPlayerMenuManager().openWarMenu(player);
            return true;
        }
        var town = TownyAPI.getInstance().getTown(player);
        var nation = town == null ? null : town.getNationOrNull();
        if (args.length == 4 && "surrender".equalsIgnoreCase(args[1]) && "town".equalsIgnoreCase(args[2])) {
            var target = TownyAPI.getInstance().getTown(args[3]);
            if (town == null || town.getMayor() == null || !town.getMayor().getUUID().equals(player.getUniqueId())) {
                send(player, "common_no_authority"); return true;
            }
            send(player, plugin.getOrgManager().surrenderTownWar(town, target) ? "war_town_surrendered" : "war_declaration_blocked");
            return true;
        }
        if (args.length != 5 || !"declare".equalsIgnoreCase(args[1])) {
            send(player, "war_usage"); return true;
        }
        WarReason reason = WarReason.parse(args[4]);
        if (reason == null) { send(player, "war_reason_invalid"); return true; }
        if ("town".equalsIgnoreCase(args[2])) {
            if (town == null || town.getMayor() == null || !town.getMayor().getUUID().equals(player.getUniqueId())) {
                send(player, "common_no_authority"); return true;
            }
            if (reason == WarReason.INDEPENDENCE) {
                var result = plugin.getOrgManager().declareTownIndependence(town, args[3], plugin.getNationManager());
                if (result.isSuccess()) send(player, "war_declared", args[3], plugin.getLocaleMessage(reason.messageKey()));
                else send(player, result.getFailureMessageKey(), result.getFailureMessageArgs());
                return true;
            }
            var targetTown = TownyAPI.getInstance().getTown(args[3]);
            if (targetTown == null) {
                send(player, "town_not_found", args[3]); return true;
            }
            var result = plugin.getOrgManager().startTownWarDetailed(town, targetTown, reason);
            if (result.isStarted()) {
                send(player, "war_declared", args[3], plugin.getLocaleMessage(reason.messageKey()));
            } else {
                send(player, result.getFailureMessageKey(), result.getFailureMessageArgs());
            }
            return true;
        }
        if (!"nation".equalsIgnoreCase(args[2]) || nation == null) { send(player, "war_usage"); return true; }
        var data = plugin.getNationManager().getNation(nation.getName());
        boolean king = nation.getKing() != null && nation.getKing().getUUID().equals(player.getUniqueId());
        if (data == null || !king && !player.getName().equalsIgnoreCase(data.getGovernmentLeader())
                && !player.getName().equalsIgnoreCase(data.getArmyCommander())) {
            send(player, "common_no_authority"); return true;
        }
        String target = args[3];
        var group = plugin.getOrgManager().getOrganization(target);
        if (reason == WarReason.DESTROY_THREAT && group != null
                && (group.isSovereignEmpire() || plugin.getOrgManager().isMilitaryAlliance(group))) {
            target = group.getChairmanNationName();
        }
        if (nation.getName().equalsIgnoreCase(target)) {
            send(player, "war_reason_invalid"); return true;
        }
        if (plugin.getNationManager().getNation(target) == null) {
            send(player, "nation_not_found_with_name", target); return true;
        }
        String reasonError = plugin.getOrgManager().warReasonFailure(reason, nation.getName(), target);
        if (reasonError != null) {
            send(player, reasonError); return true;
        }
        var alliances = plugin.getOrgManager().getMilitaryAlliancesByNation(nation.getName());
        if (!alliances.isEmpty()) {
            var alliance = alliances.getFirst();
            var result = plugin.getOrgManager().startMilitaryWarVote(alliance, nation.getName(), target,
                    plugin.getNationManager(), reason);
            sendMilitaryWarVoteResult(player, alliance, target, result);
        } else {
            var result = plugin.getOrgManager().handleMilitaryAllianceWar(nation.getName(), target,
                    plugin.getNationManager(), reason);
            if (result.isSuccess()) {
                send(player, "war_declared", target, plugin.getLocaleMessage(reason.messageKey()));
            } else {
                send(player, result.getFailureMessageKey(), result.getFailureMessageArgs());
            }
        }
        return true;
    }

    private void sendMilitaryWarVoteResult(Player player,
                                           com.thatguycy.worlddynamicsengine.model.WDEorg alliance,
                                           String target,
                                           com.thatguycy.worlddynamicsengine.manager.organization.OrgManager.MilitaryWarVoteResult result) {
        String key = "war_declaration_blocked";
        Object[] args = new Object[0];
        switch (result.getStatus()) {
            case APPROVED -> key = "war_vote_approved";
            case PENDING -> key = "war_vote_pending";
            case ALREADY_PENDING -> {
                key = "org_war_vote_already_pending";
                args = new Object[]{target};
            }
            case ACTIVE_WAR -> {
                key = "org_war_vote_already_active";
                args = new Object[]{target};
            }
            case TRUCE -> {
                key = "org_war_vote_target_truce";
                args = new Object[]{target};
            }
            case PEACE_PROTECTED -> {
                if (plugin.getOrgManager().isNationNeutral(target)) {
                    key = "diplomacy_target_nation_peace_protected";
                    args = new Object[]{target};
                } else {
                    key = "org_neutral_war_forbidden";
                }
            }
            case INVALID -> {
                if (result.getFailureMessageKey() != null) {
                    key = result.getFailureMessageKey();
                } else if (alliance != null && alliance.hasMemberNation(target)) {
                    key = "war_target_same_military_alliance";
                    args = new Object[]{target};
                } else {
                    key = "war_declaration_blocked";
                }
            }
            case NOT_FOUND, REJECTED -> key = "war_declaration_blocked";
        }
        send(player, key, args);
    }

    private boolean handleFocus(Player player, String[] args) {
        List<com.thatguycy.worlddynamicsengine.manager.organization.OrgManager.WarFocusOption> wars =
                plugin.getWarScoreboardManager().getAvailableWars(player);
        if (wars.isEmpty()) {
            player.sendMessage(ChatColor.RED + "你当前没有可专注的活跃战争。");
            return true;
        }
        if (args.length == 2) {
            String focused = plugin.getWarScoreboardManager().getFocusedWarKey(player);
            player.sendMessage(ChatColor.GOLD + "当前可专注的战争：");
            for (int i = 0; i < wars.size(); i++) {
                var war = wars.get(i);
                boolean current = war.focusKey().equalsIgnoreCase(focused);
                String marker = current ? ChatColor.GREEN + " [当前]" : "";
                player.sendMessage(ChatColor.YELLOW + String.valueOf(i + 1) + ". "
                        + ChatColor.WHITE + war.title()
                        + ChatColor.GRAY + " - " + plugin.getLocaleMessage(war.reason().messageKey())
                        + marker);
            }
            player.sendMessage(ChatColor.GRAY + "使用 /wde war focus <序号> 切换，或 /wde war focus clear 清除专注。");
            return true;
        }
        if ("clear".equalsIgnoreCase(args[2])) {
            plugin.getWarScoreboardManager().clearFocusedWar(player, true);
            player.sendMessage(ChatColor.YELLOW + "已清除当前专注战争；若你仍在参战，系统会在需要时自动选择一场战争显示。");
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "请输入有效的战争序号。");
            return true;
        }
        if (index < 1 || index > wars.size()) {
            player.sendMessage(ChatColor.RED + "战争序号超出范围。");
            return true;
        }
        var selected = wars.get(index - 1);
        if (!plugin.getWarScoreboardManager().setFocusedWar(player, selected.focusKey(), true)) {
            player.sendMessage(ChatColor.RED + "未能切换专注战争，请稍后再试。");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + "已将专注战争切换为："
                + ChatColor.WHITE + selected.title()
                + ChatColor.GRAY + "（" + plugin.getLocaleMessage(selected.reason().messageKey()) + "）");
        return true;
    }

    private void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(plugin.formatLocaleMessage(plugin.userLang, key, args));
    }
}
