package com.thatguycy.worlddynamicsengine.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.ChatColor;

/**
 * 描述戒律及其可选态度、图标和模因约束，不直接执行玩家行为效果。
 */
public class IdeologyDoctrineDefinition {
    private final String id;
    private final String title;
    private final String lore;
    private final String color;
    private final String icon;
    private final Map<String, IdeologyDoctrineAttitudeDefinition> attitudes;
    private final Map<String, IdeologyDoctrineMemeConstraint> memeConstraints;
    private final String source;
    private final String defaultAttitudeId;

    public IdeologyDoctrineDefinition(String id,
                                      String title,
                                      String lore,
                                      String icon,
                                      Collection<IdeologyDoctrineAttitudeDefinition> attitudes,
                                      Collection<IdeologyDoctrineMemeConstraint> memeConstraints,
                                      String source) {
        this(id, title, lore, "", icon, attitudes, memeConstraints, source);
    }

    public IdeologyDoctrineDefinition(String id,
                                      String title,
                                      String lore,
                                      String color,
                                      String icon,
                                      Collection<IdeologyDoctrineAttitudeDefinition> attitudes,
                                      Collection<IdeologyDoctrineMemeConstraint> memeConstraints,
                                      String source) {
        this.id = normalizeId(id);
        this.title = title == null ? "" : title.trim();
        this.lore = lore == null ? "" : lore.trim();
        this.color = color == null ? "" : color.trim();
        this.icon = icon == null ? "" : icon.trim();
        this.attitudes = new LinkedHashMap<>();
        if (attitudes != null) {
            for (IdeologyDoctrineAttitudeDefinition attitude : attitudes) {
                if (attitude != null && attitude.isValid()) {
                    this.attitudes.put(attitude.getId(), attitude);
                }
            }
        }

        this.memeConstraints = new LinkedHashMap<>();
        if (memeConstraints != null) {
            for (IdeologyDoctrineMemeConstraint constraint : memeConstraints) {
                if (constraint != null && !constraint.getMemeId().isBlank()) {
                    this.memeConstraints.put(constraint.getMemeId(), constraint);
                }
            }
        }
        this.source = source == null || source.isBlank() ? "unknown" : source.trim();
        this.defaultAttitudeId = this.attitudes.isEmpty() ? "" : this.attitudes.values().iterator().next().getId();
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
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

    public String getIcon() {
        return icon;
    }

    public Map<String, IdeologyDoctrineAttitudeDefinition> getAttitudes() {
        return Collections.unmodifiableMap(attitudes);
    }

    public IdeologyDoctrineAttitudeDefinition getAttitude(String attitudeId) {
        return attitudes.get(attitudeId);
    }

    public Map<String, IdeologyDoctrineMemeConstraint> getMemeConstraints() {
        return Collections.unmodifiableMap(memeConstraints);
    }

    public IdeologyDoctrineMemeConstraint getMemeConstraint(String memeId) {
        return memeId == null ? null : memeConstraints.get(memeId);
    }

    public String getSource() {
        return source;
    }

    public String getDefaultAttitudeId() {
        return defaultAttitudeId;
    }

    public boolean isValid() {
        return !id.isBlank() && !title.isBlank() && !defaultAttitudeId.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeologyDoctrineDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
