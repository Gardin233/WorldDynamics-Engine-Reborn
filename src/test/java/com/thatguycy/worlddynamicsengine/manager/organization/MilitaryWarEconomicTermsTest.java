package com.thatguycy.worlddynamicsengine.manager.organization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MilitaryWarEconomicTermsTest {
    @Test
    void tracksEconomicTermsSeparatelyForEachWarSide() {
        OrgManager.MilitaryWarState war = new OrgManager.MilitaryWarState(
                "Attacker", "Defender", List.of("Attacker", "Ally"), List.of("Defender"), 1L);

        assertTrue(war.markEconomicTermsDemanded("Ally"));
        assertTrue(war.hasAttackerEconomicTermsDemanded());
        assertFalse(war.hasDefenderEconomicTermsDemanded());

        OrgManager.MilitaryWarState restored = OrgManager.MilitaryWarState.fromMap(
                war.getWarKey(), war.toMap());
        assertTrue(restored.hasAttackerEconomicTermsDemanded());
        assertFalse(restored.hasDefenderEconomicTermsDemanded());

        assertTrue(restored.markEconomicTermsDemanded("Defender"));
        assertTrue(restored.hasDefenderEconomicTermsDemanded());
        assertFalse(restored.markEconomicTermsDemanded("Not a participant"));
    }
}
