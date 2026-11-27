package com.thatguycy.worlddynamicsengine.manager.event;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.util.BoundingBox;

/** Spawns strength-scaled phantoms above claimed town plots under open sky. */
final class PhantomMigrationEvent implements AttackGameEvent {
    private static final int MAXIMUM_ALLOWED_PHANTOMS = 64;

    private final WorldDynamicsEngine plugin;
    private final Random random = new Random();

    PhantomMigrationEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "phantom_migration";
    }

    @Override
    public EventOccurrence execute(RandomEventContext context) {
        var config = plugin.getEventManager().getConfig();
        ConfigurationSection eventConfig = config.getConfigurationSection("phantom-migration");
        int maximumCount = Math.min(MAXIMUM_ALLOWED_PHANTOMS,
                getInt(eventConfig, "maximum-phantoms", 40));
        int minimumCount = Math.min(maximumCount,
                Math.max(1, getInt(eventConfig, "minimum-phantoms", 3)));
        int count = PhantomMigrationComposition.phantomCount(
                context.strengthPoints(),
                getDouble(eventConfig, "phantoms-per-strength-point", 0.5D),
                minimumCount, maximumCount);
        if (count <= 0) {
            return null;
        }

        int minimumHeight = Math.max(10,
                Math.min(256, getInt(eventConfig, "minimum-height-above-ground", 10)));
        ConfigurationSection spawnConfig = config.getConfigurationSection("phantom-migration.spawn");
        int attemptsPerPhantom = Math.max(1, Math.min(200,
                getInt(spawnConfig, "attempts-per-phantom", 20)));
        List<Location> locations = findSpawnLocations(
                context.town(), count, minimumCount, minimumHeight, attemptsPerPhantom);
        if (locations.size() < minimumCount) {
            return null;
        }

        List<Phantom> spawnedPhantoms = new ArrayList<>();
        for (Location location : locations) {
            Entity entity = location.getWorld().spawnEntity(location, EntityType.PHANTOM);
            if (entity instanceof Phantom phantom) {
                spawnedPhantoms.add(phantom);
            } else {
                entity.remove();
            }
        }
        if (spawnedPhantoms.size() < minimumCount) {
            spawnedPhantoms.forEach(Entity::remove);
            return null;
        }

        context.target().sendMessage(plugin.getEventManager().message("phantom-migration", "arrived")
                .replace("%town%", context.townName())
                .replace("%count%", String.valueOf(spawnedPhantoms.size()))
                .replace("%points%", String.valueOf(context.strengthPoints())));
        return new Arrival(context.eventId(), context.target().getUniqueId(), context.townName());
    }

    private List<Location> findSpawnLocations(Town town, int count, int minimumCount,
                                              int minimumHeight, int attemptsPerPhantom) {
        if (town == null || town.getWorld() == null) {
            return List.of();
        }
        List<TownBlock> claimedPlots = town.getTownBlocks().stream()
                .filter(plot -> plot.getWorldCoord() != null
                        && plot.getWorldCoord().getBukkitWorld() != null
                        && plot.getWorldCoord().getBukkitWorld().equals(town.getWorld()))
                .toList();
        if (claimedPlots.isEmpty()) {
            return List.of();
        }

        List<Location> locations = new ArrayList<>();
        Set<Long> usedColumns = new HashSet<>();
        for (int phantom = 0; phantom < count; phantom++) {
            for (int attempt = 0; attempt < attemptsPerPhantom; attempt++) {
                TownBlock plot = claimedPlots.get(random.nextInt(claimedPlots.size()));
                Location spawn = findOpenSkyLocation(plot, minimumHeight);
                if (spawn == null || !isClaimedBy(town, spawn)) {
                    continue;
                }
                long column = ((long) spawn.getBlockX() << 32) ^ (spawn.getBlockZ() & 0xffffffffL);
                if (usedColumns.add(column)) {
                    locations.add(spawn);
                    break;
                }
            }
            if (locations.size() >= count) {
                break;
            }
            if (locations.size() + (count - phantom - 1) < minimumCount) {
                break;
            }
        }
        return List.copyOf(locations);
    }

    private Location findOpenSkyLocation(TownBlock plot, int minimumHeight) {
        WorldCoord worldCoord = plot.getWorldCoord();
        World world = worldCoord.getBukkitWorld();
        if (world == null) {
            return null;
        }
        BoundingBox bounds = worldCoord.getBoundingBox();
        int minX = (int) Math.floor(bounds.getMinX());
        int maxX = (int) Math.ceil(bounds.getMaxX()) - 1;
        int minZ = (int) Math.floor(bounds.getMinZ());
        int maxZ = (int) Math.ceil(bounds.getMaxZ()) - 1;
        int x = minX + random.nextInt(Math.max(1, maxX - minX + 1));
        int z = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));

        int topY = world.getHighestBlockYAt(x, z);
        int groundY = findHighestSolidBlock(world, x, z, topY);
        if (groundY < world.getMinHeight()) {
            return null;
        }
        int spawnY = groundY + minimumHeight + 1;
        if (spawnY + 1 >= world.getMaxHeight()
                || topY >= spawnY) {
            return null;
        }
        return new Location(world, x + 0.5D, spawnY, z + 0.5D);
    }

    private int findHighestSolidBlock(World world, int x, int z, int topY) {
        for (int y = Math.min(topY, world.getMaxHeight() - 1); y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return y;
            }
        }
        return world.getMinHeight() - 1;
    }

    private boolean isClaimedBy(Town town, Location location) {
        TownBlock plot = TownyAPI.getInstance().getTownBlock(location);
        return plot != null && town.equals(plot.getTownOrNull());
    }

    private static int getInt(ConfigurationSection section, String path, int defaultValue) {
        return section == null ? defaultValue : section.getInt(path, defaultValue);
    }

    private static double getDouble(ConfigurationSection section, String path, double defaultValue) {
        if (section == null) {
            return defaultValue;
        }
        double value = section.getDouble(path, defaultValue);
        return Double.isFinite(value) ? value : defaultValue;
    }

    private record Arrival(UUID eventId, UUID ownerId, String townName) implements EventOccurrence {
        @Override
        public String eventTypeId() {
            return "phantom_migration";
        }

        @Override
        public boolean isOngoing() {
            return false;
        }
    }
}
