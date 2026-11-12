package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class IdeologyCollapseConfigTest {
    @Test
    void defaultConfigDefinesSeverityAndAllRequestedEffects() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/ideology.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));

            assertEquals(34, config.getInt("mood.collapse.severity.mildMinimumMood"));
            assertEquals(17, config.getInt("mood.collapse.severity.moderateMinimumMood"));
            assertEquals(300, config.getInt("mood.collapse.effects.sadWander.durationSeconds"));
            assertEquals(180, config.getInt("mood.collapse.effects.darkVision.durationSeconds"));
            assertEquals(180, config.getInt("mood.collapse.effects.crazyTalk.durationSeconds"));
            assertEquals(120, config.getInt("mood.collapse.effects.catatonicStupor.durationSeconds"));
            assertEquals(120, config.getInt("mood.collapse.effects.faithCrisis.durationSeconds"));
            assertTrue(config.getBoolean("mood.collapse.effects.humanityCollapse.enabled"));
            assertEquals(180, config.getInt("mood.collapse.effects.humanityCollapse.durationSeconds"));
            assertEquals(3, config.getMapList("mood.collapse.effects.humanityCollapse.sequence").size());
            assertTrue(config.getStringList("mood.collapse.effects.crazyTalk.messages").size() >= 2);
        }
    }
}
