package com.lifelink119.app.data

import org.junit.Assert.*
import org.junit.Test

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
}
