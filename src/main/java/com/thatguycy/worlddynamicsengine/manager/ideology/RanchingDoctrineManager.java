package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

/** Applies the ranching doctrine when a player successfully breeds an animal. */
public final class RanchingDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/ranching";
    private static final String CONFIG_PATH = "mood.doctrineActions.ranching.enabled";

    private final WorldDynamicsEngine plugin;

    public RanchingDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            apply(player);
        }
    }

    public void apply(Player player) {
        if (player == null || !plugin.religionEnabled || !player.isOnline() || player.isDead()
                || plugin.getMoodManager() == null
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            return;
        }
        var config = plugin.getIdeologyConfig();
        if (config != null && !config.getBoolean(CONFIG_PATH, true)) return;

        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) return;

        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        Boolean compliant = RanchingDoctrineCalculator.assess(attitude);
        if (attitude == null || compliant == null) return;

        int weight = Math.max(0, plugin.getReligionManager().getDoctrineMoodWeight(DOCTRINE_ID, attitude));
        int moodChange = weight * Math.max(0,
                plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                        DOCTRINE_ID, attitude, compliant, null));
        var moodEntry = new DoctrineMoodEntry(DOCTRINE_ID, attitude, compliant, moodChange);
        plugin.getMoodManager().applyDoctrineMoodChanges(player,
                compliant ? moodChange : 0,
                compliant ? 0 : moodChange,
                moodEntry.source());
        if (!compliant && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
        }
    }
}
