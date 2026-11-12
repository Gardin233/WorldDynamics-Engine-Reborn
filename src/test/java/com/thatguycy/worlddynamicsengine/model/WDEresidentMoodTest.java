package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WDEresidentMoodTest {
    @Test
    void legacyResidentStartsAtNeutralMoodAndPersistsUpdates() {
        WDEresident resident = WDEresident.deserialize(Map.of(
                "userUUID", UUID.randomUUID().toString(),
                "username", "Player"
        ));

        assertEquals(50, resident.getMood());
        resident.setMood(73);
        assertEquals(73, WDEresident.deserialize(resident.serialize()).getMood());
    }

    @Test
    void moodIsBoundedBetweenZeroAndOneHundred() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");

        resident.setMood(-5);
        assertEquals(0, resident.getMood());
        resident.setMood(105);
        assertEquals(100, resident.getMood());
    }

    @Test
    void temporaryMoodChangesExpireAndSurviveSerialization() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        long expiresAt = now + 60_000L;

        resident.addMoodChange(12, expiresAt);
        assertEquals(62, resident.getMoodAt(now));

        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertEquals(62, restored.getMoodAt(now));
        assertEquals(50, restored.getMoodAt(expiresAt));
        assertTrue(restored.refreshMood(expiresAt));
        assertEquals(50, restored.getMood());
    }

    @Test
    void legacyMoodWithoutTimedChangesMigratesToThirtyMinuteDelta() {
        long now = System.currentTimeMillis();
        WDEresident resident = WDEresident.deserialize(Map.of(
                "userUUID", UUID.randomUUID().toString(),
                "username", "Player",
                "mood", 70
        ));

        assertEquals(70, resident.getMoodAt(now));
        assertEquals(50, resident.getMoodAt(now + 30L * 60L * 1000L + 1000));
    }

    @Test
    void activeCollapseAndExpirySurviveSerialization() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long expiresAt = System.currentTimeMillis() + 60_000L;

        resident.setActiveCollapse("faith_crisis", expiresAt);
        resident.addFlag("wde:manual_collapse");
        WDEresident restored = WDEresident.deserialize(resident.serialize());

        assertEquals("faith_crisis", restored.getActiveCollapseId());
        assertEquals(expiresAt, restored.getActiveCollapseExpiresAt());
        assertTrue(restored.hasFlag("wde:manual_collapse"));
        assertTrue(restored.hasActiveCollapse(System.currentTimeMillis()));
    }

    @Test
    void reliefBonusIsASeparateExpiringMoodChange() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        long expiresAt = now + 30L * 60L * 1000L;

        resident.addMoodChange("relief", 40, expiresAt);

        assertEquals(90, resident.getMoodAt(now));
        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertEquals(90, restored.getMoodAt(now));
        assertEquals(50, restored.getMoodAt(expiresAt));
    }

    @Test
    void repeatedCompliantAndViolatedActionsStackAndExpireIndependently() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        long firstExpiry = now + 10_000L;
        long secondExpiry = now + 20_000L;

        resident.addMoodChange("animalKilling", -4, firstExpiry);
        resident.addMoodChange("animalKilling", -4, secondExpiry);
        assertEquals(42, resident.getMoodAt(now));
        assertEquals(46, resident.getMoodAt(firstExpiry));
        assertEquals(50, resident.getMoodAt(secondExpiry));

        WDEresident compliant = new WDEresident(UUID.randomUUID(), "Player");
        long thirdExpiry = now + 30_000L;
        long fourthExpiry = now + 40_000L;
        compliant.addMoodChange("humanMeat", 6, thirdExpiry);
        compliant.addMoodChange("humanMeat", 6, fourthExpiry);
        assertEquals(62, compliant.getMoodAt(now));
        assertEquals(56, compliant.getMoodAt(thirdExpiry));
        assertEquals(50, compliant.getMoodAt(fourthExpiry));
    }

    @Test
    void changesAddedAtMoodCapRemainStackedUntilTheirOwnExpiry() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        long firstExpiry = now + 10_000L;
        long secondExpiry = now + 20_000L;

        resident.addMoodChange("doctrine", 80, firstExpiry);
        resident.addMoodChange("doctrine", 40, secondExpiry);

        assertEquals(100, resident.getMoodAt(now));
        assertEquals(90, resident.getMoodAt(firstExpiry));
        assertEquals(50, resident.getMoodAt(secondExpiry));
    }

    @Test
    void activeMoodChangeDetailsIncludeSourceAndIgnoreExpiredEntries() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        long firstExpiry = now + 1_000L;
        long secondExpiry = now + 2_000L;
        resident.addMoodChange("animalKilling", 3, firstExpiry);
        resident.addMoodChange("food:human_meat", -5, secondExpiry);

        List<WDEresident.MoodChangeEntry> changes = resident.getActiveMoodChanges(now);

        assertEquals(2, changes.size());
        assertEquals("animalKilling", changes.get(0).source());
        assertEquals(3, changes.get(0).delta());
        assertEquals("food:human_meat", changes.get(1).source());
        assertEquals(-5, changes.get(1).delta());
        List<WDEresident.MoodChangeEntry> remaining = resident.getActiveMoodChanges(firstExpiry + 1L);
        assertEquals(1, remaining.size());
        assertEquals("food:human_meat", remaining.getFirst().source());
    }
}
