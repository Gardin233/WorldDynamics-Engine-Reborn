package com.thatguycy.worlddynamicsengine.manager.text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads shared presets and persists each player's editable presets in a separate YAML file. */
final class TynanPresetStore {
    private final File dataFolder;
    private final Logger logger;
    private FileConfiguration sharedConfig;

    TynanPresetStore(File dataFolder, Logger logger, FileConfiguration sharedConfig) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        reload(sharedConfig);
    }

    void reload(FileConfiguration sharedConfig) {
        this.sharedConfig = sharedConfig == null
                ? YamlConfiguration.loadConfiguration(sharedFile()) : sharedConfig;
    }

    List<TynanPersonPreset> list(UUID playerId) {
        Map<String, TynanPersonPreset> merged = new LinkedHashMap<>();
        for (TynanPersonPreset preset : readPresets(sharedConfig, "presets")) {
            merged.put(preset.name(), preset);
        }
        if (playerId != null) {
            File playerFile = playerFile(playerId);
            if (playerFile.exists()) {
                FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                for (TynanPersonPreset preset : readPresets(playerConfig, "presets")) {
                    merged.put(preset.name(), preset);
                }
            }
        }
        return List.copyOf(merged.values());
    }

    TynanPersonPreset defaultPreset(UUID playerId) {
        if (playerId != null) {
            File playerFile = playerFile(playerId);
            if (playerFile.exists()) {
                TynanPersonPreset personalDefault = readPresets(
                        YamlConfiguration.loadConfiguration(playerFile), "presets").stream()
                        .filter(TynanPersonPreset::auto)
                        .findFirst().orElse(null);
                if (personalDefault != null) {
                    return personalDefault;
                }
            }
        }
        return readPresets(sharedConfig, "presets").stream()
                .filter(TynanPersonPreset::auto)
                .findFirst().orElse(null);
    }

    boolean save(UUID playerId, TynanPersonPreset preset) {
        if (playerId == null || preset == null || preset.name().isBlank()) {
            return false;
        }
        FileConfiguration playerConfig = loadPlayerConfig(playerId);
        List<Map<String, Object>> presets = serializedPresets(playerConfig);
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Map<String, Object> value : presets) {
            if (preset.name().equals(value.get("name"))) {
                continue;
            }
            if (preset.auto()) {
                value.put("auto", false);
            }
            updated.add(value);
        }
        updated.add(toMap(preset));
        playerConfig.set("presets", updated);
        return savePlayerConfig(playerId, playerConfig);
    }

    boolean delete(UUID playerId, String presetName) {
        if (playerId == null || presetName == null || presetName.isBlank()) {
            return false;
        }
        File playerFile = playerFile(playerId);
        if (!playerFile.exists()) {
            return false;
        }
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        List<Map<String, Object>> updated = serializedPresets(playerConfig).stream()
                .filter(value -> !presetName.equals(value.get("name")))
                .toList();
        if (updated.size() == serializedPresets(playerConfig).size()) {
            return false;
        }
        playerConfig.set("presets", updated);
        return savePlayerConfig(playerId, playerConfig);
    }

    private List<TynanPersonPreset> readPresets(FileConfiguration config, String path) {
        List<TynanPersonPreset> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        for (Map<?, ?> value : config.getMapList(path)) {
            String name = stringValue(value.get("name"));
            if (name.isBlank()) {
                continue;
            }
            result.add(new TynanPersonPreset(
                    name,
                    stringValue(value.get("title")),
                    stringValue(value.get("pronounId")),
                    Boolean.TRUE.equals(value.get("auto"))));
        }
        return result;
    }

    private List<Map<String, Object>> serializedPresets(FileConfiguration config) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> value : config.getMapList("presets")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            value.forEach((key, item) -> copy.put(String.valueOf(key), item));
            result.add(copy);
        }
        return result;
    }

    private Map<String, Object> toMap(TynanPersonPreset preset) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", preset.name());
        value.put("title", preset.title());
        value.put("pronounId", preset.pronounId());
        value.put("auto", preset.auto());
        return value;
    }

    private FileConfiguration loadPlayerConfig(UUID playerId) {
        File file = playerFile(playerId);
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private boolean savePlayerConfig(UUID playerId, FileConfiguration config) {
        File file = playerFile(playerId);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create Tynan preset folder: " + parent.getAbsolutePath());
            return false;
        }
        try {
            config.save(file);
            return true;
        } catch (IOException exception) {
            logger.warning("Could not save Tynan presets for " + playerId + ": " + exception.getMessage());
            return false;
        }
    }

    private File sharedFile() {
        return new File(dataFolder, "lang/tynan/presets.yml");
    }

    private File playerFile(UUID playerId) {
        return new File(dataFolder, "lang/tynan/presets/" + playerId + ".yml");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
