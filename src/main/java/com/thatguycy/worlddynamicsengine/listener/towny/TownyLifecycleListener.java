package com.thatguycy.worlddynamicsengine.listener.towny;

import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangedEvent;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 根据 Towny 成功的创建、删除、改名和归属事件维护 WDER 治理档案，不定时扫描建档。
 */
public final class TownyLifecycleListener implements Listener {
    private final WorldDynamicsEngine plugin;
    private final TownManager towns;
    private final NationManager nations;

    public TownyLifecycleListener(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        towns = plugin.getTownManager();
        nations = plugin.getNationManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNewTown(NewTownEvent event) {
        towns.registerTown(event.getTown());
        towns.saveTowns();
        nations.refreshTownHierarchy();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNewNation(NewNationEvent event) {
        nations.registerNation(event.getNation());
        if (plugin.getNationHistoryManager() != null) plugin.getNationHistoryManager().created(event.getNation());
        for (Town town : event.getNation().getTowns()) {
            towns.updateTownNation(town.getName(), event.getNation().getName());
        }
        nations.refreshTownHierarchy();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeleteTown(DeleteTownEvent event) {
        towns.removeTown(event.getTownName());
        nations.refreshTownHierarchy();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeleteNation(DeleteNationEvent event) {
        nations.removeNation(event.getNationName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRenameTown(RenameTownEvent event) {
        if (plugin.getNationHistoryManager() != null && event.getTown().getNationOrNull() != null) {
            plugin.getNationHistoryManager().renameTown(event.getTown().getNationOrNull().getName(),
                    event.getOldName(), event.getTown().getName());
        }
        towns.renameTown(event.getOldName(), event.getTown().getName());
        nations.refreshTownHierarchy();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRenameNation(RenameNationEvent event) {
        nations.renameNation(event.getOldName(), event.getNation().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNationAddTown(NationAddTownEvent event) {
        towns.updateTownNation(event.getTown().getName(), event.getNation().getName());
        nations.refreshTownHierarchy();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNationRemoveTown(NationRemoveTownEvent event) {
        WDEtown town = towns.getTown(event.getTown().getName());
        if (town != null && event.getNation().getName().equalsIgnoreCase(town.getNationName())) {
            towns.updateTownNation(town.getTownName(), "None");
            nations.refreshTownHierarchy();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMayorChanged(TownMayorChangedEvent event) {
        WDEtown town = towns.getTown(event.getTown().getName());
        if (town != null) {
            town.setTownLeader(event.getNewMayor().getName());
            towns.saveTowns();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKingChange(NationKingChangeEvent event) {
        // This is a cancellable pre-change event; inspect only the affected nation after it commits.
        var townyNation = event.getNation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            var nation = nations.getNation(townyNation.getName());
            if (nation != null && townyNation.hasKing()) {
                nation.setGovernmentLeader(townyNation.getKing().getName());
                nations.saveNations();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResidentAdded(TownAddResidentEvent event) {
        refreshConscriptsAfterChange(event.getTown());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResidentRemoved(TownRemoveResidentEvent event) {
        WDEtown town = towns.getTown(event.getTown().getName());
        if (town != null) {
            town.getConscripts().remove(event.getResident().getName());
            towns.saveTowns();
        }
        refreshConscriptsAfterChange(event.getTown());
    }

    private void refreshConscriptsAfterChange(Town town) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            towns.syncConscriptData(town);
            towns.saveTowns();
        });
    }
}
