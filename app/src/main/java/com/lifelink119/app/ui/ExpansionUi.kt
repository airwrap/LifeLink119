package com.lifelink119.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.R
import com.lifelink119.app.core.*
import com.lifelink119.app.core.emergency.EmergencyHealthSelection
import com.lifelink119.app.data.*
import com.lifelink119.app.vehicle.PleosVehicleGateway
import com.lifelink119.app.vehicle.VehicleContextController
import java.util.Locale
import org.json.JSONObject

/** Incremental Views UI over the existing Activity, dispatch gateway and SDK tools. */
class ExpansionUi(
    private val host: Activity,
    dispatch: MockDispatchGateway,
    saved: String?,
    vehicleGateway: PleosVehicleGateway,
    private val legacy: (String) -> Unit,
) {
    private enum class Page {
        HOME,
        EMERGENCY,
        CONSULT,
        SESSION,
        PERSON,
        GALLERY,
        PROFILES,
        EDIT,
        MY_HEALTH,
        MY_HEALTH_EDIT,
        HEALTH_LINK,
        HEALTH_EXAMPLE,
        CARD,
        MORE,
        SUPPORT,
        TEXT,
        SIGN,
        VIDEO,
        GUIDED_VIDEO,
        LOCATION,
        SUMMARY,
        CENTER,
        POLICE,
        NAV,
        SAFE,
        AGENCY,
        ACCESS,
        POWER,
        DRIVER,
        TOOLS,
        FINISH,
        VOICE,
        EASY,
        COOPERATION,
        SECURITY,
        DRIVING_SETUP,
        DRIVING_DETAILS,
        VEHICLE_CONTEXT,
        EMERGENCY_SUPPORT,
        AI_PREFERENCES,
    }

    private val bg = LifeLinkUiStyle.background
    private val surface = LifeLinkUiStyle.surface
    private val ink = LifeLinkUiStyle.ink
    private val muted = LifeLinkUiStyle.secondary
    private val accent = LifeLinkUiStyle.accent
    private val warning = LifeLinkUiStyle.warning
    private val emergency = LifeLinkUiStyle.emergency
    private val main = Handler(Looper.getMainLooper())
    val catalog =
        ExperienceCatalogParser.parse(
            host.assets.open("experience-catalog.json").bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        )
    private val repository = FakeProfileRepository(host, catalog)
    private val personalHealth = PersonalHealthStore(host)
    private val healthDraft = HealthEditorDraft()
    private var healthRecordId = "SELF"
    private val emergencyHealth = EmergencyHealthSelection()
    private var healthForIncident: String? = null
    private var agencyRole = RegistrationRole.SELF
    private var safeFault = SafeCallFault.NONE
    private val healthLinkExample = HealthLinkExample()
    private val cooperationCatalog =
        CooperationCatalogParser.parse(
            host.assets.open("cooperation-catalog.json").bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        )
    val engine = SessionEngine(catalog, repository.load(), cooperationCatalog = cooperationCatalog)
    private val privacy = MedicalPrivacyManager(host)
    private val voiceAiPreferences = VoiceAiPreferences(host)
    private val protectedPages =
        setOf(
            Page.PROFILES,
            Page.EDIT,
            Page.MY_HEALTH,
            Page.MY_HEALTH_EDIT,
            Page.HEALTH_LINK,
            Page.HEALTH_EXAMPLE,
            Page.PERSON,
            Page.CARD,
            Page.SUMMARY,
            Page.CENTER,
            Page.SAFE,
            Page.DRIVER,
            Page.VIDEO,
        )
    private var deferredMedicalAction: (() -> Unit)? = null
    private val calls: EmergencyCallGateway = DemoEmergencyCallGateway(dispatch)
    private val camera: CameraGateway = SyntheticCameraGateway()
    private val bridge = ExperienceConsoleBridge()
    private var page = Page.HOME
    private var editId: String? = null
    private var lastPage: Page? = null
    private var message = ""
    private var inAction = false
    private var renderedMedicalUnlocked = false
    private var largeText = repository.preferences().first
    private var easy = repository.preferences().second
    private var platform = DrivingRestrictions()
    private var extraRestriction = false
    private var shareDraft = mutableSetOf<ShareField>()
    private var pcConsent = false
    private var pcPostedId: String? = null
    private var pcBusy = false
    private var bridgeMessage = "PC 전송 전 · 앱 내 상황실은 오프라인으로 동작합니다."
    private var frame = 0
    private var guideStep = 0
    private var changeContextOnly = false
    private var electric = true
    private var v2lSupported = true
    private var capacity = 64.0
    private var soc = 72.0
    private var reserve = 30.0
    private var efficiency = .85
    private var load = 200.0
    private val presence = UiPresence()
    private val inForeground
        get() = presence.active

    private var vehicleConsent = false
    private var powerSocReading: VehicleSample? = null
    private val vehicleContext =
        VehicleContextController(vehicleGateway) {
            vehicleConsent = false
            if (inForeground && page == Page.VEHICLE_CONTEXT) requestRender()
        }
    private val emergencySupport: EmergencySupportController =
        EmergencySupportController(host, vehicleGateway) {
            emergencySupportUi.onStateChanged()
            if (inForeground && page == Page.EMERGENCY_SUPPORT) requestRender()
        }
    private val emergencySupportUi: EmergencySupportUi by lazy {
        EmergencySupportUi(
            host,
            emergencySupport,
            { engine.restrictions.complexBlocked },
            { inForeground && page == Page.EMERGENCY_SUPPORT },
            { medicalUnlocked() },
            { action -> reveal(action) },
            { if (inForeground && page == Page.EMERGENCY_SUPPORT) requestRender() },
            { go(Page.SESSION) },
            { legacy("manual") },
            { checkpoint ->
                engine.restore(
                    EmergencySession(
                        checkpoint.call.incidentId,
                        ConnectionTarget.EMERGENCY_119,
                        "D01",
                        status = LinkStatus.DEMO_CONNECTED,
                        driving = DrivingEmergencyState(reconfirm = true),
                    ),
                    null,
                )
                emergencySupport.restore(checkpoint)
            },
            {
                if (emergencySupport.scenario == null) {
                    healthForIncident = emergencySupport.snapshot?.incidentId
                    go(Page.MY_HEALTH)
                }
            },
            {
                val selected =
                    emergencyHealth.profileId(
                        emergencySupport.snapshot?.incidentId.orEmpty(),
                        medicalUnlocked(),
                        engine.restrictions.complexBlocked,
                    )
                if (selected == null || emergencySupport.scenario != null) null
                else
                    runCatching {
                            personalHealth
                                .records()
                                .firstOrNull { it.id == selected }
                                ?.let(EmergencyHealthSummary::build)
                        }
                        .getOrNull()
            },
            {
                emergencyHealth.clear()
                healthForIncident = null
            },
        )
    }

    private fun openEmergencySupport(voice: Boolean = false, demo: Boolean = false) {
        engine.current?.let { emergencySupport.attach(it.id) }
        emergencySupport.foreground(inForeground)
        emergencySupportUi.open()
        go(Page.EMERGENCY_SUPPORT)
        if (demo) emergencySupportUi.startDemo() else if (voice) emergencySupportUi.beginVoice()
    }

    private var previewWidthDp = 0
    private val cooperationTick =
        object : Runnable {
            override fun run() {
                if (inForeground) {
                    engine.expireVehicleContext(SystemClock.elapsedRealtime())
                    if (page == Page.POWER) updatePowerResult()
                    if (page == Page.VEHICLE_CONTEXT) updateVehicleAge()
                    if (renderedMedicalUnlocked && !medicalUnlocked()) {
                        emergencyHealth.clear()
                        healthForIncident = null
                        inAction = true
                        engine.lockPrivate()
                        emergencySupport.expireMedicalAuthentication()
                        emergencySupportUi.medicalAuthenticationExpired()
                        inAction = false
                        requestRender()
                    }
                    val before = engine.cooperation.snapshot
                    inAction = true
                    engine.cooperation.tick()
                    inAction = false
                    val after = engine.cooperation.snapshot
                    if (page == Page.COOPERATION) {
                        if (
                            before?.state != after?.state ||
                                before?.beaconState != after?.beaconState
                        )
                            requestRender()
                        else {
                            host.findViewById<TextView>(R.id.x_coop_time)?.text =
                                host.getString(
                                    R.string.coop_time,
                                    after?.missionRemaining ?: 0,
                                    engine.cooperation.elapsedSeconds,
                                )
                            host.findViewById<TextView>(R.id.x_coop_beacon_time)?.text =
                                host.getString(
                                    R.string.coop_beacon_time,
                                    after?.beaconRemaining ?: 0,
                                )
                        }
                        host.findViewById<TextView>(R.id.x_coop_preview)?.let { v ->
                            val visible = engine.cooperation.canDisplay()
                            v.visibility = if (visible) View.VISIBLE else View.GONE
                            v.setBackgroundColor(
                                if (
                                    visible &&
                                        android.os.SystemClock.elapsedRealtime() / 500 % 2 == 0L
                                )
                                    Color.rgb(151, 36, 25)
                                else Color.rgb(78, 33, 29)
                            )
                        }
                    }
                }
                main.postDelayed(this, 500)
            }
        }
    var requestRender: () -> Unit = { render() }

    init {
        if (saved != null)
            runCatching {
                val o = JSONObject(saved)
                engine.restore(
                    ExperienceStateCodec.decode(
                        o.optString("session").takeIf { it.isNotEmpty() },
                        catalog,
                        engine.profiles,
                    ),
                    o.optString("driver"),
                )
                page = runCatching { Page.valueOf(o.getString("page")) }.getOrDefault(Page.HOME)
                if (page in setOf(Page.EDIT, Page.CARD)) page = Page.PROFILES
                if (page in setOf(Page.HEALTH_LINK, Page.HEALTH_EXAMPLE, Page.MY_HEALTH_EDIT))
                    page = Page.MY_HEALTH
                if (
                    engine.current == null &&
                        page !in
                            setOf(
                                Page.HOME,
                                Page.EMERGENCY,
                                Page.CONSULT,
                                Page.MORE,
                                Page.TOOLS,
                                Page.PROFILES,
                                Page.MY_HEALTH,
                                Page.MY_HEALTH_EDIT,
                                Page.GALLERY,
                                Page.POWER,
                                Page.ACCESS,
                                Page.DRIVER,
                                Page.AGENCY,
                                Page.AI_PREFERENCES,
                            )
                )
                    page = Page.HOME
                pcPostedId = o.optString("pcPostedId").takeIf { it == engine.current?.id }
            }
        if (saved == null && voiceAiPreferences.consent.needsChoice) page = Page.AI_PREFERENCES
        engine.onChange = { if (!inAction) requestRender() }
        privacy.onStateChange = {
            if (!inAction && !host.isDestroyed && !host.isFinishing) requestRender()
        }
        scheduleAnswer()
        main.post(cooperationTick)
    }

    fun save() =
        JSONObject()
            .put("session", ExperienceStateCodec.encodePrivacySafe(engine.current))
            .put(
                "page",
                if (page in setOf(Page.HEALTH_LINK, Page.HEALTH_EXAMPLE, Page.MY_HEALTH_EDIT))
                    Page.MY_HEALTH.name
                else page.name,
            )
            .put("pcPostedId", pcPostedId)
            .toString()

    fun release() {
        healthDraft.clear()
        main.removeCallbacksAndMessages(null)
        vehicleContext.close()
        emergencySupport.close()
        emergencySupportUi.close()
        privacy.release()
        bridge.release()
    }

    private fun suspendUi() {
        emergencyHealth.clear()
        healthForIncident = null
        safeFault = SafeCallFault.NONE
        vehicleContext.background()
        emergencySupport.foreground(false)
        emergencySupportUi.suspend()
        vehicleConsent = false
        powerSocReading = powerSocReading?.copy(status = DiagnosticStatus.UNAVAILABLE, value = null)
        healthLinkExample.revoke()
        privacy.background()
        engine.background()
    }

    private fun resumeUi() {
        if (inForeground) {
            engine.foreground()
            emergencySupport.foreground(true)
            emergencySupportUi.foreground()
            deferredMedicalAction?.let {
                deferredMedicalAction = null
                it()
            }
        }
    }

    fun background() {
        presence.foreground = false
        suspendUi()
    }

    fun foreground() {
        presence.foreground = true
        resumeUi()
    }

    fun visible(value: Boolean) {
        presence.visible = value
        if (inForeground) resumeUi() else suspendUi()
    }

    fun nativeMedicalResult(request: Int, result: Int) = privacy.nativeResult(request, result)

    fun microphoneResult(granted: Boolean) = emergencySupportUi.microphoneResult(granted)

    private fun medicalUnlocked() = engine.privateUnlocked && privacy.authorized

    private fun personLabel() =
        MedicalPrivacyPolicy.safeAlias(
            medicalUnlocked(),
            engine.person()?.let(engine::alias),
            engine.person() != null,
        )

    private fun lockMedical() {
        emergencyHealth.clear()
        healthForIncident = null
        privacy.lock()
        deferredMedicalAction = null
        emergencySupport.lockPrivateObservations()
        emergencySupportUi.suspend()
        engine.lockPrivate()
    }

    private fun pauseCooperationPreview() {
        val old = inAction
        inAction = true
        engine.cooperation.pausePreview()
        inAction = old
    }

    fun home() {
        healthDraft.clear()
        healthRecordId = "SELF"
        healthLinkExample.revoke()
        pauseCooperationPreview()
        inAction = true
        lockMedical()
        inAction = false
        message = ""
        page = Page.HOME
        requestRender()
    }

    fun emergency() {
        go(Page.EMERGENCY)
    }

    fun restrictions(value: DrivingRestrictions) {
        if (platform == value) return
        platform = value
        engine.setRestrictions(platform.withTestRestriction(extraRestriction))
        emergencySupportUi.onStateChanged()
    }

    fun effectiveRestrictions() = engine.restrictions

    fun back(): Boolean {
        if (page == Page.HOME) return false
        if (page == Page.EMERGENCY_SUPPORT && emergencySupportUi.back()) {
            requestRender()
            return true
        }
        if (page == Page.MY_HEALTH_EDIT) healthDraft.clear()
        if (page in setOf(Page.HEALTH_LINK, Page.HEALTH_EXAMPLE)) healthLinkExample.revoke()
        if (page == Page.COOPERATION) pauseCooperationPreview()
        page =
            when (page) {
                Page.EMERGENCY,
                Page.CONSULT,
                Page.MORE,
                Page.PROFILES,
                Page.MY_HEALTH,
                Page.FINISH -> Page.HOME
                Page.AGENCY -> Page.MORE
                Page.MY_HEALTH_EDIT,
                Page.HEALTH_LINK -> Page.MY_HEALTH
                Page.HEALTH_EXAMPLE -> Page.HEALTH_LINK
                Page.GALLERY,
                Page.DRIVER -> Page.TOOLS
                Page.EDIT -> Page.PROFILES
                Page.ACCESS,
                Page.POWER,
                Page.TOOLS -> Page.MORE
                Page.AI_PREFERENCES -> Page.HOME
                Page.VEHICLE_CONTEXT ->
                    if (engine.current?.active == true) Page.SESSION else Page.MORE
                Page.SESSION -> Page.HOME
                else -> if (engine.current?.active == true) Page.SESSION else Page.HOME
            }
        if (page == Page.HOME) {
            home()
            return true
        }
        message = ""
        requestRender()
        return true
    }

    private fun go(p: Page) {
        if (page == Page.EMERGENCY_SUPPORT && p != page) emergencySupportUi.suspend()
        if (p in protectedPages && !medicalUnlocked() && !engine.restrictions.complexBlocked) {
            reveal { go(p) }
            return
        }
        if (page == Page.MY_HEALTH_EDIT && p != page) healthDraft.clear()
        if (
            page in setOf(Page.HEALTH_LINK, Page.HEALTH_EXAMPLE) &&
                p !in setOf(Page.HEALTH_LINK, Page.HEALTH_EXAMPLE)
        )
            healthLinkExample.revoke()
        if (page == Page.COOPERATION && p != page) pauseCooperationPreview()
        message = ""
        page = p
        if (p == Page.CARD)
            shareDraft =
                engine.current
                    ?.shared
                    ?.keys
                    .orEmpty()
                    .filter { it !in setOf(ShareField.VIDEO, ShareField.TEXT, ShareField.VEHICLE) }
                    .toMutableSet()
        requestRender()
    }

    private fun mutate(action: () -> Unit) {
        inAction = true
        message = ""
        runCatching(action).onFailure {
            message =
                it.message?.takeUnless { m -> m in setOf("Failed requirement.", "Check failed.") }
                    ?: "선택 정보와 현재 단계를 확인하고 다시 시도하세요."
        }
        inAction = false
        if (engine.current?.ended == true && emergencySupport.snapshot != null) {
            emergencySupportUi.clear()
            emergencySupport.clear()
        }
        if (engine.current?.ended == true && pcPostedId == engine.current?.id) revokePc()
        requestRender()
    }

    private fun scheduleAnswer() {
        val id = engine.current?.takeIf { it.status == LinkStatus.REQUESTING }?.id ?: return
        main.postDelayed(
            {
                if (engine.current?.id == id && engine.current?.status == LinkStatus.REQUESTING)
                    mutate { engine.answer() }
            },
            900,
        )
    }

    private fun start(target: ConnectionTarget, spec: ScenarioSpec? = null) {
        healthLinkExample.revoke()
        privacy.lock()
        if (pcPostedId != null) revokePc()
        pcConsent = false
        bridgeMessage = "PC 전송 전 · 앱 내 상황실에서 확인할 수 있습니다."
        mutate {
            engine.current?.takeIf { !it.ended }?.let { engine.finish(true) }
            engine.start(calls.request(target), target, spec?.id ?: "E04", spec?.profileHint)
            page = Page.SESSION
        }
        if (target in setOf(ConnectionTarget.EMERGENCY_119, ConnectionTarget.CONSULT_119)) {
            emergencySupport.attach(requireNotNull(engine.current).id)
            emergencySupport.foreground(inForeground)
        }
        scheduleAnswer()
    }

    private fun startDriving(trigger: DrivingTrigger = DrivingTrigger.TOUCH) {
        if (engine.current?.scenarioId == "D01" && engine.current?.ended == false) {
            go(Page.SESSION)
            return
        }
        healthLinkExample.revoke()
        if (trigger == DrivingTrigger.VOICE_EXAMPLE && engine.restrictions.complexBlocked) {
            message = "플랫폼 제한 중에는 음성 요청 예시 설정을 사용할 수 없습니다."
            requestRender()
            return
        }
        privacy.lock()
        if (pcPostedId != null) revokePc()
        pcConsent = false
        mutate {
            engine.current?.takeIf { !it.ended }?.let { engine.finish(true) }
            engine.startDriving(calls.request(ConnectionTarget.EMERGENCY_119), trigger)
            page = Page.SESSION
        }
        emergencySupport.attach(requireNotNull(engine.current).id)
        emergencySupport.foreground(inForeground)
        scheduleAnswer()
    }

    private fun dp(x: Int) = (x * host.resources.displayMetrics.density).toInt()

    private fun size(sp: Float) =
        sp *
            (if (largeText || medicalUnlocked() && engine.person()?.access?.largeText == true) 1.18f
            else 1f)

    private fun col() = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }

    private fun LinearLayout.add(v: View, top: Int = 0) {
        addView(
            v,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin =
                    dp(LifeLinkUiStyle.spacing(if (top > 0 && v is Button) maxOf(24, top) else top))
            },
        )
    }

    private fun text(
        value: String,
        sp: Float = 22f,
        color: Int = ink,
        bold: Boolean = false,
        id: Int = View.NO_ID,
    ) =
        TextView(host).apply {
            this.id = id
            text = value
            textSize = size(sp)
            setTextColor(color)
            setLineSpacing(dp(4).toFloat(), 1f)
            typeface = LifeLinkUiStyle.typeface(bold)
            fontFeatureSettings = "tnum"
            if (bold) isAccessibilityHeading = true
        }

    private fun shape(color: Int) = LifeLinkUiStyle.shape(host, color)

    private fun interactive(color: Int) = LifeLinkUiStyle.actionBackground(host, color)

    private fun button(
        label: String,
        id: Int = View.NO_ID,
        primary: Boolean = true,
        enabled: Boolean = true,
        action: () -> Unit,
    ) =
        Button(host).apply {
            this.id = id
            text = label
            contentDescription = label
            textSize = size(LifeLinkUiStyle.actionTextSize)
            isAllCaps = false
            minHeight = dp(LifeLinkUiStyle.touchHeight)
            minimumHeight = minHeight
            minWidth = dp(LifeLinkUiStyle.touchHeight)
            gravity = Gravity.CENTER
            typeface = LifeLinkUiStyle.typeface(true)
            stateListAnimator = null
            elevation = 0f
            val emergency119 =
                id in setOf(R.id.x_emergency, R.id.x_driving_quick, R.id.x_119, R.id.x_medical)
            val emergency112 = id in setOf(R.id.x_home_112, R.id.x_112)
            val fill =
                when {
                    emergency119 -> LifeLinkUiStyle.emergencyFill
                    emergency112 -> LifeLinkUiStyle.policeFill
                    primary -> LifeLinkUiStyle.primaryFill
                    else -> LifeLinkUiStyle.control
                }
            backgroundTintList = null
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setTextColor(LifeLinkUiStyle.actionText(LifeLinkUiStyle.actionForeground(fill)))
            background = interactive(fill)
            isEnabled = enabled
            setFilterTouchesWhenObscured(true)
            setOnClickListener {
                if (
                    page in protectedPages &&
                        !medicalUnlocked() &&
                        id !in
                            setOf(
                                R.id.x_home,
                                R.id.x_back,
                                R.id.x_urgent_shortcut,
                                R.id.x_driving_quick,
                                R.id.privacy_unlock,
                                R.id.privacy_lock,
                                R.id.x_revoke,
                            )
                )
                    reveal { action() }
                else action()
            }
        }

    private fun homeCard(
        title: String,
        subtitle: String,
        id: Int,
        urgent: Boolean = false,
        action: () -> Unit,
    ) =
        button("$title\n$subtitle", id, false, action = action).apply {
            val label = "$title\n$subtitle"
            val compact =
                availableWidthDp() < 760 || host.resources.configuration.screenHeightDp < 720
            val number = title in setOf("119", "112", "109")
            val titleSize = if (compact) if (number) 44f else 32f else if (number) 64f else 40f
            text =
                SpannableString(label).apply {
                    setSpan(
                        TypefaceSpan(LifeLinkUiStyle.typeface(true)),
                        0,
                        title.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    setSpan(
                        TypefaceSpan(LifeLinkUiStyle.typeface()),
                        title.length + 1,
                        label.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    setSpan(
                        RelativeSizeSpan((if (compact) 24f else 28f) / titleSize),
                        title.length + 1,
                        label.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            contentDescription = label
            gravity = Gravity.CENTER
            textSize = size(titleSize)
            setLineSpacing(dp(8).toFloat(), 1f)
            val cardHeight =
                when {
                    shortLandscape() ->
                        LifeLinkUiStyle.spacing(
                            (host.resources.configuration.screenHeightDp - 300).coerceIn(160, 220)
                        )
                    availableWidthDp() < 760 -> 160
                    else ->
                        LifeLinkUiStyle.spacing(
                            ((host.resources.configuration.screenHeightDp - 320) / 2).coerceIn(
                                144,
                                300,
                            )
                        )
                }
            setPadding(dp(24), dp(16), dp(24), dp(16))
            minHeight = dp(cardHeight)
            minimumHeight = minHeight
            val fill =
                when {
                    urgent -> LifeLinkUiStyle.emergencyFill
                    id in setOf(R.id.x_112, R.id.x_home_112) -> LifeLinkUiStyle.policeFill
                    else -> LifeLinkUiStyle.control
                }
            setTextColor(LifeLinkUiStyle.actionText(LifeLinkUiStyle.actionForeground(fill)))
            background = LifeLinkUiStyle.actionBackground(host, fill, LifeLinkUiStyle.panelRadius)
        }

    private fun panel(content: LinearLayout.() -> Unit) =
        col().apply {
            background = shape(surface)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            content()
        }

    private fun check(
        label: String,
        checked: Boolean,
        id: Int = View.NO_ID,
        enabled: Boolean = true,
        changed: (Boolean) -> Unit,
    ) =
        CheckBox(host).apply {
            this.id = id
            text = label
            textSize = size(24f)
            typeface = LifeLinkUiStyle.typeface()
            setTextColor(ink)
            minHeight = dp(LifeLinkUiStyle.touchHeight)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, v -> changed(v) }
        }

    private fun pickerAdapter(labels: List<String>) =
        object : ArrayAdapter<String>(host, android.R.layout.simple_spinner_dropdown_item, labels) {
            private fun style(view: View) =
                view.apply {
                    minimumHeight = dp(LifeLinkUiStyle.touchHeight)
                    setPadding(dp(24), dp(16), dp(24), dp(16))
                    if (this is TextView) {
                        textSize = size(24f)
                        setTextColor(ink)
                        typeface = LifeLinkUiStyle.typeface()
                        gravity = Gravity.CENTER_VERTICAL
                    }
                }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getView(position, convertView, parent))

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View = style(super.getDropDownView(position, convertView, parent))
        }

    private fun availableWidthDp() =
        if (previewWidthDp > 0) minOf(previewWidthDp, host.resources.configuration.screenWidthDp)
        else host.resources.configuration.screenWidthDp

    private fun shortLandscape() =
        availableWidthDp() >= 900 && host.resources.configuration.screenHeightDp < 700

    private fun row(left: View, right: View) =
        LinearLayout(host).apply {
            val wide = availableWidthDp() >= 760
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            isBaselineAligned = false
            addView(
                left,
                LinearLayout.LayoutParams(
                    if (wide) 0 else -1,
                    if (wide) -1 else -2,
                    if (wide) 1f else 0f,
                ),
            )
            addView(
                right,
                LinearLayout.LayoutParams(
                        if (wide) 0 else -1,
                        if (wide) -1 else -2,
                        if (wide) 1f else 0f,
                    )
                    .apply { if (wide) marginStart = dp(24) else topMargin = dp(24) },
            )
        }

    private fun LinearLayout.actionGrid(actions: List<View>) {
        actions.chunked(2).forEach { pair ->
            add(if (pair.size == 2) row(pair[0], pair[1]) else pair[0], 24)
        }
    }

    private fun LinearLayout.primaryGrid(actions: List<View>) {
        if (!shortLandscape()) {
            actionGrid(actions)
            return
        }
        add(
            LinearLayout(host).apply {
                isBaselineAligned = false
                actions.forEachIndexed { index, view ->
                    addView(
                        view,
                        LinearLayout.LayoutParams(0, -1, 1f).apply {
                            if (index > 0) marginStart = dp(24)
                        },
                    )
                }
            },
            24,
        )
    }

    private fun navButton(label: String, id: Int, action: () -> Unit) =
        button(label, id, false, action = action).apply {
            textSize = size(24f)
            if (id != R.id.x_driving_quick) {
                background = LifeLinkUiStyle.actionBackground(host, surface, 20)
                setTextColor(LifeLinkUiStyle.actionText(ink))
            }
            minHeight = dp(80)
            minimumHeight = minHeight
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

    private fun shell(title: String, content: LinearLayout.() -> Unit) {
        if (
            page in protectedPages ||
                medicalUnlocked() ||
                privacy.prompting ||
                (page == Page.EMERGENCY_SUPPORT && emergencySupport.scenario == null)
        )
            host.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else host.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val same = lastPage == page
        val previous = if (same) host.findViewById<ScrollView>(R.id.x_scroll)?.scrollY ?: 0 else 0
        val root = col().apply { setBackgroundColor(bg) }
        val compact =
            availableWidthDp() < 760 || largeText || host.resources.configuration.fontScale > 1.2f
        root.add(
            text("시연 모드 | 실제 신고·상담 연결 아님", 22f, warning, false, R.id.safety_banner).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setBackgroundColor(LifeLinkUiStyle.notice)
            }
        )
        if (previewWidthDp > 0)
            root.add(
                text("화면 너비 ${availableWidthDp()}dp 미리보기 · 실제 차량 창과 별개", 14f, muted).apply {
                    gravity = Gravity.CENTER
                }
            )
        root.add(
            LinearLayout(host).apply {
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                setPadding(dp(24), dp(12), dp(24), dp(12))
                setBackgroundColor(LifeLinkUiStyle.chrome)
                if (page !in setOf(Page.HOME, Page.SESSION) && !compact)
                    addView(
                        navButton("이전", R.id.x_back) { back() },
                        LinearLayout.LayoutParams(dp(120), -2).apply { marginEnd = dp(24) },
                    )
                addView(
                    text("LifeLink 119", 28f, accent, true),
                    LinearLayout.LayoutParams(0, -2, 1f),
                )
                if (page != Page.HOME) {
                    if (!compact)
                        addView(
                            navButton("119 요청", R.id.x_driving_quick) { startDriving() },
                            LinearLayout.LayoutParams(dp(184), -2).apply { marginStart = dp(24) },
                        )
                    addView(
                        navButton("홈", R.id.x_home) { home() },
                        LinearLayout.LayoutParams(dp(120), -2).apply { marginStart = dp(24) },
                    )
                } else
                    addView(
                        navButton("더보기", R.id.x_more) { go(Page.MORE) },
                        LinearLayout.LayoutParams(dp(144), -2),
                    )
            }
        )
        if (medicalUnlocked())
            root.add(button("의료정보 다시 잠금", R.id.privacy_lock, false) { lockMedical() }, 4)
        if (compact && page != Page.HOME)
            root.add(
                LinearLayout(host).apply {
                    setPadding(dp(24), dp(8), dp(24), dp(8))
                    addView(
                        navButton("이전", R.id.x_back) { back() },
                        LinearLayout.LayoutParams(0, -2, 1f),
                    )
                    addView(
                        navButton("119 요청", R.id.x_driving_quick) { startDriving() },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(24) },
                    )
                }
            )
        val body =
            col().apply {
                val shortPrimary =
                    shortLandscape() &&
                        page in setOf(Page.HOME, Page.SESSION, Page.CONSULT, Page.EMERGENCY)
                setPadding(
                    dp(24),
                    dp(if (shortPrimary) 16 else 24),
                    dp(24),
                    dp(if (shortPrimary) 24 else 32),
                )
                if (title.isNotEmpty()) add(text(title, 32f, ink, true), 4)
                if (message.isNotBlank())
                    add(
                        text(message, 20f, warning, false, R.id.x_message).apply {
                            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                        },
                        10,
                    )
                if (
                    engine.restrictions.known &&
                        (engine.restrictions.requiresOptimization ||
                            engine.restrictions.noVideo ||
                            engine.restrictions.noSetup)
                )
                    add(text("차량 제한 적용 중 · 실제 긴급신고는 기기의 표준 긴급통화 기능을 이용하세요.", 17f, warning), 8)
                content()
            }
        val scroll =
            ScrollView(host).apply {
                id = R.id.x_scroll
                isFillViewport = true
                val centered = page in setOf(Page.HOME, Page.CONSULT, Page.EMERGENCY, Page.SESSION)
                addView(
                    FrameLayout(host).apply {
                        addView(
                            body,
                            FrameLayout.LayoutParams(
                                dp(minOf(LifeLinkUiStyle.contentWidth, availableWidthDp())),
                                -2,
                                (if (centered) Gravity.CENTER_VERTICAL else Gravity.TOP) or
                                    Gravity.CENTER_HORIZONTAL,
                            ),
                        )
                    }
                )
            }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (previewWidthDp > 0)
            host.setContentView(
                FrameLayout(host).apply {
                    setBackgroundColor(Color.BLACK)
                    addView(
                        root,
                        FrameLayout.LayoutParams(dp(availableWidthDp()), -1, Gravity.CENTER),
                    )
                }
            )
        else host.setContentView(root)
        lastPage = page
        if (same) scroll.post { scroll.scrollTo(0, previous) }
    }

    fun render() {
        renderedMedicalUnlocked = medicalUnlocked()
        if (page != Page.VEHICLE_CONTEXT) vehicleConsent = false
        vehicleContext.visible(
            inForeground && page == Page.VEHICLE_CONTEXT && !engine.restrictions.complexBlocked
        )
        if (!medicalUnlocked() && page in setOf(Page.HEALTH_LINK, Page.HEALTH_EXAMPLE))
            healthLinkExample.revoke()
        if (page == Page.HEALTH_EXAMPLE && !healthLinkExample.canShowExample())
            page = Page.HEALTH_LINK
        if (
            engine.restrictions.complexBlocked &&
                page in
                    setOf(
                        Page.PERSON,
                        Page.PROFILES,
                        Page.EDIT,
                        Page.MY_HEALTH,
                        Page.MY_HEALTH_EDIT,
                        Page.HEALTH_LINK,
                        Page.HEALTH_EXAMPLE,
                        Page.CARD,
                        Page.SUMMARY,
                        Page.POWER,
                        Page.DRIVER,
                        Page.ACCESS,
                        Page.COOPERATION,
                        Page.DRIVING_DETAILS,
                        Page.VEHICLE_CONTEXT,
                        Page.SAFE,
                        Page.AGENCY,
                        Page.AI_PREFERENCES,
                    )
        ) {
            shell("상세 조작 제한") {
                add(
                    text(
                        "플랫폼이 상세 정보·설정을 제한하고 있습니다. 안전하게 이용할 수 있을 때 다시 열어주세요. 긴급 연결 시연은 상단에서 선택할 수 있습니다.",
                        24f,
                        warning,
                    ),
                    18,
                )
            }
            return
        }
        if (page in protectedPages && !medicalUnlocked()) {
            shell("의료정보가 잠겨 있습니다") {
                add(
                    text(
                        "가족·의료정보는 인증한 사람만 열 수 있습니다. 긴급 연결 시연은 잠금을 풀지 않아도 계속 이용할 수 있습니다.",
                        23f,
                        warning,
                        false,
                        R.id.privacy_state,
                    ),
                    18,
                )
                add(button("인증하고 정보 열기", R.id.privacy_unlock) { reveal {} }, 18)
                add(button("잠금 설정·정보 삭제", R.id.privacy_settings, false) { go(Page.SECURITY) }, 12)
            }
            return
        }
        when (page) {
            Page.HOME -> homePage()
            Page.EMERGENCY -> entry(true)
            Page.CONSULT -> entry(false)
            Page.SESSION -> if (engine.current?.driving != null) drivingPage() else sessionPage()
            Page.PERSON -> personPage()
            Page.GALLERY -> gallery()
            Page.PROFILES -> profilesPage()
            Page.EDIT -> editProfile()
            Page.MY_HEALTH -> personalHealthPage()
            Page.MY_HEALTH_EDIT -> personalHealthEdit()
            Page.HEALTH_LINK -> healthLinkPage()
            Page.HEALTH_EXAMPLE -> healthExamplePage()
            Page.CARD -> cardPage()
            Page.MORE -> morePage()
            Page.SUPPORT -> supportPage()
            Page.TEXT -> textPage()
            Page.SIGN -> signPage()
            Page.VIDEO -> videoPage()
            Page.GUIDED_VIDEO -> guidedVideoPage()
            Page.LOCATION -> locationPage()
            Page.SUMMARY -> summaryPage()
            Page.CENTER -> centerPage()
            Page.POLICE -> policePage()
            Page.NAV -> navigationPage()
            Page.SAFE -> safePage()
            Page.AGENCY -> agencyPage()
            Page.ACCESS -> accessPage()
            Page.POWER -> powerPage()
            Page.DRIVER -> driverPage()
            Page.TOOLS -> toolsPage()
            Page.FINISH -> finishPage()
            Page.VOICE -> voicePage()
            Page.EASY -> easyPage()
            Page.COOPERATION -> cooperationPage()
            Page.SECURITY -> securityPage()
            Page.DRIVING_SETUP -> drivingSetup()
            Page.DRIVING_DETAILS -> drivingDetails()
            Page.VEHICLE_CONTEXT -> vehicleContextPage()
            Page.EMERGENCY_SUPPORT -> shell("") { add(emergencySupportUi.view(), 12) }
            Page.AI_PREFERENCES -> voiceAiSettings()
        }
    }

    private fun homePage() =
        shell("") {
            add(
                assistanceUi()
                    .home(
                        emergency = { startDriving() },
                        medical = { start(ConnectionTarget.CONSULT_119) },
                        counsel = { start(ConnectionTarget.COUNSEL_109) },
                        police = { start(ConnectionTarget.EMERGENCY_112) },
                        family = { go(Page.MY_HEALTH) },
                    )
            )
        }

    private fun assistanceUi() =
        AssistanceUi(host, minOf(LifeLinkUiStyle.contentWidth, availableWidthDp()) - 48, largeText)

    private fun sharingSummary(s: EmergencySession): String {
        val count = s.shared.size + if (engine.vehicleContextShare != null) 1 else 0
        return if (count == 0) "위치·가족정보 기본 비공유" else "공유 중인 항목 ${count}개 · 종료 시 정리"
    }

    private fun entry(urgent: Boolean) =
        shell(if (urgent) "어디로 연결할까요?" else "어떤 상담이 필요하세요?") {
            val targets =
                if (urgent) listOf(ConnectionTarget.EMERGENCY_119, ConnectionTarget.EMERGENCY_112)
                else listOf(ConnectionTarget.CONSULT_119, ConnectionTarget.COUNSEL_109)
            val ids =
                if (urgent) listOf(R.id.x_119, R.id.x_112) else listOf(R.id.x_medical, R.id.x_109)
            add(
                row(
                    homeCard("119", if (urgent) "구조·구급 요청" else "응급의료상담", ids[0], true) {
                        start(targets[0])
                    },
                    homeCard(
                        if (urgent) "112" else "109",
                        if (urgent) "긴급신고" else "심리위기 상담",
                        ids[1],
                    ) {
                        start(targets[1])
                    },
                ),
                24,
            )
        }

    private fun drivingSetup() =
        shell("주행 중 긴급 호출 시연") {
            add(text("공모전용 가상 장면입니다. 실차 없이 에뮬레이터에서 전체 과정을 연습합니다.", 22f, muted), 12)
            add(
                text(
                    "상단 119 빠른 호출은 정차·PIN·위치 확인을 기다리지 않습니다. 화면의 ‘주행 중’은 가상 상태이며 실제 차량 상태를 변경하지 않습니다.",
                    22f,
                ),
                16,
            )
            add(button("버튼으로 119 호출 시연", R.id.x_driving_touch) { startDriving() }, 20)
            if (!engine.restrictions.complexBlocked) {
                add(
                    button("“차 안에 위험이 있어, 119 도와줘”\n음성 요청 예시 실행", R.id.x_driving_voice, false) {
                        startDriving(DrivingTrigger.VOICE_EXAMPLE)
                    },
                    12,
                )
                add(text("정해진 예시 버튼입니다. 마이크 녹음·음성 인식·전화 발신은 없습니다.", 19f, warning), 12)
            }
        }

    private fun drivingPage() =
        shell("") {
            val s = engine.current ?: return@shell
            val d = s.driving ?: return@shell
            add(
                assistanceUi()
                    .connection(
                        s.target,
                        status(s),
                        "조작보다 안전 확보를 우선하세요.",
                        sharingSummary(s),
                        d.safety.label.replace(" · 시연", ""),
                    ),
                16,
            )
            if (
                !s.ended &&
                    s.target in setOf(ConnectionTarget.EMERGENCY_119, ConnectionTarget.CONSULT_119)
            )
                add(
                    row(
                        button("AI 음성 동행 시작", R.id.x_ai_quick) {
                            openEmergencySupport(voice = true)
                        },
                        button("응급 요약 · 대기 지원", R.id.x_es_entry, false) { openEmergencySupport() },
                    ),
                    12,
                )
            if (s.status == LinkStatus.REQUESTING) {
                add(text("가상 상담원 응답 대기 중 · 정차·의료정보 인증 없이 요청했습니다.", 22f), 16)
                add(
                    button("호출 취소·정보 정리", R.id.x_end, false) {
                        mutate {
                            engine.finish(true)
                            page = Page.FINISH
                        }
                    },
                    16,
                )
                return@shell
            }
            if (s.status == LinkStatus.FAILED) {
                add(text("연결 실패 예시입니다. 실제 상황에서는 기기의 표준 긴급통화 기능을 이용하세요.", 22f, warning), 16)
                add(
                    button("같은 호출 다시 시도", R.id.x_retry) {
                        mutate { engine.retry() }
                        scheduleAnswer()
                    },
                    16,
                )
                add(
                    button("취소·정보 정리", R.id.x_end, false) {
                        mutate {
                            engine.finish(true)
                            page = Page.FINISH
                        }
                    },
                    12,
                )
                return@shell
            }
            if (s.ended) return@shell
            if (d.reconfirm)
                add(text("현재 안전 상태를 다시 확인하세요", 22f, warning, false, R.id.x_driving_reconfirm), 12)
            if (engine.restrictions.complexBlocked)
                add(
                    text("플랫폼 조작 제한 중 · 긴급 요청 유지", 22f, warning, false, R.id.x_driving_restricted),
                    16,
                )
            primaryGrid(
                listOf(
                    assistanceUi().action(
                        "상황·정보 전달",
                        R.id.x_driving_details,
                        R.drawable.ic_lifelink_arrow,
                        primary = true,
                        enabled = !engine.restrictions.complexBlocked,
                    ) {
                        go(Page.DRIVING_DETAILS)
                    },
                    assistanceUi().action(
                        "요청 종료",
                        R.id.x_end,
                        R.drawable.ic_lifelink_end,
                        end = true,
                    ) {
                        mutate {
                            engine.finish(true)
                            page = Page.FINISH
                        }
                    },
                    assistanceUi().action(
                        "영상 안내",
                        R.id.x_guided_video,
                        R.drawable.ic_lifelink_video,
                        enabled = !engine.restrictions.videoBlocked,
                    ) {
                        go(Page.GUIDED_VIDEO)
                    },
                    assistanceUi().action(
                        "추가 지원",
                        R.id.x_driving_extra,
                        R.drawable.ic_lifelink_family,
                    ) {
                        go(Page.SUPPORT)
                    },
                )
            )
        }

    private fun drivingDetails() =
        shell("상황·정보 전달") {
            val s = engine.current ?: return@shell
            val d = s.driving ?: return@shell
            if (s.ended) {
                add(text("종료된 요청입니다", 24f, muted), 16)
                return@shell
            }
            add(text(s.id, 18f, muted, false, R.id.x_ticket), 12)
            add(text(d.guidance, 24f), 16)
            add(text(d.trigger.label, 18f, muted, false, R.id.x_driving_trigger), 12)
            add(text("차 안의 위급 상황 · 구체적인 내용은 연결 후 설명", 24f, ink, true, R.id.x_driving_context), 16)
            add(
                panel {
                    add(text("가상 상황 전달", 24f, accent, true))
                    add(text(d.summary(), 20f), 12)
                    add(text(d.report.label, 24f, warning, true, R.id.x_driving_report), 12)
                },
                20,
            )
            if (d.report == DrivingReport.DRAFT)
                add(button("상황 전달", R.id.x_driving_send) { mutate { engine.drivingSend() } }, 12)
            if (d.report == DrivingReport.SENT)
                add(
                    button("상황실 역할 · 내용 확인", R.id.x_driving_ack) {
                        mutate { engine.drivingAcknowledge() }
                    },
                    12,
                )
            val sharedLabels =
                s.shared.keys.map { it.label } +
                    if (engine.vehicleContextShare != null) listOf("차량 관측 정보") else emptyList()
            add(
                text(
                    "공유 항목: " + sharedLabels.joinToString().ifEmpty { "없음 · 기본 비공유" },
                    20f,
                    muted,
                    false,
                    R.id.x_driving_shared,
                ),
                18,
            )
            if (d.report != DrivingReport.HANDED_OVER) {
                add(
                    button(
                        if (engine.locationAvailable) "가상 위치만 공유 · GPS 아님"
                        else "가상 위치 없음 · 호출은 계속 가능",
                        R.id.x_driving_location,
                        false,
                        engine.locationAvailable,
                    ) {
                        mutate { engine.share(setOf(ShareField.LOCATION)) }
                    },
                    12,
                )
                add(
                    button("차량 상태 조회·공유", R.id.x_driving_vehicle, false) {
                        go(Page.VEHICLE_CONTEXT)
                    },
                    12,
                )
                if (s.shared.isNotEmpty() || engine.vehicleContextShare != null)
                    add(
                        button("공유 철회", R.id.x_driving_revoke, false) {
                            mutate { engine.revoke() }
                        },
                        12,
                    )
            }
            if (d.safety == DrivingSafety.MOVING)
                add(
                    button("동승자 역할 · 안전한 곳에 정차했다고 알림", R.id.x_driving_stopped, false) {
                        mutate { engine.drivingStopped() }
                    },
                    20,
                )
            if (d.safety == DrivingSafety.STOPPED_REPORTED) {
                add(
                    text(
                        "정차 후에도 주변 교통·2차 사고 위험을 확인하세요. 안전한 대피 위치와 이동 방법은 현장 여건과 실제 상황실 안내에 따르세요.",
                        21f,
                        warning,
                    ),
                    18,
                )
                add(
                    button("동승자 역할 · 안전 확보했다고 알림", R.id.x_driving_safe, false) {
                        mutate { engine.drivingSafe() }
                    },
                    12,
                )
            }
            if (
                d.report == DrivingReport.ACKNOWLEDGED &&
                    d.safety == DrivingSafety.SAFE_PLACE_REPORTED
            )
                add(
                    button("상황실 역할 · 가상 대응팀 인계 확인", R.id.x_driving_handover) {
                        mutate { engine.drivingHandover() }
                    },
                    20,
                )
            if (d.report == DrivingReport.HANDED_OVER)
                add(
                    button("완료·공유정보 정리", R.id.x_driving_finish) {
                        mutate {
                            engine.finish()
                            page = Page.FINISH
                        }
                    },
                    20,
                )
            add(button("연결 화면으로", R.id.x_return_session, false) { go(Page.SESSION) }, 24)
            add(text("의료정보는 별도 인증·동의가 필요합니다", 20f, muted), 16)
        }

    private fun status(s: EmergencySession) =
        when (s.status) {
            LinkStatus.REQUESTING -> "연결 요청 중"
            LinkStatus.DEMO_CONNECTED -> "가상 상담원 응답"
            LinkStatus.SUPPORT_IN_PROGRESS -> "추가 지원 진행"
            LinkStatus.FAILED -> "연결 실패"
            LinkStatus.COMPLETED -> "종료 · 공유정보 정리됨"
            LinkStatus.CANCELLED -> "취소 · 공유정보 정리됨"
            else -> "대기"
        }

    private fun statusColor(s: EmergencySession) =
        when (s.status) {
            LinkStatus.FAILED -> warning
            LinkStatus.COMPLETED,
            LinkStatus.CANCELLED -> muted
            else -> accent
        }

    private fun sessionPage() =
        shell("") {
            val s = engine.current
            if (s == null) {
                add(button("긴급 연결 선택", R.id.x_119) { emergency() }, 16)
                return@shell
            }
            add(
                assistanceUi()
                    .connection(
                        s.target,
                        status(s),
                        if (s.target == ConnectionTarget.COUNSEL_109) "마음이 힘들 때, 109 상담 연결을 시연합니다."
                        else "선택한 기관의 상담 연결을 시연합니다.",
                        sharingSummary(s),
                    ),
                16,
            )
            if (
                !s.ended &&
                    s.target in setOf(ConnectionTarget.EMERGENCY_119, ConnectionTarget.CONSULT_119)
            )
                add(
                    row(
                        button("AI 음성 동행 시작", R.id.x_ai_quick) {
                            openEmergencySupport(voice = true)
                        },
                        button("응급 요약 · 대기 지원", R.id.x_es_entry, false) { openEmergencySupport() },
                    ),
                    12,
                )
            if (s.status == LinkStatus.REQUESTING) {
                add(
                    assistanceUi().action(
                        "연결 취소",
                        R.id.x_end,
                        R.drawable.ic_lifelink_end,
                        end = true,
                    ) {
                        mutate {
                            engine.finish(true)
                            page = Page.FINISH
                        }
                    },
                    20,
                )
                return@shell
            }
            if (s.status == LinkStatus.FAILED) {
                add(
                    button("다시 시도", R.id.x_retry) {
                        mutate { engine.retry() }
                        scheduleAnswer()
                    },
                    20,
                )
                add(
                    button("취소·정보 정리", R.id.x_end, false) {
                        mutate {
                            engine.finish(true)
                            page = Page.FINISH
                        }
                    },
                    12,
                )
                return@shell
            }
            if (s.ended) {
                add(button("종료 요약 보기", R.id.x_end) { go(Page.FINISH) }, 20)
                return@shell
            }
            val spec = catalog.scenario(s.scenarioId)
            val plan =
                ContextualActionResolver.resolve(
                    if (medicalUnlocked()) engine.person() else null,
                    spec,
                    s.target,
                    engine.restrictions,
                )
            val hasVideo =
                s.target in setOf(ConnectionTarget.CONSULT_119, ConnectionTarget.EMERGENCY_119)
            val mainAction =
                if (s.target == ConnectionTarget.COUNSEL_109 && SupportAction.TEXT in plan.primary)
                    SupportAction.TEXT
                else plan.primary.first()
            val actions = mutableListOf<View>()
            actions.add(
                assistanceUi().action(
                    mainAction.label,
                    R.id.x_primary_1,
                    R.drawable.ic_lifelink_arrow,
                    primary = true,
                ) {
                    action(mainAction)
                }
            )
            actions.add(
                assistanceUi().action("연결 종료", R.id.x_end, R.drawable.ic_lifelink_end, end = true) {
                    mutate {
                        engine.finish()
                        page = Page.FINISH
                    }
                }
            )
            if (hasVideo) {
                actions.add(
                    assistanceUi().action(
                        "영상 안내",
                        R.id.x_guided_video,
                        R.drawable.ic_lifelink_video,
                        enabled = !engine.restrictions.videoBlocked,
                    ) {
                        go(Page.GUIDED_VIDEO)
                    }
                )
            } else {
                plan.primary
                    .firstOrNull { it != mainAction }
                    ?.let { secondaryAction ->
                        actions.add(
                            assistanceUi().action(
                                secondaryAction.label,
                                R.id.x_primary_2,
                                R.drawable.ic_lifelink_shield,
                            ) {
                                action(secondaryAction)
                            }
                        )
                    }
            }
            actions.add(
                assistanceUi().action("추가 지원", R.id.x_support, R.drawable.ic_lifelink_family) {
                    go(Page.SUPPORT)
                }
            )
            primaryGrid(actions)
        }

    private fun action(a: SupportAction) {
        when (a) {
            SupportAction.CARD,
            SupportAction.GUARDIAN,
            SupportAction.MOBILITY,
            SupportAction.PRIVACY -> go(Page.CARD)
            SupportAction.VIDEO -> go(Page.VIDEO)
            SupportAction.LOCATION -> go(Page.LOCATION)
            SupportAction.VEHICLE -> go(Page.VEHICLE_CONTEXT)
            SupportAction.TEXT,
            SupportAction.PHRASES -> go(Page.TEXT)
            SupportAction.SIGN -> go(Page.SIGN)
            SupportAction.VOICE -> go(Page.VOICE)
            SupportAction.EASY -> go(Page.EASY)
            SupportAction.SUMMARY -> go(Page.SUMMARY)
            SupportAction.POLICE -> go(Page.POLICE)
            SupportAction.NAVIGATION -> go(Page.NAV)
            SupportAction.SAFE_CALL -> go(Page.SAFE)
            SupportAction.POWER -> go(Page.POWER)
            SupportAction.MANUAL -> legacy("manual")
            SupportAction.COOPERATION -> go(Page.COOPERATION)
        }
    }

    private fun supportPage() =
        shell("추가 지원") {
            val s = engine.current ?: return@shell
            add(button("가상 상황실", R.id.x_center) { go(Page.CENTER) }, 16)
            add(text(s.id, 18f, muted, false, R.id.x_ticket), 12)
            if (engine.restrictions.galleryBlocked) {
                add(text("플랫폼 제한 중에는 추가 기능 목록을 표시하지 않습니다.", 22f, warning), 16)
                return@shell
            }
            add(
                row(
                    button("도움받는 분 선택", R.id.x_person, false) { go(Page.PERSON) },
                    button("상황 바꾸기", R.id.x_change_case, false) {
                        changeContextOnly = true
                        go(Page.GALLERY)
                    },
                ),
                24,
            )
            val spec = catalog.scenario(s.scenarioId)
            add(text(spec.title, 26f, ink, true, R.id.x_case_title), 24)
            add(
                text(
                    if (easy || engine.person()?.access?.easy == true) "필요한 도움을 하나씩 선택하세요"
                    else spec.context,
                    22f,
                    muted,
                ),
                12,
            )
            val plan =
                ContextualActionResolver.resolve(
                    if (medicalUnlocked()) engine.person() else null,
                    spec,
                    s.target,
                    engine.restrictions,
                )
            actionGrid(
                (plan.primary + plan.additional).distinct().map { a ->
                    button(a.label, idForAction(a), false) { action(a) }
                }
            )
        }

    private fun personPage() =
        shell("누가 도움이 필요한가요?") {
            add(text("선택은 나중에 바꾸거나 건너뛸 수 있습니다. 변경하면 이전 개인 정보 공유는 철회합니다.", 20f, muted), 12)
            add(
                button("선택 안 함 · 미등록 승객", R.id.x_no_person) {
                    mutate {
                        engine.selectPerson(null)
                        page = Page.SESSION
                    }
                },
                16,
            )
            engine.profiles.forEach { p ->
                add(
                    button(engine.alias(p), ExperienceViewIds.person(p.id), false) {
                        mutate {
                            engine.selectPerson(p.id)
                            page = Page.SESSION
                        }
                    },
                    10,
                )
            }
        }

    private fun gallery() =
        shell("시나리오 갤러리 · 19개 가상 예시") {
            if (engine.restrictions.galleryBlocked) {
                add(text("플랫폼이 긴 목록·설정을 제한하고 있습니다. 긴급 연결 시연은 상단에서 선택할 수 있습니다.", 22f, warning), 16)
                return@shell
            }
            add(text("가상 인물을 포함한 예시입니다. 선택해도 의료정보가 자동 공유되지 않습니다.", 20f, muted), 10)
            catalog.scenarios.chunked(2).forEach { pair ->
                fun item(c: ScenarioSpec) =
                    button("${c.id} · ${c.title}", idForCase(c.id), false) {
                        if (c.id == "D01") startDriving()
                        else if (changeContextOnly && engine.current?.active == true) {
                            mutate {
                                engine.selectCase(c.id)
                                page = Page.SESSION
                            }
                        } else start(c.target, c)
                    }
                add(if (pair.size == 2) row(item(pair[0]), item(pair[1])) else item(pair[0]), 12)
            }
        }

    private fun idForCase(id: String) = ExperienceViewIds.scenario(id)

    private fun idForAction(action: SupportAction) = ExperienceViewIds.action(action)

    private fun morePage() =
        shell("더보기") {
            add(button("음성 AI 설정", R.id.x_ai_preferences, false) { go(Page.AI_PREFERENCES) }, 16)
            if (BuildConfig.DEMO_FEATURES && !engine.restrictions.complexBlocked)
                add(
                    button("음성 동행 자동 시연", R.id.x_ai_auto_demo) {
                        startDriving()
                        openEmergencySupport(demo = true)
                    },
                    16,
                )
            if (engine.current?.active == true)
                add(button("진행 중인 연결", R.id.x_resume) { go(Page.SESSION) }, 18)
            add(button("119 · 109 상담 선택", R.id.x_consult, false) { go(Page.CONSULT) }, 16)
            add(
                button(OfficialHealthSite.SAFE_CALL.label, R.id.x_safe_official, false) {
                    go(Page.AGENCY)
                },
                16,
            )
            add(button("차량 상태", R.id.x_vehicle_context, false) { go(Page.VEHICLE_CONTEXT) }, 18)
            add(
                row(
                    button("응급 매뉴얼", R.id.x_manual, false) { legacy("manual") },
                    button("EV 전력 계산", R.id.x_power, false) { go(Page.POWER) },
                ),
                24,
            )
            add(
                row(
                    button("설정·도구", R.id.x_tools, false) { go(Page.TOOLS) },
                    button("119·112 선택", R.id.x_urgent_shortcut, false) { emergency() },
                ),
                24,
            )
            if (engine.current?.active != true)
                add(button("접근성 설정", R.id.x_access, false) { go(Page.ACCESS) }, 12)
        }

    private fun voiceAiSettings() =
        shell("") {
            add(
                VoiceAiSetupUi(host, voiceAiPreferences) { external ->
                        voiceAiPreferences.choose(external)
                        emergencySupportUi.preferenceChanged()
                        home()
                        if (
                            external &&
                                host.checkSelfPermission(
                                    android.Manifest.permission.RECORD_AUDIO
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        )
                            host.requestPermissions(
                                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                8192,
                            )
                    }
                    .view(),
                12,
            )
        }

    private fun toolsPage() =
        shell("설정·도구") {
            if (engine.restrictions.galleryBlocked) {
                add(
                    text(
                        "주행 제한: ${engine.restrictions.source}\n${platform.detail}",
                        22f,
                        warning,
                        false,
                        R.id.x_restrictions,
                    ),
                    16,
                )
                add(text("플랫폼 제한 중에는 시연 도구 목록과 상세 설정을 표시하지 않습니다.", 22f), 16)
                if (extraRestriction && !platform.galleryBlocked)
                    add(
                        button("추가 제한 시연 종료", R.id.x_test_restriction) {
                            extraRestriction = false
                            engine.setRestrictions(platform)
                        },
                        16,
                    )
                return@shell
            }
            actionGrid(
                listOf(
                    button("상황별 체험", R.id.x_gallery) {
                        changeContextOnly = false
                        go(Page.GALLERY)
                    },
                    button("주행 중 도움 요청", R.id.x_driving_setup, false) { go(Page.DRIVING_SETUP) },
                    button("접근성 설정", R.id.x_access, false) { go(Page.ACCESS) },
                    button("의료정보 잠금·PIN", R.id.privacy_settings, false) { go(Page.SECURITY) },
                    button("운전자·디지털키 예시", R.id.x_driver, false) { go(Page.DRIVER) },
                    button(
                        if (previewWidthDp > 0) "기본 너비로" else "좁은 화면 미리보기",
                        R.id.x_narrow_preview,
                        false,
                    ) {
                        previewWidthDp = if (previewWidthDp > 0) 0 else 520
                        requestRender()
                    },
                    button("기존 흐름·상황실", R.id.x_legacy, false) { legacy("legacy") },
                    button("응급 매뉴얼", R.id.x_manual, false) { legacy("manual") },
                    button("주변 차량 협력", R.id.x_cooperation, false) { go(Page.COOPERATION) },
                )
            )
            add(
                panel {
                    add(text("연동 상태", 24f, accent, true))
                    add(
                        text(
                            "차량 상태: 도어·SoC·에어백 읽기(지원 기기)\n전화·문자·영상·위치·수어·내비·안심콜·디지털키: MOCK\n실제 기관 연결·차량 제어: NEEDS_PARTNERSHIP",
                            18f,
                            muted,
                        ),
                        12,
                    )
                    add(
                        text(
                            "주행 제한: ${engine.restrictions.source}\n${platform.detail}",
                            19f,
                            warning,
                            false,
                            R.id.x_restrictions,
                        ),
                        12,
                    )
                    add(
                        check("영상·상세 입력 추가 제한 시연", extraRestriction, R.id.x_test_restriction) { v ->
                            extraRestriction = v
                            engine.setRestrictions(platform.withTestRestriction(v))
                        },
                        12,
                    )
                    add(
                        check("다음 연결 실패 시연", engine.failNextConnection, R.id.x_fault_connection) {
                            engine.failNextConnection = it
                        },
                        8,
                    )
                    add(
                        check("가상 위치 사용 가능", engine.locationAvailable, R.id.x_fault_location) { v ->
                            engine.locationAvailable = v
                            if (!v && engine.current?.active == true)
                                mutate { engine.revoke(ShareField.LOCATION) }
                        },
                        8,
                    )
                },
                22,
            )
        }

    private fun accessPage() =
        shell("접근성 설정") {
            add(text("선호는 표시 순서를 바꿉니다. 기능을 이용하지 못하게 하는 조건이 아닙니다.", 21f, muted), 12)
            add(
                check("큰 글씨", largeText, R.id.x_large_text) {
                    largeText = it
                    repository.preferences(largeText, easy)
                    requestRender()
                },
                16,
            )
            add(
                check("쉬운 짧은 안내", easy, R.id.x_easy_text) {
                    easy = it
                    repository.preferences(largeText, easy)
                    requestRender()
                },
                12,
            )
            add(
                text(
                    "개인별 문자·수어·음성·이동 도움 선호는 가족 응급정보에서 가상 프로필별로 선택할 수 있습니다. 스크린리더는 시스템 설정을 사용합니다.",
                    21f,
                    muted,
                ),
                18,
            )
            add(button("우리 가족 응급정보", R.id.x_family, false) { go(Page.PROFILES) }, 18)
        }

    private fun finishPage() =
        shell("연결 종료") {
            add(text("공유정보를 정리했습니다", 30f, accent, true, R.id.x_finished), 18)
            add(text("이번 연결의 공유정보와 동의를 지웠습니다. 저장된 건강정보는 안심콜에서 관리하세요.", 24f), 16)
            add(button("처음으로", R.id.x_finish_home) { home() }, 24)
            add(
                button("다른 시나리오 연습", R.id.x_gallery, false) {
                    changeContextOnly = false
                    go(Page.GALLERY)
                },
                12,
            )
            if (pcPostedId != null) {
                add(text(bridgeMessage, 19f, warning), 16)
                add(
                    button("PC 게시 철회 다시 시도", R.id.x_pc_revoke, false, enabled = !pcBusy) {
                        revokePc()
                    },
                    12,
                )
            }
        }

    private fun reveal(next: () -> Unit) {
        if (engine.restrictions.complexBlocked) {
            message = "플랫폼 제한 중에는 상세 정보를 열 수 없습니다."
            requestRender()
            return
        }
        privacy.authenticate {
            val complete = {
                if (privacy.authorized && !engine.restrictions.complexBlocked)
                    mutate {
                        engine.unlockPrivate(true)
                        next()
                    }
            }
            if (inForeground) complete() else deferredMedicalAction = complete
        }
    }

    private fun securityPage() =
        shell("의료정보 잠금") {
            add(text(privacy.description, 24f, accent, true, R.id.privacy_state), 16)
            add(
                text(
                    "가족 선택·열람·편집·공유는 인증 뒤 가능합니다. 60초 후, 홈 복귀 또는 다른 앱으로 전환하면 다시 잠급니다. 의료정보 화면은 캡처·화면 공유를 제한합니다.",
                    22f,
                ),
                16,
            )
            add(
                button(if (medicalUnlocked()) "지금 잠그기" else "인증 상태 확인", R.id.privacy_unlock) {
                    if (medicalUnlocked()) lockMedical() else reveal {}
                },
                18,
            )
            add(text("PIN 초기화 시 이 기기에 저장한 내 건강정보와 가족정보도 삭제됩니다.", 20f, muted), 20)
            add(
                button("저장 의료정보 삭제·잠금 초기화", R.id.privacy_reset, false) {
                    AlertDialog.Builder(host)
                        .setTitle("저장 정보를 지우고 초기화할까요?")
                        .setMessage(
                            "이 기기에 저장한 내 건강정보와 가족정보, 현재 상담 공유를 삭제하고 앱 잠금을 초기화합니다. 차량 Android의 화면 잠금은 바꾸지 않습니다."
                        )
                        .setPositiveButton("정보 삭제·초기화") { _, _ ->
                            mutate {
                                engine.finish(true)
                                engine.restoreProfiles()
                                repository.eraseMedicalData()
                                personalHealth.erase()
                                EmergencyCheckpointStore(host).clear()
                                privacy.clearAfterMedicalDataErased()
                                page = Page.HOME
                            }
                        }
                        .setNegativeButton("취소", null)
                        .show()
                },
                16,
            )
        }

    private fun personalHealthPage() =
        shell("우리 가족 안심콜") {
            add(text("이 기기에 암호화 보관합니다. 공식 안심콜 등록·조회는 별도입니다.", 19f, muted), 10)
            add(
                text(
                    "사고 시 인증 후 이 화면에서 저장 정보를 확인할 수 있습니다. 신고 시에는 상황요원에게 필요한 내용을 알려주세요. 자동 전송은 지원하지 않습니다.",
                    20f,
                    muted,
                ),
                10,
            )
            add(button("119안심콜 공식 로그인·등록 안내", R.id.x_safe_official, false) { go(Page.AGENCY) }, 16)
            val loaded = runCatching { personalHealth.records() }
            if (loaded.isFailure) {
                add(
                    text(
                        "저장된 정보를 열 수 없습니다. 기존 정보를 유지했습니다.",
                        21f,
                        warning,
                        false,
                        R.id.x_my_health_status,
                    ),
                    18,
                )
                return@shell
            }
            val records = loaded.getOrThrow()
            if (healthRecordId != "SELF" && records.none { it.id == healthRecordId })
                healthRecordId = "SELF"
            val record = records.firstOrNull { it.id == healthRecordId }
            if (
                healthForIncident != null &&
                    healthForIncident == emergencySupport.snapshot?.incidentId &&
                    record != null &&
                    emergencySupport.scenario == null
            ) {
                add(
                    text(
                        "이번 환자가 ${record.name}님인지 확인해 주세요. 선택한 정보는 이번 응급 요약에만 표시하며 외부 AI에 보내지 않습니다.",
                        21f,
                    ),
                    14,
                )
                add(
                    button("이 환자 정보로 응급 요약 만들기", R.id.x_health_use_emergency) {
                        val incident = emergencySupport.snapshot?.incidentId.orEmpty()
                        if (
                            healthForIncident == incident &&
                                medicalUnlocked() &&
                                !engine.restrictions.complexBlocked
                        ) {
                            emergencyHealth.select(incident, record.id, true, false)
                            healthForIncident = null
                            go(Page.EMERGENCY_SUPPORT)
                            emergencySupportUi.showPacket()
                        }
                    },
                    14,
                )
            }
            if (records.size > 1 || records.any { it.id != "SELF" }) {
                val keys =
                    intArrayOf(
                        R.id.x_local_record_1,
                        R.id.x_local_record_2,
                        R.id.x_local_record_3,
                        R.id.x_local_record_4,
                        R.id.x_local_record_5,
                        R.id.x_local_record_6,
                        R.id.x_local_record_7,
                        R.id.x_local_record_8,
                        R.id.x_local_record_9,
                        R.id.x_local_record_10,
                        R.id.x_local_record_11,
                        R.id.x_local_record_12,
                    )
                add(
                    button("내 정보", R.id.x_local_self, false) {
                        healthRecordId = "SELF"
                        requestRender()
                    },
                    16,
                )
                records
                    .filter { it.id != "SELF" }
                    .forEachIndexed { i, r ->
                        add(
                            button(r.name.ifBlank { "가족 정보" }, keys[i], false) {
                                healthRecordId = r.id
                                requestRender()
                            },
                            12,
                        )
                    }
            }
            if (record == null)
                add(text("등록된 내 건강정보가 없습니다.", 22f, ink, false, R.id.x_my_health_status), 18)
            else
                add(
                    panel {
                        fun field(title: String, value: String) {
                            add(text(title, 20f, accent, true), 12)
                            add(text(value.ifBlank { "입력 없음" }, 21f), 6)
                        }
                        field("관리 방식", record.role.label)
                        field("이름", record.name)
                        field("병력", record.conditions)
                        field("알레르기", record.allergies)
                        field("투약 내역 · 직접 입력", record.medicationHistory)
                        SafeCallSummary.sections(record.safeCall).forEach { (section, fields) ->
                            add(text(section, 24f, accent, true), 24)
                            if (fields.isEmpty()) field("보호자", "입력 없음")
                            else fields.forEach { (label, value) -> field(label, value) }
                        }
                        field("안심콜 등록 상태", record.registration.label + " · 공식 조회 아님")
                        field(
                            "등록 연락처",
                            if (record.registeredPhone.isBlank()) "확인 안 됨"
                            else
                                "끝 ${record.registeredPhone.filter(Char::isDigit).takeLast(4)} · 직접 입력",
                        )
                        field(
                            "마지막 수정",
                            if (record.updatedAt == 0L) "확인 안 됨"
                            else
                                java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.SHORT,
                                        java.text.DateFormat.SHORT,
                                    )
                                    .format(java.util.Date(record.updatedAt)),
                        )
                    },
                    16,
                )
            add(
                button(if (record == null) "내 정보 등록" else "선택한 정보 수정", R.id.x_my_health_edit) {
                    go(Page.MY_HEALTH_EDIT)
                },
                18,
            )
            add(
                button("가족 대신 정보 등록", R.id.x_my_health_add, false, records.size < 12) {
                    healthRecordId = "LOCAL-" + java.util.UUID.randomUUID().toString()
                    go(Page.MY_HEALTH_EDIT)
                },
                12,
            )
            if (record != null)
                add(
                    button("내 정보 삭제", R.id.x_my_health_delete, false) {
                        AlertDialog.Builder(host)
                            .setTitle("선택한 건강정보를 삭제할까요?")
                            .setMessage("다른 가족의 정보는 유지합니다.")
                            .setPositiveButton("삭제") { _, _ ->
                                if (medicalUnlocked() && !engine.restrictions.complexBlocked)
                                    mutate {
                                        personalHealth.remove(record.id)
                                        healthRecordId = "SELF"
                                        message = "선택한 정보를 삭제했습니다."
                                    }
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    },
                    12,
                )
            add(button("건강정보 고속도로 가입·연결", R.id.x_health_link, false) { go(Page.HEALTH_LINK) }, 16)
            add(button("가족 예시 관리", R.id.x_my_health_family, false) { go(Page.PROFILES) }, 12)
        }

    private fun healthLinkPage() =
        shell("건강정보 고속도로") {
            add(text("가입 및 정보 제공 동의", 27f, accent, true), 14)
            add(text("실제 가입이나 의료정보 전송은 진행되지 않습니다. 동의 후 예시 화면을 확인할 수 있습니다.", 20f, muted), 12)
            var accepted = false
            add(
                check("가입 및 예시 건강정보 확인에 동의합니다", false, R.id.x_health_consent) { value ->
                    accepted = value
                    host.findViewById<Button>(R.id.x_health_continue)?.isEnabled = value
                },
                18,
            )
            add(
                button("동의하고 예시 보기", R.id.x_health_continue, enabled = false) {
                    mutate {
                        healthLinkExample.agree(accepted)
                        page = Page.HEALTH_EXAMPLE
                    }
                },
                16,
            )
            add(
                button("취소", R.id.x_health_back, false) {
                    healthLinkExample.revoke()
                    go(Page.MY_HEALTH)
                },
                12,
            )
        }

    private fun healthExamplePage() =
        shell("건강정보 고속도로") {
            add(text("건강정보 예시", 27f, accent, true, R.id.x_health_example), 14)
            add(text("아래 내용은 예시이며 실제 의료기관 조회 결과가 아닙니다.", 20f, muted), 10)
            add(
                panel {
                    add(text("투약 내역", 23f, ink, true))
                    add(text("2026.01 · 예시 약품 A\n2026.02 · 예시 약품 B", 21f), 12)
                    add(text("표시된 내용은 가상의 예시입니다.", 18f, muted), 10)
                },
                18,
            )
            add(
                button("동의 철회", R.id.x_health_revoke, false) {
                    healthLinkExample.revoke()
                    go(Page.HEALTH_LINK)
                },
                16,
            )
            add(
                button("내 건강정보로 돌아가기", R.id.x_health_back, false) {
                    healthLinkExample.revoke()
                    go(Page.MY_HEALTH)
                },
                12,
            )
        }

    private fun personalHealthEdit() =
        shell("건강정보 등록·수정") {
            val loaded = runCatching { personalHealth.records() }
            if (loaded.isFailure) {
                add(text("저장된 정보를 열 수 없어 수정할 수 없습니다.", 21f, warning), 18)
                return@shell
            }
            val record =
                healthDraft.begin(
                    loaded.getOrThrow().firstOrNull { it.id == healthRecordId }
                        ?: PersonalHealthRecord(
                            id = healthRecordId,
                            role =
                                if (healthRecordId == "SELF") RegistrationRole.SELF
                                else RegistrationRole.PROXY,
                        )
                )
            add(text("직접 입력한 정보만 저장합니다. 투약 내역은 날짜와 약 이름을 적어주세요.", 20f, muted), 12)
            val safeForm =
                SafeCallForm(
                    host,
                    { healthDraft.begin(record) },
                    healthDraft::update,
                    { requestRender() },
                ) { action ->
                    if (medicalUnlocked() && !engine.restrictions.complexBlocked) action()
                    else reveal(action)
                }
            fun input(
                title: String,
                value: String,
                id: Int,
                max: Int,
                multiline: Boolean,
            ): EditText {
                add(text(title, 21f, ink, true), 16)
                return EditText(host).apply {
                    this.id = id
                    contentDescription = title
                    setText(value)
                    setTextColor(ink)
                    setHintTextColor(muted)
                    textSize = size(24f)
                    inputType =
                        InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                            if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
                    filters = arrayOf(InputFilter.LengthFilter(max))
                    minHeight = dp(if (multiline) 144 else LifeLinkUiStyle.touchHeight)
                    if (!multiline) setSingleLine() else gravity = Gravity.TOP or Gravity.START
                    imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    isSaveEnabled = false
                    setFilterTouchesWhenObscured(true)
                    add(this, 6)
                }
            }
            val name = input("이름", record.name, R.id.x_my_health_name, 80, false)
            var selectedRole = record.role
            var proxyConsent = record.proxyConsent
            var registered = record.registration == LocalRegistration.USER_REPORTED
            var captureDraft: () -> Unit = {}
            val proxyCheck =
                check("가족 정보를 관리할 동의·권한이 있습니다", proxyConsent, R.id.x_health_proxy_consent) {
                    proxyConsent = it
                    captureDraft()
                }
            proxyCheck.visibility =
                if (selectedRole == RegistrationRole.PROXY) View.VISIBLE else View.GONE
            add(
                button("관리 방식: ${selectedRole.label}", R.id.x_health_role, false) {
                    AlertDialog.Builder(host)
                        .setTitle("정보 관리 방식")
                        .setSingleChoiceItems(
                            RegistrationRole.entries.map { it.label }.toTypedArray(),
                            selectedRole.ordinal,
                        ) { dialog, which ->
                            selectedRole = RegistrationRole.entries[which]
                            proxyConsent = false
                            proxyCheck.isChecked = false
                            registered = false
                            host.findViewById<CheckBox>(R.id.x_health_registered)?.isChecked = false
                            proxyCheck.visibility =
                                if (selectedRole == RegistrationRole.PROXY) View.VISIBLE
                                else View.GONE
                            host.findViewById<Button>(R.id.x_health_role)?.text =
                                host.getString(R.string.health_role_label, selectedRole.label)
                            captureDraft()
                            dialog.dismiss()
                        }
                        .show()
                },
                16,
            )
            add(proxyCheck, 8)
            val phone =
                input("안심콜에 등록한 전화번호 · 선택", record.registeredPhone, R.id.x_health_phone, 24, false)
                    .apply { inputType = InputType.TYPE_CLASS_PHONE }
            val registeredCheck =
                check("공식 사이트에서 등록 완료를 확인했습니다", registered, R.id.x_health_registered) {
                    registered = it
                    captureDraft()
                }
            add(registeredCheck, 12)
            val identityChanged =
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        registeredCheck.isChecked = false
                        registered = false
                    }

                    override fun afterTextChanged(value: Editable?) {}
                }
            name.addTextChangedListener(identityChanged)
            phone.addTextChangedListener(identityChanged)
            add(text("사용자 확인으로만 기록합니다. 이 앱에서 공식 가입 여부를 조회하지 않습니다.", 18f, muted), 6)
            safeForm.applicant(this)
            safeForm.diseaseBeforeMedication(this)
            val conditions =
                input("기존 병력 메모", record.conditions, R.id.x_my_health_conditions, 1500, true)
            val allergies = input("알레르기", record.allergies, R.id.x_my_health_allergies, 1000, true)
            val medications =
                input("투약 내역", record.medicationHistory, R.id.x_my_health_medications, 3000, true)
            safeForm.diseaseAfterMedication(this)
            safeForm.additional(this)
            safeForm.guardiansBasic(this)
            safeForm.guardiansAdditional(this)
            fun entered() =
                PersonalHealthRecord(
                    name.text.toString(),
                    conditions.text.toString(),
                    allergies.text.toString(),
                    medications.text.toString(),
                    healthRecordId,
                    selectedRole,
                    phone.text.toString(),
                    if (registered) LocalRegistration.USER_REPORTED else LocalRegistration.UNKNOWN,
                    record.updatedAt,
                    proxyConsent,
                    healthDraft.begin(record).safeCall,
                )
            captureDraft = { healthDraft.update(entered()) }
            val draftWatcher =
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        captureDraft()
                    }

                    override fun afterTextChanged(s: Editable?) {}
                }
            listOf(name, phone, conditions, allergies, medications).forEach {
                it.addTextChangedListener(draftWatcher)
            }
            val status =
                text(healthDraft.validationMessage, 20f, warning, false, R.id.x_my_health_status)
                    .apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
            add(status, 12)
            fun showError(value: String) {
                healthDraft.validationMessage = value
                status.text = value
            }
            add(
                button("저장", R.id.x_my_health_save) {
                    if (!medicalUnlocked() || engine.restrictions.complexBlocked) return@button
                    val saved =
                        PersonalHealthRecord(
                            name.text.toString().trim(),
                            conditions.text.toString().trim(),
                            allergies.text.toString().trim(),
                            medications.text.toString().trim(),
                            healthRecordId,
                            selectedRole,
                            phone.text.toString().trim(),
                            if (registered) LocalRegistration.USER_REPORTED
                            else LocalRegistration.UNKNOWN,
                            System.currentTimeMillis(),
                            proxyConsent,
                            healthDraft.begin(record).safeCall,
                        )
                    if (
                        listOf(
                                saved.name,
                                saved.conditions,
                                saved.allergies,
                                saved.medicationHistory,
                            )
                            .all { it.isBlank() } && saved.safeCall == SafeCallDetails()
                    ) {
                        showError("한 항목 이상 입력해 주세요.")
                    } else if (SafeCallDetailsCodec.error(saved.safeCall) != null) {
                        showError(SafeCallDetailsCodec.error(saved.safeCall).orEmpty())
                    } else if (!PersonalHealthCodec.validate(saved)) {
                        showError("전화번호 형식과 가족 정보 관리 동의·권한을 확인해 주세요.")
                    } else
                        runCatching { personalHealth.save(saved) }
                            .onSuccess {
                                healthDraft.clear()
                                page = Page.MY_HEALTH
                                message = "이 기기에 저장했습니다."
                                requestRender()
                            }
                            .onFailure {
                                showError(
                                    if (it is IllegalArgumentException) it.message.orEmpty()
                                    else "저장하지 못했습니다. 입력과 기존 정보를 유지했습니다."
                                )
                            }
                },
                20,
            )
            add(button("취소", R.id.x_my_health_cancel, false) { go(Page.MY_HEALTH) }, 12)
        }

    private fun profilesPage() =
        shell("가족 예시 관리") {
            if (repository.storageUnavailable)
                add(
                    text(
                        "저장 정보 보호 키를 확인할 수 없습니다. 이전 내용은 표시하지 않습니다. 잠금 설정에서 정보를 삭제한 뒤 가상 예시를 복원할 수 있습니다.",
                        22f,
                        warning,
                    ),
                    16,
                )
            add(text("가상 예시만 저장합니다. 실제 이름·연락처·병력은 입력하지 않습니다.", 20f, muted), 12)
            if (engine.profiles.isEmpty())
                add(text("등록된 가상 프로필이 없습니다. 미등록 승객으로 모든 긴급 연결 시연을 이용할 수 있습니다.", 22f), 16)
            engine.profiles.forEach { p ->
                add(
                    panel {
                        add(text(engine.alias(p), 24f, ink, true))
                        add(
                            text(catalog.word("ages", p.ageKey) + " · 의료정보는 가려져 있습니다", 19f, muted),
                            8,
                        )
                        add(
                            button(
                                "가상정보 확인·편집",
                                id =
                                    when (p.id) {
                                        "DEMO-P-1" -> R.id.x_profile_edit_1
                                        "DEMO-P-11" -> R.id.x_profile_edit_11
                                        else -> View.NO_ID
                                    },
                                primary = false,
                                enabled = !engine.restrictions.complexBlocked,
                            ) {
                                editId = p.id
                                reveal { page = Page.EDIT }
                            },
                            12,
                        )
                    },
                    14,
                )
            }
            add(
                button(
                    "가상 가족 추가",
                    R.id.x_profile_add,
                    enabled = !engine.restrictions.complexBlocked && engine.profiles.size < 12,
                ) {
                    editId = null
                    reveal { page = Page.EDIT }
                },
                18,
            )
            add(
                button(
                    "기본 가상 예시 복원",
                    R.id.x_profile_reset,
                    false,
                    enabled = !engine.restrictions.complexBlocked,
                ) {
                    AlertDialog.Builder(host)
                        .setTitle("가상 예시를 복원할까요?")
                        .setMessage("편집한 가상 프로필을 기본 예시로 바꿉니다.")
                        .setPositiveButton("복원") { _, _ ->
                            mutate {
                                engine.restoreProfiles()
                                repository.save(engine.profiles)
                            }
                        }
                        .setNegativeButton("취소", null)
                        .show()
                },
                12,
            )
        }

    private fun choose(group: String, selected: String, id: Int = View.NO_ID): Spinner {
        val keys = catalog.words.getValue(group).keys.toList()
        return Spinner(host).apply {
            this.id = id
            minimumHeight = dp(LifeLinkUiStyle.touchHeight)
            contentDescription =
                when (group) {
                    "aliases" -> "가상 별명"
                    "ages" -> "연령대"
                    "guardians" -> "가상 보호자"
                    else -> group
                }
            adapter = pickerAdapter(keys.map { catalog.word(group, it) })
            setSelection(keys.indexOf(selected).coerceAtLeast(0))
            tag = keys
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun selection(spinner: Spinner) =
        (spinner.tag as List<String>)[spinner.selectedItemPosition]

    private fun editProfile() =
        shell("가상 가족 편집") {
            if (!engine.privateUnlocked || engine.restrictions.complexBlocked) {
                add(text("상세 정보가 잠겼습니다. 가족 목록에서 다시 확인하세요.", 22f, warning), 18)
                return@shell
            }
            val existing = engine.profiles.find { it.id == editId }
            val id =
                existing?.id
                    ?: "DEMO-P-" +
                        ((engine.profiles
                            .mapNotNull { it.id.removePrefix("DEMO-P-").toIntOrNull() }
                            .maxOrNull() ?: 0) + 1)
            val p = existing ?: PersonProfile(id, "NEW_A", "ADULT")
            val alias = choose("aliases", p.aliasKey, R.id.x_profile_alias)
            val age = choose("ages", p.ageKey, R.id.x_profile_age)
            add(text("미리 정의된 가상 정보에서 선택합니다. 긴급 연결의 선행 조건이 아닙니다.", 19f, muted), 12)
            add(text("가상 별명", 20f), 12)
            add(alias)
            add(text("연령대", 20f), 10)
            add(age)
            fun choices(group: String, selected: Set<String>, title: String): MutableSet<String> {
                val values = selected.toMutableSet()
                add(text(title, 23f, accent, true), 18)
                catalog.words.getValue(group).forEach { (key, label) ->
                    add(
                        check(label, key in values) {
                            if (it) values.add(key) else values.remove(key)
                        },
                        4,
                    )
                }
                return values
            }
            val conditions = choices("conditions", p.conditions, "병력 참고 · 가상")
            val meds = choices("medications", p.medications, "복용약 참고 · 가상")
            val allergies = choices("allergies", p.allergies, "알레르기 · 가상")
            val access = choices("access", p.access.needs, "접근성 선호")
            val pregnancy = choose("pregnancy", p.pregnancyKey)
            val guardian = choose("guardians", p.guardianKey)
            add(text("임신 참고 · 가상", 20f), 18)
            add(pregnancy)
            add(text("보호자 · 실제 연락 불가", 20f), 14)
            add(guardian)
            var big = p.access.largeText
            var simple = p.access.easy
            var safe = p.safeCallRegistered
            add(check("큰 글씨 선호", big) { big = it }, 12)
            add(check("쉬운 안내 선호", simple) { simple = it }, 6)
            add(check("119안심콜 등록된 가상 예시", safe) { safe = it }, 6)
            add(
                button("가상 프로필 저장", R.id.x_profile_save) {
                    mutate {
                        val changed =
                            PersonProfile(
                                id,
                                selection(alias),
                                selection(age),
                                conditions,
                                meds,
                                allergies,
                                selection(pregnancy),
                                selection(guardian),
                                AccessPreferences(access, big, simple),
                                safe,
                            )
                        if (existing == null) engine.addProfile(changed)
                        else engine.editProfile(changed)
                        repository.save(engine.profiles)
                        page = Page.PROFILES
                    }
                },
                22,
            )
            if (existing != null)
                add(
                    button("이 가상 프로필 삭제", R.id.x_profile_delete, false) {
                        AlertDialog.Builder(host)
                            .setTitle("가상 프로필 삭제")
                            .setMessage("선택 중인 개인 정보 공유도 함께 철회합니다.")
                            .setPositiveButton("삭제") { _, _ ->
                                reveal {
                                    mutate {
                                        engine.deleteProfile(id)
                                        repository.save(engine.profiles)
                                        page = Page.PROFILES
                                    }
                                }
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    },
                    12,
                )
        }

    private val sensitive = MedicalSharePolicy.protectedFields

    private fun cardPage() =
        shell("선택한 정보만 공유") {
            val s = engine.current ?: return@shell
            add(text("대상: ${s.target.label} · 앱 안의 가상 상황실\n동의하지 않아도 연결 시연은 계속됩니다.", 21f, muted), 12)
            add(text("도움받는 분: ${personLabel()}", 23f, ink, true), 14)
            if (engine.person() == null)
                add(button("가상 가족 선택 · 건너뛰어도 됩니다", R.id.x_person, false) { go(Page.PERSON) }, 12)
            ShareField.entries
                .filter { it !in setOf(ShareField.VIDEO, ShareField.TEXT, ShareField.VEHICLE) }
                .forEach { f ->
                    val value = engine.value(f)
                    val shown =
                        if (value == null) "정보 없음"
                        else if (f in sensitive && !engine.privateUnlocked) "가려진 가상 정보" else value
                    add(
                        panel {
                            add(
                                check(
                                    "${f.label} 제공에 동의",
                                    f in shareDraft,
                                    ExperienceViewIds.consent(f),
                                    enabled = value != null,
                                ) { checked ->
                                    if (checked) shareDraft.add(f)
                                    else {
                                        shareDraft.remove(f)
                                        if (f in engine.current?.shared.orEmpty())
                                            mutate { engine.revoke(f) }
                                    }
                                    host.findViewById<Button>(R.id.x_share_card)?.isEnabled =
                                        shareDraft.isNotEmpty()
                                }
                            )
                            add(text(shown, 18f, muted), 6)
                            if (f in s.shared) add(text("가상 상황실에 공유 중", 17f, accent), 6)
                        },
                        10,
                    )
                }
            add(
                button("선택 정보 모의 공유", R.id.x_share_card, enabled = shareDraft.isNotEmpty()) {
                    mutate {
                        engine.share(shareDraft)
                        message = "선택한 항목을 앱 내 가상 상황실에 반영했습니다."
                    }
                },
                18,
            )
            add(
                button(
                    if (medicalUnlocked()) "의료정보 다시 잠그기" else "의료정보 인증하고 확인",
                    R.id.x_unlock,
                    false,
                ) {
                    if (medicalUnlocked()) lockMedical() else reveal {}
                },
                12,
            )
            add(
                button(
                    "전체 공유 철회",
                    R.id.x_revoke,
                    false,
                    enabled = s.shared.isNotEmpty() || engine.vehicleContextShare != null,
                ) {
                    mutate {
                        engine.revoke()
                        shareDraft.clear()
                        message = "공유정보와 영상·경로를 철회했습니다."
                    }
                },
                12,
            )
            add(
                button("차량 상태 조회·공유", R.id.x_vehicle_context, false) { go(Page.VEHICLE_CONTEXT) },
                12,
            )
            add(button("가상 상황실에서 확인", R.id.x_center, false) { go(Page.CENTER) }, 12)
        }

    private fun textPage() =
        shell("문자·의사표현 시연") {
            add(text("실제 문자 발송 없이, 선택한 문구가 앱 내 상황실에 표시됩니다.", 20f, muted), 12)
            if (engine.restrictions.messagesBlocked) {
                add(text("플랫폼 제한으로 문자 조작을 잠갔습니다. 상단의 긴급 연결 경로를 확인하세요.", 22f, warning), 16)
                return@shell
            }
            catalog.words.getValue("phrases").entries.forEachIndexed { index, e ->
                add(
                    button(e.value, if (index == 0) R.id.x_send_phrase else View.NO_ID, false) {
                        mutate { engine.sendPhrase(e.key) }
                    },
                    12,
                )
            }
            add(
                text(
                    engine.current?.messages.orEmpty().joinToString("\n\n").ifBlank {
                        "선택한 문구가 없습니다."
                    },
                    22f,
                    ink,
                    false,
                    R.id.x_messages,
                ),
                20,
            )
            add(button("가상 상담원 답장 보기", R.id.x_reply) { mutate { engine.reply() } }, 16)
        }

    private fun signPage() =
        shell("수어통역 연결 모형") {
            val connected = engine.current?.sign == SignStatus.CONNECTED
            add(text("실제 영상·수어 번역이 아닌 3자 연결 상태 시연입니다.", 20f, muted), 12)
            add(
                row(
                    panel {
                        add(text("나 · 가상", 25f, accent, true))
                        add(text("영상 사용 동의는 별도입니다.", 18f, muted), 10)
                    },
                    panel {
                        add(text("119 가상 상담원", 25f, accent, true))
                        add(text(if (connected) "통역 연결 시연 확인" else "대기", 20f), 10)
                    },
                ),
                18,
            )
            add(
                panel {
                    add(text("수어통역사 · 가상", 25f, accent, true))
                    add(
                        text(
                            if (connected) "가상 통역사 연결 시연" else "아직 연결 요청 전",
                            23f,
                            ink,
                            false,
                            R.id.x_sign_status,
                        ),
                        14,
                    )
                },
                16,
            )
            add(
                button(
                    if (connected) "통역 연결 시연 종료" else "가상 통역사 연결에 동의",
                    R.id.x_sign_connect,
                    enabled = connected || !engine.restrictions.videoBlocked,
                ) {
                    mutate { engine.sign(!connected) }
                },
                18,
            )
            if (engine.restrictions.videoBlocked)
                add(text("플랫폼 제한 중에는 새 영상·통역 연결 조작이 제한됩니다.", 19f, warning), 12)
        }

    private fun guidedVideoPage() =
        shell("119 상황실 영상 안내") {
            val s = engine.current ?: return@shell
            if (s.ended) {
                add(text("종료된 시연입니다.", 22f, warning), 16)
                return@shell
            }
            add(text("합성 영상 안내 · 실제 통화·촬영 없음", 22f, muted), 12)
            if (engine.restrictions.videoBlocked)
                add(text("차량 제한으로 영상 미리보기와 시작을 잠갔습니다. 중단은 가능합니다.", 21f, warning), 16)
            when (s.video) {
                VideoStatus.OFF,
                VideoStatus.STOPPED -> {
                    add(
                        button(
                            "영상 안내 요청",
                            R.id.x_guide_request,
                            enabled = !engine.restrictions.videoBlocked,
                        ) {
                            mutate {
                                guideStep = 0
                                engine.requestVideo(VideoSource.PHONE)
                            }
                        },
                        18,
                    )
                }
                VideoStatus.REQUESTED -> {
                    add(
                        panel {
                            add(text("합성 영상 사용 동의", 24f, accent, true))
                            add(text("합성 장면과 가상 안내가 표시됩니다. 언제든 중단할 수 있습니다.", 20f), 10)
                        },
                        16,
                    )
                    add(
                        button(
                            "동의하고 계속",
                            R.id.x_guide_consent,
                            enabled = !engine.restrictions.videoBlocked,
                        ) {
                            mutate { engine.approveVideo() }
                        },
                        16,
                    )
                }
                VideoStatus.APPROVED ->
                    add(
                        button(
                            "영상 안내 시작",
                            R.id.x_guide_start,
                            enabled = !engine.restrictions.videoBlocked,
                        ) {
                            mutate { engine.startVideo() }
                        },
                        18,
                    )
                VideoStatus.SHARING -> {
                    if (engine.videoPreview && !engine.restrictions.videoBlocked) {
                        add(
                            panel {
                                add(text("가상 119 상황실 담당자", 24f, accent, true))
                                add(
                                    text(
                                        listOf(
                                            "현재 상황을 알려주세요.",
                                            "화면을 함께 확인하는 상황입니다.",
                                            "실제 상황에서는 119 안내에 따라주세요.",
                                        )[guideStep],
                                        24f,
                                        ink,
                                        false,
                                        R.id.x_guide_message,
                                    ),
                                    12,
                                )
                            },
                            16,
                        )
                        add(
                            SyntheticFrameView(host, s.videoSource, frame).apply {
                                id = R.id.x_guide_preview
                                minimumHeight = dp(180)
                                contentDescription = "합성 장면, 실제 촬영 아님"
                            },
                            16,
                        )
                        add(text("합성 장면 · 실제 촬영이나 음성 연결 없음", 18f, muted), 8)
                        add(
                            button("다음 안내 보기", R.id.x_guide_next, false) {
                                guideStep = (guideStep + 1) % 3
                                frame = (frame + 1) % 4
                                requestRender()
                            },
                            12,
                        )
                    } else {
                        add(text("미리보기가 일시 중지되었습니다. 다시 보려면 직접 선택하세요.", 21f, warning), 16)
                        if (!engine.restrictions.videoBlocked)
                            add(
                                button("다시 보기", R.id.x_guide_resume) {
                                    mutate { engine.openVideoPreview() }
                                },
                                12,
                            )
                    }
                }
            }
            if (s.video !in setOf(VideoStatus.OFF, VideoStatus.STOPPED))
                add(
                    button("영상 중단·동의 철회", R.id.x_guide_stop, false) {
                        mutate { engine.stopVideo() }
                    },
                    16,
                )
            add(button("연결 화면으로", R.id.x_guide_back, false) { go(Page.SESSION) }, 12)
        }

    private fun videoPage() =
        shell("영상 공유 · 합성 예시") {
            val s = engine.current ?: return@shell
            add(text(camera.capability(), 20f, muted), 12)
            add(
                text(
                    "${s.videoSource.label} · ${when(s.video){VideoStatus.OFF->"요청 전"
VideoStatus.REQUESTED->"공유 요청 · 동의 대기"
VideoStatus.APPROVED->"동의 완료 · 시작 전"
VideoStatus.SHARING->"영상 공유 중 — 시연"
VideoStatus.STOPPED->"공유 종료"}}",
                    23f,
                    accent,
                    true,
                    R.id.x_video_status,
                ),
                16,
            )
            if (engine.restrictions.videoBlocked)
                add(text("플랫폼 제한으로 영상 미리보기와 새 공유 조작을 잠갔습니다.", 22f, warning), 16)
            if (engine.videoPreview && !engine.restrictions.videoBlocked) {
                add(
                    SyntheticFrameView(host, s.videoSource, frame).apply {
                        id = R.id.x_video_preview
                        minimumHeight = dp(180)
                    },
                    16,
                )
                add(
                    button("다음 합성 장면", R.id.x_next_frame, false) {
                        frame = (frame + 1) % 4
                        requestRender()
                    },
                    12,
                )
            }
            when (s.video) {
                VideoStatus.OFF,
                VideoStatus.STOPPED ->
                    VideoSource.entries.forEachIndexed { i, source ->
                        add(
                            button(
                                source.label + " 공유 요청",
                                if (i == 0) R.id.x_video_front
                                else if (i == 1) R.id.x_video_rear else R.id.x_video_phone,
                                i == 2,
                                !engine.restrictions.videoBlocked,
                            ) {
                                mutate { engine.requestVideo(source) }
                            },
                            12,
                        )
                    }
                VideoStatus.REQUESTED -> {
                    add(text(camera.sharePreview(), 21f), 16)
                    add(
                        button(
                            "합성 영상 사용 범위에 동의",
                            R.id.x_video_approve,
                            enabled = !engine.restrictions.videoBlocked,
                        ) {
                            mutate { engine.approveVideo() }
                        },
                        16,
                    )
                }
                VideoStatus.APPROVED ->
                    add(
                        button(
                            "영상 공유 시작 · 시연",
                            R.id.x_video_start,
                            enabled = !engine.restrictions.videoBlocked,
                        ) {
                            mutate { engine.startVideo() }
                        },
                        18,
                    )
                VideoStatus.SHARING ->
                    if (!engine.videoPreview && !engine.restrictions.videoBlocked)
                        add(
                            button("합성 미리보기 다시 열기", R.id.x_video_preview_open) {
                                mutate { engine.openVideoPreview() }
                            },
                            16,
                        )
            }
            if (s.video !in setOf(VideoStatus.OFF, VideoStatus.STOPPED))
                add(
                    button("영상 공유 중단·동의 취소", R.id.x_video_stop, false) {
                        mutate { engine.stopVideo() }
                    },
                    16,
                )
        }

    private fun locationPage() =
        shell("위치·차량 정보") {
            add(text("위치는 가상 좌표입니다. 차량 상태는 별도로 조회하고 동의한 뒤 전달할 수 있습니다.", 20f, muted), 12)
            add(
                panel {
                    add(
                        text(
                            engine.value(ShareField.LOCATION) ?: "가상 위치 없음",
                            23f,
                            ink,
                            true,
                            R.id.x_location_status,
                        )
                    )
                },
                16,
            )
            add(
                button("가상 위치 공유", R.id.x_share_location, enabled = engine.locationAvailable) {
                    mutate { engine.share(setOf(ShareField.LOCATION)) }
                },
                18,
            )
            add(button("차량 상태 조회·공유", R.id.x_share_vehicle, false) { go(Page.VEHICLE_CONTEXT) }, 12)
            add(button("실제 Vehicle SDK 진단", R.id.x_sdk, false) { legacy("vehicle") }, 12)
            if (!engine.locationAvailable)
                add(
                    button("가상 위치 다시 확인", R.id.x_location_retry, false) {
                        engine.locationAvailable = true
                        requestRender()
                    },
                    12,
                )
        }

    private fun summaryPage() =
        shell("상담 전달용 정보 요약") {
            val s = engine.current ?: return@shell
            add(text("규칙 기반 문장입니다. 사용자가 검토·수정한 뒤 동의해야 공유합니다. 진단·위험도 판정은 하지 않습니다.", 20f, muted), 12)
            add(panel { add(text(engine.summary(), 23f, ink, false, R.id.x_summary)) }, 16)
            if (!engine.restrictions.complexBlocked) {
                val observation = choose("observations", s.observation, R.id.x_observation)
                add(text("상황 설명 수정", 22f, accent, true), 18)
                add(observation)
                val times = listOf("NOW", "AGO5", "AGO10", "AGO30")
                val time =
                    Spinner(host).apply {
                        id = R.id.x_discovered
                        minimumHeight = dp(LifeLinkUiStyle.touchHeight)
                        contentDescription = "가상 발견 시각"
                        adapter = pickerAdapter(listOf("방금 발견", "5분 전", "10분 전", "30분 전"))
                        setSelection(times.indexOf(s.discovered).coerceAtLeast(0))
                    }
                add(text("발견 시각", 20f), 12)
                add(time)
                val glucose =
                    EditText(host).apply {
                        id = R.id.x_glucose
                        hint = "선택: 가상 혈당 20~600 mg/dL"
                        textSize = size(24f)
                        setTextColor(ink)
                        setHintTextColor(muted)
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setText(s.demoGlucose?.let { String.format(Locale.ROOT, "%d", it) } ?: "")
                        minHeight = dp(LifeLinkUiStyle.touchHeight)
                        setSingleLine()
                    }
                add(glucose, 12)
                add(
                    button("요약 수정 반영", R.id.x_summary_update, false) {
                        mutate {
                            val v = glucose.text.toString()
                            engine.observe(
                                selection(observation),
                                times[time.selectedItemPosition],
                                if (v.isBlank()) null else v.toInt(),
                            )
                        }
                    },
                    12,
                )
            } else add(text("플랫폼 제한 중에는 상세 수정이 잠깁니다.", 20f, warning), 12)
            add(
                button("검토한 요약 공유에 동의", R.id.x_share_summary) {
                    mutate {
                        engine.share(setOf(ShareField.SUMMARY))
                        message = "검토한 요약만 가상 상황실에 공유했습니다."
                    }
                },
                18,
            )
        }

    private fun voicePage() =
        shell("음성 도움말 시연") {
            add(text("현재는 소리 재생 없이 안내 문장을 강조합니다. 실제 STT·TTS 서비스나 마이크를 사용하지 않습니다.", 21f, muted), 14)
            add(text("화면 위의 긴급 연결에서 119 또는 112 연결 시연을 선택할 수 있습니다.", 27f, accent, true), 20)
            add(
                button("음성 안내 문장 강조 · 시연", R.id.x_voice) {
                    mutate {
                        engine.voice()
                        message = engine.current?.voice.orEmpty()
                    }
                },
                18,
            )
        }

    private fun easyPage() =
        shell("하나씩, 쉽게 안내할게요") {
            listOf(
                    "1. 도움이 필요한 분 곁에서 안전을 확인하세요.",
                    "2. 가상 상담원에게 필요한 도움을 선택해 알려주세요.",
                    "3. 원한다면 보호자·위치 정보를 따로 선택하세요.",
                )
                .forEach { add(text(it, 27f), 18) }
            add(button("의사표현 카드", R.id.x_send_phrase) { go(Page.TEXT) }, 20)
            add(button("보호자 정보 선택", primary = false) { go(Page.CARD) }, 12)
        }

    private fun centerPage() =
        shell("가상 상황실 · 앱 안") {
            val s = engine.current ?: return@shell
            if (engine.restrictions.galleryBlocked) {
                add(
                    text(
                        "${s.target.label} · ${status(s)}\n동의 항목 ${s.shared.size}개 · 상세 목록은 플랫폼 제한 중 숨깁니다.",
                        24f,
                        accent,
                        true,
                        R.id.x_center_status,
                    ),
                    16,
                )
                add(button("연결 시연으로 돌아가기", R.id.x_return_session, false) { go(Page.SESSION) }, 16)
                return@shell
            }
            add(
                panel {
                    add(
                        text(
                            "${s.target.label} · ${status(s)}",
                            25f,
                            accent,
                            true,
                            R.id.x_center_status,
                        )
                    )
                    add(text("${catalog.scenario(s.scenarioId).title}\n${personLabel()}", 22f), 12)
                    add(
                        text("공유 항목 ${s.shared.size}개", 23f, warning, true, R.id.x_shared_count),
                        12,
                    )
                    if (s.shared.isEmpty()) add(text("동의한 정보가 없습니다. 연결 시연은 계속됩니다.", 20f, muted), 10)
                    s.shared.forEach { (field, value) ->
                        add(
                            text(
                                field.label +
                                    ": " +
                                    if (field in sensitive && !engine.privateUnlocked) "가려진 가상 정보"
                                    else value,
                                20f,
                            ),
                            12,
                        )
                    }
                    add(
                        text(
                            "영상 ${s.video} · 통역 ${s.sign}\n112 협조 ${s.police}\n만남 장소 ${s.navigation}\n안심콜 ${s.safeCall}",
                            18f,
                            muted,
                        ),
                        16,
                    )
                },
                16,
            )
            engine.vehicleContextShare
                ?.takeIf { it.fresh(SystemClock.elapsedRealtime()) }
                ?.let { shared ->
                    add(
                        panel {
                            add(text("사용자가 전달한 차량 상태", 24f, accent, true))
                            add(
                                text(
                                    shared.summary(SystemClock.elapsedRealtime()),
                                    21f,
                                    id = R.id.x_vctx_center,
                                ),
                                12,
                            )
                            add(
                                button("차량 상태 전달 철회", R.id.x_vctx_revoke, false) {
                                    mutate { engine.revokeVehicleContext() }
                                },
                                12,
                            )
                        },
                        16,
                    )
                }
            if (s.active) {
                if (engine.cooperation.snapshot != null)
                    add(
                        button("협력 차량·표시 승인 역할 보기", R.id.x_coop_center, false) {
                            go(Page.COOPERATION)
                        },
                        12,
                    )
                if (s.target in setOf(ConnectionTarget.EMERGENCY_119, ConnectionTarget.CONSULT_119))
                    add(
                        button("가상 상담원: 만남 장소 안내", R.id.x_operator_guidance) {
                            mutate {
                                engine.giveGuidance()
                                message = "가상 전문가 안내를 기록했습니다. 목적지는 사용자가 확인해야 합니다."
                            }
                        },
                        16,
                    )
                add(button("공유·동의 관리", R.id.x_card_manage, false) { go(Page.CARD) }, 12)
                add(button("연결 시연으로 돌아가기", R.id.x_return_session, false) { go(Page.SESSION) }, 12)
            }
            add(
                panel {
                    add(text("PC 상황실 · 선택 기능", 23f, accent, true))
                    add(
                        text(
                            "현재 시연 상태와 공유 항목 이름만 localhost로 보냅니다. 개별 의료 내용·연락처는 보내지 않습니다. 수동 전송한 시점의 상태입니다.",
                            18f,
                            muted,
                        ),
                        10,
                    )
                    add(
                        check(
                            "이 PC에 가상 상태 게시에 동의",
                            pcConsent,
                            R.id.x_pc_consent,
                            BuildConfig.DEMO_FEATURES && !pcBusy,
                        ) { v ->
                            pcConsent = v
                            requestRender()
                        },
                        10,
                    )
                    add(
                        button(
                            "현재 시연 상태 PC로 보내기",
                            R.id.x_pc_publish,
                            enabled = pcConsent && BuildConfig.DEMO_FEATURES && !pcBusy,
                        ) {
                            publishPc()
                        },
                        10,
                    )
                    if (pcPostedId != null)
                        add(
                            button("PC 게시 철회", R.id.x_pc_revoke, false, enabled = !pcBusy) {
                                revokePc()
                            },
                            10,
                        )
                    add(text(bridgeMessage, 18f, warning, false, R.id.x_pc_status), 12)
                    add(
                        text(
                            "PC: http://127.0.0.1:8781/experience-console\n네트워크 없이도 위 앱 내 상황실에서 모든 새 기능을 시연할 수 있습니다.",
                            17f,
                            muted,
                        ),
                        10,
                    )
                },
                22,
            )
            add(text("시연 동작 기록\n" + s.events.joinToString(" → "), 17f, muted), 20)
        }

    private fun publishPc() {
        val s = engine.current ?: return
        if (!pcConsent || pcBusy) return
        pcBusy = true
        bridgeMessage = "현재 가상 상태 전송 중…"
        requestRender()
        bridge.publish(s) { result ->
            pcBusy = false
            if (result == null) {
                pcPostedId = s.id
                bridgeMessage = "${s.id} · revision ${s.revision} 시점 상태 게시됨"
                if (engine.current?.id != s.id || engine.current?.ended == true) revokePc(s.id)
            } else bridgeMessage = result
            requestRender()
        }
    }

    private fun revokePc(id: String? = pcPostedId) {
        if (id == null || pcBusy) return
        pcBusy = true
        pcPostedId = null
        pcConsent = false
        bridgeMessage = "PC 게시 철회 요청 중…"
        bridge.revoke(id) { error ->
            pcBusy = false
            if (error != null) pcPostedId = id
            bridgeMessage = error ?: "PC 게시 철회됨 · 앱 내 시연은 계속 이용할 수 있습니다."
            requestRender()
        }
    }

    private fun policePage() =
        shell("112 공동대응 · 모의 요청") {
            val s = engine.current ?: return@shell
            add(text("경찰 출동·호송·우선통행을 보장하지 않습니다. 별도 기관 역할이 검토·승인 또는 거절하는 가상 절차입니다.", 21f, muted), 12)
            add(
                text(
                    when (s.police) {
                        PoliceStatus.NONE -> "요청 전"
                        PoliceStatus.REQUESTED -> "경찰 협조 요청됨 — 시연"
                        PoliceStatus.REVIEWING -> "가상 112 역할 검토 중"
                        PoliceStatus.APPROVED -> "가상 112 역할 승인 · 실제 권한 없음"
                        PoliceStatus.REJECTED -> "가상 112 역할 거절"
                    },
                    25f,
                    accent,
                    true,
                    R.id.x_police_status,
                ),
                18,
            )
            when (s.police) {
                PoliceStatus.NONE,
                PoliceStatus.REJECTED ->
                    add(
                        button("경찰 협조 모의 요청", R.id.x_police_request) {
                            mutate { engine.requestPolice() }
                        },
                        18,
                    )
                PoliceStatus.REQUESTED ->
                    add(
                        button("가상 112 역할: 검토 시작", R.id.x_police_review) {
                            mutate { engine.reviewPolice() }
                        },
                        18,
                    )
                PoliceStatus.REVIEWING -> {
                    add(
                        button("가상 112 역할: 승인", R.id.x_police_approve) {
                            mutate { engine.decidePolice(true) }
                        },
                        18,
                    )
                    add(
                        button("가상 112 역할: 거절", R.id.x_police_reject, false) {
                            mutate { engine.decidePolice(false) }
                        },
                        12,
                    )
                }
                PoliceStatus.APPROVED ->
                    add(
                        text("이 상태는 공모전 가상 절차입니다. 실제 교통법규 특례나 에스코트 권리가 발생하지 않습니다.", 22f, warning),
                        16,
                    )
            }
        }

    private fun navigationPage() =
        shell("전문가 안내 후 만남 장소 확인") {
            val s = engine.current ?: return@shell
            add(
                text(
                    "NaviHelper 실제 호출 없이 가상 목적지 설정을 시연합니다. 병원의 수용 가능 여부나 자가 이송의 안전성을 판단하지 않습니다.",
                    20f,
                    muted,
                ),
                12,
            )
            val stage =
                when (s.navigation) {
                    NavigationStatus.NONE -> "제안 전"
                    NavigationStatus.PROPOSED -> "가상 경로 제안"
                    NavigationStatus.CONFIRMED -> "사용자 확인 완료"
                    NavigationStatus.SET -> "시연 목적지 설정 완료"
                }
            add(
                text(
                    "$stage · ${when(s.destination){"MEET_B"->"가상 구급대 만남 지점 B"
"HOSPITAL_A"->"가상 병원 A · 수용 여부 미확인"
else->"아직 목적지 없음"}}",
                    24f,
                    accent,
                    true,
                    R.id.x_nav_status,
                ),
                16,
            )
            if (!s.guidance) {
                add(text("먼저 가상 상담원의 안내가 필요합니다.", 22f), 14)
                add(button("가상 상황실에서 안내 받기", R.id.x_center) { go(Page.CENTER) }, 16)
                return@shell
            }
            if (ShareField.LOCATION !in s.shared || !engine.locationAvailable) {
                add(button("가상 위치 선택·공유", R.id.x_share_location) { go(Page.LOCATION) }, 16)
                return@shell
            }
            when (s.navigation) {
                NavigationStatus.NONE -> {
                    add(
                        button("가상 만남 지점 B 제안", R.id.x_nav_propose) {
                            mutate { engine.proposeNavigation("MEET_B") }
                        },
                        16,
                    )
                    add(
                        button("가상 병원 A 제안 · 전문가 안내 가정", primary = false) {
                            mutate { engine.proposeNavigation("HOSPITAL_A") }
                        },
                        12,
                    )
                }
                NavigationStatus.PROPOSED ->
                    add(
                        button("제안된 가상 목적지 확인", R.id.x_nav_confirm) {
                            mutate { engine.confirmNavigation() }
                        },
                        18,
                    )
                NavigationStatus.CONFIRMED ->
                    add(
                        button("시연 목적지 설정", R.id.x_nav_set) { mutate { engine.setNavigation() } },
                        18,
                    )
                NavigationStatus.SET ->
                    add(text("시연 목적지 설정 완료 · 실제 내비 앱·차량은 변경하지 않았습니다.", 23f, warning), 18)
            }
        }

    private fun safePage() =
        shell("119안심콜 · 대상·회선 확인") {
            val link = engine.safeCallLink
            add(text("전화와 조회 응답은 가상 조건입니다. 공식 기관 조회는 미연결입니다.", 19f, muted), 12)
            add(
                text(
                    "도움 대상: ${engine.person()?.let(engine::alias) ?: "미선택"}",
                    24f,
                    ink,
                    true,
                    R.id.x_safe_person,
                ),
                16,
            )
            add(
                text(
                    link.outcome.label,
                    25f,
                    if (link.outcome == SafeCallOutcome.MATCHED) accent else warning,
                    true,
                    R.id.x_safe_status,
                ),
                16,
            )
            add(text("번호가 다르거나 확인되지 않아도 긴급 연결은 계속됩니다.", 19f, muted), 10)
            add(button("도움 대상자 선택", R.id.x_safe_person_choose, false) { go(Page.PERSON) }, 16)
            add(
                row(
                    button(
                        if (link.role == RegistrationRole.SELF) "선택됨 · 본인" else "수혜자 본인",
                        R.id.x_safe_role_self,
                        false,
                    ) {
                        mutate { engine.configureSafeCall(role = RegistrationRole.SELF) }
                    },
                    button(
                        if (link.role == RegistrationRole.PROXY) "선택됨 · 대리자" else "가족·대리자",
                        R.id.x_safe_role_proxy,
                        false,
                    ) {
                        mutate { engine.configureSafeCall(role = RegistrationRole.PROXY) }
                    },
                ),
                16,
            )
            if (link.role == RegistrationRole.PROXY)
                add(
                    check("가족 정보 관리 권한을 확인했습니다", link.authorityConfirmed, R.id.x_safe_authority) { v
                        ->
                        mutate { engine.configureSafeCall(authority = v) }
                    },
                    12,
                )
            fun <T> picker(
                title: String,
                values: List<T>,
                selected: T,
                id: Int,
                label: (T) -> String,
                changed: (T) -> Unit,
            ) {
                add(text(title, 21f, ink, true), 18)
                add(
                    Spinner(host).apply {
                        this.id = id
                        contentDescription = title
                        minimumHeight = dp(LifeLinkUiStyle.touchHeight)
                        adapter = pickerAdapter(values.map(label))
                        setSelection(values.indexOf(selected))
                        val picker = this
                        onItemSelectedListener =
                            object : AdapterView.OnItemSelectedListener {
                                override fun onNothingSelected(parent: AdapterView<*>?) {}

                                override fun onItemSelected(
                                    parent: AdapterView<*>?,
                                    view: View?,
                                    position: Int,
                                    itemId: Long,
                                ) {
                                    if (
                                        picker.isAttachedToWindow &&
                                            host.findViewById<Spinner>(id) === picker &&
                                            values[position] != selected
                                    )
                                        changed(values[position])
                                }
                            }
                    },
                    6,
                )
            }
            picker(
                "발신 회선 · 가상",
                DemoCallLine.entries.toList(),
                link.line,
                R.id.x_safe_line,
                { it.label },
            ) { v ->
                mutate { engine.configureSafeCall(line = v) }
            }
            picker(
                "조회 응답 조건",
                SafeCallFault.entries.toList(),
                safeFault,
                R.id.x_safe_fault,
                { it.label },
            ) { v ->
                safeFault = v
                mutate { engine.revokeSafeCall() }
            }
            var accepted = link.consent
            add(
                check("이번 등록정보 확인에 동의합니다", accepted, R.id.x_safe_consent) { v ->
                    accepted = v
                    if (!v) mutate { engine.revokeSafeCall() }
                    else
                        host.findViewById<Button>(R.id.x_safe_lookup)?.isEnabled =
                            link.outcome != SafeCallOutcome.PENDING
                },
                16,
            )
            add(
                button(
                    "등록번호 대조",
                    R.id.x_safe_lookup,
                    enabled = accepted && link.outcome != SafeCallOutcome.PENDING,
                ) {
                    mutate {
                        val request = engine.beginSafeCall(accepted, safeFault)
                        if (request != null)
                            main.postDelayed(
                                {
                                    if (inForeground && medicalUnlocked())
                                        mutate { engine.completeSafeCall(request) }
                                },
                                650,
                            )
                    }
                },
                16,
            )
            add(
                button("동의 철회·결과 지우기", R.id.x_safe_revoke, false) {
                    mutate { engine.revokeSafeCall() }
                },
                12,
            )
            if (
                link.outcome in
                    setOf(
                        SafeCallOutcome.DIFFERENT,
                        SafeCallOutcome.UNKNOWN,
                        SafeCallOutcome.AMBIGUOUS,
                    )
            )
                add(
                    text("상황요원에게 신고한 사람과 도움이 필요한 사람이 다를 수 있음을 알려주세요. 번호 확인을 위해 연결을 끊지 않습니다.", 22f),
                    16,
                )
            add(button("119안심콜 공식 가입 안내", R.id.x_safe_official, false) { go(Page.AGENCY) }, 12)
            add(button("응급정보 공유는 별도로 선택", primary = false) { go(Page.CARD) }, 12)
        }

    private fun agencyPage() =
        shell("119안심콜 공식 가입 안내") {
            add(text("본인 가입과 가족 대신 가입을 구분해 주세요.", 23f, ink, true, R.id.x_agency_status), 16)
            add(
                text(
                    "공식 사이트에서는 ID와 휴대폰 문자 인증을 거쳐 본인의 등록 정보를 확인할 수 있습니다. 아래 버튼으로 공식 사이트를 열어 진행해 주세요.",
                    21f,
                    muted,
                ),
                14,
            )
            add(
                row(
                    button("수혜자 본인", R.id.x_agency_self, false) {
                        agencyRole = RegistrationRole.SELF
                        requestRender()
                    },
                    button("가족·대리자", R.id.x_agency_proxy, false) {
                        agencyRole = RegistrationRole.PROXY
                        requestRender()
                    },
                ),
                16,
            )
            add(
                text(
                    if (agencyRole == RegistrationRole.SELF) "공식 사이트에서 본인 정보를 등록·수정합니다."
                    else "대리자 가입 후 도움받을 수혜자의 정보를 등록·관리합니다. 대리자 전화와 수혜자 등록 전화는 다를 수 있습니다.",
                    23f,
                ),
                16,
            )
            add(
                text(
                    "등록한 전화로 신고해야 사전 정보 활용이 원활합니다. 다른 전화로 신고 중이라면 연결을 유지하고 상황요원의 안내를 따르세요.",
                    21f,
                    muted,
                ),
                14,
            )
            add(
                text(
                    "공식 사이트를 앱 안에서 엽니다. 사이트에서 직접 로그인하며, 차량에 저장한 이름·전화번호·병력은 자동으로 전달하지 않습니다. 닫으면 임시 웹 로그인 정보를 정리합니다.",
                    19f,
                    muted,
                ),
                14,
            )
            add(
                text(
                    "공식 사이트에서 로그인해도 이 차량으로 정보를 자동으로 가져오지 않습니다. 차량에 보관할 정보는 ‘우리 가족 안심콜’에 직접 입력하고, 사고 시 인증 후 확인할 수 있습니다.",
                    21f,
                    muted,
                ),
                14,
            )
            OfficialHealthSite.entries.forEach { site ->
                add(
                    button(
                        site.label,
                        when (site) {
                            OfficialHealthSite.SAFE_CALL -> R.id.x_agency_open
                            OfficialHealthSite.SAFE_CALL_GUIDE -> R.id.x_agency_guide
                            OfficialHealthSite.HEALTHWAY -> R.id.x_agency_healthway
                        },
                        site == OfficialHealthSite.SAFE_CALL,
                    ) {
                        openOfficial(site)
                    },
                    16,
                )
            }
            add(
                text(
                    "소방청 정보 불러오기·119 자동 전달: 기관 연계 협의가 필요합니다.",
                    19f,
                    muted,
                    false,
                    R.id.x_agency_api_status,
                ),
                16,
            )
        }

    private fun openOfficial(site: OfficialHealthSite) {
        if (engine.restrictions.complexBlocked) {
            message = "플랫폼의 상세 조작 제한 중에는 공식 사이트를 열 수 없습니다."
            requestRender()
            return
        }
        inAction = true
        lockMedical()
        inAction = false
        if (!OfficialHealthNavigation.open(host, site)) {
            AlertDialog.Builder(host)
                .setTitle("휴대전화·PC에서 열어주세요")
                .setMessage(site.url + "\n\n내장 웹 화면을 시작하지 못했습니다. 휴대전화나 PC에서 공식 사이트를 이용해 주세요.")
                .setPositiveButton("확인", null)
                .show()
        }
    }

    private fun driverPage() =
        shell("가상 운전자 프로필·디지털키") {
            add(text("OEM 디지털키·인식 API는 사용하지 않습니다. 운전자와 도움받는 동승자는 별도로 선택합니다.", 21f, muted), 12)
            add(
                text(
                    "선택된 가상 운전자: ${engine.profiles.find { it.id==engine.driverProfileId }?.let(engine::alias) ?: "선택 없음"}",
                    24f,
                    accent,
                    true,
                    R.id.x_driver_status,
                ),
                18,
            )
            if (engine.restrictions.complexBlocked) {
                add(text("플랫폼 제한 중에는 설정을 변경할 수 없습니다.", 21f, warning), 16)
                return@shell
            }
            engine.profiles.forEach { p ->
                add(
                    button(
                        engine.alias(p) + " 운전자 선택 시연",
                        id =
                            when (p.id) {
                                "DEMO-P-1" -> R.id.x_driver_1
                                "DEMO-P-2" -> R.id.x_driver_2
                                else -> View.NO_ID
                            },
                        primary = false,
                    ) {
                        mutate { engine.selectDriver(p.id) }
                    },
                    10,
                )
            }
            add(
                button("운전자 선택 해제", R.id.x_driver_clear, false) {
                    mutate { engine.selectDriver(null) }
                },
                16,
            )
        }

    private fun cooperationPage() =
        shell("주변 차량 협력 · 오프라인 시연") {
            add(text("가상 위치와 가상 차량만 사용합니다. 실제 출동·운송·기관 승인·등화 제어는 없습니다.", 20f, muted), 12)
            val session = engine.current
            if (session?.active != true) {
                add(text("먼저 연결 시연을 시작하고 가상 위치 공유를 선택하세요.", 23f), 16)
                add(
                    button("119 연결 시연 시작", R.id.x_coop_intro) {
                        start(ConnectionTarget.EMERGENCY_119, catalog.scenario("S14"))
                    },
                    18,
                )
                return@shell
            }
            if (session.target == ConnectionTarget.COUNSEL_109) {
                add(text("109 상담은 프라이버시를 우선합니다. 별도 긴급 도움이 필요하면 상단 긴급 연결을 선택하세요.", 23f), 16)
                return@shell
            }
            if (ShareField.LOCATION !in session.shared || !engine.locationAvailable) {
                add(text("협력 요청에는 가상 위치 공유와 별도 참여 동의가 필요합니다. 의료정보를 자동 제공하지 않습니다.", 23f), 16)
                add(button("가상 위치 공유 선택", R.id.x_share_location) { go(Page.LOCATION) }, 18)
                return@shell
            }
            val coop = engine.cooperation
            val s = coop.snapshot
            if (s == null) {
                var consent = false
                add(
                    check("가상 위치로 주변 차량 도움 요청 시연에 동의", false, R.id.x_coop_consent) { v ->
                        consent = v
                        listOf(R.id.x_coop_case_early, R.id.x_coop_case_ems, R.id.x_coop_case_none)
                            .forEach { host.findViewById<Button>(it)?.isEnabled = v }
                    },
                    16,
                )
                coop.cases.forEach { c ->
                    add(
                        button(
                            c.title,
                            when (c.id) {
                                "EARLY_HELP" -> R.id.x_coop_case_early
                                "EMS_FIRST" -> R.id.x_coop_case_ems
                                else -> R.id.x_coop_case_none
                            },
                            enabled = consent,
                        ) {
                            mutate { coop.request(c.id, consent) }
                        },
                        12,
                    )
                }
                add(text("도착·조율 시간은 고정 실험 가정입니다. 실제 시연 경과와 구분하며 생존 효과를 측정하지 않습니다.", 19f, muted), 16)
                return@shell
            }
            val state =
                when (s.state) {
                    "REQUESTED" -> "도움 요청됨"
                    "REVIEWED" -> "가상 119 역할 상황 확인"
                    "INVITED" -> "가상 차량 참여 초대"
                    "ACCEPTED" -> "참여 차량이 자발적으로 수락"
                    "AUTHORIZED" -> "가상 응급지원 임무 부여"
                    "ON_SCENE" -> "가상 현장 지원 중"
                    "HANDED_OVER" -> "구급대 인계 시연 완료"
                    "EXPIRED" -> "임무 만료"
                    "CANCELLED" -> "요청 취소"
                    else -> "협력 종료·철회"
                }
            add(text(state, 26f, accent, true, R.id.x_coop_status), 18)
            add(
                text(
                    "${s.title}\n${s.vehicleId ?: "선택 차량 없음"}\n${s.missionId ?: "임무 부여 전"}",
                    20f,
                    muted,
                ),
                10,
            )
            add(
                text(
                    "가상 임무 남은 시간 ${s.missionRemaining}초 · 실제 시연 경과 ${coop.elapsedSeconds}초",
                    20f,
                    warning,
                    false,
                    R.id.x_coop_time,
                ),
                12,
            )
            if (s.supportSeconds != null) {
                val gain = s.baselineSeconds - s.supportSeconds
                add(
                    text(
                        "고정 가정: 구급대 ${s.baselineSeconds}초 / 조율+협력차 ${s.supportSeconds}초\n" +
                            if (gain > 0) "가정상 선행 가능 $gain 초 · 생존 효과 측정 아님"
                            else "구급대가 더 빠르거나 동일한 대조 조건 · 기존 대응 유지",
                        21f,
                    ),
                    14,
                )
            }
            when (s.state) {
                "REQUESTED" ->
                    add(
                        button("가상 119 역할: 상황 확인", R.id.x_coop_review) { mutate { coop.review() } },
                        18,
                    )
                "REVIEWED" -> {
                    add(text("참여 동의·교육·가용 조건을 확인합니다. 가까워도 조건 미충족 차량은 초대하지 않습니다.", 20f, muted), 16)
                    for (v in coop.vehicles) {
                        val eligible = coop.eligible(v.id)
                        add(
                            text(
                                v.name +
                                    " · " +
                                    if (eligible) "참여 가능 · 이동 가정 ${v.travelSeconds}초"
                                    else "참여 불가 · 동의/교육/가용 조건 미충족",
                                20f,
                            ),
                            12,
                        )
                        if (eligible)
                            add(
                                button(
                                    "${v.name} 초대",
                                    if (v.id == "DEMO-HELPER-A") R.id.x_coop_invite_a
                                    else R.id.x_coop_invite_d,
                                    false,
                                ) {
                                    mutate { coop.invite(v.id) }
                                },
                                8,
                            )
                    }
                    if (coop.vehicles.none { coop.eligible(it.id) })
                        add(text("참여 가능한 가상 차량 없음 · 119 연결 시연과 기존 대응을 유지합니다.", 23f, warning), 18)
                }
                "INVITED" -> {
                    add(
                        button("가상 협력 차량 역할: 자발적 수락", R.id.x_coop_accept) {
                            mutate { coop.respond(true) }
                        },
                        18,
                    )
                    add(
                        button("참여 거절", R.id.x_coop_decline, false) {
                            mutate { coop.respond(false) }
                        },
                        12,
                    )
                }
                "ACCEPTED" ->
                    add(
                        button("가상 119 역할: 300초 임무 부여", R.id.x_coop_authorize) {
                            mutate { coop.authorize() }
                        },
                        18,
                    )
                "AUTHORIZED",
                "ON_SCENE" -> {
                    val beacon =
                        when (s.beaconState) {
                            "OFF" -> "표시 승인 전"
                            "REQUESTED" -> "표시 요청·상황 확인 대기"
                            "REVIEWING" -> "표시 요청 검토 중"
                            "APPROVED" -> "가상 한시 승인 · 시작 전"
                            "ACTIVE" -> "화면 표시 모형 동작 중"
                            "REJECTED" -> "표시 요청 거절"
                            "EXPIRED" -> "표시 승인 만료"
                            else -> "표시 승인 철회"
                        }
                    add(
                        text(
                            "${s.beaconAuthority ?: "기관 미선택"} 역할 · $beacon",
                            24f,
                            accent,
                            true,
                            R.id.x_coop_beacon_status,
                        ),
                        20,
                    )
                    add(
                        text(
                            "표시 승인 남은 시간 ${s.beaconRemaining}초",
                            20f,
                            warning,
                            false,
                            R.id.x_coop_beacon_time,
                        ),
                        10,
                    )
                    when (s.beaconState) {
                        "OFF",
                        "REJECTED",
                        "EXPIRED",
                        "REVOKED" -> {
                            add(
                                button("119 역할에 별도 표시 승인 요청", R.id.x_coop_request_119) {
                                    mutate { coop.requestBeacon("119") }
                                },
                                14,
                            )
                            add(
                                button("112 역할에 별도 표시 승인 요청", R.id.x_coop_request_112, false) {
                                    mutate { coop.requestBeacon("112") }
                                },
                                12,
                            )
                        }
                        "REQUESTED" ->
                            add(
                                button(
                                    "가상 ${s.beaconAuthority} 역할: 비상상황 확인",
                                    R.id.x_coop_beacon_review,
                                ) {
                                    mutate { coop.reviewBeacon(s.beaconAuthority!!) }
                                },
                                16,
                            )
                        "REVIEWING" -> {
                            add(
                                button(
                                    "가상 ${s.beaconAuthority} 역할: 최대 120초 승인",
                                    R.id.x_coop_beacon_approve,
                                ) {
                                    mutate { coop.decideBeacon(s.beaconAuthority!!, true) }
                                },
                                16,
                            )
                            add(
                                button("가상 표시 요청 거절", R.id.x_coop_beacon_reject, false) {
                                    mutate { coop.decideBeacon(s.beaconAuthority!!, false) }
                                },
                                12,
                            )
                        }
                        "APPROVED" ->
                            add(
                                button(
                                    "승인된 화면 표시 모형 시작",
                                    R.id.x_coop_beacon_start,
                                    enabled = !engine.restrictions.videoBlocked && inForeground,
                                ) {
                                    mutate { coop.activateBeacon() }
                                },
                                16,
                            )
                    }
                    if (coop.canDisplay())
                        add(
                            text(
                                    "응급지원 차량 · DEMO\n화면 표시 모형 · 실제 차량 등화 아님",
                                    28f,
                                    Color.WHITE,
                                    true,
                                    R.id.x_coop_preview,
                                )
                                .apply {
                                    gravity = Gravity.CENTER
                                    minHeight = dp(110)
                                    setPadding(dp(16), dp(16), dp(16), dp(16))
                                    background = shape(Color.rgb(151, 36, 25))
                                },
                            16,
                        )
                    if (s.beaconState in setOf("REQUESTED", "REVIEWING", "APPROVED", "ACTIVE"))
                        add(
                            button("표시 승인 철회·모형 중단", R.id.x_coop_beacon_revoke, false) {
                                mutate { coop.revokeBeacon() }
                            },
                            12,
                        )
                    add(
                        text("해당 가상 차량·임무에 한정한 역할 시연입니다. 실제 비상차량 자격·통행 권한은 발생하지 않습니다.", 18f, muted),
                        16,
                    )
                    if (s.state == "AUTHORIZED")
                        add(
                            button("가상 차량: 현장 도착", R.id.x_coop_arrive, false) {
                                mutate { coop.arrive() }
                            },
                            16,
                        )
                    else
                        add(
                            button("가상 119 역할: 구급대 인계 완료", R.id.x_coop_handover) {
                                mutate { coop.finish("HANDED_OVER") }
                            },
                            16,
                        )
                }
            }
            if (s.state in CooperationPolicy.terminal) {
                add(text("임무와 화면 표시를 종료했습니다. 기존 119 연결 시연은 별도로 계속됩니다.", 22f), 18)
                add(
                    button("다른 협력 실험", R.id.x_coop_restart) { mutate { coop.attach(session.id) } },
                    16,
                )
            } else
                add(
                    button("협력 요청 취소·전체 표시 중단", R.id.x_coop_cancel, false) {
                        mutate { coop.finish("CANCELLED") }
                    },
                    18,
                )
        }

    private fun vehicleContextPage() =
        shell("차량 상태") {
            val snapshot = vehicleContext.snapshot
            val now = SystemClock.elapsedRealtime()
            add(text(snapshot.origin.label, 23f, accent, true, R.id.x_vctx_source), 12)
            add(
                text(
                    if (vehicleContext.busy) "조회 중 · 긴급 요청은 계속 이용할 수 있습니다."
                    else "읽기 전용 · 30초 이내 조회값만 이용",
                    19f,
                    muted,
                    false,
                    R.id.x_vctx_state,
                ),
                8,
            )
            add(
                button(
                    if (vehicleContext.busy) "차량 조회 중" else "차량에서 새로 조회",
                    R.id.x_vctx_refresh,
                    enabled = !vehicleContext.busy,
                ) {
                    vehicleContext.refresh()
                },
                16,
            )
            add(
                check("이 화면에서 5초마다 확인", vehicleContext.monitoring, R.id.x_vctx_watch) {
                    vehicleContext.monitor(it)
                    requestRender()
                },
                12,
            )
            add(
                panel {
                    VehicleMetric.entries.forEach { metric ->
                        val value = snapshot.samples[metric]
                        add(
                            text(
                                metric.label + " · " + (value?.display(now) ?: "아직 조회하지 않음"),
                                23f,
                                ink,
                                true,
                                metricViewId(metric),
                            ),
                            12,
                        )
                    }
                    add(text(vehicleAge(), 18f, muted, false, R.id.x_vctx_age), 12)
                },
                16,
            )
            add(text("에어백·도어 신호로 사고, 탑승자, 환자 상태를 판정하지 않습니다.", 18f, muted), 12)
            val canShare =
                engine.current?.active == true &&
                    engine.current?.target != ConnectionTarget.COUNSEL_109 &&
                    snapshot.usable(now).isNotEmpty()
            add(
                check("표시된 최신 차량 상태를 앱 내 상황실에 전달", vehicleConsent, R.id.x_vctx_consent, canShare) {
                    vehicleConsent = it
                    requestRender()
                },
                18,
            )
            add(
                button("선택한 차량 상태 전달", R.id.x_vctx_share, enabled = canShare && vehicleConsent) {
                    mutate {
                        engine.shareVehicleContext(
                            snapshot,
                            vehicleConsent,
                            SystemClock.elapsedRealtime(),
                        )
                        vehicleConsent = false
                    }
                },
                12,
            )
            if (engine.current?.active != true)
                add(text("119·112 연결 후 전달할 수 있습니다. 차량 조회 없이도 바로 도움을 요청할 수 있습니다.", 19f, muted), 10)
            else if (engine.current?.target == ConnectionTarget.COUNSEL_109)
                add(text("109 상담에는 차량 상태를 전달하지 않습니다.", 19f, muted), 10)
            val shared = engine.vehicleContextShare?.takeIf { it.fresh(now) }
            add(
                text(
                    shared?.let { "앱 내 상황실 수신\n" + it.summary(now) } ?: "전달한 차량 상태 없음",
                    20f,
                    if (shared != null) accent else muted,
                    false,
                    R.id.x_vctx_receipt,
                ),
                16,
            )
            if (shared != null)
                add(
                    button("전달 철회", R.id.x_vctx_revoke, false) {
                        mutate {
                            engine.revokeVehicleContext()
                            vehicleConsent = false
                        }
                    },
                    12,
                )
            add(text("이 전달값은 앱 안에서만 사용하며 PC 게시·저장·실제 기관 전송에 포함하지 않습니다.", 18f, muted), 12)
            add(button("배터리 잔량으로 EV 계산", R.id.x_vctx_power, false) { go(Page.POWER) }, 18)
            add(
                panel {
                    add(text("실차 없이 확인하기", 23f, ink, true))
                    add(text("아래 선택은 SDK 조회값과 분리된 예시입니다.", 18f, muted), 8)
                    add(
                        row(
                            button("일반 상태 예시", R.id.x_vctx_normal, false) {
                                vehicleContext.example("NORMAL")
                            },
                            button("신호 변화 예시", R.id.x_vctx_incident, false) {
                                vehicleContext.example("INCIDENT")
                            },
                        ),
                        12,
                    )
                    add(
                        row(
                            button("값 없음 예시", R.id.x_vctx_missing, false) {
                                vehicleContext.example("NO_DATA")
                            },
                            button("오래된 값 예시", R.id.x_vctx_stale, false) {
                                vehicleContext.example("STALE")
                            },
                        ),
                        12,
                    )
                },
                24,
            )
            if (engine.current?.active == true)
                add(button("연결 화면으로", R.id.x_return_session, false) { go(Page.SESSION) }, 18)
        }

    private fun metricViewId(metric: VehicleMetric) =
        when (metric) {
            VehicleMetric.DRIVER_DOOR -> R.id.x_vctx_door
            VehicleMetric.BATTERY_SOC -> R.id.x_vctx_soc
            VehicleMetric.DRIVER_AIRBAG -> R.id.x_vctx_airbag
        }

    private fun vehicleAge(): String {
        val now = SystemClock.elapsedRealtime()
        val samples = vehicleContext.snapshot.samples.values
        if (samples.isEmpty()) return "조회 시각 없음"
        val ages =
            samples
                .filter { it.status == DiagnosticStatus.READ }
                .map { maxOf(0, now - it.receivedAt) / 1000 }
        return if (ages.isEmpty()) "확인 가능한 값 없음 · 예시로 자동 대체하지 않음"
        else "수신 후 ${ages.maxOrNull()}초 · 유효 ${vehicleContext.snapshot.usable(now).size}/3개"
    }

    private fun updateVehicleAge() {
        val now = SystemClock.elapsedRealtime()
        val snapshot = vehicleContext.snapshot
        host.findViewById<TextView>(R.id.x_vctx_age)?.text = vehicleAge()
        snapshot.samples.forEach { (metric, sample) ->
            host.findViewById<TextView>(metricViewId(metric))?.text =
                host.getString(R.string.vehicle_metric_caption, metric.label, sample.display(now))
        }
        val allowed =
            engine.current?.active == true &&
                engine.current?.target != ConnectionTarget.COUNSEL_109 &&
                snapshot.usable(now).isNotEmpty()
        host.findViewById<Button>(R.id.x_vctx_share)?.isEnabled = allowed && vehicleConsent
        host.findViewById<CheckBox>(R.id.x_vctx_consent)?.isEnabled = allowed
    }

    private fun powerSocSource(): String =
        powerSocReading?.let { sample ->
            if (sample.fresh(SystemClock.elapsedRealtime()))
                "${sample.origin.label} · 수신 후 ${(SystemClock.elapsedRealtime()-sample.receivedAt)/1000}초"
            else "불러온 SoC 만료·해제 · 다시 조회하거나 수동 입력을 선택하세요."
        } ?: "SoC 수동 입력 · 차량 조회값 아님"

    private fun powerPage() =
        shell("차량 안전 준비 · EV 전력 계산") {
            val editable = !engine.restrictions.complexBlocked
            add(
                text(
                    "SoC만 조회값을 불러올 수 있습니다. 용량·V2L 지원·효율·부하는 수동 가정이며 의료·생명유지장치 전원 공급을 보장하지 않습니다.",
                    20f,
                    muted,
                ),
                12,
            )
            val available = vehicleContext.snapshot.soc(SystemClock.elapsedRealtime())
            add(text(powerSocSource(), 20f, warning, false, R.id.x_ev_soc_source), 12)
            add(
                button(
                    "최근 차량 SoC 불러오기",
                    R.id.x_ev_import_soc,
                    enabled = editable && available != null,
                ) {
                    val sample = vehicleContext.snapshot.soc(SystemClock.elapsedRealtime())
                    if (sample != null) {
                        soc = requireNotNull(sample.value)
                        powerSocReading = sample.copy()
                    }
                    requestRender()
                },
                12,
            )
            add(
                row(
                    button("차량 상태 조회", R.id.x_vehicle_context, false) { go(Page.VEHICLE_CONTEXT) },
                    button("SoC 수동 입력", R.id.x_ev_manual_soc, false) {
                        powerSocReading = null
                        requestRender()
                    },
                ),
                12,
            )
            add(
                check("가상 전기차", electric, R.id.x_ev_electric, editable) {
                    electric = it
                    requestRender()
                },
                14,
            )
            add(
                check("V2L 지원 가정", v2lSupported, R.id.x_ev_support, editable && electric) {
                    v2lSupported = it
                    requestRender()
                },
                6,
            )
            fun slider(
                label: String,
                value: Double,
                max: Int,
                scale: Double,
                id: Int,
                changed: (Double) -> Unit,
            ) {
                val caption = text("$label: ${"%.1f".format(Locale.ROOT,value)}", 21f)
                val bar =
                    SeekBar(host).apply {
                        this.id = id
                        this.max = max
                        progress = (value / scale).toInt().coerceIn(0, max)
                        isEnabled = editable && electric && v2lSupported
                        minimumHeight = dp(LifeLinkUiStyle.touchHeight)
                        contentDescription = label
                    }
                bar.setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(b: SeekBar?, n: Int, user: Boolean) {
                            if (user) {
                                changed(n * scale)
                                caption.text =
                                    host.getString(
                                        R.string.value_caption,
                                        label,
                                        "%.1f".format(Locale.ROOT, n * scale),
                                    )
                                updatePowerResult()
                            }
                        }

                        override fun onStartTrackingTouch(b: SeekBar?) {}

                        override fun onStopTrackingTouch(b: SeekBar?) {}
                    }
                )
                add(caption, 12)
                add(bar)
            }
            slider("배터리 용량 가정 (kWh)", capacity, 160, 1.0, R.id.x_ev_capacity) { capacity = it }
            slider("SoC (%) · 조절하면 수동 입력", soc, 100, 1.0, R.id.x_ev_soc) {
                soc = it
                powerSocReading = null
            }
            slider("보존 SOC (%)", reserve, 100, 1.0, R.id.x_ev_reserve) { reserve = it }
            slider("변환 효율 (%)", efficiency * 100, 100, 1.0, R.id.x_ev_efficiency) {
                efficiency = it / 100.0
            }
            slider("기기 소비전력 가정 (W)", load, 360, 10.0, R.id.x_ev_load) { load = it }
            add(text(powerResult(), 24f, accent, true, R.id.x_power_result), 18)
            add(
                text(
                    "사용가능량 = 용량 × max(0, SOC−보존SOC)/100 × 효율\n예상 시간 = 사용가능량 / (부하W/1000)\n가정 출력 한도 3,600W · 실제 차량 제어 없음",
                    18f,
                    muted,
                ),
                14,
            )
            if (!editable) add(text("플랫폼 제한 중에는 계산 입력을 변경할 수 없습니다.", 20f, warning), 12)
            add(button("실제 Vehicle SDK 진단", R.id.x_sdk, false) { legacy("vehicle") }, 20)
        }

    private fun powerResult(): String {
        if (powerSocReading?.fresh(SystemClock.elapsedRealtime()) == false)
            return "불러온 SoC가 유효하지 않아 계산을 보류합니다.\n차량 상태를 다시 확인하거나 SoC 수동 입력을 선택하세요."
        val p =
            V2lCalculator.estimate(electric, v2lSupported, capacity, soc, reserve, efficiency, load)
        return (p.availableKwh?.let { "사용가능량 가정 %.2f kWh".format(Locale.ROOT, it) }
            ?: "계산할 수 없는 조건") +
            (p.hours?.let { "\n예상 사용 시간 %.2f시간".format(Locale.ROOT, it) } ?: "") +
            (p.issue?.let { "\n$it" } ?: "\n가정값이며 실제 사용 보장이 아닙니다.")
    }

    private fun updatePowerResult() {
        host.findViewById<TextView>(R.id.x_power_result)?.text = powerResult()
        host.findViewById<TextView>(R.id.x_ev_soc_source)?.text = powerSocSource()
        host.findViewById<Button>(R.id.x_ev_import_soc)?.isEnabled =
            vehicleContext.snapshot.soc(SystemClock.elapsedRealtime()) != null &&
                !engine.restrictions.complexBlocked
    }

    private class SyntheticFrameView(
        context: android.content.Context,
        private val source: VideoSource,
        private val frame: Int,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            contentDescription = "${source.label} · 합성 도형 장면 ${frame+1} · 실제 카메라 아님"
            isFocusable = true
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthSpec),
                resolveSize((180 * resources.displayMetrics.density).toInt(), heightSpec),
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(16, 32, 51))
            paint.color = Color.rgb(85, 225, 208)
            val x = width * (.2f + .16f * frame)
            canvas.drawCircle(x, height * .5f, height * .17f, paint)
            paint.color = Color.rgb(255, 210, 140)
            canvas.drawRect(width * .68f, height * .28f, width * .88f, height * .70f, paint)
            paint.color = Color.WHITE
            paint.textSize =
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    20f,
                    resources.displayMetrics,
                )
            canvas.drawText("${source.label} · 합성 장면 ${frame+1}", 24f, height - 24f, paint)
        }
    }
}
