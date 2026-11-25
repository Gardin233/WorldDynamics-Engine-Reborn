package com.thatguycy.worlddynamicsengine.manager.organization;

import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.MilitaryWarState;
import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.PeaceTreatyProposalState;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WarLedger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes the existing war and treaty snapshots without performing disk I/O. */
final class WarStateStore {
    private static final String TREATIES_KEY = "peaceTreatyProposals";
    private final WarLedger ledger;

    WarStateStore(WarLedger ledger) {
        this.ledger = ledger;
    }

    Map<String, MilitaryWarState> readActiveWars(WDEorg org) {
        Map<String, MilitaryWarState> wars = new LinkedHashMap<>();
        for (var entry : ledger.wars.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> warMap)) continue;
            var state = MilitaryWarState.fromMap(entry.getKey(), warMap);
            if (org == null || org.getMemberNationNames().stream()
                    .anyMatch(n -> state.isAttackerNation(n) || state.isDefenderNation(n))) {
                wars.put(state.getWarKey(), state);
            }
        }
        return wars;
    }

    void writeActiveWars(WDEorg org, Map<String, MilitaryWarState> wars) {
        for (String key : readActiveWars(org).keySet()) ledger.wars.remove(key);
        if (wars != null) wars.forEach((key, state) -> ledger.wars.put(key, state.toMap()));
    }

    Map<String, PeaceTreatyProposalState> readTreaties(WDEorg org) {
        Map<String, PeaceTreatyProposalState> proposals = new LinkedHashMap<>();
        Object raw = org == null ? ledger.treaties : org.getSetting(TREATIES_KEY);
        if (raw instanceof Map<?, ?> entries) {
            for (var entry : entries.entrySet()) {
                if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> proposalMap)) continue;
                var state = PeaceTreatyProposalState.fromMap(String.valueOf(entry.getKey()), proposalMap);
                proposals.put(state.getWarKey(), state);
            }
        }
        return proposals;
    }

    void writeTreaties(WDEorg org, Map<String, PeaceTreatyProposalState> proposals) {
        if (org == null) {
            ledger.treaties.clear();
            if (proposals != null) proposals.forEach((key, state) -> ledger.treaties.put(key, state.toMap()));
        } else if (proposals == null || proposals.isEmpty()) {
            org.getSettings().remove(TREATIES_KEY);
        } else {
            Map<String, Object> serialized = new LinkedHashMap<>();
            proposals.forEach((key, state) -> serialized.put(key, state.toMap()));
            org.setSetting(TREATIES_KEY, serialized);
        }
    }
}
