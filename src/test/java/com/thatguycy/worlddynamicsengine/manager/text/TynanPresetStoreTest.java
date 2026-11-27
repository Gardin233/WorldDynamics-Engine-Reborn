package com.thatguycy.worlddynamicsengine.manager.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TynanPresetStoreTest {
    @Test
    void storesPlayerPresetsInIsolatedYamlAndKeepsOneDefault(@TempDir Path dataFolder) {
        YamlConfiguration shared = new YamlConfiguration();
        shared.set("presets", List.of(Map.of(
                "name", "Shared name",
                "title", "Artist",
                "pronounId", "they",
                "auto", false)));
        TynanPresetStore store = new TynanPresetStore(
                dataFolder.toFile(), Logger.getLogger("tynan-test"), shared);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(store.save(firstPlayer, new TynanPersonPreset("Rim", "Keeper", "he", true)));
        assertTrue(store.save(firstPlayer, new TynanPersonPreset("Mira", "Builder", "she", true)));

        List<TynanPersonPreset> firstPlayerPresets = store.list(firstPlayer);
        assertEquals(3, firstPlayerPresets.size());
        assertTrue(firstPlayerPresets.stream()
                .filter(TynanPersonPreset::auto)
                .allMatch(preset -> preset.name().equals("Mira")));
        assertEquals(1, firstPlayerPresets.stream().filter(TynanPersonPreset::auto).count());
        assertEquals(1, store.list(secondPlayer).size());

        assertTrue(store.delete(firstPlayer, "Mira"));
        assertFalse(store.list(firstPlayer).stream().anyMatch(preset -> preset.name().equals("Mira")));
        assertFalse(store.delete(firstPlayer, "Mira"));
    }
}
