package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class IdeologyDoctrineDefaultsTest {
    @Test
    void bundledDoctrinesStartWithAnExistingNeutralOrOpposingAttitude() throws Exception {
        Map<String, String> opposition = Map.of(
                "cannibalism", "disgust",
                "blood_feeding", "disgust",
                "display_of_heads", "disgust",
                "potions", "forbidden",
                "meat_eating", "disgust_meat",
                "mining", "disgust",
                "rituals", "disgust",
                "indoors", "love");
        try (InputStream input = getClass().getResourceAsStream("/ideology.yml")) {
            assertNotNull(input);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection registry = config.getConfigurationSection("doctrines.registry");
            assertNotNull(registry);

            for (String doctrineId : registry.getKeys(false)) {
                List<Map<?, ?>> entries = config.getMapList("doctrines.registry." + doctrineId + ".attitudes");
                List<String> ids = new ArrayList<>();
                for (Map<?, ?> entry : entries) {
                    assertEquals(1, entry.size(), doctrineId);
                    ids.add(doctrineId + "/" + entry.keySet().iterator().next());
                }
                assertEquals(ids.size(), new HashSet<>(ids).size(), doctrineId);
                String defaultId = IdeologyDoctrineDefinition.preferredDefaultAttitudeId(doctrineId, ids);
                assertEquals(ids.getFirst(), defaultId, doctrineId);
                String localName = doctrineId.substring(doctrineId.lastIndexOf('/') + 1);
                if (opposition.containsKey(localName)) {
                    assertEquals(doctrineId + "/" + opposition.get(localName), defaultId);
                } else {
                    assertTrue(defaultId.endsWith("/neutral") || defaultId.endsWith("/none")
                            || defaultId.endsWith("/unrestricted"), doctrineId);
                }
                // Older server configs retain their original list order.
                List<String> olderOrder = new ArrayList<>(ids);
                java.util.Collections.reverse(olderOrder);
                assertEquals(defaultId,
                        IdeologyDoctrineDefinition.preferredDefaultAttitudeId(doctrineId, olderOrder), doctrineId);
            }
        }
    }

    @Test
    void everyBundledAttitudeHasExplicitMoodChanges() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/ideology.yml")) {
            assertNotNull(input);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection registry = config.getConfigurationSection("doctrines.registry");
            assertNotNull(registry);
            int checked = 0;
            for (String doctrineId : registry.getKeys(false)) {
                List<Map<?, ?>> entries = config.getMapList("doctrines.registry." + doctrineId + ".attitudes");
                for (Map<?, ?> entry : entries) {
                    assertEquals(1, entry.size(), doctrineId);
                    Map.Entry<?, ?> attitude = entry.entrySet().iterator().next();
                    String attitudeId = String.valueOf(attitude.getKey());
                    assertTrue(attitude.getValue() instanceof Map, doctrineId + "/" + attitudeId);
                    Map<?, ?> fields = (Map<?, ?>) attitude.getValue();
                    assertTrue(fields.get("moodChange") instanceof Map, doctrineId + "/" + attitudeId);
                    Map<?, ?> moodChange = (Map<?, ?>) fields.get("moodChange");
                    int expected = List.of("neutral", "none", "unrestricted").contains(attitudeId) ? 0 : 1;
                    assertEquals(expected, moodChange.get("compliant"), doctrineId + "/" + attitudeId);
                    assertEquals(expected, moodChange.get("violation"), doctrineId + "/" + attitudeId);
                    checked++;
                }
            }
            assertTrue(checked > 60, "Expected to validate the bundled doctrine attitudes");
        }
    }
}
