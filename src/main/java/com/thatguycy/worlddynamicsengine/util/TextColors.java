package com.thatguycy.worlddynamicsengine.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Adventure-backed color helpers for existing Bukkit APIs that still consume legacy strings.
 */
public final class TextColors {
    private static final LegacyComponentSerializer SECTION_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    public static final char COLOR_CHAR = '\u00A7';
    public static final String AQUA = color(NamedTextColor.AQUA);
    public static final String BLUE = color(NamedTextColor.BLUE);
    public static final String DARK_BLUE = color(NamedTextColor.DARK_BLUE);
    public static final String DARK_GRAY = color(NamedTextColor.DARK_GRAY);
    public static final String GOLD = color(NamedTextColor.GOLD);
    public static final String GRAY = color(NamedTextColor.GRAY);
    public static final String GREEN = color(NamedTextColor.GREEN);
    public static final String LIGHT_PURPLE = color(NamedTextColor.LIGHT_PURPLE);
    public static final String RED = color(NamedTextColor.RED);
    public static final String RESET = "\u00A7r";
    public static final String WHITE = color(NamedTextColor.WHITE);
    public static final String YELLOW = color(NamedTextColor.YELLOW);

    private TextColors() {}

    /**
     * Translates valid legacy color pairs and serializes them through Adventure.
     */
    public static String translateAlternateColorCodes(char alternateColorChar, String text) {
        if (text == null || text.isEmpty()) return text;
        String ampersandText = alternateColorChar == '&'
                ? text : replaceAlternateCodes(alternateColorChar, text);
        return SECTION_SERIALIZER.serialize(AMPERSAND_SERIALIZER.deserialize(ampersandText));
    }

    /**
     * Returns the last color and formatting codes so wrapped legacy text keeps its style.
     */
    public static String getLastColors(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (int index = text.length() - 1; index > 0; index--) {
            if (text.charAt(index - 1) != COLOR_CHAR) continue;
            char code = Character.toLowerCase(text.charAt(index));
            if (isLegacyColorCode(code) || code == 'r') {
                result.insert(0, COLOR_CHAR).insert(1, code);
                break;
            }
            if (isLegacyFormatCode(code)) {
                result.insert(0, COLOR_CHAR).insert(1, code);
            }
            index--;
        }
        return result.toString();
    }

    private static String color(NamedTextColor color) {
        String rendered = SECTION_SERIALIZER.serialize(Component.text("x", color));
        return rendered.substring(0, rendered.length() - 1);
    }

    private static String replaceAlternateCodes(char alternateColorChar, String text) {
        StringBuilder translated = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == alternateColorChar && i + 1 < text.length()
                    && isLegacyCode(text.charAt(i + 1))) {
                translated.append('&').append(text.charAt(++i));
            } else {
                translated.append(current);
            }
        }
        return translated.toString();
    }

    private static boolean isLegacyFormatCode(char code) {
        return code >= 'k' && code <= 'o';
    }

    private static boolean isLegacyColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean isLegacyCode(char code) {
        char normalized = Character.toLowerCase(code);
        return isLegacyColorCode(normalized) || isLegacyFormatCode(normalized) || normalized == 'r';
    }
}
