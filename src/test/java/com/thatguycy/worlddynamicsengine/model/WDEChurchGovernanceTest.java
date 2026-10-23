package com.thatguycy.worlddynamicsengine.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WDEChurchGovernanceTest {
    @Test
    void churchMembershipTreasuryAndSanctionRoundTrip() {
        UUID churchId = UUID.randomUUID();
        UUID ideologyId = UUID.randomUUID();
        UUID head = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        WDEChurch church = new WDEChurch(churchId, ideologyId, "Source", "North", head, "Head", 1L);
        church.addMember(member, "Member", 2L);
        church.creditTreasury(new BigDecimal("125"), UUID.randomUUID(), member.toString(), 3L);
        church.imposeSanction("reason", 1000L, 4L, UUID.randomUUID());

        WDEChurch restored = WDEChurch.deserialize(church.serialize());

        assertEquals(WDEChurch.State.ACTIVE, restored.getState());
        assertEquals(head, restored.getHeadUuid());
        assertTrue(restored.isMember(member));
        assertEquals(new BigDecimal("125"), restored.getTreasuryBalance());
        assertTrue(restored.isSanctioned(500L));
        assertEquals("reason", restored.getSanctionReason());
    }

    @Test
    void unionMarkerMakesTreasurySettlementAuditable() {
        UUID operation = UUID.randomUUID();
        WDEChurch church = new WDEChurch(UUID.randomUUID(), UUID.randomUUID(),
                "Source", "North", UUID.randomUUID(), "Head", 1L);
        church.creditTreasury(new BigDecimal("75"), UUID.randomUUID(), "member", 2L);

        church.markMerged(operation, new BigDecimal("75"), 3L);
        WDEChurch restored = WDEChurch.deserialize(church.serialize());

        assertEquals(WDEChurch.State.MERGED, restored.getState());
        assertEquals(operation, restored.getTreasurySettlementOperation());
        assertEquals(new BigDecimal("75"), restored.getSettledTreasuryAmount());
        assertEquals(BigDecimal.ZERO, restored.getTreasuryBalance());
        assertThrows(IllegalStateException.class,
                () -> restored.markMerged(operation, new BigDecimal("75"), 4L));
    }

    @Test
    void councilProposalKeepsVoteAndDecisionAcrossReload() {
        UUID proposalId = UUID.randomUUID();
        UUID ideologyId = UUID.randomUUID();
        UUID churchId = UUID.randomUUID();
        UUID voterChurch = UUID.randomUUID();
        IdeologyCouncilProposal proposal = new IdeologyCouncilProposal(
                proposalId, ideologyId, "Source", churchId, UUID.randomUUID(), "Head",
                IdeologyCouncilProposal.Type.SET_TITHE, null,
                Map.of("titheType", "FIXED", "titheAmount", "10"),
                "council-approved reason", 1L, 100L);
        proposal.castVote(voterChurch, "YES");
        proposal.approve(5, 4, 1, 50L);
        proposal.complete(60L, "SOURCE_DEFINITION_UPDATED");

        IdeologyCouncilProposal restored = IdeologyCouncilProposal.deserialize(proposal.serialize());

        assertEquals(IdeologyCouncilProposal.State.COMPLETED, restored.getState());
        assertEquals("YES", restored.getVotes().get(voterChurch.toString()));
        assertEquals(5, restored.getTotalWeight());
        assertEquals(4, restored.getYesWeight());
        assertEquals("SOURCE_DEFINITION_UPDATED", restored.getOutcome());
    }
}
