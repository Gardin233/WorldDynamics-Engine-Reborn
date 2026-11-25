package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.listener.ideology.AnimalKillingDoctrineListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.ArmorDoctrineListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.DoctrineBuffListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.FoodDoctrineListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.IdeologyRitualListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.IdeologyTreasuryListener;
import com.thatguycy.worlddynamicsengine.listener.ideology.RelicBuffListener;
import com.thatguycy.worlddynamicsengine.manager.history.IdeologyHistoryManager;
import java.util.UUID;
import org.bukkit.event.Listener;

/** Owns culture services and their lifecycle; business rules remain in individual managers. */
public final class CultureModule {
    private final WorldDynamicsEngine plugin;
    private ReligionManager religionManager;
    private IdeologyChurchManager ideologyChurchManager;
    private IdeologyTreasuryManager ideologyTreasuryManager;
    private IdeologyRitualManager ideologyRitualManager;
    private RitualMoodDoctrineManager ritualMoodDoctrineManager;
    private FoodDoctrineManager foodDoctrineManager;
    private RelicManager relicManager;
    private AnimalKillingDoctrineManager animalKillingDoctrineManager;
    private AnimalResonanceDoctrineManager animalResonanceDoctrineManager;
    private AnimalPersonhoodLeashManager animalPersonhoodLeashManager;
    private DoctrineBuffManager doctrineBuffManager;
    private MoodManager moodManager;
    private BondingPainDoctrineManager bondingPainDoctrineManager;
    private ComfortDoctrineManager comfortDoctrineManager;
    private RoughingItDoctrineManager roughingItDoctrineManager;
    private LightingDoctrineManager lightingDoctrineManager;
    private IndoorsDoctrineManager indoorsDoctrineManager;
    private MiningDoctrineManager miningDoctrineManager;
    private LoggingTreesDoctrineManager loggingTreesDoctrineManager;
    private PotionDoctrineManager potionDoctrineManager;
    private DisplayOfHeadsDoctrineManager displayOfHeadsDoctrineManager;
    private FieldBattleDoctrineManager fieldBattleDoctrineManager;
    private MulticulturalismDoctrineManager multiculturalismDoctrineManager;
    private WarMoodDoctrineManager warMoodDoctrineManager;
    private RanchingDoctrineManager ranchingDoctrineManager;
    private FaithConflictManager faithConflictManager;
    private MentalCollapseManager mentalCollapseManager;
    private NonhumanizationMoodManager nonhumanizationMoodManager;
    private InspirationManager inspirationManager;
    private ArmorDoctrineListener armorDoctrineListener;
    private IdeologyHistoryManager ideologyHistoryManager;

    // Construction must not call plugin getters: the module is published before initializing services.
    public CultureModule(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public void initializeDefinitions() {
        foodDoctrineManager = new FoodDoctrineManager(plugin);
        relicManager = new RelicManager(plugin);
    }

    public void initializeCore() {
        religionManager = new ReligionManager(plugin);
        ideologyChurchManager = new IdeologyChurchManager(plugin);
        ideologyRitualManager = new IdeologyRitualManager(plugin);
        doctrineBuffManager = new DoctrineBuffManager(plugin);
        moodManager = new MoodManager(plugin);
        ritualMoodDoctrineManager = new RitualMoodDoctrineManager(plugin);
    }

    public void initializeActions() {
        warMoodDoctrineManager = new WarMoodDoctrineManager(plugin);
        bondingPainDoctrineManager = new BondingPainDoctrineManager(plugin);
        comfortDoctrineManager = new ComfortDoctrineManager(plugin);
        roughingItDoctrineManager = new RoughingItDoctrineManager(plugin);
        lightingDoctrineManager = new LightingDoctrineManager(plugin);
        indoorsDoctrineManager = new IndoorsDoctrineManager(plugin);
        miningDoctrineManager = new MiningDoctrineManager(plugin);
        loggingTreesDoctrineManager = new LoggingTreesDoctrineManager(plugin);
        potionDoctrineManager = new PotionDoctrineManager(plugin);
        displayOfHeadsDoctrineManager = new DisplayOfHeadsDoctrineManager(plugin);
        fieldBattleDoctrineManager = new FieldBattleDoctrineManager(plugin);
        multiculturalismDoctrineManager = new MulticulturalismDoctrineManager(plugin);
        ranchingDoctrineManager = new RanchingDoctrineManager(plugin);
        faithConflictManager = new FaithConflictManager(plugin);
    }

    public void initializeEffects() {
        mentalCollapseManager = new MentalCollapseManager(plugin);
        nonhumanizationMoodManager = new NonhumanizationMoodManager(plugin);
        animalKillingDoctrineManager = new AnimalKillingDoctrineManager(plugin);
        animalResonanceDoctrineManager = new AnimalResonanceDoctrineManager(plugin);
        animalPersonhoodLeashManager = new AnimalPersonhoodLeashManager(plugin);
    }

    public void initializeTreasury() {
        ideologyTreasuryManager = new IdeologyTreasuryManager(plugin);
    }

    public void initializeHistory() {
        ideologyHistoryManager = new IdeologyHistoryManager(plugin);
        ideologyHistoryManager.initialize();
    }

    public void registerListeners() {
        register(faithConflictManager);
        register(bondingPainDoctrineManager);
        register(comfortDoctrineManager);
        register(roughingItDoctrineManager);
        register(lightingDoctrineManager);
        register(indoorsDoctrineManager);
        register(miningDoctrineManager);
        register(loggingTreesDoctrineManager);
        register(potionDoctrineManager);
        register(displayOfHeadsDoctrineManager);
        register(fieldBattleDoctrineManager);
        register(multiculturalismDoctrineManager);
        register(ranchingDoctrineManager);
        register(new IdeologyTreasuryListener(plugin));
        register(new IdeologyRitualListener(ideologyRitualManager));
        register(new DoctrineBuffListener(doctrineBuffManager));
        register(new AnimalKillingDoctrineListener(animalKillingDoctrineManager));
        register(animalResonanceDoctrineManager);
        register(animalPersonhoodLeashManager);
        animalPersonhoodLeashManager.start();
        inspirationManager = new InspirationManager(plugin);
        register(inspirationManager);
        inspirationManager.initializeOnlinePlayers();
        armorDoctrineListener = new ArmorDoctrineListener(
                plugin, plugin.getResidentManager(), religionManager, moodManager);
        register(armorDoctrineListener);
        armorDoctrineListener.initializeOnlinePlayers();
        register(mentalCollapseManager);
        mentalCollapseManager.initializeOnlinePlayers();
        register(nonhumanizationMoodManager);
        var relicListener = new RelicBuffListener(
                plugin, relicManager, plugin.getResidentManager(), religionManager, doctrineBuffManager);
        register(relicListener);
        relicListener.initializeOnlinePlayers();
        register(new FoodDoctrineListener(plugin));
    }

    private void register(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public void startActionTasks() {
        if (doctrineBuffManager != null) doctrineBuffManager.start();
        if (moodManager != null) moodManager.start();
        if (ritualMoodDoctrineManager != null) ritualMoodDoctrineManager.start();
        if (bondingPainDoctrineManager != null) bondingPainDoctrineManager.start();
        if (comfortDoctrineManager != null) comfortDoctrineManager.start();
        if (warMoodDoctrineManager != null) warMoodDoctrineManager.start();
        if (lightingDoctrineManager != null) lightingDoctrineManager.start();
        if (indoorsDoctrineManager != null) indoorsDoctrineManager.start();
        if (displayOfHeadsDoctrineManager != null) displayOfHeadsDoctrineManager.start();
        if (multiculturalismDoctrineManager != null) multiculturalismDoctrineManager.start();
    }

    public void startResidentTasks() {
        if (nonhumanizationMoodManager != null) nonhumanizationMoodManager.start();
    }

    public void startGovernanceTasks() {
        religionManager.enableAutoSave();
        if (ideologyChurchManager != null) ideologyChurchManager.startTasks();
    }

    public void scheduleRegistryReloads() {
        if (religionManager == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> religionManager.reloadMemeRegistry());
        plugin.getServer().getScheduler().runTask(plugin, () -> religionManager.reloadDoctrineRegistry());
    }

    public void prepareReload() {
        if (doctrineBuffManager != null) doctrineBuffManager.clearAll();
        if (mentalCollapseManager != null) mentalCollapseManager.stopTasks();
        if (ideologyRitualManager != null) ideologyRitualManager.cancelAll();
    }

    public void reloadHistory() {
        if (ideologyHistoryManager != null) {
            ideologyHistoryManager.reload();
            ideologyHistoryManager.initialize();
        }
    }

    public void reloadDefinitions() {
        if (foodDoctrineManager != null) foodDoctrineManager.reload();
        if (relicManager != null) relicManager.reload();
        if (animalKillingDoctrineManager != null) animalKillingDoctrineManager.reload();
        if (miningDoctrineManager != null) miningDoctrineManager.reload();
        if (loggingTreesDoctrineManager != null) loggingTreesDoctrineManager.reload();
        if (potionDoctrineManager != null) potionDoctrineManager.reload();
        if (ideologyRitualManager != null) ideologyRitualManager.loadAll();
    }

    public void evaluateOnlinePlayersNow() {
        if (moodManager != null) moodManager.evaluateOnlinePlayersNow();
    }

    public void stop() {
        if (nonhumanizationMoodManager != null) nonhumanizationMoodManager.stop();
        if (inspirationManager != null) inspirationManager.stopTasks();
        prepareReload();
    }

    public void save() {
        if (religionManager != null) religionManager.saveReligions();
        if (ideologyChurchManager != null) ideologyChurchManager.saveAll();
    }

    public void refreshArmorForPlayer(UUID residentUuid) {
        if (armorDoctrineListener == null || residentUuid == null) return;
        var player = plugin.getServer().getPlayer(residentUuid);
        if (player != null) armorDoctrineListener.refreshPlayer(player);
    }

    public void refreshArmorForReligion(String religionName) {
        if (armorDoctrineListener != null) armorDoctrineListener.refreshReligion(religionName);
    }

    public ReligionManager getReligionManager() { return religionManager; }
    public IdeologyChurchManager getIdeologyChurchManager() { return ideologyChurchManager; }
    public IdeologyRitualManager getIdeologyRitualManager() { return ideologyRitualManager; }
    public RitualMoodDoctrineManager getRitualMoodDoctrineManager() { return ritualMoodDoctrineManager; }
    public IdeologyTreasuryManager getIdeologyTreasuryManager() { return ideologyTreasuryManager; }
    public FoodDoctrineManager getFoodDoctrineManager() { return foodDoctrineManager; }
    public RelicManager getRelicManager() { return relicManager; }
    public DoctrineBuffManager getDoctrineBuffManager() { return doctrineBuffManager; }
    public MoodManager getMoodManager() { return moodManager; }
    public WarMoodDoctrineManager getWarMoodDoctrineManager() { return warMoodDoctrineManager; }
    public BondingPainDoctrineManager getBondingPainDoctrineManager() { return bondingPainDoctrineManager; }
    public FaithConflictManager getFaithConflictManager() { return faithConflictManager; }
    public MentalCollapseManager getMentalCollapseManager() { return mentalCollapseManager; }
    public NonhumanizationMoodManager getNonhumanizationMoodManager() { return nonhumanizationMoodManager; }
    public InspirationManager getInspirationManager() { return inspirationManager; }
    public RanchingDoctrineManager getRanchingDoctrineManager() { return ranchingDoctrineManager; }
    public ArmorDoctrineListener getArmorDoctrineListener() { return armorDoctrineListener; }
    public AnimalKillingDoctrineManager getAnimalKillingDoctrineManager() { return animalKillingDoctrineManager; }
    public AnimalResonanceDoctrineManager getAnimalResonanceDoctrineManager() { return animalResonanceDoctrineManager; }
    public IdeologyHistoryManager getIdeologyHistoryManager() { return ideologyHistoryManager; }
}
