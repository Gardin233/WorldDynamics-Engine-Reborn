package com.thatguycy.worlddynamicsengine.manager.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.PeaceTreatyClauseState;
import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.PeaceTreatyClauseState.ClauseType;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalState;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PeaceTreatyRulesTest {
    @Test
    void appliesConfiguredCostsBeforeSeparatePeaceMultiplierAndRounding() {
        var config = new YamlConfiguration();
        config.set("peaceTreaty.clauses.reparations.pointsPerPercent", 2);
        config.set("peaceTreaty.clauses.humiliatePoints", -10);
        config.set("warGoals.clauses.forceDemocracyPoints", 7);
        var clauses = List.of(clause(ClauseType.REPARATIONS, Map.of("percent", 5)),
                clause(ClauseType.HUMILIATE, Map.of()), clause(ClauseType.FORCE_DEMOCRATIZATION, Map.of()));

        assertEquals(13, PeaceTreatyRules.points(clauses, config, false, () -> 0.75));
        assertEquals(26, PeaceTreatyRules.points(clauses, config, true, () -> 0.75));
        assertEquals(0, PeaceTreatyRules.points(List.of(), config, true, () -> 1.5));
    }

    @Test
    void conquestRequiresBothTargetTownAndAttackerDestination() {
        var goal = new WarGoalState(null, "Capital", null, null, null, 0, true);
        assertTrue(PeaceTreatyRules.matchesWarGoal(WarReason.CONQUEST, goal, "Attacker",
                clause(ClauseType.TRANSFER_TOWN, Map.of("town", "capital", "destinationNation", "attacker"))));
        assertFalse(PeaceTreatyRules.matchesWarGoal(WarReason.CONQUEST, goal, "Attacker",
                clause(ClauseType.TRANSFER_TOWN, Map.of("town", "Capital", "destinationNation", "Ally"))));
        assertFalse(PeaceTreatyRules.matchesWarGoal(WarReason.RECONQUEST, goal, "Attacker",
                clause(ClauseType.TRANSFER_TOWN, Map.of("town", "Another", "destinationNation", "Attacker"))));
    }

    @Test
    void specialGoalsCheckRequiredClauseParameters() {
        var goal = new WarGoalState(null, null, null, "Threat", null, 0, true);
        assertTrue(PeaceTreatyRules.matchesWarGoal(WarReason.DESTROY_THREAT, goal, "Attacker",
                clause(ClauseType.DISSOLVE_THREAT_ORGANIZATION, Map.of("organization", "threat"))));
        assertFalse(PeaceTreatyRules.matchesWarGoal(WarReason.DESTROY_THREAT, goal, "Attacker",
                clause(ClauseType.DISSOLVE_THREAT_ORGANIZATION, Map.of("organization", "Other"))));
        assertFalse(PeaceTreatyRules.matchesWarGoal(WarReason.EXPORT_REVOLUTION, goal, "Attacker",
                clause(ClauseType.FORCE_DEMOCRATIZATION, Map.of())));
        assertTrue(PeaceTreatyRules.matchesWarGoal(WarReason.EXPORT_REVOLUTION, goal, "Attacker",
                clause(ClauseType.FORCE_DEMOCRATIZATION, Map.of("president", "Candidate"))));
    }

    @Test
    void raidingRecognizesFixedTributeAndAtLeastFivePercentReparations() {
        assertFalse(PeaceTreatyRules.hasQualifyingRaidingTerms(null));
        assertFalse(PeaceTreatyRules.hasQualifyingRaidingTerms(List.of(
                clause(ClauseType.REPARATIONS, Map.of("percent", 4)))));
        assertTrue(PeaceTreatyRules.hasQualifyingRaidingTerms(List.of(
                clause(ClauseType.REPARATIONS, Map.of("percent", 5)))));
        assertTrue(PeaceTreatyRules.hasQualifyingRaidingTerms(List.of(
                clause(ClauseType.TRIBUTE, Map.of("amount", "10.00")))));
    }

    private static PeaceTreatyClauseState clause(ClauseType type, Map<String, Object> parameters) {
        return new PeaceTreatyClauseState(type, parameters);
    }
}
