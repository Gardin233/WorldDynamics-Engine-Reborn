package com.thatguycy.worlddynamicsengine.manager.background;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Matches blocked words after removing width, case, whitespace, and punctuation variations. */
public final class BackgroundTextFilter {
    private final Map<String, String> blockedWords;

    public BackgroundTextFilter(List<String> blockedWords) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (blockedWords != null) {
            for (String word : blockedWords) {
                if (word == null || word.isBlank()) {
                    continue;
                }
                String key = normalize(word);
                if (!key.isBlank()) {
                    normalized.putIfAbsent(key, word.trim());
                }
            }
        }
        this.blockedWords = Map.copyOf(normalized);
    }

    /** Returns the configured spelling of the first match, or {@code null} when allowed. */
    public String findBlockedWord(String title, String story) {
        String match = findIn(title);
        return match != null ? match : findIn(story);
    }

    private String findIn(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return null;
        }
        for (Map.Entry<String, String> blocked : blockedWords.entrySet()) {
            if (normalized.contains(blocked.getKey())) {
                return blocked.getValue();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compatible = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(compatible.length());
        compatible.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
