package com.lifelink119.app.core

import com.lifelink119.app.data.CooperationCatalogParser
import com.lifelink119.app.data.ExperienceCatalogParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class OfflineCooperationTest {
    private var now = 1000L
    private val fixture =
        CooperationCatalogParser.parse(File("src/main/assets/cooperation-catalog.json").readText())

    private fun model(caseId: String = "EARLY_HELP") =
        OfflineCooperationEngine(fixture, { now }).apply {
            attach("DEMO-12345678901234567")
            request(caseId, true)
        }

    private fun authorized() =
        model().apply {
            review()
            invite("DEMO-HELPER-A")
            respond(true)
            authorize()
        }

    private fun approved() =
        authorized().apply {
            requestBeacon("119")
            reviewBeacon("119")
            decideBeacon("119", true)
        }

    private fun refuses(block: () -> Unit) {
        try {
            block()
            fail("Invalid transition accepted")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Test
    fun explicitConsentAndBoundSessionAreRequired() {
        val m = OfflineCooperationEngine(fixture, { now })
        refuses { m.request("EARLY_HELP", true) }
        m.attach("DEMO-1234")
        refuses { m.request("EARLY_HELP", false) }
        m.request("EARLY_HELP", true)
        assertEquals("REQUESTED", m.snapshot!!.state)
    }

    @Test
    fun untrainedAndNonConsentingVehiclesCannotBeInvited() {
        val m = model()
        m.review()
        refuses { m.invite("DEMO-HELPER-B") }
        refuses { m.invite("DEMO-HELPER-C") }
        assertTrue(m.eligible("DEMO-HELPER-A"))
        assertTrue(m.eligible("DEMO-HELPER-D"))
    }

    @Test
    fun requestReviewVoluntaryAcceptAndMissionOrder() {
        val m = model()
        refuses { m.authorize() }
        refuses { m.invite("DEMO-HELPER-A") }
        m.review()
        m.invite("DEMO-HELPER-A")
        refuses { m.authorize() }
        m.respond(true)
        m.authorize()
        assertEquals("AUTHORIZED", m.snapshot!!.state)
        assertEquals(300, m.snapshot!!.missionRemaining)
        assertEquals(195, m.snapshot!!.supportSeconds)
        assertEquals(285, m.snapshot!!.headStartSeconds)
        assertFalse(m.canDisplay())
    }

    @Test
    fun declineDoesNotDispatchAndAllowsAnotherEligibleVehicle() {
        val m = model()
        m.review()
        m.invite("DEMO-HELPER-A")
        m.respond(false)
        assertFalse(m.eligible("DEMO-HELPER-A"))
        refuses { m.invite("DEMO-HELPER-A") }
        m.invite("DEMO-HELPER-D")
        m.respond(true)
        m.authorize()
        assertEquals("DEMO-HELPER-D", m.snapshot!!.vehicleId)
    }

    @Test
    fun approvalRequiresEmergencyReviewAndSameAuthority() {
        val m = authorized()
        refuses { m.activateBeacon() }
        m.requestBeacon("112")
        refuses { m.decideBeacon("112", true) }
        refuses { m.reviewBeacon("119") }
        m.reviewBeacon("112")
        refuses { m.decideBeacon("119", true) }
        m.decideBeacon("112", true)
        assertFalse(m.canDisplay())
        m.activateBeacon()
        assertTrue(m.canDisplay())
        assertEquals("112", m.snapshot!!.beaconAuthority)
    }

    @Test
    fun beaconRejectRevokeAndReapprovalUseDistinctScope() {
        val m = authorized()
        m.requestBeacon("119")
        m.reviewBeacon("119")
        m.decideBeacon("119", false)
        refuses { m.activateBeacon() }
        m.requestBeacon("112")
        m.reviewBeacon("112")
        m.decideBeacon("112", true)
        m.activateBeacon()
        val prior = m.snapshot!!.beaconApprovalId
        m.revokeBeacon()
        assertFalse(m.canDisplay())
        m.requestBeacon("119")
        m.reviewBeacon("119")
        m.decideBeacon("119", true)
        assertNotEquals(prior, m.snapshot!!.beaconApprovalId)
    }

    @Test
    fun realTimeBeaconExpiresAt120Seconds() {
        val m = approved()
        m.activateBeacon()
        now += 119_999
        m.tick()
        assertEquals(1, m.snapshot!!.beaconRemaining)
        assertTrue(m.canDisplay())
        now++
        m.tick()
        assertFalse(m.canDisplay())
        assertEquals("EXPIRED", m.snapshot!!.beaconState)
        refuses { m.activateBeacon() }
    }

    @Test
    fun missionExpiresAt300SecondsAndLimitsLateApproval() {
        val m = authorized()
        now += 290_000
        m.tick()
        m.requestBeacon("119")
        m.reviewBeacon("119")
        m.decideBeacon("119", true)
        assertEquals(10, m.snapshot!!.beaconRemaining)
        m.activateBeacon()
        now += 10_000
        m.tick()
        assertFalse(m.canDisplay())
        assertEquals("EXPIRED", m.snapshot!!.state)
        assertEquals(0, m.snapshot!!.missionRemaining)
    }

    @Test
    fun backgroundAndRestrictionsStopPreviewWithoutAutoRestart() {
        var permitted = true
        val m =
            OfflineCooperationEngine(fixture, { now }, previewAllowed = { permitted }).apply {
                attach("DEMO-1234")
                request("EARLY_HELP", true)
                review()
                invite("DEMO-HELPER-A")
                respond(true)
                authorize()
                requestBeacon("119")
                reviewBeacon("119")
                decideBeacon("119", true)
                activateBeacon()
            }
        m.foreground(false)
        assertFalse(m.canDisplay())
        m.foreground(true)
        assertFalse(m.canDisplay())
        m.activateBeacon()
        permitted = false
        m.tick()
        assertFalse(m.canDisplay())
        permitted = true
        assertFalse(m.canDisplay())
    }

    @Test
    fun arrivalAndHandoverRevokeEverything() {
        val m = approved()
        m.activateBeacon()
        refuses { m.finish("HANDED_OVER") }
        m.arrive()
        m.finish("HANDED_OVER")
        assertEquals("HANDED_OVER", m.snapshot!!.state)
        assertFalse(m.canDisplay())
        assertNull(m.snapshot!!.beaconApprovalId)
        assertEquals(0, m.snapshot!!.missionRemaining)
    }

    @Test
    fun noResponderAndEmsFirstAreHonestControlConditions() {
        val none = model("NO_RESPONDER")
        none.review()
        assertTrue(none.vehicles.none { none.eligible(it.id) })
        refuses { none.invite("DEMO-HELPER-A") }
        val early = model("EMS_FIRST")
        early.review()
        early.invite("DEMO-HELPER-A")
        early.respond(true)
        early.authorize()
        assertEquals(0, early.snapshot!!.headStartSeconds)
        assertTrue(early.snapshot!!.baselineSeconds < early.snapshot!!.supportSeconds!!)
    }

    @Test
    fun subsequentRunCannotReusePriorApprovalIdentity() {
        val m = approved()
        val old = m.snapshot!!.id
        m.finish("CANCELLED")
        m.request("EARLY_HELP", true)
        assertNotEquals(old, m.snapshot!!.id)
        assertNull(m.snapshot!!.beaconApprovalId)
        assertFalse(m.canDisplay())
    }

    @Test
    fun measuredDemoElapsedTimeStopsWhenMissionEnds() {
        val m = authorized()
        now += 2500
        m.finish("CANCELLED")
        assertEquals(2, m.elapsedSeconds)
        now += 30_000
        m.tick()
        assertEquals(2, m.elapsedSeconds)
        m.request("EARLY_HELP", true)
        assertEquals(0, m.elapsedSeconds)
    }

    @Test
    fun lossOfContextImmediatelyStopsAnActivePreview() {
        var consent = true
        val m =
            OfflineCooperationEngine(fixture, { now }, allowed = { consent }).apply {
                attach("DEMO-1234")
                request("EARLY_HELP", true)
                review()
                invite("DEMO-HELPER-A")
                respond(true)
                authorize()
                requestBeacon("119")
                reviewBeacon("119")
                decideBeacon("119", true)
                activateBeacon()
            }
        assertTrue(m.canDisplay())
        consent = false
        assertFalse(m.canDisplay())
        m.tick()
        assertEquals("REVOKED", m.snapshot!!.state)
    }

    @Test
    fun displayPredicateNeverChangesStateWhileUiIsRendering() {
        val m = approved()
        m.activateBeacon()
        val prior = m.snapshot
        now += 120_000
        assertFalse(m.canDisplay())
        assertEquals(prior, m.snapshot)
        m.tick()
        assertEquals("EXPIRED", m.snapshot!!.beaconState)
    }

    @Test
    fun sessionEndPatientChangeAndConsentRevocationInvalidateCooperation() {
        val c =
            ExperienceCatalogParser.parse(
                File("src/main/assets/experience-catalog.json").readText()
            )
        val e = SessionEngine(c, cooperationCatalog = fixture, clock = { now })
        e.start("DEMO-123456789", ConnectionTarget.EMERGENCY_119, "S14")
        e.answer()
        refuses { e.cooperation.request("EARLY_HELP", true) }
        e.share(setOf(ShareField.LOCATION))
        e.cooperation.request("EARLY_HELP", true)
        e.selectPerson("DEMO-P-1")
        assertEquals("REVOKED", e.cooperation.snapshot!!.state)
        e.cooperation.request("EARLY_HELP", true)
        e.revoke(ShareField.LOCATION)
        assertEquals("REVOKED", e.cooperation.snapshot!!.state)
        refuses { e.cooperation.review() }
        e.finish()
        assertNull(e.cooperation.snapshot)
    }

    @Test
    fun psychologicalRouteNeverCreatesAHelperRequest() {
        val c =
            ExperienceCatalogParser.parse(
                File("src/main/assets/experience-catalog.json").readText()
            )
        val e = SessionEngine(c, cooperationCatalog = fixture, clock = { now })
        e.start("DEMO-1234", ConnectionTarget.COUNSEL_109, "S13")
        e.answer()
        e.share(setOf(ShareField.LOCATION))
        refuses { e.cooperation.request("EARLY_HELP", true) }
    }
}
