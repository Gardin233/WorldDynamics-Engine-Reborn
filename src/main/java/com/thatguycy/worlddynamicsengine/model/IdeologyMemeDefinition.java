package com.thatguycy.worlddynamicsengine.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import com.thatguycy.worlddynamicsengine.util.TextColors;

/**
 * 描述模因的标识、展示信息、图标和互斥关系，供意识形态组合校验使用。
 */
public class IdeologyMemeDefinition {
    private final String id;
    private final String title;
    private final String description;
    private final String color;
    private final String icon;
    private final String source;
    private final Set<String> exclusiveWith;

    public IdeologyMemeDefinition(String id, String title, String description) {
        this(id, title, description, "", "", "config", Collections.emptySet());
    }

    public IdeologyMemeDefinition(String id, String title, String description, String source) {
        this(id, title, description, "", "", source, Collections.emptySet());
    }

    public IdeologyMemeDefinition(String id, String title, String description, String source, Collection<String> exclusiveWith) {
        this(id, title, description, "", "", source, exclusiveWith);
    }

    public IdeologyMemeDefinition(String id, String title, String description, String icon, String source, Collection<String> exclusiveWith) {
        this(id, title, description, "", icon, source, exclusiveWith);
    }

    public IdeologyMemeDefinition(String id, String title, String description, String color, String icon, String source, Collection<String> exclusiveWith) {
        this.id = normalizeId(id);
        this.title = title == null ? "" : title.trim();
        this.description = description == null ? "" : description.trim();
        this.color = color == null ? "" : color.trim();
        this.icon = icon == null ? "" : icon.trim();
        this.source = source == null || source.isBlank() ? "unknown" : source.trim();
        this.exclusiveWith = new LinkedHashSet<>();
        if (exclusiveWith != null) {
            for (String exclusiveMemeId : exclusiveWith) {
                String normalizedExclusiveMemeId = normalizeId(exclusiveMemeId);
                if (!normalizedExclusiveMemeId.isBlank() && !normalizedExclusiveMemeId.equals(this.id)) {
                    this.exclusiveWith.add(normalizedExclusiveMemeId);
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    public String getDisplayTitle() {
        return TextColors.translateAlternateColorCodes('&', color + title);
    }

    public String getIcon() {
        return icon;
    }

    public String getSource() {
        return source;
    }

    public Set<String> getExclusiveWith() {
        return Collections.unmodifiableSet(exclusiveWith);
    }

    public boolean isExclusiveWith(String memeId) {
        return exclusiveWith.contains(normalizeId(memeId));
    }

    public boolean isValid() {
        return !id.isBlank() && !title.isBlank();
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeologyMemeDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
