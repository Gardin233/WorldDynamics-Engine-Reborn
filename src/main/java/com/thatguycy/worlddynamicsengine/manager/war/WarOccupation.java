package com.thatguycy.worlddynamicsengine.manager.war;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提供纯占领权重和人数比例算法，统一首都、单城国家及非首都人口分配规则。
 */
public final class WarOccupation {
    private WarOccupation() {}
    public static Map<String, Double> weights(Map<String, Integer> population, String capital) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (population.isEmpty()) return result;
        if (population.size() == 1) {
            result.put(population.keySet().iterator().next(), 50D);
            return result;
        }
        String seat = population.containsKey(capital) ? capital : population.keySet().iterator().next();
        long total = population.entrySet().stream().filter(e -> !e.getKey().equals(seat))
                .mapToLong(e -> Math.max(1, e.getValue())).sum();
        population.forEach((town, count) -> result.put(town,
                town.equals(seat) ? 25D : 25D * Math.max(1, count) / total));
        return result;
    }
    public static double attackerShare(int attackers, int defenders) {
        long total = (long) Math.max(0, attackers) + Math.max(0, defenders);
        return total == 0 ? 0D : Math.max(0, attackers) / (double) total;
    }
}
