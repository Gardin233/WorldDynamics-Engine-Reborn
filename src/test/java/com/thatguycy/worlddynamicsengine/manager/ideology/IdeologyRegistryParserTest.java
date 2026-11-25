package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class IdeologyRegistryParserTest {
    @Test
    void parsesBundledDefinitionsAndPreservesBuiltInAttitudeWeights() throws Exception {
        YamlConfiguration config = loadIdeologyConfig();
        Map<String, IdeologyMemeDefinition> memes = new LinkedHashMap<>();
        Map<String, IdeologyDoctrineDefinition> doctrines = new LinkedHashMap<>();
        var warnings = new ArrayList<String>();
        var parser = new IdeologyRegistryParser(warnings::add, value -> normalizeMeme(value, memes));

        parser.loadMemes(config, memes);
        parser.loadDoctrines(config, doctrines);

        assertTrue(memes.size() >= 18);
        assertTrue(doctrines.size() >= 28);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(3, doctrines.get("namespace:wder/slaughter_animals")
                .getAttitude("namespace:wder/slaughter_animals/taboo").getMoodWeight());
        assertEquals(4, doctrines.get("namespace:wder/cannibalism")
                .getAttitude("namespace:wder/cannibalism/greedy").getMoodWeight());
        assertFalse(doctrines.get("namespace:wder/armor_wearing")
                .getMemeConstraint("nudist").blocks("namespace:wder/armor_wearing/naked"));
        assertTrue(doctrines.get("namespace:wder/armor_wearing")
                .getMemeConstraint("nudist").blocks("namespace:wder/armor_wearing/fully_covered"));
        assertTrue(memes.get("nature_primacy").getOutcomeEffects("inspiration").contains("animal_taming"));
        assertTrue(memes.get("ritualist").getOutcomeEffects("collapse").contains("dark_vision"));
    }

    @Test
    void resolvesMemeNamesAsEarlierDefinitionsAreLoaded() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                memes:
                  registry:
                    first:
                      title: First Meme
                    second:
                      title: Second Meme
                      exclusive_with:
                        - First Meme
                """);
        Map<String, IdeologyMemeDefinition> memes = new LinkedHashMap<>();
        var parser = new IdeologyRegistryParser(value -> {}, value -> normalizeMeme(value, memes));

        parser.loadMemes(config, memes);

        assertNotNull(memes.get("first"));
        assertTrue(memes.get("first").getExclusiveWith().isEmpty());
        assertEquals("first", memes.get("second").getExclusiveWith().iterator().next());
    }

    private static YamlConfiguration loadIdeologyConfig() throws Exception {
        try (InputStream input = IdeologyRegistryParserTest.class.getResourceAsStream("/ideology.yml")) {
            assertNotNull(input);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }

    private static String normalizeMeme(String raw, Map<String, IdeologyMemeDefinition> memes) {
        if (raw == null) return null;
        String normalized = IdeologyMemeDefinition.normalizeId(raw);
        if (memes.containsKey(normalized)) return normalized;
        return memes.values().stream()
                .filter(meme -> meme.getTitle().toLowerCase(Locale.ROOT).equals(raw.trim().toLowerCase(Locale.ROOT)))
                .map(IdeologyMemeDefinition::getId)
                .findFirst()
                .orElse(null);
    }
}
