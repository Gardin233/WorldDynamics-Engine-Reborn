package com.thatguycy.worlddynamicsengine.command.system;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * 触发插件内置配置热重载并反馈结果；存储连接的变更仍需重启服务器。
 */
public class ReloadCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;

    public ReloadCommand(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("wde.reload")) {
            sender.sendMessage(ChatColor.RED + msg("reload_no_permission"));
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + msg("reload_in_progress"));
        try {
            WorldDynamicsEngine.ReloadResult result = plugin.reloadPluginState();
            sender.sendMessage(ChatColor.GREEN + msg("reload_success"));
            if (result.storageChanged()) {
                sender.sendMessage(ChatColor.YELLOW + msg("reload_storage_changed_requires_restart"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reload plugin state: " + e.getMessage());
            sender.sendMessage(ChatColor.RED + msg("reload_failed", e.getMessage()));
        }
        return true;
    }

    private String msg(String key, Object... args) {
        return plugin.formatLocaleMessage(key, args);
    }
}
