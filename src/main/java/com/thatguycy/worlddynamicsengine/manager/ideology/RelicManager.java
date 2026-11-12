package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** 加载两个宗教圣物槽位，并按原版材质或 CraftEngine 完整 ID 识别背包物品。 */
public final class RelicManager {
    private final WorldDynamicsEngine plugin;
    private Set<Material> vanillaRelics = Set.of();
    private Set<String> craftEngineRelics = Set.of();
    private boolean enabled;
    private int speedLevel;

    public RelicManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "relics.yml");
        if (!file.exists()) plugin.saveResource("relics.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (var entry : List.of(
                new DefaultEntry("enabled", true),
                new DefaultEntry("effects.speedLevel", 1),
                new DefaultEntry("relics.first.type", "vanilla"),
                new DefaultEntry("relics.first.id", "NETHER_STAR"),
                new DefaultEntry("relics.second.type", "vanilla"),
                new DefaultEntry("relics.second.id", "TOTEM_OF_UNDYING"))) {
            if (!config.contains(entry.path())) {
                config.set(entry.path(), entry.value());
                changed = true;
            }
        }
        if (changed) {
            try {
                config.save(file);
            } catch (java.io.IOException ex) {
                plugin.getLogger().warning("Failed to save relic defaults: " + ex.getMessage());
            }
        }

        enabled = config.getBoolean("enabled", true);
        speedLevel = Math.max(0, Math.min(10, config.getInt("effects.speedLevel", 1)));
        Set<Material> vanilla = new LinkedHashSet<>();
        Set<String> craftEngine = new LinkedHashSet<>();
        for (String slot : List.of("first", "second")) {
            String type = config.getString("relics." + slot + ".type", "vanilla");
            String configuredId = config.getString("relics." + slot + ".id", "");
            String id = configuredId == null ? "" : configuredId.trim();
            if (id.isEmpty()) {
                plugin.getLogger().warning("Relic " + slot + " has no item ID.");
                continue;
            }
            if ("vanilla".equalsIgnoreCase(type)) {
                Material material = Material.matchMaterial(id);
                if (material == null || material.isAir()) {
                    plugin.getLogger().warning("Invalid vanilla relic material in slot " + slot + ": " + id);
                    continue;
                }
                vanilla.add(material);
            } else if ("craftengine".equalsIgnoreCase(type)) {
                craftEngine.add(id);
            } else {
                plugin.getLogger().warning("Invalid relic type in slot " + slot + ": " + type);
            }
        }
        vanillaRelics = Set.copyOf(vanilla);
        craftEngineRelics = Set.copyOf(craftEngine);
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public boolean hasRelic(PlayerInventory inventory) {
        if (!enabled || inventory == null) return false;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isRelic(item)) return true;
        }
        return isRelic(inventory.getItemInOffHand());
    }

    private boolean isRelic(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        var craftEngine = plugin.getCraftEngineIntegration();
        String customId = craftEngine == null ? null : craftEngine.getItemId(item);
        return customId == null
                ? vanillaRelics.contains(item.getType())
                : craftEngineRelics.contains(customId);
    }

    private record DefaultEntry(String path, Object value) {}
}
