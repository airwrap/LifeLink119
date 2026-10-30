package com.lifelink119.app.data

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Base64
import com.lifelink119.app.R
import com.lifelink119.app.core.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Native device credential when available; installation-local PIN otherwise. No preset PIN. */
class MedicalPrivacyManager(private val host: Activity) {
    companion object { const val NATIVE_REQUEST=11928 }
    private val prefs=host.getSharedPreferences("lifelink_medical_lock_v1",Context.MODE_PRIVATE)
    private val access=MedicalAccessWindow{SystemClock.elapsedRealtime()}
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor{r->Thread(r,"lifelink-medical-auth").apply{isDaemon=true}}
    private var dialog: AlertDialog?=null
    private var nativePending=false
    private var nativeUnavailable=false
    private var success: (() -> Unit)?=null
    private val epoch=MedicalAuthEpoch()
    private var released=false
    var onStateChange: ()->Unit = {}
    val authorized get()=access.authorized
    val prompting get()=dialog!=null || nativePending
    private val configured get()=prefs.contains("pin_salt") || prefs.contains("pin_verifier")
    private fun keyguard()=host.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    private fun nativeSecure()=runCatching{(android.os.Build.VERSION.SDK_INT<29 || host.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN)) && keyguard()?.isDeviceSecure==true && !nativeUnavailable}.getOrDefault(false)
    val description get()=when{
        configured->"앱 전용 PIN으로 의료정보 보호"
        nativeSecure()->"차량 Android의 PIN·패턴·비밀번호 인증 사용"
        else->"시스템 화면 잠금 없음 · 최초 접근 시 앱 PIN 설정"
    }
    fun lock(){access.lock()}
    fun background(){
        access.lock()
        if(!nativePending){epoch.invalidate();success=null;dialog?.dismiss();dialog=null}
    }
    fun release(){released=true;onStateChange={};epoch.invalidate();success=null;dialog?.dismiss();dialog=null;worker.shutdownNow();main.removeCallbacksAndMessages(null)}

    @Suppress("DEPRECATION")
    fun authenticate(onSuccess: ()->Unit){
        if(released)return
        if(authorized){onSuccess();return}
        if(prompting)return
        success=onSuccess;epoch.invalidate()
        host.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if(!configured && nativeSecure()){
            val intent=runCatching{keyguard()?.createConfirmDeviceCredentialIntent("LifeLink 의료정보 잠금 해제","차량에 설정된 PIN·패턴·비밀번호로 확인하세요.")}.getOrNull()
            if(intent!=null){
                nativePending=true
                try{host.startActivityForResult(intent,NATIVE_REQUEST);return}catch(_:Exception){nativePending=false;nativeUnavailable=true}
            }
        }
        pinDialog()
    }
    fun nativeResult(request: Int,result: Int): Boolean {
        if(request!=NATIVE_REQUEST)return false
        if(!nativePending || released)return true
        nativePending=false
        val action=success;success=null
        if(result==Activity.RESULT_OK){access.unlock();action?.invoke()}else access.lock()
        onStateChange();return true
    }
    private fun attempts()=PinAttemptState(prefs.getInt("failures",0),prefs.getInt("strikes",0),prefs.getLong("locked_until",0))
    private fun saveAttempts(state: PinAttemptState){check(prefs.edit().putInt("failures",state.failures).putInt("strikes",state.strikes).putLong("locked_until",state.lockedUntil).commit())}
    private fun b64(bytes: ByteArray)=Base64.encodeToString(bytes,Base64.NO_WRAP)
    private fun dp(n: Int)=(n*host.resources.displayMetrics.density).toInt()
    private fun field(label: String,id: Int)=EditText(host).apply{
        this.id=id;hint=label;contentDescription=label;textSize=22f;minimumHeight=dp(64)
        inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        transformationMethod=PasswordTransformationMethod.getInstance();filters=arrayOf(InputFilter.LengthFilter(12))
        imeOptions=EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setFilterTouchesWhenObscured(true)
    }
    private fun pinDialog(){
        val setup=!configured;val token=epoch.token
        val body=LinearLayout(host).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(12),dp(24),dp(12))}
        val status=TextView(host).apply{id=R.id.privacy_status;textSize=18f;text=if(setup)"의료정보를 보호할 6~12자리 숫자 PIN을 직접 설정하세요. 기본 PIN은 없습니다." else "저장된 앱 PIN을 입력하세요. 5회 실패하면 잠시 입력을 제한합니다.";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val pin=field(if(setup)"새 의료정보 PIN" else "의료정보 PIN",R.id.privacy_pin)
        val confirm=if(setup)field("PIN 다시 입력",R.id.privacy_confirm)else null
        body.addView(status);body.addView(pin);confirm?.let{body.addView(it)}
        val d=AlertDialog.Builder(host).setTitle(if(setup)"의료정보 PIN 설정" else "의료정보 잠금 해제").setView(body)
            .setPositiveButton(if(setup)"설정하고 열기" else "잠금 해제",null).setNegativeButton("취소",null).create()
        dialog=d;d.setCanceledOnTouchOutside(false)
        d.setOnDismissListener{if(dialog===d){dialog=null;epoch.invalidate();success=null};pin.text.clear();confirm?.text?.clear();onStateChange()}
        d.setOnShowListener{
            d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            d.getButton(AlertDialog.BUTTON_POSITIVE).apply{minimumHeight=dp(56);setFilterTouchesWhenObscured(true);setOnClickListener{
                val remaining=PinAttemptPolicy.remainingSeconds(attempts(),System.currentTimeMillis())
                if(remaining>0){status.text=host.getString(R.string.privacy_wait,remaining);return@setOnClickListener}
                val entered=pin.text.toString().toCharArray();val repeated=confirm?.text?.toString()?.toCharArray()
                if(!MedicalPrivacyPolicy.validPin(entered) || setup && (repeated==null || !entered.contentEquals(repeated))){
                    entered.fill('\u0000');repeated?.fill('\u0000');status.setText(R.string.privacy_invalid_pin);pin.text.clear();confirm?.text?.clear();return@setOnClickListener
                }
                isEnabled=false;status.setText(R.string.privacy_checking)
                worker.execute{
                    var enrollment: Pair<String,String>?=null
                    val ok=runCatching{
                        if(setup){
                            val salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
                            val verifier=MedicalPinDigest.derive(entered,salt,MedicalCrypto::pepper)
                            try{enrollment=b64(salt) to b64(verifier)}finally{verifier.fill(0);salt.fill(0)}
                            true
                        }else{
                            val salt=Base64.decode(requireNotNull(prefs.getString("pin_salt",null)),Base64.NO_WRAP)
                            val expected=Base64.decode(requireNotNull(prefs.getString("pin_verifier",null)),Base64.NO_WRAP)
                            val actual=MedicalPinDigest.derive(entered,salt,MedicalCrypto::pepper)
                            try{MessageDigest.isEqual(expected,actual)}finally{salt.fill(0);expected.fill(0);actual.fill(0)}
                        }
                    }
                    entered.fill('\u0000');repeated?.fill('\u0000')
                    val persisted=runCatching{
                        epoch.runIfCurrent(token){
                            if(ok.getOrNull()==true){
                                val edit=prefs.edit().putInt("failures",0).putInt("strikes",0).putLong("locked_until",0)
                                enrollment?.let{edit.putString("pin_salt",it.first).putString("pin_verifier",it.second)}
                                check(edit.commit())
                            }else if(ok.isSuccess)saveAttempts(PinAttemptPolicy.failed(attempts(),System.currentTimeMillis()))
                        }
                    }.getOrDefault(false)
                    enrollment=null
                    main.post{
                        if(released || !epoch.isCurrent(token) || dialog!==d)return@post
                        pin.text.clear();confirm?.text?.clear()
                        if(ok.getOrNull()==true && persisted){
                            access.unlock();val action=success;success=null;dialog=null;d.dismiss();action?.invoke();onStateChange()
                        }else{
                            isEnabled=true;d.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled=true
                            val wait=PinAttemptPolicy.remainingSeconds(attempts(),System.currentTimeMillis())
                            status.text=if(!persisted || ok.isFailure)host.getString(R.string.privacy_unavailable)else if(wait>0)host.getString(R.string.privacy_wait,wait)else host.getString(R.string.privacy_wrong_pin)
                        }
                    }
                }
            }}
        }
        d.show()
    }
    /** Caller must first erase all saved profiles and session data; reset never unlocks anything. */
    fun clearAfterMedicalDataErased(){success=null;nativePending=false;access.lock();epoch.invalidate{check(prefs.edit().clear().commit());MedicalCrypto.clear()};dialog?.dismiss();dialog=null}
}
