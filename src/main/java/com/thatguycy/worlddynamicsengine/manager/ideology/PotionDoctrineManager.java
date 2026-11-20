package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
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
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectTypeCategory;

/** Applies potion-doctrine mood changes when a player drinks a configured potion. */
public final class PotionDoctrineManager implements Listener {
    private static final String DOCTRINE_ID = "namespace:wder/potions";
    private static final String CONFIG_PATH = "mood.doctrineActions.potions.";
    private static final String INEBRIATION_SOURCE = "potion:inebriation";

    private final WorldDynamicsEngine plugin;
    private Set<String> craftEnginePotionItems = Set.of();
    private Set<String> inebriationVanillaPotionTypes = Set.of();
    private Set<String> inebriationCraftEngineItems = Set.of();
    private boolean enabled;
    private int inebriationMoodBonus;

    public PotionDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getIdeologyConfig();
        enabled = config == null || config.getBoolean(CONFIG_PATH + "enabled", true);
        inebriationMoodBonus = config == null ? 10
                : Math.max(0, Math.min(100, config.getInt(CONFIG_PATH + "inebriationMoodBonus", 10)));
        craftEnginePotionItems = normalizeIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "craftenginePotionItems"));
        inebriationVanillaPotionTypes = normalizeIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "inebriationPotions.vanilla"));
        inebriationCraftEngineItems = normalizeIds(config == null ? List.of()
                : config.getStringList(CONFIG_PATH + "inebriationPotions.craftengine"));
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

    public void apply(Player player, ItemStack item, boolean inebriationPotion) {
        if (!enabled || !plugin.religionEnabled || player == null || item == null
                || !player.isOnline() || player.isDead()
                || !plugin.getMoodManager().shouldProcessMoodAction(player)) {
            return;
        }

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
