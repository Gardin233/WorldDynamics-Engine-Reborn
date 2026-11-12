package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.model.war.WarGoalState;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalType;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import org.junit.jupiter.api.Test;

class WarGoalStateTest {
    @Test
    void controlGoalRequiresStrictlyMoreThanHoldDurationAndResetsOnLoss() {
        WarGoalState goal = new WarGoalState(WarGoalType.CAPTURE_TOWN,
                "Target", "Defender", null, null, 10L, true);
        long holdMillis = 30_000L;

        assertTrue(goal.updateCondition(true, 100L));
        assertFalse(goal.isReady(100L + holdMillis, holdMillis));
        assertTrue(goal.isReady(101L + holdMillis, holdMillis));

        assertTrue(goal.updateCondition(false, 40_101L));
        assertFalse(goal.isReady(80_000L, holdMillis));
        assertTrue(goal.updateCondition(true, 80_000L));
        assertFalse(goal.isReady(110_000L, holdMillis));
    }

    @Test
    void defenderCanDemandIndependenceOnlyWhenAttackersDoNotCurrentlyMeetGoal() {
        WarGoalState goal = new WarGoalState(WarGoalType.OCCUPY_CAPITAL,
                "Capital", "Defender", null, null, 10L, true);

        assertFalse(goal.canDefenderDemandIndependence(39.99D, 40D));
        assertTrue(goal.canDefenderDemandIndependence(40D, 40D));

        goal.updateCondition(true, 100L);
        assertFalse(goal.canDefenderDemandIndependence(100D, 40D));

        goal.updateCondition(false, 200L);
        assertTrue(goal.canDefenderDemandIndependence(40D, 40D));
    }

    @Test
    void defenderCannotDemandIndependenceInAllianceWarsOrForOtherGoalTypes() {
        WarGoalState allianceGoal = new WarGoalState(WarGoalType.OCCUPY_CAPITAL,
                "Capital", "Defender", null, null, 10L, false);
        WarGoalState otherGoal = new WarGoalState(WarGoalType.CAPTURE_TOWN,
                "Target", "Defender", null, null, 10L, true);

        assertFalse(allianceGoal.canDefenderDemandIndependence(100D, 40D));
        assertFalse(otherGoal.canDefenderDemandIndependence(100D, 40D));
    }

    @Test
    void dominanceDefeatsAreUniqueAndPersistAcrossReload() {
        WarGoalState goal = new WarGoalState(WarGoalType.SHOW_HEGEMONY,
                null, "Defender", "Threat", null, 8L, true);

        assertTrue(goal.recordAttackerDefeat("victim-1"));
        assertFalse(goal.recordAttackerDefeat("victim-1"));
        assertTrue(goal.recordAttackerDefeat("victim-2"));

        WarGoalState restored = WarGoalState.fromMap(goal.toMap());
        assertEquals(2L, restored.getAttackerDefeatedPlayers());
        assertFalse(restored.recordAttackerDefeat("victim-2"));
        assertTrue(restored.recordAttackerDefeat("victim-3"));
        assertEquals(3L, restored.getAttackerDefeatedPlayers());
    }

    @Test
    void warReasonsMapToConfiguredObjectiveTypes() {
        assertEquals(WarGoalType.CAPTURE_TOWN, WarGoalType.forReason(WarReason.CONQUEST));
        assertEquals(WarGoalType.CAPTURE_TOWN, WarGoalType.forReason(WarReason.RECONQUEST));
        assertEquals(WarGoalType.OCCUPY_CAPITAL, WarGoalType.forReason(WarReason.INDEPENDENCE));
        assertEquals(WarGoalType.OCCUPY_CAPITAL, WarGoalType.forReason(WarReason.HOLY_WAR));
        assertEquals(WarGoalType.OCCUPY_CAPITAL, WarGoalType.forReason(WarReason.EXPORT_REVOLUTION));
        assertEquals(WarGoalType.SHOW_HEGEMONY, WarGoalType.forReason(WarReason.HUMILIATE_RIVAL));
        assertEquals(WarGoalType.SHOW_HEGEMONY, WarGoalType.forReason(WarReason.DESTROY_THREAT));
        assertEquals(WarGoalType.NONE, WarGoalType.forReason(WarReason.TRADE_CONFLICT));
    }
}
