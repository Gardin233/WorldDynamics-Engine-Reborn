package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.event.IdeologyDoctrineEffectRegistryEvent;
import com.thatguycy.worlddynamicsengine.event.IdeologyDoctrineRegistryEvent;
import com.thatguycy.worlddynamicsengine.event.IdeologyMemeRegistryEvent;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeEffect;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineMemeConstraint;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 维护意识形态归属、模因与戒律注册、定义迁移和修改冷却；注册定义不等同于实现效果。
 */
public class ReligionManager {
    private static final String STORAGE_CATEGORY = "religions";
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final String DOCTRINE_ID_PATTERN = "^[a-z0-9_.-]+:(?:wde|wder)/[a-z0-9_.-]+$";
    private static final String ATTITUDE_ID_PATTERN = "^[a-z0-9_.-]+:(?:wde|wder)/[a-z0-9_.-]+/[a-z0-9_.-]+$";
    private static final String LOCAL_ATTITUDE_ID_PATTERN = "^[a-z0-9_.-]+$";
    private static final Map<String, String> LEGACY_MEME_NAMES = createLegacyMemeNames();

    private final JavaPlugin plugin;
    private final DataStorageManager dataStorageManager;
    private final Map<String, WDEreligion> religions;
    private final Map<String, IdeologyMemeDefinition> registeredMemes;
    private final Map<String, IdeologyDoctrineDefinition> registeredDoctrines;
    private final Map<String, List<IdeologyDoctrineAttitudeEffect>> doctrineEffectsByAttitude;

    public ReligionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataStorageManager = ((WorldDynamicsEngine) plugin).getDataStorageManager();
        this.religions = new LinkedHashMap<>();
        this.registeredMemes = new LinkedHashMap<>();
        this.registeredDoctrines = new LinkedHashMap<>();
        this.doctrineEffectsByAttitude = new LinkedHashMap<>();
        reloadMemeRegistry();
        reloadDoctrineRegistry();
        loadReligions();
    }

    public synchronized void loadReligions() {
        religions.clear();
        religions.putAll(dataStorageManager.loadStringKeyed(STORAGE_CATEGORY, WDEreligion.class));
        boolean migrated = false;
        for (WDEreligion religion : religions.values()) {
            migrated |= reconcileDoctrineAttitudes(religion);
        }
        if (migrated) {
            dataStorageManager.saveStringKeyed(STORAGE_CATEGORY, religions);
        }
    }

    public synchronized void saveReligions() {
        for (WDEreligion religion : religions.values()) {
            reconcileDoctrineAttitudes(religion);
        }
        dataStorageManager.saveStringKeyed(STORAGE_CATEGORY, religions);
    }

    public boolean religionExists(String religionName) {
        return religionName != null && religions.containsKey(religionName.toLowerCase());
    }

    public synchronized WDEreligion getReligion(String religionName) {
        if (religionName == null) {
            return null;
        }
        return religions.get(religionName.toLowerCase());
    }

    public synchronized void addReligion(WDEreligion religion) {
        if (isReligionLeader(religion.getPontiffUuid())) {
            throw new IllegalArgumentException("An existing ideology leader cannot create another ideology.");
        }
        FileConfiguration config = ((WorldDynamicsEngine) plugin).getIdeologyConfig();
        try {
            religion.setTithePolicy(WDEreligion.TitheType.valueOf(
                    config.getString("economy.treasury.defaultTitheType", "WEALTH_PERCENTAGE").toUpperCase(Locale.ROOT)),
                    new BigDecimal(config.getString("economy.treasury.defaultTitheAmount", "10")));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid default ideology tithe policy; new ideology starts with zero tithe.");
        }
        initializeDoctrineAttitudes(religion);
        religions.put(religion.getReligionName().toLowerCase(), religion);
        saveReligions();
    }

    public synchronized boolean isReligionLeader(UUID playerUuid) {
        return playerUuid != null && religions.values().stream()
                .anyMatch(religion -> playerUuid.equals(religion.getPontiffUuid()));
    }

    public synchronized void removeReligion(WDEreligion religion) {
        religions.remove(religion.getReligionName().toLowerCase(), religion);
        saveReligions();
    }

    public synchronized void saveReligion(WDEreligion religion) {
        if (religion == null) {
            return;
        }
        reconcileDoctrineAttitudes(religion);
        religions.put(religion.getReligionName().toLowerCase(), religion);
        saveReligions();
    }

    public boolean isMemeModificationBlocked(WDEreligion religion) {
        return religion != null && isCooldownActive(religion.getMemeCooldown());
    }

    public boolean isDoctrineModificationBlocked(WDEreligion religion, String doctrineId) {
        return religion != null && doctrineId != null && isCooldownActive(religion.getDoctrineCooldown(doctrineId));
    }

    public String describeMemeModificationCooldown(String locale, WDEreligion religion) {
        return religion == null ? "" : describeCooldown(locale, religion.getMemeCooldown());
    }

    public String describeDoctrineModificationCooldown(String locale, WDEreligion religion, String doctrineId) {
        return religion == null || doctrineId == null ? "" : describeCooldown(locale, religion.getDoctrineCooldown(doctrineId));
    }

    public void markMemeModified(WDEreligion religion) {
        if (religion != null) {
            religion.setMemeCooldown(createCooldownEntry("cooldowns.memes"));
        }
    }

    public void markDoctrineModified(WDEreligion religion, String doctrineId) {
        if (religion != null && doctrineId != null) {
            religion.setDoctrineCooldown(doctrineId, createCooldownEntry("cooldowns.doctrines"));
        }
    }

    public synchronized BigDecimal creditTreasury(String name, BigDecimal amount) {
        WDEreligion current = getReligion(name);
        if (current == null) throw new IllegalArgumentException("Ideology not found.");
        WDEreligion updated = WDEreligion.deserialize(current.serialize());
        updated.creditTreasury(amount);
        persistTreasuryUpdate(updated);
        return updated.getTreasuryBalance();
    }

    public synchronized boolean configureTithe(String name, UUID actor, WDEreligion.TitheType type, BigDecimal amount) {
        WDEreligion current = getReligion(name);
        if (current == null || actor == null || !actor.equals(current.getPontiffUuid())) return false;
        WDEreligion updated = WDEreligion.deserialize(current.serialize());
        updated.setTithePolicy(type, amount);
        persistTreasuryUpdate(updated);
        return true;
    }

    private void persistTreasuryUpdate(WDEreligion updated) {
        // Publish only after the database commit; failed credits never alter the live balance.
        String key = updated.getReligionName().toLowerCase();
        dataStorageManager.saveEntity(STORAGE_CATEGORY, key, updated);
        religions.put(key, updated);
    }

    private Map<String, Object> createCooldownEntry(String path) {
        FileConfiguration config = ((WorldDynamicsEngine) plugin).getIdeologyConfig();
        long days = Math.max(0L, config.getLong(path + ".days", 0L));
        if (days <= 0L) {
            return Collections.emptyMap();
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("endAt", Math.addExact(System.currentTimeMillis(), Math.multiplyExact(days, DAY_MILLIS)));

        String mode = config.getString("cooldowns.mode", "REAL_TIME");
        if (!"REALISTIC_SEASONS".equalsIgnoreCase(mode)) {
            entry.put("clock", "REAL_TIME");
            return entry;
        }

        WorldDynamicsEngine engine = (WorldDynamicsEngine) plugin;
        String world = config.getString("cooldowns.world", "world");
        var date = engine.getWarTruceClock() == null ? null : engine.getWarTruceClock().currentDate(world);
        if (date == null) {
            entry.put("clock", "REAL_TIME");
            return entry;
        }

        entry.put("clock", "REALISTIC_SEASONS");
        entry.put("world", world);
        entry.put("calendarSignature", date.calendarSignature());
        entry.put("endDay", Math.addExact(date.dayNumber(), days));
        return entry;
    }

    private boolean isCooldownActive(Map<String, Object> entry) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        WorldDynamicsEngine engine = (WorldDynamicsEngine) plugin;
        return engine.getWarTruceClock() != null
                ? engine.getWarTruceClock().isActive(entry)
                : entry.get("endAt") instanceof Number endAt && endAt.longValue() > System.currentTimeMillis();
    }

    private String describeCooldown(String locale, Map<String, Object> entry) {
        if (!isCooldownActive(entry)) {
            return "";
        }

        WorldDynamicsEngine engine = (WorldDynamicsEngine) plugin;
        if ("REALISTIC_SEASONS".equals(entry.get("clock")) && engine.getWarTruceClock() != null) {
            String world = String.valueOf(entry.getOrDefault("world", ""));
            var date = engine.getWarTruceClock().currentDate(world);
            if (date != null && date.calendarSignature().equals(entry.get("calendarSignature"))
                    && entry.get("endDay") instanceof Number endDay) {
                long remainingDays = Math.max(1L, endDay.longValue() - date.dayNumber());
                return engine.formatLocaleMessage(locale, "religion_modification_cooldown_realisticseasons", remainingDays);
            }
        }

        long endAt = entry.get("endAt") instanceof Number number ? number.longValue() : System.currentTimeMillis();
        long remainingDays = Math.max(1L, (long) Math.ceil(Math.max(0L, endAt - System.currentTimeMillis()) / (double) DAY_MILLIS));
        return engine.formatLocaleMessage(locale, "religion_modification_cooldown_real", remainingDays);
    }

    public List<String> getAllReligionNames() {
        List<String> names = new ArrayList<>();
        for (WDEreligion religion : religions.values()) {
            names.add(religion.getReligionName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public List<String> getValidMemes() {
        List<String> names = new ArrayList<>();
        for (IdeologyMemeDefinition meme : registeredMemes.values()) {
            names.add(meme.getTitle());
        }
        return names;
    }

    public void reloadMemeRegistry() {
        registeredMemes.clear();
        loadConfiguredMemes();

        IdeologyMemeRegistryEvent event = new IdeologyMemeRegistryEvent((WorldDynamicsEngine) plugin, registeredMemes);
        Bukkit.getPluginManager().callEvent(event);

        registeredMemes.clear();
        for (IdeologyMemeDefinition meme : event.getRegisteredMemes()) {
            registeredMemes.put(meme.getId(), meme);
        }
    }

    public void reloadDoctrineRegistry() {
        registeredDoctrines.clear();
        doctrineEffectsByAttitude.clear();
        loadConfiguredDoctrines();

        IdeologyDoctrineRegistryEvent doctrineEvent = new IdeologyDoctrineRegistryEvent((WorldDynamicsEngine) plugin, registeredDoctrines);
        Bukkit.getPluginManager().callEvent(doctrineEvent);

        registeredDoctrines.clear();
        for (IdeologyDoctrineDefinition doctrine : doctrineEvent.getRegisteredDoctrines()) {
            registeredDoctrines.put(doctrine.getId(), doctrine);
        }

        IdeologyDoctrineEffectRegistryEvent effectEvent = new IdeologyDoctrineEffectRegistryEvent((WorldDynamicsEngine) plugin);
        Bukkit.getPluginManager().callEvent(effectEvent);
        for (IdeologyDoctrineAttitudeEffect effect : effectEvent.getRegisteredEffects()) {
            registerDoctrineEffect(effect);
        }

        for (WDEreligion religion : religions.values()) {
            reconcileDoctrineAttitudes(religion);
        }
    }

    public Collection<IdeologyMemeDefinition> getRegisteredMemes() {
        return Collections.unmodifiableCollection(registeredMemes.values());
    }

    public Collection<IdeologyDoctrineDefinition> getRegisteredDoctrines() {
        return Collections.unmodifiableCollection(registeredDoctrines.values());
    }

    /** Returns only saved attitudes whose doctrine and attitude remain registered. */
    public Map<String, String> getConfiguredDoctrineAttitudes(WDEreligion religion) {
        if (religion == null || religion.getDoctrineAttitudes().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> configured = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : religion.getDoctrineAttitudes().entrySet()) {
            IdeologyDoctrineDefinition doctrine = getDoctrine(entry.getKey());
            String attitudeId = doctrine == null ? null : normalizeAttitudeId(doctrine.getId(), entry.getValue());
            if (attitudeId != null && doctrine.getAttitude(attitudeId) != null) {
                configured.put(doctrine.getId(), attitudeId);
            }
        }
        return Collections.unmodifiableMap(configured);
    }

    /** Filters dormant saved meme selections without deleting their persisted IDs. */
    public List<String> getConfiguredMemes(Collection<String> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> configured = new LinkedHashSet<>();
        for (String memeId : memeIds) {
            String normalized = normalizeMeme(memeId);
            if (normalized != null) {
                configured.add(normalized);
            }
        }
        return List.copyOf(configured);
    }

    public boolean isValidMeme(String memeName) {
        return normalizeMeme(memeName) != null;
    }

    public String normalizeMeme(String memeName) {
        if (memeName == null) {
            return null;
        }

        String trimmed = memeName.trim();
        for (IdeologyMemeDefinition meme : registeredMemes.values()) {
            if (meme.getId().equalsIgnoreCase(trimmed)) {
                return meme.getId();
            }

            String legacyName = LEGACY_MEME_NAMES.get(meme.getId());
            if (legacyName != null && legacyName.equalsIgnoreCase(trimmed)) {
                return meme.getId();
            }

            if (meme.getTitle().equalsIgnoreCase(trimmed)) {
                return meme.getId();
            }
        }
        return null;
    }

    public String getMemeDisplayName(String memeName) {
        String normalizedMeme = normalizeMeme(memeName);
        if (normalizedMeme == null) {
            return memeName == null ? "" : memeName;
        }

        IdeologyMemeDefinition definition = registeredMemes.get(normalizedMeme);
        if (definition != null && !definition.getTitle().isBlank()) {
            return definition.getDisplayTitle();
        }
        return LEGACY_MEME_NAMES.getOrDefault(normalizedMeme, normalizedMeme);
    }

    public String getMemeDescription(String memeName) {
        String normalizedMeme = normalizeMeme(memeName);
        if (normalizedMeme == null) {
            return "";
        }

        IdeologyMemeDefinition definition = registeredMemes.get(normalizedMeme);
        return definition == null ? "" : definition.getDescription();
    }

    public boolean areMemesMutuallyExclusive(String firstMemeName, String secondMemeName) {
        String firstMemeId = normalizeMeme(firstMemeName);
        String secondMemeId = normalizeMeme(secondMemeName);
        if (firstMemeId == null || secondMemeId == null || firstMemeId.equals(secondMemeId)) {
            return false;
        }

        IdeologyMemeDefinition firstMeme = registeredMemes.get(firstMemeId);
        IdeologyMemeDefinition secondMeme = registeredMemes.get(secondMemeId);
        return (firstMeme != null && firstMeme.isExclusiveWith(secondMemeId))
                || (secondMeme != null && secondMeme.isExclusiveWith(firstMemeId));
    }

    public String findExclusiveMemeConflict(String memeName, Collection<String> selectedMemes) {
        String normalizedMemeId = normalizeMeme(memeName);
        if (normalizedMemeId == null || selectedMemes == null) {
            return null;
        }

        for (String selectedMeme : selectedMemes) {
            String normalizedSelectedMemeId = normalizeMeme(selectedMeme);
            if (normalizedSelectedMemeId == null || normalizedSelectedMemeId.equals(normalizedMemeId)) {
                continue;
            }
            if (areMemesMutuallyExclusive(normalizedMemeId, normalizedSelectedMemeId)) {
                return normalizedSelectedMemeId;
            }
        }
        return null;
    }

    public IdeologyDoctrineDefinition getDoctrine(String doctrineId) {
        if (doctrineId == null) {
            return null;
        }
        return registeredDoctrines.get(IdeologyDoctrineDefinition.normalizeId(doctrineId));
    }

    public IdeologyMemeDefinition getMeme(String memeId) {
        if (memeId == null) {
            return null;
        }
        return registeredMemes.get(IdeologyMemeDefinition.normalizeId(memeId));
    }

    public String getDoctrineDisplayName(String doctrineId) {
        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        return doctrine == null ? (doctrineId == null ? "" : doctrineId) : doctrine.getDisplayTitle();
    }

    public String getDoctrineLore(String doctrineId) {
        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        return doctrine == null ? "" : doctrine.getLore();
    }

    public String getDoctrineAttitudeDisplayName(String doctrineId, String attitudeId) {
        IdeologyDoctrineAttitudeDefinition attitude = getDoctrineAttitudeDefinition(doctrineId, attitudeId);
        return attitude == null ? (attitudeId == null ? "" : attitudeId) : attitude.getDisplayTitle();
    }

    public String getDoctrineAttitudeLore(String doctrineId, String attitudeId) {
        IdeologyDoctrineAttitudeDefinition attitude = getDoctrineAttitudeDefinition(doctrineId, attitudeId);
        return attitude == null ? "" : attitude.getLore();
    }

    public IdeologyDoctrineAttitudeDefinition getDoctrineAttitudeDefinition(String doctrineId, String attitudeId) {
        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        if (doctrine == null || attitudeId == null) {
            return null;
        }
        String normalizedAttitudeId = normalizeAttitudeId(doctrine.getId(), attitudeId);
        return normalizedAttitudeId == null ? null : doctrine.getAttitude(normalizedAttitudeId);
    }

    public boolean initializeDoctrineAttitudes(WDEreligion religion) {
        return reconcileDoctrineAttitudes(religion);
    }

    public boolean reconcileDoctrineAttitudes(WDEreligion religion) {
        if (religion == null) {
            return false;
        }

        boolean changed = false;
        for (IdeologyDoctrineDefinition doctrine : registeredDoctrines.values()) {
            String doctrineId = doctrine.getId();
            if (!doctrineId.startsWith("namespace:wder/")) {
                continue;
            }

            String legacyDoctrineId = "namespace:wde/" + doctrineId.substring("namespace:wder/".length());
            String legacyAttitudeId = religion.getDoctrineAttitude(legacyDoctrineId);
            if (legacyAttitudeId == null) {
                continue;
            }

            if (religion.getDoctrineAttitude(doctrineId) == null) {
                String prefix = legacyDoctrineId + "/";
                String migratedAttitudeId = legacyAttitudeId.startsWith(prefix)
                        ? doctrineId + legacyAttitudeId.substring(legacyDoctrineId.length())
                        : doctrineId + "/" + legacyAttitudeId;
                religion.setDoctrineAttitude(doctrineId, migratedAttitudeId);
            }
            religion.removeDoctrineAttitude(legacyDoctrineId);
            changed = true;
        }

        for (IdeologyDoctrineDefinition doctrine : registeredDoctrines.values()) {
            String currentAttitudeId = religion.getDoctrineAttitude(doctrine.getId());
            if (currentAttitudeId == null || doctrine.getAttitude(currentAttitudeId) == null || !canUseDoctrineAttitude(religion, doctrine.getId(), currentAttitudeId)) {
                String resolvedAttitudeId = resolveDoctrineDefaultAttitude(religion, doctrine);
                if (!resolvedAttitudeId.equals(currentAttitudeId)) {
                    religion.setDoctrineAttitude(doctrine.getId(), resolvedAttitudeId);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public boolean canUseDoctrineAttitude(WDEreligion religion, String doctrineId, String attitudeId) {
        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        if (doctrine == null || attitudeId == null || doctrine.getAttitude(attitudeId) == null) {
            return false;
        }
        if (religion == null) {
            return true;
        }

        for (String memeId : religion.getMemes()) {
            String normalizedMemeId = normalizeMeme(memeId);
            if (normalizedMemeId == null) {
                continue;
            }
            IdeologyDoctrineMemeConstraint constraint = doctrine.getMemeConstraint(normalizedMemeId);
            if (constraint != null && constraint.blocks(attitudeId)) {
                return false;
            }
        }
        return true;
    }

    public String setDoctrineAttitude(WDEreligion religion, String doctrineId, String requestedAttitudeId) {
        if (religion == null) {
            return null;
        }

        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        if (doctrine == null) {
            return null;
        }

        String normalizedAttitudeId = normalizeAttitudeId(doctrine.getId(), requestedAttitudeId);
        if (normalizedAttitudeId == null || doctrine.getAttitude(normalizedAttitudeId) == null || !canUseDoctrineAttitude(religion, doctrine.getId(), normalizedAttitudeId)) {
            normalizedAttitudeId = resolveDoctrineDefaultAttitude(religion, doctrine);
        }

        religion.setDoctrineAttitude(doctrine.getId(), normalizedAttitudeId);
        return normalizedAttitudeId;
    }

    public String resolveDoctrineDefaultAttitude(WDEreligion religion, IdeologyDoctrineDefinition doctrine) {
        if (doctrine == null) {
            return null;
        }

        if (religion != null) {
            for (String memeId : religion.getMemes()) {
                String normalizedMemeId = normalizeMeme(memeId);
                if (normalizedMemeId == null) {
                    continue;
                }
                IdeologyDoctrineMemeConstraint constraint = doctrine.getMemeConstraint(normalizedMemeId);
                if (constraint == null) {
                    continue;
                }

                for (String candidateAttitudeId : constraint.getDefaultAttitudeIds()) {
                    if (doctrine.getAttitude(candidateAttitudeId) != null) {
                        return candidateAttitudeId;
                    }
                }
            }
        }

        return doctrine.getDefaultAttitudeId();
    }

    public List<IdeologyDoctrineAttitudeEffect> getDoctrineEffects(String doctrineId, String attitudeId) {
        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        String normalizedDoctrineId = doctrine == null ? null : doctrine.getId();
        String normalizedAttitudeId = doctrine == null ? null : normalizeAttitudeId(doctrine.getId(), attitudeId);
        List<IdeologyDoctrineAttitudeEffect> effects = normalizedDoctrineId == null || normalizedAttitudeId == null
                ? null
                : doctrineEffectsByAttitude.get(buildDoctrineAttitudeKey(normalizedDoctrineId, normalizedAttitudeId));
        return effects == null ? Collections.emptyList() : Collections.unmodifiableList(effects);
    }

    public List<IdeologyDoctrineAttitudeEffect> getActiveDoctrineEffects(WDEreligion religion) {
        if (religion == null) {
            return Collections.emptyList();
        }

        List<IdeologyDoctrineAttitudeEffect> effects = new ArrayList<>();
        for (Map.Entry<String, String> entry : getConfiguredDoctrineAttitudes(religion).entrySet()) {
            effects.addAll(getDoctrineEffects(entry.getKey(), entry.getValue()));
        }
        return effects;
    }

    public void enableAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveReligions();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 5);
    }

    private void registerDoctrineEffect(IdeologyDoctrineAttitudeEffect effect) {
        if (effect == null) {
            return;
        }

        String doctrineId = IdeologyDoctrineDefinition.normalizeId(effect.getDoctrineId());
        IdeologyDoctrineDefinition doctrine = getDoctrine(doctrineId);
        if (doctrine == null) {
            plugin.getLogger().warning("Skipped doctrine effect for unknown doctrine: " + effect.getDoctrineId());
            return;
        }

        String attitudeId = normalizeAttitudeId(doctrineId, effect.getAttitudeId());
        if (attitudeId == null || doctrine.getAttitude(attitudeId) == null) {
            plugin.getLogger().warning("Skipped doctrine effect for unknown attitude: " + effect.getAttitudeId());
            return;
        }

        doctrineEffectsByAttitude.computeIfAbsent(buildDoctrineAttitudeKey(doctrineId, attitudeId), ignored -> new ArrayList<>()).add(effect);
    }

    private void loadConfiguredMemes() {
        FileConfiguration ideologyConfig = ((WorldDynamicsEngine) plugin).getIdeologyConfig();
        if (ideologyConfig == null) {
            return;
        }

        ConfigurationSection registrySection = ideologyConfig.getConfigurationSection("memes.registry");
        if (registrySection == null) {
            return;
        }

        for (String key : registrySection.getKeys(false)) {
            String normalizedId = IdeologyMemeDefinition.normalizeId(key);
            if (!isValidMemeId(normalizedId)) {
                plugin.getLogger().warning("Skipped ideology meme with invalid id: " + key);
                continue;
            }

            String basePath = "memes.registry." + key;
            IdeologyMemeDefinition meme = new IdeologyMemeDefinition(
                    normalizedId,
                    ideologyConfig.getString(basePath + ".title", normalizedId),
                    ideologyConfig.getString(basePath + ".description", ""),
                    ideologyConfig.getString(basePath + ".color", ""),
                    ideologyConfig.getString(basePath + ".icon", ""),
                    "config",
                    parseMemeExclusiveWith(ideologyConfig.get(basePath + ".exclusive_with"))
            );
            if (!meme.isValid()) {
                plugin.getLogger().warning("Skipped ideology meme with missing title: " + key);
                continue;
            }
            if (registeredMemes.containsKey(normalizedId)) {
                plugin.getLogger().warning("Skipped duplicate ideology meme id: " + normalizedId);
                continue;
            }
            registeredMemes.put(normalizedId, meme);
        }
    }

    private void loadConfiguredDoctrines() {
        FileConfiguration ideologyConfig = ((WorldDynamicsEngine) plugin).getIdeologyConfig();
        if (ideologyConfig == null) {
            return;
        }

        ConfigurationSection registrySection = ideologyConfig.getConfigurationSection("doctrines.registry");
        if (registrySection == null) {
            return;
        }

        for (String key : registrySection.getKeys(false)) {
            String normalizedDoctrineId = IdeologyDoctrineDefinition.normalizeId(key);
            if (!isValidDoctrineId(normalizedDoctrineId)) {
                plugin.getLogger().warning("Skipped ideology doctrine with invalid id: " + key);
                continue;
            }

            Map<?, ?> doctrineValues = asMap(registrySection.get(key));
            if (doctrineValues.isEmpty()) {
                plugin.getLogger().warning("Skipped ideology doctrine without section body: " + key);
                continue;
            }

            List<IdeologyDoctrineAttitudeDefinition> attitudes = parseDoctrineAttitudes(normalizedDoctrineId, doctrineValues.get("attitudes"));
            if (attitudes.isEmpty()) {
                plugin.getLogger().warning("Skipped ideology doctrine without attitudes: " + key);
                continue;
            }

            IdeologyDoctrineDefinition doctrine = new IdeologyDoctrineDefinition(
                    normalizedDoctrineId,
                    getStringValue(doctrineValues, "title", normalizedDoctrineId),
                    getStringValue(doctrineValues, "lore", ""),
                    getStringValue(doctrineValues, "color", ""),
                    getStringValue(doctrineValues, "icon", ""),
                    attitudes,
                    parseDoctrineMemeConstraints(normalizedDoctrineId, doctrineValues.get("meme")),
                    "config"
            );
            if (!doctrine.isValid()) {
                plugin.getLogger().warning("Skipped ideology doctrine with invalid payload: " + key);
                continue;
            }
            if (registeredDoctrines.containsKey(normalizedDoctrineId)) {
                plugin.getLogger().warning("Skipped duplicate ideology doctrine id: " + normalizedDoctrineId);
                continue;
            }
            registeredDoctrines.put(normalizedDoctrineId, doctrine);
        }
    }

    private List<IdeologyDoctrineAttitudeDefinition> parseDoctrineAttitudes(String doctrineId, Object rawAttitudes) {
        List<IdeologyDoctrineAttitudeDefinition> attitudes = new ArrayList<>();
        if (rawAttitudes instanceof Collection<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> entryMap) {
                    for (Map.Entry<?, ?> mapEntry : entryMap.entrySet()) {
                        addDoctrineAttitudeFromEntry(attitudes, doctrineId, String.valueOf(mapEntry.getKey()), mapEntry.getValue());
                    }
                }
            }
        } else if (rawAttitudes instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                addDoctrineAttitudeFromEntry(attitudes, doctrineId, key, section.get(key));
            }
        }
        return attitudes;
    }

    private void addDoctrineAttitudeFromEntry(List<IdeologyDoctrineAttitudeDefinition> attitudes, String doctrineId, String rawAttitudeId, Object rawValue) {
        String attitudeId = normalizeAttitudeId(doctrineId, rawAttitudeId);
        if (attitudeId == null) {
            plugin.getLogger().warning("Skipped doctrine attitude with invalid id: " + rawAttitudeId);
            return;
        }

        Map<?, ?> values = asMap(rawValue);
        String title = getStringValue(values, "title", rawAttitudeId);
        String lore = getStringValue(values, "lore", "");
        String color = getStringValue(values, "color", "");
        IdeologyDoctrineAttitudeDefinition attitude = new IdeologyDoctrineAttitudeDefinition(attitudeId, title, lore, color);
        if (!attitude.isValid()) {
            plugin.getLogger().warning("Skipped doctrine attitude with missing title: " + rawAttitudeId);
            return;
        }
        attitudes.add(attitude);
    }

    private List<IdeologyDoctrineMemeConstraint> parseDoctrineMemeConstraints(String doctrineId, Object rawConstraints) {
        List<IdeologyDoctrineMemeConstraint> constraints = new ArrayList<>();
        if (!(rawConstraints instanceof Collection<?> entries)) {
            return constraints;
        }

        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> entryMap)) {
                continue;
            }
            for (Map.Entry<?, ?> mapEntry : entryMap.entrySet()) {
                String memeId = normalizeMeme(String.valueOf(mapEntry.getKey()));
                if (memeId == null) {
                    memeId = String.valueOf(mapEntry.getKey()).trim().toLowerCase();
                }
                Map<?, ?> values = asMap(mapEntry.getValue());
                List<String> defaultAttitudes = normalizeAttitudeIdList(doctrineId, values.get("default"));
                List<String> blockedAttitudes = normalizeAttitudeIdList(doctrineId, values.get("cant_use"));
                constraints.add(new IdeologyDoctrineMemeConstraint(memeId, defaultAttitudes, blockedAttitudes));
            }
        }
        return constraints;
    }

    private List<String> parseMemeExclusiveWith(Object rawValue) {
        List<String> exclusiveWith = new ArrayList<>();
        if (rawValue instanceof Collection<?> values) {
            for (Object value : values) {
                String normalizedMemeId = normalizeMeme(value == null ? null : String.valueOf(value));
                if (normalizedMemeId == null) {
                    normalizedMemeId = IdeologyMemeDefinition.normalizeId(value == null ? null : String.valueOf(value));
                }
                if (normalizedMemeId != null && !normalizedMemeId.isBlank()) {
                    exclusiveWith.add(normalizedMemeId);
                }
            }
        } else if (rawValue != null) {
            String normalizedMemeId = normalizeMeme(String.valueOf(rawValue));
            if (normalizedMemeId == null) {
                normalizedMemeId = IdeologyMemeDefinition.normalizeId(String.valueOf(rawValue));
            }
            if (normalizedMemeId != null && !normalizedMemeId.isBlank()) {
                exclusiveWith.add(normalizedMemeId);
            }
        }
        return exclusiveWith;
    }

    private List<String> normalizeAttitudeIdList(String doctrineId, Object rawValue) {
        List<String> normalizedIds = new ArrayList<>();
        if (rawValue instanceof Collection<?> values) {
            for (Object value : values) {
                String normalizedId = normalizeAttitudeId(doctrineId, value == null ? null : String.valueOf(value));
                if (normalizedId != null) {
                    normalizedIds.add(normalizedId);
                }
            }
        }
        return normalizedIds;
    }

    private String normalizeAttitudeId(String doctrineId, String rawAttitudeId) {
        if (rawAttitudeId == null) {
            return null;
        }

        String normalized = rawAttitudeId.trim().toLowerCase();
        if (normalized.matches(ATTITUDE_ID_PATTERN)) {
            return normalized;
        }

        if (!isValidDoctrineId(doctrineId) || !normalized.matches(LOCAL_ATTITUDE_ID_PATTERN)) {
            return null;
        }
        return doctrineId + "/" + normalized;
    }

    private Map<?, ?> asMap(Object rawValue) {
        if (rawValue instanceof Map<?, ?> map) {
            return map;
        }
        if (rawValue instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        return Collections.emptyMap();
    }

    private String getStringValue(Map<?, ?> values, String key, String fallback) {
        Object rawValue = values.get(key);
        return rawValue == null ? fallback : String.valueOf(rawValue);
    }

    private String buildDoctrineAttitudeKey(String doctrineId, String attitudeId) {
        return doctrineId + "|" + attitudeId;
    }

    private boolean isValidMemeId(String memeId) {
        return memeId != null && memeId.matches("[a-z0-9._-]{2,64}");
    }

    private boolean isValidDoctrineId(String doctrineId) {
        return doctrineId != null && doctrineId.matches(DOCTRINE_ID_PATTERN);
    }

    private static Map<String, String> createLegacyMemeNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("original_sin", "原罪主义");
        names.put("nature_primacy", "自然至上");
        names.put("proselytizer", "传教热忱");
        names.put("loyalty", "赤胆忠心");
        names.put("supremacist", "霸权主义");
        names.put("collectivist", "集体主义");
        names.put("ritualist", "仪式热忱");
        names.put("darkness", "黑暗亲和");
        names.put("tunneler", "地底之心");
        names.put("cannibal", "食人狂热");
        names.put("bloodfeeder", "汲血狂热");
        names.put("pain_is_virtue", "苦痛美德");
        names.put("nudist", "裸体主义");
        names.put("nonhuman_alienation", "非人异化");
        names.put("raider", "劫掠成性");
        names.put("individualist", "个人主义");
        names.put("animal_personhood", "动物平权");
        names.put("human_primacy", "人类至上");
        return Collections.unmodifiableMap(names);
    }
}
