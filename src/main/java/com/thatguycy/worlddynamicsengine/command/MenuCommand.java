package com.thatguycy.worlddynamicsengine.command;

import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.menu.MenuPage;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Resolves the viewer and page before invoking a menu renderer. */
final class MenuCommand implements CommandExecutor {
    static final String OTHERS_PERMISSION = "wde.menu.others";
    private final WorldDynamicsEngine plugin;

    MenuCommand(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    record Request(String viewer, MenuPage page, List<String> parameters) {}

    static Request parse(String[] args) {
        if (args.length <= 1) return new Request(null, MenuPage.HOME, List.of());
        MenuPage page = MenuPage.parse(args[1]);
        boolean explicitViewer = page == null;
        if (explicitViewer && args.length < 3) return null;
        String viewer = explicitViewer ? args[1].replaceFirst("^@", "") : null;
        int start = explicitViewer ? 3 : 2;
        if (explicitViewer) page = MenuPage.parse(args[2]);
        if (page == null || !page.accepts(args.length - start)
                || explicitViewer && viewer.isBlank()) return null;
        return new Request(viewer, page, List.copyOf(Arrays.asList(args).subList(start, args.length)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Request request = parse(args);
        if (request == null) {
            sender.sendMessage(plugin.getLocaleMessage("menu_command_usage"));
            sender.sendMessage(String.join(", ", MenuPage.ids()));
            return true;
        }
        var menus = plugin.getPlayerMenuManager();
        Player viewer = sender instanceof Player player ? player : null;
        if (request.viewer() != null && (viewer == null || !viewer.getName().equalsIgnoreCase(request.viewer()))) {
            if (!sender.hasPermission(OTHERS_PERMISSION)) {
                sender.sendMessage(plugin.getLocaleMessage("command_no_permission"));
                return true;
            }
            viewer = Bukkit.getPlayerExact(request.viewer());
            if (viewer == null || sender instanceof Player player && !player.canSee(viewer)) {
                sender.sendMessage(plugin.getLocaleMessage("menu_player_not_found"));
                return true;
            }
        }
        if (viewer == null) {
            menus.sendPlayerOnly(sender);
        } else if (!CommandPermissions.root(viewer, "menu")) {
            sender.sendMessage(plugin.getLocaleMessage("command_no_permission"));
        } else {
            menus.openPage(viewer, request.page(), request.parameters());
        }
        return true;
    }

    static List<String> complete(CommandSender sender, String[] args, OrgManager organizations) {
        var options = new ArrayList<String>();
        if (args.length == 2) {
            if (sender instanceof Player player) {
                options.addAll(MenuPage.ids());
                options.add("@" + player.getName());
            }
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !(sender instanceof Player player) || player.canSee(p))
                        .filter(p -> CommandPermissions.root(p, "menu"))
                        .map(p -> "@" + p.getName()).forEach(options::add);
            }
        } else if (args.length == 3 && MenuPage.parse(args[1]) == null) {
            String name = args[1].replaceFirst("^@", "");
            if (sender.hasPermission(OTHERS_PERMISSION)
                    || sender instanceof Player player && player.getName().equalsIgnoreCase(name)) {
                options.addAll(MenuPage.ids());
            }
        } else if (sender instanceof Player player) {
            int pageIndex = MenuPage.parse(args[1]) == null ? 2 : 1;
            boolean self = pageIndex == 1 || player.getName().equalsIgnoreCase(args[1].replaceFirst("^@", ""));
            if (self && args.length == pageIndex + 2) {
                switch (args[pageIndex].toLowerCase(Locale.ROOT)) {
                    case "background-stage", "background-detail" -> options.addAll(List.of("childhood", "adulthood"));
                    case "war", "war-detail" -> {
                        var town = TownyAPI.getInstance().getTown(player);
                        if (town != null && organizations != null) {
                            var nation = town.getNationOrNull();
                            var wars = organizations.getWarManager();
                            if ("war-detail".equalsIgnoreCase(args[pageIndex])) {
                                wars.getWarFocusOptions(nation == null ? "" : nation.getName(), town.getName())
                                        .forEach(war -> options.add(war.focusKey()));
                            } else {
                                wars.getVisibleWarDetails(nation == null ? "" : nation.getName(), town.getName())
                                        .forEach(war -> options.addAll(war.enemyParticipants()));
                            }
                        }
                    }
                    default -> { }
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().distinct().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
