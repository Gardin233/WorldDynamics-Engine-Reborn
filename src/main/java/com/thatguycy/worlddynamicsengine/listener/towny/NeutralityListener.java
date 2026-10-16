package com.thatguycy.worlddynamicsengine.listener.towny;

import com.palmergames.bukkit.towny.event.nation.toggle.NationToggleNeutralEvent;
import com.palmergames.bukkit.towny.event.NationPreAddEnemyEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownToggleNeutralEvent;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 拦截战争期间开启 Towny 中立等冲突操作，并落实常规战争中的中立保护。
 */
public final class NeutralityListener implements Listener {
    private final WorldDynamicsEngine plugin;

    public NeutralityListener(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAddEnemy(NationPreAddEnemyEvent event) {
        if (!event.getNation().isNeutral() && !event.getEnemy().isNeutral()) return;
        if (plugin.getOrgManager().hasActiveIndependenceWarBetween(event.getNationName(), event.getEnemyName())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegularWarDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                ? shooter : null;
        if (attacker == null) return;
        var api = TownyAPI.getInstance();
        var attackerNation = api.getNation(attacker);
        var victimNation = api.getNation(victim);
        if (attackerNation == null || victimNation == null) return;
        var orgs = plugin.getOrgManager();
        if (!orgs.hasActiveWarBetween(attackerNation.getName(), victimNation.getName())
                || orgs.hasActiveIndependenceWarBetween(attackerNation.getName(), victimNation.getName())) return;
        if (isNeutral(api.getTown(attacker)) || isNeutral(api.getTown(victim))
                || isNeutral(api.getTown(victim.getLocation()))) event.setCancelled(true);
    }

    private boolean isNeutral(Town town) {
        return town != null && town.isNeutral();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNationNeutralDuringWar(NationToggleNeutralEvent event) {
        if (!event.getFutureState() || event.getCurrentState()) return;
        if (plugin.getOrgManager().isNationInActiveWar(event.getNation().getName())) {
            event.setCancelled(true);
            if (event.getSender() != null) event.getSender().sendMessage(
                    plugin.formatLocaleMessage("neutrality_blocked_during_war"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTownNeutralDuringWar(TownToggleNeutralEvent event) {
        if (!event.getFutureState() || event.getCurrentState()) return;
        Town town = event.getTown();
        var nation = town.getNationOrNull();
        if (town.hasActiveWar() || plugin.getOrgManager().isTownInActiveWar(town.getName())
                || nation != null && plugin.getOrgManager().isNationInActiveWar(nation.getName())) {
            event.setCancelled(true);
            if (event.getSender() != null) event.getSender().sendMessage(
                    plugin.formatLocaleMessage("neutrality_blocked_during_war"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNationNeutral(NationToggleNeutralEvent event) {
        if (!event.getFutureState()) return;
        var nation = event.getNation();
        // The toggle event precedes the final state and fee transaction.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !nation.isNeutral()) return;
            var removed = plugin.getOrgManager().enforceNeutralNation(nation.getName());
            if (event.getSender() != null) {
                event.getSender().sendMessage(plugin.formatLocaleMessage("org_neutral_enforced"));
                if (!removed.isEmpty()) event.getSender().sendMessage(
                        plugin.formatLocaleMessage("org_neutral_alliances_removed", String.join("、", removed)));
            }
        });
    }
}
