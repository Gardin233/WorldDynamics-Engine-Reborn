package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TownChronicleTest {
    @Test
    void serializationPreservesTownIdentitySnapshotAndEvents() {
        TownChronicle original = new TownChronicle(UUID.randomUUID().toString(), "North Town", 1234L);
        original.snapshot.put("government", "REPUBLIC");
        original.snapshot.put("population", 12);
        original.events.add(Map.of("id", UUID.randomUUID().toString(), "type", "TOWN_CREATED",
                "town", original.name, "recordedAt", 1234L,
                "values", Map.of("mayor", "Mayor", "population", 12)));

        TownChronicle restored = TownChronicle.deserialize(original.serialize());

        assertEquals(original.id, restored.id);
        assertEquals(original.townUuid, restored.townUuid);
        assertEquals(original.name, restored.name);
        assertEquals(original.snapshot, restored.snapshot);
        assertEquals(original.events, restored.events);
        assertTrue(restored.serialize().get("folder").toString().contains("history/towns/"));
    }

    @Test
    void mapConstructorCopiesEventMapsAndArchiveState() {
        Map<String, Object> snapshot = new LinkedHashMap<>(Map.of("nation", "A Nation", "population", 4));
        Map<String, Object> event = new LinkedHashMap<>(Map.of("type", "TOWN_NATION_CHANGED", "town", "A Town"));
        TownChronicle restored = TownChronicle.deserialize(Map.of(
                "id", UUID.randomUUID().toString(),
                "townUuid", UUID.randomUUID().toString(),
                "name", "A Town",
                "openedAt", 1L,
                "foundedAt", 2L,
                "endedAt", 3L,
                "archived", true,
                "snapshot", snapshot,
                "events", List.of(event)));

        assertTrue(restored.archived);
        assertEquals(3L, restored.endedAt);
        assertEquals(snapshot, restored.snapshot);
        assertEquals(event, restored.events.getFirst());
    }
}
