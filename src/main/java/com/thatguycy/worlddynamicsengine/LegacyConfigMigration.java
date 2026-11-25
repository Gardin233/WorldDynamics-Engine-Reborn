package com.thatguycy.worlddynamicsengine;

import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/** Moves legacy root keys into their current module configurations. */
final class LegacyConfigMigration {
    private final FileConfiguration root;
    private final Logger logger;

    LegacyConfigMigration(FileConfiguration root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    void migrate(FileConfiguration nationConfig, FileConfiguration townConfig, FileConfiguration organizationConfig, FileConfiguration ideologyConfig) {
        boolean hasLegacyKeys = root.contains("validGovernmentTypes")
                || root.contains("validTownGovernmentTypes")
                || root.contains("townElectionIntervalDays")
                || root.contains("townElectionDurationDays")
                || root.contains("validOrganizationTypes")
                || root.contains("orgFormationCost")
                || root.contains("residentOrgLimit")
                || root.contains("nationOrgLimit");
        boolean hasLegacyIdeologyKeys = root.contains("religionEnabled")
                || root.contains("religionCreationCost")
                || root.contains("religionPlayerConversionCost")
                || root.contains("religionTownConversionCost")
                || root.contains("religionNationConversionCost")
                || root.contains("religionForcedPlayerConversionCost")
                || root.contains("religionForcedTownConversionCost")
                || root.isConfigurationSection("economy.charges.religionCreation")
                || root.isConfigurationSection("economy.charges.religionPlayerConversion")
                || root.isConfigurationSection("economy.charges.religionTownConversion")
                || root.isConfigurationSection("economy.charges.religionNationConversion")
                || root.isConfigurationSection("economy.charges.religionForcedPlayerConversion")
                || root.isConfigurationSection("economy.charges.religionForcedTownConversion");
        boolean hasLegacyEconomyCostKeys = root.contains("diplomacyVisitCostNeutralNone")
                || root.contains("diplomacyVisitCostFriendly")
                || root.contains("diplomacyFormCostTrade")
                || (organizationConfig != null && organizationConfig.contains("orgFormationCost"));

        if (!root.getBoolean("moduleConfigMigrated", false) && hasLegacyKeys) {
            migrateLegacyValue("validGovernmentTypes", root, nationConfig);
            migrateLegacyValue("validTownGovernmentTypes", root, townConfig);
            migrateLegacyValue("townElectionIntervalDays", root, townConfig);
            migrateLegacyValue("townElectionDurationDays", root, townConfig);
            migrateLegacyValue("townConscriptLimitRatio", root, townConfig);
            migrateLegacyValue("validOrganizationTypes", root, organizationConfig);
            migrateLegacyValue("orgFormationCost", root, organizationConfig);
            migrateLegacyValue("nationOrgLimit", root, organizationConfig);
            if (root.contains("residentOrgLimit") && organizationConfig != null && !organizationConfig.contains("nationOrgLimit")) {
                organizationConfig.set("nationOrgLimit", root.getInt("residentOrgLimit", -1));
            }

            root.set("validGovernmentTypes", null);
            root.set("validTownGovernmentTypes", null);
            root.set("townElectionIntervalDays", null);
            root.set("townElectionDurationDays", null);
            root.set("validOrganizationTypes", null);
            root.set("orgFormationCost", null);
            root.set("residentOrgLimit", null);
            root.set("nationOrgLimit", null);
            root.set("moduleConfigMigrated", true);

            logger.info("Migrated legacy config.yml entries into nation.yml, town.yml and organization.yml.");
        }

        if (ideologyConfig != null && hasLegacyIdeologyKeys) {
            migrateLegacyValue("religionEnabled", root, ideologyConfig);
            migrateLegacyCharge(root, "religionCreationCost", "economy.charges.religionCreation", "15000.00", ideologyConfig);
            migrateLegacyCharge(root, "religionPlayerConversionCost", "economy.charges.religionPlayerConversion", "3000.00", ideologyConfig);
            migrateLegacyCharge(root, "religionTownConversionCost", "economy.charges.religionTownConversion", "12000.00", ideologyConfig);
            migrateLegacyCharge(root, "religionNationConversionCost", "economy.charges.religionNationConversion", "30000.00", ideologyConfig);
            migrateLegacyCharge(root, "religionForcedPlayerConversionCost", "economy.charges.religionForcedPlayerConversion", "5000.00", ideologyConfig);
            migrateLegacyCharge(root, "religionForcedTownConversionCost", "economy.charges.religionForcedTownConversion", "20000.00", ideologyConfig);

            copySectionIfMissing(root, ideologyConfig, "economy.charges.religionCreation");
            copySectionIfMissing(root, ideologyConfig, "economy.charges.religionPlayerConversion");
            copySectionIfMissing(root, ideologyConfig, "economy.charges.religionTownConversion");
            copySectionIfMissing(root, ideologyConfig, "economy.charges.religionNationConversion");
            copySectionIfMissing(root, ideologyConfig, "economy.charges.religionForcedPlayerConversion");
            copySectionIfMissing(root, ideologyConfig, "economy.charges.religionForcedTownConversion");

            root.set("religionEnabled", null);
            root.set("religionCreationCost", null);
            root.set("religionPlayerConversionCost", null);
            root.set("religionTownConversionCost", null);
            root.set("religionNationConversionCost", null);
            root.set("religionForcedPlayerConversionCost", null);
            root.set("religionForcedTownConversionCost", null);
            root.set("economy.charges.religionCreation", null);
            root.set("economy.charges.religionPlayerConversion", null);
            root.set("economy.charges.religionTownConversion", null);
            root.set("economy.charges.religionNationConversion", null);
            root.set("economy.charges.religionForcedPlayerConversion", null);
            root.set("economy.charges.religionForcedTownConversion", null);

            logger.info("Migrated ideology settings from config.yml into ideology.yml.");
        }

        if (root.getInt("config-version", 0) < 5 || hasLegacyEconomyCostKeys || !root.isConfigurationSection("economy")) {
            migrateLegacyCharge(root, "diplomacyVisitCostNeutralNone", "economy.charges.diplomacyVisitNeutralNone", "1000.00");
            migrateLegacyCharge(root, "diplomacyVisitCostFriendly", "economy.charges.diplomacyVisitFriendly", "750.00");
            migrateLegacyCharge(root, "diplomacyFormCostTrade", "economy.charges.diplomacyFormTrade", "10000.00");
            if (organizationConfig != null) {
                migrateLegacyCharge(organizationConfig, "orgFormationCost", "economy.charges.orgFormation", "25000.00");
            }

            if (!root.isString("economy.defaultCurrency")) {
                root.set("economy.defaultCurrency", "default");
            }

            String configuredDefaultCurrency = root.getString("economy.defaultCurrency", "default");
            if (!root.isConfigurationSection("economy.exchangeRates")) {
                root.createSection("economy.exchangeRates");
            }
            if (!root.contains("economy.exchangeRates." + configuredDefaultCurrency)) {
                root.set("economy.exchangeRates." + configuredDefaultCurrency, "1.0");
            }

            root.set("diplomacyVisitCostNeutralNone", null);
            root.set("diplomacyVisitCostFriendly", null);
            root.set("diplomacyFormCostTrade", null);
            if (organizationConfig != null) {
                organizationConfig.set("orgFormationCost", null);
            }
        }

        if (root.getInt("config-version", 0) < 5) {
            root.set("config-version", 5);
        }

        if (!root.contains("moduleConfigMigrated")) {
            root.set("moduleConfigMigrated", true);
        }
    }

    private void migrateLegacyValue(String key, FileConfiguration source, FileConfiguration target) {
        if (source.contains(key) && target != null) {
            target.set(key, source.get(key));
        }
    }

    private void migrateLegacyCharge(FileConfiguration configuration, String legacyAmountPath, String targetSectionPath, String fallbackAmount) {
        if (configuration == null) {
            return;
        }

        if (!configuration.isSet(targetSectionPath + ".amount")) {
            configuration.set(targetSectionPath + ".amount", String.valueOf(configuration.get(legacyAmountPath, fallbackAmount)));
        }
        if (!configuration.isSet(targetSectionPath + ".currency")) {
            configuration.set(targetSectionPath + ".currency", root.getString("economy.defaultCurrency", "default"));
        }
    }

    private void migrateLegacyCharge(FileConfiguration source, String legacyAmountPath, String targetSectionPath, String fallbackAmount, FileConfiguration target) {
        if (target == null) {
            return;
        }

        if (!target.isSet(targetSectionPath + ".amount")) {
            String amountValue = fallbackAmount;
            if (source != null) {
                if (source.isSet(targetSectionPath + ".amount")) {
                    amountValue = String.valueOf(source.get(targetSectionPath + ".amount"));
                } else if (source.isSet(legacyAmountPath)) {
                    amountValue = String.valueOf(source.get(legacyAmountPath));
                }
            }
            target.set(targetSectionPath + ".amount", amountValue);
        }
        if (!target.isSet(targetSectionPath + ".currency")) {
            String currencyValue = root.getString("economy.defaultCurrency", "default");
            if (source != null && source.isSet(targetSectionPath + ".currency")) {
                currencyValue = String.valueOf(source.get(targetSectionPath + ".currency"));
            }
            target.set(targetSectionPath + ".currency", currencyValue);
        }
    }

    private void copySectionIfMissing(FileConfiguration source, FileConfiguration target, String path) {
        if (source == null || target == null || !source.isConfigurationSection(path) || target.isConfigurationSection(path)) {
            return;
        }

        for (String key : source.getConfigurationSection(path).getKeys(true)) {
            target.set(path + "." + key, source.get(path + "." + key));
        }
    }
}
