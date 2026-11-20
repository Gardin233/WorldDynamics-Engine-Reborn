package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class AnimalResonanceDoctrineCalculatorTest {
    @Test
    void powerfulAttitudeRewardsFeedingAndPenalizesKilling() {
        assertTrue(AnimalResonanceDoctrineCalculator.assessFeeding(
                "namespace:wder/animal_resonance/powerful"));
        assertFalse(AnimalResonanceDoctrineCalculator.assessKilling(
                "namespace:wder/animal_resonance/powerful"));
    }

    @Test
    void noneAndUnknownAttitudesDoNotApply() {
        assertNull(AnimalResonanceDoctrineCalculator.assessFeeding("none"));
        assertNull(AnimalResonanceDoctrineCalculator.assessKilling("neutral"));
        assertNull(AnimalResonanceDoctrineCalculator.assessFeeding("custom"));
    }

    @Test
    void feedingRequiresAnItemCountOrTypeChange() {
        assertTrue(AnimalResonanceDoctrineCalculator.itemWasConsumed(
                Material.WHEAT, 2, Material.WHEAT, 1));
        assertTrue(AnimalResonanceDoctrineCalculator.itemWasConsumed(
                Material.WHEAT, 1, Material.AIR, 0));
        assertFalse(AnimalResonanceDoctrineCalculator.itemWasConsumed(
                Material.WHEAT, 2, Material.WHEAT, 2));
        assertFalse(AnimalResonanceDoctrineCalculator.itemWasConsumed(
                Material.AIR, 0, Material.WHEAT, 1));
    }
}
