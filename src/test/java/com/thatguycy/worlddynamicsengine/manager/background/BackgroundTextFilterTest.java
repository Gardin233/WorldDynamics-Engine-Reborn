package com.thatguycy.worlddynamicsengine.manager.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackgroundTextFilterTest {
    @Test
    void catchesCaseWidthWhitespaceAndPunctuationVariations() {
        BackgroundTextFilter filter = new BackgroundTextFilter(List.of("fuck", "纳粹"));

        assertEquals("fuck", filter.findBlockedWord("", "F U C K"));
        assertEquals("fuck", filter.findBlockedWord("", "Ｆ．Ｕ—Ｃ Ｋ"));
        assertEquals("纳粹", filter.findBlockedWord("纳! 粹", ""));
    }

    @Test
    void allowsTextThatDoesNotContainBlockedWords() {
        BackgroundTextFilter filter = new BackgroundTextFilter(List.of("bad word"));

        assertNull(filter.findBlockedWord("A quiet childhood", "A story about learning."));
        assertNull(new BackgroundTextFilter(List.of()).findBlockedWord("title", "story"));
    }
}
