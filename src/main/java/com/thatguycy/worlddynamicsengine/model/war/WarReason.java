package com.thatguycy.worlddynamicsengine.model.war;

import java.util.Locale;

/**
 * 定义战争理由及命令解析标识；独立战争的使用资格由宣战流程进一步校验。
 */
public enum WarReason {
    CONQUEST, RECONQUEST, INDEPENDENCE, HOLY_WAR, TRADE_CONFLICT,
    HUMILIATE_RIVAL, EXPORT_REVOLUTION, DESTROY_THREAT;

    public static WarReason parse(String raw) {
        if (raw == null) return null;
        try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
    public String messageKey() { return "war_reason_" + name().toLowerCase(Locale.ROOT); }
}
