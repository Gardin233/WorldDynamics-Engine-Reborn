package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * Resident profile with personal faith, timed mood changes, backgrounds and war preferences.
 */
@SerializableAs("WDEresident")
public class WDEresident implements ConfigurationSerializable {
    private static final long DEFAULT_MOOD_DURATION_MILLIS = 30L * 60L * 1000L;
    public static final String DEHUMANIZED_FLAG = "wde:dehumanized";

    private UUID userUUID;
    private String username;
    private String religionName;
    private UUID churchUuid;
    private long churchChangedAt;
    private boolean religionChosen;
    private String childhoodBackgroundId;
    private String adulthoodBackgroundId;
    private String focusedWarKey;
    private int mood;
    private final List<MoodChange> moodChanges = new ArrayList<>();
    private final Map<String, Integer> persistentMoodChanges = new LinkedHashMap<>();
    private String activeCollapseId;
    private long activeCollapseExpiresAt;
    private Set<String> appliedBackgroundBonusIds;
    private Set<String> flags;

    private record MoodChange(String source, int delta, long expiresAt) {}

    public record MoodChangeEntry(String source, int delta, long expiresAt, boolean permanent) {
        public MoodChangeEntry(String source, int delta, long expiresAt) {
            this(source, delta, expiresAt, false);
        }
    }

    public WDEresident(UUID userUUID, String username) {
        this.userUUID = userUUID;
        this.username = username;
        this.religionName = "None";
        this.churchUuid = null;
        this.churchChangedAt = 0L;
        this.religionChosen = false;
        this.childhoodBackgroundId = "";
        this.adulthoodBackgroundId = "";
        this.focusedWarKey = "";
        this.mood = 50;
        this.activeCollapseId = "";
        this.activeCollapseExpiresAt = 0L;
        this.appliedBackgroundBonusIds = new HashSet<>();
        this.flags = new HashSet<>();
    }

    // Constructor used for deserialization
    public WDEresident(Map<String, Object> map) {
        this.userUUID = UUID.fromString(String.valueOf(map.get("userUUID")));
        this.username = String.valueOf(map.getOrDefault("username", "Unknown"));
        this.religionName = String.valueOf(map.getOrDefault("religionName", "None"));
        this.flags = readStringSet(map.get("flags"));
        String churchUuidValue = normalizeStringValue(map.get("churchUuid"));
        this.churchUuid = churchUuidValue.isBlank() ? null : UUID.fromString(churchUuidValue);
        Object rawChurchChangedAt = map.get("churchChangedAt");
        this.churchChangedAt = rawChurchChangedAt instanceof Number number ? number.longValue() : 0L;
        Object rawReligionChosen = map.get("religionChosen");
        this.religionChosen = rawReligionChosen instanceof Boolean
                ? (Boolean) rawReligionChosen
                : !isNoneReligion(this.religionName);
        this.childhoodBackgroundId = normalizeStringValue(map.get("childhoodBackgroundId"));
        this.adulthoodBackgroundId = normalizeStringValue(map.get("adulthoodBackgroundId"));
        this.focusedWarKey = normalizeStringValue(map.get("focusedWarKey"));
        Object rawMood = map.get("mood");
        this.mood = clampMood(rawMood instanceof Number number ? number.intValue() : 50);
        Object rawMoodChanges = map.get("moodChanges");
        if (rawMoodChanges instanceof Collection<?> values) {
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> change)) continue;
                Object rawDelta = change.get("delta");
                Object rawExpiresAt = change.get("expiresAt");
                if (!(rawDelta instanceof Number delta) || !(rawExpiresAt instanceof Number expiresAt)) continue;
                String source = normalizeStringValue(change.get("source"));
                if (delta.intValue() != 0) {
                    moodChanges.add(new MoodChange(source.isBlank() ? "temporary" : source,
                            delta.intValue(), expiresAt.longValue()));
                }
            }
        } else if (rawMood instanceof Number number && number.intValue() != 50) {
            moodChanges.add(new MoodChange("legacy", number.intValue() - 50,
                    System.currentTimeMillis() + DEFAULT_MOOD_DURATION_MILLIS));
        }
        Object rawPersistentMoodChanges = map.get("persistentMoodChanges");
        if (rawPersistentMoodChanges instanceof Map<?, ?> changes) {
            for (Map.Entry<?, ?> entry : changes.entrySet()) {
                String source = normalizeStringValue(entry.getKey());
                if (!(entry.getValue() instanceof Number delta) || source.isBlank() || delta.intValue() == 0) {
                    continue;
                }
                persistentMoodChanges.put(source, clampMoodDelta(delta.intValue()));
            }
        }
        if (isDehumanized()) {
            moodChanges.clear();
            persistentMoodChanges.clear();
            mood = 50;
        }
        refreshMood(System.currentTimeMillis());
        this.activeCollapseId = normalizeStringValue(map.get("activeCollapseId"));
        Object rawCollapseExpiresAt = map.get("activeCollapseExpiresAt");
        this.activeCollapseExpiresAt = rawCollapseExpiresAt instanceof Number number ? number.longValue() : 0L;
        if (this.activeCollapseId.isBlank()) {
            clearActiveCollapse();
        }
        this.appliedBackgroundBonusIds = readStringSet(map.get("appliedBackgroundBonusIds"));
    }

    // Method for serialization
    @Override
    public Map<String, Object> serialize() {
        refreshMood(System.currentTimeMillis());
        Map<String, Object> map = new HashMap<>();
        map.put("userUUID", userUUID.toString());
        map.put("username", username);
        map.put("religionName", religionName);
        map.put("churchUuid", churchUuid == null ? "" : churchUuid.toString());
        map.put("churchChangedAt", churchChangedAt);
        map.put("religionChosen", religionChosen);
        map.put("childhoodBackgroundId", childhoodBackgroundId);
        map.put("adulthoodBackgroundId", adulthoodBackgroundId);
        map.put("focusedWarKey", focusedWarKey);
        map.put("mood", mood);
        List<Map<String, Object>> serializedMoodChanges = new ArrayList<>();
        for (MoodChange change : moodChanges) {
            serializedMoodChanges.add(Map.of("source", change.source(), "delta", change.delta(),
                    "expiresAt", change.expiresAt()));
        }
        map.put("moodChanges", serializedMoodChanges);
        map.put("persistentMoodChanges", new LinkedHashMap<>(persistentMoodChanges));
        map.put("activeCollapseId", activeCollapseId);
        map.put("activeCollapseExpiresAt", activeCollapseExpiresAt);
        map.put("appliedBackgroundBonusIds", appliedBackgroundBonusIds);
        map.put("flags", flags);
        return map;
    }


    // Getters and Setters
    public UUID getUserUUID() {
        return userUUID;
    }

    public void setUserUUID(UUID userUUID) {
        this.userUUID = userUUID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getFlags() {
        return flags;
    }

    public void setFlags(Set<String> flags) {
        this.flags = flags;
    }

    public String getReligionName() {
        return religionName;
    }

    public void setReligionName(String religionName) {
        if (this.religionName == null || religionName == null
                || !this.religionName.equalsIgnoreCase(religionName)) {
            churchUuid = null;
        }
        this.religionName = religionName;
    }

    public UUID getChurchUuid() {
        return churchUuid;
    }

    public void setChurchUuid(UUID churchUuid, long changedAt) {
        this.churchUuid = churchUuid;
        this.churchChangedAt = changedAt;
    }

    public long getChurchChangedAt() {
        return churchChangedAt;
    }

    public boolean isReligionChosen() {
        return religionChosen;
    }

    public void setReligionChosen(boolean religionChosen) {
        this.religionChosen = religionChosen;
    }

    public String getChildhoodBackgroundId() {
        return childhoodBackgroundId;
    }

    public void setChildhoodBackgroundId(String childhoodBackgroundId) {
        this.childhoodBackgroundId = normalizeStringValue(childhoodBackgroundId);
    }

    public String getAdulthoodBackgroundId() {
        return adulthoodBackgroundId;
    }

    public void setAdulthoodBackgroundId(String adulthoodBackgroundId) {
        this.adulthoodBackgroundId = normalizeStringValue(adulthoodBackgroundId);
    }

    public Set<String> getAppliedBackgroundBonusIds() {
        return appliedBackgroundBonusIds;
    }

    public void setAppliedBackgroundBonusIds(Set<String> appliedBackgroundBonusIds) {
        this.appliedBackgroundBonusIds = appliedBackgroundBonusIds == null
                ? new HashSet<>()
                : new HashSet<>(appliedBackgroundBonusIds);
    }

    public String getFocusedWarKey() {
        return focusedWarKey;
    }

    public void setFocusedWarKey(String focusedWarKey) {
        this.focusedWarKey = normalizeStringValue(focusedWarKey);
    }

    public int getMood() {
        return getMoodAt(System.currentTimeMillis());
    }

    public void setMood(int mood) {
        if (isDehumanized()) {
            moodChanges.clear();
            persistentMoodChanges.clear();
            this.mood = 50;
            return;
        }
        this.mood = clampMood(mood);
        moodChanges.clear();
        int delta = this.mood - 50;
        if (delta != 0) {
            moodChanges.add(new MoodChange("manual", delta,
                    System.currentTimeMillis() + DEFAULT_MOOD_DURATION_MILLIS));
        }
    }

    /** Appends a temporary mood delta; equal-source changes stack and expire independently. */
    public int addMoodChange(int delta, long expiresAt) {
        return addMoodChange("temporary", delta, expiresAt);
    }

    public int addMoodChange(String source, int delta, long expiresAt) {
        if (isDehumanized()) return 50;
        long now = System.currentTimeMillis();
        refreshMood(now);
        int boundedDelta = Math.max(-100, Math.min(100, delta));
        if (boundedDelta != 0 && expiresAt > now) {
            String normalizedSource = normalizeStringValue(source);
            moodChanges.add(new MoodChange(normalizedSource.isBlank() ? "temporary" : normalizedSource,
                    boundedDelta, expiresAt));
        }
        mood = calculateMood(now);
        return mood;
    }

    /** Replaces all persistent changes from one source family with its current value. */
    public boolean replacePersistentMoodChanges(String sourcePrefix, String source, int delta) {
        if (isDehumanized()) return false;
        String normalizedPrefix = normalizeStringValue(sourcePrefix);
        if (normalizedPrefix.isBlank()) return false;
        String normalizedSource = normalizeStringValue(source);
        int boundedDelta = clampMoodDelta(delta);
        if (!normalizedSource.startsWith(normalizedPrefix) || boundedDelta == 0) normalizedSource = "";
        String legacyPrefix = normalizedPrefix.startsWith("doctrine|")
                && normalizedPrefix.endsWith("|") && normalizedPrefix.length() > "doctrine||".length()
                ? "doctrine:" + normalizedPrefix.substring("doctrine|".length(), normalizedPrefix.length() - 1) + ":"
                : "";

        Map<String, Integer> previous = new LinkedHashMap<>();
        persistentMoodChanges.forEach((key, value) -> {
            if (key.startsWith(normalizedPrefix)) previous.put(key, value);
        });
        boolean removedLegacyTimedChanges = moodChanges.removeIf(change ->
                change.source().startsWith(normalizedPrefix)
                        || (!legacyPrefix.isBlank() && change.source().startsWith(legacyPrefix)));
        persistentMoodChanges.keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        if (!normalizedSource.isBlank()) persistentMoodChanges.put(normalizedSource, boundedDelta);

        Map<String, Integer> current = new LinkedHashMap<>();
        persistentMoodChanges.forEach((key, value) -> {
            if (key.startsWith(normalizedPrefix)) current.put(key, value);
        });
        if (previous.equals(current) && !removedLegacyTimedChanges) return false;
        mood = calculateMood(System.currentTimeMillis());
        return true;
    }

    /** Removes expired deltas and recalculates mood from the neutral baseline. */
    public boolean refreshMood(long now) {
        int previous = mood;
        if (isDehumanized()) {
            boolean changed = !moodChanges.isEmpty() || !persistentMoodChanges.isEmpty() || mood != 50;
            moodChanges.clear();
            persistentMoodChanges.clear();
            mood = 50;
            return changed;
        }
        boolean removed = moodChanges.removeIf(change -> change.expiresAt() <= now);
        mood = calculateMood(now);
        return removed || mood != previous;
    }

    public int getMoodAt(long now) {
        return calculateMood(now);
    }

    public List<MoodChangeEntry> getActiveMoodChanges(long now) {
        if (isDehumanized()) return List.of();
        List<MoodChangeEntry> changes = new ArrayList<>(moodChanges.stream()
                .filter(change -> change.expiresAt() > now)
                .map(change -> new MoodChangeEntry(change.source(), change.delta(), change.expiresAt()))
                .toList());
        persistentMoodChanges.forEach((source, delta) ->
                changes.add(new MoodChangeEntry(source, delta, Long.MAX_VALUE, true)));
        return List.copyOf(changes);
    }

    public String getActiveCollapseId() {
        return activeCollapseId;
    }

    public long getActiveCollapseExpiresAt() {
        return activeCollapseExpiresAt;
    }

    public boolean hasActiveCollapse(long now) {
        return !activeCollapseId.isBlank() && activeCollapseExpiresAt > now;
    }

    public void setActiveCollapse(String collapseId, long expiresAt) {
        this.activeCollapseId = normalizeStringValue(collapseId);
        this.activeCollapseExpiresAt = this.activeCollapseId.isBlank() ? 0L : Math.max(0L, expiresAt);
    }

    public void clearActiveCollapse() {
        this.activeCollapseId = "";
        this.activeCollapseExpiresAt = 0L;
    }

    private int calculateMood(long now) {
        if (isDehumanized()) return 50;
        long total = 50L;
        for (MoodChange change : moodChanges) {
            if (change.expiresAt() > now) total += change.delta();
        }
        for (int delta : persistentMoodChanges.values()) total += delta;
        return clampMood(total);
    }

    private static int clampMoodDelta(int delta) {
        return Math.max(-100, Math.min(100, delta));
    }

    public boolean hasSelectedChildhoodBackground() {
        return !childhoodBackgroundId.isBlank();
    }

    public boolean hasSelectedAdulthoodBackground() {
        return !adulthoodBackgroundId.isBlank();
    }

    public boolean hasAppliedBackgroundBonus(String backgroundId) {
        return backgroundId != null && appliedBackgroundBonusIds.contains(backgroundId.toLowerCase());
    }

    public void markBackgroundBonusApplied(String backgroundId) {
        if (backgroundId != null && !backgroundId.isBlank()) {
            appliedBackgroundBonusIds.add(backgroundId.toLowerCase());
        }
    }

    // Helper methods
    public void addFlag(String flag) {
        flags.add(flag);
    }

    public void removeFlag(String flag) {
        flags.remove(flag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public boolean isDehumanized() {
        return hasFlag(DEHUMANIZED_FLAG);
    }

    public void setDehumanized(boolean dehumanized) {
        if (dehumanized) {
            flags.add(DEHUMANIZED_FLAG);
            moodChanges.clear();
            persistentMoodChanges.clear();
            mood = 50;
        } else {
            flags.remove(DEHUMANIZED_FLAG);
            mood = calculateMood(System.currentTimeMillis());
        }
    }

    public static WDEresident deserialize(Map<String, Object> map) {
        return new WDEresident(map);
    }

    private Set<String> readStringSet(Object rawValue) {
        Set<String> values = new HashSet<>();
        if (rawValue instanceof Collection<?>) {
            for (Object entry : (Collection<?>) rawValue) {
                if (entry != null) {
                    values.add(String.valueOf(entry));
                }
            }
        }
        return values;
    }

    private boolean isNoneReligion(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value);
    }

    private String normalizeStringValue(Object rawValue) {
        return rawValue == null ? "" : String.valueOf(rawValue).trim();
    }

    private static int clampMood(int mood) {
        return Math.max(0, Math.min(100, mood));
    }

    private static int clampMood(long mood) {
        return (int) Math.max(0L, Math.min(100L, mood));
    }
}
