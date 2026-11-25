package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.event.PlayerMoodEvent;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeMoodEffect;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/** Persists doctrine mood changes and evaluates timed mood outcomes for online players. */
public final class MoodManager {
    public record ExpectationBreakdown(boolean exempt, int newPlayerTier, int newPlayerBonus,
                                       int configuredTownMayorAdjustment, int townMayorAdjustment,
                                       boolean townMayor, int configuredNationKingAdjustment,
                                       int nationKingAdjustment, boolean nationKing,
                                       int totalAdjustment) {}

    private final WorldDynamicsEngine plugin;
    private final Set<UUID> dirtyResidents = new HashSet<>();
    private final Map<UUID, List<WDEresident.MoodChangeEntry>> exemptMoodPreviews = new HashMap<>();
    private long lastExpectationConfigModified;
    private long lastExpectationConfigLength;
    private long pendingExpectationConfigModified = -1L;
    private long pendingExpectationConfigLength = -1L;

    public MoodManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::evaluateOnlinePlayers, 20L * 60, 20L * 60);
        File ideologyFile = new File(plugin.getDataFolder(), "ideology.yml");
        lastExpectationConfigModified = ideologyFile.lastModified();
        lastExpectationConfigLength = ideologyFile.length();
        Bukkit.getScheduler().runTaskTimer(plugin, this::watchExpectationConfig, 20L, 20L);
    }

    public void evaluateOnlinePlayersNow() {
        evaluateOnlinePlayers();
    }

    private void watchExpectationConfig() {
        File ideologyFile = new File(plugin.getDataFolder(), "ideology.yml");
        if (!ideologyFile.isFile()) return;

        long modified = ideologyFile.lastModified();
        long length = ideologyFile.length();
        if (modified == lastExpectationConfigModified && length == lastExpectationConfigLength) {
            pendingExpectationConfigModified = -1L;
            pendingExpectationConfigLength = -1L;
            return;
        }
        if (modified != pendingExpectationConfigModified || length != pendingExpectationConfigLength) {
            pendingExpectationConfigModified = modified;
            pendingExpectationConfigLength = length;
            return;
        }

        lastExpectationConfigModified = modified;
        lastExpectationConfigLength = length;
        pendingExpectationConfigModified = -1L;
        pendingExpectationConfigLength = -1L;
        if (plugin.reloadMoodExpectationSettingsFromDisk()) {
            evaluateOnlinePlayers();
        }
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
        if (player == null) return false;
        if (player.isOp() || player.getGameMode() == GameMode.CREATIVE) return true;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        return resident != null && resident.isDehumanized();
    }

    private boolean shouldRecordExemptMoodPreview(Player player) {
        if (player == null || (!player.isOp() && player.getGameMode() != GameMode.CREATIVE)) return false;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        return resident == null || !resident.isDehumanized();
    }

    /** Allows exempt test modes to calculate display-only outcomes but keeps dehumanized players excluded. */
    public boolean shouldProcessMoodAction(Player player) {
        return player != null && (!isMoodExempt(player) || shouldRecordExemptMoodPreview(player));
    }

    /** Returns transient debug entries for exempt players without reading them into mood calculations. */
    public List<WDEresident.MoodChangeEntry> getExemptMoodPreviews(Player player, long now) {
        if (!shouldRecordExemptMoodPreview(player)) return List.of();
        pruneExemptMoodPreviews(now);
        return List.copyOf(exemptMoodPreviews.getOrDefault(player.getUniqueId(), List.of()));
    }

    public void pruneExemptMoodPreviews() {
        pruneExemptMoodPreviews(System.currentTimeMillis());
    }

    private void addExemptMoodPreview(Player player, String source, int delta, long expiresAt) {
        if (!shouldRecordExemptMoodPreview(player) || source == null || source.isBlank() || delta == 0) return;
        pruneExemptMoodPreviews(System.currentTimeMillis());
        exemptMoodPreviews.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>())
                .add(new WDEresident.MoodChangeEntry(source, delta, expiresAt, false));
    }

    private void replaceExemptPersistentPreview(UUID residentUuid, String sourcePrefix,
                                                String source, int delta) {
        List<WDEresident.MoodChangeEntry> entries =
                exemptMoodPreviews.computeIfAbsent(residentUuid, ignored -> new ArrayList<>());
        entries.removeIf(entry -> entry.permanent() && entry.source().startsWith(sourcePrefix));
        if (source != null && !source.isBlank() && delta != 0) {
            entries.add(new WDEresident.MoodChangeEntry(source, delta, Long.MAX_VALUE, true));
        }
        if (entries.isEmpty()) exemptMoodPreviews.remove(residentUuid);
    }

    private void clearExemptPersistentPreview(UUID residentUuid, String sourcePrefix) {
        List<WDEresident.MoodChangeEntry> entries = exemptMoodPreviews.get(residentUuid);
        if (entries == null) return;
        entries.removeIf(entry -> entry.permanent() && entry.source().startsWith(sourcePrefix));
        if (entries.isEmpty()) exemptMoodPreviews.remove(residentUuid);
    }

    private void pruneExemptMoodPreviews(long now) {
        var iterator = exemptMoodPreviews.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            entry.getValue().removeIf(change -> !change.permanent() && change.expiresAt() <= now);
            if (player == null || !player.isOnline() || !shouldRecordExemptMoodPreview(player)
                    || entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public int getExpectationModifier(Player player) {
        return getExpectationBreakdown(player).totalAdjustment();
    }

    public ExpectationBreakdown getExpectationBreakdown(Player player) {
        if (player == null) {
            return new ExpectationBreakdown(true, -1, 0, 0, 0, false, 0, 0, false, 0);
        }
        var config = plugin.getIdeologyConfig();
        if (config == null) {
            return new ExpectationBreakdown(false, -1, 0, 0, 0, false, 0, 0, false, 0);
        }

        boolean exempt = isMoodExempt(player);
        int configuredMayorAdjustment = config.getInt("mood.expectations.roles.townMayor", -8);
        int configuredKingAdjustment = config.getInt("mood.expectations.roles.nationKing", -12);
        int stepHours = Math.max(1, Math.min(24,
                config.getInt("mood.expectations.newPlayer.stepHours", 2)));
        List<Integer> moodBonuses = config.getIntegerList("mood.expectations.newPlayer.moodBonuses");
        long playedTicks = exempt ? -1L : player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int tier = getNewPlayerExpectationTier(playedTicks, stepHours, moodBonuses.size());
        int beginnerBonus = exempt ? 0 : getNewPlayerExpectationBonus(playedTicks, stepHours, moodBonuses);

        boolean townMayor = false;
        boolean nationKing = false;
        Resident townyResident = TownyAPI.getInstance().getResident(player);
        if (!exempt && townyResident != null && townyResident.hasTown() && townyResident.getTownOrNull() != null) {
            var town = townyResident.getTownOrNull();
            townMayor = town.getMayor() != null && town.getMayor().getUUID().equals(player.getUniqueId());
            var nation = town.getNationOrNull();
            nationKing = nation != null && nation.getKing() != null
                    && nation.getKing().getUUID().equals(player.getUniqueId());
        }
        int mayorAdjustment = !exempt && townMayor && !nationKing ? configuredMayorAdjustment : 0;
        int kingAdjustment = !exempt && nationKing ? configuredKingAdjustment : 0;
        int roleAdjustment = exempt ? 0 : selectRoleExpectationAdjustment(
                townMayor, configuredMayorAdjustment, nationKing, configuredKingAdjustment);
        int total = exempt ? 0 : clampMoodModifier(
                (long) beginnerBonus + roleAdjustment);
        return new ExpectationBreakdown(exempt, tier, beginnerBonus,
                configuredMayorAdjustment, mayorAdjustment, townMayor,
                configuredKingAdjustment, kingAdjustment, nationKing, total);
    }

    static int selectRoleExpectationAdjustment(boolean townMayor, int townMayorAdjustment,
                                               boolean nationKing, int nationKingAdjustment) {
        if (nationKing) return nationKingAdjustment;
        return townMayor ? townMayorAdjustment : 0;
    }

    private static int getNewPlayerExpectationTier(long playedTicks, int stepHours, int tierCount) {
        if (playedTicks < 0L || tierCount <= 0) return -1;
        long stepTicks = Math.max(1, stepHours) * 72_000L;
        return (int) Math.min(tierCount - 1, playedTicks / stepTicks);
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
        if (player == null || !plugin.religionEnabled
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
        if (isMoodExempt(player)) {
            if (boundedDelta != 0) {
                addExemptMoodPreview(player, source, boundedDelta, now + durationMinutes * 60_000L);
            }
            return getMood(player);
        }
        int previous = resident.getMoodAt(now);
        resident.addMoodChange(source, boundedDelta, now + durationMinutes * 60_000L);
        if (resident.getMood() != previous) dirtyResidents.add(resident.getUserUUID());
        return getMood(player);
    }

    public int applyDoctrineMoodChanges(Player player, int compliantMoodChange, int violationMoodChange,
                                        String source) {
        if (player == null || !plugin.religionEnabled
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
        if (isMoodExempt(player)) {
            addExemptMoodPreview(player, source, boundedDelta, now + durationMinutes * 60_000L);
            return getMood(player);
        }
        int previous = resident.getMoodAt(now);
        resident.addMoodChange(source, boundedDelta, now + durationMinutes * 60_000L);
        if (resident.getMood() != previous) dirtyResidents.add(resident.getUserUUID());
        return getMood(player);
    }

    /** Applies a temporary mood delta to an online or offline resident profile. */
    public boolean applyTemporaryMoodChange(UUID residentUuid, String source, int delta) {
        if (delta == 0) return false;
        var config = plugin.getIdeologyConfig();
        int durationMinutes = config == null ? 30
                : Math.max(1, Math.min(1440, config.getInt("mood.decayMinutes", 30)));
        return applyTemporaryMoodChange(residentUuid, source, delta, durationMinutes * 60_000L);
    }

    /** Clears timed changes and exempt previews without disturbing persistent condition-based modifiers. */
    public void clearTemporaryMoodChanges(UUID residentUuid) {
        if (residentUuid == null) return;
        WDEresident resident = plugin.getResidentManager().getResident(residentUuid);
        if (resident != null && resident.clearTemporaryMoodChanges()) {
            plugin.getResidentManager().saveResident(resident);
        }
        List<WDEresident.MoodChangeEntry> previews = exemptMoodPreviews.get(residentUuid);
        if (previews != null) {
            previews.removeIf(change -> !change.permanent());
            if (previews.isEmpty()) exemptMoodPreviews.remove(residentUuid);
        }
    }

    /** Applies a temporary mood delta with a caller-defined duration. */
    public boolean applyTemporaryMoodChange(UUID residentUuid, String source, int delta, long durationMillis) {
        if (residentUuid == null || delta == 0 || !plugin.religionEnabled) return false;
        WDEresident resident = plugin.getResidentManager().getResident(residentUuid);
        if (resident == null || resident.isDehumanized()) return false;
        Player player = Bukkit.getPlayer(residentUuid);
        if (player != null ? isMoodExempt(player) : Bukkit.getOfflinePlayer(residentUuid).isOp()) {
            if (shouldRecordExemptMoodPreview(player)) {
                long now = System.currentTimeMillis();
                int boundedDelta = Math.max(-100, Math.min(100, delta));
                addExemptMoodPreview(player, source, boundedDelta,
                        now + Math.max(1L, Math.min(durationMillis, 3650L * 24 * 60 * 60 * 1000)));
                return true;
            }
            return false;
        }

        long now = System.currentTimeMillis();
        resident.addMoodChange(source, Math.max(-100, Math.min(100, delta)),
                now + Math.max(1L, Math.min(durationMillis, 3650L * 24 * 60 * 60 * 1000)));
        plugin.getResidentManager().saveResident(resident);
        return true;
    }

    /** Replaces one persistent mood state on an online or offline resident. */
    public boolean replacePersistentMoodModifier(UUID residentUuid, String sourcePrefix,
                                                 String source, int delta) {
        if (residentUuid == null) return false;
        WDEresident resident = plugin.getResidentManager().getResident(residentUuid);
        if (resident == null) return false;
        Player player = Bukkit.getPlayer(residentUuid);
        boolean exempt = player != null ? isMoodExempt(player) : Bukkit.getOfflinePlayer(residentUuid).isOp();
        if (exempt && shouldRecordExemptMoodPreview(player)) {
            replaceExemptPersistentPreview(residentUuid, sourcePrefix,
                    plugin.religionEnabled && !resident.isDehumanized() ? source : null,
                    plugin.religionEnabled && !resident.isDehumanized() ? delta : 0);
        } else {
            clearExemptPersistentPreview(residentUuid, sourcePrefix);
        }
        String appliedSource = plugin.religionEnabled && !exempt && !resident.isDehumanized()
                ? source : null;
        int appliedDelta = appliedSource == null ? 0 : delta;
        if (!resident.replacePersistentMoodChanges(sourcePrefix, appliedSource, appliedDelta)) return false;
        plugin.getResidentManager().saveResident(resident);
        return true;
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

    /** Replaces a doctrine's persistent state-based mood modifier, or clears it when state is null. */
    public int setPersistentDoctrineOutcome(Player player, String doctrineId, String attitudeId,
                                            Boolean compliant) {
        if (player == null) return 50;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return getMood(player);

        String sourcePrefix = "doctrine|" + doctrineId + "|";
        String source = null;
        int delta = 0;
        if (compliant != null && plugin.religionEnabled && attitudeId != null) {
            ReligionManager religions = plugin.getReligionManager();
            int weight = religions.getDoctrineMoodWeight(doctrineId, attitudeId);
            int perWeight = religions.getDoctrineMoodChangePerWeight(doctrineId, attitudeId, compliant, null);
            long magnitude = (long) weight * Math.max(0, perWeight);
            int boundedMagnitude = (int) Math.min(100L, magnitude);
            delta = compliant ? boundedMagnitude : -boundedMagnitude;
            source = new DoctrineMoodEntry(doctrineId, attitudeId, compliant, delta).source();
        }

        if (isMoodExempt(player)) {
            if (shouldRecordExemptMoodPreview(player)) {
                replaceExemptPersistentPreview(player.getUniqueId(), sourcePrefix, source, delta);
            } else {
                clearExemptPersistentPreview(player.getUniqueId(), sourcePrefix);
            }
            source = null;
            delta = 0;
        } else {
            clearExemptPersistentPreview(player.getUniqueId(), sourcePrefix);
        }

        if (resident.replacePersistentMoodChanges(sourcePrefix, source, delta)) {
            dirtyResidents.add(resident.getUserUUID());
            flushDirtyResidents();
        }
        return getMood(player);
    }

    private void evaluateOnlinePlayers() {
        pruneExemptMoodPreviews();
        if (!plugin.religionEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isMoodExempt(player)) continue;
            WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
            WDEreligion religion = resident == null
                    ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
            if (religion == null) continue;

            long now = System.currentTimeMillis();
            if (resident.refreshMood(now)) {
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

            var config = plugin.getIdeologyConfig();
            int inspirationIntervalMinutes = config == null ? 2
                    : Math.max(1, Math.min(1440, config.getInt("mood.inspiration.intervalMinutes", 2)));
            boolean inspirationDue = resident.advanceInspirationCheckIfDue(
                    now, inspirationIntervalMinutes * 60_000L);
            if (inspirationDue) dirtyResidents.add(resident.getUserUUID());

            InspirationManager inspirations = plugin.getInspirationManager();
            String inspirationEffect = mood > 50 && inspirationDue && inspirations != null
                    ? inspirations.chooseRandomEffect(player) : null;
            double chance = mood < 50 ? getOutcomeChancePercent(mood)
                    : inspirationEffect == null ? 0.0 : 100.0;
            PlayerMoodEvent.Outcome outcome = mood < 50
                    ? PlayerMoodEvent.Outcome.COLLAPSE : PlayerMoodEvent.Outcome.INSPIRATION;
            callMoodEvent(player, mood, PlayerMoodEvent.Outcome.MOOD_CHECK, chance, memes, moodEffects);
            if (mood < 50 && chance > 0.0
                    && java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                Set<String> outcomeEffects = new LinkedHashSet<>(moodEffects);
                for (IdeologyMemeDefinition meme : memes) {
                    outcomeEffects.addAll(meme.getOutcomeEffects("collapse"));
                }
                callMoodEvent(player, mood, outcome, chance, memes, outcomeEffects);
            } else if (inspirationEffect != null && inspirations.grant(player, inspirationEffect)) {
                Set<String> outcomeEffects = new LinkedHashSet<>(moodEffects);
                for (IdeologyMemeDefinition meme : memes) {
                    outcomeEffects.addAll(meme.getOutcomeEffects("inspiration"));
                }
                outcomeEffects.add(inspirationEffect);
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
        return boundedMood >= 50 ? 0.0 : Math.min(100.0, (50 - boundedMood) * 2.0);
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
