package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;

/** Handles potion-triggered alchemical dependence and doctrine mood changes. */
public final class PotionDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/potions";
    private static final String CONFIG_PATH = "mood.doctrineActions.potions.";
    private static final String INEBRIATION_SOURCE = "potion:inebriation";
    static final String ALCHEMY_SOURCE_PREFIX = "potion:alchemy_dependency|";

    private final WorldDynamicsEngine plugin;
    private Set<String> craftEnginePotionItems = Set.of();
    private Set<String> inebriationVanillaPotionTypes = Set.of();
    private Set<String> inebriationCraftEngineItems = Set.of();
    private boolean enabled;
    private int inebriationMoodBonus;
    private int reinforcementMoodBonus;
    private int dependencyGainPerPotion;
    private int dependencyBaseChancePercent;
    private int dependencyChanceIncreasePerDrinkPercent;
    private int dependencyMaximumChancePercent;
    private long consecutiveDrinkWindowMillis;
    private double satisfactionGainPerPotion;
    private double satisfactionDecayPerHour;
    private int splashSatisfactionLoss;
    private int maxDependencyDecayAccelerationPercent;
    private int satisfiedMinimum;
    private int deficientMinimum;
    private int cravingMinimum;
    private int satisfiedMood;
    private int deficientMood;
    private int cravingMood;
    private int madnessMood;
    private BukkitTask refreshTask;

    public PotionDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getIdeologyConfig();
        enabled = config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
        inebriationMoodBonus = config == null ? 10
                : Math.max(0, Math.min(100, config.getInt(CONFIG_PATH + "inebriationMoodBonus", 10)));
        reinforcementMoodBonus = boundedInt(config, "reinforcementMoodBonus", 5, -100, 100);
        dependencyGainPerPotion = boundedInt(config, "dependencyGainPerPotion", 5, 0, 100);
        dependencyBaseChancePercent =
                boundedInt(config, "dependencyAcquisition.baseChancePercent", 10, 0, 100);
        dependencyChanceIncreasePerDrinkPercent =
                boundedInt(config, "dependencyAcquisition.increasePerConsecutiveDrinkPercent", 10, 0, 100);
        dependencyMaximumChancePercent =
                boundedInt(config, "dependencyAcquisition.maximumChancePercent", 100, 0, 100);
        consecutiveDrinkWindowMillis = boundedInt(
                config, "dependencyAcquisition.streakWindowMinutes", 10, 1, 1440) * 60_000L;
        satisfactionGainPerPotion = config == null ? 35.0
                : Math.max(0.0, Math.min(100.0,
                        config.getDouble(CONFIG_PATH + "satisfactionGainPerPotion", 35.0)));
        satisfactionDecayPerHour = config == null ? 10.0
                : Math.max(0.0, config.getDouble(CONFIG_PATH + "satisfactionDecayPerHour", 10.0));
        splashSatisfactionLoss = boundedInt(config, "splashSatisfactionLoss", 20, 0, 100);
        maxDependencyDecayAccelerationPercent =
                boundedInt(config, "maxDependencyDecayAccelerationPercent", 20, 0, 1000);
        satisfiedMinimum = boundedInt(config, "satisfactionThresholds.satisfiedMinimum", 75, 0, 100);
        deficientMinimum = Math.min(satisfiedMinimum,
                boundedInt(config, "satisfactionThresholds.deficientMinimum", 50, 0, 100));
        cravingMinimum = Math.min(deficientMinimum,
                boundedInt(config, "satisfactionThresholds.cravingMinimum", 25, 0, 100));
        satisfiedMood = boundedInt(config, "moodByStage.satisfied", 2, -100, 100);
        deficientMood = boundedInt(config, "moodByStage.deficient", -5, -100, 100);
        cravingMood = boundedInt(config, "moodByStage.craving", -10, -100, 100);
        madnessMood = boundedInt(config, "moodByStage.madness", -20, -100, 100);
        craftEnginePotionItems = normalizeIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "craftenginePotionItems"));
        inebriationVanillaPotionTypes = normalizeIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "inebriationPotions.vanilla"));
        inebriationCraftEngineItems = normalizeIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "inebriationPotions.craftengine"));
    }

    public void start() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::refreshDependenceMoodModifiers, 20L, 20L * 60L);
    }

    public void stop() {
        if (refreshTask == null) return;
        refreshTask.cancel();
        refreshTask = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        if (!enabled || event.getItem() == null) return;
        ItemStack item = event.getItem().clone();
        String craftEngineItemId = getCraftEngineItemId(item);
        String vanillaPotionType = item.getItemMeta() instanceof PotionMeta potionMeta
                && potionMeta.getBasePotionType() != null
                ? potionMeta.getBasePotionType().name() : null;
        boolean inebriationPotion = PotionDoctrineCalculator.matchesInebriationPotion(
                vanillaPotionType, craftEngineItemId,
                inebriationVanillaPotionTypes, inebriationCraftEngineItems);
        boolean configuredCraftEnginePotion =
                PotionDoctrineCalculator.matchesCraftEngineId(craftEnginePotionItems, craftEngineItemId);
        if (item.getType() != Material.POTION && !configuredCraftEnginePotion && !inebriationPotion) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled()) {
                apply(player, item, inebriationPotion);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!enabled || event.getPotion().getItem().getType() != Material.SPLASH_POTION) return;
        long now = System.currentTimeMillis();
        for (var affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player player)
                    || plugin.getMoodManager() == null
                    || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
                continue;
            }
            WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
            if (resident == null || resident.getAlchemyDependencyPercent() <= 0) continue;
            if (resident.applyAlchemySplashHit(now, splashSatisfactionLoss,
                    satisfactionDecayPerHour, maxDependencyDecayAccelerationPercent)) {
                refreshDependenceMoodModifier(resident, now);
                plugin.getResidentManager().saveResident(resident);
            }
        }
    }

    public void apply(Player player, ItemStack item, boolean inebriationPotion) {
        if (!enabled || player == null || item == null
                || !player.isOnline() || player.isDead()
                || plugin.getMoodManager() == null
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            return;
        }

        long now = System.currentTimeMillis();
        WDEresident resident = plugin.getResidentManager().getOrCreateResident(
                player.getUniqueId(), player.getName());
        int consecutiveDrinks = resident.recordAlchemyPotionDrink(now, consecutiveDrinkWindowMillis);
        int dependencyChance = AlchemyDependencyCalculator.dependencyChancePercent(
                consecutiveDrinks, dependencyBaseChancePercent,
                dependencyChanceIncreasePerDrinkPercent, dependencyMaximumChancePercent);
        boolean acquiredDependency = java.util.concurrent.ThreadLocalRandom.current().nextInt(100)
                < dependencyChance;
        resident.consumeAlchemyPotion(now, acquiredDependency ? dependencyGainPerPotion : 0,
                satisfactionGainPerPotion,
                satisfactionDecayPerHour, maxDependencyDecayAccelerationPercent);
        refreshDependenceMoodModifier(resident, now);
        if (reinforcementMoodBonus != 0) {
            plugin.getMoodManager().applyTemporaryMoodChange(
                    player.getUniqueId(), "potion:alchemy_reinforcement", reinforcementMoodBonus);
        }
        plugin.getResidentManager().saveResident(resident);

        if (!plugin.religionEnabled) return;
        WDEreligion religion = getReligion(player);
        if (religion == null || plugin.getReligionManager().getDoctrine(DOCTRINE_ID) == null) return;
        String attitude = religion.getDoctrineAttitude(DOCTRINE_ID);
        PotionDoctrineCalculator.EffectProfile effects = PotionDoctrineCalculator.inspectEffects(
                getEffectCategories(item));
        PotionDoctrineCalculator.Assessment assessment =
                PotionDoctrineCalculator.assess(attitude, effects, inebriationPotion);
        if (assessment.compliant() == null) return;

        int weight = Math.max(0, plugin.getReligionManager().getDoctrineMoodWeight(DOCTRINE_ID, attitude));
        int moodChange = weight * Math.max(0,
                plugin.getReligionManager().getDoctrineMoodChangePerWeight(
                        DOCTRINE_ID, attitude, assessment.compliant(), null));
        DoctrineMoodEntry moodEntry =
                new DoctrineMoodEntry(DOCTRINE_ID, attitude, assessment.compliant(), moodChange);
        plugin.getMoodManager().applyDoctrineMoodChanges(player,
                assessment.compliant() ? moodChange : 0,
                assessment.compliant() ? 0 : moodChange,
                moodEntry.source());

        if (assessment.earnsInebriationBonus() && inebriationMoodBonus > 0) {
            plugin.getMoodManager().applyTemporaryMoodChange(
                    player.getUniqueId(), INEBRIATION_SOURCE, inebriationMoodBonus);
        }
        if (!assessment.compliant() && plugin.getPlayerMenuManager() != null) {
            plugin.getPlayerMenuManager().sendDoctrineViolation(player, DOCTRINE_ID);
        }
    }

    private void refreshDependenceMoodModifiers() {
        if (plugin.getMoodManager() == null || plugin.getResidentManager() == null) return;
        long now = System.currentTimeMillis();
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            refreshDependenceMoodModifier(resident, now);
        }
    }

    private void refreshDependenceMoodModifier(WDEresident resident, long now) {
        if (resident == null || plugin.getMoodManager() == null) return;
        int dependency = resident.getAlchemyDependencyPercent();
        if (!enabled || dependency <= 0) {
            plugin.getMoodManager().replacePersistentMoodModifier(
                    resident.getUserUUID(), ALCHEMY_SOURCE_PREFIX, null, 0);
            return;
        }
        double satisfaction = resident.getAlchemySatisfactionAt(
                now, satisfactionDecayPerHour, maxDependencyDecayAccelerationPercent);
        AlchemyDependencyCalculator.Stage stage = AlchemyDependencyCalculator.stageAt(
                satisfaction, satisfiedMinimum, deficientMinimum, cravingMinimum);
        String source = ALCHEMY_SOURCE_PREFIX + dependency + "|" + stage.id();
        int mood = switch (stage) {
            case SATISFIED -> satisfiedMood;
            case DEFICIENT -> deficientMood;
            case CRAVING -> cravingMood;
            case MADNESS -> madnessMood;
        };
        plugin.getMoodManager().replacePersistentMoodModifier(
                resident.getUserUUID(), ALCHEMY_SOURCE_PREFIX, source, mood);
    }

    private int boundedInt(org.bukkit.configuration.file.FileConfiguration config, String key,
                           int defaultValue, int minimum, int maximum) {
        return config == null ? defaultValue
                : Math.max(minimum, Math.min(maximum, config.getInt(CONFIG_PATH + key, defaultValue)));
    }

    private WDEreligion getReligion(Player player) {
        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        return resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
    }

    private String getCraftEngineItemId(ItemStack item) {
        var integration = plugin.getCraftEngineIntegration();
        return integration == null ? null : integration.getItemId(item);
    }

    private static Collection<PotionEffectTypeCategory> getEffectCategories(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta potionMeta)) return List.of();
        return potionMeta.getAllEffects().stream()
                .map(effect -> effect.getType().getCategory())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalizeIds(List<String> ids) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) normalized.add(id.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

}
