package com.thatguycy.worlddynamicsengine.manager.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.MilitaryWarState;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WarLedger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarManagerTest {
    @Test
    void importsLegacyOrganizationWarIntoTheCentralLedgerOnlyOnce() {
        var ledger = new WarLedger();
        var wars = new WarManager(null, null, null, null, ledger);
        var org = new WDEorg("Alliance", UUID.randomUUID(), "One", "Capital", "MILITARY_ALLIANCE");
        var war = new MilitaryWarState("One", "Two", List.of("One"), List.of("Two"), 5L);
        org.setSetting("activeMilitaryWars", Map.of(war.getWarKey(), war.toMap()));

        assertTrue(wars.migrateOrganizationWars(org));
        assertNull(org.getSetting("activeMilitaryWars"));
        assertEquals(Map.of(war.getWarKey(), war.toMap()), ledger.wars);
        assertEquals(war.toMap(), wars.getActiveMilitaryWars(org).getFirst().toMap());
        assertFalse(wars.migrateOrganizationWars(org));
    }

    @Test
    void activeWarQueriesReadDetachedSnapshotsOfPersistedState() {
        var original = new MilitaryWarState("One", "Two", List.of("One"), List.of("Two"), 5L);
        var ledger = new WarLedger(Map.of("wars", Map.of(original.getWarKey(), original.toMap())));
        var wars = new WarManager(null, null, null, null, ledger);

        wars.getActiveMilitaryWars(null).getFirst().markEconomicTermsDemanded("One");

        assertFalse(wars.getActiveMilitaryWars(null).getFirst().hasAttackerEconomicTermsDemanded());
    }

    @Test
    void warDetailsExposeEnemySideOnlyFromAParticipantPerspective() {
        var war = new MilitaryWarState("One", "Two", List.of("One", "Ally"), List.of("Two"), 5L);
        var ledger = new WarLedger(Map.of("wars", Map.of(war.getWarKey(), war.toMap())));
        var wars = new WarManager(null, null, null, null, ledger);

        assertEquals(List.of("Two"), wars.getWarFocusDetails(war.getWarKey(), "Ally", "Town").enemyParticipants());
        var defender = wars.getWarFocusDetails(war.getWarKey(), "Two", "Town");
        assertTrue(defender.defenderPerspective());
        assertTrue(defender.enemyParticipants().containsAll(List.of("One", "Ally")));
        assertNull(wars.getWarFocusDetails(war.getWarKey(), "Outsider", "Town"));
    }
}
