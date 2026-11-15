package com.lifelink119.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.widget.*
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.R
import com.lifelink119.app.core.emergency.*
import com.lifelink119.app.data.*
import com.lifelink119.app.vehicle.PleosEmergencyLocationProvider

/** Small screen group; policy and SDK ownership remain outside the existing large UI class. */
class EmergencySupportUi(
    private val host: Activity,
    private val controller: EmergencySupportController,
    private val restrictions: () -> Boolean,
    private val active: () -> Boolean,
    private val authenticated: () -> Boolean,
    private val authenticate: (() -> Unit) -> Unit,
    private val render: () -> Unit,
    private val returnToCall: () -> Unit,
    private val openManual: () -> Unit,
    private val restore: (EmergencyCheckpoint) -> Unit,
    private val openMedical: () -> Unit,
    private val medicalReadout: () -> String?,
    private val removeMedical: () -> Unit,
) {
    enum class Screen {
        OVERVIEW,
        PACKET,
        CALL,
        AI,
        OPTIONS,
        DEMO,
    }

    private var screen = Screen.OVERVIEW
    private val style = LifeLinkUiStyle
    private val preferences = VoiceAiPreferences(host)
    private val network = EmergencyNetwork { preferences.externalAllowed }
    private var simulateLlmFailure = false
    private val llm =
        object : LlmProvider {
            override fun suggest(request: LlmRequest, done: (String?) -> Unit) {
                if (simulateLlmFailure) done(null) else network.suggest(request, done)
            }

            override fun interpret(request: VoiceMeaningRequest, done: (ObservationCode?) -> Unit) {
                if (simulateLlmFailure) done(null) else network.interpret(request, done)
            }

            override fun cancel() = network.cancel()
        }
    private val voice: EmergencyVoice by lazy {
        EmergencyVoice(host, ::voiceAllowed) {
            conversation.stop()
            message = "통화 또는 오디오 사용 변경으로 음성 대화를 멈췄습니다."
            render()
        }
    }
    private val speech: DemoEmergencySpeech by lazy { DemoEmergencySpeech(voice) { render() } }
    private val conversation: EmergencyVoiceConversation by lazy {
        EmergencyVoiceConversation(controller.ai, speech, llm, ::voiceAllowed, controller::answer) {
                render()
            }
            .apply {
                onCallStatement = { state ->
                    controller.confirm(state)
                    this@EmergencySupportUi.message = "${state.label} · 사용자가 말로 확인한 내용입니다."
                }
            }
    }
    private var manualChoices = false
    private var callChoices = false
    private val callCheck = EmergencyCallCheck()
    private val checkpoints = EmergencyCheckpointStore(host)
    private var message = ""
    private val externalConsent
        get() = preferences.externalAllowed

    private var exportConsent = false
    private var packetDetails = false
    private var pcConsent = false
    private var publishedId: String? = null
    private var pcBusy = false
    private var noCallConfirmed = false
    private var demoStopped = false
    private var token = 0L
    private var attachedId: String? = null
    private var pendingPermission = false
    private var pendingVoiceStart = false
    private val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var clip: ClipData? = null

    private fun dp(x: Int) = (x * host.resources.displayMetrics.density).toInt()

    private fun col() = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }

    private fun label(
        value: String,
        size: Float = 21f,
        bold: Boolean = false,
        color: Int = style.ink,
    ) =
        TextView(host).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = style.typeface(bold)
            setLineSpacing(dp(4).toFloat(), 1f)
            isSaveEnabled = false
        }

    private fun LinearLayout.add(view: View, gap: Int = 12) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
    }

    private fun button(
        text: String,
        id: Int,
        primary: Boolean = false,
        enabled: Boolean = true,
        action: () -> Unit,
    ) =
        Button(host).apply {
            this.id = id
            this.text = text
            textSize = 22f
            isAllCaps = false
            minHeight = dp(96)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            typeface = style.typeface(true)
            val fill =
                if (id == R.id.x_es_retry) style.emergency
                else if (primary) style.accent else style.control
            background = style.actionBackground(host, fill, 20)
            setTextColor(style.actionText(style.actionForeground(fill)))
            isEnabled = enabled
            filterTouchesWhenObscured = true
            isSaveEnabled = false
            setOnClickListener {
                runCatching(action).onFailure { message = "현재 상태와 동의를 확인하세요." }
                render()
            }
        }

    private fun check(text: String, checked: Boolean, id: Int, action: (Boolean) -> Unit) =
        CheckBox(host).apply {
            this.id = id
            this.text = text
            textSize = 21f
            minHeight = dp(64)
            setTextColor(style.ink)
            isChecked = checked
            isSaveEnabled = false
            filterTouchesWhenObscured = true
            setOnCheckedChangeListener { _, value ->
                action(value)
                render()
            }
        }

    private fun row(vararg views: View) =
        LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            views.forEachIndexed { index, v ->
                addView(
                    v,
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) marginStart = dp(12)
                    },
                )
            }
        }

    private fun card(
        title: String,
        body: String,
        icon: Int = R.drawable.ic_lifelink_shield,
        bodyId: Int = View.NO_ID,
    ): View =
        col().apply {
            background = style.shape(host, style.medicalFill, 24)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            add(
                LinearLayout(host).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        ImageView(host).apply {
                            setImageResource(icon)
                            setColorFilter(style.medicalInk)
                        },
                        LinearLayout.LayoutParams(dp(40), dp(40)),
                    )
                    addView(
                        label(title, 28f, true, style.medicalInk),
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) },
                    )
                }
            )
            add(
                label(
                        body,
                        size =
                            if (bodyId in setOf(R.id.x_es_question, R.id.x_es_call_question)) 30f
                            else 21f,
                        color = style.medicalInk,
                    )
                    .apply { id = bodyId }
            )
        }

    private fun answer(code: ObservationCode) {
        token++
        if (conversation.active) conversation.accept(code)
        else {
            voice.stop()
            controller.answer(code)
        }
        host.findViewById<ScrollView>(R.id.x_scroll)?.scrollTo(0, 0)
    }

    private fun blocked() =
        restrictions() || controller.scenario == EmergencyDemoScenario.HIGHWAY && !demoStopped

    fun open() {
        cancelPendingStart()
        screen = Screen.OVERVIEW
        message = ""
        exportConsent = false
        packetDetails = false
        render()
    }

    fun showPacket() {
        navigate(Screen.PACKET)
    }

    private fun currentReadout(): String =
        controller.readout(true) + medicalReadout()?.let { "\n\n$it" }.orEmpty()

    fun back(): Boolean {
        cancelPendingStart()
        conversation.stop()
        speech.end()
        if (screen == Screen.OVERVIEW) return false
        screen = Screen.OVERVIEW
        host.findViewById<ScrollView>(R.id.x_scroll)?.scrollTo(0, 0)
        exportConsent = false
        message = ""
        return true
    }

    fun suspend() {
        token++
        conversation.stop()
        speech.end()
        network.cancel()
        controller.ai.cancelExternal()
        pendingVoiceStart = false
        exportConsent = false
        noCallConfirmed = false
        clearClipboard()
    }

    fun medicalAuthenticationExpired() {
        exportConsent = false
        clearClipboard()
        if (controller.backgroundLocked) suspend()
    }

    fun onStateChanged() {
        if (attachedId != controller.snapshot?.incidentId) {
            attachedId = controller.snapshot?.incidentId
            suspend()
            pcConsent = false
            demoStopped = false
            callChoices = false
            callCheck.reset()
            removeMedical()
            revoke()
        }
        if (!voiceAllowed()) conversation.stop()
        resumePendingVoice()
    }

    fun clear() {
        suspend()
        simulateLlmFailure = false
        screen = Screen.OVERVIEW
        demoStopped = false
        pcConsent = false
        checkpoints.clear()
        revoke()
    }

    fun close() {
        suspend()
        voice.close()
        network.close()
    }

    private fun navigate(next: Screen) {
        cancelPendingStart()
        conversation.stop()
        speech.end()
        screen = next
        host.findViewById<ScrollView>(R.id.x_scroll)?.scrollTo(0, 0)
        message = ""
        exportConsent = false
        packetDetails = false
        render()
    }

    private fun retry() {
        conversation.stop()
        noCallConfirmed = false
        controller.attempt()
        message = "연결 재시도 시뮬레이션 · 실제 발신 없음. 실제 상황은 휴대전화/차량 표준 긴급통화로 신고하세요."
    }

    fun view(): View =
        col().apply {
            val call = controller.call ?: return@apply
            if (screen in setOf(Screen.AI, Screen.CALL))
                add(label("${call.connectionStatus.label} · ${call.statusSource.label}", 18f))
            else
                add(
                    card(
                        "응급 요약 · 대기 지원",
                        "${call.connectionStatus.label} · ${call.statusSource.label}\n실제 접수·출동·콜백 예약은 확인할 수 없습니다.",
                    )
                )
            if (screen !in setOf(Screen.AI, Screen.CALL))
                add(
                    row(
                        button("119 다시 연결 · 시연", R.id.x_es_retry, true) { retry() },
                        button("연결 화면", R.id.x_es_return) {
                            conversation.stop()
                            returnToCall()
                        },
                    )
                )
            if (message.isNotEmpty())
                add(
                    label(message).apply {
                        id = R.id.x_es_message
                        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                    }
                )
            if (controller.backgroundLocked) {
                add(
                    card(
                        "대기 기록이 잠겨 있습니다",
                        "이전 답변은 인증 후 다시 볼 수 있습니다. 신고 재시도와 공개 응급 매뉴얼은 계속 이용할 수 있습니다.",
                    )
                )
                add(
                    button("인증하고 대기 기록 열기", R.id.x_es_unlock) {
                        authenticate { controller.unlock(authenticated()) }
                    }
                )
                add(button("공개 응급 매뉴얼", R.id.x_es_manual) { openManual() })
                return@apply
            }
            if (blocked() && screen !in setOf(Screen.OVERVIEW, Screen.AI)) screen = Screen.OVERVIEW
            when (screen) {
                Screen.OVERVIEW -> overview(this)
                Screen.PACKET -> packet(this)
                Screen.CALL -> callState(this)
                Screen.AI -> companion(this)
                Screen.OPTIONS -> options(this)
                Screen.DEMO -> demo(this)
            }
        }

    private fun overview(out: LinearLayout) =
        with(out) {
            add(
                row(
                    button("응급상황 요약", R.id.x_es_packet, enabled = !blocked()) {
                        navigate(Screen.PACKET)
                    },
                    button("AI 음성 동행 시작", R.id.x_es_ai, true) { beginVoice() },
                )
            )
            if (blocked()) {
                add(label("주행·플랫폼 제한 중 · 상세 입력은 안전하게 정차한 뒤 이용하세요. 재신고는 제한하지 않습니다."))
                if (!restrictions() && controller.scenario == EmergencyDemoScenario.HIGHWAY)
                    add(button("시뮬레이션: 정차 상황으로 전환", R.id.x_es_stopped) { demoStopped = true })
                return@with
            }
            add(button("연결 지연 · 콜백 · 신고 진행 확인", R.id.x_es_progress) { navigate(Screen.CALL) })
            add(
                label(
                    "최근 연결 시도 ${EmergencyPacket.time(controller.call?.callAttemptTime)}\n블루링크와 이미 연결 중이어도 요약을 확인할 수 있습니다. 중복 신고 여부를 자동 판정하지 않습니다."
                )
            )
            add(button("수집 동의 · 보관 · 음성/외부 AI 설정", R.id.x_es_options) { navigate(Screen.OPTIONS) })
            if (BuildConfig.DEMO_FEATURES)
                add(button("공모전 시연 시나리오", R.id.x_es_demo) { navigate(Screen.DEMO) })
        }

    private fun packet(out: LinearLayout) =
        with(out) {
            add(label("상담원에게 읽어줄 내용", 28f, true))
            add(label(currentReadout()).apply { id = R.id.x_es_readout })
            if (controller.scenario == null) {
                val included = medicalReadout() != null
                add(
                    button(if (included) "가족 안심콜 정보 제외" else "가족 안심콜 정보 포함", R.id.x_es_medical) {
                        if (included) removeMedical() else openMedical()
                    }
                )
                add(
                    label(
                        "인증 후 환자 한 명을 확인하면 병력·알레르기·복용약·보호자 정보를 이 요약에 포함합니다. 외부 AI로 보내지 않습니다.",
                        18f,
                    )
                )
            }
            add(
                button(if (packetDetails) "차량·출처 상세 접기" else "차량·출처 상세 보기", R.id.x_es_details) {
                    packetDetails = !packetDetails
                }
            )
            if (packetDetails) add(label(controller.summary(true)).apply { id = R.id.x_es_summary })
            add(
                check("이 요약을 클립보드/선택한 가족 앱에 넘기는 데 동의", exportConsent, R.id.x_es_export_consent) {
                    exportConsent = it
                }
            )
            add(
                row(
                    button("신고 내용 복사", R.id.x_es_copy, enabled = exportConsent) {
                        check(exportConsent && !blocked() && !controller.backgroundLocked)
                        val data = ClipData.newPlainText("LifeLink 응급 요약", currentReadout())
                        data.description.extras =
                            android.os.PersistableBundle().apply {
                                putBoolean("android.content.extra.IS_SENSITIVE", true)
                            }
                        clip = data
                        clipboard.setPrimaryClip(data)
                        android.os
                            .Handler(host.mainLooper)
                            .postDelayed({ clearClipboard() }, 60_000)
                        message = "복사했습니다. 다른 앱에 붙여넣은 내용은 사용자가 관리해야 합니다."
                    },
                    button("가족 앱 선택", R.id.x_es_share, enabled = exportConsent) {
                        check(exportConsent && !blocked() && !controller.backgroundLocked)
                        val intent =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, currentReadout())
                        if (intent.resolveActivity(host.packageManager) != null) {
                            host.startActivity(Intent.createChooser(intent, "가족에게 전달할 앱 선택"))
                            message = "앱 선택 화면을 열었습니다. 전달 성공 여부는 확인할 수 없습니다."
                        } else message = "공유를 지원하는 앱이 없습니다. 요약을 읽어주세요."
                    },
                )
            )
            add(
                button("인계 요약만 보기", R.id.x_es_handover) {
                    message = controller.ai.session?.handover().orEmpty()
                }
            )
        }

    private fun callState(out: LinearLayout) =
        with(out) {
            if (callChoices && callCheck.step != EmergencyCallCheck.Step.DONE) {
                add(
                    card(
                        "연결 상태 확인",
                        callCheck.question,
                        R.drawable.ic_lifelink_microphone,
                        R.id.x_es_call_question,
                    )
                )
                fun reply(value: Boolean?) {
                    callCheck.reply(value)?.let { state ->
                        conversation.stop()
                        noCallConfirmed = false
                        controller.confirm(state)
                    }
                    if (callCheck.step == EmergencyCallCheck.Step.DONE) {
                        callChoices = false
                        message = "직접 확인한 내용만 반영했습니다. 콜백 예약·출동을 기관에서 확인한 것은 아닙니다."
                    }
                }
                add(
                    row(
                        button("예", R.id.x_es_call_yes) { reply(true) },
                        button("아니요", R.id.x_es_call_no) { reply(false) },
                        button("잘 모르겠어요", R.id.x_es_call_unknown) { reply(null) },
                    )
                )
                add(button("음성으로 돌아가기", R.id.x_es_call_choices) { callChoices = false })
            } else {
                add(
                    card(
                        "기다리는 동안 함께 확인하겠습니다",
                        "연결 상태도 말로 알려주세요. 확인한 내용만 반영합니다.",
                        R.drawable.ic_lifelink_microphone,
                    )
                )
                add(button("AI 음성 동행 시작", R.id.x_es_ai, true) { beginVoice() })
                add(label("예: “연결이 안 돼요” · “콜백 기다리래요” · “출동 기다리래요”", 20f))
                add(
                    button("화면으로 상태 확인", R.id.x_es_call_choices) {
                        callChoices = true
                        callCheck.reset()
                    }
                )
            }
            add(button("119 다시 연결 · 시연", R.id.x_es_retry, true) { retry() })
            add(label("실제 연결이 어려우면 휴대전화/차량 표준 긴급통화로 다시 신고하세요. 앱은 접수·콜백 예약을 보장하지 않습니다.", 19f))
        }

    private fun voiceAllowed() =
        screen == Screen.AI &&
            active() &&
            noCallConfirmed &&
            !controller.backgroundLocked &&
            controller.call?.connectionStatus != null &&
            controller.call?.connectionStatus !in
                setOf(
                    EmergencyCallStatus.ATTEMPTING,
                    EmergencyCallStatus.OPERATOR,
                    EmergencyCallStatus.CALLBOT,
                    EmergencyCallStatus.ARRIVED,
                )

    fun beginVoice() {
        navigate(Screen.AI)
        startVoice()
        render()
    }

    private fun cancelPendingStart() {
        pendingPermission = false
        pendingVoiceStart = false
    }

    fun microphoneResult(granted: Boolean) {
        if (!pendingPermission) return
        pendingPermission = false
        pendingVoiceStart = granted
        if (!granted) message = "마이크 권한이 필요합니다. 화면으로도 답할 수 있습니다."
        resumePendingVoice()
        render()
    }

    fun foreground() = resumePendingVoice()

    private fun resumePendingVoice() {
        if (
            pendingVoiceStart &&
                active() &&
                screen == Screen.AI &&
                controller.call?.connectionStatus != EmergencyCallStatus.ATTEMPTING
        ) {
            pendingVoiceStart = false
            startVoice()
        }
    }

    private fun startVoice() {
        speech.end()
        // Explicit user confirmation concerns audio use only, never reception/callback/dispatch
        // status.
        noCallConfirmed = true
        if (
            screen == Screen.AI &&
                active() &&
                controller.call?.connectionStatus == EmergencyCallStatus.ATTEMPTING
        ) {
            pendingVoiceStart = true
            message = "연결 시도 중입니다. 통화 음성을 사용할 수 있을 때 시작합니다."
            return
        }
        if (!voiceAllowed()) {
            message = "연결 시도·통화 중에는 음성 대화를 시작하지 않습니다."
            return
        }
        if (!voice.hasPermission()) {
            pendingPermission = true
            voice.requestPermission()
            message = "마이크를 허용하면 질문을 바로 시작합니다."
            return
        }
        manualChoices = false
        message = ""
        voice.initialize()
        conversation.start(externalConsent)
    }

    fun startDemo() {
        if (!BuildConfig.DEMO_FEATURES || blocked() || !active()) return
        navigate(Screen.AI)
        controller.selectScenario(EmergencyDemoScenario.ILLNESS)
        controller.confirm(EmergencyCallStatus.DISPATCH_WAIT)
        noCallConfirmed = true
        manualChoices = false
        speech.begin()
        voice.initialize()
        conversation.start(externalConsent)
        render()
    }

    fun preferenceChanged() {
        cancelPendingStart()
        conversation.stop()
        speech.end()
        network.cancel()
        controller.ai.cancelExternal()
    }

    private fun companion(out: LinearLayout) =
        with(out) {
            val session = requireNotNull(controller.ai.session)
            if (
                !controller.ai.consent ||
                    session.aiSessionState in setOf(SupportStep.OFF, SupportStep.STOPPED)
            ) {
                add(
                    card(
                        "제가 묻고, 말씀으로 답해주세요",
                        "한 번에 질문 하나씩 확인하겠습니다.",
                        R.drawable.ic_lifelink_microphone,
                    )
                )
                add(label("답변과 시각은 이번 대기 중 메모리에만 보관합니다. 녹음·원문은 저장하지 않습니다.", 19f))
                add(button("음성 동행 시작", R.id.x_es_start_ai, true) { startVoice() })
                add(button("화면으로 진행", R.id.x_es_start_text) { useManual() })
                return@with
            }
            if (speech.enabled)
                add(
                    label("자동 음성 시연 · 합성 응답 · 실제 마이크 입력 아님", 20f, true, style.warning).apply {
                        id = R.id.x_ai_demo_badge
                    }
                )
            if (speech.finished && !conversation.active) {
                add(
                    card(
                        "음성 동행 시연 완료 · 인계 요약",
                        session.handover(),
                        R.drawable.ic_lifelink_medical,
                        R.id.x_es_demo_handover,
                    )
                )
                add(button("음성 시연 다시 보기", R.id.x_es_voice_start, true) { startDemo() })
                add(label("예시 답변입니다. 실제 기관으로 전송되지 않습니다.", 19f))
                return@with
            }
            val guidance = VerifiedGuidance.card(session.verifiedGuidanceId)
            if (session.escalationRequired)
                add(
                    card(guidance.title, guidance.text, R.drawable.ic_lifelink_medical).apply {
                        id = R.id.x_es_guidance
                    }
                )
            val urgent = conversation.urgentActionRequired
            if (!urgent) {
                add(
                    card(
                        "AI 응급 대기 동행",
                        conversation.displayQuestion(),
                        R.drawable.ic_lifelink_microphone,
                        R.id.x_es_question,
                    )
                )
            }
            add(
                label(
                        if (speech.enabled && speech.example != null) "예시 음성 응답: ${speech.example}"
                        else conversation.message,
                        22f,
                        true,
                        style.accent,
                    )
                    .apply {
                        id = R.id.x_es_voice_status
                        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                    }
            )
            if (!urgent)
                add(
                    label(
                        if (conversation.manual) "이 질문에 해당하는 답 하나만 선택해 주세요."
                        else "말이 끝나면 “예”, “아니요”, “잘 모르겠어요”로 답하세요.",
                        19f,
                    )
                )
            if (conversation.manual && !urgent) {
                val pending = conversation.pending
                add(
                    row(
                        button("예", R.id.x_es_yes) {
                            if (pending != null) conversation.confirm(true)
                            else conversation.answerShort(true)
                        },
                        button("아니요", R.id.x_es_no) {
                            if (pending != null) conversation.confirm(false)
                            else conversation.answerShort(false)
                        },
                        button("잘 모르겠어요", R.id.x_es_unknown) { conversation.answerShort(null) },
                    )
                )
            }
            if (conversation.active) {
                add(
                    row(
                        button("음성 잠시 멈춤", R.id.x_es_mute) {
                            conversation.stop()
                            speech.end()
                            manualChoices = false
                            message = "마이크와 음성을 멈췄습니다."
                        },
                        button("화면으로 답하기", R.id.x_es_choices) { useManual() },
                    )
                )
            } else {
                add(
                    button(if (urgent) "안내 다시 듣기" else "음성 대화 다시 시작", R.id.x_es_voice_start, true) {
                        startVoice()
                    }
                )
                if (!conversation.manual && !urgent)
                    add(button("화면으로 답하기", R.id.x_es_choices) { useManual() })
            }
            add(button("119 다시 연결 · 시연", R.id.x_es_retry, true) { retry() })
            if (conversation.active)
                add(label("“통화 중이에요”라고 말하면 음성을 멈춥니다. “콜백 기다리래요”처럼 연결 상태도 말로 알릴 수 있습니다.", 18f))
            add(label(VerifiedGuidance.NOTICE, 16f))
            if (!blocked()) add(label(controller.ai.externalState, 16f))
        }

    private fun useManual() {
        cancelPendingStart()
        noCallConfirmed = true
        manualChoices = true
        speech.end()
        conversation.useManual()
        message = ""
    }

    private fun options(out: LinearLayout) =
        with(out) {
            add(
                check(
                    "이번 요청에서 차량·위치 자동 준비에 동의 (기기 안에서만)",
                    controller.collectionConsent,
                    R.id.x_es_collect,
                ) {
                    controller.collection(it)
                    if (
                        it &&
                            controller.scenario == null &&
                            host.checkSelfPermission(PleosEmergencyLocationProvider.PERMISSION) !=
                                PackageManager.PERMISSION_GRANTED
                    )
                        host.requestPermissions(
                            arrayOf(
                                PleosEmergencyLocationProvider.PERMISSION,
                                com.lifelink119.app.vehicle.PleosEmergencyNavigationProvider
                                    .PERMISSION,
                            ),
                            8191,
                        )
                }
            )
            add(label("전경에서 5초마다 조회 · 최근 위치 2분/6개까지만 메모리 보관. 위치·건강정보를 외부 AI나 PC에 자동 전송하지 않습니다."))
            add(
                button(
                    if (controller.busy) "수집 중" else "지금 다시 준비",
                    R.id.x_es_refresh,
                    enabled = controller.collectionConsent && !controller.busy,
                ) {
                    controller.refresh()
                }
            )
            add(
                row(
                    button("탑승자 1명", R.id.x_es_one) { controller.passengerCount(1) },
                    button("탑승자 2명", R.id.x_es_two) { controller.passengerCount(2) },
                )
            )
            add(
                row(
                    button("차량 충돌 상황", R.id.x_es_incident_collision) {
                        controller.incidentType("사용자가 알린 차량 충돌")
                    },
                    button("응급질환 상황", R.id.x_es_incident_illness) {
                        controller.incidentType("사용자가 알린 응급질환")
                    },
                )
            )
            add(
                check(
                    "외부 AI에 질문 단계·한정 증상 의미 코드 전송 (이름·위치·원문·음성 제외)",
                    externalConsent,
                    R.id.x_es_llm_consent,
                ) {
                    preferences.choose(it)
                    if (!it) {
                        conversation.stop()
                        network.cancel()
                        controller.ai.cancelExternal()
                    }
                }
            )
            add(
                button(
                    "외부 AI로 쉬운 문장 선택",
                    R.id.x_es_llm,
                    enabled =
                        externalConsent &&
                            controller.ai.consent &&
                            !controller.ai.session!!.escalationRequired &&
                            BuildConfig.DEMO_FEATURES,
                ) {
                    controller.ai.rephrase(llm, externalConsent) { render() }
                    message = "${controller.ai.externalState} · 결과는 다음 동행 화면에 반영됩니다."
                }
            )
            add(label(controller.ai.externalState).apply { id = R.id.x_es_llm_state })
            if (BuildConfig.DEMO_FEATURES)
                add(
                    button(
                        if (simulateLlmFailure) "LLM 통신 실패 시연 종료" else "LLM 통신 실패 시연",
                        R.id.x_es_llm_fail,
                        enabled =
                            controller.ai.consent && !controller.ai.session!!.escalationRequired,
                    ) {
                        simulateLlmFailure = !simulateLlmFailure
                        controller.ai.rephrase(llm, externalConsent) { render() }
                        message = controller.ai.externalState
                    }
                )
            add(
                button("인증 후 대기 기록 1시간 암호화 보관", R.id.x_es_save, enabled = controller.ai.consent) {
                    authenticate {
                        val snapshot = requireNotNull(controller.snapshot)
                        checkpoints.save(
                            EmergencyCheckpoint(
                                snapshot.incidentTime.value ?: System.currentTimeMillis(),
                                requireNotNull(controller.call),
                                requireNotNull(controller.ai.session),
                            ),
                            authenticated(),
                            true,
                        )
                        message = "선택한 답변·시각만 암호화 보관했습니다. 재시작 후 인증해 복구할 수 있습니다."
                        render()
                    }
                }
            )
            add(
                button("이전 대기 기록 인증 후 복구", R.id.x_es_restore, enabled = checkpoints.available) {
                    authenticate {
                        val value = checkpoints.load(authenticated())
                        if (value != null) restore(value)
                        else message = "보관 기한이 지났거나 읽을 수 없어 기록을 정리했습니다."
                        render()
                    }
                }
            )
            add(
                button("암호화 대기 기록 삭제", R.id.x_es_delete) {
                    checkpoints.clear()
                    message = "저장한 대기 기록을 삭제했습니다."
                }
            )
            add(label("전화·접수 상태는 복구 후 다시 확인합니다. 가족 건강정보는 이 기록에 포함하지 않습니다."))
        }

    private fun demo(out: LinearLayout) =
        with(out) {
            add(label("아래 선택은 모두 합성 시나리오입니다", 27f, true))
            add(button("음성 동행 자동 시연", R.id.x_ai_auto_demo, true) { startDemo() })
            EmergencyDemoScenario.entries.forEachIndexed { i, scenario ->
                add(
                    button(
                        scenario.label,
                        listOf(
                            R.id.x_es_demo_airbag,
                            R.id.x_es_demo_collision,
                            R.id.x_es_demo_illness,
                            R.id.x_es_demo_highway,
                            R.id.x_es_demo_failure,
                        )[i],
                    ) {
                        revoke()
                        demoStopped = false
                        controller.selectScenario(scenario)
                        screen = Screen.OVERVIEW
                    }
                )
            }
            add(
                button("시뮬레이션 해제 · SDK 사용", R.id.x_es_demo_off) {
                    revoke()
                    controller.selectScenario(null)
                    screen = Screen.OVERVIEW
                }
            )
            add(
                check("선택한 합성 시나리오를 PC 모의 상황실에 게시하는 데 동의", pcConsent, R.id.x_es_pc_consent) {
                    pcConsent = it
                    if (!it) revoke()
                }
            )
            add(
                button(
                    "PC 모의 상황실에 현재 상태 게시",
                    R.id.x_es_publish,
                    enabled = pcConsent && controller.scenario != null && !pcBusy,
                ) {
                    pcBusy = true
                    val id = requireNotNull(controller.call).incidentId
                    publishedId = id
                    network.publish(controller, pcConsent) { success ->
                        host.findViewById<ScrollView>(R.id.x_scroll)?.scrollTo(0, 0)
                        pcBusy = false
                        if (!pcConsent && success) revoke()
                        message =
                            if (success) "PC 모의 상황실 게시 완료 · 실제 기관 전송 아님"
                            else "PC 게시 실패 (${network.lastFailureCode}) · 앱 내 요약/동행은 계속 이용 가능합니다."
                        render()
                    }
                }
            )
            add(
                button("PC 게시 철회", R.id.x_es_revoke, enabled = publishedId != null && !pcBusy) {
                    revoke()
                }
            )
        }

    private fun revoke() {
        val id = publishedId ?: return
        network.revoke(id) { ok ->
            host.findViewById<ScrollView>(R.id.x_scroll)?.scrollTo(0, 0)
            if (ok && publishedId == id) publishedId = null
            message =
                if (ok) "PC 게시를 철회했습니다." else "PC 철회 실패 · 서버 복구 후 다시 철회해 주세요. 게시물은 1시간 후 만료됩니다."
            render()
        }
    }

    private fun clearClipboard() {
        val own = clip ?: return
        if (clipboard.primaryClip?.getItemAt(0)?.text == own.getItemAt(0).text)
            clipboard.clearPrimaryClip()
        clip = null
    }
}
