package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FieldBattleDoctrineCalculatorTest {
    @Test
    void rewardsEnjoyAttitudeOnlyInWilderness() {
        assertTrue(FieldBattleDoctrineCalculator.earnsWildernessBonus("enjoy", true));
        assertFalse(FieldBattleDoctrineCalculator.earnsWildernessBonus("enjoy", false));
        assertFalse(FieldBattleDoctrineCalculator.earnsWildernessBonus("neutral", true));
    }

    @Test
    void acceptsNamespacedAttitudeIds() {
        assertTrue(FieldBattleDoctrineCalculator.earnsWildernessBonus(
                "namespace:wder/field_battle/enjoy", true));
    }

    @Test
    void ignoresMissingAttitudes() {
        assertFalse(FieldBattleDoctrineCalculator.earnsWildernessBonus(null, true));
        assertFalse(FieldBattleDoctrineCalculator.earnsWildernessBonus("", true));
    }
}
