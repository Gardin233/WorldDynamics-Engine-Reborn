package com.thatguycy.worlddynamicsengine;

import com.thatguycy.worlddynamicsengine.command.background.BackgroundCommand;
import com.thatguycy.worlddynamicsengine.command.CommandHandler;
import com.thatguycy.worlddynamicsengine.command.diplomacy.DiplomacyCommand;
import com.thatguycy.worlddynamicsengine.command.governance.NationCommand;
import com.thatguycy.worlddynamicsengine.command.governance.TownCommand;
import com.thatguycy.worlddynamicsengine.command.ideology.ReligionCommand;
import com.thatguycy.worlddynamicsengine.command.inn.InnCommand;
import com.thatguycy.worlddynamicsengine.command.MyTabCompleter;
import com.thatguycy.worlddynamicsengine.command.organization.OrgCommand;
import com.thatguycy.worlddynamicsengine.command.system.DebugCommand;
import com.thatguycy.worlddynamicsengine.command.system.DocCommand;
import com.thatguycy.worlddynamicsengine.command.system.HelpCommand;
import com.thatguycy.worlddynamicsengine.command.system.ReloadCommand;
import com.thatguycy.worlddynamicsengine.integration.craftengine.CraftEngineIntegration;
import com.thatguycy.worlddynamicsengine.listener.background.PlayerBackgroundListener;
import com.thatguycy.worlddynamicsengine.listener.drop.PlayerDropListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.IdeologyTreasuryListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.ArmorDoctrineListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.AnimalKillingDoctrineListener;
import com.thatguycy.worlddynamicsengine.listener.inn.InnListener;
import com.thatguycy.worlddynamicsengine.listener.towny.NeutralityListener;
import com.thatguycy.worlddynamicsengine.listener.towny.TownyEventListener;
import com.thatguycy.worlddynamicsengine.listener.towny.TownyLifecycleListener;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.manager.drop.PlayerDropManager;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyTreasuryManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.AnimalKillingDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.manager.inn.InnManager;
import com.thatguycy.worlddynamicsengine.manager.menu.PlayerMenuManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.manager.war.WarTruceClock;
import com.thatguycy.worlddynamicsengine.manager.war.WarScoreboardManager;
import com.thatguycy.worlddynamicsengine.model.InnBed;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineBuffManager;
import com.thatguycy.worlddynamicsengine.listener.ideology.DoctrineBuffListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 插件生命周期入口，协调依赖接入、配置迁移、管理器初始化、命令注册和停服保存。
 */
public class WorldDynamicsEngine extends JavaPlugin {
    private static final String PLUGIN_BRAND_EN = "WorldDynamicsEngine:Reborn";
    private static final String PLUGIN_BRAND_ZH = "万邦演变引擎:重生";
    private static final String LEGACY_DATA_FOLDER_NAME = "WorldDynamicsEngine";
    private static final Pattern RELEASE_TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static WorldDynamicsEngine instance;

    private CommandHandler commandHandler;
    private Economy economy;
    private EconomyBridge economyBridge;
    private DataStorageManager dataStorageManager;
    private TownManager townManager;
    private WarTruceClock warTruceClock;
    private NationManager nationManager;
    private ResidentManager residentManager;
    private BackgroundManager backgroundManager;
    private PlayerMenuManager playerMenuManager;
    private OrgManager orgManager;
    private ReligionManager religionManager;
    private IdeologyTreasuryManager ideologyTreasuryManager;
    private CraftEngineIntegration craftEngineIntegration;
    private PlayerDropManager playerDropManager;
    private com.thatguycy.worlddynamicsengine.manager.ideology.FoodDoctrineManager foodDoctrineManager;
    private AnimalKillingDoctrineManager animalKillingDoctrineManager;
    private DoctrineBuffManager doctrineBuffManager;
    private ArmorDoctrineListener armorDoctrineListener;
    private WarScoreboardManager warScoreboardManager;
    private InnManager innManager;
    private com.thatguycy.worlddynamicsengine.manager.history.NationHistoryManager nationHistoryManager;

    public String framework;
    public boolean govEnabled;
    public boolean orgEnabled;
    public boolean armyEnabled;
    public boolean religionEnabled;
    public int nationOrgLimit;
    public String userLang;
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
    private final Map<String, FileConfiguration> locales = new HashMap<>();
    private final Map<String, FileConfiguration> bundledLocales = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        migrateLegacyDataFolderIfNeeded();
        registerSerializers();
        ensureResources();
        reloadConfig();
        loadModuleConfigs();
        migrateLegacyConfigIfNeeded();
        loadLocales();
        readConfigSettings();
        initializeDataStorage();

        if (!checkDependencies()) {
            getLogger().severe("Missing Towny or a Vault/VaultUnlocked economy provider. Disabling " + PLUGIN_BRAND_EN + ".");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        craftEngineIntegration = CraftEngineIntegration.load(this);
        if (craftEngineIntegration != null) {
            getLogger().info("CraftEngine API connected (version " + craftEngineIntegration.getVersion() + ").");
        } else {
            getLogger().info("CraftEngine not detected; optional custom item/block integration is inactive.");
        }

        playerDropManager = new PlayerDropManager(this);
        playerDropManager.reloadDefinitions();
        foodDoctrineManager = new com.thatguycy.worlddynamicsengine.manager.ideology.FoodDoctrineManager(this);
        warTruceClock = new WarTruceClock(this);
        townManager = new TownManager(this);
        nationManager = new NationManager(this, townManager);
        residentManager = new ResidentManager(this);
        backgroundManager = new BackgroundManager(this, residentManager);
        orgManager = new OrgManager(this, townManager);
        religionManager = new ReligionManager(this);
        doctrineBuffManager = new DoctrineBuffManager(this);
        animalKillingDoctrineManager = new AnimalKillingDoctrineManager(this);
        warScoreboardManager = new WarScoreboardManager(this);
        ideologyTreasuryManager = new IdeologyTreasuryManager(this);
        innManager = new InnManager(this);
        playerMenuManager = new PlayerMenuManager(this);

        loadTownyDataOnStartup();
        nationHistoryManager = new com.thatguycy.worlddynamicsengine.manager.history.NationHistoryManager(this);
        nationHistoryManager.initialize();
        registerCommands();
        registerListeners();
        startBackgroundTasks();
        logStartupBanner();

        new Metrics(this, 20763);
    }

    @Override
    public void onDisable() {
        if (doctrineBuffManager != null) doctrineBuffManager.clearAll();
        if (warScoreboardManager != null) warScoreboardManager.clearAll();
        if (playerMenuManager != null) playerMenuManager.closeMenus();
        if (innManager != null) innManager.refundPending();
        saveConfig();
        saveModuleConfigs();

        if (townManager == null || nationManager == null || residentManager == null || orgManager == null || religionManager == null) {
            return;
        }

        try {
            if (isTownyAvailable()) {
                residentManager.syncWithTowny();
            }
        } catch (Exception e) {
            getLogger().warning("Failed to sync with Towny during shutdown: " + e.getMessage());
        }

        townManager.saveTowns();
        nationManager.saveNations();
        residentManager.saveResidents();
        orgManager.saveOrganizations();
        religionManager.saveReligions();
    }

    public static WorldDynamicsEngine getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public EconomyBridge getEconomyBridge() {
        return economyBridge;
    }

    public TownManager getTownManager() {
        return townManager;
    }

    public NationManager getNationManager() {
        return nationManager;
    }

    public com.thatguycy.worlddynamicsengine.manager.history.NationHistoryManager getNationHistoryManager() {
        return nationHistoryManager;
    }

    public ResidentManager getResidentManager() {
        return residentManager;
    }

    public OrgManager getOrgManager() {
        return orgManager;
    }

    public BackgroundManager getBackgroundManager() {
        return backgroundManager;
    }

    public PlayerMenuManager getPlayerMenuManager() {
        return playerMenuManager;
    }

    public ReligionManager getReligionManager() {
        return religionManager;
    }

    public IdeologyTreasuryManager getIdeologyTreasuryManager() {
        return ideologyTreasuryManager;
    }

    public CraftEngineIntegration getCraftEngineIntegration() {
        return craftEngineIntegration;
    }

    public PlayerDropManager getPlayerDropManager() {
        return playerDropManager;
    }

    public DataStorageManager getDataStorageManager() {
        return dataStorageManager;
    }

    public FileConfiguration getTownConfig() {
        return townConfig;
    }

    public FileConfiguration getNationConfig() {
        return nationConfig;
    }

    public FileConfiguration getOrganizationConfig() {
        return organizationConfig;
    }

    public FileConfiguration getWarConfig() {
        return warConfig;
    }

    public WarTruceClock getWarTruceClock() {
        return warTruceClock;
    }

    public FileConfiguration getIdeologyConfig() {
        return ideologyConfig;
    }

    public com.thatguycy.worlddynamicsengine.manager.ideology.FoodDoctrineManager getFoodDoctrineManager() {
        return foodDoctrineManager;
    }

    public DoctrineBuffManager getDoctrineBuffManager() {
        return doctrineBuffManager;
    }

    public WarScoreboardManager getWarScoreboardManager() {
        return warScoreboardManager;
    }

    public AnimalKillingDoctrineManager getAnimalKillingDoctrineManager() {
        return animalKillingDoctrineManager;
    }

    public FileConfiguration getBackgroundConfig() {
        return backgroundConfig;
    }

    public FileConfiguration getDropsConfig() {
        return dropsConfig;
    }

    public boolean isRegularTownWarParticipationAllowed() {
        return townConfig != null && townConfig.getBoolean("allowRegularWarParticipation", false);
    }

    public String getDefaultEconomyCurrency() {
        return defaultEconomyCurrency;
    }

    public Map<String, BigDecimal> getCurrencyExchangeRates() {
        return java.util.Collections.unmodifiableMap(currencyExchangeRates);
    }

    public EconomyBridge.Charge getDiplomacyVisitCostNeutralNoneCharge() {
        return diplomacyVisitCostNeutralNoneCharge;
    }

    public EconomyBridge.Charge getDiplomacyVisitCostFriendlyCharge() {
        return diplomacyVisitCostFriendlyCharge;
    }

    public EconomyBridge.Charge getDiplomacyFormCostTradeCharge() {
        return diplomacyFormCostTradeCharge;
    }

    public EconomyBridge.Charge getOrgFormationCostCharge() {
        return orgFormationCostCharge;
    }

    public EconomyBridge.Charge getOrgMaintenanceCostCharge() {
        return orgMaintenanceCostCharge;
    }

    public long getOrganizationMaintenanceIntervalHours() {
        return organizationMaintenanceIntervalHours;
    }

    public EconomyBridge.Charge getReligionCreationCostCharge() {
        return religionCreationCostCharge;
    }

    public EconomyBridge.Charge getReligionPlayerConversionCostCharge() {
        return religionPlayerConversionCostCharge;
    }

    public EconomyBridge.Charge getReligionTownConversionCostCharge() {
        return religionTownConversionCostCharge;
    }

    public EconomyBridge.Charge getReligionNationConversionCostCharge() {
        return religionNationConversionCostCharge;
    }

    public EconomyBridge.Charge getReligionForcedPlayerConversionCostCharge() {
        return religionForcedPlayerConversionCostCharge;
    }

    public EconomyBridge.Charge getReligionForcedTownConversionCostCharge() {
        return religionForcedTownConversionCostCharge;
    }

    public List<String> getValidGovernmentTypes() {
        return nationConfig != null ? nationConfig.getStringList("validGovernmentTypes") : java.util.Collections.emptyList();
    }

    public List<String> getValidTownGovernmentTypes() {
        return townConfig != null ? townConfig.getStringList("validTownGovernmentTypes") : java.util.Collections.emptyList();
    }

    public List<String> getValidOrganizationTypes() {
        return organizationConfig != null ? organizationConfig.getStringList("validOrganizationTypes") : java.util.Collections.emptyList();
    }

    public int getDefaultTownElectionIntervalDays() {
        return townConfig != null ? Math.max(1, townConfig.getInt("townElectionIntervalDays", 7)) : 7;
    }

    public int getTownElectionDurationDays() {
        return townConfig != null ? Math.max(1, townConfig.getInt("townElectionDurationDays", 2)) : 2;
    }

    private void registerSerializers() {
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.WarLedger.class);
        ConfigurationSerialization.registerClass(InnBed.class, "InnBed");
        ConfigurationSerialization.registerClass(WDEtown.class, "WDEtown");
        ConfigurationSerialization.registerClass(WDEnation.class);
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.DiplomaticLetter.class);
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.NationChronicle.class);
        ConfigurationSerialization.registerClass(WDEresident.class, "WDEresident");
        ConfigurationSerialization.registerClass(WDEorg.class, "WDEorg");
        ConfigurationSerialization.registerClass(WDEreligion.class, "WDEreligion");
    }

    private void ensureResources() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Failed to create plugin data folder.");
        }

        saveDefaultResource("config.yml");
        saveDefaultResource("nation.yml");
        saveDefaultResource("town.yml");
        saveDefaultResource("organization.yml");
        saveDefaultResource("war.yml");
        saveDefaultResource("ideology.yml");
        saveDefaultResource("backgrounds.yml");
        saveDefaultResource("drops.yml");
        saveDefaultResource("foods.yml");
        saveDefaultResource("inn.yml");
        saveDefaultResource("lang/messages_en.yml");
        saveDefaultResource("lang/messages_es.yml");
        saveDefaultResource("lang/messages_zh_cn.yml");
        migrateLegacyLocales();
    }

    private void readConfigSettings() {
        framework = getConfig().getString("framework", "towny");
        govEnabled = getConfig().getBoolean("govEnabled", true);
        orgEnabled = getConfig().getBoolean("orgEnabled", true);
        armyEnabled = getConfig().getBoolean("armyEnabled", true);
        religionEnabled = ideologyConfig != null && ideologyConfig.getBoolean("religionEnabled", true);
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
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.dominion.wealthPercentage")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.dominion.wealthPercentage", 25.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.protectorate.fixed")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.protectorate.fixed", 3000.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.protectorate.wealthPercentage")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.protectorate.wealthPercentage", 15.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.puppet.fixed")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.puppet.fixed", 1500.0D);
                organizationConfigChanged = true;
            }
            if (!organizationConfig.contains("sovereignEmpire.subjectControl.taxCaps.puppet.wealthPercentage")) {
                organizationConfig.set("sovereignEmpire.subjectControl.taxCaps.puppet.wealthPercentage", 8.0D);
                organizationConfigChanged = true;
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
                saveYamlConfiguration(organizationConfig, organizationConfigFile);
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
                saveYamlConfiguration(organizationConfig, organizationConfigFile);
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
            if (!warConfig.contains("peaceTreaty.tribute.taxRatePercent")) {
                warConfig.set("peaceTreaty.tribute.taxRatePercent", 5.0D);
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
                    saveYamlConfiguration(organizationConfig, organizationConfigFile);
                }
            }
            if (organizationConfig.contains("sovereignEmpire.independenceWar")) {
                organizationConfig.set("sovereignEmpire.independenceWar", null);
                saveYamlConfiguration(organizationConfig, organizationConfigFile);
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
                saveYamlConfiguration(warConfig, warConfigFile);
            }
        }
        if (townConfig != null && !townConfig.contains("allowRegularWarParticipation")) {
            townConfig.set("allowRegularWarParticipation", false);
            saveYamlConfiguration(townConfig, townConfigFile);
        }
        boolean historyChanged = false;
        for (var entry : Map.<String, Object>of("history.enabled", false, "history.clock", "REAL_TIME",
                "history.world", "world").entrySet()) {
            if (!nationConfig.contains(entry.getKey())) {
                nationConfig.set(entry.getKey(), entry.getValue());
                historyChanged = true;
            }
        }
        if (historyChanged) saveYamlConfiguration(nationConfig, nationConfigFile);
        boolean lettersChanged = false;
        for (var entry : Map.<String, Object>of("diplomacy.letters.cost.amount", "100.00",
                "diplomacy.letters.cost.currency", "default").entrySet()) {
            if (!getConfig().contains(entry.getKey())) {
                getConfig().set(entry.getKey(), entry.getValue());
                lettersChanged = true;
            }
        }
        if (lettersChanged) saveConfig();
        userLang = getConfig().getString("locale", "zh_cn");
    }

    private void initializeDataStorage() {
        dataStorageManager = new DataStorageManager(this);
        dataStorageManager.initialize();
    }

    private boolean checkDependencies() {
        return isTownyAvailable() && setupEconomy();
    }

    private boolean isTownyAvailable() {
        return getServer().getPluginManager().getPlugin("Towny") != null;
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> serviceProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (serviceProvider == null) {
            return false;
        }

        economy = serviceProvider.getProvider();
        economyBridge = economy != null ? new EconomyBridge(this, economy, defaultEconomyCurrency, currencyExchangeRates) : null;
        if (economyBridge != null) {
            economyBridge.logConfiguredCurrencyStatus();
            validateConfiguredChargeCurrencies();
        }
        return economy != null;
    }

    private void registerCommands() {
        PluginCommand rootCommand = getCommand("wde");
        if (rootCommand == null) {
            throw new IllegalStateException("Command 'wde' is not defined in plugin.yml");
        }

        commandHandler = new CommandHandler(this);
        commandHandler.registerSubCommand("help", new HelpCommand());
        commandHandler.registerSubCommand("admin", new com.thatguycy.worlddynamicsengine.command.admin.AdminCommand(this));
        commandHandler.registerSubCommand("docs", new DocCommand());
        commandHandler.registerSubCommand("debug", new DebugCommand(this));
        commandHandler.registerSubCommand("reload", new ReloadCommand(this));
        commandHandler.registerSubCommand("inn", new InnCommand(this, innManager));
        commandHandler.registerSubCommand("rally", new com.thatguycy.worlddynamicsengine.command.war.RallyCommand(this));
        commandHandler.registerSubCommand("war", new com.thatguycy.worlddynamicsengine.command.war.WarCommand(this));
        commandHandler.registerSubCommand("history", new com.thatguycy.worlddynamicsengine.command.history.HistoryCommand(this));
        commandHandler.registerSubCommand("background", new BackgroundCommand(this, backgroundManager, residentManager));
        commandHandler.registerSubCommand("menu", (sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player player) {
                playerMenuManager.openHome(player);
            } else {
                playerMenuManager.sendPlayerOnly(sender);
            }
            return true;
        });
        commandHandler.registerSubCommand("town", new TownCommand(townManager));
        commandHandler.registerSubCommand("nation", new NationCommand(nationManager, residentManager));
        commandHandler.registerSubCommand("org", new OrgCommand(nationManager, townManager, orgManager, economyBridge));
        commandHandler.registerSubCommand("ideology", new ReligionCommand(nationManager, townManager, residentManager, religionManager, economyBridge));
        commandHandler.registerSubCommand("diplomacy", new DiplomacyCommand(nationManager, residentManager, economyBridge, orgManager));

        rootCommand.setTabCompleter(new MyTabCompleter(orgManager, religionManager));
    }

    private void registerListeners() {
        if (playerMenuManager != null) {
            getServer().getPluginManager().registerEvents(playerMenuManager, this);
        }
        if (innManager != null) {
            getServer().getPluginManager().registerEvents(new InnListener(innManager), this);
        }
        getServer().getPluginManager().registerEvents(new TownyEventListener(this, orgManager, economyBridge), this);
        getServer().getPluginManager().registerEvents(new IdeologyTreasuryListener(this), this);
        getServer().getPluginManager().registerEvents(new DoctrineBuffListener(doctrineBuffManager), this);
        getServer().getPluginManager().registerEvents(
                new AnimalKillingDoctrineListener(animalKillingDoctrineManager), this);
        armorDoctrineListener = new ArmorDoctrineListener(this, residentManager, religionManager, doctrineBuffManager);
        getServer().getPluginManager().registerEvents(armorDoctrineListener, this);
        armorDoctrineListener.initializeOnlinePlayers();
        getServer().getPluginManager().registerEvents(new com.thatguycy.worlddynamicsengine.listener.ideology.FoodDoctrineListener(this), this);
        getServer().getPluginManager().registerEvents(new TownyLifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new NeutralityListener(this), this);
        getServer().getPluginManager().registerEvents(new com.thatguycy.worlddynamicsengine.listener.diplomacy.TradeSanctionListener(this, orgManager), this);
        if (backgroundManager != null) {
            getServer().getPluginManager().registerEvents(new PlayerBackgroundListener(this, backgroundManager, residentManager), this);
        }
        if (playerDropManager != null) {
            getServer().getPluginManager().registerEvents(new PlayerDropListener(playerDropManager), this);
        }
    }

    private void loadTownyDataOnStartup() {
        townManager.importExistingTownyTowns();
        nationManager.importExistingTownyNations();
        getLogger().info("Towny startup data loaded; runtime town and nation records are event-managed.");
    }

    private void startBackgroundTasks() {
        if (doctrineBuffManager != null) doctrineBuffManager.start();
        if (warScoreboardManager != null) warScoreboardManager.start();
        if (innManager != null) innManager.start();
        residentManager.syncWithTowny();
        orgManager.enforceNeutralityOnStartup();
        townManager.enableAutoSave();
        nationManager.enableAutoSave();
        residentManager.enableAutoSave();
        orgManager.enableAutoSave();
        religionManager.enableAutoSave();
        if (backgroundManager != null) {
            getServer().getScheduler().runTask(this, () -> getServer().getOnlinePlayers().forEach(player -> {
                backgroundManager.applyPendingBonuses(player);
                backgroundManager.promptSelectionIfNeeded(player);
            }));
        }
        scheduleIdeologyMemeRegistryReload();
        scheduleIdeologyDoctrineRegistryReload();
    }

    private void logStartupBanner() {
        getLogger().info("Framework: " + framework + ", gov=" + govEnabled + ", org=" + orgEnabled + ", army=" + armyEnabled + ", locale=" + userLang);

        new BukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("================ " + PLUGIN_BRAND_EN + " ================");
                getLogger().info(PLUGIN_BRAND_ZH);
                getLogger().info("Upstream: https://github.com/stoleyourharvs/WorldDynamics-Engine");
                getLogger().info("Version: " + getDescription().getVersion());
                getLogger().info("Latest Release: " + fetchLatestVersion());
                getLogger().info("Crafting Complex Worlds, Shaping Geopolitical Adventures.");
                getLogger().info("===========================================================");
            }
        }.runTaskAsynchronously(this);
    }

    private void migrateLegacyDataFolderIfNeeded() {
        File currentDataFolder = getDataFolder();
        File parentFolder = currentDataFolder.getParentFile();
        if (parentFolder == null) {
            return;
        }

        File legacyDataFolder = new File(parentFolder, LEGACY_DATA_FOLDER_NAME);
        if (!legacyDataFolder.exists() || legacyDataFolder.equals(currentDataFolder) || currentDataFolder.exists()) {
            return;
        }

        try {
            Files.move(legacyDataFolder.toPath(), currentDataFolder.toPath());
            getLogger().info("Migrated legacy plugin data folder from " + legacyDataFolder.getName()
                    + " to " + currentDataFolder.getName() + ".");
        } catch (Exception e) {
            getLogger().warning("Failed to migrate legacy plugin data folder from "
                    + legacyDataFolder.getAbsolutePath() + " to " + currentDataFolder.getAbsolutePath()
                    + ": " + e.getMessage());
        }
    }

    public synchronized ReloadResult reloadPluginState() {
        String storageSignatureBeforeReload = buildStorageSignature();

        if (doctrineBuffManager != null) doctrineBuffManager.clearAll();
        if (warScoreboardManager != null) warScoreboardManager.clearAll();
        if (innManager != null) innManager.refundPending();
        saveRuntimeState();
        reloadConfig();
        loadModuleConfigs();
        migrateLegacyConfigIfNeeded();
        loadLocales();
        readConfigSettings();
        if (warTruceClock != null) warTruceClock.reload();
        if (nationHistoryManager != null) {
            nationHistoryManager.reload();
            nationHistoryManager.initialize();
        }
        if (playerDropManager != null) {
            playerDropManager.reloadDefinitions();
        }
        if (foodDoctrineManager != null) foodDoctrineManager.reload();
        if (animalKillingDoctrineManager != null) animalKillingDoctrineManager.reload();
        if (backgroundManager == null && residentManager != null) {
            backgroundManager = new BackgroundManager(this, residentManager);
        } else if (backgroundManager != null) {
            backgroundManager.reloadDefinitions();
        }

        if (!checkDependencies()) {
            throw new IllegalStateException("Missing Towny or a Vault/VaultUnlocked economy provider after reload.");
        }

        if (innManager != null) {
            innManager.refundPending();
            innManager.reload();
        }
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);

        if (playerMenuManager != null) playerMenuManager.reload();
        registerCommands();
        registerListeners();
        startBackgroundTasks();

        boolean storageChanged = !storageSignatureBeforeReload.equals(buildStorageSignature());
        return new ReloadResult(storageChanged);
    }

    private void saveRuntimeState() {
        if (townManager != null) {
            townManager.saveTowns();
        }
        if (nationManager != null) {
            nationManager.saveNations();
        }
        if (residentManager != null) {
            residentManager.saveResidents();
        }
        if (orgManager != null) {
            orgManager.saveOrganizations();
        }
        if (religionManager != null) {
            religionManager.saveReligions();
        }
    }

    private void scheduleIdeologyMemeRegistryReload() {
        if (religionManager == null) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> religionManager.reloadMemeRegistry());
    }

    private void scheduleIdeologyDoctrineRegistryReload() {
        if (religionManager == null) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> religionManager.reloadDoctrineRegistry());
    }

    private String buildStorageSignature() {
        return String.join("|",
                getConfig().getString("storage.type", "sqlite"),
                getConfig().getString("storage.sqlite.file", "data/worlddynamics-engine.db"),
                getConfig().getString("storage.mysql.host", "127.0.0.1"),
                String.valueOf(getConfig().getInt("storage.mysql.port", 3306)),
                getConfig().getString("storage.mysql.database", "worlddynamics_engine"),
                getConfig().getString("storage.mysql.username", "root"),
                getConfig().getString("storage.mysql.parameters", "useSSL=false&characterEncoding=utf8&serverTimezone=UTC"));
    }

    private String fetchLatestVersion() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.github.com/repos/stoleyourharvs/WorldDynamics-Engine/releases/latest");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", PLUGIN_BRAND_EN);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Unavailable";
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                Matcher matcher = RELEASE_TAG_PATTERN.matcher(response);
                return matcher.find() ? matcher.group(1) : "Unknown";
            }
        } catch (Exception e) {
            getLogger().warning("Failed to fetch the latest version: " + e.getMessage());
            return "Unknown";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void saveDefaultResource(String resourcePath) {
        File targetFile = new File(getDataFolder(), resourcePath);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLogger().warning("Failed to create resource folder: " + parent.getAbsolutePath());
            return;
        }

        if (!targetFile.exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void loadModuleConfigs() {
        nationConfigFile = new File(getDataFolder(), "nation.yml");
        townConfigFile = new File(getDataFolder(), "town.yml");
        organizationConfigFile = new File(getDataFolder(), "organization.yml");
        warConfigFile = new File(getDataFolder(), "war.yml");
        ideologyConfigFile = new File(getDataFolder(), "ideology.yml");
        backgroundConfigFile = new File(getDataFolder(), "backgrounds.yml");
        dropsConfigFile = new File(getDataFolder(), "drops.yml");
        nationConfig = YamlConfiguration.loadConfiguration(nationConfigFile);
        townConfig = YamlConfiguration.loadConfiguration(townConfigFile);
        ensureRallyDefaults(nationConfig, nationConfigFile, "250.00");
        ensureRallyDefaults(townConfig, townConfigFile, "100.00");
        organizationConfig = YamlConfiguration.loadConfiguration(organizationConfigFile);
        warConfig = YamlConfiguration.loadConfiguration(warConfigFile);
        ideologyConfig = YamlConfiguration.loadConfiguration(ideologyConfigFile);
        backgroundConfig = YamlConfiguration.loadConfiguration(backgroundConfigFile);
        dropsConfig = YamlConfiguration.loadConfiguration(dropsConfigFile);
    }

    private void saveModuleConfigs() {
        saveYamlConfiguration(nationConfig, nationConfigFile);
        saveYamlConfiguration(townConfig, townConfigFile);
        saveYamlConfiguration(organizationConfig, organizationConfigFile);
        saveYamlConfiguration(warConfig, warConfigFile);
        saveYamlConfiguration(ideologyConfig, ideologyConfigFile);
        saveYamlConfiguration(backgroundConfig, backgroundConfigFile);
        saveYamlConfiguration(dropsConfig, dropsConfigFile);
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
        if (changed) saveYamlConfiguration(config, file);
    }

    private void saveYamlConfiguration(FileConfiguration configuration, File file) {
        if (configuration == null || file == null) {
            return;
        }

        try {
            configuration.save(file);
        } catch (Exception e) {
            getLogger().warning("Failed to save configuration " + file.getName() + ": " + e.getMessage());
        }
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

    private void validateConfiguredChargeCurrencies() {
        if (economyBridge == null || !economyBridge.supportsMultiCurrency()) {
            return;
        }

        validateChargeCurrency("economy.charges.diplomacyVisitNeutralNone.currency", diplomacyVisitCostNeutralNoneCharge);
        validateChargeCurrency("economy.charges.diplomacyVisitFriendly.currency", diplomacyVisitCostFriendlyCharge);
        validateChargeCurrency("economy.charges.diplomacyFormTrade.currency", diplomacyFormCostTradeCharge);
        validateChargeCurrency("ideology.economy.charges.religionCreation.currency", religionCreationCostCharge);
        validateChargeCurrency("ideology.economy.charges.religionPlayerConversion.currency", religionPlayerConversionCostCharge);
        validateChargeCurrency("ideology.economy.charges.religionTownConversion.currency", religionTownConversionCostCharge);
        validateChargeCurrency("ideology.economy.charges.religionNationConversion.currency", religionNationConversionCostCharge);
        validateChargeCurrency("ideology.economy.charges.religionForcedPlayerConversion.currency", religionForcedPlayerConversionCostCharge);
        validateChargeCurrency("ideology.economy.charges.religionForcedTownConversion.currency", religionForcedTownConversionCostCharge);
        validateChargeCurrency("organization.economy.charges.orgFormation.currency", orgFormationCostCharge);
        validateChargeCurrency("organization.economy.charges.orgMaintenance.currency", orgMaintenanceCostCharge);
    }

    private void validateChargeCurrency(String configPath, EconomyBridge.Charge charge) {
        if (charge == null || charge.currency() == null || charge.currency().isBlank()) {
            return;
        }
        if (!economyBridge.hasCurrency(charge.currency())) {
            getLogger().warning("Configured currency id not found for " + configPath + ": " + charge.currency());
        }
    }

    private void migrateLegacyLocales() {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            getLogger().warning("Failed to create lang folder: " + langFolder.getAbsolutePath());
            return;
        }

        String[] localeFiles = {"messages_en.yml", "messages_es.yml", "messages_zh_cn.yml"};
        for (String fileName : localeFiles) {
            File legacyFile = new File(getDataFolder(), fileName);
            File targetFile = new File(langFolder, fileName);
            if (!legacyFile.exists()) {
                continue;
            }

            try {
                Files.move(legacyFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Moved legacy locale file to lang/: " + fileName);
            } catch (Exception e) {
                getLogger().warning("Failed to move legacy locale file " + fileName + ": " + e.getMessage());
            }
        }
    }

    private void migrateLegacyConfigIfNeeded() {
        boolean hasLegacyKeys = getConfig().contains("validGovernmentTypes")
                || getConfig().contains("validTownGovernmentTypes")
                || getConfig().contains("townElectionIntervalDays")
                || getConfig().contains("townElectionDurationDays")
                || getConfig().contains("validOrganizationTypes")
                || getConfig().contains("orgFormationCost")
                || getConfig().contains("residentOrgLimit")
                || getConfig().contains("nationOrgLimit");
        boolean hasLegacyIdeologyKeys = getConfig().contains("religionEnabled")
                || getConfig().contains("religionCreationCost")
                || getConfig().contains("religionPlayerConversionCost")
                || getConfig().contains("religionTownConversionCost")
                || getConfig().contains("religionNationConversionCost")
                || getConfig().contains("religionForcedPlayerConversionCost")
                || getConfig().contains("religionForcedTownConversionCost")
                || getConfig().isConfigurationSection("economy.charges.religionCreation")
                || getConfig().isConfigurationSection("economy.charges.religionPlayerConversion")
                || getConfig().isConfigurationSection("economy.charges.religionTownConversion")
                || getConfig().isConfigurationSection("economy.charges.religionNationConversion")
                || getConfig().isConfigurationSection("economy.charges.religionForcedPlayerConversion")
                || getConfig().isConfigurationSection("economy.charges.religionForcedTownConversion");
        boolean hasLegacyEconomyCostKeys = getConfig().contains("diplomacyVisitCostNeutralNone")
                || getConfig().contains("diplomacyVisitCostFriendly")
                || getConfig().contains("diplomacyFormCostTrade")
                || (organizationConfig != null && organizationConfig.contains("orgFormationCost"));

        if (!getConfig().getBoolean("moduleConfigMigrated", false) && hasLegacyKeys) {
            migrateLegacyValue("validGovernmentTypes", getConfig(), nationConfig);
            migrateLegacyValue("validTownGovernmentTypes", getConfig(), townConfig);
            migrateLegacyValue("townElectionIntervalDays", getConfig(), townConfig);
            migrateLegacyValue("townElectionDurationDays", getConfig(), townConfig);
            migrateLegacyValue("townConscriptLimitRatio", getConfig(), townConfig);
            migrateLegacyValue("validOrganizationTypes", getConfig(), organizationConfig);
            migrateLegacyValue("orgFormationCost", getConfig(), organizationConfig);
            migrateLegacyValue("nationOrgLimit", getConfig(), organizationConfig);
            if (getConfig().contains("residentOrgLimit") && organizationConfig != null && !organizationConfig.contains("nationOrgLimit")) {
                organizationConfig.set("nationOrgLimit", getConfig().getInt("residentOrgLimit", -1));
            }

            getConfig().set("validGovernmentTypes", null);
            getConfig().set("validTownGovernmentTypes", null);
            getConfig().set("townElectionIntervalDays", null);
            getConfig().set("townElectionDurationDays", null);
            getConfig().set("validOrganizationTypes", null);
            getConfig().set("orgFormationCost", null);
            getConfig().set("residentOrgLimit", null);
            getConfig().set("nationOrgLimit", null);
            getConfig().set("moduleConfigMigrated", true);

            getLogger().info("Migrated legacy config.yml entries into nation.yml, town.yml and organization.yml.");
        }

        if (ideologyConfig != null && hasLegacyIdeologyKeys) {
            migrateLegacyValue("religionEnabled", getConfig(), ideologyConfig);
            migrateLegacyCharge(getConfig(), "religionCreationCost", "economy.charges.religionCreation", "15000.00", ideologyConfig);
            migrateLegacyCharge(getConfig(), "religionPlayerConversionCost", "economy.charges.religionPlayerConversion", "3000.00", ideologyConfig);
            migrateLegacyCharge(getConfig(), "religionTownConversionCost", "economy.charges.religionTownConversion", "12000.00", ideologyConfig);
            migrateLegacyCharge(getConfig(), "religionNationConversionCost", "economy.charges.religionNationConversion", "30000.00", ideologyConfig);
            migrateLegacyCharge(getConfig(), "religionForcedPlayerConversionCost", "economy.charges.religionForcedPlayerConversion", "5000.00", ideologyConfig);
            migrateLegacyCharge(getConfig(), "religionForcedTownConversionCost", "economy.charges.religionForcedTownConversion", "20000.00", ideologyConfig);

            copySectionIfMissing(getConfig(), ideologyConfig, "economy.charges.religionCreation");
            copySectionIfMissing(getConfig(), ideologyConfig, "economy.charges.religionPlayerConversion");
            copySectionIfMissing(getConfig(), ideologyConfig, "economy.charges.religionTownConversion");
            copySectionIfMissing(getConfig(), ideologyConfig, "economy.charges.religionNationConversion");
            copySectionIfMissing(getConfig(), ideologyConfig, "economy.charges.religionForcedPlayerConversion");
            copySectionIfMissing(getConfig(), ideologyConfig, "economy.charges.religionForcedTownConversion");

            getConfig().set("religionEnabled", null);
            getConfig().set("religionCreationCost", null);
            getConfig().set("religionPlayerConversionCost", null);
            getConfig().set("religionTownConversionCost", null);
            getConfig().set("religionNationConversionCost", null);
            getConfig().set("religionForcedPlayerConversionCost", null);
            getConfig().set("religionForcedTownConversionCost", null);
            getConfig().set("economy.charges.religionCreation", null);
            getConfig().set("economy.charges.religionPlayerConversion", null);
            getConfig().set("economy.charges.religionTownConversion", null);
            getConfig().set("economy.charges.religionNationConversion", null);
            getConfig().set("economy.charges.religionForcedPlayerConversion", null);
            getConfig().set("economy.charges.religionForcedTownConversion", null);

            getLogger().info("Migrated ideology settings from config.yml into ideology.yml.");
        }

        if (getConfig().getInt("config-version", 0) < 5 || hasLegacyEconomyCostKeys || !getConfig().isConfigurationSection("economy")) {
            migrateLegacyCharge(getConfig(), "diplomacyVisitCostNeutralNone", "economy.charges.diplomacyVisitNeutralNone", "1000.00");
            migrateLegacyCharge(getConfig(), "diplomacyVisitCostFriendly", "economy.charges.diplomacyVisitFriendly", "750.00");
            migrateLegacyCharge(getConfig(), "diplomacyFormCostTrade", "economy.charges.diplomacyFormTrade", "10000.00");
            if (organizationConfig != null) {
                migrateLegacyCharge(organizationConfig, "orgFormationCost", "economy.charges.orgFormation", "25000.00");
            }

            if (!getConfig().isString("economy.defaultCurrency")) {
                getConfig().set("economy.defaultCurrency", "default");
            }

            String configuredDefaultCurrency = getConfig().getString("economy.defaultCurrency", "default");
            if (!getConfig().isConfigurationSection("economy.exchangeRates")) {
                getConfig().createSection("economy.exchangeRates");
            }
            if (!getConfig().contains("economy.exchangeRates." + configuredDefaultCurrency)) {
                getConfig().set("economy.exchangeRates." + configuredDefaultCurrency, "1.0");
            }

            getConfig().set("diplomacyVisitCostNeutralNone", null);
            getConfig().set("diplomacyVisitCostFriendly", null);
            getConfig().set("diplomacyFormCostTrade", null);
            if (organizationConfig != null) {
                organizationConfig.set("orgFormationCost", null);
            }
        }

        if (getConfig().getInt("config-version", 0) < 5) {
            getConfig().set("config-version", 5);
        }

        if (!getConfig().contains("moduleConfigMigrated")) {
            getConfig().set("moduleConfigMigrated", true);
        }

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
        if (getConfig().getInt("config-version", 0) < 8) {
            getConfig().set("config-version", 8);
            changed = true;
        }

        if (changed) {
            saveConfig();
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
            configuration.set(targetSectionPath + ".currency", getConfig().getString("economy.defaultCurrency", "default"));
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
            String currencyValue = getConfig().getString("economy.defaultCurrency", "default");
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

    private void ensureIdeologyDefaults() {
        if (ideologyConfig == null) {
            return;
        }

        boolean changed = false;
        if (!ideologyConfig.contains("religionEnabled")) {
            ideologyConfig.set("religionEnabled", true);
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.mode")) {
            ideologyConfig.set("cooldowns.mode", "REAL_TIME");
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.world")) {
            ideologyConfig.set("cooldowns.world", "world");
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.memes.days")) {
            ideologyConfig.set("cooldowns.memes.days", 7);
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.doctrines.days")) {
            ideologyConfig.set("cooldowns.doctrines.days", 7);
            changed = true;
        }
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionCreation", "15000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionPlayerConversion", "3000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionTownConversion", "12000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionNationConversion", "30000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionForcedPlayerConversion", "5000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionForcedTownConversion", "20000.00");
        Map<String, Object> treasuryDefaults = Map.of(
                "enabled", true, "titheEnabled", true, "taxWorld", "world",
                "defaultTitheType", "WEALTH_PERCENTAGE", "defaultTitheAmount", "10",
                "logPartialCollection", true);
        for (var entry : treasuryDefaults.entrySet()) {
            String path = "economy.treasury." + entry.getKey();
            if (!ideologyConfig.contains(path)) {
                ideologyConfig.set(path, entry.getValue());
                changed = true;
            }
        }
        Map<String, Object> animalKillingDefaults = Map.of(
                "enabled", true, "durationSeconds", 30, "resistanceLevel", 1, "weaknessLevel", 1);
        for (var entry : animalKillingDefaults.entrySet()) {
            String path = "doctrineEffects.animalKilling." + entry.getKey();
            if (!ideologyConfig.contains(path)) {
                ideologyConfig.set(path, entry.getValue());
                changed = true;
            }
        }
        changed |= migrateLegacyDoctrineRegistryToMemeRegistry();
        changed |= migrateExistingDoctrineNamespaces();

        if (changed) {
            saveYamlConfiguration(ideologyConfig, ideologyConfigFile);
        }
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
            saveYamlConfiguration(backgroundConfig, backgroundConfigFile);
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

    private boolean ensureChargeDefaults(FileConfiguration configuration, String path, String defaultAmount) {
        boolean changed = false;
        if (!configuration.contains(path + ".amount")) {
            configuration.set(path + ".amount", defaultAmount);
            changed = true;
        }
        if (!configuration.contains(path + ".currency")) {
            configuration.set(path + ".currency", getConfig().getString("economy.defaultCurrency", "default"));
            changed = true;
        }
        return changed;
    }

    private boolean ensureMemeDefaults() {
        if (ideologyConfig == null) {
            return false;
        }

        boolean changed = false;
        changed |= ensureMemeDefinition("original_sin", "原罪主义", "我们生来有罪。其他人比我们更有价值。");
        changed |= ensureMemeDefinition("nature_primacy", "自然至上", "人类是完美大自然中的污点。");
        changed |= ensureMemeDefinition("proselytizer", "传教热忱", "我们的使命是传播信仰。");
        changed |= ensureMemeDefinition("loyalty", "赤胆忠心", "我们在面对其他人时必须站在自己人的立场上。");
        changed |= ensureMemeDefinition("supremacist", "霸权主义", "我们的人民应该主宰其他所有人。");
        changed |= ensureMemeDefinition("collectivist", "集体主义", "每个人都是集体的一部分。人们应该努力发挥自己的作用为集体做贡献。");
        changed |= ensureMemeDefinition("ritualist", "仪式热忱", "宇宙尚有广大潜能。借助仪式，我们方能对它有所了解，进而驾驭其中的力量。");
        changed |= ensureMemeDefinition("darkness", "黑暗亲和", "明亮的光线带来燃烧和毁灭！黑暗带来滋养和创造。人们应该生活在黑暗中。");
        changed |= ensureMemeDefinition("tunneler", "地底之心", "人类应该生活在地下，享受地下深处甜美多汁的果实。");
        changed |= ensureMemeDefinition("cannibal", "食人狂热", "我们需要人肉。");
        changed |= ensureMemeDefinition("bloodfeeder", "汲血狂热", "享用血液乃神圣之事，汲血者理应受到我们的崇拜。");
        changed |= ensureMemeDefinition("pain_is_virtue", "苦痛美德", "美德是通过自己和他人的痛苦表现出来的。");
        changed |= ensureMemeDefinition("nudist", "裸体主义", "护甲束缚、控制着我们，这让我们感到窒息。我们应当自由地一丝不挂。");
        changed |= ensureMemeDefinition("nonhuman_alienation", "非人异化", "在神与祂那丰厚的赏赐面前，人性不过是阻碍罢了。");
        changed |= ensureMemeDefinition("raider", "劫掠成性", "强者理应掠夺弱者。");
        changed |= ensureMemeDefinition("individualist", "个人主义", "每个人都是自由的个体，都有权有自己的想法和决定。任何人都不应该被强迫顺从。");
        changed |= ensureMemeDefinition("animal_personhood", "动物平权", "动物应该拥有和人类一样的权利。");
        changed |= ensureMemeDefinition("human_primacy", "人类至上", "人类是宇宙的道德中心。");
        return changed;
    }

    private boolean ensureMemeDefinition(String id, String title, String description) {
        String basePath = "memes.registry." + id;
        boolean changed = false;
        if (!ideologyConfig.contains(basePath + ".title")) {
            ideologyConfig.set(basePath + ".title", title);
            changed = true;
        }
        if (!ideologyConfig.contains(basePath + ".description")) {
            ideologyConfig.set(basePath + ".description", description);
            changed = true;
        }
        return changed;
    }

    private boolean ensureDoctrineDefaults() {
        if (ideologyConfig == null) {
            return false;
        }

        boolean changed = false;
        for (String doctrinePath : List.of(
                "armor_wearing",
                "cannibalism",
                "blood_feeding",
                "multiculturalism",
                "display_of_heads",
                "slaughter_animals",
                "betrayal",
                "kindness",
                "potions",
                "killing_innocent_animals",
                "lighting",
                "field_battle",
                "meat_eating",
                "mining",
                "raiding",
                "logging",
                "trees",
                "nonhumanization",
                "rituals",
                "animal_resonance",
                "spiritual_vision",
                "bonding",
                "comfort",
                "pain",
                "proselytizing",
                "ranching",
                "roughing_it",
                "indoors")) {
            changed |= migrateDoctrineNamespace(doctrinePath);
        }

        List<Map<String, Object>> warMemeRules = new ArrayList<>();
        Map<String, Object> supremacistRule = new LinkedHashMap<>();
        Map<String, Object> supremacistValue = new LinkedHashMap<>();
        supremacistValue.put("default", List.of("want"));
        supremacistValue.put("cant_use", List.of("hate"));
        supremacistRule.put("supremacist", supremacistValue);
        warMemeRules.add(supremacistRule);

        changed |= ensureDoctrineDefinition("namespace:wde/war", "战争", "对于国家之间战争的渴望", List.of(
                createDoctrineAttitudeEntry("neutral", "中立", "战争作为政治上的最后手段，我们不会滥用也不会放弃"),
                createDoctrineAttitudeEntry("hate", "厌恶", "除了毫无意义的牺牲，战争还能带来什么?"),
                createDoctrineAttitudeEntry("want", "渴望", "只有鲜血才能实现生命的意义")
        ), warMemeRules);
        changed |= ensureDoctrineDefinition("namespace:wder/armor_wearing", "穿着护甲", "对于身体遮蔽与护甲穿着的态度", List.of(
                createDoctrineAttitudeEntry("naked", "全裸", "身体不该被护甲束缚，赤裸才是最自然的状态。"),
                createDoctrineAttitudeEntry("pants_only", "仅裤子", "最低限度的遮蔽已经足够，其余护甲都显得多余。"),
                createDoctrineAttitudeEntry("unrestricted", "无规则", "穿不穿护甲都只是个人选择，无需上升为信条。"),
                createDoctrineAttitudeEntry("fully_covered", "完全遮蔽", "身体应被严密遮蔽，完整护甲象征体面与秩序。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/cannibalism", "食人", "对于食用同类尸体的态度", List.of(
                createDoctrineAttitudeEntry("abhor", "厌恶", "食人是最污秽的禁忌，应被彻底排斥。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "食人的想法令人本能恐惧，最好远远避开。"),
                createDoctrineAttitudeEntry("disgust", "反感", "这种行为令人不适，即便不是绝对禁忌也不应鼓励。"),
                createDoctrineAttitudeEntry("accept", "认可", "在特定处境下，食人可以被视为可接受的选择。"),
                createDoctrineAttitudeEntry("enjoy", "喜好", "食用同类被当作某种独特而强烈的偏好。"),
                createDoctrineAttitudeEntry("crave", "渴望", "对人肉有明显追求，它被看作特殊满足。"),
                createDoctrineAttitudeEntry("greedy", "贪婪", "对血肉的占有欲永无止境，食人被视为应被放纵的欲望。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/blood_feeding", "血食", "对于饮用或食用血液的态度", List.of(
                createDoctrineAttitudeEntry("abhor", "厌恶", "饮血是不可接受的禁忌。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "血食令人不安，应当回避。"),
                createDoctrineAttitudeEntry("disgust", "反感", "排斥食用血液。"),
                createDoctrineAttitudeEntry("accept", "认可", "血食是可以接受的进食方式。"),
                createDoctrineAttitudeEntry("enjoy", "喜好", "血食带来满足。"),
                createDoctrineAttitudeEntry("crave", "渴望", "渴望饮血或食用血液。"),
                createDoctrineAttitudeEntry("greedy", "贪婪", "对血食有强烈而持续的需求。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/multiculturalism", "多元文化", "对于外来文化与异质群体的态度", List.of(
                createDoctrineAttitudeEntry("strong_prejudice", "强烈的偏见", "异文化被视为威胁，必须保持明确隔阂。"),
                createDoctrineAttitudeEntry("measured_prejudice", "适当的偏见", "可以接触外来者，但必须始终保留戒备与优先级差异。"),
                createDoctrineAttitudeEntry("mild_prejudice", "温和的偏见", "对外来文化不完全信任，但也不必彻底拒绝。"),
                createDoctrineAttitudeEntry("neutral", "中立", "文化差异只是事实本身，不特别赞扬也不特别排斥。"),
                createDoctrineAttitudeEntry("approve", "认可", "不同文化能共处并互补，多样性值得接纳。"),
                createDoctrineAttitudeEntry("highly_approve", "高度认可", "文化交融会让共同体更强大，应积极欢迎。"),
                createDoctrineAttitudeEntry("noble", "高尚", "尊重多元并保护差异，本身就是一种高贵德性。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/display_of_heads", "枭示", "对于公开枭首示众的态度", List.of(
                createDoctrineAttitudeEntry("disgust", "反感", "以头颅示众过于残忍低劣，不应成为常态。"),
                createDoctrineAttitudeEntry("crave", "渴望", "枭示能传递威慑与荣耀，值得主动追求。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/slaughter_animals", "宰杀动物", "对于主动宰杀动物的态度", List.of(
                createDoctrineAttitudeEntry("taboo", "禁忌", "杀生本身就是禁忌，轻易动刀不可接受。"),
                createDoctrineAttitudeEntry("fear", "恐惧", "屠宰令人本能不安，应尽可能回避。"),
                createDoctrineAttitudeEntry("oppose", "反对", "不赞同主动宰杀，但并未上升到绝对禁令。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "这只是日常生存行为，没有特别道德倾向。"),
                createDoctrineAttitudeEntry("support", "支持", "宰杀动物是正当资源获取方式，应被允许。"),
                createDoctrineAttitudeEntry("love", "喜爱", "屠宰与分肉被当作令人满足的事务。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/betrayal", "变节", "对于背叛、倒戈与失节的态度", List.of(
                createDoctrineAttitudeEntry("hostile", "敌视", "背叛者必须被视为永远的敌人。"),
                createDoctrineAttitudeEntry("abhor", "厌恶", "变节令人深恶痛绝，几乎不可原谅。"),
                createDoctrineAttitudeEntry("disgust", "反感", "虽不至于绝对敌对，但仍应受到明显贬斥。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "立场转换只是现实选择，并不天然带有道德污点。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/kindness", "仁善", "对于仁慈、宽和与善行的态度", List.of(
                createDoctrineAttitudeEntry("required", "必须", "善良不是可选美德，而是每个人必须遵守的准则。"),
                createDoctrineAttitudeEntry("important", "重点", "仁善值得被重点强调，是理想共同体的重要基础。"),
                createDoctrineAttitudeEntry("joy", "愉悦", "施与善意能带来积极满足，应被鼓励。"),
                createDoctrineAttitudeEntry("neutral", "无感", "仁善并非核心价值，有没有都不是重点。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/potions", "药水", "对于药水使用目的与频率的态度", List.of(
                createDoctrineAttitudeEntry("forbidden", "禁止", "药水会破坏纯粹与秩序，应被严格禁用。"),
                createDoctrineAttitudeEntry("medical_only", "仅医疗", "药水只应用于治疗与救命，不应用于其他用途。"),
                createDoctrineAttitudeEntry("medical_or_social", "医疗或社交", "治疗与社交仪式中的药水都可接受，但不应滥用。"),
                createDoctrineAttitudeEntry("required", "必须", "药水是文明生活的重要组成部分，应被主动使用。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/killing_innocent_animals", "杀戮无辜动物", "对于伤害无害动物的态度", List.of(
                createDoctrineAttitudeEntry("abhor", "厌恶", "无辜动物不该被屠害，这种行为令人厌恶。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "这种伤害会引起本能恐惧，最好彻底避开。"),
                createDoctrineAttitudeEntry("disgust", "反感", "并不认同这种行为，哪怕并非绝对禁令。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "这不被视为特殊道德议题。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/lighting", "照明", "对于光照环境的偏好", List.of(
                createDoctrineAttitudeEntry("dim", "微光", "柔弱而克制的光线最适合生活，过亮会破坏氛围。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "亮与暗都只是环境条件，没有特殊偏好。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/field_battle", "野战", "对于开阔地正面作战的态度", List.of(
                createDoctrineAttitudeEntry("enjoy", "喜好", "在旷野正面交锋才能体现勇武与决断。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "野战只是一种战斗方式，本身并无价值倾向。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/meat_eating", "肉食", "对于食肉与素食倾向的态度", List.of(
                createDoctrineAttitudeEntry("abhor_meat", "厌恶", "食肉令人排斥，克制肉欲才算纯净。"),
                createDoctrineAttitudeEntry("fear_meat", "畏惧", "肉食让人感到本能不安，应尽可能远离。"),
                createDoctrineAttitudeEntry("disgust_meat", "反感", "虽然并非绝对禁忌，但仍明显不喜欢肉食。"),
                createDoctrineAttitudeEntry("anti_vegetarian_disgust", "反感素食", "排斥长期素食，认为它违背正常生活。"),
                createDoctrineAttitudeEntry("anti_vegetarian_hate", "憎恶素食", "将素食视为严重偏差，难以容忍。"),
                createDoctrineAttitudeEntry("anti_vegetarian_abhor", "厌恶素食", "素食被看作令人厌恶的禁欲倾向，应明确抵制。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/mining", "采矿", "对于深入地下采掘资源的态度", List.of(
                createDoctrineAttitudeEntry("taboo", "禁忌", "挖掘地下会惊扰不该触碰的东西，因此必须禁绝。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "矿洞深处令人恐惧，不应轻率涉足。"),
                createDoctrineAttitudeEntry("disgust", "反感", "采矿并不受欢迎，最好只在不得已时进行。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/raiding", "掠袭", "对于掠夺式袭击的态度", List.of(
                createDoctrineAttitudeEntry("valued", "重视", "掠袭是展示力量与获取财富的重要方式。"),
                createDoctrineAttitudeEntry("required", "必要", "若想生存和扩张，掠袭几乎不可避免。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "是否掠袭取决于现实局势，并非信条核心。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/logging", "伐木", "对于砍伐树木的态度", List.of(
                createDoctrineAttitudeEntry("taboo", "禁忌", "伤害树木就是触犯禁忌，不能轻易进行。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "砍树会带来不安和后果，应尽量避免。"),
                createDoctrineAttitudeEntry("disgust", "反感", "虽然并非绝对禁止，但仍不赞同滥伐。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "树木只是资源之一，取用与否无关信仰。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/trees", "树", "对于树木与林地本身的态度", List.of(
                createDoctrineAttitudeEntry("crave", "渴望", "树木、林冠与绿意本身就是值得主动追求的存在。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "树只是环境的一部分，没有特别倾向。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/nonhumanization", "非人化", "对于摆脱传统人类身份的态度", List.of(
                createDoctrineAttitudeEntry("required", "必需", "超越人类本身是进步与信条要求。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "是否改变自身并不是价值核心。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/rituals", "仪式", "对于仪式与典礼行为的态度", List.of(
                createDoctrineAttitudeEntry("promote", "推崇", "仪式能塑造秩序、神圣感与共同体认同。"),
                createDoctrineAttitudeEntry("disgust", "反感", "繁复仪式令人厌烦，没有必要过度重视。"),
                createDoctrineAttitudeEntry("abhor", "厌恶", "仪式被视为虚饰与负担，应尽量摒弃。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/animal_resonance", "动物共鸣", "对于与动物建立精神共鸣的态度", List.of(
                createDoctrineAttitudeEntry("powerful", "强力", "与动物的共鸣被视为强大而珍贵的能力。"),
                createDoctrineAttitudeEntry("none", "无", "并不重视这种联系，也不赋予特殊意义。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/spiritual_vision", "灵视", "对于感知灵性与超常视界的态度", List.of(
                createDoctrineAttitudeEntry("powerful", "强力", "灵视意味着更高层次的洞察与力量。"),
                createDoctrineAttitudeEntry("none", "无", "灵视不被当作重要追求或价值。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/bonding", "牵绊", "对于深层情感连结与羁绊的态度", List.of(
                createDoctrineAttitudeEntry("disgust", "反感", "过深牵绊会削弱意志，应避免沉溺其中。"),
                createDoctrineAttitudeEntry("none", "无", "是否建立羁绊都只是个人选择。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/comfort", "舒适", "对于安逸、享受与舒适环境的态度", List.of(
                createDoctrineAttitudeEntry("indifferent", "漠视", "舒适并不重要，过度追求只会腐化心志。"),
                createDoctrineAttitudeEntry("none", "无", "舒适与否都不是信条关注的重点。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/pain", "痛苦", "对于痛苦与受难的态度", List.of(
                createDoctrineAttitudeEntry("ideal", "理想", "痛苦能提炼人格与意义，因此值得追求。"),
                createDoctrineAttitudeEntry("none", "无", "痛苦只是处境之一，并不带有特殊价值。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/proselytizing", "传教", "对于对外传播信条的积极程度", List.of(
                createDoctrineAttitudeEntry("occasional", "偶尔", "有机会时可以传播，但不必过度强求。"),
                createDoctrineAttitudeEntry("sometimes", "有时", "在合适场合主动传教是可以接受的常规行为。"),
                createDoctrineAttitudeEntry("frequent", "频繁", "持续而高频地扩张信仰，是应被鼓励的职责。"),
                createDoctrineAttitudeEntry("neutral", "无感", "是否传教都无所谓，不是核心任务。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/ranching", "畜牧", "对于驯养与规模化饲养动物的态度", List.of(
                createDoctrineAttitudeEntry("primary", "主要", "畜牧应成为生产与生活的主要支柱。"),
                createDoctrineAttitudeEntry("none", "无", "不特别强调畜牧，它只是众多手段之一。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/roughing_it", "餐风露宿", "对于露天生存与艰苦环境的态度", List.of(
                createDoctrineAttitudeEntry("accept", "接纳", "风餐露宿是生活的一部分，应坦然接受。"),
                createDoctrineAttitudeEntry("neutral", "无感", "露宿并无特殊意义，只是环境条件。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/indoors", "室内", "对于居住在室内空间的态度", List.of(
                createDoctrineAttitudeEntry("love", "喜爱", "有墙、有顶和封闭空间带来真正的安全与安宁。")
        ));
        return changed;
    }

    private boolean migrateDoctrineNamespace(String doctrinePath) {
        String oldBasePath = "doctrines.registry.namespace:wde/" + doctrinePath;
        ConfigurationSection oldSection = ideologyConfig.getConfigurationSection(oldBasePath);
        if (oldSection == null) {
            return false;
        }

        String newBasePath = "doctrines.registry.namespace:wder/" + doctrinePath;
        for (String relativePath : oldSection.getKeys(true)) {
            if (oldSection.isConfigurationSection(relativePath)) {
                continue;
            }
            String newPath = newBasePath + "." + relativePath;
            if (!ideologyConfig.contains(newPath)) {
                ideologyConfig.set(newPath, oldSection.get(relativePath));
            }
        }
        ideologyConfig.set(oldBasePath, null);
        return true;
    }

    /** Migrates only definitions that the administrator kept in the configuration. */
    private boolean migrateExistingDoctrineNamespaces() {
        if (ideologyConfig == null) {
            return false;
        }
        boolean changed = false;
        for (String doctrinePath : List.of(
                "armor_wearing",
                "cannibalism",
                "blood_feeding",
                "multiculturalism",
                "display_of_heads",
                "slaughter_animals",
                "betrayal",
                "kindness",
                "potions",
                "killing_innocent_animals",
                "lighting",
                "field_battle",
                "meat_eating",
                "mining",
                "raiding",
                "logging",
                "trees",
                "nonhumanization",
                "rituals",
                "animal_resonance",
                "spiritual_vision",
                "bonding",
                "comfort",
                "pain",
                "proselytizing",
                "ranching",
                "roughing_it",
                "indoors")) {
            changed |= migrateDoctrineNamespace(doctrinePath);
        }
        return changed;
    }

    private boolean ensureDoctrineDefinition(String doctrineId,
                                            String title,
                                            String lore,
                                            List<Map<String, Object>> attitudes) {
        return ensureDoctrineDefinition(doctrineId, title, lore, attitudes, null);
    }

    private boolean ensureDoctrineDefinition(String doctrineId,
                                            String title,
                                            String lore,
                                            List<Map<String, Object>> attitudes,
                                            List<Map<String, Object>> memeRules) {
        String basePath = "doctrines.registry." + doctrineId;
        boolean changed = false;
        if (!ideologyConfig.contains(basePath + ".title")) {
            ideologyConfig.set(basePath + ".title", title);
            changed = true;
        }
        if (!ideologyConfig.contains(basePath + ".lore")) {
            ideologyConfig.set(basePath + ".lore", lore);
            changed = true;
        }
        if (memeRules != null && !ideologyConfig.contains(basePath + ".meme")) {
            ideologyConfig.set(basePath + ".meme", memeRules);
            changed = true;
        }
        if (!ideologyConfig.contains(basePath + ".attitudes")) {
            ideologyConfig.set(basePath + ".attitudes", attitudes);
            changed = true;
        }
        return changed;
    }

    private Map<String, Object> createDoctrineAttitudeEntry(String attitudeId, String title, String lore) {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", title);
        value.put("lore", lore);
        entry.put(attitudeId, value);
        return entry;
    }

    private boolean migrateLegacyDoctrineRegistryToMemeRegistry() {
        if (ideologyConfig == null || !ideologyConfig.isConfigurationSection("doctrines.registry")) {
            return false;
        }

        boolean changed = false;
        ConfigurationSection doctrineRegistry = ideologyConfig.getConfigurationSection("doctrines.registry");
        if (doctrineRegistry == null) {
            return false;
        }

        List<String> migratedIds = new ArrayList<>();
        for (String id : doctrineRegistry.getKeys(false)) {
            // Namespaced entries are doctrine IDs; only legacy bare meme IDs belong here.
            if (!id.matches("^[a-z0-9_.-]+$")) {
                continue;
            }

            String sourceBasePath = "doctrines.registry." + id;
            String targetBasePath = "memes.registry." + id;
            if (!ideologyConfig.contains(targetBasePath + ".title")) {
                ideologyConfig.set(targetBasePath + ".title", ideologyConfig.getString(sourceBasePath + ".title", id));
                changed = true;
            }
            if (!ideologyConfig.contains(targetBasePath + ".description")) {
                ideologyConfig.set(targetBasePath + ".description", ideologyConfig.getString(sourceBasePath + ".description", ""));
                changed = true;
            }
            migratedIds.add(id);
        }

        for (String migratedId : migratedIds) {
            ideologyConfig.set("doctrines.registry." + migratedId, null);
            changed = true;
        }
        ConfigurationSection remainingDoctrines = ideologyConfig.getConfigurationSection("doctrines.registry");
        if (remainingDoctrines != null && remainingDoctrines.getKeys(false).isEmpty()) {
            ideologyConfig.set("doctrines", null);
            changed = true;
        }
        return changed;
    }

    private void loadLocales() {
        locales.clear();
        bundledLocales.clear();
        loadBundledLocales();
        loadLocalesFromDirectory(new File(getDataFolder(), "lang"), true);
        loadLocalesFromDirectory(getDataFolder(), false);
    }

    private void loadBundledLocales() {
        String[] localeFiles = {"messages_en.yml", "messages_es.yml", "messages_zh_cn.yml"};
        for (String fileName : localeFiles) {
            String resourcePath = "lang/" + fileName;
            try (InputStreamReader reader = new InputStreamReader(getResource(resourcePath), StandardCharsets.UTF_8)) {
                String localeKey = fileName.substring("messages_".length(), fileName.length() - ".yml".length()).toLowerCase();
                bundledLocales.put(localeKey, YamlConfiguration.loadConfiguration(reader));
            } catch (Exception e) {
                getLogger().warning("Failed to load bundled locale " + resourcePath + ": " + e.getMessage());
            }
        }
    }

    private void loadLocalesFromDirectory(File directory, boolean overrideExisting) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.getName().startsWith("messages_") && file.getName().endsWith(".yml")) {
                String localeKey = file.getName().substring("messages_".length(), file.getName().length() - ".yml".length()).toLowerCase();
                if (overrideExisting || !locales.containsKey(localeKey)) {
                    locales.put(localeKey, YamlConfiguration.loadConfiguration(file));
                }
            }
        }
    }

    public String getLocaleMessage(String locale, String key) {
        String normalizedLocale = normalizeLocale(locale);
        String resolved = resolveLocaleMessage(locales, normalizedLocale, key);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveLocaleMessage(bundledLocales, normalizedLocale, key);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveLocaleMessage(locales, "zh_cn", key);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveLocaleMessage(bundledLocales, "zh_cn", key);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveLocaleMessage(locales, "en", key);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveLocaleMessage(bundledLocales, "en", key);
        if (resolved != null) {
            return resolved;
        }

        return key;
    }

    private String resolveLocaleMessage(Map<String, FileConfiguration> localeMap, String locale, String key) {
        if (localeMap == null || locale == null || key == null) {
            return null;
        }

        FileConfiguration localeConfig = localeMap.get(locale);
        if (localeConfig != null && localeConfig.contains(key)) {
            return localeConfig.getString(key, key);
        }

        int separatorIndex = locale.indexOf('_');
        if (separatorIndex > 0) {
            FileConfiguration shortLocaleConfig = localeMap.get(locale.substring(0, separatorIndex));
            if (shortLocaleConfig != null && shortLocaleConfig.contains(key)) {
                return shortLocaleConfig.getString(key, key);
            }
        }

        return null;
    }

    public String getLocaleMessage(String key) {
        return getLocaleMessage(userLang, key);
    }

    public String formatLocaleMessage(String locale, String key, Object... args) {
        String template = getLocaleMessage(locale, key);
        if (args == null || args.length == 0) {
            return template;
        }

        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException ex) {
            getLogger().warning("Invalid locale format for key " + key + ": " + ex.getMessage());
            return template;
        }
    }

    public String formatLocaleMessage(String key, Object... args) {
        return formatLocaleMessage(userLang, key, args);
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }

        String normalized = locale.trim().toLowerCase().replace('-', '_');
        if ("zh".equals(normalized) || "zh_cn".equals(normalized) || "zh_hans".equals(normalized)) {
            return "zh_cn";
        }
        return normalized;
    }

    /**
     * 返回热重载结果，标明存储配置是否变化以提示服务器重启。
     */
    public record ReloadResult(boolean storageChanged) {
    }

    /**
     * 封装内置背景默认配置，供首次生成和缺失字段补全使用。
     */
    private record BackgroundDefaultDefinition(String id,
                                               String title,
                                               List<String> lore,
                                               Map<String, Integer> skillLevelBonuses) {
    }
}
