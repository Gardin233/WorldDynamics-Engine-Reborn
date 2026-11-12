package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MentalCollapseDehumanizationTest {
    @Test
    void onlyRequiredNonhumanizationAttitudeForcesHumanityCollapse() {
        assertTrue(MentalCollapseManager.isRequiredNonhumanizationAttitude(
                "namespace:wder/nonhumanization", "namespace:wder/nonhumanization/required"));
        assertFalse(MentalCollapseManager.isRequiredNonhumanizationAttitude(
                "namespace:wder/nonhumanization", "namespace:wder/nonhumanization/neutral"));
        assertFalse(MentalCollapseManager.isRequiredNonhumanizationAttitude(
                "namespace:wder/some_other_doctrine", "namespace:wder/some_other_doctrine/required"));
        assertFalse(MentalCollapseManager.isRequiredNonhumanizationAttitude(
                "namespace:wder/nonhumanization", null));
    }
}
