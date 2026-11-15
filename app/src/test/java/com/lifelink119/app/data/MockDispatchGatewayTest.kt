package com.lifelink119.app.data

import com.lifelink119.app.core.DemoShareSelection
import com.lifelink119.app.core.EmergencyScenario
import org.junit.Assert.*
import org.junit.Test

class MockDispatchGatewayTest {
    @Test
    fun aNewSessionSharesNothingWithoutConsent() {
        val session = MockDispatchGateway().connect(EmergencyScenario.MEDICAL)
        assertNull(session.vehicle)
        assertNull(session.location)
        assertNull(session.videoLink)
        assertTrue(session.syntheticOnly)
    }

    @Test
    fun vehicleConsentDoesNotGrantLocationConsent() {
        val session =
            MockDispatchGateway()
                .connect(EmergencyScenario.VIDEO, DemoShareSelection(vehicle = true))
        assertEquals("DEMO-CAR-01", session.vehicle?.id)
        assertNull(session.location)
        assertNull(session.videoLink)
    }

    @Test
    fun locationUsesAFictionalGridAndDoesNotIncludeVehicleData() {
        val session =
            MockDispatchGateway()
                .connect(EmergencyScenario.TRANSPORT, DemoShareSelection(location = true))
        assertNotNull(session.location)
        assertNull(session.vehicle)
        assertEquals("가상 훈련장 A구역", session.location?.label)
    }

    @Test
    fun restoredSessionDoesNotCreateDuplicateTicketNumbers() {
        val gateway = MockDispatchGateway()
        val restored =
            gateway.restore(
                "DEMO-0007",
                EmergencyScenario.POLICE,
                DemoShareSelection(),
                MockDispatchGateway.Status.DEMO_CONNECTED,
            )
        assertEquals("DEMO-0007", restored.ticket)
        assertEquals("DEMO-0008", gateway.connect(EmergencyScenario.MEDICAL).ticket)
    }

    @Test
    fun closingASessionRetainsOnlyItsOriginalConsentChoices() {
        val gateway = MockDispatchGateway()
        val original =
            gateway.connect(EmergencyScenario.WELLBEING, DemoShareSelection(vehicle = true))
        val closed = gateway.close(original)
        assertEquals(MockDispatchGateway.Status.DEMO_CLOSED, closed.status)
        assertEquals(original.ticket, closed.ticket)
        assertNull(closed.location)
        assertNull(closed.videoLink)
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalIdentifiersCannotBeRestoredAsDemoSessions() {
        MockDispatchGateway()
            .restore(
                "REAL-119",
                EmergencyScenario.MEDICAL,
                DemoShareSelection(),
                MockDispatchGateway.Status.DEMO_CONNECTED,
            )
    }
}
