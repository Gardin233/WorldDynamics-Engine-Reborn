package com.thatguycy.worlddynamicsengine.integration.craftengine;

import java.util.Collections;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

/**
 * 隔离可选 CraftEngine 依赖，通过延迟加载适配器识别自定义物品和方块。
 */
public final class CraftEngineIntegration {
    private static final String PROVIDER_CLASS = "com.thatguycy.worlddynamicsengine.integration.craftengine.CraftEngine269Provider";
    private final String version;
    private final CraftEngineProvider provider;

    private CraftEngineIntegration(String version, CraftEngineProvider provider) {
        this.version = version;
        this.provider = provider;
    }

    public static CraftEngineIntegration load(JavaPlugin plugin) {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return null;
        }

        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS, true, plugin.getClass().getClassLoader());
            CraftEngineProvider provider = (CraftEngineProvider) providerClass.getDeclaredConstructor().newInstance();
            return new CraftEngineIntegration(craftEngine.getDescription().getVersion(), provider);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("CraftEngine was detected, but its 26.9 API adapter could not be loaded: " + exception.getMessage());
            return null;
        }
    }

    public String getVersion() {
        return version;
    }

    public String getItemId(ItemStack itemStack) {
        return provider.getItemId(itemStack);
    }

    public String getBlockId(Block block) {
        return provider.getBlockId(block);
    }

    public ItemStack createItemStack(String itemId, Player player) {
        return provider.createItemStack(itemId, player);
    }

    public Set<String> getLoadedItemIds() {
        return Collections.unmodifiableSet(provider.getLoadedItemIds());
    }

    public Set<String> getLoadedBlockIds() {
        return Collections.unmodifiableSet(provider.getLoadedBlockIds());
    }
}
