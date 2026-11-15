package com.lifelink119.app.core

import org.junit.Assert.*
import org.junit.Test

class SafeCallLinkTest {
    private fun link(person: String? = "DEMO-P-1") =
        SafeCallLink().apply { attach("DEMO-12345678", person) }

    private fun resolve(
        line: DemoCallLine,
        registered: Boolean = true,
        fault: SafeCallFault = SafeCallFault.NONE,
    ): SafeCallLink =
        link().apply {
            setLine(line)
            assertTrue(complete(requireNotNull(begin(registered, true, fault))))
        }

    @Test
    fun missingConsentDoesNotStartLookup() {
        val l = link()
        assertThrows(IllegalStateException::class.java) { l.begin(true, false) }
        assertEquals(SafeCallOutcome.NOT_REQUESTED, l.outcome)
    }

    @Test
    fun noSessionCannotLookup() {
        assertThrows(IllegalStateException::class.java) { SafeCallLink().begin(true, true) }
    }

    @Test
    fun noTargetIsNotMisclassifiedAsUnregistered() {
        val l = link(null)
        assertNull(l.begin(false, true))
        assertEquals(SafeCallOutcome.TARGET_REQUIRED, l.outcome)
    }

    @Test
    fun registeredLineMatchesOnlySyntheticData() {
        val l = resolve(DemoCallLine.REGISTERED)
        assertEquals(SafeCallOutcome.MATCHED, l.outcome)
        assertFalse(l.officialConnected)
    }

    @Test
    fun differentPhoneDoesNotChangeTarget() {
        assertEquals(SafeCallOutcome.DIFFERENT, resolve(DemoCallLine.OTHER).outcome)
    }

    @Test
    fun vehicleLineIsNotAssumedToBePersonalPhone() {
        assertEquals(SafeCallOutcome.DIFFERENT, resolve(DemoCallLine.VEHICLE).outcome)
    }

    @Test
    fun unknownPhoneIsNotUnregistered() {
        assertEquals(SafeCallOutcome.UNKNOWN, resolve(DemoCallLine.UNKNOWN).outcome)
    }

    @Test
    fun sharedPhoneNeedsAdditionalTargetCheck() {
        assertEquals(SafeCallOutcome.AMBIGUOUS, resolve(DemoCallLine.SHARED).outcome)
    }

    @Test
    fun explicitSyntheticMissingRecordIsNotFound() {
        assertEquals(SafeCallOutcome.NOT_FOUND, resolve(DemoCallLine.REGISTERED, false).outcome)
    }

    @Test
    fun timeoutIsNotNotFound() {
        assertEquals(
            SafeCallOutcome.TIMEOUT,
            resolve(DemoCallLine.REGISTERED, fault = SafeCallFault.TIMEOUT).outcome,
        )
    }

    @Test
    fun errorIsNotNotFound() {
        assertEquals(
            SafeCallOutcome.ERROR,
            resolve(DemoCallLine.REGISTERED, fault = SafeCallFault.ERROR).outcome,
        )
    }

    @Test
    fun unsupportedIsNotFakeOfficialSuccess() {
        assertEquals(
            SafeCallOutcome.UNSUPPORTED,
            resolve(DemoCallLine.REGISTERED, fault = SafeCallFault.UNSUPPORTED).outcome,
        )
    }

    @Test
    fun proxyMustConfirmManagementAuthority() {
        val l = link()
        l.setRole(RegistrationRole.PROXY)
        assertNull(l.begin(true, true))
        assertEquals(SafeCallOutcome.AUTHORITY_REQUIRED, l.outcome)
    }

    @Test
    fun proxyCanProceedAfterExplicitAttestation() {
        val l = link()
        l.setRole(RegistrationRole.PROXY)
        l.setAuthority(true)
        assertNotNull(l.begin(true, true))
    }

    @Test
    fun changingLineInvalidatesPendingRequest() {
        val l = link()
        val old = l.begin(true, true)!!
        l.setLine(DemoCallLine.OTHER)
        assertFalse(l.complete(old))
        assertFalse(l.consent)
        assertEquals(SafeCallOutcome.NOT_REQUESTED, l.outcome)
    }

    @Test
    fun changingPatientRejectsOldResponse() {
        val l = link()
        val old = l.begin(true, true)!!
        l.attach("DEMO-12345678", "DEMO-P-2")
        assertFalse(l.complete(old))
    }

    @Test
    fun changingSessionRejectsOldResponse() {
        val l = link()
        val old = l.begin(true, true)!!
        l.attach("DEMO-87654321", "DEMO-P-1")
        assertFalse(l.complete(old))
    }

    @Test
    fun changingRoleRevokesConsentAndAuthority() {
        val l = link()
        val old = l.begin(true, true)!!
        l.setRole(RegistrationRole.PROXY)
        assertFalse(l.complete(old))
        assertFalse(l.authorityConfirmed)
    }

    @Test
    fun lockRejectsLateResult() {
        val l = link()
        val old = l.begin(true, true)!!
        l.lock()
        assertFalse(l.complete(old))
        assertFalse(l.consent)
    }

    @Test
    fun newRequestRejectsOlderGeneration() {
        val l = link()
        val old = l.begin(true, true)!!
        val fresh = l.begin(true, true)!!
        assertFalse(l.complete(old))
        assertTrue(l.complete(fresh))
        assertFalse(l.complete(fresh))
    }

    @Test
    fun revokeClearsCompletedOutcome() {
        val l = resolve(DemoCallLine.REGISTERED)
        l.revoke()
        assertEquals(SafeCallOutcome.NOT_REQUESTED, l.outcome)
        assertFalse(l.consent)
    }

    @Test
    fun endingSessionPreventsCompletion() {
        val l = link()
        val old = l.begin(true, true)!!
        l.attach(null, null)
        assertFalse(l.complete(old))
    }

    @Test
    fun identifiersCannotContainRealNamesOrPhoneNumbers() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeCallLink().attach("01012345678", "REAL-PERSON")
        }
    }

    @Test
    fun publicSitesAreFixedHttpsAndContainNoQueryOrIdentifiers() {
        OfficialHealthSite.entries.forEach { site ->
            val uri = java.net.URI(site.url)
            assertEquals("https", uri.scheme)
            assertNull(uri.query)
            assertNull(uri.userInfo)
            assertTrue(
                uri.host in setOf("u119.nfa.go.kr", "www.nfa.go.kr", "www.myhealthway.go.kr")
            )
        }
    }
}
