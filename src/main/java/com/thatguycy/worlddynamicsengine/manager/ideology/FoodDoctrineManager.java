package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * Loads vanilla and CE food mappings and adjusts mood from personal dietary doctrines.
 */
public final class FoodDoctrineManager {
    /**
     * 定义素食、肉食、人肉和血食四类映射标签；标签本身不会使物品变得可食用。
     */
    public enum FoodCategory { VEGETARIAN, MEAT, HUMAN_MEAT, BLOOD }
    /** 保存饮食规则是否适用、心情增减值及所有违规项。 */
    public record Assessment(boolean applicable, int compliantCount, int compliantMoodWeight,
                             int violationMoodWeight, int compliantMoodChange, int violationMoodChange,
                             List<String> violations, List<DoctrineMoodEntry> moodEntries) {
        public Assessment(boolean applicable, int compliantCount, int compliantMoodWeight,
                          int violationMoodWeight, int compliantMoodChange, int violationMoodChange,
                          List<String> violations) {
            this(applicable, compliantCount, compliantMoodWeight, violationMoodWeight,
                    compliantMoodChange, violationMoodChange, violations, List.of());
        }

        public boolean compliant() { return applicable && violations.isEmpty(); }
        public int violationCount() { return violations.size(); }
    }
    private final WorldDynamicsEngine plugin;
    private Map<Material, FoodCategory> vanilla = Map.of();
    private Map<String, FoodCategory> craftengine = Map.of();
    private boolean enabled;

    public FoodDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "foods.yml");
        if (!file.exists()) plugin.saveResource("foods.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        if (config.contains("effects")) {
            config.set("effects", null);
            changed = true;
        }
        for (var entry : Map.<String, Object>of("enabled", true).entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            try { config.save(file); }
            catch (java.io.IOException ex) { plugin.getLogger().warning("Failed to save food defaults: " + ex.getMessage()); }
        }
        enabled = config.getBoolean("enabled", true);
        Map<Material, FoodCategory> loadedVanilla = new EnumMap<>(Material.class);
        Map<String, FoodCategory> loadedCe = new LinkedHashMap<>();
        for (String type : List.of("vanilla", "craftengine")) {
            var section = config.getConfigurationSection("mapping." + type);
            if (section == null) continue;
            for (var entry : section.getValues(false).entrySet()) {
                FoodCategory category;
                try { category = FoodCategory.valueOf(String.valueOf(entry.getValue()).trim().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid food category for " + type + ":" + entry.getKey());
                    continue;
                }
                if (type.equals("craftengine")) loadedCe.put(entry.getKey(), category);
                else {
                    Material material = Material.matchMaterial(entry.getKey());
                    if (material == null || material.isAir()) {
                        plugin.getLogger().warning("Invalid vanilla food mapping: " + entry.getKey());
                    } else loadedVanilla.put(material, category);
                }
            }
        }
        vanilla = Collections.unmodifiableMap(loadedVanilla);
        craftengine = Collections.unmodifiableMap(loadedCe);
    }

    public FoodCategory classify(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        var ce = plugin.getCraftEngineIntegration();
        String id = ce == null ? null : ce.getItemId(item);
        return classifyId(item.getType(), id);
    }

    // A recognized CE item must never inherit the classification of its vanilla base material.
    public FoodCategory classifyId(Material material, String ceId) {
        return ceId == null ? vanilla.get(material) : craftengine.get(ceId);
    }

    public void apply(Player player, FoodCategory category) {
        if (!enabled || !plugin.religionEnabled || category == null || !player.isOnline() || player.isDead()) {
            return;
        }
        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) {
            return;
        }
        Map<String, String> attitudes = plugin.getReligionManager().getConfiguredDoctrineAttitudes(religion);
        String instantAction = category == FoodCategory.HUMAN_MEAT ? "humanMeat" : null;
        Assessment assessment = evaluate(category, attitudes, plugin.getReligionManager()::getDoctrineMoodWeight,
                (doctrine, attitude, compliant) -> plugin.getReligionManager()
                        .getDoctrineMoodChangePerWeight(doctrine, attitude, compliant, instantAction));
        if (!assessment.applicable()) {
            return;
        }
        for (DoctrineMoodEntry entry : assessment.moodEntries()) {
            plugin.getMoodManager().applyDoctrineMoodChanges(player,
                    entry.compliant() ? entry.moodChange() : 0,
                    entry.compliant() ? 0 : entry.moodChange(),
                    entry.source());
        }
        for (String doctrine : assessment.violations()) {
            if (plugin.getPlayerMenuManager() != null) plugin.getPlayerMenuManager().sendDoctrineViolation(player, doctrine);
        }
    }

    public static Assessment evaluate(FoodCategory category, Map<String, String> attitudes) {
        return evaluate(category, attitudes, (doctrineId, attitudeId) -> 1,
                (doctrineId, attitudeId, compliant) -> 1);
    }

    public static Assessment evaluate(FoodCategory category, Map<String, String> attitudes,
                                      ToIntBiFunction<String, String> moodWeight) {
        return evaluate(category, attitudes, moodWeight, (doctrineId, attitudeId, compliant) -> 1);
    }

    public static Assessment evaluate(FoodCategory category, Map<String, String> attitudes,
                                      ToIntBiFunction<String, String> moodWeight,
                                      MoodChangeResolver moodChangeResolver) {
        if (category == null || attitudes == null) return new Assessment(false, 0, 0, 0, 0, 0, List.of());
        List<String> violations = new ArrayList<>();
        int compliantCount = 0;
        int compliantMoodWeight = 0;
        int violationMoodWeight = 0;
        int compliantMoodChange = 0;
        int violationMoodChange = 0;
        boolean applicable = false;
        List<DoctrineMoodEntry> moodEntries = new ArrayList<>();
        boolean hasCannibalismDoctrine = category == FoodCategory.HUMAN_MEAT
                && attitudes.keySet().stream().anyMatch(id -> matches(id, "cannibalism"));
        for (var entry : attitudes.entrySet()) {
            String id = entry.getKey();
            String attitude = entry.getValue();
            if (attitude == null) continue;
            attitude = attitude.substring(attitude.lastIndexOf('/') + 1);
            Boolean compliant = null;
            if (matches(id, "meat_eating") && category != FoodCategory.BLOOD && !hasCannibalismDoctrine) {
                if (Set.of("abhor_meat", "fear_meat", "disgust_meat").contains(attitude))
                    compliant = category == FoodCategory.VEGETARIAN;
                else if (Set.of("anti_vegetarian_disgust", "anti_vegetarian_hate", "anti_vegetarian_abhor").contains(attitude))
                    compliant = category != FoodCategory.VEGETARIAN;
                else if (Set.of("neutral", "unrestricted").contains(attitude)) compliant = true;
            } else if ((matches(id, "cannibalism") && category == FoodCategory.HUMAN_MEAT)
                    || (matches(id, "blood_feeding") && category == FoodCategory.BLOOD)) {
                if (Set.of("abhor", "fear", "disgust", "forbidden", "taboo").contains(attitude)) compliant = false;
                else if (Set.of("accept", "enjoy", "crave", "greedy", "neutral", "unrestricted").contains(attitude)) compliant = true;
            }
            if (compliant != null) {
                applicable = true;
                int weight = Math.max(0, moodWeight.applyAsInt(id, entry.getValue()));
                if (!compliant) {
                    violations.add(id);
                    violationMoodWeight += weight;
                    int moodChange = weight
                            * Math.max(0, moodChangeResolver.getPerWeightChange(id, entry.getValue(), false));
                    violationMoodChange += moodChange;
                    moodEntries.add(new DoctrineMoodEntry(id, entry.getValue(), false, moodChange));
                } else {
                    compliantCount++;
                    compliantMoodWeight += weight;
                    int moodChange = weight
                            * Math.max(0, moodChangeResolver.getPerWeightChange(id, entry.getValue(), true));
                    compliantMoodChange += moodChange;
                    moodEntries.add(new DoctrineMoodEntry(id, entry.getValue(), true, moodChange));
                }
            }
        }
        return new Assessment(applicable, compliantCount, compliantMoodWeight, violationMoodWeight,
                compliantMoodChange, violationMoodChange, List.copyOf(violations), List.copyOf(moodEntries));
    }

    private static boolean matches(String id, String path) {
        return id.endsWith(":wder/" + path) || id.endsWith(":wde/" + path);
    }
}
