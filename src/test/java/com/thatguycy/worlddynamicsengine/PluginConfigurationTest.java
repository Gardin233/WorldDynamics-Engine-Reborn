package com.thatguycy.worlddynamicsengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

        configs.loadAndMigrate();
        assertEquals(List.of("custom_effect"), configs.getIdeologyConfig()
                .getStringList("memes.registry.custom.moodEffects.inspiration"));
        assertFalse(configs.getIdeologyConfig().contains("economy.treasury.defaultTitheType"));
        assertEquals("0", configs.getIdeologyConfig().getString("economy.treasury.defaultTitheAmount"));
        assertEquals("42.00", configs.getTownConfig().getString("rally.cost"));
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
