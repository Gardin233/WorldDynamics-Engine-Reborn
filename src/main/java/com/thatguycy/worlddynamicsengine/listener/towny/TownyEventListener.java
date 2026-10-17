package com.thatguycy.worlddynamicsengine.listener.towny;

import com.palmergames.bukkit.towny.event.economy.TownyPreTransactionEvent;
import com.palmergames.bukkit.towny.event.NewDayEvent;
import com.palmergames.bukkit.towny.event.SpawnEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.time.dailytaxes.NewDayTaxAndUpkeepPreCollectionEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownToggleNeutralEvent;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.transaction.Transaction;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 衔接 Towny 与玩家战斗事件，处理有效击杀计分和防守方复活冻结等战争联动。
 */
public class TownyEventListener implements Listener {

    private final WorldDynamicsEngine plugin;
    private final OrgManager orgManager;
    private final EconomyBridge economyBridge;
    private final Random random = new Random();
    private boolean residentTaxCollectionActive = false;
    private boolean handlingResidentTaxOverride = false;

    public TownyEventListener(WorldDynamicsEngine plugin, OrgManager orgManager, EconomyBridge economyBridge) {
        this.plugin = plugin;
        this.orgManager = orgManager;
        this.economyBridge = economyBridge;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTownySpawn(SpawnEvent event) {
        Town town = TownyAPI.getInstance().getTown(event.getPlayer());
        var nation = town == null ? null : town.getNationOrNull();
        if (town == null || !(town.hasActiveWar() || orgManager.isTownInActiveWar(town.getName())
                || nation != null && orgManager.isNationInActiveWar(nation.getName()))) return;
        event.setCancelled(true);
        event.setCancelMessage(plugin.getLocaleMessage("war_towny_spawn_blocked"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaxCollectionStart(NewDayTaxAndUpkeepPreCollectionEvent event) {
        TownManager townManager = plugin.getTownManager();
        for (var town : townManager.getAllTowns()) {
            Town townyTown = com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(town.getTownName());
            if (townyTown != null) {
                townManager.syncTownTaxPolicy(townyTown);
            }
        }
        townManager.saveTowns();
        if (!plugin.getConfig().getBoolean("economy.townyResidentTaxOverride.enabled", true)) {
            return;
        }

        residentTaxCollectionActive = true;
        Bukkit.getScheduler().runTask(plugin, () -> residentTaxCollectionActive = false);
    }

    @EventHandler
    public void onTownyNewDay(NewDayEvent event) {
        residentTaxCollectionActive = false;

        List<WDEorg> orgs = orgManager.getAllOrganizations();
        for (WDEorg org : orgs) {
            double percentageChange = random.nextDouble(-0.1D, 0.3D);
            double modifier = 1 + percentageChange;
            double newBalance = org.getBalance() * modifier;
            orgManager.updateBalance(org, newBalance);
        }

        orgManager.processPeaceTreatyTributes(plugin.getNationManager());
        orgManager.saveOrganizations();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTownyResidentTax(TownyPreTransactionEvent event) {
        if (!residentTaxCollectionActive || handlingResidentTaxOverride) {
            return;
        }
        if (economyBridge == null || !economyBridge.hasModernEconomy()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("economy.townyResidentTaxOverride.enabled", true)) {
            return;
        }

        Transaction transaction = event.getTransaction();
        if (transaction == null || transaction.getAmount() <= 0 || !transaction.hasSenderAccount() || !transaction.hasReceiverAccount()) {
            return;
        }

        Account sendingAccount = transaction.getSendingAccount();
        Account receivingAccount = transaction.getReceivingAccount();
        if (sendingAccount == null || receivingAccount == null || !sendingAccount.isPlayerAccount()) {
            return;
        }
        if (!(receivingAccount.getEconomyHandler() instanceof Town town)) {
            return;
        }

        TownManager townManager = plugin.getTownManager();
        String senderPlayerName = resolvePlayerName(sendingAccount);
        if (townManager != null && senderPlayerName != null && townManager.isConscriptTaxExempt(town.getName(), senderPlayerName)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        BigDecimal requestedDefault = resolveRequestedResidentTax(town, sendingAccount, transaction);
        handlingResidentTaxOverride = true;
        try {
            EconomyBridge.CollectionResult result = economyBridge.collectMaximumAsDefault(
                    sendingAccount.getUUID(),
                    resolveWorldName(sendingAccount),
                    requestedDefault,
                    plugin.getCurrencyExchangeRates().keySet()
            );

            if (!result.collectedAnything()) {
                return;
            }

            receivingAccount.deposit(result.collectedDefault().doubleValue(), "WorldDynamicsEngine resident multi-currency tax");
            town.save();

            if (plugin.getConfig().getBoolean("economy.townyResidentTaxOverride.logPartialCollection", true)
                    && result.collectedDefault().compareTo(requestedDefault) < 0) {
                plugin.getLogger().info("Collected partial resident tax for town " + town.getName()
                        + ": requested " + economyBridge.format(requestedDefault)
                        + ", collected " + economyBridge.format(result.collectedDefault()) + ".");
            }
        } finally {
            handlingResidentTaxOverride = false;
        }
    }

    private String resolveWorldName(Account sendingAccount) {
        try {
            return sendingAccount.getWorld() != null ? sendingAccount.getWorld().getName() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String resolvePlayerName(Account sendingAccount) {
        try {
            if (sendingAccount.getUUID() == null) {
                return null;
            }
            com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(sendingAccount.getUUID());
            return resident != null ? resident.getName() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal resolveRequestedResidentTax(Town town, Account sendingAccount, Transaction transaction) {
        BigDecimal fallbackAmount = BigDecimal.valueOf(transaction.getAmount()).max(BigDecimal.ZERO);
        if (town == null || sendingAccount == null || !town.isTaxPercentage()) {
            return fallbackAmount;
        }

        BigDecimal totalWealthDefault = economyBridge.getTotalWealthAsDefault(
                sendingAccount.getUUID(),
                resolveWorldName(sendingAccount),
                plugin.getCurrencyExchangeRates().keySet()
        );
        if (totalWealthDefault.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal percentageRate = resolveTaxPercentageRate(town, sendingAccount, fallbackAmount);
        if (percentageRate.compareTo(BigDecimal.ZERO) <= 0) {
            return fallbackAmount;
        }

        BigDecimal requestedAmount = totalWealthDefault.multiply(percentageRate).setScale(8, RoundingMode.HALF_UP);
        double maxPercentTaxAmount = town.getMaxPercentTaxAmount();
        if (maxPercentTaxAmount > 0D) {
            requestedAmount = requestedAmount.min(BigDecimal.valueOf(maxPercentTaxAmount));
        }
        return requestedAmount.max(BigDecimal.ZERO);
    }

    private BigDecimal resolveTaxPercentageRate(Town town, Account sendingAccount, BigDecimal fallbackAmount) {
        double configuredTaxValue = town.getTaxes();
        if (configuredTaxValue <= 0D) {
            return BigDecimal.ZERO;
        }

        BigDecimal rawRate = BigDecimal.valueOf(configuredTaxValue);
        BigDecimal dividedRate = rawRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal holdingBalance = BigDecimal.valueOf(Math.max(0D, sendingAccount.getHoldingBalance()));
        if (holdingBalance.compareTo(BigDecimal.ZERO) <= 0 || fallbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return configuredTaxValue > 1D ? dividedRate : rawRate;
        }

        BigDecimal rawExpected = holdingBalance.multiply(rawRate).setScale(8, RoundingMode.HALF_UP);
        BigDecimal dividedExpected = holdingBalance.multiply(dividedRate).setScale(8, RoundingMode.HALF_UP);
        BigDecimal rawDiff = rawExpected.subtract(fallbackAmount).abs();
        BigDecimal dividedDiff = dividedExpected.subtract(fallbackAmount).abs();
        return dividedDiff.compareTo(rawDiff) <= 0 ? dividedRate : rawRate;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        orgManager.recordUnifiedWarKill(killer, victim);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!orgManager.isWarDefender(event.getPlayer())) {
            return;
        }

        orgManager.registerRespawnLock(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(plugin.formatLocaleMessage(plugin.userLang, "war_defender_respawn_locked",
                orgManager.getIndependenceWarRespawnLockSeconds()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!orgManager.isRespawnLocked(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTownToggleNeutral(TownToggleNeutralEvent event) {
        if (plugin.isRegularTownWarParticipationAllowed() || event.getFutureState()) {
            return;
        }

        event.setCancelled(true);
        if (event.getSender() != null) {
            event.getSender().sendMessage(plugin.formatLocaleMessage("town_regular_war_disabled"));
        }
    }

    private boolean isPlayerTownNeutral(Player player) {
        var resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        return resident != null && resident.hasTown() && resident.getTownOrNull() != null
                && resident.getTownOrNull().isNeutral();
    }

    private String resolvePlayerNationName(Player player) {
        try {
            com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
            if (resident == null || !resident.hasTown()) {
                return null;
            }

            Town town = resident.getTown();
            if (!town.hasNation()) {
                return null;
            }
            return town.getNation().getName();
        } catch (Exception ignored) {
            return null;
        }
    }
}
