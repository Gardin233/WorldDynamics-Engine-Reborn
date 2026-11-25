package com.thatguycy.worlddynamicsengine.command;

import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.organization.WarManager;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.List;
import org.bukkit.entity.Player;

/** Typed command entry points for inventory actions that carry structured state. */
public final class MenuActionExecutor {
    private final WorldDynamicsEngine plugin;

    public MenuActionExecutor(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public String treatyActor(Player player) {
        var town = TownyAPI.getInstance().getTown(player);
        if (town == null) return null;
        if (town.getMayor() != null && player.getUniqueId().equals(town.getMayor().getUUID())
                && wars().isTownInActiveWar(town.getName())) return "town:" + town.getName();
        var nation = town.getNationOrNull();
        return nation != null && nation.getKing() != null
                && player.getUniqueId().equals(nation.getKing().getUUID()) ? nation.getName() : null;
    }

    public WarManager.PeaceTreatyProposalResult propose(Player player, String scope, String actor, String target,
                    WarManager.PeaceTreatyProposalState.ProposalType type, List<WarManager.PeaceTreatyClauseState> clauses) {
        if (!canManageTreaty(player, actor)) {
            return WarManager.PeaceTreatyProposalResult.invalid("common_no_authority");
        }
        return wars().proposePeaceTreaty(plugin.getOrgManager().getOrganization(scope), actor, target,
                type, clauses, plugin.getNationManager());
    }

    public WarManager.PeaceTreatyReviewResult review(Player player, String actor,
                    WarManager.PeaceTreatyMenuIncoming incoming, boolean accept) {
        if (!canManageTreaty(player, actor)) {
            return WarManager.PeaceTreatyReviewResult.invalid("common_no_authority");
        }
        return wars().reviewPeaceTreaty(plugin.getOrgManager().getOrganization(incoming.organization()), actor,
                incoming.proposal().getProposerNationName(), accept, plugin.getNationManager());
    }

    public boolean setDoctrine(Player player, String name, IdeologyDoctrineDefinition definition, String attitude) {
        var religions = plugin.getReligionManager();
        var religion = religions.getReligion(name);
        String id = definition.getId();
        if (!player.hasPermission("wde.player") || !plugin.religionEnabled || religion == null
                || religion.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL
                || !player.getUniqueId().equals(religion.getPontiffUuid())
                || religions.getDoctrine(id) != definition || !religions.canUseDoctrineAttitude(religion, id, attitude)
                || religions.isDoctrineModificationBlocked(religion, id)) return false;
        religions.setDoctrineAttitude(religion, id, attitude);
        religions.markDoctrineModified(religion, id);
        religions.saveReligion(religion);
        return true;
    }

    public boolean focusWar(Player player, String key) {
        return player.hasPermission("wde.player") && plugin.getWarScoreboardManager().setFocusedWar(player, key, true);
    }

    private boolean canManageTreaty(Player player, String actor) {
        return plugin.orgEnabled && player.hasPermission("wde.player") && actor.equals(treatyActor(player));
    }

    private WarManager wars() {
        return plugin.getOrgManager().getWarManager();
    }
}
