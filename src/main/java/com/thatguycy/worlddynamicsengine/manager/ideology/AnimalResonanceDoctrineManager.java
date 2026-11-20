package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Applies animal-resonance mood changes for player kills and successful feeding. */
public final class AnimalResonanceDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/animal_resonance";
    private static final String CONFIG_PATH = "mood.doctrineActions.animalResonance.enabled";

    private final WorldDynamicsEngine plugin;

    public AnimalResonanceDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimalDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals animal)) return;
        Player killer = animal.getKiller();
        if (killer != null) apply(killer, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimalInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals animal)) return;
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack held = getHandItem(player, hand);
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) return;

        Material beforeType = held.getType();
        int beforeAmount = held.getAmount();
        UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player onlinePlayer = Bukkit.getPlayer(playerUuid);
            if (onlinePlayer == null || !onlinePlayer.isOnline() || onlinePlayer.isDead()
                    || !animal.isValid()) {
                return;
            }
            ItemStack after = getHandItem(onlinePlayer, hand);
            Material afterType = after == null ? Material.AIR : after.getType();
            int afterAmount = after == null ? 0 : after.getAmount();
            if (AnimalResonanceDoctrineCalculator.itemWasConsumed(
                    beforeType, beforeAmount, afterType, afterAmount)) {
                apply(onlinePlayer, true);
            }
        });
    }

    private void apply(Player player, boolean feeding) {
        var config = plugin.getIdeologyConfig();
        if (config != null && !config.getBoolean(CONFIG_PATH, true)) return;
        if (!plugin.religionEnabled || player == null || !player.isOnline() || player.isDead()
                || plugin.getMoodManager() == null
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            return;
        }

        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) return;

        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        Boolean compliant = feeding
                ? AnimalResonanceDoctrineCalculator.assessFeeding(attitude)
                : AnimalResonanceDoctrineCalculator.assessKilling(attitude);
        if (attitude == null || compliant == null) return;

        int weight = Math.max(0, plugin.getReligionManager().getDoctrineMoodWeight(DOCTRINE_ID, attitude));
        int perWeight = Math.max(0, plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                DOCTRINE_ID, attitude, compliant, null));
        int moodChange = (int) Math.min(100L, (long) weight * perWeight);
        var entry = new DoctrineMoodEntry(DOCTRINE_ID, attitude, compliant, moodChange);
        plugin.getMoodManager().applyDoctrineMoodChanges(player,
                compliant ? moodChange : 0,
                compliant ? 0 : moodChange,
                entry.source());
        if (!compliant && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
        }
    }

    private static ItemStack getHandItem(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }
}
