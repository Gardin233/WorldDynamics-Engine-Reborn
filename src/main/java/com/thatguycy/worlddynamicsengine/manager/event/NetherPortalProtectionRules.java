package com.thatguycy.worlddynamicsengine.manager.event;

final class NetherPortalProtectionRules {
    private NetherPortalProtectionRules() {
    }

    static boolean isWithinNetherChunkRadius(double overworldX, double overworldZ,
                                             int netherBlockX, int netherBlockZ,
                                             int chunkRadius) {
        if (!Double.isFinite(overworldX) || !Double.isFinite(overworldZ)) {
            return false;
        }

        long centerChunkX = (long) Math.floor(overworldX / 128.0D);
        long centerChunkZ = (long) Math.floor(overworldZ / 128.0D);
        long portalChunkX = Math.floorDiv(netherBlockX, 16);
        long portalChunkZ = Math.floorDiv(netherBlockZ, 16);
        long deltaX = portalChunkX - centerChunkX;
        long deltaZ = portalChunkZ - centerChunkZ;
        long radius = Math.max(0, chunkRadius);
        return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
    }
}
