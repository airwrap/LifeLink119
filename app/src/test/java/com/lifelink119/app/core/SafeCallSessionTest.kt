package com.lifelink119.app.core

import com.lifelink119.app.data.ExperienceCatalogParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SafeCallSessionTest {
    private fun engine(person: String?="DEMO-P-1")=SessionEngine(ExperienceCatalogParser.parse(File("src/main/assets/experience-catalog.json").readText())).apply{
        setRestrictions(DrivingRestrictions.unrestrictedFixture());start("DEMO-12345678",ConnectionTarget.EMERGENCY_119,"S01",person);answer()
    }
    @Test fun lookupCannotBypassMedicalLock(){val e=engine();assertThrows(IllegalStateException::class.java){e.beginSafeCall(true)};assertTrue(e.current!!.active)}
    @Test fun targetMissingDoesNotInterruptEmergencySession(){val e=engine(null);e.unlockPrivate(true);assertNull(e.beginSafeCall(true));assertEquals(SafeCallOutcome.TARGET_REQUIRED,e.safeCallLink.outcome);assertEquals("NOT_REQUESTED",e.current!!.safeCall);assertTrue(e.current!!.active)}
    @Test fun patientChangeInvalidatesRequestEvenAfterReauthentication(){val e=engine();e.unlockPrivate(true);val r=e.beginSafeCall(true)!!;e.selectPerson("DEMO-P-2");e.unlockPrivate(true);assertFalse(e.completeSafeCall(r));assertTrue(e.current!!.active)}
    @Test fun backgroundInvalidatesPendingResponse(){val e=engine();e.unlockPrivate(true);val r=e.beginSafeCall(true)!!;e.background();e.foreground();e.unlockPrivate(true);assertFalse(e.completeSafeCall(r));assertTrue(e.current!!.active)}
    @Test fun newPlatformRestrictionInvalidatesLookupWithoutHangingUp(){val e=engine();e.unlockPrivate(true);val r=e.beginSafeCall(true)!!;e.setRestrictions(DrivingRestrictions());assertFalse(e.completeSafeCall(r));assertFalse(e.privateUnlocked);assertTrue(e.current!!.active)}
    @Test fun changingSituationInvalidatesOldResponse(){val e=engine();e.unlockPrivate(true);val r=e.beginSafeCall(true)!!;e.selectCase("S02");assertFalse(e.completeSafeCall(r))}
    @Test fun timeoutDoesNotEndSessionOrBecomeUnregistered(){val e=engine();e.unlockPrivate(true);val r=e.beginSafeCall(true,SafeCallFault.TIMEOUT)!!;assertTrue(e.completeSafeCall(r));assertEquals(SafeCallOutcome.TIMEOUT,e.safeCallLink.outcome);assertTrue(e.current!!.active);assertEquals("NOT_REQUESTED",e.current!!.safeCall)}
    @Test fun finishedSessionCannotAcceptResponse(){val e=engine();e.unlockPrivate(true);val r=e.beginSafeCall(true)!!;e.finish();assertFalse(e.completeSafeCall(r));assertTrue(e.current!!.ended)}
    @Test fun syntheticNumberMatchNeverSharesMedicalFields(){val e=engine();e.unlockPrivate(true);e.configureSafeCall(line=DemoCallLine.REGISTERED);val r=e.beginSafeCall(true)!!;assertTrue(e.completeSafeCall(r));assertEquals(SafeCallOutcome.MATCHED,e.safeCallLink.outcome);assertTrue(e.current!!.shared.isEmpty());assertEquals("MOCK_REGISTERED",e.current!!.safeCall)}
    @Test fun explicitLockClearsOldMatchedResult(){val e=engine();e.unlockPrivate(true);e.configureSafeCall(line=DemoCallLine.REGISTERED);e.completeSafeCall(e.beginSafeCall(true)!!);e.lockPrivate();assertEquals(SafeCallOutcome.NOT_REQUESTED,e.safeCallLink.outcome);assertEquals("NOT_REQUESTED",e.current!!.safeCall)}
}
