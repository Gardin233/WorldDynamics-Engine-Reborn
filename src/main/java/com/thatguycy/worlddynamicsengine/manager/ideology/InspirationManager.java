package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Grants one-shot armor-piercing inspirations and consumes them on the matching hit. */
public final class InspirationManager implements Listener {
    public static final String ARROW_ARMOR_PIERCING = "arrow_armor_piercing";
    public static final String MELEE_ARMOR_PIERCING = "melee_armor_piercing";

    private final WorldDynamicsEngine plugin;

    public InspirationManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public List<String> getEnabledEffectIds() {
        var config = plugin.getIdeologyConfig();
        String path = "mood.inspiration.effects.";
        List<String> effects = new ArrayList<>();
        if (config == null || config.getBoolean(path + "arrowArmorPiercing.enabled", true)) {
            effects.add(ARROW_ARMOR_PIERCING);
        }
        if (config == null || config.getBoolean(path + "meleeArmorPiercing.enabled", true)) {
            effects.add(MELEE_ARMOR_PIERCING);
        }
        return List.copyOf(effects);
    }

    public String chooseRandomEffect() {
        List<String> effects = getEnabledEffectIds();
        return effects.isEmpty() ? null : effects.get(ThreadLocalRandom.current().nextInt(effects.size()));
    }

    public boolean grant(Player player, String effectId) {
        if (player == null || effectId == null || !getEnabledEffectIds().contains(effectId)
                || player.isOp()) {
            return false;
        }
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null || resident.isDehumanized() || !resident.addInspirationCharge(effectId)) {
            return false;
        }
        plugin.getResidentManager().saveResident(resident);

        String effectName = plugin.getLocaleMessage("mood_inspiration_effect_" + effectId);
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        player.showTitle(Title.title(serializer.deserialize(plugin.getLocaleMessage("mood_inspiration_title")),
                serializer.deserialize(effectName),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage("mood_inspiration_awarded", effectName)));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player player) {
            applyInspiration(event, player, ARROW_ARMOR_PIERCING);
            return;
        }
        if (event.getDamager() instanceof Player player
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            applyInspiration(event, player, MELEE_ARMOR_PIERCING);
        }
    }

    private void applyInspiration(EntityDamageByEntityEvent event, Player player, String effectId) {
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (player.isOp() || !getEnabledEffectIds().contains(effectId)
                || resident == null || resident.isDehumanized() || resident.getInspirationCharges(effectId) <= 0
                || !event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            return;
        }
        try {
            double armorModifier = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR);
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, armorModifier * 0.5);
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            plugin.getLogger().fine("Could not apply armor-piercing inspiration: " + exception.getMessage());
            return;
        }
        if (!resident.consumeInspirationCharge(effectId)) return;
        plugin.getResidentManager().saveResident(resident);
        String effectName = plugin.getLocaleMessage("mood_inspiration_effect_" + effectId);
        player.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage("mood_inspiration_consumed", effectName)));
    }
}
