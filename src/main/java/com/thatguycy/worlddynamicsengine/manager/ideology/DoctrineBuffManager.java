package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 戒律原版效果的唯一施加入口，按来源重算并覆盖同类型效果。所有操作限服务器主线程。
 * 管理期间接管对应效果类型，不尝试恢复被覆盖的原版或外部插件效果。
 */
public final class DoctrineBuffManager {
    private final WorldDynamicsEngine plugin;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final Map<String, Consumer<Player>> persistentRefreshers = new LinkedHashMap<>();
    private long tick;

    /** 一名玩家的贡献账本、已施加结果和归属快照，用于过期及改信清理。 */
    private static final class PlayerState {
        final DoctrineBuffCalculator<PotionEffectType> calculator = new DoctrineBuffCalculator<>();
        Map<PotionEffectType, DoctrineBuffCalculator.Total> applied = Map.of();
        final String religion;
        final Map<String, String> attitudes;

        PlayerState(String religion, Map<String, String> attitudes) {
            this.religion = religion;
            this.attitudes = Map.copyOf(attitudes);
        }
    }

    public DoctrineBuffManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 1L, 1L);
    }

    /** 注册需要在玩家信仰或戒律状态变化后重新计算的持续来源。 */
    public void registerPersistentRefresher(String source, Consumer<Player> refresher) {
        requireMainThread();
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Missing buff source");
        persistentRefreshers.put(source, Objects.requireNonNull(refresher, "refresher"));
    }

    /** 持续来源：重复提交相同贡献不累加，也不反复刷新效果。 */
    public void replacePersistent(Player player, String source, Map<PotionEffectType, Integer> levels) {
        replace(player, source, levels, DoctrineBuffCalculator.PERMANENT);
    }

    /** 限时来源：重复提交替换旧贡献，并从当前服务器 tick 刷新本来源有效期。 */
    public void replaceTimed(Player player, String source, Map<PotionEffectType, Integer> levels, int durationTicks) {
        if (durationTicks <= 0) throw new IllegalArgumentException("Non-positive buff duration");
        replace(player, source, levels, tick + durationTicks);
    }

    private void replace(Player player, String source, Map<PotionEffectType, Integer> levels, long endTick) {
        requireMainThread();
        PlayerState state = state(player);
        if (state == null) return;
        state.calculator.replace(source, levels, endTick);
        reconcile(player, state, false);
    }

    /** 按来源加减等级；结果钳制为非负值，到期时间不因加减而自动延长。 */
    public void adjustLevel(Player player, String source, PotionEffectType type, int delta) {
        requireMainThread();
        PlayerState state = state(player);
        if (state == null) return;
        state.calculator.adjust(source, type, delta, tick);
        reconcile(player, state, false);
    }

    public void removeSource(Player player, String source) {
        requireMainThread();
        PlayerState state = players.get(player.getUniqueId());
        if (state == null) return;
        state.calculator.remove(source);
        reconcile(player, state, false);
    }

    /** 查询最终逻辑等级，未提供该效果时返回 0。 */
    public int getLevel(Player player, PotionEffectType type) {
        requireMainThread();
        PlayerState state = players.get(player.getUniqueId());
        if (state == null) return 0;
        reconcile(player, state, false);
        var total = state.applied.get(type);
        return total == null ? 0 : total.level();
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

    private PlayerState state(Player player) {
        if (!plugin.religionEnabled || !player.isOnline() || player.isDead()) {
            clearPlayer(player);
            return null;
        }
        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        var religion = resident == null ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) {
            clearPlayer(player);
            return null;
        }
        PlayerState state = players.get(player.getUniqueId());
        boolean refreshPersistentSources = state != null
                && (!Objects.equals(state.religion, religion.getReligionName())
                || !state.attitudes.equals(religion.getDoctrineAttitudes()));
        if (refreshPersistentSources) {
            clearPlayer(player);
            state = null;
        }
        if (state == null) {
            state = new PlayerState(religion.getReligionName(), religion.getDoctrineAttitudes());
            players.put(player.getUniqueId(), state);
            if (refreshPersistentSources) {
                persistentRefreshers.values().forEach(refresher -> refresher.accept(player));
                state = players.get(player.getUniqueId());
            }
        }
        return state;
    }

    private void pulse() {
        tick++;
        for (UUID uuid : new HashSet<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                players.remove(uuid);
                continue;
            }
            PlayerState state = state(player);
            if (state != null) reconcile(player, state, tick % 10 == 0);
        }
    }

    private void reconcile(Player player, PlayerState state, boolean checkExternalChanges) {
        Map<PotionEffectType, DoctrineBuffCalculator.Total> totals = state.calculator.totals(tick);
        for (PotionEffectType type : state.applied.keySet()) {
            if (!totals.containsKey(type)) player.removePotionEffect(type);
        }
        totals.forEach((type, total) -> {
            PotionEffect current = checkExternalChanges ? player.getPotionEffect(type) : null;
            boolean changed = !total.equals(state.applied.get(type));
            if (changed || checkExternalChanges && (current == null || current.getAmplifier() != total.level() - 1)) {
                int duration = total.endTick() == DoctrineBuffCalculator.PERMANENT
                        ? PotionEffect.INFINITE_DURATION
                        : (int) Math.min(Integer.MAX_VALUE, Math.max(1L, total.endTick() - tick));
                // Explicit replacement permits both upgrades and downgrades; vanilla merging does not.
                player.removePotionEffect(type);
                player.addPotionEffect(new PotionEffect(type, duration, total.level() - 1, true, false, true));
            }
        });
        state.applied = totals;
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Doctrine buffs require the server thread");
    }
}
