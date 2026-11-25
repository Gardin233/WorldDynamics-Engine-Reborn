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

    @Test
    void legacyPercentageResidentTaxMigratesToStoppedFixedTax() {
        WDEtown town = WDEtown.deserialize(Map.of(
                "townName", "Legacy Town",
                "nationName", "Legacy Nation",
                "residentTaxManagedByNation", true,
                "residentTaxType", "WEALTH_PERCENTAGE",
                "residentTaxAmount", 25.0D
        ));

        assertEquals("FIXED", town.getResidentTaxType());
        assertEquals(0.0D, town.getResidentTaxAmount(), 0.0001D);
        assertEquals("FIXED", town.serialize().get("residentTaxType"));
        assertEquals(0.0D, ((Number) town.serialize().get("residentTaxAmount")).doubleValue(), 0.0001D);
    }
}
