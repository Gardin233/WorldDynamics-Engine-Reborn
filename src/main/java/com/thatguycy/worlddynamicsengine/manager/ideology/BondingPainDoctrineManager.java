package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

/** Tracks tameable animals and evaluates the pain doctrine from current player health. */
public final class BondingPainDoctrineManager implements Listener {
    private static final String BONDING_PREFIX = "bonding:";
    private static final String BONDING_PETS_SOURCE = "bonding:pets";
    private static final String PAIN_PREFIX = "pain:";
    private static final String PAIN_DOCTRINE = "namespace:wder/pain";
    private static final String PET_DEATH_SOURCE = "bondingPetDeath";
    private static final List<Double> DEFAULT_HEALTH_THRESHOLDS = List.of(0.75, 0.50, 0.25, 0.10);
    private static final List<Integer> DEFAULT_MOOD_STEPS = List.of(2, 4, 6, 10);

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, UUID> knownAnimalOwners = new HashMap<>();
    private final Queue<Chunk> startupChunks = new ArrayDeque<>();
    private BukkitTask startupScanTask;

    public BondingPainDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        loadKnownAnimalOwners();
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                startupChunks.add(chunk);
            }
        }
        if (!startupChunks.isEmpty()) {
            startupScanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanStartupChunks, 1L, 1L);
        }
        long checkInterval = Math.max(1,
                plugin.getIdeologyConfig().getLong("mood.doctrineActions.pain.checkIntervalTicks", 20L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnlinePlayers, 1L, checkInterval);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimalTamed(EntityTameEvent event) {
        UUID ownerUuid = event.getOwner() == null ? null : event.getOwner().getUniqueId();
        registerOwnedAnimal(event.getEntity(), ownerUuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimalDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Tameable animal) || !animal.isTamed()) return;

        UUID animalUuid = entity.getUniqueId();
        UUID ownerUuid = animal.getOwnerUniqueId();
        UUID knownOwnerUuid = knownAnimalOwners.remove(animalUuid);
        if (knownOwnerUuid != null && !knownOwnerUuid.equals(ownerUuid)) {
            removeOwnedAnimal(knownOwnerUuid, animalUuid);
        }
        if (ownerUuid == null) return;

        WDEresident owner = getOrCreateResident(ownerUuid);
        if (owner == null) return;
        boolean wasTracked = owner.removeBondedAnimalUuid(animalUuid);
        if (wasTracked) {
            plugin.getResidentManager().saveResident(owner);
        }
        refreshBondingMood(owner);
        applyPetDeathPenalty(ownerUuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            WDEresident resident = getOrCreateResident(player.getUniqueId());
            if (resident != null) refreshBondingMood(resident);
            refreshPainMood(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshPainMood(event.getPlayer()));
    }

    private void scanStartupChunks() {
        for (int index = 0; index < 16 && !startupChunks.isEmpty(); index++) {
            scanChunk(startupChunks.remove());
        }
        if (startupChunks.isEmpty() && startupScanTask != null) {
            startupScanTask.cancel();
            startupScanTask = null;
        }
    }

    private void scanChunk(Chunk chunk) {
        if (!chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) return;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Tameable animal) || !animal.isTamed()) continue;
            registerOwnedAnimal(entity, animal.getOwnerUniqueId());
        }
    }

    private void registerOwnedAnimal(Entity animal, UUID ownerUuid) {
        if (animal == null || ownerUuid == null) return;
        UUID animalUuid = animal.getUniqueId();
        UUID previousOwnerUuid = knownAnimalOwners.put(animalUuid, ownerUuid);
        if (previousOwnerUuid != null && !previousOwnerUuid.equals(ownerUuid)) {
            removeOwnedAnimal(previousOwnerUuid, animalUuid);
        }

        WDEresident owner = getOrCreateResident(ownerUuid);
        if (owner == null) return;
        if (owner.addBondedAnimalUuid(animalUuid)) {
            plugin.getResidentManager().saveResident(owner);
        }
        refreshBondingMood(owner);
    }

    private void removeOwnedAnimal(UUID ownerUuid, UUID animalUuid) {
        WDEresident owner = plugin.getResidentManager().getResident(ownerUuid);
        if (owner == null || !owner.removeBondedAnimalUuid(animalUuid)) return;
        plugin.getResidentManager().saveResident(owner);
        refreshBondingMood(owner);
    }

    private void loadKnownAnimalOwners() {
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            for (String animalId : resident.getBondedAnimalUuids()) {
                try {
                    knownAnimalOwners.putIfAbsent(UUID.fromString(animalId), resident.getUserUUID());
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring invalid bonded animal UUID in resident "
                            + resident.getUserUUID() + ": " + animalId);
                }
            }
        }
    }

    private WDEresident getOrCreateResident(UUID ownerUuid) {
        WDEresident resident = plugin.getResidentManager().getResident(ownerUuid);
        if (resident != null) return resident;
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        if (!owner.isOnline() && !owner.hasPlayedBefore()) return null;
        String username = owner.getName() == null ? "Unknown" : owner.getName();
        return plugin.getResidentManager().getOrCreateResident(ownerUuid, username);
    }

    private void refreshBondingMood(WDEresident owner) {
        var config = plugin.getIdeologyConfig();
        String path = "mood.doctrineActions.bonding.";
        boolean enabled = config.getBoolean(path + "enabled", true);
        int moodPerPet = Math.max(0, Math.min(100, config.getInt(path + "moodPerTamedAnimal", 2)));
        long delta = enabled ? (long) owner.getBondedAnimalUuids().size() * moodPerPet : 0L;
        plugin.getMoodManager().replacePersistentMoodModifier(owner.getUserUUID(), BONDING_PREFIX,
                delta > 0 ? BONDING_PETS_SOURCE : null, (int) Math.min(100L, delta));
    }

    private void applyPetDeathPenalty(UUID ownerUuid) {
        var config = plugin.getIdeologyConfig();
        String path = "mood.doctrineActions.bonding.";
        if (!config.getBoolean(path + "enabled", true)) return;
        int penalty = Math.max(0, Math.min(100, config.getInt(path + "petDeathPenalty", 4)));
        long days = Math.max(1, Math.min(3650, config.getLong(path + "petDeathDurationDays", 7)));
        plugin.getMoodManager().applyTemporaryMoodChange(ownerUuid, PET_DEATH_SOURCE, -penalty,
                days * 24L * 60 * 60 * 1000);
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            WDEresident resident = getOrCreateResident(player.getUniqueId());
            if (resident != null) refreshBondingMood(resident);
            refreshPainMood(player);
        }
    }

    private void refreshPainMood(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return;
        var config = plugin.getIdeologyConfig();
        String path = "mood.doctrineActions.pain.";
        WDEresident resident = getOrCreateResident(player.getUniqueId());
        if (resident == null) return;

        if (!config.getBoolean(path + "enabled", true)) {
            plugin.getMoodManager().replacePersistentMoodModifier(player.getUniqueId(),
                    PAIN_PREFIX, null, 0);
            return;
        }

        String attitude = getPainAttitude(resident);
        List<Double> thresholds = getThresholds(config.getDoubleList(path + "healthThresholds"));
        List<Integer> idealBonuses = getMoodSteps(config.getIntegerList(path + "ideal.lowHealthBonuses"));
        List<Integer> neutralPenalties = getMoodSteps(config.getIntegerList(path + "none.lowHealthPenalties"));
        if (idealBonuses.size() != thresholds.size() || neutralPenalties.size() != thresholds.size()) {
            thresholds = DEFAULT_HEALTH_THRESHOLDS;
            idealBonuses = DEFAULT_MOOD_STEPS;
            neutralPenalties = DEFAULT_MOOD_STEPS;
        }
        int healthyPenalty = Math.max(0, Math.min(100, config.getInt(path + "ideal.highHealthPenalty", 2)));
        PainMoodCalculator.MoodState state = PainMoodCalculator.calculate(
                player.getHealth(), player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                attitude, thresholds, idealBonuses, neutralPenalties, healthyPenalty);
        plugin.getMoodManager().replacePersistentMoodModifier(player.getUniqueId(),
                PAIN_PREFIX, state.source(), state.delta());
    }

    private String getPainAttitude(WDEresident resident) {
        WDEreligion religion = plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) return "none";
        String attitude = religion.getDoctrineAttitude(PAIN_DOCTRINE);
        if (attitude == null || attitude.isBlank()) return "none";
        return attitude.substring(attitude.lastIndexOf('/') + 1);
    }

    private static List<Double> getThresholds(List<Double> configured) {
        if (configured == null || configured.isEmpty()) return DEFAULT_HEALTH_THRESHOLDS;
        List<Double> values = new ArrayList<>(configured);
        double previous = 1.0;
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value <= 0 || value >= previous) {
                return DEFAULT_HEALTH_THRESHOLDS;
            }
            previous = value;
        }
        return List.copyOf(values);
    }

    private static List<Integer> getMoodSteps(List<Integer> configured) {
        if (configured == null || configured.isEmpty()) return DEFAULT_MOOD_STEPS;
        return configured.stream().map(value -> Math.max(0, Math.min(100, value))).toList();
    }
}
