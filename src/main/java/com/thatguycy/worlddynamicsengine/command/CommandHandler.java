package com.thatguycy.worlddynamicsengine.command;

import com.thatguycy.worlddynamicsengine.command.system.HelpCommand;
import java.util.HashMap;
import java.util.Map;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 分发 /wde 根命令与子命令，先检查入口权限；具体角色资格仍由业务命令校验。
 */
public class CommandHandler implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Map<String, CommandExecutor> subCommandMap;

    public CommandHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.subCommandMap = new HashMap<>();
        plugin.getCommand("wde").setExecutor(this);
    }

    public void registerSubCommand(String name, CommandExecutor executor) {
        subCommandMap.put(name.toLowerCase(), executor);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String root = args.length == 0 ? "menu" : args[0];
        if (!CommandPermissions.root(sender, root)) {
            sender.sendMessage(TextColors.RED + com.thatguycy.worlddynamicsengine.WorldDynamicsEngine.getInstance()
                    .getLocaleMessage("command_no_permission"));
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof org.bukkit.entity.Player player) {
                com.thatguycy.worlddynamicsengine.WorldDynamicsEngine.getInstance().getPlayerMenuManager().openHome(player);
                return true;
            }
            sender.sendMessage(TextColors.RED + com.thatguycy.worlddynamicsengine.WorldDynamicsEngine.getInstance().getLocaleMessage("command_unknown_subcommand_docs"));
            return true;
        }

        if (args.length >= 2 && "help".equalsIgnoreCase(args[1])
                && HelpCommand.supportsModule(args[0])) {
            return HelpCommand.handleModuleHelp(sender, args[0], args);
        }

        CommandExecutor subCommand = subCommandMap.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.onCommand(sender, cmd, label, args);
        }

        sender.sendMessage(TextColors.RED + com.thatguycy.worlddynamicsengine.WorldDynamicsEngine.getInstance().getLocaleMessage("command_unknown_subcommand_docs"));
        return true;
    }
}
