package com.thatguycy.worlddynamicsengine.manager.menu;

import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager.AuraSkillEntry;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundPointRules;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundDefinition;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Duration;
import java.util.function.BiConsumer;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.thatguycy.worlddynamicsengine.util.TextColors;

/** Renders and handles the childhood/adulthood background selection pages. */
final class BackgroundMenu {
    private static final int POINTS = BackgroundPointRules.REQUIRED_POINTS;
    private static final int LIST_PAGE_SIZE = 8;
    private static final int SKILL_PAGE_SIZE = 4;
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
        var resident = context.resident(player);
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(component(context.text("background_stage_picker_hint")), 420));
        List<ActionButton> actions = new ArrayList<>();
        RoleBackgroundStage nextPending = backgrounds.getNextPendingStage(resident);
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            String id = backgrounds.getSelectedBackgroundId(resident, stage);
            String selectedTitle = resident.getBackgroundTitle(stage);
            boolean selected = !id.isBlank();
            boolean locked = stage == RoleBackgroundStage.ADULTHOOD
                    && nextPending == RoleBackgroundStage.CHILDHOOD;
            String stageStatus = selected
                    ? backgrounds.resolveText(player, selectedTitle.isBlank() ? id : selectedTitle)
                    : context.text("pending");
            if (locked) stageStatus = context.text("locked");
            body.add(DialogBody.plainMessage(
                    component(context.text("background_status_stage_title", stageName(stage), stageStatus)), 420));

            String buttonLabel = locked
                    ? context.text("background_stage_locked_button", stageName(stage))
                    : selected
                            ? context.text("background_stage_selected_button", stageName(stage),
                                    backgrounds.resolveText(player, selectedTitle.isBlank() ? id : selectedTitle))
                            : context.text("background_stage_choose_button", stageName(stage));
            actions.add(action(player, buttonLabel, (viewer, response) -> {
                if (locked) {
                    viewer.sendMessage(context.text("background_stage_locked"));
                    open(viewer);
                } else {
                    openList(viewer, stage, 0);
                }
            }));
        }
        actions.add(action(player, context.text("background_editor_cancel"),
                (viewer, response) -> context.openHome(viewer)));
        show(player, dialog(component(context.text("background_stage_picker_title")), body, List.of()),
                DialogType.multiAction(actions).columns(2).build());
    }

    boolean openCommunityBackgroundAdminEditor(Player player, String backgroundId) {
        if (player == null || !player.hasPermission("wde.admin.background")) {
            return false;
        }
        var backgrounds = context.backgrounds();
        RoleBackgroundDefinition definition = backgrounds.getCommunityDefinition(backgroundId);
        if (definition == null) {
            return false;
        }

        List<String> details = new ArrayList<>();
        details.add(context.text("background_admin_editor_hint"));
        details.add(context.text("background_workshop_author", definition.getAuthor()));
        details.add(context.text("background_admin_editor_skill_hint"));
        definition.getSkillPoints().forEach((skill, points) -> details.add(context.text(
                "background_status_skill_entry",
                backgrounds.getAuraSkillDisplayName(player, skill), points)));

        DialogBase base = DialogBase.builder(component(
                        context.text("background_admin_editor_title", definition.getTitle())))
                .body(List.of(DialogBody.plainMessage(
                        component(String.join("\n", details)), 420)))
                .inputs(List.of(
                        DialogInput.text("background_title",
                                        component(context.text("background_editor_title")))
                                .width(400)
                                .initial(definition.getTitle())
                                .maxLength(64)
                                .build(),
                        DialogInput.text("background_story",
                                        component(context.text("background_editor_story")))
                                .width(400)
                                .initial(String.join("\n", definition.getLore()))
                                .maxLength(1024)
                                .multiline(TextDialogInput.MultilineOptions.create(12, 240))
                                .build()))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        Duration lifetime = Duration.ofMinutes(10);
        ActionButton save = ActionButton.builder(
                        component(context.text("background_admin_editor_save")))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player editor
                            && editor.getUniqueId().equals(player.getUniqueId())) {
                        String title = response.getText("background_title");
                        String story = response.getText("background_story");
                        Bukkit.getScheduler().runTask(context.plugin(), () -> {
                            if (!editor.isOnline()) {
                                return;
                            }
                            if (!editor.hasPermission("wde.admin.background")) {
                                editor.sendMessage(context.plugin().getLocaleMessage(editor.getLocale(),
                                        "command_no_permission"));
                                return;
                            }
                            if (backgrounds.updateCommunityBackground(backgroundId, title, story)) {
                                editor.sendMessage(TextColors.translateAlternateColorCodes('&',
                                        context.plugin().formatLocaleMessage(editor.getLocale(),
                                                "admin_background_edited", title)));
                            } else {
                                editor.sendMessage(TextColors.translateAlternateColorCodes('&',
                                        context.plugin().formatLocaleMessage(editor.getLocale(),
                                                "admin_background_edit_failed")));
                                openCommunityBackgroundAdminEditor(editor, backgroundId);
                            }
                        });
                    }
                }, ClickCallback.Options.builder().uses(1).lifetime(lifetime).build()))
                .build();
        ActionButton cancel = ActionButton.builder(component(context.text("background_editor_cancel")))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> { },
                        ClickCallback.Options.builder().uses(1).lifetime(lifetime).build()))
                .build();
        context.showBackgroundDialog(player, Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.confirmation(save, cancel))));
        return true;
    }

    private Component component(String value) {
        return LegacyComponentSerializer.legacySection().deserialize(value == null ? "" : value);
    }

    private String stageName(RoleBackgroundStage stage) {
        return context.text(stage == RoleBackgroundStage.CHILDHOOD ? "childhood" : "adulthood");
    }

    void openList(Player player, RoleBackgroundStage stage, int page) {
        openList(player, stage, page, "");
    }

    private void openList(Player player, RoleBackgroundStage stage, int page, String query) {
        var backgrounds = context.backgrounds();
        if (!backgrounds.isEnabled()) return;
        String keyword = query.toLowerCase(Locale.ROOT);
        List<RoleBackgroundDefinition> entries = backgrounds.getDefinitions(stage).stream()
                .filter(definition -> keyword.isEmpty() || matches(player, definition, keyword)).toList();
        int pages = pageCount(entries.size(), LIST_PAGE_SIZE);
        int current = Math.clamp(page, 0, pages - 1);
        List<ActionButton> actions = new ArrayList<>();
        for (RoleBackgroundDefinition definition : pageEntries(entries, current, LIST_PAGE_SIZE)) {
            actions.add(action(player, Component.text(backgrounds.resolveText(player, definition.getTitle())),
                    (viewer, response) -> openDetail(viewer, stage, definition.getId(), current, query)));
        }
        actions.add(action(player, context.text("background_search_button"), (viewer, response) -> {
            String value = response.getText("query");
            openList(viewer, stage, 0, value == null ? "" : value.trim());
        }));
        actions.add(action(player, context.text("background_create_blank"),
                (viewer, response) -> openEditor(viewer, stage, 0, new Draft("", "", Map.of()))));
        addPageActions(player, actions, current, pages,
                (next, response) -> openList(player, stage, next, query));
        actions.add(action(player, context.text("background_editor_cancel"), (viewer, response) -> open(viewer)));
        var input = DialogInput.text("query", component(context.text("background_search_label")))
                .width(320).initial(query).maxLength(64).build();
        String hint = context.text("background_dialog_choose_hint");
        if (entries.isEmpty()) hint += "\n" + context.text("background_search_empty");
        show(player, dialog(component(stageName(stage)).append(Component.text(" (" + (current + 1) + " / " + pages + ")")),
                List.of(DialogBody.plainMessage(component(hint), 420)), List.of(input)),
                DialogType.multiAction(actions).columns(2).build());
    }

    private boolean matches(Player player, RoleBackgroundDefinition definition, String keyword) {
        var backgrounds = context.backgrounds();
        return java.util.stream.Stream.of(definition.getId(), definition.getAuthor(), definition.getTitle(),
                        String.join("\n", definition.getLore()))
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(keyword))
                || definition.getSkillPoints().keySet().stream().anyMatch(skill ->
                        backgrounds.getAuraSkillDisplayName(player, skill).toLowerCase(Locale.ROOT).contains(keyword));
    }

    void openDetail(Player player, RoleBackgroundStage stage, String id, int page) {
        openDetail(player, stage, id, page, "");
    }

    private void openDetail(Player player, RoleBackgroundStage stage, String id, int page, String query) {
        var backgrounds = context.backgrounds();
        RoleBackgroundDefinition definition = backgrounds.getDefinition(stage, id);
        if (definition == null || !backgrounds.isEnabled()) return;
        String title = backgrounds.resolveText(player, definition.getTitle());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(String.join("\n", backgrounds.resolveLore(player, definition))), 420));
        if (!definition.getAuthor().isBlank()) {
            body.add(DialogBody.plainMessage(component(context.text("background_workshop_author", definition.getAuthor())), 420));
        }
        body.add(DialogBody.plainMessage(component(context.text("background_skill_entries_label") + "\n"
                + String.join("\n", skillLines(player, definition.getSkillPoints()))), 420));
        ActionButton back = action(player, context.text("background_confirmation_back"),
                (viewer, response) -> openList(viewer, stage, page, query));
        if (backgrounds.getNextPendingStage(context.resident(player)) == stage) {
            body.add(DialogBody.plainMessage(component(context.text("irreversible")), 420));
            ActionButton confirm = action(player, context.text("choose"), (viewer, response) -> {
                if (backgrounds.getDefinition(stage, id) != definition || !backgrounds.isEnabled()) {
                    viewer.sendMessage(context.text("stale"));
                    openDetail(viewer, stage, id, page, query);
                    return;
                }
                var result = backgrounds.selectBackground(viewer, stage, definition);
                if (!result.selected()) {
                    viewer.sendMessage(context.text("background_selection_failed"));
                    openList(viewer, stage, page, query);
                    return;
                }
                viewer.sendMessage(context.text("background_status_saved", title));
                if (result.bonusesPending()) {
                    viewer.sendMessage(context.plugin().getLocaleMessage(viewer.getLocale(), "background_choose_bonus_pending"));
                }
                context.success(viewer);
                if (result.nextPendingStage() != null) openList(viewer, result.nextPendingStage(), 0);
                else open(viewer);
            });
            show(player, dialog(Component.text(title), body, List.of()), DialogType.confirmation(confirm, back));
        } else {
            show(player, dialog(Component.text(title), body, List.of()), DialogType.notice(back));
        }
    }

    private void openEditor(Player player, RoleBackgroundStage stage, int page, Draft draft) {
        var backgrounds = context.backgrounds();
        if (!backgrounds.isEnabled()) return;
        List<AuraSkillEntry> skills = backgrounds.getAvailableAuraSkills();
        if (skills.isEmpty()) {
            player.sendMessage(context.text("background_auraskills_unavailable"));
            openList(player, stage, 0);
            return;
        }
        int pages = pageCount(skills.size(), SKILL_PAGE_SIZE);
        int current = Math.clamp(page, 0, pages - 1);
        List<ActionButton> actions = new ArrayList<>();
        List<String> details = new ArrayList<>(List.of(context.text("background_editor_blank_hint"),
                context.text("background_editor_points_total", draft.total(), POINTS)));
        details.addAll(skillLines(player, draft.points()));
        for (AuraSkillEntry skill : pageEntries(skills, current, SKILL_PAGE_SIZE)) {
            String label = backgrounds.getAuraSkillDisplayName(player, skill.id())
                    + " (" + draft.points().getOrDefault(skill.id(), 0) + ")";
            actions.add(action(player, context.text("background_skill_decrease_button", label),
                    (viewer, response) -> adjust(viewer, stage, current, draft.read(response), skill.id(), -1)));
            actions.add(action(player, context.text("background_skill_increase_button", label),
                    (viewer, response) -> adjust(viewer, stage, current, draft.read(response), skill.id(), 1)));
        }
        addPageActions(player, actions, current, pages,
                (next, response) -> openEditor(player, stage, next, draft.read(response)));
        actions.add(action(player, context.text("background_editor_save"),
                (viewer, response) -> publish(viewer, stage, current, draft.read(response))));
        actions.add(action(player, context.text("background_editor_cancel"), (viewer, response) -> openList(viewer, stage, 0)));
        show(player, dialog(component(context.text("background_editor_page_title", stageName(stage), current + 1, pages)),
                        List.of(DialogBody.plainMessage(component(String.join("\n", details)), 420)),
                        List.of(DialogInput.text("background_title", component(context.text("background_editor_title")))
                                        .width(400).initial(draft.title()).maxLength(64).build(),
                                DialogInput.text("background_story", component(context.text("background_editor_story")))
                                        .width(400).initial(draft.story()).maxLength(1024)
                                        .multiline(TextDialogInput.MultilineOptions.create(12, 120)).build())),
                DialogType.multiAction(actions).columns(2).build());
    }

    private void adjust(Player player, RoleBackgroundStage stage, int page, Draft draft, String skill, int delta) {
        Map<String, Integer> points = new LinkedHashMap<>(draft.points());
        int next = points.getOrDefault(skill, 0) + delta;
        if (next < 0) {
            player.sendMessage(context.text("background_skill_point_min"));
        } else if (draft.total() + delta > POINTS) {
            player.sendMessage(context.text("background_points_limit_reached"));
        } else if (next == 0) {
            points.remove(skill);
        } else {
            points.put(skill, next);
        }
        openEditor(player, stage, page, new Draft(draft.title(), draft.story(), points));
    }

    private void publish(Player player, RoleBackgroundStage stage, int page, Draft draft) {
        var backgrounds = context.backgrounds();
        if (draft.title().isBlank() || draft.story().isBlank()) {
            player.sendMessage(context.text("background_custom_fields_required"));
        } else if (!BackgroundPointRules.isValid(draft.points())) {
            player.sendMessage(context.text("background_points_invalid", draft.total(), POINTS));
        } else if (backgrounds.findBlockedBackgroundWord(draft.title(), draft.story()) != null) {
            player.sendMessage(context.text("background_sensitive_word_blocked"));
        } else {
            RoleBackgroundDefinition published = backgrounds.createCommunityBackground(player, stage,
                    draft.title(), draft.story(), draft.points());
            if (published != null) {
                player.sendMessage(context.text("background_workshop_publish_success", published.getId()));
                openDetail(player, stage, published.getId(), 0);
                return;
            }
            player.sendMessage(context.text("background_selection_failed"));
        }
        openEditor(player, stage, page, draft);
    }

    private List<String> skillLines(Player player, Map<String, Integer> points) {
        return points.entrySet().stream().map(entry -> context.text("background_status_skill_entry",
                context.backgrounds().getAuraSkillDisplayName(player, entry.getKey()), entry.getValue())).toList();
    }

    private void addPageActions(Player player, List<ActionButton> actions, int page, int pages,
                                BiConsumer<Integer, DialogResponseView> navigate) {
        if (page > 0) actions.add(action(player, context.text("previous"), (viewer, response) -> navigate.accept(page - 1, response)));
        if (page + 1 < pages) actions.add(action(player, context.text("next"), (viewer, response) -> navigate.accept(page + 1, response)));
    }

    private static int pageCount(int count, int size) {
        return Math.max(1, (count + size - 1) / size);
    }

    private static <T> List<T> pageEntries(List<T> entries, int page, int size) {
        return entries.subList(page * size, Math.min(entries.size(), (page + 1) * size));
    }

    private ActionButton action(Player player, String label, BiConsumer<Player, DialogResponseView> callback) {
        return action(player, component(label), callback);
    }

    private ActionButton action(Player player, Component label, BiConsumer<Player, DialogResponseView> callback) {
        return ActionButton.builder(label).width(200).action(DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player viewer && viewer.getUniqueId().equals(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(context.plugin(), () -> {
                    if (viewer.isOnline() && viewer.hasPermission("wde.player")) callback.accept(viewer, response);
                });
            }
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build())).build();
    }

    private static DialogBase dialog(Component title, List<DialogBody> body, List<DialogInput> inputs) {
        return DialogBase.builder(title).body(body).inputs(inputs).canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE).build();
    }

    private void show(Player player, DialogBase base, DialogType type) {
        context.showBackgroundDialog(player, Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    private record Draft(String title, String story, Map<String, Integer> points) {
        int total() {
            return points.values().stream().mapToInt(Integer::intValue).sum();
        }

        Draft read(DialogResponseView response) {
            String updatedTitle = response.getText("background_title");
            String updatedStory = response.getText("background_story");
            return new Draft(updatedTitle == null ? title : updatedTitle,
                    updatedStory == null ? story : updatedStory, points);
        }
    }
}
