package com.thatguycy.worlddynamicsengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigurationTest {
    @TempDir Path folder;
    private final YamlConfiguration root = new YamlConfiguration();

    @Test
    void exportsOnlyMissingResourcesWithoutOverwritingExistingFiles() throws Exception {
        Files.writeString(folder.resolve("town.yml"), "rally:\n  enabled: false\n");
        var exported = new ArrayList<String>();
        var files = new ConfigurationFiles(folder::toFile, (path, replace) -> {
            assertFalse(replace);
            exported.add(path);
        }, () -> Logger.getLogger("PluginConfigurationTest"));

        files.ensureResources();

        assertTrue(exported.contains("nation.yml"));
        assertTrue(exported.contains("lang/messages_en.yml"));
        assertFalse(exported.contains("town.yml"));
        assertEquals("rally:\n  enabled: false\n", Files.readString(folder.resolve("town.yml")));
    }

    @Test
    void splitsLegacyTynanDataBeforeExportingNewDefaults() throws Exception {
        Path tynanFolder = Files.createDirectories(folder.resolve("lang/tynan"));
        Path legacyFile = tynanFolder.resolve("art-gen.yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("meta.version", "legacy");
        legacy.set("carriers", List.of(Map.of(
                "id", "sculpture",
                "label", Map.of("zh", "雕塑", "en", "Sculpture"))));
        legacy.set("rulepacks", Map.of("LegacyPack", Map.of("rulesByKeyword", Map.of(
                "root", List.of(Map.of("zh", "家具", "en", "furniture"))))));
        legacy.set("wordPools", Map.of("Animal", Map.of("zh", List.of("狼"))));
        legacy.set("tales", List.of(Map.of(
                "id", "LegacyTale",
                "package", "core",
                "label", Map.of("zh", "旧故事", "en", "Legacy tale"),
                "rulesByKeyword", Map.of("image", List.of(Map.of("zh", "故事", "en", "a tale"))))));
        legacy.save(legacyFile.toFile());
        var files = new ConfigurationFiles(
                folder::toFile, (path, replace) -> { }, () -> Logger.getLogger("PluginConfigurationTest"));

        files.ensureResources();

        assertFalse(Files.exists(legacyFile));
        var chineseCore = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/zh/core.yml").toFile());
        var englishCore = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/en/core.yml").toFile());
        var chineseRules = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/zh/rulepacks/base.yml").toFile());
        var englishRules = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/en/rulepacks/base.yml").toFile());
        var chineseTales = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/zh/tales/core-01.yml").toFile());
        var englishTales = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/en/tales/core-01.yml").toFile());
        assertEquals("legacy", chineseCore.getString("meta.version"));
        assertEquals("雕塑", ((Map<?, ?>) chineseCore.getMapList("carriers").get(0).get("label")).get("zh"));
        assertEquals("Sculpture", ((Map<?, ?>) englishCore.getMapList("carriers").get(0).get("label")).get("en"));
        assertTrue(chineseRules.contains("rulepacks.LegacyPack"));
        Map<?, ?> chineseRule = (Map<?, ?>) chineseRules
                .getMapList("rulepacks.LegacyPack.rulesByKeyword.root").get(0);
        Map<?, ?> englishRule = (Map<?, ?>) englishRules
                .getMapList("rulepacks.LegacyPack.rulesByKeyword.root").get(0);
        assertEquals("家具", chineseRule.get("zh"));
        assertFalse(chineseRule.containsKey("en"));
        assertEquals("furniture", englishRule.get("en"));
        assertFalse(englishRule.containsKey("zh"));
        assertEquals("LegacyTale",
                chineseTales.getMapList("tales").get(0).get("id"));
        assertEquals("旧故事", ((Map<?, ?>) chineseTales.getMapList("tales").get(0).get("label")).get("zh"));
        assertEquals("Legacy tale", ((Map<?, ?>) englishTales.getMapList("tales").get(0).get("label")).get("en"));
    }

    @Test
    void migratesThePreviousCombinedRulepackFile() throws Exception {
        Path tynanFolder = Files.createDirectories(folder.resolve("lang/tynan"));
        Path legacyFile = tynanFolder.resolve("rulepacks.yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("rulepacks.ArtDescription_Furniture.rulesByKeyword.root",
                List.of(Map.of("zh", "家具", "en", "furniture")));
        legacy.set("rulepacks.NamerArtCommon.rulesByKeyword.name",
                List.of(Map.of("zh", "作品", "en", "artwork")));
        legacy.save(legacyFile.toFile());
        var files = new ConfigurationFiles(
                folder::toFile, (path, replace) -> { }, () -> Logger.getLogger("PluginConfigurationTest"));

        files.ensureResources();

        assertFalse(Files.exists(legacyFile));
        var carrierRules = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/zh/rulepacks/carriers.yml").toFile());
        var namingRules = YamlConfiguration.loadConfiguration(
                folder.resolve("lang/tynan/en/rulepacks/naming.yml").toFile());
        assertTrue(carrierRules.contains("rulepacks.ArtDescription_Furniture"));
        assertTrue(namingRules.contains("rulepacks.NamerArtCommon"));
    }

    @Test
    void migratesLegacyFolderAndLocaleWithoutRepeatingTheMove() throws Exception {
        Path legacy = Files.createDirectory(folder.resolve("WorldDynamicsEngine"));
        Files.writeString(legacy.resolve("messages_en.yml"), "message: legacy");
        Path current = folder.resolve("WorldDynamicsEngine-Reborn");
        var configs = configuration(current);

        configs.migrateLegacyDataFolderIfNeeded();
        configs.ensureResources();
        configs.migrateLegacyDataFolderIfNeeded();
        configs.ensureResources();

        assertFalse(Files.exists(legacy));
        assertFalse(Files.exists(current.resolve("messages_en.yml")));
        assertEquals("message: legacy", Files.readString(current.resolve("lang/messages_en.yml")));
    }

    @Test
    void migratesLegacyConfigAndPreservesUnrelatedCustomSettingsOnReload() throws Exception {
        root.set("validGovernmentTypes", List.of("custom"));
        root.set("religionEnabled", false);
        Files.writeString(folder.resolve("town.yml"), "rally:\n  cost: '42.00'\n");
        Files.writeString(folder.resolve("ideology.yml"), """
                economy:
                  treasury:
                    defaultTitheType: WEALTH_PERCENTAGE
                    defaultTitheAmount: '15'
                memes:
                  registry:
                    custom:
                      moodEffects:
                        inspiration: custom_effect
                """);
        var configs = configuration(folder);
        configs.loadAndMigrate();
        assertFalse(configs.readConfigSettings().religionEnabled());
        assertFalse(root.contains("validGovernmentTypes"));
        assertFalse(root.contains("religionEnabled"));
        assertEquals(List.of("custom"), configs.getValidGovernmentTypes());
        assertEquals("42.00", configs.getTownConfig().getString("rally.cost"));
        assertTrue(configs.getTownConfig().getBoolean("rally.enabled"));
        assertFalse(configs.getTownConfig().getBoolean("history.enabled", true));
        assertTrue(configs.getTownConfig().getBoolean("wealth.enabled"));
        assertEquals("100.00", configs.getTownConfig().getString("wealth.plot-value"));
        assertEquals("1000.00", configs.getTownConfig().getString("wealth.population-value"));
        assertEquals(6000L, configs.getTownConfig().getLong("wealth.refresh-interval-ticks"));

        configs.loadAndMigrate();
        assertEquals(List.of("custom_effect"), configs.getIdeologyConfig()
                .getStringList("memes.registry.custom.moodEffects.inspiration"));
        assertFalse(configs.getIdeologyConfig().contains("economy.treasury.defaultTitheType"));
        assertEquals("0", configs.getIdeologyConfig().getString("economy.treasury.defaultTitheAmount"));
        assertEquals("42.00", configs.getTownConfig().getString("rally.cost"));
        assertEquals("100.00", configs.getTownConfig().getString("wealth.plot-value"));
    }

    @Test
    void migratesFlatBackgroundPresetsToTheirStagesWithoutOverwritingCustomFilter() throws Exception {
        Files.writeString(folder.resolve("backgrounds.yml"), """
                enabled: true
                forceSelectionOnFirstJoin: true
                content-filter:
                  enabled: true
                  blocked-words:
                    - "custom-blocked-word"
                backgrounds:
                  farm_child:
                    title: "My Farm Childhood"
                    lore:
                      - "A custom childhood story."
                    skillPoints:
                      "auraskills/farming": 5
                  custom_life:
                    title: "A Custom Life"
                    lore:
                      - "A custom story for both stages."
                    skillPoints:
                      "auraskills/mining": 3
                      "auraskills/farming": 2
                """);
        var configs = configuration(folder);

        configs.loadAndMigrate();

        var backgrounds = configs.getBackgroundConfig();
        assertEquals("My Farm Childhood",
                backgrounds.getString("stages.childhood.backgrounds.farm_child.title"));
        assertFalse(backgrounds.contains("stages.adulthood.backgrounds.farm_child"));
        assertTrue(backgrounds.contains("stages.childhood.backgrounds.custom_life"));
        assertTrue(backgrounds.contains("stages.adulthood.backgrounds.custom_life"));
        Map<String, Object> migratedPoints = backgrounds.getConfigurationSection(
                "stages.adulthood.backgrounds.custom_life.skillPoints").getValues(false);
        assertEquals(3, migratedPoints.get("auraskills/mining"));
        assertEquals(2, migratedPoints.get("auraskills/farming"));
        assertEquals(List.of("custom-blocked-word"),
                backgrounds.getStringList("content-filter.blocked-words"));
    }

    @Test
    void hotReadsOnlyMoodExpectationsAndLeavesLastValuesOnInvalidYaml() throws Exception {
        var configs = configuration(folder);
        configs.loadAndMigrate();
        var ideology = folder.resolve("ideology.yml");
        Files.writeString(ideology, "religionEnabled: false\nmood:\n  expectations:\n    roles:\n      townMayor: -25\n");

        assertTrue(configs.reloadMoodExpectationSettingsFromDisk());
        assertFalse(configs.reloadMoodExpectationSettingsFromDisk());
        assertEquals(-25, configs.getIdeologyConfig().getInt("mood.expectations.roles.townMayor"));
        assertTrue(configs.getIdeologyConfig().getBoolean("religionEnabled"));
        Files.writeString(ideology, "mood: [");

        assertFalse(configs.reloadMoodExpectationSettingsFromDisk());
        assertEquals(-25, configs.getIdeologyConfig().getInt("mood.expectations.roles.townMayor"));
        assertEquals("mood: [", Files.readString(ideology));
    }

    private PluginConfiguration configuration(Path dataFolder) {
        return new PluginConfiguration(dataFolder::toFile, () -> root, () -> {}, (path, replace) -> {},
                () -> Logger.getLogger("PluginConfigurationTest"));
    }
}
