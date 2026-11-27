package com.thatguycy.worlddynamicsengine.manager.event;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

final class NetherPortalSite {
    private static final int FRAME_HEIGHT = 5;
    private static final int FRAME_MIN_WIDTH = -1;
    private static final int FRAME_MAX_WIDTH = 2;

    private final World world;
    private final Axis axis;
    private final List<BlockPosition> framePositions;
    private final Set<BlockPosition> portalPositions;

    private NetherPortalSite(World world, Axis axis, List<BlockPosition> framePositions,
                             Set<BlockPosition> portalPositions) {
        this.world = world;
        this.axis = axis;
        this.framePositions = List.copyOf(framePositions);
        this.portalPositions = Set.copyOf(portalPositions);
    }

    static NetherPortalSite find(Town town, int attempts, Random random) {
        if (town == null || town.getWorld() == null) {
            return null;
        }
        World world = town.getWorld();
        List<WorldCoord> wildernessPlots = WildernessAnimalSpawns.findWildernessPlots(town);
        if (wildernessPlots.isEmpty()) {
            return null;
        }
        Collections.shuffle(wildernessPlots, random);

        for (int attempt = 0; attempt < Math.max(1, attempts); attempt++) {
            WorldCoord plot = wildernessPlots.get(attempt % wildernessPlots.size());
            Location candidate = WildernessAnimalSpawns.randomSafeLocation(plot, random);
            if (candidate == null) {
                continue;
            }
            Axis axis = random.nextBoolean() ? Axis.X : Axis.Z;
            NetherPortalSite site = create(
                    world, candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ(), axis);
            if (site != null && site.isValid()) {
                return site;
            }
        }
        return null;
    }

    void place() {
        for (BlockPosition position : framePositions) {
            world.getBlockAt(position.x(), position.y(), position.z()).setType(Material.OBSIDIAN, false);
        }
        for (BlockPosition position : portalPositions) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            block.setType(Material.NETHER_PORTAL, false);
            BlockData data = block.getBlockData();
            if (data instanceof Orientable orientable) {
                orientable.setAxis(axis);
                block.setBlockData(orientable, false);
            }
        }
    }

    boolean containsFrameBlock(Block block) {
        return block.getWorld().equals(world)
                && framePositions.contains(BlockPosition.from(block));
    }

    boolean containsPortalLocation(Location location) {
        return location != null
                && world.equals(location.getWorld())
                && portalPositions.contains(BlockPosition.from(location));
    }

    boolean isProtected(Location location, int radius) {
        if (location == null || !world.equals(location.getWorld())) {
            return false;
        }
        BlockPosition position = BlockPosition.from(location);
        if (framePositions.contains(position) || portalPositions.contains(position)) {
            return true;
        }
        Location center = center();
        double dx = position.x() + 0.5D - center.getX();
        double dz = position.z() + 0.5D - center.getZ();
        double safeRadius = Math.max(0, radius);
        return dx * dx + dz * dz <= safeRadius * safeRadius;
    }

    Location center() {
        BlockPosition firstFrame = framePositions.get(0);
        double centerX = axis == Axis.X ? firstFrame.x() + 2.0D : firstFrame.x() + 0.5D;
        double centerZ = axis == Axis.Z ? firstFrame.z() + 2.0D : firstFrame.z() + 0.5D;
        return new Location(world, centerX, firstFrame.y() + 2.5D, centerZ);
    }

    World world() {
        return world;
    }

    boolean isWithinCorrespondingNetherChunks(World netherWorld, Location location, int chunkRadius) {
        if (netherWorld == null || location == null
                || !netherWorld.equals(location.getWorld())
                || world.getEnvironment() != World.Environment.NORMAL
                || netherWorld.getEnvironment() != World.Environment.NETHER) {
            return false;
        }

        Location portalCenter = center();
        return NetherPortalProtectionRules.isWithinNetherChunkRadius(
                portalCenter.getX(), portalCenter.getZ(),
                location.getBlockX(), location.getBlockZ(), chunkRadius);
    }

    Location ejectionLocation(Location location) {
        Location portalCenter = center();
        double side = axis == Axis.X
                ? location.getZ() - portalCenter.getZ()
                : location.getX() - portalCenter.getX();
        double direction = side < 0.0D ? -1.0D : 1.0D;

        for (double distance = 1.5D; distance <= 4.5D; distance += 1.0D) {
            double x = axis == Axis.Z
                    ? portalCenter.getX() + direction * distance
                    : location.getX();
            double z = axis == Axis.X
                    ? portalCenter.getZ() + direction * distance
                    : location.getZ();
            int blockX = Location.locToBlock(x);
            int blockZ = Location.locToBlock(z);
            int feetY = world.getHighestBlockYAt(blockX, blockZ) + 1;
            if (feetY + 1 >= world.getMaxHeight() || feetY < world.getMinHeight()) {
                continue;
            }

            Location candidate = new Location(
                    world, blockX + 0.5D, feetY, blockZ + 0.5D,
                    location.getYaw(), location.getPitch());
            if (candidate.getBlock().isPassable()
                    && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()
                    && candidate.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType().isSolid()) {
                return candidate;
            }
        }

        double x = axis == Axis.Z
                ? portalCenter.getX() + direction * 5.5D
                : location.getX();
        double z = axis == Axis.X
                ? portalCenter.getZ() + direction * 5.5D
                : location.getZ();
        return new Location(world, x, location.getY(), z, location.getYaw(), location.getPitch());
    }

    void remove(Block preserveFrameBlock) {
        BlockPosition preserve = preserveFrameBlock == null ? null : BlockPosition.from(preserveFrameBlock);
        for (BlockPosition position : portalPositions) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (block.getType() == Material.NETHER_PORTAL) {
                block.setType(Material.AIR, false);
            }
        }
        for (BlockPosition position : framePositions) {
            if (position.equals(preserve)) {
                continue;
            }
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (block.getType() == Material.OBSIDIAN) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private boolean isValid() {
        int baseY = framePositions.get(0).y();
        if (baseY <= world.getMinHeight() || baseY + FRAME_HEIGHT - 1 >= world.getMaxHeight()) {
            return false;
        }
        for (BlockPosition position : allPortalPositions()) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (block.getType() == Material.NETHER_PORTAL
                    || !block.isPassable()
                    || !TownyWilderness.isUnclaimed(block.getLocation())) {
                return false;
            }
        }
        for (BlockPosition position : framePositions) {
            if (position.y() != baseY) {
                continue;
            }
            Block floor = world.getBlockAt(position.x(), baseY - 1, position.z());
            if (!floor.getType().isSolid() || !TownyWilderness.isUnclaimed(floor.getLocation())) {
                return false;
            }
        }
        return true;
    }

    private List<BlockPosition> allPortalPositions() {
        List<BlockPosition> positions = new ArrayList<>(framePositions);
        positions.addAll(portalPositions);
        return positions;
    }

    private static NetherPortalSite create(World world, int baseX, int baseY, int baseZ, Axis axis) {
        List<BlockPosition> frame = new ArrayList<>();
        Set<BlockPosition> portal = new HashSet<>();
        for (int yOffset = 0; yOffset < FRAME_HEIGHT; yOffset++) {
            for (int widthOffset = FRAME_MIN_WIDTH; widthOffset <= FRAME_MAX_WIDTH; widthOffset++) {
                BlockPosition position = axis == Axis.X
                        ? new BlockPosition(baseX + widthOffset, baseY + yOffset, baseZ)
                        : new BlockPosition(baseX, baseY + yOffset, baseZ + widthOffset);
                boolean isFrame = yOffset == 0 || yOffset == FRAME_HEIGHT - 1
                        || widthOffset == FRAME_MIN_WIDTH || widthOffset == FRAME_MAX_WIDTH;
                if (isFrame) {
                    frame.add(position);
                } else {
                    portal.add(position);
                }
            }
        }
        return new NetherPortalSite(world, axis, frame, portal);
    }

    private record BlockPosition(int x, int y, int z) {
        private static BlockPosition from(Block block) {
            return new BlockPosition(block.getX(), block.getY(), block.getZ());
        }

        private static BlockPosition from(Location location) {
            return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
