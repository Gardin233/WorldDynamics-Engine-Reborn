package com.thatguycy.worlddynamicsengine.manager.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RimWorldPersonNameGeneratorTest {
    @Test
    void generatesDeterministicNamesFromLocalizedRimWorldPools() {
        RimWorldPersonNameGenerator chinese = new RimWorldPersonNameGenerator("zh");
        RimWorldPersonNameGenerator english = new RimWorldPersonNameGenerator("en");
        String chineseName = chinese.generate(new Random(1842L));
        String englishName = english.generate(new Random(1842L));

        assertFalse(chineseName.isBlank());
        assertTrue(chineseName.codePoints().anyMatch(Character::isIdeographic));
        assertFalse(englishName.isBlank());
        assertTrue(englishName.chars().allMatch(character -> character < 128));
        assertEquals(chineseName, chinese.generate(new Random(1842L)));
        assertEquals(englishName, english.generate(new Random(1842L)));
    }
}
