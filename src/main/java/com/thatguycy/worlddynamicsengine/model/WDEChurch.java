package com.thatguycy.worlddynamicsengine.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/** 持久化教会、成员归属、首脑、金库及制裁状态。 */
@SerializableAs("WDEChurch")
public final class WDEChurch implements ConfigurationSerializable {
    public enum State { ACTIVE, MERGED, SCHISMED, DISSOLVED }

    private final UUID churchUuid;
    private final UUID sourceIdeologyUuid;
    private final String sourceIdeologyName;
    private final String churchName;
    private final long createdAt;
    private UUID headUuid;
    private String headName;
    private final Map<String, String> members = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private BigDecimal treasuryBalance = BigDecimal.ZERO;
    private State state = State.ACTIVE;
    private String sanctionReason = "";
    private long sanctionExpiresAt;
    private String lastAppealReason = "";
    private long lastAppealAt;
    private long lastMembershipChangeAt;
    private long lastProposalAt;
    private long lastGovernanceActionAt;
    private long closedAt;
    private UUID treasurySettlementOperation;
    private BigDecimal settledTreasuryAmount = BigDecimal.ZERO;

    public WDEChurch(UUID churchUuid, UUID sourceIdeologyUuid, String sourceIdeologyName,
            String churchName, UUID headUuid, String headName, long createdAt) {
        if (churchUuid == null || sourceIdeologyUuid == null || headUuid == null
                || sourceIdeologyName == null || churchName == null || headName == null) {
            throw new IllegalArgumentException("Church identity, source ideology, and head are required.");
        }
        this.churchUuid = churchUuid;
        this.sourceIdeologyUuid = sourceIdeologyUuid;
        this.sourceIdeologyName = sourceIdeologyName;
        this.churchName = churchName;
        this.headUuid = headUuid;
        this.headName = headName;
        this.createdAt = createdAt;
        this.lastGovernanceActionAt = createdAt;
        members.put(headUuid.toString(), headName);
        record("CHURCH_CREATED", createdAt, Map.of("headUuid", headUuid.toString()));
    }

    public WDEChurch(Map<String, Object> map) {
        churchUuid = UUID.fromString(String.valueOf(map.get("churchUuid")));
        sourceIdeologyUuid = UUID.fromString(String.valueOf(map.get("sourceIdeologyUuid")));
        sourceIdeologyName = String.valueOf(map.getOrDefault("sourceIdeologyName", "Unknown"));
        churchName = String.valueOf(map.getOrDefault("churchName", "Unknown"));
        headUuid = UUID.fromString(String.valueOf(map.get("headUuid")));
        headName = String.valueOf(map.getOrDefault("headName", "Unknown"));
        createdAt = readLong(map.get("createdAt"), System.currentTimeMillis());
        copyStringMap(map.get("members"), members);
        copyHistory(map.get("history"), history);
        treasuryBalance = readNonNegativeDecimal(map.get("treasuryBalance"));
        try {
            state = State.valueOf(String.valueOf(map.getOrDefault("state", "ACTIVE")));
        } catch (IllegalArgumentException ignored) {
            state = State.ACTIVE;
        }
        sanctionReason = String.valueOf(map.getOrDefault("sanctionReason", ""));
        sanctionExpiresAt = readLong(map.get("sanctionExpiresAt"), 0L);
        lastAppealReason = String.valueOf(map.getOrDefault("lastAppealReason", ""));
        lastAppealAt = readLong(map.get("lastAppealAt"), 0L);
        lastMembershipChangeAt = readLong(map.get("lastMembershipChangeAt"), 0L);
        lastProposalAt = readLong(map.get("lastProposalAt"), 0L);
        lastGovernanceActionAt = readLong(map.get("lastGovernanceActionAt"), 0L);
        closedAt = readLong(map.get("closedAt"), 0L);
        Object operation = map.get("treasurySettlementOperation");
        if (operation != null && !String.valueOf(operation).isBlank()) {
            treasurySettlementOperation = UUID.fromString(String.valueOf(operation));
        }
        settledTreasuryAmount = readNonNegativeDecimal(map.get("settledTreasuryAmount"));
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("churchUuid", churchUuid.toString());
        map.put("sourceIdeologyUuid", sourceIdeologyUuid.toString());
        map.put("sourceIdeologyName", sourceIdeologyName);
        map.put("churchName", churchName);
        map.put("createdAt", createdAt);
        map.put("headUuid", headUuid.toString());
        map.put("headName", headName);
        map.put("members", new LinkedHashMap<>(members));
        map.put("history", copyHistoryForSerialization(history));
        map.put("treasuryBalance", treasuryBalance.toPlainString());
        map.put("state", state.name());
        map.put("sanctionReason", sanctionReason);
        map.put("sanctionExpiresAt", sanctionExpiresAt);
        map.put("lastAppealReason", lastAppealReason);
        map.put("lastAppealAt", lastAppealAt);
        map.put("lastMembershipChangeAt", lastMembershipChangeAt);
        map.put("lastProposalAt", lastProposalAt);
        map.put("lastGovernanceActionAt", lastGovernanceActionAt);
        map.put("closedAt", closedAt);
        map.put("treasurySettlementOperation", treasurySettlementOperation == null
                ? "" : treasurySettlementOperation.toString());
        map.put("settledTreasuryAmount", settledTreasuryAmount.toPlainString());
        return map;
    }

    public UUID getChurchUuid() { return churchUuid; }
    public UUID getSourceIdeologyUuid() { return sourceIdeologyUuid; }
    public String getSourceIdeologyName() { return sourceIdeologyName; }
    public String getChurchName() { return churchName; }
    public long getCreatedAt() { return createdAt; }
    public UUID getHeadUuid() { return headUuid; }
    public String getHeadName() { return headName; }
    public BigDecimal getTreasuryBalance() { return treasuryBalance; }
    public State getState() { return state; }
    public String getSanctionReason() { return sanctionReason; }
    public long getSanctionExpiresAt() { return sanctionExpiresAt; }
    public String getLastAppealReason() { return lastAppealReason; }
    public long getLastAppealAt() { return lastAppealAt; }
    public long getLastMembershipChangeAt() { return lastMembershipChangeAt; }
    public long getLastProposalAt() { return lastProposalAt; }
    public long getLastGovernanceActionAt() { return lastGovernanceActionAt; }
    public long getClosedAt() { return closedAt; }
    public UUID getTreasurySettlementOperation() { return treasurySettlementOperation; }
    public BigDecimal getSettledTreasuryAmount() { return settledTreasuryAmount; }

    public Map<String, String> getMembers() {
        return new LinkedHashMap<>(members);
    }

    public List<Map<String, Object>> getHistory() {
        return copyHistoryForSerialization(history);
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public boolean isMember(UUID playerUuid) {
        return playerUuid != null && members.containsKey(playerUuid.toString());
    }

    public boolean isSanctioned(long now) {
        return isActive() && sanctionExpiresAt > now;
    }

    public void addMember(UUID playerUuid, String playerName, long changedAt) {
        requirePlayer(playerUuid, playerName);
        if (!isActive()) throw new IllegalStateException("Only an active church can accept members.");
        if (members.putIfAbsent(playerUuid.toString(), playerName) != null) {
            throw new IllegalArgumentException("Player is already a church member.");
        }
        lastMembershipChangeAt = changedAt;
        record("MEMBER_JOINED", changedAt, Map.of("playerUuid", playerUuid.toString(), "playerName", playerName));
    }

    public boolean removeMember(UUID playerUuid, long changedAt) {
        if (playerUuid == null || playerUuid.equals(headUuid)) {
            throw new IllegalArgumentException("The church head must abdicate before leaving.");
        }
        String name = members.remove(playerUuid.toString());
        if (name == null) return false;
        lastMembershipChangeAt = changedAt;
        record("MEMBER_LEFT", changedAt, Map.of("playerUuid", playerUuid.toString(), "playerName", name));
        return true;
    }

    public void transferHead(UUID successorUuid, String successorName, long changedAt) {
        requirePlayer(successorUuid, successorName);
        if (!isActive() || !isMember(successorUuid)) {
            throw new IllegalArgumentException("A church head must be an active member of that church.");
        }
        String previousUuid = headUuid.toString();
        String previousName = headName;
        headUuid = successorUuid;
        headName = successorName;
        lastGovernanceActionAt = changedAt;
        record("HEAD_TRANSFERRED", changedAt, Map.of(
                "previousHeadUuid", previousUuid, "previousHeadName", previousName,
                "successorUuid", successorUuid.toString(), "successorName", successorName));
    }

    public void markProposalCreated(long timestamp) {
        lastProposalAt = timestamp;
    }

    public void markGovernanceAction(long timestamp) {
        lastGovernanceActionAt = timestamp;
    }

    public void creditTreasury(BigDecimal amount, UUID transactionUuid, String actorUuid, long at) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Church treasury credits must be positive.");
        }
        treasuryBalance = treasuryBalance.add(amount);
        record("TREASURY_CREDIT", at, Map.of(
                "transactionUuid", transactionUuid.toString(), "actorUuid", actorUuid,
                "amount", amount.toPlainString()));
    }

    public void debitTreasury(BigDecimal amount, UUID transactionUuid, String actorUuid, long at) {
        if (amount == null || amount.signum() <= 0 || treasuryBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Invalid amount or insufficient church treasury balance.");
        }
        treasuryBalance = treasuryBalance.subtract(amount);
        record("TREASURY_DEBIT", at, Map.of(
                "transactionUuid", transactionUuid.toString(), "actorUuid", actorUuid,
                "amount", amount.toPlainString()));
    }

    public void imposeSanction(String reason, long expiresAt, long at, UUID proposalUuid) {
        if (!isActive() || reason == null || reason.isBlank() || expiresAt <= at) {
            throw new IllegalArgumentException("A sanction requires an active church, a reason, and a future expiry.");
        }
        sanctionReason = reason;
        sanctionExpiresAt = expiresAt;
        record("SANCTION_IMPOSED", at, Map.of(
                "proposalUuid", proposalUuid.toString(), "reason", reason, "expiresAt", expiresAt));
    }

    public void appealSanction(String reason, long at) {
        if (!isSanctioned(at) || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Only a sanctioned active church may appeal with a reason.");
        }
        lastAppealReason = reason;
        lastAppealAt = at;
        record("SANCTION_APPEAL", at, Map.of("reason", reason));
    }

    public void liftSanction(String reason, long at, UUID proposalUuid) {
        if (sanctionExpiresAt == 0L) throw new IllegalArgumentException("This church has no active sanction.");
        String oldReason = sanctionReason;
        sanctionReason = "";
        sanctionExpiresAt = 0L;
        record("SANCTION_LIFTED", at, Map.of(
                "proposalUuid", proposalUuid.toString(), "reason", reason,
                "previousReason", oldReason));
    }

    public void markMerged(UUID operationUuid, BigDecimal settledAmount, long at) {
        if (!isActive() || operationUuid == null || settledAmount == null
                || treasuryBalance.compareTo(settledAmount) != 0) {
            throw new IllegalStateException("Church treasury is not in a mergeable state.");
        }
        treasuryBalance = BigDecimal.ZERO;
        settledTreasuryAmount = settledAmount;
        treasurySettlementOperation = operationUuid;
        state = State.MERGED;
        closedAt = at;
        record("CHURCH_MERGED", at, Map.of(
                "operationUuid", operationUuid.toString(), "settledAmount", settledAmount.toPlainString()));
    }

    public void markSchism(UUID operationUuid, BigDecimal settledAmount, long at) {
        if (!isActive() || operationUuid == null || settledAmount == null
                || treasuryBalance.compareTo(settledAmount) != 0) {
            throw new IllegalStateException("Church treasury is not in a schism-ready state.");
        }
        treasuryBalance = BigDecimal.ZERO;
        settledTreasuryAmount = settledAmount;
        treasurySettlementOperation = operationUuid;
        state = State.SCHISMED;
        closedAt = at;
        record("CHURCH_SECEDED", at, Map.of(
                "operationUuid", operationUuid.toString(), "settledAmount", settledAmount.toPlainString()));
    }

    public void dissolve(long at) {
        if (!isActive() || treasuryBalance.signum() != 0) {
            throw new IllegalStateException("Only an empty active church can be dissolved.");
        }
        state = State.DISSOLVED;
        closedAt = at;
        lastGovernanceActionAt = at;
        record("CHURCH_DISSOLVED", at, Map.of());
    }

    private void record(String type, long at, Map<String, Object> parameters) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("at", at);
        event.put("parameters", new LinkedHashMap<>(parameters));
        history.add(event);
    }

    private void requirePlayer(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("A player UUID and name are required.");
        }
    }

    private void copyStringMap(Object raw, Map<String, String> target) {
        if (raw instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    target.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
    }

    private void copyHistory(Object raw, List<Map<String, Object>> target) {
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() != null) event.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    target.add(event);
                }
            }
        }
    }

    private List<Map<String, Object>> copyHistoryForSerialization(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> event : source) copy.add(new LinkedHashMap<>(event));
        return copy;
    }

    private long readLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private BigDecimal readNonNegativeDecimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value)).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public static WDEChurch deserialize(Map<String, Object> map) {
        return new WDEChurch(map);
    }
}
