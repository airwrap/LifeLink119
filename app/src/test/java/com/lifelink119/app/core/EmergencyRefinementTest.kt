package com.lifelink119.app.core

import com.lifelink119.app.core.emergency.*
import com.lifelink119.app.data.*
import org.junit.Assert.*
import org.junit.Test

class EmergencyRefinementTest {
    @Test
    fun familyPinTimeoutDoesNotLockOngoingForegroundEmergencySupport() {
        val privacy = EmergencyObservationPrivacy()
        privacy.expireAuthentication()
        assertFalse(privacy.locked)
        privacy.leave(true)
        assertTrue(privacy.locked)
        assertThrows(IllegalStateException::class.java) { privacy.unlock(false) }
        privacy.unlock(true)
        assertFalse(privacy.locked)
        privacy.expireAuthentication()
        assertTrue(privacy.locked)
    }

    @Test
    fun restoredObservationsRelockOnExpiryAndNewIncidentClearsTheScope() {
        val privacy = EmergencyObservationPrivacy()
        privacy.restore()
        assertFalse(privacy.locked)
        privacy.expireAuthentication()
        assertTrue(privacy.locked)
        privacy.clear()
        privacy.expireAuthentication()
        assertFalse(privacy.locked)
    }

    @Test
    fun healthReadoutNeverSilentlyCutsAnAllergyOrMedicationEntry() {
        val medicine = "복약 확인 ".repeat(100) + "마지막 항목"
        val summary =
            EmergencyHealthSummary.build(PersonalHealthRecord(medicationHistory = medicine))
        assertTrue(summary.contains(medicine))
    }

    @Test
    fun callChecksNeverInferReceptionFromNegativeOrUnknownAnswers() {
        val check = EmergencyCallCheck()
        assertNull(check.reply(false))
        assertEquals(EmergencyCallCheck.Step.CALLBACK, check.step)
        assertNull(check.reply(false))
        assertEquals(EmergencyCallCheck.Step.DISPATCH, check.step)
        assertNull(check.reply(null))
        assertNull(check.reply(true))
        check.reset()
        check.reply(false)
        assertEquals(EmergencyCallStatus.CALLBACK, check.reply(true))
    }

    @Test
    fun ambiguousCallStatementsNeverCertifyAConnection() {
        for (text in listOf("연결됐을까요", "통화 중이 아니에요", "구급차가 올까요", "네", "")) assertNull(
            EmergencyCallSpeech.status(text)
        )
        assertEquals(EmergencyCallStatus.DISPATCH_WAIT, EmergencyCallSpeech.status("출동 기다리래요"))
    }

    @Test
    fun healthSelectionIsPatientExplicitIncidentBoundAndAuthGated() {
        val selection = EmergencyHealthSelection()
        assertNull(selection.profileId("one", true, false))
        assertThrows(IllegalStateException::class.java) {
            selection.select("one", "SELF", false, false)
        }
        assertThrows(IllegalStateException::class.java) {
            selection.select("one", "SELF", true, true)
        }
        selection.select("one", "SELF", true, false)
        assertEquals("SELF", selection.profileId("one", true, false))
        assertNull(selection.profileId("two", true, false))
        assertNull(selection.profileId("one", false, false))
        assertNull(selection.profileId("one", true, true))
        selection.clear()
        assertNull(selection.profileId("one", true, false))
    }

    @Test
    fun localHealthReadoutContainsSavedPatientContextButNoDiagnosticClaim() {
        val record =
            PersonalHealthRecord(
                name = "테스트 환자",
                conditions = "기존 병력",
                allergies = "기존 알레르기",
                medicationHistory = "직접 입력 복약",
                safeCall =
                    SafeCallDetails(
                        mobility = "보행 도움",
                        canCall = OptionalAnswer.NO,
                        callUnavailableReason = "의사소통 도움",
                        responderRequests = "보호자에게 확인",
                        guardians =
                            listOf(SafeCallGuardian(name = "테스트 보호자", mobile = "01000000000")),
                    ),
            )
        val summary = EmergencyHealthSummary.build(record)
        for (text in
            listOf(
                "테스트 환자",
                "기존 병력",
                "기존 알레르기",
                "직접 입력 복약",
                "보행 도움",
                "의사소통 도움",
                "보호자에게 확인",
                "공식 조회 아님",
                "외부 AI로 보내지 않습니다",
            )) assertTrue(text, summary.contains(text))
        assertFalse(summary.contains("미입력"))
    }

    @Test
    fun multipleSymptomsAndCriticalGuidanceSurviveLaterObservations() {
        var now = 1000L
        val ai =
            EmergencyAiCoordinator { now++ }
                .apply {
                    attach("DEMO-1234")
                    start(true)
                }
        ai.answer(ObservationCode.BREATHING_NO)
        ai.answer(ObservationCode.CHEST_PAIN)
        ai.answer(ObservationCode.BLEEDING)
        repeat(40) { ai.answer(ObservationCode.ALONE) }
        assertEquals("BREATHING", ai.session!!.verifiedGuidanceId)
        assertTrue(ai.session!!.patientObservations.size <= 24)
        val summary = ai.session!!.handover()
        for (code in
            listOf(
                ObservationCode.BREATHING_NO,
                ObservationCode.CHEST_PAIN,
                ObservationCode.BLEEDING,
            )) assertTrue(summary.contains(code.label))
        ai.answer(ObservationCode.FIRE)
        ai.answer(ObservationCode.BREATHING_NO)
        assertEquals("FIRE", ai.session!!.verifiedGuidanceId)
    }
}
