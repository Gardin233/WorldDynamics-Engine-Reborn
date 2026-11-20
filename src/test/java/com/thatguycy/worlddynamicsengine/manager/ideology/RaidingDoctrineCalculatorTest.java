package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidingDoctrineCalculatorTest {
    @Test
    void onlyTributeOrReparationsOfAtLeastFivePercentQualify() {
        assertTrue(RaidingDoctrineCalculator.hasQualifyingEconomicTerms(true, 0));
        assertTrue(RaidingDoctrineCalculator.hasQualifyingEconomicTerms(false, 5));
        assertTrue(RaidingDoctrineCalculator.hasQualifyingEconomicTerms(false, 20));
        assertFalse(RaidingDoctrineCalculator.hasQualifyingEconomicTerms(false, 4));
        assertFalse(RaidingDoctrineCalculator.hasQualifyingEconomicTerms(false, 0));
    }

    @Test
    void economicWarSatisfiesActiveRaidingAttitudes() {
        assertEquals(Boolean.TRUE, RaidingDoctrineCalculator.assessEconomicWar("valued", true));
        assertEquals(Boolean.TRUE, RaidingDoctrineCalculator.assessEconomicWar("required", true));
        assertEquals(Boolean.FALSE, RaidingDoctrineCalculator.assessEconomicWar("valued", false));
        assertNull(RaidingDoctrineCalculator.assessEconomicWar("neutral", true));
    }

    @Test
    void prolongedPeaceViolatesActiveRaidingAttitudes() {
        assertEquals(Boolean.FALSE, RaidingDoctrineCalculator.assessLongPeace("valued"));
        assertEquals(Boolean.FALSE, RaidingDoctrineCalculator.assessLongPeace("required"));
        assertNull(RaidingDoctrineCalculator.assessLongPeace("neutral"));
    }
}
