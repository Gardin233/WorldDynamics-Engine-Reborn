package com.thatguycy.worlddynamicsengine.command.admin;

import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 记录管理员操作的真实执行者、命令参数和结果，并清理控制字符以避免日志注入。
 */
public final class AdminAuditLogger {
    private final Logger logger;

    public AdminAuditLogger(Logger logger) {
        this.logger = logger;
    }

    public void record(CommandSender sender, String[] args, String result) {
        String identity = sender.getName();
        if (sender instanceof Player player) identity += " (" + player.getUniqueId() + ")";
        logger.info("[ADMIN] actor=" + clean(identity) + " command=/wde " + clean(String.join(" ", args))
                + " result=" + clean(result));
    }

    private String clean(String value) {
        return value.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
    }
}
