package com.lifelink119.app.core

import com.lifelink119.app.data.ExperienceCatalogParser
import com.lifelink119.app.data.ExperienceStateCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

private fun catalog()=ExperienceCatalogParser.parse(File("src/main/assets/experience-catalog.json").readText(Charsets.UTF_8))
private fun engine(caseId: String="E04",person: String?=null,target: ConnectionTarget=ConnectionTarget.EMERGENCY_119)=SessionEngine(catalog()).apply {
    setRestrictions(DrivingRestrictions.unrestrictedFixture());start("DEMO-12345678",target,caseId,person);answer()
}
private inline fun rejects(block: ()->Unit){try{block();fail("Expected a rejected transition")}catch(_: IllegalArgumentException){}catch(_: IllegalStateException){}}

@RunWith(Parameterized::class)
class ScenarioExperienceTest(private val id: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name="{0}") fun cases()=catalog().scenarios.map { arrayOf(it.id) } }
    @Test fun fixtureConnectsWithoutConsentAndResolvesThreeExpectedActionsThenClears(){
        val c=catalog();val spec=c.scenario(id);val e=SessionEngine(c);e.setRestrictions(DrivingRestrictions.unrestrictedFixture())
        e.start("DEMO-12345678",spec.target,id,spec.profileHint)
        assertEquals(LinkStatus.REQUESTING,e.current!!.status);assertTrue(e.current!!.shared.isEmpty())
        e.answer();assertEquals(LinkStatus.DEMO_CONNECTED,e.current!!.status)
        val plan=ContextualActionResolver.resolve(e.person(),spec,spec.target,e.restrictions)
        assertEquals(spec.priorities,plan.primary);assertEquals(3,plan.primary.distinct().size)
        assertTrue(plan.additional.none { it in plan.primary })
        e.share(setOf(ShareField.LOCATION));assertEquals(LinkStatus.SUPPORT_IN_PROGRESS,e.current!!.status)
        e.finish();assertTrue(e.current!!.ended);assertTrue(e.current!!.shared.isEmpty());assertNull(e.current!!.personId)
    }
    @Test fun sameCaseIsUsableForUnregisteredPassenger(){
        val spec=catalog().scenario(id);val e=engine(id,null,spec.target)
        assertNull(e.person());assertTrue(e.current!!.active);assertTrue(e.current!!.shared.isEmpty())
        assertEquals(3,ContextualActionResolver.resolve(null,spec,spec.target,e.restrictions).primary.size)
    }
}

class ExperienceStateTest {
    @Test fun noRegisteredProfilesStillAllowsUrgentRequestLocationAndFinish(){val e=SessionEngine(catalog(),emptyList());e.start("DEMO-1234",ConnectionTarget.EMERGENCY_119);e.answer();assertTrue(e.profiles.isEmpty());assertNull(e.person());e.share(setOf(ShareField.LOCATION));assertTrue(e.current!!.active);e.finish();assertTrue(e.current!!.shared.isEmpty())}
    @Test fun directTargetsNeedNoProfileOrConsent(){for(target in ConnectionTarget.entries){val e=engine(target=target);assertTrue(e.current!!.active);assertNull(e.person());assertTrue(e.current!!.shared.isEmpty())}}
    @Test fun failureRetryAndCancelHaveGuardedTransitions(){val e=SessionEngine(catalog());e.failNextConnection=true;e.start("DEMO-1234",ConnectionTarget.EMERGENCY_112);e.answer();assertEquals(LinkStatus.FAILED,e.current!!.status);rejects{e.share(setOf(ShareField.LOCATION))};e.retry();e.answer();assertTrue(e.current!!.active);rejects{e.retry()};e.finish(true);assertEquals(LinkStatus.CANCELLED,e.current!!.status);rejects{e.requestPolice()}}
    @Test fun consentIsFieldSpecificAndRevocable(){val e=engine("S01","DEMO-P-1");assertFalse(e.privateUnlocked);e.share(setOf(ShareField.ALLERGIES));assertEquals(setOf(ShareField.ALLERGIES),e.current!!.shared.keys);e.share(setOf(ShareField.LOCATION));e.revoke(ShareField.ALLERGIES);assertEquals(setOf(ShareField.LOCATION),e.current!!.shared.keys);e.revoke();assertTrue(e.current!!.shared.isEmpty());rejects{e.share(emptySet())}}
    @Test fun missingProfileAndLocationDoNotCrashOrPreventConnection(){val e=engine();rejects{e.share(setOf(ShareField.CONDITIONS))};e.locationAvailable=false;assertNull(e.value(ShareField.LOCATION));rejects{e.share(setOf(ShareField.LOCATION))};assertTrue(e.current!!.active);e.locationAvailable=true;e.share(setOf(ShareField.LOCATION))}
    @Test fun changingPatientDoesNotCarryMedicalConsentOrTransportApproval(){val e=engine("S01","DEMO-P-1");e.share(setOf(ShareField.ALLERGIES,ShareField.LOCATION));e.giveGuidance();e.proposeNavigation("MEET_B");e.confirmNavigation();e.setNavigation();e.selectPerson("DEMO-P-2");assertEquals(setOf(ShareField.LOCATION),e.current!!.shared.keys);assertEquals(NavigationStatus.NONE,e.current!!.navigation);assertFalse(e.current!!.guidance);assertFalse(e.privateUnlocked)}
    @Test fun changingContextRequiresNewVideoSummaryAndNavigationConsent(){val e=engine("S05","DEMO-P-10");e.observe("HELP","AGO5",80);e.share(setOf(ShareField.SUMMARY));e.requestVideo(VideoSource.PHONE);e.approveVideo();e.startVideo();e.selectCase("S14");assertNull(e.current!!.demoGlucose);assertFalse(e.videoPreview);assertTrue(e.current!!.shared.isEmpty());assertEquals(VideoStatus.STOPPED,e.current!!.video)}
    @Test fun videoRequiresRequestConsentAndStartAndStops(){val e=engine();rejects{e.startVideo()};e.requestVideo(VideoSource.FRONT);assertTrue(e.current!!.shared.isEmpty());e.approveVideo();assertFalse(e.videoPreview);e.startVideo();assertTrue(e.videoPreview);assertTrue(ShareField.VIDEO in e.current!!.shared);e.stopVideo();assertFalse(e.videoPreview);assertFalse(ShareField.VIDEO in e.current!!.shared)}
    @Test fun backgroundHidesSensitiveViewAndPreviewButNotSession(){val e=engine("S01","DEMO-P-1");e.unlockPrivate(true);e.requestVideo(VideoSource.REAR);e.approveVideo();e.startVideo();e.background();assertFalse(e.privateUnlocked);assertFalse(e.videoPreview);assertTrue(e.current!!.active);e.openVideoPreview();assertTrue(e.videoPreview)}
    @Test fun actualRestrictionsCannotBeDisabledByDemoToggle(){val e=engine();val actual=DrivingRestrictions.unrestrictedFixture().copy(source=CapabilityStatus.REAL,noVideo=true,noSetup=true,noMessages=true);e.setRestrictions(actual.withTestRestriction(false));rejects{e.requestVideo(VideoSource.PHONE)};rejects{e.unlockPrivate(true)};rejects{e.sendPhrase(catalog().words.getValue("phrases").keys.first())};assertTrue(e.current!!.active);assertTrue(actual.withTestRestriction(true).videoBlocked);assertTrue(DrivingRestrictions().videoBlocked)}
    @Test fun restrictionChangeHidesPreviewAndPrivacy(){val e=engine();e.unlockPrivate(true);e.requestVideo(VideoSource.PHONE);e.approveVideo();e.startVideo();e.setRestrictions(DrivingRestrictions());assertFalse(e.privateUnlocked);assertFalse(e.videoPreview);val p=ContextualActionResolver.resolve(null,e.catalog.scenario("S01"),ConnectionTarget.EMERGENCY_119,e.restrictions);assertFalse(SupportAction.VIDEO in p.primary);assertFalse(SupportAction.TEXT in p.primary)}
    @Test fun policeRequiresReviewAndHasDistinctApprovalAndRejection(){val e=engine();rejects{e.decidePolice(true)};e.requestPolice();assertEquals(PoliceStatus.REQUESTED,e.current!!.police);e.reviewPolice();e.decidePolice(false);assertEquals(PoliceStatus.REJECTED,e.current!!.police);e.requestPolice();e.reviewPolice();e.decidePolice(true);assertEquals(PoliceStatus.APPROVED,e.current!!.police)}
    @Test fun navigationNeedsProfessionalGuidanceLocationAndUserConfirmation(){val e=engine();rejects{e.proposeNavigation("MEET_B")};e.giveGuidance();rejects{e.proposeNavigation("MEET_B")};e.share(setOf(ShareField.LOCATION));e.proposeNavigation("MEET_B");rejects{e.setNavigation()};e.confirmNavigation();e.setNavigation();assertEquals(NavigationStatus.SET,e.current!!.navigation);e.revoke(ShareField.LOCATION);assertEquals(NavigationStatus.NONE,e.current!!.navigation);assertNull(e.current!!.destination)}
    @Test fun psychologicalPrivacyNeverAutomaticallyNotifiesGuardian(){val e=engine("S13","DEMO-P-2",ConnectionTarget.COUNSEL_109);rejects{e.requestPolice()};val p=ContextualActionResolver.resolve(e.person(),e.catalog.scenario("S13"),e.current!!.target,e.restrictions);assertEquals(listOf(SupportAction.PRIVACY,SupportAction.TEXT,SupportAction.SUMMARY),p.primary);assertTrue(e.current!!.shared.isEmpty());assertFalse(SupportAction.POLICE in p.additional)}
    @Test fun safeCallOnlyLooksUpSyntheticRegistrationAfterConsent(){val e=engine("S01","DEMO-P-1");rejects{e.safeCall(false)};assertEquals("NOT_REQUESTED",e.current!!.safeCall);e.safeCall(true);assertEquals("MOCK_REGISTERED",e.current!!.safeCall);e.selectPerson(null);e.safeCall(true);assertEquals("MOCK_NOT_REGISTERED",e.current!!.safeCall)}
    @Test fun driverSelectionNeverSelectsPatientOrSharesData(){val e=engine();e.selectDriver("DEMO-P-1");assertNull(e.person());assertTrue(e.current!!.shared.isEmpty());e.selectPerson("DEMO-P-2");assertEquals("DEMO-P-1",e.driverProfileId);assertEquals("DEMO-P-2",e.person()!!.id)}
    @Test fun profileCrudAcceptsOnlyFixedSyntheticVocabulary(){val e=engine();val p=e.profiles.first().copy(id="DEMO-P-11",aliasKey="NEW_A");e.addProfile(p);e.editProfile(p.copy(ageKey="ADULT"));assertEquals("ADULT",e.profiles.last().ageKey);rejects{e.addProfile(p.copy(id="DEMO-P-12",aliasKey="Real Person"))};e.selectPerson(p.id);e.share(setOf(ShareField.ALLERGIES));e.deleteProfile(p.id);assertNull(e.person());assertTrue(e.current!!.shared.isEmpty());e.restoreProfiles();assertEquals(10,e.profiles.size)}
    @Test fun summaryEditRevokesOldSharedSummaryAndValidatesInput(){val e=engine("S05","DEMO-P-10");e.share(setOf(ShareField.SUMMARY));e.observe("HELP","AGO10",90);assertFalse(ShareField.SUMMARY in e.current!!.shared);assertTrue(e.summary().contains("90 mg/dL"));rejects{e.observe("HELP","AGO10",0)};rejects{e.observe("UNKNOWN","NOW")}}
    @Test fun textAndSignAreExplicitMockStates(){val e=engine("S08","DEMO-P-3");e.sendPhrase(e.catalog.words.getValue("phrases").keys.first());e.reply();assertEquals(2,e.current!!.messages.size);e.sign(true);assertEquals(SignStatus.CONNECTED,e.current!!.sign);e.sign(false);assertEquals(SignStatus.OFF,e.current!!.sign);e.revoke(ShareField.TEXT);assertFalse(ShareField.TEXT in e.current!!.shared)}
    @Test fun recreationPreservesOnlyWhitelistedSyntheticSessionAndLocksViews(){val e=engine("S01","DEMO-P-1");e.share(setOf(ShareField.ALLERGIES));e.unlockPrivate(true);val decoded=ExperienceStateCodec.decode(ExperienceStateCodec.encode(e.current),e.catalog,e.profiles);assertEquals(e.current,decoded);val restored=SessionEngine(e.catalog);restored.restore(decoded,"DEMO-P-2");assertFalse(restored.privateUnlocked);assertFalse(restored.videoPreview);assertEquals("DEMO-P-2",restored.driverProfileId)}
    @Test fun restorationDiscardsInjectedSensitiveTextAndUnknownSession(){val e=engine("S01","DEMO-P-1");e.share(setOf(ShareField.ALLERGIES));val data=JSONObject(ExperienceStateCodec.encode(e.current)!!);data.getJSONObject("shared").put("ALLERGIES","PERSONAL DATA");data.put("messages",org.json.JSONArray(listOf("PERSONAL DATA")));val decoded=ExperienceStateCodec.decode(data.toString(),e.catalog,e.profiles)!!;assertTrue(decoded.shared.isEmpty());assertTrue(decoded.messages.isEmpty());data.put("id","REAL-1234");assertNull(ExperienceStateCodec.decode(data.toString(),e.catalog,e.profiles))}
    @Test fun finishClearsAllSessionSensitiveFieldsAndRestoresClean(){val e=engine("S05","DEMO-P-10");e.observe("HELP","AGO5",100);e.share(setOf(ShareField.SUMMARY,ShareField.LOCATION));e.requestVideo(VideoSource.PHONE);e.approveVideo();e.startVideo();e.safeCall(true);e.finish();val s=e.current!!;assertTrue(s.shared.isEmpty());assertNull(s.personId);assertNull(s.demoGlucose);assertTrue(s.messages.isEmpty());assertFalse(s.guidance);assertFalse(e.videoPreview);assertEquals(s,ExperienceStateCodec.decode(ExperienceStateCodec.encode(s),e.catalog,e.profiles))}
}

class V2lEstimateTest {
    @Test fun documentedFormula(){val p=V2lCalculator.estimate(true,true,64.0,72.0,30.0,.85,200.0);assertEquals(22.848,p.availableKwh!!,1e-9);assertEquals(114.24,p.hours!!,1e-9);assertNull(p.issue)}
    @Test fun emptyAndReserveAboveSocYieldZero(){for(soc in listOf(0.0,20.0,30.0)){val p=V2lCalculator.estimate(true,true,64.0,soc,30.0,.85,200.0);assertEquals(0.0,p.hours!!,0.0);assertEquals(0.0,p.availableKwh!!,0.0)}}
    @Test fun zeroLoadAndOutputLimitDoNotGiveMisleadingHours(){for(load in listOf(0.0,4000.0)){val p=V2lCalculator.estimate(true,true,64.0,72.0,30.0,.85,load);assertNull(p.hours);assertNotNull(p.issue)}}
    @Test fun unsupportedAndNonElectricFallback(){for(pair in listOf(false to true,true to false)){val p=V2lCalculator.estimate(pair.first,pair.second,64.0,72.0,30.0,.85,200.0);assertNull(p.availableKwh);assertNull(p.hours);assertNotNull(p.issue)}}
    @Test fun invalidNumbersNeverLeakNaNOrInfiniteResult(){for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,101.0)){val p=V2lCalculator.estimate(true,true,64.0,value,30.0,.85,200.0);assertNull(p.hours);assertNotNull(p.issue)};assertNull(V2lCalculator.estimate(true,true,0.0,50.0,30.0,.85,200.0).hours);assertNull(V2lCalculator.estimate(true,true,64.0,50.0,30.0,1.1,200.0).hours)}
}
