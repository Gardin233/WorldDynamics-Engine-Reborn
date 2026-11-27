package com.thatguycy.worlddynamicsengine.manager.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TynanTextComposerTest {
    @Test
    void fullYamlDataSupportsDeterministicBilingualGeneration() throws Exception {
        TynanTextComposer composer = loadComposer("zh");
        TynanCompositionRequest request = new TynanCompositionRequest(
                "TornadoFromItem",
                "sculpture",
                Map.of(
                        "COLONIST_nameFull", "Ada Vale",
                        "COLONIST_nameDef", "Ada Vale",
                        "PAWN_nameDef", "Ada Vale",
                        "COLONIST_possessive", "她的",
                        "DATE", "5500"),
                "zh", "light", "tale", 1.0D, "auto", "auto",
                List.of(), true, 2, "fixed-seed");

        List<TynanGeneratedText> first = composer.generate(request);
        List<TynanGeneratedText> second = composer.generate(request);

        assertEquals(2, first.size());
        assertTrue(first.stream().allMatch(item -> !item.text().isBlank()));
        assertEquals(first.stream().map(TynanGeneratedText::text).toList(),
                second.stream().map(TynanGeneratedText::text).toList());
        assertTrue(first.stream().allMatch(TynanGeneratedText::usedTale));
    }

    @Test
    void exposesTheImportedTalesCarriersAndStylePools() throws Exception {
        TynanTextComposer chineseComposer = loadComposer("zh");
        TynanTextComposer englishComposer = loadComposer("en");

        assertEquals(92, chineseComposer.tales("zh", null).size());
        assertEquals(8, englishComposer.carriers("en").size());
        assertFalse(chineseComposer.styleClauses("zh").isEmpty());
        assertFalse(englishComposer.artStyles("en").isEmpty());
    }

    @Test
    void languageFoldersContainOnlyTheirOwnLocalizedText() throws Exception {
        FileConfiguration chineseCore = loadYaml(TynanTextDataLoader.corePath("zh"));
        FileConfiguration englishCore = loadYaml(TynanTextDataLoader.corePath("en"));
        Map<?, ?> chineseLabel = (Map<?, ?>) chineseCore.getMapList("carriers").get(0).get("label");
        Map<?, ?> englishLabel = (Map<?, ?>) englishCore.getMapList("carriers").get(0).get("label");

        assertTrue(chineseLabel.containsKey("zh"));
        assertFalse(chineseLabel.containsKey("en"));
        assertTrue(englishLabel.containsKey("en"));
        assertFalse(englishLabel.containsKey("zh"));
    }

    @Test
    void strictModeResolvesTheOfficialRootTemplate() throws Exception {
        TynanTextComposer composer = loadComposer("en");
        TynanCompositionRequest request = new TynanCompositionRequest(
                "TornadoFromItem", "sculpture",
                Map.of("COLONIST_nameDef", "Ada Vale", "PAWN_nameDef", "Ada Vale"),
                "en", "strict", "tale", 1.0D, "auto", "auto",
                List.of(), true, 1, "strict-seed");

        TynanGeneratedText generated = composer.generate(request).get(0);

        assertEquals("strict", generated.mode());
        assertTrue(generated.usedTale());
        assertFalse(generated.text().isBlank());
    }

    @Test
    void randomEventNarrativesUseDeterministicQuestProfilesInsteadOfArtTales() throws Exception {
        Map<String, String> chineseSlots = Map.of(
                "PERSON", "林岚", "TOWN", "石桥镇", "REWARD", "120枚银币与一包药品");
        Map<String, String> englishSlots = Map.of(
                "PERSON", "Ada Vale", "TOWN", "Stonebridge", "REWARD", "120 silver and a medicine pack");

        TynanGeneratedText chinese = loadComposer("zh")
                .generateEventQuest("mislead_enemies", "zh", chineseSlots, 811L);
        TynanGeneratedText chineseRepeat = loadComposer("zh")
                .generateEventQuest("mislead_enemies", "zh", chineseSlots, 811L);
        TynanGeneratedText english = loadComposer("en")
                .generateEventQuest("mislead_enemies", "en", englishSlots, 811L);

        assertFalse(chinese.text().isBlank());
        assertEquals(chinese.text(), chineseRepeat.text());
        assertEquals(5, chinese.text().split("\\R\\R").length);
        assertTrue(chinese.text().contains("林岚"));
        assertTrue(chinese.text().contains("石桥镇"));
        assertTrue(chinese.text().contains("追兵"));
        assertTrue(chinese.text().contains("120枚银币与一包药品"));
        assertTrue(chinese.text().contains("环世界历"));
        assertFalse(chinese.text().contains("雕塑"));
        assertEquals(5, english.text().split("\\R\\R").length);
        assertTrue(english.text().contains("Ada Vale"));
        assertTrue(english.text().contains("Stonebridge"));
        assertTrue(english.text().contains("pursuers"));
        assertTrue(english.text().contains("120 silver and a medicine pack"));
        assertTrue(english.text().contains("RimWorld Calendar"));
        assertFalse(english.text().contains("sculpture"));
        assertEquals("quest", chinese.mode());
        assertFalse(chinese.usedTale());
    }

    private TynanTextComposer loadComposer(String language) throws Exception {
        List<FileConfiguration> rulepacks = new ArrayList<>();
        for (String path : TynanTextDataLoader.rulepackPaths(language)) {
            rulepacks.add(loadYaml(path));
        }
        List<FileConfiguration> taleFiles = new ArrayList<>();
        for (String path : TynanTextDataLoader.talePaths(language)) {
            taleFiles.add(loadYaml(path));
        }
        FileConfiguration combined = TynanTextDataLoader.merge(
                loadYaml(TynanTextDataLoader.corePath(language)),
                rulepacks,
                loadYaml(TynanTextDataLoader.wordPoolsPath(language)),
                loadYaml(TynanTextDataLoader.eventQuestsPath(language)),
                loadYaml(TynanTextDataLoader.calendarPath()),
                taleFiles);
        return new TynanTextComposer(combined);
    }

    private FileConfiguration loadYaml(String path) throws Exception {
        InputStream resource = getClass().getResourceAsStream("/" + path);
        if (resource == null) {
            throw new IllegalStateException("Missing test resource " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
