package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.event.PlayerMoodEvent;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeMoodEffect;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/** Persists doctrine mood changes and evaluates meme effects once per minute. */
public final class MoodManager {
    private final WorldDynamicsEngine plugin;
    private final Set<UUID> dirtyResidents = new HashSet<>();

    public MoodManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::evaluateOnlinePlayers, 20L * 60, 20L * 60);
    }

    public int getMood(UUID residentUuid) {
        Player player = Bukkit.getPlayer(residentUuid);
        if (player != null && player.isOnline()) return getMood(player);
        WDEresident resident = plugin.getResidentManager().getResident(residentUuid);
        return resident == null ? 50 : resident.getMood();
    }

    public int getMood(Player player) {
        if (player == null) return 50;
        if (isMoodExempt(player)) return 50;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        int storedMood = resident == null ? 50 : resident.getMood();
        return clampMood(storedMood + getExpectationModifier(player));
    }

    public boolean isMoodExempt(Player player) {
        return player != null && player.isOp();
    }

    public int getExpectationModifier(Player player) {
        if (player == null || isMoodExempt(player)) return 0;
        var config = plugin.getIdeologyConfig();
        if (config == null) return 0;

        int stepHours = Math.max(1, Math.min(24,
                config.getInt("mood.expectations.newPlayer.stepHours", 2)));
        int beginnerBonus = getNewPlayerExpectationBonus(
                player.getStatistic(Statistic.PLAY_ONE_MINUTE),
                stepHours,
                config.getIntegerList("mood.expectations.newPlayer.moodBonuses"));

        int roleAdjustment = 0;
        Resident townyResident = TownyAPI.getInstance().getResident(player);
        if (townyResident != null && townyResident.hasTown() && townyResident.getTownOrNull() != null) {
            var town = townyResident.getTownOrNull();
            if (town.getMayor() != null && town.getMayor().getUUID().equals(player.getUniqueId())) {
                roleAdjustment += config.getInt("mood.expectations.roles.townMayor", -8);
            }
            var nation = town.getNationOrNull();
            if (nation != null && nation.getKing() != null
                    && nation.getKing().getUUID().equals(player.getUniqueId())) {
                roleAdjustment += config.getInt("mood.expectations.roles.nationKing", -12);
            }
        }
        return clampMoodModifier((long) beginnerBonus + roleAdjustment);
    }

    public static int getNewPlayerExpectationBonus(long playedTicks, int stepHours, List<Integer> moodBonuses) {
        if (playedTicks < 0 || moodBonuses == null || moodBonuses.isEmpty()) return 0;
        long stepTicks = Math.max(1, stepHours) * 72_000L;
        long tier = playedTicks / stepTicks;
        return tier >= moodBonuses.size() ? 0 : moodBonuses.get((int) tier);
    }

    /** Applies attitude-weighted mood changes; instant actions may use their own configured values. */
    public int applyDoctrineOutcomes(Player player, int compliantWeight, int violationWeight, String instantAction) {
        String source = instantAction == null ? "doctrine" : instantAction;
        return applyDoctrineOutcomes(player, compliantWeight, violationWeight, instantAction, source);
    }

    public int applyDoctrineOutcomes(Player player, int compliantWeight, int violationWeight,
                                     String instantAction, String source) {
        if (player == null || isMoodExempt(player) || !plugin.religionEnabled
                || compliantWeight + violationWeight <= 0) {
            return getMood(player);
        }
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return 50;

        var config = plugin.getIdeologyConfig();
        int compliantChange = config == null ? 1 : Math.max(0, config.getInt("mood.changes.compliant", 1));
        int violationChange = config == null ? 1 : Math.max(0, config.getInt("mood.changes.violation", 1));
        if (config != null && instantAction != null) {
            String path = "mood.instantActions." + instantAction + ".";
            compliantChange = Math.max(0, config.getInt(path + "compliant", compliantChange));
            violationChange = Math.max(0, config.getInt(path + "violation", violationChange));
        }
        long delta = (long) Math.max(0, compliantWeight) * compliantChange
                - (long) Math.max(0, violationWeight) * violationChange;
        int boundedDelta = (int) Math.max(-100L, Math.min(100L, delta));
        int durationMinutes = config == null ? 30
                : Math.max(1, Math.min(1440, config.getInt("mood.decayMinutes", 30)));
        long now = System.currentTimeMillis();
        int previous = resident.getMoodAt(now);
        resident.addMoodChange(source, boundedDelta, now + durationMinutes * 60_000L);
        if (resident.getMood() != previous) dirtyResidents.add(resident.getUserUUID());
        return getMood(player);
    }

    public int applyDoctrineMoodChanges(Player player, int compliantMoodChange, int violationMoodChange,
                                        String source) {
        if (player == null || isMoodExempt(player) || !plugin.religionEnabled
                || (compliantMoodChange <= 0 && violationMoodChange <= 0)) {
            return getMood(player);
        }
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return 50;

        long delta = (long) Math.max(0, compliantMoodChange) - Math.max(0, violationMoodChange);
        int boundedDelta = (int) Math.max(-100L, Math.min(100L, delta));
        if (boundedDelta == 0) return getMood(player);
        var config = plugin.getIdeologyConfig();
        int durationMinutes = config == null ? 30
                : Math.max(1, Math.min(1440, config.getInt("mood.decayMinutes", 30)));
        long now = System.currentTimeMillis();
        int previous = resident.getMoodAt(now);
        resident.addMoodChange(source, boundedDelta, now + durationMinutes * 60_000L);
        if (resident.getMood() != previous) dirtyResidents.add(resident.getUserUUID());
        return getMood(player);
    }

    public int applyDoctrineOutcome(Player player, String doctrineId, String attitudeId, boolean compliant) {
        ReligionManager religions = plugin.getReligionManager();
        int weight = religions.getDoctrineMoodWeight(doctrineId, attitudeId);
        String source = new DoctrineMoodEntry(doctrineId, attitudeId, compliant, 0).source();
        int compliantDelta = compliant
                ? weight * religions.getDoctrineMoodChangePerWeight(doctrineId, attitudeId, true, null) : 0;
        int violationDelta = compliant ? 0
                : weight * religions.getDoctrineMoodChangePerWeight(doctrineId, attitudeId, false, null);
        return applyDoctrineMoodChanges(player, compliantDelta, violationDelta, source);
    }

    private void evaluateOnlinePlayers() {
        if (!plugin.religionEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isMoodExempt(player)) continue;
            WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
            WDEreligion religion = resident == null
                    ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
            if (religion == null) continue;

            if (resident.refreshMood(System.currentTimeMillis())) {
                dirtyResidents.add(resident.getUserUUID());
            }
            int mood = getMood(player);
            List<IdeologyMemeDefinition> memes = new ArrayList<>();
            Set<String> moodEffects = new LinkedHashSet<>();
            for (String memeId : plugin.getReligionManager().getConfiguredMemes(religion.getMemes())) {
                IdeologyMemeDefinition meme = plugin.getReligionManager().getMeme(memeId);
                if (meme == null) continue;
                memes.add(meme);
                for (IdeologyMemeMoodEffect effect : meme.getMoodEffects()) {
                    if (effect.matches(mood)) moodEffects.addAll(effect.effectIds());
                }
            }

            double chance = getOutcomeChancePercent(mood);
            PlayerMoodEvent.Outcome outcome = mood < 50
                    ? PlayerMoodEvent.Outcome.COLLAPSE
                    : PlayerMoodEvent.Outcome.INSPIRATION;
            callMoodEvent(player, mood, PlayerMoodEvent.Outcome.MOOD_CHECK, chance, memes, moodEffects);
            if (chance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                Set<String> outcomeEffects = new LinkedHashSet<>(moodEffects);
                String outcomeKey = outcome == PlayerMoodEvent.Outcome.COLLAPSE ? "collapse" : "inspiration";
                for (IdeologyMemeDefinition meme : memes) {
                    outcomeEffects.addAll(meme.getOutcomeEffects(outcomeKey));
                }
                callMoodEvent(player, mood, outcome, chance, memes, outcomeEffects);
            }
        }
        flushDirtyResidents();
    }

    private void flushDirtyResidents() {
        for (UUID uuid : new HashSet<>(dirtyResidents)) {
            WDEresident resident = plugin.getResidentManager().getResident(uuid);
            if (resident == null) {
                dirtyResidents.remove(uuid);
                continue;
            }
            try {
                plugin.getResidentManager().saveResident(resident);
                dirtyResidents.remove(uuid);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to persist mood for resident " + uuid + ": "
                        + exception.getMessage());
            }
        }
    }

    public static double getOutcomeChancePercent(int mood) {
        int boundedMood = Math.max(0, Math.min(100, mood));
        return Math.min(100.0, Math.abs(boundedMood - 50) * 2.0);
    }

    private static int clampMood(int mood) {
        return Math.max(0, Math.min(100, mood));
    }

    private static int clampMoodModifier(long modifier) {
        return (int) Math.max(-100L, Math.min(100L, modifier));
    }

    private void callMoodEvent(Player player, int mood, PlayerMoodEvent.Outcome outcome, double chance,
                               List<IdeologyMemeDefinition> memes, Set<String> effects) {
        Bukkit.getPluginManager().callEvent(new PlayerMoodEvent(player, mood, outcome, chance,
                memes, List.copyOf(effects)));
    }
}
