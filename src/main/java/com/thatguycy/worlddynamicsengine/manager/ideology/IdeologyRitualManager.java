package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.model.IdeologyRitualDefinition;
import com.thatguycy.worlddynamicsengine.model.WDEChurch;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** 管理意识形态仪式的注册、金库扣费、运行中会话和结果判定。 */
public final class IdeologyRitualManager {
    private static final String STORAGE_CATEGORY = "ideology_rituals";
    private static final String CHURCHES_CATEGORY = "ideology_churches";
    private static final String RELIGIONS_CATEGORY = "religions";
    private static final int MONEY_SCALE = 2;

    public enum RitualTemplate {
        INSPIRING_SPEECH("inspiring_speech", "激励演讲", 0),
        HONOR_DUEL("honor_duel", "荣耀对决", 2);

        private final String id;
        private final String displayName;
        private final int requiredDuelists;

        RitualTemplate(String id, String displayName, int requiredDuelists) {
            this.id = id;
            this.displayName = displayName;
            this.requiredDuelists = requiredDuelists;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public int requiredDuelists() {
            return requiredDuelists;
        }

        public static RitualTemplate resolve(String raw) {
            if (raw == null) return null;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (RitualTemplate template : values()) {
                if (template.id.equals(normalized)
                        || template.name().equalsIgnoreCase(normalized)
                        || template.displayName.equalsIgnoreCase(raw)) {
                    return template;
                }
            }
            if ("speech".equals(normalized) || "演讲".equals(raw)) return INSPIRING_SPEECH;
            if ("duel".equals(normalized) || "对决".equals(raw)) return HONOR_DUEL;
            return null;
        }
    }

    public enum RitualResult {
        TERRIBLE("糟糕"),
        MEDIOCRE("平庸"),
        SUCCESS("成功"),
        PERFECT("完美");

        private final String displayName;

        RitualResult(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private record ActiveRitual(UUID sessionUuid, IdeologyRitualDefinition definition,
            RitualTemplate template, UUID hostUuid, String hostName, List<UUID> duelistUuids,
            long startedAt, long endsAt) {}

    private final WorldDynamicsEngine plugin;
    private final DataStorageManager storage;
    private final Map<UUID, IdeologyRitualDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, ActiveRitual> activeBySession = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeSessionByCoreParticipant = new LinkedHashMap<>();

    public IdeologyRitualManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.storage = plugin.getDataStorageManager();
        loadAll();
    }

    public synchronized void loadAll() {
        definitions.clear();
        for (Map.Entry<UUID, IdeologyRitualDefinition> entry
                : storage.loadUuidKeyed(STORAGE_CATEGORY, IdeologyRitualDefinition.class).entrySet()) {
            definitions.put(entry.getKey(), entry.getValue());
        }
    }

    public synchronized List<IdeologyRitualDefinition> getRituals(String ideologyName) {
        WDEreligion ideology = plugin.getReligionManager().getReligion(ideologyName);
        if (ideology == null) return List.of();
        return definitions.values().stream()
                .filter(definition -> definition.getIdeologyUuid().equals(ideology.getIdeologyUuid()))
                .sorted(Comparator.comparing(IdeologyRitualDefinition::getRitualName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized IdeologyRitualDefinition registerRitual(
            Player actor, String ideologyName, String templateToken, String ritualName) {
        if (actor == null) throw new IllegalArgumentException("A player is required.");
        WDEreligion ideology = requireIdeology(ideologyName);
        RitualTemplate template = RitualTemplate.resolve(templateToken);
        if (template == null) throw new IllegalArgumentException("Unknown ritual template.");
        validateRitualName(ritualName);
        if (getRituals(ideology.getReligionName()).stream()
                .anyMatch(definition -> definition.getRitualName().equalsIgnoreCase(ritualName))) {
            throw new IllegalArgumentException("A ritual with that name already exists.");
        }
        requireDefiningAuthority(actor, ideology);

        BigDecimal cost = readCreationCost();
        long now = System.currentTimeMillis();
        IdeologyRitualDefinition definition = new IdeologyRitualDefinition(
                UUID.randomUUID(), ideology.getIdeologyUuid(), ideology.getReligionName(),
                template.id(), ritualName, actor.getUniqueId(), actor.getName(), now, cost);

        if (ideology.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL) {
            registerSynodalRitual(actor, ideology, definition, cost, now);
        } else {
            WDEreligion updated = WDEreligion.deserialize(ideology.serialize());
            if (cost.signum() > 0) updated.debitGovernanceFee(cost);
            storage.saveEntities(List.of(
                    write(RELIGIONS_CATEGORY, updated.getReligionName().toLowerCase(Locale.ROOT), updated),
                    write(STORAGE_CATEGORY, definition.getRitualUuid().toString(), definition)));
            plugin.getReligionManager().publishPersistedReligion(updated);
        }
        definitions.put(definition.getRitualUuid(), definition);
        return definition;
    }

    public synchronized UUID startRitual(Player host, String ideologyName, String ritualToken, List<Player> duelists) {
        if (host == null) throw new IllegalArgumentException("A host is required.");
        WDEreligion ideology = requireIdeology(ideologyName);
        WDEresident hostResident = plugin.getResidentManager().getResident(host.getUniqueId());
        if (hostResident == null || !ideology.getReligionName().equalsIgnoreCase(hostResident.getReligionName())
                || ideology.isPlayerExcommunicated(host.getUniqueId())) {
            throw new IllegalArgumentException("The host must follow this ideology.");
        }
        IdeologyRitualDefinition definition = findRitual(ideology, ritualToken);
        if (definition == null) throw new IllegalArgumentException("Ritual not found.");
        RitualTemplate template = RitualTemplate.resolve(definition.getTemplateId());
        if (template == null) throw new IllegalStateException("Ritual template is no longer available.");
        if (activeSessionByCoreParticipant.containsKey(host.getUniqueId())) {
            throw new IllegalArgumentException("The host is already part of an active ritual.");
        }

        List<Player> requiredDuelists = duelists == null ? List.of() : duelists;
        if (requiredDuelists.size() != template.requiredDuelists()) {
            throw new IllegalArgumentException("Invalid ritual participant count.");
        }
        List<UUID> duelistUuids = new ArrayList<>();
        Set<UUID> uniqueDuelists = new LinkedHashSet<>();
        for (Player duelist : requiredDuelists) {
            validateDuelist(ideology, host, duelist, uniqueDuelists);
            duelistUuids.add(duelist.getUniqueId());
        }

        long now = System.currentTimeMillis();
        UUID sessionUuid = UUID.randomUUID();
        ActiveRitual session = new ActiveRitual(sessionUuid, definition, template,
                host.getUniqueId(), host.getName(), duelistUuids, now,
                Math.addExact(now, Math.multiplyExact(durationSeconds(), 1000L)));
        activeBySession.put(sessionUuid, session);
        activeSessionByCoreParticipant.put(host.getUniqueId(), sessionUuid);
        for (UUID duelistUuid : duelistUuids) {
            activeSessionByCoreParticipant.put(duelistUuid, sessionUuid);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> completeIfActive(sessionUuid),
                Math.max(1L, durationSeconds() * 20L));
        return sessionUuid;
    }

    public synchronized boolean cancelForQuit(UUID playerUuid) {
        UUID sessionUuid = activeSessionByCoreParticipant.get(playerUuid);
        if (sessionUuid == null) return false;
        ActiveRitual session = activeBySession.get(sessionUuid);
        if (session == null) return false;
        boolean critical = session.hostUuid().equals(playerUuid) || session.duelistUuids().contains(playerUuid);
        if (!critical) return false;
        cancelSession(sessionUuid, "核心参与者离线，仪式已取消。");
        return true;
    }

    public synchronized void cancelAll() {
        for (UUID sessionUuid : new ArrayList<>(activeBySession.keySet())) {
            cancelSession(sessionUuid, "插件正在关闭，仪式已取消。");
        }
    }

    private void registerSynodalRitual(Player actor, WDEreligion ideology,
            IdeologyRitualDefinition definition, BigDecimal cost, long now) {
        List<WDEChurch> activeChurches = plugin.getIdeologyChurchManager().getChurches(ideology.getReligionName())
                .stream().filter(WDEChurch::isActive)
                .sorted(Comparator.comparing(church -> church.getChurchUuid().toString()))
                .toList();
        if (activeChurches.isEmpty()) {
            throw new IllegalArgumentException("This synodal ideology has no active churches.");
        }
        BigDecimal totalTreasury = activeChurches.stream()
                .map(WDEChurch::getTreasuryBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalTreasury.compareTo(cost) < 0) {
            throw new IllegalArgumentException("Insufficient synodal church treasuries.");
        }

        List<WDEChurch> changedChurches = new ArrayList<>();
        List<DataStorageManager.EntityWrite> writes = new ArrayList<>();
        List<BigDecimal> shares = calculateSynodalShares(activeChurches, totalTreasury, cost);
        for (int index = 0; index < activeChurches.size(); index++) {
            WDEChurch church = WDEChurch.deserialize(activeChurches.get(index).serialize());
            BigDecimal share = shares.get(index);
            if (share.signum() > 0) {
                church.debitTreasury(share, definition.getRitualUuid(),
                        "ritual:" + actor.getUniqueId(), now);
            }
            changedChurches.add(church);
            writes.add(write(CHURCHES_CATEGORY, church.getChurchUuid().toString(), church));
        }
        writes.add(write(STORAGE_CATEGORY, definition.getRitualUuid().toString(), definition));
        storage.saveEntities(writes);
        for (WDEChurch church : changedChurches) {
            plugin.getIdeologyChurchManager().publishPersistedChurch(church);
        }
    }

    private List<BigDecimal> calculateSynodalShares(List<WDEChurch> churches,
            BigDecimal totalTreasury, BigDecimal cost) {
        List<BigDecimal> shares = new ArrayList<>();
        List<BigDecimal> remainders = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        for (WDEChurch church : churches) {
            BigDecimal rawShare = cost.multiply(church.getTreasuryBalance())
                    .divide(totalTreasury, MONEY_SCALE + 4, RoundingMode.DOWN);
            BigDecimal share = rawShare.setScale(MONEY_SCALE, RoundingMode.DOWN);
            shares.add(share);
            remainders.add(rawShare.subtract(share));
            assigned = assigned.add(share);
        }

        BigDecimal cent = new BigDecimal("0.01");
        BigDecimal remaining = cost.subtract(assigned).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        while (remaining.signum() > 0) {
            int target = -1;
            BigDecimal bestRemainder = BigDecimal.valueOf(-1);
            for (int index = 0; index < churches.size(); index++) {
                BigDecimal candidateShare = shares.get(index).add(cent);
                if (candidateShare.compareTo(churches.get(index).getTreasuryBalance()) > 0) {
                    continue;
                }
                BigDecimal remainder = remainders.get(index);
                if (remainder.compareTo(bestRemainder) > 0) {
                    bestRemainder = remainder;
                    target = index;
                }
            }
            if (target < 0) {
                throw new IllegalArgumentException("Insufficient synodal church treasuries.");
            }
            shares.set(target, shares.get(target).add(cent));
            remainders.set(target, BigDecimal.ZERO);
            remaining = remaining.subtract(cent);
        }
        return shares;
    }

    private void completeIfActive(UUID sessionUuid) {
        ActiveRitual session;
        synchronized (this) {
            session = activeBySession.get(sessionUuid);
        }
        if (session == null) return;
        Player host = Bukkit.getPlayer(session.hostUuid());
        if (host == null || !host.isOnline()) {
            synchronized (this) {
                cancelSession(sessionUuid, "主持人离线，仪式已取消。");
            }
            return;
        }
        for (UUID duelistUuid : session.duelistUuids()) {
            Player duelist = Bukkit.getPlayer(duelistUuid);
            if (duelist == null || !duelist.isOnline()) {
                synchronized (this) {
                    cancelSession(sessionUuid, "决斗者离线，仪式已取消。");
                }
                return;
            }
        }

        List<Player> participants = collectParticipants(session, host);
        double weight = calculateWeight(session.definition(), participants);
        RitualResult result = rollResult(weight);
        applyRitualRewards(session, result, participants);
        synchronized (this) {
            removeSession(sessionUuid);
        }
        for (Player participant : participants) {
            participant.sendMessage(plugin.formatLocaleMessage("ritual_completed",
                    session.definition().getRitualName(), result.displayName(),
                    String.format(Locale.ROOT, "%.2f", weight * 100D) + "%"));
        }
    }

    private List<Player> collectParticipants(ActiveRitual session, Player host) {
        WDEreligion ideology = plugin.getReligionManager().getReligion(session.definition().getIdeologyName());
        if (ideology == null) return List.of(host);
        double rangeSquared = Math.pow(rangeBlocks(), 2D);
        Location center = host.getLocation();
        List<Player> participants = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(center.getWorld())
                    || player.getLocation().distanceSquared(center) > rangeSquared) {
                continue;
            }
            WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
            if (resident != null && ideology.getReligionName().equalsIgnoreCase(resident.getReligionName())
                    && !ideology.isPlayerExcommunicated(player.getUniqueId())) {
                participants.add(player);
            }
        }
        return participants;
    }

    private double calculateWeight(IdeologyRitualDefinition definition, Collection<Player> participants) {
        WDEreligion ideology = plugin.getReligionManager().getReligion(definition.getIdeologyName());
        if (ideology == null || participants == null || participants.isEmpty()) return 0D;
        double followerWeight = configWeight("rituals.weights.follower", 0.02D);
        double cardinalWeight = configWeight("rituals.weights.cardinal", 0.05D);
        double ideologyLeaderWeight = configWeight("rituals.weights.ideologyLeader", 0.20D);
        double churchHeadWeight = configWeight("rituals.weights.synodalChurchHead", 0.20D);
        double weight = 0D;
        for (Player participant : participants) {
            UUID uuid = participant.getUniqueId();
            double participantWeight = followerWeight;
            if (ideology.isCardinal(uuid)) participantWeight = Math.max(participantWeight, cardinalWeight);
            if (uuid.equals(ideology.getPontiffUuid())
                    && ideology.getGovernanceMode() != WDEreligion.GovernanceMode.SYNODAL) {
                participantWeight = Math.max(participantWeight, ideologyLeaderWeight);
            }
            if (ideology.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL
                    && plugin.getIdeologyChurchManager().isChurchHead(uuid)) {
                participantWeight = Math.max(participantWeight, churchHeadWeight);
            }
            weight += participantWeight;
        }
        return Math.max(0D, Math.min(1D, weight));
    }

    private RitualResult rollResult(double weight) {
        double w = Math.max(0D, Math.min(1D, weight));
        double floor = 0.03D;
        double terrible = floor + (1D - w) * 0.45D;
        double mediocre = floor + (1D - Math.abs(w - 0.35D)) * 0.22D;
        double success = floor + w * 0.30D;
        double perfect = floor + w * 0.42D;
        double total = terrible + mediocre + success + perfect;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        if (roll < terrible) return RitualResult.TERRIBLE;
        roll -= terrible;
        if (roll < mediocre) return RitualResult.MEDIOCRE;
        roll -= mediocre;
        if (roll < success) return RitualResult.SUCCESS;
        return RitualResult.PERFECT;
    }

    private void applyRitualRewards(ActiveRitual session, RitualResult result, List<Player> participants) {
        // 预留奖励接入点：后续可按 result 给主持人、决斗者和观众分发效果或资源。
    }

    private void validateDuelist(WDEreligion ideology, Player host, Player duelist, Set<UUID> uniqueDuelists) {
        if (duelist == null || !duelist.isOnline() || duelist.getUniqueId().equals(host.getUniqueId())
                || !uniqueDuelists.add(duelist.getUniqueId())
                || activeSessionByCoreParticipant.containsKey(duelist.getUniqueId())) {
            throw new IllegalArgumentException("Invalid ritual duelist.");
        }
        WDEresident resident = plugin.getResidentManager().getResident(duelist.getUniqueId());
        if (resident == null || !ideology.getReligionName().equalsIgnoreCase(resident.getReligionName())
                || ideology.isPlayerExcommunicated(duelist.getUniqueId())) {
            throw new IllegalArgumentException("Duelists must follow this ideology.");
        }
        if (!duelist.getWorld().equals(host.getWorld())
                || duelist.getLocation().distanceSquared(host.getLocation()) > Math.pow(rangeBlocks(), 2D)) {
            throw new IllegalArgumentException("Duelists must stand inside the ritual range.");
        }
    }

    private IdeologyRitualDefinition findRitual(WDEreligion ideology, String token) {
        if (token == null || token.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(token);
            IdeologyRitualDefinition definition = definitions.get(uuid);
            return definition != null && definition.getIdeologyUuid().equals(ideology.getIdeologyUuid())
                    ? definition : null;
        } catch (IllegalArgumentException ignored) {
            return definitions.values().stream()
                    .filter(definition -> definition.getIdeologyUuid().equals(ideology.getIdeologyUuid()))
                    .filter(definition -> definition.getRitualName().equalsIgnoreCase(token))
                    .findFirst().orElse(null);
        }
    }

    private void requireDefiningAuthority(Player actor, WDEreligion ideology) {
        if (ideology.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL) {
            WDEresident resident = plugin.getResidentManager().getResident(actor.getUniqueId());
            WDEChurch church = plugin.getIdeologyChurchManager().getActiveChurchForResident(resident, ideology);
            if (church == null || !actor.getUniqueId().equals(church.getHeadUuid())) {
                throw new IllegalArgumentException("Only an active church head may define synodal rituals.");
            }
            return;
        }
        if (!actor.getUniqueId().equals(ideology.getPontiffUuid())) {
            throw new IllegalArgumentException("Only the ideology leader may define rituals.");
        }
    }

    private WDEreligion requireIdeology(String ideologyName) {
        WDEreligion ideology = plugin.getReligionManager().getReligion(ideologyName);
        if (ideology == null) throw new IllegalArgumentException("Ideology not found.");
        return ideology;
    }

    private void validateRitualName(String name) {
        if (name == null || name.isBlank() || weightedLength(name) > 60) {
            throw new IllegalArgumentException("Invalid ritual name.");
        }
    }

    private int weightedLength(String value) {
        int length = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            length += codePoint <= 0x7F ? 1 : 2;
            offset += Character.charCount(codePoint);
        }
        return length;
    }

    private BigDecimal readCreationCost() {
        try {
            BigDecimal value = new BigDecimal(plugin.getIdeologyConfig()
                    .getString("rituals.creationCost", "10000.00"));
            value = value.stripTrailingZeros();
            if (value.signum() < 0 || value.scale() > MONEY_SCALE
                    || value.compareTo(WDEreligion.MAX_FIXED_TITHE) > 0) {
                throw new NumberFormatException("out of range");
            }
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid ritual creation cost.", ex);
        }
    }

    private long durationSeconds() {
        return Math.max(1L, plugin.getIdeologyConfig().getLong("rituals.durationSeconds", 900L));
    }

    private double rangeBlocks() {
        return Math.max(1D, plugin.getIdeologyConfig().getDouble("rituals.rangeBlocks", 48D));
    }

    private double configWeight(String path, double fallback) {
        double value = plugin.getIdeologyConfig().getDouble(path, fallback);
        return Double.isFinite(value) ? Math.max(0D, Math.min(1D, value)) : fallback;
    }

    private void cancelSession(UUID sessionUuid, String reason) {
        ActiveRitual session = activeBySession.get(sessionUuid);
        if (session == null) return;
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(session.hostUuid());
        recipients.addAll(session.duelistUuids());
        for (UUID uuid : recipients) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.formatLocaleMessage("ritual_cancelled",
                        session.definition().getRitualName(), reason));
            }
        }
        removeSession(sessionUuid);
    }

    private void removeSession(UUID sessionUuid) {
        ActiveRitual session = activeBySession.remove(sessionUuid);
        if (session == null) return;
        activeSessionByCoreParticipant.remove(session.hostUuid());
        for (UUID duelistUuid : session.duelistUuids()) {
            activeSessionByCoreParticipant.remove(duelistUuid);
        }
    }

    private DataStorageManager.EntityWrite write(String category, String key,
            org.bukkit.configuration.serialization.ConfigurationSerializable value) {
        return new DataStorageManager.EntityWrite(category, key, value);
    }
}
