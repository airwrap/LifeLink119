package com.lifelink119.app.core

import com.lifelink119.app.data.MockDispatchGateway
import org.junit.Assert.*
import org.junit.Test

class DemoWorkflowTest {
    @Test fun defaultSelectionsContainNoVideo() {
        assertFalse(DemoShareSelection().video)
        assertNull(MockDispatchGateway().connect(EmergencyScenario.VIDEO).videoLink)
    }
    @Test fun selectedVideoIsSyntheticAndSurvivesRestore() {
        val gateway = MockDispatchGateway()
        val session = gateway.connect(EmergencyScenario.VIDEO, DemoShareSelection(video = true), "VIDEO_SCENE_B")
        assertEquals("demo://video/${session.ticket}", session.videoLink)
        val restored = gateway.restore(session.ticket, session.scenario, session.selection, session.status, session.caseCode)
        assertEquals(session, restored)
    }
    @Test(expected = IllegalArgumentException::class) fun videoCannotBeAttachedToPoliceScenario() {
        MockDispatchGateway().connect(EmergencyScenario.POLICE, DemoShareSelection(video = true))
    }
    @Test(expected = IllegalArgumentException::class) fun caseCannotCrossScenario() {
        MockDispatchGateway().connect(EmergencyScenario.MEDICAL, caseCode = "POLICE_REVIEW_A")
    }
    @Test fun eachScenarioHasDistinctSyntheticOptions() {
        val options = EmergencyScenario.entries.flatMap { DemoCases.options(it) }
        assertEquals(10, options.size)
        assertEquals(options.size, options.map { it.code }.toSet().size)
        for (scenario in EmergencyScenario.entries) assertTrue(DemoCases.accepts(scenario, DemoCases.defaultCode(scenario)))
    }
    @Test fun batteryCapabilityDoesNotAllowWriteOnly() {
        assertNull(BatteryReadPolicy.capability("READ"))
        assertNull(BatteryReadPolicy.capability("READ_WRITE"))
        assertEquals(DiagnosticStatus.UNSUPPORTED, BatteryReadPolicy.capability("WRITE")!!.status)
        assertEquals(DiagnosticStatus.UNSUPPORTED, BatteryReadPolicy.capability("NONE")!!.status)
    }
    @Test fun batteryNullAndZeroAreDifferentAndRawValueIsNotRescaled() {
        assertEquals(DiagnosticStatus.UNAVAILABLE, BatteryReadPolicy.position(null).status)
        assertEquals(DiagnosticStatus.READ, BatteryReadPolicy.position(0).status)
        assertTrue(BatteryReadPolicy.position(7200).message.contains("7200"))
    }
}
