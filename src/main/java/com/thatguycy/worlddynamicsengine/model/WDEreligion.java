package com.thatguycy.worlddynamicsengine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * 意识形态持久化实体，保存领袖、模因、戒律、修改冷却、单向金库及什一税政策。
 */
@SerializableAs("WDEreligion")
public class WDEreligion implements ConfigurationSerializable {
    /**
     * 定义定额与财富比例两种什一税计税方式。
     */
    public enum TitheType { FIXED, WEALTH_PERCENTAGE }
    public static final BigDecimal MAX_FIXED_TITHE = new BigDecimal("1000000000000");
    private final String religionName;
    private final UUID founderUuid;
    private final String founderName;
    private final long createdAt;
    private UUID pontiffUuid;
    private String pontiffName;
    private final Map<String, String> excommunicatedPlayers;
    private final Set<String> excommunicatedTowns;
    private final Set<String> excommunicatedNations;
    private final Set<String> memes;
    private final Map<String, String> doctrineAttitudes;
    private final Map<String, Object> memeCooldown;
    private final Map<String, Map<String, Object>> doctrineCooldowns;
    private BigDecimal treasuryBalance = BigDecimal.ZERO;
    private TitheType titheType = TitheType.FIXED;
    private BigDecimal titheAmount = BigDecimal.ZERO;

    public WDEreligion(String religionName, UUID founderUuid, String founderName, long createdAt) {
        this.religionName = religionName;
        this.founderUuid = founderUuid;
        this.founderName = founderName;
        this.createdAt = createdAt;
        this.pontiffUuid = founderUuid;
        this.pontiffName = founderName;
        this.excommunicatedPlayers = new LinkedHashMap<>();
        this.excommunicatedTowns = new LinkedHashSet<>();
        this.excommunicatedNations = new LinkedHashSet<>();
        this.memes = new LinkedHashSet<>();
        this.doctrineAttitudes = new LinkedHashMap<>();
        this.memeCooldown = new LinkedHashMap<>();
        this.doctrineCooldowns = new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public WDEreligion(Map<String, Object> map) {
        this.religionName = String.valueOf(map.getOrDefault("religionName", "Unknown"));
        String founderUuidValue = String.valueOf(map.getOrDefault("founderUuid", "00000000-0000-0000-0000-000000000000"));
        this.founderUuid = UUID.fromString(founderUuidValue);
        this.founderName = String.valueOf(map.getOrDefault("founderName", "Unknown"));
        Object rawCreatedAt = map.get("createdAt");
        this.createdAt = rawCreatedAt instanceof Number ? ((Number) rawCreatedAt).longValue() : System.currentTimeMillis();
        String pontiffUuidValue = String.valueOf(map.getOrDefault("pontiffUuid", founderUuidValue));
        this.pontiffUuid = UUID.fromString(pontiffUuidValue);
        this.pontiffName = String.valueOf(map.getOrDefault("pontiffName", founderName));
        this.excommunicatedPlayers = new LinkedHashMap<>();
        this.excommunicatedTowns = new LinkedHashSet<>();
        this.excommunicatedNations = new LinkedHashSet<>();
        this.memes = new LinkedHashSet<>();
        this.doctrineAttitudes = new LinkedHashMap<>();
        this.memeCooldown = new LinkedHashMap<>();
        this.doctrineCooldowns = new LinkedHashMap<>();

        this.treasuryBalance = readNonNegativeDecimal(map.get("treasuryBalance"));
        try {
            setTithePolicy(TitheType.valueOf(String.valueOf(map.getOrDefault("titheType", "FIXED"))),
                    readNonNegativeDecimal(map.get("titheAmount")));
        } catch (IllegalArgumentException ignored) {
            this.titheType = TitheType.FIXED;
            this.titheAmount = BigDecimal.ZERO;
        }

        Object rawPlayers = map.get("excommunicatedPlayers");
        if (rawPlayers instanceof Map<?, ?> playerMap) {
            for (Map.Entry<?, ?> entry : playerMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.excommunicatedPlayers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }

        readStringSet(map.get("excommunicatedTowns"), this.excommunicatedTowns);
        readStringSet(map.get("excommunicatedNations"), this.excommunicatedNations);
        readStringSet(map.get("memes"), this.memes);

        Object rawDoctrineAttitudes = map.get("doctrineAttitudes");
        if (rawDoctrineAttitudes instanceof Map<?, ?> doctrineMap) {
            for (Map.Entry<?, ?> entry : doctrineMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.doctrineAttitudes.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }

        copyFlatMap(map.get("memeCooldown"), this.memeCooldown);
        Object rawDoctrineCooldowns = map.get("doctrineCooldowns");
        if (rawDoctrineCooldowns instanceof Map<?, ?> cooldownMap) {
            for (Map.Entry<?, ?> entry : cooldownMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    copyFlatMap(entry.getValue(), copy);
                    if (!copy.isEmpty()) {
                        this.doctrineCooldowns.put(String.valueOf(entry.getKey()), copy);
                    }
                }
            }
        }
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("religionName", religionName);
        map.put("founderUuid", founderUuid.toString());
        map.put("founderName", founderName);
        map.put("createdAt", createdAt);
        map.put("pontiffUuid", pontiffUuid.toString());
        map.put("pontiffName", pontiffName);
        map.put("excommunicatedPlayers", new LinkedHashMap<>(excommunicatedPlayers));
        map.put("excommunicatedTowns", new LinkedHashSet<>(excommunicatedTowns));
        map.put("excommunicatedNations", new LinkedHashSet<>(excommunicatedNations));
        map.put("memes", new ArrayList<>(memes));
        map.put("doctrineAttitudes", new LinkedHashMap<>(doctrineAttitudes));
        map.put("memeCooldown", new LinkedHashMap<>(memeCooldown));
        map.put("doctrineCooldowns", serializeNestedDoctrineCooldowns());
        map.put("treasuryBalance", treasuryBalance.toPlainString());
        map.put("titheType", titheType.name());
        map.put("titheAmount", titheAmount.toPlainString());
        return map;
    }

    public BigDecimal getTreasuryBalance() {
        return treasuryBalance;
    }

    // This treasury is a permanent sink: there is deliberately no debit or balance setter.
    public void creditTreasury(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Treasury credits must be positive.");
        }
        treasuryBalance = treasuryBalance.add(amount);
    }

    public TitheType getTitheType() {
        return titheType;
    }

    public BigDecimal getTitheAmount() {
        return titheAmount;
    }

    public void setTithePolicy(TitheType type, BigDecimal amount) {
        validateTithePolicy(type, amount);
        titheType = type;
        titheAmount = amount;
    }

    public static void validateTithePolicy(TitheType type, BigDecimal amount) {
        BigDecimal maximum = type == TitheType.WEALTH_PERCENTAGE
                ? BigDecimal.valueOf(100) : MAX_FIXED_TITHE;
        if (type == null || amount == null || amount.signum() < 0
                || amount.scale() > 8 || amount.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid tithe policy.");
        }
    }

    public BigDecimal calculateTithe(BigDecimal wealth) {
        if (titheType == TitheType.FIXED) {
            return titheAmount;
        }
        return (wealth == null ? BigDecimal.ZERO : wealth.max(BigDecimal.ZERO))
                .multiply(titheAmount).divide(BigDecimal.valueOf(100), 8, RoundingMode.DOWN);
    }

    private static BigDecimal readNonNegativeDecimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value)).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public static WDEreligion deserialize(Map<String, Object> map) {
        return new WDEreligion(map);
    }

    public String getReligionName() {
        return religionName;
    }

    public UUID getFounderUuid() {
        return founderUuid;
    }

    public String getFounderName() {
        return founderName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getPontiffUuid() {
        return pontiffUuid;
    }

    public String getPontiffName() {
        return pontiffName;
    }

    public void setPontiff(UUID pontiffUuid, String pontiffName) {
        this.pontiffUuid = pontiffUuid;
        this.pontiffName = pontiffName;
    }

    public Map<String, String> getExcommunicatedPlayers() {
        return excommunicatedPlayers;
    }

    public Set<String> getExcommunicatedTowns() {
        return excommunicatedTowns;
    }

    public Set<String> getExcommunicatedNations() {
        return excommunicatedNations;
    }

    public List<String> getMemes() {
        return new ArrayList<>(memes);
    }

    public void setMemes(Collection<String> memes) {
        this.memes.clear();
        if (memes == null) {
            return;
        }

        for (String meme : memes) {
            if (meme != null && !meme.isBlank()) {
                this.memes.add(meme);
            }
        }
    }

    public Map<String, String> getDoctrineAttitudes() {
        return new LinkedHashMap<>(doctrineAttitudes);
    }

    public Map<String, Object> getMemeCooldown() {
        return new LinkedHashMap<>(memeCooldown);
    }

    public void setMemeCooldown(Map<String, Object> cooldown) {
        memeCooldown.clear();
        if (cooldown != null) {
            memeCooldown.putAll(cooldown);
        }
    }

    public Map<String, Object> getDoctrineCooldown(String doctrineId) {
        Map<String, Object> cooldown = doctrineId == null ? null : doctrineCooldowns.get(doctrineId);
        return cooldown == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cooldown);
    }

    public Map<String, Map<String, Object>> getDoctrineCooldowns() {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : doctrineCooldowns.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    public void setDoctrineCooldown(String doctrineId, Map<String, Object> cooldown) {
        if (doctrineId == null || doctrineId.isBlank()) {
            return;
        }
        if (cooldown == null || cooldown.isEmpty()) {
            doctrineCooldowns.remove(doctrineId);
            return;
        }
        doctrineCooldowns.put(doctrineId, new LinkedHashMap<>(cooldown));
    }

    public String getDoctrineAttitude(String doctrineId) {
        return doctrineId == null ? null : doctrineAttitudes.get(doctrineId);
    }

    public void setDoctrineAttitude(String doctrineId, String attitudeId) {
        if (doctrineId == null || doctrineId.isBlank()) {
            return;
        }
        if (attitudeId == null || attitudeId.isBlank()) {
            doctrineAttitudes.remove(doctrineId);
            return;
        }
        doctrineAttitudes.put(doctrineId, attitudeId);
    }

    public void removeDoctrineAttitude(String doctrineId) {
        if (doctrineId != null) {
            doctrineAttitudes.remove(doctrineId);
        }
    }

    public boolean isPlayerExcommunicated(UUID playerUuid) {
        return playerUuid != null && excommunicatedPlayers.containsKey(playerUuid.toString());
    }

    public void excommunicatePlayer(UUID playerUuid, String playerName) {
        if (playerUuid != null && playerName != null) {
            excommunicatedPlayers.put(playerUuid.toString(), playerName);
        }
    }

    public boolean isTownExcommunicated(String townName) {
        return containsIgnoreCase(excommunicatedTowns, townName);
    }

    public void excommunicateTown(String townName) {
        if (townName != null && !townName.isBlank()) {
            excommunicatedTowns.add(townName);
        }
    }

    public boolean isNationExcommunicated(String nationName) {
        return containsIgnoreCase(excommunicatedNations, nationName);
    }

    public void excommunicateNation(String nationName) {
        if (nationName != null && !nationName.isBlank()) {
            excommunicatedNations.add(nationName);
        }
    }

    private void readStringSet(Object rawValue, Set<String> target) {
        if (rawValue instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry != null) {
                    target.add(String.valueOf(entry));
                }
            }
        }
    }

    private void copyFlatMap(Object rawValue, Map<String, Object> target) {
        if (rawValue instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null) {
                    target.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
    }

    private Map<String, Map<String, Object>> serializeNestedDoctrineCooldowns() {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : doctrineCooldowns.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        if (target == null) {
            return false;
        }
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
