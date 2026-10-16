package com.thatguycy.worlddynamicsengine.manager.economy;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 封装 Vault 经济与多货币折算、收费及补收，严格区分币种大小写并提供退款所需记录。
 */
public class EconomyBridge {

    /**
     * 保存一次收费的精确金额和币种，供扣款及原币种退款使用。
     */
    public record Charge(BigDecimal amount, String currency) {
        public Charge {
            amount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
            currency = currency == null ? "" : currency.trim();
        }

        public boolean isFree() {
            return amount.compareTo(BigDecimal.ZERO) <= 0;
        }
    }

    /**
     * 封装经济操作是否成功、余额与提供者返回的错误信息。
     */
    public record TransactionResult(boolean success, BigDecimal balance, String errorMessage) {
        public static TransactionResult success(BigDecimal balance) {
            return new TransactionResult(true, balance, "");
        }

        public static TransactionResult failure(String errorMessage) {
            return new TransactionResult(false, BigDecimal.ZERO, errorMessage == null ? "" : errorMessage);
        }
    }

    /**
     * 保存应收、实收默认币值及逐币种扣款明细，支持部分征收和异常退款。
     */
    public record CollectionResult(BigDecimal requestedDefault, BigDecimal collectedDefault, Map<String, BigDecimal> withdrawnAmounts) {
        public CollectionResult {
            requestedDefault = requestedDefault == null ? BigDecimal.ZERO : requestedDefault.max(BigDecimal.ZERO);
            collectedDefault = collectedDefault == null ? BigDecimal.ZERO : collectedDefault.max(BigDecimal.ZERO);
            withdrawnAmounts = withdrawnAmounts == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(withdrawnAmounts));
        }

        public boolean fullyCovered() {
            return collectedDefault.compareTo(requestedDefault) >= 0;
        }

        public boolean collectedAnything() {
            return collectedDefault.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    private final WorldDynamicsEngine plugin;
    private final Economy legacyEconomy;
    private final Object modernEconomy;
    private final Class<?> modernEconomyClass;
    private final String pluginName;
    private final String defaultCurrency;
    private final Map<String, BigDecimal> exchangeRates;

    public EconomyBridge(WorldDynamicsEngine plugin, Economy legacyEconomy, String defaultCurrency, Map<String, BigDecimal> exchangeRates) {
        this.plugin = plugin;
        this.legacyEconomy = legacyEconomy;
        this.modernEconomy = resolveModernEconomy(plugin);
        this.modernEconomyClass = resolveModernEconomyClass();
        this.pluginName = plugin.getDescription().getName();
        this.defaultCurrency = (defaultCurrency == null || defaultCurrency.isBlank()) ? "default" : defaultCurrency.trim();
        this.exchangeRates = Collections.unmodifiableMap(new LinkedHashMap<>(exchangeRates));
    }

    public boolean hasProvider() {
        return legacyEconomy != null;
    }

    public boolean hasModernEconomy() {
        return modernEconomy != null;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public Map<String, BigDecimal> getExchangeRates() {
        return exchangeRates;
    }

    public Charge charge(BigDecimal amount, String currency) {
        return new Charge(amount, currency == null || currency.isBlank() ? defaultCurrency : currency);
    }

    public String format(Charge charge) {
        return format(charge.amount(), charge.currency());
    }

    public String format(BigDecimal amount) {
        return format(amount, defaultCurrency);
    }

    public String format(BigDecimal amount, String currency) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        String resolvedCurrency = normalizeCurrency(currency);
        if (modernEconomy != null) {
            Object formatted = invokeModern("format", new Class[]{String.class, BigDecimal.class, String.class}, pluginName, safeAmount, resolvedCurrency);
            if (formatted instanceof String value && !value.isBlank()) {
                return value;
            }
        }

        if (legacyEconomy != null) {
            return legacyEconomy.format(safeAmount.doubleValue());
        }

        return safeAmount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + resolvedCurrency;
    }

    public boolean has(Player player, Charge charge) {
        if (charge == null || charge.isFree()) {
            return true;
        }
        if (player == null || legacyEconomy == null) {
            return false;
        }
        if (!supportsExactCurrency(charge.currency())) {
            return false;
        }

        if (modernEconomy != null) {
            Object value = invokeModern(
                    "has",
                    new Class[]{String.class, UUID.class, String.class, String.class, BigDecimal.class},
                    pluginName,
                    player.getUniqueId(),
                    getWorldName(player),
                    normalizeCurrency(charge.currency()),
                    charge.amount()
            );
            if (value instanceof Boolean supported) {
                return supported;
            }
        }

        return normalizeCurrency(charge.currency()).equals(defaultCurrency)
                && legacyEconomy.has(player, charge.amount().doubleValue());
    }

    public TransactionResult withdraw(Player player, Charge charge) {
        if (charge == null || charge.isFree()) {
            return TransactionResult.success(BigDecimal.ZERO);
        }
        if (player == null || legacyEconomy == null) {
            return TransactionResult.failure("Economy provider missing.");
        }
        if (!supportsExactCurrency(charge.currency())) {
            return TransactionResult.failure("Requested currency is unavailable.");
        }

        if (modernEconomy != null) {
            Object response = invokeModern(
                    "withdraw",
                    new Class[]{String.class, UUID.class, String.class, String.class, BigDecimal.class},
                    pluginName,
                    player.getUniqueId(),
                    getWorldName(player),
                    normalizeCurrency(charge.currency()),
                    charge.amount()
            );
            TransactionResult modernResult = mapModernResponse(response);
            if (modernResult != null) {
                return modernResult;
            }
        }

        if (!normalizeCurrency(charge.currency()).equals(defaultCurrency)) {
            return TransactionResult.failure("Currency withdrawal failed.");
        }
        net.milkbowl.vault.economy.EconomyResponse response = legacyEconomy.withdrawPlayer(player, charge.amount().doubleValue());
        return response.transactionSuccess()
                ? TransactionResult.success(BigDecimal.valueOf(response.balance))
                : TransactionResult.failure(response.errorMessage);
    }

    public TransactionResult withdraw(UUID playerUuid, String worldName, Charge charge) {
        if (charge == null || charge.isFree()) {
            return TransactionResult.success(BigDecimal.ZERO);
        }
        if (playerUuid == null || legacyEconomy == null) {
            return TransactionResult.failure("Economy provider missing.");
        }
        if (!supportsExactCurrency(charge.currency())) {
            return TransactionResult.failure("Requested currency is unavailable.");
        }

        if (modernEconomy != null) {
            Object response = invokeModern(
                    "withdraw",
                    new Class[]{String.class, UUID.class, String.class, String.class, BigDecimal.class},
                    pluginName,
                    playerUuid,
                    worldName == null ? "" : worldName,
                    normalizeCurrency(charge.currency()),
                    charge.amount()
            );
            TransactionResult modernResult = mapModernResponse(response);
            if (modernResult != null) {
                return modernResult;
            }
        }

        if (!normalizeCurrency(charge.currency()).equals(defaultCurrency)) {
            return TransactionResult.failure("Currency withdrawal failed.");
        }
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUuid);
        net.milkbowl.vault.economy.EconomyResponse response = legacyEconomy.withdrawPlayer(offlinePlayer, charge.amount().doubleValue());
        return response.transactionSuccess()
                ? TransactionResult.success(BigDecimal.valueOf(response.balance))
                : TransactionResult.failure(response.errorMessage);
    }

    public TransactionResult deposit(Player player, BigDecimal amount) {
        return deposit(player, amount, defaultCurrency);
    }

    // Inn payments must never fall back to a different currency.
    public boolean supportsExactCurrency(String currency) {
        String resolved = normalizeCurrency(currency);
        return hasProvider() && (modernEconomy != null
                ? hasCurrency(resolved) && (resolved.equals(defaultCurrency) || supportsMultiCurrency())
                : resolved.equals(defaultCurrency));
    }

    public TransactionResult withdrawExact(UUID uuid, String world, Charge charge) {
        if (!supportsExactCurrency(charge.currency())) {
            return TransactionResult.failure("Requested currency is unavailable.");
        }
        if (charge.isFree()) {
            return TransactionResult.success(BigDecimal.ZERO);
        }
        if (modernEconomy != null) {
            TransactionResult result = mapModernResponse(invokeModern("withdraw",
                    new Class[]{String.class, UUID.class, String.class, String.class, BigDecimal.class},
                    pluginName, uuid, world, normalizeCurrency(charge.currency()), charge.amount()));
            return result == null ? TransactionResult.failure("Currency withdrawal failed.") : result;
        }
        return withdraw(uuid, world, charge);
    }

    public TransactionResult depositExact(UUID uuid, String world, Charge charge) {
        if (!supportsExactCurrency(charge.currency())) {
            return TransactionResult.failure("Requested currency is unavailable.");
        }
        if (charge.isFree()) {
            return TransactionResult.success(BigDecimal.ZERO);
        }
        if (modernEconomy != null) {
            TransactionResult result = mapModernResponse(invokeModern("deposit",
                    new Class[]{String.class, UUID.class, String.class, String.class, BigDecimal.class},
                    pluginName, uuid, world, normalizeCurrency(charge.currency()), charge.amount()));
            return result == null ? TransactionResult.failure("Currency deposit failed.") : result;
        }
        var response = legacyEconomy.depositPlayer(plugin.getServer().getOfflinePlayer(uuid),
                charge.amount().doubleValue());
        return response.transactionSuccess()
                ? TransactionResult.success(BigDecimal.valueOf(response.balance))
                : TransactionResult.failure(response.errorMessage);
    }

    public TransactionResult deposit(Player player, BigDecimal amount, String currency) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        if (safeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return TransactionResult.success(BigDecimal.ZERO);
        }
        if (player == null || legacyEconomy == null) {
            return TransactionResult.failure("Economy provider missing.");
        }
        if (!supportsExactCurrency(currency)) {
            return TransactionResult.failure("Requested currency is unavailable.");
        }

        if (modernEconomy != null) {
            Object response = invokeModern(
                    "deposit",
                    new Class[]{String.class, UUID.class, String.class, String.class, BigDecimal.class},
                    pluginName,
                    player.getUniqueId(),
                    getWorldName(player),
                    normalizeCurrency(currency),
                    safeAmount
            );
            TransactionResult modernResult = mapModernResponse(response);
            if (modernResult != null) {
                return modernResult;
            }
        }

        if (!normalizeCurrency(currency).equals(defaultCurrency)) {
            return TransactionResult.failure("Currency deposit failed.");
        }
        net.milkbowl.vault.economy.EconomyResponse response = legacyEconomy.depositPlayer(player, safeAmount.doubleValue());
        return response.transactionSuccess()
                ? TransactionResult.success(BigDecimal.valueOf(response.balance))
                : TransactionResult.failure(response.errorMessage);
    }

    public BigDecimal getBalance(Player player) {
        return getBalance(player, defaultCurrency);
    }

    public BigDecimal getBalance(Player player, String currency) {
        if (player == null || legacyEconomy == null) {
            return BigDecimal.ZERO;
        }
        if (!supportsExactCurrency(currency)) {
            return BigDecimal.ZERO;
        }

        if (modernEconomy != null) {
            Object value = invokeModern(
                    "balance",
                    new Class[]{String.class, UUID.class, String.class, String.class},
                    pluginName,
                    player.getUniqueId(),
                    getWorldName(player),
                    normalizeCurrency(currency)
            );
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
        }

        return normalizeCurrency(currency).equals(defaultCurrency)
                ? BigDecimal.valueOf(legacyEconomy.getBalance(player)) : BigDecimal.ZERO;
    }

    public BigDecimal getBalance(UUID playerUuid, String worldName, String currency) {
        if (playerUuid == null || legacyEconomy == null) {
            return BigDecimal.ZERO;
        }
        if (!supportsExactCurrency(currency)) {
            return BigDecimal.ZERO;
        }

        if (modernEconomy != null) {
            Object value = invokeModern(
                    "balance",
                    new Class[]{String.class, UUID.class, String.class, String.class},
                    pluginName,
                    playerUuid,
                    worldName == null ? "" : worldName,
                    normalizeCurrency(currency)
            );
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
        }

        if (!normalizeCurrency(currency).equals(defaultCurrency)) {
            return BigDecimal.ZERO;
        }
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUuid);
        return BigDecimal.valueOf(legacyEconomy.getBalance(offlinePlayer));
    }

    public CollectionResult collectMaximumAsDefault(UUID playerUuid, String worldName, BigDecimal requestedDefault, Iterable<String> currencies) {
        BigDecimal safeRequested = requestedDefault == null ? BigDecimal.ZERO : requestedDefault.max(BigDecimal.ZERO);
        if (safeRequested.compareTo(BigDecimal.ZERO) <= 0) {
            return new CollectionResult(BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyMap());
        }

        BigDecimal remainingDefault = safeRequested;
        BigDecimal collectedDefault = BigDecimal.ZERO;
        Map<String, BigDecimal> withdrawnAmounts = new LinkedHashMap<>();

        for (String currency : orderedCurrencies(currencies)) {
            if (remainingDefault.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal balance = getBalance(playerUuid, worldName, currency);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal neededCurrency = normalizeCurrency(currency).equals(defaultCurrency)
                    ? remainingDefault
                    : convert(remainingDefault, defaultCurrency, currency);
            BigDecimal withdrawAmount = neededCurrency.min(balance).max(BigDecimal.ZERO);
            if (withdrawAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            TransactionResult result = withdraw(playerUuid, worldName, new Charge(withdrawAmount, currency));
            if (!result.success()) {
                continue;
            }

            BigDecimal collectedInDefault = normalizeCurrency(currency).equals(defaultCurrency)
                    ? withdrawAmount
                    : convert(withdrawAmount, currency, defaultCurrency);
            if (collectedInDefault.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            withdrawnAmounts.merge(normalizeCurrency(currency), withdrawAmount, BigDecimal::add);
            collectedDefault = collectedDefault.add(collectedInDefault);
            remainingDefault = safeRequested.subtract(collectedDefault).max(BigDecimal.ZERO);
        }

        return new CollectionResult(safeRequested, collectedDefault, withdrawnAmounts);
    }

    public BigDecimal getTotalWealthAsDefault(UUID playerUuid, String worldName, Iterable<String> currencies) {
        if (playerUuid == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (String currency : orderedCurrencies(currencies)) {
            BigDecimal balance = getBalance(playerUuid, worldName, currency);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String normalizedCurrency = normalizeCurrency(currency);
            BigDecimal asDefault = normalizedCurrency.equals(defaultCurrency)
                    ? balance
                    : convert(balance, normalizedCurrency, defaultCurrency);
            if (asDefault.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(asDefault);
            }
        }
        return total;
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        String normalizedFrom = normalizeCurrency(fromCurrency);
        String normalizedTo = normalizeCurrency(toCurrency);
        if (normalizedFrom.equals(normalizedTo)) {
            return safeAmount;
        }

        BigDecimal fromRate = exchangeRates.get(normalizedFrom);
        BigDecimal toRate = exchangeRates.get(normalizedTo);
        if (fromRate == null || toRate == null || toRate.compareTo(BigDecimal.ZERO) == 0) {
            return safeAmount;
        }

        BigDecimal baseAmount = safeAmount.multiply(fromRate);
        return baseAmount.divide(toRate, 8, RoundingMode.HALF_UP);
    }

    public void logConfiguredCurrencyStatus() {
        if (legacyEconomy == null) {
            return;
        }

        if (modernEconomy == null) {
            plugin.getLogger().info("Economy bridge is using legacy Vault compatibility mode. Only the configured default currency is available.");
            return;
        }

        if (!supportsMultiCurrency()) {
            plugin.getLogger().info("Economy provider does not advertise multi-currency support. Non-default currency operations are disabled.");
            return;
        }

        for (String currency : exchangeRates.keySet()) {
            if (!hasCurrency(currency)) {
                plugin.getLogger().warning("Configured currency id not found in VaultUnlocked provider: " + currency);
            }
        }
    }

    public boolean hasCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return false;
        }
        if (modernEconomy == null) {
            return normalizeCurrency(currency).equals(defaultCurrency);
        }
        String resolved = normalizeCurrency(currency);
        // Providers may implement hasCurrency without case sensitivity.
        if (!currencies().contains(resolved)) {
            return false;
        }
        Object result = invokeModern("hasCurrency", new Class[]{String.class}, resolved);
        return result instanceof Boolean value && value;
    }

    public boolean supportsMultiCurrency() {
        if (modernEconomy == null) {
            return false;
        }
        Object result = invokeModern("hasMultiCurrencySupport", new Class[0]);
        return result instanceof Boolean value && value;
    }

    public Collection<String> currencies() {
        if (modernEconomy == null) {
            return Collections.singleton(defaultCurrency);
        }
        Object result = invokeModern("currencies", new Class[0]);
        if (result instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return Collections.singleton(defaultCurrency);
    }

    private List<String> orderedCurrencies(Iterable<String> currencies) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(defaultCurrency);
        if (currencies != null) {
            for (String currency : currencies) {
                if (currency != null && !currency.isBlank()) {
                    String resolved = normalizeCurrency(currency);
                    if (exchangeRates.containsKey(resolved) && supportsExactCurrency(resolved)) {
                        ordered.add(resolved);
                    }
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    private Object resolveModernEconomy(WorldDynamicsEngine plugin) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault2.economy.Economy");
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<Object> serviceProvider = (RegisteredServiceProvider<Object>) plugin.getServer()
                    .getServicesManager()
                    .getRegistration((Class<Object>) economyClass);
            return serviceProvider != null ? serviceProvider.getProvider() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Class<?> resolveModernEconomyClass() {
        try {
            return Class.forName("net.milkbowl.vault2.economy.Economy");
        } catch (Throwable ignored) {
            return modernEconomy != null ? modernEconomy.getClass() : null;
        }
    }

    private Object invokeModern(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (modernEconomy == null || modernEconomyClass == null) {
            return null;
        }

        try {
            Method method = modernEconomyClass.getMethod(methodName, parameterTypes);
            return method.invoke(modernEconomy, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private TransactionResult mapModernResponse(Object response) {
        if (response == null) {
            return null;
        }

        try {
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            boolean success = (boolean) successMethod.invoke(response);
            Method balanceMethod = response.getClass().getMethod("balance");
            BigDecimal balance = (BigDecimal) balanceMethod.invoke(response);
            Method errorMethod = response.getClass().getMethod("errorMessage");
            String error = String.valueOf(errorMethod.invoke(response));
            return success ? TransactionResult.success(balance) : TransactionResult.failure(error);
        } catch (ReflectiveOperationException ignored) {
            try {
                boolean success = "SUCCESS".equals(String.valueOf(response.getClass().getField("type").get(response)));
                BigDecimal balance = (BigDecimal) response.getClass().getField("balance").get(response);
                String error = String.valueOf(response.getClass().getField("errorMessage").get(response));
                return success ? TransactionResult.success(balance) : TransactionResult.failure(error);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private String getWorldName(Player player) {
        return player.getWorld() != null ? player.getWorld().getName() : "";
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return defaultCurrency;
        }
        String normalized = currency.trim();
        if ("default".equals(normalized)) {
            return defaultCurrency;
        }
        return normalized;
    }
}
