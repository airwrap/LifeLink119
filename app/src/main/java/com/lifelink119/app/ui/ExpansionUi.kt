package com.lifelink119.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.lifelink119.app.R
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.core.*
import com.lifelink119.app.data.*
import org.json.JSONObject
import java.util.Locale

/** Incremental Views UI over the existing Activity, dispatch gateway and SDK tools. */
class ExpansionUi(private val host: Activity, dispatch: MockDispatchGateway, saved: String?, private val legacy: (String)->Unit) {
    private enum class Page { HOME, EMERGENCY, CONSULT, SESSION, PERSON, GALLERY, PROFILES, EDIT, CARD, MORE, SUPPORT, TEXT, SIGN, VIDEO, LOCATION, SUMMARY, CENTER, POLICE, NAV, SAFE, ACCESS, POWER, DRIVER, TOOLS, FINISH, VOICE, EASY, COOPERATION, SECURITY }
    private val bg=Color.rgb(9,20,35);private val surface=Color.rgb(22,39,58);private val ink=Color.rgb(243,249,253)
    private val muted=Color.rgb(183,201,216);private val accent=Color.rgb(85,225,208);private val warning=Color.rgb(255,210,140)
    private val main=Handler(Looper.getMainLooper())
    val catalog=ExperienceCatalogParser.parse(host.assets.open("experience-catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    private val repository=FakeProfileRepository(host,catalog)
    private val cooperationCatalog=CooperationCatalogParser.parse(host.assets.open("cooperation-catalog.json").bufferedReader(Charsets.UTF_8).use{it.readText()})
    val engine=SessionEngine(catalog,repository.load(),cooperationCatalog=cooperationCatalog)
    private val privacy=MedicalPrivacyManager(host)
    private val protectedPages=setOf(Page.PROFILES,Page.EDIT,Page.PERSON,Page.CARD,Page.SUMMARY,Page.CENTER,Page.SAFE,Page.DRIVER,Page.VIDEO)
    private var deferredMedicalAction: (() -> Unit)?=null
    private val calls: EmergencyCallGateway=DemoEmergencyCallGateway(dispatch)
    private val camera: CameraGateway=SyntheticCameraGateway()
    private val bridge=ExperienceConsoleBridge()
    private var page=Page.HOME
    private var editId: String?=null
    private var lastPage: Page?=null
    private var message=""
    private var inAction=false
    private var largeText=repository.preferences().first
    private var easy=repository.preferences().second
    private var platform=DrivingRestrictions()
    private var extraRestriction=false
    private var shareDraft=mutableSetOf<ShareField>()
    private var pcConsent=false
    private var pcPostedId: String?=null
    private var pcBusy=false
    private var bridgeMessage="PC 전송 전 · 앱 내 상황실은 오프라인으로 동작합니다."
    private var frame=0
    private var changeContextOnly=false
    private var electric=true;private var v2lSupported=true
    private var capacity=64.0;private var soc=72.0;private var reserve=30.0;private var efficiency=.85;private var load=200.0
    private var inForeground=true
    private var previewWidthDp=0
    private val cooperationTick=object: Runnable {
        override fun run(){
            if(inForeground){
                if(engine.privateUnlocked && !privacy.authorized){inAction=true;engine.lockPrivate();inAction=false;requestRender()}
                val before=engine.cooperation.snapshot;inAction=true;engine.cooperation.tick();inAction=false
                val after=engine.cooperation.snapshot
                if(page==Page.COOPERATION){
                    if(before?.state!=after?.state || before?.beaconState!=after?.beaconState)requestRender()
                    else {
                        host.findViewById<TextView>(R.id.x_coop_time)?.text=host.getString(R.string.coop_time,after?.missionRemaining ?: 0,engine.cooperation.elapsedSeconds)
                        host.findViewById<TextView>(R.id.x_coop_beacon_time)?.text=host.getString(R.string.coop_beacon_time,after?.beaconRemaining ?: 0)
                    }
                    host.findViewById<TextView>(R.id.x_coop_preview)?.let {v->
                        val visible=engine.cooperation.canDisplay();v.visibility=if(visible)View.VISIBLE else View.GONE
                        v.setBackgroundColor(if(visible && android.os.SystemClock.elapsedRealtime()/500%2==0L)Color.rgb(151,36,25) else surface)
                    }
                }
            }
            main.postDelayed(this,500)
        }
    }
    var requestRender: ()->Unit = { render() }
    init {
        if(saved!=null)runCatching {
            val o=JSONObject(saved)
            engine.restore(ExperienceStateCodec.decode(o.optString("session").takeIf { it.isNotEmpty() },catalog,engine.profiles),o.optString("driver"))
            page=runCatching { Page.valueOf(o.getString("page")) }.getOrDefault(Page.HOME)
            if(page in setOf(Page.EDIT,Page.CARD))page=Page.PROFILES
            if(engine.current==null && page !in setOf(Page.HOME,Page.EMERGENCY,Page.CONSULT,Page.MORE,Page.TOOLS,Page.PROFILES,Page.GALLERY,Page.POWER,Page.ACCESS,Page.DRIVER))page=Page.HOME
            pcPostedId=o.optString("pcPostedId").takeIf{it==engine.current?.id}
        }
        engine.onChange={ if(!inAction)requestRender() }
        privacy.onStateChange={if(!inAction && !host.isDestroyed && !host.isFinishing)requestRender()}
        scheduleAnswer()
        main.post(cooperationTick)
    }
    fun save()=JSONObject().put("session",ExperienceStateCodec.encodePrivacySafe(engine.current)).put("page",page.name).put("pcPostedId",pcPostedId).toString()
    fun release(){main.removeCallbacksAndMessages(null);privacy.release();bridge.release()}
    fun background(){inForeground=false;privacy.background();engine.background()}
    fun foreground(){inForeground=true;engine.foreground();deferredMedicalAction?.let{deferredMedicalAction=null;it()}}
    fun nativeMedicalResult(request: Int,result: Int)=privacy.nativeResult(request,result)
    private fun medicalUnlocked()=engine.privateUnlocked && privacy.authorized
    private fun personLabel()=MedicalPrivacyPolicy.safeAlias(medicalUnlocked(),engine.person()?.let(engine::alias),engine.person()!=null)
    private fun lockMedical(){privacy.lock();deferredMedicalAction=null;engine.lockPrivate()}
    private fun pauseCooperationPreview(){val old=inAction;inAction=true;engine.cooperation.pausePreview();inAction=old}
    fun home(){pauseCooperationPreview();inAction=true;lockMedical();inAction=false;message="";page=Page.HOME;requestRender()}
    fun emergency(){go(Page.EMERGENCY)}
    fun restrictions(value: DrivingRestrictions){if(platform==value)return;platform=value;engine.setRestrictions(platform.withTestRestriction(extraRestriction))}
    fun effectiveRestrictions()=engine.restrictions
    fun back(): Boolean {
        if(page==Page.HOME)return false
        if(page==Page.COOPERATION)pauseCooperationPreview()
        page=when(page){Page.EMERGENCY,Page.CONSULT,Page.MORE,Page.PROFILES,Page.FINISH->Page.HOME
            Page.GALLERY,Page.DRIVER->Page.TOOLS;Page.EDIT->Page.PROFILES
            Page.ACCESS,Page.POWER,Page.TOOLS->Page.MORE
            Page.SESSION->Page.HOME
            else->if(engine.current?.active==true)Page.SESSION else Page.HOME}
        if(page==Page.HOME){home();return true}
        message="";requestRender();return true
    }
    private fun go(p: Page){
        if(p in protectedPages && !medicalUnlocked() && !engine.restrictions.complexBlocked){reveal{go(p)};return}
        if(page==Page.COOPERATION && p!=page)pauseCooperationPreview();message="";page=p;if(p==Page.CARD)shareDraft=engine.current?.shared?.keys.orEmpty().filter { it!=ShareField.VIDEO && it!=ShareField.TEXT }.toMutableSet();requestRender()
    }
    private fun mutate(action: ()->Unit){inAction=true;message="";runCatching(action).onFailure { message=it.message?.takeUnless {m->m in setOf("Failed requirement.","Check failed.") } ?: "선택 정보와 현재 단계를 확인하고 다시 시도하세요." };inAction=false
        if(engine.current?.ended==true && pcPostedId==engine.current?.id)revokePc()
        requestRender()}
    private fun scheduleAnswer(){val id=engine.current?.takeIf { it.status==LinkStatus.REQUESTING }?.id ?: return
        main.postDelayed({ if(engine.current?.id==id && engine.current?.status==LinkStatus.REQUESTING)mutate { engine.answer() } },900)}
    private fun start(target: ConnectionTarget,spec: ScenarioSpec?=null){
        privacy.lock()
        if(pcPostedId!=null)revokePc()
        pcConsent=false;bridgeMessage="PC 전송 전 · 앱 내 상황실에서 확인할 수 있습니다."
        mutate { engine.current?.takeIf { !it.ended }?.let { engine.finish(true) };engine.start(calls.request(target),target,spec?.id ?: "E04",spec?.profileHint);page=Page.SESSION }
        scheduleAnswer()
    }
    private fun dp(x: Int)=(x*host.resources.displayMetrics.density).toInt()
    private fun size(sp: Float)=sp*(if(largeText || medicalUnlocked() && engine.person()?.access?.largeText==true)1.18f else 1f)
    private fun col()=LinearLayout(host).apply { orientation=LinearLayout.VERTICAL }
    private fun LinearLayout.add(v: View,top: Int=0){addView(v,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top) })}
    private fun text(value: String,sp: Float=22f,color: Int=ink,bold: Boolean=false,id: Int=View.NO_ID)=TextView(host).apply {
        this.id=id;text=value;textSize=size(sp);setTextColor(color);setLineSpacing(dp(3).toFloat(),1f)
        if(bold){setTypeface(null,Typeface.BOLD);isAccessibilityHeading=true}
    }
    private fun shape(color: Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.rgb(49,70,91))}
    private fun button(label: String,id: Int=View.NO_ID,primary: Boolean=true,enabled: Boolean=true,action: ()->Unit)=Button(host).apply{
        this.id=id;text=label;contentDescription=label;textSize=size(22f);isAllCaps=false;minHeight=dp(64);minimumHeight=dp(64)
        setPadding(dp(18),dp(12),dp(18),dp(12));setTextColor(if(primary)bg else ink);background=shape(if(primary)accent else surface);isEnabled=enabled;alpha=if(enabled)1f else .48f
        setFilterTouchesWhenObscured(true)
        setOnClickListener{if(page in protectedPages && !medicalUnlocked() && id !in setOf(R.id.x_home,R.id.x_back,R.id.x_urgent_shortcut,R.id.privacy_unlock,R.id.privacy_lock,R.id.x_revoke))reveal{action()}else action()}
    }
    private fun panel(content: LinearLayout.()->Unit)=col().apply{background=shape(surface);setPadding(dp(22),dp(20),dp(22),dp(20));content()}
    private fun check(label: String,checked: Boolean,id: Int=View.NO_ID,enabled: Boolean=true,changed: (Boolean)->Unit)=CheckBox(host).apply{
        this.id=id;text=label;textSize=size(20f);setTextColor(ink);minHeight=dp(52);isChecked=checked;isEnabled=enabled;setOnCheckedChangeListener{_,v->changed(v)}
    }
    private fun availableWidthDp()=if(previewWidthDp>0)minOf(previewWidthDp,host.resources.configuration.screenWidthDp) else host.resources.configuration.screenWidthDp
    private fun row(left: View,right: View)=LinearLayout(host).apply{
        val wide=availableWidthDp()>=760;orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        addView(left,LinearLayout.LayoutParams(if(wide)0 else -1,-2,if(wide)1f else 0f))
        addView(right,LinearLayout.LayoutParams(if(wide)0 else -1,-2,if(wide)1f else 0f).apply{if(wide)marginStart=dp(16)else topMargin=dp(12)})
    }
    private fun shell(title: String,content: LinearLayout.()->Unit){
        if(page in protectedPages || medicalUnlocked() || privacy.prompting)host.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)else host.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val same=lastPage==page;val previous=if(same)host.findViewById<ScrollView>(R.id.x_scroll)?.scrollY ?: 0 else 0
        val root=col().apply{setBackgroundColor(bg)}
        val compact=availableWidthDp()<680
        root.add(text("시연 모드 | 실제 신고·상담 연결 아님",16f,warning,true,R.id.safety_banner).apply{gravity=Gravity.CENTER;setPadding(dp(12),dp(8),dp(12),dp(8))})
        if(previewWidthDp>0)root.add(text("화면 너비 ${availableWidthDp()}dp 미리보기 · 실제 차량 창과 별개",14f,muted).apply{gravity=Gravity.CENTER})
        root.add(LinearLayout(host).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))
            if(page!=Page.HOME && !compact)addView(button("이전",R.id.x_back,false){back()},LinearLayout.LayoutParams(dp(94),-2))
            addView(text("LifeLink",28f,ink,true),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(12)})
            if(page!=Page.HOME){addView(button("홈",R.id.x_home,false){home()},LinearLayout.LayoutParams(dp(88),-2))
                if(!compact)addView(button("긴급 연결",R.id.x_urgent_shortcut){emergency()},LinearLayout.LayoutParams(dp(150),-2).apply{marginStart=dp(10)})}
            else addView(button("더보기",R.id.x_more,false){go(Page.MORE)},LinearLayout.LayoutParams(dp(124),-2))
        })
        if(medicalUnlocked())root.add(button("의료정보 다시 잠금",R.id.privacy_lock,false){lockMedical()},4)
        if(compact && page!=Page.HOME)root.add(LinearLayout(host).apply{
            setPadding(dp(20),0,dp(20),dp(8));addView(button("이전",R.id.x_back,false){back()},LinearLayout.LayoutParams(0,-2,1f))
            addView(button("긴급 연결",R.id.x_urgent_shortcut){emergency()},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(12)})
        })
        val body=col().apply{
            setPadding(dp(24),dp(14),dp(24),dp(28))
            if(title.isNotEmpty())add(text(title,30f,ink,true),4)
            if(message.isNotBlank())add(text(message,20f,warning,false,R.id.x_message).apply{accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE},10)
            if(engine.restrictions.known && (engine.restrictions.requiresOptimization || engine.restrictions.noVideo || engine.restrictions.noSetup))
                add(text("차량 제한 적용 중 · 실제 긴급신고는 기기의 표준 긴급통화 기능을 이용하세요.",17f,warning),8)
            content()
        }
        val scroll=ScrollView(host).apply{id=R.id.x_scroll;isFillViewport=true;addView(body)}
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(previewWidthDp>0)host.setContentView(FrameLayout(host).apply{setBackgroundColor(Color.BLACK);addView(root,FrameLayout.LayoutParams(dp(availableWidthDp()),-1,Gravity.CENTER))})else host.setContentView(root)
        lastPage=page
        if(same)scroll.post{scroll.scrollTo(0,previous)}
    }
    fun render(){
        if(engine.restrictions.complexBlocked && page in setOf(Page.PERSON,Page.PROFILES,Page.EDIT,Page.CARD,Page.SUMMARY,Page.POWER,Page.DRIVER,Page.ACCESS,Page.COOPERATION)){
            shell("상세 조작 제한"){add(text("플랫폼이 상세 정보·설정을 제한하고 있습니다. 안전하게 이용할 수 있을 때 다시 열어주세요. 긴급 연결 시연은 상단에서 선택할 수 있습니다.",24f,warning),18)}
            return
        }
        if(page in protectedPages && !medicalUnlocked()){
            shell("의료정보가 잠겨 있습니다"){
                add(text("가족·의료정보는 인증한 사람만 열 수 있습니다. 긴급 연결 시연은 잠금을 풀지 않아도 계속 이용할 수 있습니다.",23f,warning,false,R.id.privacy_state),18)
                add(button("인증하고 정보 열기",R.id.privacy_unlock){reveal{}},18)
                add(button("잠금 설정·정보 삭제",R.id.privacy_settings,false){go(Page.SECURITY)},12)
            };return
        }
        when(page){
        Page.HOME->homePage();Page.EMERGENCY->entry(true);Page.CONSULT->entry(false);Page.SESSION->sessionPage();Page.PERSON->personPage()
        Page.GALLERY->gallery();Page.PROFILES->profilesPage();Page.EDIT->editProfile();Page.CARD->cardPage();Page.MORE->morePage();Page.SUPPORT->supportPage()
        Page.TEXT->textPage();Page.SIGN->signPage();Page.VIDEO->videoPage();Page.LOCATION->locationPage();Page.SUMMARY->summaryPage();Page.CENTER->centerPage()
        Page.POLICE->policePage();Page.NAV->navigationPage();Page.SAFE->safePage();Page.ACCESS->accessPage();Page.POWER->powerPage();Page.DRIVER->driverPage()
        Page.TOOLS->toolsPage();Page.FINISH->finishPage();Page.VOICE->voicePage();Page.EASY->easyPage();Page.COOPERATION->cooperationPage();Page.SECURITY->securityPage()
    }}
    private fun homePage()=shell(""){
        add(text("어떤 도움이 필요하세요?",38f,ink,true),12)
        add(text("프로필 등록 없이 바로 연결 시연을 시작할 수 있습니다.",22f,muted),12)
        add(row(button("지금 긴급해요\n119 구조·구급 / 112 긴급신고",R.id.x_emergency){go(Page.EMERGENCY)}.apply{minHeight=dp(128)},
            button("상담받고 싶어요\n119 의료상담 / 109 심리상담",R.id.x_consult,false){go(Page.CONSULT)}.apply{minHeight=dp(128)}),26)
        add(button("우리 가족 응급정보",R.id.x_family,false){go(Page.PROFILES)},24)
        add(text("가상 상담센터에서 연습하는 화면입니다. 실제 신고나 개인정보 전송은 발생하지 않습니다.",18f,muted),20)
    }
    private fun entry(urgent: Boolean)=shell(if(urgent)"어디로 연결할까요?" else "어떤 상담이 필요하세요?"){
        add(text(if(urgent)"등록·문답 없이 연결 시연을 요청합니다." else "전문 상담원에게 도움을 구하는 흐름을 연습합니다.",22f,muted),12)
        val targets=if(urgent)listOf(ConnectionTarget.EMERGENCY_119,ConnectionTarget.EMERGENCY_112) else listOf(ConnectionTarget.CONSULT_119,ConnectionTarget.COUNSEL_109)
        val ids=if(urgent)listOf(R.id.x_119,R.id.x_112) else listOf(R.id.x_medical,R.id.x_109)
        add(row(button(targets[0].label+"\n연결 시연",ids[0]){start(targets[0])},button(targets[1].label+"\n연결 시연",ids[1],false){start(targets[1])}),24)
        add(text("실제 번호로 발신하지 않습니다. 도움이 필요한 분과 공유 정보는 연결 시연 후에 선택할 수 있습니다.",19f,muted),22)
    }
    private fun status(s: EmergencySession)=when(s.status){LinkStatus.REQUESTING->"연결 요청 중 · 시연";LinkStatus.DEMO_CONNECTED->"가상 상담원 응답 · 시연";LinkStatus.SUPPORT_IN_PROGRESS->"추가 지원 진행 · 시연";LinkStatus.FAILED->"연결 시연 실패";LinkStatus.COMPLETED->"시연 종료 · 공유정보 정리됨";LinkStatus.CANCELLED->"시연 취소 · 공유정보 정리됨";else->"대기"}
    private fun sessionPage()=shell("응급지원 연결 시연"){
        val s=engine.current
        if(s==null){add(button("긴급 연결 선택",R.id.x_119){emergency()},16);return@shell}
        add(panel{
            add(text(s.target.label,27f,accent,true));add(text(s.target.purpose,20f,muted),8)
            add(text(status(s),24f,warning,true,R.id.x_session_status),14)
            add(text(s.id,16f,muted,false,R.id.x_ticket),8)
        },16)
        if(s.status==LinkStatus.REQUESTING){add(text("앱 안의 가상 상담원이 응답하는 중입니다. 전화·네트워크 연결이 아닙니다.",21f),20);return@shell}
        if(s.status==LinkStatus.FAILED){add(button("연결 시연 다시 시도",R.id.x_retry){mutate{engine.retry()};scheduleAnswer()},20);add(button("취소·정보 정리",R.id.x_end,false){mutate{engine.finish(true);page=Page.FINISH}},12);return@shell}
        if(s.ended){add(button("종료 요약 보기",R.id.x_end){go(Page.FINISH)},20);return@shell}
        val spec=catalog.scenario(s.scenarioId)
        add(text(spec.title,25f,ink,true,R.id.x_case_title),20)
        add(text(if(easy || engine.person()?.access?.easy==true)"필요한 도움을 하나씩 선택하세요." else spec.context,20f,muted),10)
        add(button("도움이 필요한 분: ${personLabel()}",R.id.x_person,false){go(Page.PERSON)},16)
        val plan=ContextualActionResolver.resolve(if(medicalUnlocked())engine.person()else null,spec,s.target,engine.restrictions)
        plan.primary.forEachIndexed { index,a -> add(button(a.label,listOf(R.id.x_primary_1,R.id.x_primary_2,R.id.x_primary_3)[index],index==0){action(a)},12) }
        add(button("추가 지원",R.id.x_support,false){go(Page.SUPPORT)},18)
        add(button("종료·공유정보 정리",R.id.x_end,false){mutate{engine.finish();page=Page.FINISH}},12)
    }
    private fun action(a: SupportAction){when(a){
        SupportAction.CARD,SupportAction.GUARDIAN,SupportAction.MOBILITY,SupportAction.PRIVACY->go(Page.CARD)
        SupportAction.VIDEO->go(Page.VIDEO);SupportAction.LOCATION,SupportAction.VEHICLE->go(Page.LOCATION)
        SupportAction.TEXT,SupportAction.PHRASES->go(Page.TEXT);SupportAction.SIGN->go(Page.SIGN);SupportAction.VOICE->go(Page.VOICE);SupportAction.EASY->go(Page.EASY)
        SupportAction.SUMMARY->go(Page.SUMMARY);SupportAction.POLICE->go(Page.POLICE);SupportAction.NAVIGATION->go(Page.NAV)
        SupportAction.SAFE_CALL->go(Page.SAFE);SupportAction.POWER->go(Page.POWER);SupportAction.MANUAL->legacy("manual");SupportAction.COOPERATION->go(Page.COOPERATION)
    }}
    private fun supportPage()=shell("추가 지원"){
        val s=engine.current ?: return@shell
        add(button("가상 상황실 보기",R.id.x_center){go(Page.CENTER)},16)
        if(engine.restrictions.galleryBlocked){add(text("플랫폼 제한 중에는 추가 기능 목록을 표시하지 않습니다.",22f,warning),16);return@shell}
        add(button("가상 상황 바꾸기",R.id.x_change_case,false){changeContextOnly=true;go(Page.GALLERY)},12)
        ContextualActionResolver.resolve(if(medicalUnlocked())engine.person()else null,catalog.scenario(s.scenarioId),s.target,engine.restrictions).additional.forEach { a->add(button(a.label,idForAction(a),false){action(a)},10) }
    }
    private fun personPage()=shell("누가 도움이 필요한가요?"){
        add(text("선택은 나중에 바꾸거나 건너뛸 수 있습니다. 변경하면 이전 개인 정보 공유는 철회합니다.",20f,muted),12)
        add(button("선택 안 함 · 미등록 승객",R.id.x_no_person){mutate{engine.selectPerson(null);page=Page.SESSION}},16)
        engine.profiles.forEach { p->add(button(engine.alias(p),ExperienceViewIds.person(p.id),false){mutate{engine.selectPerson(p.id);page=Page.SESSION}},10) }
    }
    private fun gallery()=shell("시나리오 갤러리 · 18개 가상 예시"){
        if(engine.restrictions.galleryBlocked){add(text("플랫폼이 긴 목록·설정을 제한하고 있습니다. 긴급 연결 시연은 상단에서 선택할 수 있습니다.",22f,warning),16);return@shell}
        add(text("가상 인물을 포함한 예시입니다. 선택해도 의료정보가 자동 공유되지 않습니다.",20f,muted),10)
        catalog.scenarios.chunked(2).forEach { pair ->
            fun item(c: ScenarioSpec)=button("${c.id} · ${c.title}",idForCase(c.id),false){if(changeContextOnly && engine.current?.active==true){mutate{engine.selectCase(c.id);page=Page.SESSION}}else start(c.target,c)}
            add(if(pair.size==2)row(item(pair[0]),item(pair[1]))else item(pair[0]),12)
        }
    }
    private fun idForCase(id: String)=ExperienceViewIds.scenario(id)
    private fun idForAction(action: SupportAction)=ExperienceViewIds.action(action)
    private fun morePage()=shell("더보기"){
        if(engine.current?.active==true)add(button("진행 중인 연결 시연",R.id.x_resume){go(Page.SESSION)},18)
        add(button("차량 안전 준비 · EV 전력 계산",R.id.x_power,engine.current?.active!=true){go(Page.POWER)},16)
        add(button("설정·시연 도구",R.id.x_tools,false){go(Page.TOOLS)},12)
        if(engine.current?.active!=true)add(button("접근성 설정",R.id.x_access,false){go(Page.ACCESS)},12)
    }
    private fun toolsPage()=shell("설정·시연 도구"){
        if(engine.restrictions.galleryBlocked){
            add(text("주행 제한: ${engine.restrictions.source}\n${platform.detail}",22f,warning,false,R.id.x_restrictions),16)
            add(text("플랫폼 제한 중에는 시연 도구 목록과 상세 설정을 표시하지 않습니다.",22f),16)
            if(extraRestriction && !platform.galleryBlocked)add(button("추가 제한 시연 종료",R.id.x_test_restriction){extraRestriction=false;engine.setRestrictions(platform)},16)
            return@shell
        }
        add(button("시나리오 갤러리 · S01~S14 / E01~E04",R.id.x_gallery){changeContextOnly=false;go(Page.GALLERY)},16)
        add(button("접근성 설정",R.id.x_access,false){go(Page.ACCESS)},12)
        add(button("의료정보 잠금·PIN 설정",R.id.privacy_settings,false){go(Page.SECURITY)},12)
        add(button("가상 운전자·디지털키",R.id.x_driver,false){go(Page.DRIVER)},12)
        add(button(if(previewWidthDp>0)"기본 화면 너비로 복귀" else "좁은 화면 520dp 미리보기",R.id.x_narrow_preview,false){previewWidthDp=if(previewWidthDp>0)0 else 520;requestRender()},12)
        add(button("기존 5개 흐름·로컬 상황실 도구",R.id.x_legacy,false){legacy("legacy")},12)
        add(button("기본 응급 매뉴얼",R.id.x_manual,false){legacy("manual")},12)
        add(button("주변 차량 협력·승인 표시",R.id.x_cooperation,false){go(Page.COOPERATION)},12)
        add(panel{
            add(text("연동 상태",24f,accent,true))
            add(text("Vehicle 도어·배터리 진단: REAL(지원 기기)\n전화·문자·영상·위치·수어·내비·안심콜·디지털키: MOCK\n실제 기관 연결·차량 제어: NEEDS_PARTNERSHIP",18f,muted),12)
            add(text("주행 제한: ${engine.restrictions.source}\n${platform.detail}",19f,warning,false,R.id.x_restrictions),12)
            add(check("영상·상세 입력 추가 제한 시연",extraRestriction,R.id.x_test_restriction){v->extraRestriction=v;engine.setRestrictions(platform.withTestRestriction(v))},12)
            add(check("다음 연결 실패 시연",engine.failNextConnection,R.id.x_fault_connection){engine.failNextConnection=it},8)
            add(check("가상 위치 사용 가능",engine.locationAvailable,R.id.x_fault_location){v->engine.locationAvailable=v;if(!v && engine.current?.active==true)mutate{engine.revoke(ShareField.LOCATION)}},8)
        },22)
    }
    private fun accessPage()=shell("접근성 설정"){
        add(text("선호는 표시 순서를 바꿉니다. 기능을 이용하지 못하게 하는 조건이 아닙니다.",21f,muted),12)
        add(check("큰 글씨",largeText,R.id.x_large_text){largeText=it;repository.preferences(largeText,easy);requestRender()},16)
        add(check("쉬운 짧은 안내",easy,R.id.x_easy_text){easy=it;repository.preferences(largeText,easy);requestRender()},12)
        add(text("개인별 문자·수어·음성·이동 도움 선호는 가족 응급정보에서 가상 프로필별로 선택할 수 있습니다. 스크린리더는 시스템 설정을 사용합니다.",21f,muted),18)
        add(button("우리 가족 응급정보",R.id.x_family,false){go(Page.PROFILES)},18)
    }
    private fun finishPage()=shell("시연 종료"){
        add(text("공유정보를 정리했습니다",30f,accent,true,R.id.x_finished),18)
        add(text("현재 세션의 의료·위치·영상·대화 정보와 동의를 제거했습니다. 가상 가족 프로필은 가족 응급정보에서 따로 편집·삭제할 수 있습니다.",22f),16)
        add(button("처음으로",R.id.x_finish_home){home()},24)
        add(button("다른 시나리오 연습",R.id.x_gallery,false){changeContextOnly=false;go(Page.GALLERY)},12)
        if(pcPostedId!=null){add(text(bridgeMessage,19f,warning),16);add(button("PC 게시 철회 다시 시도",R.id.x_pc_revoke,false,enabled=!pcBusy){revokePc()},12)}
    }

    private fun reveal(next: ()->Unit){
        if(engine.restrictions.complexBlocked){message="플랫폼 제한 중에는 상세 정보를 열 수 없습니다.";requestRender();return}
        privacy.authenticate{
            val complete={if(privacy.authorized && !engine.restrictions.complexBlocked)mutate{engine.unlockPrivate(true);next()}}
            if(inForeground)complete()else deferredMedicalAction=complete
        }
    }
    private fun securityPage()=shell("의료정보 잠금"){
        add(text(privacy.description,24f,accent,true,R.id.privacy_state),16)
        add(text("가족 선택·열람·편집·공유는 인증 뒤 가능합니다. 60초 후, 홈 복귀 또는 다른 앱으로 전환하면 다시 잠급니다. 의료정보 화면은 캡처·화면 공유를 제한합니다.",22f),16)
        add(button(if(medicalUnlocked())"지금 잠그기" else "인증 상태 확인",R.id.privacy_unlock){if(medicalUnlocked())lockMedical()else reveal{}},18)
        add(text("PIN을 잊은 경우 기존 의료정보를 남겨 둔 채 잠금을 우회하지 않습니다. 아래 초기화는 저장한 가족정보·상담정보·PIN을 함께 지우며 가상 기본 예시만 복원합니다.",20f,muted),20)
        add(button("저장 의료정보 삭제·잠금 초기화",R.id.privacy_reset,false){
            AlertDialog.Builder(host).setTitle("저장 정보를 지우고 초기화할까요?").setMessage("모든 저장 가족정보와 현재 상담 공유를 삭제하고 앱 잠금을 초기화합니다. 실제 차량 Android의 화면 잠금은 바꾸지 않습니다.")
                .setPositiveButton("정보 삭제·초기화"){_,_->mutate{engine.finish(true);engine.restoreProfiles();repository.eraseMedicalData();privacy.clearAfterMedicalDataErased();page=Page.HOME}}
                .setNegativeButton("취소",null).show()
        },16)
    }
    private fun profilesPage()=shell("우리 가족 응급정보"){
        if(repository.storageUnavailable)add(text("저장 정보 보호 키를 확인할 수 없습니다. 이전 내용은 표시하지 않습니다. 잠금 설정에서 정보를 삭제한 뒤 가상 예시를 복원할 수 있습니다.",22f,warning),16)
        add(text("가상 예시만 저장합니다. 실제 이름·연락처·병력은 입력하지 않습니다.",20f,muted),12)
        if(engine.profiles.isEmpty())add(text("등록된 가상 프로필이 없습니다. 미등록 승객으로 모든 긴급 연결 시연을 이용할 수 있습니다.",22f),16)
        engine.profiles.forEach { p->add(panel{
            add(text(engine.alias(p),24f,ink,true));add(text(catalog.word("ages",p.ageKey)+" · 의료정보는 가려져 있습니다",19f,muted),8)
            add(button("가상정보 확인·편집",id=when(p.id){"DEMO-P-1"->R.id.x_profile_edit_1;"DEMO-P-11"->R.id.x_profile_edit_11;else->View.NO_ID},primary=false,enabled=!engine.restrictions.complexBlocked){editId=p.id;reveal{page=Page.EDIT}},12)
        },14) }
        add(button("가상 가족 추가",R.id.x_profile_add,enabled=!engine.restrictions.complexBlocked && engine.profiles.size<12){editId=null;reveal{page=Page.EDIT}},18)
        add(button("기본 가상 예시 복원",R.id.x_profile_reset,false,enabled=!engine.restrictions.complexBlocked){
            AlertDialog.Builder(host).setTitle("가상 예시를 복원할까요?").setMessage("편집한 가상 프로필을 기본 예시로 바꿉니다.").setPositiveButton("복원"){_,_->mutate{engine.restoreProfiles();repository.save(engine.profiles)}}.setNegativeButton("취소",null).show()
        },12)
    }
    private fun choose(group: String,selected: String,id: Int=View.NO_ID): Spinner {
        val keys=catalog.words.getValue(group).keys.toList()
        return Spinner(host).apply{
            this.id=id;minimumHeight=dp(56);contentDescription=when(group){"aliases"->"가상 별명";"ages"->"연령대";"guardians"->"가상 보호자";else->group}
            adapter=ArrayAdapter(host,android.R.layout.simple_spinner_dropdown_item,keys.map { catalog.word(group,it) })
            setSelection(keys.indexOf(selected).coerceAtLeast(0));tag=keys
        }
    }
    @Suppress("UNCHECKED_CAST") private fun selection(spinner: Spinner)=(spinner.tag as List<String>)[spinner.selectedItemPosition]
    private fun editProfile()=shell("가상 가족 편집"){
        if(!engine.privateUnlocked || engine.restrictions.complexBlocked){add(text("상세 정보가 잠겼습니다. 가족 목록에서 다시 확인하세요.",22f,warning),18);return@shell}
        val existing=engine.profiles.find { it.id==editId }
        val id=existing?.id ?: "DEMO-P-"+((engine.profiles.mapNotNull { it.id.removePrefix("DEMO-P-").toIntOrNull() }.maxOrNull() ?: 0)+1)
        val p=existing ?: PersonProfile(id,"NEW_A","ADULT")
        val alias=choose("aliases",p.aliasKey,R.id.x_profile_alias);val age=choose("ages",p.ageKey,R.id.x_profile_age)
        add(text("미리 정의된 가상 정보에서 선택합니다. 긴급 연결의 선행 조건이 아닙니다.",19f,muted),12)
        add(text("가상 별명",20f),12);add(alias);add(text("연령대",20f),10);add(age)
        fun choices(group: String,selected: Set<String>,title: String): MutableSet<String> {
            val values=selected.toMutableSet();add(text(title,23f,accent,true),18)
            catalog.words.getValue(group).forEach { (key,label)->add(check(label,key in values){if(it)values.add(key)else values.remove(key)},4) }
            return values
        }
        val conditions=choices("conditions",p.conditions,"병력 참고 · 가상")
        val meds=choices("medications",p.medications,"복용약 참고 · 가상")
        val allergies=choices("allergies",p.allergies,"알레르기 · 가상")
        val access=choices("access",p.access.needs,"접근성 선호")
        val pregnancy=choose("pregnancy",p.pregnancyKey);val guardian=choose("guardians",p.guardianKey)
        add(text("임신 참고 · 가상",20f),18);add(pregnancy);add(text("보호자 · 실제 연락 불가",20f),14);add(guardian)
        var big=p.access.largeText;var simple=p.access.easy;var safe=p.safeCallRegistered
        add(check("큰 글씨 선호",big){big=it},12);add(check("쉬운 안내 선호",simple){simple=it},6)
        add(check("119안심콜 등록된 가상 예시",safe){safe=it},6)
        add(button("가상 프로필 저장",R.id.x_profile_save){mutate{
            val changed=PersonProfile(id,selection(alias),selection(age),conditions,meds,allergies,selection(pregnancy),selection(guardian),AccessPreferences(access,big,simple),safe)
            if(existing==null)engine.addProfile(changed)else engine.editProfile(changed)
            repository.save(engine.profiles);page=Page.PROFILES
        }},22)
        if(existing!=null)add(button("이 가상 프로필 삭제",R.id.x_profile_delete,false){
            AlertDialog.Builder(host).setTitle("가상 프로필 삭제").setMessage("선택 중인 개인 정보 공유도 함께 철회합니다.")
                .setPositiveButton("삭제"){_,_->reveal{mutate{engine.deleteProfile(id);repository.save(engine.profiles);page=Page.PROFILES}}}.setNegativeButton("취소",null).show()
        },12)
    }
    private val sensitive=ShareField.entries.toSet()-setOf(ShareField.LOCATION,ShareField.VEHICLE)
    private fun cardPage()=shell("선택한 정보만 공유"){
        val s=engine.current ?: return@shell
        add(text("대상: ${s.target.label} · 앱 안의 가상 상황실\n동의하지 않아도 연결 시연은 계속됩니다.",21f,muted),12)
        add(text("도움받는 분: ${personLabel()}",23f,ink,true),14)
        if(engine.person()==null)add(button("가상 가족 선택 · 건너뛰어도 됩니다",R.id.x_person,false){go(Page.PERSON)},12)
        ShareField.entries.filter { it !in setOf(ShareField.VIDEO,ShareField.TEXT) }.forEach { f ->
            val value=engine.value(f)
            val shown=if(value==null)"정보 없음" else if(f in sensitive && !engine.privateUnlocked)"가려진 가상 정보" else value
            add(panel{
                add(check("${f.label} 제공에 동의",f in shareDraft,ExperienceViewIds.consent(f),enabled=value!=null){checked->
                    if(checked)shareDraft.add(f)else {shareDraft.remove(f);if(f in engine.current?.shared.orEmpty())mutate{engine.revoke(f)}}
                    host.findViewById<Button>(R.id.x_share_card)?.isEnabled=shareDraft.isNotEmpty()
                })
                add(text(shown,18f,muted),6)
                if(f in s.shared)add(text("가상 상황실에 공유 중",17f,accent),6)
            },10)
        }
        add(button("선택 정보 모의 공유",R.id.x_share_card,enabled=shareDraft.isNotEmpty()){mutate{engine.share(shareDraft);message="선택한 항목을 앱 내 가상 상황실에 반영했습니다."}},18)
        add(button(if(medicalUnlocked())"의료정보 다시 잠그기" else "의료정보 인증하고 확인",R.id.x_unlock,false){if(medicalUnlocked())lockMedical()else reveal{}},12)
        add(button("전체 공유 철회",R.id.x_revoke,false,enabled=s.shared.isNotEmpty()){mutate{engine.revoke();shareDraft.clear();message="공유정보와 영상·경로를 철회했습니다."}},12)
        add(button("가상 상황실에서 확인",R.id.x_center,false){go(Page.CENTER)},12)
    }
    private fun textPage()=shell("문자·의사표현 시연"){
        add(text("실제 문자 발송 없이, 선택한 문구가 앱 내 상황실에 표시됩니다.",20f,muted),12)
        if(engine.restrictions.messagesBlocked){add(text("플랫폼 제한으로 문자 조작을 잠갔습니다. 상단의 긴급 연결 경로를 확인하세요.",22f,warning),16);return@shell}
        catalog.words.getValue("phrases").entries.forEachIndexed { index,e->add(button(e.value,if(index==0)R.id.x_send_phrase else View.NO_ID,false){mutate{engine.sendPhrase(e.key)}},12) }
        add(text(engine.current?.messages.orEmpty().joinToString("\n\n").ifBlank{"선택한 문구가 없습니다."},22f,ink,false,R.id.x_messages),20)
        add(button("가상 상담원 답장 보기",R.id.x_reply){mutate{engine.reply()}},16)
    }
    private fun signPage()=shell("수어통역 연결 모형"){
        val connected=engine.current?.sign==SignStatus.CONNECTED
        add(text("실제 영상·수어 번역이 아닌 3자 연결 상태 시연입니다.",20f,muted),12)
        add(row(panel{add(text("나 · 가상",25f,accent,true));add(text("영상 사용 동의는 별도입니다.",18f,muted),10)},
            panel{add(text("119 가상 상담원",25f,accent,true));add(text(if(connected)"통역 연결 시연 확인" else "대기",20f),10)}),18)
        add(panel{add(text("수어통역사 · 가상",25f,accent,true));add(text(if(connected)"가상 통역사 연결 시연" else "아직 연결 요청 전",23f,ink,false,R.id.x_sign_status),14)},16)
        add(button(if(connected)"통역 연결 시연 종료" else "가상 통역사 연결에 동의",R.id.x_sign_connect,enabled=connected || !engine.restrictions.videoBlocked){mutate{engine.sign(!connected)}},18)
        if(engine.restrictions.videoBlocked)add(text("플랫폼 제한 중에는 새 영상·통역 연결 조작이 제한됩니다.",19f,warning),12)
    }
    private fun videoPage()=shell("영상 공유 · 합성 예시"){
        val s=engine.current ?: return@shell
        add(text(camera.capability(),20f,muted),12)
        add(text("${s.videoSource.label} · ${when(s.video){VideoStatus.OFF->"요청 전";VideoStatus.REQUESTED->"공유 요청 · 동의 대기";VideoStatus.APPROVED->"동의 완료 · 시작 전";VideoStatus.SHARING->"영상 공유 중 — 시연";VideoStatus.STOPPED->"공유 종료"}}",23f,accent,true,R.id.x_video_status),16)
        if(engine.restrictions.videoBlocked)add(text("플랫폼 제한으로 영상 미리보기와 새 공유 조작을 잠갔습니다.",22f,warning),16)
        if(engine.videoPreview && !engine.restrictions.videoBlocked){
            add(SyntheticFrameView(host,s.videoSource,frame).apply{id=R.id.x_video_preview;minimumHeight=dp(180)},16)
            add(button("다음 합성 장면",R.id.x_next_frame,false){frame=(frame+1)%4;requestRender()},12)
        }
        when(s.video){
            VideoStatus.OFF,VideoStatus.STOPPED->VideoSource.entries.forEachIndexed { i,source->add(button(source.label+" 공유 요청",if(i==0)R.id.x_video_front else if(i==1)R.id.x_video_rear else R.id.x_video_phone,i==2,!engine.restrictions.videoBlocked){mutate{engine.requestVideo(source)}},12)}
            VideoStatus.REQUESTED->{add(text(camera.sharePreview(),21f),16);add(button("합성 영상 사용 범위에 동의",R.id.x_video_approve,enabled=!engine.restrictions.videoBlocked){mutate{engine.approveVideo()}},16)}
            VideoStatus.APPROVED->add(button("영상 공유 시작 · 시연",R.id.x_video_start,enabled=!engine.restrictions.videoBlocked){mutate{engine.startVideo()}},18)
            VideoStatus.SHARING->if(!engine.videoPreview && !engine.restrictions.videoBlocked)add(button("합성 미리보기 다시 열기",R.id.x_video_preview_open){mutate{engine.openVideoPreview()}},16)
        }
        if(s.video !in setOf(VideoStatus.OFF,VideoStatus.STOPPED))add(button("영상 공유 중단·동의 취소",R.id.x_video_stop,false){mutate{engine.stopVideo()}},16)
    }
    private fun locationPage()=shell("위치·차량 정보"){
        add(text("MOCK · 실제 GPS나 차량 식별정보를 보내지 않습니다. 실제 SDK 원본 조회는 별도 진단에서 확인합니다.",20f,muted),12)
        add(panel{add(text(engine.value(ShareField.LOCATION) ?: "가상 위치 없음",23f,ink,true,R.id.x_location_status));add(text(engine.value(ShareField.VEHICLE).orEmpty(),21f,muted),14)},16)
        add(button("가상 위치 공유",R.id.x_share_location,enabled=engine.locationAvailable){mutate{engine.share(setOf(ShareField.LOCATION))}},18)
        add(button("가상 차량 정보 공유",R.id.x_share_vehicle,false){mutate{engine.share(setOf(ShareField.VEHICLE))}},12)
        add(button("실제 Vehicle SDK 진단",R.id.x_sdk,false){legacy("vehicle")},12)
        if(!engine.locationAvailable)add(button("가상 위치 다시 확인",R.id.x_location_retry,false){engine.locationAvailable=true;requestRender()},12)
    }
    private fun summaryPage()=shell("상담 전달용 정보 요약"){
        val s=engine.current ?: return@shell
        add(text("규칙 기반 문장입니다. 사용자가 검토·수정한 뒤 동의해야 공유합니다. 진단·위험도 판정은 하지 않습니다.",20f,muted),12)
        add(panel{add(text(engine.summary(),23f,ink,false,R.id.x_summary))},16)
        if(!engine.restrictions.complexBlocked){
            val observation=choose("observations",s.observation,R.id.x_observation)
            add(text("상황 설명 수정",22f,accent,true),18);add(observation)
            val times=listOf("NOW","AGO5","AGO10","AGO30")
            val time=Spinner(host).apply{id=R.id.x_discovered;minimumHeight=dp(56);contentDescription="가상 발견 시각";adapter=ArrayAdapter(host,android.R.layout.simple_spinner_dropdown_item,listOf("방금 발견(시연)","5분 전(시연)","10분 전(시연)","30분 전(시연)"));setSelection(times.indexOf(s.discovered).coerceAtLeast(0))}
            add(text("발견 시각",20f),12);add(time)
            val glucose=EditText(host).apply{id=R.id.x_glucose;hint="선택: 가상 혈당 20~600 mg/dL";textSize=size(22f);setTextColor(ink);setHintTextColor(muted);inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText(s.demoGlucose?.let{String.format(Locale.ROOT,"%d",it)} ?: "");minHeight=dp(60);setSingleLine()}
            add(glucose,12)
            add(button("요약 수정 반영",R.id.x_summary_update,false){mutate{val v=glucose.text.toString();engine.observe(selection(observation),times[time.selectedItemPosition],if(v.isBlank())null else v.toInt())}},12)
        }else add(text("플랫폼 제한 중에는 상세 수정이 잠깁니다.",20f,warning),12)
        add(button("검토한 요약 공유에 동의",R.id.x_share_summary){mutate{engine.share(setOf(ShareField.SUMMARY));message="검토한 요약만 가상 상황실에 공유했습니다."}},18)
    }
    private fun voicePage()=shell("음성 도움말 시연"){
        add(text("현재는 소리 재생 없이 안내 문장을 강조합니다. 실제 STT·TTS 서비스나 마이크를 사용하지 않습니다.",21f,muted),14)
        add(text("화면 위의 긴급 연결에서 119 또는 112 연결 시연을 선택할 수 있습니다.",27f,accent,true),20)
        add(button("음성 안내 문장 강조 · 시연",R.id.x_voice){mutate{engine.voice();message=engine.current?.voice.orEmpty()}},18)
    }
    private fun easyPage()=shell("하나씩, 쉽게 안내할게요"){
        listOf("1. 도움이 필요한 분 곁에서 안전을 확인하세요.","2. 가상 상담원에게 필요한 도움을 선택해 알려주세요.","3. 원한다면 보호자·위치 정보를 따로 선택하세요.").forEach{add(text(it,27f),18)}
        add(button("의사표현 카드",R.id.x_send_phrase){go(Page.TEXT)},20)
        add(button("보호자 정보 선택",primary=false){go(Page.CARD)},12)
    }
    private fun centerPage()=shell("가상 상황실 · 앱 안"){
        val s=engine.current ?: return@shell
        if(engine.restrictions.galleryBlocked){
            add(text("${s.target.label} · ${status(s)}\n동의 항목 ${s.shared.size}개 · 상세 목록은 플랫폼 제한 중 숨깁니다.",24f,accent,true,R.id.x_center_status),16)
            add(button("연결 시연으로 돌아가기",R.id.x_return_session,false){go(Page.SESSION)},16)
            return@shell
        }
        add(panel{
            add(text("${s.target.label} · ${status(s)}",25f,accent,true,R.id.x_center_status))
            add(text("${catalog.scenario(s.scenarioId).title}\n${personLabel()}",22f),12)
            add(text("공유 항목 ${s.shared.size}개",23f,warning,true,R.id.x_shared_count),12)
            if(s.shared.isEmpty())add(text("동의한 정보가 없습니다. 연결 시연은 계속됩니다.",20f,muted),10)
            s.shared.forEach { (field,value)->add(text(field.label+": "+if(field in sensitive && !engine.privateUnlocked)"가려진 가상 정보" else value,20f),12) }
            add(text("영상 ${s.video} · 통역 ${s.sign}\n112 협조 ${s.police}\n만남 장소 ${s.navigation}\n안심콜 ${s.safeCall}",18f,muted),16)
        },16)
        if(s.active){
            if(engine.cooperation.snapshot!=null)add(button("협력 차량·표시 승인 역할 보기",R.id.x_coop_center,false){go(Page.COOPERATION)},12)
            if(s.target in setOf(ConnectionTarget.EMERGENCY_119,ConnectionTarget.CONSULT_119))add(button("가상 상담원: 만남 장소 안내",R.id.x_operator_guidance){mutate{engine.giveGuidance();message="가상 전문가 안내를 기록했습니다. 목적지는 사용자가 확인해야 합니다."}},16)
            add(button("공유·동의 관리",R.id.x_card_manage,false){go(Page.CARD)},12)
            add(button("연결 시연으로 돌아가기",R.id.x_return_session,false){go(Page.SESSION)},12)
        }
        add(panel{
            add(text("PC 상황실 · 선택 기능",23f,accent,true));add(text("현재 시연 상태와 공유 항목 이름만 localhost로 보냅니다. 개별 의료 내용·연락처는 보내지 않습니다. 수동 전송한 시점의 상태입니다.",18f,muted),10)
            add(check("이 PC에 가상 상태 게시에 동의",pcConsent,R.id.x_pc_consent,BuildConfig.DEBUG && !pcBusy){v->pcConsent=v;requestRender()},10)
            add(button("현재 시연 상태 PC로 보내기",R.id.x_pc_publish,enabled=pcConsent && BuildConfig.DEBUG && !pcBusy){publishPc()},10)
            if(pcPostedId!=null)add(button("PC 게시 철회",R.id.x_pc_revoke,false,enabled=!pcBusy){revokePc()},10)
            add(text(bridgeMessage,18f,warning,false,R.id.x_pc_status),12)
            add(text("PC: http://127.0.0.1:8781/experience-console\n네트워크 없이도 위 앱 내 상황실에서 모든 새 기능을 시연할 수 있습니다.",17f,muted),10)
        },22)
        add(text("시연 동작 기록\n"+s.events.joinToString(" → "),17f,muted),20)
    }
    private fun publishPc(){val s=engine.current ?: return;if(!pcConsent || pcBusy)return
        pcBusy=true;bridgeMessage="현재 가상 상태 전송 중…";requestRender()
        bridge.publish(s){result->pcBusy=false
            if(result==null){pcPostedId=s.id;bridgeMessage="${s.id} · revision ${s.revision} 시점 상태 게시됨"
                if(engine.current?.id!=s.id || engine.current?.ended==true)revokePc(s.id)
            }else bridgeMessage=result
            requestRender()
        }
    }
    private fun revokePc(id: String?=pcPostedId){if(id==null || pcBusy)return;pcBusy=true;pcPostedId=null;pcConsent=false;bridgeMessage="PC 게시 철회 요청 중…"
        bridge.revoke(id){error->pcBusy=false;if(error!=null)pcPostedId=id;bridgeMessage=error ?: "PC 게시 철회됨 · 앱 내 시연은 계속 이용할 수 있습니다.";requestRender()}
    }
    private fun policePage()=shell("112 공동대응 · 모의 요청"){
        val s=engine.current ?: return@shell
        add(text("경찰 출동·호송·우선통행을 보장하지 않습니다. 별도 기관 역할이 검토·승인 또는 거절하는 가상 절차입니다.",21f,muted),12)
        add(text(when(s.police){PoliceStatus.NONE->"요청 전";PoliceStatus.REQUESTED->"경찰 협조 요청됨 — 시연";PoliceStatus.REVIEWING->"가상 112 역할 검토 중";PoliceStatus.APPROVED->"가상 112 역할 승인 · 실제 권한 없음";PoliceStatus.REJECTED->"가상 112 역할 거절"},25f,accent,true,R.id.x_police_status),18)
        when(s.police){
            PoliceStatus.NONE,PoliceStatus.REJECTED->add(button("경찰 협조 모의 요청",R.id.x_police_request){mutate{engine.requestPolice()}},18)
            PoliceStatus.REQUESTED->add(button("가상 112 역할: 검토 시작",R.id.x_police_review){mutate{engine.reviewPolice()}},18)
            PoliceStatus.REVIEWING->{add(button("가상 112 역할: 승인",R.id.x_police_approve){mutate{engine.decidePolice(true)}},18);add(button("가상 112 역할: 거절",R.id.x_police_reject,false){mutate{engine.decidePolice(false)}},12)}
            PoliceStatus.APPROVED->add(text("이 상태는 공모전 가상 절차입니다. 실제 교통법규 특례나 에스코트 권리가 발생하지 않습니다.",22f,warning),16)
        }
    }
    private fun navigationPage()=shell("전문가 안내 후 만남 장소 확인"){
        val s=engine.current ?: return@shell
        add(text("NaviHelper 실제 호출 없이 가상 목적지 설정을 시연합니다. 병원의 수용 가능 여부나 자가 이송의 안전성을 판단하지 않습니다.",20f,muted),12)
        val stage=when(s.navigation){NavigationStatus.NONE->"제안 전";NavigationStatus.PROPOSED->"가상 경로 제안";NavigationStatus.CONFIRMED->"사용자 확인 완료";NavigationStatus.SET->"시연 목적지 설정 완료"}
        add(text("$stage · ${when(s.destination){"MEET_B"->"가상 구급대 만남 지점 B";"HOSPITAL_A"->"가상 병원 A · 수용 여부 미확인";else->"아직 목적지 없음"}}",24f,accent,true,R.id.x_nav_status),16)
        if(!s.guidance){add(text("먼저 가상 상담원의 안내가 필요합니다.",22f),14);add(button("가상 상황실에서 안내 받기",R.id.x_center){go(Page.CENTER)},16);return@shell}
        if(ShareField.LOCATION !in s.shared || !engine.locationAvailable){add(button("가상 위치 선택·공유",R.id.x_share_location){go(Page.LOCATION)},16);return@shell}
        when(s.navigation){
            NavigationStatus.NONE->{add(button("가상 만남 지점 B 제안",R.id.x_nav_propose){mutate{engine.proposeNavigation("MEET_B")}},16);add(button("가상 병원 A 제안 · 전문가 안내 가정",primary=false){mutate{engine.proposeNavigation("HOSPITAL_A")}},12)}
            NavigationStatus.PROPOSED->add(button("제안된 가상 목적지 확인",R.id.x_nav_confirm){mutate{engine.confirmNavigation()}},18)
            NavigationStatus.CONFIRMED->add(button("시연 목적지 설정",R.id.x_nav_set){mutate{engine.setNavigation()}},18)
            NavigationStatus.SET->add(text("시연 목적지 설정 완료 · 실제 내비 앱·차량은 변경하지 않았습니다.",23f,warning),18)
        }
    }
    private fun safePage()=shell("119안심콜 연계 모형"){
        add(text("공식 조회 API나 실제 상황실과 연결하지 않습니다. 등록된 전화번호로 신고하는 실제 제도의 확인은 별도 공식 안내를 따릅니다.",21f,muted),14)
        add(text("가상 등록 여부: ${if(engine.person()?.safeCallRegistered==true)"등록된 예시" else "미등록 예시"}\n가상 연락 표식: ${engine.person()?.let { catalog.word("guardians",it.guardianKey) } ?: "프로필 미선택"}",22f),18)
        val result=when(engine.current?.safeCall){"MOCK_REGISTERED"->"등록된 가상 예시를 확인했습니다";"MOCK_NOT_REGISTERED"->"등록된 가상 예시가 없습니다 · 연결 시연은 계속됩니다";else->"조회 전"}
        add(text("조회 시연 결과: $result",24f,accent,true,R.id.x_safe_status),16)
        add(button("가상 정보 연계에 동의하고 조회",R.id.x_safe_lookup){mutate{engine.safeCall(true)}},18)
        add(button("응급정보 공유는 별도로 선택",primary=false){go(Page.CARD)},12)
    }
    private fun driverPage()=shell("가상 운전자 프로필·디지털키"){
        add(text("OEM 디지털키·인식 API는 사용하지 않습니다. 운전자와 도움받는 동승자는 별도로 선택합니다.",21f,muted),12)
        add(text("선택된 가상 운전자: ${engine.profiles.find { it.id==engine.driverProfileId }?.let(engine::alias) ?: "선택 없음"}",24f,accent,true,R.id.x_driver_status),18)
        if(engine.restrictions.complexBlocked){add(text("플랫폼 제한 중에는 설정을 변경할 수 없습니다.",21f,warning),16);return@shell}
        engine.profiles.forEach { p->add(button(engine.alias(p)+" 운전자 선택 시연",id=when(p.id){"DEMO-P-1"->R.id.x_driver_1;"DEMO-P-2"->R.id.x_driver_2;else->View.NO_ID},primary=false){mutate{engine.selectDriver(p.id)}},10) }
        add(button("운전자 선택 해제",R.id.x_driver_clear,false){mutate{engine.selectDriver(null)}},16)
    }
    private fun cooperationPage()=shell("주변 차량 협력 · 오프라인 시연"){
        add(text("가상 위치와 가상 차량만 사용합니다. 실제 출동·운송·기관 승인·등화 제어는 없습니다.",20f,muted),12)
        val session=engine.current
        if(session?.active!=true){
            add(text("먼저 연결 시연을 시작하고 가상 위치 공유를 선택하세요.",23f),16)
            add(button("119 연결 시연 시작",R.id.x_coop_intro){start(ConnectionTarget.EMERGENCY_119,catalog.scenario("S14"))},18);return@shell
        }
        if(session.target==ConnectionTarget.COUNSEL_109){add(text("109 상담은 프라이버시를 우선합니다. 별도 긴급 도움이 필요하면 상단 긴급 연결을 선택하세요.",23f),16);return@shell}
        if(ShareField.LOCATION !in session.shared || !engine.locationAvailable){
            add(text("협력 요청에는 가상 위치 공유와 별도 참여 동의가 필요합니다. 의료정보를 자동 제공하지 않습니다.",23f),16)
            add(button("가상 위치 공유 선택",R.id.x_share_location){go(Page.LOCATION)},18);return@shell
        }
        val coop=engine.cooperation;val s=coop.snapshot
        if(s==null){
            var consent=false
            add(check("가상 위치로 주변 차량 도움 요청 시연에 동의",false,R.id.x_coop_consent){v->consent=v
                listOf(R.id.x_coop_case_early,R.id.x_coop_case_ems,R.id.x_coop_case_none).forEach{host.findViewById<Button>(it)?.isEnabled=v}},16)
            coop.cases.forEach { c->add(button(c.title,when(c.id){"EARLY_HELP"->R.id.x_coop_case_early;"EMS_FIRST"->R.id.x_coop_case_ems;else->R.id.x_coop_case_none},enabled=consent){mutate{coop.request(c.id,consent)}},12) }
            add(text("도착·조율 시간은 고정 실험 가정입니다. 실제 시연 경과와 구분하며 생존 효과를 측정하지 않습니다.",19f,muted),16);return@shell
        }
        val state=when(s.state){"REQUESTED"->"도움 요청됨";"REVIEWED"->"가상 119 역할 상황 확인";"INVITED"->"가상 차량 참여 초대";"ACCEPTED"->"참여 차량이 자발적으로 수락";"AUTHORIZED"->"가상 응급지원 임무 부여";"ON_SCENE"->"가상 현장 지원 중";"HANDED_OVER"->"구급대 인계 시연 완료";"EXPIRED"->"임무 만료";"CANCELLED"->"요청 취소";else->"협력 종료·철회"}
        add(text(state,26f,accent,true,R.id.x_coop_status),18)
        add(text("${s.title}\n${s.vehicleId ?: "선택 차량 없음"}\n${s.missionId ?: "임무 부여 전"}",20f,muted),10)
        add(text("가상 임무 남은 시간 ${s.missionRemaining}초 · 실제 시연 경과 ${coop.elapsedSeconds}초",20f,warning,false,R.id.x_coop_time),12)
        if(s.supportSeconds!=null){val gain=s.baselineSeconds-s.supportSeconds
            add(text("고정 가정: 구급대 ${s.baselineSeconds}초 / 조율+협력차 ${s.supportSeconds}초\n"+if(gain>0)"가정상 선행 가능 $gain 초 · 생존 효과 측정 아님" else "구급대가 더 빠르거나 동일한 대조 조건 · 기존 대응 유지",21f),14)}
        when(s.state){
            "REQUESTED"->add(button("가상 119 역할: 상황 확인",R.id.x_coop_review){mutate{coop.review()}},18)
            "REVIEWED"->{
                add(text("참여 동의·교육·가용 조건을 확인합니다. 가까워도 조건 미충족 차량은 초대하지 않습니다.",20f,muted),16)
                coop.vehicles.forEach{v->
                    val eligible=coop.eligible(v.id)
                    add(text(v.name+" · "+if(eligible)"참여 가능 · 이동 가정 ${v.travelSeconds}초" else "참여 불가 · 동의/교육/가용 조건 미충족",20f),12)
                    if(eligible)add(button("${v.name} 초대",if(v.id=="DEMO-HELPER-A")R.id.x_coop_invite_a else R.id.x_coop_invite_d,false){mutate{coop.invite(v.id)}},8)
                }
                if(coop.vehicles.none{coop.eligible(it.id)})add(text("참여 가능한 가상 차량 없음 · 119 연결 시연과 기존 대응을 유지합니다.",23f,warning),18)
            }
            "INVITED"->{add(button("가상 협력 차량 역할: 자발적 수락",R.id.x_coop_accept){mutate{coop.respond(true)}},18);add(button("참여 거절",R.id.x_coop_decline,false){mutate{coop.respond(false)}},12)}
            "ACCEPTED"->add(button("가상 119 역할: 300초 임무 부여",R.id.x_coop_authorize){mutate{coop.authorize()}},18)
            "AUTHORIZED","ON_SCENE"->{
                val beacon=when(s.beaconState){"OFF"->"표시 승인 전";"REQUESTED"->"표시 요청·상황 확인 대기";"REVIEWING"->"표시 요청 검토 중";"APPROVED"->"가상 한시 승인 · 시작 전";"ACTIVE"->"화면 표시 모형 동작 중";"REJECTED"->"표시 요청 거절";"EXPIRED"->"표시 승인 만료";else->"표시 승인 철회"}
                add(text("${s.beaconAuthority ?: "기관 미선택"} 역할 · $beacon",24f,accent,true,R.id.x_coop_beacon_status),20)
                add(text("표시 승인 남은 시간 ${s.beaconRemaining}초",20f,warning,false,R.id.x_coop_beacon_time),10)
                when(s.beaconState){
                    "OFF","REJECTED","EXPIRED","REVOKED"->{
                        add(button("119 역할에 별도 표시 승인 요청",R.id.x_coop_request_119){mutate{coop.requestBeacon("119")}},14)
                        add(button("112 역할에 별도 표시 승인 요청",R.id.x_coop_request_112,false){mutate{coop.requestBeacon("112")}},12)
                    }
                    "REQUESTED"->add(button("가상 ${s.beaconAuthority} 역할: 비상상황 확인",R.id.x_coop_beacon_review){mutate{coop.reviewBeacon(s.beaconAuthority!!)}},16)
                    "REVIEWING"->{add(button("가상 ${s.beaconAuthority} 역할: 최대 120초 승인",R.id.x_coop_beacon_approve){mutate{coop.decideBeacon(s.beaconAuthority!!,true)}},16);add(button("가상 표시 요청 거절",R.id.x_coop_beacon_reject,false){mutate{coop.decideBeacon(s.beaconAuthority!!,false)}},12)}
                    "APPROVED"->add(button("승인된 화면 표시 모형 시작",R.id.x_coop_beacon_start,enabled=!engine.restrictions.videoBlocked && inForeground){mutate{coop.activateBeacon()}},16)
                }
                if(coop.canDisplay())add(text("응급지원 차량 · DEMO\n화면 표시 모형 · 실제 차량 등화 아님",28f,ink,true,R.id.x_coop_preview).apply{gravity=Gravity.CENTER;minHeight=dp(110);setPadding(dp(16),dp(16),dp(16),dp(16));background=shape(Color.rgb(151,36,25))},16)
                if(s.beaconState in setOf("REQUESTED","REVIEWING","APPROVED","ACTIVE"))add(button("표시 승인 철회·모형 중단",R.id.x_coop_beacon_revoke,false){mutate{coop.revokeBeacon()}},12)
                add(text("해당 가상 차량·임무에 한정한 역할 시연입니다. 실제 비상차량 자격·통행 권한은 발생하지 않습니다.",18f,muted),16)
                if(s.state=="AUTHORIZED")add(button("가상 차량: 현장 도착",R.id.x_coop_arrive,false){mutate{coop.arrive()}},16)
                else add(button("가상 119 역할: 구급대 인계 완료",R.id.x_coop_handover){mutate{coop.finish("HANDED_OVER")}},16)
            }
        }
        if(s.state in CooperationPolicy.terminal){
            add(text("임무와 화면 표시를 종료했습니다. 기존 119 연결 시연은 별도로 계속됩니다.",22f),18)
            add(button("다른 협력 실험",R.id.x_coop_restart){mutate{coop.attach(session.id)}},16)
        }else add(button("협력 요청 취소·전체 표시 중단",R.id.x_coop_cancel,false){mutate{coop.finish("CANCELLED")}},18)
    }
    private fun powerPage()=shell("차량 안전 준비 · EV 전력 계산"){
        val editable=!engine.restrictions.complexBlocked
        add(text("가상 조건으로 계산합니다. 차종별 V2L 지원·출력 한도를 확인해야 하며 의료·생명유지장치의 안전한 전원 공급을 보장하지 않습니다.",20f,muted),12)
        add(check("가상 전기차",electric,R.id.x_ev_electric,editable){electric=it;requestRender()},14)
        add(check("V2L 지원 가정",v2lSupported,R.id.x_ev_support,editable && electric){v2lSupported=it;requestRender()},6)
        fun slider(label: String,value: Double,max: Int,scale: Double,id: Int,changed: (Double)->Unit){
            val caption=text("$label: ${"%.1f".format(Locale.ROOT,value)}",21f)
            val bar=SeekBar(host).apply{this.id=id;this.max=max;progress=(value/scale).toInt().coerceIn(0,max);isEnabled=editable && electric && v2lSupported;minimumHeight=dp(48);contentDescription=label}
            bar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(b: SeekBar?,n: Int,user: Boolean){if(user){changed(n*scale);caption.text=host.getString(R.string.value_caption,label,"%.1f".format(Locale.ROOT,n*scale));updatePowerResult()}}
                override fun onStartTrackingTouch(b: SeekBar?){};override fun onStopTrackingTouch(b: SeekBar?){}
            });add(caption,12);add(bar)
        }
        slider("배터리 용량 가정 (kWh)",capacity,160,1.0,R.id.x_ev_capacity){capacity=it}
        slider("가상 SOC (%)",soc,100,1.0,R.id.x_ev_soc){soc=it}
        slider("보존 SOC (%)",reserve,100,1.0,R.id.x_ev_reserve){reserve=it}
        slider("변환 효율 (%)",efficiency*100,100,1.0,R.id.x_ev_efficiency){efficiency=it/100.0}
        slider("기기 소비전력 가정 (W)",load,360,10.0,R.id.x_ev_load){load=it}
        add(text(powerResult(),24f,accent,true,R.id.x_power_result),18)
        add(text("사용가능량 = 용량 × max(0, SOC−보존SOC)/100 × 효율\n예상 시간 = 사용가능량 / (부하W/1000)\n가정 출력 한도 3,600W · 실제 차량 제어 없음",18f,muted),14)
        if(!editable)add(text("플랫폼 제한 중에는 계산 입력을 변경할 수 없습니다.",20f,warning),12)
        add(button("실제 Vehicle SDK 진단",R.id.x_sdk,false){legacy("vehicle")},20)
    }
    private fun powerResult(): String {
        val p=V2lCalculator.estimate(electric,v2lSupported,capacity,soc,reserve,efficiency,load)
        return (p.availableKwh?.let { "사용가능량 가정 %.2f kWh".format(Locale.ROOT,it) } ?: "계산할 수 없는 조건")+
            (p.hours?.let { "\n예상 사용 시간 %.2f시간".format(Locale.ROOT,it) } ?: "")+(p.issue?.let { "\n$it" } ?: "\n가정값이며 실제 사용 보장이 아닙니다.")
    }
    private fun updatePowerResult(){host.findViewById<TextView>(R.id.x_power_result)?.text=powerResult()}
    private class SyntheticFrameView(context: android.content.Context,private val source: VideoSource,private val frame: Int): View(context){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init{contentDescription="${source.label} · 합성 도형 장면 ${frame+1} · 실제 카메라 아님";isFocusable=true}
        override fun onMeasure(widthSpec: Int,heightSpec: Int){
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize((180*resources.displayMetrics.density).toInt(),heightSpec))
        }
        override fun onDraw(canvas: Canvas){super.onDraw(canvas);canvas.drawColor(Color.rgb(16,32,51));paint.color=Color.rgb(85,225,208)
            val x=width*(.2f+.16f*frame);canvas.drawCircle(x,height*.5f,height*.17f,paint);paint.color=Color.rgb(255,210,140);canvas.drawRect(width*.68f,height*.28f,width*.88f,height*.70f,paint)
            paint.color=Color.WHITE;paint.textSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,20f,resources.displayMetrics);canvas.drawText("${source.label} · 합성 장면 ${frame+1}",24f,height-24f,paint)
        }
    }
}
