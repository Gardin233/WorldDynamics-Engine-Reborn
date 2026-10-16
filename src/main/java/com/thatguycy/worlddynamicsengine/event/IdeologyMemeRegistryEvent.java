package com.thatguycy.worlddynamicsengine.event;

import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 向外部扩展开放模因定义注册，注册结果由意识形态管理器统一校验和使用。
 */
public class IdeologyMemeRegistryEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final WorldDynamicsEngine plugin;
    private final Map<String, IdeologyMemeDefinition> registeredMemes;

    public IdeologyMemeRegistryEvent(WorldDynamicsEngine plugin, Map<String, IdeologyMemeDefinition> baseMemes) {
        this.plugin = plugin;
        this.registeredMemes = new LinkedHashMap<>(baseMemes);
    }

    public WorldDynamicsEngine getPlugin() {
        return plugin;
    }

    public Collection<IdeologyMemeDefinition> getRegisteredMemes() {
        return Collections.unmodifiableCollection(registeredMemes.values());
    }

    public boolean registerMeme(String id, String title, String description) {
        return registerMeme(new IdeologyMemeDefinition(id, title, description, "external-listener"));
    }

    public boolean registerMeme(String id, String title, String description, Collection<String> exclusiveWith) {
        Set<String> normalizedExclusiveWith = new LinkedHashSet<>();
        if (exclusiveWith != null) {
            normalizedExclusiveWith.addAll(exclusiveWith);
        }
        return registerMeme(new IdeologyMemeDefinition(id, title, description, "external-listener", normalizedExclusiveWith));
    }

    public boolean registerMeme(IdeologyMemeDefinition definition) {
        if (definition == null || !definition.isValid()) {
            return false;
        }
        String normalizedId = definition.getId();
        if (registeredMemes.containsKey(normalizedId)) {
            return false;
        }
        registeredMemes.put(normalizedId, definition);
        return true;
    }

    public IdeologyMemeDefinition getMeme(String id) {
        return registeredMemes.get(IdeologyMemeDefinition.normalizeId(id));
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
