package com.thatguycy.worlddynamicsengine.manager.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.MilitaryWarState;
import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.PeaceTreatyProposalState;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WarLedger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationStateTest {
    @Test
    void migrationDisablesPercentageTributesAndPreservesExistingFixedAmounts() {
        var nation = new WDEnation("Source");
        nation.setSetting("peaceTreatyTributes", List.of(
                Map.of("beneficiaryNation", "One", "taxRatePercent", 5, "appliedAt", 1L),
                Map.of("beneficiaryNation", "Two", "taxRatePercent", 5, "fixedAmount", "12.50")));

        assertTrue(PeaceTreatyTributes.migrate(List.of(nation)));
        assertEquals(List.of(
                Map.of("beneficiaryNation", "One", "fixedAmount", "0", "appliedAt", 1L),
                Map.of("beneficiaryNation", "Two", "fixedAmount", "12.50")),
                nation.getSetting("peaceTreatyTributes"));
        assertFalse(PeaceTreatyTributes.migrate(List.of(nation)));
    }

    @Test
    void collectionDepositsOnlyAfterSuccessfulWithdrawal() {
        var nation = new WDEnation("Source");
        nation.setSetting("peaceTreatyTributes", List.of(
                Map.of("beneficiaryNation", "Target", "fixedAmount", "1.000000001")));
        PeaceTreatyTributes.add(nation, "Target", new BigDecimal("12.50"), 1L);
        PeaceTreatyTributes.add(nation, "Target", new BigDecimal("20"), 2L);
        var calls = new ArrayList<String>();

        assertTrue(PeaceTreatyTributes.collect(List.of(nation), name -> name,
                (source, amount, reason) -> {
                    calls.add("withdraw:" + source + ":" + amount);
                    return amount.compareTo(new BigDecimal("20")) < 0;
                },
                (target, amount, reason) -> {
                    calls.add("deposit:" + target + ":" + amount);
                    return false;
                }));
        assertEquals(List.of("withdraw:Source:12.50", "deposit:Target:12.50",
                "withdraw:Source:20"), calls);
    }

    @Test
    void scopedWarUpdateLeavesUnrelatedWarsUntouched() {
        var ledger = new WarLedger();
        var store = new WarStateStore(ledger);
        var first = new MilitaryWarState("One", "Two", List.of("One"), List.of("Two"), 1L);
        var second = new MilitaryWarState("Three", "Four", List.of("Three"), List.of("Four"), 2L);
        store.writeActiveWars(null, Map.of(first.getWarKey(), first, second.getWarKey(), second));
        var org = organization();

        assertEquals(Set.of(first.getWarKey()), store.readActiveWars(org).keySet());
        store.writeActiveWars(org, Map.of());
        assertEquals(Map.of(second.getWarKey(), second.toMap()), ledger.wars);
    }

    @Test
    void centralAndOrganizationTreatiesRoundTripSeparately() {
        var ledger = new WarLedger();
        var store = new WarStateStore(ledger);
        var org = organization();
        var central = new PeaceTreatyProposalState("war", PeaceTreatyProposalState.ProposalType.DEMAND,
                "One", "Two", 4, List.of(), 1L);
        var scoped = new PeaceTreatyProposalState("war", PeaceTreatyProposalState.ProposalType.SURRENDER,
                "Two", "One", 8, List.of(), 2L);
        store.writeTreaties(null, Map.of("war", central));
        store.writeTreaties(org, Map.of("war", scoped));

        assertEquals(central.toMap(), store.readTreaties(null).get("war").toMap());
        assertEquals(scoped.toMap(), store.readTreaties(org).get("war").toMap());
        store.writeTreaties(org, Map.of());
        assertNull(org.getSetting("peaceTreatyProposals"));
        assertEquals(Map.of("war", central.toMap()), ledger.treaties);
    }

    private static WDEorg organization() {
        return new WDEorg("Alliance", UUID.randomUUID(), "One", "Capital", "MILITARY_ALLIANCE");
    }
}
