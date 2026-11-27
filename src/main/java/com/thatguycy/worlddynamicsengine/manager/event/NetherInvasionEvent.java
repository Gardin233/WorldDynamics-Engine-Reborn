package com.thatguycy.worlddynamicsengine.manager.event;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Opens a sealed, non-traversable portal and sustains a Nether mob invasion around it. */
final class NetherInvasionEvent implements AttackGameEvent {
    private final WorldDynamicsEngine plugin;

    NetherInvasionEvent(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "nether_invasion";
    }

    @Override
    public double minimumTownWealth() {
        double minimum = plugin.getEventManager().getConfig()
                .getDouble("nether-invasion.minimum-town-wealth", 10000.0D);
        return Double.isFinite(minimum) ? Math.max(0.0D, minimum) : 10000.0D;
    }

    @Override
    public EventOccurrence execute(RandomEventContext context) {
        var config = plugin.getEventManager().getConfig();
        Map<EntityType, Double> mobWeights = configuredMobWeights(
                config.getConfigurationSection("nether-invasion.mob-weights"));
        if (mobWeights.isEmpty()) {
            return null;
        }

        ConfigurationSection spawnConfig = config.getConfigurationSection("nether-invasion.spawn");
        int siteAttempts = Math.max(1, getInt(spawnConfig, "site-attempts", 40));
        NetherPortalSite portalSite = NetherPortalSite.find(
                context.town(), siteAttempts, new Random());
        if (portalSite == null) {
            return null;
        }

        double mobsPerPoint = config.getDouble("nether-invasion.initial-mobs-per-strength-point", 0.25D);
        int initialMobLimit = Math.max(0, config.getInt("nether-invasion.maximum-initial-mobs", 12));
        int initialMobCount = NetherInvasionComposition.initialMobCount(
                context.strengthPoints(), mobsPerPoint, initialMobLimit);
        if (initialMobCount <= 0) {
            return null;
        }

        int minimumMobRadius = Math.max(3, getInt(spawnConfig, "minimum-mob-radius", 4));
        int maximumMobRadius = Math.max(minimumMobRadius, getInt(spawnConfig, "maximum-mob-radius", 10));
        int mobAttempts = Math.max(1, getInt(spawnConfig, "mob-attempts", 20));
        int maximumActiveMobs = Math.max(0,
                config.getInt("nether-invasion.reinforcement.maximum-active-mobs", 30));
        int blockProtectionRadius = Math.max(0,
                config.getInt("nether-invasion.protection-radius", 5));

        portalSite.place();
        ActiveInvasion invasion = new ActiveInvasion(
                context, portalSite, mobWeights, minimumMobRadius, maximumMobRadius,
                mobAttempts, maximumActiveMobs, blockProtectionRadius);
        int spawnedMobs = 0;
        for (int i = 0; i < initialMobCount; i++) {
            if (invasion.spawnMob(context.target())) {
                spawnedMobs++;
            }
        }
        if (spawnedMobs == 0) {
            invasion.finish(false);
            return null;
        }

        invasion.startReinforcements();
        invasion.startExpiration();
        context.target().sendMessage(plugin.getEventManager().message("nether-invasion", "started")
                .replace("%town%", context.townName())
                .replace("%count%", String.valueOf(spawnedMobs))
                .replace("%points%", String.valueOf(context.strengthPoints())));
        return invasion;
    }

    private Map<EntityType, Double> configuredMobWeights(ConfigurationSection section) {
        if (section == null) {
            plugin.getLogger().warning("Missing nether-invasion.mob-weights configuration; invasions are disabled.");
            return Map.of();
        }
        Map<EntityType, Double> weights = new EnumMap<>(EntityType.class);
        for (String key : section.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring unknown Nether invasion mob type: " + key);
                continue;
            }
            double weight = section.getDouble(key, 0.0D);
            if (!NetherInvasionComposition.isNetherHostile(type)
                    || !Double.isFinite(weight) || weight <= 0.0D) {
                plugin.getLogger().warning("Ignoring invalid Nether invasion mob weight: " + key);
                continue;
            }
            weights.put(type, weight);
        }
        return weights;
    }

    private static int getInt(ConfigurationSection section, String path, int defaultValue) {
        return section == null ? defaultValue : section.getInt(path, defaultValue);
    }

    private final class ActiveInvasion implements EventOccurrence {
        private final RandomEventContext context;
        private final NetherPortalSite portalSite;
        private final Map<EntityType, Double> mobWeights;
        private final int minimumMobRadius;
        private final int maximumMobRadius;
        private final int mobAttempts;
        private final int maximumActiveMobs;
        private final int blockProtectionRadius;
        private final RaidSuccessTracker successTracker = new RaidSuccessTracker();
        private final String ownerWorld;
        private final Random random = new Random();
        private BukkitTask reinforcementTask;
        private BukkitTask expirationTask;
        private org.bukkit.block.Block brokenFrameBlock;
        private Player currentTarget;
        private boolean finished;
        private boolean portalBroken;
        private boolean expired;

        private ActiveInvasion(RandomEventContext context, NetherPortalSite portalSite,
                               Map<EntityType, Double> mobWeights, int minimumMobRadius,
                               int maximumMobRadius, int mobAttempts, int maximumActiveMobs,
                               int blockProtectionRadius) {
            this.context = context;
            this.portalSite = portalSite;
            this.mobWeights = Map.copyOf(mobWeights);
            this.minimumMobRadius = minimumMobRadius;
            this.maximumMobRadius = maximumMobRadius;
            this.mobAttempts = mobAttempts;
            this.maximumActiveMobs = maximumActiveMobs;
            this.blockProtectionRadius = blockProtectionRadius;
            this.currentTarget = context.target();
            this.ownerWorld = context.target().getWorld().getName();
            context.townPlayers().forEach(this::addParticipant);
            addParticipant(context.target());
        }

        @Override
        public String eventTypeId() {
            return "nether_invasion";
        }

        @Override
        public UUID ownerId() {
            return context.target().getUniqueId();
        }

        @Override
        public UUID eventId() {
            return context.eventId();
        }

        @Override
        public String townName() {
            return context.townName();
        }

        @Override
        public boolean isOngoing() {
            return true;
        }

        @Override
        public boolean endsWhenTownEmpty() {
            return false;
        }

        @Override
        public boolean finishesWhenSuccessful() {
            return true;
        }

        @Override
        public boolean onBlockBreak(BlockBreakEvent event) {
            if (!portalSite.containsFrameBlock(event.getBlock())) {
                return false;
            }
            brokenFrameBlock = event.getBlock();
            portalBroken = true;
            return true;
        }

        @Override
        public boolean shouldCancelBlockPlace(BlockPlaceEvent event) {
            return portalSite.isProtected(
                            event.getBlockPlaced().getLocation(), blockProtectionRadius)
                    || portalSite.isProtected(
                            event.getBlockAgainst().getLocation(), blockProtectionRadius);
        }

        @Override
        public boolean shouldCancelPortal(EntityPortalEvent event) {
            return portalSite.containsPortalLocation(event.getFrom());
        }

        @Override
        public Location ejectPlayerFromPortal(PlayerMoveEvent event) {
            Location destination = event.getTo();
            return portalSite.containsPortalLocation(destination)
                    ? portalSite.ejectionLocation(destination)
                    : null;
        }

        @Override
        public boolean shouldBlockPortalActivation(PortalCreateEvent event) {
            if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) {
                return false;
            }
            World netherWorld = correspondingNetherWorld();
            return netherWorld != null && event.getBlocks().stream()
                    .filter(block -> block.getType() == Material.NETHER_PORTAL)
                    .anyMatch(block -> portalSite.isWithinCorrespondingNetherChunks(
                            netherWorld, block.getLocation(), 10));
        }

        @Override
        public void updateTownPlayers(List<Player> onlinePlayers) {
            onlinePlayers.forEach(this::addParticipant);
            List<Player> livingPlayers = onlinePlayers.stream()
                    .filter(player -> player.isOnline() && !player.isDead())
                    .toList();
            currentTarget = livingPlayers.isEmpty()
                    ? null : livingPlayers.get(random.nextInt(livingPlayers.size()));
            updateMobTargets();
        }

        @Override
        public boolean wasSuccessful() {
            return successTracker.wasSuccessful();
        }

        @Override
        public void onPlayerDeath(UUID playerId) {
            successTracker.recordDeath(playerId);
        }

        @Override
        public void finish(boolean successful) {
            if (finished) {
                return;
            }
            finished = true;
            if (reinforcementTask != null) {
                reinforcementTask.cancel();
                reinforcementTask = null;
            }
            if (expirationTask != null) {
                expirationTask.cancel();
                expirationTask = null;
            }
            removeInvasionMobs();
            portalSite.remove(brokenFrameBlock);
            boolean raidSucceeded = successful && !portalBroken && successTracker.wasSuccessful();
            if (raidSucceeded) {
                plugin.getEventManager().chargeSuccessfulRaidLoss(ownerId(), ownerWorld);
            }
            Player owner = plugin.getServer().getPlayer(ownerId());
            if (owner == null) {
                return;
            }
            if (portalBroken) {
                owner.sendMessage(plugin.getEventManager().message("nether-invasion", "destroyed")
                        .replace("%town%", townName()));
            } else if (raidSucceeded) {
                owner.sendMessage(plugin.getEventManager().message("nether-invasion", "raid-success")
                        .replace("%town%", townName()));
            } else if (expired) {
                owner.sendMessage(plugin.getEventManager().message("nether-invasion", "expired")
                        .replace("%town%", townName()));
            }
        }

        private void addParticipant(Player player) {
            successTracker.addParticipant(player.getUniqueId());
        }

        private World correspondingNetherWorld() {
            World overworld = portalSite.world();
            if (overworld.getEnvironment() != World.Environment.NORMAL) {
                return null;
            }
            World namedNether = plugin.getServer().getWorld(overworld.getName() + "_nether");
            if (namedNether != null && namedNether.getEnvironment() == World.Environment.NETHER) {
                return namedNether;
            }
            List<World> netherWorlds = plugin.getServer().getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NETHER)
                    .toList();
            return netherWorlds.size() == 1 ? netherWorlds.get(0) : null;
        }

        private boolean spawnMob(Player target) {
            if (finished || (maximumActiveMobs > 0 && countActiveMobs() >= maximumActiveMobs)) {
                return false;
            }
            EntityType type = NetherInvasionComposition.chooseMob(mobWeights, random);
            Location spawnLocation = findMobSpawn();
            if (type == null || spawnLocation == null) {
                return false;
            }
            Entity entity = spawnLocation.getWorld().spawnEntity(spawnLocation, type);
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                return false;
            }
            mob.setPersistent(true);
            mob.getPersistentDataContainer().set(
                    plugin.getEventManager().getNetherInvasionKey(),
                    PersistentDataType.STRING, eventId().toString());
            mob.setTarget(target != null && target.isOnline() && !target.isDead() ? target : null);
            return true;
        }

        private void startReinforcements() {
            var config = plugin.getEventManager().getConfig();
            ConfigurationSection reinforcement = config.getConfigurationSection("nether-invasion.reinforcement");
            int minimumMinutes = getInt(reinforcement, "minimum-interval-minutes", 2);
            int maximumMinutes = getInt(reinforcement, "maximum-interval-minutes", 5);
            int strengthReference = getInt(reinforcement, "strength-reference-points", 100);
            long intervalTicks = NetherInvasionComposition.reinforcementIntervalTicks(
                    context.strengthPoints(), strengthReference, minimumMinutes, maximumMinutes);
            reinforcementTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, () -> spawnMob(currentTarget), intervalTicks, intervalTicks);
        }

        private void startExpiration() {
            int durationMinutes = Math.max(1,
                    plugin.getEventManager().getConfig().getInt("nether-invasion.duration-minutes", 60));
            long timeoutTicks = durationMinutes * 1200L;
            expirationTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                expired = true;
                plugin.getEventManager().finishEvent(eventId(), false);
            }, timeoutTicks);
        }

        private Location findMobSpawn() {
            World world = portalSite.center().getWorld();
            Location center = portalSite.center();
            for (int attempt = 0; attempt < mobAttempts; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int radius = minimumMobRadius + random.nextInt(maximumMobRadius - minimumMobRadius + 1);
                int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = world.getHighestBlockYAt(x, z) + 1;
                Location spawn = new Location(world, x + 0.5D, y, z + 0.5D);
                if (y + 1 >= world.getMaxHeight()
                        || !spawn.getBlock().isPassable()
                        || !spawn.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()
                        || !spawn.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType().isSolid()
                        || !TownyWilderness.isUnclaimed(spawn)) {
                    continue;
                }
                return spawn;
            }
            return null;
        }

        private int countActiveMobs() {
            int count = 0;
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String taggedId = entity.getPersistentDataContainer().get(
                            plugin.getEventManager().getNetherInvasionKey(), PersistentDataType.STRING);
                    if (eventId().toString().equals(taggedId) && entity.isValid() && !entity.isDead()) {
                        count++;
                    }
                }
            }
            return count;
        }

        private void updateMobTargets() {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String taggedId = entity.getPersistentDataContainer().get(
                            plugin.getEventManager().getNetherInvasionKey(), PersistentDataType.STRING);
                    if (eventId().toString().equals(taggedId) && entity instanceof Mob mob) {
                        mob.setTarget(currentTarget);
                    }
                }
            }
        }

        private void removeInvasionMobs() {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String taggedId = entity.getPersistentDataContainer().get(
                            plugin.getEventManager().getNetherInvasionKey(), PersistentDataType.STRING);
                    if (eventId().toString().equals(taggedId)) {
                        entity.remove();
                    }
                }
            }
        }
    }
}
