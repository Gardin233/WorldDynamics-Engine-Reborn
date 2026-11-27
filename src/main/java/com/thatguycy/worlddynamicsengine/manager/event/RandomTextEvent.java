package com.thatguycy.worlddynamicsengine.manager.event;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.text.TynanGeneratedText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Shared Dialog, timeout, Tynan text, and reward behavior for random text events. */
abstract class RandomTextEvent implements GameEvent {
    private static final Set<String> PENDING_TOWNS = new HashSet<>();

    protected final WorldDynamicsEngine plugin;
    private final String eventId;
    private final String configPath;

    RandomTextEvent(WorldDynamicsEngine plugin, String eventId, String configPath) {
        this.plugin = plugin;
        this.eventId = eventId;
        this.configPath = configPath;
    }

    @Override
    public final String id() {
        return eventId;
    }

    @Override
    public final boolean requiresStrengthPoints() {
        return true;
    }

    @Override
    public final double strengthMultiplier() {
        return 1.0D;
    }

    final double rollStrengthMultiplier(Random random) {
        String path = configPath + ".strength";
        double minimum = plugin.getEventManager().getConfig().getDouble(path + ".minimum-multiplier", 0.75D);
        double maximum = plugin.getEventManager().getConfig().getDouble(path + ".maximum-multiplier", 1.25D);
        return RandomTextStrengthMultiplier.roll(minimum, maximum, random);
    }

    @Override
    public final boolean isEligible(RandomEventContext context) {
        return !PENDING_TOWNS.contains(context.townName().toLowerCase(java.util.Locale.ROOT))
                && canOffer(context);
    }

    protected boolean canOffer(RandomEventContext context) {
        return true;
    }

    @Override
    public final EventOccurrence execute(RandomEventContext context) {
        String townKey = context.townName().toLowerCase(java.util.Locale.ROOT);
        if (!PENDING_TOWNS.add(townKey)) {
            return null;
        }

        Player mayor = context.target();
        try {
            String language = mayor.getLocale().toLowerCase(java.util.Locale.ROOT).startsWith("en") ? "en" : "zh";
            long seed = new Random().nextLong();
            String personName = plugin.getTynanTextCompositionManager()
                    .generateRandomPersonName(language, seed);
            var eventConfig = plugin.getEventManager().getConfig().getConfigurationSection(configPath);
            var rewardConfig = eventConfig == null ? null : eventConfig.getConfigurationSection("rewards");
            RandomTextRewardPool.Reward reward = RandomTextRewardPool.roll(
                    rewardConfig, plugin, new Random(seed ^ 31L));
            EconomyBridge economy = plugin.getEconomyBridge();
            String currency = economy == null ? "" : economy.getDefaultCurrency();
            if (economy == null || !economy.supportsExactCurrency(currency)) {
                reward = new RandomTextRewardPool.Reward(BigDecimal.ZERO, reward.item());
            }
            String itemName = reward.item() == null
                    ? ("en".equals(language) ? "no item" : "无") : reward.itemName();
            String rewardMoney = economy != null && economy.supportsExactCurrency(currency)
                    ? economy.format(reward.money(), currency) : reward.money().toPlainString();
            String rewardSummary = "en".equals(language)
                    ? rewardMoney + " currency and " + itemName
                    : rewardMoney + "货币与" + itemName;
            TynanGeneratedText generated = plugin.getTynanTextCompositionManager()
                    .generateRandomEventText(language, eventId, Map.of(
                            "PERSON", personName,
                            "TOWN", context.townName(),
                            "REWARD", rewardSummary), seed);
            TextOffer offer = new TextOffer(context, townKey, reward, mayor);
            present(mayor, personName, generated.text(), reward, offer);

            long timeoutSeconds = Math.max(30L, Math.min(86_400L,
                    plugin.getEventManager().getConfig().getLong(configPath + ".duration-seconds", 300L)));
            offer.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> expireOffer(offer),
                    timeoutSeconds * 20L);
            return offer;
        } catch (RuntimeException exception) {
            PENDING_TOWNS.remove(townKey);
            plugin.getLogger().warning("Could not create random text event '" + eventId
                    + "': " + exception.getMessage());
            return null;
        }
    }

    protected abstract String dialogTitle(String personName, RandomEventContext context);

    protected abstract String dialogBody(String personName, String generatedText,
                                         RandomTextRewardPool.Reward reward, RandomEventContext context);

    /** Starts the event-specific action; returning false keeps the reward unclaimed. */
    protected abstract boolean onAccepted(RandomEventContext context);

    private void present(Player mayor, String personName, String generatedText,
                         RandomTextRewardPool.Reward reward, TextOffer offer) {
        DialogBase base = DialogBase.builder(component(dialogTitle(personName, offer.context)))
                .body(List.of(DialogBody.plainMessage(
                        component(dialogBody(personName, generatedText, reward, offer.context)), 420)))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        long callbackSeconds = Math.max(30L, Math.min(86_400L,
                plugin.getEventManager().getConfig().getLong(configPath + ".duration-seconds", 300L)));
        Duration callbackLifetime = Duration.ofSeconds(callbackSeconds);
        ActionButton accept = button(
                plugin.getEventManager().getConfig().getString(configPath + ".messages.accept", "接受"),
                mayor, offer, true, callbackLifetime);
        ActionButton decline = button(
                plugin.getEventManager().getConfig().getString(configPath + ".messages.decline", "拒绝"),
                mayor, offer, false, callbackLifetime);
        mayor.showDialog(Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(accept), decline, 1))));
    }

    private ActionButton button(String label, Player mayor, TextOffer offer,
                                boolean accept, Duration lifetime) {
        return ActionButton.builder(component(label))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player clicker
                            && clicker.getUniqueId().equals(mayor.getUniqueId())) {
                        Bukkit.getScheduler().runTask(plugin, () -> resolveOffer(clicker, offer, accept));
                    }
                }, ClickCallback.Options.builder().uses(1).lifetime(lifetime).build()))
                .build();
    }

    private void resolveOffer(Player mayor, TextOffer offer, boolean accepted) {
        if (offer.closed || !mayor.isOnline()) {
            return;
        }
        if (accepted) {
            if (!onAccepted(offer.context)) {
                mayor.sendMessage(plugin.formatLocaleMessage(mayor.getLocale(),
                        "random_text_event_start_failed"));
                plugin.getEventManager().finishEvent(offer.context.eventId(), false);
                return;
            }
            grantReward(mayor, offer.reward);
        }
        plugin.getEventManager().finishEvent(offer.context.eventId(), false);
    }

    private void expireOffer(TextOffer offer) {
        if (offer.closed) {
            return;
        }
        plugin.getEventManager().finishEvent(offer.context.eventId(), false);
        if (offer.mayor.isOnline()) {
            DialogBase expired = DialogBase.builder(component(
                            plugin.getEventManager().getConfig().getString(
                                    configPath + ".messages.expired", "§7这项提议已过期。")))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();
            offer.mayor.showDialog(Dialog.create(builder -> builder.empty()
                    .base(expired).type(DialogType.notice())));
        }
    }

    private void grantReward(Player player, RandomTextRewardPool.Reward reward) {
        if (reward.money().signum() > 0) {
            EconomyBridge economy = plugin.getEconomyBridge();
            String currency = economy == null ? "" : economy.getDefaultCurrency();
            if (economy != null && economy.supportsExactCurrency(currency)
                    && economy.depositExact(player.getUniqueId(), player.getWorld().getName(),
                            economy.charge(reward.money(), currency)).success()) {
                player.sendMessage(plugin.formatLocaleMessage(player.getLocale(),
                        "random_text_event_reward_money", economy.format(reward.money(), currency)));
            }
        }
        ItemStack item = reward.item();
        if (item != null) {
            player.getInventory().addItem(item).values().forEach(overflow ->
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow));
            player.sendMessage(plugin.formatLocaleMessage(player.getLocale(),
                    "random_text_event_reward_item", reward.itemName()));
        }
    }

    private Component component(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text == null ? "" : text);
    }

    private final class TextOffer implements EventOccurrence {
        private final RandomEventContext context;
        private final String townKey;
        private final RandomTextRewardPool.Reward reward;
        private final Player mayor;
        private boolean closed;
        private BukkitTask timeoutTask;

        private TextOffer(RandomEventContext context, String townKey,
                          RandomTextRewardPool.Reward reward, Player mayor) {
            this.context = context;
            this.townKey = townKey;
            this.reward = reward;
            this.mayor = mayor;
        }

        @Override
        public String eventTypeId() {
            return eventId;
        }

        @Override
        public UUID eventId() {
            return context.eventId();
        }

        @Override
        public UUID ownerId() {
            return context.target().getUniqueId();
        }

        @Override
        public String townName() {
            return context.townName();
        }

        @Override
        public boolean isOngoing() {
            return !closed;
        }

        @Override
        public void onForcedClose() {
            if (!mayor.isOnline()) {
                return;
            }
            DialogBase cancelled = DialogBase.builder(component(plugin.formatLocaleMessage(
                            mayor.getLocale(), "random_text_event_cancelled_by_admin")))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();
            mayor.showDialog(Dialog.create(builder -> builder.empty()
                    .base(cancelled).type(DialogType.notice())));
        }

        @Override
        public void finish(boolean successful) {
            closed = true;
            PENDING_TOWNS.remove(townKey);
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
        }
    }
}
