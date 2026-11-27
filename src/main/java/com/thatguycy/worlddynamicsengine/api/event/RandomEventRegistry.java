package com.thatguycy.worlddynamicsengine.api.event;

import java.util.List;
import org.bukkit.plugin.Plugin;

/** Registration service for built-in and third-party random event definitions. */
public interface RandomEventRegistry {
    /**
     * Registers an event owned by {@code owner}. Registration is main-thread only.
     *
     * @return true when the ID is valid and was not already registered
     */
    boolean register(Plugin owner, RandomEvent event);

    /**
     * Unregisters an event only when {@code owner} is its original registrant.
     * Any ongoing occurrences owned by the event are stopped without success.
     */
    boolean unregister(Plugin owner, String eventId);

    /** Returns an immutable snapshot of all current built-in and external event IDs. */
    List<String> registeredEventIds();
}
