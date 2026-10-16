package com.thatguycy.worlddynamicsengine.command.system;

import com.thatguycy.worlddynamicsengine.command.CommandPermissions;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 按页展示命令帮助，并为玩家生成可点击的翻页入口。
 */
public class HelpCommand implements CommandExecutor {
    private static final int PAGE_SIZE = 8;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        List<String> lines = helpLines(plugin, sender);
        int pages = pageCount(lines);
        int page = 1;
        try {
            if (args.length > 2) throw new NumberFormatException();
            if (args.length == 2) page = Integer.parseInt(args[1]);
            if (page < 1 || page > pages) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + plugin.formatLocaleMessage("help_page_invalid", pages));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + plugin.getLocaleMessage("help_title")
                + " " + ChatColor.GRAY + plugin.formatLocaleMessage("help_page_indicator", page, pages));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, lines.size());
        // Repeat the current category when a page starts midway through it.
        if (start > 0 && !lines.get(start).startsWith(ChatColor.AQUA.toString())) {
            for (int i = start - 1; i >= 0; i--) {
                if (lines.get(i).startsWith(ChatColor.AQUA.toString())) {
                    sender.sendMessage(lines.get(i));
                    break;
                }
            }
        }
        for (int i = start; i < end; i++) sender.sendMessage(lines.get(i));

        if (sender instanceof Player) {
            Component navigation = Component.empty();
            if (page > 1) navigation = navigation.append(pageButton(plugin, "help_page_previous", page - 1));
            navigation = navigation.append(Component.text("  "))
                    .append(LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.GRAY + plugin.formatLocaleMessage("help_page_indicator", page, pages)))
                    .append(Component.text("  "));
            if (page < pages) navigation = navigation.append(pageButton(plugin, "help_page_next", page + 1));
            sender.sendMessage(navigation);
        } else {
            sender.sendMessage(ChatColor.GRAY + plugin.formatLocaleMessage("help_page_console", page, pages));
        }
        return true;
    }

    private Component pageButton(WorldDynamicsEngine plugin, String key, int page) {
        return LegacyComponentSerializer.legacySection().deserialize(ChatColor.YELLOW + plugin.getLocaleMessage(key))
                .clickEvent(ClickEvent.runCommand("/wde help " + page))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(
                        plugin.formatLocaleMessage("help_page_hover", page))));
    }

    public static int getPageCount(WorldDynamicsEngine plugin) {
        return pageCount(new HelpCommand().helpLines(plugin, null));
    }

    public static int getPageCount(WorldDynamicsEngine plugin, CommandSender sender) {
        return pageCount(new HelpCommand().helpLines(plugin, sender));
    }

    private static int pageCount(List<String> lines) {
        return Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private List<String> helpLines(WorldDynamicsEngine plugin, CommandSender sender) {
        List<String> lines = new ArrayList<>();

        lines.add(ChatColor.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_general")));
        lines.add(ChatColor.YELLOW + "/wde help [page]" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_help"));
        lines.add(ChatColor.YELLOW + "/wde docs" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_docs"));
        if (sender == null || CommandPermissions.root(sender, "debug")) {
            lines.add(ChatColor.YELLOW + "/wde debug balance <player> [currency]" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_debug_balance"));
        }
        if (sender == null || CommandPermissions.root(sender, "reload")) {
            lines.add(ChatColor.YELLOW + "/wde reload" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_reload"));
        }
        if (sender != null && CommandPermissions.anyAdmin(sender)) {
            lines.add(ChatColor.YELLOW + "/wde admin help" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("admin_help_description"));
        }
        lines.add(ChatColor.YELLOW + "/wde background status" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_status"));
        lines.add(ChatColor.YELLOW + "/wde rally <nation|town>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_rally"));
        lines.add(ChatColor.YELLOW + "/wde war declare <nation|town> <target> <reason>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_war"));
        lines.add(ChatColor.YELLOW + "/wde background list <childhood|adulthood>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_list"));
        lines.add(ChatColor.YELLOW + "/wde background info <childhood|adulthood> <id>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_info"));
        lines.add(ChatColor.YELLOW + "/wde background choose <childhood|adulthood> <id>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_choose"));
        lines.add(ChatColor.YELLOW + "/wde ideology list" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_list"));
        lines.add(ChatColor.YELLOW + "/wde ideology info <ideology>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_info"));
        lines.add(ChatColor.YELLOW + "/wde ideology create <ideology>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_create"));
        lines.add(ChatColor.YELLOW + "/wde ideology treasury <ideology>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_treasury"));
        lines.add(ChatColor.YELLOW + "/wde ideology donate <ideology> <amount>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_donate"));
        lines.add(ChatColor.YELLOW + "/wde ideology settithe <ideology> <fixed|wealth_percentage> <amount>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_settithe"));
        lines.add(ChatColor.YELLOW + "/wde ideology setplayer <ideology>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_setplayer"));
        lines.add(ChatColor.YELLOW + "/wde ideology setplayer <player> <ideology|none>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_forceplayer"));
        lines.add(ChatColor.YELLOW + "/wde ideology setnation <nation> <ideology|none>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_setnation"));
        lines.add(ChatColor.YELLOW + "/wde ideology settown <town> <ideology|none>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_settown"));
        lines.add(ChatColor.YELLOW + "/wde ideology setmemes <ideology> <meme1> [meme2] [meme3] [meme4]" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_setmemes"));
        lines.add(ChatColor.YELLOW + "/wde ideology abdicate <ideology> <player>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_abdicate"));
        lines.add(ChatColor.YELLOW + "/wde ideology excommunicate <ideology> <player|town|nation> <target>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_excommunicate"));

        lines.add(ChatColor.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_town")));
        lines.add(ChatColor.YELLOW + "/wde town <town>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_info"));
        lines.add(ChatColor.YELLOW + "/wde town <town> setgovtype <dictatorship|republic>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setgovtype"));
        lines.add(ChatColor.YELLOW + "/wde town <town> setleader <player>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setleader"));
        lines.add(ChatColor.YELLOW + "/wde town <town> nominateleader <player>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_nominateleader"));
        lines.add(ChatColor.YELLOW + "/wde town <town> voteleader <candidate|yes|no>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_voteleader"));
        lines.add(ChatColor.YELLOW + "/wde town <town> setelectiondays <days>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setelectiondays"));
        lines.add(ChatColor.YELLOW + "/wde town <town> setconscript <player> <enabled|disabled>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setconscript"));
        lines.add(ChatColor.YELLOW + "/wde town <town> setconscriptprivilege <player> <taxfree> <enabled|disabled>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setconscriptprivilege"));

        lines.add(ChatColor.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_nation")));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> setgovtype <type>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_setgovtype"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> setgovleader <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_setgovleader"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> startelection" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_startelection"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> voteleader <candidate|yes|no>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_voteleader"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> addgovmember <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_addgovmember"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> kickgovmember <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_kickgovmember"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> appointarmycommander <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_appointarmycommander"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> votearmycommander <candidate|yes|no>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_votearmycommander"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> enlistarmymember <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_enlistarmymember"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> dischargearmymember <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_dischargearmymember"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> tpconscript <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_tpconscript"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> tpchaircommander" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_tpchaircommander"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> settowntax <town> <fixed|wealth_percentage> <amount>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_settowntax"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> adddiplomat <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_adddiplomat"));
        lines.add(ChatColor.YELLOW + "/wde nation <nation> removediplomat <username>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_removediplomat"));

        lines.add(ChatColor.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_diplomacy")));
        lines.add(ChatColor.YELLOW + "/wde diplomacy setrelation <nation> <friendly/neutral/unfriendly>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_setrelation"));
        lines.add(ChatColor.YELLOW + "/wde diplomacy trading <nation> <enabled/disabled>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_trading"));
        lines.add(ChatColor.YELLOW + "/wde diplomacy viewtrading <nation>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_viewtrading"));
        lines.add(ChatColor.YELLOW + "/wde diplomacy relations <nation>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_relations"));
        lines.add(ChatColor.YELLOW + "/wde diplomacy visit <nation>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_visit"));

        lines.add(ChatColor.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_organization")));
        lines.add(ChatColor.YELLOW + "/wde org create <orgname> <type>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_create_type"));
        lines.add(ChatColor.YELLOW + "/wde org dissolve <orgname>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_dissolve"));
        lines.add(ChatColor.YELLOW + "/wde org applynation <orgname>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_applynation"));
        lines.add(ChatColor.YELLOW + "/wde org votejoin <orgname> <nation> <yes|no>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_votejoin"));
        lines.add(ChatColor.YELLOW + "/wde org reviewjoin <orgname> <nation> <accept|reject>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_reviewjoin"));
        lines.add(ChatColor.YELLOW + "/wde org declarewar <orgname> <nation> <reason>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_declarewar"));
        lines.add(ChatColor.YELLOW + "/wde org votewar <orgname> <nation> <yes|no>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_votewar"));
        lines.add(ChatColor.YELLOW + "/wde org sanction <orgname> <nation> <enabled|disabled>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_sanction"));
        lines.add(ChatColor.YELLOW + "/wde org deposit <orgname> <amount>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_deposit"));
        lines.add(ChatColor.YELLOW + "/wde org withdraw <orgname> <amount>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_withdraw"));
        lines.add(ChatColor.YELLOW + "/wde org leave <orgname>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_leave_restricted"));
        lines.add(ChatColor.YELLOW + "/wde org grantindependence <orgname> <nation>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_grantindependence"));
        lines.add(ChatColor.YELLOW + "/wde org peacetreaty <orgname> <nation> <demand|surrender> <clauses>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_peacetreaty"));
        lines.add(ChatColor.YELLOW + "/wde org reviewtreaty <orgname> <nation> <accept|reject>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_reviewtreaty"));
        lines.add(ChatColor.YELLOW + "/wde org promoteautonomy <orgname>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_promoteautonomy"));
        lines.add(ChatColor.YELLOW + "/wde org demoteautonomy <orgname> <nation>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_demoteautonomy"));
        lines.add(ChatColor.YELLOW + "/wde org setgarrison <orgname> <nation> <enabled|disabled>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_setgarrison"));
        lines.add(ChatColor.YELLOW + "/wde org mobilizewar <orgname> <nation> <enabled|disabled>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_mobilizewar"));
        lines.add(ChatColor.YELLOW + "/wde org replaceleader <orgname> <nation> <player>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_replaceleader"));
        lines.add(ChatColor.YELLOW + "/wde org demandtribute <orgname> <nation>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_demandtribute"));
        lines.add(ChatColor.YELLOW + "/wde org info <orgname>" + ChatColor.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_info"));

        return lines;
    }
}
