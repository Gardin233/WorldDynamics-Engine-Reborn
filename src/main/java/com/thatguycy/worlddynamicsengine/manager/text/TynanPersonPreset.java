package com.thatguycy.worlddynamicsengine.manager.text;

/** A player-owned person preset modeled after TynanArtSimulator's local presets. */
public record TynanPersonPreset(String name, String title, String pronounId, boolean auto) {
    public TynanPersonPreset {
        name = name == null ? "" : name.trim();
        title = title == null ? "" : title.trim();
        pronounId = pronounId == null || pronounId.isBlank() ? "he" : pronounId.trim();
    }
}
