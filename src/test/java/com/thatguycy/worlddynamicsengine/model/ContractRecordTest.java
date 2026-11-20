package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractRecordTest {
    @Test
    void serializationPreservesPartiesTextAndDecisionMetadata() {
        ContractRecord original = new ContractRecord("abc12345", "TOWN", "town-uuid",
                "North Town", "NATION", "nation-uuid", "North Nation",
                UUID.randomUUID(), "Signer", 1234L, List.of("First page", "Second page"));
        original.status = "ACCEPTED";
        original.decisionName = "Delegate";
        original.decisionUuid = UUID.randomUUID().toString();
        original.decidedAt = 5678L;
        original.viewedBy.add(original.decisionUuid);

        ContractRecord restored = ContractRecord.deserialize(original.serialize());

        assertEquals(original.fromDisplay, restored.fromDisplay);
        assertEquals(original.toDisplay, restored.toDisplay);
        assertEquals(original.bodyPages, restored.bodyPages);
        assertEquals("ACCEPTED", restored.status);
        assertEquals(original.decisionName, restored.decisionName);
        assertEquals(original.decisionUuid, restored.decisionUuid);
        assertEquals(5678L, restored.decidedAt);
        assertTrue(restored.viewedBy.contains(original.decisionUuid));
    }

    @Test
    void deserializationDefaultsMissingDecisionFieldsForPendingRecords() {
        ContractRecord restored = ContractRecord.deserialize(Map.of(
                "id", "abc12345",
                "fromType", "PLAYER",
                "fromKey", UUID.randomUUID().toString(),
                "toType", "PLAYER",
                "toKey", UUID.randomUUID().toString(),
                "bodyPages", List.of("Contract text")));

        assertEquals("PENDING", restored.status);
        assertEquals("", restored.decisionName);
        assertEquals(0L, restored.decidedAt);
        assertTrue(restored.viewedBy.isEmpty());
    }
}
