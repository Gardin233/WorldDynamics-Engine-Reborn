package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WDEreligionGovernanceTest {
    @Test
    void legacyDataMigratesToLifetimeGovernance() {
        UUID founder = UUID.randomUUID();
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("religionName", "Example");
        legacy.put("founderUuid", founder.toString());
        legacy.put("founderName", "Founder");
        legacy.put("pontiffUuid", founder.toString());
        legacy.put("pontiffName", "Founder");

        WDEreligion religion = WDEreligion.deserialize(legacy);

        assertEquals(WDEreligion.GovernanceMode.LIFETIME, religion.getGovernanceMode());
        assertTrue(religion.needsGovernanceModeMigration());
        religion.setGovernanceMode(WDEreligion.GovernanceMode.LIFETIME);
        assertFalse(religion.needsGovernanceModeMigration());
        assertEquals("LIFETIME", religion.serialize().get("governanceMode"));
    }

    @Test
    void governanceAndElectionStateSurviveSerialization() {
        UUID founder = UUID.randomUUID();
        UUID cardinal = UUID.randomUUID();
        WDEreligion religion = new WDEreligion("Example", founder, "Founder", 10L);
        religion.creditTreasury(new BigDecimal("1000"));
        religion.setGovernanceMode(WDEreligion.GovernanceMode.CARDINAL);
        religion.appointCardinal(cardinal, "Cardinal");
        religion.openElection(Map.of("endAt", 500L));
        religion.castElectionBallot(cardinal, founder.toString());
        religion.transferLeadership(cardinal, "Cardinal", "VOLUNTARY_ABDICATION", 100L);

        WDEreligion restored = WDEreligion.deserialize(religion.serialize());

        assertEquals(WDEreligion.GovernanceMode.CARDINAL, restored.getGovernanceMode());
        assertEquals(new BigDecimal("1000"), restored.getTreasuryBalance());
        assertTrue(restored.isCardinal(cardinal));
        assertEquals(founder.toString(), restored.getElectionBallots().get(cardinal.toString()));
        assertEquals(cardinal, restored.getPontiffUuid());
        assertEquals(1, restored.getLeadershipHistory().size());
        assertEquals(100L, restored.getLeadershipHistory().getFirst().get("previousLeaderAuthorityEndedAt"));
    }

    @Test
    void everyGovernanceModeCanBePersisted() {
        for (WDEreligion.GovernanceMode mode : WDEreligion.GovernanceMode.values()) {
            WDEreligion religion = new WDEreligion("Example", UUID.randomUUID(), "Founder", 10L);
            religion.setGovernanceMode(mode);

            WDEreligion restored = WDEreligion.deserialize(religion.serialize());

            assertEquals(mode, restored.getGovernanceMode());
        }
    }

    @Test
    void cardinalSeatsScaleAndRespectConfiguredCap() {
        assertEquals(0, WDEreligion.cardinalSeatLimit(0, 2, 25, 9));
        assertEquals(2, WDEreligion.cardinalSeatLimit(25, 2, 25, 9));
        assertEquals(3, WDEreligion.cardinalSeatLimit(26, 2, 25, 9));
        assertEquals(9, WDEreligion.cardinalSeatLimit(1000, 2, 25, 9));
    }

    @Test
    void treasuryGovernanceFeeCannotOverdraw() {
        WDEreligion religion = new WDEreligion("Example", UUID.randomUUID(), "Founder", 10L);
        religion.creditTreasury(new BigDecimal("10"));
        religion.debitGovernanceFee(new BigDecimal("4"));

        assertEquals(new BigDecimal("6"), religion.getTreasuryBalance());
        assertThrows(IllegalArgumentException.class,
                () -> religion.debitGovernanceFee(new BigDecimal("7")));
        assertEquals(new BigDecimal("6"), religion.getTreasuryBalance());
    }

    @Test
    void aCardinalCannotChangeASealedBallot() {
        UUID founder = UUID.randomUUID();
        UUID cardinal = UUID.randomUUID();
        WDEreligion religion = new WDEreligion("Example", founder, "Founder", 10L);
        religion.setGovernanceMode(WDEreligion.GovernanceMode.CARDINAL);
        religion.appointCardinal(cardinal, "Cardinal");
        religion.openElection(Map.of("endAt", 500L));
        religion.castElectionBallot(cardinal, founder.toString());

        assertThrows(IllegalArgumentException.class,
                () -> religion.castElectionBallot(cardinal, "ABSTAIN"));
    }
}
