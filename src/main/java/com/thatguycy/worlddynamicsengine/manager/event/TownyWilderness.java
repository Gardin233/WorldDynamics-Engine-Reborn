package com.thatguycy.worlddynamicsengine.manager.event;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Location;

/** Shared check for locations outside every Towny town and claimed town block. */
final class TownyWilderness {
    private TownyWilderness() {
    }

    static boolean isUnclaimed(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        TownyAPI towny = TownyAPI.getInstance();
        return towny.getTown(location) == null && towny.getTownBlock(location) == null;
    }
}
