package com.thatguycy.worlddynamicsengine.manager.menu;

import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundDefinition;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Renders and handles the childhood/adulthood background selection pages. */
final class BackgroundMenu {
    private final MenuPageContext context;

    BackgroundMenu(MenuPageContext context) {
        this.context = context;
    }

    void open(Player player) {
        var backgrounds = context.backgrounds();
        if (!backgrounds.isEnabled()) {
            player.sendMessage(context.text("unavailable"));
            return;
        }
        MenuView menu = context.menu(player, context.text("background"));
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            String id = backgrounds.getSelectedBackgroundId(context.resident(player), stage);
            RoleBackgroundDefinition selected = backgrounds.getDefinition(stage, id);
            List<String> lore = new ArrayList<>();
            lore.add(id.isBlank() ? context.text("pending")
                    : context.text("selected") + " " + (selected == null ? id : selected.getTitle()));
            lore.add(context.text("irreversible"));
            boolean locked = stage == RoleBackgroundStage.ADULTHOOD
                    && !backgrounds.hasSelectedStage(context.resident(player), RoleBackgroundStage.CHILDHOOD);
            if (locked) lore.add(context.text("locked"));
            context.button(menu, stage == RoleBackgroundStage.CHILDHOOD ? 20 : 24,
                    locked ? Material.GRAY_DYE : Material.BOOK, stageName(stage), lore,
                    () -> {
                        if (!locked) openList(player, stage, 0);
                    });
        }
        context.back(menu, () -> context.openHome(player));
        player.openInventory(menu.inventory);
    }

    private String stageName(RoleBackgroundStage stage) {
        return context.text(stage == RoleBackgroundStage.CHILDHOOD ? "childhood" : "adulthood");
    }

    private List<String> backgroundLore(Player player, RoleBackgroundDefinition definition) {
        var backgrounds = context.backgrounds();
        List<String> lore = new ArrayList<>(backgrounds.resolveLore(player, definition));
        for (var entry : definition.getSkillLevelBonuses().entrySet()) {
            String skill = entry.getKey().replace(':', '/');
            skill = skill.substring(skill.lastIndexOf('/') + 1);
            lore.add(context.text("bonus", context.plugin().getLocaleMessage("ui.skills." + skill, skill),
                    entry.getValue()));
        }
        return lore;
    }

    void openList(Player player, RoleBackgroundStage stage, int page) {
        var backgrounds = context.backgrounds();
        if (!backgrounds.isEnabled()) return;
        List<RoleBackgroundDefinition> entries = backgrounds.getDefinitions(stage);
        MenuView menu = context.menu(player, context.text("background") + " - " + stageName(stage));
        int start = context.paginate(menu, entries.size(), page, next -> openList(player, stage, next));
        for (int i = start; i < Math.min(start + 45, entries.size()); i++) {
            RoleBackgroundDefinition definition = entries.get(i);
            List<String> lore = backgroundLore(player, definition);
            lore.add(context.text("preview"));
            context.button(menu, i - start, Material.BOOK, definition.getTitle(), lore,
                    () -> openDetail(player, stage, definition.getId(), page));
        }
        context.back(menu, () -> open(player));
        player.openInventory(menu.inventory);
    }

    void openDetail(Player player, RoleBackgroundStage stage, String id, int page) {
        var backgrounds = context.backgrounds();
        RoleBackgroundDefinition definition = backgrounds.getDefinition(stage, id);
        if (definition == null || !backgrounds.isEnabled()) return;
        MenuView menu = context.menu(player, context.text("background"));
        context.button(menu, 22, Material.WRITTEN_BOOK, definition.getTitle(), backgroundLore(player, definition), null);
        if (!backgrounds.hasSelectedStage(context.resident(player), stage)
                && backgrounds.getNextPendingStage(context.resident(player)) == stage) {
            context.button(menu, 30, Material.LIME_DYE, context.text("choose"),
                    List.of(context.text("irreversible")),
                    () -> context.confirm(player, definition.getTitle(), List.of(context.text("irreversible")),
                            () -> openDetail(player, stage, id, page), () -> {
                                RoleBackgroundDefinition latest = backgrounds.getDefinition(stage, id);
                                if (latest != definition || !backgrounds.isEnabled()) {
                                    player.sendMessage(context.text("stale"));
                                } else {
                                    execute(player, stage, id);
                                }
                                RoleBackgroundStage next = backgrounds.getNextPendingStage(context.resident(player));
                                if (next != null && backgrounds.isEnabled()) openList(player, next, 0);
                                else open(player);
                            }));
        } else {
            context.button(menu, 30, Material.GRAY_DYE, context.text("selected"),
                    List.of(context.text("irreversible")), null);
        }
        context.back(menu, () -> openList(player, stage, page));
        player.openInventory(menu.inventory);
    }

    private void execute(Player player, RoleBackgroundStage stage, String id) {
        var backgrounds = context.backgrounds();
        String before = backgrounds.getSelectedBackgroundId(context.resident(player), stage);
        context.executeCommand(player, "background", "choose", stage.getConfigKey(), id);
        if (!before.equals(backgrounds.getSelectedBackgroundId(context.resident(player), stage))) {
            context.success(player);
        }
    }
}
