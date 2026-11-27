package com.thatguycy.worlddynamicsengine.manager.event;

import com.destroystokyo.paper.entity.Pathfinder;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Turns a strength-scaled group of familiar animals hostile to nearby players. */
final class MaddenedAnimalsEvent implements AttackGameEvent {
    private final WorldDynamicsEngine plugin;
    private final Random random = new Random();

    MaddenedAnimalsEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "maddened_animals";
    }

    @Override
    public double minimumTownWealth() {
        double minimum = plugin.getEventManager().getConfig()
                .getDouble("maddened-animals.minimum-town-wealth", 10000.0D);
        return Double.isFinite(minimum) ? Math.max(0.0D, minimum) : 10000.0D;
    }

    @Override
    public EventOccurrence execute(RandomEventContext context) {
        var config = plugin.getEventManager().getConfig();
        Map<EntityType, Double> weights = loadAnimalWeights(
                config.getConfigurationSection("maddened-animals.animal-weights"));
        EntityType type = AnimalEventComposition.chooseAnimal(weights, random);
        if (type == null) {
            return null;
        }

        ConfigurationSection spawn = config.getConfigurationSection("maddened-animals.spawn");
        int centerAttempts = Math.max(1, Math.min(200, getInt(spawn, "center-attempts", 40)));
        Location center = WildernessAnimalSpawns.findGroupCenter(
                context.town(), centerAttempts, random);
        if (center == null) {
            return null;
        }

        ConfigurationSection configSection = config.getConfigurationSection("maddened-animals");
        double animalsPerStrengthPoint = config.getDouble(
                "maddened-animals.animals-per-strength-point", 0.25D);
        int maximumCount = Math.min(64,
                Math.max(2, getInt(configSection, "maximum-initial-animals", 20)));
        int count = AnimalEventComposition.countByStrength(
                context.strengthPoints(), animalsPerStrengthPoint, 2, maximumCount);
        if (count < 2) {
            return null;
        }

        int groupRadius = Math.max(1, Math.min(16, getInt(spawn, "group-radius", 5)));
        int attemptsPerAnimal = Math.max(1, Math.min(100, getInt(spawn, "attempts-per-animal", 20)));
        List<Location> locations = WildernessAnimalSpawns.findGroupLocations(
                center, count, groupRadius, attemptsPerAnimal, random);
        if (locations.size() < 2) {
            return null;
        }

        List<Mob> spawnedAnimals = new ArrayList<>();
        for (Location location : locations) {
            Entity entity = location.getWorld().spawnEntity(location, type);
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                continue;
            }
            if (mob instanceof Ageable ageable) {
                ageable.setAdult();
            }
            mob.setPersistent(true);
            mob.getPersistentDataContainer().set(
                    plugin.getEventManager().getMaddenedAnimalKey(),
                    PersistentDataType.STRING, context.eventId().toString());
            spawnedAnimals.add(mob);
        }
        if (spawnedAnimals.size() < 2) {
            spawnedAnimals.forEach(Entity::remove);
            return null;
        }

        ActiveMaddenedAnimals occurrence = new ActiveMaddenedAnimals(
                context, type, spawnedAnimals, config.getConfigurationSection("maddened-animals"));
        occurrence.start();
        String animalName = config.getString(
                "maddened-animals.animal-names." + type.name(), type.name().toLowerCase(Locale.ROOT));
        int durationMinutes = Math.max(1, getInt(configSection, "duration-minutes", 15));
        context.target().sendMessage(plugin.getEventManager().message("maddened-animals", "arrived")
                .replace("%town%", context.townName())
                .replace("%animal%", animalName)
                .replace("%count%", String.valueOf(spawnedAnimals.size()))
                .replace("%minutes%", String.valueOf(durationMinutes)));
        return occurrence;
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
                plugin.getLogger().warning("Ignoring unknown maddened animal type: " + key);
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

    private final class ActiveMaddenedAnimals implements EventOccurrence {
        private final RandomEventContext context;
        private final EntityType type;
        private final List<Mob> animals;
        private final int durationMinutes;
        private final double targetRange;
        private final double attackRangeSquared;
        private final double damage;
        private final double pathSpeed;
        private final int updateIntervalTicks;
        private final int attackCooldownTicks;
        private final Map<UUID, Long> nextAttackTicks = new HashMap<>();
        private BukkitTask behaviorTask;
        private BukkitTask expirationTask;
        private long elapsedTicks;
        private boolean finished;
        private boolean expired;

        private ActiveMaddenedAnimals(RandomEventContext context, EntityType type, List<Mob> animals,
                                      ConfigurationSection config) {
            this.context = context;
            this.type = type;
            this.animals = List.copyOf(animals);
            durationMinutes = Math.max(1, getInt(config, "duration-minutes", 15));
            targetRange = Math.max(4.0D, getDouble(config, "attack.target-range", 96.0D));
            double attackRange = Math.max(1.0D, getDouble(config, "attack.range", 1.8D));
            attackRangeSquared = attackRange * attackRange;
            damage = Math.max(0.0D, getDouble(config, "attack.damage", 2.0D));
            pathSpeed = Math.max(0.1D, getDouble(config, "attack.path-speed", 1.1D));
            updateIntervalTicks = Math.max(1, getInt(config, "attack.update-interval-ticks", 10));
            attackCooldownTicks = Math.max(1, getInt(config, "attack.cooldown-ticks", 20));
        }

        @Override
        public String eventTypeId() {
            return "maddened_animals";
        }

        @Override
        public UUID ownerId() {
            return context.target().getUniqueId();
        }

        @Override
        public UUID eventId() {
            return context.eventId();
        }

        @Override
        public String townName() {
            return context.townName();
        }

        @Override
        public boolean isOngoing() {
            return true;
        }

        @Override
        public boolean endsWhenTownEmpty() {
            return false;
        }

        @Override
        public void finish(boolean successful) {
            if (finished) {
                return;
            }
            finished = true;
            if (behaviorTask != null) {
                behaviorTask.cancel();
                behaviorTask = null;
            }
            if (expirationTask != null) {
                expirationTask.cancel();
                expirationTask = null;
            }
            for (Mob animal : animals) {
                if (!animal.isValid() || animal.isDead()) {
                    continue;
                }
                if (expired) {
                    animal.setHealth(0.0D);
                } else {
                    animal.remove();
                }
            }
            if (expired) {
                Player owner = plugin.getServer().getPlayer(ownerId());
                if (owner != null) {
                    owner.sendMessage(plugin.getEventManager().message("maddened-animals", "expired")
                            .replace("%town%", townName())
                            .replace("%minutes%", String.valueOf(durationMinutes)));
                }
            }
        }

        private void start() {
            long lifetimeTicks = durationMinutes * 1200L;
            expirationTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                expired = true;
                plugin.getEventManager().finishEvent(eventId(), false);
            }, lifetimeTicks);
            behaviorTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::tickBehavior, updateIntervalTicks, updateIntervalTicks);
        }

        private void tickBehavior() {
            if (finished) {
                return;
            }
            elapsedTicks += updateIntervalTicks;
            boolean hasLivingAnimals = false;
            for (Mob animal : animals) {
                if (!animal.isValid() || animal.isDead()) {
                    continue;
                }
                hasLivingAnimals = true;
                attackNearbyPlayer(animal);
            }
            if (!hasLivingAnimals) {
                plugin.getEventManager().finishEvent(eventId(), false);
            }
        }

        private void attackNearbyPlayer(Mob animal) {
            Player target = animal.getWorld().getPlayers().stream()
                    .filter(player -> player.isOnline() && !player.isDead())
                    .filter(player -> player.getLocation().distanceSquared(animal.getLocation())
                            <= targetRange * targetRange)
                    .min((first, second) -> Double.compare(
                            first.getLocation().distanceSquared(animal.getLocation()),
                            second.getLocation().distanceSquared(animal.getLocation())))
                    .orElse(null);
            if (target == null) {
                animal.setTarget(null);
                animal.getPathfinder().stopPathfinding();
                return;
            }

            animal.setTarget(target);
            Pathfinder pathfinder = animal.getPathfinder();
            pathfinder.moveTo(target, pathSpeed);
            if (animal.getLocation().distanceSquared(target.getLocation()) > attackRangeSquared) {
                return;
            }

            long nextAttack = nextAttackTicks.getOrDefault(animal.getUniqueId(), 0L);
            if (elapsedTicks < nextAttack) {
                return;
            }
            animal.swingMainHand();
            target.damage(damage, animal);
            nextAttackTicks.put(animal.getUniqueId(), elapsedTicks + attackCooldownTicks);
        }

        private double getDouble(ConfigurationSection section, String path, double defaultValue) {
            if (section == null) {
                return defaultValue;
            }
            double value = section.getDouble(path, defaultValue);
            return Double.isFinite(value) ? value : defaultValue;
        }
    }
}
