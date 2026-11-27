package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class AnimalEventCompositionTest {
    @Test
    void strengthControlsMaddenedAnimalCountWithinConfiguredBounds() {
        assertEquals(2, AnimalEventComposition.countByStrength(1, 2, 20));
        assertEquals(5, AnimalEventComposition.countByStrength(5, 2, 20));
        assertEquals(20, AnimalEventComposition.countByStrength(25, 2, 20));
    }

    @Test
    void livestockCountNeverFallsBelowAGroup() {
        assertTrue(AnimalEventComposition.randomCount(3, 5, new Random(1L)) >= 3);
        assertEquals(2, AnimalEventComposition.randomCount(0, 1, new Random(1L)));
    }

    @Test
    void animalSelectionRejectsNonLivestockTypes() {
        EntityType selected = AnimalEventComposition.chooseAnimal(
                Map.of(EntityType.COW, 1.0D, EntityType.CREEPER, 100.0D),
                new Random(2L));

        assertEquals(EntityType.COW, selected);
        assertNull(AnimalEventComposition.chooseAnimal(Map.of(), new Random()));
    }
}
