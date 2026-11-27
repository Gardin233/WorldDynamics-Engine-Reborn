package com.thatguycy.worlddynamicsengine.manager.event;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

/** Spawns a compact group of ordinary, non-hostile livestock in the wilderness near a town. */
final class WanderingLivestockEvent implements GameEvent {
    private final WorldDynamicsEngine plugin;
    private final Random random = new Random();

    WanderingLivestockEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "wandering_livestock";
    }

    @Override
    public EventOccurrence execute(RandomEventContext context) {
        var config = plugin.getEventManager().getConfig();
        Map<EntityType, Double> weights = loadAnimalWeights(
                config.getConfigurationSection("wandering-livestock.animal-weights"));
        EntityType type = AnimalEventComposition.chooseAnimal(weights, random);
        if (type == null) {
            return null;
        }

        ConfigurationSection spawn = config.getConfigurationSection("wandering-livestock.spawn");
        int centerAttempts = Math.max(1, Math.min(200, getInt(spawn, "center-attempts", 40)));
        Location center = WildernessAnimalSpawns.findGroupCenter(
                context.town(), centerAttempts, random);
        if (center == null) {
            return null;
        }

        ConfigurationSection livestockConfig = config.getConfigurationSection("wandering-livestock");
        int minimumCount = Math.min(20, Math.max(2, getInt(livestockConfig, "minimum-count", 3)));
        int maximumCount = Math.min(20,
                Math.max(minimumCount, getInt(livestockConfig, "maximum-count", 5)));
        int count = AnimalEventComposition.randomCount(minimumCount, maximumCount, random);
        int groupRadius = Math.max(1, Math.min(16, getInt(spawn, "group-radius", 5)));
        int attemptsPerAnimal = Math.max(1, Math.min(100, getInt(spawn, "attempts-per-animal", 20)));
        List<Location> locations = WildernessAnimalSpawns.findGroupLocations(
                center, count, groupRadius, attemptsPerAnimal, random);
        if (locations.size() < minimumCount) {
            return null;
        }

        List<Mob> spawnedAnimals = new java.util.ArrayList<>();
        for (Location location : locations) {
            if (!TownyWilderness.isUnclaimed(location)) {
                continue;
            }
            Entity entity = location.getWorld().spawnEntity(location, type);
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                continue;
            }
            mob.setPersistent(true);
            spawnedAnimals.add(mob);
        }
        if (spawnedAnimals.size() < minimumCount) {
            spawnedAnimals.forEach(Entity::remove);
            return null;
        }

        String animalName = config.getString(
                "wandering-livestock.animal-names." + type.name(), type.name().toLowerCase(Locale.ROOT));
        context.target().sendMessage(plugin.getEventManager().message(
                        "wandering-livestock", "arrived")
                .replace("%town%", context.townName())
                .replace("%animal%", animalName)
                .replace("%count%", String.valueOf(spawnedAnimals.size())));
        return new Arrival(context.eventId(), context.target().getUniqueId(), context.townName());
    }

    private Map<EntityType, Double> loadAnimalWeights(ConfigurationSection section) {
        Map<EntityType, Double> weights = new EnumMap<>(EntityType.class);
        if (section == null) {
            return weights;
        }
        for (String key : section.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring unknown wandering livestock type: " + key);
                continue;
            }
            double weight = section.getDouble(key, 0.0D);
            if (AnimalEventComposition.isDomesticAnimal(type) && Double.isFinite(weight) && weight > 0.0D) {
                weights.put(type, weight);
            }
        }
        return weights;
    }

    private static int getInt(ConfigurationSection section, String path, int defaultValue) {
        return section == null ? defaultValue : section.getInt(path, defaultValue);
    }

    private record Arrival(UUID eventId, UUID ownerId, String townName) implements EventOccurrence {
        @Override
        public String eventTypeId() {
            return "wandering_livestock";
        }
    }
}
