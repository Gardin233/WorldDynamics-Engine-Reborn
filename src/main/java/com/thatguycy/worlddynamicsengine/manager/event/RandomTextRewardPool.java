package com.thatguycy.worlddynamicsengine.manager.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/** Rolls the currency and item portion of a random text event offer. */
final class RandomTextRewardPool {
    private static final Set<Material> NON_SURVIVAL_ITEMS = EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.CHAIN_COMMAND_BLOCK,
            Material.COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.DEBUG_STICK,
            Material.END_GATEWAY,
            Material.END_PORTAL_FRAME,
            Material.JIGSAW,
            Material.KNOWLEDGE_BOOK,
            Material.LIGHT,
            Material.REPEATING_COMMAND_BLOCK,
            Material.SPAWNER,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID);

    private RandomTextRewardPool() {
    }

    static Reward roll(ConfigurationSection rewards, Plugin plugin, Random random) {
        Random source = random == null ? new Random() : random;
        BigDecimal rolledMoney = rollMoney(rewards, source);

        List<ItemStack> items = vanillaItems();
        ConfigurationSection itemConfig = rewards == null ? null : rewards.getConfigurationSection("items");
        if (itemConfig != null) {
            for (String configuredMaterial : itemConfig.getStringList("additional-materials")) {
                Material material = Material.matchMaterial(configuredMaterial);
                if (material != null && material.isItem() && !material.isAir()) {
                    addMaterial(items, material);
                }
            }
            for (String itemId : itemConfig.getStringList("craft-engine-items")) {
                ItemStack customItem = createCraftEngineItem(plugin, itemId);
                if (customItem != null) {
                    items.add(customItem);
                }
            }
        }

        ItemStack item = items.isEmpty() ? null : items.get(source.nextInt(items.size())).clone();
        return new Reward(rolledMoney, item);
    }

    static BigDecimal rollMoney(ConfigurationSection rewards, Random random) {
        Random source = random == null ? new Random() : random;
        ConfigurationSection money = rewards == null ? null : rewards.getConfigurationSection("money");
        double minimum = finiteNonNegative(money == null ? 0.0D : money.getDouble("minimum", 0.0D));
        double maximum = finiteNonNegative(money == null ? minimum : money.getDouble("maximum", minimum));
        if (minimum > maximum) {
            double swap = minimum;
            minimum = maximum;
            maximum = swap;
        }
        double amount = minimum == maximum
                ? minimum : minimum + source.nextDouble() * (maximum - minimum);
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<ItemStack> vanillaItems() {
        List<ItemStack> items = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir()
                    && material.getMaxStackSize() > 0 && !NON_SURVIVAL_ITEMS.contains(material)) {
                items.add(new ItemStack(material));
            }
        }
        return items;
    }

    private static void addMaterial(List<ItemStack> items, Material material) {
        if (items.stream().noneMatch(item -> item.getType() == material)) {
            items.add(new ItemStack(material));
        }
    }

    private static ItemStack createCraftEngineItem(Plugin plugin, String itemId) {
        if (plugin == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }
        try {
            Class<?> itemsApi = Class.forName(
                    "net.momirealms.craftengine.bukkit.api.CraftEngineItems",
                    true, craftEngine.getClass().getClassLoader());
            Method byId = itemsApi.getMethod("byId", String.class);
            Object definition = byId.invoke(null, itemId.trim());
            if (definition == null) {
                return null;
            }
            Method build = definition.getClass().getMethod("buildBukkitItem", org.bukkit.entity.Player.class);
            Object result = build.invoke(definition, new Object[] {null});
            return result instanceof ItemStack stack ? stack.clone() : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not create configured CraftEngine reward '"
                    + itemId + "': " + exception.getMessage());
            return null;
        }
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    record Reward(BigDecimal money, ItemStack item) {
        Reward {
            money = money == null ? BigDecimal.ZERO : money.max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            item = item == null ? null : item.clone();
        }

        @Override
        public ItemStack item() {
            return item == null ? null : item.clone();
        }

        String itemName() {
            if (item == null) {
                return "无";
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && !meta.getDisplayName().isBlank()) {
                return meta.getDisplayName();
            }
            String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
            StringBuilder name = new StringBuilder();
            for (String word : words) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return name.toString();
        }
    }
}
