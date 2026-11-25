package com.thatguycy.worlddynamicsengine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocaleMessagesTest {
    @TempDir Path folder;
    private final ArrayList<String> warnings = new ArrayList<>();
    private final Map<String, String> resources = Map.of(
            "lang/messages_es.yml", "hello: bundled-es\nbase: bundled-base",
            "lang/messages_zh_cn.yml", "fallback: bundled-zh",
            "lang/messages_en.yml", "fallback: bundled-en\nonlyEn: '%s: %.2f'\nbad: '%d'");
    private final LocaleMessages messages = new LocaleMessages(
            path -> new ByteArrayInputStream(resources.get(path).getBytes(StandardCharsets.UTF_8)), warnings::add);

    @Test
    void prefersRegionalThenBaseOverridesAndIgnoresShadowedLegacyFile() throws IOException {
        Files.createDirectory(folder.resolve("lang"));
        Files.writeString(folder.resolve("lang/messages_es_mx.yml"), "hello: regional");
        Files.writeString(folder.resolve("lang/messages_es.yml"), "hello: base\nbase: disk");
        Path legacy = folder.resolve("messages_es.yml");
        Files.writeString(legacy, "hello: legacy\nlegacyOnly: legacy-value");
        messages.reload(folder.toFile());
        assertEquals("regional", messages.get(" ES-MX ", "hello"));
        assertEquals("disk", messages.get("es-MX", "base"));
        assertEquals("legacyOnly", messages.get("es", "legacyOnly"));
        Files.delete(folder.resolve("lang/messages_es.yml"));
        messages.reload(folder.toFile());
        assertEquals("legacy", messages.get("es", "hello"));
        assertEquals("bundled-base", messages.get("es-MX", "base"));
    }

    @Test
    void fallsBackToChineseThenEnglishThenKey() throws IOException {
        Files.writeString(folder.resolve("messages_zh_cn.yml"), "custom: custom-zh");
        messages.reload(folder.toFile());
        assertEquals("custom-zh", messages.get("ZH-Hans", "custom"));
        assertEquals("bundled-zh", messages.get("fr", "fallback"));
        assertEquals("bundled-en", messages.get(null, "fallback"));
        assertEquals("bundled-es", messages.get("es-MX", "hello"));
        assertEquals("missing", messages.get("es", "missing"));
        assertNull(messages.get("en", null));
    }

    @Test
    void preservesFormatFailuresAndLogsTheKey() {
        messages.reload(folder.toFile());
        assertEquals("cost: 1.50", messages.format("es", "onlyEn", "cost", 1.5));
        assertEquals("%d", messages.format("en", "bad"));
        assertTrue(warnings.isEmpty());
        assertEquals("%d", messages.format("en", "bad", "not a number"));
        assertTrue(warnings.getFirst().contains("bad"));
    }
}
