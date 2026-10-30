package com.lifelink119.app.core

import com.lifelink119.app.data.ExperienceCatalogParser
import com.lifelink119.app.data.ExperienceStateCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MedicalPrivacyTest {
    @Test fun resetRejectsAnOlderPinCalculationBeforeItCanWrite(){
        val epoch=MedicalAuthEpoch();val token=epoch.token;var stored:String?="old"
        epoch.invalidate{stored=null}
        assertFalse(epoch.runIfCurrent(token){stored="late verifier"});assertNull(stored)
        assertTrue(epoch.runIfCurrent(epoch.token){stored="new verifier"});assertEquals("new verifier",stored)
    }
    @Test fun resetWaitsForAnAlreadyCommittingAttemptAndErasesItsResult(){
        val epoch=MedicalAuthEpoch();val entered=CountDownLatch(1);val finish=CountDownLatch(1);val resetDone=CountDownLatch(1);var stored:String?=null
        val token=epoch.token
        val writer=Thread{epoch.runIfCurrent(token){entered.countDown();check(finish.await(2,TimeUnit.SECONDS));stored="verifier"}}
        writer.start();assertTrue(entered.await(2,TimeUnit.SECONDS))
        val reset=Thread{epoch.invalidate{stored=null};resetDone.countDown()};reset.start()
        try{assertFalse(resetDone.await(50,TimeUnit.MILLISECONDS))}finally{finish.countDown()}
        writer.join(2000);reset.join(2000);assertFalse(writer.isAlive);assertFalse(reset.isAlive);assertNull(stored);assertFalse(epoch.isCurrent(token))
    }
    @Test fun usesExistingDeviceCredentialWhenItCanBePresented(){assertEquals(MedicalAuthRoute.DEVICE_CREDENTIAL,MedicalPrivacyPolicy.route(true,true,false))}
    @Test fun missingOrUnavailableNativeCredentialRequiresPinSetup(){assertEquals(MedicalAuthRoute.APP_PIN_SETUP,MedicalPrivacyPolicy.route(false,true,false));assertEquals(MedicalAuthRoute.APP_PIN_SETUP,MedicalPrivacyPolicy.route(true,false,false))}
    @Test fun laterSystemLockChangeCannotBypassAnExistingAppPin(){assertEquals(MedicalAuthRoute.APP_PIN,MedicalPrivacyPolicy.route(true,true,true));assertEquals(MedicalAuthRoute.APP_PIN,MedicalPrivacyPolicy.route(false,false,true))}
    @Test fun onlySixToTwelveAsciiDigitsAreAccepted(){assertTrue(MedicalPrivacyPolicy.validPin("908172".toCharArray()));assertTrue(MedicalPrivacyPolicy.validPin("908172635409".toCharArray()));for(s in listOf("","12345","1234567890123","90817a","１２３４５６"," 908172"))assertFalse(MedicalPrivacyPolicy.validPin(s.toCharArray()))}
    @Test fun authenticationExpiresAtSixtySecondsAndExplicitLockIsImmediate(){var now=1000L;val a=MedicalAccessWindow{now};assertFalse(a.authorized);a.unlock();now+=59_999;assertTrue(a.authorized);now++;assertFalse(a.authorized);a.unlock();assertTrue(a.authorized);a.lock();assertFalse(a.authorized)}
    @Test fun processRecreationDoesNotRestoreAnUnlockGrant(){var now=1000L;val a=MedicalAccessWindow{now};a.unlock();assertTrue(a.authorized);assertFalse(MedicalAccessWindow{now}.authorized)}
    @Test fun fifthFailureStartsPersistentThirtySecondBackoff(){var s=PinAttemptState();repeat(4){s=PinAttemptPolicy.failed(s,1000);assertEquals(0,PinAttemptPolicy.remainingSeconds(s,1000))};s=PinAttemptPolicy.failed(s,1000);assertEquals(30,PinAttemptPolicy.remainingSeconds(s,1000));assertEquals(1,PinAttemptPolicy.remainingSeconds(s,30_999));assertEquals(0,PinAttemptPolicy.remainingSeconds(s,31_000));assertEquals(s,PinAttemptState(s.failures,s.strikes,s.lockedUntil))}
    @Test fun repeatedFailureGroupsIncreaseDelayAndCapAtFiveMinutes(){var s=PinAttemptState();var now=1000L;val waits=mutableListOf<Int>();repeat(7){repeat(5){s=PinAttemptPolicy.failed(s,now)};waits+=PinAttemptPolicy.remainingSeconds(s,now);now=s.lockedUntil+1};assertEquals(listOf(30,60,120,240,300,300,300),waits)}
    @Test fun lockedAliasNeverIncludesTheSelectedPersonNameOrDisability(){val alias="PRIVATE-ALIAS";assertFalse(MedicalPrivacyPolicy.safeAlias(false,alias,true).contains(alias));assertEquals(alias,MedicalPrivacyPolicy.safeAlias(true,alias,true));assertEquals("미등록 승객",MedicalPrivacyPolicy.safeAlias(false,null,false))}
    @Test fun saltedPepperedVerifierRejectsDifferentPinAndSalt(){
        val key=ByteArray(32){19};val pepper={bytes:ByteArray->Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(key,"HmacSHA256"))}.doFinal(bytes)}
        val salt=ByteArray(16){3};val expected=MedicalPinDigest.derive("908172".toCharArray(),salt,pepper)
        assertEquals(32,expected.size);assertTrue(MessageDigest.isEqual(expected,MedicalPinDigest.derive("908172".toCharArray(),salt,pepper)))
        assertFalse(MessageDigest.isEqual(expected,MedicalPinDigest.derive("908173".toCharArray(),salt,pepper)))
        assertFalse(MessageDigest.isEqual(expected,MedicalPinDigest.derive("908172".toCharArray(),ByteArray(16){4},pepper)))
    }
    @Test fun savedInstanceStateDoesNotContainPersonalMedicalPayload(){
        val c=ExperienceCatalogParser.parse(File("src/main/assets/experience-catalog.json").readText());val e=SessionEngine(c);e.setRestrictions(DrivingRestrictions.unrestrictedFixture());e.start("DEMO-1234",ConnectionTarget.EMERGENCY_119,"S05","DEMO-P-10");e.answer();e.observe("HELP","AGO5",80);e.share(setOf(ShareField.ALIAS,ShareField.CONDITIONS,ShareField.LOCATION,ShareField.SUMMARY));e.sendPhrase(c.words.getValue("phrases").keys.first())
        val json=JSONObject(ExperienceStateCodec.encodePrivacySafe(e.current)!!);assertTrue(json.isNull("person"));assertTrue(json.isNull("glucose"));assertEquals(0,json.getJSONArray("messages").length());assertEquals(setOf("LOCATION"),json.getJSONObject("shared").keys().asSequence().toSet())
        val restored=ExperienceStateCodec.decode(json.toString(),c,e.profiles)!!;assertNull(restored.personId);assertNull(restored.demoGlucose);assertTrue(restored.messages.isEmpty());assertEquals(setOf(ShareField.LOCATION),restored.shared.keys)
    }
}
