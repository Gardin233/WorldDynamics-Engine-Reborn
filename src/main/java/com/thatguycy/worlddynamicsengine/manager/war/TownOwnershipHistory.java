package com.thatguycy.worlddynamicsengine.manager.war;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records continuous ownership intervals without inventing pre-upgrade history.
 */
public final class TownOwnershipHistory {
    private TownOwnershipHistory() {}

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> intervals(WDEtown town) {
        Object raw = town.getOwnershipHistory().get("intervals");
        if (raw instanceof List<?> list) return (List<Map<String, Object>>) list;
        var list = new ArrayList<Map<String, Object>>();
        town.getOwnershipHistory().put("intervals", list);
        return list;
    }

    public static void change(WorldDynamicsEngine plugin, WDEtown town, String owner) {
        owner = owner == null ? "None" : owner;
        var history = town.getOwnershipHistory();
        if (owner.equalsIgnoreCase(String.valueOf(history.get("owner")))) return;
        long now = System.currentTimeMillis();
        String world = plugin.getWarConfig().getString("reasons.reconquest.world", "world");
        var date = plugin.getWarTruceClock().currentDate(world);
        if (history.get("since") instanceof Number since && history.get("owner") instanceof String previous
                && !"None".equalsIgnoreCase(previous)) {
            Map<String, Object> interval = new LinkedHashMap<>();
            interval.put("owner", previous);
            interval.put("realDays", Math.max(0D, (now - since.longValue()) / 86_400_000D));
            if (date != null && world.equals(history.get("world"))
                    && date.calendarSignature().equals(history.get("signature"))
                    && history.get("sinceDay") instanceof Number day) {
                interval.put("seasonDays", Math.max(0L, date.dayNumber() - day.longValue()));
            }
            intervals(town).add(interval);
        }
        history.put("owner", owner);
        history.put("since", now);
        history.remove("sinceDay");
        history.remove("signature");
        history.put("world", world);
        if (date != null) {
            history.put("sinceDay", date.dayNumber());
            history.put("signature", date.calendarSignature());
        }
    }

    public static boolean qualifies(WorldDynamicsEngine plugin, WDEtown town, String formerOwner) {
        double minimum = Math.max(0D, plugin.getWarConfig().getDouble("reasons.reconquest.minimumDays", 7D));
        boolean seasonal = "REALISTIC_SEASONS".equalsIgnoreCase(
                plugin.getWarConfig().getString("reasons.reconquest.clock", "REAL_TIME"));
        return qualifies(town, formerOwner, minimum, seasonal);
    }

    public static boolean qualifies(WDEtown town, String formerOwner, double minimum, boolean seasonal) {
        for (var interval : intervals(town)) {
            if (!formerOwner.equalsIgnoreCase(String.valueOf(interval.get("owner")))) continue;
            Object days = seasonal && interval.get("seasonDays") instanceof Number
                    ? interval.get("seasonDays") : interval.get("realDays");
            if (days instanceof Number number && number.doubleValue() > minimum) return true;
        }
        return false;
    }

    public static void renameNation(WDEtown town, String oldName, String newName) {
        var history = town.getOwnershipHistory();
        if (oldName.equalsIgnoreCase(String.valueOf(history.get("owner")))) history.put("owner", newName);
        for (var interval : intervals(town)) {
            if (oldName.equalsIgnoreCase(String.valueOf(interval.get("owner")))) interval.put("owner", newName);
        }
    }
}
