package com.thatguycy.worlddynamicsengine.manager.war;

import com.thatguycy.worlddynamicsengine.integration.seasons.RealisticSeasonsIntegration;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 生成并判定现实时间或季节日历停战记录，日历不可用时使用已保存的现实时间备用截止点。
 */
public final class WarTruceClock {
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;
    private final WorldDynamicsEngine plugin;
    private RealisticSeasonsIntegration seasons;
    private long lastWarningAt;

    public WarTruceClock(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        seasons = null;
        lastWarningAt = 0L;
        var dependency = plugin.getServer().getPluginManager().getPlugin("RealisticSeasons");
        if (dependency == null || !dependency.isEnabled()) {
            if (enabled()) warn("RealisticSeasons is unavailable; new truces use real time.");
            return;
        }
        try {
            seasons = RealisticSeasonsIntegration.load(dependency);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (enabled()) warn("Cannot connect to RealisticSeasons calendar: " + ex.getMessage());
        }
    }

    public boolean enabled() {
        return config().getBoolean("truce.realisticSeasons.enabled", false);
    }

    public String configuredWorld() {
        return config().getString("truce.realisticSeasons.world", "world");
    }

    private FileConfiguration config() {
        return plugin.getWarConfig();
    }

    public RealisticSeasonsIntegration.CalendarDate currentDate(String worldName) {
        if (seasons == null) return null;
        var world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("RealisticSeasons truce world is not loaded: " + worldName);
            return null;
        }
        try {
            return seasons.getDate(world);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warn("Cannot read RealisticSeasons date for " + worldName + ": " + ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> createDeadline(double scoreRatio) {
        double ratio = Math.max(0D, Math.min(1D, scoreRatio));
        Map<String, Object> deadline = new LinkedHashMap<>();
        long baseHours = Math.max(0L, config().getLong("truce.baseHours", 24L));
        long extraHours = Math.max(0L, config().getLong("truce.maxAdditionalHours", 168L));
        long duration = Math.addExact(Math.multiplyExact(baseHours, HOUR_MILLIS),
                Math.round(Math.multiplyExact(extraHours, HOUR_MILLIS) * ratio));
        deadline.put("endAt", Math.addExact(System.currentTimeMillis(), duration));
        if (enabled()) {
            String world = configuredWorld();
            var date = currentDate(world);
            if (date != null) {
                long days = calendarDurationDays(config().getLong("truce.realisticSeasons.baseDays", 1L),
                        config().getLong("truce.realisticSeasons.maxAdditionalDays", 7L), ratio);
                deadline.put("clock", "REALISTIC_SEASONS");
                deadline.put("world", world);
                deadline.put("calendarSignature", date.calendarSignature());
                deadline.put("endDay", Math.addExact(date.dayNumber(), days));
                return deadline;
            }
            warn("RealisticSeasons date is unavailable; new truces use real time.");
        }
        deadline.put("clock", "REAL_TIME");
        return deadline;
    }

    public static long calendarDurationDays(long baseDays, long additionalDays, double ratio) {
        return Math.addExact(Math.max(0L, baseDays),
                (long) Math.ceil(Math.max(0L, additionalDays) * Math.max(0D, Math.min(1D, ratio))));
    }

    public boolean isActive(Map<String, Object> entry) {
        return estimatedEndAt(entry) > System.currentTimeMillis();
    }

    // Calendar time cannot be expressed as a real deadline. This compatibility projection is
    // used only by the old timestamp accessor; active/expired status follows the stored clock.
    public long estimatedEndAt(Map<String, Object> entry) {
        if ("REALISTIC_SEASONS".equals(entry.get("clock"))) {
            var date = currentDate(String.valueOf(entry.getOrDefault("world", "")));
            if (date != null && date.calendarSignature().equals(entry.get("calendarSignature"))
                    && entry.get("endDay") instanceof Number endDay) {
                long remaining = endDay.longValue() - date.dayNumber();
                if (remaining <= 0L) return 0L;
                long now = System.currentTimeMillis();
                if (remaining > (Long.MAX_VALUE - now) / DAY_MILLIS) return Long.MAX_VALUE;
                return now + remaining * DAY_MILLIS;
            }
            warn("Saved calendar truce cannot read its original calendar; using its real-time fallback.");
        }
        return entry.get("endAt") instanceof Number endAt ? endAt.longValue() : 0L;
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (lastWarningAt == 0L || now - lastWarningAt >= 60_000L) {
            plugin.getLogger().warning(message);
            lastWarningAt = now;
        }
    }
}
