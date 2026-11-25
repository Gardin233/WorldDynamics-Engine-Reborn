package com.thatguycy.worlddynamicsengine;

import com.thatguycy.worlddynamicsengine.command.CommandRegistration;
import com.thatguycy.worlddynamicsengine.integration.craftengine.CraftEngineIntegration;
import com.thatguycy.worlddynamicsengine.integration.papi.MoodPlaceholderIntegration;
import com.thatguycy.worlddynamicsengine.listener.background.PlayerBackgroundListener;
import com.thatguycy.worlddynamicsengine.listener.drop.PlayerDropListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.ArmorDoctrineListener;
import com.thatguycy.worlddynamicsengine.listener.inn.InnListener;
import com.thatguycy.worlddynamicsengine.listener.diplomacy.ContractBookListener;
import com.thatguycy.worlddynamicsengine.listener.towny.NeutralityListener;
import com.thatguycy.worlddynamicsengine.listener.towny.TownyEventListener;
import com.thatguycy.worlddynamicsengine.listener.towny.TownyLifecycleListener;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.manager.drop.PlayerDropManager;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.economy.PlayerGiftManager;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyTreasuryManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyRitualManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.AnimalKillingDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.AnimalResonanceDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.CultureModule;
import com.thatguycy.worlddynamicsengine.manager.ideology.BondingPainDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.MentalCollapseManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.NonhumanizationMoodManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.WarMoodDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.MoodManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.RitualMoodDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.InspirationManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.RanchingDoctrineManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.RelicManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.manager.inn.InnManager;
import com.thatguycy.worlddynamicsengine.manager.diplomacy.ContractManager;
import com.thatguycy.worlddynamicsengine.manager.history.TownHistoryManager;
import com.thatguycy.worlddynamicsengine.manager.history.IdeologyHistoryManager;
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
import com.thatguycy.worlddynamicsengine.model.ContractRecord;
import com.thatguycy.worlddynamicsengine.model.TownChronicle;
import com.thatguycy.worlddynamicsengine.model.IdeologyChronicle;
import com.thatguycy.worlddynamicsengine.model.ChurchChronicle;
import com.thatguycy.worlddynamicsengine.model.PlayerGiftLedger;
import com.thatguycy.worlddynamicsengine.manager.ideology.DoctrineBuffManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
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
    private static final Pattern RELEASE_TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static WorldDynamicsEngine instance;

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
    private final CultureModule cultureModule = new CultureModule(this);
    private CraftEngineIntegration craftEngineIntegration;
    private PlayerDropManager playerDropManager;
    private PlayerGiftManager playerGiftManager;
    private MoodPlaceholderIntegration moodPlaceholderIntegration;
    private WarScoreboardManager warScoreboardManager;
    private InnManager innManager;
    private com.thatguycy.worlddynamicsengine.manager.history.NationHistoryManager nationHistoryManager;
    private TownHistoryManager townHistoryManager;
    private ContractManager contractManager;

    public String framework;
    public boolean govEnabled;
    public boolean orgEnabled;
    public boolean armyEnabled;
    public boolean religionEnabled;
    public int nationOrgLimit;
    public String userLang;
    private final PluginConfiguration configuration = new PluginConfiguration(this);
    private final LocaleMessages messages = new LocaleMessages(this::getResource, message -> getLogger().warning(message));

    @Override
    public void onEnable() {
        instance = this;
        configuration.migrateLegacyDataFolderIfNeeded();
        registerSerializers();
        configuration.ensureResources();
        reloadConfig();
        configuration.loadAndMigrate();
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
        cultureModule.initializeDefinitions();
        warTruceClock = new WarTruceClock(this);
        townManager = new TownManager(this);
        nationManager = new NationManager(this, townManager);
        residentManager = new ResidentManager(this);
        contractManager = new ContractManager(this);
        backgroundManager = new BackgroundManager(this, residentManager);
        orgManager = new OrgManager(this, townManager);
        cultureModule.initializeCore();
        playerGiftManager = new PlayerGiftManager(this);
        cultureModule.initializeActions();
        registerPlaceholders();
        cultureModule.initializeEffects();
        warScoreboardManager = new WarScoreboardManager(this, orgManager.getWarManager());
        cultureModule.initializeTreasury();
        innManager = new InnManager(this);
        playerMenuManager = new PlayerMenuManager(this);

        loadTownyDataOnStartup();
        nationHistoryManager = new com.thatguycy.worlddynamicsengine.manager.history.NationHistoryManager(this);
        nationHistoryManager.initialize();
        townHistoryManager = new TownHistoryManager(this);
        townHistoryManager.initialize();
        cultureModule.initializeHistory();
        registerCommands();
        registerListeners();
        startBackgroundTasks();
        logStartupBanner();

        new Metrics(this, 20763);
    }

    @Override
    public void onDisable() {
        if (moodPlaceholderIntegration != null) {
            moodPlaceholderIntegration.unregister();
            moodPlaceholderIntegration = null;
        }
        cultureModule.stop();
        if (warScoreboardManager != null) warScoreboardManager.clearAll();
        if (playerMenuManager != null) playerMenuManager.closeMenus();
        if (innManager != null) innManager.refundPending();
        saveConfig();
        configuration.saveModuleConfigs();

        if (townManager == null || nationManager == null || residentManager == null || orgManager == null || getReligionManager() == null) {
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
        cultureModule.save();
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

    public TownHistoryManager getTownHistoryManager() {
        return townHistoryManager;
    }

    public IdeologyHistoryManager getIdeologyHistoryManager() {
        return cultureModule.getIdeologyHistoryManager();
    }

    public ContractManager getContractManager() {
        return contractManager;
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

    public PlayerGiftManager getPlayerGiftManager() {
        return playerGiftManager;
    }

    public ReligionManager getReligionManager() {
        return cultureModule.getReligionManager();
    }

    public com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyChurchManager getIdeologyChurchManager() {
        return cultureModule.getIdeologyChurchManager();
    }

    public IdeologyRitualManager getIdeologyRitualManager() {
        return cultureModule.getIdeologyRitualManager();
    }

    public RitualMoodDoctrineManager getRitualMoodDoctrineManager() {
        return cultureModule.getRitualMoodDoctrineManager();
    }

    public IdeologyTreasuryManager getIdeologyTreasuryManager() {
        return cultureModule.getIdeologyTreasuryManager();
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
        return configuration.getTownConfig();
    }

    public FileConfiguration getNationConfig() {
        return configuration.getNationConfig();
    }

    public FileConfiguration getOrganizationConfig() {
        return configuration.getOrganizationConfig();
    }

    public FileConfiguration getWarConfig() {
        return configuration.getWarConfig();
    }

    public WarTruceClock getWarTruceClock() {
        return warTruceClock;
    }

    public FileConfiguration getIdeologyConfig() {
        return configuration.getIdeologyConfig();
    }

    public synchronized boolean reloadMoodExpectationSettingsFromDisk() {
        return configuration.reloadMoodExpectationSettingsFromDisk();
    }

    public com.thatguycy.worlddynamicsengine.manager.ideology.FoodDoctrineManager getFoodDoctrineManager() {
        return cultureModule.getFoodDoctrineManager();
    }

    public RelicManager getRelicManager() {
        return cultureModule.getRelicManager();
    }

    public DoctrineBuffManager getDoctrineBuffManager() {
        return cultureModule.getDoctrineBuffManager();
    }

    public MoodManager getMoodManager() {
        return cultureModule.getMoodManager();
    }

    public WarMoodDoctrineManager getWarMoodDoctrineManager() {
        return cultureModule.getWarMoodDoctrineManager();
    }

    public BondingPainDoctrineManager getBondingPainDoctrineManager() {
        return cultureModule.getBondingPainDoctrineManager();
    }

    public com.thatguycy.worlddynamicsengine.manager.ideology.FaithConflictManager getFaithConflictManager() {
        return cultureModule.getFaithConflictManager();
    }

    public MentalCollapseManager getMentalCollapseManager() {
        return cultureModule.getMentalCollapseManager();
    }

    public NonhumanizationMoodManager getNonhumanizationMoodManager() {
        return cultureModule.getNonhumanizationMoodManager();
    }

    public InspirationManager getInspirationManager() {
        return cultureModule.getInspirationManager();
    }

    public RanchingDoctrineManager getRanchingDoctrineManager() {
        return cultureModule.getRanchingDoctrineManager();
    }

    public ArmorDoctrineListener getArmorDoctrineListener() {
        return cultureModule.getArmorDoctrineListener();
    }

    public void refreshArmorDoctrineForPlayer(java.util.UUID residentUuid) {
        cultureModule.refreshArmorForPlayer(residentUuid);
    }

    public void refreshArmorDoctrineForReligion(String religionName) {
        cultureModule.refreshArmorForReligion(religionName);
    }

    public WarScoreboardManager getWarScoreboardManager() {
        return warScoreboardManager;
    }

    public AnimalKillingDoctrineManager getAnimalKillingDoctrineManager() {
        return cultureModule.getAnimalKillingDoctrineManager();
    }

    public AnimalResonanceDoctrineManager getAnimalResonanceDoctrineManager() {
        return cultureModule.getAnimalResonanceDoctrineManager();
    }

    public FileConfiguration getBackgroundConfig() {
        return configuration.getBackgroundConfig();
    }

    public FileConfiguration getDropsConfig() {
        return configuration.getDropsConfig();
    }

    public boolean isRegularTownWarParticipationAllowed() {
        return configuration.isRegularTownWarParticipationAllowed();
    }

    public String getDefaultEconomyCurrency() {
        return configuration.getDefaultEconomyCurrency();
    }

    public Map<String, BigDecimal> getCurrencyExchangeRates() {
        return configuration.getCurrencyExchangeRates();
    }

    public EconomyBridge.Charge getDiplomacyVisitCostNeutralNoneCharge() {
        return configuration.getDiplomacyVisitCostNeutralNoneCharge();
    }

    public EconomyBridge.Charge getDiplomacyVisitCostFriendlyCharge() {
        return configuration.getDiplomacyVisitCostFriendlyCharge();
    }

    public EconomyBridge.Charge getDiplomacyFormCostTradeCharge() {
        return configuration.getDiplomacyFormCostTradeCharge();
    }

    public EconomyBridge.Charge getOrgFormationCostCharge() {
        return configuration.getOrgFormationCostCharge();
    }

    public EconomyBridge.Charge getOrgMaintenanceCostCharge() {
        return configuration.getOrgMaintenanceCostCharge();
    }

    public long getOrganizationMaintenanceIntervalHours() {
        return configuration.getOrganizationMaintenanceIntervalHours();
    }

    public EconomyBridge.Charge getReligionCreationCostCharge() {
        return configuration.getReligionCreationCostCharge();
    }

    public EconomyBridge.Charge getReligionPlayerConversionCostCharge() {
        return configuration.getReligionPlayerConversionCostCharge();
    }

    public EconomyBridge.Charge getReligionTownConversionCostCharge() {
        return configuration.getReligionTownConversionCostCharge();
    }

    public EconomyBridge.Charge getReligionNationConversionCostCharge() {
        return configuration.getReligionNationConversionCostCharge();
    }

    public EconomyBridge.Charge getReligionForcedPlayerConversionCostCharge() {
        return configuration.getReligionForcedPlayerConversionCostCharge();
    }

    public EconomyBridge.Charge getReligionForcedTownConversionCostCharge() {
        return configuration.getReligionForcedTownConversionCostCharge();
    }

    public List<String> getValidGovernmentTypes() {
        return configuration.getValidGovernmentTypes();
    }

    public List<String> getValidTownGovernmentTypes() {
        return configuration.getValidTownGovernmentTypes();
    }

    public List<String> getValidOrganizationTypes() {
        return configuration.getValidOrganizationTypes();
    }

    public int getDefaultTownElectionIntervalDays() {
        return configuration.getDefaultTownElectionIntervalDays();
    }

    public int getTownElectionDurationDays() {
        return configuration.getTownElectionDurationDays();
    }

    private void registerSerializers() {
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.WarLedger.class);
        ConfigurationSerialization.registerClass(PlayerGiftLedger.class, "PlayerGiftLedger");
        ConfigurationSerialization.registerClass(InnBed.class, "InnBed");
        ConfigurationSerialization.registerClass(WDEtown.class, "WDEtown");
        ConfigurationSerialization.registerClass(WDEnation.class);
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.DiplomaticLetter.class);
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.NationChronicle.class);
        ConfigurationSerialization.registerClass(TownChronicle.class, "TownChronicle");
        ConfigurationSerialization.registerClass(IdeologyChronicle.class, "IdeologyChronicle");
        ConfigurationSerialization.registerClass(ChurchChronicle.class, "ChurchChronicle");
        ConfigurationSerialization.registerClass(WDEresident.class, "WDEresident");
        ConfigurationSerialization.registerClass(WDEorg.class, "WDEorg");
        ConfigurationSerialization.registerClass(WDEreligion.class, "WDEreligion");
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.WDEChurch.class, "WDEChurch");
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.IdeologyCouncilProposal.class, "IdeologyCouncilProposal");
        ConfigurationSerialization.registerClass(com.thatguycy.worlddynamicsengine.model.IdeologyRitualDefinition.class, "IdeologyRitualDefinition");
        ConfigurationSerialization.registerClass(ContractRecord.class, "ContractRecord");
    }

    private void readConfigSettings() {
        var flags = configuration.readConfigSettings();
        framework = flags.framework();
        govEnabled = flags.govEnabled();
        orgEnabled = flags.orgEnabled();
        armyEnabled = flags.armyEnabled();
        religionEnabled = flags.religionEnabled();
        nationOrgLimit = flags.nationOrgLimit();
        userLang = flags.userLang();
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
        economyBridge = economy != null ? new EconomyBridge(this, economy, getDefaultEconomyCurrency(), getCurrencyExchangeRates()) : null;
        if (economyBridge != null) {
            economyBridge.logConfiguredCurrencyStatus();
            configuration.validateConfiguredChargeCurrencies(economyBridge);
        }
        return economy != null;
    }

    private void registerCommands() {
        CommandRegistration.register(this, innManager);
    }

    private void registerListeners() {
        if (playerMenuManager != null) {
            getServer().getPluginManager().registerEvents(playerMenuManager, this);
        }
        if (innManager != null) {
            getServer().getPluginManager().registerEvents(new InnListener(innManager), this);
        }
        getServer().getPluginManager().registerEvents(new TownyEventListener(this, orgManager, economyBridge), this);
        cultureModule.registerListeners();
        getServer().getPluginManager().registerEvents(new TownyLifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new NeutralityListener(this), this);
        getServer().getPluginManager().registerEvents(new com.thatguycy.worlddynamicsengine.listener.diplomacy.TradeSanctionListener(this, orgManager), this);
        getServer().getPluginManager().registerEvents(new ContractBookListener(contractManager), this);
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

    private void registerPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI not detected; WDER placeholders are inactive.");
            return;
        }
        moodPlaceholderIntegration = new MoodPlaceholderIntegration(this);
        if (moodPlaceholderIntegration.register()) {
            getLogger().info("PlaceholderAPI WDER expansion registered.");
        } else {
            getLogger().warning("Failed to register the PlaceholderAPI WDER expansion.");
            moodPlaceholderIntegration = null;
        }
    }

    private void startBackgroundTasks() {
        cultureModule.startActionTasks();
        if (warScoreboardManager != null) warScoreboardManager.start();
        if (innManager != null) innManager.start();
        residentManager.syncWithTowny();
        cultureModule.startResidentTasks();
        orgManager.enforceNeutralityOnStartup();
        townManager.enableAutoSave();
        nationManager.enableAutoSave();
        residentManager.enableAutoSave();
        orgManager.enableAutoSave();
        cultureModule.startGovernanceTasks();
        if (backgroundManager != null) {
            getServer().getScheduler().runTask(this, () -> getServer().getOnlinePlayers().forEach(player -> {
                backgroundManager.applyPendingBonuses(player);
                backgroundManager.promptSelectionIfNeeded(player);
            }));
        }
        cultureModule.scheduleRegistryReloads();
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

    public synchronized ReloadResult reloadPluginState() {
        String storageSignatureBeforeReload = configuration.buildStorageSignature();

        cultureModule.prepareReload();
        if (warScoreboardManager != null) warScoreboardManager.clearAll();
        if (innManager != null) innManager.refundPending();
        saveRuntimeState();
        reloadConfig();
        configuration.loadAndMigrate();
        loadLocales();
        readConfigSettings();
        if (warTruceClock != null) warTruceClock.reload();
        if (nationHistoryManager != null) {
            nationHistoryManager.reload();
            nationHistoryManager.initialize();
        }
        if (townHistoryManager != null) {
            townHistoryManager.reload();
            townHistoryManager.initialize();
        }
        cultureModule.reloadHistory();
        if (playerDropManager != null) {
            playerDropManager.reloadDefinitions();
        }
        cultureModule.reloadDefinitions();
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
        cultureModule.evaluateOnlinePlayersNow();

        boolean storageChanged = !storageSignatureBeforeReload.equals(configuration.buildStorageSignature());
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
        cultureModule.save();
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

    private void loadLocales() {
        messages.reload(getDataFolder());
    }

    public String getLocaleMessage(String locale, String key) {
        return messages.get(locale, key);
    }

    public String getLocaleMessage(String key) {
        return getLocaleMessage(userLang, key);
    }

    public String formatLocaleMessage(String locale, String key, Object... args) {
        return messages.format(locale, key, args);
    }

    public String formatLocaleMessage(String key, Object... args) {
        return formatLocaleMessage(userLang, key, args);
    }

    /**
     * 返回热重载结果，标明存储配置是否变化以提示服务器重启。
     */
    public record ReloadResult(boolean storageChanged) {
    }

}
