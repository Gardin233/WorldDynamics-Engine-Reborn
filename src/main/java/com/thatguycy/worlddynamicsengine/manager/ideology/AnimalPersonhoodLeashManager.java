package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerUnleashEntityEvent;

/** Applies animal-personhood mood changes for observing and removing leashes. */
public final class AnimalPersonhoodLeashManager implements Listener {
    private static final String MEME_ID = "animal_personhood";
    private static final String OBSERVED_SOURCE = "animal_personhood_leash_observed";
    private static final String RELEASED_SOURCE = "animal_personhood_leash_released";
    private static final String CONFIG_PATH = "mood.doctrineActions.animalPersonhoodLeash.";
    private static final long MINUTE_MILLIS = 60_000L;

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, Set<UUID>> observedAnimals = new HashMap<>();
    private int ticksSinceScan;

    public AnimalPersonhoodLeashManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerUnleash(PlayerUnleashEntityEvent event) {
        if (!(event.getEntity() instanceof Animals animal)) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !animal.isValid() || animal.isLeashed()
                    || plugin.getMoodManager() == null || !hasAnimalPersonhood(player)
                    || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
                return;
            }
            int bonus = configuredMagnitude("moodBonus", 5);
            long duration = configuredDurationMillis("moodDurationMinutes", 5);
            if (plugin.getMoodManager().applyTemporaryMoodChange(
                    player.getUniqueId(), RELEASED_SOURCE, bonus, duration)) {
                player.sendMessage(plugin.formatLocaleMessage(
                        "animal_personhood_leash_released", signed(bonus), duration / MINUTE_MILLIS));
            }
        });
    }

    private void tick() {
        int interval = configuredInt("checkIntervalTicks", 40, 10, 1200);
        ticksSinceScan++;
        if (ticksSinceScan < interval) return;
        ticksSinceScan = 0;
        scanPlayers();
    }

    private void scanPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!player.isOnline() || player.isDead() || !hasAnimalPersonhood(player)
                    || plugin.getMoodManager() == null
                    || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
                observedAnimals.remove(playerId);
                continue;
            }

            int radius = configuredInt("radiusBlocks", 8, 1, 64);
            double radiusSquared = (double) radius * radius;
            Set<UUID> currentlyObserved = new HashSet<>();
            Set<UUID> previouslyObserved = observedAnimals.getOrDefault(playerId, Set.of());
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof Animals animal) || !animal.isLeashed()
                        || animal.getLocation().distanceSquared(player.getLocation()) > radiusSquared) {
                    continue;
                }
                UUID animalId = animal.getUniqueId();
                currentlyObserved.add(animalId);
                if (!previouslyObserved.contains(animalId)) {
                    int penalty = configuredMagnitude("moodPenalty", 5);
                    plugin.getMoodManager().applyTemporaryMoodChange(
                            playerId, OBSERVED_SOURCE, -penalty,
                            configuredDurationMillis("moodDurationMinutes", 5));
                }
            }
            observedAnimals.put(playerId, currentlyObserved);
        }
        observedAnimals.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private boolean hasAnimalPersonhood(Player player) {
        if (!isEnabled() || !plugin.religionEnabled || player == null
                || plugin.getReligionManager() == null) return false;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        return religion != null && plugin.getReligionManager()
                .getConfiguredMemes(religion.getMemes()).contains(MEME_ID);
    }

    private boolean isEnabled() {
        var config = plugin.getIdeologyConfig();
        return config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
    }

    private int configuredMagnitude(String key, int fallback) {
        return configuredInt(key, fallback, 0, 100);
    }

    private long configuredDurationMillis(String key, int fallbackMinutes) {
        return configuredInt(key, fallbackMinutes, 1, 1440) * MINUTE_MILLIS;
    }

    private int configuredInt(String key, int fallback, int minimum, int maximum) {
        var config = plugin.getIdeologyConfig();
        int value = config == null ? fallback : config.getInt(CONFIG_PATH + key, fallback);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
