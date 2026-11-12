package com.lifelink119.app.data

import org.junit.Assert.*
import org.junit.Test
import com.lifelink119.app.core.LocalRegistration
import com.lifelink119.app.core.RegistrationRole

class PersonalHealthRecordTest {
    @Test fun directInputRoundTripsWithoutChangingHistory() {
        val r=PersonalHealthRecord("예시 이용자","예시 병력","예시 알레르기","2026-01 예시 약\n2026-02 복용 종료")
        assertEquals(r,PersonalHealthCodec.decode(PersonalHealthCodec.encode(r)))
        assertFalse(r.toString().contains("예시"))
    }
    @Test fun rejectsUnsupportedOrIncompleteRecords() {
        assertThrows(Exception::class.java){PersonalHealthCodec.decode("{\"version\":2}")}
        assertThrows(Exception::class.java){PersonalHealthCodec.decode("{\"version\":1}")}
    }
    @Test fun fieldLimitsPreventOversizedPayloads() {
        assertFalse(PersonalHealthCodec.validate(PersonalHealthRecord(name="x".repeat(81))))
        assertFalse(PersonalHealthCodec.validate(PersonalHealthRecord(medicationHistory="x".repeat(3001))))
        assertThrows(IllegalArgumentException::class.java){PersonalHealthCodec.encode(PersonalHealthRecord(allergies="x".repeat(1001)))}
    }
    @Test fun escapedMultilineHistoryAtAllowedLimitStillRoundTrips() {
        val r=PersonalHealthRecord(medicationHistory="\n".repeat(3000),conditions="\n".repeat(1500),allergies="\n".repeat(1000))
        assertEquals(r,PersonalHealthCodec.decode(PersonalHealthCodec.encode(r)))
    }
    @Test fun legacyVersionOneStillLoadsWithoutAssumingRegistration(){
        val r=PersonalHealthCodec.decode("""{"version":1,"name":"QA","conditions":"","allergies":"","medicationHistory":"past"}""")
        assertEquals("SELF",r.id);assertEquals(LocalRegistration.UNKNOWN,r.registration);assertEquals(0,r.updatedAt)
    }
    @Test fun familyRecordsRemainDistinctAndRoundTripWithRedactedToString(){
        val a=PersonalHealthRecord(name="QA-SELF")
        val b=PersonalHealthRecord(name="QA-FAMILY",id="LOCAL-00000000-0000-0000-0000-000000000001",role=RegistrationRole.PROXY,proxyConsent=true,registration=LocalRegistration.USER_REPORTED,updatedAt=123)
        assertEquals(listOf(a,b),PersonalHealthCodec.decodeList(PersonalHealthCodec.encodeList(listOf(a,b))))
        assertFalse(b.toString().contains("QA-FAMILY"))
    }
    @Test fun proxyRecordRequiresExplicitAttestation(){assertFalse(PersonalHealthCodec.validate(PersonalHealthRecord(role=RegistrationRole.PROXY)))}
    @Test fun duplicateBeneficiaryIdentifiersAreRejected(){assertThrows(IllegalArgumentException::class.java){PersonalHealthCodec.encodeList(listOf(PersonalHealthRecord(),PersonalHealthRecord()))}}
    @Test fun corruptCollectionCannotSilentlyOverwriteExistingInformation(){assertThrows(Exception::class.java){PersonalHealthCodec.decodeList("[{}]")};assertThrows(Exception::class.java){PersonalHealthCodec.decodeList("not JSON")}}
    @Test fun twelveRecordLimitIsEnforced(){val records=(1..13).map{PersonalHealthRecord(id="LOCAL-00000000-0000-0000-0000-"+it.toString().padStart(12,'0'))};assertThrows(IllegalArgumentException::class.java){PersonalHealthCodec.encodeList(records)}}
    @Test fun phoneReferencesRejectUrlsAndControlCharacters(){listOf("https://example.test","1234567\n1234567","real phone text").forEach{assertFalse(PersonalHealthCodec.validate(PersonalHealthRecord(registeredPhone=it)))}}
    @Test fun officialRegistrationCannotBeForgedThroughCodec(){val raw=PersonalHealthCodec.encode(PersonalHealthRecord()).replace("UNKNOWN","OFFICIALLY_VERIFIED");assertThrows(Exception::class.java){PersonalHealthCodec.decode(raw)}}
}
