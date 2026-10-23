package com.thatguycy.worlddynamicsengine.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextColorsTest {
    @Test
    void translatesAmpersandColorsThroughAdventure() {
        assertEquals("\u00A7aGreen \u00A7lBold",
                TextColors.translateAlternateColorCodes('&', "&aGreen &lBold"));
    }

    @Test
    void returnsActiveColorAndDecorationsForWrappedText() {
        assertEquals("\u00A7a\u00A7l", TextColors.getLastColors("\u00A7aFaith \u00A7lkeepsake"));
        assertEquals("\u00A7r", TextColors.getLastColors("\u00A7aFaith\u00A7r"));
    }
}
