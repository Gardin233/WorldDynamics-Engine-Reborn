package com.thatguycy.worlddynamicsengine.manager.economy;

import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.BankAccount;
import java.math.BigDecimal;

/**
 * 统一换算、展示和扣除 Towny 公共金库费用；角色权限及业务失败后的补偿仍由调用方负责。
 */
public final class GovernmentTreasury {
    private GovernmentTreasury() {}

    /**
     * 金库以默认货币记账。保持币种大小写敏感及缺少经济桥时沿用原金额的既有行为。
     */
    public static BigDecimal amountInDefaultCurrency(EconomyBridge.Charge charge,
                                                    EconomyBridge economy,
                                                    String defaultCurrency) {
        if (charge == null || charge.amount() == null) {
            return BigDecimal.ZERO;
        }
        if (economy == null || charge.currency() == null || charge.currency().equals(defaultCurrency)) {
            return charge.amount();
        }
        return economy.convert(charge.amount(), charge.currency(), defaultCurrency);
    }

    public static String format(EconomyBridge.Charge charge, EconomyBridge economy, String defaultCurrency) {
        BigDecimal amount = amountInDefaultCurrency(charge, economy, defaultCurrency);
        return economy == null
                ? amount.toPlainString() + " " + defaultCurrency
                : economy.format(new EconomyBridge.Charge(amount, defaultCurrency));
    }

    /**
     * 免费费用不访问账户；付费先检查余额，成功后保存对应国家或城镇，账户或保存异常返回失败。
     */
    public static boolean withdraw(Government government, EconomyBridge.Charge charge,
                                   EconomyBridge economy, String defaultCurrency, String reason) {
        BigDecimal amount = amountInDefaultCurrency(charge, economy, defaultCurrency);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        try {
            BankAccount account = government.getAccount();
            if (account == null || !account.canPayFromHoldings(amount.doubleValue())) {
                return false;
            }
            boolean success = account.withdraw(amount.doubleValue(), reason);
            if (success) {
                if (government instanceof Town town) {
                    town.save();
                } else if (government instanceof Nation nation) {
                    nation.save();
                }
            }
            return success;
        } catch (Exception ignored) {
            return false;
        }
    }
}
