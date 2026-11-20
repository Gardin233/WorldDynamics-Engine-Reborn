package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WDEresidentRitualTest {
    @Test
    void ritualParticipationTimestampPersistsAndLegacyProfilesReceiveGracePeriod() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Ritualist");
        resident.setLastRitualParticipationAt(123456789L);

        WDEresident restored = new WDEresident(resident.serialize());
        assertEquals(123456789L, restored.getLastRitualParticipationAt());

        HashMap<String, Object> legacyData = new HashMap<>(resident.serialize());
        legacyData.remove("lastRitualParticipationAt");
        long beforeLoad = System.currentTimeMillis();
        WDEresident migrated = new WDEresident(legacyData);
        assertTrue(migrated.getLastRitualParticipationAt() >= beforeLoad);
    }

    @Test
    void changingPersonalFaithStartsANewParticipationPeriod() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Ritualist");
        resident.setLastRitualParticipationAt(1L);

        resident.setReligionName("New Faith");

        assertTrue(resident.getLastRitualParticipationAt() > 1L);
    }
}
