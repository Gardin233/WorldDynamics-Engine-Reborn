package com.thatguycy.worlddynamicsengine.manager.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;

/** Spends a raid's strength-point budget on configured illager types. */
public final class RaidComposition {
    private RaidComposition() {
    }

    public static List<EntityType> create(int strengthPoints, Map<EntityType, Integer> costs,
                                          int maximumMobs, Random random) {
        if (strengthPoints <= 0 || maximumMobs <= 0 || costs == null || costs.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<EntityType, Integer>> validCosts = costs.entrySet().stream()
                .filter(entry -> isRaidType(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0)
                .toList();
        if (validCosts.isEmpty()) {
            return List.of();
        }

        List<EntityType> lineup = new ArrayList<>();
        int remainingPoints = strengthPoints;
        while (lineup.size() < maximumMobs) {
            int budget = remainingPoints;
            List<Map.Entry<EntityType, Integer>> affordable = validCosts.stream()
                    .filter(entry -> entry.getValue() <= budget)
                    .toList();
            if (affordable.isEmpty()) {
                break;
            }
            Map.Entry<EntityType, Integer> selected = affordable.get(random.nextInt(affordable.size()));
            lineup.add(selected.getKey());
            remainingPoints -= selected.getValue();
        }
        return List.copyOf(lineup);
    }

    public static boolean isRaidType(EntityType type) {
        return type == EntityType.PILLAGER
                || type == EntityType.VINDICATOR
                || type == EntityType.EVOKER
                || type == EntityType.ILLUSIONER
                || type == EntityType.WITCH
                || type == EntityType.RAVAGER;
    }
}
