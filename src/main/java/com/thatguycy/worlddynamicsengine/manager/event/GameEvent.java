package com.thatguycy.worlddynamicsengine.manager.event;

import com.thatguycy.worlddynamicsengine.api.event.RandomEvent;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventOccurrence;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import java.util.UUID;

/** An event implementation executes only after EventManager selects it. */
interface GameEvent extends RandomEvent {
    @Override
    EventOccurrence execute(RandomEventContext context);
}

/** Only attack events impose a minimum town-wealth eligibility threshold. */
interface AttackGameEvent extends GameEvent {
    @Override
    default boolean requiresStrengthPoints() {
        return true;
    }
}

interface EventOccurrence extends RandomEventOccurrence {
    String eventTypeId();

    UUID ownerId();

    UUID eventId();

    String townName();

    default void onForcedClose() {
    }

    default boolean endsWhenTownEmpty() {
        return true;
    }

    default boolean onBlockBreak(BlockBreakEvent event) {
        return false;
    }

    default boolean shouldCancelBlockPlace(BlockPlaceEvent event) {
        return false;
    }

    default boolean shouldCancelPortal(EntityPortalEvent event) {
        return false;
    }
}
