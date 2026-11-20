package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Awards the field-battle doctrine once per combat session fought in Towny wilderness. */
public final class FieldBattleDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/field_battle";
    private static final String CONFIG_PATH = "mood.doctrineActions.fieldBattle.";

    private static final class CombatSession {
        private boolean wildernessBonusAwarded;
        private BukkitTask expirationTask;
    }

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, CombatSession> combatSessions = new HashMap<>();

    public FieldBattleDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled() || !plugin.religionEnabled || event.getFinalDamage() <= 0
                || !isCombatTarget(event.getEntity()) || !hasLivingAttacker(event.getDamager())) {
            return;
        }

        Player attacker = findPlayerAttacker(event.getDamager());
        if (attacker != null && attacker != event.getEntity()) recordCombat(attacker);
        if (event.getEntity() instanceof Player victim && isOnlineAndAlive(victim)) {
            recordCombat(victim);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearCombat(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        clearCombat(event.getEntity().getUniqueId());
    }

    private void recordCombat(Player player) {
        if (!isOnlineAndAlive(player) || !plugin.getMoodManager().shouldProcessMoodAction(player)) return;

        UUID uuid = player.getUniqueId();
        CombatSession session = combatSessions.computeIfAbsent(uuid, ignored -> new CombatSession());
        if (session.expirationTask != null) session.expirationTask.cancel();
        session.expirationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (combatSessions.get(uuid) == session) combatSessions.remove(uuid);
        }, getCombatTimeoutTicks());

        if (!session.wildernessBonusAwarded && isWilderness(player)) {
            awardWildernessBonus(player, session);
        }
    }

    private void awardWildernessBonus(Player player, CombatSession session) {
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) return;

        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        if (!FieldBattleDoctrineCalculator.earnsWildernessBonus(attitude, true)) return;

        plugin.getMoodManager().applyDoctrineOutcome(player, DOCTRINE_ID, attitude, true);
        session.wildernessBonusAwarded = true;
    }

    private boolean isWilderness(Player player) {
        return TownyAPI.getInstance().getTown(player.getLocation()) == null;
    }

    private boolean isEnabled() {
        var config = plugin.getIdeologyConfig();
        return config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
    }

    private long getCombatTimeoutTicks() {
        var config = plugin.getIdeologyConfig();
        int seconds = config == null ? 30 : config.getInt(CONFIG_PATH + "combatTimeoutSeconds", 30);
        return Math.max(1L, Math.min(3600L, seconds)) * 20L;
    }

    private static boolean isOnlineAndAlive(Player player) {
        return player != null && player.isOnline() && !player.isDead();
    }

    private static boolean isCombatTarget(Entity entity) {
        return entity instanceof Player || entity instanceof Mob;
    }

    private static boolean hasLivingAttacker(Entity damager) {
        if (damager instanceof LivingEntity) return true;
        return damager instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity;
    }

    private static Player findPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void clearCombat(UUID uuid) {
        CombatSession session = combatSessions.remove(uuid);
        if (session != null && session.expirationTask != null) session.expirationTask.cancel();
    }
}
