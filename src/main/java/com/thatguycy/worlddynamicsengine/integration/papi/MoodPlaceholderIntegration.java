package com.thatguycy.worlddynamicsengine.integration.papi;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Keeps the optional PlaceholderAPI dependency isolated from the main plugin class. */
public final class MoodPlaceholderIntegration {
    private static final String EXPANSION_CLASS =
            "com.thatguycy.worlddynamicsengine.integration.papi.WDERMoodExpansion";
    private final WorldDynamicsEngine plugin;
    private Object expansion;

    public MoodPlaceholderIntegration(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        try {
            Class<?> expansionClass = Class.forName(EXPANSION_CLASS, true, plugin.getClass().getClassLoader());
            expansion = expansionClass.getConstructor(WorldDynamicsEngine.class).newInstance(plugin);
            Method register = expansionClass.getMethod("register");
            return (boolean) register.invoke(expansion);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not load the PlaceholderAPI mood expansion: "
                    + getCauseMessage(exception));
            expansion = null;
            return false;
        }
    }

    public void unregister() {
        if (expansion == null) {
            return;
        }
        try {
            Method isRegistered = expansion.getClass().getMethod("isRegistered");
            if ((boolean) isRegistered.invoke(expansion)) {
                Method unregister = expansion.getClass().getMethod("unregister");
                unregister.invoke(expansion);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not unregister the PlaceholderAPI mood expansion: "
                    + getCauseMessage(exception));
        } finally {
            expansion = null;
        }
    }

    private static String getCauseMessage(Throwable exception) {
        if (exception instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return String.valueOf(invocation.getCause().getMessage());
        }
        return String.valueOf(exception.getMessage());
    }
}
