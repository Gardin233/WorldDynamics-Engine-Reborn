package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoodManagerExpectationTest {
    @Test
    void kingAdjustmentReplacesMayorAdjustmentWhenBothRolesAreHeld() {
        assertEquals(-12, MoodManager.selectRoleExpectationAdjustment(true, -20, true, -12));
    }

    @Test
    void mayorAdjustmentAppliesWhenPlayerIsNotKing() {
        assertEquals(-8, MoodManager.selectRoleExpectationAdjustment(true, -8, false, -12));
    }

    @Test
    void noRoleAdjustmentAppliesWithoutEitherRole() {
        assertEquals(0, MoodManager.selectRoleExpectationAdjustment(false, -8, false, -12));
    }
}
