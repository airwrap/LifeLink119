package com.lifelink119.app.core

import com.lifelink119.app.data.ExperienceCatalogParser
import com.lifelink119.app.data.HealthEditorDraft
import com.lifelink119.app.data.PersonalHealthCodec
import com.lifelink119.app.data.PersonalHealthRecord
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class ReviewRegressionTest {
    private fun catalog() =
        ExperienceCatalogParser.parse(File("src/main/assets/experience-catalog.json").readText())

    @Test
    fun returningFromLegacyToolDoesNotLeaveForegroundAuthenticationDeferred() {
        val presence = UiPresence()
        presence.visible = false
        assertFalse(presence.active)
        assertTrue(presence.foreground)
        presence.visible = true
        assertTrue(presence.active)
        presence.foreground = false
        presence.visible = false
        presence.visible = true
        assertFalse(presence.active)
        presence.foreground = true
        assertTrue(presence.active)
    }

    @Test
    fun draftSurvivesExpiryRerenderValidationFailureAndReauthentication() {
        val stored = PersonalHealthRecord(name = "QA")
        val draft = HealthEditorDraft()
        draft.begin(stored)
        val edited = stored.copy(conditions = "작성 중인 예시 병력", registeredPhone = "invalid")
        draft.update(edited)
        assertFalse(PersonalHealthCodec.validate(edited))
        assertEquals(edited, draft.begin(stored))
        draft.clear()
        assertEquals(stored, draft.begin(stored))
        assertFalse(draft.toString().contains("QA"))
    }

    @Test
    fun switchingDraftPatientCannotReuseAnotherPatientInput() {
        val draft = HealthEditorDraft()
        draft.begin(PersonalHealthRecord(name = "QA", conditions = "draft"))
        val other = PersonalHealthRecord(id = "LOCAL-00000000-0000-0000-0000-000000000001")
        assertEquals(other, draft.begin(other))
    }

    @Test
    fun twelveFullKoreanRecordsAndPreviouslyUnreadableCollectionsRoundTrip() {
        val records =
            (1..12).map { n ->
                PersonalHealthRecord(
                    name = "가".repeat(80),
                    conditions = "나".repeat(1500),
                    allergies = "다".repeat(1000),
                    medicationHistory = "라".repeat(3000),
                    id = "LOCAL-00000000-0000-0000-0000-" + n.toString().padStart(12, '0'),
                )
            }
        val raw = PersonalHealthCodec.encodeList(records)
        assertTrue(raw.toByteArray(Charsets.UTF_8).size > 40_000)
        assertEquals(records, PersonalHealthCodec.decodeList(raw))
    }

    @Test
    fun byteLimitIncludesGcmOverheadAndAcceptsExactBoundary() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val raw = ByteArray(MedicalStoragePolicy.MAX_PLAINTEXT_BYTES) { 65 }
        MedicalStoragePolicy.requirePlaintextSize(raw.size)
        val encrypt = Cipher.getInstance("AES/GCM/NoPadding")
        encrypt.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = encrypt.doFinal(raw)
        assertEquals(MedicalStoragePolicy.MAX_CIPHERTEXT_BYTES, encrypt.iv.size + encrypted.size)
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
        decrypt.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypt.iv))
        assertArrayEquals(raw, decrypt.doFinal(encrypted))
        assertThrows(IllegalArgumentException::class.java) {
            MedicalStoragePolicy.requirePlaintextSize(raw.size + 1)
        }
    }

    @Test
    fun byteBudgetRejectsWorstCaseEscapingBeforeCommit() {
        val records =
            (1..12).map { n ->
                PersonalHealthRecord(
                    name = "\u0001".repeat(80),
                    conditions = "\u0001".repeat(1500),
                    allergies = "\u0001".repeat(1000),
                    medicationHistory = "\u0001".repeat(3000),
                    id = "LOCAL-00000000-0000-0000-0000-" + n.toString().padStart(12, '0'),
                )
            }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalHealthCodec.encodeList(records)
        }
    }

    @Test
    fun coreRejectsMedicalShareWhileLockedExpiredRestrictedOrInBackground() {
        var now = 100L
        val e = SessionEngine(catalog(), clock = { now })
        e.setRestrictions(DrivingRestrictions.unrestrictedFixture())
        e.start("DEMO-1234", ConnectionTarget.EMERGENCY_119, "S01", "DEMO-P-1")
        e.answer()
        assertThrows(IllegalStateException::class.java) {
            e.share(setOf(ShareField.ALLERGIES, ShareField.LOCATION))
        }
        assertTrue(e.current!!.shared.isEmpty())
        e.share(setOf(ShareField.LOCATION))
        e.unlockPrivate(true)
        e.share(setOf(ShareField.ALLERGIES))
        now += MedicalPrivacyPolicy.UNLOCK_MS
        assertThrows(IllegalStateException::class.java) { e.share(setOf(ShareField.CONDITIONS)) }
        e.unlockPrivate(true)
        e.background()
        assertThrows(IllegalStateException::class.java) { e.share(setOf(ShareField.CONDITIONS)) }
        e.unlockPrivate(true)
        e.setRestrictions(DrivingRestrictions())
        assertThrows(IllegalStateException::class.java) { e.share(setOf(ShareField.CONDITIONS)) }
    }

    @Test
    fun profileThirteenCanBeSelectedAndLookedUpAfterDeleteAndAdd() {
        val e = SessionEngine(catalog())
        e.setRestrictions(DrivingRestrictions.unrestrictedFixture())
        e.deleteProfile(e.profiles.last().id)
        e.addProfile(e.profiles.first().copy(id = "DEMO-P-13"))
        e.start("DEMO-1234", ConnectionTarget.EMERGENCY_119)
        e.answer()
        e.selectPerson("DEMO-P-13")
        e.unlockPrivate(true)
        e.configureSafeCall(line = DemoCallLine.REGISTERED)
        assertTrue(e.completeSafeCall(requireNotNull(e.beginSafeCall(true))))
        listOf("DEMO-P-0", "DEMO-P-01", "DEMO-P-1000000", "01012345678").forEach {
            assertFalse(DemoProfileId.valid(it))
        }
    }

    @Test
    fun rebootRestartsOutstandingMonotonicPenaltyWithoutWallClockDependence() {
        val state = PinAttemptState(strikes = 2, lockedUntil = 80_000)
        assertEquals(state, PinAttemptPolicy.forBoot(state, 7, 7, 20_000))
        val rebooted = PinAttemptPolicy.forBoot(state, 7, 8, 2_000)
        assertEquals(60, PinAttemptPolicy.remainingSeconds(rebooted, 2_000))
        assertEquals(59, PinAttemptPolicy.remainingSeconds(rebooted, 3_000))
        assertEquals(rebooted, PinAttemptPolicy.forBoot(rebooted, 8, 8, 3_000))
    }

    @Test
    fun stalledVehicleWorkerIsUnavailableUntilItActuallyReturns() {
        val worker = VehicleWorkerState()
        assertTrue(worker.begin())
        worker.timeout()
        assertEquals(VehicleWorkerState.State.STALLED, worker.state)
        repeat(100) { assertFalse(worker.begin()) }
        worker.complete()
        assertTrue(worker.begin())
        worker.complete()
        worker.timeout() // Async callback timeout after the worker already returned.
        assertEquals(VehicleWorkerState.State.IDLE, worker.state)
        assertTrue(worker.begin())
    }
}
