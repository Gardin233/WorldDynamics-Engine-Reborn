package com.thatguycy.worlddynamicsengine;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads and resolves plugin messages without depending on the plugin lifecycle. */
final class LocaleMessages {
    private record Catalog(Map<String, YamlConfiguration> custom, Map<String, YamlConfiguration> bundled) {}

    private final Function<String, InputStream> resources;
    private final Consumer<String> warn;
    private volatile Catalog catalog = new Catalog(Map.of(), Map.of());

    LocaleMessages(Function<String, InputStream> resources, Consumer<String> warn) {
        this.resources = resources;
        this.warn = warn;
    }

    void reload(File folder) {
        var bundled = new HashMap<String, YamlConfiguration>();
        for (String locale : List.of("en", "es", "zh_cn")) {
            String path = "lang/messages_" + locale + ".yml";
            try (var reader = new InputStreamReader(resources.apply(path), StandardCharsets.UTF_8)) {
                bundled.put(locale, YamlConfiguration.loadConfiguration(reader));
            } catch (Exception e) {
                warn.accept("Failed to load bundled locale " + path + ": " + e.getMessage());
            }
        }
        var custom = new HashMap<String, YamlConfiguration>();
        loadDirectory(new File(folder, "lang"), custom, true);
        loadDirectory(folder, custom, false);
        catalog = new Catalog(custom, bundled);
    }

    private static void loadDirectory(File folder, Map<String, YamlConfiguration> custom, boolean override) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith("messages_") || !name.endsWith(".yml")) continue;
            String locale = name.substring("messages_".length(), name.length() - ".yml".length()).toLowerCase();
            if (override || !custom.containsKey(locale)) custom.put(locale, YamlConfiguration.loadConfiguration(file));
        }
    }

    String get(String locale, String key) {
        if (key == null) return null;
        Catalog snapshot = catalog;
        for (String candidate : List.of(normalize(locale), "zh_cn", "en")) {
            String value = resolve(snapshot.custom(), candidate, key);
            if (value == null) value = resolve(snapshot.bundled(), candidate, key);
            if (value != null) return value;
        }
        return key;
    }

    private static String resolve(Map<String, YamlConfiguration> source, String locale, String key) {
        var config = source.get(locale);
        if (config != null && config.contains(key)) return config.getString(key, key);
        int separator = locale.indexOf('_');
        if (separator <= 0) return null;
        var base = source.get(locale.substring(0, separator));
        return base != null && base.contains(key) ? base.getString(key, key) : null;
    }

    String format(String locale, String key, Object... args) {
        String template = get(locale, key);
        if (args == null || args.length == 0) return template;
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException e) {
            warn.accept("Invalid locale format for key " + key + ": " + e.getMessage());
            return template;
        }
    }

    private static String normalize(String locale) {
        if (locale == null || locale.isBlank()) return "en";
        String normalized = locale.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case "zh", "zh_hans" -> "zh_cn";
            default -> normalized;
        };
    }
}
