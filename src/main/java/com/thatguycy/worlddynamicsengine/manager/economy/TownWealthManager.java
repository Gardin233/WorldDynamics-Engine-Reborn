package com.thatguycy.worlddynamicsengine.manager.economy;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.World;

/**
 * Refreshes town wealth without doing balance aggregation or database writes on the server thread.
 */
public final class TownWealthManager {
    private static final String STORAGE_CATEGORY = "towns";

    private record TownSnapshot(String name, int population, int plotCount, List<UUID> residents,
                                String worldName) {}

    private record WealthResult(String townName, BigDecimal wealth) {}

    private final WorldDynamicsEngine plugin;
    private final TownManager townManager;
    private final AtomicBoolean refreshRunning = new AtomicBoolean();
    private volatile long generation;

    public TownWealthManager(WorldDynamicsEngine plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
    }

    public void start() {
        long activeGeneration = ++generation;
        refreshRunning.set(false);
        var config = plugin.getTownConfig();
        if (config == null || !config.getBoolean("wealth.enabled", true)) {
            return;
        }
        long intervalTicks = Math.max(20L, config.getLong("wealth.refresh-interval-ticks", 6000L));
        plugin.getServer().getScheduler().runTaskTimer(
                plugin, () -> refreshAllTowns(activeGeneration), 20L, intervalTicks);
    }

    private void refreshAllTowns(long activeGeneration) {
        if (activeGeneration != generation) {
            return;
        }
        if (!refreshRunning.compareAndSet(false, true)) {
            return;
        }

        List<TownSnapshot> snapshots;
        BigDecimal plotValue;
        BigDecimal residentValue;
        List<String> currencies;
        try {
            snapshots = collectTownSnapshots();
            plotValue = readPlotValue();
            residentValue = readResidentValue();
            currencies = List.copyOf(plugin.getCurrencyExchangeRates().keySet());
        } catch (RuntimeException exception) {
            if (activeGeneration == generation) refreshRunning.set(false);
            plugin.getLogger().warning("Could not collect town wealth inputs: " + exception.getMessage());
            return;
        }

        if (snapshots.isEmpty()) {
            if (activeGeneration == generation) refreshRunning.set(false);
            return;
        }

        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.hasProvider()) {
            if (activeGeneration == generation) refreshRunning.set(false);
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<WealthResult> results = new ArrayList<>(snapshots.size());
                for (TownSnapshot snapshot : snapshots) {
                    List<BigDecimal> balances = new ArrayList<>(snapshot.residents().size());
                    for (UUID residentId : snapshot.residents()) {
                        balances.add(economy.getTotalWealthAsDefault(residentId, snapshot.worldName(), currencies));
                    }
                    BigDecimal wealth = TownWealthCalculator.calculate(snapshot.population(), residentValue,
                            snapshot.plotCount(), plotValue, balances);
                    results.add(new WealthResult(snapshot.name(), wealth));
                }
                plugin.getServer().getScheduler().runTask(
                        plugin, () -> applyResults(results, activeGeneration));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not calculate town wealth: " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (activeGeneration == generation) refreshRunning.set(false);
                });
            }
        });
    }

    private List<TownSnapshot> collectTownSnapshots() {
        List<TownSnapshot> snapshots = new ArrayList<>();
        for (WDEtown record : townManager.getAllTowns()) {
            try {
                Town town = TownyUniverse.getInstance().getTown(record.getTownName());
                if (town == null) {
                    continue;
                }

                List<UUID> residentIds = new ArrayList<>();
                for (Resident resident : town.getResidents()) {
                    if (resident != null && resident.getUUID() != null) {
                        residentIds.add(resident.getUUID());
                    }
                }

                String worldName = "";
                if (town.getHomeblockWorld() != null) {
                    World world = town.getHomeblockWorld().getBukkitWorld();
                    if (world != null) {
                        worldName = world.getName();
                    }
                }

                snapshots.add(new TownSnapshot(town.getName(), Math.max(0, town.getResidents().size()),
                        Math.max(0, town.getNumTownBlocks()), List.copyOf(residentIds), worldName));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not snapshot town wealth for " + record.getTownName()
                        + ": " + exception.getMessage());
            }
        }
        return snapshots;
    }

    private BigDecimal readPlotValue() {
        return readWealthValue("wealth.plot-value");
    }

    private BigDecimal readResidentValue() {
        return readWealthValue("wealth.population-value");
    }

    private BigDecimal readWealthValue(String path) {
        String rawValue = plugin.getTownConfig().getString(path, "0.00");
        try {
            return new BigDecimal(rawValue == null ? "0.00" : rawValue.trim()).max(BigDecimal.ZERO);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Invalid town " + path + "; using 0.00.");
            return BigDecimal.ZERO;
        }
    }

    private void applyResults(List<WealthResult> results, long activeGeneration) {
        if (activeGeneration != generation) {
            return;
        }
        try {
            List<DataStorageManagerPayload> payloads = new ArrayList<>(results.size());
            long updatedAt = System.currentTimeMillis();
            for (WealthResult result : results) {
                WDEtown town = townManager.getTown(result.townName());
                if (town == null) {
                    continue;
                }
                town.setWealth(result.wealth().doubleValue());
                town.setWealthUpdatedAt(updatedAt);
                WDEtown persistedTown = WDEtown.deserialize(town.serialize());
                payloads.add(new DataStorageManagerPayload(town.getTownName(),
                        plugin.getDataStorageManager().prepareEntityPayload(persistedTown)));
            }

            if (payloads.isEmpty()) {
                refreshRunning.set(false);
                return;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    for (DataStorageManagerPayload payload : payloads) {
                        plugin.getDataStorageManager().saveEntityPayload(
                                STORAGE_CATEGORY, payload.townName(), payload.serialized());
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Could not persist town wealth: " + exception.getMessage());
                } finally {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (activeGeneration == generation) refreshRunning.set(false);
                    });
                }
            });
        } catch (RuntimeException exception) {
            if (activeGeneration == generation) refreshRunning.set(false);
            plugin.getLogger().severe("Could not prepare town wealth for persistence: " + exception.getMessage());
        }
    }

    private record DataStorageManagerPayload(String townName, String serialized) {}
}
