package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.model.WDEChurch;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.World;

/**
 * 管理只存不取的意识形态金库与什一税；扣费后持久化失败时尝试原币种退款。
 */
public final class IdeologyTreasuryManager {
    /**
     * 区分献金成功、参数错误、依赖不可用、余额不足及持久化失败。
     */
    public enum DonationStatus { SUCCESS, INVALID_AMOUNT, NOT_FOUND, UNAVAILABLE, INSUFFICIENT_FUNDS, STORAGE_ERROR }
    /**
     * 返回献金结果和金库余额，供命令及菜单反馈。
     */
    public record DonationResult(DonationStatus status, BigDecimal balance) {}

    private final WorldDynamicsEngine plugin;
    private boolean collecting;

    public IdeologyTreasuryManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    public static boolean validDonationAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.scale() <= 8
                && amount.compareTo(WDEreligion.MAX_FIXED_TITHE) <= 0;
    }

    public DonationResult donate(Player player, String religionName, BigDecimal amount) {
        if (!validDonationAmount(amount)) return result(DonationStatus.INVALID_AMOUNT);
        WDEreligion religion = plugin.getReligionManager().getReligion(religionName);
        if (religion == null) return result(DonationStatus.NOT_FOUND);
        if (religion.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL) {
            return result(DonationStatus.UNAVAILABLE);
        }
        EconomyBridge economy = plugin.getEconomyBridge();
        if (player == null || !plugin.religionEnabled || !treasuryEnabled()
                || economy == null || !economy.supportsExactCurrency(economy.getDefaultCurrency())) {
            return result(DonationStatus.UNAVAILABLE);
        }
        String world = player.getWorld().getName();
        EconomyBridge.Charge charge = economy.charge(amount, economy.getDefaultCurrency());
        if (economy.getBalance(player.getUniqueId(), world, charge.currency()).compareTo(amount) < 0
                || !economy.withdrawExact(player.getUniqueId(), world, charge).success()) {
            return result(DonationStatus.INSUFFICIENT_FUNDS);
        }
        try {
            BigDecimal balance = plugin.getReligionManager().creditTreasury(religionName, amount);
            return new DonationResult(DonationStatus.SUCCESS, balance);
        } catch (RuntimeException ex) {
            refund(economy, player.getUniqueId(), world, Map.of(charge.currency(), amount));
            plugin.getLogger().severe("Failed to persist ideology donation for " + religionName + ": " + ex.getMessage());
            return result(DonationStatus.STORAGE_ERROR);
        }
    }

    public void collectDailyTithes() {
        if (collecting || !plugin.religionEnabled || !treasuryEnabled()
                || !plugin.getIdeologyConfig().getBoolean("economy.treasury.titheEnabled", true)) return;
        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.hasProvider()) return;
        String worldName = plugin.getIdeologyConfig().getString("economy.treasury.taxWorld", "world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Ideology tithe skipped: tax world is unavailable: " + worldName);
            return;
        }
        collecting = true;
        try {
            for (var resident : plugin.getResidentManager().getAllResidents()) {
                WDEreligion religion = plugin.getReligionManager().getReligion(resident.getReligionName());
                if (religion == null || religion.getTitheAmount().signum() <= 0) continue;
                WDEChurch church = null;
                if (religion.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL) {
                    church = plugin.getIdeologyChurchManager().getActiveChurchForResident(resident, religion);
                    if (church == null) continue;
                } else if (plugin.getReligionManager().isCardinalTitheExempt(resident.getUserUUID(), religion)) {
                    continue;
                }
                try {
                    BigDecimal wealth = religion.getTitheType() == WDEreligion.TitheType.WEALTH_PERCENTAGE
                            ? economy.getTotalWealthAsDefault(resident.getUserUUID(), worldName,
                                    plugin.getCurrencyExchangeRates().keySet()) : BigDecimal.ZERO;
                    BigDecimal requested = religion.calculateTithe(wealth);
                    var collection = economy.collectMaximumAsDefault(resident.getUserUUID(), worldName,
                            requested, plugin.getCurrencyExchangeRates().keySet());
                    if (!collection.collectedAnything()) continue;
                    try {
                        if (church == null) {
                            plugin.getReligionManager().creditTreasury(religion.getReligionName(), collection.collectedDefault());
                        } else {
                            plugin.getIdeologyChurchManager().creditTithe(church,
                                    collection.collectedDefault(), resident.getUserUUID());
                        }
                    } catch (RuntimeException ex) {
                        refund(economy, resident.getUserUUID(), worldName, collection.withdrawnAmounts());
                        throw ex;
                    }
                    Player online = plugin.getServer().getPlayer(resident.getUserUUID());
                    if (online != null) online.sendMessage(plugin.formatLocaleMessage(plugin.userLang,
                            church == null ? "religion_tithe_collected" : "church_tithe_collected",
                            church == null ? religion.getReligionName() : church.getChurchName(),
                            economy.format(collection.collectedDefault())));
                    if (!collection.fullyCovered()
                            && plugin.getIdeologyConfig().getBoolean("economy.treasury.logPartialCollection", true)) {
                        plugin.getLogger().info("Partial ideology tithe for " + resident.getUserUUID()
                                + " in " + religion.getReligionName() + ": " + collection.collectedDefault() + " / " + requested);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("Failed to collect ideology tithe for " + resident.getUserUUID() + ": " + ex.getMessage());
                }
            }
        } finally {
            collecting = false;
        }
    }

    public boolean treasuryEnabled() {
        return plugin.getIdeologyConfig().getBoolean("economy.treasury.enabled", true);
    }

    private void refund(EconomyBridge economy, UUID uuid, String world, Map<String, BigDecimal> amounts) {
        // This compensates a failed deposit before it reaches the treasury, not a treasury withdrawal.
        for (var entry : amounts.entrySet()) {
            try {
                if (!economy.depositExact(uuid, world, economy.charge(entry.getValue(), entry.getKey())).success()) {
                    plugin.getLogger().severe("Ideology payment refund failed: player=" + uuid
                            + ", currency=" + entry.getKey() + ", amount=" + entry.getValue());
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Ideology payment refund failed for " + uuid + ": " + ex.getMessage());
            }
        }
    }

    private DonationResult result(DonationStatus status) {
        return new DonationResult(status, BigDecimal.ZERO);
    }
}
