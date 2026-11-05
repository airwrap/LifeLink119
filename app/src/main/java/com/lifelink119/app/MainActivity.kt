package com.lifelink119.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import com.lifelink119.app.core.DemoCases
import com.lifelink119.app.core.DemoDriveState
import com.lifelink119.app.core.DemoSignalPolicy
import com.lifelink119.app.core.DemoSignalSnapshot
import com.lifelink119.app.core.CooperationSnapshot
import com.lifelink119.app.core.CooperationPolicy
import com.lifelink119.app.data.CooperationGateway
import com.lifelink119.app.core.DemoShareSelection
import com.lifelink119.app.core.EmergencyScenario
import com.lifelink119.app.core.FlowPolicy
import com.lifelink119.app.data.MockDispatchGateway
import com.lifelink119.app.data.UnavailableCameraGateway
import com.lifelink119.app.data.LocalConsoleGateway
import com.lifelink119.app.data.ConsoleSnapshot
import com.lifelink119.app.vehicle.PleosVehicleGateway
import java.util.UUID
import org.json.JSONObject
import com.lifelink119.app.ui.ExpansionUi
import com.lifelink119.app.core.DrivingRestrictions
import com.lifelink119.app.vehicle.DrivingRestrictionsGateway

/** Consultation sessions contain synthetic fixtures and stay in memory. */
class MainActivity : Activity() {
    private enum class Page { HOME, SCENARIO, REVIEW, CONNECTED, VEHICLE, ROUTE, MANUAL, MANUAL_DETAIL, COOPERATION }
    private val bgColor = Color.rgb(246, 248, 252)
    private val surfaceColor = Color.WHITE
    private val accentColor = Color.rgb(36, 82, 170)
    private val textColor = Color.rgb(25, 38, 57)
    private val mutedColor = Color.rgb(77, 94, 115)
    private val warningColor = Color.rgb(137, 57, 30)
    private val camera = UnavailableCameraGateway()
    private lateinit var mock: MockDispatchGateway
    private lateinit var vehicle: PleosVehicleGateway
    private var page = Page.HOME
    private var scenario = EmergencyScenario.MEDICAL
    private var selection = DemoShareSelection()
    private var caseCode = DemoCases.defaultCode(EmergencyScenario.MEDICAL)
    private var driveState = DemoDriveState.UNKNOWN
    private lateinit var expansion: ExpansionUi
    private var expansionVisible=true
    private var drivingRestrictions: DrivingRestrictionsGateway?=null
    private val console = LocalConsoleGateway()
    private val ui = Handler(Looper.getMainLooper())
    private var started = false
    private var interactive = false
    private var consoleBusy = false
    private var consoleAutomatic = false
    private var queuedConsoleAction: String? = null
    private var consoleId: String? = null
    private var consoleSnapshot: ConsoleSnapshot? = null
    private var consoleMessage = "아직 로컬 상담센터에 공유하지 않았습니다."
    private var requestKey = "android-" + UUID.randomUUID()
    private val poll = Runnable { if (started && interactive && page in setOf(Page.CONNECTED, Page.ROUTE) && consoleId != null && !consoleBusy) consoleRequest("refresh", true) }
    private var session: MockDispatchGateway.Session? = null
    private var diagnosticMessage = "조회 전 · Pleos 전용 에뮬레이터에서 도어 상태를 확인할 수 있습니다."
    private var querying = false
    private var renderedPage: Page? = null
    private val manual by lazy { JSONObject(assets.open("emergency-manual.json").bufferedReader(Charsets.UTF_8).use { it.readText() }) }
    private var manualTopic = 0
    private var signalLastSuccess = 0L
    private var signalDeadline = 0L
    private var signalConnected = false
    private var signalWasVisible = false
    private val cooperation=CooperationGateway()
    private val cooperationCatalog by lazy { JSONObject(assets.open("cooperation-catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }) }
    private var coopId: String?=null
    private var coopSnapshot: CooperationSnapshot?=null
    private var coopScenario="EARLY_HELP"
    private var coopConsent=false
    private var coopVolunteer=false
    private var coopKey="poc-android-"+UUID.randomUUID()
    private var coopMessage="가상 위치로 주변 차량에 도움을 요청하고, 기관 조율 아래 응급협력 역할을 맡는 실험입니다."
    private var coopBusy=false
    private var coopAutomatic=false
    private var coopQueued: String?=null
    private var coopConnected=false
    private var coopFresh=0L
    private var coopDeadline=0L
    private var coopMissing=false
    private var coopWasVisible=false
    private val coopPoll=Runnable { if(started && interactive && page==Page.COOPERATION && coopId!=null && !coopBusy) coopOperation("REFRESH",true) }
    private val signalWatchdog = object : Runnable {
        override fun run() {
            if (!started || !interactive) return
            if (page == Page.CONNECTED && signalWasVisible != signalVisible()) render()
            if (page == Page.COOPERATION && coopWasVisible != coopVisible()) render()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        mock = MockDispatchGateway(savedInstanceState?.getInt("sequence") ?: 0)
        vehicle = PleosVehicleGateway(this)
        savedInstanceState?.let { saved ->
            page = runCatching { Page.valueOf(saved.getString("page") ?: "HOME") }.getOrDefault(Page.HOME)
            scenario = runCatching { EmergencyScenario.valueOf(saved.getString("scenario") ?: "MEDICAL") }.getOrDefault(EmergencyScenario.MEDICAL)
            selection = DemoShareSelection(saved.getBoolean("shareVehicle"), saved.getBoolean("shareLocation"), saved.getBoolean("shareVideo"))
            caseCode = saved.getString("caseCode")?.takeIf { DemoCases.accepts(scenario, it) } ?: DemoCases.defaultCode(scenario)
            driveState = runCatching { DemoDriveState.valueOf(saved.getString("driveState") ?: "UNKNOWN") }.getOrDefault(DemoDriveState.UNKNOWN)
            requestKey = saved.getString("requestKey") ?: requestKey
            consoleId = saved.getString("consoleId")?.takeIf { it.matches(Regex("DEMO-[0-9]{4,}")) }
            consoleMessage = saved.getString("consoleMessage") ?: consoleMessage
            diagnosticMessage = saved.getString("diagnosticMessage") ?: diagnosticMessage
            manualTopic = saved.getInt("manualTopic",0).coerceIn(0,manual.getJSONArray("guides").length()-1)
            coopId=saved.getString("coopId")?.takeIf { it.matches(Regex("POC-[0-9]{16}")) }
            coopScenario=saved.getString("coopScenario")?.takeIf { it in setOf("EARLY_HELP","EMS_FIRST","NO_RESPONDER") } ?: "EARLY_HELP"
            coopKey=saved.getString("coopKey") ?: coopKey
            coopVolunteer=saved.getBoolean("coopVolunteer",false)
            saved.getString("ticket")?.let { ticket ->
                session = runCatching {
                    mock.restore(ticket, scenario, selection, MockDispatchGateway.Status.valueOf(saved.getString("sessionStatus") ?: "DEMO_CONNECTED"), caseCode)
                }.getOrNull()
            }
            if (page == Page.CONNECTED && session == null) page = Page.REVIEW
        }
        expansionVisible=savedInstanceState?.getBoolean("expansionVisible",true) ?: true
        expansion=ExpansionUi(this,mock,savedInstanceState?.getString("expandedExperience"),::openLegacyTool)
        expansion.requestRender={if(expansionVisible && !isFinishing && !isDestroyed)expansion.render()}
        if(packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE)){
            runCatching { drivingRestrictions=DrivingRestrictionsGateway(this){value->runOnUiThread{
                if(!isDestroyed){expansion.restrictions(value)
                    if(!expansionVisible && (value.complexBlocked || value.videoBlocked))openExpansionHome()
                }
            }} }.onFailure { expansion.restrictions(DrivingRestrictions(detail="차량 제한 API 연결 불가 · 상세 기능 제한")) }
        }else expansion.restrictions(DrivingRestrictions.nonAutomotiveDemo())
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("expansionVisible",expansionVisible)
        if(::expansion.isInitialized)outState.putString("expandedExperience",expansion.save())
        outState.putString("page", page.name)
        outState.putString("scenario", scenario.name)
        outState.putBoolean("shareVehicle", selection.vehicle)
        outState.putBoolean("shareLocation", selection.location)
        outState.putBoolean("shareVideo", selection.video)
        outState.putString("caseCode", caseCode)
        outState.putString("driveState", driveState.name)
        outState.putString("requestKey", requestKey)
        outState.putString("consoleId", consoleId)
        outState.putString("consoleMessage", consoleMessage)
        outState.putInt("sequence", mock.sequence)
        outState.putString("diagnosticMessage", diagnosticMessage)
        outState.putInt("manualTopic",manualTopic)
        outState.putString("coopId",coopId);outState.putString("coopScenario",coopScenario)
        outState.putString("coopKey",coopKey);outState.putBoolean("coopVolunteer",coopVolunteer)
        session?.let { outState.putString("ticket", it.ticket); outState.putString("sessionStatus", it.status.name) }
        super.onSaveInstanceState(outState)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if(expansionVisible){if(!expansion.back())finish();return}
        if(page==Page.HOME){openExpansionHome();return}
        when (page) {
            Page.HOME -> finish()
            Page.REVIEW -> navigate(Page.SCENARIO)
            Page.CONNECTED -> navigate(Page.REVIEW)
            Page.ROUTE -> navigate(Page.CONNECTED)
            Page.MANUAL_DETAIL -> navigate(Page.MANUAL)
            else -> navigate(Page.HOME)
        }
    }

    @Suppress("DEPRECATION","OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: android.content.Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(::expansion.isInitialized)expansion.nativeMedicalResult(requestCode,resultCode)
    }

    override fun onStart() { super.onStart(); started = true; schedulePoll(); ui.post(signalWatchdog) }
    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT < 29) resumeInteractivePreview()
    }
    override fun onPause() { interactive=false; lockPreview(); super.onPause() }
    @android.annotation.TargetApi(29)
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        // Multiple Pleos activities can remain resumed while another app is active.
        if (isTopResumedActivity) resumeInteractivePreview() else { interactive=false; lockPreview() }
    }
    private fun resumeInteractivePreview() {
        if(::expansion.isInitialized)expansion.foreground()
        interactive=true; render(); schedulePoll()
        ui.removeCallbacks(signalWatchdog); ui.post(signalWatchdog)
    }
    private fun lockPreview() {
        if(::expansion.isInitialized)expansion.background()
        ui.removeCallbacks(poll); ui.removeCallbacks(signalWatchdog)
        ui.removeCallbacks(coopPoll);coopConnected=false;findViewById<View>(R.id.coop_indicator)?.clearAnimation()
        signalConnected=false; findViewById<View>(R.id.signal_indicator)?.clearAnimation()
        if (!isFinishing && !isDestroyed && !expansionVisible) render()
    }
    override fun onStop() { started=false; interactive=false; lockPreview(); super.onStop() }
    override fun onRestart() { super.onRestart(); render() }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); if(::expansion.isInitialized)expansion.release();drivingRestrictions?.release();console.release(); cooperation.release(); vehicle.release(); super.onDestroy() }

    private fun schedulePoll() {
        ui.removeCallbacks(poll);ui.removeCallbacks(coopPoll)
        if(expansionVisible)return
        if (started && interactive && page in setOf(Page.CONNECTED, Page.ROUTE) && consoleId != null) ui.postDelayed(poll, 2500)
        if (started && interactive && page==Page.COOPERATION && coopId!=null && coopSnapshot?.state !in CooperationPolicy.terminal) ui.postDelayed(coopPoll,2500)
    }

    // Navigation can rebuild controls while an unchanged automatic read is in flight.
    // Such a read need not render again, so keep its one queued action available.
    private val canOperateConsole: Boolean
        get() = !consoleBusy || (consoleAutomatic && queuedConsoleAction == null)

    private fun consoleRequest(action: String, automatic: Boolean = false) {
        val current = session ?: return
        if (!automatic && !interactive) return
        if (consoleBusy) {
            if (!automatic && consoleAutomatic && queuedConsoleAction == null) {
                queuedConsoleAction = action
                consoleMessage = "상태 갱신 뒤 요청한 동작을 처리합니다."
                if (BuildConfig.DEBUG) android.util.Log.i("LifeLinkConsole", "queued_$action")
                render()
            }
            return
        }
        val expected = requestKey
        consoleBusy = true
        consoleAutomatic = automatic
        if (BuildConfig.DEBUG && automatic) android.util.Log.d("LifeLinkConsole", "poll_start")
        ui.removeCallbacks(poll)
        if (!automatic) { consoleMessage = "로컬 모의 상담센터 요청 처리 중…"; render() }
        val callback: (com.lifelink119.app.data.ConsoleResult) -> Unit = callback@{ result ->
            if (isDestroyed || expected != requestKey) return@callback
            consoleBusy = false
            consoleAutomatic = false
            val old = consoleSnapshot
            val oldMessage = consoleMessage
            result.snapshot?.let { snapshot ->
                signalLastSuccess = SystemClock.elapsedRealtime(); signalDeadline = signalLastSuccess + snapshot.signal.remainingSeconds * 1000L; signalConnected=true
                consoleSnapshot = snapshot; consoleId = snapshot.id
                consoleMessage = "${snapshot.id} · ${consoleStatus(snapshot.status)}\n${snapshot.lastMessage}"
                selection = snapshot.selection
                val state = if (snapshot.status == "DEMO_CLOSED") MockDispatchGateway.Status.DEMO_CLOSED else current.status
                session = mock.restore(current.ticket, scenario, selection, state, caseCode)
            }
            result.error?.let { consoleMessage = it; signalConnected=false }
            val queued = queuedConsoleAction
            queuedConsoleAction = null
            if (queued != null && started && interactive) {
                consoleRequest(queued)
                return@callback
            }
            if (started && page in setOf(Page.CONNECTED, Page.ROUTE) && (!automatic || old != consoleSnapshot || oldMessage != consoleMessage)) render()
            schedulePoll()
        }
        when (action) {
            "share" -> console.share(current, requestKey, callback)
            "refresh" -> consoleId?.let { console.refresh(it, callback) }
            "close" -> consoleId?.let { console.close(it, callback) }
            "revoke" -> consoleId?.let { console.revoke(it, callback) }
            "signal_request" -> consoleId?.let { console.signal(it,"REQUEST",callback) }
            "signal_activate" -> consoleId?.let { console.signal(it,"ACTIVATE",callback) }
            "signal_stop" -> consoleId?.let { console.signal(it,"STOP",callback) }
        }
    }

    private fun consoleStatus(value: String) = when (value) {
        "DEMO_RECEIVED" -> "모의 접수"; "DEMO_REVIEWING" -> "모의 검토"
        "DEMO_GUIDANCE" -> "모의 안내"; "DEMO_CLOSED" -> "모의 종료"; else -> "모의 연결"
    }

    private fun navigate(next: Page) {
        page = next
        ui.removeCallbacks(poll)
        if (next == Page.HOME) {
            signalConnected=false
            selection = DemoShareSelection(); session = null; consoleId = null; consoleSnapshot = null; consoleBusy = false; consoleAutomatic = false; queuedConsoleAction = null
            requestKey = "android-" + UUID.randomUUID(); consoleMessage = "아직 로컬 상담센터에 공유하지 않았습니다."
        }
        render()
        schedulePoll()
    }
    private fun render() {
        if(::expansion.isInitialized && expansionVisible){expansion.render();return}
        // Each page records whether it actually renders an active indicator.
        signalWasVisible=false
        coopWasVisible=false
        when (page) {
        Page.HOME -> showHome()
        Page.SCENARIO -> showScenario()
        Page.REVIEW -> showReview()
        Page.CONNECTED -> showConnected()
        Page.VEHICLE -> showVehicleDiagnostic()
        Page.ROUTE -> showRoute()
        Page.MANUAL -> showManual()
        Page.MANUAL_DETAIL -> showManualDetail()
        Page.COOPERATION -> showCooperation()
        }
    }
    private fun openExpansionHome(){
        expansionVisible=true;page=Page.HOME;signalConnected=false;coopConnected=false
        ui.removeCallbacks(poll);ui.removeCallbacks(coopPoll);expansion.home()
    }
    private fun openLegacyTool(kind: String){
        val restrictions=expansion.effectiveRestrictions()
        if(restrictions.complexBlocked || (restrictions.videoBlocked && kind=="legacy") || (restrictions.galleryBlocked && kind in setOf("manual","legacy","cooperation"))){
            android.widget.Toast.makeText(this,"플랫폼 제한 중에는 상세 시연 도구를 열 수 없습니다.",android.widget.Toast.LENGTH_LONG).show();return
        }
        expansionVisible=false;expansion.background()
        page=when(kind){"vehicle"->Page.VEHICLE;"manual"->Page.MANUAL;"cooperation"->Page.COOPERATION;else->Page.HOME}
        render();schedulePoll()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, focus: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(20).toFloat()
        setStroke(dp(if (focus) 3 else 1), if (focus) accentColor else if(color==surfaceColor) Color.rgb(207,217,229) else color)
    }
    private fun interactiveBackground(color: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(color, true))
        addState(intArrayOf(android.R.attr.state_focused), shape(color, true))
        addState(intArrayOf(), shape(color))
    }
    private fun label(value: String, size: Float = 22f, color: Int = textColor, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(dp(4).toFloat(), 1f)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.add(view: View, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun panel() = column().apply {
        background = shape(surfaceColor)
        setPadding(dp(26), dp(24), dp(26), dp(24))
    }
    private fun button(caption: String, viewId: Int = View.NO_ID, action: () -> Unit) = Button(this).apply {
        id = viewId; text = caption; textSize = 22f; isAllCaps = false
        setTextColor(bgColor); background = interactiveBackground(accentColor)
        minHeight = dp(76); setPadding(dp(20), dp(14), dp(20), dp(14))
        setOnClickListener { action() }
    }
    private fun split(left: View, right: View): LinearLayout = LinearLayout(this).apply {
        val wide = resources.configuration.screenWidthDp >= 900
        orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        addView(left, LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f))
        addView(right, LinearLayout.LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f).apply {
            if (wide) marginStart = dp(20) else topMargin = dp(20)
        })
    }

    private fun shell(title: String, content: LinearLayout.() -> Unit) {
        val samePage = renderedPage == page
        val previousScroll = if (samePage) findViewById<ScrollView>(R.id.content_scroll)?.scrollY ?: 0 else 0
        val previousFocus = if (samePage) currentFocus?.id else null
        val root = column().apply { setBackgroundColor(bgColor) }
        root.add(label("시연 모드 | 실제 신고·상담 연결 아님\n이동 중 응급상황도 시연 가능 · 실제 차량 속도 감지 미연동", 18f, warningColor, true).apply {
            id = R.id.safety_banner; gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setBackgroundColor(Color.rgb(255, 244, 230))
        })
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val side = maxOf(dp(24), (resources.displayMetrics.widthPixels - dp(1480)) / 2 + dp(24))
            setPadding(side, dp(14), side, dp(14))
        }
        if (page != Page.HOME) nav.addView(button("← 이전", R.id.back_button) { onBackPressed() }, LinearLayout.LayoutParams(dp(144), dp(64)).apply { marginEnd = dp(22) })
        nav.addView(label("LifeLink 119", 30f, textColor, true), LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(label(title, 20f, mutedColor))
        root.add(nav)
        root.add(LinearLayout(this).apply{
            setPadding(dp(24),0,dp(24),dp(8))
            addView(button("홈",R.id.x_home){openExpansionHome()},LinearLayout.LayoutParams(0,dp(64),1f))
            addView(button("긴급 연결",R.id.x_urgent_shortcut){openExpansionHome();expansion.emergency()},LinearLayout.LayoutParams(0,dp(64),1f).apply{marginStart=dp(12)})
        })
        val body = column().apply {
            setPadding(dp(24), dp(14), dp(24), dp(34))
            add(panel().apply {
                val context = when (driveState) {
                    DemoDriveState.DRIVING -> "이동 중 응급상황 · 동승자 조작 시연"
                    DemoDriveState.PARKED -> "정차 상황 · 응급지원 시연"
                    DemoDriveState.UNKNOWN -> "이동 상태 선택 전 · 응급지원 바로 사용 가능"
                }
                add(label(context, 20f, warningColor, true).apply { id = R.id.motion_status })
                add(split(
                    button("이동 중 상황 시연", R.id.simulate_driving) {
                        driveState = DemoDriveState.DRIVING
                        render(); schedulePoll()
                    }.apply { isEnabled = driveState != DemoDriveState.DRIVING },
                    button("정차 상황 시연", R.id.confirm_parked) {
                        driveState = DemoDriveState.PARKED
                        render(); schedulePoll()
                    }.apply { isEnabled = driveState != DemoDriveState.PARKED }
                ), 12)
            })
            content()
        }
        val frame = FrameLayout(this).apply {
            addView(body, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels, dp(1480)), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        val scroll = ScrollView(this).apply { id = R.id.content_scroll; isFillViewport = true; addView(frame) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderedPage = page
        if (samePage) scroll.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                scroll.scrollTo(0, previousScroll)
                previousFocus?.takeIf { it != View.NO_ID }?.let { id -> findViewById<View>(id)?.takeIf { it.isEnabled }?.requestFocus() }
            }
        })
    }

    private fun tile(title: String, subtitle: String, viewId: Int, action: () -> Unit) = panel().apply {
        id = viewId; background = interactiveBackground(surfaceColor)
        isClickable = true; isFocusable = true; contentDescription = "$title 열기"
        minimumHeight = dp(154)
        add(label(title, 25f, textColor, true))
        add(label(subtitle, 18f, mutedColor), 12)
        setOnClickListener { action() }
    }

    private fun showHome() = shell("가족을 위한 차량형 응급지원 데모") {
        add(label("가족을 위한 응급지원,\n차 안에서 미리 체험하세요", 38f, textColor, true))
        add(label("5가지 상황을 가상 데이터로 체험합니다. 실제 위급상황은 직접 119 또는 112로 신고하세요.", 21f, mutedColor), 16)
        val ids = listOf(R.id.scenario_medical, R.id.scenario_video, R.id.scenario_transport, R.id.scenario_police, R.id.scenario_wellbeing)
        val tiles = EmergencyScenario.entries.mapIndexed { index, item ->
            tile(item.heading, item.tagline, ids[index]) { scenario = item; caseCode = DemoCases.defaultCode(item); selection = DemoShareSelection(); session = null; navigate(Page.SCENARIO) }
        } + tile("Pleos SDK 진단", "실제 도어·배터리 조회 · 상담 정보와 분리", R.id.sdk_diagnostic) { navigate(Page.VEHICLE) }
        val columns = when { resources.configuration.screenWidthDp >= 1200 -> 3; resources.configuration.screenWidthDp >= 760 -> 2; else -> 1 }
        tiles.chunked(columns).forEach { group ->
            val row = LinearLayout(this@MainActivity)
            group.forEachIndexed { index, tile -> row.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) marginStart = dp(18) }) }
            repeat(columns - group.size) { row.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f)) }
            add(row, 18)
        }
        add(button("기본 응급상황 매뉴얼 (오프라인)",R.id.open_manual) { navigate(Page.MANUAL) },18)
        add(button("옆 차가 첫 도움으로 · 응급협력 차량 PoC",R.id.open_cooperation) { navigate(Page.COOPERATION) },18)
        add(label("로컬 모의 상담센터에서 진행 상태를 확인할 수 있습니다. 실제 촬영·GPS 수집·기관 연계는 미연결입니다.", 18f, warningColor), 22)
    }

    private fun showManual() = shell("기본 응급상황 매뉴얼") {
        add(label(manual.getString("title"),34f,textColor,true))
        add(label(manual.getString("notice"),21f,warningColor),18)
        add(label(manual.getString("scope"),20f,mutedColor),16)
        val guides=manual.getJSONArray("guides")
        val ids=listOf(R.id.manual_first,R.id.manual_cpr,R.id.manual_aed,R.id.manual_bleeding,R.id.manual_burn,R.id.manual_seizure)
        for (i in 0 until guides.length()) add(button(guides.getJSONObject(i).getString("title"),ids[i]) {
            manualTopic=i; navigate(Page.MANUAL_DETAIL)
        },14)
        add(label("자료 확인일: ${manual.getString("version")} · 의료진 검토 전 · 앱 내 오프라인 열람",18f,mutedColor),20)
    }

    private fun showManualDetail() = shell("공식자료 요약 · 학습용") {
        val guide=manual.getJSONArray("guides").getJSONObject(manualTopic)
        add(label(guide.getString("title"),34f,textColor,true).apply { id=R.id.manual_title })
        add(label(guide.getString("audience"),21f,accentColor,true),14)
        add(label("실제 위급상황은 직접 119 신고 · 상담원 지시 우선 · 앱은 통화를 연결하지 않습니다.",20f,warningColor),16)
        val steps=guide.getJSONArray("steps")
        for(i in 0 until steps.length()) add(panel().apply { add(label("${i+1}. ${steps.getString(i)}",24f)) },18)
        add(label(guide.getString("caution"),21f,warningColor,true),20)
        val sources=guide.getJSONArray("sources")
        val bibliography=manual.getJSONObject("sources")
        for(i in 0 until sources.length()) {
            val source=bibliography.getJSONObject(sources.getString(i))
            add(label("출처: ${source.getString("title")}\n${source.getString("url")}",17f,mutedColor),14)
        }
        add(label("자료 확인일: ${manual.getString("version")} · 의료진 검토 전",18f,mutedColor),18)
    }

    private fun resetCooperation() {
        coopId=null;coopSnapshot=null;coopConsent=false;coopVolunteer=false;coopMissing=false
        coopConnected=false;coopFresh=0;coopDeadline=0;coopKey="poc-android-"+UUID.randomUUID()
        coopMessage="새 가상 상황을 선택하고 모의 위치 공유에 동의하면 시작합니다."
        render();schedulePoll()
    }
    private fun coopVisible()=CooperationPolicy.canDisplay(coopSnapshot,SystemClock.elapsedRealtime(),coopFresh,coopDeadline,
        started && interactive,coopConnected,coopVolunteer)

    private fun coopOperation(action: String, automatic: Boolean=false) {
        if(!automatic && !interactive)return
        if(coopBusy) { if(!automatic && coopAutomatic && coopQueued==null){coopQueued=action;coopMessage="상태 갱신 후 요청한 실험 동작을 처리합니다.";render()};return }
        if(action=="CREATE" && !coopConsent)return
        val key=coopKey;coopBusy=true;coopAutomatic=automatic;ui.removeCallbacks(coopPoll)
        if(!automatic){coopMessage="로컬 응급협력 실험 요청 처리 중…";render()}
        val callback:(com.lifelink119.app.data.CooperationResult)->Unit=callback@{ result ->
            if(isDestroyed || key!=coopKey)return@callback
            coopBusy=false;coopAutomatic=false;coopMissing=result.missing
            result.snapshot?.let { s ->
                val previous=coopSnapshot
                val next=SystemClock.elapsedRealtime()+s.beaconRemaining*1000L
                coopDeadline=if(previous?.beaconApprovalId!=null && previous.id==s.id && previous.beaconApprovalId==s.beaconApprovalId) minOf(coopDeadline,next) else next
                coopFresh=SystemClock.elapsedRealtime();coopConnected=true;coopId=s.id;coopSnapshot=s
                coopMessage=coopLabel(s.state)+" · "+s.id
            }
            result.error?.let { coopMessage=it;coopConnected=false }
            val queued=coopQueued;coopQueued=null
            if(queued!=null && started && interactive){coopOperation(queued);return@callback}
            if(started && page==Page.COOPERATION)render()
            schedulePoll()
        }
        when(action) {
            "CREATE" -> cooperation.create(coopScenario,coopKey,callback)
            "REFRESH" -> coopId?.let { cooperation.refresh(it,callback) }
            else -> coopId?.let { cooperation.action(it,action,coopSnapshot?.vehicleId,callback) }
        }
    }
    private fun coopLabel(state:String)=when(state){
        "REQUESTED"->"주변 도움 요청";"CONFIRMED"->"119 역할 상황 확인";"INVITED"->"협력 차량 초대"
        "ACCEPTED"->"자발적 참여 수락";"DECLINED"->"참여 거절 · 다른 차량 검토";"AUTHORIZED"->"응급협력 역할 부여"
        "ON_SCENE"->"안전한 현장 지원 시연";"HANDED_OVER"->"구급대 인계 완료";"CANCELLED"->"요청 취소"
        "REVOKED"->"기관 역할 철회";"WITHDRAWN"->"참여 철회";"EXPIRED"->"임무 유효기간 만료";else->state
    }
    private fun coopTime(seconds:Int?)=seconds?.let { "${it/60}분 ${it%60}초" } ?: "참여 차량 없음"
    private fun showCooperation()=shell("응급협력 차량 · PoC") {
        add(label("옆 차가, 첫 도움으로.",34f,textColor,true))
        add(label("주변 차량의 자발적 참여와 기관 조율로 구급대 도착 전 도움을 연결하는 실험입니다.",22f,mutedColor),14)
        add(label(coopMessage,20f,warningColor).apply { id=R.id.coop_status },16)
        val s=coopSnapshot
        val canUse=(!coopBusy || (coopAutomatic && coopQueued==null)) && BuildConfig.DEBUG
        if(coopId==null) {
            val cases=cooperationCatalog.getJSONArray("scenarios")
            val ids=listOf(R.id.coop_case_early,R.id.coop_case_ems,R.id.coop_case_none)
            for(i in 0 until cases.length()) {
                val item=cases.getJSONObject(i);val code=item.getString("id")
                add(button((if(coopScenario==code) "● " else "○ ")+item.getString("title"),ids[i]){
                    coopScenario=code;coopKey="poc-android-"+UUID.randomUUID();coopConsent=false;render()
                }.apply { isEnabled=!coopBusy },12)
            }
            add(CheckBox(this@MainActivity).apply {
                id=R.id.coop_consent;text="고정 가상 위치로 주변 협력 차량 찾기에 동의";textSize=22f;setTextColor(textColor)
                minHeight=dp(64);isChecked=coopConsent;isEnabled=!coopBusy
                setOnCheckedChangeListener { _,checked -> coopConsent=checked;this@MainActivity.findViewById<Button>(R.id.coop_create)?.isEnabled=checked && !coopBusy && BuildConfig.DEBUG }
            },18)
            add(button("주변 차량 도움 요청 (모의)",R.id.coop_create){coopOperation("CREATE")}.apply {isEnabled=canUse && coopConsent},14)
            add(label(cooperationCatalog.getString("assumptions"),19f,mutedColor),18)
            if(!BuildConfig.DEBUG)add(label("이 빌드는 오프라인 설명만 제공합니다. 로컬 실험 연결은 debug 빌드에서 실행합니다.",19f,warningColor),14)
        } else if(s==null) {
            add(button("진행 중인 실험 불러오기",R.id.coop_refresh){coopOperation("REFRESH")}.apply {isEnabled=canUse},14)
        } else {
            add(panel().apply {
                add(label(s.title,25f,textColor,true));add(label(coopLabel(s.state),24f,accentColor,true).apply {id=R.id.coop_state},14)
                add(label("구급대 도착 가정 ${coopTime(s.baselineSeconds)}\n협력 차량 첫 도움 가정 ${coopTime(s.supportSeconds)}\n먼저 도울 기회 가정 ${coopTime(s.headStartSeconds)}",23f).apply {id=R.id.coop_metrics},14)
                add(label("고정 가정 비교입니다. 실제 대응시간·생존율 측정값이 아닙니다. 기존 119 대응을 유지하는 시나리오입니다.",18f,warningColor),12)
            },18)
            add(button(if(coopVolunteer) "요청자 역할로 보기" else "협력 차량 역할로 보기",R.id.coop_role_switch){coopVolunteer=!coopVolunteer;render()},18)
            add(panel().apply {
                add(label(if(coopVolunteer) "협력 차량 역할 · ${s.vehicleId ?: "초대 대기"}" else "도움 요청자 역할",24f,accentColor,true))
                add(label(s.missionId?.let { "$it\n임무 잔여 ${s.missionRemaining}초" } ?: "119 역할의 상황 확인 · 차량의 수락 · 임무 부여를 기다립니다.",20f),14)
                coopWasVisible=coopVisible()
                add(label(if(coopWasVisible) "● 응급협력 비상표시 ON · 실등 미연결" else "● 응급협력 비상표시 OFF",23f,if(coopWasVisible) Color.rgb(255,130,140) else mutedColor,true).apply {
                    id=R.id.coop_indicator
                    if(coopWasVisible && ValueAnimator.areAnimatorsEnabled())startAnimation(AlphaAnimation(.35f,1f).apply {duration=1000;repeatCount=Animation.INFINITE;repeatMode=Animation.REVERSE})
                },18)
                add(label("표시 승인 ${s.beaconAuthority ?: "대기"} · 잔여 ${s.beaconRemaining}초\n${s.beaconApprovalId ?: "이 차량의 한시 승인 후 표시를 켭니다."}",18f,mutedColor),12)
                if(coopVolunteer && s.vehicleId!=null) {
                    if(s.state=="INVITED"){
                        add(button("자발적 참여 수락",R.id.coop_accept){coopOperation("ACCEPT")}.apply {isEnabled=canUse},14)
                        add(button("참여 거절",R.id.coop_decline){coopOperation("DECLINE")}.apply {isEnabled=canUse},12)
                    }
                    if(s.state=="AUTHORIZED")add(button("현장 도착·초기대응 지원 시연",R.id.coop_arrive){coopOperation("ARRIVE")}.apply {isEnabled=canUse},14)
                    if(s.beaconState=="APPROVED")add(button("승인된 응급협력 표시 켜기",R.id.coop_activate){coopOperation("ACTIVATE_BEACON")}.apply {isEnabled=canUse && coopConnected},14)
                    if(s.beaconState in setOf("APPROVED","ACTIVE"))add(button("응급협력 표시 끄기",R.id.coop_stop){coopOperation("STOP_BEACON")}.apply {isEnabled=canUse},12)
                    if(s.state in setOf("ACCEPTED","AUTHORIZED","ON_SCENE"))add(button("참여 철회",R.id.coop_withdraw){coopOperation("WITHDRAW")}.apply {isEnabled=canUse},12)
                }
            },18)
            add(label("기관 역할 조율: PC의 http://127.0.0.1:8781/cooperation\n119 역할에서 상황 확인 → 차량 초대 → 수락 확인 → 임무 부여. 표시 승인은 별도로 119·112 역할에서 진행합니다.",20f,mutedColor),18)
            add(button("실험 상태 새로고침",R.id.coop_refresh){coopOperation("REFRESH")}.apply {isEnabled=canUse},14)
            if(s.state !in CooperationPolicy.terminal && !coopVolunteer)add(button("도움 요청 취소",R.id.coop_cancel){coopOperation("CANCEL")}.apply {isEnabled=canUse},12)
            if(s.state in CooperationPolicy.terminal)add(button("새 가상 상황 실험",R.id.coop_new){resetCooperation()},14)
            add(label("가상 차량과 참여 조건\n\n${s.candidates}",20f,mutedColor),20)
            add(label("실험 조작 기록 · 실제 이동시간과 별개\n${s.events}",19f,mutedColor),20)
        }
        if(coopMissing)add(button("없는 실험 기록 지우고 새로 시작",R.id.coop_recover){resetCooperation()},14)
        add(label("실제 신고·운행·차량 등화 제어는 연결하지 않습니다. 현장 지원에서 구급대 인계까지를 시연하며 환자 이송·병원 선택은 별도 기관 판단입니다.",18f,warningColor),20)
    }

    private fun signalVisible() = DemoSignalPolicy.canDisplay(consoleSnapshot?.signal ?: DemoSignalSnapshot(), consoleId,
        SystemClock.elapsedRealtime(),signalLastSuccess,signalDeadline,started && interactive,
        signalConnected)

    private fun signalPanel(): LinearLayout = panel().apply {
        val signal=consoleSnapshot?.signal ?: DemoSignalSnapshot()
        add(label("기관 승인형 비상 표시 (모의)",24f,accentColor,true))
        add(label("실제 기관 승인·긴급자동차 지정·차량 등화 제어가 아닙니다. 색상·점멸 규격은 미확정입니다.",18f,warningColor),12)
        val labels=mapOf("OFF" to "꺼짐","REQUESTED" to "승인 요청 대기","CONFIRMED" to "비상상황 확인 (모의)","APPROVED" to "표시 승인 (모의)","PREVIEW_ACTIVE" to "화면 점멸 표시 중 (모의)","REVOKED" to "중단·승인 철회","REJECTED" to "요청 거절","EXPIRED" to "승인 만료")
        add(label("${labels[signal.state] ?: "꺼짐"} · ${signal.authority ?: "기관 미선택"} · 잔여 ${signal.remainingSeconds}초",21f).apply { id=R.id.signal_status },14)
        signalWasVisible=signalVisible()
        add(label(if(signalWasVisible) "● 화면 표시 ON · 실등 미연결" else "● 화면 표시 OFF",23f,if(signalWasVisible) Color.rgb(255,130,140) else mutedColor,true).apply {
            id=R.id.signal_indicator
            if(signalWasVisible && ValueAnimator.areAnimatorsEnabled()) startAnimation(AlphaAnimation(.35f,1f).apply {
                duration=1000; repeatCount=Animation.INFINITE; repeatMode=Animation.REVERSE
            })
        },14)
        signal.approvalId?.let { add(label("가짜 승인 ID: $it",17f,mutedColor),12) }
        val closed=session?.status==MockDispatchGateway.Status.DEMO_CLOSED
        if(!closed && consoleId!=null) {
            if(signal.state in setOf("OFF","REVOKED","EXPIRED","REJECTED")) add(button("표시 승인 요청 (모의)",R.id.signal_request) { consoleRequest("signal_request") }.apply { isEnabled=canOperateConsole && BuildConfig.DEBUG },14)
            if(signal.state=="APPROVED") add(button("승인된 화면 점멸 시작",R.id.signal_activate) { consoleRequest("signal_activate") }.apply { isEnabled=canOperateConsole && signalConnected && signal.remainingSeconds>0 },14)
            if(signal.state in setOf("REQUESTED","CONFIRMED","APPROVED","PREVIEW_ACTIVE")) add(button("표시 중단·승인 철회",R.id.signal_stop) { consoleRequest("signal_stop") }.apply { isEnabled=canOperateConsole },14)
        }
        add(label("탑승자는 승인할 수 없습니다. 상담원 웹에서 119·112 역할로 비상 확인 후 승인합니다. 이동 중 상황에서도 표시할 수 있으며, 승인 120초 만료·철회·종료·연결 끊김·백그라운드에서는 화면 표시를 멈춥니다.",18f,mutedColor),14)
    }

    private fun progress(step: Int): LinearLayout = LinearLayout(this).apply {
        val titles = listOf(if (scenario == EmergencyScenario.MEDICAL) "119 응급상담" else "상황 안내", "정보 확인", "모의 연결")
        titles.forEachIndexed { index, title ->
            addView(label("${index + 1}  $title", 20f, if (index + 1 == step) bgColor else mutedColor, true).apply {
                background = shape(if (index + 1 == step) accentColor else surfaceColor)
                gravity = Gravity.CENTER; setPadding(dp(10), dp(14), dp(10), dp(14))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(12) })
        }
    }

    private fun showScenario() = shell(scenario.heading) {
        add(progress(1)); add(label(scenario.heading, 36f, textColor, true), 28)
        add(label(scenario.tagline, 23f, mutedColor), 12)
        val cases = DemoCases.options(scenario)
        add(label("가상 상황 선택 · 개인정보 입력 없음", 21f, accentColor, true), 20)
        add(Spinner(this@MainActivity).apply {
            id = R.id.case_picker; minimumHeight = dp(64)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, cases.map { it.label })
            setSelection(cases.indexOfFirst { it.code == caseCode }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { caseCode = cases[position].code; (view as? TextView)?.setTextColor(textColor) }
            }
        }, 10)
        add(split(panel().apply {
            add(label("먼저 알아두세요", 24f, warningColor, true))
            add(label(scenario.warning, 23f), 18)
            add(label(scenario.nextStep, 20f, mutedColor), 18)
        }, panel().apply {
            add(label("연결 흐름 미리보기", 24f, accentColor, true))
            add(label("1. 상황 안내 확인\n2. 가상 정보와 선택 항목 확인\n3. ${FlowPolicy.agency(scenario)} 가상 세션 생성", 23f), 18)
            if (scenario == EmergencyScenario.VIDEO) add(label(camera.capability(), 20f, warningColor), 18)
        }), 24)
        add(button("정보 확인으로", R.id.next_step) { navigate(Page.REVIEW) }, 26)
    }

    private fun sharingSummary(): String = buildString {
        append("선택한 모의 정보\n\n")
        append(if (selection.vehicle) "차량: 가상 차량 A / DEMO-CAR-01\n배터리: 72% (가상 값)" else "차량 정보: 공유 안 함")
        append("\n\n")
        append(if (selection.location) "위치: 가상 훈련장 A구역\nDEMO_GRID (120, 80) · 실제 GPS 아님" else "위치 정보: 공유 안 함")
        append(if (selection.video) "\n\n영상: 합성 도형 예시만 공유 · 실제 촬영 없음" else "\n\n영상: 공유 안 함 · 실제 카메라 미연결")
    }

    private fun showReview() = shell("모의 상담 전 정보 확인") {
        add(progress(2)); add(label("공유할 가상 정보를 확인하세요", 34f, textColor, true), 26)
        add(label("선택은 모두 꺼진 상태로 시작합니다. 아무 항목도 선택하지 않고 모의 연결할 수 있습니다.", 21f, mutedColor), 12)
        val preview = label(sharingSummary(), 22f)
        val choices = panel().apply {
            add(label("${FlowPolicy.agency(scenario)} · ${scenario.heading}", 23f, accentColor, true))
            add(label("실제 차량정보·위치·증상은 수집하지 않습니다.", 20f, mutedColor), 16)
            fun option(caption: String, checked: Boolean, viewId: Int, changed: (Boolean) -> Unit): CheckBox = CheckBox(this@MainActivity).apply {
                id = viewId; text = caption; textSize = 22f; setTextColor(textColor)
                minHeight = dp(76); isChecked = checked; buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
                setOnCheckedChangeListener { _, value -> changed(value); preview.text = sharingSummary() }
            }
            add(option("가상 차량 정보 포함", selection.vehicle, R.id.share_vehicle) { selection = selection.copy(vehicle = it) }, 20)
            add(option("가상 위치 정보 포함", selection.location, R.id.share_location) { selection = selection.copy(location = it) }, 12)
            if (scenario == EmergencyScenario.VIDEO) add(option("합성 영상 예시 포함 (실제 촬영 없음)", selection.video, R.id.share_video) { selection = selection.copy(video = it) }, 12)
        }
        add(split(choices, panel().apply { add(preview) }), 24)
        add(button("앱 안의 가상 세션 생성", R.id.connect_demo) {
            consoleId = null; consoleSnapshot = null; consoleMessage = "아직 로컬 상담센터에 공유하지 않았습니다."
            consoleBusy = false; consoleAutomatic = false; queuedConsoleAction = null
            requestKey = "android-" + UUID.randomUUID()
            session = mock.connect(scenario, selection, caseCode); navigate(Page.CONNECTED)
        }, 26)
    }

    private fun showConnected() {
        val current = session ?: run { navigate(Page.REVIEW); return }
        shell("모의 상담 세션") {
            val closed = current.status == MockDispatchGateway.Status.DEMO_CLOSED
            add(progress(3)); add(label(if (closed) "모의 세션을 종료했습니다" else "앱 안의 가상 세션을 만들었습니다", 34f, textColor, true), 26)
            add(split(panel().apply {
                add(label("${current.agency} · ${if (closed) "종료 (모의)" else "연결 (모의)"}", 24f, accentColor, true))
                add(label(current.ticket, 42f, textColor, true).apply { id = R.id.session_ticket; typeface = Typeface.MONOSPACE }, 22)
                add(label(current.message, 21f, mutedColor), 22)
            }, panel().apply { add(label(sharingSummary(), 22f)) }), 24)
            add(panel().apply {
                add(label("로컬 모의 상담센터", 24f, accentColor, true))
                add(label(consoleMessage, 21f).apply { id = R.id.console_result }, 16)
                add(label("고정 가짜 정보만 이 PC의 localhost에 공유합니다. 실제 기관으로 보내지 않습니다.", 18f, mutedColor), 12)
                if (!BuildConfig.DEBUG) add(label("현재 빌드는 앱 내부 시연만 제공합니다. 로컬 상담센터 연결은 개발용 시연 빌드에서 이용하세요.", 18f, warningColor), 12)
                if (!closed && consoleId == null) add(button("로컬 모의 상담센터에 공유", R.id.share_console) { consoleRequest("share") }.apply { isEnabled = canOperateConsole && BuildConfig.DEBUG }, 18)
                if (consoleId != null) {
                    add(button("상담 상태 새로고침", R.id.refresh_console) { consoleRequest("refresh") }.apply { isEnabled = canOperateConsole }, 14)
                    if (selection.vehicle || selection.location || selection.video) add(button("공유 정보 철회", R.id.revoke_console) { consoleRequest("revoke") }.apply { isEnabled = canOperateConsole }, 14)
                }
            }, 22)
            if(consoleId!=null) add(signalPanel(),22)
            if (selection.location) add(button("가상 경로 미리보기 (실제 내비 아님)", R.id.view_route) { navigate(Page.ROUTE) }, 18)
            if (!closed) add(button("모의 세션 종료", R.id.close_session) {
                if (consoleId == null) { session = mock.close(current); render() } else consoleRequest("close")
            }.apply { isEnabled = canOperateConsole }, 24)
            add(button("다른 상황 체험", R.id.home_button) { navigate(Page.HOME) }, 18)
        }
    }

    private fun showRoute() = shell("가상 경로 미리보기") {
        add(label("가상 만남 지점 B", 34f, textColor, true))
        add(label("DEMO_GRID 고정 격자 경로입니다. 실제 지도·병원 선택·이동 지시·NaviHelper 실행이 아닙니다.", 22f, warningColor), 18)
        if (selection.location) {
            addView(DemoRouteView(this@MainActivity).apply { id = R.id.route_preview }, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(22) })
            add(label("가상 훈련장 A구역 (120, 80) → 가상 만남 지점 B (240, 160)\n실제 이송·경찰 지원 여부와 이동 방법은 전문기관이 판단합니다.", 23f), 20)
        } else add(label("가상 위치 공유가 선택되지 않아 경로를 표시하지 않습니다.", 23f), 20)
        add(button("모의 상담으로 돌아가기", R.id.return_session) { navigate(Page.CONNECTED) }, 24)
    }

    private fun showVehicleDiagnostic() = shell("Pleos SDK 진단 · 읽기 전용") {
        add(label("도어·배터리 상태를 확인합니다", 34f, textColor, true))
        add(label("이 화면만 실제 Vehicle SDK 2.3.2를 사용합니다. 조회 결과는 모의 상담 세션에 포함되지 않습니다.", 22f, mutedColor), 16)
        val status = label(diagnosticMessage, 23f).apply { id = R.id.diagnostic_result }
        val query = button("운전석 도어 상태 조회", R.id.read_driver_door) {}
        query.isEnabled = !querying
        query.setOnClickListener {
            querying = true
            diagnosticMessage = "지원 기능과 운전석 영역을 확인하는 중…"
            render()
            vehicle.readDriverDoor { result ->
                if (isFinishing || isDestroyed) return@readDriverDoor
                querying = false
                diagnosticMessage = "조회 결과: ${result.status.name}\n${result.message}"
                // The user may have left and reopened this screen during the request.
                if (page == Page.VEHICLE) render()
            }
        }
        add(panel().apply { add(status); add(query, 26) }, 28)
        add(button("배터리 충전 상태 조회 (SDK 원본)", R.id.read_battery) {
            querying = true; diagnosticMessage = "배터리 조회 지원 기능을 확인하는 중…"; render()
            vehicle.readBatteryCharge { result ->
                if (isFinishing || isDestroyed) return@readBatteryCharge
                querying = false; diagnosticMessage = "배터리 조회 결과: ${result.status.name}\n${result.message}"
                if (page == Page.VEHICLE) render()
            }
        }.apply { isEnabled = !querying }, 20)
        add(label("조회만 수행합니다. 미지원·권한 부족·값 없음·15초 시간 초과를 구분하며, 도어를 제어하지 않습니다.", 20f, warningColor), 24)
    }
}

private class DemoRouteView(context: android.content.Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    init { contentDescription = "실제 지도가 아닌 가상 격자 경로. 가상 훈련장 A구역에서 가상 만남 지점 B까지." }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val sx = width / 320f; val sy = height / 220f; val density = resources.displayMetrics.density
        canvas.drawColor(Color.rgb(22,39,58)); paint.color = Color.rgb(49,70,91); paint.strokeWidth = density
        for (x in 0..320 step 40) canvas.drawLine(x*sx,0f,x*sx,height.toFloat(),paint)
        for (y in 0..220 step 40) canvas.drawLine(0f,y*sy,width.toFloat(),y*sy,paint)
        paint.color = Color.rgb(85,225,208); paint.strokeWidth = 4f*density
        canvas.drawLine(120*sx,80*sy,180*sx,80*sy,paint); canvas.drawLine(180*sx,80*sy,180*sx,160*sy,paint); canvas.drawLine(180*sx,160*sy,240*sx,160*sy,paint)
        canvas.drawCircle(120*sx,80*sy,9*density,paint); paint.color = Color.rgb(255,210,140); canvas.drawCircle(240*sx,160*sy,9*density,paint)
        paint.textSize = 14f*density; canvas.drawText("DEMO_GRID · SIMULATION ONLY",12*density,height-12*density,paint)
    }
}
