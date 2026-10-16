package com.thatguycy.worlddynamicsengine.command.admin;

import com.thatguycy.worlddynamicsengine.command.CommandPermissions;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.*;
import org.bukkit.command.CommandSender;

/**
 * 根据管理员模块权限和当前参数位置生成补全候选，不向无权限用户暴露管理入口。
 */
public final class AdminTabCompleter {
    private AdminTabCompleter() {}

    public static List<String> complete(CommandSender sender, String[] a) {
        if (!CommandPermissions.anyAdmin(sender)) return List.of();
        Set<String> candidates = new LinkedHashSet<>();
        if (a.length == 2) {
            candidates.add("help");
            CommandPermissions.ADMIN_MODULES.stream().filter(m -> CommandPermissions.admin(sender, m)).forEach(candidates::add);
        } else if (a[1].equalsIgnoreCase("help")) {
            long count = AdminCommand.ENTRIES.stream().filter(e -> CommandPermissions.admin(sender, e.module())).count();
            if (a.length == 3) for (int i = 1; i <= (count + 7) / 8; i++) candidates.add(String.valueOf(i));
        } else if (CommandPermissions.admin(sender, a[1].toLowerCase(Locale.ROOT))) {
            List<AdminCommand.Entry> entries = AdminCommand.ENTRIES.stream().filter(e -> e.module().equalsIgnoreCase(a[1])).toList();
            if (a.length == 3) entries.forEach(e -> candidates.add(e.action()));
            else {
                var entry = entries.stream().filter(e -> e.action().equalsIgnoreCase(a[2])).findFirst().orElse(null);
                if (entry != null) {
                    String[] parameters = entry.parameters().split(" ");
                    int index = a.length - 4;
                    if (index < parameters.length) addParameter(candidates, parameters[index], a);
                }
            }
        }
        String partial = a[a.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(partial)).sorted().toList();
    }

    private static void addParameter(Set<String> values, String parameter, String[] a) {
        var plugin = WorldDynamicsEngine.getInstance();
        String token = parameter.replaceAll("[<>\\[\\]]", "");
        if (token.equals("player|none") || token.equals("ideology|none")) {
            addParameter(values, token.substring(0, token.indexOf('|')), a);
            values.add("none");
            return;
        }
        if (token.contains("|")) { values.addAll(Arrays.asList(token.split("\\|"))); return; }
        switch (token) {
            case "reason" -> Arrays.stream(com.thatguycy.worlddynamicsengine.model.war.WarReason.values())
                    .filter(r -> r != com.thatguycy.worlddynamicsengine.model.war.WarReason.INDEPENDENCE)
                    .forEach(r -> values.add(r.name().toLowerCase(Locale.ROOT)));
            case "nation", "chairmanNation", "attackerNation", "defenderNation", "opponent", "receiverNation" ->
                    plugin.getNationManager().getAllNations().forEach(n -> values.add(n.getNationName()));
            case "town" -> plugin.getTownManager().getAllTowns().forEach(t -> values.add(t.getTownName()));
            case "org" -> values.addAll(plugin.getOrgManager().getAllOrganizationNames());
            case "ideology" -> values.addAll(plugin.getReligionManager().getAllReligionNames());
            case "player", "founderPlayer", "candidate" -> {
                plugin.getResidentManager().getAllResidents().forEach(r -> values.add(r.getUsername()));
                com.palmergames.bukkit.towny.TownyUniverse.getInstance().getResidents().forEach(r -> values.add(r.getName()));
            }
            case "target" -> {
                if (a[1].equalsIgnoreCase("ideology") && a.length > 4) {
                    addParameter(values, a[3], a);
                } else plugin.getNationManager().getAllNations().forEach(n -> values.add(n.getNationName()));
            }
            case "subject" -> {
                var org = plugin.getOrgManager().getOrganization(a[3]);
                if (org != null) org.getMemberNationNames().stream().filter(n -> !n.equalsIgnoreCase(org.getChairmanNationName())).forEach(values::add);
            }
            case "type" -> values.addAll(List.of("TRADE_ALLIANCE", "MILITARY_ALLIANCE", "SOVEREIGN_EMPIRE", "IDEOLOGICAL_ALLIANCE"));
            case "id" -> {
                RoleBackgroundStage stage = RoleBackgroundStage.fromString(a[4]);
                if (stage != null) values.addAll(plugin.getBackgroundManager().getBackgroundIds(stage));
            }
            case "doctrine" -> plugin.getReligionManager().getRegisteredDoctrines().forEach(d -> values.add(d.getId()));
            case "attitude" -> {
                var doctrine = plugin.getReligionManager().getDoctrine(a[4]);
                if (doctrine != null) values.addAll(doctrine.getAttitudes().keySet());
            }
            case "meme1", "meme2", "meme3", "meme4" -> values.addAll(plugin.getReligionManager().getValidMemes());
            case "value" -> {
                if (a.length > 5) switch (a[5].toLowerCase(Locale.ROOT)) {
                    case "tier" -> values.addAll(List.of("DOMINION", "PROTECTORATE", "PUPPET"));
                    case "garrison", "duty" -> values.addAll(List.of("enabled", "disabled"));
                    case "suppression" -> values.addAll(List.of("0", "50", "100"));
                    default -> {}
                }
            }
            case "confirm" -> values.add("confirm");
            default -> {}
        }
    }
}
