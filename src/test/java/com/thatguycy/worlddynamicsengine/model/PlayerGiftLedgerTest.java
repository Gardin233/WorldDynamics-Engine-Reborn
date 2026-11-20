package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerGiftLedgerTest {
    @Test
    void cooldownTimestampsSurviveSerializationAndExpireCleanly() {
        PlayerGiftLedger ledger = new PlayerGiftLedger();
        ledger.setRequestCooldownUntil("requester:target", 200L);
        ledger.setKindnessGiftCooldownUntil("giver:receiver", 300L);
        ledger.setRefusalMoodCooldownUntil("target:requester", 400L);

        PlayerGiftLedger restored = PlayerGiftLedger.deserialize(ledger.serialize());
        assertEquals(200L, restored.getRequestCooldownUntil("requester:target"));
        assertEquals(300L, restored.getKindnessGiftCooldownUntil("giver:receiver"));
        assertEquals(400L, restored.getRefusalMoodCooldownUntil("target:requester"));

        restored.pruneExpired(300L);
        assertEquals(0L, restored.getRequestCooldownUntil("requester:target"));
        assertEquals(0L, restored.getKindnessGiftCooldownUntil("giver:receiver"));
        assertEquals(400L, restored.getRefusalMoodCooldownUntil("target:requester"));

        PlayerGiftLedger legacy = PlayerGiftLedger.deserialize(Map.of());
        assertEquals(0L, legacy.getRequestCooldownUntil("missing"));
    }
}
