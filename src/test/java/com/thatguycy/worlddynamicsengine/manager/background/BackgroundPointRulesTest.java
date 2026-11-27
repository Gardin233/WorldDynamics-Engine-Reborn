package com.thatguycy.worlddynamicsengine.manager.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import java.io.File;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BackgroundPointRulesTest {
    @Test
    void acceptsExactlyFiveNonNegativeSkillPoints() {
        assertTrue(BackgroundPointRules.isValid(Map.of("auraskills/farming", 3, "auraskills/mining", 2)));
        assertTrue(BackgroundPointRules.isValid(Map.of("auraskills/farming", 5)));
        assertTrue(BackgroundPointRules.isValid(Map.of(
                "auraskills/farming", 5, "auraskills/mining", 0)));
    }

    @Test
    void rejectsMissingExcessNegativeAndUnkeyedPoints() {
        assertFalse(BackgroundPointRules.isValid(null));
        assertFalse(BackgroundPointRules.isValid(Map.of()));
        assertFalse(BackgroundPointRules.isValid(Map.of("auraskills/farming", 4)));
        assertFalse(BackgroundPointRules.isValid(Map.of("auraskills/farming", 6)));
        assertFalse(BackgroundPointRules.isValid(Map.of(" ", 5)));
        assertFalse(BackgroundPointRules.isValid(Map.of("auraskills/farming", -1)));
    }

    @Test
    void allBundledPresetsHaveExactlyFiveIntegerPoints() {
        var config = YamlConfiguration.loadConfiguration(new File("src/main/resources/backgrounds.yml"));
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            var presets = config.getConfigurationSection("stages." + stage.getConfigKey() + ".backgrounds");
            assertNotNull(presets);
            assertFalse(presets.getKeys(false).isEmpty());
            for (String preset : presets.getKeys(false)) {
                var points = presets.getConfigurationSection(preset + ".skillPoints");
                assertNotNull(points, preset);
                int total = 0;
                for (String skill : points.getKeys(false)) {
                    assertTrue(points.isInt(skill), preset + ": " + skill);
                    assertTrue(points.getInt(skill) >= 0, preset + ": " + skill);
                    total += points.getInt(skill);
                }
                assertEquals(BackgroundPointRules.REQUIRED_POINTS, total, preset);
            }
        }
    }
}
