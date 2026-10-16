package com.thatguycy.worlddynamicsengine.model.background;

import java.util.Locale;

/**
 * 定义儿童与成年两个背景阶段，以及对应的配置和本地化键。
 */
public enum RoleBackgroundStage {
    CHILDHOOD("childhood", "background_stage_childhood"),
    ADULTHOOD("adulthood", "background_stage_adulthood");

    private final String configKey;
    private final String localeKey;

    RoleBackgroundStage(String configKey, String localeKey) {
        this.configKey = configKey;
        this.localeKey = localeKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getLocaleKey() {
        return localeKey;
    }

    public static RoleBackgroundStage fromString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "childhood", "child", "儿童时期", "儿童" -> CHILDHOOD;
            case "adulthood", "adult", "成年时期", "成年" -> ADULTHOOD;
            default -> null;
        };
    }
}
