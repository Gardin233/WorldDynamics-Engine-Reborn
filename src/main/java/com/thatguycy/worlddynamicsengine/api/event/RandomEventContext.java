package com.thatguycy.worlddynamicsengine.api.event;

import com.palmergames.bukkit.towny.object.Town;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Immutable information available while a random event is being selected and executed. */
public record RandomEventContext(
        UUID eventId,
        Player target,
        Town town,
        double townWealth,
        int onlinePopulation,
        int population,
        List<Player> townPlayers,
        int strengthPoints) {

    public RandomEventContext {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(town, "town");
        townPlayers = List.copyOf(townPlayers);
    }

    public String townName() {
        return town.getName();
    }

    public RandomEventContext withStrengthPoints(int updatedStrengthPoints) {
        return new RandomEventContext(eventId, target, town, townWealth, onlinePopulation,
                population, townPlayers, updatedStrengthPoints);
    }
}
