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
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Loads childhood and adulthood presets, validates each five-point allocation, and applies rewards once per stage.
 */
public class BackgroundManager {
    private final WorldDynamicsEngine plugin;
    private final ResidentManager residentManager;
    private final Map<RoleBackgroundStage, Map<String, RoleBackgroundDefinition>> definitions;
    private final Map<String, RoleBackgroundDefinition> roleDefinitions;
    private final Map<String, RoleBackgroundDefinition> communityDefinitions = new LinkedHashMap<>();
    private volatile Map<String, String> auraSkillDisplayNames = Map.of();
    private BackgroundTextFilter backgroundTextFilter = new BackgroundTextFilter(List.of());
    private boolean sensitiveWordFilterEnabled = true;
    private boolean enabled;
    private boolean forceSelectionOnFirstJoin;

    public BackgroundManager(WorldDynamicsEngine plugin, ResidentManager residentManager) {
        this.plugin = plugin;
        this.residentManager = residentManager;
        this.definitions = new EnumMap<>(RoleBackgroundStage.class);
        this.roleDefinitions = new LinkedHashMap<>();
        reloadDefinitions();
    }

    public void reloadDefinitions() {
        definitions.clear();
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            definitions.put(stage, new LinkedHashMap<>());
        }
        roleDefinitions.clear();
        communityDefinitions.clear();

        FileConfiguration config = plugin.getBackgroundConfig();
        enabled = config != null && config.getBoolean("enabled", false);
        forceSelectionOnFirstJoin = config != null && config.getBoolean("forceSelectionOnFirstJoin", false);
        if (config == null) {
            backgroundTextFilter = new BackgroundTextFilter(List.of());
            return;
        }
        sensitiveWordFilterEnabled = config.getBoolean("content-filter.enabled", true);
        backgroundTextFilter = new BackgroundTextFilter(config.getStringList("content-filter.blocked-words"));

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
                Map<String, Integer> skillPoints = readConfiguredValues(
                        config.getConfigurationSection(basePath + ".skillPoints"));
                if (!config.contains(basePath + ".skillPoints")) {
                    skillPoints = distributeLegacyStagePoints(
                            config.getConfigurationSection(basePath + ".skillLevelBonuses"));
                }
                if (!BackgroundPointRules.isValid(skillPoints)) {
                    plugin.getLogger().warning("Ignoring " + stage.getConfigKey() + " background " + backgroundId
                            + ": skillPoints must total exactly " + BackgroundPointRules.REQUIRED_POINTS + ".");
                    continue;
                }
                RoleBackgroundDefinition definition =
                        new RoleBackgroundDefinition(stage, normalizedId, title, lore, skillPoints);
                definitions.get(stage).put(normalizedId, definition);
                roleDefinitions.putIfAbsent(normalizedId, definition);
            }
        }

        ConfigurationSection communitySection = config.getConfigurationSection("community-backgrounds");
        if (communitySection != null) {
            for (String backgroundId : communitySection.getKeys(false)) {
                String basePath = "community-backgrounds." + backgroundId;
                RoleBackgroundStage stage = RoleBackgroundStage.fromString(config.getString(basePath + ".stage"));
                Map<String, Integer> skillPoints = readConfiguredValues(
                        config.getConfigurationSection(basePath + ".skillPoints"));
                if (stage == null || !BackgroundPointRules.isValid(skillPoints)) {
                    plugin.getLogger().warning("Ignoring community background " + backgroundId
                            + ": stage or five-point skill allocation is invalid.");
                    continue;
                }
                String normalizedId = backgroundId.trim().toLowerCase(Locale.ROOT);
                if (roleDefinitions.containsKey(normalizedId)) {
                    plugin.getLogger().warning("Ignoring community background with a duplicate ID: " + backgroundId);
                    continue;
                }
                RoleBackgroundDefinition definition = new RoleBackgroundDefinition(stage, normalizedId,
                        config.getString(basePath + ".title", backgroundId),
                        config.getString(basePath + ".author", ""),
                        config.getStringList(basePath + ".lore"), skillPoints);
                definitions.get(stage).put(normalizedId, definition);
                roleDefinitions.put(normalizedId, definition);
                communityDefinitions.put(normalizedId, definition);
            }
        }

        ConfigurationSection roleSection = config.getConfigurationSection("backgrounds");
        if (roleSection != null) {
            for (String backgroundId : roleSection.getKeys(false)) {
                String basePath = "backgrounds." + backgroundId;
                if (roleDefinitions.containsKey(backgroundId.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Map<String, Integer> skillPoints = readConfiguredValues(
                        config.getConfigurationSection(basePath + ".skillPoints"));
                if (BackgroundPointRules.isValid(skillPoints)) {
                    String normalizedId = backgroundId.trim().toLowerCase(Locale.ROOT);
                    roleDefinitions.put(normalizedId, new RoleBackgroundDefinition(normalizedId,
                            config.getString(basePath + ".title", backgroundId),
                            config.getStringList(basePath + ".lore"), skillPoints));
                }
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

    public List<RoleBackgroundDefinition> getRoleDefinitions() {
        return new ArrayList<>(roleDefinitions.values());
    }

    public List<RoleBackgroundDefinition> getRoleDefinitions(RoleBackgroundStage stage) {
        return getDefinitions(stage);
    }

    public List<AuraSkillEntry> getAvailableAuraSkills() {
        Plugin auraSkillsPlugin = plugin.getServer().getPluginManager().getPlugin("AuraSkills");
        if (auraSkillsPlugin == null || !auraSkillsPlugin.isEnabled()) {
            auraSkillDisplayNames = Map.of();
            return List.of();
        }
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object registry = apiClass.getMethod("getGlobalRegistry").invoke(api);
            Class<?> registryClass = Class.forName("dev.aurelium.auraskills.api.registry.GlobalRegistry");
            Class<?> skillClass = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            Method getId = skillClass.getMethod("getId");
            Method isEnabled = skillClass.getMethod("isEnabled");
            Object rawSkills = registryClass.getMethod("getSkills").invoke(registry);
            if (!(rawSkills instanceof Iterable<?> skills)) {
                auraSkillDisplayNames = Map.of();
                return List.of();
            }

            List<AuraSkillEntry> result = new ArrayList<>();
            Map<String, String> displayNames = new LinkedHashMap<>();
            for (Object skill : skills) {
                if (!Boolean.TRUE.equals(isEnabled.invoke(skill))) continue;
                String id = canonicalSkillId(String.valueOf(getId.invoke(skill)));
                String displayName = resolveAuraSkillName(skillClass, skill, id);
                if (!id.isBlank()) {
                    result.add(new AuraSkillEntry(id, displayName));
                    displayNames.put(id, displayName);
                }
            }
            result.sort((first, second) -> first.displayName().compareToIgnoreCase(second.displayName()));
            auraSkillDisplayNames = Map.copyOf(displayNames);
            return result;
        } catch (Exception ex) {
            auraSkillDisplayNames = Map.of();
            plugin.getLogger().warning("Failed to discover AuraSkills skills: " + ex.getMessage());
            return List.of();
        }
    }

    public String getAuraSkillDisplayName(String skillId) {
        return getAuraSkillDisplayName(null, skillId);
    }

    public String getAuraSkillDisplayName(Player player, String skillId) {
        String normalizedId = canonicalSkillId(skillId);
        if (normalizedId.isBlank()) {
            return skillId == null ? "" : skillId;
        }

        String localized = localizedAuraSkillName(player, normalizedId);
        if (localized != null) {
            return localized;
        }
        String cached = auraSkillDisplayNames.get(normalizedId);
        if (cached != null) {
            return cached;
        }

        getAvailableAuraSkills();
        return auraSkillDisplayNames.getOrDefault(normalizedId, prettifyAuraSkillId(normalizedId));
    }

    public List<String> getBackgroundIds(RoleBackgroundStage stage) {
        Map<String, RoleBackgroundDefinition> stageDefinitions = definitions.get(stage);
        return stageDefinitions == null ? new ArrayList<>() : new ArrayList<>(stageDefinitions.keySet());
    }

    public List<String> resolveLore(Player player, RoleBackgroundDefinition definition) {
        return definition == null ? List.of() : definition.getLore().stream()
                .map(line -> resolveText(player, line)).toList();
    }

    public String resolveText(Player player, String rawText) {
        if (player == null || !rawText.contains("%")) {
            return rawText;
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                rawText = PlaceholderAPI.setPlaceholders(player, rawText);
            } catch (RuntimeException ex) {
                plugin.getLogger().fine(() -> "Could not resolve background placeholders: " + ex.getMessage());
            }
        }
        return rawText.replace("%player_name%", player.getName());
    }

    public RoleBackgroundDefinition getDefinition(RoleBackgroundStage stage, String backgroundId) {
        if (stage == null || backgroundId == null || backgroundId.isBlank()) {
            return null;
        }
        Map<String, RoleBackgroundDefinition> stageDefinitions = definitions.get(stage);
        return stageDefinitions == null ? null : stageDefinitions.get(backgroundId.trim().toLowerCase(Locale.ROOT));
    }

    public RoleBackgroundDefinition getRoleDefinition(String backgroundId) {
        if (backgroundId == null || backgroundId.isBlank()) {
            return null;
        }
        return roleDefinitions.get(backgroundId.trim().toLowerCase(Locale.ROOT));
    }

    public RoleBackgroundDefinition getCommunityDefinition(String backgroundId) {
        return backgroundId == null ? null : communityDefinitions.get(backgroundId.trim().toLowerCase(Locale.ROOT));
    }

    public boolean updateCommunityBackground(String backgroundId, String title, String story) {
        RoleBackgroundDefinition current = getCommunityDefinition(backgroundId);
        if (current == null || title == null || title.isBlank() || title.length() > 64
                || story == null || story.isBlank() || story.length() > 1024
                || findBlockedBackgroundWord(title, story) != null) {
            return false;
        }
        FileConfiguration config = plugin.getBackgroundConfig();
        ConfigurationSection section = config.getConfigurationSection("community-backgrounds");
        String configId = section == null ? null : section.getKeys(false).stream()
                .filter(id -> id.trim().equalsIgnoreCase(current.getId())).findFirst().orElse(null);
        if (configId == null) {
            return false;
        }
        String basePath = "community-backgrounds." + configId;
        String oldTitle = config.getString(basePath + ".title", current.getTitle());
        List<String> oldLore = config.getStringList(basePath + ".lore");
        config.set(basePath + ".title", title.trim());
        config.set(basePath + ".lore", List.of(story.trim().split("\\R", -1)));
        if (!plugin.saveBackgroundConfig()) {
            config.set(basePath + ".title", oldTitle);
            config.set(basePath + ".lore", oldLore);
            return false;
        }
        reloadDefinitions();
        return true;
    }

    public String findBlockedBackgroundWord(String title, String story) {
        return sensitiveWordFilterEnabled ? backgroundTextFilter.findBlockedWord(title, story) : null;
    }

    public RoleBackgroundStage getNextPendingStage(WDEresident resident) {
        if (resident == null) {
            return RoleBackgroundStage.CHILDHOOD;
        }
        migrateLegacySelection(resident);
        if (!resident.hasSelectedChildhoodBackground()) return RoleBackgroundStage.CHILDHOOD;
        if (!resident.hasSelectedAdulthoodBackground()) {
            return RoleBackgroundStage.ADULTHOOD;
        }
        return null;
    }

    public boolean hasCompletedSelection(WDEresident resident) {
        if (resident == null) {
            return false;
        }
        migrateLegacySelection(resident);
        return resident.hasSelectedChildhoodBackground() && resident.hasSelectedAdulthoodBackground();
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
        return enabled && forceSelectionOnFirstJoin && resident != null
                && hasEnabledAuraSkills()
                && !definitions.get(RoleBackgroundStage.CHILDHOOD).isEmpty()
                && !definitions.get(RoleBackgroundStage.ADULTHOOD).isEmpty()
                && !hasCompletedSelection(resident);
    }

    private boolean hasEnabledAuraSkills() {
        Plugin auraSkillsPlugin = plugin.getServer().getPluginManager().getPlugin("AuraSkills");
        return auraSkillsPlugin != null && auraSkillsPlugin.isEnabled();
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
        player.sendMessage(plugin.formatLocaleMessage("background_forced_prompt_header"));
        player.sendMessage(plugin.formatLocaleMessage("background_forced_prompt_usage_choose"));
        player.sendMessage(plugin.formatLocaleMessage("background_move_locked"));
        if (player.isOnline() && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().openBackground(player);
        }
    }

    public SelectionApplyResult selectBackground(Player player, RoleBackgroundStage stage,
                                                 RoleBackgroundDefinition definition) {
        WDEresident resident = player == null ? null
                : residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        if (!enabled || resident == null || definition == null || stage == null
                || getDefinition(stage, definition.getId()) != definition
                || getNextPendingStage(resident) != stage
                || (hasEnabledAuraSkills() && !hasRegisteredSkills(definition.getSkillPoints()))) {
            return new SelectionApplyResult(definition, false, false, getNextPendingStage(resident), false);
        }
        setSelectedBackgroundId(resident, stage, definition.getId());
        resident.setBackgroundTitle(stage, definition.getTitle());
        resident.setBackgroundStory(stage, String.join("\n", definition.getLore()));
        resident.setBackgroundSkillPoints(stage, definition.getSkillPoints());
        boolean applied = applyStageBackgroundPoints(player, resident, stage);
        residentManager.saveResidents();
        return new SelectionApplyResult(definition, applied, !applied, getNextPendingStage(resident), true);
    }

    public boolean selectStageBackground(Player player, RoleBackgroundStage stage,
                                         RoleBackgroundDefinition definition, String title,
                                         String story, Map<String, Integer> skillPoints) {
        if (definition == null || !definition.getTitle().equals(title)
                || !String.join("\n", definition.getLore()).equals(story)
                || !definition.getSkillPoints().equals(skillPoints)) {
            return false;
        }
        var result = selectBackground(player, stage, definition);
        return result.selected() && !result.bonusesPending();
    }

    public RoleBackgroundDefinition createCommunityBackground(Player player, RoleBackgroundStage stage,
                                                               String title, String story,
                                                               Map<String, Integer> skillPoints) {
        if (!enabled || player == null || stage == null || title == null || title.isBlank()
                || story == null || story.isBlank() || title.length() > 64 || story.length() > 1024
                || !BackgroundPointRules.isValid(skillPoints)
                || findBlockedBackgroundWord(title, story) != null) {
            return null;
        }
        if (!hasRegisteredSkills(skillPoints)) {
            return null;
        }

        String backgroundId = "community_" + UUID.randomUUID().toString().replace("-", "");
        String basePath = "community-backgrounds." + backgroundId;
        FileConfiguration config = plugin.getBackgroundConfig();
        config.set(basePath + ".stage", stage.getConfigKey());
        config.set(basePath + ".author", player.getName());
        config.set(basePath + ".title", title.trim());
        config.set(basePath + ".lore", List.of(story.trim().split("\\R", -1)));
        ConfigurationSection skillPointsSection = config.createSection(basePath + ".skillPoints");
        for (Map.Entry<String, Integer> entry : skillPoints.entrySet()) {
            skillPointsSection.set(entry.getKey(), entry.getValue());
        }
        if (!plugin.saveBackgroundConfig()) {
            config.set(basePath, null);
            return null;
        }
        reloadDefinitions();
        return getDefinition(stage, backgroundId);
    }

    private boolean hasRegisteredSkills(Map<String, Integer> skillPoints) {
        var validIds = getAvailableAuraSkills().stream().map(AuraSkillEntry::id)
                .map(this::canonicalSkillId).collect(java.util.stream.Collectors.toSet());
        return skillPoints.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .map(entry -> canonicalSkillId(entry.getKey())).allMatch(validIds::contains);
    }

    public void applyPendingBonuses(Player player) {
        if (player == null || !enabled) {
            return;
        }

        WDEresident resident = residentManager.getOrCreateResident(player.getUniqueId(), player.getName());
        migrateLegacySelection(resident);
        boolean changed = false;
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            if (hasSelectedStage(resident, stage) && applyStageBackgroundPoints(player, resident, stage)) {
                changed = true;
            }
        }
        if (changed) {
            residentManager.saveResidents();
        }
    }

    private void migrateLegacySelection(WDEresident resident) {
        if (resident == null) {
            return;
        }
        boolean changed = false;
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            if (!hasSelectedStage(resident, stage) || !resident.getBackgroundSkillPoints(stage).isEmpty()) {
                continue;
            }
            RoleBackgroundDefinition definition = getDefinition(stage, getSelectedBackgroundId(resident, stage));
            if (definition == null) {
                continue;
            }
            if (resident.getBackgroundTitle(stage).isBlank()) {
                resident.setBackgroundTitle(stage, definition.getTitle());
            }
            if (resident.getBackgroundStory(stage).isBlank()) {
                resident.setBackgroundStory(stage, String.join("\n", definition.getLore()));
            }
            resident.setBackgroundSkillPoints(stage, definition.getSkillPoints());
            if (resident.hasAppliedBackgroundBonus(definition.getId())) {
                resident.markBackgroundBonusApplied(stageAppliedKey(stage, definition.getId()));
            }
            changed = true;
        }
        if (resident.hasSelectedRoleBackground()) {
            String legacyId = resident.getRoleBackgroundId();
            RoleBackgroundDefinition definition = getRoleDefinition(legacyId);
            RoleBackgroundStage stage = matchingSelectedStage(resident, legacyId);
            boolean alreadySelected = stage != null;
            if (stage == null) {
                stage = definition == null || definition.getStage() == null
                        ? (isAdulthoodPreset(legacyId) ? RoleBackgroundStage.ADULTHOOD : RoleBackgroundStage.CHILDHOOD)
                        : definition.getStage();
                if (hasSelectedStage(resident, stage)) {
                    stage = getNextUnselectedStage(resident);
                }
            }
            if (stage != null) {
                if (!alreadySelected) {
                    setSelectedBackgroundId(resident, stage, legacyId);
                }
                if (!resident.getRoleBackgroundTitle().isBlank()) {
                    resident.setBackgroundTitle(stage, resident.getRoleBackgroundTitle());
                } else if (resident.getBackgroundTitle(stage).isBlank() && definition != null) {
                    resident.setBackgroundTitle(stage, definition.getTitle());
                }
                if (!resident.getRoleBackgroundStory().isBlank()) {
                    resident.setBackgroundStory(stage, resident.getRoleBackgroundStory());
                } else if (resident.getBackgroundStory(stage).isBlank() && definition != null) {
                    resident.setBackgroundStory(stage, String.join("\n", definition.getLore()));
                }
                Map<String, Integer> points = resident.getRoleBackgroundSkillPoints();
                if (points.isEmpty() && definition != null) {
                    points = definition.getSkillPoints();
                }
                if (!points.isEmpty()) {
                    resident.setBackgroundSkillPoints(stage, points);
                }
                if (resident.hasAppliedBackgroundBonus("role-background:" + legacyId)) {
                    resident.markBackgroundBonusApplied(stageAppliedKey(stage, legacyId));
                }
                changed = true;
            }
            resident.setRoleBackgroundId("");
            resident.setRoleBackgroundTitle("");
            resident.setRoleBackgroundStory("");
            resident.setRoleBackgroundSkillPoints(Map.of());
            changed = true;
        }
        if (changed) {
            residentManager.saveResidents();
        }
    }

    private boolean applyStageBackgroundPoints(Player player, WDEresident resident, RoleBackgroundStage stage) {
        String backgroundId = getSelectedBackgroundId(resident, stage);
        String appliedKey = stageAppliedKey(stage, backgroundId);
        if (resident.hasAppliedBackgroundBonus(appliedKey)) {
            return true;
        }
        Map<String, Integer> points = resident.getBackgroundSkillPoints(stage);
        if (points.isEmpty()) {
            return false;
        }
        Plugin auraSkillsPlugin = plugin.getServer().getPluginManager().getPlugin("AuraSkills");
        if (auraSkillsPlugin == null || !auraSkillsPlugin.isEnabled()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object user = apiClass.getMethod("getUser", UUID.class).invoke(api, player.getUniqueId());
            Class<?> userClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            if (user == null || !Boolean.TRUE.equals(userClass.getMethod("isLoaded").invoke(user))) {
                return false;
            }
            Object registry = apiClass.getMethod("getGlobalRegistry").invoke(api);
            Class<?> idClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId");
            Class<?> skillClass = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            Method idFactory = idClass.getMethod("of", String.class, String.class);
            Class<?> registryClass = Class.forName("dev.aurelium.auraskills.api.registry.GlobalRegistry");
            Method getSkill = registryClass.getMethod("getSkill", idClass);
            Method getLevel = userClass.getMethod("getSkillLevel", skillClass);
            Method setLevel = userClass.getMethod("setSkillLevel", skillClass, int.class);
            List<SkillLevelChange> changes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : points.entrySet()) {
                ParsedAuraSkillBonus parsed = parseAuraSkillBonus(entry.getKey(), entry.getValue());
                if (parsed == null) {
                    continue;
                }
                Object skill = getSkill.invoke(registry, idFactory.invoke(null, parsed.namespace(), parsed.key()));
                if (skill == null || !Boolean.TRUE.equals(skillClass.getMethod("isEnabled").invoke(skill))) {
                    return false;
                }
                int current = (int) getLevel.invoke(user, skill);
                int max = (int) skillClass.getMethod("getMaxLevel").invoke(skill);
                changes.add(new SkillLevelChange(skill, current,
                        Math.max(current, Math.min(max, current + parsed.increment()))));
            }
            List<SkillLevelChange> applied = new ArrayList<>();
            try {
                for (SkillLevelChange change : changes) {
                    setLevel.invoke(user, change.skill(), change.targetLevel());
                    applied.add(change);
                }
            } catch (Exception ex) {
                for (int index = applied.size() - 1; index >= 0; index--) {
                    SkillLevelChange change = applied.get(index);
                    try {
                        setLevel.invoke(user, change.skill(), change.currentLevel());
                    } catch (Exception rollbackError) {
                        plugin.getLogger().warning("Failed to roll back a partial AuraSkills background grant: "
                                + rollbackError.getMessage());
                    }
                }
                throw ex;
            }
            resident.markBackgroundBonusApplied(appliedKey);
            player.sendMessage(plugin.formatLocaleMessage("background_auraskills_bonus_applied",
                    resolveText(player, resident.getBackgroundTitle(stage)), points.size()));
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to apply role background points for " + player.getName()
                    + ": " + ex.getMessage());
            return false;
        }
    }

    private void setSelectedBackgroundId(WDEresident resident, RoleBackgroundStage stage, String backgroundId) {
        if (stage == RoleBackgroundStage.CHILDHOOD) {
            resident.setChildhoodBackgroundId(backgroundId);
        } else if (stage == RoleBackgroundStage.ADULTHOOD) {
            resident.setAdulthoodBackgroundId(backgroundId);
        }
    }

    private RoleBackgroundStage getNextUnselectedStage(WDEresident resident) {
        if (!resident.hasSelectedChildhoodBackground()) {
            return RoleBackgroundStage.CHILDHOOD;
        }
        if (!resident.hasSelectedAdulthoodBackground()) {
            return RoleBackgroundStage.ADULTHOOD;
        }
        return null;
    }

    private boolean isAdulthoodPreset(String backgroundId) {
        return getDefinition(RoleBackgroundStage.ADULTHOOD, backgroundId) != null;
    }

    private RoleBackgroundStage matchingSelectedStage(WDEresident resident, String backgroundId) {
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            if (hasSelectedStage(resident, stage)
                    && getSelectedBackgroundId(resident, stage).equalsIgnoreCase(backgroundId)) {
                return stage;
            }
        }
        return null;
    }

    private String stageAppliedKey(RoleBackgroundStage stage, String backgroundId) {
        return "background-stage:" + stage.getConfigKey() + ":" + backgroundId;
    }

    private Map<String, Integer> distributeLegacyStagePoints(ConfigurationSection legacyBonuses) {
        if (legacyBonuses == null) {
            return Map.of();
        }
        List<String> skills = legacyBonuses.getKeys(false).stream()
                .filter(skill -> legacyBonuses.getInt(skill, 0) > 0)
                .sorted()
                .toList();
        if (skills.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> points = new LinkedHashMap<>();
        int base = BackgroundPointRules.REQUIRED_POINTS / skills.size();
        int remainder = BackgroundPointRules.REQUIRED_POINTS % skills.size();
        for (int index = 0; index < skills.size(); index++) {
            int allocation = base + (index < remainder ? 1 : 0);
            if (allocation > 0) {
                points.put(skills.get(index), allocation);
            }
        }
        return points;
    }

    private Map<String, Integer> readConfiguredValues(ConfigurationSection section) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (!(raw instanceof Integer value) || values.putIfAbsent(canonicalSkillId(key), value) != null) {
                return Map.of();
            }
        }
        return values;
    }

    private String resolveAuraSkillName(Class<?> skillApi, Object skill, String fallback) {
        try {
            Locale locale = Locale.forLanguageTag(plugin.userLang.replace('_', '-'));
            Object value = skillApi.getMethod("getDisplayName", Locale.class).invoke(skill, locale);
            if (value instanceof String name && !name.isBlank() && !looksLikeAuraSkillId(name, fallback)) {
                return name;
            }
        } catch (ReflectiveOperationException ignored) {
            // Older AuraSkills versions may not expose a localized display name.
        }
        for (String methodName : List.of("getDisplayName", "getName")) {
            try {
                Object value = skill.getClass().getMethod(methodName).invoke(skill);
                if (value instanceof Component component) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(component);
                    if (!plain.isBlank() && !looksLikeAuraSkillId(plain, fallback)) {
                        return plain;
                    }
                } else if (value != null && !value.toString().isBlank()
                        && !looksLikeAuraSkillId(value.toString(), fallback)) {
                    return value.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // AuraSkills versions expose different optional display-name methods.
            }
        }
        return prettifyAuraSkillId(normalizeAuraSkillId(fallback));
    }

    private boolean looksLikeAuraSkillId(String value, String expectedId) {
        String normalizedValue = normalizeAuraSkillId(value);
        String normalizedExpectedId = normalizeAuraSkillId(expectedId);
        int separator = normalizedExpectedId.lastIndexOf('/');
        String expectedKey = separator >= 0 ? normalizedExpectedId.substring(separator + 1) : normalizedExpectedId;
        if (normalizedValue.equals(normalizedExpectedId) || normalizedValue.equals(expectedKey)) {
            return true;
        }
        return value.matches("(?i)[a-z0-9_.-]+[:/][a-z0-9_./-]+");
    }

    private String localizedAuraSkillName(Player player, String normalizedId) {
        if (normalizedId == null || normalizedId.isBlank()) {
            return null;
        }
        String key = "background_skill_name_" + normalizedId.replaceAll("[^a-z0-9]+", "_");
        String value = player == null ? plugin.getLocaleMessage(key) : plugin.getLocaleMessage(player.getLocale(), key);
        return key.equals(value) || value.isBlank() ? null : value;
    }

    private String normalizeAuraSkillId(String skillId) {
        return skillId == null ? "" : skillId.trim().toLowerCase(Locale.ROOT).replace(':', '/');
    }

    private String prettifyAuraSkillId(String normalizedId) {
        int separator = normalizedId.lastIndexOf('/');
        String name = separator >= 0 ? normalizedId.substring(separator + 1) : normalizedId;
        String[] words = name.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? normalizedId : result.toString();
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

    private String canonicalSkillId(String rawSkillId) {
        ParsedAuraSkillBonus parsed = parseAuraSkillBonus(rawSkillId, 1);
        return parsed == null ? "" : (parsed.namespace() + "/" + parsed.key()).toLowerCase(Locale.ROOT);
    }

    /**
     * 返回背景选择结果及技能奖励发放情况，供命令或菜单反馈。
     */
    public record SelectionApplyResult(RoleBackgroundDefinition definition,
                                       boolean bonusesAppliedNow,
                                       boolean bonusesPending,
                                       RoleBackgroundStage nextPendingStage,
                                       boolean selected) {
    }

    public record AuraSkillEntry(String id, String displayName) {
    }

    /**
     * 保存解析后的技能命名空间、键和正向等级增量。
     */
    private record ParsedAuraSkillBonus(String namespace, String key, int increment) {
    }

    private record SkillLevelChange(Object skill, int currentLevel, int targetLevel) {
    }
}
