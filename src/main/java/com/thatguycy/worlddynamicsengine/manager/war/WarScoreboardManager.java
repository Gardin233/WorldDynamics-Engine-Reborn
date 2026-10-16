package com.thatguycy.worlddynamicsengine.manager.war;

import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitTask;

/** 为参战玩家刷新当前专注战争的侧边计分板，并处理多场战争间的专注切换。 */
public final class WarScoreboardManager {
    private static final String OBJECTIVE_ID = "wde_war_focus";
    private final WorldDynamicsEngine plugin;
    private final Map<UUID, Scoreboard> scoreboards = new LinkedHashMap<>();
    private BukkitTask task;

    public WarScoreboardManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    public void clearAll() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayer(player);
        }
        scoreboards.clear();
    }

    public void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        FocusContext context = resolveContext(player, true);
        if (context == null || context.details() == null) {
            clearPlayer(player);
            return;
        }
        applySidebar(player, context.details());
    }

    public String getFocusedWarKey(Player player) {
        if (player == null) {
            return "";
        }
        return plugin.getResidentManager().getOrCreateResident(player.getUniqueId(), player.getName()).getFocusedWarKey();
    }

    public boolean setFocusedWar(Player player, String focusKey, boolean save) {
        FocusContext context = resolveContext(player, false);
        if (context == null || focusKey == null || focusKey.isBlank()) {
            return false;
        }
        for (OrgManager.WarFocusOption option : context.options()) {
            if (option.focusKey().equalsIgnoreCase(focusKey)) {
                WDEresident resident = context.resident();
                resident.setFocusedWarKey(option.focusKey());
                if (save) {
                    plugin.getResidentManager().saveResidents();
                }
                refreshPlayer(player);
                return true;
            }
        }
        return false;
    }

    public void clearFocusedWar(Player player, boolean save) {
        if (player == null) {
            return;
        }
        WDEresident resident = plugin.getResidentManager().getOrCreateResident(player.getUniqueId(), player.getName());
        resident.setFocusedWarKey("");
        if (save) {
            plugin.getResidentManager().saveResidents();
        }
        refreshPlayer(player);
    }

    public List<OrgManager.WarFocusOption> getAvailableWars(Player player) {
        FocusContext context = resolveContext(player, false);
        return context == null ? List.of() : context.options();
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    private FocusContext resolveContext(Player player, boolean allowAutoFocus) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        var town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            return null;
        }
        String nation = town.getNationOrNull() == null ? "" : town.getNationOrNull().getName();
        String townName = town.getName();
        List<OrgManager.WarFocusOption> options = plugin.getOrgManager().getWarFocusOptions(nation, townName);
        WDEresident resident = plugin.getResidentManager().getOrCreateResident(player.getUniqueId(), player.getName());
        if (options.isEmpty()) {
            if (!resident.getFocusedWarKey().isBlank()) {
                resident.setFocusedWarKey("");
            }
            return new FocusContext(resident, nation, townName, options, null);
        }

        String focused = resident.getFocusedWarKey();
        String savedFocus = focused;
        if (focused.isBlank() || options.stream().noneMatch(option -> option.focusKey().equalsIgnoreCase(savedFocus))) {
            if (!allowAutoFocus) {
                return new FocusContext(resident, nation, townName, options, null);
            }
            focused = options.getFirst().focusKey();
            resident.setFocusedWarKey(focused);
        }

        OrgManager.WarFocusDetails details = plugin.getOrgManager().getWarFocusDetails(focused, nation, townName);
        if (details == null && allowAutoFocus) {
            focused = options.getFirst().focusKey();
            resident.setFocusedWarKey(focused);
            details = plugin.getOrgManager().getWarFocusDetails(focused, nation, townName);
        }
        return new FocusContext(resident, nation, townName, options, details);
    }

    private void applySidebar(Player player, OrgManager.WarFocusDetails details) {
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = scoreboards.computeIfAbsent(player.getUniqueId(), ignored -> manager.getNewScoreboard());
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective != null) {
            objective.unregister();
        }
        objective = scoreboard.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY,
                ChatColor.DARK_BLUE + "战争计分");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = buildLines(details);
        Map<String, Integer> unique = new LinkedHashMap<>();
        int score = lines.size();
        for (String raw : lines) {
            String entry = uniquify(raw, unique);
            objective.getScore(entry).setScore(score--);
        }
        player.setScoreboard(scoreboard);
    }

    private void clearPlayer(Player player) {
        Scoreboard scoreboard = scoreboards.remove(player.getUniqueId());
        if (scoreboard != null && player.getScoreboard() == scoreboard) {
            player.setScoreboard(Bukkit.getScoreboardManager() == null
                    ? scoreboard : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private List<String> buildLines(OrgManager.WarFocusDetails details) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + shortTitle(details.title()));
        lines.add(ChatColor.GRAY + "理由 " + plugin.getLocaleMessage(details.reason().messageKey()));
        lines.add((details.defenderPerspective() ? ChatColor.BLUE : ChatColor.RED)
                + "身份 " + (details.defenderPerspective() ? "防守方" : "进攻方"));
        lines.add(ChatColor.GREEN + "我方 " + decimal(details.friendlyScore()));
        lines.add(ChatColor.RED + "敌方 " + decimal(details.enemyScore()));

        double friendlyOccupation = sum(details.friendlyOccupation());
        double friendlyKills = sum(details.friendlyKills());
        double enemyOccupation = sum(details.enemyOccupation());
        double enemyKills = sum(details.enemyKills());
        lines.add(ChatColor.GREEN + "我占 " + decimal(friendlyOccupation));
        lines.add(ChatColor.GREEN + "我杀 " + decimal(friendlyKills));
        lines.add(ChatColor.RED + "敌占 " + decimal(enemyOccupation));
        lines.add(ChatColor.RED + "敌杀 " + decimal(enemyKills));

        lines.add(ChatColor.YELLOW + "我方国数 " + details.friendlyParticipants().size());
        lines.add(ChatColor.YELLOW + "敌方国数 " + details.enemyParticipants().size());

        String topFriendly = topEntry(details.friendlyOccupation(), "我方占领");
        String topEnemy = topEntry(details.enemyOccupation(), "敌方占领");
        if (!topFriendly.isBlank()) {
            lines.add(ChatColor.AQUA + topFriendly);
        }
        if (!topEnemy.isBlank()) {
            lines.add(ChatColor.LIGHT_PURPLE + topEnemy);
        }

        if (details.independenceWar()) {
            lines.add(ChatColor.GOLD + "周期 " + details.elapsedCycles());
        }
        lines.add(ChatColor.GRAY + elapsed(details.startedAt()));
        lines.add(ChatColor.DARK_GRAY + "/wde war focus");
        return lines.size() > 15 ? lines.subList(0, 15) : lines;
    }

    private String topEntry(Map<String, Double> scores, String prefix) {
        String bestKey = "";
        double bestScore = 0D;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestKey = entry.getKey();
            }
        }
        if (bestKey.isBlank() || bestScore <= 0D) {
            return "";
        }
        return prefix + " " + shortName(bestKey) + " " + decimal(bestScore);
    }

    private String shortTitle(String title) {
        if (title == null || title.isBlank()) {
            return "当前战争";
        }
        return title.length() > 24 ? title.substring(0, 24) : title;
    }

    private String shortName(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.startsWith("town:") ? value.substring(5) : value;
        return normalized.length() > 10 ? normalized.substring(0, 10) : normalized;
    }

    private double sum(Map<String, Double> scores) {
        double total = 0D;
        for (Double value : scores.values()) {
            if (value != null && Double.isFinite(value)) {
                total += value;
            }
        }
        return total;
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String elapsed(long startedAt) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
        Duration duration = Duration.ofSeconds(seconds);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        return hours > 0 ? "已战 " + hours + "h" + minutes + "m" : "已战 " + minutes + "m";
    }

    private String uniquify(String value, Map<String, Integer> seen) {
        int count = seen.getOrDefault(value, 0);
        seen.put(value, count + 1);
        if (count == 0) {
            return value;
        }
        return value + ChatColor.RESET.toString().repeat(count);
    }

    private record FocusContext(WDEresident resident, String nation, String town,
                                List<OrgManager.WarFocusOption> options,
                                OrgManager.WarFocusDetails details) {}
}
