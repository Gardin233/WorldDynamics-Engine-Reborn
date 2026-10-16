package com.thatguycy.worlddynamicsengine.command.system;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * 向玩家或控制台提供文档入口，不承担业务状态修改。
 */
public class DocCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text(WorldDynamicsEngine.getInstance().getLocaleMessage("docs_click_here"), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl("https://github.com/stoleyourharvs/WorldDynamics-Engine/releases/tag/v0.2.1")));
        return true;
    }
}
