package com.thatguycy.worlddynamicsengine.model;

import java.util.Objects;
import com.thatguycy.worlddynamicsengine.util.TextColors;

/**
 * Describes a doctrine attitude, its weight, and optional compliance/violation mood-change overrides.
 */
public class IdeologyDoctrineAttitudeDefinition {
    private final String id;
    private final String title;
    private final String lore;
    private final String color;
    private final int moodWeight;
    private final int compliantMoodChange;
    private final int violationMoodChange;

    public IdeologyDoctrineAttitudeDefinition(String id, String title, String lore) {
        this(id, title, lore, "", 1, -1, -1);
    }

    public IdeologyDoctrineAttitudeDefinition(String id, String title, String lore, String color) {
        this(id, title, lore, color, 1, -1, -1);
    }

    public IdeologyDoctrineAttitudeDefinition(String id, String title, String lore, String color, int moodWeight) {
        this(id, title, lore, color, moodWeight, -1, -1);
    }

    public IdeologyDoctrineAttitudeDefinition(String id, String title, String lore, String color, int moodWeight,
                                              int compliantMoodChange, int violationMoodChange) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null ? "" : title.trim();
        this.lore = lore == null ? "" : lore.trim();
        this.color = color == null ? "" : color.trim();
        this.moodWeight = Math.max(0, Math.min(10, moodWeight));
        this.compliantMoodChange = clampMoodChange(compliantMoodChange);
        this.violationMoodChange = clampMoodChange(violationMoodChange);
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

    public int getMoodWeight() {
        return moodWeight;
    }

    /** Returns -1 when this attitude inherits the shared/configured mood change. */
    public int getCompliantMoodChange() {
        return compliantMoodChange;
    }

    /** Returns -1 when this attitude inherits the shared/configured mood change. */
    public int getViolationMoodChange() {
        return violationMoodChange;
    }

    private static int clampMoodChange(int value) {
        return value < 0 ? -1 : Math.min(100, value);
    }

    public String getDisplayTitle() {
        return TextColors.translateAlternateColorCodes('&', color + title);
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
