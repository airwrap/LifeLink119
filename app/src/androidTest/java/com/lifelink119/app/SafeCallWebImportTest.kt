package com.lifelink119.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifelink119.app.data.*
import com.lifelink119.app.ui.ExpansionUi
import com.lifelink119.app.ui.OfficialWebSettings
import com.lifelink119.app.ui.SafeCallImportReviewUi
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic DOM only. No network, actual account, SMS or attached medical data. */
@RunWith(AndroidJUnit4::class)
class SafeCallWebImportTest {
    private val prefix = "mf_wfm_container_wfm_userInfo_"

    private fun fixture(): String = buildString {
        append("<!doctype html><html><body>")
        fun input(id: String, value: String = "", type: String = "text") {
            append("<input id='$prefix$id' type='$type' value='$value'>")
        }
        input("ibx_name", "QA 본인")
        input("ibx_phone1", "010")
        input("ibx_phone2", "0000")
        input("ibx_phone3", "0000")
        input("ibx_takngDrfstfCn")
        input("ibx_hstcsInfoCn", "가상 과거력")
        input("ibx_pw", "NEVER_EXPORT_PASSWORD", "password")
        input("ibx_id", "NEVER_EXPORT_ID")
        input("smsVerification", "NEVER_EXPORT_OTP")
        append("<input type='hidden' value='NEVER_EXPORT_HIDDEN'>")
        append(
            "<div id='${prefix}sbx_bhaveSeCode' role='combobox'><div id='${prefix}sbx_bhaveSeCode_label' class='w2selectbox_label'>정상</div></div>"
        )
        append(
            "<div class='form-group'><h5 id='${prefix}wq_uuid_1111'>전화통화 가능여부 (선택 입력)</h5><div class='w2radio'><input type='radio' id='changed_uuid_99' checked><label for='changed_uuid_99'>가능</label></div></div>"
        )
        // The heading ID must belong to this beneficiary form; numeric UUID is deliberately
        // different.
        append(
            "<div class='form-group'><h5 id='${prefix}wq_uuid_9876'>승강기 설치여부 (선택 입력)</h5><input type='radio' id='changed_uuid_88' checked><label for='changed_uuid_88'>있음</label></div>"
        )
        listOf("gen_setDiss", "gen_firstDiss", "gen_setFirstDsblty", "gen_firstDsblty").forEach {
            append("<div id='$prefix$it'></div>")
        }
        append("</body></html>")
    }

    private fun extract(
        base: String = "https://u119.nfa.go.kr/",
        before: String = "",
        html: String = fixture(),
    ): JSONObject {
        val i = InstrumentationRegistry.getInstrumentation()
        lateinit var web: WebView
        val loaded = CountDownLatch(1)
        i.runOnMainSync {
            web = WebView(i.targetContext)
            OfficialWebSettings.configure(web)
            web.webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ) =
                        WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0)),
                        )

                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
            web.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
        }
        try {
            assertTrue("Synthetic DOM must load", loaded.await(10, TimeUnit.SECONDS))
            val result = AtomicReference<String>()
            val done = CountDownLatch(1)
            val script =
                i.targetContext.assets.open("safe-call-extract.js").bufferedReader().use {
                    it.readText()
                }
            i.runOnMainSync {
                web.evaluateJavascript(before + "\n" + script) {
                    result.set(it)
                    done.countDown()
                }
            }
            assertTrue("Extraction callback must arrive", done.await(10, TimeUnit.SECONDS))
            return JSONObject(JSONTokener(result.get()).nextValue() as String)
        } finally {
            i.runOnMainSync { web.destroy() }
        }
    }

    @Test
    fun liveValuesDisabledFieldsCustomSelectAndSemanticRadioAreReadWithoutCredentials() {
        val r =
            extract(
                before =
                    "document.getElementById('${prefix}ibx_takngDrfstfCn').value='가상 복용약';document.getElementById('${prefix}ibx_name').disabled=true;"
            )
        val p = SafeCallWebImport.prepare(r.toString(), 1234, null)
        assertEquals("가상 복용약", p.record.medicationHistory)
        assertEquals("정상", p.record.safeCall.mobility)
        assertEquals(OptionalAnswer.YES, p.record.safeCall.elevator)
        assertEquals(OptionalAnswer.YES, p.record.safeCall.canCall)
        assertFalse(r.toString().contains("NEVER_EXPORT"))
    }

    private fun nativeSelect(suffix: String, label: String): String =
        "<div id='$prefix$suffix' class='w2selectbox_native w2selectbox_native_mobile'>" +
            "<div class='w2selectbox_native_innerDiv'>" +
            "<select class='w2selectbox_native_select' id='$prefix${suffix}_input_0'>" +
            "<option value='UNSELECTED_SECRET_CODE'>선택</option>" +
            "<option value='SELECTED_SECRET_CODE' selected>$label</option>" +
            "</select></div></div>"

    @Test
    fun androidNativeSelectLabelsAreImportedWithoutOptionCodesOrUnselectedValues() {
        val html =
            fixture()
                .replace(
                    "</body>",
                    nativeSelect("sbx_propTp", "질병외") +
                        nativeSelect("sbx_propTpCode", "고령자") +
                        nativeSelect("sbx_bldtyCd", "선택") +
                        nativeSelect("sbx_bldtySeCd", "선택") +
                        "</body>",
                )
        val r =
            extract(
                html = html,
                before =
                    "document.getElementById('${prefix}ibx_hstcsInfoCn').value='';" +
                        "document.getElementById('${prefix}sbx_bhaveSeCode').outerHTML=" +
                        JSONObject.quote(nativeSelect("sbx_bhaveSeCode", "정상")) +
                        ";",
            )
        val p = SafeCallWebImport.prepare(r.toString(), 1234, null)
        assertEquals("질병외", p.record.safeCall.applicationType)
        assertEquals("고령자", p.record.safeCall.applicationDetail)
        assertEquals("정상", p.record.safeCall.mobility)
        assertEquals("", p.record.safeCall.bloodGroup)
        assertEquals("", p.record.safeCall.rhesus)
        assertFalse(p.missing.contains("신청유형"))
        assertFalse(r.toString().contains("SECRET_CODE"))
    }

    @Test
    fun nativeDiseaseListReadsSelectedLabelsAndRejectsAmbiguousMultipleSelect() {
        val row = "<div>" + nativeSelect("qa_condition", "QA 질환") + "</div>"
        val before =
            "document.getElementById('${prefix}gen_setDiss').innerHTML=" +
                JSONObject.quote(row) +
                ";"
        val r = extract(before = before)
        assertEquals(
            listOf("QA 질환"),
            SafeCallWebImport.prepare(r.toString(), 1234, null).record.safeCall.currentConditions,
        )
        assertFalse(r.toString().contains("SECRET_CODE"))
        val ambiguous =
            extract(
                before =
                    before +
                        "document.getElementById('${prefix}qa_condition_input_0').multiple=true;"
            )
        assertFalse(ambiguous.getJSONObject("fields").has("currentConditions"))
    }

    @Test
    fun emptyOptionalMedicalFieldsDoNotPreventImportOrInventNegativeAnswers() {
        val r =
            extract(
                before =
                    "document.getElementById('${prefix}ibx_hstcsInfoCn').value='';" +
                        "document.getElementById('${prefix}sbx_bhaveSeCode_label').textContent='선택';" +
                        "document.querySelectorAll('input[type=radio]').forEach(x=>x.checked=false);"
            )
        val p = SafeCallWebImport.prepare(r.toString(), 1234, null)
        assertEquals("QA 본인", p.record.name)
        assertEquals("", p.record.safeCall.pastHistory)
        assertEquals(OptionalAnswer.UNSPECIFIED, p.record.safeCall.canCall)
        assertEquals(OptionalAnswer.UNSPECIFIED, p.record.safeCall.elevator)
        assertEquals(listOf("name", "registeredPhone"), p.record.webSource?.fields)
        assertEquals(p.record, PersonalHealthCodec.decode(PersonalHealthCodec.encode(p.record)))
    }

    @Test
    fun wrongOriginLoginDialogHiddenFormAndDuplicateIdsFailClosed() {
        assertTrue(extract(base = "https://other.invalid/").has("error"))
        assertTrue(
            extract(
                    before =
                        "document.body.insertAdjacentHTML('beforeend', '<div role=dialog>로그인</div>');"
                )
                .has("error")
        )
        assertTrue(
            extract(before = "document.getElementById('${prefix}ibx_name').style.display='none';")
                .has("error")
        )
        assertTrue(
            extract(
                    before =
                        "document.body.appendChild(document.getElementById('${prefix}ibx_name').cloneNode());"
                )
                .has("error")
        )
    }

    @Test
    fun unrecognizedDynamicRowsAreOmittedAndDoNotDeleteOldConditions() {
        val r =
            extract(
                before =
                    "document.getElementById('${prefix}gen_setDiss').innerHTML='<div><input value=unknown></div>';document.getElementById('${prefix}ibx_takngDrfstfCn').value='가상 약';"
            )
        assertFalse(r.getJSONObject("fields").has("currentConditions"))
        val p =
            SafeCallWebImport.prepare(
                r.toString(),
                1234,
                PersonalHealthRecord(
                    safeCall = SafeCallDetails(currentConditions = listOf("기존 가상 질환"))
                ),
            )
        assertEquals(listOf("기존 가상 질환"), p.record.safeCall.currentConditions)
    }

    @Test
    fun resultCipherRoundTripsWithKeystoreAndDoesNotExposePlaintext() {
        val raw =
            extract(
                    before = "document.getElementById('${prefix}ibx_takngDrfstfCn').value='가상 복용약';"
                )
                .toString()
        val session = SafeCallImportSession { 1L }
        val nonce = session.begin()
        val cipher = MedicalCrypto.encrypt(SafeCallImportSession.wrap(raw, nonce, 1234))
        assertFalse(cipher.contains("QA 본인"))
        assertTrue(session.accept(cipher))
        val p = session.preview(null, MedicalCrypto::decrypt)
        assertEquals("QA 본인", p.record.name)
        session.clear()
        // This test does not write to the user's health store or erase shared Keystore keys.
    }

    @Test
    fun gridUsesObservedColumnIdsAndIgnoresHiddenTemplateRowsAndUnfinishedGuardianEditor() {
        val row =
            "<tr><td col_id='flnm'>QA 보호자</td><td col_id='relNm'>가족</td><td col_id='mobAll'>010-0000-0001</td><td col_id='tlpAll'></td><td col_id='eml'>qa@example.invalid</td><td col_id='habSeNm'>별거</td><td col_id='smsRcptnNm'>아니오</td></tr>"
        val html =
            fixture()
                .replace(
                    "</body>",
                    "<table><tbody id='${prefix}grd_list_body_tbody'>" +
                        row +
                        row.replace("<tr>", "<tr style='display:none' class='w2grid_hidedRow'>") +
                        "</tbody></table><input id='${prefix}ibx_flnm' value='NEVER_EXPORT_UNSAVED_GUARDIAN'></body>",
                )
        val r =
            extract(
                html = html,
                before =
                    "document.getElementById('${prefix}gen_setDiss').innerHTML='<div><div class=w2selectbox_label>QA 질환</div></div>';",
            )
        val p = SafeCallWebImport.prepare(r.toString(), 1234, null)
        assertEquals(1, p.record.safeCall.guardians.size)
        assertEquals("QA 보호자", p.record.safeCall.guardians.single().name)
        assertEquals(LivingArrangement.SEPARATE, p.record.safeCall.guardians.single().living)
        assertEquals(OptionalAnswer.NO, p.record.safeCall.guardians.single().smsPreference)
        assertEquals(listOf("QA 질환"), p.record.safeCall.currentConditions)
        assertFalse(r.toString().contains("NEVER_EXPORT"))
    }

    @Test
    fun reviewRequiresConfirmationAndStoreRoundTripKeepsOnlyCiphertext() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val temporaryName = "qa_web_import_" + java.util.UUID.randomUUID()
        val isolated =
            object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String?, mode: Int) =
                    context.getSharedPreferences(temporaryName, Context.MODE_PRIVATE)
            }
        try {
            val raw = extract().toString()
            val preview = SafeCallWebImport.prepare(raw, 1234, null)
            val store = PersonalHealthStore(isolated)
            var cancelled = false
            i.runOnMainSync {
                val view =
                    SafeCallImportReviewUi.create(
                        context,
                        preview,
                        { store.save(preview.record) },
                        {},
                        { cancelled = true },
                    )
                val save = view.findViewById<Button>(R.id.safe_import_save)
                assertFalse(save.isEnabled)
                assertNull(store.load())
                view.findViewById<Button>(R.id.safe_import_cancel).performClick()
                assertTrue(cancelled)
                assertNull(store.load())
                view.findViewById<CheckBox>(R.id.safe_import_self).performClick()
                assertTrue(save.isEnabled)
                save.performClick()
            }
            assertEquals(preview.record, store.load())
            val prefs = context.getSharedPreferences(temporaryName, Context.MODE_PRIVATE)
            assertEquals(setOf("records_cipher"), prefs.all.keys)
            val cipher = prefs.getString("records_cipher", "").orEmpty()
            assertFalse(cipher.contains("QA 본인"))
            assertFalse(cipher.contains("가상 과거력"))
        } finally {
            context.deleteSharedPreferences(temporaryName)
        }
    }

    @Test
    fun mainActivityRequiresMedicalUnlockBeforeReviewAndCancelDoesNotWriteHealthData() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val before =
            context.getSharedPreferences("lifelink_personal_health_v1", Context.MODE_PRIVATE).all
        val raw = extract().toString()
        val activity =
            i.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        try {
            i.waitForIdleSync()
            i.runOnMainSync {
                val expansionField =
                    MainActivity::class.java.getDeclaredField("expansion").apply {
                        isAccessible = true
                    }
                val expansion = expansionField.get(activity) as ExpansionUi
                expansion.home()
                val importField =
                    ExpansionUi::class.java.getDeclaredField("webImport").apply {
                        isAccessible = true
                    }
                val session = importField.get(expansion) as SafeCallImportSession
                val nonce = session.begin()
                val cipher = MedicalCrypto.encrypt(SafeCallImportSession.wrap(raw, nonce, 1234))
                assertTrue(
                    expansion.officialImportResult(
                        SafeCallImportSession.REQUEST,
                        Activity.RESULT_OK,
                        Intent().putExtra(SafeCallImportSession.EXTRA_CIPHER, cipher),
                    )
                )
                expansion.render()
                assertNotNull(activity.findViewById<android.view.View>(R.id.privacy_unlock))
                assertNull(activity.findViewById<android.view.View>(R.id.safe_import_review))
                expansion.home()
                assertEquals(
                    before,
                    context
                        .getSharedPreferences("lifelink_personal_health_v1", Context.MODE_PRIVATE)
                        .all,
                )
            }
        } finally {
            i.runOnMainSync { activity.finish() }
        }
    }
}
