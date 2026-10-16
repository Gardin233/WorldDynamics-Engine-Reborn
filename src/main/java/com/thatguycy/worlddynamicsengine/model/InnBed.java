package com.thatguycy.worlddynamicsengine.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * 持久化单个旅馆床位的金额、币种、城镇和告示牌关联，保留原序列化名称。
 */
@SerializableAs("InnBed")
public record InnBed(BigDecimal amount, String currency, UUID townId, String signKey)
        implements ConfigurationSerializable {
    public InnBed(Map<String, Object> data) {
        this(new BigDecimal(String.valueOf(data.get("amount"))), String.valueOf(data.get("currency")),
                UUID.fromString(String.valueOf(data.get("townId"))), String.valueOf(data.get("signKey")));
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        return Map.of("amount", amount.toPlainString(), "currency", currency,
                "townId", townId.toString(), "signKey", signKey);
    }
}
