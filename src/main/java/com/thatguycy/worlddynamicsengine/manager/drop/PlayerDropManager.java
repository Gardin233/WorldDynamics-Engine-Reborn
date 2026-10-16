package com.thatguycy.worlddynamicsengine.manager.drop;

import com.thatguycy.worlddynamicsengine.integration.craftengine.CraftEngineIntegration;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * 读取死亡额外掉落池，独立抽取条目并构建原版或 CE 物品，保留原版掉落。
 */
public final class PlayerDropManager {
    private static final int MAX_AMOUNT_PER_ENTRY = 2304;
    private final WorldDynamicsEngine plugin;
    private List<DropEntry> pool = List.of();
    private boolean enabled;
    private int rolls;

    public PlayerDropManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void reloadDefinitions() {
        FileConfiguration config = plugin.getDropsConfig();
        enabled = config != null && config.getBoolean("playerDrops.enabled", false);
        rolls = config == null ? 0 : Math.max(0, Math.min(100, config.getInt("playerDrops.rolls", 3)));
        List<DropEntry> loadedEntries = new ArrayList<>();
        if (config == null) {
            pool = List.of();
            return;
        }

        int index = 0;
        for (Map<?, ?> rawEntry : config.getMapList("playerDrops.pool")) {
            index++;
            String type = getString(rawEntry, "type", "vanilla").toLowerCase(Locale.ROOT);
            String itemId = getString(rawEntry, "id", "");
            if ("vanilla".equals(type)) {
                itemId = getString(rawEntry, "material", "");
            } else if ("ce".equals(type)) {
                type = "craftengine";
            }

            int amount = Math.max(1, Math.min(MAX_AMOUNT_PER_ENTRY, getInt(rawEntry, "amount", 1)));
            double chance = Math.max(0D, Math.min(1D, getDouble(rawEntry, "dropChance", 1D)));
            if ((!type.equals("vanilla") && !type.equals("craftengine")) || itemId.isBlank()) {
                plugin.getLogger().warning("Ignored invalid player drop pool entry #" + index + " in drops.yml.");
                continue;
            }
            if ("vanilla".equals(type)) {
                Material material = Material.matchMaterial(itemId);
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("Ignored invalid vanilla item in drops.yml pool entry #" + index + ": " + itemId);
                    continue;
                }
            } else {
                CraftEngineIntegration integration = plugin.getCraftEngineIntegration();
                if (integration == null) {
                    plugin.getLogger().warning("CraftEngine drop pool entry #" + index + " is configured, but CraftEngine API is unavailable.");
                } else if (!integration.getLoadedItemIds().contains(itemId)) {
                    plugin.getLogger().warning("CraftEngine item is not currently loaded for drop pool entry #" + index + ": " + itemId);
                }
            }

            loadedEntries.add(new DropEntry(type, itemId, amount, chance));
        }
        pool = List.copyOf(loadedEntries);
    }

    public void addDrops(PlayerDeathEvent event) {
        if (!enabled || rolls <= 0 || pool.isEmpty()) {
            return;
        }

        Player player = event.getEntity();
        for (int roll = 0; roll < rolls; roll++) {
            DropEntry selected = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            if (ThreadLocalRandom.current().nextDouble() >= selected.dropChance()) {
                continue;
            }

            for (ItemStack stack : createStacks(selected, player)) {
                event.getDrops().add(stack);
            }
        }
    }

    private List<ItemStack> createStacks(DropEntry entry, Player player) {
        ItemStack prototype;
        if ("vanilla".equals(entry.type())) {
            Material material = Material.matchMaterial(entry.itemId());
            if (material == null || !material.isItem()) {
                return List.of();
            }
            prototype = new ItemStack(material);
        } else {
            CraftEngineIntegration integration = plugin.getCraftEngineIntegration();
            if (integration == null) {
                return List.of();
            }
            prototype = integration.createItemStack(entry.itemId(), player);
            if (prototype == null || prototype.getType().isAir()) {
                return List.of();
            }
        }

        List<ItemStack> stacks = new ArrayList<>();
        int remaining = entry.amount();
        int maxStackSize = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = prototype.clone();
            int stackAmount = Math.min(remaining, maxStackSize);
            stack.setAmount(stackAmount);
            stacks.add(stack);
            remaining -= stackAmount;
        }
        return stacks;
    }

    private String getString(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private int getInt(Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double getDouble(Map<?, ?> values, String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * 保存一个已解析的掉落候选条目，包括物品类型、标识、数量和独立掉落概率。
     */
    private record DropEntry(String type, String itemId, int amount, double dropChance) {
    }
}
