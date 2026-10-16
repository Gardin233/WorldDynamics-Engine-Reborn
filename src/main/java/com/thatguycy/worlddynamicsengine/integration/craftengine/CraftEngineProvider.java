package com.thatguycy.worlddynamicsengine.integration.craftengine;

import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 定义 CraftEngine 适配器契约，使业务层不直接依赖特定版本的自定义资源 API。
 */
public interface CraftEngineProvider {
    String getItemId(ItemStack itemStack);

    String getBlockId(Block block);

    ItemStack createItemStack(String itemId, Player player);

    Set<String> getLoadedItemIds();

    Set<String> getLoadedBlockIds();
}
