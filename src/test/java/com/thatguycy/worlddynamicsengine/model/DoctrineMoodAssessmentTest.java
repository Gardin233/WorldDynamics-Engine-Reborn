package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.manager.ideology.AnimalKillingDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineMoodEntry;
import com.thatguycy.worlddynamicsengine.manager.ideology.FoodDoctrineManager;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DoctrineMoodAssessmentTest {
    @Test
    void animalActionCountsEachApplicableCompliantAndViolatedDoctrine() {
        AnimalKillingDoctrineManager.Assessment assessment = AnimalKillingDoctrineManager.evaluate(true, Map.of(
                "namespace:wder/slaughter_animals", "namespace:wder/slaughter_animals/love",
                "namespace:wder/killing_innocent_animals", "namespace:wder/killing_innocent_animals/abhor"
        ));

        assertEquals(1, assessment.compliantCount());
        assertEquals(1, assessment.violationCount());
        assertEquals(1, assessment.compliantMoodWeight());
        assertEquals(1, assessment.violationMoodWeight());
    }

    @Test
    void animalMoodWeightCanScaleWithDoctrineExtremity() {
        AnimalKillingDoctrineManager.Assessment assessment = AnimalKillingDoctrineManager.evaluate(true, Map.of(
                "namespace:wder/slaughter_animals", "namespace:wder/slaughter_animals/love",
                "namespace:wder/killing_innocent_animals", "namespace:wder/killing_innocent_animals/abhor"
        ), (doctrine, attitude) -> attitude.endsWith("/abhor") ? 3 : 2);

        assertEquals(2, assessment.compliantMoodWeight());
        assertEquals(3, assessment.violationMoodWeight());
        assertEquals(2, assessment.moodEntries().size());
        assertTrue(assessment.moodEntries().contains(new DoctrineMoodEntry("namespace:wder/slaughter_animals",
                "namespace:wder/slaughter_animals/love", true, 2)));
        assertTrue(assessment.moodEntries().contains(new DoctrineMoodEntry(
                "namespace:wder/killing_innocent_animals",
                "namespace:wder/killing_innocent_animals/abhor", false, 3)));
        assertTrue(assessment.moodEntries().stream().anyMatch(entry -> entry.source().equals(
                "doctrine|namespace:wder/slaughter_animals|namespace:wder/slaughter_animals/love|compliant")));
    }

    @Test
    void animalAttitudesCanUseSeparateCompliantAndViolationMoodChanges() {
        AnimalKillingDoctrineManager.Assessment assessment = AnimalKillingDoctrineManager.evaluate(true, Map.of(
                "namespace:wder/slaughter_animals", "namespace:wder/slaughter_animals/love",
                "namespace:wder/killing_innocent_animals", "namespace:wder/killing_innocent_animals/abhor"
        ), (doctrine, attitude) -> 2, (doctrine, attitude, compliant) -> compliant ? 3 : 5);

        assertEquals(6, assessment.compliantMoodChange());
        assertEquals(10, assessment.violationMoodChange());
    }

    @Test
    void cannibalismDoctrineOverridesMeatEatingForHumanMeat() {
        FoodDoctrineManager.Assessment assessment = FoodDoctrineManager.evaluate(
                FoodDoctrineManager.FoodCategory.HUMAN_MEAT,
                Map.of(
                        "namespace:wder/meat_eating", "namespace:wder/meat_eating/abhor_meat",
                        "namespace:wder/cannibalism", "namespace:wder/cannibalism/enjoy"
                )
        );

        assertEquals(1, assessment.compliantCount());
        assertEquals(0, assessment.violationCount());
        assertEquals(1, assessment.compliantMoodWeight());
        assertEquals(0, assessment.violationMoodWeight());
        assertEquals(1, assessment.moodEntries().size());
        assertEquals("namespace:wder/cannibalism", assessment.moodEntries().getFirst().doctrineId());
    }

    @Test
    void cannibalismViolationSuppressesMeatEatingBonusForHumanMeat() {
        FoodDoctrineManager.Assessment assessment = FoodDoctrineManager.evaluate(
                FoodDoctrineManager.FoodCategory.HUMAN_MEAT,
                Map.of(
                        "namespace:wder/meat_eating", "namespace:wder/meat_eating/anti_vegetarian_hate",
                        "namespace:wder/cannibalism", "namespace:wder/cannibalism/abhor"
                )
        );

        assertEquals(0, assessment.compliantCount());
        assertEquals(1, assessment.violationCount());
        assertEquals(0, assessment.compliantMoodWeight());
        assertEquals(1, assessment.violationMoodWeight());
        assertEquals(1, assessment.moodEntries().size());
        assertEquals("namespace:wder/cannibalism", assessment.moodEntries().getFirst().doctrineId());
    }

    @Test
    void meatEatingDoctrineStillAppliesToHumanMeatWithoutCannibalismDoctrine() {
        FoodDoctrineManager.Assessment assessment = FoodDoctrineManager.evaluate(
                FoodDoctrineManager.FoodCategory.HUMAN_MEAT,
                Map.of("namespace:wder/meat_eating", "namespace:wder/meat_eating/abhor_meat")
        );

        assertEquals(1, assessment.violationCount());
        assertEquals(1, assessment.violationMoodWeight());
    }

    @Test
    void humanMeatMoodWeightCanScaleWithDoctrineExtremity() {
        FoodDoctrineManager.Assessment assessment = FoodDoctrineManager.evaluate(
                FoodDoctrineManager.FoodCategory.HUMAN_MEAT,
                Map.of(
                        "namespace:wder/meat_eating", "namespace:wder/meat_eating/abhor_meat",
                        "namespace:wder/cannibalism", "namespace:wder/cannibalism/enjoy"
                ),
                (doctrine, attitude) -> attitude.endsWith("/abhor_meat") ? 3 : 2
        );

        assertEquals(2, assessment.compliantMoodWeight());
        assertEquals(0, assessment.violationMoodWeight());
    }

    @Test
    void foodAttitudesCanUseSeparateCompliantAndViolationMoodChanges() {
        FoodDoctrineManager.Assessment assessment = FoodDoctrineManager.evaluate(
                FoodDoctrineManager.FoodCategory.HUMAN_MEAT,
                Map.of(
                        "namespace:wder/meat_eating", "namespace:wder/meat_eating/abhor_meat",
                        "namespace:wder/cannibalism", "namespace:wder/cannibalism/enjoy"
                ),
                (doctrine, attitude) -> attitude.endsWith("/enjoy") ? 2 : 3,
                (doctrine, attitude, compliant) -> compliant ? 4 : 1
        );

        assertEquals(8, assessment.compliantMoodChange());
        assertEquals(0, assessment.violationMoodChange());
        assertEquals(1, assessment.moodEntries().size());
        assertTrue(assessment.moodEntries().stream().anyMatch(entry -> entry.source().equals(
                "doctrine|namespace:wder/cannibalism|namespace:wder/cannibalism/enjoy|compliant")));
    }


}
