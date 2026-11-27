package com.thatguycy.worlddynamicsengine.command.background;

import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundDefinition;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 处理两阶段背景的查看和选择；选择顺序、不可变更及奖励发放由背景管理器校验。
 */
public class BackgroundCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    private final BackgroundManager backgroundManager;
    private final ResidentManager residentManager;

    public BackgroundCommand(WorldDynamicsEngine plugin, BackgroundManager backgroundManager, ResidentManager residentManager) {
        this.plugin = plugin;
        this.backgroundManager = backgroundManager;
        this.residentManager = residentManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!backgroundManager.isEnabled()) {
            sender.sendMessage(TextColors.RED + msg("background_disabled"));
            return true;
        }

        if (sender instanceof Player player && (args.length < 2 || args[1].equalsIgnoreCase("menu"))) {
            plugin.getPlayerMenuManager().openBackground(player);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(TextColors.RED + msg("background_usage_root"));
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "status" -> handleStatus(sender);
            case "choose" -> handleChoose(sender, args);
            default -> {
                sender.sendMessage(TextColors.RED + msg("background_usage_root"));
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextColors.RED + msg("background_usage_list"));
            return true;
        }

        RoleBackgroundStage stage = RoleBackgroundStage.fromString(args[2]);
        if (stage == null) {
            sender.sendMessage(TextColors.RED + msg("background_invalid_stage"));
            return true;
        }

        List<RoleBackgroundDefinition> definitions = backgroundManager.getDefinitions(stage);
        sender.sendMessage(TextColors.GOLD + msg("background_list_header", msg(stage.getLocaleKey()), definitions.size()));
        if (definitions.isEmpty()) {
            sender.sendMessage(TextColors.GRAY + msg("background_list_empty"));
            return true;
        }

        Player viewer = sender instanceof Player player ? player : null;
        for (RoleBackgroundDefinition definition : definitions) {
            sender.sendMessage(TextColors.YELLOW + "- " + definition.getId() + TextColors.WHITE + " : "
                    + backgroundManager.resolveText(viewer, definition.getTitle()));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("background_usage_info"));
            return true;
        }

        RoleBackgroundStage stage = RoleBackgroundStage.fromString(args[2]);
        if (stage == null) {
            sender.sendMessage(TextColors.RED + msg("background_invalid_stage"));
            return true;
        }

        RoleBackgroundDefinition definition = backgroundManager.getDefinition(stage, args[3]);
        if (definition == null) {
            sender.sendMessage(TextColors.RED + msg("background_not_found", args[3]));
            return true;
        }

        Player viewer = sender instanceof Player player ? player : null;
        String title = backgroundManager.resolveText(viewer, definition.getTitle());
        sender.sendMessage(TextColors.GOLD + msg("background_info_header", title));
        sender.sendMessage(TextColors.YELLOW + msg("background_info_stage") + TextColors.WHITE + msg(stage.getLocaleKey()));
        sender.sendMessage(TextColors.YELLOW + msg("background_info_id") + TextColors.WHITE + definition.getId());
        sender.sendMessage(TextColors.YELLOW + msg("background_info_title") + TextColors.WHITE + title);
        sender.sendMessage(TextColors.YELLOW + msg("background_info_lore"));
        List<String> resolvedLore = backgroundManager.resolveLore(viewer, definition);
        if (resolvedLore.isEmpty()) {
            sender.sendMessage(TextColors.GRAY + msg("common_none"));
        } else {
            for (String line : resolvedLore) {
                sender.sendMessage(TextColors.GRAY + "- " + line);
            }
        }

        sender.sendMessage(TextColors.YELLOW + msg("background_info_skill_bonuses"));
        if (definition.getSkillLevelBonuses().isEmpty()) {
            sender.sendMessage(TextColors.GRAY + msg("common_none"));
        } else {
            for (Map.Entry<String, Integer> entry : definition.getSkillLevelBonuses().entrySet()) {
                sender.sendMessage(TextColors.GRAY + msg("background_info_skill_bonus_entry",
                        backgroundManager.getAuraSkillDisplayName(viewer, entry.getKey()), entry.getValue()));
            }
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        sender.sendMessage(TextColors.GOLD + msg("background_status_header"));
        sendStageStatus(player, resident, RoleBackgroundStage.CHILDHOOD);
        sendStageStatus(player, resident, RoleBackgroundStage.ADULTHOOD);

        RoleBackgroundStage nextPendingStage = backgroundManager.getNextPendingStage(resident);
        if (nextPendingStage == null) {
            sender.sendMessage(TextColors.GREEN + msg("background_status_complete"));
        } else {
            sender.sendMessage(TextColors.YELLOW + msg("background_status_pending", msg(nextPendingStage.getLocaleKey())));
        }
        return true;
    }

    private void sendStageStatus(Player sender, WDEresident resident, RoleBackgroundStage stage) {
        String selectedId = backgroundManager.getSelectedBackgroundId(resident, stage);
        if (selectedId.isBlank()) {
            sender.sendMessage(TextColors.YELLOW + msg("background_status_stage_line", msg(stage.getLocaleKey()), msg("common_none")));
            return;
        }

        String title = resident.getBackgroundTitle(stage);
        String display = title.isBlank() ? selectedId
                : backgroundManager.resolveText(sender, title) + " (" + selectedId + ")";
        sender.sendMessage(TextColors.YELLOW + msg("background_status_stage_line", msg(stage.getLocaleKey()), display));
    }

    private boolean handleChoose(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(TextColors.RED + msg("background_usage_choose"));
            return true;
        }

        RoleBackgroundStage stage = RoleBackgroundStage.fromString(args[2]);
        if (stage == null) {
            sender.sendMessage(TextColors.RED + msg("background_invalid_stage"));
            return true;
        }

        RoleBackgroundDefinition definition = backgroundManager.getDefinition(stage, args[3]);
        if (definition == null) {
            sender.sendMessage(TextColors.RED + msg("background_not_found", args[3]));
            return true;
        }

        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        if (backgroundManager.hasCompletedSelection(resident)) {
            sender.sendMessage(TextColors.RED + msg("background_selection_already_completed"));
            return true;
        }
        if (backgroundManager.hasSelectedStage(resident, stage)) {
            sender.sendMessage(TextColors.RED + msg("background_stage_already_selected", msg(stage.getLocaleKey())));
            return true;
        }

        RoleBackgroundStage nextPendingStage = backgroundManager.getNextPendingStage(resident);
        if (nextPendingStage != stage) {
            sender.sendMessage(TextColors.RED + msg("background_choose_must_follow_order", msg(nextPendingStage.getLocaleKey())));
            return true;
        }

        plugin.getPlayerMenuManager().openBackgroundConfirmation(player, stage, definition.getId());
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(TextColors.RED + msg("background_only_players"));
        return null;
    }

    private String msg(String key, Object... args) {
        return plugin.formatLocaleMessage(key, args);
    }
}
