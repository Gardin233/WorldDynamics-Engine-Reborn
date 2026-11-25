package com.thatguycy.worlddynamicsengine.command;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.command.admin.AdminCommand;
import com.thatguycy.worlddynamicsengine.command.background.BackgroundCommand;
import com.thatguycy.worlddynamicsengine.command.diplomacy.ContractCommand;
import com.thatguycy.worlddynamicsengine.command.diplomacy.DiplomacyCommand;
import com.thatguycy.worlddynamicsengine.command.economy.GiftCommand;
import com.thatguycy.worlddynamicsengine.command.governance.NationCommand;
import com.thatguycy.worlddynamicsengine.command.governance.TownCommand;
import com.thatguycy.worlddynamicsengine.command.history.HistoryCommand;
import com.thatguycy.worlddynamicsengine.command.ideology.MoodCommand;
import com.thatguycy.worlddynamicsengine.command.ideology.ReligionCommand;
import com.thatguycy.worlddynamicsengine.command.inn.InnCommand;
import com.thatguycy.worlddynamicsengine.command.organization.OrgCommand;
import com.thatguycy.worlddynamicsengine.command.system.DebugCommand;
import com.thatguycy.worlddynamicsengine.command.system.DocCommand;
import com.thatguycy.worlddynamicsengine.command.system.HelpCommand;
import com.thatguycy.worlddynamicsengine.command.system.ReloadCommand;
import com.thatguycy.worlddynamicsengine.command.war.RallyCommand;
import com.thatguycy.worlddynamicsengine.command.war.WarCommand;
import com.thatguycy.worlddynamicsengine.manager.inn.InnManager;

/** Assembles the root command and its handlers on startup and reload. */
public final class CommandRegistration {
    private CommandRegistration() {}

    public static void register(WorldDynamicsEngine plugin, InnManager innManager) {
        var rootCommand = plugin.getCommand("wde");
        if (rootCommand == null) {
            throw new IllegalStateException("Command 'wde' is not defined in plugin.yml");
        }

        var nations = plugin.getNationManager();
        var towns = plugin.getTownManager();
        var residents = plugin.getResidentManager();
        var organizations = plugin.getOrgManager();
        var religions = plugin.getReligionManager();
        var economy = plugin.getEconomyBridge();
        var handler = new CommandHandler(plugin);
        handler.registerSubCommand("help", new HelpCommand());
        handler.registerSubCommand("admin", new AdminCommand(plugin));
        handler.registerSubCommand("docs", new DocCommand());
        handler.registerSubCommand("debug", new DebugCommand(plugin));
        handler.registerSubCommand("reload", new ReloadCommand(plugin));
        handler.registerSubCommand("inn", new InnCommand(plugin, innManager));
        handler.registerSubCommand("rally", new RallyCommand(plugin));
        handler.registerSubCommand("war", new WarCommand(plugin));
        handler.registerSubCommand("history", new HistoryCommand(plugin));
        handler.registerSubCommand("background", new BackgroundCommand(
                plugin, plugin.getBackgroundManager(), residents));
        handler.registerSubCommand("menu", new MenuCommand(plugin));
        handler.registerSubCommand("town", new TownCommand(towns));
        handler.registerSubCommand("nation", new NationCommand(nations, residents));
        handler.registerSubCommand("org", new OrgCommand(nations, towns, organizations, economy));
        handler.registerSubCommand("ideology", new ReligionCommand(
                nations, towns, residents, religions, economy,
                plugin.getIdeologyChurchManager(), plugin.getIdeologyRitualManager()));
        handler.registerSubCommand("mood", new MoodCommand(plugin));
        handler.registerSubCommand("diplomacy", new DiplomacyCommand(nations, residents, economy, organizations));
        handler.registerSubCommand("contract", new ContractCommand(plugin, plugin.getContractManager()));
        var giftCommand = new GiftCommand(plugin, plugin.getPlayerGiftManager());
        handler.registerSubCommand("gift", giftCommand);
        handler.registerSubCommand("alms", giftCommand);
        rootCommand.setTabCompleter(new MyTabCompleter(organizations, religions));
    }
}
