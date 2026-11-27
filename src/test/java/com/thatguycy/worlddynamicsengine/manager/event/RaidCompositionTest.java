package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class RaidCompositionTest {
    @Test
    void lineupNeverSpendsMorePointsThanAvailable() {
        Map<EntityType, Integer> costs = new EnumMap<>(EntityType.class);
        costs.put(EntityType.PILLAGER, 1);
        costs.put(EntityType.VINDICATOR, 2);
        costs.put(EntityType.WITCH, 4);
        costs.put(EntityType.EVOKER, 8);
        costs.put(EntityType.RAVAGER, 10);

        var lineup = RaidComposition.create(11, costs, 40, new Random(7));
        int spent = lineup.stream().mapToInt(costs::get).sum();

        assertTrue(!lineup.isEmpty());
        assertTrue(spent <= 11);
        assertTrue(lineup.stream().allMatch(RaidComposition::isRaidType));
    }

    @Test
    void respectsMaximumMobCountAndUnavailableBudget() {
        Map<EntityType, Integer> costs = Map.of(EntityType.PILLAGER, 1);

        assertEquals(2, RaidComposition.create(4, costs, 2, new Random(1)).size());
        assertTrue(RaidComposition.create(0, costs, 2, new Random(1)).isEmpty());
    }
}
