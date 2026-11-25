package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/** Grants one-shot inspirations and consumes them on their matching action. */
public final class InspirationManager implements Listener {
    public static final String ARROW_ARMOR_PIERCING = "arrow_armor_piercing";
    public static final String MELEE_ARMOR_PIERCING = "melee_armor_piercing";
    public static final String ANIMAL_TAMING = "animal_taming";
    public static final String NIGHTINGALE = "nightingale";
    public static final String CONSOLATION = "consolation";
    public static final String ACTION_INCENTIVE = "action_incentive";
    public static final String MINING_INCENTIVE = "mining_incentive";
    public static final String STRETCH = "stretch";
    private static final String STRETCH_BLOCK_MODIFIER = "stretch_block_interaction_range";
    private static final String STRETCH_ENTITY_MODIFIER = "stretch_entity_interaction_range";

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, BukkitTask> stretchExpiryTasks = new HashMap<>();

    public InspirationManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public List<String> getEnabledEffectIds(Player player) {
        var config = plugin.getIdeologyConfig();
        String path = "mood.inspiration.effects.";
        List<String> effects = new ArrayList<>();
        if (config == null || config.getBoolean(path + "arrowArmorPiercing.enabled", true)) {
            effects.add(ARROW_ARMOR_PIERCING);
        }
        if (config == null || config.getBoolean(path + "meleeArmorPiercing.enabled", true)) {
            effects.add(MELEE_ARMOR_PIERCING);
        }
        if (isEffectEnabled(ANIMAL_TAMING) && hasMemeInspiration(player, ANIMAL_TAMING)) {
            effects.add(ANIMAL_TAMING);
        }
        if (isEffectEnabled(NIGHTINGALE) && hasMemeInspiration(player, NIGHTINGALE)) {
            effects.add(NIGHTINGALE);
        }
        if (isEffectEnabled(CONSOLATION) && hasMemeInspiration(player, CONSOLATION)) {
            effects.add(CONSOLATION);
        }
        if (isEffectEnabled(ACTION_INCENTIVE) && hasMemeInspiration(player, ACTION_INCENTIVE)) {
            effects.add(ACTION_INCENTIVE);
        }
        if (isEffectEnabled(MINING_INCENTIVE) && hasMemeInspiration(player, MINING_INCENTIVE)) {
            effects.add(MINING_INCENTIVE);
        }
        if (isEffectEnabled(STRETCH) && hasMemeInspiration(player, STRETCH)) {
            effects.add(STRETCH);
        }
        return List.copyOf(effects);
    }

    public String chooseRandomEffect(Player player) {
        List<String> effects = getEnabledEffectIds(player);
        return effects.isEmpty() ? null : effects.get(ThreadLocalRandom.current().nextInt(effects.size()));
    }

    public boolean grant(Player player, String effectId) {
        if (player == null || effectId == null || !getEnabledEffectIds(player).contains(effectId)
                || player.isOp()) {
            return false;
        }
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null || resident.isDehumanized()) {
            return false;
        }
        if (NIGHTINGALE.equals(effectId)) {
            if (!applyNightingale(player)) return false;
        } else if (ACTION_INCENTIVE.equals(effectId)) {
            if (!applyActionIncentive(player)) return false;
        } else if (MINING_INCENTIVE.equals(effectId)) {
            if (!applyMiningIncentive(player)) return false;
        } else if (STRETCH.equals(effectId)) {
            if (!applyStretchInspiration(player)) return false;
        } else {
            if (!resident.addInspirationCharge(effectId)) return false;
            plugin.getResidentManager().saveResident(resident);
        }

        String effectName = getEffectDisplayName(effectId);
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        player.showTitle(Title.title(serializer.deserialize(plugin.getLocaleMessage("mood_inspiration_title")),
                serializer.deserialize(effectName),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage("mood_inspiration_awarded", effectName)));
        return true;
    }

    private boolean isEffectEnabled(String effectId) {
        var config = plugin.getIdeologyConfig();
        String path = "mood.inspiration.effects.";
        return switch (effectId) {
            case ARROW_ARMOR_PIERCING -> config == null
                    || config.getBoolean(path + "arrowArmorPiercing.enabled", true);
            case MELEE_ARMOR_PIERCING -> config == null
                    || config.getBoolean(path + "meleeArmorPiercing.enabled", true);
            case ANIMAL_TAMING -> config == null
                    || config.getBoolean(path + "animalTaming.enabled", true);
            case NIGHTINGALE -> config == null
                    || config.getBoolean(path + "nightingale.enabled", true);
            case CONSOLATION -> config == null
                    || config.getBoolean(path + "consolation.enabled", true);
            case ACTION_INCENTIVE -> config == null
                    || config.getBoolean(path + "actionIncentive.enabled", true);
            case MINING_INCENTIVE -> config == null
                    || config.getBoolean(path + "miningIncentive.enabled", true);
            case STRETCH -> config == null
                    || config.getBoolean(path + "stretch.enabled", true);
            default -> false;
        };
    }

    private boolean applyNightingale(Player player) {
        return applyPotionInspiration(player, PotionEffectType.NIGHT_VISION,
                "nightingale.durationMinutes", 15, 0);
    }

    private boolean applyActionIncentive(Player player) {
        return applyPotionInspiration(player, PotionEffectType.SPEED,
                "actionIncentive.durationMinutes", 15, 2);
    }

    private boolean applyMiningIncentive(Player player) {
        return applyPotionInspiration(player, PotionEffectType.HASTE,
                "miningIncentive.durationMinutes", 15, 2);
    }

    private boolean applyStretchInspiration(Player player) {
        AttributeInstance blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        AttributeInstance entityRange = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (blockRange == null || entityRange == null || resident == null) return false;

        long now = System.currentTimeMillis();
        long newExpiry = now + getConfiguredDurationMinutes("stretch.durationMinutes", 15) * 60_000L;
        long expiresAt = Math.max(resident.getStretchInspirationExpiresAt(), newExpiry);
        resident.setStretchInspirationExpiresAt(expiresAt);
        plugin.getResidentManager().saveResident(resident);
        applyStretchModifiers(player, blockRange, entityRange);
        scheduleStretchExpiry(player.getUniqueId(), expiresAt);
        return true;
    }

    private void applyStretchModifiers(Player player, AttributeInstance blockRange,
                                       AttributeInstance entityRange) {
        removeStretchModifiers(player);
        blockRange.addTransientModifier(new AttributeModifier(
                new NamespacedKey(plugin, STRETCH_BLOCK_MODIFIER), 1.0,
                AttributeModifier.Operation.ADD_NUMBER));
        entityRange.addTransientModifier(new AttributeModifier(
                new NamespacedKey(plugin, STRETCH_ENTITY_MODIFIER), 1.0,
                AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeStretchModifiers(Player player) {
        removeStretchModifier(player, Attribute.BLOCK_INTERACTION_RANGE, STRETCH_BLOCK_MODIFIER);
        removeStretchModifier(player, Attribute.ENTITY_INTERACTION_RANGE, STRETCH_ENTITY_MODIFIER);
    }

    private void removeStretchModifier(Player player, Attribute attribute, String modifierKey) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(new NamespacedKey(plugin, modifierKey));
    }

    private void scheduleStretchExpiry(UUID uuid, long expiresAt) {
        BukkitTask previous = stretchExpiryTasks.remove(uuid);
        if (previous != null) previous.cancel();
        long remainingTicks = Math.max(1L, (expiresAt - System.currentTimeMillis() + 49L) / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expireStretchInspiration(uuid, expiresAt), remainingTicks);
        stretchExpiryTasks.put(uuid, task);
    }

    private void expireStretchInspiration(UUID uuid, long expectedExpiry) {
        stretchExpiryTasks.remove(uuid);
        WDEresident resident = plugin.getResidentManager().getResident(uuid);
        if (resident != null && resident.getStretchInspirationExpiresAt() > expectedExpiry) {
            scheduleStretchExpiry(uuid, resident.getStretchInspirationExpiresAt());
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) removeStretchModifiers(player);
        if (resident != null && resident.getStretchInspirationExpiresAt() > 0L) {
            resident.clearStretchInspiration();
            plugin.getResidentManager().saveResident(resident);
        }
    }

    private void restoreStretchInspiration(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask previous = stretchExpiryTasks.remove(uuid);
        if (previous != null) previous.cancel();
        removeStretchModifiers(player);

        WDEresident resident = plugin.getResidentManager().getResident(uuid);
        if (resident == null || resident.isDehumanized()) return;
        long expiresAt = resident.getStretchInspirationExpiresAt();
        if (expiresAt <= System.currentTimeMillis()) {
            if (expiresAt > 0L) {
                resident.clearStretchInspiration();
                plugin.getResidentManager().saveResident(resident);
            }
            return;
        }

        AttributeInstance blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        AttributeInstance entityRange = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (blockRange == null || entityRange == null) {
            resident.clearStretchInspiration();
            plugin.getResidentManager().saveResident(resident);
            return;
        }
        applyStretchModifiers(player, blockRange, entityRange);
        scheduleStretchExpiry(uuid, expiresAt);
    }

    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) restoreStretchInspiration(player);
    }

    public void clearStretchInspiration(Player player) {
        if (player == null) return;
        BukkitTask task = stretchExpiryTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        removeStretchModifiers(player);
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident != null && resident.getStretchInspirationExpiresAt() > 0L) {
            resident.clearStretchInspiration();
            plugin.getResidentManager().saveResident(resident);
        }
    }

    public void stopTasks() {
        for (BukkitTask task : stretchExpiryTasks.values()) task.cancel();
        stretchExpiryTasks.clear();
        for (Player player : Bukkit.getOnlinePlayers()) removeStretchModifiers(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> restoreStretchInspiration(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> restoreStretchInspiration(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BukkitTask task = stretchExpiryTasks.remove(uuid);
        if (task != null) task.cancel();
        removeStretchModifiers(event.getPlayer());
    }

    private boolean applyPotionInspiration(Player player, PotionEffectType type,
                                          String durationConfigPath, int defaultMinutes,
                                          int amplifier) {
        int minutes = getConfiguredDurationMinutes(durationConfigPath, defaultMinutes);
        int durationTicks = minutes * 1_200;
        PotionEffect existing = player.getPotionEffect(type);
        if (existing != null && (existing.getDuration() < 0
                || existing.getDuration() >= durationTicks
                || existing.getAmplifier() > amplifier)) {
            return true;
        }
        return player.addPotionEffect(new PotionEffect(
                type, durationTicks, amplifier, true, false, true), true);
    }

    private String getEffectDisplayName(String effectId) {
        String key = "mood_inspiration_effect_" + effectId;
        if (NIGHTINGALE.equals(effectId)) {
            return plugin.formatLocaleMessage(key,
                    getConfiguredDurationMinutes("nightingale.durationMinutes", 15));
        }
        if (ACTION_INCENTIVE.equals(effectId)) {
            return plugin.formatLocaleMessage(key,
                    getConfiguredDurationMinutes("actionIncentive.durationMinutes", 15));
        }
        if (MINING_INCENTIVE.equals(effectId)) {
            return plugin.formatLocaleMessage(key,
                    getConfiguredDurationMinutes("miningIncentive.durationMinutes", 15));
        }
        if (STRETCH.equals(effectId)) {
            return plugin.formatLocaleMessage(key,
                    getConfiguredDurationMinutes("stretch.durationMinutes", 15));
        }
        return plugin.getLocaleMessage(key);
    }

    private int getConfiguredDurationMinutes(String durationConfigPath, int defaultMinutes) {
        var config = plugin.getIdeologyConfig();
        return config == null ? defaultMinutes
                : Math.max(1, Math.min(1440, config.getInt(
                "mood.inspiration.effects." + durationConfigPath, defaultMinutes)));
    }

    private boolean hasMemeInspiration(Player player, String effectId) {
        if (player == null || !plugin.religionEnabled || plugin.getResidentManager() == null
                || plugin.getReligionManager() == null) {
            return false;
        }
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) return false;
        for (String memeId : plugin.getReligionManager().getConfiguredMemes(religion.getMemes())) {
            var meme = plugin.getReligionManager().getMeme(memeId);
            if (meme != null && meme.getOutcomeEffects("inspiration").stream()
                    .anyMatch(configured -> effectId.equalsIgnoreCase(configured))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        Player player = event.getPlayer();
        if (player.isOp() || player.getGameMode() == GameMode.CREATIVE) return;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null || resident.isDehumanized()) return;
        if (grantConsolation(event, player, resident)) return;
        if (!isEffectEnabled(ANIMAL_TAMING)
                || resident.getInspirationCharges(ANIMAL_TAMING) <= 0) return;

        Entity target = event.getRightClicked();
        ItemStack held = getHandItem(player, hand);
        if (target instanceof Tameable tameable && !tameable.isTamed()
                && isTamingItem(tameable, held)) {
            event.setCancelled(true);
            EntityTameEvent tameEvent = new EntityTameEvent((LivingEntity) target, player);
            Bukkit.getPluginManager().callEvent(tameEvent);
            if (tameEvent.isCancelled()
                    || !consumeTamingInspiration(player, resident, hand,
                    !(target instanceof AbstractHorse))) {
                return;
            }
            tameable.setOwner(player);
            tameable.setTamed(true);
            if (tameable instanceof org.bukkit.entity.Sittable sittable) {
                sittable.setSitting(false);
            }
            if (!(target instanceof AbstractHorse)
                    && plugin.getAnimalResonanceDoctrineManager() != null) {
                plugin.getAnimalResonanceDoctrineManager().applyFeeding(player);
            }
            return;
        }

        if (!(target instanceof Animals animal) || !animal.isAdult() || !animal.canBreed()
                || !animal.isBreedItem(held)
                || (animal instanceof Tameable tameable && !tameable.isTamed())) {
            return;
        }
        if (breedFromSingleAnimal(player, resident, animal, hand)) {
            event.setCancelled(true);
        }
    }

    private boolean grantConsolation(PlayerInteractEntityEvent event, Player giver,
                                     WDEresident giverResident) {
        if (!isEffectEnabled(CONSOLATION) || !(event.getRightClicked() instanceof Player recipient)
                || recipient.equals(giver) || recipient.isDead() || !recipient.isOnline()
                || plugin.getMoodManager() == null || plugin.getMoodManager().isMoodExempt(recipient)
                || plugin.getResidentManager().getResident(recipient.getUniqueId()) == null
                || giverResident.getInspirationCharges(CONSOLATION) <= 0) {
            return false;
        }

        var config = plugin.getIdeologyConfig();
        int moodBonus = config == null ? 10
                : Math.max(0, Math.min(100,
                config.getInt("mood.inspiration.effects.consolation.moodBonus", 10)));
        if (moodBonus <= 0 || !giverResident.consumeInspirationCharge(CONSOLATION)) return false;
        if (!plugin.getMoodManager().applyTemporaryMoodChange(
                recipient.getUniqueId(), CONSOLATION, moodBonus)) {
            giverResident.addInspirationCharge(CONSOLATION);
            return false;
        }

        event.setCancelled(true);
        plugin.getResidentManager().saveResident(giverResident);
        String effectName = plugin.getLocaleMessage("mood_inspiration_effect_" + CONSOLATION);
        giver.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage("mood_inspiration_consumed", effectName)));
        recipient.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage("mood_inspiration_consoled", giver.getName(), moodBonus)));
        return true;
    }

    private boolean isTamingItem(Tameable animal, ItemStack held) {
        Material material = held == null ? Material.AIR : held.getType();
        return switch (animal.getType()) {
            case WOLF -> material == Material.BONE;
            case CAT -> material == Material.COD || material == Material.SALMON;
            case PARROT -> switch (material) {
                case WHEAT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS, BEETROOT_SEEDS,
                        TORCHFLOWER_SEEDS, PITCHER_POD -> true;
                default -> false;
            };
            default -> animal instanceof AbstractHorse && material.isAir();
        };
    }

    private boolean breedFromSingleAnimal(Player player, WDEresident resident,
                                          Animals parent, EquipmentSlot hand) {
        Entity spawned;
        try {
            spawned = parent.getWorld().spawnEntity(parent.getLocation(), parent.getType());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            plugin.getLogger().fine("Could not spawn an inspired animal offspring: " + exception.getMessage());
            return false;
        }
        if (!(spawned instanceof Animals offspring) || !offspring.isValid()) {
            if (spawned != null) spawned.remove();
            return false;
        }

        offspring.setBaby();
        offspring.setBreedCause(player.getUniqueId());
        if (!consumeTamingInspiration(player, resident, hand, true)) {
            offspring.remove();
            return false;
        }
        parent.setAge(6_000);
        parent.setLoveModeTicks(0);
        if (plugin.getRanchingDoctrineManager() != null) {
            plugin.getRanchingDoctrineManager().apply(player);
        }
        if (plugin.getAnimalResonanceDoctrineManager() != null) {
            plugin.getAnimalResonanceDoctrineManager().applyFeeding(player);
        }
        return true;
    }

    private boolean consumeTamingInspiration(Player player, WDEresident resident,
                                             EquipmentSlot hand, boolean consumeFood) {
        if (!isEffectEnabled(ANIMAL_TAMING) || resident.getInspirationCharges(ANIMAL_TAMING) <= 0
                || !resident.consumeInspirationCharge(ANIMAL_TAMING)) {
            return false;
        }
        if (consumeFood && !consumeOneItem(player, hand)) {
            resident.addInspirationCharge(ANIMAL_TAMING);
            return false;
        }
        plugin.getResidentManager().saveResident(resident);
        String effectName = plugin.getLocaleMessage("mood_inspiration_effect_" + ANIMAL_TAMING);
        player.sendMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage("mood_inspiration_consumed", effectName)));
        return true;
    }

    private static boolean consumeOneItem(Player player, EquipmentSlot hand) {
        ItemStack held = getHandItem(player, hand);
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) return false;
        ItemStack updated;
        if (held.getAmount() == 1) {
            updated = new ItemStack(Material.AIR);
        } else {
            updated = held.clone();
            updated.setAmount(updated.getAmount() - 1);
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(updated);
        } else {
            player.getInventory().setItemInMainHand(updated);
        }
        return true;
    }

    private static ItemStack getHandItem(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
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
        if (player.isOp() || !isEffectEnabled(effectId)
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
