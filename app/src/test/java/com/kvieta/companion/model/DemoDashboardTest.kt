package com.kvieta.companion.model

import org.junit.Assert.*
import org.junit.Test

class DemoDashboardTest {
    @Test fun personalCannotMakeFamilyDecisions() {
        val state = DemoDashboard(DemoRole.PERSONAL)
        assertEquals(state, state.decide(DemoAction.APPROVE))
    }
    @Test fun offlineCannotApproveOrReject() {
        val state = DemoDashboard(DemoRole.PARENT, offline = true)
        DemoAction.entries.forEach { assertEquals(state, state.decide(it)) }
    }
    @Test fun approvalDoesNotAddTimeOrClaimApplied() {
        val state = DemoDashboard(DemoRole.PARENT)
        val approved = state.decide(DemoAction.APPROVE)
        assertEquals(DemoDecision.APPROVED_AWAITING_DEVICE, approved.decision)
        assertEquals(state.remainingMinutes, approved.remainingMinutes)
        assertFalse(approved.canDecide)
    }
    @Test fun repeatedOrConflictingDecisionDoesNotChangeResult() {
        val approved = DemoDashboard(DemoRole.PARENT).decide(DemoAction.APPROVE)
        DemoAction.entries.forEach { assertEquals(approved, approved.decide(it)) }
        val rejected = DemoDashboard(DemoRole.PARENT).decide(DemoAction.REJECT)
        assertEquals(rejected, rejected.decide(DemoAction.APPROVE))
    }
    @Test fun usageSummaryMatchesApplications() {
        val state = DemoDashboard(DemoRole.PERSONAL)
        assertEquals(state.usedMinutes, state.applications.sumOf { it.minutes })
    }
}
