package com.lifelink119.app.core

import org.junit.Assert.*
import org.junit.Test

class DemoSignalPolicyTest {
    private val approved = DemoSignalSnapshot("PREVIEW_ACTIVE","119","SIM-LIGHT-DEMO-12340001-4",120,true)
    private fun allowed(s: DemoSignalSnapshot=approved, id: String?="DEMO-12340001", now: Long=2000,
        last: Long=1000, deadline: Long=121000, foreground: Boolean=true, connected: Boolean=true) =
        DemoSignalPolicy.canDisplay(s,id,now,last,deadline,foreground,connected)

    @Test fun validBoundedLocalApprovalAllowsOnlyTheScreenPreview() { assertTrue(allowed()); assertTrue(allowed(approved.copy(authority="112"))) }
    @Test fun requestOrConfirmationIsNotApproval() { for(state in listOf("OFF","REQUESTED","CONFIRMED","APPROVED","REVOKED","EXPIRED","REJECTED")) assertFalse(allowed(approved.copy(state=state))) }
    @Test fun only119Or112RoleAndMatchingSessionAreAccepted() { assertFalse(allowed(approved.copy(authority="109")));assertFalse(allowed(approved.copy(authority=null)));assertFalse(allowed(id="DEMO-OTHER"));assertFalse(allowed(id=null));assertFalse(allowed(approved.copy(approvalId=null))) }
    @Test fun staleOrLostConnectionImmediatelyBlocksPreview() { assertFalse(allowed(now=9000));assertFalse(allowed(connected=false));assertFalse(allowed(last=0));assertFalse(allowed(now=500)) }
    @Test fun backgroundBlocksPreview() { assertFalse(allowed(foreground=false)) }
    @Test fun expiryAndInvalidDurationBlockPreview() { assertFalse(allowed(deadline=2000));assertFalse(allowed(approved.copy(remainingSeconds=0)));assertFalse(allowed(approved.copy(remainingSeconds=121))) }
    @Test fun physicalOrNonSimulationResponsesCannotEnablePreview() { assertFalse(allowed(approved.copy(simulationOnly=false)));assertFalse(allowed(approved.copy(hardwareConnected=true)));assertFalse(allowed(approved.copy(previewEnabled=false))) }
}
