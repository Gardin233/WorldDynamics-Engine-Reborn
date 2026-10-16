package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 按来源保存非负等级并重新求和，不依赖玩家当前药水等级；限时来源独立到期。
 * 等级 0 表示没有贡献，I 级为 1；只在应用原版效果时转换为 amplifier。
 */
public final class DoctrineBuffCalculator<K> {
    public static final int MAX_LEVEL = 256;
    public static final long PERMANENT = Long.MAX_VALUE;
    private final Map<String, Source<K>> sources = new LinkedHashMap<>();

    /** 保存某一原版效果的汇总等级及最晚到期时间。 */
    public record Total(int level, long endTick) {}

    /** 同一来源中的效果共享有效期，替换来源时整组更新。 */
    private record Source<K>(Map<K, Integer> levels, long endTick) {}

    public void replace(String source, Map<K, Integer> levels, long endTick) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Missing buff source");
        Objects.requireNonNull(levels, "levels");
        Map<K, Integer> checked = new LinkedHashMap<>();
        levels.forEach((type, level) -> {
            Objects.requireNonNull(type, "effect");
            if (level == null || level < 0) throw new IllegalArgumentException("Negative buff level");
            if (level > 0) checked.put(type, Math.min(MAX_LEVEL, level));
        });
        if (checked.isEmpty()) sources.remove(source);
        else sources.put(source, new Source<>(Map.copyOf(checked), endTick));
    }

    /** 增减单个来源的贡献，最终等级下限为 0，不修改其他来源或延长已有有效期。 */
    public void adjust(String source, K type, int delta, long now) {
        Objects.requireNonNull(type, "effect");
        totals(now);
        Source<K> existing = sources.get(source);
        Map<K, Integer> levels = new LinkedHashMap<>(existing == null ? Map.of() : existing.levels());
        long level = (long) levels.getOrDefault(type, 0) + delta;
        levels.put(type, (int) Math.max(0, Math.min(MAX_LEVEL, level)));
        replace(source, levels, existing == null ? PERMANENT : existing.endTick());
    }

    public void remove(String source) {
        sources.remove(source);
    }

    public Map<K, Total> totals(long now) {
        sources.values().removeIf(source -> source.endTick() != PERMANENT && source.endTick() <= now);
        Map<K, Total> totals = new LinkedHashMap<>();
        sources.values().forEach(source -> source.levels().forEach((type, level) -> {
            Total previous = totals.get(type);
            totals.put(type, previous == null ? new Total(level, source.endTick())
                    : new Total(Math.min(MAX_LEVEL, previous.level() + level),
                            Math.max(previous.endTick(), source.endTick())));
        }));
        return Map.copyOf(totals);
    }
}
