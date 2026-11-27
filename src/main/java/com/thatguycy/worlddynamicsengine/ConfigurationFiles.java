package com.thatguycy.worlddynamicsengine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
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
        saveDefaultResource("events.yml");
        migrateLegacyHistoryTemplate();
        migrateLegacyTynanData();
        migrateLegacyTynanRulepacks();
        saveDefaultResource("lang/tynan/presets.yml");
        migrateFlatTynanLanguages();
        for (String language : List.of("zh", "en")) {
            exportTynanLanguageDefaults(language);
        }
        saveDefaultResource("lang/messages_en.yml");
        saveDefaultResource("lang/messages_es.yml");
        saveDefaultResource("lang/messages_zh_cn.yml");
        migrateLegacyLocales();
    }

    private void exportTynanLanguageDefaults(String language) {
        String root = "lang/tynan/" + language + "/";
        saveDefaultResource(root + "core.yml");
        saveDefaultResource(root + "word-pools.yml");
        for (String category : List.of("base", "carriers", "naming")) {
            saveDefaultResource(root + "rulepacks/" + category + ".yml");
        }
        for (int chunk = 1; chunk <= 9; chunk++) {
            saveDefaultResource(root + "tales/core-%02d.yml".formatted(chunk));
        }
        for (String pack : List.of("royalty", "biotech", "anomaly", "odyssey")) {
            saveDefaultResource(root + "tales/" + pack + ".yml");
        }
    }

    private void migrateFlatTynanLanguages() {
        File tynanFolder = new File(dataFolder.get(), "lang/tynan");
        if (hasLanguageTynanData(tynanFolder) || !hasFlatTynanData(tynanFolder)) {
            return;
        }

        File coreFile = new File(tynanFolder, "core.yml");
        File wordPoolsFile = new File(tynanFolder, "word-pools.yml");
        Map<String, Object> coreValues = configurationValues(loadExistingYaml(coreFile));
        Map<String, Object> wordPoolValues = configurationValues(loadExistingYaml(wordPoolsFile));
        Map<String, Object> allRulepacks = loadFlatRulepacks(tynanFolder);
        Map<String, Map<String, Object>> groupedRulepacks = groupTynanRulepacks(allRulepacks);
        List<File> taleFiles = orderedFlatTaleFiles(new File(tynanFolder, "tales"));
        List<File> migratedFiles = new ArrayList<>();

        try {
            for (String language : List.of("zh", "en")) {
                File languageFolder = new File(tynanFolder, language);
                if (coreFile.exists()) {
                    File target = new File(languageFolder, "core.yml");
                    migratedFiles.add(target);
                    writeLocalizedTynanFragment(target, coreValues, language);
                }
                if (wordPoolsFile.exists()) {
                    File target = new File(languageFolder, "word-pools.yml");
                    migratedFiles.add(target);
                    writeLocalizedTynanFragment(target, wordPoolValues, language);
                }
                for (Map.Entry<String, Map<String, Object>> entry : groupedRulepacks.entrySet()) {
                    File target = new File(
                            new File(languageFolder, "rulepacks"), entry.getKey() + ".yml");
                    migratedFiles.add(target);
                    writeLocalizedTynanFragment(
                            target, Map.of("rulepacks", entry.getValue()), language);
                }
                for (File taleFile : taleFiles) {
                    Map<String, Object> taleValues =
                            configurationValues(loadExistingYaml(taleFile));
                    File target = new File(
                            new File(languageFolder, "tales"), taleFile.getName());
                    migratedFiles.add(target);
                    writeLocalizedTynanFragment(target, taleValues, language);
                }
            }
        } catch (Exception exception) {
            for (File migratedFile : migratedFiles) {
                try {
                    Files.deleteIfExists(migratedFile.toPath());
                } catch (IOException cleanupException) {
                    logger.get().warning("Could not remove partial language split "
                            + migratedFile.getAbsolutePath() + ": " + cleanupException.getMessage());
                }
            }
            logger.get().warning("Could not split flat Tynan files into language folders: "
                    + exception.getMessage());
            return;
        }

        deleteFlatTynanFiles(tynanFolder, coreFile, wordPoolsFile, taleFiles);
        logger.get().info("Split flat Tynan data into independent zh/ and en/ language folders.");
    }

    private Map<String, Object> configurationValues(FileConfiguration configuration) {
        Map<String, Object> values = new LinkedHashMap<>();
        configuration.getValues(false).forEach(
                (key, value) -> values.put(key, copyYamlValue(value)));
        return values;
    }

    private boolean hasFlatTynanData(File tynanFolder) {
        if (new File(tynanFolder, "core.yml").exists()
                || new File(tynanFolder, "word-pools.yml").exists()
                || new File(tynanFolder, "rulepacks.yml").exists()) {
            return true;
        }
        for (String directory : List.of("rulepacks", "tales")) {
            File folder = new File(tynanFolder, directory);
            File[] files = folder.listFiles((parent, name) -> name.endsWith(".yml"));
            if (files != null && files.length > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLanguageTynanData(File tynanFolder) {
        return hasYamlFiles(new File(tynanFolder, "zh"))
                || hasYamlFiles(new File(tynanFolder, "en"));
    }

    private boolean hasYamlFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isDirectory() ? hasYamlFiles(file) : file.getName().endsWith(".yml")) {
                return true;
            }
        }
        return false;
    }

    private FileConfiguration loadExistingYaml(File file) {
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private Map<String, Object> loadFlatRulepacks(File tynanFolder) {
        Map<String, Object> result = new LinkedHashMap<>();
        File combinedFile = new File(tynanFolder, "rulepacks.yml");
        addRulepacks(result, loadExistingYaml(combinedFile));
        File folder = new File(tynanFolder, "rulepacks");
        File[] files = folder.listFiles((parent, name) -> name.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files, (first, second) -> first.getName().compareTo(second.getName()));
            for (File file : files) {
                addRulepacks(result, loadExistingYaml(file));
            }
        }
        return result;
    }

    private void addRulepacks(Map<String, Object> target, FileConfiguration source) {
        ConfigurationSection section = source.getConfigurationSection("rulepacks");
        if (section == null) {
            return;
        }
        for (String packId : section.getKeys(false)) {
            target.put(packId, copyYamlValue(section.get(packId)));
        }
    }

    private List<File> orderedFlatTaleFiles(File talesFolder) {
        Map<String, File> byName = new LinkedHashMap<>();
        File[] files = talesFolder.listFiles((parent, name) -> name.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files, (first, second) -> first.getName().compareTo(second.getName()));
            for (File file : files) {
                byName.put(file.getName(), file);
            }
        }
        List<File> ordered = new ArrayList<>();
        for (int chunk = 1; chunk <= 9; chunk++) {
            addIfPresent(ordered, byName, "core-%02d.yml".formatted(chunk));
        }
        for (String pack : List.of("royalty", "biotech", "anomaly", "odyssey")) {
            addIfPresent(ordered, byName, pack + ".yml");
        }
        byName.values().stream()
                .filter(file -> !ordered.contains(file))
                .forEach(ordered::add);
        return ordered;
    }

    private void addIfPresent(List<File> ordered, Map<String, File> files, String name) {
        File file = files.get(name);
        if (file != null) {
            ordered.add(file);
        }
    }

    private void writeLocalizedTynanFragment(File file, Object values, String language)
            throws IOException {
        Object localized = localizeTynanValue(copyYamlValue(values), language, "");
        if (!(localized instanceof Map<?, ?> map)) {
            throw new IOException("Expected a YAML map at " + file.getAbsolutePath());
        }
        Map<String, Object> stringKeys = new LinkedHashMap<>();
        map.forEach((key, value) -> stringKeys.put(String.valueOf(key), value));
        writeTynanFragment(file, stringKeys);
    }

    private Object localizeTynanValue(Object value, String language, String parentKey) {
        if (value instanceof Map<?, ?> source) {
            boolean hasLanguageValues = source.containsKey("zh") || source.containsKey("en");
            Map<String, Object> localized = new LinkedHashMap<>();
            source.forEach((keyValue, item) -> {
                String key = String.valueOf(keyValue);
                if (!hasLanguageValues || (!"zh".equals(key) && !"en".equals(key))) {
                    localized.put(key, localizeTynanValue(item, language, key));
                }
            });
            if (hasLanguageValues) {
                Object text = source.get(language);
                if (text == null) {
                    text = source.get("zh".equals(language) ? "en" : "zh");
                }
                if (text != null) {
                    localized.put(language, localizeTynanValue(text, language, ""));
                }
                if ("artstyle_adj".equals(parentKey)) {
                    Object id = source.get("en");
                    if (id == null) {
                        id = source.get("zh");
                    }
                    if (id != null) {
                        localized.put("id", id);
                    }
                }
            }
            return localized;
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> localizeTynanValue(item, language, parentKey))
                    .toList();
        }
        return value;
    }

    private void deleteFlatTynanFiles(File tynanFolder, File coreFile,
                                      File wordPoolsFile, List<File> taleFiles) {
        for (File file : List.of(coreFile, wordPoolsFile, new File(tynanFolder, "rulepacks.yml"))) {
            deleteTynanFile(file);
        }
        deleteYamlFiles(new File(tynanFolder, "rulepacks"));
        for (File file : taleFiles) {
            deleteTynanFile(file);
        }
        new File(tynanFolder, "rulepacks").delete();
        new File(tynanFolder, "tales").delete();
    }

    private void deleteYamlFiles(File folder) {
        File[] files = folder.listFiles((parent, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                deleteTynanFile(file);
            }
        }
    }

    private void deleteTynanFile(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException exception) {
            logger.get().warning("Could not remove migrated Tynan file "
                    + file.getAbsolutePath() + ": " + exception.getMessage());
        }
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

    private void migrateLegacyHistoryTemplate() {
        File legacyFile = new File(dataFolder.get(), "event.yml");
        File historyFile = new File(dataFolder.get(), "history.yml");
        if (!legacyFile.exists() || historyFile.exists()) {
            return;
        }

        try {
            Files.move(legacyFile.toPath(), historyFile.toPath());
            logger.get().info("Moved legacy history template event.yml to history.yml.");
        } catch (IOException exception) {
            logger.get().warning("Failed to migrate event.yml to history.yml: " + exception.getMessage());
        }
    }

    private void migrateLegacyTynanData() {
        File tynanFolder = new File(dataFolder.get(), "lang/tynan");
        File legacyFile = new File(tynanFolder, "art-gen.yml");
        if (!legacyFile.exists() || hasSplitTynanData(tynanFolder)) {
            return;
        }

        List<File> migratedFiles = new ArrayList<>();
        try {
            FileConfiguration legacy = new YamlConfiguration();
            legacy.load(legacyFile);
            Map<String, Object> core = new LinkedHashMap<>();
            for (String key : List.of("meta", "carriers", "systemRulePackIds",
                    "utilityAliases", "pronouns", "qualities", "pipeline")) {
                if (legacy.contains(key)) {
                    core.put(key, copyYamlValue(legacy.get(key)));
                }
            }
            File coreFile = new File(tynanFolder, "core.yml");
            migratedFiles.add(coreFile);
            writeTynanFragment(coreFile, core);
            Map<String, Map<String, Object>> groupedRulepacks =
                    groupTynanRulepacks(legacy.getConfigurationSection("rulepacks"));
            for (Map.Entry<String, Map<String, Object>> entry : groupedRulepacks.entrySet()) {
                File rulepackFile = new File(
                        new File(tynanFolder, "rulepacks"), entry.getKey() + ".yml");
                migratedFiles.add(rulepackFile);
                writeTynanFragment(rulepackFile, Map.of("rulepacks", entry.getValue()));
            }
            File wordPoolsFile = new File(tynanFolder, "word-pools.yml");
            migratedFiles.add(wordPoolsFile);
            writeTynanFragment(wordPoolsFile,
                    Map.of("wordPools", copyYamlValue(legacy.get("wordPools"))));

            Map<String, List<Map<String, Object>>> talesByPackage = new LinkedHashMap<>();
            for (Map<?, ?> tale : legacy.getMapList("tales")) {
                Map<String, Object> copiedTale = new LinkedHashMap<>();
                tale.forEach((key, value) ->
                        copiedTale.put(String.valueOf(key), copyYamlValue(value)));
                String packageName = String.valueOf(copiedTale.getOrDefault("package", "custom"));
                if (!packageName.matches("[a-z0-9_-]+")) {
                    packageName = "custom";
                }
                talesByPackage.computeIfAbsent(packageName, ignored -> new ArrayList<>()).add(copiedTale);
            }

            List<Map<String, Object>> coreTales = talesByPackage.getOrDefault("core", List.of());
            int coreChunk = 1;
            for (int start = 0; start < coreTales.size(); start += 9) {
                List<Map<String, Object>> chunk = coreTales.subList(
                        start, Math.min(start + 9, coreTales.size()));
                String fileName = String.format(Locale.ROOT, "core-%02d.yml", coreChunk++);
                File taleFile = new File(new File(tynanFolder, "tales"), fileName);
                migratedFiles.add(taleFile);
                writeTynanFragment(taleFile,
                        Map.of("tales", chunk));
            }
            for (Map.Entry<String, List<Map<String, Object>>> entry : talesByPackage.entrySet()) {
                if ("core".equals(entry.getKey())) {
                    continue;
                }
                File taleFile = new File(new File(tynanFolder, "tales"), entry.getKey() + ".yml");
                migratedFiles.add(taleFile);
                writeTynanFragment(taleFile,
                        Map.of("tales", entry.getValue()));
            }
            Files.delete(legacyFile.toPath());
            logger.get().info("Split legacy Tynan art-gen.yml into the lang/tynan YAML files.");
        } catch (Exception exception) {
            for (File migratedFile : migratedFiles) {
                try {
                    Files.deleteIfExists(migratedFile.toPath());
                } catch (IOException cleanupException) {
                    logger.get().warning("Could not remove partial Tynan migration file "
                            + migratedFile.getAbsolutePath() + ": " + cleanupException.getMessage());
                }
            }
            logger.get().warning("Could not split legacy Tynan art-gen.yml: " + exception.getMessage());
        }
    }

    private boolean hasSplitTynanData(File tynanFolder) {
        if (hasLanguageTynanData(tynanFolder)) {
            return true;
        }
        for (String fileName : List.of("core.yml", "word-pools.yml")) {
            if (new File(tynanFolder, fileName).exists()) {
                return true;
            }
        }
        File rulepackFolder = new File(tynanFolder, "rulepacks");
        File[] rulepackFiles = rulepackFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        if (rulepackFiles != null && rulepackFiles.length > 0) {
            return true;
        }
        File talesFolder = new File(tynanFolder, "tales");
        File[] taleFiles = talesFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        return taleFiles != null && taleFiles.length > 0;
    }

    private void migrateLegacyTynanRulepacks() {
        File tynanFolder = new File(dataFolder.get(), "lang/tynan");
        File legacyFile = new File(tynanFolder, "rulepacks.yml");
        File rulepackFolder = new File(tynanFolder, "rulepacks");
        if (!legacyFile.exists() || hasSplitTynanRulepacks(rulepackFolder)) {
            return;
        }

        List<File> migratedFiles = new ArrayList<>();
        try {
            FileConfiguration legacy = new YamlConfiguration();
            legacy.load(legacyFile);
            Map<String, Map<String, Object>> grouped =
                    groupTynanRulepacks(legacy.getConfigurationSection("rulepacks"));
            for (Map.Entry<String, Map<String, Object>> entry : grouped.entrySet()) {
                File target = new File(rulepackFolder, entry.getKey() + ".yml");
                migratedFiles.add(target);
                writeTynanFragment(target, Map.of("rulepacks", entry.getValue()));
            }
            Files.delete(legacyFile.toPath());
            logger.get().info("Split legacy Tynan rulepacks.yml into categorized rule files.");
        } catch (Exception exception) {
            for (File migratedFile : migratedFiles) {
                try {
                    Files.deleteIfExists(migratedFile.toPath());
                } catch (IOException cleanupException) {
                    logger.get().warning("Could not remove partial Tynan rule migration file "
                            + migratedFile.getAbsolutePath() + ": " + cleanupException.getMessage());
                }
            }
            logger.get().warning("Could not split legacy Tynan rulepacks.yml: " + exception.getMessage());
        }
    }

    private boolean hasSplitTynanRulepacks(File rulepackFolder) {
        File tynanFolder = rulepackFolder.getParentFile();
        if (tynanFolder != null && hasLanguageTynanData(tynanFolder)) {
            return true;
        }
        File[] files = rulepackFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        return files != null && files.length > 0;
    }

    private Map<String, Map<String, Object>> groupTynanRulepacks(ConfigurationSection section) {
        if (section == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> packs = new LinkedHashMap<>();
        for (String packId : section.getKeys(false)) {
            packs.put(packId, copyYamlValue(section.get(packId)));
        }
        return groupTynanRulepacks(packs);
    }

    private Map<String, Map<String, Object>> groupTynanRulepacks(Map<String, Object> packs) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : packs.entrySet()) {
            String packId = entry.getKey();
            String category = packId.startsWith("Namer")
                    ? "naming" : packId.startsWith("ArtDescription_") ? "carriers" : "base";
            Map<String, Object> categoryPacks = grouped.computeIfAbsent(
                    category, ignored -> new LinkedHashMap<>());
            categoryPacks.put(packId, entry.getValue());
        }
        return grouped;
    }

    private Object copyYamlValue(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                copied.put(key, copyYamlValue(section.get(key)));
            }
            return copied;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, item) -> copied.put(String.valueOf(key), copyYamlValue(item)));
            return copied;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::copyYamlValue).toList();
        }
        return value;
    }

    private void writeTynanFragment(File file, Map<String, Object> values) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        FileConfiguration fragment = new YamlConfiguration();
        values.forEach(fragment::set);
        fragment.save(file);
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
