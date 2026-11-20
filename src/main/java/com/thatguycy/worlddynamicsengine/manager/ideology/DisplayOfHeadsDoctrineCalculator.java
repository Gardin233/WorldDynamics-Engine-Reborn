package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

/** Classifies nearby skull blocks against the display-of-heads doctrine. */
public final class DisplayOfHeadsDoctrineCalculator {
    private static final Set<Material> HEAD_BLOCKS = Set.of(
            Material.CREEPER_HEAD,
            Material.CREEPER_WALL_HEAD,
            Material.DRAGON_HEAD,
            Material.DRAGON_WALL_HEAD,
            Material.PIGLIN_HEAD,
            Material.PIGLIN_WALL_HEAD,
            Material.PLAYER_HEAD,
            Material.PLAYER_WALL_HEAD,
            Material.SKELETON_SKULL,
            Material.SKELETON_WALL_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.WITHER_SKELETON_WALL_SKULL,
            Material.ZOMBIE_HEAD,
            Material.ZOMBIE_WALL_HEAD);

    private DisplayOfHeadsDoctrineCalculator() {}

    public static boolean isHeadBlock(Material material) {
        return material != null && HEAD_BLOCKS.contains(material);
    }

    public static Boolean assess(String attitude, boolean hasNearbyHead) {
        String localId = localId(attitude);
        return switch (localId) {
            case "disgust" -> !hasNearbyHead;
            case "crave" -> hasNearbyHead;
            default -> null;
        };
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
    }
}
