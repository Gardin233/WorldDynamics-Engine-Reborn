package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/** Applies temporary mood changes when configured trees are cut or planted. */
public final class LoggingTreesDoctrineManager implements Listener {
    private static final String LOGGING_DOCTRINE_ID = "namespace:wder/logging";
    private static final String TREES_DOCTRINE_ID = "namespace:wder/trees";
    private static final String CONFIG_PATH = "mood.doctrineActions.";

    private final WorldDynamicsEngine plugin;
    private Set<Material> vanillaWood = Set.of();
    private Set<String> craftEngineWood = Set.of();
    private Set<Material> vanillaSaplings = Set.of();
    private Set<String> craftEngineSaplings = Set.of();
    private boolean loggingEnabled;
    private boolean treesEnabled;

    public LoggingTreesDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getIdeologyConfig();
        loggingEnabled = config == null || config.getBoolean(CONFIG_PATH + "logging.enabled", true);
        treesEnabled = config == null || config.getBoolean(CONFIG_PATH + "trees.enabled", true);

        vanillaWood = loadMaterials(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "logging.wood.vanilla"), true,
                "vanilla logging wood");
        craftEngineWood = loadIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "logging.wood.craftengine"));
        vanillaSaplings = loadMaterials(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "trees.saplings.vanilla"), false,
                "vanilla tree sapling");
        craftEngineSaplings = loadIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "trees.saplings.craftengine"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        applyLogging(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        applyPlanting(event.getPlayer(), event.getItemInHand(), event.getBlockPlaced());
    }

    private void applyLogging(Player player, Block block) {
        if (!loggingEnabled || !canApply(player)) return;
        String craftEngineBlockId = getCraftEngineBlockId(block);
        if (!LoggingTreesDoctrineCalculator.isConfiguredWood(
                block.getType(), craftEngineBlockId, vanillaWood, craftEngineWood)) {
            return;
        }
        String attitude = getAttitude(player, LOGGING_DOCTRINE_ID);
        applyDoctrineOutcome(player, LOGGING_DOCTRINE_ID, attitude,
                LoggingTreesDoctrineCalculator.assessLogging(attitude));
    }

    private void applyPlanting(Player player, ItemStack item, Block placedBlock) {
        if (!treesEnabled || !canApply(player) || item == null || placedBlock == null) return;
        String craftEngineItemId = getCraftEngineItemId(item);
        String craftEngineBlockId = getCraftEngineBlockId(placedBlock);
        if (!LoggingTreesDoctrineCalculator.isConfiguredSapling(
                item.getType(), craftEngineItemId, placedBlock.getType(), craftEngineBlockId,
                vanillaSaplings, craftEngineSaplings)) {
            return;
        }
        String attitude = getAttitude(player, TREES_DOCTRINE_ID);
        applyDoctrineOutcome(player, TREES_DOCTRINE_ID, attitude,
                LoggingTreesDoctrineCalculator.assessPlanting(attitude));
    }

    private boolean canApply(Player player) {
        return plugin.religionEnabled && player != null && player.isOnline() && !player.isDead()
                && plugin.getMoodManager().shouldProcessMoodAction(player);
    }

    private String getAttitude(Player player, String doctrineId) {
        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(doctrineId) == null) return null;
        return religion.getDoctrineAttitude(doctrineId);
    }

    private void applyDoctrineOutcome(Player player, String doctrineId, String attitude, Boolean compliant) {
        if (attitude == null || compliant == null) return;

        int weight = Math.max(0, plugin.getReligionManager().getDoctrineMoodWeight(doctrineId, attitude));
        int moodChange = weight * Math.max(0,
                plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                        doctrineId, attitude, compliant, null));
        DoctrineMoodEntry moodEntry = new DoctrineMoodEntry(doctrineId, attitude, compliant, moodChange);
        plugin.getMoodManager().applyDoctrineMoodChanges(player,
                compliant ? moodChange : 0,
                compliant ? 0 : moodChange,
                moodEntry.source());
        if (!compliant && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, doctrineId);
        }
    }

    private String getCraftEngineBlockId(Block block) {
        var integration = plugin.getCraftEngineIntegration();
        return integration == null ? null : integration.getBlockId(block);
    }

    private String getCraftEngineItemId(ItemStack item) {
        var integration = plugin.getCraftEngineIntegration();
        return integration == null ? null : integration.getItemId(item);
    }

    private Set<Material> loadMaterials(List<String> names, boolean blocksOnly, String label) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name.trim());
            if (material == null || material.isAir() || (blocksOnly && !material.isBlock())) {
                plugin.getLogger().warning("Invalid " + label + " material: " + name);
                continue;
            }
            result.add(material);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> loadIds(List<String> ids) {
        Set<String> result = new LinkedHashSet<>();
        for (String id : ids) {
            String normalized = normalize(id);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return Collections.unmodifiableSet(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
