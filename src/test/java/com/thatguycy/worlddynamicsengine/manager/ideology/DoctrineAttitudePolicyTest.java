package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineMemeConstraint;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoctrineAttitudePolicyTest {
    private static final String ID = "namespace:wder/test";

    @Test
    void normalizesLocalAndFullIdsWithoutChangingLegacyNamespace() {
        assertEquals(ID + "/neutral", DoctrineAttitudePolicy.normalizeAttitudeId(ID, " NEUTRAL "));
        assertEquals(ID + "/neutral", DoctrineAttitudePolicy.normalizeAttitudeId(ID, ID.toUpperCase() + "/NEUTRAL"));
        assertEquals("namespace:wde/test/neutral",
                DoctrineAttitudePolicy.normalizeAttitudeId(ID, "namespace:wde/test/neutral"));
        assertNull(DoctrineAttitudePolicy.normalizeAttitudeId(ID, null));
        assertNull(DoctrineAttitudePolicy.normalizeAttitudeId(ID, "bad/value"));
        assertNull(DoctrineAttitudePolicy.normalizeAttitudeId("invalid", "neutral"));
    }

    @Test
    void rejectsMissingDefinitionsAndAttitudesEvenWithoutCultureContext() {
        var doctrine = definition(ID, List.of());
        assertFalse(DoctrineAttitudePolicy.canUse(null, "neutral", null));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "missing", null));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, null, List.of()));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "namespace:wder/other/neutral", List.of()));
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "neutral", null));
    }

    @Test
    void combinesBlocksFromEverySelectedMeme() {
        var doctrine = definition(ID, List.of(
                constraint("first", List.of("required"), List.of("neutral")),
                constraint("second", List.of("neutral"), List.of("required"))));
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "required", List.of("first")));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "neutral", List.of("first")));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "required", List.of("first", "second")));
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "other", List.of("first", "second")));
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "neutral", List.of("unconfigured")));
    }

    @Test
    void distinguishesDefinitionQueriesFromCulturesWithoutMemes() {
        var doctrine = definition("namespace:wder/nonhumanization", List.of());
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "required", null));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "required", List.of()));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "required", List.of("individualist")));
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "neutral", List.of()));
        assertTrue(DoctrineAttitudePolicy.canUse(doctrine, "required", List.of("nonhuman_alienation")));
    }

    @Test
    void nonhumanizationMemeDoesNotBypassOrdinaryBlocks() {
        String id = "namespace:wder/nonhumanization";
        var doctrine = definition(id, List.of(new IdeologyDoctrineMemeConstraint(
                "nonhuman_alienation", List.of(), List.of(id + "/required"))));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, "required", List.of("nonhuman_alienation")));
    }

    @Test
    void defaultsFollowMemeAndCandidateOrderSkippingMissingCandidates() {
        var doctrine = definition(ID, List.of(
                constraint("first", List.of("missing", "required"), List.of()),
                constraint("second", List.of("other"), List.of())));
        assertEquals(ID + "/required", DoctrineAttitudePolicy.resolveDefault(doctrine, List.of("first", "second")));
        assertEquals(ID + "/other", DoctrineAttitudePolicy.resolveDefault(doctrine, List.of("second", "first")));
        assertEquals(ID + "/neutral", DoctrineAttitudePolicy.resolveDefault(doctrine, List.of("unconfigured")));
        assertEquals(ID + "/neutral", DoctrineAttitudePolicy.resolveDefault(doctrine, null));
        assertNull(DoctrineAttitudePolicy.resolveDefault(null, List.of()));
    }

    @Test
    void keepsExistingDefaultSelectionSeparateFromCrossMemeValidation() {
        var doctrine = definition(ID, List.of(
                constraint("first", List.of("required"), List.of()),
                constraint("second", List.of("other"), List.of("required"))));
        var memes = List.of("first", "second");
        assertEquals(ID + "/required", DoctrineAttitudePolicy.resolveDefault(doctrine, memes));
        assertFalse(DoctrineAttitudePolicy.canUse(doctrine, ID + "/required", memes));
    }

    private static IdeologyDoctrineDefinition definition(String id, List<IdeologyDoctrineMemeConstraint> constraints) {
        return new IdeologyDoctrineDefinition(id, "Test", "", "",
                List.of("neutral", "required", "other").stream()
                        .map(value -> new IdeologyDoctrineAttitudeDefinition(id + "/" + value, value, "")).toList(),
                constraints, "test");
    }

    private static IdeologyDoctrineMemeConstraint constraint(String meme, List<String> defaults, List<String> blocks) {
        return new IdeologyDoctrineMemeConstraint(meme,
                defaults.stream().map(value -> ID + "/" + value).toList(),
                blocks.stream().map(value -> ID + "/" + value).toList());
    }
}
