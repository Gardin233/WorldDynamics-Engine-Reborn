package com.thatguycy.worlddynamicsengine.manager.text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Combines the smaller Tynan YAML files into the in-memory data tree expected by the composer. */
final class TynanTextDataLoader {
    private static final List<String> RULEPACK_FILES = List.of(
            "rulepacks/base.yml", "rulepacks/carriers.yml", "rulepacks/naming.yml");
    private static final List<String> TALE_FILES = createTalePaths();

    private TynanTextDataLoader() {
    }

    static String corePath(String language) {
        return languageRoot(language) + "core.yml";
    }

    static String wordPoolsPath(String language) {
        return languageRoot(language) + "word-pools.yml";
    }

    static String eventQuestsPath(String language) {
        return languageRoot(language) + "event-quests.yml";
    }

    static String calendarPath() {
        return "lang/tynan/calendar.yml";
    }

    static List<String> rulepackPaths(String language) {
        return withLanguageRoot(language, RULEPACK_FILES);
    }

    static List<String> rulepackPaths(File dataFolder, String language) {
        return withExtraFiles(dataFolder, language, "rulepacks", RULEPACK_FILES);
    }

    static List<String> talePaths(String language) {
        return withLanguageRoot(language, TALE_FILES);
    }

    static List<String> talePaths(File dataFolder, String language) {
        return withExtraFiles(dataFolder, language, "tales", TALE_FILES);
    }

    static FileConfiguration merge(FileConfiguration core, List<FileConfiguration> rulepacks,
                                   FileConfiguration wordPools, List<FileConfiguration> taleFiles) {
        return merge(core, rulepacks, wordPools,
                new YamlConfiguration(), new YamlConfiguration(), taleFiles);
    }

    static FileConfiguration merge(FileConfiguration core, List<FileConfiguration> rulepacks,
                                   FileConfiguration wordPools, FileConfiguration eventQuests,
                                   List<FileConfiguration> taleFiles) {
        return merge(core, rulepacks, wordPools,
                eventQuests, new YamlConfiguration(), taleFiles);
    }

    static FileConfiguration merge(FileConfiguration core, List<FileConfiguration> rulepacks,
                                   FileConfiguration wordPools, FileConfiguration eventQuests,
                                   FileConfiguration calendar, List<FileConfiguration> taleFiles) {
        YamlConfiguration combined = new YamlConfiguration();
        copySection(core, combined, "");
        for (FileConfiguration rulepack : rulepacks) {
            copySection(rulepack, combined, "");
        }
        copySection(wordPools, combined, "");
        copySection(eventQuests, combined, "");
        copySection(calendar, combined, "");
        List<Map<?, ?>> tales = new ArrayList<>();
        for (FileConfiguration taleFile : taleFiles) {
            tales.addAll(taleFile.getMapList("tales"));
        }
        combined.set("tales", tales);
        return combined;
    }

    private static List<String> withLanguageRoot(String language, List<String> relativePaths) {
        String root = languageRoot(language);
        return relativePaths.stream().map(path -> root + path).toList();
    }

    private static List<String> withExtraFiles(File dataFolder, String language,
                                               String subfolder, List<String> defaults) {
        List<String> paths = new ArrayList<>(defaults);
        Set<String> known = new HashSet<>(defaults);
        File directory = new File(dataFolder, languageRoot(language) + subfolder);
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, (first, second) -> first.getName().compareTo(second.getName()));
            for (File file : files) {
                String path = subfolder + "/" + file.getName();
                if (known.add(path)) {
                    paths.add(path);
                }
            }
        }
        return withLanguageRoot(language, paths);
    }

    private static String languageRoot(String language) {
        return "lang/tynan/" + ("en".equalsIgnoreCase(language) ? "en" : "zh") + "/";
    }

    private static List<String> createTalePaths() {
        List<String> paths = new ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            paths.add("tales/core-%02d.yml".formatted(index));
        }
        paths.addAll(List.of(
                "tales/royalty.yml",
                "tales/biotech.yml",
                "tales/anomaly.yml",
                "tales/odyssey.yml"));
        return List.copyOf(paths);
    }

    private static void copySection(ConfigurationSection source, YamlConfiguration target, String prefix) {
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection child = source.getConfigurationSection(key);
            if (child == null) {
                target.set(path, source.get(key));
            } else {
                copySection(child, target, path);
            }
        }
    }
}
