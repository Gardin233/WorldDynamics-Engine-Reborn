package com.thatguycy.worlddynamicsengine.manager.economy;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineMoodEntry;
import com.thatguycy.worlddynamicsengine.model.PlayerGiftLedger;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Handles online player-to-player gifts and alms requests. */
public final class PlayerGiftManager {
    private static final String STORAGE_CATEGORY = "player_gifts";
    private static final String STORAGE_KEY = "ledger";
    private static final String KINDNESS_DOCTRINE = "namespace:wder/kindness";
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;

    public enum TransferStatus {
        SUCCESS, INVALID_AMOUNT, SELF, DISABLED, ECONOMY_UNAVAILABLE, INVALID_CURRENCY,
        INSUFFICIENT_FUNDS, TRANSFER_FAILED, REFUND_FAILED, NO_PENDING, REQUEST_EXPIRED
    }

    public record TransferResult(TransferStatus status, BigDecimal amount, String currency,
                                 boolean kindnessMoodApplied) {}

    public enum RequestStatus {
        SENT, NOT_LOW_ASSET, COOLDOWN, ALREADY_PENDING, DISABLED, ECONOMY_UNAVAILABLE
    }

    public record RequestResult(RequestStatus status, long retryAfterMillis, long expiresAt) {}

    public enum RefusalStatus { PENALTY_APPLIED, NO_PENALTY, NO_PENDING, EXPIRED, REQUESTER_OFFLINE }

    private record PendingRequest(UUID requester, UUID recipient, long expiresAt) {}

    private final WorldDynamicsEngine plugin;
    private final Map<String, PendingRequest> pendingRequests = new LinkedHashMap<>();
    private final PlayerGiftLedger ledger;

    public PlayerGiftManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.ledger = plugin.getDataStorageManager()
                .loadStringKeyed(STORAGE_CATEGORY, PlayerGiftLedger.class)
                .getOrDefault(STORAGE_KEY, new PlayerGiftLedger());
        this.ledger.pruneExpired(System.currentTimeMillis());
        Bukkit.getScheduler().runTaskTimer(plugin, this::pruneExpiredRequests, 1200L, 1200L);
    }

    public synchronized TransferResult gift(Player giver, Player recipient, BigDecimal amount, String currency) {
        if (giver == null || recipient == null || !giver.isOnline() || !recipient.isOnline()) {
            return transferResult(TransferStatus.ECONOMY_UNAVAILABLE, amount, currency, false);
        }
        if (giver.getUniqueId().equals(recipient.getUniqueId())) {
            return transferResult(TransferStatus.SELF, amount, currency, false);
        }
        if (!transfersEnabled()) {
            return transferResult(TransferStatus.DISABLED, amount, currency, false);
        }
        if (!PlayerGiftCalculator.isValidAmount(amount)) {
            return transferResult(TransferStatus.INVALID_AMOUNT, amount, currency, false);
        }

        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.hasProvider()) {
            return transferResult(TransferStatus.ECONOMY_UNAVAILABLE, amount, currency, false);
        }
        String resolvedCurrency = currency == null || currency.isBlank()
                ? economy.getDefaultCurrency() : currency.trim();
        if (!economy.supportsExactCurrency(resolvedCurrency)) {
            return transferResult(TransferStatus.INVALID_CURRENCY, amount, resolvedCurrency, false);
        }

        String giverWorld = giver.getWorld().getName();
        String recipientWorld = recipient.getWorld().getName();
        BigDecimal giverWealth = totalWealth(economy, giver);
        BigDecimal recipientWealth = totalWealth(economy, recipient);
        boolean qualifiesForKindness = PlayerGiftCalculator.recipientIsBelowHalfWealth(
                recipientWealth, giverWealth);
        EconomyBridge.Charge charge = economy.charge(amount, resolvedCurrency);
        if (economy.getBalance(giver.getUniqueId(), giverWorld, resolvedCurrency).compareTo(amount) < 0) {
            return transferResult(TransferStatus.INSUFFICIENT_FUNDS, amount, resolvedCurrency, false);
        }

        if (!economy.withdrawExact(giver.getUniqueId(), giverWorld, charge).success()) {
            return transferResult(TransferStatus.INSUFFICIENT_FUNDS, amount, resolvedCurrency, false);
        }
        if (!economy.depositExact(recipient.getUniqueId(), recipientWorld, charge).success()) {
            boolean refunded = economy.depositExact(giver.getUniqueId(), giverWorld, charge).success();
            if (!refunded) {
                plugin.getLogger().severe("Player gift refund failed: giver=" + giver.getUniqueId()
                        + ", recipient=" + recipient.getUniqueId() + ", amount=" + amount
                        + ", currency=" + resolvedCurrency);
            }
            return transferResult(refunded ? TransferStatus.TRANSFER_FAILED : TransferStatus.REFUND_FAILED,
                    amount, resolvedCurrency, false);
        }

        pendingRequests.remove(requestKey(recipient.getUniqueId(), giver.getUniqueId()));
        boolean kindnessMoodApplied = qualifiesForKindness
                && applyKindnessGiftMood(giver, recipient);
        return transferResult(TransferStatus.SUCCESS, amount, resolvedCurrency, kindnessMoodApplied);
    }

    public synchronized RequestResult requestAlms(Player requester, Player recipient) {
        if (requester == null || recipient == null || !requester.isOnline() || !recipient.isOnline()
                || requester.getUniqueId().equals(recipient.getUniqueId())) {
            return new RequestResult(RequestStatus.ECONOMY_UNAVAILABLE, 0L, 0L);
        }
        if (!transfersEnabled()) return new RequestResult(RequestStatus.DISABLED, 0L, 0L);

        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.hasProvider()) {
            return new RequestResult(RequestStatus.ECONOMY_UNAVAILABLE, 0L, 0L);
        }
        if (!PlayerGiftCalculator.recipientIsBelowHalfWealth(
                totalWealth(economy, requester), totalWealth(economy, recipient))) {
            return new RequestResult(RequestStatus.NOT_LOW_ASSET, 0L, 0L);
        }

        long now = System.currentTimeMillis();
        String pairKey = directedPairKey(requester.getUniqueId(), recipient.getUniqueId());
        long cooldownUntil = ledger.getRequestCooldownUntil(pairKey);
        if (cooldownUntil > now) {
            return new RequestResult(RequestStatus.COOLDOWN, cooldownUntil - now, 0L);
        }

        String requestKey = requestKey(requester.getUniqueId(), recipient.getUniqueId());
        PendingRequest existing = pendingRequests.get(requestKey);
        if (existing != null && existing.expiresAt() > now) {
            return new RequestResult(RequestStatus.ALREADY_PENDING, 0L, existing.expiresAt());
        }

        long expiresAt = now + minutesToMillis(
                getConfigInt("economy.playerGifts.requestExpiryMinutes", 5), 1, 60);
        pendingRequests.put(requestKey, new PendingRequest(
                requester.getUniqueId(), recipient.getUniqueId(), expiresAt));
        ledger.setRequestCooldownUntil(pairKey, now + minutesToMillis(
                getConfigInt("economy.playerGifts.requestCooldownMinutes", 10), 1, 1440));
        saveLedger();
        return new RequestResult(RequestStatus.SENT, 0L, expiresAt);
    }

    public synchronized TransferResult acceptAlms(Player giver, Player requester,
                                                   BigDecimal amount, String currency) {
        if (giver == null || requester == null || !giver.isOnline() || !requester.isOnline()) {
            return transferResult(TransferStatus.ECONOMY_UNAVAILABLE, amount, currency, false);
        }
        String key = requestKey(requester.getUniqueId(), giver.getUniqueId());
        PendingRequest request = pendingRequests.get(key);
        if (request == null) return transferResult(TransferStatus.NO_PENDING, amount, currency, false);
        if (request.expiresAt() <= System.currentTimeMillis()) {
            pendingRequests.remove(key);
            return transferResult(TransferStatus.REQUEST_EXPIRED, amount, currency, false);
        }

        TransferResult result = gift(giver, requester, amount, currency);
        if (result.status() == TransferStatus.SUCCESS) pendingRequests.remove(key);
        return result;
    }

    public synchronized RefusalStatus refuseAlms(Player recipient, Player requester) {
        if (recipient == null || requester == null || !recipient.isOnline() || !requester.isOnline()) {
            return RefusalStatus.REQUESTER_OFFLINE;
        }
        String requestKey = requestKey(requester.getUniqueId(), recipient.getUniqueId());
        PendingRequest request = pendingRequests.get(requestKey);
        if (request == null) return RefusalStatus.NO_PENDING;
        if (request.expiresAt() <= System.currentTimeMillis()) {
            pendingRequests.remove(requestKey);
            return RefusalStatus.EXPIRED;
        }

        pendingRequests.remove(requestKey);
        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.hasProvider()
                || !PlayerGiftCalculator.recipientIsBelowHalfWealth(
                        totalWealth(economy, requester), totalWealth(economy, recipient))) {
            return RefusalStatus.NO_PENALTY;
        }
        return applyKindnessRefusalMood(recipient, requester)
                ? RefusalStatus.PENALTY_APPLIED : RefusalStatus.NO_PENALTY;
    }

    public synchronized List<String> getPendingRequesters(Player recipient) {
        if (recipient == null) return List.of();
        pruneExpiredRequests();
        List<String> names = new ArrayList<>();
        for (PendingRequest request : pendingRequests.values()) {
            if (!request.recipient().equals(recipient.getUniqueId())) continue;
            Player requester = Bukkit.getPlayer(request.requester());
            if (requester != null && requester.isOnline()) names.add(requester.getName());
        }
        return List.copyOf(names);
    }

    private boolean applyKindnessGiftMood(Player giver, Player recipient) {
        String attitude = kindnessAttitude(giver);
        if (!kindnessMoodEnabled() || !PlayerGiftCalculator.supportsKindness(attitude)) return false;
        String pairKey = directedPairKey(giver.getUniqueId(), recipient.getUniqueId());
        long now = System.currentTimeMillis();
        if (ledger.getKindnessGiftCooldownUntil(pairKey) > now) return false;

        int moodChange = kindnessMoodChange(attitude, true);
        if (moodChange <= 0) return false;
        ledger.setKindnessGiftCooldownUntil(pairKey, now + minutesToMillis(
                getKindnessConfigInt("giftMoodCooldownMinutes", 30), 0, 1440));
        saveLedger();
        applyKindnessMood(giver, attitude, true, moodChange);
        return true;
    }

    private boolean applyKindnessRefusalMood(Player recipient, Player requester) {
        String attitude = kindnessAttitude(recipient);
        if (!kindnessMoodEnabled() || !PlayerGiftCalculator.supportsKindness(attitude)) return false;
        String pairKey = directedPairKey(requester.getUniqueId(), recipient.getUniqueId());
        long now = System.currentTimeMillis();
        if (ledger.getRefusalMoodCooldownUntil(pairKey) > now) return false;

        int moodChange = kindnessMoodChange(attitude, false);
        if (moodChange <= 0) return false;
        ledger.setRefusalMoodCooldownUntil(pairKey, now + hoursToMillis(
                getKindnessConfigInt("refusalMoodCooldownHours", 24), 1, 8760));
        saveLedger();
        applyKindnessMood(recipient, attitude, false, moodChange);
        return true;
    }

    private int kindnessMoodChange(String attitude, boolean compliant) {
        var religions = plugin.getReligionManager();
        int weight = Math.max(0, religions.getDoctrineMoodWeight(KINDNESS_DOCTRINE, attitude));
        int perWeight = Math.max(0, religions.getDoctrineMoodChangePerWeight(
                KINDNESS_DOCTRINE, attitude, compliant, null));
        return (int) Math.min(100L, (long) weight * perWeight);
    }

    private void applyKindnessMood(Player player, String attitude, boolean compliant, int moodChange) {
        try {
            var entry = new DoctrineMoodEntry(KINDNESS_DOCTRINE, attitude, compliant, moodChange);
            plugin.getMoodManager().applyDoctrineMoodChanges(player,
                    compliant ? moodChange : 0,
                    compliant ? 0 : moodChange,
                    entry.source());
            if (!compliant && plugin.getPlayerMenuManager() != null) {
                plugin.getPlayerMenuManager().sendDoctrineViolation(player, KINDNESS_DOCTRINE);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed to apply kindness mood for " + player.getUniqueId()
                    + ": " + ex.getMessage());
        }
    }

    private String kindnessAttitude(Player player) {
        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        return religion == null || plugin.getReligionManager().getDoctrine(KINDNESS_DOCTRINE) == null
                ? null : religion.getDoctrineAttitude(KINDNESS_DOCTRINE);
    }

    private BigDecimal totalWealth(EconomyBridge economy, Player player) {
        return economy.getTotalWealthAsDefault(player.getUniqueId(), player.getWorld().getName(),
                plugin.getCurrencyExchangeRates().keySet());
    }

    private boolean transfersEnabled() {
        return plugin.getConfig().getBoolean("economy.playerGifts.enabled", true);
    }

    private boolean kindnessMoodEnabled() {
        var config = plugin.getIdeologyConfig();
        return plugin.religionEnabled
                && (config == null || config.getBoolean("mood.doctrineActions.kindness.enabled", true));
    }

    private int getConfigInt(String path, int fallback) {
        return Math.max(0, plugin.getConfig().getInt(path, fallback));
    }

    private int getKindnessConfigInt(String path, int fallback) {
        var config = plugin.getIdeologyConfig();
        return Math.max(0, config == null ? fallback
                : config.getInt("mood.doctrineActions.kindness." + path, fallback));
    }

    private void saveLedger() {
        long now = System.currentTimeMillis();
        ledger.pruneExpired(now);
        try {
            plugin.getDataStorageManager().saveEntity(STORAGE_CATEGORY, STORAGE_KEY, ledger);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed to persist player gift cooldowns: " + ex.getMessage());
        }
    }

    private synchronized void pruneExpiredRequests() {
        long now = System.currentTimeMillis();
        pendingRequests.values().removeIf(request -> request.expiresAt() <= now);
        ledger.pruneExpired(now);
    }

    private static long minutesToMillis(int minutes, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, minutes)) * MINUTE_MILLIS;
    }

    private static long hoursToMillis(int hours, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, hours)) * HOUR_MILLIS;
    }

    private static String requestKey(UUID requester, UUID recipient) {
        return directedPairKey(requester, recipient);
    }

    private static String directedPairKey(UUID actor, UUID other) {
        return actor + ">" + other;
    }

    private static TransferResult transferResult(TransferStatus status, BigDecimal amount,
                                                  String currency, boolean moodApplied) {
        return new TransferResult(status, amount == null ? BigDecimal.ZERO : amount,
                currency == null ? "" : currency, moodApplied);
    }
}
