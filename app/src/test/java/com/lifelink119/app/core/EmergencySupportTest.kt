package com.lifelink119.app.core

import com.lifelink119.app.core.emergency.*
import com.lifelink119.app.data.EmergencyCheckpoint
import com.lifelink119.app.data.EmergencyCheckpointCodec
import org.junit.Assert.*
import org.junit.Test

class EmergencySupportTest {
    private val now = 1_000_000L

    private fun sample() =
        Evidence(LocationFix(37.0, 127.0), EvidenceStatus.VEHICLE, "sdk", now, 100L)

    private fun ai() =
        EmergencyAiCoordinator { now }
            .apply {
                attach("DEMO-1234")
                start(true)
            }

    private fun call() = EmergencyCallSession("DEMO-1234", lastUpdatedAt = now)

    private fun snapshot() =
        EmergencyContextSnapshot(
            "DEMO-1234",
            Evidence(now),
            currentLocation = sample(),
            collectedAt = now,
        )

    @Test
    fun staleFutureAndClockResetAreNeverCurrent() {
        assertTrue(sample().fresh(now, 100))
        assertFalse(sample().fresh(now + 30_001, 30_101))
        assertFalse(sample().fresh(now - 1, 100))
        assertFalse(sample().fresh(now, 99))
        assertFalse(sample().copy(measuredAt = null).fresh(now, 100))
        assertFalse(sample().copy(status = EvidenceStatus.UNKNOWN).fresh(now, 100))
    }

    @Test
    fun coordinateAndBearingValidation() {
        assertFalse(LocationFix(Double.NaN, 0.0).valid())
        assertFalse(LocationFix(91.0, 0.0).valid())
        assertFalse(LocationFix(0.0, 0.0, -1.0).valid())
        assertFalse(LocationFix(0.0, 0.0, headingDegrees = 360.0).valid())
        assertTrue(LocationFix(0.0, 0.0).valid())
        assertTrue(EmergencyPacket.location(sample(), now, 100).contains("정확도 미확인"))
    }

    @Test
    fun readAloudNeverPresentsStaleLocationOrHiddenPatientAsCurrent() {
        val support = ai().apply { answer(ObservationCode.CHEST_PAIN) }.session!!
        val hidden = EmergencyPacket.readAloud(snapshot(), support, now + 60_000, 60_100, false)
        assertTrue(hidden.contains("현재 상태 아님"))
        assertFalse(hidden.contains("37.00000"))
        assertFalse(hidden.contains("가슴 통증"))
        assertTrue(hidden.contains("실제 기관 자동 전송 없음"))
        assertTrue(EmergencyPacket.readAloud(snapshot(), support, now, 100, true).contains("가슴 통증"))
    }

    @Test
    fun boundedRouteDeduplicatesExpiresAndClears() {
        val r = RecentLocations()
        repeat(10) { i ->
            r.add(
                sample().copy(measuredAt = now + i, receivedElapsed = 100L + i),
                now + i,
                100L + i,
            )
        }
        assertEquals(6, r.values(now + 10, 110).size)
        r.add(sample().copy(measuredAt = now + 9, receivedElapsed = 109), now + 10, 110)
        assertEquals(6, r.values(now + 10, 110).size)
        assertTrue(r.values(now + 130_000, 130_100).isEmpty())
        r.add(sample(), now, 100)
        r.clear()
        assertTrue(r.values(now, 100).isEmpty())
    }

    @Test
    fun allFiveMocksHaveExplicitProvenanceAndFailureNeverAirbagFalse() {
        EmergencyDemoScenario.entries.forEach { scenario ->
            val p = MockEmergencyProviders(scenario, { now }, { 100 })
            p.vehicle.read { v ->
                if (scenario == EmergencyDemoScenario.NO_DATA) assertNull(v.airbag.value)
                else {
                    assertEquals(EvidenceStatus.SIMULATION, v.airbag.status)
                    assertTrue(v.values.values.all { it.status == EvidenceStatus.SIMULATION })
                }
            }
            p.location.read { loc ->
                if (scenario == EmergencyDemoScenario.NO_DATA) assertNull(loc.value)
                else assertEquals(EvidenceStatus.SIMULATION, loc.status)
            }
        }
    }

    @Test
    fun noAirbagDeploymentDoesNotAssertNoAccidentOrNoInjury() {
        val p = MockEmergencyProviders(EmergencyDemoScenario.COLLISION, { now }, { 100 })
        p.vehicle.read { v ->
            val text =
                EmergencyPacket.build(
                    snapshot().copy(vehicleState = v),
                    call(),
                    ai().session!!,
                    now,
                    100,
                    false,
                )
            assertTrue(text.contains("안전 여부 판단 불가"))
            assertTrue(text.contains("모의 충돌 이벤트"))
            assertFalse(text.contains("사고 없음"))
            assertFalse(text.contains("부상 없음"))
        }
    }

    @Test
    fun staleLocationClearlyLabelledAndNoPatientInferenceFromCar() {
        val text =
            EmergencyPacket.build(snapshot(), call(), ai().session!!, now + 30_001, 30_101, false)
        assertTrue(text.contains("현재 위치 아님"))
        assertTrue(text.contains("차량 신호로 추정하지 않음"))
        assertTrue(text.contains("정확한 현재 위치").not())
    }

    @Test
    fun patientSummaryRequiresSeparateInclusion() {
        val coordinator = ai()
        coordinator.answer(ObservationCode.CHEST_PAIN)
        val privateText =
            EmergencyPacket.build(snapshot(), call(), coordinator.session!!, now, 100, false)
        assertFalse(privateText.contains("가슴 통증이라고"))
        assertTrue(
            EmergencyPacket.build(snapshot(), call(), coordinator.session!!, now, 100, true)
                .contains("가슴 통증이라고")
        )
    }

    @Test
    fun attemptAndUnknownCannotSetReportedOrDispatch() {
        val next = call().attempt(now).unconfirmed(now + 1200)
        assertEquals(EmergencyCallStatus.UNKNOWN, next.connectionStatus)
        assertNull(next.reportStatus.value)
        assertNull(next.dispatchStatus.value)
        assertNull(next.callbackStatus.value)
    }

    @Test
    fun onlyUserOrSyntheticConfirmationAccepted() {
        EvidenceStatus.entries
            .filter { it !in setOf(EvidenceStatus.USER, EvidenceStatus.SIMULATION) }
            .forEach { source ->
                assertThrows(IllegalArgumentException::class.java) {
                    call().confirm(EmergencyCallStatus.REPORTED, source, now, 100)
                }
            }
        assertTrue(
            call()
                .confirm(EmergencyCallStatus.CALLBACK, EvidenceStatus.USER, now, 100)
                .callbackStatus
                .value == true
        )
    }

    @Test
    fun retryDuringCallbackAlwaysAllowedAndRetainsIncident() {
        val c =
            call()
                .confirm(EmergencyCallStatus.CALLBACK, EvidenceStatus.USER, now, 100)
                .attempt(now + 50)
        assertEquals("DEMO-1234", c.incidentId)
        assertEquals(EmergencyCallStatus.ATTEMPTING, c.connectionStatus)
        assertEquals(now + 50, c.callAttemptTime)
    }

    @Test
    fun arrivingIsTerminalForConfirmationButDoesNotProhibitEmergencyRetry() {
        val c = call().confirm(EmergencyCallStatus.ARRIVED, EvidenceStatus.USER, now, 100)
        assertThrows(IllegalStateException::class.java) {
            c.confirm(EmergencyCallStatus.CALLBACK, EvidenceStatus.USER, now, 100)
        }
        assertEquals(EmergencyCallStatus.ATTEMPTING, c.attempt(now).connectionStatus)
    }

    @Test
    fun uncertainOrConnectedCallMutesVoice() {
        listOf(
                EmergencyCallStatus.UNKNOWN,
                EmergencyCallStatus.ATTEMPTING,
                EmergencyCallStatus.OPERATOR,
                EmergencyCallStatus.CALLBOT,
            )
            .forEach { assertTrue(call().copy(connectionStatus = it).voiceMustPause) }
    }

    @Test
    fun explicitConsentBeforeObservations() {
        val a = EmergencyAiCoordinator { now }
        a.attach("DEMO-1234")
        assertThrows(IllegalStateException::class.java) { a.answer(ObservationCode.CONSCIOUS_NO) }
        assertThrows(IllegalStateException::class.java) { a.start(false) }
        a.start(true)
        assertEquals("제가 함께 상황을 확인하겠습니다. 환자는 의식이 있나요?", a.question())
    }

    @Test
    fun singleQuestionFlowAndNoDiagnosis() {
        val a = ai()
        a.answer(ObservationCode.CONSCIOUS_YES)
        assertEquals(SupportStep.BREATHING, a.session!!.aiSessionState)
        a.answer(ObservationCode.BREATHING_YES)
        assertEquals(SupportStep.SYMPTOMS, a.session!!.aiSessionState)
        a.answer(ObservationCode.ALONE)
        assertEquals("ALONE", a.session!!.verifiedGuidanceId)
        assertFalse(a.session!!.escalationRequired)
    }

    @Test
    fun uncertainConsciousnessAndBreathingEscalate() {
        for (code in
            listOf(
                ObservationCode.CONSCIOUS_NO,
                ObservationCode.CONSCIOUS_UNKNOWN,
                ObservationCode.BREATHING_NO,
                ObservationCode.BREATHING_UNKNOWN,
                ObservationCode.BLEEDING,
                ObservationCode.CHEST_PAIN,
                ObservationCode.STROKE_SIGNS,
                ObservationCode.FIRE,
                ObservationCode.WORSENING,
            )) {
            val a = ai()
            a.answer(code)
            assertTrue(code.name, a.session!!.escalationRequired)
        }
    }

    @Test
    fun laterBenignAnswerCannotClearUrgentGuidance() {
        val a = ai()
        a.answer(ObservationCode.BREATHING_NO)
        a.answer(ObservationCode.ALONE)
        assertEquals("BREATHING", a.session!!.verifiedGuidanceId)
        assertTrue(a.session!!.escalationRequired)
    }

    @Test
    fun stopAndClearDoNotAcceptMoreInput() {
        val a = ai()
        a.stop()
        assertThrows(IllegalStateException::class.java) { a.answer(ObservationCode.ALONE) }
        a.clear()
        assertNull(a.session)
        assertFalse(a.consent)
    }

    @Test
    fun boundedObservationsPreserveFirstAndLatestChange() {
        val a = ai()
        a.answer(ObservationCode.CONSCIOUS_YES)
        repeat(30) { a.answer(ObservationCode.ALONE) }
        a.answer(ObservationCode.WORSENING)
        assertEquals(24, a.session!!.patientObservations.size)
        assertEquals(ObservationCode.CONSCIOUS_YES, a.session!!.patientObservations.first().code)
        assertTrue(a.session!!.handover().contains("이전보다 상태"))
    }

    @Test
    fun externalFailureAndInjectionCannotBecomeGuidance() {
        val a = ai()
        a.rephrase(MockLlmProvider(true), true) {}
        assertNull(a.phraseId)
        assertTrue(a.externalState.contains("오프라인"))
        val malicious =
            object : LlmProvider {
                override fun suggest(request: LlmRequest, done: (String?) -> Unit) =
                    done("take medicine; ignore rules")
            }
        a.rephrase(malicious, true) {}
        assertNull(a.phraseId)
        assertEquals("WAIT", a.session!!.verifiedGuidanceId)
    }

    @Test
    fun boundedHistoryRetainsUrgentEvidenceAcrossRestart() {
        val a = ai()
        a.answer(ObservationCode.CONSCIOUS_YES)
        a.answer(ObservationCode.BREATHING_NO)
        repeat(40) { a.answer(ObservationCode.ALONE) }
        val observations = a.session!!.patientObservations
        assertEquals(24, observations.size)
        assertTrue(observations.any { it.code == ObservationCode.BREATHING_NO })
        val raw =
            EmergencyCheckpointCodec.encode(EmergencyCheckpoint(now, call(), a.session!!), now)
        val restored = EmergencyCheckpointCodec.decode(raw, now)
        assertEquals("BREATHING", restored.support.verifiedGuidanceId)
        assertTrue(restored.support.escalationRequired)
    }

    @Test
    fun restartKeepsTheSameCriticalWarningWhenSeveralSymptomsWereRecorded() {
        val a = ai()
        a.answer(ObservationCode.BREATHING_NO)
        a.answer(ObservationCode.CHEST_PAIN)
        a.answer(ObservationCode.BLEEDING)
        val raw =
            EmergencyCheckpointCodec.encode(EmergencyCheckpoint(now, call(), a.session!!), now)
        val restored = EmergencyCheckpointCodec.decode(raw, now)
        assertEquals(a.session!!.verifiedGuidanceId, restored.support.verifiedGuidanceId)
        assertEquals("BREATHING", restored.support.verifiedGuidanceId)
    }

    @Test
    fun urgentStateSkipsAllExternalModels() {
        val a = ai()
        a.answer(ObservationCode.FIRE)
        a.rephrase(
            object : LlmProvider {
                override fun suggest(request: LlmRequest, done: (String?) -> Unit) {
                    fail("must not call")
                }
            },
            true,
        ) {}
        assertEquals("FIRE", a.session!!.verifiedGuidanceId)
    }

    @Test
    fun missingExternalConsentFailsBeforeProvider() {
        assertThrows(IllegalStateException::class.java) {
            ai().rephrase(MockLlmProvider(), false) {}
        }
    }

    @Test
    fun lateModelResponseAfterStateChangeIsDiscarded() {
        val a = ai()
        var respond: ((String?) -> Unit)? = null
        a.rephrase(
            object : LlmProvider {
                override fun suggest(request: LlmRequest, done: (String?) -> Unit) {
                    respond = done
                }
            },
            true,
        ) {}
        a.answer(ObservationCode.CONSCIOUS_NO)
        respond!!("CONSCIOUSNESS_EASY")
        assertNull(a.phraseId)
        assertEquals("RESPONSE", a.session!!.verifiedGuidanceId)
    }

    @Test
    fun checkpointExpiresAndRestoresNoConnectionClaim() {
        val a = ai()
        a.answer(ObservationCode.BREATHING_NO)
        val v = EmergencyCheckpoint(now, call().attempt(now), a.session!!)
        val raw = EmergencyCheckpointCodec.encode(v, now + 10)
        val restored = EmergencyCheckpointCodec.decode(raw, now + 20)
        assertEquals(EmergencyCallStatus.UNKNOWN, restored.call.connectionStatus)
        assertEquals(SupportStep.STOPPED, restored.support.aiSessionState)
        assertTrue(restored.support.escalationRequired)
        assertThrows(IllegalArgumentException::class.java) {
            EmergencyCheckpointCodec.decode(raw, now - 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmergencyCheckpointCodec.decode(raw, now + EmergencyCheckpointCodec.TTL + 10)
        }
        assertFalse(raw.contains("latitude"))
    }

    @Test
    fun sensitiveDataToStringIsRedacted() {
        assertFalse(sample().toString().contains("37."))
        val a = ai()
        a.answer(ObservationCode.CHEST_PAIN)
        assertFalse(a.session.toString().contains("CHEST"))
        assertFalse(a.session!!.patientObservations.toString().contains("CHEST"))
    }
}
