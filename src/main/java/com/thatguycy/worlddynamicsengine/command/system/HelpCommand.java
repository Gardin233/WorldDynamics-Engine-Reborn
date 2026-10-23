package com.thatguycy.worlddynamicsengine.command.system;

import com.thatguycy.worlddynamicsengine.command.CommandPermissions;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 按页展示命令帮助，并为玩家生成可点击的翻页入口。
 */
public class HelpCommand implements CommandExecutor {
    private static final int PAGE_SIZE = 8;
    private static final Set<String> MODULES = Set.of(
            "org", "nation", "town", "inn", "ideology", "war", "diplomacy", "background", "history", "rally");

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
            sender.sendMessage(TextColors.RED + plugin.formatLocaleMessage("help_page_invalid", pages));
            return true;
        }

        return showPage(sender, plugin, lines, page, "help", null, "help_page_console");
    }

    public static boolean supportsModule(String module) {
        return module != null && MODULES.contains(module.toLowerCase(Locale.ROOT));
    }

    public static boolean handleModuleHelp(CommandSender sender, String module, String[] args) {
        WorldDynamicsEngine plugin = WorldDynamicsEngine.getInstance();
        String normalizedModule = module.toLowerCase(Locale.ROOT);
        String commandPath = normalizedModule + " help";
        List<String> lines = new HelpCommand().moduleHelpLines(plugin, normalizedModule);
        int pages = pageCount(lines);
        int page = 1;
        try {
            if (args.length > 3) throw new NumberFormatException();
            if (args.length == 3) page = Integer.parseInt(args[2]);
            if (page < 1 || page > pages) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextColors.RED + plugin.formatLocaleMessage(
                    "help_module_page_invalid", "/wde " + commandPath, pages));
            return true;
        }
        return new HelpCommand().showPage(
                sender, plugin, lines, page, commandPath, "help_" + moduleTitleSuffix(normalizedModule),
                "help_module_page_console");
    }

    public static int getModulePageCount(WorldDynamicsEngine plugin, String module) {
        if (!supportsModule(module)) return 1;
        return pageCount(new HelpCommand().moduleHelpLines(plugin, module.toLowerCase(Locale.ROOT)));
    }

    private boolean showPage(CommandSender sender, WorldDynamicsEngine plugin, List<String> lines, int page,
            String commandPath, String moduleTitleKey, String consoleMessageKey) {
        int pages = pageCount(lines);
        String title = plugin.getLocaleMessage("help_title");
        if (moduleTitleKey != null) title += " " + plugin.getLocaleMessage(moduleTitleKey);
        sender.sendMessage(TextColors.GOLD + title
                + " " + TextColors.GRAY + plugin.formatLocaleMessage("help_page_indicator", page, pages));
        if (moduleTitleKey != null) {
            sender.sendMessage(TextColors.AQUA + plugin.formatLocaleMessage("help_section_header",
                    plugin.getLocaleMessage(moduleTitleKey)));
        }
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, lines.size());
        // Repeat the current category when a page starts midway through it.
        if (moduleTitleKey == null && start > 0 && !lines.get(start).startsWith(TextColors.AQUA.toString())) {
            for (int i = start - 1; i >= 0; i--) {
                if (lines.get(i).startsWith(TextColors.AQUA.toString())) {
                    sender.sendMessage(lines.get(i));
                    break;
                }
            }
        }
        for (int i = start; i < end; i++) sender.sendMessage(lines.get(i));

        if (sender instanceof Player) {
            Component navigation = Component.empty();
            if (page > 1) navigation = navigation.append(pageButton(plugin, "help_page_previous", page - 1, commandPath));
            navigation = navigation.append(Component.text("  "))
                    .append(LegacyComponentSerializer.legacySection().deserialize(
                            TextColors.GRAY + plugin.formatLocaleMessage("help_page_indicator", page, pages)))
                    .append(Component.text("  "));
            if (page < pages) navigation = navigation.append(pageButton(plugin, "help_page_next", page + 1, commandPath));
            sender.sendMessage(navigation);
        } else {
            String consoleMessage = "help_module_page_console".equals(consoleMessageKey)
                    ? plugin.formatLocaleMessage(consoleMessageKey, "/wde " + commandPath, page, pages)
                    : plugin.formatLocaleMessage(consoleMessageKey, page, pages);
            sender.sendMessage(TextColors.GRAY + consoleMessage);
        }
        return true;
    }

    private Component pageButton(WorldDynamicsEngine plugin, String key, int page, String commandPath) {
        return LegacyComponentSerializer.legacySection().deserialize(TextColors.YELLOW + plugin.getLocaleMessage(key))
                .clickEvent(ClickEvent.runCommand("/wde " + commandPath + " " + page))
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

    private List<String> moduleHelpLines(WorldDynamicsEngine plugin, String module) {
        List<String> lines = new ArrayList<>();
        switch (module) {
            case "org" -> {
                add(lines, plugin, "/wde org menu [集团]", "help_cmd_org_menu");
                add(lines, plugin, "/wde org info <集团>", "help_cmd_org_info");
                add(lines, plugin, "/wde org create <名称> <类型>", "help_cmd_org_create_type");
                add(lines, plugin, "/wde org dissolve <集团>", "help_cmd_org_dissolve");
                add(lines, plugin, "/wde org applynation <集团>", "help_cmd_org_applynation");
                add(lines, plugin, "/wde org votejoin <集团> <国家> <yes|no>", "help_cmd_org_votejoin");
                add(lines, plugin, "/wde org reviewjoin <集团> <国家> <accept|reject>", "help_cmd_org_reviewjoin");
                add(lines, plugin, "/wde org declarewar <集团> <国家> <理由>", "help_cmd_org_declarewar");
                add(lines, plugin, "/wde org votewar <集团> <国家> <yes|no>", "help_cmd_org_votewar");
                add(lines, plugin, "/wde org sanction <集团> <国家> <enabled|disabled>", "help_cmd_org_sanction");
                add(lines, plugin, "/wde org deposit <集团> <金额>", "help_cmd_org_deposit");
                add(lines, plugin, "/wde org withdraw <集团> <金额>", "help_cmd_org_withdraw");
                add(lines, plugin, "/wde org leave <集团>", "help_cmd_org_leave_restricted");
                add(lines, plugin, "/wde org grantindependence <集团> <国家>", "help_cmd_org_grantindependence");
                add(lines, plugin, "/wde org peacetreaty <集团> <国家> <demand|surrender> <条款>", "help_cmd_org_peacetreaty");
                add(lines, plugin, "/wde org reviewtreaty <集团> <国家> <accept|reject>", "help_cmd_org_reviewtreaty");
                add(lines, plugin, "/wde org promoteautonomy <集团>", "help_cmd_org_promoteautonomy");
                add(lines, plugin, "/wde org demoteautonomy <集团> <国家>", "help_cmd_org_demoteautonomy");
                add(lines, plugin, "/wde org setgarrison <集团> <国家> <enabled|disabled>", "help_cmd_org_setgarrison");
                add(lines, plugin, "/wde org mobilizewar <集团> <国家> <enabled|disabled>", "help_cmd_org_mobilizewar");
                add(lines, plugin, "/wde org replaceleader <集团> <国家> <玩家>", "help_cmd_org_replaceleader");
                add(lines, plugin, "/wde org demandtribute <集团> <国家>", "help_cmd_org_demandtribute");
            }
            case "nation" -> {
                add(lines, plugin, "/wde nation <国家>", "help_cmd_nation_info");
                add(lines, plugin, "/wde nation <国家> menu", "help_cmd_nation_menu");
                add(lines, plugin, "/wde nation <国家> setgovtype <类型>", "help_cmd_setgovtype");
                add(lines, plugin, "/wde nation <国家> setgovleader <玩家>", "help_cmd_setgovleader");
                add(lines, plugin, "/wde nation <国家> startelection", "help_cmd_startelection");
                add(lines, plugin, "/wde nation <国家> voteleader <候选人|yes|no>", "help_cmd_voteleader");
                add(lines, plugin, "/wde nation <国家> addgovmember <玩家>", "help_cmd_addgovmember");
                add(lines, plugin, "/wde nation <国家> kickgovmember <玩家>", "help_cmd_kickgovmember");
                add(lines, plugin, "/wde nation <国家> appointarmycommander <玩家>", "help_cmd_appointarmycommander");
                add(lines, plugin, "/wde nation <国家> votearmycommander <候选人|yes|no>", "help_cmd_votearmycommander");
                add(lines, plugin, "/wde nation <国家> enlistarmymember <玩家>", "help_cmd_enlistarmymember");
                add(lines, plugin, "/wde nation <国家> dischargearmymember <玩家>", "help_cmd_dischargearmymember");
                add(lines, plugin, "/wde nation <国家> tpconscript <玩家>", "help_cmd_tpconscript");
                add(lines, plugin, "/wde nation <国家> tpchaircommander", "help_cmd_tpchaircommander");
                add(lines, plugin, "/wde nation <国家> settowntax <城镇> <fixed|wealth_percentage> <金额>", "help_cmd_settowntax");
                add(lines, plugin, "/wde nation <国家> adddiplomat <玩家>", "help_cmd_adddiplomat");
                add(lines, plugin, "/wde nation <国家> removediplomat <玩家>", "help_cmd_removediplomat");
            }
            case "town" -> {
                add(lines, plugin, "/wde town <城镇>", "help_cmd_town_info");
                add(lines, plugin, "/wde town <城镇> menu", "help_cmd_town_menu");
                add(lines, plugin, "/wde town <城镇> setgovtype <dictatorship|republic>", "help_cmd_town_setgovtype");
                add(lines, plugin, "/wde town <城镇> setleader <玩家>", "help_cmd_town_setleader");
                add(lines, plugin, "/wde town <城镇> nominateleader <玩家>", "help_cmd_town_nominateleader");
                add(lines, plugin, "/wde town <城镇> voteleader <候选人|yes|no>", "help_cmd_town_voteleader");
                add(lines, plugin, "/wde town <城镇> setelectiondays <天数>", "help_cmd_town_setelectiondays");
                add(lines, plugin, "/wde town <城镇> setconscript <玩家> <enabled|disabled>", "help_cmd_town_setconscript");
                add(lines, plugin, "/wde town <城镇> setconscriptprivilege <玩家> <taxfree> <enabled|disabled>", "help_cmd_town_setconscriptprivilege");
            }
            case "inn" -> add(lines, plugin, "/wde inn set <金额> [币种]", "help_cmd_inn_set");
            case "ideology" -> {
                add(lines, plugin, "/wde ideology list", "help_cmd_religion_list");
                add(lines, plugin, "/wde ideology info <意识形态>", "help_cmd_religion_info");
                add(lines, plugin, "/wde ideology create <名称>", "help_cmd_religion_create");
                add(lines, plugin, "/wde ideology treasury <意识形态>", "help_cmd_religion_treasury");
                add(lines, plugin, "/wde ideology donate <意识形态> <金额>", "help_cmd_religion_donate");
                add(lines, plugin, "/wde ideology settithe <意识形态> <fixed|wealth_percentage> <金额>", "help_cmd_religion_settithe");
                add(lines, plugin, "/wde ideology governance <意识形态> <lifetime|cardinal|synodal>", "help_cmd_religion_governance");
                add(lines, plugin, "/wde ideology cardinal <意识形态> <appoint|revoke> <玩家>", "help_cmd_religion_cardinal");
                add(lines, plugin, "/wde ideology vote <意识形态> <候选人|abstain>", "help_cmd_religion_vote");
                add(lines, plugin, "/wde ideology church <create|list|join|leave|abdicate|disband|treasury>", "help_cmd_religion_church");
                add(lines, plugin, "/wde ideology council <propose|vote|list>", "help_cmd_religion_council");
                add(lines, plugin, "/wde ideology split <源意识形态> <教会> <新意识形态>", "help_cmd_religion_split");
                add(lines, plugin, "/wde ideology setplayer <意识形态>", "help_cmd_religion_setplayer");
                add(lines, plugin, "/wde ideology setplayer <玩家> <意识形态|none>", "help_cmd_religion_forceplayer");
                add(lines, plugin, "/wde ideology setnation <国家> <意识形态|none>", "help_cmd_religion_setnation");
                add(lines, plugin, "/wde ideology settown <城镇> <意识形态|none>", "help_cmd_religion_settown");
                add(lines, plugin, "/wde ideology setmemes <意识形态> <模因...>", "help_cmd_religion_setmemes");
                add(lines, plugin, "/wde ideology abdicate <意识形态> <玩家>", "help_cmd_religion_abdicate");
                add(lines, plugin, "/wde ideology excommunicate <意识形态> <player|town|nation> <目标>", "help_cmd_religion_excommunicate");
            }
            case "war" -> {
                add(lines, plugin, "/wde war [menu]", "help_cmd_war");
                add(lines, plugin, "/wde war focus [序号|clear]", "help_cmd_war_focus");
                add(lines, plugin, "/wde war declare <nation|town> <目标> <战争理由>", "help_cmd_war");
                add(lines, plugin, "/wde war surrender town <敌镇>", "help_cmd_war_surrender");
                add(lines, plugin, "/wde rally <nation|town>", "help_cmd_rally");
            }
            case "diplomacy" -> {
                add(lines, plugin, "/wde diplomacy setrelation <国家> <friendly|neutral|unfriendly>", "help_cmd_setrelation");
                add(lines, plugin, "/wde diplomacy trading <国家> <enabled|disabled>", "help_cmd_trading");
                add(lines, plugin, "/wde diplomacy viewtrading <国家>", "help_cmd_viewtrading");
                add(lines, plugin, "/wde diplomacy relations <国家>", "help_cmd_relations");
                add(lines, plugin, "/wde diplomacy visit <国家>", "help_cmd_visit");
                add(lines, plugin, "/wde diplomacy letter <国家> <insult|notice|request> <正文>", "help_cmd_diplomacy_letter");
                add(lines, plugin, "/wde diplomacy letters <页码>", "help_cmd_diplomacy_letters");
                add(lines, plugin, "/wde diplomacy readletter <信件UUID> [页码]", "help_cmd_diplomacy_readletter");
            }
            case "background" -> {
                add(lines, plugin, "/wde background status", "help_cmd_background_status");
                add(lines, plugin, "/wde background list <childhood|adulthood>", "help_cmd_background_list");
                add(lines, plugin, "/wde background info <childhood|adulthood> <编号>", "help_cmd_background_info");
                add(lines, plugin, "/wde background choose <childhood|adulthood> <编号>", "help_cmd_background_choose");
            }
            case "history" -> {
                add(lines, plugin, "/wde history list [页码]", "help_cmd_history_list");
                add(lines, plugin, "/wde history read <国家史书ID> [页码]", "help_cmd_history_read");
                add(lines, plugin, "/wde history txt <国家史书ID>", "help_cmd_history_txt");
                add(lines, plugin, "/wde history book <国家史书ID> [卷号]", "help_cmd_history_book");
            }
            case "rally" -> {
                add(lines, plugin, "/wde rally nation", "help_cmd_rally");
                add(lines, plugin, "/wde rally town", "help_cmd_rally");
            }
            default -> { }
        }
        return lines;
    }

    private void add(List<String> lines, WorldDynamicsEngine plugin, String command, String descriptionKey) {
        lines.add(TextColors.YELLOW + command + TextColors.WHITE + " - " + plugin.getLocaleMessage(descriptionKey));
    }

    private static String moduleTitleSuffix(String module) {
        return switch (module) {
            case "org" -> "organization";
            case "ideology" -> "ideology";
            default -> module;
        };
    }

    private List<String> helpLines(WorldDynamicsEngine plugin, CommandSender sender) {
        List<String> lines = new ArrayList<>();

        lines.add(TextColors.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_general")));
        lines.add(TextColors.YELLOW + "/wde help [page]" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_help"));
        lines.add(TextColors.YELLOW + "/wde <模块> help [页码]" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_module"));
        lines.add(TextColors.YELLOW + "/wde docs" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_docs"));
        if (plugin.getNationHistoryManager() != null) {
            lines.add(TextColors.YELLOW + "/wde history <list|read|txt|book>" + TextColors.WHITE + " - "
                    + plugin.getNationHistoryManager().message("help", java.util.Map.of()));
        }
        if (sender == null || CommandPermissions.root(sender, "debug")) {
            lines.add(TextColors.YELLOW + "/wde debug balance <player> [currency]" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_debug_balance"));
        }
        if (sender == null || CommandPermissions.root(sender, "reload")) {
            lines.add(TextColors.YELLOW + "/wde reload" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_reload"));
        }
        if (sender != null && CommandPermissions.anyAdmin(sender)) {
            lines.add(TextColors.YELLOW + "/wde admin help" + TextColors.WHITE + " - " + plugin.getLocaleMessage("admin_help_description"));
        }
        lines.add(TextColors.YELLOW + "/wde background status" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_status"));
        lines.add(TextColors.YELLOW + "/wde rally <nation|town>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_rally"));
        lines.add(TextColors.YELLOW + "/wde war declare <nation|town> <target> <reason>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_war"));
        lines.add(TextColors.YELLOW + "/wde background list <childhood|adulthood>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_list"));
        lines.add(TextColors.YELLOW + "/wde background info <childhood|adulthood> <id>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_info"));
        lines.add(TextColors.YELLOW + "/wde background choose <childhood|adulthood> <id>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_background_choose"));
        lines.add(TextColors.YELLOW + "/wde ideology list" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_list"));
        lines.add(TextColors.YELLOW + "/wde ideology info <ideology>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_info"));
        lines.add(TextColors.YELLOW + "/wde ideology create <ideology>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_create"));
        lines.add(TextColors.YELLOW + "/wde ideology treasury <ideology>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_treasury"));
        lines.add(TextColors.YELLOW + "/wde ideology donate <ideology> <amount>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_donate"));
        lines.add(TextColors.YELLOW + "/wde ideology settithe <ideology> <fixed|wealth_percentage> <amount>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_settithe"));
        lines.add(TextColors.YELLOW + "/wde ideology governance <ideology> <lifetime|cardinal|synodal>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_governance"));
        lines.add(TextColors.YELLOW + "/wde ideology cardinal <ideology> <appoint|revoke> <player>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_cardinal"));
        lines.add(TextColors.YELLOW + "/wde ideology vote <ideology> <candidate|abstain>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_vote"));
        lines.add(TextColors.YELLOW + "/wde ideology church <create|list|join|leave|abdicate|disband|treasury>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_church"));
        lines.add(TextColors.YELLOW + "/wde ideology council <propose|vote|list>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_council"));
        lines.add(TextColors.YELLOW + "/wde ideology split <source> <church> <newIdeology>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_split"));
        lines.add(TextColors.YELLOW + "/wde ideology setplayer <ideology>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_setplayer"));
        lines.add(TextColors.YELLOW + "/wde ideology setplayer <player> <ideology|none>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_forceplayer"));
        lines.add(TextColors.YELLOW + "/wde ideology setnation <nation> <ideology|none>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_setnation"));
        lines.add(TextColors.YELLOW + "/wde ideology settown <town> <ideology|none>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_settown"));
        lines.add(TextColors.YELLOW + "/wde ideology setmemes <ideology> <meme1> [meme2] [meme3] [meme4]" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_setmemes"));
        lines.add(TextColors.YELLOW + "/wde ideology abdicate <ideology> <player>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_abdicate"));
        lines.add(TextColors.YELLOW + "/wde ideology excommunicate <ideology> <player|town|nation> <target>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_religion_excommunicate"));

        lines.add(TextColors.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_town")));
        lines.add(TextColors.YELLOW + "/wde town <town>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_info"));
        lines.add(TextColors.YELLOW + "/wde town <town> setgovtype <dictatorship|republic>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setgovtype"));
        lines.add(TextColors.YELLOW + "/wde town <town> setleader <player>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setleader"));
        lines.add(TextColors.YELLOW + "/wde town <town> nominateleader <player>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_nominateleader"));
        lines.add(TextColors.YELLOW + "/wde town <town> voteleader <candidate|yes|no>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_voteleader"));
        lines.add(TextColors.YELLOW + "/wde town <town> setelectiondays <days>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setelectiondays"));
        lines.add(TextColors.YELLOW + "/wde town <town> setconscript <player> <enabled|disabled>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setconscript"));
        lines.add(TextColors.YELLOW + "/wde town <town> setconscriptprivilege <player> <taxfree> <enabled|disabled>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_town_setconscriptprivilege"));

        lines.add(TextColors.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_nation")));
        lines.add(TextColors.YELLOW + "/wde nation <nation> setgovtype <type>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_setgovtype"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> setgovleader <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_setgovleader"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> startelection" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_startelection"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> voteleader <candidate|yes|no>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_voteleader"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> addgovmember <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_addgovmember"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> kickgovmember <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_kickgovmember"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> appointarmycommander <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_appointarmycommander"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> votearmycommander <candidate|yes|no>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_votearmycommander"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> enlistarmymember <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_enlistarmymember"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> dischargearmymember <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_dischargearmymember"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> tpconscript <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_tpconscript"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> tpchaircommander" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_tpchaircommander"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> settowntax <town> <fixed|wealth_percentage> <amount>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_settowntax"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> adddiplomat <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_adddiplomat"));
        lines.add(TextColors.YELLOW + "/wde nation <nation> removediplomat <username>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_removediplomat"));

        lines.add(TextColors.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_diplomacy")));
        lines.add(TextColors.YELLOW + "/wde diplomacy setrelation <nation> <friendly/neutral/unfriendly>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_setrelation"));
        lines.add(TextColors.YELLOW + "/wde diplomacy trading <nation> <enabled/disabled>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_trading"));
        lines.add(TextColors.YELLOW + "/wde diplomacy viewtrading <nation>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_viewtrading"));
        lines.add(TextColors.YELLOW + "/wde diplomacy relations <nation>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_relations"));
        lines.add(TextColors.YELLOW + "/wde diplomacy visit <nation>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_visit"));

        lines.add(TextColors.AQUA + plugin.formatLocaleMessage("help_section_header", plugin.getLocaleMessage("help_organization")));
        lines.add(TextColors.YELLOW + "/wde org create <orgname> <type>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_create_type"));
        lines.add(TextColors.YELLOW + "/wde org dissolve <orgname>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_dissolve"));
        lines.add(TextColors.YELLOW + "/wde org applynation <orgname>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_applynation"));
        lines.add(TextColors.YELLOW + "/wde org votejoin <orgname> <nation> <yes|no>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_votejoin"));
        lines.add(TextColors.YELLOW + "/wde org reviewjoin <orgname> <nation> <accept|reject>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_reviewjoin"));
        lines.add(TextColors.YELLOW + "/wde org declarewar <orgname> <nation> <reason>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_declarewar"));
        lines.add(TextColors.YELLOW + "/wde org votewar <orgname> <nation> <yes|no>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_votewar"));
        lines.add(TextColors.YELLOW + "/wde org sanction <orgname> <nation> <enabled|disabled>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_sanction"));
        lines.add(TextColors.YELLOW + "/wde org deposit <orgname> <amount>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_deposit"));
        lines.add(TextColors.YELLOW + "/wde org withdraw <orgname> <amount>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_withdraw"));
        lines.add(TextColors.YELLOW + "/wde org leave <orgname>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_leave_restricted"));
        lines.add(TextColors.YELLOW + "/wde org grantindependence <orgname> <nation>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_grantindependence"));
        lines.add(TextColors.YELLOW + "/wde org peacetreaty <orgname> <nation> <demand|surrender> <clauses>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_peacetreaty"));
        lines.add(TextColors.YELLOW + "/wde org reviewtreaty <orgname> <nation> <accept|reject>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_reviewtreaty"));
        lines.add(TextColors.YELLOW + "/wde org promoteautonomy <orgname>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_promoteautonomy"));
        lines.add(TextColors.YELLOW + "/wde org demoteautonomy <orgname> <nation>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_demoteautonomy"));
        lines.add(TextColors.YELLOW + "/wde org setgarrison <orgname> <nation> <enabled|disabled>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_setgarrison"));
        lines.add(TextColors.YELLOW + "/wde org mobilizewar <orgname> <nation> <enabled|disabled>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_mobilizewar"));
        lines.add(TextColors.YELLOW + "/wde org replaceleader <orgname> <nation> <player>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_replaceleader"));
        lines.add(TextColors.YELLOW + "/wde org demandtribute <orgname> <nation>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_demandtribute"));
        lines.add(TextColors.YELLOW + "/wde org info <orgname>" + TextColors.WHITE + " - " + plugin.getLocaleMessage("help_cmd_org_info"));

        return lines;
    }
}
