package com.thatguycy.worlddynamicsengine.manager.event;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;

final class AnimalEventComposition {
    private static final List<EntityType> DOMESTIC_ANIMALS = List.of(
            EntityType.CAMEL,
            EntityType.CHICKEN,
            EntityType.COW,
            EntityType.DONKEY,
            EntityType.GOAT,
            EntityType.HORSE,
            EntityType.LLAMA,
            EntityType.MOOSHROOM,
            EntityType.MULE,
            EntityType.PANDA,
            EntityType.PIG,
            EntityType.RABBIT,
            EntityType.SHEEP,
            EntityType.TRADER_LLAMA);

    private AnimalEventComposition() {
    }

    static boolean isDomesticAnimal(EntityType type) {
        return DOMESTIC_ANIMALS.contains(type);
    }

    static EntityType chooseAnimal(Map<EntityType, Double> weights, Random random) {
        if (weights == null || weights.isEmpty()) {
            return null;
        }
        List<Map.Entry<EntityType, Double>> validWeights = weights.entrySet().stream()
                .filter(entry -> isDomesticAnimal(entry.getKey())
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

    static int countByStrength(int strengthPoints, double animalsPerStrengthPoint,
                               int minimum, int maximum) {
        if (strengthPoints <= 0 || !Double.isFinite(animalsPerStrengthPoint)
                || animalsPerStrengthPoint <= 0.0D) {
            return 0;
        }
        int minCount = Math.max(1, minimum);
        int maxCount = Math.max(minCount, maximum);
        double requested = Math.ceil(strengthPoints * animalsPerStrengthPoint);
        return Math.max(minCount, (int) Math.min(maxCount, requested));
    }

    static int countByStrength(int strengthPoints, int minimum, int maximum) {
        return countByStrength(strengthPoints, 1.0D, minimum, maximum);
    }

    static int randomCount(int minimum, int maximum, Random random) {
        int minCount = Math.max(2, minimum);
        int maxCount = Math.max(minCount, maximum);
        return minCount + random.nextInt(maxCount - minCount + 1);
    }
}
