package com.thatguycy.worlddynamicsengine;

import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns configuration files, disk reloads, default repair and legacy migrations. */
final class PluginConfiguration {
    private final ConfigurationFiles files;
    private final Supplier<FileConfiguration> rootConfig;
    private final Runnable saveRoot;
    private final Supplier<Logger> logger;

    PluginConfiguration(JavaPlugin plugin) {
        this(plugin::getDataFolder, plugin::getConfig, plugin::saveConfig, plugin::saveResource, plugin::getLogger);
    }

    PluginConfiguration(Supplier<File> dataFolder, Supplier<FileConfiguration> rootConfig, Runnable saveRoot,
                        BiConsumer<String, Boolean> exportResource, Supplier<Logger> logger) {
        this.files = new ConfigurationFiles(dataFolder, exportResource, logger);
        this.rootConfig = rootConfig;
        this.saveRoot = saveRoot;
        this.logger = logger;
    }

    record RuntimeFlags(String framework, boolean govEnabled, boolean orgEnabled, boolean armyEnabled,
                        boolean religionEnabled, int nationOrgLimit, String userLang) {}

    void loadAndMigrate() {
        loadModuleConfigs();
        migrateLegacyConfigIfNeeded();
    }

    private FileConfiguration getConfig() { return rootConfig.get(); }
    private Logger getLogger() { return logger.get(); }
    private void saveConfig() { saveRoot.run(); }

    private String defaultEconomyCurrency;
    private Map<String, BigDecimal> currencyExchangeRates = new LinkedHashMap<>();
    private EconomyBridge.Charge diplomacyVisitCostNeutralNoneCharge;
    private EconomyBridge.Charge diplomacyVisitCostFriendlyCharge;
    private EconomyBridge.Charge diplomacyFormCostTradeCharge;
    private EconomyBridge.Charge orgFormationCostCharge;
    private EconomyBridge.Charge orgMaintenanceCostCharge;
    private EconomyBridge.Charge religionCreationCostCharge;
    private EconomyBridge.Charge religionPlayerConversionCostCharge;
    private EconomyBridge.Charge religionTownConversionCostCharge;
    private EconomyBridge.Charge religionNationConversionCostCharge;
    private EconomyBridge.Charge religionForcedPlayerConversionCostCharge;
    private EconomyBridge.Charge religionForcedTownConversionCostCharge;

    private File townConfigFile;
    private File nationConfigFile;
    private File organizationConfigFile;
    private File warConfigFile;
    private File ideologyConfigFile;
    private File backgroundConfigFile;
    private File dropsConfigFile;
    private FileConfiguration townConfig;
    private FileConfiguration nationConfig;
    private FileConfiguration organizationConfig;
    private FileConfiguration warConfig;
    private FileConfiguration ideologyConfig;
    private FileConfiguration backgroundConfig;
    private FileConfiguration dropsConfig;
    private long organizationMaintenanceIntervalHours;
    void ensureResources() {
        files.ensureResources();
    }

    RuntimeFlags readConfigSettings() {
        String framework = getConfig().getString("framework", "towny");
        boolean govEnabled = getConfig().getBoolean("govEnabled", true);
        boolean orgEnabled = getConfig().getBoolean("orgEnabled", true);
        boolean armyEnabled = getConfig().getBoolean("armyEnabled", true);
        boolean religionEnabled = ideologyConfig != null && ideologyConfig.getBoolean("religionEnabled", true);
        currencyExchangeRates = loadExchangeRates();
        defaultEconomyCurrency = resolveDefaultEconomyCurrency();
        diplomacyVisitCostNeutralNoneCharge = readCharge(getConfig(), "economy.charges.diplomacyVisitNeutralNone", "diplomacyVisitCostNeutralNone", "default", "1000.00");
        diplomacyVisitCostFriendlyCharge = readCharge(getConfig(), "economy.charges.diplomacyVisitFriendly", "diplomacyVisitCostFriendly", "default", "750.00");
        diplomacyFormCostTradeCharge = readCharge(getConfig(), "economy.charges.diplomacyFormTrade", "diplomacyFormCostTrade", "default", "10000.00");
        religionCreationCostCharge = readCharge(ideologyConfig, "economy.charges.religionCreation", "religionCreationCost", "default", "15000.00");
        religionPlayerConversionCostCharge = readCharge(ideologyConfig, "economy.charges.religionPlayerConversion", "religionPlayerConversionCost", "default", "3000.00");
        religionTownConversionCostCharge = readCharge(ideologyConfig, "economy.charges.religionTownConversion", "religionTownConversionCost", "default", "12000.00");
        religionNationConversionCostCharge = readCharge(ideologyConfig, "economy.charges.religionNationConversion", "religionNationConversionCost", "default", "30000.00");
        religionForcedPlayerConversionCostCharge = readCharge(ideologyConfig, "economy.charges.religionForcedPlayerConversion", "religionForcedPlayerConversionCost", "default", "5000.00");
        religionForcedTownConversionCostCharge = readCharge(ideologyConfig, "economy.charges.religionForcedTownConversion", "religionForcedTownConversionCost", "default", "20000.00");
        orgFormationCostCharge = readCharge(organizationConfig, "economy.charges.orgFormation", "orgFormationCost", "default", "25000.00");
        orgMaintenanceCostCharge = readCharge(organizationConfig, "economy.charges.orgMaintenance", "orgMaintenanceCost", "default", "0.00");
        int nationOrgLimit;
        if (organizationConfig != null) {
            boolean organizationConfigChanged = false;
            if (!organizationConfig.contains("economy.maintenance.intervalHours")
                    && organizationConfig.contains("economy.maintenance.intervalMinutes")) {
                long legacyMinutes = Math.max(0L, organizationConfig.getLong("economy.maintenance.intervalMinutes", 1440L));
                long migratedHours = legacyMinutes <= 0L ? 0L : Math.max(1L, (legacyMinutes + 59L) / 60L);
                organizationConfig.set("economy.maintenance.intervalHours", migratedHours);
                organizationConfig.set("economy.maintenance.intervalMinutes", null);
                organizationConfigChanged = true;
            }
            organizationMaintenanceIntervalHours = Math.max(0L, organizationConfig.getLong("economy.maintenance.intervalHours", 24L));
            if (!organizationConfig.contains("nationOrgLimit") && organizationConfig.contains("residentOrgLimit")) {
                organizationConfig.set("nationOrgLimit", organizationConfig.getInt("residentOrgLimit", -1));
                organizationConfig.set("residentOrgLimit", null);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.intervalHours")) {
                organizationConfig.set("sovereignEmpire.subjectControl.intervalHours", 24L);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.defaultSuppression")) {
                organizationConfig.set("sovereignEmpire.subjectControl.defaultSuppression", 50);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.suppressionDecrease")) {
                organizationConfig.set("sovereignEmpire.subjectControl.suppressionDecrease", 10);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.suppressionIncrease")) {
                organizationConfig.set("sovereignEmpire.subjectControl.suppressionIncrease", 10);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.forcedLeaderChangeSuppressionReduction")) {
                organizationConfig.set("sovereignEmpire.subjectControl.forcedLeaderChangeSuppressionReduction", 50);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.forcedTributeSuppressionReduction")) {
                organizationConfig.set("sovereignEmpire.subjectControl.forcedTributeSuppressionReduction", 30);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.autonomyPromotionResetSuppression")) {
                organizationConfig.set("sovereignEmpire.subjectControl.autonomyPromotionResetSuppression", 90);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.autonomyDemotionResetSuppression")) {
                organizationConfig.set("sovereignEmpire.subjectControl.autonomyDemotionResetSuppression", 10);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.dominion.fixed")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.dominion.fixed", 5000.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.protectorate.fixed")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.protectorate.fixed", 3000.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.puppet.fixed")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.puppet.fixed", 1500.0D);
                organizationConfigChanged = true;
            }
            for (String tier : List.of("dominion", "protectorate", "puppet")) {
                String obsoletePath = "sovereignEmpire.subjectControl.taxCaps." + tier + ".wealthPercentage";
                if (organizationConfig.contains(obsoletePath)) {
                    organizationConfig.set(obsoletePath, null);
                    organizationConfigChanged = true;
                }
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.autonomyUpgradeCost.amount")) {
                organizationConfig.set("sovereignEmpire.subjectControl.autonomyUpgradeCost.amount", "10000.00");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.autonomyUpgradeCost.currency")) {
                organizationConfig.set("sovereignEmpire.subjectControl.autonomyUpgradeCost.currency", "default");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.tribute.enabled")) {
                organizationConfig.set("sovereignEmpire.tribute.enabled", true);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.tribute.intervalHours")) {
                organizationConfig.set("sovereignEmpire.tribute.intervalHours", 24L);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.tribute.amount")) {
                organizationConfig.set("sovereignEmpire.tribute.amount", "0.00");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.tribute.currency")) {
                organizationConfig.set("sovereignEmpire.tribute.currency", "default");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.warParticipation.checkIntervalSeconds")) {
                organizationConfig.set("sovereignEmpire.warParticipation.checkIntervalSeconds", 180L);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.warParticipation.commanderRadius")) {
                organizationConfig.set("sovereignEmpire.warParticipation.commanderRadius", 200.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.forcedTribute.byTier.dominion.amount")) {
                organizationConfig.set("sovereignEmpire.forcedTribute.byTier.dominion.amount", "4000.00");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.forcedTribute.byTier.dominion.currency")) {
                organizationConfig.set("sovereignEmpire.forcedTribute.byTier.dominion.currency", "default");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.forcedTribute.byTier.protectorate.amount")) {
                organizationConfig.set("sovereignEmpire.forcedTribute.byTier.protectorate.amount", "8000.00");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.forcedTribute.byTier.protectorate.currency")) {
                organizationConfig.set("sovereignEmpire.forcedTribute.byTier.protectorate.currency", "default");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.forcedTribute.byTier.puppet.amount")) {
                organizationConfig.set("sovereignEmpire.forcedTribute.byTier.puppet.amount", "12000.00");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.forcedTribute.byTier.puppet.currency")) {
                organizationConfig.set("sovereignEmpire.forcedTribute.byTier.puppet.currency", "default");
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("ideologicalAlliance.peaceTreaty.sameReligionClauseCostMultiplier")) {
                organizationConfig.set("ideologicalAlliance.peaceTreaty.sameReligionClauseCostMultiplier", 1.50D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("ideologicalAlliance.peaceTreaty.hereticClauseCostMultiplier")) {
                organizationConfig.set("ideologicalAlliance.peaceTreaty.hereticClauseCostMultiplier", 0.75D);
                organizationConfigChanged = true;
            }
            if (organizationConfigChanged) {
                files.save(organizationConfig, organizationConfigFile);
            }
            nationOrgLimit = organizationConfig.getInt("nationOrgLimit", -1);
        } else {
            organizationMaintenanceIntervalHours = 24L;
            nationOrgLimit = -1;
        }
        if (warConfig != null) {
            boolean warConfigChanged = false;
            if (organizationConfig != null && organizationConfig.contains("peaceTreaty")) {
                if (!warConfig.contains("peaceTreaty")) {
                    warConfig.set("peaceTreaty", organizationConfig.get("peaceTreaty"));
                    warConfigChanged = true;
                }
                organizationConfig.set("peaceTreaty", null);
                files.save(organizationConfig, organizationConfigFile);
            }
            if (!warConfig.contains("peaceTreaty.maxProposalPoints")) {
                warConfig.set("peaceTreaty.maxProposalPoints", 100);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.reparations.pointsPerPercent")) {
                warConfig.set("peaceTreaty.clauses.reparations.pointsPerPercent", 1);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.reparations.maxPercent")) {
                warConfig.set("peaceTreaty.clauses.reparations.maxPercent", 20);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.forceConversionPoints")) {
                warConfig.set("peaceTreaty.clauses.forceConversionPoints", 15);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.transferTownPoints")) {
                warConfig.set("peaceTreaty.clauses.transferTownPoints", 20);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.dissolveAlliancePoints")) {
                warConfig.set("peaceTreaty.clauses.dissolveAlliancePoints", 30);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.expelFromAlliancePoints")) {
                warConfig.set("peaceTreaty.clauses.expelFromAlliancePoints", 15);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.liberateSubjectPoints")) {
                warConfig.set("peaceTreaty.clauses.liberateSubjectPoints", 25);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.unconditionalPassagePoints")) {
                warConfig.set("peaceTreaty.clauses.unconditionalPassagePoints", 10);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.humiliatePoints")) {
                warConfig.set("peaceTreaty.clauses.humiliatePoints", 5);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.lootCapitalPoints")) {
                warConfig.set("peaceTreaty.clauses.lootCapitalPoints", 20);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.tributePoints")) {
                warConfig.set("peaceTreaty.clauses.tributePoints", 25);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.allowIndependencePoints")) {
                warConfig.set("peaceTreaty.clauses.allowIndependencePoints", 40);
                warConfigChanged = true;
            }
            if (!warConfig.contains("peaceTreaty.clauses.forceVassalizePoints")) {
                warConfig.set("peaceTreaty.clauses.forceVassalizePoints", 60);
                warConfigChanged = true;
            }
            for (var entry : java.util.Map.<String, Object>of(
                    "reasons.reconquest.minimumDays", 7,
                    "reasons.reconquest.clock", "REAL_TIME",
                    "reasons.reconquest.world", "world").entrySet()) {
                if (!warConfig.contains(entry.getKey())) {
                    warConfig.set(entry.getKey(), entry.getValue());
                    warConfigChanged = true;
                }
            }
            for (var entry : java.util.Map.<String, Object>of(
                    "warGoals.controlThresholdPercent", 75.0D,
                    "warGoals.holdSeconds", 1800L,
                    "warGoals.progressCheckSeconds", 5L,
                    "warGoals.dominancePopulationMultiplier", 1.0D,
                    "warGoals.defenderIndependenceScoreDifference", 40.0D,
                    "warGoals.clauses.forceDemocracyPoints", 60,
                    "warGoals.clauses.dissolveThreatOrganizationPoints", 70,
                    "warGoals.regimeLock.durationDays", 100L,
                    "warGoals.regimeLock.clock", "REAL_TIME",
                    "warGoals.regimeLock.world", "world").entrySet()) {
                if (!warConfig.contains(entry.getKey())) {
                    warConfig.set(entry.getKey(), entry.getValue());
                    warConfigChanged = true;
                }
            }
            if (warConfig.contains("peaceTreaty.tribute.taxRatePercent")) {
                warConfig.set("peaceTreaty.tribute.taxRatePercent", null);
                warConfigChanged = true;
            }
            if (!warConfig.contains("scoring.killPoints")) {
                warConfig.set("scoring.killPoints", warConfig.get("scoring.military.killPoints", 1.0D));
                warConfigChanged = true;
            }
            if (warConfig.contains("scoring.military")) {
                warConfig.set("scoring.military", null);
                warConfigChanged = true;
            }
            if (!warConfig.contains("scoring.cycleSeconds")) {
                warConfig.set("scoring.cycleSeconds", organizationConfig.getLong("sovereignEmpire.independenceWar.cycleSeconds", 300L));
                warConfigChanged = true;
            }
            if (!warConfig.contains("scoring.defenderRespawnLockSeconds")) {
                warConfig.set("scoring.defenderRespawnLockSeconds",
                        organizationConfig.getInt("sovereignEmpire.independenceWar.respawnLockSeconds", 30));
                warConfigChanged = true;
            }
            for (String old : List.of("cycleSeconds", "rebelKillPoints", "empireKillPoints",
                    "rebelCyclePoints", "empireCyclePoints", "respawnLockSeconds")) {
                String path = "sovereignEmpire.independenceWar." + old;
                if (organizationConfig.contains(path)) {
                    organizationConfig.set(path, null);
                    files.save(organizationConfig, organizationConfigFile);
                }
            }
            if (organizationConfig.contains("sovereignEmpire.independenceWar")) {
                organizationConfig.set("sovereignEmpire.independenceWar", null);
                files.save(organizationConfig, organizationConfigFile);
            }
            if (!warConfig.contains("truce.baseHours")) {
                warConfig.set("truce.baseHours", 24L);
                warConfigChanged = true;
            }
            if (!warConfig.contains("truce.maxAdditionalHours")) {
                warConfig.set("truce.maxAdditionalHours", 168L);
                warConfigChanged = true;
            }
            Map<String, Object> seasonsDefaults = Map.of(
                    "enabled", false, "world", "world", "baseDays", 1L, "maxAdditionalDays", 7L);
            for (var entry : seasonsDefaults.entrySet()) {
                String path = "truce.realisticSeasons." + entry.getKey();
                if (!warConfig.contains(path)) {
                    warConfig.set(path, entry.getValue());
                    warConfigChanged = true;
                }
            }
            if (warConfigChanged) {
                files.save(warConfig, warConfigFile);
            }
        }
        if (townConfig != null && !townConfig.contains("allowRegularWarParticipation")) {
            townConfig.set("allowRegularWarParticipation", false);
            files.save(townConfig, townConfigFile);
        }
        boolean historyChanged = false;
        for (var entry : Map.<String, Object>of("history.enabled", false, "history.clock", "REAL_TIME",
                "history.world", "world").entrySet()) {
            if (!nationConfig.contains(entry.getKey())) {
                nationConfig.set(entry.getKey(), entry.getValue());
                historyChanged = true;
            }
        }
        if (historyChanged) files.save(nationConfig, nationConfigFile);
        boolean lettersChanged = false;
        for (var entry : Map.<String, Object>of("diplomacy.letters.cost.amount", "100.00",
                "diplomacy.letters.cost.currency", "default").entrySet()) {
            if (!getConfig().contains(entry.getKey())) {
                getConfig().set(entry.getKey(), entry.getValue());
                lettersChanged = true;
            }
        }
        if (lettersChanged) saveConfig();
        String userLang = getConfig().getString("locale", "zh_cn");
        return new RuntimeFlags(framework, govEnabled, orgEnabled, armyEnabled, religionEnabled, nationOrgLimit, userLang);
    }

    void migrateLegacyDataFolderIfNeeded() {
        files.migrateLegacyDataFolderIfNeeded();
    }

    String buildStorageSignature() {
        return String.join("|",
                getConfig().getString("storage.type", "sqlite"),
                getConfig().getString("storage.sqlite.file", "data/worlddynamics-engine.db"),
                getConfig().getString("storage.mysql.host", "127.0.0.1"),
                String.valueOf(getConfig().getInt("storage.mysql.port", 3306)),
                getConfig().getString("storage.mysql.database", "worlddynamics_engine"),
                getConfig().getString("storage.mysql.username", "root"),
                getConfig().getString("storage.mysql.parameters", "useSSL=false&characterEncoding=utf8&serverTimezone=UTC"));
    }

    private void loadModuleConfigs() {
        nationConfigFile = files.resolve("nation.yml");
        townConfigFile = files.resolve("town.yml");
        organizationConfigFile = files.resolve("organization.yml");
        warConfigFile = files.resolve("war.yml");
        ideologyConfigFile = files.resolve("ideology.yml");
        backgroundConfigFile = files.resolve("backgrounds.yml");
        dropsConfigFile = files.resolve("drops.yml");
        nationConfig = files.load(nationConfigFile);
        townConfig = files.load(townConfigFile);
        ensureRallyDefaults(nationConfig, nationConfigFile, "250.00");
        ensureRallyDefaults(townConfig, townConfigFile, "100.00");
        ensureTownHistoryDefaults();
        organizationConfig = files.load(organizationConfigFile);
        warConfig = files.load(warConfigFile);
        ideologyConfig = files.load(ideologyConfigFile);
        backgroundConfig = files.load(backgroundConfigFile);
        dropsConfig = files.load(dropsConfigFile);
    }

    void saveModuleConfigs() {
        files.save(nationConfig, nationConfigFile);
        files.save(townConfig, townConfigFile);
        files.save(organizationConfig, organizationConfigFile);
        files.save(warConfig, warConfigFile);
        files.save(ideologyConfig, ideologyConfigFile);
        files.save(backgroundConfig, backgroundConfigFile);
        files.save(dropsConfig, dropsConfigFile);
    }

    private void ensureRallyDefaults(FileConfiguration config, File file, String cost) {
        boolean changed = false;
        if (!config.contains("rally.enabled")) {
            config.set("rally.enabled", true);
            changed = true;
        }
        if (!config.contains("rally.cost")) {
            config.set("rally.cost", cost);
            changed = true;
        }
        if (changed) files.save(config, file);
    }

    private void ensureTownHistoryDefaults() {
        boolean changed = false;
        if (!townConfig.contains("history.enabled")) {
            townConfig.set("history.enabled", false);
            changed = true;
        }
        if (!townConfig.contains("history.clock")) {
            townConfig.set("history.clock", "REAL_TIME");
            changed = true;
        }
        if (!townConfig.contains("history.world")) {
            townConfig.set("history.world", "world");
            changed = true;
        }
        if (changed) files.save(townConfig, townConfigFile);
    }

    private Map<String, BigDecimal> loadExchangeRates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        String configuredDefaultCurrency = getConfig().getString("economy.defaultCurrency", "default");

        if (getConfig().isConfigurationSection("economy.exchangeRates")) {
            for (String key : getConfig().getConfigurationSection("economy.exchangeRates").getKeys(false)) {
                BigDecimal value = parseBigDecimal(String.valueOf(getConfig().get("economy.exchangeRates." + key, "1.0")), BigDecimal.ONE);
                if (value.compareTo(BigDecimal.ZERO) > 0) {
                    rates.put(key, value);
                }
            }
        }

        if (rates.isEmpty()) {
            rates.put(configuredDefaultCurrency, BigDecimal.ONE);
        } else {
            rates.putIfAbsent(configuredDefaultCurrency, BigDecimal.ONE);
        }
        return rates;
    }

    private String resolveDefaultEconomyCurrency() {
        String configuredDefaultCurrency = getConfig().getString("economy.defaultCurrency", "default");
        if (currencyExchangeRates.isEmpty()) {
            return configuredDefaultCurrency;
        }

        String firstConfiguredCurrency = currencyExchangeRates.keySet().iterator().next();
        if (!firstConfiguredCurrency.equals(configuredDefaultCurrency)) {
            getLogger().warning("economy.defaultCurrency (" + configuredDefaultCurrency + ") does not match the first economy.exchangeRates entry (" + firstConfiguredCurrency + "). Due to Towny/Vault compatibility, the first exchangeRates entry will be used as the runtime default currency.");
        }
        return firstConfiguredCurrency;
    }

    private EconomyBridge.Charge readCharge(FileConfiguration configuration, String sectionPath, String legacyAmountPath, String fallbackCurrency, String fallbackAmount) {
        if (configuration == null) {
            return new EconomyBridge.Charge(parseBigDecimal(fallbackAmount, BigDecimal.ZERO), fallbackCurrency);
        }

        String amountValue = configuration.isSet(sectionPath + ".amount")
                ? String.valueOf(configuration.get(sectionPath + ".amount"))
                : String.valueOf(configuration.get(legacyAmountPath, fallbackAmount));
        String currencyValue = configuration.getString(sectionPath + ".currency", getConfig().getString("economy.defaultCurrency", fallbackCurrency));
        return new EconomyBridge.Charge(parseBigDecimal(amountValue, parseBigDecimal(fallbackAmount, BigDecimal.ZERO)), currencyValue);
    }

    private BigDecimal parseBigDecimal(String rawValue, BigDecimal fallback) {
        try {
            return new BigDecimal(rawValue);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    void validateConfiguredChargeCurrencies(EconomyBridge economyBridge) {
        if (economyBridge == null || !economyBridge.supportsMultiCurrency()) {
            return;
        }

        validateChargeCurrency(economyBridge, "economy.charges.diplomacyVisitNeutralNone.currency", diplomacyVisitCostNeutralNoneCharge);
        validateChargeCurrency(economyBridge, "economy.charges.diplomacyVisitFriendly.currency", diplomacyVisitCostFriendlyCharge);
        validateChargeCurrency(economyBridge, "economy.charges.diplomacyFormTrade.currency", diplomacyFormCostTradeCharge);
        validateChargeCurrency(economyBridge, "ideology.economy.charges.religionCreation.currency", religionCreationCostCharge);
        validateChargeCurrency(economyBridge, "ideology.economy.charges.religionPlayerConversion.currency", religionPlayerConversionCostCharge);
        validateChargeCurrency(economyBridge, "ideology.economy.charges.religionTownConversion.currency", religionTownConversionCostCharge);
        validateChargeCurrency(economyBridge, "ideology.economy.charges.religionNationConversion.currency", religionNationConversionCostCharge);
        validateChargeCurrency(economyBridge, "ideology.economy.charges.religionForcedPlayerConversion.currency", religionForcedPlayerConversionCostCharge);
        validateChargeCurrency(economyBridge, "ideology.economy.charges.religionForcedTownConversion.currency", religionForcedTownConversionCostCharge);
        validateChargeCurrency(economyBridge, "organization.economy.charges.orgFormation.currency", orgFormationCostCharge);
        validateChargeCurrency(economyBridge, "organization.economy.charges.orgMaintenance.currency", orgMaintenanceCostCharge);
    }

    private void validateChargeCurrency(EconomyBridge economyBridge, String configPath, EconomyBridge.Charge charge) {
        if (charge == null || charge.currency() == null || charge.currency().isBlank()) {
            return;
        }
        if (!economyBridge.hasCurrency(charge.currency())) {
            getLogger().warning("Configured currency id not found for " + configPath + ": " + charge.currency());
        }
    }

    private void migrateLegacyConfigIfNeeded() {
        new LegacyConfigMigration(getConfig(), getLogger()).migrate(nationConfig, townConfig, organizationConfig, ideologyConfig);

        ensureStorageDefaults();
        ensureIdeologyDefaults();
        ensureBackgroundDefaults();
        ensureDropDefaults();

        saveModuleConfigs();
        saveConfig();
    }

    private void ensureDropDefaults() {
        if (dropsConfig == null) {
            return;
        }
        if (!dropsConfig.contains("playerDrops.enabled")) {
            dropsConfig.set("playerDrops.enabled", false);
        }
        if (!dropsConfig.contains("playerDrops.rolls")) {
            dropsConfig.set("playerDrops.rolls", 3);
        }
        if (!dropsConfig.isList("playerDrops.pool")) {
            Map<String, Object> defaultEntry = new LinkedHashMap<>();
            defaultEntry.put("type", "vanilla");
            defaultEntry.put("material", "DIAMOND");
            defaultEntry.put("amount", 1);
            defaultEntry.put("dropChance", 0.10D);
            dropsConfig.set("playerDrops.pool", List.of(defaultEntry));
        }
    }

    private void ensureStorageDefaults() {
        boolean changed = false;

        if (!getConfig().isString("storage.type")) {
            getConfig().set("storage.type", "sqlite");
            changed = true;
        }
        if (!getConfig().isString("storage.sqlite.file")) {
            getConfig().set("storage.sqlite.file", "data/worlddynamics-engine.db");
            changed = true;
        }
        if (!getConfig().isString("storage.mysql.host")) {
            getConfig().set("storage.mysql.host", "127.0.0.1");
            changed = true;
        }
        if (!getConfig().contains("storage.mysql.port")) {
            getConfig().set("storage.mysql.port", 3306);
            changed = true;
        }
        if (!getConfig().isString("storage.mysql.database")) {
            getConfig().set("storage.mysql.database", "worlddynamics_engine");
            changed = true;
        }
        if (!getConfig().isString("storage.mysql.username")) {
            getConfig().set("storage.mysql.username", "root");
            changed = true;
        }
        if (!getConfig().contains("storage.mysql.password")) {
            getConfig().set("storage.mysql.password", "");
            changed = true;
        }
        if (!getConfig().isString("storage.mysql.parameters")) {
            getConfig().set("storage.mysql.parameters", "useSSL=false&characterEncoding=utf8&serverTimezone=UTC");
            changed = true;
        }
        if (!getConfig().contains("economy.townyResidentTaxOverride.enabled")) {
            getConfig().set("economy.townyResidentTaxOverride.enabled", true);
            changed = true;
        }
        if (!getConfig().contains("economy.townyResidentTaxOverride.logPartialCollection")) {
            getConfig().set("economy.townyResidentTaxOverride.logPartialCollection", true);
            changed = true;
        }
        if (!getConfig().contains("economy.playerGifts.enabled")) {
            getConfig().set("economy.playerGifts.enabled", true);
            changed = true;
        }
        if (!getConfig().contains("economy.playerGifts.requestCooldownMinutes")) {
            getConfig().set("economy.playerGifts.requestCooldownMinutes", 10);
            changed = true;
        }
        if (!getConfig().contains("economy.playerGifts.requestExpiryMinutes")) {
            getConfig().set("economy.playerGifts.requestExpiryMinutes", 5);
            changed = true;
        }
        if (getConfig().getInt("config-version", 0) < 9) {
            getConfig().set("config-version", 9);
            changed = true;
        }

        if (changed) {
            saveConfig();
        }
    }

    private void ensureIdeologyDefaults() {
        var repair = new CultureConfigRepair(ideologyConfig, getConfig().getString("economy.defaultCurrency", "default"));
        if (repair.apply()) files.save(ideologyConfig, ideologyConfigFile);
    }

    private void ensureBackgroundDefaults() {
        if (backgroundConfig == null) {
            return;
        }

        boolean changed = false;
        if (!backgroundConfig.contains("enabled")) {
            backgroundConfig.set("enabled", false);
            changed = true;
        }
        if (!backgroundConfig.contains("forceSelectionOnFirstJoin")) {
            backgroundConfig.set("forceSelectionOnFirstJoin", false);
            changed = true;
        }
        changed |= ensureBackgroundStageDefaults("childhood", List.of(
                createBackgroundDefault("farm_child", "农家孩子",
                        List.of("%player_name%从小在田埂与作物之间长大。", "对耕作与土地的熟悉早已成为本能。"),
                        Map.of("auraskills/farming", 1)),
                createBackgroundDefault("mine_child", "矿镇少年",
                        List.of("%player_name%在矿道边缘长大，对石头、矿脉和洞穴毫不陌生。", "儿时的生活让%player_name%更早接触了采掘。"),
                        Map.of("auraskills/mining", 1)),
                createBackgroundDefault("river_child", "河湾渔童",
                        List.of("%player_name%在河湾边长大，熟悉潮汐、水流与鱼群。", "抛竿与收线是%player_name%童年的日常。"),
                        Map.of("auraskills/fishing", 1))
        ));
        changed |= ensureBackgroundStageDefaults("adulthood", List.of(
                createBackgroundDefault("frontier_farmer", "边境垦殖者",
                        List.of("成年后，%player_name%选择在边境开垦荒地。", "恶劣环境锤炼了%player_name%的劳作技巧。"),
                        Map.of("auraskills/farming", 1, "auraskills/foraging", 1)),
                createBackgroundDefault("militia_veteran", "民兵老兵",
                        List.of("%player_name%曾在地方民兵中服役，熟悉战斗与纪律。", "长期训练让%player_name%比常人更善于应对冲突。"),
                        Map.of("auraskills/fighting", 1, "auraskills/defense", 1)),
                createBackgroundDefault("guild_craftsman", "行会匠人",
                        List.of("%player_name%成年后进入行会，从学徒一步步成长。", "对材料与工艺的把握让%player_name%在制作上更有天赋。"),
                        Map.of("auraskills/excavation", 1, "auraskills/alchemy", 1))
        ));

        if (changed) {
            files.save(backgroundConfig, backgroundConfigFile);
        }
    }

    private boolean ensureBackgroundStageDefaults(String stageKey, List<BackgroundDefaultDefinition> defaults) {
        boolean changed = false;
        String backgroundsPath = "stages." + stageKey + ".backgrounds";
        if (!backgroundConfig.isConfigurationSection(backgroundsPath)) {
            backgroundConfig.createSection(backgroundsPath);
            changed = true;
        }

        for (BackgroundDefaultDefinition definition : defaults) {
            String basePath = backgroundsPath + "." + definition.id();
            if (!backgroundConfig.contains(basePath + ".title")) {
                backgroundConfig.set(basePath + ".title", definition.title());
                changed = true;
            }
            if (!backgroundConfig.contains(basePath + ".lore")) {
                backgroundConfig.set(basePath + ".lore", definition.lore());
                changed = true;
            }
            for (Map.Entry<String, Integer> entry : definition.skillLevelBonuses().entrySet()) {
                String bonusPath = basePath + ".skillLevelBonuses." + entry.getKey();
                if (!backgroundConfig.contains(bonusPath)) {
                    backgroundConfig.set(bonusPath, entry.getValue());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private BackgroundDefaultDefinition createBackgroundDefault(String id, String title, List<String> lore, Map<String, Integer> bonuses) {
        return new BackgroundDefaultDefinition(id, title, lore, bonuses);
    }


    /**
     * 封装内置背景默认配置，供首次生成和缺失字段补全使用。
     */
    private record BackgroundDefaultDefinition(String id,
                                               String title,
                                               List<String> lore,
                                               Map<String, Integer> skillLevelBonuses) {
    }
    FileConfiguration getTownConfig() {
        return townConfig;
    }

    FileConfiguration getNationConfig() {
        return nationConfig;
    }

    FileConfiguration getOrganizationConfig() {
        return organizationConfig;
    }

    FileConfiguration getWarConfig() {
        return warConfig;
    }

    FileConfiguration getIdeologyConfig() {
        return ideologyConfig;
    }

    boolean reloadMoodExpectationSettingsFromDisk() {
        if (ideologyConfig == null || ideologyConfigFile == null || !ideologyConfigFile.isFile()) {
            return false;
        }

        FileConfiguration diskConfig;
        try {
            diskConfig = files.loadStrict(ideologyConfigFile);
        } catch (Exception exception) {
            getLogger().warning("Could not hot-reload mood expectations from ideology.yml: "
                    + exception.getMessage());
            return false;
        }
        boolean changed = false;
        Map<String, Object> defaults = Map.of(
                "mood.expectations.newPlayer.stepHours", 2,
                "mood.expectations.newPlayer.moodBonuses", List.of(30, 24, 18, 12, 6, 0),
                "mood.expectations.roles.townMayor", -8,
                "mood.expectations.roles.nationKing", -12);
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String path = entry.getKey();
            Object value = diskConfig.contains(path) ? diskConfig.get(path) : entry.getValue();
            if (!java.util.Objects.equals(ideologyConfig.get(path), value)) {
                ideologyConfig.set(path, value);
                changed = true;
            }
        }
        return changed;
    }

    FileConfiguration getBackgroundConfig() {
        return backgroundConfig;
    }

    FileConfiguration getDropsConfig() {
        return dropsConfig;
    }

    boolean isRegularTownWarParticipationAllowed() {
        return townConfig != null && townConfig.getBoolean("allowRegularWarParticipation", false);
    }

    String getDefaultEconomyCurrency() {
        return defaultEconomyCurrency;
    }

    Map<String, BigDecimal> getCurrencyExchangeRates() {
        return java.util.Collections.unmodifiableMap(currencyExchangeRates);
    }

    EconomyBridge.Charge getDiplomacyVisitCostNeutralNoneCharge() {
        return diplomacyVisitCostNeutralNoneCharge;
    }

    EconomyBridge.Charge getDiplomacyVisitCostFriendlyCharge() {
        return diplomacyVisitCostFriendlyCharge;
    }

    EconomyBridge.Charge getDiplomacyFormCostTradeCharge() {
        return diplomacyFormCostTradeCharge;
    }

    EconomyBridge.Charge getOrgFormationCostCharge() {
        return orgFormationCostCharge;
    }

    EconomyBridge.Charge getOrgMaintenanceCostCharge() {
        return orgMaintenanceCostCharge;
    }

    long getOrganizationMaintenanceIntervalHours() {
        return organizationMaintenanceIntervalHours;
    }

    EconomyBridge.Charge getReligionCreationCostCharge() {
        return religionCreationCostCharge;
    }

    EconomyBridge.Charge getReligionPlayerConversionCostCharge() {
        return religionPlayerConversionCostCharge;
    }

    EconomyBridge.Charge getReligionTownConversionCostCharge() {
        return religionTownConversionCostCharge;
    }

    EconomyBridge.Charge getReligionNationConversionCostCharge() {
        return religionNationConversionCostCharge;
    }

    EconomyBridge.Charge getReligionForcedPlayerConversionCostCharge() {
        return religionForcedPlayerConversionCostCharge;
    }

    EconomyBridge.Charge getReligionForcedTownConversionCostCharge() {
        return religionForcedTownConversionCostCharge;
    }

    List<String> getValidGovernmentTypes() {
        return nationConfig != null ? nationConfig.getStringList("validGovernmentTypes") : java.util.Collections.emptyList();
    }

    List<String> getValidTownGovernmentTypes() {
        return townConfig != null ? townConfig.getStringList("validTownGovernmentTypes") : java.util.Collections.emptyList();
    }

    List<String> getValidOrganizationTypes() {
        return organizationConfig != null ? organizationConfig.getStringList("validOrganizationTypes") : java.util.Collections.emptyList();
    }

    int getDefaultTownElectionIntervalDays() {
        return townConfig != null ? Math.max(1, townConfig.getInt("townElectionIntervalDays", 7)) : 7;
    }

    int getTownElectionDurationDays() {
        return townConfig != null ? Math.max(1, townConfig.getInt("townElectionDurationDays", 2)) : 2;
    }

}
