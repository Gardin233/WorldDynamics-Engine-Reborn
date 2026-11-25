package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CultureModuleTest {
    @Test
    void constructionDoesNotResolvePluginDependencies() {
        var module = new CultureModule(null);
        assertNull(module.getReligionManager());
        assertNull(module.getIdeologyChurchManager());
        assertNull(module.getIdeologyRitualManager());
        assertNull(module.getIdeologyTreasuryManager());
        assertNull(module.getRitualMoodDoctrineManager());
        assertNull(module.getFoodDoctrineManager());
        assertNull(module.getRelicManager());
        assertNull(module.getDoctrineBuffManager());
        assertNull(module.getMoodManager());
        assertNull(module.getWarMoodDoctrineManager());
        assertNull(module.getBondingPainDoctrineManager());
        assertNull(module.getFaithConflictManager());
        assertNull(module.getMentalCollapseManager());
        assertNull(module.getNonhumanizationMoodManager());
        assertNull(module.getInspirationManager());
        assertNull(module.getRanchingDoctrineManager());
        assertNull(module.getArmorDoctrineListener());
        assertNull(module.getAnimalKillingDoctrineManager());
        assertNull(module.getAnimalResonanceDoctrineManager());
        assertNull(module.getIdeologyHistoryManager());
    }

    @Test
    void cleanupIsSafeBeforeServicesHaveBeenInitialized() {
        var module = new CultureModule(null);
        assertDoesNotThrow(() -> {
            module.stop();
            module.prepareReload();
            module.save();
            module.reloadHistory();
            module.reloadDefinitions();
            module.evaluateOnlinePlayersNow();
            module.scheduleRegistryReloads();
            module.refreshArmorForPlayer(null);
            module.refreshArmorForReligion("test");
            module.stop();
        });
    }
}
