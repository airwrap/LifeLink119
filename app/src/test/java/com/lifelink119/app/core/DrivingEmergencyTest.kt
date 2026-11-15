package com.lifelink119.app.core

import com.lifelink119.app.data.ExperienceCatalogParser
import com.lifelink119.app.data.ExperienceStateCodec
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DrivingEmergencyTest {
    private fun engine() =
        SessionEngine(
            ExperienceCatalogParser.parse(
                File("src/main/assets/experience-catalog.json").readText(Charsets.UTF_8)
            ),
            emptyList(),
        )

    private fun connected() =
        engine().apply {
            setRestrictions(DrivingRestrictions.unrestrictedFixture())
            startDriving("DEMO-123456")
            answer()
        }

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("Transition should be rejected")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Test
    fun requestNeedsNeitherStopProfileLocationNorMedicalUnlock() {
        val e = engine()
        e.locationAvailable = false
        e.startDriving("DEMO-123456")
        e.answer()
        assertTrue(e.current!!.active)
        assertNull(e.person())
        assertFalse(e.privateUnlocked)
        assertEquals(DrivingSafety.MOVING, e.current!!.driving!!.safety)
        assertTrue(e.current!!.shared.isEmpty())
    }

    @Test
    fun repeatedShortcutPreservesPendingFailedAndConnectedSession() {
        val e = engine()
        e.failNextConnection = true
        e.startDriving("DEMO-123456")
        e.startDriving("DEMO-987654")
        assertEquals("DEMO-123456", e.current!!.id)
        e.answer()
        assertEquals(LinkStatus.FAILED, e.current!!.status)
        e.startDriving("DEMO-987654")
        assertEquals(LinkStatus.FAILED, e.current!!.status)
        e.retry()
        e.answer()
        e.startDriving("DEMO-987654")
        assertEquals("DEMO-123456", e.current!!.id)
    }

    @Test
    fun movingReportDoesNotRequireLocationOrStopAndDoesNotAssumeInjury() {
        val e = connected()
        e.locationAvailable = false
        e.drivingSend()
        e.drivingAcknowledge()
        assertEquals(DrivingSafety.MOVING, e.current!!.driving!!.safety)
        assertTrue(e.current!!.driving!!.summary().contains("인원·부상 여부: 미확인"))
        assertTrue(e.current!!.shared.isEmpty())
    }

    @Test
    fun acknowledgementAndSafePlaceAreBothRequiredForHandover() {
        val e = connected()
        rejects { e.drivingAcknowledge() }
        rejects { e.drivingHandover() }
        rejects { e.drivingSafe() }
        e.drivingSend()
        e.drivingAcknowledge()
        rejects { e.drivingHandover() }
        e.drivingStopped()
        rejects { e.drivingHandover() }
        e.drivingSafe()
        e.drivingHandover()
        assertEquals(DrivingReport.HANDED_OVER, e.current!!.driving!!.report)
    }

    @Test
    fun genericSituationConnectsAndReportsWithoutAClassificationQuestion() {
        val e = connected()
        assertTrue(e.current!!.driving!!.summary().contains("차 안의 위급 상황"))
        e.drivingSend()
        assertEquals(DrivingReport.SENT, e.current!!.driving!!.report)
        rejects { e.drivingSend() }
        e.drivingAcknowledge()
        rejects { e.drivingAcknowledge() }
    }

    @Test
    fun restrictionsKeepUrgentCallAvailableButBlockDetailedRoleActionsAndVoiceExample() {
        val e = engine()
        rejects { e.startDriving("DEMO-123456", DrivingTrigger.VOICE_EXAMPLE) }
        e.startDriving("DEMO-123456")
        e.answer()
        rejects { e.drivingStopped() }
        rejects { e.drivingSend() }
        e.setRestrictions(DrivingRestrictions.unrestrictedFixture())
        e.drivingSend()
        e.setRestrictions(DrivingRestrictions.unrestrictedFixture().withTestRestriction(true))
        rejects { e.drivingAcknowledge() }
        assertEquals(DrivingReport.SENT, e.current!!.driving!!.report)
    }

    @Test
    fun failedOrEndedConnectionsRejectRoleActions() {
        val e = engine()
        e.failNextConnection = true
        e.startDriving("DEMO-123456")
        rejects { e.drivingSend() }
        e.answer()
        rejects { e.drivingSend() }
        e.retry()
        e.answer()
        e.setRestrictions(DrivingRestrictions.unrestrictedFixture())
        e.finish(true)
        rejects { e.drivingSend() }
        assertNull(e.current!!.driving)
        assertTrue(e.current!!.shared.isEmpty())
    }

    @Test
    fun consentIsExplicitRevocableAndClearedOnCompletion() {
        val e = connected()
        assertTrue(e.current!!.shared.isEmpty())
        e.share(setOf(ShareField.LOCATION))
        assertEquals(setOf(ShareField.LOCATION), e.current!!.shared.keys)
        e.revoke()
        assertTrue(e.current!!.shared.isEmpty())
        e.share(setOf(ShareField.VEHICLE))
        e.finish()
        assertTrue(e.current!!.shared.isEmpty())
        assertNull(e.current!!.driving)
    }

    @Test
    fun savedInstanceStateRetainsConnectionButRequiresSituationReconfirmation() {
        val e = connected()
        e.drivingSend()
        e.drivingAcknowledge()
        e.drivingStopped()
        e.drivingSafe()
        e.drivingHandover()
        val restored =
            ExperienceStateCodec.decode(
                ExperienceStateCodec.encodePrivacySafe(e.current),
                e.catalog,
                e.profiles,
            )!!
        assertTrue(restored.active)
        assertFalse(ExperienceStateCodec.encodePrivacySafe(e.current)!!.contains("hazard"))
        assertEquals(DrivingReport.DRAFT, restored.driving!!.report)
        assertTrue(restored.driving!!.reconfirm)
        assertEquals(DrivingSafety.MOVING, restored.driving!!.safety)
    }

    @Test
    fun forgedEnumsAndImpossibleHandoverCannotBeRestored() {
        val e = connected()
        val encoded = JSONObject(ExperienceStateCodec.encode(e.current)!!)
        encoded.getJSONObject("driving").put("trigger", "REAL_PERSON_TEXT")
        assertNull(ExperienceStateCodec.decode(encoded.toString(), e.catalog, e.profiles))
        encoded.getJSONObject("driving").put("trigger", "TOUCH").put("report", "HANDED_OVER")
        assertNull(ExperienceStateCodec.decode(encoded.toString(), e.catalog, e.profiles))
    }

    @Test
    fun backgroundLocksMedicalDataWithoutEndingEmergencyDemo() {
        val e = connected()
        e.unlockPrivate(true)
        e.background()
        assertFalse(e.privateUnlocked)
        assertTrue(e.current!!.active)
    }

    @Test
    fun selectingAnotherCaseClearsDrivingProgress() {
        val e = connected()
        e.drivingSend()
        e.selectCase("E04")
        assertNull(e.current!!.driving)
    }
}
