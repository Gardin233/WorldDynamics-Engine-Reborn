package com.thatguycy.worlddynamicsengine.manager.event;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;

/** Offers the mayor a reward in exchange for luring a raid toward their town. */
final class MisleadEnemiesTextEvent extends RandomTextEvent {
    private static final String CONFIG_PATH = "random-text-events.mislead-enemies";

    MisleadEnemiesTextEvent(WorldDynamicsEngine plugin) {
        super(plugin, "mislead_enemies", CONFIG_PATH);
    }

    @Override
    protected boolean canOffer(RandomEventContext context) {
        return context.strengthPoints() > 0;
    }

    @Override
    protected String dialogTitle(String personName, RandomEventContext context) {
        return message("title")
                .replace("%name%", personName)
                .replace("%town%", context.townName());
    }

    @Override
    protected String dialogBody(String personName, String generatedText,
                                RandomTextRewardPool.Reward reward, RandomEventContext context) {
        String itemName = reward.item() == null ? "无" : reward.itemName();
        return message("body")
                .replace("%name%", personName)
                .replace("%town%", context.townName())
                .replace("%story%", generatedText)
                .replace("%points%", String.valueOf(context.strengthPoints()))
                .replace("%reward_money%", reward.money().toPlainString())
                .replace("%reward_item%", itemName);
    }

    @Override
    protected boolean onAccepted(RandomEventContext context) {
        return plugin.getEventManager().executeForcedEvent("raiding", context);
    }

    private String message(String key) {
        return plugin.getEventManager().getConfig().getString(
                CONFIG_PATH + ".messages." + key, "");
    }
}
