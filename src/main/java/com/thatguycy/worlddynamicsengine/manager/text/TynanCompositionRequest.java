package com.thatguycy.worlddynamicsengine.manager.text;

import java.util.List;
import java.util.Map;

/** Inputs for one or more deterministic Tynan-style art-description combinations. */
public record TynanCompositionRequest(
        String taleId,
        String carrierId,
        Map<String, String> slots,
        String language,
        String mode,
        String branch,
        double styleLevel,
        String artStyle,
        String conceptTone,
        List<String> styleClauseIds,
        boolean includeContext,
        int count,
        String seed) {

    public TynanCompositionRequest {
        taleId = valueOr(taleId, "");
        carrierId = valueOr(carrierId, "");
        slots = slots == null ? Map.of() : Map.copyOf(slots);
        language = "en".equalsIgnoreCase(language) ? "en" : "zh";
        mode = "strict".equalsIgnoreCase(mode) ? "strict" : "light";
        branch = switch (valueOr(branch, "auto").toLowerCase()) {
            case "tale" -> "tale";
            case "taleless" -> "taleless";
            default -> "auto";
        };
        styleLevel = Double.isFinite(styleLevel) ? Math.max(0.0D, Math.min(2.0D, styleLevel)) : 1.0D;
        artStyle = valueOr(artStyle, "auto");
        conceptTone = valueOr(conceptTone, "auto");
        styleClauseIds = styleClauseIds == null ? List.of() : List.copyOf(styleClauseIds);
        count = Math.max(1, Math.min(20, count));
        seed = valueOr(seed, "");
    }

    public static TynanCompositionRequest defaults(String taleId, String carrierId,
                                                   Map<String, String> slots) {
        return new TynanCompositionRequest(
                taleId, carrierId, slots, "zh", "light", "auto", 1.0D,
                "auto", "auto", List.of(), true, 1, "");
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
