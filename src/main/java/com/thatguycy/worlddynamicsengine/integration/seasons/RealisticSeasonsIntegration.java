package com.thatguycy.worlddynamicsengine.integration.seasons;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

/**
 * 封装可选季节插件的反射接口，把指定世界日历转换为可比较的日期数据。
 */
public final class RealisticSeasonsIntegration {
    private final Plugin dependency;
    private final Object api;
    private final Method getDate;
    private final Method getMonthName;
    private final List<String> monthNames;
    private final List<Integer> monthLengths;

    private RealisticSeasonsIntegration(Plugin dependency, Object api, Method getDate, Method getMonthName,
                                        List<String> monthNames, List<Integer> monthLengths) {
        this.dependency = dependency;
        this.api = api;
        this.getDate = getDate;
        this.getMonthName = getMonthName;
        this.monthNames = monthNames;
        this.monthLengths = monthLengths;
    }

    public static RealisticSeasonsIntegration load(Plugin dependency) throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("me.casperge.realisticseasons.api.SeasonsAPI", true,
                dependency.getClass().getClassLoader());
        Object api = apiClass.getMethod("getInstance").invoke(null);
        if (api == null) throw new IllegalStateException("RealisticSeasons API is not initialized.");
        var calendar = YamlConfiguration.loadConfiguration(new File(dependency.getDataFolder(), "calendar.yml"));
        var months = calendar.getConfigurationSection("months");
        if (!calendar.getBoolean("enabled", false) || months == null) {
            throw new IllegalStateException("RealisticSeasons calendar is disabled or missing.");
        }
        List<String> names = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        for (String name : months.getKeys(false)) {
            int days = months.getInt(name + ".days", 0);
            if (days <= 0) throw new IllegalStateException("Invalid calendar month length: " + name);
            names.add(name);
            lengths.add(days);
        }
        if (names.isEmpty()) throw new IllegalStateException("RealisticSeasons calendar has no months.");
        return new RealisticSeasonsIntegration(dependency, api, apiClass.getMethod("getDate", World.class),
                apiClass.getMethod("getCurrentMonthName", World.class), List.copyOf(names), List.copyOf(lengths));
    }

    public CalendarDate getDate(World world) throws ReflectiveOperationException {
        if (!dependency.isEnabled()) return null;
        Object date = getDate.invoke(api, world);
        if (date == null) return null;
        int day = readNumber(date, "Day", "day");
        int year = readNumber(date, "Year", "year");
        String monthName = String.valueOf(getMonthName.invoke(api, world));
        int month = monthNames.indexOf(monthName);
        if (month < 0) throw new IllegalStateException("Unknown calendar month: " + monthName);
        long ordinal = toDayNumber(year, month + 1, day, monthLengths);
        return new CalendarDate(ordinal, year + "/" + (month + 1) + "/" + day,
                String.join("|", monthNames) + ":" +
                        monthLengths.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
    }

    private static int readNumber(Object date, String getterSuffix, String field) throws ReflectiveOperationException {
        try {
            return ((Number) date.getClass().getMethod("get" + getterSuffix).invoke(date)).intValue();
        } catch (NoSuchMethodException ex) {
            return ((Number) date.getClass().getField(field).get(date)).intValue();
        }
    }

    public static long toDayNumber(int year, int month, int day, List<Integer> lengths) {
        if (lengths.isEmpty() || month < 1 || month > lengths.size()
                || day < 1 || day > lengths.get(month - 1)) {
            throw new IllegalArgumentException("Invalid RealisticSeasons date.");
        }
        long yearLength = 0;
        for (int length : lengths) {
            if (length <= 0) throw new IllegalArgumentException("Invalid calendar month length.");
            yearLength = Math.addExact(yearLength, length);
        }
        long ordinal = Math.multiplyExact((long) year, yearLength);
        for (int i = 0; i < month - 1; i++) ordinal = Math.addExact(ordinal, lengths.get(i));
        return Math.addExact(ordinal, day - 1L);
    }

    /**
     * 保存日历日序、展示日期和日历签名，用于检测日期到期及日历结构变化。
     */
    public record CalendarDate(long dayNumber, String display, String calendarSignature) {
    }
}
