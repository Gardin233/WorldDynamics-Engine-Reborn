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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

/**
 * 加载原版及 CE 食物映射，按个人饮食戒律判定力量或虚弱；CE 未映射时不回退底材。
 */
public final class FoodDoctrineManager {
    private static final String BUFF_SOURCE = "wde:food_doctrines";
    /**
     * 定义素食、肉食、人肉和血食四类映射标签；标签本身不会使物品变得可食用。
     */
    public enum FoodCategory { VEGETARIAN, MEAT, HUMAN_MEAT, BLOOD }
    /**
     * 保存饮食规则是否适用及所有违规项，任一相关规则违规即整体不合规。
     */
    public record Assessment(boolean applicable, List<String> violations) {
        public boolean compliant() { return applicable && violations.isEmpty(); }
    }
    private final WorldDynamicsEngine plugin;
    private Map<Material, FoodCategory> vanilla = Map.of();
    private Map<String, FoodCategory> craftengine = Map.of();
    private boolean enabled;
    private int durationTicks;
    private int strengthLevel;
    private int weaknessLevel;

    public FoodDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "foods.yml");
        if (!file.exists()) plugin.saveResource("foods.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (var entry : Map.<String, Object>of("enabled", true, "effects.durationSeconds", 30,
                "effects.strengthLevel", 1, "effects.weaknessLevel", 1).entrySet()) {
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
        durationTicks = Math.max(1, Math.min(3600, config.getInt("effects.durationSeconds", 30))) * 20;
        strengthLevel = Math.max(0, Math.min(10, config.getInt("effects.strengthLevel", 1)));
        weaknessLevel = Math.max(0, Math.min(10, config.getInt("effects.weaknessLevel", 1)));
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
            plugin.getDoctrineBuffManager().removeSource(player, BUFF_SOURCE);
            return;
        }
        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) {
            plugin.getDoctrineBuffManager().removeSource(player, BUFF_SOURCE);
            return;
        }
        Assessment assessment = evaluate(category,
                plugin.getReligionManager().getConfiguredDoctrineAttitudes(religion));
        if (!assessment.applicable()) {
            plugin.getDoctrineBuffManager().removeSource(player, BUFF_SOURCE);
            return;
        }
        PotionEffectType effect = assessment.compliant() ? PotionEffectType.STRENGTH : PotionEffectType.WEAKNESS;
        int level = assessment.compliant() ? strengthLevel : weaknessLevel;
        plugin.getDoctrineBuffManager().replaceTimed(player, BUFF_SOURCE, Map.of(effect, level), durationTicks);
        for (String doctrine : assessment.violations()) {
            if (plugin.getPlayerMenuManager() != null) plugin.getPlayerMenuManager().sendDoctrineViolation(player, doctrine);
        }
    }

    public static Assessment evaluate(FoodCategory category, Map<String, String> attitudes) {
        if (category == null) return new Assessment(false, List.of());
        List<String> violations = new ArrayList<>();
        boolean applicable = false;
        for (var entry : attitudes.entrySet()) {
            String id = entry.getKey();
            String attitude = entry.getValue();
            if (attitude == null) continue;
            attitude = attitude.substring(attitude.lastIndexOf('/') + 1);
            Boolean compliant = null;
            if (matches(id, "meat_eating") && category != FoodCategory.BLOOD) {
                if (Set.of("abhor_meat", "fear_meat", "disgust_meat").contains(attitude))
                    compliant = category == FoodCategory.VEGETARIAN;
                else if (Set.of("anti_vegetarian_disgust", "anti_vegetarian_hate", "anti_vegetarian_abhor").contains(attitude))
                    compliant = category != FoodCategory.VEGETARIAN;
                else if (Set.of("neutral", "unrestricted").contains(attitude)) compliant = true;
            } else if (matches(id, "cannibalism") && category == FoodCategory.HUMAN_MEAT
                    || matches(id, "blood_feeding") && category == FoodCategory.BLOOD) {
                if (Set.of("abhor", "fear", "disgust", "forbidden", "taboo").contains(attitude)) compliant = false;
                else if (Set.of("accept", "enjoy", "crave", "greedy", "neutral", "unrestricted").contains(attitude)) compliant = true;
            }
            if (compliant != null) {
                applicable = true;
                if (!compliant) violations.add(id);
            }
        }
        return new Assessment(applicable, List.copyOf(violations));
    }

    private static boolean matches(String id, String path) {
        return id.endsWith(":wder/" + path) || id.endsWith(":wde/" + path);
    }
}
