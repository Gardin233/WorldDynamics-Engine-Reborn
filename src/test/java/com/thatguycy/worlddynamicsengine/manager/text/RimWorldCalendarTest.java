package com.thatguycy.worlddynamicsengine.manager.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RimWorldCalendarTest {
    @Test
    void usesConfiguredYearBoundsCalendarNamesAndSeasonNames() {
        RimWorldCalendar calendar = new RimWorldCalendar(Map.of(
                "name", Map.of("zh", "帝国纪年", "en", "Imperial Reckoning"),
                "year-range", Map.of("minimum", 5600, "maximum", 5600),
                "seasons", List.of(
                        Map.of("days", 10, "name", Map.of("zh", "融雪季", "en", "Thaw")),
                        Map.of("days", 20, "name", Map.of("zh", "长昼季", "en", "Longlight")))));

        String chineseDate = calendar.date("zh", 281L);
        String englishDate = calendar.date("en", 281L);

        assertEquals(5600, calendar.minimumYear());
        assertEquals(5600, calendar.maximumYear());
        assertTrue(chineseDate.startsWith("帝国纪年·5600年·"));
        assertTrue(chineseDate.contains("融雪季") || chineseDate.contains("长昼季"));
        assertTrue(englishDate.startsWith("Imperial Reckoning — "));
        assertTrue(englishDate.contains("Thaw") || englishDate.contains("Longlight"));
        assertEquals(chineseDate, calendar.date("zh", 281L));
        assertEquals(englishDate, calendar.date("en", 281L));
    }
}
