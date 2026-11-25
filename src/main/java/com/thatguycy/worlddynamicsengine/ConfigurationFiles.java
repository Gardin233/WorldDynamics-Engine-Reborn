package com.thatguycy.worlddynamicsengine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Owns resource export, YAML I/O and legacy filesystem moves. */
final class ConfigurationFiles {
    private static final String LEGACY_DATA_FOLDER_NAME = "WorldDynamicsEngine";
    private final Supplier<File> dataFolder;
    private final BiConsumer<String, Boolean> exportResource;
    private final Supplier<Logger> logger;

    ConfigurationFiles(Supplier<File> dataFolder, BiConsumer<String, Boolean> exportResource, Supplier<Logger> logger) {
        this.dataFolder = dataFolder;
        this.exportResource = exportResource;
        this.logger = logger;
    }

    File resolve(String name) {
        return new File(dataFolder.get(), name);
    }

    FileConfiguration load(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    FileConfiguration loadStrict(File file) throws IOException, InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.load(file);
        return config;
    }

    void ensureResources() {
        if (!dataFolder.get().exists() && !dataFolder.get().mkdirs()) {
            logger.get().warning("Failed to create plugin data folder.");
        }

        saveDefaultResource("config.yml");
        saveDefaultResource("nation.yml");
        saveDefaultResource("town.yml");
        saveDefaultResource("organization.yml");
        saveDefaultResource("war.yml");
        saveDefaultResource("ideology.yml");
        saveDefaultResource("backgrounds.yml");
        saveDefaultResource("drops.yml");
        saveDefaultResource("foods.yml");
        saveDefaultResource("relics.yml");
        saveDefaultResource("inn.yml");
        saveDefaultResource("lang/messages_en.yml");
        saveDefaultResource("lang/messages_es.yml");
        saveDefaultResource("lang/messages_zh_cn.yml");
        migrateLegacyLocales();
    }

    void migrateLegacyDataFolderIfNeeded() {
        File currentDataFolder = dataFolder.get();
        File parentFolder = currentDataFolder.getParentFile();
        if (parentFolder == null) {
            return;
        }

        File legacyDataFolder = new File(parentFolder, LEGACY_DATA_FOLDER_NAME);
        if (!legacyDataFolder.exists() || legacyDataFolder.equals(currentDataFolder) || currentDataFolder.exists()) {
            return;
        }

        try {
            Files.move(legacyDataFolder.toPath(), currentDataFolder.toPath());
            logger.get().info("Migrated legacy plugin data folder from " + legacyDataFolder.getName()
                    + " to " + currentDataFolder.getName() + ".");
        } catch (Exception e) {
            logger.get().warning("Failed to migrate legacy plugin data folder from "
                    + legacyDataFolder.getAbsolutePath() + " to " + currentDataFolder.getAbsolutePath()
                    + ": " + e.getMessage());
        }
    }

    private void saveDefaultResource(String resourcePath) {
        File targetFile = new File(dataFolder.get(), resourcePath);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.get().warning("Failed to create resource folder: " + parent.getAbsolutePath());
            return;
        }

        if (!targetFile.exists()) {
            exportResource.accept(resourcePath, false);
        }
    }

    void save(FileConfiguration configuration, File file) {
        if (configuration == null || file == null) {
            return;
        }

        try {
            configuration.save(file);
        } catch (Exception e) {
            logger.get().warning("Failed to save configuration " + file.getName() + ": " + e.getMessage());
        }
    }

    private void migrateLegacyLocales() {
        File langFolder = new File(dataFolder.get(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            logger.get().warning("Failed to create lang folder: " + langFolder.getAbsolutePath());
            return;
        }

        String[] localeFiles = {"messages_en.yml", "messages_es.yml", "messages_zh_cn.yml"};
        for (String fileName : localeFiles) {
            File legacyFile = new File(dataFolder.get(), fileName);
            File targetFile = new File(langFolder, fileName);
            if (!legacyFile.exists()) {
                continue;
            }

            try {
                Files.move(legacyFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.get().info("Moved legacy locale file to lang/: " + fileName);
            } catch (Exception e) {
                logger.get().warning("Failed to move legacy locale file " + fileName + ": " + e.getMessage());
            }
        }
    }
}
