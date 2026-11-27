package com.thatguycy.worlddynamicsengine.manager.event;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks whether every player involved in an event has died at least once. */
final class RaidSuccessTracker {
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> participantsWhoDied = new HashSet<>();

    void addParticipant(UUID playerId) {
        if (playerId != null) {
            participants.add(playerId);
        }
    }

    void recordDeath(UUID playerId) {
        if (participants.contains(playerId)) {
            participantsWhoDied.add(playerId);
        }
    }

    boolean wasSuccessful() {
        return !participants.isEmpty() && participantsWhoDied.containsAll(participants);
    }
}
