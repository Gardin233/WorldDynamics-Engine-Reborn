package com.thatguycy.worlddynamicsengine.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/** 持久化神圣大会议案、密封教会表决及执行状态。 */
@SerializableAs("IdeologyCouncilProposal")
public final class IdeologyCouncilProposal implements ConfigurationSerializable {
    public enum Type {
        SET_MEMES, SET_DOCTRINE, SET_TITHE,
        SANCTION_CHURCH, LIFT_SANCTION, APPEAL_SANCTION,
        REFORM_UNION
    }

    public enum State { PENDING, APPROVED, REJECTED, COMPLETED, FAILED }

    private final UUID proposalUuid;
    private final UUID sourceIdeologyUuid;
    private final String sourceIdeologyName;
    private final UUID proposerChurchUuid;
    private final UUID proposerUuid;
    private final String proposerName;
    private final Type type;
    private final UUID targetChurchUuid;
    private final Map<String, Object> payload = new LinkedHashMap<>();
    private final String reason;
    private final long createdAt;
    private final long deadlineAt;
    private final Map<String, String> votes = new LinkedHashMap<>();
    private State state = State.PENDING;
    private int totalWeight;
    private int yesWeight;
    private int noWeight;
    private long resolvedAt;
    private String outcome = "";

    public IdeologyCouncilProposal(UUID proposalUuid, UUID sourceIdeologyUuid, String sourceIdeologyName,
            UUID proposerChurchUuid, UUID proposerUuid, String proposerName, Type type,
            UUID targetChurchUuid, Map<String, Object> payload, String reason, long createdAt, long deadlineAt) {
        this.proposalUuid = proposalUuid;
        this.sourceIdeologyUuid = sourceIdeologyUuid;
        this.sourceIdeologyName = sourceIdeologyName;
        this.proposerChurchUuid = proposerChurchUuid;
        this.proposerUuid = proposerUuid;
        this.proposerName = proposerName;
        this.type = type;
        this.targetChurchUuid = targetChurchUuid;
        this.reason = reason == null ? "" : reason;
        this.createdAt = createdAt;
        this.deadlineAt = deadlineAt;
        if (payload != null) this.payload.putAll(payload);
        validateRequiredFields();
    }

    public IdeologyCouncilProposal(Map<String, Object> map) {
        proposalUuid = UUID.fromString(String.valueOf(map.get("proposalUuid")));
        sourceIdeologyUuid = UUID.fromString(String.valueOf(map.get("sourceIdeologyUuid")));
        sourceIdeologyName = String.valueOf(map.getOrDefault("sourceIdeologyName", "Unknown"));
        proposerChurchUuid = UUID.fromString(String.valueOf(map.get("proposerChurchUuid")));
        proposerUuid = UUID.fromString(String.valueOf(map.get("proposerUuid")));
        proposerName = String.valueOf(map.getOrDefault("proposerName", "Unknown"));
        type = Type.valueOf(String.valueOf(map.get("type")));
        Object target = map.get("targetChurchUuid");
        targetChurchUuid = target == null || String.valueOf(target).isBlank()
                ? null : UUID.fromString(String.valueOf(target));
        copyMap(map.get("payload"), payload);
        reason = String.valueOf(map.getOrDefault("reason", ""));
        createdAt = readLong(map.get("createdAt"), 0L);
        deadlineAt = readLong(map.get("deadlineAt"), 0L);
        copyStringMap(map.get("votes"), votes);
        try {
            state = State.valueOf(String.valueOf(map.getOrDefault("state", "PENDING")));
        } catch (IllegalArgumentException ignored) {
            state = State.FAILED;
        }
        totalWeight = (int) readLong(map.get("totalWeight"), 0L);
        yesWeight = (int) readLong(map.get("yesWeight"), 0L);
        noWeight = (int) readLong(map.get("noWeight"), 0L);
        resolvedAt = readLong(map.get("resolvedAt"), 0L);
        outcome = String.valueOf(map.getOrDefault("outcome", ""));
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("proposalUuid", proposalUuid.toString());
        map.put("sourceIdeologyUuid", sourceIdeologyUuid.toString());
        map.put("sourceIdeologyName", sourceIdeologyName);
        map.put("proposerChurchUuid", proposerChurchUuid.toString());
        map.put("proposerUuid", proposerUuid.toString());
        map.put("proposerName", proposerName);
        map.put("type", type.name());
        map.put("targetChurchUuid", targetChurchUuid == null ? "" : targetChurchUuid.toString());
        map.put("payload", new LinkedHashMap<>(payload));
        map.put("reason", reason);
        map.put("createdAt", createdAt);
        map.put("deadlineAt", deadlineAt);
        map.put("votes", new LinkedHashMap<>(votes));
        map.put("state", state.name());
        map.put("totalWeight", totalWeight);
        map.put("yesWeight", yesWeight);
        map.put("noWeight", noWeight);
        map.put("resolvedAt", resolvedAt);
        map.put("outcome", outcome);
        return map;
    }

    public UUID getProposalUuid() { return proposalUuid; }
    public UUID getSourceIdeologyUuid() { return sourceIdeologyUuid; }
    public String getSourceIdeologyName() { return sourceIdeologyName; }
    public UUID getProposerChurchUuid() { return proposerChurchUuid; }
    public UUID getProposerUuid() { return proposerUuid; }
    public String getProposerName() { return proposerName; }
    public Type getType() { return type; }
    public UUID getTargetChurchUuid() { return targetChurchUuid; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
    public long getDeadlineAt() { return deadlineAt; }
    public Map<String, String> getVotes() { return new LinkedHashMap<>(votes); }
    public State getState() { return state; }
    public int getTotalWeight() { return totalWeight; }
    public int getYesWeight() { return yesWeight; }
    public int getNoWeight() { return noWeight; }
    public long getResolvedAt() { return resolvedAt; }
    public String getOutcome() { return outcome; }

    public Map<String, Object> getPayload() {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection) {
                copy.put(entry.getKey(), collection.stream().map(String::valueOf).toList());
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedCopy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                    if (nestedEntry.getKey() != null) {
                        nestedCopy.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
                    }
                }
                copy.put(entry.getKey(), nestedCopy);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    public boolean isPending() { return state == State.PENDING; }

    public void castVote(UUID churchUuid, String choice) {
        if (!isPending() || churchUuid == null || !("YES".equals(choice) || "NO".equals(choice))) {
            throw new IllegalArgumentException("Invalid council ballot.");
        }
        if (votes.containsKey(churchUuid.toString())) {
            throw new IllegalArgumentException("This church has already voted on the proposal.");
        }
        votes.put(churchUuid.toString(), choice);
    }

    public void resolve(State result, int totalWeight, int yesWeight, int noWeight,
            long resolvedAt, String outcome) {
        if (!isPending() || result == State.PENDING || result == State.APPROVED) {
            throw new IllegalStateException("Invalid proposal resolution.");
        }
        this.totalWeight = Math.max(0, totalWeight);
        this.yesWeight = Math.max(0, yesWeight);
        this.noWeight = Math.max(0, noWeight);
        this.resolvedAt = resolvedAt;
        this.state = result;
        this.outcome = outcome == null ? "" : outcome;
    }

    public void approve(int totalWeight, int yesWeight, int noWeight, long resolvedAt) {
        if (!isPending()) throw new IllegalStateException("Proposal is no longer pending.");
        this.totalWeight = Math.max(0, totalWeight);
        this.yesWeight = Math.max(0, yesWeight);
        this.noWeight = Math.max(0, noWeight);
        this.resolvedAt = resolvedAt;
        this.state = State.APPROVED;
        this.outcome = "APPROVED";
    }

    public void complete(long at, String result) {
        if (state != State.APPROVED) throw new IllegalStateException("Only an approved proposal can be completed.");
        state = State.COMPLETED;
        resolvedAt = at;
        outcome = result == null ? "COMPLETED" : result;
    }

    public void fail(long at, String result) {
        if (state != State.APPROVED && state != State.PENDING) {
            throw new IllegalStateException("Only an unresolved proposal can fail.");
        }
        state = State.FAILED;
        resolvedAt = at;
        outcome = result == null ? "FAILED" : result;
    }

    private void validateRequiredFields() {
        if (proposalUuid == null || sourceIdeologyUuid == null || sourceIdeologyName == null
                || proposerChurchUuid == null || proposerUuid == null || proposerName == null
                || type == null || deadlineAt <= createdAt) {
            throw new IllegalArgumentException("Proposal identity, type, and future deadline are required.");
        }
    }

    private void copyMap(Object raw, Map<String, Object> target) {
        if (raw instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null) target.put(String.valueOf(entry.getKey()), entry.getValue());
            }
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

    private long readLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    public static IdeologyCouncilProposal deserialize(Map<String, Object> map) {
        return new IdeologyCouncilProposal(map);
    }
}
