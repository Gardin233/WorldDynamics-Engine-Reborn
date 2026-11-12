package com.thatguycy.worlddynamicsengine.integration.papi;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Exposes the player's effective mood as %wder_mood%. */
public final class WDERMoodExpansion extends PlaceholderExpansion {
    private final WorldDynamicsEngine plugin;

    public WDERMoodExpansion(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "wder";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || !"mood".equalsIgnoreCase(params)) {
            return null;
        }
        if (plugin.getMoodManager() == null) {
            return "50";
        }
        return Integer.toString(plugin.getMoodManager().getMood(player.getUniqueId()));
    }
}
