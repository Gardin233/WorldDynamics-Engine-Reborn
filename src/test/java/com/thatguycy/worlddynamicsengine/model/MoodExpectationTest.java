package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thatguycy.worlddynamicsengine.manager.ideology.MoodManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoodExpectationTest {
    @Test
    void newPlayerExpectationStepsDownEveryTwoHours() {
        List<Integer> bonuses = List.of(30, 24, 18, 12, 6, 0);
        long twoHoursInTicks = 2L * 60L * 60L * 20L;

        assertEquals(30, MoodManager.getNewPlayerExpectationBonus(0, 2, bonuses));
        assertEquals(30, MoodManager.getNewPlayerExpectationBonus(twoHoursInTicks - 1, 2, bonuses));
        assertEquals(24, MoodManager.getNewPlayerExpectationBonus(twoHoursInTicks, 2, bonuses));
        assertEquals(18, MoodManager.getNewPlayerExpectationBonus(twoHoursInTicks * 2, 2, bonuses));
        assertEquals(0, MoodManager.getNewPlayerExpectationBonus(twoHoursInTicks * 5, 2, bonuses));
        assertEquals(0, MoodManager.getNewPlayerExpectationBonus(twoHoursInTicks * 8, 2, bonuses));
    }
}
