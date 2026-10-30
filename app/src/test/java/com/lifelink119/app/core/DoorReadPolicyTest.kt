package com.lifelink119.app.core

import org.junit.Assert.*
import org.junit.Test

class DoorReadPolicyTest {
    @Test fun writeOnlyAndUnknownCapabilitiesNeverPermitReading() {
        for (access in listOf("NONE", "WRITE", "UNKNOWN")) {
            assertEquals(DiagnosticStatus.UNSUPPORTED, DoorReadPolicy.capabilityProblem(access, true, 0, 100)?.status)
        }
    }
    @Test fun readableCapabilityStillRequiresTheDriverArea() {
        assertEquals(DiagnosticStatus.UNSUPPORTED, DoorReadPolicy.capabilityProblem("READ", false, null, null)?.status)
    }
    @Test fun validDriverAreaCanBeReadWithEitherReadPermission() {
        assertNull(DoorReadPolicy.capabilityProblem("READ", true, 0, 100))
        assertNull(DoorReadPolicy.capabilityProblem("READ_WRITE", true, 0, 100))
    }
    @Test fun malformedBoundsAreNotUsed() {
        assertEquals(DiagnosticStatus.ERROR, DoorReadPolicy.capabilityProblem("READ", true, 100, 0)?.status)
    }
    @Test fun aMissingResultIsNotReportedAsSuccessfulOrAsZero() {
        assertEquals(DiagnosticStatus.UNAVAILABLE, DoorReadPolicy.position(null, 0, 100).status)
        assertEquals(DiagnosticStatus.READ, DoorReadPolicy.position(0, 0, 100).status)
    }
    @Test fun outOfRangeResultsRemainErrors() {
        assertEquals(DiagnosticStatus.ERROR, DoorReadPolicy.position(101, 0, 100).status)
    }
    @Test fun unknownSdkExceptionsAreNotMislabelledAsPermissionFailures() {
        assertEquals(DiagnosticStatus.ERROR, DoorReadPolicy.failure("지원 기능 확인", Exception("opaque SDK error")).status)
        assertEquals(DiagnosticStatus.PERMISSION_DENIED, DoorReadPolicy.failure("도어 위치 조회", SecurityException()).status)
    }
}
