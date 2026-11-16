package com.lifelink119.app.data

import com.lifelink119.app.core.LocalRegistration
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SafeCallWebImportTest {
    private fun payload(change: (JSONObject) -> Unit = {}): String {
        val fields =
            JSONObject()
                .put("name", "QA 본인")
                .put("registeredPhone", "010-0000-0000")
                .put("medicationHistory", "검증용 가상 약 이름")
                .put("pastHistory", "검증용 과거력")
        change(fields)
        return JSONObject()
            .put("schema", 1)
            .put("origin", "https://u119.nfa.go.kr")
            .put("fields", fields)
            .put("missing", JSONArray())
            .toString()
    }

    @Test
    fun importedCopyHasProvenanceButDoesNotVerifyOfficialRegistration() {
        val p = SafeCallWebImport.prepare(payload(), 1234, null)
        assertEquals("검증용 가상 약 이름", p.record.medicationHistory)
        assertEquals(LocalRegistration.UNKNOWN, p.record.registration)
        assertEquals(1234L, p.record.webSource?.retrievedAt)
        assertFalse(p.identityConflict)
        assertFalse(p.toString().contains("QA 본인"))
        assertEquals(p.record, PersonalHealthCodec.decode(PersonalHealthCodec.encode(p.record)))
        assertTrue(EmergencyHealthSummary.build(p.record).contains("웹화면 복사"))
    }

    @Test
    fun absentAndBlankFieldsPreserveExistingDataAndLists() {
        val old =
            PersonalHealthRecord(
                name = "QA 본인",
                allergies = "가상 알레르기",
                medicationHistory = "기존 약",
                safeCall =
                    SafeCallDetails(pastHistory = "기존 병력", currentConditions = listOf("기존 질환")),
            )
        val p =
            SafeCallWebImport.prepare(
                payload {
                    it.put("medicationHistory", "")
                        .put("pastHistory", "")
                        .put("currentConditions", JSONArray(listOf("새 가상 질환")))
                },
                1234,
                old,
            )
        assertEquals("기존 약", p.record.medicationHistory)
        assertEquals("기존 병력", p.record.safeCall.pastHistory)
        assertEquals("가상 알레르기", p.record.allergies)
        assertEquals(listOf("기존 질환", "새 가상 질환"), p.record.safeCall.currentConditions)
        assertTrue(p.missing.contains("장애유형"))
    }

    @Test
    fun differentIdentityIsMarkedForBlockingAndNeverAutomaticallyMixed() {
        val existing =
            PersonalHealthRecord(
                name = "다른 QA",
                registeredPhone = "010-0000-0001",
                registration = LocalRegistration.USER_REPORTED,
            )
        val p = SafeCallWebImport.prepare(payload(), 1234, existing)
        assertTrue(p.identityConflict)
        assertEquals(LocalRegistration.UNKNOWN, p.record.registration)
        assertEquals("다른 QA", existing.name)
    }

    @Test
    fun emptyMaskedOversizedAndUnexpectedSecretsAreRejected() {
        val changes: List<(JSONObject) -> Unit> =
            listOf(
                { it.put("name", "") },
                { it.put("name", "김*명") },
                { it.put("registeredPhone", "010-****-1234") },
                { it.put("password", "not-a-real-password") },
                { it.put("medicationHistory", "가".repeat(3001)) },
                { it.put("pastHistory", JSONObject().put("nested", "bad")) },
                { it.put("pastHistory", "fake\u202edata") },
                { it.put("bloodGroup", "알 수 없는 코드") },
                { it.put("currentConditions", JSONArray(List(17) { n -> "가상 $n" })) },
            )
        changes.forEach { change ->
            assertThrows(Exception::class.java) {
                SafeCallWebImport.prepare(payload(change), 1234, null)
            }
        }
        assertThrows(Exception::class.java) {
            SafeCallWebImport.prepare("x".repeat(96_001), 1234, null)
        }
        assertThrows(Exception::class.java) {
            SafeCallWebImport.prepare(
                payload {
                    it.remove("pastHistory")
                    it.remove("medicationHistory")
                },
                1234,
                null,
            )
        }
    }

    @Test
    fun guardianContactConfirmationIsNeverInferredFromWebRadio() {
        val guardian =
            JSONObject()
                .put("name", "QA 보호자")
                .put("relation", "가족")
                .put("mobile", "010-0000-0001")
                .put("phone", "")
                .put("email", "")
                .put("living", "동거")
                .put("smsPreference", "예")
        val p =
            SafeCallWebImport.prepare(
                payload { it.put("canCall", "불가능").put("guardians", JSONArray().put(guardian)) },
                1234,
                null,
            )
        assertFalse(p.record.safeCall.guardianCallConfirmed)
        assertFalse(PersonalHealthCodec.validate(p.record))
        assertTrue(
            PersonalHealthCodec.validate(
                p.record.copy(safeCall = p.record.safeCall.copy(guardianCallConfirmed = true))
            )
        )
    }

    @Test
    fun incomingGuardianContactIsFirstAndThePreservedOldContactIsAlsoShownInReview() {
        val oldGuardian =
            SafeCallGuardian(name = "QA 보호자", relation = "가족", mobile = "010-0000-0001")
        val old =
            PersonalHealthRecord(
                name = "QA 본인",
                safeCall = SafeCallDetails(guardians = listOf(oldGuardian)),
            )
        val incoming =
            JSONObject()
                .put("name", "QA 보호자")
                .put("relation", "가족")
                .put("mobile", "010-0000-0002")
                .put("phone", "")
                .put("email", "")
                .put("living", "")
                .put("smsPreference", "아니오")
        val p =
            SafeCallWebImport.prepare(
                payload { it.put("guardians", JSONArray().put(incoming)) },
                1234,
                old,
            )
        assertEquals("010-0000-0002", p.record.safeCall.guardians.first().mobile)
        assertEquals(oldGuardian, p.record.safeCall.guardians.last())
        assertEquals(OptionalAnswer.NO, p.record.safeCall.guardians.first().smsPreference)
        val displayed = p.imported.first { it.first == "보호자 목록" }.second
        assertTrue(displayed.contains("010-0000-0001"))
        assertTrue(displayed.contains("010-0000-0002"))
        assertTrue(EmergencyHealthSummary.build(p.record).contains("010-0000-0002"))
    }

    @Test
    fun oldV3RecordIsNotMarkedAsWebImported() {
        val old =
            JSONObject(PersonalHealthCodec.encode(PersonalHealthRecord(name = "legacy")))
                .put("version", 3)
        old.remove("webSource")
        assertNull(PersonalHealthCodec.decode(old.toString()).webSource)
    }

    @Test
    fun originMustBeExactHttpsHostWithoutCredentialsOrDifferentPort() {
        assertTrue(SafeCallWebImport.allowsOrigin("https://u119.nfa.go.kr/web?a=b"))
        listOf(
                "https://u119.nfa.go.kr.evil.test/",
                "https://evil.test/u119.nfa.go.kr",
                "file:///test",
                "http://u119.nfa.go.kr",
                "https://user@u119.nfa.go.kr",
                "https://u119.nfa.go.kr:444/",
                "https://www.nfa.go.kr/",
            )
            .forEach { assertFalse(SafeCallWebImport.allowsOrigin(it)) }
    }

    @Test
    fun transferRequiresMatchingNonceExpiresAndIsNotRestoredAfterRestart() {
        var now = 500L
        val s = SafeCallImportSession { now }
        val nonce = s.begin()
        val envelope = SafeCallImportSession.wrap(payload(), nonce, 1234)
        val opaque = "ciphertext".repeat(10)
        assertTrue(s.accept(opaque))
        assertEquals("QA 본인", s.preview(null) { envelope }.record.name)
        assertThrows(Exception::class.java) {
            s.preview(null) { SafeCallImportSession.wrap(payload(), "0".repeat(36), 1234) }
        }
        now += SafeCallImportSession.TTL_MS + 1
        assertThrows(Exception::class.java) { s.preview(null) { envelope } }
        s.clear()
        assertFalse(s.accept(opaque))
        assertFalse(SafeCallImportSession { now }.accept(opaque))
    }

    @Test
    fun replayAndUnsolicitedResultsDoNotCreateAnImport() {
        val s = SafeCallImportSession { 1L }
        assertFalse(s.accept("cipher".repeat(20)))
        s.begin()
        assertTrue(s.accept("cipher".repeat(20)))
        assertFalse(s.accept("cipher".repeat(20)))
        assertThrows(Exception::class.java) { s.preview(null) { "" } }
    }

    @Test
    fun twoSmsChecksHaveSeparateDeadlineFromReviewAndBothStillExpire() {
        var now = 1L
        val s = SafeCallImportSession { now }
        val nonce = s.begin()
        now += SafeCallImportSession.TTL_MS + 1
        assertTrue(s.accept("cipher".repeat(20)))
        val envelope = SafeCallImportSession.wrap(payload(), nonce, 1234)
        assertEquals("QA 본인", s.preview(null) { envelope }.record.name)
        now += SafeCallImportSession.TTL_MS + 1
        assertThrows(Exception::class.java) { s.preview(null) { envelope } }
        s.begin()
        now += SafeCallImportSession.AUTH_WAIT_MS + 1
        assertFalse(s.accept("cipher".repeat(20)))
    }

    @Test
    fun diagnosticsNeverEchoUntrustedCodesFieldsOrMedicalValues() {
        val secret = "NEVER_SHOW_PRIVATE_VALUE"
        val failure = SafeCallImportFailure(secret, secret)
        assertEquals("READ", failure.code)
        assertNull(failure.field)
        assertFalse(failure.userMessage().contains(secret))
        assertFalse(failure.toString().contains(secret))
        assertTrue(SafeCallImportFailure("VALIDATION", "bloodGroup").userMessage().contains("혈액형"))
    }
}
