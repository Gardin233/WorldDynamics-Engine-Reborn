package com.thatguycy.worlddynamicsengine.manager.event;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;

final class NetherInvasionComposition {
    private static final List<EntityType> NETHER_HOSTILES = List.of(
            EntityType.BLAZE,
            EntityType.GHAST,
            EntityType.HOGLIN,
            EntityType.MAGMA_CUBE,
            EntityType.PIGLIN_BRUTE,
            EntityType.WITHER_SKELETON,
            EntityType.ZOGLIN);

    private NetherInvasionComposition() {
    }

    static boolean isNetherHostile(EntityType type) {
        return NETHER_HOSTILES.contains(type);
    }

    static EntityType chooseMob(Map<EntityType, Double> weights, Random random) {
        if (weights == null || weights.isEmpty()) {
            return null;
        }
        List<Map.Entry<EntityType, Double>> validWeights = weights.entrySet().stream()
                .filter(entry -> isNetherHostile(entry.getKey())
                        && entry.getValue() != null
                        && Double.isFinite(entry.getValue())
                        && entry.getValue() > 0.0D)
                .toList();
        double totalWeight = validWeights.stream().mapToDouble(Map.Entry::getValue).sum();
        if (validWeights.isEmpty() || !Double.isFinite(totalWeight) || totalWeight <= 0.0D) {
            return null;
        }

        double selection = random.nextDouble() * totalWeight;
        for (Map.Entry<EntityType, Double> entry : validWeights) {
            selection -= entry.getValue();
            if (selection < 0.0D) {
                return entry.getKey();
            }
        }
        return validWeights.get(validWeights.size() - 1).getKey();
    }

    static int initialMobCount(int strengthPoints, double mobsPerStrengthPoint, int maximumMobs) {
        if (strengthPoints <= 0 || maximumMobs <= 0
                || !Double.isFinite(mobsPerStrengthPoint) || mobsPerStrengthPoint <= 0.0D) {
            return 0;
        }
        double requested = Math.ceil(strengthPoints * mobsPerStrengthPoint);
        return Math.max(1, (int) Math.min(maximumMobs, requested));
    }

    static long reinforcementIntervalTicks(int strengthPoints, int strengthReferencePoints,
                                           int minimumMinutes, int maximumMinutes) {
        int fastest = Math.max(1, minimumMinutes);
        int slowest = Math.max(fastest, maximumMinutes);
        double strengthRatio = Math.min(1.0D,
                Math.max(0, strengthPoints) / (double) Math.max(1, strengthReferencePoints));
        double intervalMinutes = slowest - ((slowest - fastest) * strengthRatio);
        return Math.max(20L, Math.round(intervalMinutes * 1200.0D));
    }

}
