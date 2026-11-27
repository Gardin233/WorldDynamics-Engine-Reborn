package com.thatguycy.worlddynamicsengine.manager.event;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEvent;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventOccurrence;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventRegistry;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Schedules gameplay events and owns their active lifecycle. */
public final class EventManager implements Listener, RandomEventRegistry {
    private static final BigDecimal RAID_SUCCESS_LOSS_RATE = new BigDecimal("0.05");
    private static final String EVENT_ID_PATTERN = "[a-z0-9][a-z0-9_-]{0,63}";

    private final WorldDynamicsEngine plugin;
    private final Map<String, GameEvent> events = new ConcurrentHashMap<>();
    private final Map<String, Plugin> externalEventOwners = new ConcurrentHashMap<>();
    private final Map<UUID, EventOccurrence> activeEvents = new HashMap<>();
    private final Map<String, List<EventOccurrence>> activeTownEvents = new HashMap<>();
    private final Random random = new Random();
    private final NamespacedKey raidOwnerKey;
    private final NamespacedKey netherInvasionKey;
    private final NamespacedKey maddenedAnimalKey;
    private FileConfiguration config;
    private String warnedInvalidFormula;
    private BukkitTask generationTask;

    public EventManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.raidOwnerKey = new NamespacedKey(plugin, "raid_owner");
        this.netherInvasionKey = new NamespacedKey(plugin, "nether_invasion_id");
        this.maddenedAnimalKey = new NamespacedKey(plugin, "maddened_animal_id");
        events.put("raiding", new RaidingEvent(plugin));
        events.put("refugees", new RefugeesEvent(plugin));
        events.put("nether_invasion", new NetherInvasionEvent(plugin));
        events.put("wandering_livestock", new WanderingLivestockEvent(plugin));
        events.put("maddened_animals", new MaddenedAnimalsEvent(plugin));
        events.put("phantom_migration", new PhantomMigrationEvent(plugin));
        events.put("mislead_enemies", new MisleadEnemiesTextEvent(plugin));
        reload();
    }

    public void reload() {
        loadConfig();
        warnedInvalidFormula = null;
    }

    public void start() {
        stop();
        removeOrphanedEntities();
        long interval = Math.max(20L, config.getLong("generation.check-interval-ticks", 6000L));
        generationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkEvents, interval, interval);
    }

    public void stop() {
        if (generationTask != null) {
            generationTask.cancel();
            generationTask = null;
        }
        for (EventOccurrence event : Set.copyOf(activeEvents.values())) {
            finishEvent(event, false);
        }
        activeEvents.clear();
        activeTownEvents.clear();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public ForceEventResult forceEvent(String eventId, String townName, Player requestedTarget) {
        if (!plugin.getServer().isPrimaryThread()) {
            return ForceEventResult.FAILED;
        }
        GameEvent event = events.get(eventId == null ? "" : eventId.toLowerCase(java.util.Locale.ROOT));
        if (event == null) {
            return ForceEventResult.UNKNOWN_EVENT;
        }

        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            return ForceEventResult.INVALID_TOWN;
        }
        WDEtown townRecord = plugin.getTownManager().getTown(town.getName());
        if (townRecord == null) {
            return ForceEventResult.TOWN_NOT_REGISTERED;
        }

        Set<UUID> residentIds = new HashSet<>();
        for (Resident resident : town.getResidents()) {
            if (resident != null && resident.getUUID() != null) {
                residentIds.add(resident.getUUID());
            }
        }
        List<Player> townPlayers = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (residentIds.contains(player.getUniqueId())) {
                townPlayers.add(player);
            }
        }
        if (townPlayers.isEmpty()) {
            return ForceEventResult.NO_ONLINE_PLAYERS;
        }
        if (requestedTarget != null && !townPlayers.contains(requestedTarget)) {
            return ForceEventResult.INVALID_TARGET;
        }

        UUID mayorId = town.getMayor() == null ? null : town.getMayor().getUUID();
        Player mayor = mayorId == null ? null : townPlayers.stream()
                .filter(player -> player.getUniqueId().equals(mayorId))
                .findFirst().orElse(null);
        Player target = requestedTarget != null ? requestedTarget
                : event instanceof RandomTextEvent ? mayor : townPlayers.get(random.nextInt(townPlayers.size()));
        if (target == null) {
            return ForceEventResult.NO_ONLINE_MAYOR;
        }

        int population = Math.max(town.getResidents().size(), townPlayers.size());
        int strengthPoints = 1;
        if (event.requiresStrengthPoints()) {
            String formula = config.getString("generation.strength-formula", "floor(wealth / 10000)");
            int maximumPoints = Math.max(0, config.getInt("generation.maximum-strength-points", 100));
            try {
                strengthPoints = StrengthFormulaEvaluator.calculatePoints(
                        formula, townRecord.getWealth(), maximumPoints);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Cannot force random event '" + event.id()
                        + "' because generation.strength-formula is invalid: " + exception.getMessage());
                return ForceEventResult.INVALID_STRENGTH_FORMULA;
            }
            strengthPoints = StrengthFormulaEvaluator.scalePointsForOnlinePopulation(
                    strengthPoints, townPlayers.size(), population);
            double multiplier = event instanceof RandomTextEvent textEvent
                    ? textEvent.rollStrengthMultiplier(random) : event.strengthMultiplier();
            strengthPoints = StrengthFormulaEvaluator.applyMultiplier(strengthPoints, multiplier);
        }

        RandomEventContext context = new RandomEventContext(
                UUID.randomUUID(), target, town, townRecord.getWealth(), townPlayers.size(),
                population, townPlayers, strengthPoints);
        return executeForcedEvent(event.id(), context)
                ? ForceEventResult.STARTED : ForceEventResult.START_FAILED;
    }

    public enum ForceEventResult {
        STARTED,
        UNKNOWN_EVENT,
        INVALID_TOWN,
        TOWN_NOT_REGISTERED,
        NO_ONLINE_PLAYERS,
        INVALID_TARGET,
        NO_ONLINE_MAYOR,
        INVALID_STRENGTH_FORMULA,
        START_FAILED,
        FAILED
    }

    public CloseEventsResult closeEvents(String eventId, String townName) {
        if (!plugin.getServer().isPrimaryThread()) {
            return new CloseEventsResult(CloseEventsStatus.FAILED, 0);
        }
        boolean closeAllTypes = eventId != null && eventId.equalsIgnoreCase("all");
        if (!closeAllTypes && (eventId == null
                || !events.containsKey(eventId.toLowerCase(java.util.Locale.ROOT)))) {
            return new CloseEventsResult(CloseEventsStatus.UNKNOWN_EVENT, 0);
        }
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            return new CloseEventsResult(CloseEventsStatus.INVALID_TOWN, 0);
        }

        List<EventOccurrence> matches = activeEvents.values().stream()
                .filter(occurrence -> occurrence.townName().equalsIgnoreCase(town.getName()))
                .filter(occurrence -> closeAllTypes
                        || occurrence.eventTypeId().equalsIgnoreCase(eventId))
                .toList();
        if (matches.isEmpty()) {
            return new CloseEventsResult(CloseEventsStatus.NO_ACTIVE_EVENTS, 0);
        }

        for (EventOccurrence occurrence : matches) {
            try {
                occurrence.onForcedClose();
            } catch (RuntimeException exception) {
                logApiFailure(occurrence.eventTypeId(), "forced-close callback", exception);
            } finally {
                finishEvent(occurrence, false);
            }
        }
        return new CloseEventsResult(CloseEventsStatus.CLOSED, matches.size());
    }

    public enum CloseEventsStatus {
        CLOSED,
        UNKNOWN_EVENT,
        INVALID_TOWN,
        NO_ACTIVE_EVENTS,
        FAILED
    }

    public record CloseEventsResult(CloseEventsStatus status, int closedCount) {
    }

    @Override
    public boolean register(Plugin owner, RandomEvent event) {
        if (!plugin.getServer().isPrimaryThread() || owner == null || event == null) {
            return false;
        }

        String eventId;
        double defaultWeight;
        double minimumTownWealth;
        double strengthMultiplier;
        boolean requiresStrengthPoints;
        try {
            eventId = event.id();
            defaultWeight = event.defaultWeight();
            minimumTownWealth = event.minimumTownWealth();
            strengthMultiplier = event.strengthMultiplier();
            requiresStrengthPoints = event.requiresStrengthPoints();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Rejected random event whose metadata failed: " + exception.getMessage());
            return false;
        }
        if (eventId == null || !eventId.matches(EVENT_ID_PATTERN)) {
            plugin.getLogger().warning("Rejected random event with invalid ID: " + eventId);
            return false;
        }
        if (!Double.isFinite(defaultWeight) || defaultWeight < 0.0D
                || !Double.isFinite(strengthMultiplier) || strengthMultiplier < 0.0D
                || Double.isNaN(minimumTownWealth)) {
            plugin.getLogger().warning("Rejected random event with invalid metadata: " + eventId);
            return false;
        }
        if (events.putIfAbsent(eventId, new RegisteredGameEvent(
                eventId, event, defaultWeight, minimumTownWealth,
                strengthMultiplier, requiresStrengthPoints)) != null) {
            return false;
        }

        externalEventOwners.put(eventId, owner);
        return true;
    }

    @Override
    public boolean unregister(Plugin owner, String eventId) {
        if (!plugin.getServer().isPrimaryThread() || owner == null || eventId == null
                || externalEventOwners.get(eventId) != owner) {
            return false;
        }

        externalEventOwners.remove(eventId);
        events.remove(eventId);
        for (EventOccurrence occurrence : List.copyOf(activeEvents.values())) {
            if (occurrence.eventTypeId().equals(eventId)) {
                finishEvent(occurrence, false);
            }
        }
        return true;
    }

    @Override
    public List<String> registeredEventIds() {
        return events.keySet().stream().sorted().toList();
    }

    NamespacedKey getRaidOwnerKey() {
        return raidOwnerKey;
    }

    NamespacedKey getNetherInvasionKey() {
        return netherInvasionKey;
    }

    NamespacedKey getMaddenedAnimalKey() {
        return maddenedAnimalKey;
    }

    String message(String key) {
        return message("raiding", key);
    }

    String message(String eventId, String key) {
        return config.getString(eventId + ".messages." + key, "");
    }

    boolean executeForcedEvent(String eventId, RandomEventContext sourceContext) {
        if (!plugin.getServer().isPrimaryThread() || sourceContext == null) {
            return false;
        }
        GameEvent event = events.get(eventId);
        if (event == null) {
            return false;
        }
        RandomEventContext context = new RandomEventContext(
                UUID.randomUUID(), sourceContext.target(), sourceContext.town(),
                sourceContext.townWealth(), sourceContext.onlinePopulation(),
                sourceContext.population(), sourceContext.townPlayers(), sourceContext.strengthPoints());
        EventOccurrence occurrence = event.execute(context);
        if (occurrence == null) {
            return false;
        }
        if (occurrence.isOngoing()) {
            activeEvents.put(occurrence.eventId(), occurrence);
            activeTownEvents.computeIfAbsent(occurrence.townName(), ignored -> new ArrayList<>()).add(occurrence);
        }
        return true;
    }

    void chargeSuccessfulRaidLoss(UUID playerId, String worldName) {
        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.supportsExactCurrency(economy.getDefaultCurrency())) {
            return;
        }
        String currency = economy.getDefaultCurrency();
        BigDecimal balance = economy.getBalance(playerId, worldName, currency).max(BigDecimal.ZERO);
        BigDecimal loss = balance.multiply(RAID_SUCCESS_LOSS_RATE).setScale(2, RoundingMode.HALF_UP);
        if (loss.signum() > 0 && economy.withdrawExact(
                playerId, worldName, economy.charge(loss, currency)).success()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message("money-lost")
                        .replace("%amount%", economy.format(loss, currency)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            return;
        }
        List<Player> townPlayers = onlineTownPlayers(town.getName(), null);
        for (EventOccurrence activeEvent : activeEventsForTown(town.getName())) {
            activeEvent.updateTownPlayers(townPlayers);
            activeEvent.onPlayerDeath(player.getUniqueId());
            if (activeEvent.finishesWhenSuccessful() && activeEvent.wasSuccessful()) {
                finishEvent(activeEvent, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            return;
        }

        List<EventOccurrence> townEvents = activeEventsForTown(town.getName());
        if (townEvents.isEmpty()) {
            return;
        }
        List<Player> remainingPlayers = onlineTownPlayers(town.getName(), player.getUniqueId());
        for (EventOccurrence activeEvent : townEvents) {
            activeEvent.updateTownPlayers(remainingPlayers);
            if (remainingPlayers.isEmpty() && activeEvent.endsWhenTownEmpty()) {
                finishEvent(activeEvent, activeEvent.wasSuccessful());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        for (EventOccurrence activeEvent : List.copyOf(activeEvents.values())) {
            if (activeEvent.onBlockBreak(event)) {
                finishEvent(activeEvent, true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        for (EventOccurrence activeEvent : List.copyOf(activeEvents.values())) {
            if (activeEvent.shouldCancelBlockPlace(event)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(message("nether-invasion", "building-blocked"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        for (EventOccurrence activeEvent : List.copyOf(activeEvents.values())) {
            if (activeEvent.shouldCancelPortal(event)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) {
            return;
        }
        for (EventOccurrence activeEvent : List.copyOf(activeEvents.values())) {
            if (activeEvent.shouldBlockPortalActivation(event)) {
                event.setCancelled(true);
                notifyPortalActivationBlocked(event);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        for (EventOccurrence activeEvent : List.copyOf(activeEvents.values())) {
            Location ejection = activeEvent.ejectPlayerFromPortal(event);
            if (ejection != null) {
                event.setTo(ejection);
                return;
            }
        }
    }

    private void notifyPortalActivationBlocked(PortalCreateEvent event) {
        String warning = message("nether-invasion", "portal-activation-blocked");
        if (event.getEntity() instanceof Player player) {
            player.sendMessage(warning);
            return;
        }
        if (event.getBlocks().isEmpty()) {
            return;
        }
        Location portalLocation = event.getBlocks().get(0).getLocation().add(0.5D, 0.5D, 0.5D);
        for (Player player : event.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(portalLocation) <= 32.0D * 32.0D) {
                player.sendMessage(warning);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> updateActiveTownPlayers(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> updateActiveTownPlayers(event.getPlayer()));
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        List<String> eventIds = externalEventOwners.entrySet().stream()
                .filter(entry -> entry.getValue() == event.getPlugin())
                .map(Map.Entry::getKey)
                .toList();
        for (String eventId : eventIds) {
            unregister(event.getPlugin(), eventId);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        removeOrphanedEntities(event.getChunk());
    }

    private void checkEvents() {
        reconcileActiveTownEvents();
        if (!config.getBoolean("generation.enabled", true)) {
            return;
        }
        double configuredChance = config.getDouble("generation.chance-per-town-check", 0.05D);
        double chance = Double.isFinite(configuredChance)
                ? Math.max(0.0D, Math.min(1.0D, configuredChance)) : 0.0D;
        double configuredNoEventChance = config.getDouble("generation.no-event-chance", 0.25D);
        double noEventChance = Double.isFinite(configuredNoEventChance)
                ? Math.max(0.0D, Math.min(1.0D, configuredNoEventChance)) : 0.0D;
        String formula = config.getString("generation.strength-formula", "floor(wealth / 10000)");
        int maximumPoints = Math.max(0, config.getInt("generation.maximum-strength-points", 100));

        for (OnlineTown onlineTown : collectOnlineTowns()) {
            String townName = onlineTown.town().getName();
            List<GameEvent> eligibleEvents = eligibleEvents(
                    onlineTown.townWealth(), onlineTown.mayor() != null);
            if (eligibleEvents.isEmpty() || random.nextDouble() >= chance) {
                continue;
            }
            if (random.nextDouble() < noEventChance) {
                continue;
            }

            int strengthPoints = 1;
            boolean hasStrengthEvent = eligibleEvents.stream().anyMatch(GameEvent::requiresStrengthPoints);
            if (hasStrengthEvent) {
                try {
                    strengthPoints = StrengthFormulaEvaluator.calculatePoints(
                            formula, onlineTown.townWealth(), maximumPoints);
                    warnedInvalidFormula = null;
                } catch (IllegalArgumentException exception) {
                    if (!formula.equals(warnedInvalidFormula)) {
                        plugin.getLogger().warning("Invalid generation.strength-formula; skipping strength-dependent events: "
                                + exception.getMessage());
                        warnedInvalidFormula = formula;
                    }
                    eligibleEvents.removeIf(GameEvent::requiresStrengthPoints);
                    strengthPoints = 1;
                }
                if (eligibleEvents.stream().anyMatch(GameEvent::requiresStrengthPoints)) {
                    strengthPoints = StrengthFormulaEvaluator.scalePointsForOnlinePopulation(
                            strengthPoints, onlineTown.onlinePlayers().size(), onlineTown.population());
                    if (strengthPoints <= 0) {
                        eligibleEvents.removeIf(GameEvent::requiresStrengthPoints);
                        strengthPoints = 1;
                    }
                }
            }
            if (eligibleEvents.isEmpty()) {
                continue;
            }

            GameEvent selected = chooseEvent(eligibleEvents);
            if (selected == null) {
                continue;
            }
            Player target = selected instanceof RandomTextEvent
                    ? onlineTown.mayor()
                    : onlineTown.onlinePlayers().get(random.nextInt(onlineTown.onlinePlayers().size()));
            RandomEventContext context = new RandomEventContext(
                    UUID.randomUUID(), target, onlineTown.town(), onlineTown.townWealth(),
                    onlineTown.onlinePlayers().size(), onlineTown.population(),
                    onlineTown.onlinePlayers(), strengthPoints);
            if (selected.requiresStrengthPoints()) {
                double multiplier = selected instanceof RandomTextEvent textEvent
                        ? textEvent.rollStrengthMultiplier(random) : selected.strengthMultiplier();
                context = context.withStrengthPoints(StrengthFormulaEvaluator.applyMultiplier(
                        strengthPoints, multiplier));
            }
            if (!selected.isEligible(context)) {
                continue;
            }
            EventOccurrence occurrence = selected.execute(context);
            if (occurrence != null) {
                if (occurrence.isOngoing()) {
                    activeEvents.put(occurrence.eventId(), occurrence);
                    activeTownEvents.computeIfAbsent(townName, ignored -> new ArrayList<>()).add(occurrence);
                }
            }
        }
    }

    private List<OnlineTown> collectOnlineTowns() {
        Map<String, Town> towns = new LinkedHashMap<>();
        Map<String, List<Player>> onlinePlayers = new LinkedHashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null) {
                continue;
            }
            towns.putIfAbsent(town.getName(), town);
            onlinePlayers.computeIfAbsent(town.getName(), ignored -> new ArrayList<>()).add(player);
        }

        List<OnlineTown> result = new ArrayList<>();
        for (Map.Entry<String, List<Player>> entry : onlinePlayers.entrySet()) {
            Town town = towns.get(entry.getKey());
            WDEtown record = plugin.getTownManager().getTown(entry.getKey());
            if (town == null || record == null) {
                continue;
            }
            Set<UUID> residentIds = new HashSet<>();
            for (Resident resident : town.getResidents()) {
                if (resident != null && resident.getUUID() != null) {
                    residentIds.add(resident.getUUID());
                }
            }
            List<Player> townOnline = entry.getValue().stream()
                    .filter(player -> residentIds.contains(player.getUniqueId()))
                    .toList();
            if (townOnline.isEmpty()) {
                continue;
            }
            int population = Math.max(town.getResidents().size(), townOnline.size());
            UUID mayorId = town.getMayor() == null ? null : town.getMayor().getUUID();
            Player mayor = townOnline.stream()
                    .filter(player -> player.getUniqueId().equals(mayorId))
                    .findFirst().orElse(null);
            result.add(new OnlineTown(town, townOnline, population, record.getWealth(), mayor));
        }
        return result;
    }

    private List<GameEvent> eligibleEvents(double townWealth, boolean mayorOnline) {
        List<GameEvent> eligible = new ArrayList<>();
        for (GameEvent event : List.copyOf(events.values())) {
            if (event instanceof RandomTextEvent && !mayorOnline) {
                continue;
            }
            if (eventWeight(event) <= 0.0D) {
                continue;
            }
            double minimumTownWealth = event.minimumTownWealth();
            if (Double.isNaN(minimumTownWealth) || townWealth <= minimumTownWealth) {
                continue;
            }
            eligible.add(event);
        }
        return eligible;
    }

    private GameEvent chooseEvent(List<GameEvent> eligibleEvents) {
        double totalWeight = eligibleEvents.stream()
                .mapToDouble(this::eventWeight)
                .sum();
        if (!Double.isFinite(totalWeight) || totalWeight <= 0.0D) {
            return null;
        }
        double selection = random.nextDouble() * totalWeight;
        for (GameEvent event : eligibleEvents) {
            selection -= eventWeight(event);
            if (selection < 0.0D) {
                return event;
            }
        }
        return eligibleEvents.get(eligibleEvents.size() - 1);
    }

    private double eventWeight(GameEvent event) {
        double weight = config.getDouble("generation.event-weights." + event.id(), event.defaultWeight());
        return Double.isFinite(weight) ? Math.max(0.0D, weight) : 0.0D;
    }

    private void finishEvent(EventOccurrence occurrence, boolean successful) {
        activeEvents.remove(occurrence.eventId(), occurrence);
        List<EventOccurrence> townEvents = activeTownEvents.get(occurrence.townName());
        if (townEvents != null) {
            townEvents.remove(occurrence);
            if (townEvents.isEmpty()) {
                activeTownEvents.remove(occurrence.townName());
            }
        }
        occurrence.finish(successful);
    }

    void finishEvent(UUID eventId, boolean successful) {
        EventOccurrence occurrence = activeEvents.get(eventId);
        if (occurrence != null) {
            finishEvent(occurrence, successful);
        }
    }

    private void updateActiveTownPlayers(Player player) {
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            return;
        }
        List<EventOccurrence> townEvents = activeEventsForTown(town.getName());
        if (!townEvents.isEmpty()) {
            List<Player> players = onlineTownPlayers(town.getName(), null);
            for (EventOccurrence occurrence : townEvents) {
                occurrence.updateTownPlayers(players);
            }
        }
    }

    private void reconcileActiveTownEvents() {
        for (EventOccurrence occurrence : List.copyOf(activeEvents.values())) {
            List<Player> townPlayers = onlineTownPlayers(occurrence.townName(), null);
            occurrence.updateTownPlayers(townPlayers);
            if (townPlayers.isEmpty() && occurrence.endsWhenTownEmpty()) {
                finishEvent(occurrence, occurrence.wasSuccessful());
            }
        }
    }

    private List<EventOccurrence> activeEventsForTown(String townName) {
        return List.copyOf(activeTownEvents.getOrDefault(townName, List.of()));
    }

    private List<Player> onlineTownPlayers(String townName, UUID excludedPlayer) {
        List<Player> townPlayers = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getUniqueId().equals(excludedPlayer)) {
                continue;
            }
            Town town = TownyAPI.getInstance().getTown(player);
            if (town != null && town.getName().equalsIgnoreCase(townName)) {
                townPlayers.add(player);
            }
        }
        return townPlayers;
    }

    private void removeOrphanedEntities() {
        for (var world : plugin.getServer().getWorlds()) {
            for (var entity : world.getEntities()) {
                removeIfOrphaned(entity);
            }
        }
    }

    private void removeOrphanedEntities(Chunk chunk) {
        for (var entity : chunk.getEntities()) {
            removeIfOrphaned(entity);
        }
    }

    private void removeIfOrphaned(org.bukkit.entity.Entity entity) {
        String owner = entity.getPersistentDataContainer().get(raidOwnerKey, PersistentDataType.STRING);
        if (owner != null) {
            try {
                if (!activeEvents.containsKey(UUID.fromString(owner))) {
                    entity.remove();
                }
            } catch (IllegalArgumentException exception) {
                entity.remove();
            }
        }

        String invasionId = entity.getPersistentDataContainer().get(
                netherInvasionKey, PersistentDataType.STRING);
        if (invasionId != null) {
            try {
                if (!activeEvents.containsKey(UUID.fromString(invasionId))) {
                    entity.remove();
                }
            } catch (IllegalArgumentException exception) {
                entity.remove();
            }
        }

        String maddenedAnimalId = entity.getPersistentDataContainer().get(
                maddenedAnimalKey, PersistentDataType.STRING);
        if (maddenedAnimalId != null) {
            try {
                if (!activeEvents.containsKey(UUID.fromString(maddenedAnimalId))) {
                    entity.remove();
                }
            } catch (IllegalArgumentException exception) {
                entity.remove();
            }
        }
    }

    private void logApiFailure(String eventId, String operation, RuntimeException exception) {
        plugin.getLogger().warning("Random event '" + eventId + "' failed during " + operation
                + ": " + exception.getMessage());
    }

    private final class RegisteredGameEvent implements GameEvent {
        private final String eventId;
        private final RandomEvent delegate;
        private final double defaultWeight;
        private final double minimumTownWealth;
        private final double strengthMultiplier;
        private final boolean requiresStrengthPoints;

        private RegisteredGameEvent(String eventId, RandomEvent delegate, double defaultWeight,
                                    double minimumTownWealth, double strengthMultiplier,
                                    boolean requiresStrengthPoints) {
            this.eventId = eventId;
            this.delegate = delegate;
            this.defaultWeight = defaultWeight;
            this.minimumTownWealth = minimumTownWealth;
            this.strengthMultiplier = strengthMultiplier;
            this.requiresStrengthPoints = requiresStrengthPoints;
        }

        @Override
        public String id() {
            return eventId;
        }

        @Override
        public double defaultWeight() {
            return defaultWeight;
        }

        @Override
        public double minimumTownWealth() {
            return minimumTownWealth;
        }

        @Override
        public boolean requiresStrengthPoints() {
            return requiresStrengthPoints;
        }

        @Override
        public double strengthMultiplier() {
            return strengthMultiplier;
        }

        @Override
        public boolean isEligible(RandomEventContext context) {
            try {
                return delegate.isEligible(context);
            } catch (RuntimeException exception) {
                logApiFailure(eventId, "eligibility check", exception);
                return false;
            }
        }

        @Override
        public EventOccurrence execute(RandomEventContext context) {
            try {
                RandomEventOccurrence occurrence = delegate.execute(context);
                return occurrence == null ? null : new RegisteredOccurrence(
                        context.eventId(), eventId, context.target().getUniqueId(),
                        context.townName(), occurrence);
            } catch (RuntimeException exception) {
                logApiFailure(eventId, "execution", exception);
                return null;
            }
        }
    }

    private final class RegisteredOccurrence implements EventOccurrence {
        private final UUID eventId;
        private final String eventTypeId;
        private final UUID ownerId;
        private final String townName;
        private final RandomEventOccurrence delegate;

        private RegisteredOccurrence(UUID eventId, String eventTypeId, UUID ownerId, String townName,
                                     RandomEventOccurrence delegate) {
            this.eventId = eventId;
            this.eventTypeId = eventTypeId;
            this.ownerId = ownerId;
            this.townName = townName;
            this.delegate = delegate;
        }

        @Override
        public UUID eventId() {
            return eventId;
        }

        @Override
        public String eventTypeId() {
            return eventTypeId;
        }

        @Override
        public UUID ownerId() {
            return ownerId;
        }

        @Override
        public String townName() {
            return townName;
        }

        @Override
        public boolean isOngoing() {
            try {
                return delegate.isOngoing();
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "lifecycle initialization", exception);
                finish(false);
                return false;
            }
        }

        @Override
        public boolean endsWhenTownEmpty() {
            try {
                return delegate.endsWhenTownEmpty();
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "empty-town policy", exception);
                return true;
            }
        }

        @Override
        public void onPlayerDeath(UUID playerId) {
            try {
                delegate.onPlayerDeath(playerId);
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "player-death callback", exception);
            }
        }

        @Override
        public void updateTownPlayers(List<Player> onlinePlayers) {
            try {
                delegate.updateTownPlayers(onlinePlayers);
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "town-player update", exception);
            }
        }

        @Override
        public boolean wasSuccessful() {
            try {
                return delegate.wasSuccessful();
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "success check", exception);
                return false;
            }
        }

        @Override
        public boolean finishesWhenSuccessful() {
            try {
                return delegate.finishesWhenSuccessful();
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "success completion policy", exception);
                return false;
            }
        }

        @Override
        public void finish(boolean successful) {
            try {
                delegate.finish(successful);
            } catch (RuntimeException exception) {
                logApiFailure(eventTypeId, "finish callback", exception);
            }
        }

        @Override
        public void onForcedClose() {
            if (delegate instanceof EventOccurrence occurrence) {
                try {
                    occurrence.onForcedClose();
                } catch (RuntimeException exception) {
                    logApiFailure(eventTypeId, "forced-close callback", exception);
                }
            }
        }
    }

    private record OnlineTown(Town town, List<Player> onlinePlayers, int population,
                              double townWealth, Player mayor) {
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) {
            plugin.saveResource("events.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (var reader = new InputStreamReader(
                java.util.Objects.requireNonNull(plugin.getResource("events.yml")), StandardCharsets.UTF_8)) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = migrateLegacySettings();
            for (String path : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(path) && !config.contains(path)) {
                    config.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                config.save(file);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not repair events.yml defaults: " + exception.getMessage());
        }
    }

    private boolean migrateLegacySettings() {
        boolean changed = false;
        changed |= migrate("raiding.check-interval-ticks", "generation.check-interval-ticks");
        changed |= migrate("raiding.chance-per-check", "generation.chance-per-town-check");
        changed |= migrate("raiding.strength.formula", "generation.strength-formula");
        changed |= migrate("raiding.strength.maximum-points", "generation.maximum-strength-points");
        changed |= migrate("raiding.strength.maximum-mobs-per-raid", "raiding.maximum-mobs-per-raid");
        if (!config.contains("raiding.minimum-town-wealth")
                && config.contains("raiding.minimum-player-wealth")) {
            config.set("raiding.minimum-town-wealth", config.get("raiding.minimum-player-wealth"));
            changed = true;
        }
        if (!config.contains("generation.event-weights.raiding")) {
            config.set("generation.event-weights.raiding",
                    config.getBoolean("raiding.enabled", true) ? 1.0D : 0.0D);
            changed = true;
        }
        for (String path : List.of("raiding.check-interval-ticks", "raiding.chance-per-check",
                "raiding.cooldown-minutes", "generation.town-cooldown-minutes",
                "raiding.enabled", "raiding.minimum-player-wealth",
                "raiding.strength.formula", "raiding.strength.maximum-points",
                "raiding.strength.maximum-mobs-per-raid",
                "nether-invasion.spawn.minimum-radius", "nether-invasion.spawn.maximum-radius")) {
            if (config.contains(path)) {
                config.set(path, null);
                changed = true;
            }
        }
        return changed;
    }

    private boolean migrate(String source, String target) {
        if (config.contains(source) && !config.contains(target)) {
            config.set(target, config.get(source));
            return true;
        }
        return false;
    }
}
