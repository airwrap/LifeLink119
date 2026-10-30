package com.lifelink119.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowPolicyTest {
    @Test fun medicalUses119() {
        assertEquals("119 (모의)", FlowPolicy.agency(EmergencyScenario.MEDICAL))
    }
    @Test fun wellbeingUses109() {
        assertEquals("109 (모의)", FlowPolicy.agency(EmergencyScenario.WELLBEING))
    }
    @Test fun policeSupportNeedsProfessionalDecision() {
        assertTrue(FlowPolicy.requiresProfessionalDecision(EmergencyScenario.POLICE))
    }
}
