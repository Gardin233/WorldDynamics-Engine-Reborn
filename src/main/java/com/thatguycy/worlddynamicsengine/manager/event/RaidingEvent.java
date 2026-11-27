package com.thatguycy.worlddynamicsengine.manager.event;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Executes a selected raid; timing, chance, and target selection belong to EventManager. */
final class RaidingEvent implements AttackGameEvent {
    private final WorldDynamicsEngine plugin;

    RaidingEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "raiding";
    }

    @Override
    public double minimumTownWealth() {
        double minimum = plugin.getEventManager().getConfig()
                .getDouble("raiding.minimum-town-wealth", 10000.0D);
        return Double.isFinite(minimum) ? Math.max(0.0D, minimum) : 10000.0D;
    }

    @Override
    public EventOccurrence execute(RandomEventContext context) {
        Player owner = context.target();
        var config = plugin.getEventManager().getConfig();
        List<EntityType> lineup = RaidComposition.create(
                context.strengthPoints(),
                configuredMobCosts(config.getConfigurationSection("raiding.strength.mob-costs")),
                Math.max(0, config.getInt("raiding.maximum-mobs-per-raid", 40)),
                new Random());
        if (lineup.isEmpty()) {
            return null;
        }

        int attemptsPerMob = Math.max(1, config.getInt("raiding.spawn.attempts-per-mob", 12));
        Random random = new Random();
        List<Location> spawnLocations = WildernessAnimalSpawns.findBorderLocations(
                context.town(), lineup.size(), attemptsPerMob, random);
        int spawnedCount = 0;
        UUID raidId = context.eventId();
        UUID ownerId = owner.getUniqueId();
        var ownerKey = plugin.getEventManager().getRaidOwnerKey();

        for (int mobIndex = 0; mobIndex < lineup.size() && mobIndex < spawnLocations.size(); mobIndex++) {
            EntityType type = lineup.get(mobIndex);
            Location spawn = spawnLocations.get(mobIndex);
            if (spawn == null || !TownyWilderness.isUnclaimed(spawn)) {
                continue;
            }
            Entity entity = spawn.getWorld().spawnEntity(spawn, type);
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                continue;
            }
            mob.setPersistent(true);
            mob.getPersistentDataContainer().set(
                    ownerKey, PersistentDataType.STRING, raidId.toString());
            mob.setTarget(owner);
            spawnedCount++;
        }

        if (spawnedCount == 0) {
            return null;
        }
        ActiveRaid occurrence = new ActiveRaid(
                plugin, raidId, ownerId, context.town().getName(), context.townPlayers());
        owner.sendMessage(plugin.getEventManager().message("started")
                .replace("%town%", context.town().getName())
                .replace("%count%", String.valueOf(spawnedCount))
                .replace("%points%", String.valueOf(context.strengthPoints())));
        return occurrence;
    }

    private Map<EntityType, Integer> configuredMobCosts(ConfigurationSection section) {
        if (section == null) {
            plugin.getLogger().warning("Missing raiding.strength.mob-costs configuration; raids are disabled.");
            return Map.of();
        }
        var costs = new EnumMap<EntityType, Integer>(EntityType.class);
        for (String key : section.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring unknown raid mob type in events.yml: " + key);
                continue;
            }
            int cost = section.getInt(key, 0);
            if (!RaidComposition.isRaidType(type) || cost <= 0) {
                plugin.getLogger().warning("Ignoring invalid raid mob cost in events.yml: " + key);
                continue;
            }
            costs.put(type, cost);
        }
        return costs;
    }

    private static final class ActiveRaid implements EventOccurrence {
        private final WorldDynamicsEngine plugin;
        private final UUID raidId;
        private final UUID ownerId;
        private final String townName;
        private final RaidSuccessTracker successTracker = new RaidSuccessTracker();
        private final Map<UUID, String> participantWorlds = new HashMap<>();
        private final Random random = new Random();

        private ActiveRaid(WorldDynamicsEngine plugin, UUID raidId, UUID ownerId, String townName,
                           List<Player> townPlayers) {
            this.plugin = plugin;
            this.raidId = raidId;
            this.ownerId = ownerId;
            this.townName = townName;
            townPlayers.forEach(this::addParticipant);
            if (townPlayers.stream().noneMatch(player -> player.getUniqueId().equals(ownerId))) {
                successTracker.addParticipant(ownerId);
            }
        }

        @Override
        public boolean isOngoing() {
            return true;
        }

        @Override
        public UUID ownerId() {
            return ownerId;
        }

        @Override
        public UUID eventId() {
            return raidId;
        }

        @Override
        public String eventTypeId() {
            return "raiding";
        }

        @Override
        public String townName() {
            return townName;
        }

        @Override
        public void onPlayerDeath(UUID playerId) {
            successTracker.recordDeath(playerId);
        }

        @Override
        public void updateTownPlayers(List<Player> onlinePlayers) {
            onlinePlayers.forEach(this::addParticipant);
            List<Player> livingPlayers = onlinePlayers.stream()
                    .filter(player -> player.isOnline() && !player.isDead())
                    .toList();
            Player target = livingPlayers.isEmpty()
                    ? null : livingPlayers.get(random.nextInt(livingPlayers.size()));
            updateRaidTargets(target);
        }

        @Override
        public boolean wasSuccessful() {
            return successTracker.wasSuccessful();
        }

        @Override
        public void finish(boolean successful) {
            removeMobs();
            if (successful) {
                plugin.getEventManager().chargeSuccessfulRaidLoss(
                        ownerId, participantWorlds.getOrDefault(ownerId, ""));
            }
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner == null) {
                return;
            }
            if (successful) {
                owner.sendMessage(plugin.getEventManager().message("ended"));
            } else {
                owner.sendMessage(plugin.getEventManager().message("cancelled"));
            }
        }

        private void addParticipant(Player player) {
            UUID playerId = player.getUniqueId();
            successTracker.addParticipant(playerId);
            participantWorlds.putIfAbsent(playerId, player.getWorld().getName());
        }

        private void updateRaidTargets(Player target) {
            var ownerKey = plugin.getEventManager().getRaidOwnerKey();
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String taggedOwner = entity.getPersistentDataContainer().get(
                            ownerKey, PersistentDataType.STRING);
                    if (raidId.toString().equals(taggedOwner) && entity instanceof Mob mob) {
                        mob.setTarget(target);
                    }
                }
            }
        }

        private void removeMobs() {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String taggedOwner = entity.getPersistentDataContainer().get(
                            plugin.getEventManager().getRaidOwnerKey(), PersistentDataType.STRING);
                    if (raidId.toString().equals(taggedOwner)) {
                        entity.remove();
                    }
                }
            }
        }
    }

}
