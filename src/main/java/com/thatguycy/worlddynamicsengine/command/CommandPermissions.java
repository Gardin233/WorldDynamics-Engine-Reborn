package com.thatguycy.worlddynamicsengine.command;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

/**
 * 集中定义普通命令与管理员模块的权限判断，不以 OP 身份绕过权限插件的明确拒绝。
 */
public final class CommandPermissions {
    public static final List<String> ADMIN_MODULES = List.of(
            "system", "nation", "town", "org", "war", "ideology", "background", "economy");

    private CommandPermissions() {}

    public static boolean admin(CommandSender sender, String module) {
        return sender.hasPermission("wde.admin." + module);
    }

    public static boolean anyAdmin(CommandSender sender) {
        return ADMIN_MODULES.stream().anyMatch(module -> admin(sender, module));
    }

    public static boolean root(CommandSender sender, String root) {
        return switch (root.toLowerCase(Locale.ROOT)) {
            case "admin" -> anyAdmin(sender);
            case "debug" -> sender.hasPermission("wde.debug");
            case "reload" -> sender.hasPermission("wde.reload");
            default -> sender.hasPermission("wde.player");
        };
    }
}
