package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeDefinition;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Handles town bereavement and configured reactions to a player's change of faith. */
public final class FaithConflictManager implements Listener {
    private static final String BETRAYAL_DOCTRINE = "namespace:wder/betrayal";
    private static final Set<String> SEVERE_BETRAYAL_ATTITUDES = Set.of("disgust", "abhor", "hostile");
    private static final String DEATH_REBIRTH_SOURCE = "rebirth";
    private static final String TOWN_DEATH_SOURCE = "townDeath";
    private static final String BETRAYAL_SOURCE = "betrayal";
    private static final String BETRAYER_HUNT_SOURCE = "betrayalHunt";

    private final WorldDynamicsEngine plugin;

    public FaithConflictManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    /** Called only after a personal conversion has passed validation and payment. */
    public void onPersonalFaithChanged(WDEresident convert, String oldFaith) {
        if (convert == null) return;
        convert.setBetrayedReligionName("");
        if (!plugin.religionEnabled || !hasSevereBetrayalAttitude(oldFaith)) {
            plugin.getResidentManager().saveResident(convert);
            return;
        }

        String betrayedFaith = oldFaith.trim();
        convert.setBetrayedReligionName(betrayedFaith);
        plugin.getResidentManager().saveResident(convert);
        String playerName = convert.getUsername();
        if (playerName == null || playerName.isBlank()) playerName = "Unknown";

        for (WDEresident follower : plugin.getResidentManager().getAllResidents()) {
            if (follower.getUserUUID().equals(convert.getUserUUID())
                    || !sameFaith(follower.getReligionName(), betrayedFaith)) {
                continue;
            }
            plugin.getMoodManager().applyTemporaryMoodChange(follower.getUserUUID(), BETRAYAL_SOURCE,
                    configuredDelta("betrayalPenalty", -4));
            Player online = Bukkit.getPlayer(follower.getUserUUID());
            if (online != null) {
                online.sendMessage(plugin.formatLocaleMessage(plugin.userLang, "faith_betrayal_broadcast",
                        playerName, betrayedFaith));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.getMoodManager().applyTemporaryMoodChange(victim.getUniqueId(), DEATH_REBIRTH_SOURCE,
                configuredDelta("deathRebirthBonus", 10));
        applyTownBereavement(victim);
        awardBetrayerHunt(victim, victim.getKiller());

        WDEresident victimData = plugin.getResidentManager().getResident(victim.getUniqueId());
        if (victimData != null && !victimData.getBetrayedReligionName().isBlank()) {
            victimData.setBetrayedReligionName("");
            plugin.getResidentManager().saveResident(victimData);
        }
    }

    private void applyTownBereavement(Player victim) {
        if (!plugin.religionEnabled) return;
        Resident victimResident = TownyAPI.getInstance().getResident(victim);
        Town town = victimResident == null ? null : victimResident.getTownOrNull();
        if (town == null) return;
        int penalty = configuredDelta("townDeathPenalty", -4);
        for (Resident townResident : town.getResidents()) {
            UUID uuid = townResident.getUUID();
            if (uuid.equals(victim.getUniqueId())) continue;
            plugin.getMoodManager().applyTemporaryMoodChange(uuid, TOWN_DEATH_SOURCE, penalty);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage(plugin.formatLocaleMessage(plugin.userLang, "mood_town_member_died",
                        victim.getName()));
            }
        }
    }

    private void awardBetrayerHunt(Player victim, Player killer) {
        if (!plugin.religionEnabled || killer == null || killer.equals(victim)) return;
        WDEresident victimData = plugin.getResidentManager().getResident(victim.getUniqueId());
        WDEresident killerData = plugin.getResidentManager().getResident(killer.getUniqueId());
        if (victimData == null || killerData == null) return;
        String betrayedFaith = victimData.getBetrayedReligionName();
        if (betrayedFaith.isBlank() || !sameFaith(killerData.getReligionName(), betrayedFaith)
                || !hasSevereBetrayalAttitude(betrayedFaith)) {
            return;
        }

        plugin.getMoodManager().applyTemporaryMoodChange(killer.getUniqueId(), BETRAYER_HUNT_SOURCE,
                configuredDelta("betrayerHuntBonus", 10));
    }

    private boolean hasSevereBetrayalAttitude(String faithName) {
        if (faithName == null || faithName.isBlank()) return false;
        WDEreligion religion = plugin.getReligionManager().getReligion(faithName);
        if (religion == null) return false;
        String attitudeId = religion.getDoctrineAttitude(BETRAYAL_DOCTRINE);
        IdeologyDoctrineAttitudeDefinition attitude =
                plugin.getReligionManager().getDoctrineAttitudeDefinition(BETRAYAL_DOCTRINE, attitudeId);
        if (attitude == null) return false;
        String id = attitude.getId();
        int separator = id.lastIndexOf('/');
        String localId = (separator >= 0 ? id.substring(separator + 1) : id).toLowerCase(Locale.ROOT);
        return SEVERE_BETRAYAL_ATTITUDES.contains(localId);
    }

    private int configuredDelta(String key, int fallback) {
        var config = plugin.getIdeologyConfig();
        return config == null ? fallback : config.getInt("mood.events." + key, fallback);
    }

    private static boolean sameFaith(String first, String second) {
        return first != null && second != null && !first.isBlank() && !second.isBlank()
                && first.trim().equalsIgnoreCase(second.trim());
    }
}
