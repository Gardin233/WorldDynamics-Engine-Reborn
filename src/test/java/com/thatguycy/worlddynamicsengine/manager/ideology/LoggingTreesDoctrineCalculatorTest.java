package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LoggingTreesDoctrineCalculatorTest {
    @Test
    void woodMatchingUsesConfiguredVanillaAndCraftEngineBlockLists() {
        assertTrue(LoggingTreesDoctrineCalculator.isConfiguredWood(
                Material.STRIPPED_DARK_OAK_LOG, null,
                Set.of(Material.STRIPPED_DARK_OAK_LOG), Set.of()));
        assertTrue(LoggingTreesDoctrineCalculator.isConfiguredWood(
                Material.STONE, "Woods:Ancient_Log",
                Set.of(), Set.of("woods:ancient_log")));
        assertFalse(LoggingTreesDoctrineCalculator.isConfiguredWood(
                Material.OAK_LOG, "woods:custom_oak",
                Set.of(Material.OAK_LOG), Set.of()));
    }

    @Test
    void saplingMatchingSupportsVanillaMaterialsAndCraftEngineItemOrBlockIds() {
        assertTrue(LoggingTreesDoctrineCalculator.isConfiguredSapling(
                Material.OAK_SAPLING, null, Material.OAK_SAPLING, null,
                Set.of(Material.OAK_SAPLING), Set.of()));
        assertTrue(LoggingTreesDoctrineCalculator.isConfiguredSapling(
                Material.STICK, "Trees:Ruby_Sapling", Material.OAK_SAPLING, null,
                Set.of(), Set.of("trees:ruby_sapling")));
        assertTrue(LoggingTreesDoctrineCalculator.isConfiguredSapling(
                Material.STICK, null, Material.STONE, "trees:ancient_sapling",
                Set.of(), Set.of("trees:ancient_sapling")));
        assertFalse(LoggingTreesDoctrineCalculator.isConfiguredSapling(
                Material.OAK_SAPLING, "trees:unlisted_sapling", Material.OAK_SAPLING, null,
                Set.of(Material.OAK_SAPLING), Set.of()));
    }

    @Test
    void loggingAndPlantingUseTheirExistingDoctrineAttitudes() {
        assertFalse(LoggingTreesDoctrineCalculator.assessLogging("namespace:wder/logging/taboo"));
        assertTrue(LoggingTreesDoctrineCalculator.assessLogging("neutral"));
        assertNull(LoggingTreesDoctrineCalculator.assessLogging("support"));
        assertTrue(LoggingTreesDoctrineCalculator.assessPlanting("crave"));
        assertTrue(LoggingTreesDoctrineCalculator.assessPlanting("neutral"));
        assertNull(LoggingTreesDoctrineCalculator.assessPlanting("required"));
    }
}
