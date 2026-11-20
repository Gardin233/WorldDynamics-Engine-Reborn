package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdeologyChronicleTest {
    @Test
    void serializationPreservesIdeologyIdentityParentageAndProposalStates() {
        WDEreligion ideology = new WDEreligion("North Faith", UUID.randomUUID(), "Founder", 1234L);
        UUID parent = UUID.randomUUID();
        UUID church = UUID.randomUUID();
        ideology.setParentage(parent, church);
        IdeologyChronicle original = new IdeologyChronicle(ideology);
        original.snapshot.put("governance", "SYNODAL");
        original.snapshot.put("playerAffiliations", 12);
        original.proposalStates.put(UUID.randomUUID().toString(), "APPROVED");
        original.events.add(Map.of("id", UUID.randomUUID().toString(), "type", "IDEOLOGY_CREATED",
                "ideology", "North Faith", "recordedAt", 1234L,
                "values", Map.of("founder", "Founder")));

        IdeologyChronicle restored = IdeologyChronicle.deserialize(original.serialize());

        assertEquals(original.id, restored.id);
        assertEquals(ideology.getIdeologyUuid().toString(), restored.ideologyUuid);
        assertEquals(parent.toString(), restored.parentIdeologyUuid);
        assertEquals(church.toString(), restored.parentChurchUuid);
        assertEquals(original.snapshot, restored.snapshot);
        assertEquals(original.proposalStates, restored.proposalStates);
        assertEquals(original.events, restored.events);
        assertTrue(restored.serialize().get("folder").toString().contains("history/ideologies/"));
    }

    @Test
    void archivedIdeologyRetainsItsEvents() {
        IdeologyChronicle original = new IdeologyChronicle(
                new WDEreligion("Archived Faith", UUID.randomUUID(), "Founder", 10L));
        original.archived = true;
        original.endedAt = 20L;
        original.events.add(Map.of("type", "IDEOLOGY_DISSOLVED", "recordedAt", 20L));

        IdeologyChronicle restored = IdeologyChronicle.deserialize(original.serialize());

        assertTrue(restored.archived);
        assertEquals(20L, restored.endedAt);
        assertEquals(original.events, restored.events);
    }
}
