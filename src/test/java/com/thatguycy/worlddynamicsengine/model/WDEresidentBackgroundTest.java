package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WDEresidentBackgroundTest {
    @Test
    void roundTripsStageSnapshotsAndLegacyBackgroundFields() {
        WDEresident resident = new WDEresident(UUID.randomUUID(), "Ada");
        resident.setChildhoodBackgroundId("farm_child");
        resident.setBackgroundTitle(RoleBackgroundStage.CHILDHOOD, "Farm child");
        resident.setBackgroundStory(RoleBackgroundStage.CHILDHOOD, "Raised near the fields.");
        resident.setBackgroundSkillPoints(RoleBackgroundStage.CHILDHOOD,
                Map.of("auraskills/farming", 5));
        resident.setRoleBackgroundId("legacy_role");
        resident.setRoleBackgroundTitle("Legacy title");
        resident.setRoleBackgroundStory("Legacy story");
        resident.setRoleBackgroundSkillPoints(Map.of("auraskills/mining", 5));

        WDEresident restored = WDEresident.deserialize(resident.serialize());

        assertEquals("farm_child", restored.getChildhoodBackgroundId());
        assertEquals("Farm child", restored.getBackgroundTitle(RoleBackgroundStage.CHILDHOOD));
        assertEquals("Raised near the fields.", restored.getBackgroundStory(RoleBackgroundStage.CHILDHOOD));
        assertEquals(Map.of("auraskills/farming", 5),
                restored.getBackgroundSkillPoints(RoleBackgroundStage.CHILDHOOD));
        assertEquals("legacy_role", restored.getRoleBackgroundId());
        assertEquals("Legacy title", restored.getRoleBackgroundTitle());
        assertEquals("Legacy story", restored.getRoleBackgroundStory());
        assertEquals(Map.of("auraskills/mining", 5), restored.getRoleBackgroundSkillPoints());
        assertTrue(restored.hasSelectedRoleBackground());
    }

    @Test
    void olderResidentArchivesDefaultNewBackgroundSnapshotsToEmpty() {
        WDEresident resident = WDEresident.deserialize(Map.of(
                "userUUID", UUID.randomUUID().toString(),
                "username", "OldResident",
                "childhoodBackgroundId", "farm_child",
                "adulthoodBackgroundId", "frontier_farmer"));

        assertEquals("", resident.getBackgroundTitle(RoleBackgroundStage.CHILDHOOD));
        assertEquals("", resident.getBackgroundStory(RoleBackgroundStage.ADULTHOOD));
        assertEquals(Map.of(), resident.getBackgroundSkillPoints(RoleBackgroundStage.CHILDHOOD));
        assertTrue(!resident.hasSelectedRoleBackground());
    }
}
