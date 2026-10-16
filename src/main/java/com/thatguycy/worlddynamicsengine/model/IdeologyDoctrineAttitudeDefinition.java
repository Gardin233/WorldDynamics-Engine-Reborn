package com.thatguycy.worlddynamicsengine.model;

import java.util.Objects;
import org.bukkit.ChatColor;

/**
 * 描述一项戒律态度的标识和展示文本，供注册、默认选择及菜单渲染使用。
 */
public class IdeologyDoctrineAttitudeDefinition {
    private final String id;
    private final String title;
    private final String lore;
    private final String color;

    public IdeologyDoctrineAttitudeDefinition(String id, String title, String lore) {
        this(id, title, lore, "");
    }

    public IdeologyDoctrineAttitudeDefinition(String id, String title, String lore, String color) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null ? "" : title.trim();
        this.lore = lore == null ? "" : lore.trim();
        this.color = color == null ? "" : color.trim();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLore() {
        return lore;
    }

    public String getColor() {
        return color;
    }

    public String getDisplayTitle() {
        return ChatColor.translateAlternateColorCodes('&', color + title);
    }

    public boolean isValid() {
        return !id.isBlank() && !title.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeologyDoctrineAttitudeDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
