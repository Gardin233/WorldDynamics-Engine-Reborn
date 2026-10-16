package com.thatguycy.worlddynamicsengine.event;

import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeEffect;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 向外部扩展开放戒律效果注册；事件发布时由监听方补充效果定义。
 */
public class IdeologyDoctrineEffectRegistryEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final WorldDynamicsEngine plugin;
    private final List<IdeologyDoctrineAttitudeEffect> registeredEffects;

    public IdeologyDoctrineEffectRegistryEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.registeredEffects = new ArrayList<>();
    }

    public WorldDynamicsEngine getPlugin() {
        return plugin;
    }

    public List<IdeologyDoctrineAttitudeEffect> getRegisteredEffects() {
        return Collections.unmodifiableList(registeredEffects);
    }

    public boolean registerEffect(IdeologyDoctrineAttitudeEffect effect) {
        if (effect == null) {
            return false;
        }
        if (effect.getDoctrineId() == null || effect.getDoctrineId().isBlank()) {
            return false;
        }
        if (effect.getAttitudeId() == null || effect.getAttitudeId().isBlank()) {
            return false;
        }
        registeredEffects.add(effect);
        return true;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
