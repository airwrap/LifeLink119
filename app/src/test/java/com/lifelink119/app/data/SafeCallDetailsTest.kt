package com.lifelink119.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SafeCallDetailsTest {
    private fun populated() =
        SafeCallDetails(
            applicationType = "질병외",
            applicationDetail = "고령자",
            mobility = "거동 도움 필요",
            bloodGroup = "AB",
            rhesus = "+",
            elevator = OptionalAnswer.NO,
            canCall = OptionalAnswer.NO,
            callUnavailableReason = "QA 통화 도움",
            pastHistory = "QA 수술력",
            currentConditions = listOf("QA 현재질환1", "QA 현재질환2"),
            infectiousDiseases = listOf("QA 감염병"),
            facilityName = "QA 진료기관",
            specialty = "QA 진료과",
            dayPhone = "00000000000",
            nightPhone = "00000000001",
            disabilities = listOf("QA 장애1", "QA 장애2"),
            discomfort = "QA 불편",
            constitution = "QA 체질",
            responderRequests = "QA 요구사항",
            frequentPlace = "QA 장소",
            guardians =
                listOf(
                    SafeCallGuardian(
                        "QA 보호자1",
                        "가족",
                        "자녀",
                        "00000000002",
                        "00000000003",
                        "qa1@example.invalid",
                        LivingArrangement.TOGETHER,
                        OptionalAnswer.YES,
                    ),
                    SafeCallGuardian(
                        "QA 보호자2",
                        "기타",
                        "관계인",
                        "00000000004",
                        "",
                        "",
                        LivingArrangement.SEPARATE,
                        OptionalAnswer.NO,
                    ),
                ),
            guardianCallConfirmed = true,
        )

    @Test
    fun everySuppliedSectionAndMultipleGuardiansSurviveRecordSerialization() {
        val record =
            PersonalHealthRecord(
                name = "QA",
                conditions = "기존 병력 그대로",
                medicationHistory = "기존 투약 그대로",
                safeCall = populated(),
            )
        val raw = PersonalHealthCodec.encode(record)
        assertEquals(4, JSONObject(raw).getInt("version"))
        assertEquals(record, PersonalHealthCodec.decode(raw))
        assertEquals(
            listOf(record),
            PersonalHealthCodec.decodeList(PersonalHealthCodec.encodeList(listOf(record))),
        )
    }

    @Test
    fun versionTwoMigrationPreservesExistingTextWithoutInventingNewClinicalFacts() {
        val original =
            PersonalHealthRecord(
                name = "QA",
                conditions = "기존 병력",
                allergies = "기존 알레르기",
                medicationHistory = "기존 투약",
            )
        val old = JSONObject(PersonalHealthCodec.encode(original)).put("version", 2)
        old.remove("safeCall")
        val decoded = PersonalHealthCodec.decode(old.toString())
        assertEquals(original, decoded)
        assertEquals(OptionalAnswer.UNSPECIFIED, decoded.safeCall.canCall)
        assertEquals(OptionalAnswer.UNSPECIFIED, decoded.safeCall.elevator)
        assertTrue(decoded.safeCall.currentConditions.isEmpty())
        assertEquals("", decoded.safeCall.pastHistory)
    }

    @Test
    fun unknownBooleansAndSmsPreferenceRemainDistinctFromExplicitNo() {
        val data =
            SafeCallDetails(
                guardians = listOf(SafeCallGuardian(name = "QA", mobile = "00000000000"))
            )
        val result = SafeCallDetailsCodec.decode(SafeCallDetailsCodec.encode(data))
        assertEquals(OptionalAnswer.UNSPECIFIED, result.guardians.single().smsPreference)
        assertNotEquals(OptionalAnswer.NO, result.canCall)
        assertEquals(LivingArrangement.UNSPECIFIED, result.guardians.single().living)
    }

    @Test
    fun telephoneInabilityRequiresAConfirmedReachableGuardianButDoesNotGateEmergencyEngine() {
        assertNotNull(SafeCallDetailsCodec.error(SafeCallDetails(canCall = OptionalAnswer.NO)))
        val guardian = SafeCallGuardian(name = "QA", mobile = "00000000000")
        assertNotNull(
            SafeCallDetailsCodec.error(
                SafeCallDetails(canCall = OptionalAnswer.NO, guardians = listOf(guardian))
            )
        )
        assertNull(
            SafeCallDetailsCodec.error(
                SafeCallDetails(
                    canCall = OptionalAnswer.NO,
                    guardians = listOf(guardian),
                    guardianCallConfirmed = true,
                )
            )
        )
        assertNull(SafeCallDetailsCodec.error(SafeCallDetails()))
    }

    @Test
    fun repeatedConditionsDisabilitiesAndGuardiansEnforceLimits() {
        assertNotNull(SafeCallDetailsCodec.error(SafeCallDetails(currentConditions = listOf(""))))
        assertNotNull(
            SafeCallDetailsCodec.error(SafeCallDetails(disabilities = listOf("QA", "QA")))
        )
        assertNotNull(
            SafeCallDetailsCodec.error(
                SafeCallDetails(infectiousDiseases = (1..17).map { "QA$it" })
            )
        )
        assertNotNull(
            SafeCallDetailsCodec.error(
                populated().copy(guardians = List(9) { populated().guardians.first() })
            )
        )
        assertNotNull(SafeCallDetailsCodec.error(SafeCallDetails(frequentPlace = "가".repeat(16))))
        assertNull(SafeCallDetailsCodec.error(SafeCallDetails(frequentPlace = "가".repeat(15))))
    }

    @Test
    fun malformedContactsAndForgedChoiceValuesCannotBeSavedOrDecoded() {
        assertNotNull(SafeCallDetailsCodec.error(SafeCallDetails(dayPhone = "123")))
        assertNotNull(
            SafeCallDetailsCodec.error(
                populated()
                    .copy(
                        guardians =
                            listOf(
                                SafeCallGuardian(name = "QA", mobile = "00000000000", email = "bad")
                            )
                    )
            )
        )
        val raw =
            SafeCallDetailsCodec.encode(SafeCallDetails()).put("elevator", "OFFICIALLY_VERIFIED")
        assertThrows(Exception::class.java) { SafeCallDetailsCodec.decode(raw) }
        val missing = JSONObject(PersonalHealthCodec.encode(PersonalHealthRecord()))
        missing.remove("safeCall")
        assertThrows(Exception::class.java) { PersonalHealthCodec.decode(missing.toString()) }
    }

    @Test
    fun draftsRetainEverySectionWhenLegacyTextIsEditedOrAuthenticationExpires() {
        val draft = HealthEditorDraft()
        val baseline = PersonalHealthRecord(name = "QA")
        draft.begin(baseline)
        draft.update(baseline.copy(safeCall = populated()))
        val current = draft.begin(baseline)
        draft.update(current.copy(medicationHistory = "QA changed"))
        assertEquals(populated(), draft.begin(baseline).safeCall)
        draft.clear()
        assertEquals(SafeCallDetails(), draft.begin(baseline).safeCall)
    }

    @Test
    fun nestedSensitiveModelsNeverLogTheirContents() {
        val data = populated()
        assertFalse(data.toString().contains("QA"))
        assertFalse(data.guardians.first().toString().contains("QA"))
        assertFalse(PersonalHealthRecord(safeCall = data).toString().contains("QA"))
    }
}
