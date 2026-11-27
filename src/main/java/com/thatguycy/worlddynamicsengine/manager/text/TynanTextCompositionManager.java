package com.thatguycy.worlddynamicsengine.manager.text;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Public Java API for Tynan-style text combinations and per-player person presets.
 * Generation data and shared presets are managed under {@code lang/tynan/}.
 */
public final class TynanTextCompositionManager {
    private static final String PRESET_PATH = "lang/tynan/presets.yml";

    private final WorldDynamicsEngine plugin;
    private Map<String, TynanTextComposer> composers;
    private Map<String, RimWorldPersonNameGenerator> personNameGenerators;
    private TynanPresetStore presetStore;

    public TynanTextCompositionManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        FileConfiguration sharedPresets = loadConfiguration(PRESET_PATH);
        FileConfiguration calendar = loadConfiguration(TynanTextDataLoader.calendarPath());
        composers = Map.of(
                "zh", new TynanTextComposer(loadTextData("zh", calendar)),
                "en", new TynanTextComposer(loadTextData("en", calendar)));
        personNameGenerators = Map.of(
                "zh", new RimWorldPersonNameGenerator("zh"),
                "en", new RimWorldPersonNameGenerator("en"));
        presetStore = new TynanPresetStore(plugin.getDataFolder(), plugin.getLogger(), sharedPresets);
    }

    /** Generates between one and twenty descriptions from one event and carrier. */
    public synchronized List<TynanGeneratedText> compose(TynanCompositionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Tynan composition request cannot be null.");
        }
        return composerFor(request.language()).generate(request);
    }

    /** Selects a deterministic localized visitor name using the existing RimWorld name generator. */
    public synchronized String generateRandomPersonName(String language, long seed) {
        return nameGeneratorFor(language).generate(new Random(seed));
    }

    /** Generates a quest-style narrative using the default random text event profile. */
    public synchronized TynanGeneratedText generateRandomEventText(String language, long seed) {
        String lang = "en".equalsIgnoreCase(language) ? "en" : "zh";
        return generateRandomEventText(language, "mislead_enemies", Map.of(
                "PERSON", "en".equals(lang) ? "a visitor" : "一位来访者",
                "TOWN", "en".equals(lang) ? "the town" : "城镇",
                "REWARD", "en".equals(lang) ? "an agreed reward" : "约定的报酬"), seed);
    }

    /** Keeps the event-specific generator easy to call when only the visitor is known. */
    public synchronized TynanGeneratedText generateRandomEventText(
            String language, String personName, long seed) {
        String lang = "en".equalsIgnoreCase(language) ? "en" : "zh";
        return generateRandomEventText(language, "mislead_enemies", Map.of(
                "PERSON", personName == null || personName.isBlank()
                        ? ("en".equals(lang) ? "a visitor" : "一位来访者")
                        : personName,
                "TOWN", "en".equals(lang) ? "the town" : "城镇",
                "REWARD", "en".equals(lang) ? "an agreed reward" : "约定的报酬"), seed);
    }

    /** Generates an event-specific quest narrative with the existing Tynan composer. */
    public synchronized TynanGeneratedText generateRandomEventText(
            String language, String eventId, Map<String, String> slots, long seed) {
        return composerFor(language).generateEventQuest(eventId, language, slots, seed);
    }

    public synchronized List<TaleOption> getTales(String language, String carrierId) {
        return composerFor(language).tales(language, carrierId);
    }

    public synchronized List<CarrierOption> getCarriers(String language) {
        return composerFor(language).carriers(language);
    }

    public synchronized List<StyleClauseOption> getStyleClauses(String language) {
        return composerFor(language).styleClauses(language);
    }

    public synchronized List<StyleOption> getArtStyles(String language) {
        return composerFor(language).artStyles(language);
    }

    public synchronized List<ConceptTone> getConceptTones() {
        return composerFor("zh").conceptTones();
    }

    public synchronized List<TynanPersonPreset> getPersonPresets(UUID playerId) {
        return presetStore.list(playerId);
    }

    public synchronized TynanPersonPreset getDefaultPersonPreset(UUID playerId) {
        return presetStore.defaultPreset(playerId);
    }

    /** Saves or updates a preset owned by one player; marking it default clears their other defaults. */
    public synchronized boolean savePersonPreset(UUID playerId, TynanPersonPreset preset) {
        if (preset == null || preset.name().isBlank() || preset.name().length() > 80
                || composerFor("zh").pronoun(preset.pronounId()) == null) {
            return false;
        }
        return presetStore.save(playerId, preset);
    }

    public synchronized boolean deletePersonPreset(UUID playerId, String name) {
        return presetStore.delete(playerId, name);
    }

    /**
     * Applies a saved person preset to a role prefix such as {@code COLONIST} or {@code PAWN}.
     */
    public synchronized Map<String, String> applyPersonPreset(
            UUID playerId, String presetName, String rolePrefix,
            String language, Map<String, String> existingSlots) {
        if (playerId == null || presetName == null || rolePrefix == null
                || !rolePrefix.matches("[A-Za-z][A-Za-z0-9]*")) {
            return existingSlots == null ? Map.of() : Map.copyOf(existingSlots);
        }
        TynanPersonPreset preset = presetStore.list(playerId).stream()
                .filter(value -> value.name().equals(presetName))
                .findFirst().orElse(null);
        if (preset == null) {
            return existingSlots == null ? Map.of() : Map.copyOf(existingSlots);
        }
        String lang = "en".equalsIgnoreCase(language) ? "en" : "zh";
        Map<String, Object> pronoun = composerFor(lang).pronoun(preset.pronounId());
        Map<String, Object> localizedPronouns = pronoun == null
                ? Map.of() : objectMap(pronoun.get(lang));
        Map<String, String> slots = new LinkedHashMap<>();
        if (existingSlots != null) {
            slots.putAll(existingSlots);
        }
        slots.put(rolePrefix + "_nameFull", preset.name());
        slots.put(rolePrefix + "_nameDef", preset.name());
        if (!preset.title().isBlank()) {
            slots.put(rolePrefix + "_title", preset.title());
        }
        putIfPresent(slots, rolePrefix + "_pronoun", localizedPronouns.get("pronoun"));
        putIfPresent(slots, rolePrefix + "_possessive", localizedPronouns.get("possessive"));
        putIfPresent(slots, rolePrefix + "_objective", localizedPronouns.get("objective"));
        return Map.copyOf(slots);
    }

    private FileConfiguration loadConfiguration(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create Tynan data folder: " + parent.getAbsolutePath());
        }
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            if (file.exists()) {
                configuration.load(file);
                return configuration;
            }
            throw new IOException("Missing data file " + resourcePath);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not load " + resourcePath + "; falling back to the bundled defaults.", exception);
            try (InputStream resource = plugin.getResource(resourcePath)) {
                if (resource == null) {
                    throw new IllegalStateException("Missing bundled resource " + resourcePath, exception);
                }
                configuration.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
                return configuration;
            } catch (IOException | InvalidConfigurationException fallbackException) {
                throw new IllegalStateException("Could not load bundled Tynan data " + resourcePath,
                        fallbackException);
            }
        }
    }

    private FileConfiguration loadTextData(String language, FileConfiguration calendar) {
        FileConfiguration core = loadConfiguration(TynanTextDataLoader.corePath(language));
        List<FileConfiguration> rulepacks = TynanTextDataLoader
                .rulepackPaths(plugin.getDataFolder(), language).stream()
                .map(this::loadConfiguration)
                .toList();
        FileConfiguration wordPools = loadConfiguration(TynanTextDataLoader.wordPoolsPath(language));
        FileConfiguration eventQuests = loadConfiguration(TynanTextDataLoader.eventQuestsPath(language));
        List<FileConfiguration> tales = TynanTextDataLoader.talePaths(plugin.getDataFolder(), language).stream()
                .map(this::loadConfiguration)
                .toList();
        return TynanTextDataLoader.merge(core, rulepacks, wordPools, eventQuests, calendar, tales);
    }

    private TynanTextComposer composerFor(String language) {
        return composers.get("en".equalsIgnoreCase(language) ? "en" : "zh");
    }

    private RimWorldPersonNameGenerator nameGeneratorFor(String language) {
        String normalized = language != null
                && language.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "zh";
        return personNameGenerators.get(normalized);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static void putIfPresent(Map<String, String> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, String.valueOf(value));
        }
    }

    public record TaleOption(String id, String label, String packageId,
                             List<String> carrierIds, int ruleCount, Map<String, Object> slots) {
        public TaleOption {
            carrierIds = carrierIds == null ? List.of() : List.copyOf(carrierIds);
            slots = slots == null ? Map.of() : Map.copyOf(slots);
        }
    }

    public record CarrierOption(String id, String label, boolean available) {
    }

    public record StyleClauseOption(String id, String label, String template, double weight,
                                    boolean usesArtStyle, boolean usesConcept,
                                    boolean usesComposition, boolean usesColor) {
    }

    public record StyleOption(String id, String chinese, String english) {
    }

    public record ConceptTone(String id, String chinese, String english) {
    }
}
