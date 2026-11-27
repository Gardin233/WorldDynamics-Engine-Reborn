package com.thatguycy.worlddynamicsengine.manager.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Uses the bundled RimWorld Sophian name rules and localized name pools. */
final class RimWorldPersonNameGenerator {
    private static final String ROOT = "/lang/tynan/rimworld/";

    private final List<String> maleFirstNames;
    private final List<String> femaleFirstNames;
    private final List<String> lastNames;
    private final List<String> syllables;
    private final List<Weighted<Pattern>> malePatterns;
    private final List<Weighted<Pattern>> femalePatterns;
    private final List<Weighted<Integer>> imperialWordLengths;

    RimWorldPersonNameGenerator(String language) {
        FileConfiguration config = loadYaml(ROOT + "namer-person-sophian.yml");
        String lang = language != null
                && language.toLowerCase(Locale.ROOT).startsWith("zh") ? "zh" : "en";
        String files = "files." + lang + ".";
        maleFirstNames = loadLines(string(config.getString(files + "male-first")));
        femaleFirstNames = loadLines(string(config.getString(files + "female-first")));
        lastNames = loadLines(string(config.getString(files + "last")));
        syllables = loadLines(string(config.getString(files + "syllables")));
        malePatterns = loadPatterns(config.getMapList("name-rules.male"));
        femalePatterns = loadPatterns(config.getMapList("name-rules.female"));
        imperialWordLengths = loadWordLengths(config.getMapList("word-imperial"));
        if (maleFirstNames.isEmpty() || femaleFirstNames.isEmpty() || lastNames.isEmpty()
                || syllables.isEmpty() || malePatterns.isEmpty() || femalePatterns.isEmpty()
                || imperialWordLengths.isEmpty()) {
            throw new IllegalStateException("RimWorld Sophian name data is incomplete.");
        }
    }

    String generate(Random random) {
        boolean female = random.nextBoolean();
        Pattern pattern = chooseWeighted(female ? femalePatterns : malePatterns, random).value();
        List<String> firstNames = female ? femaleFirstNames : maleFirstNames;
        return switch (pattern) {
            case FIRST_LAST -> choose(firstNames, random) + " " + choose(lastNames, random);
            case FIRST_IMPERIAL -> choose(firstNames, random) + " " + imperialWord(random);
            case IMPERIAL_IMPERIAL -> imperialWord(random) + " " + imperialWord(random);
        };
    }

    private String imperialWord(Random random) {
        int syllableCount = chooseWeighted(imperialWordLengths, random).value();
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < syllableCount; index++) {
            result.append(choose(syllables, random));
        }
        return result.toString();
    }

    private static List<Weighted<Pattern>> loadPatterns(List<Map<?, ?>> rawPatterns) {
        List<Weighted<Pattern>> result = new ArrayList<>();
        for (Map<?, ?> raw : rawPatterns) {
            try {
                Pattern pattern = Pattern.valueOf(string(raw.get("pattern")));
                double weight = number(raw.get("weight"), 0.0D);
                if (Double.isFinite(weight) && weight > 0.0D) {
                    result.add(new Weighted<>(pattern, weight));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore unsupported name rules while preserving valid entries.
            }
        }
        return List.copyOf(result);
    }

    private static List<Weighted<Integer>> loadWordLengths(List<Map<?, ?>> rawLengths) {
        List<Weighted<Integer>> result = new ArrayList<>();
        for (Map<?, ?> raw : rawLengths) {
            int syllableCount = (int) number(raw.get("syllables"), 0.0D);
            double weight = number(raw.get("weight"), 0.0D);
            if (syllableCount > 0 && Double.isFinite(weight) && weight > 0.0D) {
                result.add(new Weighted<>(syllableCount, weight));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> loadLines(String relativePath) {
        if (relativePath.isBlank() || relativePath.contains("..")) {
            throw new IllegalStateException("Invalid RimWorld name resource path.");
        }
        String resourcePath = ROOT + relativePath;
        try (InputStream resource = RimWorldPersonNameGenerator.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                throw new IllegalStateException("Missing bundled RimWorld name resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read RimWorld name resource: " + resourcePath, exception);
        }
    }

    private static FileConfiguration loadYaml(String resourcePath) {
        try (InputStream resource = RimWorldPersonNameGenerator.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                throw new IllegalStateException("Missing bundled RimWorld name rules: " + resourcePath);
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read RimWorld name rules: " + resourcePath, exception);
        }
    }

    private static <T> Weighted<T> chooseWeighted(List<Weighted<T>> choices, Random random) {
        double totalWeight = choices.stream().mapToDouble(Weighted::weight).sum();
        double selection = random.nextDouble() * totalWeight;
        for (Weighted<T> choice : choices) {
            selection -= choice.weight();
            if (selection < 0.0D) {
                return choice;
            }
        }
        return choices.getLast();
    }

    private static <T> T choose(List<T> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private enum Pattern {
        FIRST_LAST,
        FIRST_IMPERIAL,
        IMPERIAL_IMPERIAL
    }

    private record Weighted<T>(T value, double weight) {
    }
}
