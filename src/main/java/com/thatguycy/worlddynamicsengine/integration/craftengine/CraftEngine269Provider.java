package com.thatguycy.worlddynamicsengine.integration.craftengine;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 封装 CraftEngine 26.9 API 的物品、方块识别和物品构建，仅在依赖可用时加载。
 */
public final class CraftEngine269Provider implements CraftEngineProvider {
    @Override
    public String getItemId(ItemStack itemStack) {
        if (itemStack == null || !CraftEngineItems.isCustomItem(itemStack)) {
            return null;
        }
        Key itemId = CraftEngineItems.getCustomItemId(itemStack);
        return itemId == null ? null : itemId.asString();
    }

    @Override
    public String getBlockId(Block block) {
        if (block == null || !CraftEngineBlocks.isCustomBlock(block)) {
            return null;
        }
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.owner() == null || !state.owner().isBound()) {
            return null;
        }
        return state.owner().value().id().asString();
    }

    @Override
    public ItemStack createItemStack(String itemId, Player player) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        var definition = CraftEngineItems.byId(itemId);
        return definition == null ? null : definition.buildBukkitItem(player);
    }

    @Override
    public Set<String> getLoadedItemIds() {
        return CraftEngineItems.loadedItems().keySet().stream()
                .map(Key::asString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<String> getLoadedBlockIds() {
        return CraftEngineBlocks.loadedBlocks().keySet().stream()
                .map(Key::asString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
