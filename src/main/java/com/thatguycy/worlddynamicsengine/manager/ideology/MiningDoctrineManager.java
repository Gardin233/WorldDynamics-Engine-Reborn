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

/** Applies the mining doctrine to player-mined blocks configured as ores. */
public final class MiningDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/mining";
    private static final String CONFIG_PATH = "mood.doctrineActions.mining.";

    private final WorldDynamicsEngine plugin;
    private Set<Material> vanillaOres = Set.of();
    private Set<String> craftEngineOres = Set.of();
    private boolean enabled;

    public MiningDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getIdeologyConfig();
        enabled = config == null || config.getBoolean(CONFIG_PATH + "enabled", true);

        EnumSet<Material> loadedVanillaOres = EnumSet.noneOf(Material.class);
        List<String> vanillaOreNames = config == null
                ? List.of() : config.getStringList(CONFIG_PATH + "ores.vanilla");
        for (String name : vanillaOreNames) {
            Material material = Material.matchMaterial(name.trim());
            if (material == null || material.isAir() || !material.isBlock()) {
                plugin.getLogger().warning("Invalid vanilla mining ore block: " + name);
                continue;
            }
            loadedVanillaOres.add(material);
        }

        Set<String> loadedCraftEngineOres = new LinkedHashSet<>();
        List<String> craftEngineOreIds = config == null
                ? List.of() : config.getStringList(CONFIG_PATH + "ores.craftengine");
        for (String id : craftEngineOreIds) {
            String normalizedId = normalize(id);
            if (!normalizedId.isBlank()) loadedCraftEngineOres.add(normalizedId);
        }
        vanillaOres = Collections.unmodifiableSet(loadedVanillaOres);
        craftEngineOres = Collections.unmodifiableSet(loadedCraftEngineOres);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        apply(event.getPlayer(), event.getBlock());
    }

    public void apply(Player player, Block block) {
        if (!enabled || !plugin.religionEnabled || player == null || block == null
                || !player.isOnline() || player.isDead()
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            return;
        }

        var craftEngine = plugin.getCraftEngineIntegration();
        String craftEngineId = craftEngine == null ? null : craftEngine.getBlockId(block);
        if (!MiningDoctrineCalculator.isConfiguredOre(
                block.getType(), craftEngineId, vanillaOres, craftEngineOres)) {
            return;
        }

        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) {
            return;
        }

        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        Boolean compliant = MiningDoctrineCalculator.assess(attitude);
        if (compliant == null) return;

        int weight = Math.max(0, plugin.getReligionManager().getDoctrineMoodWeight(DOCTRINE_ID, attitude));
        int moodChange = weight * Math.max(0,
                plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                        DOCTRINE_ID, attitude, compliant, null));
        var moodEntry = new DoctrineMoodEntry(DOCTRINE_ID, attitude, compliant, moodChange);
        plugin.getMoodManager().applyDoctrineMoodChanges(player,
                compliant ? moodChange : 0,
                compliant ? 0 : moodChange,
                moodEntry.source());
        if (!compliant && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
