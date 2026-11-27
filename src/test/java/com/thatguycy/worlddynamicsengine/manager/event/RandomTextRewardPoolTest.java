package com.thatguycy.worlddynamicsengine.manager.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Random;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RandomTextRewardPoolTest {
    @Test
    void roundsConfiguredCurrencyToTwoDecimalPlaces() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.money.minimum", 123.456D);
        config.set("rewards.money.maximum", 123.456D);
        ConfigurationSection rewards = config.getConfigurationSection("rewards");

        BigDecimal money = RandomTextRewardPool.rollMoney(rewards, new Random(11L));

        assertEquals(new BigDecimal("123.46"), money);
    }

    @Test
    void moneyRollIsDeterministicAndBounded() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rewards.money.minimum", 100.0D);
        config.set("rewards.money.maximum", 200.0D);
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        BigDecimal first = RandomTextRewardPool.rollMoney(rewards, new Random(42L));
        BigDecimal repeated = RandomTextRewardPool.rollMoney(rewards, new Random(42L));

        assertEquals(first, repeated);
        assertTrue(first.compareTo(new BigDecimal("100.00")) >= 0);
        assertTrue(first.compareTo(new BigDecimal("200.00")) <= 0);
    }
}
