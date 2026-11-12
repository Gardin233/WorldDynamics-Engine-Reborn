package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies persistent non-doctrine potion effects such as religious relic effects.
 * All operations run on the server thread.
 * 管理期间接管对应效果类型，不尝试恢复被覆盖的原版或外部插件效果。
 */
public final class DoctrineBuffManager {
    private final WorldDynamicsEngine plugin;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private long pulse;

    /** Per-source contributions and the effects last applied by this manager. */
    private static final class PlayerState {
        final Map<String, Map<PotionEffectType, Integer>> sources = new HashMap<>();
        Map<PotionEffectType, Integer> applied = Map.of();
    }

    public DoctrineBuffManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 1L, 1L);
    }

    /** Replaces a persistent source contribution. */
    public void replacePersistent(Player player, String source, Map<PotionEffectType, Integer> levels) {
        requireMainThread();
        if (!valid(player) || source == null || source.isBlank()) return;
        PlayerState state = players.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
        Map<PotionEffectType, Integer> checked = new HashMap<>();
        if (levels != null) {
            levels.forEach((type, level) -> {
                if (type != null && level != null && level > 0) checked.put(type, Math.min(256, level));
            });
        }
        if (checked.isEmpty()) state.sources.remove(source);
        else state.sources.put(source, Map.copyOf(checked));
        reconcile(player, state, false);
    }

    public void removeSource(Player player, String source) {
        requireMainThread();
        PlayerState state = players.get(player.getUniqueId());
        if (state == null) return;
        state.sources.remove(source);
        reconcile(player, state, false);
    }

    public void clearPlayer(Player player) {
        requireMainThread();
        PlayerState state = players.remove(player.getUniqueId());
        if (state != null) state.applied.keySet().forEach(player::removePotionEffect);
    }

    public void clearAll() {
        requireMainThread();
        for (UUID uuid : new HashSet<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) clearPlayer(player);
        }
        players.clear();
    }

    private void pulse() {
        pulse++;
        for (UUID uuid : new HashSet<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                players.remove(uuid);
                continue;
            }
            PlayerState state = players.get(uuid);
            if (state != null) reconcile(player, state, pulse % 10 == 0);
        }
    }

    private void reconcile(Player player, PlayerState state, boolean checkExternalChanges) {
        Map<PotionEffectType, Integer> totals = new HashMap<>();
        for (Map<PotionEffectType, Integer> contribution : state.sources.values()) {
            contribution.forEach((type, level) ->
                    totals.merge(type, level, (first, second) -> Math.min(256, first + second)));
        }
        for (PotionEffectType type : state.applied.keySet()) {
            if (!totals.containsKey(type)) player.removePotionEffect(type);
        }
        totals.forEach((type, level) -> {
            PotionEffect current = checkExternalChanges ? player.getPotionEffect(type) : null;
            boolean changed = !level.equals(state.applied.get(type));
            if (changed || checkExternalChanges && (current == null || current.getAmplifier() != level - 1)) {
                // Explicit replacement permits both upgrades and downgrades; vanilla merging does not.
                player.removePotionEffect(type);
                player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION,
                        level - 1, true, false, true));
            }
        });
        state.applied = Map.copyOf(totals);
        if (state.sources.isEmpty() && state.applied.isEmpty()) players.remove(player.getUniqueId());
    }

    private boolean valid(Player player) {
        if (player == null || !plugin.religionEnabled || !player.isOnline() || player.isDead()) {
            if (player != null) clearPlayer(player);
            return false;
        }
        return true;
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Doctrine buffs require the server thread");
    }
}
