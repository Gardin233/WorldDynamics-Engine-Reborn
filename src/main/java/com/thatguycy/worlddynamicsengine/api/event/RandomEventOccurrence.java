package com.thatguycy.worlddynamicsengine.api.event;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Result of starting an event. One-shot events can use the defaults; ongoing events can
 * receive player lifecycle callbacks and clean up when their town becomes empty.
 */
public interface RandomEventOccurrence {
    /** Whether the event remains active after {@link RandomEvent#execute(RandomEventContext)} returns. */
    default boolean isOngoing() {
        return false;
    }

    /** Whether an ongoing event should finish when its town has no online players. */
    default boolean endsWhenTownEmpty() {
        return true;
    }

    /** Called when a player belonging to the event's town dies. */
    default void onPlayerDeath(UUID playerId) {
    }

    /** Called when the set of online players in the event's town changes. */
    default void updateTownPlayers(List<Player> onlinePlayers) {
    }

    /** Return true when the occurrence's own success condition has been met. */
    default boolean wasSuccessful() {
        return false;
    }

    /** Whether the manager should immediately finish this occurrence once it succeeds. */
    default boolean finishesWhenSuccessful() {
        return false;
    }

    /** Called when the town has no online players, or when the occurrence is otherwise stopped. */
    default void finish(boolean successful) {
    }
}
