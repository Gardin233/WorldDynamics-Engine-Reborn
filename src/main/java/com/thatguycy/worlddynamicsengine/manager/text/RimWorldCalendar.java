package com.thatguycy.worlddynamicsengine.manager.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Configurable RimWorld-style year, quadrums, and localized calendar labels. */
final class RimWorldCalendar {
    private final String chineseName;
    private final String englishName;
    private final int minimumYear;
    private final int maximumYear;
    private final List<Season> seasons;
    private final int daysPerYear;

    RimWorldCalendar(Map<String, Object> config) {
        Map<String, Object> calendarNames = map(config.get("name"));
        chineseName = localized(calendarNames, "zh", "环世界历");
        englishName = localized(calendarNames, "en", "RimWorld Calendar");

        Map<String, Object> yearRange = map(config.get("year-range"));
        int firstYear = integer(yearRange.get("minimum"), 5500);
        int lastYear = integer(yearRange.get("maximum"), 5505);
        minimumYear = Math.min(firstYear, lastYear);
        maximumYear = Math.max(firstYear, lastYear);

        List<Season> configuredSeasons = new ArrayList<>();
        for (Object entry : list(config.get("seasons"))) {
            Map<String, Object> season = map(entry);
            Map<String, Object> names = map(season.get("name"));
            String id = string(season.get("id"));
            String chineseFallback = localized(names, "en", id.isBlank() ? "季节" : id);
            String englishFallback = localized(names, "zh", id.isBlank() ? "Season" : id);
            String chineseLabel = localized(names, "zh", chineseFallback);
            String englishLabel = localized(names, "en", englishFallback);
            int days = Math.max(1, Math.min(1000, integer(season.get("days"), 15)));
            if (!chineseLabel.isBlank() && !englishLabel.isBlank()) {
                configuredSeasons.add(new Season(chineseLabel, englishLabel, days));
            }
        }
        seasons = configuredSeasons.isEmpty() ? defaultSeasons() : List.copyOf(configuredSeasons);
        daysPerYear = seasons.stream().mapToInt(Season::days).sum();
    }

    String date(String language, long seed) {
        boolean english = language != null
                && language.toLowerCase(Locale.ROOT).startsWith("en");
        Random random = new Random(seed);
        long yearRange = (long) maximumYear - minimumYear + 1L;
        int year = (int) (minimumYear + random.nextLong(yearRange));
        int dayOfYear = random.nextInt(daysPerYear);

        for (Season season : seasons) {
            if (dayOfYear < season.days()) {
                int dayOfSeason = dayOfYear + 1;
                if (english) {
                    return "%s — %s %d, %d".formatted(
                            englishName, season.englishName(), dayOfSeason, year);
                }
                return "%s·%d年·%s第%d天".formatted(
                        chineseName, year, season.chineseName(), dayOfSeason);
            }
            dayOfYear -= season.days();
        }
        throw new IllegalStateException("RimWorld calendar season lengths do not cover the year.");
    }

    int minimumYear() {
        return minimumYear;
    }

    int maximumYear() {
        return maximumYear;
    }

    private static List<Season> defaultSeasons() {
        return List.of(
                new Season("春季（Aprimay）", "Aprimay", 15),
                new Season("夏季（Jugust）", "Jugust", 15),
                new Season("秋季（Septober）", "Septober", 15),
                new Season("冬季（Decembary）", "Decembary", 15));
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        return source.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        Map.Entry::getValue));
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        if (!(value instanceof Number number)) {
            return fallback;
        }
        long result = number.longValue();
        return result < Integer.MIN_VALUE || result > Integer.MAX_VALUE
                ? fallback : (int) result;
    }

    private static String localized(Map<String, Object> values, String language, String fallback) {
        Object value = values.get(language);
        return value == null || String.valueOf(value).isBlank()
                ? fallback : String.valueOf(value);
    }

    private record Season(String chineseName, String englishName, int days) {
    }
}
