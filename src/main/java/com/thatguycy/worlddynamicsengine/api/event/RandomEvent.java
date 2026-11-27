package com.thatguycy.worlddynamicsengine.api.event;

/** A third-party or built-in event that can participate in WDE's weighted town-event draw. */
public interface RandomEvent {
    /**
     * Lowercase ID used by {@code generation.event-weights.<id>}.
     * Allowed characters are lowercase letters, digits, underscores, and hyphens.
     */
    String id();

    /** Weight used when no explicit weight exists in events.yml. */
    default double defaultWeight() {
        return 1.0D;
    }

    /** Minimum town wealth required for this event to enter the draw. */
    default double minimumTownWealth() {
        return Double.NEGATIVE_INFINITY;
    }

    /** Whether the event consumes strength points calculated from town wealth. */
    default boolean requiresStrengthPoints() {
        return false;
    }

    /** Multiplies the shared strength points immediately before this event is executed. */
    default double strengthMultiplier() {
        return 1.0D;
    }

    /**
     * Additional eligibility check run after this event is selected.
     * Returning false ends this draw without trying another event.
     */
    default boolean isEligible(RandomEventContext context) {
        return true;
    }

    /**
     * Starts the event. Return null if startup fails; otherwise return a result.
     * This method runs synchronously on the server thread and should remain fast.
     */
    RandomEventOccurrence execute(RandomEventContext context);
}
