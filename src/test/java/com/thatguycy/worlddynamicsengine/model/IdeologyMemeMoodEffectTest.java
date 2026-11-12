package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IdeologyMemeMoodEffectTest {
    @Test
    void configuredMoodRangeIncludesBothEndpoints() {
        IdeologyMemeMoodEffect effect = new IdeologyMemeMoodEffect(20, 40, List.of("test"));

        assertTrue(effect.matches(20));
        assertTrue(effect.matches(40));
        assertFalse(effect.matches(19));
        assertFalse(effect.matches(41));
    }
}
