package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void betrayedFaithPersistsAcrossResidentSerialization() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        resident.setBetrayedReligionName("Old Faith");

        WDEresident restored = WDEresident.deserialize(resident.serialize());

        assertEquals("Old Faith", restored.getBetrayedReligionName());
        restored.setBetrayedReligionName("");
        assertEquals("", WDEresident.deserialize(restored.serialize()).getBetrayedReligionName());
    }

    @Test
    void bondedAnimalsPersistAndCanBeRemovedByUuid() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        UUID firstAnimal = UUID.randomUUID();
        UUID secondAnimal = UUID.randomUUID();

        assertTrue(resident.addBondedAnimalUuid(firstAnimal));
        assertTrue(resident.addBondedAnimalUuid(secondAnimal));
        assertFalse(resident.addBondedAnimalUuid(firstAnimal));
        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertEquals(2, restored.getBondedAnimalUuids().size());
        assertTrue(restored.removeBondedAnimalUuid(firstAnimal));
        assertFalse(restored.removeBondedAnimalUuid(firstAnimal));
        assertEquals(List.of(secondAnimal.toString()), List.copyOf(restored.getBondedAnimalUuids()));
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
    void persistentMoodChangesSurviveSerializationAndReplaceWithoutExpiring() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        String prefix = "doctrine|namespace:wder/armor_wearing|";
        String compliant = prefix + "namespace:wder/armor_wearing/naked|compliant";
        String violation = prefix + "namespace:wder/armor_wearing/naked|violation";
        resident.addMoodChange(
                "doctrine:namespace:wder/armor_wearing:namespace:wder/armor_wearing/naked:violation",
                -7, now + 30L * 60L * 1000L);
        assertEquals(43, resident.getMoodAt(now));

        assertTrue(resident.replacePersistentMoodChanges(prefix, compliant, 9));
        assertEquals(59, resident.getMoodAt(now));
        assertEquals(59, resident.getMoodAt(now + 365L * 24L * 60L * 60L * 1000L));
        assertFalse(resident.replacePersistentMoodChanges(prefix, compliant, 9));

        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertEquals(59, restored.getMoodAt(now));
        assertEquals(1, restored.getActiveMoodChanges(now).size());
        WDEresident.MoodChangeEntry entry = restored.getActiveMoodChanges(now).getFirst();
        assertTrue(entry.permanent());
        assertEquals(9, entry.delta());

        assertTrue(restored.replacePersistentMoodChanges(prefix, violation, -6));
        assertEquals(44, restored.getMoodAt(now));
        assertTrue(restored.replacePersistentMoodChanges(prefix, null, 0));
        assertEquals(50, restored.getMoodAt(now));
    }

    @Test
    void dehumanizedStatusLocksMoodAtFiftyAndBlocksAllChanges() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        resident.addMoodChange("relief", 40, now + 60_000L);
        resident.replacePersistentMoodChanges("doctrine|armor|", "doctrine|armor|attitude|compliant", 10);
        resident.addInspirationCharge("arrow_armor_piercing");

        resident.setDehumanized(true);
        assertEquals(50, resident.getMoodAt(now));
        assertTrue(resident.getActiveMoodChanges(now).isEmpty());
        assertEquals(0, resident.getInspirationCharges("arrow_armor_piercing"));
        assertEquals(50, resident.addMoodChange("manual", -25, now + 60_000L));

        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertTrue(restored.isDehumanized());
        assertEquals(50, restored.getMood());
        assertTrue(restored.getActiveMoodChanges(now).isEmpty());
        assertFalse(restored.replacePersistentMoodChanges("doctrine|armor|",
                "doctrine|armor|attitude|violation", -20));
        assertEquals(50, restored.getMood());
        restored.setDehumanized(false);
        assertFalse(restored.isDehumanized());
        assertEquals(60, restored.addMoodChange("manual", 10, now + 60_000L));
    }

    @Test
    void inspirationIntervalAndChargesSurviveSerializationAndConsumeIndividually() {
        long start = System.currentTimeMillis();
        long interval = 2L * 60L * 1000L;
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");

        assertFalse(resident.advanceInspirationCheckIfDue(start + interval - 1L, interval));
        assertTrue(resident.advanceInspirationCheckIfDue(start + interval + 10L, interval));
        assertTrue(resident.addInspirationCharge("arrow_armor_piercing"));
        assertTrue(resident.addInspirationCharge("arrow_armor_piercing"));

        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertEquals(2, restored.getInspirationCharges("arrow_armor_piercing"));
        assertFalse(restored.advanceInspirationCheckIfDue(start + interval * 2L + 9L, interval));
        assertTrue(restored.advanceInspirationCheckIfDue(start + interval * 2L + 10L, interval));
        assertTrue(restored.consumeInspirationCharge("arrow_armor_piercing"));
        assertEquals(1, restored.getInspirationCharges("arrow_armor_piercing"));
        assertTrue(restored.consumeInspirationCharge("arrow_armor_piercing"));
        assertEquals(0, restored.getInspirationCharges("arrow_armor_piercing"));
        assertFalse(restored.consumeInspirationCharge("arrow_armor_piercing"));
    }

    @Test
    void clearingMoodChangesRemovesTimedAndPersistentModifiersOnly() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        long now = System.currentTimeMillis();
        resident.addMoodChange("relief", 20, now + 60_000L);
        resident.replacePersistentMoodChanges("doctrine|armor|", "doctrine|armor|naked|compliant", 8);
        resident.addFlag("wde:manual_collapse");

        resident.clearMoodChanges();

        assertEquals(50, resident.getMood());
        assertTrue(resident.getActiveMoodChanges(now).isEmpty());
        assertTrue(resident.hasFlag("wde:manual_collapse"));
        assertFalse(resident.isDehumanized());
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
    void expiredCollapseIsPreservedForCompletionAfterReconnect() {
        WDEresident resident = WDEresident.deserialize(Map.of(
                "userUUID", UUID.randomUUID().toString(),
                "username", "Player",
                "activeCollapseId", "humanity_collapse",
                "activeCollapseExpiresAt", System.currentTimeMillis() - 1_000L
        ));

        assertEquals("humanity_collapse", resident.getActiveCollapseId());
        assertFalse(resident.hasActiveCollapse(System.currentTimeMillis()));
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

    @Test
    void comfortSleepTrackingSurvivesSerializationAndCanBeReset() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Player");
        resident.startComfortSleepTracking(72_000L);

        WDEresident restored = WDEresident.deserialize(resident.serialize());
        assertTrue(restored.isComfortSleepTracking());
        assertEquals(72_000L, restored.getLastComfortSleepPlayedTicks());

        restored.recordComfortSleep(80_000L);
        assertEquals(80_000L, WDEresident.deserialize(restored.serialize())
                .getLastComfortSleepPlayedTicks());

        restored.stopComfortSleepTracking();
        assertFalse(restored.isComfortSleepTracking());
        assertEquals(-1L, restored.getLastComfortSleepPlayedTicks());
    }
}
