package com.thatguycy.worlddynamicsengine.command.organization;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.model.WDEnation;

/**
 * 集中判断集团事务中的国家代表资格：国王、政府领袖或军队统帅，不替代和约的国王专属校验。
 */
final class NationAuthority {
    private NationAuthority() {}

    static String authorizedNationName(Resident resident, String playerName, NationManager nationManager) {
        try {
            if (resident == null || !resident.hasTown() || !resident.getTown().hasNation()) {
                return null;
            }
            Nation nation = resident.getTown().getNation();
            WDEnation data = nationManager.getNation(nation.getName());
            if (nation.isKing(resident)) {
                return nation.getName();
            }
            if (data != null && (data.getGovernmentLeader().equalsIgnoreCase(playerName)
                    || data.getArmyLeader().equalsIgnoreCase(playerName))) {
                return nation.getName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
