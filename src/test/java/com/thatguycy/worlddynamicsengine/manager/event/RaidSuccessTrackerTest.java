package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaidSuccessTrackerTest {
    @Test
    void succeedsOnlyAfterEveryParticipantHasDied() {
        RaidSuccessTracker tracker = new RaidSuccessTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.addParticipant(first);
        tracker.addParticipant(second);

        tracker.recordDeath(first);
        tracker.recordDeath(first);
        assertFalse(tracker.wasSuccessful());

        tracker.recordDeath(second);
        assertTrue(tracker.wasSuccessful());
    }

    @Test
    void ignoresDeathsFromPlayersOutsideTheParticipantSet() {
        RaidSuccessTracker tracker = new RaidSuccessTracker();
        UUID participant = UUID.randomUUID();
        tracker.addParticipant(participant);

        tracker.recordDeath(UUID.randomUUID());

        assertFalse(tracker.wasSuccessful());
    }
}
