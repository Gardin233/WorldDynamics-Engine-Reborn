package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineMemeConstraint;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeMoodEffect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Parses configured meme and doctrine definitions without owning their registries or Bukkit events. */
final class IdeologyRegistryParser {
    private static final String DOCTRINE_ID_PATTERN = "^[a-z0-9_.-]+:(?:wde|wder)/[a-z0-9_.-]+$";

    private final Consumer<String> warning;
    private final Function<String, String> normalizeMeme;

    IdeologyRegistryParser(Consumer<String> warning, Function<String, String> normalizeMeme) {
        this.warning = warning;
        this.normalizeMeme = normalizeMeme;
    }

    void loadMemes(FileConfiguration config, Map<String, IdeologyMemeDefinition> registry) {
        if (config == null) return;
        ConfigurationSection section = config.getConfigurationSection("memes.registry");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String id = IdeologyMemeDefinition.normalizeId(key);
            if (!isValidMemeId(id)) {
                warning.accept("Skipped ideology meme with invalid id: " + key);
                continue;
            }
            String path = "memes.registry." + key;
            IdeologyMemeDefinition meme = new IdeologyMemeDefinition(
                    id,
                    config.getString(path + ".title", id),
                    config.getString(path + ".description", ""),
                    config.getString(path + ".color", ""),
                    config.getString(path + ".icon", ""),
                    "config",
                    parseMemeExclusiveWith(config.get(path + ".exclusive_with")),
                    parseMemeMoodEffects(config.get(path + ".moodEffects.ranges")),
                    Map.of(
                            "collapse", parseStringList(config.get(path + ".moodEffects.collapse")),
                            "inspiration", parseStringList(config.get(path + ".moodEffects.inspiration"))));
            if (!meme.isValid()) {
                warning.accept("Skipped ideology meme with missing title: " + key);
                continue;
            }
            if (registry.containsKey(id)) {
                warning.accept("Skipped duplicate ideology meme id: " + id);
                continue;
            }
            registry.put(id, meme);
        }
    }

    void loadDoctrines(FileConfiguration config, Map<String, IdeologyDoctrineDefinition> registry) {
        if (config == null) return;
        ConfigurationSection section = config.getConfigurationSection("doctrines.registry");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String id = IdeologyDoctrineDefinition.normalizeId(key);
            if (!isValidDoctrineId(id)) {
                warning.accept("Skipped ideology doctrine with invalid id: " + key);
                continue;
            }
            Map<?, ?> values = asMap(section.get(key));
            if (values.isEmpty()) {
                warning.accept("Skipped ideology doctrine without section body: " + key);
                continue;
            }
            List<IdeologyDoctrineAttitudeDefinition> attitudes =
                    parseDoctrineAttitudes(id, values.get("attitudes"));
            if (attitudes.isEmpty()) {
                warning.accept("Skipped ideology doctrine without attitudes: " + key);
                continue;
            }
            IdeologyDoctrineDefinition doctrine = new IdeologyDoctrineDefinition(
                    id,
                    getStringValue(values, "title", id),
                    getStringValue(values, "lore", ""),
                    getStringValue(values, "color", ""),
                    getStringValue(values, "icon", ""),
                    attitudes,
                    parseDoctrineMemeConstraints(id, values.get("meme")),
                    "config");
            if (!doctrine.isValid()) {
                warning.accept("Skipped ideology doctrine with invalid payload: " + key);
                continue;
            }
            if (registry.containsKey(id)) {
                warning.accept("Skipped duplicate ideology doctrine id: " + id);
                continue;
            }
            registry.put(id, doctrine);
        }
    }

    private List<IdeologyDoctrineAttitudeDefinition> parseDoctrineAttitudes(String doctrineId, Object rawAttitudes) {
        List<IdeologyDoctrineAttitudeDefinition> attitudes = new ArrayList<>();
        if (rawAttitudes instanceof Collection<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> entryMap) {
                    for (Map.Entry<?, ?> mapEntry : entryMap.entrySet()) {
                        addDoctrineAttitude(attitudes, doctrineId, String.valueOf(mapEntry.getKey()), mapEntry.getValue());
                    }
                }
            }
        } else if (rawAttitudes instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                addDoctrineAttitude(attitudes, doctrineId, key, section.get(key));
            }
        }
        return attitudes;
    }

    private void addDoctrineAttitude(List<IdeologyDoctrineAttitudeDefinition> attitudes, String doctrineId,
                                     String rawAttitudeId, Object rawValue) {
        String attitudeId = DoctrineAttitudePolicy.normalizeAttitudeId(doctrineId, rawAttitudeId);
        if (attitudeId == null) {
            warning.accept("Skipped doctrine attitude with invalid id: " + rawAttitudeId);
            return;
        }
        Map<?, ?> values = asMap(rawValue);
        String title = getStringValue(values, "title", rawAttitudeId);
        String lore = getStringValue(values, "lore", "");
        String color = getStringValue(values, "color", "");
        int moodWeight = Math.max(0, Math.min(10,
                integerValue(values.get("moodWeight"), defaultMoodWeight(doctrineId, attitudeId))));
        Map<?, ?> moodChange = asMap(values.get("moodChange"));
        int compliant = moodChange.containsKey("compliant") ? integerValue(moodChange.get("compliant"), -1) : -1;
        int violation = moodChange.containsKey("violation") ? integerValue(moodChange.get("violation"), -1) : -1;
        var attitude = new IdeologyDoctrineAttitudeDefinition(
                attitudeId, title, lore, color, moodWeight, compliant, violation);
        if (!attitude.isValid()) {
            warning.accept("Skipped doctrine attitude with missing title: " + rawAttitudeId);
            return;
        }
        attitudes.add(attitude);
    }

    private int defaultMoodWeight(String doctrineId, String attitudeId) {
        String doctrine = doctrineId.substring(doctrineId.lastIndexOf('/') + 1);
        String attitude = attitudeId.substring(attitudeId.lastIndexOf('/') + 1);
        return switch (doctrine) {
            case "armor_wearing" -> Set.of("naked", "fully_covered").contains(attitude) ? 3 : 1;
            case "cannibalism", "blood_feeding" -> switch (attitude) {
                case "abhor" -> 3;
                case "fear" -> 2;
                case "disgust", "accept" -> 1;
                case "enjoy" -> 2;
                case "crave" -> 3;
                case "greedy" -> 4;
                default -> 1;
            };
            case "meat_eating" -> switch (attitude) {
                case "abhor_meat", "anti_vegetarian_abhor" -> 3;
                case "fear_meat", "anti_vegetarian_hate" -> 2;
                default -> 1;
            };
            case "slaughter_animals" -> switch (attitude) {
                case "taboo" -> 3;
                case "love" -> 2;
                case "fear" -> 2;
                default -> 1;
            };
            case "killing_innocent_animals" -> switch (attitude) {
                case "abhor" -> 3;
                case "fear" -> 2;
                default -> 1;
            };
            default -> 1;
        };
    }

    private List<IdeologyDoctrineMemeConstraint> parseDoctrineMemeConstraints(String doctrineId, Object raw) {
        List<IdeologyDoctrineMemeConstraint> constraints = new ArrayList<>();
        if (!(raw instanceof Collection<?> entries)) return constraints;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            for (Map.Entry<?, ?> item : map.entrySet()) {
                String sourceMeme = String.valueOf(item.getKey());
                String memeId = normalizeMeme.apply(sourceMeme);
                if (memeId == null) memeId = sourceMeme.trim().toLowerCase();
                Map<?, ?> values = asMap(item.getValue());
                constraints.add(new IdeologyDoctrineMemeConstraint(memeId,
                        normalizeAttitudeIds(doctrineId, values.get("default")),
                        normalizeAttitudeIds(doctrineId, values.get("cant_use"))));
            }
        }
        return constraints;
    }

    private List<String> parseMemeExclusiveWith(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) addMemeReference(values, value);
        } else if (raw != null) {
            addMemeReference(values, raw);
        }
        return values;
    }

    private void addMemeReference(List<String> values, Object raw) {
        String value = raw == null ? null : String.valueOf(raw);
        String normalized = normalizeMeme.apply(value);
        if (normalized == null) normalized = IdeologyMemeDefinition.normalizeId(value);
        if (normalized != null && !normalized.isBlank()) values.add(normalized);
    }

    private List<IdeologyMemeMoodEffect> parseMemeMoodEffects(Object raw) {
        List<IdeologyMemeMoodEffect> effects = new ArrayList<>();
        if (!(raw instanceof Collection<?> ranges)) return effects;
        for (Object rawRange : ranges) {
            Map<?, ?> range = asMap(rawRange);
            if (range.isEmpty()) continue;
            int minimum = integerValue(range.get("min"), -1);
            int maximum = integerValue(range.get("max"), -1);
            List<String> ids = parseStringList(range.get("effects"));
            if (minimum < 0 || maximum < minimum || maximum > 100) {
                warning.accept("Skipped invalid mood effect range for meme: " + range);
                continue;
            }
            effects.add(new IdeologyMemeMoodEffect(minimum, maximum, ids));
        }
        return effects;
    }

    private List<String> parseStringList(Object raw) {
        if (!(raw instanceof Collection<?> values)) return List.of();
        List<String> strings = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) strings.add(String.valueOf(value).trim());
        }
        return List.copyOf(strings);
    }

    private List<String> normalizeAttitudeIds(String doctrineId, Object raw) {
        List<String> ids = new ArrayList<>();
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                String normalized = DoctrineAttitudePolicy.normalizeAttitudeId(
                        doctrineId, value == null ? null : String.valueOf(value));
                if (normalized != null) ids.add(normalized);
            }
        }
        return ids;
    }

    private static int integerValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Map<?, ?> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) return map;
        if (raw instanceof ConfigurationSection section) return section.getValues(false);
        return Collections.emptyMap();
    }

    private static String getStringValue(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean isValidMemeId(String id) {
        return id != null && id.matches("[a-z0-9._-]{2,64}");
    }

    private static boolean isValidDoctrineId(String id) {
        return id != null && id.matches(DOCTRINE_ID_PATTERN);
    }
}
