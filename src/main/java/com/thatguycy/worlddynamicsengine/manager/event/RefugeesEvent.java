package com.thatguycy.worlddynamicsengine.manager.event;

import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

/** Spawns a small group of skilled villagers in the wilderness outside a town. */
final class RefugeesEvent implements GameEvent {
    private final WorldDynamicsEngine plugin;
    private final Random random = new Random();

    RefugeesEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "refugees";
    }

    @Override
    public EventOccurrence execute(RandomEventContext context) {
        Location townSpawn;
        try {
            townSpawn = context.town().getSpawn();
        } catch (TownyException exception) {
            plugin.getLogger().warning("Could not get spawn location for refugee event near town "
                    + context.town().getName() + ": " + exception.getMessage());
            return null;
        }
        if (townSpawn == null || townSpawn.getWorld() == null) {
            return null;
        }

        var config = plugin.getEventManager().getConfig();
        List<WeightedValue<Villager.Profession>> professions = loadProfessionWeights(
                config.getConfigurationSection("refugees.profession-weights"));
        int maximumLevel = Math.max(1, Math.min(5, config.getInt("refugees.maximum-level", 5)));
        List<WeightedValue<Integer>> levels = loadLevelWeights(
                config.getConfigurationSection("refugees.level-weights"), maximumLevel);
        if (professions.isEmpty() || levels.isEmpty()) {
            plugin.getLogger().warning("Refugee event skipped because no valid profession or level weights are configured.");
            return null;
        }

        int minRadius = Math.max(8, config.getInt("refugees.spawn.minimum-radius", 32));
        int maxRadius = Math.max(minRadius, config.getInt("refugees.spawn.maximum-radius", 64));
        int attempts = Math.max(1, config.getInt("refugees.spawn.location-attempts", 40));
        int groupRadius = Math.max(1, Math.min(8, config.getInt("refugees.spawn.group-radius", 4)));
        int wantedCount = 3 + random.nextInt(3);

        Location groupCenter = findWildernessLocation(townSpawn, minRadius, maxRadius, attempts);
        if (groupCenter == null) {
            return null;
        }

        List<Location> locations = new ArrayList<>();
        locations.add(groupCenter);
        int attemptsPerVillager = Math.max(1, config.getInt("refugees.spawn.attempts-per-villager", 20));
        for (int villager = 1; villager < wantedCount; villager++) {
            for (int attempt = 0; attempt < attemptsPerVillager; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 1.0D + random.nextDouble() * (groupRadius - 1);
                int x = groupCenter.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
                int z = groupCenter.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
                Location candidate = safeWildernessLocation(groupCenter.getWorld(), x, z);
                if (candidate != null && isSeparated(candidate, locations)) {
                    locations.add(candidate);
                    break;
                }
            }
        }
        if (locations.size() < 3) {
            return null;
        }

        List<Villager> spawnedVillagers = new ArrayList<>();
        for (Location location : locations) {
            if (!TownyWilderness.isUnclaimed(location)) {
                continue;
            }
            Entity entity = location.getWorld().spawnEntity(location, EntityType.VILLAGER);
            if (!(entity instanceof Villager villager)) {
                entity.remove();
                continue;
            }
            villager.setAdult();
            villager.setProfession(chooseWeighted(professions));
            villager.setVillagerLevel(chooseWeighted(levels));
            villager.setPersistent(true);
            spawnedVillagers.add(villager);
        }
        if (spawnedVillagers.size() < 3) {
            spawnedVillagers.forEach(Entity::remove);
            return null;
        }

        Player target = context.target();
        target.sendMessage(plugin.getEventManager().message("refugees", "arrived")
                .replace("%town%", context.town().getName())
                .replace("%count%", String.valueOf(spawnedVillagers.size())));
        return new Arrival(context.eventId(), context.target().getUniqueId(), context.town().getName());
    }

    private List<WeightedValue<Villager.Profession>> loadProfessionWeights(ConfigurationSection section) {
        List<WeightedValue<Villager.Profession>> weightedProfessions = new ArrayList<>();
        if (section == null) {
            return weightedProfessions;
        }

        for (String configuredKey : section.getKeys(false)) {
            Villager.Profession profession = findProfession(configuredKey);
            double weight = section.getDouble(configuredKey, 0.0D);
            if (profession == null || profession == Villager.Profession.NONE
                    || profession == Villager.Profession.NITWIT || !isPositiveFinite(weight)) {
                continue;
            }
            weightedProfessions.add(new WeightedValue<>(profession, weight));
        }
        return weightedProfessions;
    }

    private List<WeightedValue<Integer>> loadLevelWeights(ConfigurationSection section, int maximumLevel) {
        List<WeightedValue<Integer>> weightedLevels = new ArrayList<>();
        for (int level = 1; level <= maximumLevel; level++) {
            double weight = section == null ? 1.0D : section.getDouble(String.valueOf(level), 1.0D);
            if (isPositiveFinite(weight)) {
                weightedLevels.add(new WeightedValue<>(level, weight));
            }
        }
        return weightedLevels;
    }

    private Villager.Profession findProfession(String configuredKey) {
        for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
            var key = Registry.VILLAGER_PROFESSION.getKey(profession);
            if (key != null && (key.getKey().equalsIgnoreCase(configuredKey)
                    || key.toString().equalsIgnoreCase(configuredKey))) {
                return profession;
            }
        }
        return null;
    }

    private <T> T chooseWeighted(List<WeightedValue<T>> values) {
        double maximumWeight = values.stream().mapToDouble(WeightedValue::weight).max().orElseThrow();
        double totalWeight = values.stream().mapToDouble(value -> value.weight() / maximumWeight).sum();
        double selection = random.nextDouble() * totalWeight;
        for (WeightedValue<T> value : values) {
            selection -= value.weight() / maximumWeight;
            if (selection < 0.0D) {
                return value.value();
            }
        }
        return values.get(values.size() - 1).value();
    }

    private static boolean isPositiveFinite(double weight) {
        return Double.isFinite(weight) && weight > 0.0D;
    }

    private Location findWildernessLocation(Location center, int minRadius, int maxRadius, int attempts) {
        World world = center.getWorld();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location location = safeWildernessLocation(world, x, z);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    private static Location safeWildernessLocation(World world, int x, int z) {
        Location location = world.getHighestBlockAt(x, z).getLocation().add(0.5D, 1.0D, 0.5D);
        if (location.getBlockY() <= world.getMinHeight()
                || location.getBlockY() + 1 >= world.getMaxHeight()
                || !location.getBlock().isPassable()
                || !location.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()
                || !location.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType().isSolid()
                || !TownyWilderness.isUnclaimed(location)) {
            return null;
        }
        return location;
    }

    private static boolean isSeparated(Location candidate, List<Location> locations) {
        for (Location location : locations) {
            double dx = candidate.getX() - location.getX();
            double dz = candidate.getZ() - location.getZ();
            if (dx * dx + dz * dz < 0.81D) {
                return false;
            }
        }
        return true;
    }

    private record Arrival(UUID eventId, UUID ownerId, String townName) implements EventOccurrence {
        @Override
        public String eventTypeId() {
            return "refugees";
        }

        @Override
        public boolean isOngoing() {
            return false;
        }

        @Override
        public void finish(boolean successful) {
            // Refugees remain in the world after the arrival event completes.
        }
    }

    private record WeightedValue<T>(T value, double weight) {
    }
}
