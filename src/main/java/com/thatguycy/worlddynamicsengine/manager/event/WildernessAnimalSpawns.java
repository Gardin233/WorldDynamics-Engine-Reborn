package com.thatguycy.worlddynamicsengine.manager.event;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Finds safe wilderness locations along a town's claimed-plot border. */
final class WildernessAnimalSpawns {
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private WildernessAnimalSpawns() {
    }

    static Location findGroupCenter(Town town, int attempts, Random random) {
        List<WorldCoord> wildernessPlots = findWildernessPlots(town);
        if (wildernessPlots.isEmpty()) {
            return null;
        }

        Collections.shuffle(wildernessPlots, random);
        for (int attempt = 0; attempt < Math.max(1, attempts); attempt++) {
            WorldCoord plot = wildernessPlots.get(attempt % wildernessPlots.size());
            Location candidate = randomSafeLocation(plot, random);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    static List<Location> findBorderLocations(Town town, int count, int attemptsPerLocation,
                                              Random random) {
        if (count <= 0) {
            return List.of();
        }
        List<WorldCoord> wildernessPlots = findWildernessPlots(town);
        if (wildernessPlots.isEmpty()) {
            return List.of();
        }
        int attempts = Math.max(1, Math.min(200, attemptsPerLocation));
        List<Location> locations = new ArrayList<>();
        for (int mob = 0; mob < count; mob++) {
            for (int attempt = 0; attempt < attempts; attempt++) {
                WorldCoord plot = wildernessPlots.get(random.nextInt(wildernessPlots.size()));
                Location candidate = randomSafeLocation(plot, random);
                if (candidate != null && isSeparated(candidate, locations)) {
                    locations.add(candidate);
                    break;
                }
            }
        }
        return List.copyOf(locations);
    }

    static List<Location> findGroupLocations(Location center, int count, int groupRadius,
                                             int attemptsPerAnimal, Random random) {
        if (center == null || center.getWorld() == null || count <= 0) {
            return List.of();
        }
        List<Location> locations = new ArrayList<>();
        locations.add(center);
        int radiusLimit = Math.max(1, groupRadius);
        int attempts = Math.max(1, attemptsPerAnimal);
        for (int animal = 1; animal < count; animal++) {
            for (int attempt = 0; attempt < attempts; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 1.0D + random.nextDouble() * Math.max(0, radiusLimit - 1);
                int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
                Location candidate = safeWildernessLocation(center.getWorld(), x, z);
                if (candidate != null && isSeparated(candidate, locations)) {
                    locations.add(candidate);
                    break;
                }
            }
        }
        return List.copyOf(locations);
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

    static List<WorldCoord> findWildernessPlots(Town town) {
        if (town == null) {
            return List.of();
        }
        World townWorld = town.getWorld();
        if (townWorld == null) {
            return List.of();
        }
        Set<WorldCoord> wildernessPlots = new HashSet<>();
        for (TownBlock townBlock : town.getTownBlocks()) {
            WorldCoord townPlot = townBlock.getWorldCoord();
            if (townPlot == null) {
                continue;
            }
            for (int[] direction : CARDINAL_DIRECTIONS) {
                WorldCoord adjacentPlot = townPlot.add(direction[0], direction[1]);
                World world = adjacentPlot.getBukkitWorld();
                if (world == null || !world.equals(townWorld)) {
                    continue;
                }
                BoundingBox bounds = adjacentPlot.getBoundingBox();
                Location plotCenter = new Location(world, bounds.getCenterX(),
                        world.getMinHeight(), bounds.getCenterZ());
                if (TownyWilderness.isUnclaimed(plotCenter)) {
                    wildernessPlots.add(adjacentPlot);
                }
            }
        }
        return new ArrayList<>(wildernessPlots);
    }

    static Location randomSafeLocation(WorldCoord plot, Random random) {
        BoundingBox bounds = plot.getBoundingBox();
        int minX = (int) Math.floor(bounds.getMinX());
        int maxX = (int) Math.ceil(bounds.getMaxX()) - 1;
        int minZ = (int) Math.floor(bounds.getMinZ());
        int maxZ = (int) Math.ceil(bounds.getMaxZ()) - 1;
        int x = minX + random.nextInt(Math.max(1, maxX - minX + 1));
        int z = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));
        return safeWildernessLocation(plot.getBukkitWorld(), x, z);
    }

    private static boolean isSeparated(Location candidate, List<Location> locations) {
        for (Location location : locations) {
            if (!candidate.getWorld().equals(location.getWorld())) {
                continue;
            }
            double dx = candidate.getX() - location.getX();
            double dz = candidate.getZ() - location.getZ();
            if (dx * dx + dz * dz < 0.81D) {
                return false;
            }
        }
        return true;
    }
}
