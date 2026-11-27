package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class NetherInvasionCompositionTest {
    @Test
    void initialMobCountScalesWithStrengthAndHonorsCap() {
        assertEquals(1, NetherInvasionComposition.initialMobCount(1, 0.25D, 12));
        assertEquals(5, NetherInvasionComposition.initialMobCount(20, 0.25D, 12));
        assertEquals(12, NetherInvasionComposition.initialMobCount(100, 0.25D, 12));
        assertEquals(0, NetherInvasionComposition.initialMobCount(0, 0.25D, 12));
    }

    @Test
    void strongerInvasionsReceiveFasterReinforcements() {
        long weakInterval = NetherInvasionComposition.reinforcementIntervalTicks(1, 100, 2, 5);
        long strongInterval = NetherInvasionComposition.reinforcementIntervalTicks(100, 100, 2, 5);

        assertTrue(weakInterval > strongInterval);
        assertTrue(weakInterval <= 6000L);
        assertEquals(2400L, strongInterval);
    }

    @Test
    void weightedMobSelectionRejectsNonNetherTypes() {
        EntityType selected = NetherInvasionComposition.chooseMob(
                Map.of(EntityType.BLAZE, 1.0D, EntityType.CREEPER, 100.0D),
                new Random(7L));

        assertEquals(EntityType.BLAZE, selected);
        assertNull(NetherInvasionComposition.chooseMob(Map.of(), new Random()));
    }
}
