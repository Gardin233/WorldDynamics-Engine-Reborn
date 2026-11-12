package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.event.PlayerMoodEvent;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Applies and resumes severity-based, timed mental-collapse effects. */
public final class MentalCollapseManager implements Listener {
    private static final String MANUAL_COLLAPSE_FLAG = "wde:manual_collapse";
    private static final String NONHUMANIZATION_DOCTRINE_ID = "namespace:wder/nonhumanization";
    private static final Set<Material> HAZARDOUS_WANDER_BLOCKS = Set.of(
            Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
            Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.LAVA);

    public enum Severity { MILD, MODERATE, SEVERE }

    private enum CollapseEffect {
        SAD_WANDER("sad_wander", "sadWander", Severity.MILD),
        DARK_VISION("dark_vision", "darkVision", Severity.MODERATE),
        CRAZY_TALK("crazy_talk", "crazyTalk", Severity.MODERATE),
        HUMANITY_COLLAPSE("humanity_collapse", "humanityCollapse", Severity.SEVERE),
        CATATONIC_STUPOR("catatonic_stupor", "catatonicStupor", Severity.SEVERE),
        FAITH_CRISIS("faith_crisis", "faithCrisis", Severity.SEVERE);

        private final String id;
        private final String configKey;
        private final Severity severity;

        CollapseEffect(String id, String configKey, Severity severity) {
            this.id = id;
            this.configKey = configKey;
            this.severity = severity;
        }

        static CollapseEffect fromId(String id) {
            if (id == null) return null;
            for (CollapseEffect effect : values()) {
                if (effect.id.equalsIgnoreCase(id.trim())) return effect;
            }
            return null;
        }
    }

    private final WorldDynamicsEngine plugin;
    private final Map<UUID, CollapseEffect> active = new ConcurrentHashMap<>();
    private final Map<UUID, List<BukkitTask>> tasks = new HashMap<>();
    private final Map<UUID, WanderState> wanderStates = new HashMap<>();
    private final Map<UUID, Float> previousWalkSpeeds = new HashMap<>();
    private final Map<UUID, ScriptedCrazyTalk> scriptedCrazyTalk = new ConcurrentHashMap<>();
    private BukkitTask operatorExemptionTask;

    private record ScriptedCrazyTalk(String message, long expiresAt) {}

    private static final class WanderState {
        private Location target;
        private Vector heading = new Vector();
        private int retryTicks;
    }

    private enum WanderStep { BLOCKED, WALK, JUMP }

    public MentalCollapseManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void initializeOnlinePlayers() {
        if (operatorExemptionTask == null) {
            operatorExemptionTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, this::enforceOperatorExemption, 20L, 20L);
        }
        for (Player player : Bukkit.getOnlinePlayers()) resumeCollapse(player);
    }

    /** Cancels runtime tasks but preserves each resident's persisted collapse state. */
    public void stopTasks() {
        if (operatorExemptionTask != null) {
            operatorExemptionTask.cancel();
            operatorExemptionTask = null;
        }
        for (UUID uuid : new HashSet<>(tasks.keySet())) cancelRuntimeTasks(uuid);
        for (UUID uuid : new HashSet<>(previousWalkSpeeds.keySet())) {
            restoreWanderMovement(uuid, Bukkit.getPlayer(uuid));
        }
        active.clear();
        wanderStates.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMood(PlayerMoodEvent event) {
        if (event.getOutcome() != PlayerMoodEvent.Outcome.COLLAPSE || !plugin.religionEnabled) return;
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()
                || plugin.getMoodManager().isMoodExempt(player)
                || hasActiveCollapse(player.getUniqueId())) return;

        CollapseEffect effect = chooseEffect(event);
        if (effect != null) beginCollapse(player, effect, true, false);
    }

    /** Starts a selected collapse directly, including for operators exempt from automatic mood checks. */
    public boolean triggerManualCollapse(Player player, String effectId) {
        if (!plugin.religionEnabled || player == null || !player.isOnline()
                || plugin.getResidentManager().getResident(player.getUniqueId()) == null
                || hasActiveCollapse(player.getUniqueId())) {
            return false;
        }
        CollapseEffect effect = CollapseEffect.fromId(effectId);
        if (effect == null || !isEnabled(effect)) return false;
        beginCollapse(player, effect, true, true);
        return isActive(player.getUniqueId(), effect);
    }

    public List<String> getEnabledManualEffectIds() {
        List<String> ids = new ArrayList<>();
        for (CollapseEffect effect : CollapseEffect.values()) {
            if (isEnabled(effect)) ids.add(effect.id);
        }
        return List.copyOf(ids);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> resumeCollapse(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> resumeCollapse(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        scriptedCrazyTalk.remove(uuid);
        restoreWanderMovement(uuid, event.getPlayer());
        cancelRuntimeTasks(uuid);
        active.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        CollapseEffect effect = active.get(event.getPlayer().getUniqueId());
        if (effect != CollapseEffect.CATATONIC_STUPOR) return;
        if (hasPositionChanged(event)) event.setTo(event.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        CollapseEffect effect = active.get(event.getPlayer().getUniqueId());
        if (effect == CollapseEffect.SAD_WANDER || effect == CollapseEffect.CATATONIC_STUPOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isCatatonic(player)) {
            event.setCancelled(true);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (consumeScriptedCrazyTalk(uuid, event.getMessage())) return;
        if (isActive(uuid, CollapseEffect.CRAZY_TALK)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShear(PlayerShearEntityEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isCatatonic(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isCatatonic(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isCatatonic(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isCatatonic(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (isMovementLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (isMovementLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (isMovementLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (isCatatonic(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player && isCatatonic(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && isCatatonic(player)) {
            event.setCancelled(true);
        }
    }

    private CollapseEffect chooseEffect(PlayerMoodEvent event) {
        if (hasRequiredNonhumanizationAttitude(event.getPlayer())) {
            return isEnabled(CollapseEffect.HUMANITY_COLLAPSE)
                    ? CollapseEffect.HUMANITY_COLLAPSE : null;
        }
        Severity severity = severityFor(event.getMood());
        List<CollapseEffect> candidates = new ArrayList<>();
        Set<String> configuredMemeEffects = new HashSet<>();
        boolean hasMemeEffectFilter = false;
        for (var meme : event.getMemes()) {
            List<String> ids = meme.getOutcomeEffects("collapse");
            hasMemeEffectFilter |= !ids.isEmpty();
            for (String id : ids) {
                if (CollapseEffect.fromId(id) != null) configuredMemeEffects.add(id.toLowerCase(java.util.Locale.ROOT));
            }
        }

        for (CollapseEffect effect : CollapseEffect.values()) {
            if (!isEnabled(effect)) continue;
            if (effect.severity != severity) continue;
            if (hasMemeEffectFilter && !configuredMemeEffects.contains(effect.id)) continue;
            candidates.add(effect);
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean hasRequiredNonhumanizationAttitude(Player player) {
        if (player == null) return false;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) return false;
        String attitude = plugin.getReligionManager().getConfiguredDoctrineAttitudes(religion)
                .get(NONHUMANIZATION_DOCTRINE_ID);
        return isRequiredNonhumanizationAttitude(NONHUMANIZATION_DOCTRINE_ID, attitude);
    }

    static boolean isRequiredNonhumanizationAttitude(String doctrineId, String attitudeId) {
        return NONHUMANIZATION_DOCTRINE_ID.equals(doctrineId)
                && attitudeId != null && attitudeId.endsWith("/required");
    }

    private Severity severityFor(int mood) {
        var config = plugin.getIdeologyConfig();
        int mildMinimum = config == null ? 34 : config.getInt("mood.collapse.severity.mildMinimumMood", 34);
        int moderateMinimum = config == null ? 17 : config.getInt("mood.collapse.severity.moderateMinimumMood", 17);
        return getSeverityForMood(mood, mildMinimum, moderateMinimum);
    }

    public static Severity getSeverityForMood(int mood, int mildMinimum, int moderateMinimum) {
        mildMinimum = Math.max(1, Math.min(49, mildMinimum));
        moderateMinimum = Math.max(0, Math.min(mildMinimum - 1, moderateMinimum));
        if (mood >= mildMinimum) return Severity.MILD;
        if (mood >= moderateMinimum) return Severity.MODERATE;
        return Severity.SEVERE;
    }

    private void beginCollapse(Player player, CollapseEffect effect, boolean newlyTriggered, boolean manuallyTriggered) {
        UUID uuid = player.getUniqueId();
        WDEresident resident = plugin.getResidentManager().getResident(uuid);
        if (resident == null || !player.isOnline()) return;
        var config = plugin.getIdeologyConfig();
        String path = "mood.collapse.effects." + effect.configKey + ".";
        int durationSeconds = config == null ? 180
                : Math.max(1, Math.min(86_400, config.getInt(path + "durationSeconds", 180)));
        long now = System.currentTimeMillis();
        long expiresAt = now + durationSeconds * 1000L;
        resident.setActiveCollapse(effect.id, expiresAt);
        if (manuallyTriggered) resident.addFlag(MANUAL_COLLAPSE_FLAG);
        else resident.removeFlag(MANUAL_COLLAPSE_FLAG);
        plugin.getResidentManager().saveResident(resident);
        active.put(uuid, effect);
        if (newlyTriggered) showCollapseWarning(player, effect);
        startRuntimeEffect(player, effect, expiresAt, newlyTriggered);
    }

    private void resumeCollapse(Player player) {
        if (player == null || !player.isOnline()) return;
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return;
        if (!plugin.religionEnabled) {
            cancelRuntimeTasks(player.getUniqueId());
            active.remove(player.getUniqueId());
            if (hasBlindnessEffect(CollapseEffect.fromId(resident.getActiveCollapseId()))) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            if (!resident.getActiveCollapseId().isBlank() || resident.hasFlag(MANUAL_COLLAPSE_FLAG)) {
                resident.clearActiveCollapse();
                resident.removeFlag(MANUAL_COLLAPSE_FLAG);
                plugin.getResidentManager().saveResident(resident);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (!resident.getActiveCollapseId().isBlank() && player.isOp()
                && !resident.hasFlag(MANUAL_COLLAPSE_FLAG)) {
            cancelAutomaticCollapse(player, resident);
            return;
        }
        if (!resident.hasActiveCollapse(now)) {
            if (!resident.getActiveCollapseId().isBlank()) endCollapse(player.getUniqueId());
            else if (resident.hasFlag(MANUAL_COLLAPSE_FLAG)) {
                resident.removeFlag(MANUAL_COLLAPSE_FLAG);
                plugin.getResidentManager().saveResident(resident);
            }
            return;
        }
        CollapseEffect effect = CollapseEffect.fromId(resident.getActiveCollapseId());
        if (effect == null || !isEnabled(effect)) {
            cancelRuntimeTasks(player.getUniqueId());
            active.remove(player.getUniqueId());
            if (hasBlindnessEffect(effect)) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            resident.clearActiveCollapse();
            resident.removeFlag(MANUAL_COLLAPSE_FLAG);
            plugin.getResidentManager().saveResident(resident);
            return;
        }
        active.put(player.getUniqueId(), effect);
        startRuntimeEffect(player, effect, resident.getActiveCollapseExpiresAt(), false);
    }

    private void enforceOperatorExemption() {
        for (UUID uuid : new HashSet<>(active.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOp()) continue;
            WDEresident resident = plugin.getResidentManager().getResident(uuid);
            if (resident == null || !resident.hasFlag(MANUAL_COLLAPSE_FLAG)) {
                if (resident != null) cancelAutomaticCollapse(player, resident);
                else {
                    cancelRuntimeTasks(uuid);
                    active.remove(uuid);
                }
            }
        }
    }

    private void cancelAutomaticCollapse(Player player, WDEresident resident) {
        UUID uuid = player.getUniqueId();
        CollapseEffect effect = CollapseEffect.fromId(resident.getActiveCollapseId());
        cancelRuntimeTasks(uuid);
        active.remove(uuid);
        if (hasBlindnessEffect(effect)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        resident.clearActiveCollapse();
        resident.removeFlag(MANUAL_COLLAPSE_FLAG);
        plugin.getResidentManager().saveResident(resident);
    }

    private void startRuntimeEffect(Player player, CollapseEffect effect, long expiresAt, boolean newlyTriggered) {
        UUID uuid = player.getUniqueId();
        cancelRuntimeTasks(uuid);
        long remainingTicks = Math.max(1L, (expiresAt - System.currentTimeMillis()) * 20L / 1000L);
        trackTask(uuid, Bukkit.getScheduler().runTaskLater(plugin, () -> endCollapse(uuid), remainingTicks));

        var config = plugin.getIdeologyConfig();
        String path = "mood.collapse.effects." + effect.configKey + ".";
        switch (effect) {
            case SAD_WANDER -> {
                previousWalkSpeeds.putIfAbsent(uuid, player.getWalkSpeed());
                player.setWalkSpeed(0.0F);
                wanderStates.put(uuid, new WanderState());
                trackTask(uuid, Bukkit.getScheduler().runTaskTimer(plugin,
                        () -> wander(uuid), 1L, 1L));
            }
            case DARK_VISION -> {
                applyBlindness(player, remainingTicks);
            }
            case CRAZY_TALK -> {
                long interval = config == null ? 20L * 15
                        : Math.max(20L, config.getLong(path + "intervalSeconds", 15L) * 20L);
                trackTask(uuid, Bukkit.getScheduler().runTaskTimer(plugin,
                        () -> sendRandomMessage(uuid), interval, interval));
            }
            case CATATONIC_STUPOR -> {
                player.setVelocity(new Vector(0, 0, 0));
            }
            case HUMANITY_COLLAPSE -> {
                applyBlindness(player, remainingTicks);
                scheduleHumanityCollapseFlow(uuid, expiresAt);
            }
            case FAITH_CRISIS -> { if (newlyTriggered) randomizeFaith(player); }
        }
    }

    private static boolean hasBlindnessEffect(CollapseEffect effect) {
        return effect == CollapseEffect.DARK_VISION || effect == CollapseEffect.HUMANITY_COLLAPSE;
    }

    private static void applyBlindness(Player player, long durationTicks) {
        int ticks = (int) Math.min(Integer.MAX_VALUE, durationTicks);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, true, false, true), true);
    }

    private void scheduleHumanityCollapseFlow(UUID uuid, long expiresAt) {
        var config = plugin.getIdeologyConfig();
        if (config == null) return;
        String path = "mood.collapse.effects.humanityCollapse.";
        List<Map<?, ?>> sequence = config.getMapList(path + "sequence");
        long durationSeconds = Math.max(1L, Math.min(86_400L,
                config.getLong(path + "durationSeconds", 180L)));
        long durationMillis = durationSeconds * 1000L;
        long startedAt = expiresAt - durationMillis;
        long now = System.currentTimeMillis();
        for (Map<?, ?> step : sequence) {
            Object rawDelay = step.get("delaySeconds");
            long delaySeconds = rawDelay instanceof Number number
                    ? Math.max(0L, Math.min(86_400L, number.longValue())) : 0L;
            long showAt = startedAt + delaySeconds * 1000L;
            if (showAt < now || showAt >= expiresAt) continue;

            String title = promptText(step.get("title"));
            String subtitle = promptText(step.get("subtitle"));
            String chat = promptText(step.get("chat"));
            long delayTicks = Math.max(1L, (showAt - now + 49L) / 50L);
            trackTask(uuid, Bukkit.getScheduler().runTaskLater(plugin,
                    () -> showHumanityCollapsePrompt(uuid, title, subtitle, chat), delayTicks));
        }
    }

    private static String promptText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void showHumanityCollapsePrompt(UUID uuid, String title, String subtitle, String chat) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !isActive(uuid, CollapseEffect.HUMANITY_COLLAPSE)) return;
        var config = plugin.getIdeologyConfig();
        String path = "mood.collapse.effects.humanityCollapse.";
        int fadeIn = configuredTitleTicks(config, path + "titleFadeInTicks", 5, 0);
        int stay = configuredTitleTicks(config, path + "titleStayTicks", 40, 1);
        int fadeOut = configuredTitleTicks(config, path + "titleFadeOutTicks", 10, 0);
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        String name = player.getName();
        if (!title.isBlank() || !subtitle.isBlank()) {
            player.showTitle(Title.title(
                    serializer.deserialize(title.replace("%player_name%", name)),
                    serializer.deserialize(subtitle.replace("%player_name%", name)),
                    Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L))));
        }
        if (!chat.isBlank()) {
            player.sendMessage(serializer.deserialize(chat.replace("%player_name%", name)));
        }
    }

    private static int configuredTitleTicks(org.bukkit.configuration.file.FileConfiguration config,
                                            String path, int fallback, int minimum) {
        int value = config == null ? fallback : config.getInt(path, fallback);
        return Math.max(minimum, Math.min(200, value));
    }

    private void wander(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !isActive(uuid, CollapseEffect.SAD_WANDER)) return;
        WanderState state = wanderStates.computeIfAbsent(uuid, ignored -> new WanderState());
        Location current = player.getLocation();
        if (state.retryTicks > 0) {
            state.retryTicks--;
            stopHorizontalMovement(player);
            return;
        }

        if (state.target == null || state.target.getWorld() != current.getWorld()
                || horizontalDistanceSquared(current, state.target) < 0.5) {
            state.target = chooseWanderTarget(current);
            if (state.target == null) {
                state.retryTicks = 10;
                stopHorizontalMovement(player);
                return;
            }
        }

        Vector desiredHeading = state.target.toVector().subtract(current.toVector()).setY(0);
        if (desiredHeading.lengthSquared() < 1.0e-4) {
            state.target = null;
            return;
        }
        desiredHeading.normalize();
        Vector heading = state.heading.lengthSquared() < 1.0e-4
                ? desiredHeading
                : state.heading.clone().multiply(0.82).add(desiredHeading.clone().multiply(0.18));
        if (heading.lengthSquared() < 1.0e-4) heading = desiredHeading;
        heading.normalize();
        state.heading = heading;

        WanderStep step = assessWanderStep(current, heading);
        if (step == WanderStep.BLOCKED) {
            state.target = null;
            state.heading.zero();
            state.retryTicks = 2;
            stopHorizontalMovement(player);
            return;
        }

        var config = plugin.getIdeologyConfig();
        String path = "mood.collapse.effects.sadWander.";
        double speed = config == null ? 0.11
                : Math.max(0.04, Math.min(0.25, config.getDouble(path + "walkSpeed", 0.11)));
        Vector currentVelocity = player.getVelocity();
        double verticalVelocity = player.isOnGround()
                ? step == WanderStep.JUMP ? 0.42 : 0.0
                : currentVelocity.getY();
        player.setVelocity(new Vector(heading.getX() * speed, verticalVelocity, heading.getZ() * speed));
        float yaw = (float) Math.toDegrees(Math.atan2(-heading.getX(), heading.getZ()));
        player.setRotation(yaw, current.getPitch());
    }

    private Location chooseWanderTarget(Location origin) {
        var config = plugin.getIdeologyConfig();
        double radius = config == null ? 6.0
                : Math.max(2.0, Math.min(12.0,
                        config.getDouble("mood.collapse.effects.sadWander.wanderRadius", 6.0)));
        double minimumDistance = Math.min(1.5, radius * 0.5);
        int searchRadius = (int) Math.ceil(radius);
        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        List<Location> candidates = new ArrayList<>();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                double targetX = originX + dx + 0.5;
                double targetZ = originZ + dz + 0.5;
                double distanceSquared = Math.pow(targetX - origin.getX(), 2)
                        + Math.pow(targetZ - origin.getZ(), 2);
                if (distanceSquared < minimumDistance * minimumDistance
                        || distanceSquared > radius * radius) {
                    continue;
                }
                Location target = new Location(origin.getWorld(), targetX, origin.getY(), targetZ);
                if (hasSafeWanderTile(target)) candidates.add(target);
            }
        }
        return candidates.isEmpty()
                ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private WanderStep assessWanderStep(Location current, Vector heading) {
        Location nextStep = current.clone().add(heading.clone().multiply(0.65));
        int feetY = getWanderFeetBlockY(nextStep.getY());
        if (hasSafeWanderTileAt(nextStep, feetY)) return WanderStep.WALK;
        if (hasSafeWanderTileAt(nextStep, feetY + 1)) return WanderStep.JUMP;
        if (hasSafeWanderTileAt(nextStep, feetY - 1)) return WanderStep.WALK;
        return WanderStep.BLOCKED;
    }

    static int getWanderFeetBlockY(double feetY) {
        return (int) Math.ceil(feetY - 1.0e-4);
    }

    private boolean hasSafeWanderTile(Location location) {
        if (location.getWorld() == null) return false;
        int baseY = location.getBlockY();
        return hasSafeWanderTileAt(location, baseY)
                || hasSafeWanderTileAt(location, baseY + 1)
                || hasSafeWanderTileAt(location, baseY - 1);
    }

    private boolean hasSafeWanderTileAt(Location location, int feetY) {
        if (location.getWorld() == null) return false;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        Block floor = location.getWorld().getBlockAt(x, feetY - 1, z);
        Block feet = location.getWorld().getBlockAt(x, feetY, z);
        Block head = location.getWorld().getBlockAt(x, feetY + 1, z);
        return isSafeWanderFloorType(floor.getType()) && hasWanderFloorCollision(floor)
                && feet.isPassable() && head.isPassable()
                && !feet.isLiquid() && !head.isLiquid()
                && !HAZARDOUS_WANDER_BLOCKS.contains(feet.getType())
                && !HAZARDOUS_WANDER_BLOCKS.contains(head.getType());
    }

    static boolean isSafeWanderFloorType(Material material) {
        return material != null
                && material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR
                && material != Material.WATER && material != Material.LAVA
                && !HAZARDOUS_WANDER_BLOCKS.contains(material);
    }

    private static boolean hasWanderFloorCollision(Block floor) {
        return floor.getCollisionShape().getBoundingBoxes().stream()
                .anyMatch(box -> box.getMaxY() > 0.0);
    }

    private static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static void stopHorizontalMovement(Player player) {
        Vector velocity = player.getVelocity();
        player.setVelocity(new Vector(0, velocity.getY(), 0));
    }

    private void sendRandomMessage(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !isActive(uuid, CollapseEffect.CRAZY_TALK)) return;
        var config = plugin.getIdeologyConfig();
        List<String> messages = config == null ? List.of()
                : config.getStringList("mood.collapse.effects.crazyTalk.messages");
        if (messages.isEmpty()) return;
        String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        if (message != null && !message.isBlank() && !message.startsWith("/")) {
            scriptedCrazyTalk.put(uuid, new ScriptedCrazyTalk(message, System.currentTimeMillis() + 5_000L));
            player.chat(message);
        }
    }

    private boolean consumeScriptedCrazyTalk(UUID uuid, String message) {
        ScriptedCrazyTalk pending = scriptedCrazyTalk.get(uuid);
        if (pending == null) return false;
        if (pending.expiresAt() < System.currentTimeMillis()) {
            scriptedCrazyTalk.remove(uuid, pending);
            return false;
        }
        return pending.message().equals(message) && scriptedCrazyTalk.remove(uuid, pending);
    }

    private void showCollapseWarning(Player player, CollapseEffect effect) {
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        String title = plugin.getLocaleMessage("mood_collapse_warning_title");
        String effectName = plugin.getLocaleMessage("mood_collapse_effect_" + effect.id);
        String subtitle = plugin.formatLocaleMessage("mood_collapse_warning_effect", effectName);
        player.showTitle(Title.title(
                serializer.deserialize(title),
                serializer.deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500))));
    }

    private boolean randomizeFaith(Player player) {
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        if (resident == null) return false;
        ReligionManager religions = plugin.getReligionManager();
        IdeologyChurchManager churchManager = plugin.getIdeologyChurchManager();
        String current = resident.getReligionName();

        if (religions.getLedIdeology(player.getUniqueId()) != null
                && !religions.canLeaveLeadership(player.getUniqueId())) return false;
        if (churchManager != null && churchManager.isChurchHead(player.getUniqueId())) return false;

        List<String> choices = new ArrayList<>();
        for (String name : religions.getAllReligionNames()) {
            if (!name.equalsIgnoreCase(current)) choices.add(name);
        }
        choices.add("None");
        if (choices.size() == 1 && choices.getFirst().equalsIgnoreCase(current)) return false;

        String selected = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        resident.setReligionName(selected);
        resident.setReligionChosen(true);
        plugin.getResidentManager().saveResidents();
        religions.onResidentIdeologyChanged(player.getUniqueId());
        if (churchManager != null) churchManager.onResidentIdeologyChanged(player.getUniqueId());
        return true;
    }

    private void endCollapse(UUID uuid) {
        cancelRuntimeTasks(uuid);
        active.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        WDEresident resident = plugin.getResidentManager().getResident(uuid);
        if (resident == null || resident.getActiveCollapseId().isBlank()) return;
        CollapseEffect effect = CollapseEffect.fromId(resident.getActiveCollapseId());
        if (hasBlindnessEffect(effect) && player != null) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        boolean manuallyTriggered = resident.hasFlag(MANUAL_COLLAPSE_FLAG);
        resident.clearActiveCollapse();
        resident.removeFlag(MANUAL_COLLAPSE_FLAG);
        boolean dehumanizedByCollapse = effect == CollapseEffect.HUMANITY_COLLAPSE;
        if (dehumanizedByCollapse) resident.setDehumanized(true);
        var config = plugin.getIdeologyConfig();
        int releaseBonus = config == null ? 40
                : Math.max(0, Math.min(100, config.getInt("mood.collapse.release.moodBonus", 40)));
        int releaseMinutes = config == null ? 30
                : Math.max(1, Math.min(1440,
                        config.getInt("mood.collapse.release.durationMinutes", 30)));
        if (!dehumanizedByCollapse && releaseBonus > 0 && (player == null || !player.isOp() || manuallyTriggered)) {
            long releaseExpiresAt = System.currentTimeMillis() + releaseMinutes * 60_000L;
            resident.addMoodChange("relief", releaseBonus, releaseExpiresAt);
        }
        plugin.getResidentManager().saveResident(resident);
    }

    private void trackTask(UUID uuid, BukkitTask task) {
        tasks.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(task);
    }

    private void cancelRuntimeTasks(UUID uuid) {
        List<BukkitTask> playerTasks = tasks.remove(uuid);
        if (playerTasks != null) playerTasks.forEach(BukkitTask::cancel);
        scriptedCrazyTalk.remove(uuid);
        restoreWanderMovement(uuid, Bukkit.getPlayer(uuid));
    }

    private void restoreWanderMovement(UUID uuid, Player player) {
        Float previousWalkSpeed = previousWalkSpeeds.remove(uuid);
        wanderStates.remove(uuid);
        if (player != null && previousWalkSpeed != null) {
            player.setWalkSpeed(previousWalkSpeed);
            stopHorizontalMovement(player);
        }
    }

    private boolean isActive(UUID uuid, CollapseEffect effect) {
        return active.get(uuid) == effect;
    }

    private boolean isEnabled(CollapseEffect effect) {
        var config = plugin.getIdeologyConfig();
        String path = "mood.collapse.effects." + effect.configKey + ".enabled";
        return config == null || config.getBoolean(path, true);
    }

    private boolean hasActiveCollapse(UUID uuid) {
        return active.containsKey(uuid);
    }

    private boolean isCatatonic(Player player) {
        return isActive(player.getUniqueId(), CollapseEffect.CATATONIC_STUPOR);
    }

    private boolean isMovementLocked(Player player) {
        CollapseEffect effect = active.get(player.getUniqueId());
        return effect == CollapseEffect.SAD_WANDER || effect == CollapseEffect.CATATONIC_STUPOR;
    }

    private boolean hasPositionChanged(PlayerMoveEvent event) {
        return event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()
                || event.getFrom().getYaw() != event.getTo().getYaw()
                || event.getFrom().getPitch() != event.getTo().getPitch();
    }
}
