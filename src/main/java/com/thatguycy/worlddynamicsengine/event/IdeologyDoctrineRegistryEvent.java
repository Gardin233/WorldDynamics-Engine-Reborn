package com.thatguycy.worlddynamicsengine.event;

import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 向外部扩展开放戒律定义注册，保留稳定事件包名以兼容现有监听方。
 */
public class IdeologyDoctrineRegistryEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final WorldDynamicsEngine plugin;
    private final Map<String, IdeologyDoctrineDefinition> registeredDoctrines;

    public IdeologyDoctrineRegistryEvent(WorldDynamicsEngine plugin, Map<String, IdeologyDoctrineDefinition> baseDoctrines) {
        this.plugin = plugin;
        this.registeredDoctrines = new LinkedHashMap<>(baseDoctrines);
    }

    public WorldDynamicsEngine getPlugin() {
        return plugin;
    }

    public Collection<IdeologyDoctrineDefinition> getRegisteredDoctrines() {
        return Collections.unmodifiableCollection(registeredDoctrines.values());
    }

    public boolean registerDoctrine(IdeologyDoctrineDefinition definition) {
        if (definition == null || !definition.isValid()) {
            return false;
        }

        String normalizedId = IdeologyDoctrineDefinition.normalizeId(definition.getId());
        if (registeredDoctrines.containsKey(normalizedId)) {
            return false;
        }
        registeredDoctrines.put(normalizedId, definition);
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
