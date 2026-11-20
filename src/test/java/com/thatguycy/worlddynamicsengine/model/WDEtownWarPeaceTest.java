package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WDEtownWarPeaceTest {
    @Test
    void warAndPeaceTransitionTimeSurvivesSerialization() {
        WDEtown town = new WDEtown("A Town", "A Nation");
        town.setWarPeaceSince(0L);
        town.setLastEconomicWarAt(654321L);

        WDEtown atWar = WDEtown.deserialize(town.serialize());
        assertEquals(0L, atWar.getWarPeaceSince());
        assertEquals(654321L, atWar.getLastEconomicWarAt());

        atWar.setWarPeaceSince(123456L);
        assertEquals(123456L, WDEtown.deserialize(atWar.serialize()).getWarPeaceSince());
    }

    @Test
    void legacyTownStartsPeaceTrackingAtMigrationTime() {
        long beforeLoad = System.currentTimeMillis();
        WDEtown town = WDEtown.deserialize(Map.of("townName", "Legacy Town", "nationName", "None"));

        assertTrue(town.getWarPeaceSince() >= beforeLoad);
        assertTrue(town.getLastEconomicWarAt() >= beforeLoad);
    }
}
