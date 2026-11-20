package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChurchChronicleTest {
    @Test
    void serializationPreservesChurchTimelineAndPrivacyFilteredSnapshot() {
        UUID ideologyUuid = UUID.randomUUID();
        WDEChurch church = new WDEChurch(UUID.randomUUID(), ideologyUuid, "North Faith",
                "North Synod", UUID.randomUUID(), "Head", 1234L);
        church.creditTreasury(new BigDecimal("250.00"), UUID.randomUUID(), UUID.randomUUID().toString(), 1400L);
        ChurchChronicle original = new ChurchChronicle(church);
        original.snapshot.put("memberCount", 4);
        original.snapshot.put("treasury", "250.00");
        original.events.add(Map.of("id", UUID.randomUUID().toString(), "type", "CHURCH_TREASURY_CREDIT",
                "church", "North Synod", "recordedAt", 1400L,
                "values", Map.of("amount", "250.00")));

        ChurchChronicle restored = ChurchChronicle.deserialize(original.serialize());

        assertEquals(original.id, restored.id);
        assertEquals(church.getChurchUuid().toString(), restored.churchUuid);
        assertEquals(ideologyUuid.toString(), restored.ideologyUuid);
        assertEquals(original.snapshot, restored.snapshot);
        assertEquals(original.events, restored.events);
        assertTrue(restored.serialize().get("folder").toString().contains("history/churches/"));
        assertTrue(restored.events.stream().noneMatch(event -> event.toString().contains("actorUuid")));
    }
}
