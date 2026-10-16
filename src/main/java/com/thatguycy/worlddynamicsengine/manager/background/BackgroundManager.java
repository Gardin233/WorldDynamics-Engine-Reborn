package com.thatguycy.worlddynamicsengine.manager.background;

import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundDefinition;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 加载两阶段背景并持久化选择，校验不可变更规则，同时跟踪 AuraSkills 奖励避免重复发放。
 */
public class BackgroundManager {
    private final WorldDynamicsEngine plugin;
    private final ResidentManager residentManager;
    private final Map<RoleBackgroundStage, Map<String, RoleBackgroundDefinition>> definitions;
    private boolean enabled;
    private boolean forceSelectionOnFirstJoin;

    public BackgroundManager(WorldDynamicsEngine plugin, ResidentManager residentManager) {
        this.plugin = plugin;
        this.residentManager = residentManager;
        this.definitions = new EnumMap<>(RoleBackgroundStage.class);
        reloadDefinitions();
    }

    public void reloadDefinitions() {
        definitions.clear();
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            definitions.put(stage, new LinkedHashMap<>());
        }

        FileConfiguration config = plugin.getBackgroundConfig();
        enabled = config != null && config.getBoolean("enabled", false);
        forceSelectionOnFirstJoin = config != null && config.getBoolean("forceSelectionOnFirstJoin", false);
        if (config == null) {
            return;
        }

        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            ConfigurationSection backgroundsSection = config.getConfigurationSection("stages." + stage.getConfigKey() + ".backgrounds");
            if (backgroundsSection == null) {
                continue;
            }

            for (String backgroundId : backgroundsSection.getKeys(false)) {
                if (backgroundId == null || backgroundId.isBlank()) {
                    continue;
                }
                String normalizedId = backgroundId.trim().toLowerCase(Locale.ROOT);
                String basePath = "stages." + stage.getConfigKey() + ".backgrounds." + backgroundId;
                String title = config.getString(basePath + ".title", backgroundId);
                List<String> lore = config.getStringList(basePath + ".lore");
                Map<String, Integer> skillLevelBonuses = new LinkedHashMap<>();
                ConfigurationSection bonusSection = config.getConfigurationSection(basePath + ".skillLevelBonuses");
                if (bonusSection != null) {
                    for (String skillId : bonusSection.getKeys(false)) {
                        int increment = Math.max(0, bonusSection.getInt(skillId, 0));
                        if (increment > 0) {
                            skillLevelBonuses.put(skillId.trim(), increment);
                        }
                    }
                }

                definitions.get(stage).put(normalizedId, new RoleBackgroundDefinition(stage, normalizedId, title, lore, skillLevelBonuses));
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isForceSelectionOnFirstJoin() {
        return forceSelectionOnFirstJoin;
    }

    public List<RoleBackgroundDefinition> getDefinitions(RoleBackgroundStage stage) {
        Map<String, RoleBackgroundDefinition> stageDefinitions = definitions.get(stage);
        return stageDefinitions == null ? new ArrayList<>() : new ArrayList<>(stageDefinitions.values());
    }

    public List<String> getBackgroundIds(RoleBackgroundStage stage) {
        Map<String, RoleBackgroundDefinition> stageDefinitions = definitions.get(stage);
        return stageDefinitions == null ? new ArrayList<>() : new ArrayList<>(stageDefinitions.keySet());
    }

    public List<String> resolveLore(Player player, RoleBackgroundDefinition definition) {
        if (definition == null) {
            return new ArrayList<>();
        }

        List<String> resolvedLore = new ArrayList<>();
        for (String line : definition.getLore()) {
            resolvedLore.add(resolvePlayerPlaceholder(player, line));
        }
        return resolvedLore;
    }

    public RoleBackgroundDefinition getDefinition(RoleBackgroundStage stage, String backgroundId) {
        if (stage == null || backgroundId == null || backgroundId.isBlank()) {
            return null;
        }
        Map<String, RoleBackgroundDefinition> stageDefinitions = definitions.get(stage);
        return stageDefinitions == null ? null : stageDefinitions.get(backgroundId.trim().toLowerCase(Locale.ROOT));
    }

    public RoleBackgroundStage getNextPendingStage(WDEresident resident) {
        if (resident == null || !resident.hasSelectedChildhoodBackground()) {
            return RoleBackgroundStage.CHILDHOOD;
        }
        if (!resident.hasSelectedAdulthoodBackground()) {
            return RoleBackgroundStage.ADULTHOOD;
        }
        return null;
    }

    public boolean hasCompletedSelection(WDEresident resident) {
        return resident != null && resident.hasSelectedChildhoodBackground() && resident.hasSelectedAdulthoodBackground();
    }

    public boolean hasSelectedStage(WDEresident resident, RoleBackgroundStage stage) {
        if (resident == null || stage == null) {
            return false;
        }
        return switch (stage) {
            case CHILDHOOD -> resident.hasSelectedChildhoodBackground();
            case ADULTHOOD -> resident.hasSelectedAdulthoodBackground();
        };
    }

    public String getSelectedBackgroundId(WDEresident resident, RoleBackgroundStage stage) {
        if (resident == null || stage == null) {
            return "";
        }
        return switch (stage) {
            case CHILDHOOD -> resident.getChildhoodBackgroundId();
            case ADULTHOOD -> resident.getAdulthoodBackgroundId();
        };
    }

    public boolean needsImmediateSelection(WDEresident resident) {
        return enabled && forceSelectionOnFirstJoin && resident != null && !hasCompletedSelection(resident);
    }

    public boolean shouldLockMovement(Player player) {
        if (player == null) {
            return false;
        }
        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        return needsImmediateSelection(resident);
    }

    public void promptSelectionIfNeeded(Player player) {
        if (player == null || !enabled) {
            return;
        }

        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        if (!needsImmediateSelection(resident)) {
            return;
        }
        RoleBackgroundStage nextStage = getNextPendingStage(resident);
        if (nextStage == null) {
            return;
        }

        player.sendMessage(plugin.formatLocaleMessage("background_forced_prompt_header"));
        player.sendMessage(plugin.formatLocaleMessage("background_forced_prompt_stage", plugin.getLocaleMessage(nextStage.getLocaleKey())));
        player.sendMessage(plugin.formatLocaleMessage("background_forced_prompt_usage_list", nextStage.getConfigKey()));
        player.sendMessage(plugin.formatLocaleMessage("background_forced_prompt_usage_choose", nextStage.getConfigKey()));
        player.sendMessage(plugin.formatLocaleMessage("background_move_locked"));
        if (player.isOnline() && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().openBackground(player);
        }
    }

    public SelectionApplyResult selectBackground(Player player, RoleBackgroundStage stage, RoleBackgroundDefinition definition) {
        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        if (!enabled || stage == null || definition == null
                || getDefinition(stage, definition.getId()) != definition
                || getNextPendingStage(resident) != stage) {
            throw new IllegalStateException("Background selection is disabled, stale, or out of order.");
        }
        switch (stage) {
            case CHILDHOOD -> resident.setChildhoodBackgroundId(definition.getId());
            case ADULTHOOD -> resident.setAdulthoodBackgroundId(definition.getId());
        }

        boolean bonusApplied = applyBackgroundBonusesIfPossible(player, resident, definition, true);
        residentManager.saveResidents();
        return new SelectionApplyResult(definition, bonusApplied, !bonusApplied && !definition.getSkillLevelBonuses().isEmpty(), getNextPendingStage(resident));
    }

    public void applyPendingBonuses(Player player) {
        if (player == null || !enabled) {
            return;
        }

        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        boolean changed = false;
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            String backgroundId = getSelectedBackgroundId(resident, stage);
            if (backgroundId.isBlank() || resident.hasAppliedBackgroundBonus(backgroundId)) {
                continue;
            }

            RoleBackgroundDefinition definition = getDefinition(stage, backgroundId);
            if (definition != null && applyBackgroundBonusesIfPossible(player, resident, definition, false)) {
                changed = true;
            }
        }

        if (changed) {
            residentManager.saveResidents();
        }
    }

    private boolean applyBackgroundBonusesIfPossible(Player player, WDEresident resident, RoleBackgroundDefinition definition, boolean notifyPlayer) {
        if (definition == null || resident == null) {
            return false;
        }
        if (resident.hasAppliedBackgroundBonus(definition.getId())) {
            return true;
        }
        if (definition.getSkillLevelBonuses().isEmpty()) {
            resident.markBackgroundBonusApplied(definition.getId());
            return true;
        }

        Plugin auraSkillsPlugin = plugin.getServer().getPluginManager().getPlugin("AuraSkills");
        if (auraSkillsPlugin == null || !auraSkillsPlugin.isEnabled()) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object api = apiClass.getMethod("get").invoke(null);
            if (api == null) {
                return false;
            }

            Object user = apiClass.getMethod("getUser", UUID.class).invoke(api, player.getUniqueId());
            if (user == null) {
                return false;
            }

            Method isLoadedMethod = user.getClass().getMethod("isLoaded");
            if (!Boolean.TRUE.equals(isLoadedMethod.invoke(user))) {
                return false;
            }

            Object globalRegistry = apiClass.getMethod("getGlobalRegistry").invoke(api);
            Class<?> namespacedIdClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId");
            Method namespacedIdOfMethod = namespacedIdClass.getMethod("of", String.class, String.class);
            Method getSkillMethod = globalRegistry.getClass().getMethod("getSkill", namespacedIdClass);
            Method getSkillLevelMethod = user.getClass().getMethod("getSkillLevel", Class.forName("dev.aurelium.auraskills.api.skill.Skill"));
            Method setSkillLevelMethod = user.getClass().getMethod("setSkillLevel", Class.forName("dev.aurelium.auraskills.api.skill.Skill"), int.class);

            int changedSkills = 0;
            for (Map.Entry<String, Integer> entry : definition.getSkillLevelBonuses().entrySet()) {
                ParsedAuraSkillBonus parsed = parseAuraSkillBonus(entry.getKey(), entry.getValue());
                if (parsed == null) {
                    continue;
                }

                Object skillId = namespacedIdOfMethod.invoke(null, parsed.namespace(), parsed.key());
                Object skill = getSkillMethod.invoke(globalRegistry, skillId);
                if (skill == null) {
                    plugin.getLogger().warning("Unknown AuraSkills skill id in background " + definition.getId() + ": " + entry.getKey());
                    continue;
                }

                int currentLevel = (int) getSkillLevelMethod.invoke(user, skill);
                int maxLevel = (int) skill.getClass().getMethod("getMaxLevel").invoke(skill);
                int targetLevel = Math.min(maxLevel, currentLevel + parsed.increment());
                if (targetLevel > currentLevel) {
                    setSkillLevelMethod.invoke(user, skill, targetLevel);
                    changedSkills++;
                }
            }

            resident.markBackgroundBonusApplied(definition.getId());
            if (notifyPlayer && changedSkills > 0) {
                player.sendMessage(plugin.formatLocaleMessage("background_auraskills_bonus_applied",
                        definition.getTitle(), changedSkills));
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to apply AuraSkills bonuses for background " + definition.getId() + ": " + ex.getMessage());
            return false;
        }
    }

    private ParsedAuraSkillBonus parseAuraSkillBonus(String rawSkillId, int increment) {
        if (rawSkillId == null || rawSkillId.isBlank() || increment <= 0) {
            return null;
        }

        String normalized = rawSkillId.trim();
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                return new ParsedAuraSkillBonus(parts[0], parts[1], increment);
            }
        }
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                return new ParsedAuraSkillBonus(parts[0], parts[1], increment);
            }
        }
        return new ParsedAuraSkillBonus("auraskills", normalized, increment);
    }

    private String resolvePlayerPlaceholder(Player player, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return rawText == null ? "" : rawText;
        }
        if (player == null) {
            return rawText;
        }
        return rawText.replace("%player_name%", player.getName());
    }

    /**
     * 返回背景选择结果及技能奖励发放情况，供命令或菜单反馈。
     */
    public record SelectionApplyResult(RoleBackgroundDefinition definition,
                                       boolean bonusesAppliedNow,
                                       boolean bonusesPending,
                                       RoleBackgroundStage nextPendingStage) {
    }

    /**
     * 保存解析后的技能命名空间、键和正向等级增量。
     */
    private record ParsedAuraSkillBonus(String namespace, String key, int increment) {
    }
}
