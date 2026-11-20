package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MiningDoctrineCalculatorTest {
    @Test
    void matchesOnlyConfiguredVanillaOreMaterials() {
        Set<Material> configuredBlocks = Set.of(
                Material.STONE, Material.DEEPSLATE_DIAMOND_ORE);
        assertTrue(MiningDoctrineCalculator.isConfiguredOre(
                Material.DEEPSLATE_DIAMOND_ORE, null,
                configuredBlocks, Set.of()));
        assertTrue(MiningDoctrineCalculator.isConfiguredOre(
                Material.STONE, null, configuredBlocks, Set.of()));
        assertFalse(MiningDoctrineCalculator.isConfiguredOre(
                Material.COBBLESTONE, null, configuredBlocks, Set.of()));
    }

    @Test
    void matchesCraftEngineOresByFullIdWithoutVanillaFallback() {
        assertTrue(MiningDoctrineCalculator.isConfiguredOre(
                Material.STONE, "Ores:Ruby_Ore", Set.of(), Set.of("ores:ruby_ore")));
        assertFalse(MiningDoctrineCalculator.isConfiguredOre(
                Material.DIAMOND_ORE, "ores:custom_diamond_ore",
                Set.of(Material.DIAMOND_ORE), Set.of()));
    }

    @Test
    void miningAttitudesClassifyAsViolationNeutralOrUnmapped() {
        assertFalse(MiningDoctrineCalculator.assess("namespace:wder/mining/taboo"));
        assertFalse(MiningDoctrineCalculator.assess("fear"));
        assertTrue(MiningDoctrineCalculator.assess("neutral"));
        assertNull(MiningDoctrineCalculator.assess("support"));
    }
}
