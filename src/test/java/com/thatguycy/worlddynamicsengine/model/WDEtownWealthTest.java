package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WDEtownWealthTest {
    @Test
    void wealthAndRefreshTimeSurviveSerialization() {
        WDEtown town = new WDEtown("Wealth Town", "None");
        town.setWealth(12345.67D);
        town.setWealthUpdatedAt(987654321L);

        WDEtown restored = WDEtown.deserialize(town.serialize());

        assertEquals(12345.67D, restored.getWealth(), 0.0001D);
        assertEquals(987654321L, restored.getWealthUpdatedAt());
    }

    @Test
    void legacyTownDefaultsToZeroWealth() {
        WDEtown town = WDEtown.deserialize(Map.of("townName", "Legacy Town"));

        assertEquals(0.0D, town.getWealth(), 0.0001D);
        assertEquals(0L, town.getWealthUpdatedAt());
    }

    @Test
    void invalidWealthValuesAreClamped() {
        WDEtown town = WDEtown.deserialize(Map.of("townName", "Invalid Town", "wealth", Double.NaN));

        assertEquals(0.0D, town.getWealth(), 0.0001D);
        town.setWealth(-1.0D);
        assertEquals(0.0D, town.getWealth(), 0.0001D);
    }
}
