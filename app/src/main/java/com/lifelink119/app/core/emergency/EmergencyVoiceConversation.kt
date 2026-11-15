package com.lifelink119.app.core.emergency

/** Only this finite vocabulary may leave the device. Never sends transcripts or audio. */
enum class VoiceConcept {
    YES,
    NO,
    UNSURE,
    RESPONSE,
    CONSCIOUS,
    EYES,
    OPEN,
    CLOSED,
    BREATH,
    NORMAL,
    DIFFICULT,
    CHEST,
    PAIN,
    BLOOD,
    MUCH,
    FACE,
    ARM,
    SPEECH,
    SUDDEN,
    ACCIDENT,
    INJURY,
    FIRE,
    SMOKE,
    TRAPPED,
    ALONE,
    FAMILY,
    WORSE,
    NONE,
}

data class VoiceMeaningRequest(val step: SupportStep, val concepts: List<VoiceConcept>)

object VoiceAnswers {
    private fun compact(text: String) = text.lowercase().replace(Regex("[\\s.,!?。]"), "")

    fun yesNo(text: String): Boolean? =
        when (compact(text)) {
            "네",
            "예",
            "응",
            "맞아요",
            "맞습니다",
            "그렇습니다",
            "yes" -> true
            "아니요",
            "아니오",
            "아니",
            "아닙니다",
            "아니에요",
            "no" -> false
            else -> null
        }

    fun stop(text: String) =
        compact(text) in setOf("그만", "그만해", "그만해주세요", "중지", "대화중단", "멈춰", "통화할게요", "전화왔어요")

    fun urgent(text: String): ObservationCode? {
        if (text.length > 160) return null
        val s = compact(text)
        // Exact supported statements only: never infer a positive from a substring or negation.
        when (s.removePrefix("네").removePrefix("예")) {
            "불이야",
            "불이났어요",
            "연기가나요",
            "차에불이났어요",
            "갇혔어요" -> return ObservationCode.FIRE
            "상태가나빠졌어요",
            "더나빠졌어요",
            "숨쉬기힘들어요",
            "숨쉬기힘들어해요" -> return ObservationCode.WORSENING
            "의식이없어요",
            "의식없어요",
            "반응이없어요",
            "반응없어요" -> return ObservationCode.CONSCIOUS_NO
            "숨을안쉬어요",
            "숨을못쉬어요",
            "호흡이없어요" -> return ObservationCode.BREATHING_NO
            "피가많이나요",
            "피가멈추지않아요",
            "심한출혈" -> return ObservationCode.BLEEDING
            "가슴이아파요",
            "가슴통증",
            "가슴을아파해요" -> return ObservationCode.CHEST_PAIN
        }
        return null
    }

    fun local(step: SupportStep, text: String): ObservationCode? {
        if (text.length > 160) return null
        urgent(text)?.let {
            return it
        }
        val s = compact(text)
        val yes = yesNo(text)
        val unsure = s in setOf("모르겠어요", "잘모르겠어요", "모릅니다", "몰라요", "확인못했어요")
        if (step == SupportStep.CONSCIOUSNESS)
            return when {
                unsure -> ObservationCode.CONSCIOUS_UNKNOWN
                yes == true || s in setOf("의식있어요", "의식이있어요", "반응해요", "대답해요") ->
                    ObservationCode.CONSCIOUS_YES
                yes == false -> ObservationCode.CONSCIOUS_NO
                else -> null
            }
        if (step == SupportStep.BREATHING)
            return when {
                unsure -> ObservationCode.BREATHING_UNKNOWN
                yes == true || s in setOf("정상적으로숨쉬어요", "평소처럼숨쉬어요") -> ObservationCode.BREATHING_YES
                yes == false -> ObservationCode.BREATHING_NO
                else -> null
            }
        return when (s) {
            "혼자예요",
            "혼자있어요",
            "혼자기다려요" -> ObservationCode.ALONE
            "가족이아파요",
            "동승자가아파요" -> ObservationCode.COMPANION
            "사고로다쳤어요",
            "교통사고로다쳤어요" -> ObservationCode.INJURY
            "모르겠어요",
            "잘모르겠어요" -> ObservationCode.UNKNOWN
            else -> null
        }
    }

    private val vocabulary =
        linkedMapOf(
            VoiceConcept.NO to listOf("아니", "없", "안", "못"),
            VoiceConcept.UNSURE to listOf("모르", "몰라"),
            VoiceConcept.RESPONSE to listOf("반응", "대답"),
            VoiceConcept.CONSCIOUS to listOf("의식"),
            VoiceConcept.EYES to listOf("눈"),
            VoiceConcept.OPEN to listOf("뜨", "떠", "열"),
            VoiceConcept.CLOSED to listOf("감", "닫"),
            VoiceConcept.BREATH to listOf("호흡", "숨"),
            VoiceConcept.NORMAL to listOf("정상", "평소", "규칙"),
            VoiceConcept.DIFFICULT to listOf("힘들", "어려", "곤란"),
            VoiceConcept.CHEST to listOf("가슴"),
            VoiceConcept.PAIN to listOf("아프", "아파", "통증"),
            VoiceConcept.BLOOD to listOf("출혈", "피"),
            VoiceConcept.MUCH to listOf("많", "심하", "심해"),
            VoiceConcept.FACE to listOf("얼굴"),
            VoiceConcept.ARM to listOf("팔"),
            VoiceConcept.SPEECH to listOf("말", "발음"),
            VoiceConcept.SUDDEN to listOf("갑자기", "갑작"),
            VoiceConcept.ACCIDENT to listOf("사고", "충돌"),
            VoiceConcept.INJURY to listOf("다쳤", "부상"),
            VoiceConcept.FIRE to listOf("불", "화재"),
            VoiceConcept.SMOKE to listOf("연기"),
            VoiceConcept.TRAPPED to listOf("갇"),
            VoiceConcept.ALONE to listOf("혼자"),
            VoiceConcept.FAMILY to listOf("가족", "동승자"),
            VoiceConcept.WORSE to listOf("나빠", "악화"),
        )

    fun unchanged(step: SupportStep, text: String) =
        step == SupportStep.WAITING &&
            (yesNo(text) == false || compact(text) in setOf("변화없어요", "달라진점없어요", "아직같아요"))

    fun concepts(text: String): List<VoiceConcept> {
        if (text.length > 160) return emptyList()
        val s = compact(text)
        // This deliberately loses context. A model-selected meaning ALWAYS needs spoken
        // confirmation.
        return vocabulary.filterValues { terms -> terms.any(s::contains) }.keys.take(12)
    }

    fun allowed(step: SupportStep): Set<ObservationCode> =
        when (step) {
            SupportStep.CONSCIOUSNESS ->
                setOf(
                    ObservationCode.CONSCIOUS_YES,
                    ObservationCode.CONSCIOUS_NO,
                    ObservationCode.CONSCIOUS_UNKNOWN,
                )
            SupportStep.BREATHING ->
                setOf(
                    ObservationCode.BREATHING_YES,
                    ObservationCode.BREATHING_NO,
                    ObservationCode.BREATHING_UNKNOWN,
                )
            SupportStep.SYMPTOMS,
            SupportStep.WAITING ->
                ObservationCode.entries.filter { it.group !in setOf("의식", "호흡") }.toSet()
            else -> emptySet()
        }

    /** Lossy model interpretation must have matching evidence, even before confirmation. */
    fun permits(step: SupportStep, concepts: List<VoiceConcept>, code: ObservationCode): Boolean {
        if (code !in allowed(step)) return false
        val c = concepts.toSet()
        val uncertain = VoiceConcept.UNSURE in c
        val negative = VoiceConcept.NO in c || VoiceConcept.NONE in c
        fun any(vararg values: VoiceConcept) = values.any(c::contains)
        return when (code) {
            ObservationCode.CONSCIOUS_YES ->
                !uncertain &&
                    !negative &&
                    any(VoiceConcept.RESPONSE, VoiceConcept.CONSCIOUS, VoiceConcept.OPEN)
            ObservationCode.CONSCIOUS_NO ->
                !uncertain &&
                    any(VoiceConcept.CONSCIOUS, VoiceConcept.RESPONSE, VoiceConcept.EYES) &&
                    (negative || VoiceConcept.CLOSED in c)
            ObservationCode.BREATHING_YES ->
                !uncertain &&
                    !negative &&
                    VoiceConcept.BREATH in c &&
                    VoiceConcept.NORMAL in c &&
                    VoiceConcept.DIFFICULT !in c
            ObservationCode.BREATHING_NO ->
                !uncertain && VoiceConcept.BREATH in c && (negative || VoiceConcept.DIFFICULT in c)
            ObservationCode.CONSCIOUS_UNKNOWN,
            ObservationCode.BREATHING_UNKNOWN,
            ObservationCode.UNKNOWN -> true
            else ->
                !uncertain &&
                    !negative &&
                    when (code) {
                        ObservationCode.BLEEDING ->
                            VoiceConcept.BLOOD in c && VoiceConcept.MUCH in c
                        ObservationCode.CHEST_PAIN ->
                            VoiceConcept.CHEST in c && VoiceConcept.PAIN in c
                        ObservationCode.STROKE_SIGNS ->
                            VoiceConcept.SUDDEN in c &&
                                any(VoiceConcept.FACE, VoiceConcept.ARM, VoiceConcept.SPEECH)
                        ObservationCode.INJURY ->
                            VoiceConcept.ACCIDENT in c && VoiceConcept.INJURY in c
                        ObservationCode.FIRE ->
                            any(VoiceConcept.FIRE, VoiceConcept.SMOKE, VoiceConcept.TRAPPED)
                        ObservationCode.WORSENING ->
                            VoiceConcept.WORSE in c ||
                                VoiceConcept.BREATH in c && VoiceConcept.DIFFICULT in c
                        ObservationCode.ALONE -> VoiceConcept.ALONE in c
                        ObservationCode.COMPANION -> VoiceConcept.FAMILY in c
                        else -> false
                    }
        }
    }

    fun confirmation(code: ObservationCode) =
        when (code) {
            ObservationCode.CONSCIOUS_YES -> "환자에게 의식이 있다는 말씀이신가요?"
            ObservationCode.CONSCIOUS_NO -> "환자가 반응하지 않는다는 말씀이신가요?"
            ObservationCode.CONSCIOUS_UNKNOWN -> "환자의 의식을 확인하기 어렵다는 말씀이신가요?"
            ObservationCode.BREATHING_YES -> "환자가 평소처럼 정상적으로 숨을 쉰다는 말씀이신가요?"
            ObservationCode.BREATHING_NO -> "정상적인 호흡이 없다는 말씀이신가요?"
            ObservationCode.BREATHING_UNKNOWN -> "정상 호흡인지 확인하기 어렵다는 말씀이신가요?"
            ObservationCode.BLEEDING -> "피가 많이 난다는 말씀이신가요?"
            ObservationCode.CHEST_PAIN -> "가슴에 통증이 있다는 말씀이신가요?"
            ObservationCode.STROKE_SIGNS -> "얼굴이나 팔, 말에 갑작스런 이상이 있다는 말씀이신가요?"
            ObservationCode.INJURY -> "교통사고 후 다쳤다는 말씀이신가요?"
            ObservationCode.FIRE -> "불이나 연기, 또는 차량에 갇힌 상황이라는 말씀이신가요?"
            ObservationCode.WORSENING -> "이전보다 상태가 나빠졌다는 말씀이신가요?"
            ObservationCode.ALONE -> "혼자 기다리고 있다는 말씀이신가요?"
            ObservationCode.COMPANION -> "가족이나 동승자가 환자라는 말씀이신가요?"
            ObservationCode.UNKNOWN -> "상태를 설명하기 어렵다는 말씀이신가요?"
        }
}

interface EmergencySpeechPort {
    fun speak(parts: List<String>, done: (Boolean) -> Unit)

    fun listen(binary: Boolean, done: (String?) -> Unit)

    fun stop()
}

enum class VoicePhase {
    OFF,
    PREPARING,
    SPEAKING,
    LISTENING,
    INTERPRETING,
    PAUSED,
}

object VoiceSymptomQuestions {
    val items =
        listOf(
            ObservationCode.FIRE to "주변에 불이나 연기가 있거나 차량에 갇혀 있나요?",
            ObservationCode.BLEEDING to "피가 많이 나나요?",
            ObservationCode.CHEST_PAIN to "가슴에 통증이 있나요?",
            ObservationCode.STROKE_SIGNS to "갑자기 얼굴이나 팔, 말에 이상이 생겼나요?",
            ObservationCode.INJURY to "교통사고로 다친 사람이 있나요?",
            ObservationCode.ALONE to "환자가 혼자 기다리고 있나요?",
            ObservationCode.COMPANION to "가족이나 동승자가 환자인가요?",
        )
}

/**
 * Turn ownership guards every asynchronous result; no silence is ever recorded as a patient answer.
 */
class EmergencyVoiceConversation(
    private val ai: EmergencyAiCoordinator,
    private val speech: EmergencySpeechPort,
    private val llm: LlmProvider,
    private val allowed: () -> Boolean,
    private val record: (ObservationCode) -> Unit,
    private val changed: () -> Unit,
) {
    var phase = VoicePhase.OFF
        private set

    var externalConsent = false
        private set

    var active = false
        private set

    var manual = false
        private set

    var message = "음성 대화를 시작하면 질문 뒤에 답변을 듣습니다."
        private set

    var pending: ObservationCode? = null
        private set

    var onCallStatement: ((EmergencyCallStatus) -> Unit)? = null

    private var generation = 0L
    private var misses = 0
    private var lastGuidance: String? = null
    private var symptomIndex = 0

    fun displayQuestion(): String =
        pending?.let(VoiceAnswers::confirmation)
            ?: when {
                ai.session?.aiSessionState == SupportStep.SYMPTOMS ->
                    VoiceSymptomQuestions.items[symptomIndex].second
                ai.session?.aiSessionState == SupportStep.WAITING -> WORSENING_QUESTION
                else -> ai.question()
            }

    val urgentActionRequired: Boolean
        get() =
            ai.session?.let { s ->
                s.verifiedGuidanceId in
                    setOf("FIRE", "WORSENING", "BLEEDING", "STROKE", "BREATHING") ||
                    s.verifiedGuidanceId == "RESPONSE" && s.aiSessionState != SupportStep.BREATHING
            } == true

    /** Preserve the exact question and pending confirmation when switching away from audio. */
    fun useManual() {
        generation++
        active = false
        manual = true
        externalConsent = false
        speech.stop()
        llm.cancel()
        ai.cancelExternal()
        if (
            !ai.consent || ai.session?.aiSessionState in setOf(SupportStep.OFF, SupportStep.STOPPED)
        ) {
            ai.start(true)
            symptomIndex = 0
        }
        phase = VoicePhase.PAUSED
        message = "현재 질문에만 답해 주세요."
        changed()
    }

    fun start(consent: Boolean) {
        val savedPending = pending
        val savedSymptomIndex = symptomIndex
        stop()
        if (!allowed()) {
            message = "통화 중이거나 음성을 사용할 수 없습니다."
            changed()
            return
        }
        externalConsent = consent
        if (
            !ai.consent || ai.session?.aiSessionState in setOf(SupportStep.OFF, SupportStep.STOPPED)
        )
            ai.start(true)
        active = true
        misses = 0
        lastGuidance = null
        symptomIndex = savedSymptomIndex
        pending = savedPending
        question()
    }

    private fun valid(token: Long) = (active || manual) && token == generation && allowed()

    private fun state(value: VoicePhase, text: String) {
        phase = value
        message = text
        changed()
    }

    private fun question() {
        if (!allowed()) {
            stop()
            return
        }
        if (manual) {
            state(
                VoicePhase.PAUSED,
                if (urgentActionRequired) "119에 먼저 알려주세요." else "현재 질문에만 답해 주세요.",
            )
            return
        }
        val token = ++generation
        state(VoicePhase.PREPARING, "질문을 준비하고 있습니다")
        fun speakQuestion() {
            if (!valid(token)) return
            val s = ai.session ?: return
            if (urgentActionRequired) {
                state(VoicePhase.SPEAKING, "119 연결과 안전 확보를 먼저 안내합니다")
                speech.speak(listOf(VerifiedGuidance.card(s.verifiedGuidanceId).text)) {
                    if (valid(token)) pause("119에 먼저 알려주세요. 추가 질문을 멈췄습니다.")
                }
                return
            }
            val parts = mutableListOf<String>()
            if (s.escalationRequired && lastGuidance != s.verifiedGuidanceId) {
                parts.add(VerifiedGuidance.card(s.verifiedGuidanceId).text)
                lastGuidance = s.verifiedGuidanceId
            }
            parts.add(displayQuestion())
            say(parts, token)
        }
        if (
            externalConsent &&
                ai.session?.escalationRequired == false &&
                ai.session?.aiSessionState in
                    setOf(SupportStep.CONSCIOUSNESS, SupportStep.BREATHING)
        )
            ai.rephrase(llm, true, ::speakQuestion)
        else speakQuestion()
    }

    private fun say(parts: List<String>, token: Long) {
        state(VoicePhase.SPEAKING, "질문을 읽고 있습니다 · 끝나면 말씀하세요")
        speech.speak(parts) { success ->
            if (!valid(token)) return@speak
            if (!success) {
                pause("음성 출력을 준비하지 못했습니다. 화면 답변을 이용할 수 있습니다.")
                return@speak
            }
            state(VoicePhase.LISTENING, "듣고 있습니다 · 그만이라고 말하면 중단합니다")
            speech.listen(true) { heard -> if (valid(token)) receive(heard, token) }
        }
    }

    private fun receive(heard: String?, token: Long) {
        if (heard == null) {
            missed(token)
            return
        }
        if (VoiceAnswers.stop(heard)) {
            stop()
            message = "음성 대화를 중단했습니다."
            changed()
            return
        }
        EmergencyCallSpeech.status(heard)?.let { status ->
            val action = onCallStatement
            if (action != null) {
                generation++
                speech.stop()
                llm.cancel()
                pending = null
                action(status)
                if (active && allowed()) question() else stop()
                return
            }
        }
        // A new explicit danger statement also interrupts a pending model confirmation.
        VoiceAnswers.urgent(heard)?.let {
            accept(it)
            return
        }
        pending?.let { candidate ->
            when (VoiceAnswers.yesNo(heard)) {
                true -> {
                    pending = null
                    accept(candidate)
                }
                false -> {
                    pending = null
                    misses = 0
                    question()
                }
                null -> missed(token)
            }
            return
        }
        val step = ai.session?.aiSessionState ?: return
        val binary = VoiceAnswers.yesNo(heard)
        if (step == SupportStep.SYMPTOMS && binary != null) {
            if (binary) accept(VoiceSymptomQuestions.items[symptomIndex].first)
            else if (++symptomIndex < VoiceSymptomQuestions.items.size) {
                misses = 0
                question()
            } else {
                symptomIndex = 0
                if (manual) {
                    pause("확인한 기록을 유지합니다. 119에 현재 상황을 알려주세요.")
                    return
                }
                state(VoicePhase.SPEAKING, "답변을 임의의 정상 상태로 판단하지 않습니다")
                speech.speak(listOf(REST)) {
                    if (valid(token)) pause("상태를 119에 직접 설명해 주세요. 앱은 정상 여부를 판단하지 않습니다.")
                }
            }
            return
        }
        if (step == SupportStep.WAITING && binary == true) {
            accept(ObservationCode.WORSENING)
            return
        }
        if (VoiceAnswers.unchanged(step, heard)) {
            if (manual) {
                pause("현재 기록을 유지합니다. 변화가 생기면 다시 알려주세요.")
                return
            }
            state(VoicePhase.SPEAKING, "현재 기록을 유지합니다 · 추가 상태를 추정하지 않습니다")
            speech.speak(listOf(REST)) {
                if (valid(token)) pause("현재 기록을 유지합니다. 변화가 생기면 음성 대화를 다시 시작하세요.")
            }
            return
        }
        val local = VoiceAnswers.local(step, heard)
        if (local != null) {
            accept(local)
            return
        }
        val concepts = VoiceAnswers.concepts(heard)
        if (!externalConsent || concepts.isEmpty() || ai.session?.escalationRequired == true) {
            missed(token)
            return
        }
        state(VoicePhase.INTERPRETING, "답변의 의미를 확인하고 있습니다")
        llm.interpret(VoiceMeaningRequest(step, concepts)) { code ->
            if (!valid(token)) return@interpret
            if (code == null || !VoiceAnswers.permits(step, concepts, code)) {
                missed(token)
                return@interpret
            }
            pending = code
            say(listOf(VoiceAnswers.confirmation(code), CONFIRM), token)
        }
    }

    private fun missed(token: Long) {
        if (manual) {
            state(VoicePhase.PAUSED, "현재 질문에 예, 아니요, 잘 모르겠어요로 답해 주세요.")
            return
        }
        misses++
        if (misses >= 2) {
            pause("답변을 확인하지 못해 마이크를 쉬고 있습니다. 다시 시작하거나 화면으로 답하세요.")
            return
        }
        say(listOf(REPEAT, displayQuestion()), token)
    }

    fun answerShort(value: Boolean?) {
        if ((active || manual) && allowed() && !urgentActionRequired)
            receive(
                when (value) {
                    true -> "네"
                    false -> "아니요"
                    null -> "모르겠어요"
                },
                generation,
            )
    }

    fun accept(code: ObservationCode) {
        val resume = active || manual
        generation++
        speech.stop()
        llm.cancel()
        pending = null
        misses = 0
        record(code)
        if (resume) {
            if (allowed()) question() else stop()
        }
    }

    fun confirm(answer: Boolean) {
        val code = pending ?: return
        if (answer) accept(code)
        else {
            pending = null
            generation++
            speech.stop()
            question()
        }
    }

    private fun pause(reason: String) {
        stop()
        phase = VoicePhase.PAUSED
        message = reason
        changed()
    }

    fun stop() {
        val wasActive = active
        if (wasActive) message = "음성 대화를 멈췄습니다."
        generation++
        active = false
        manual = false
        pending = null
        externalConsent = false
        phase = VoicePhase.OFF
        speech.stop()
        llm.cancel()
        if (wasActive) ai.cancelExternal()
    }

    companion object {
        const val REPEAT = "답변을 확인하지 못했습니다. 짧게 다시 말씀해 주세요."
        const val CONFIRM = "예 또는 아니요로 답해 주세요."
        const val REST = "알겠습니다. 변화가 생기면 음성 대화를 다시 시작하거나 119에 알려주세요."
        const val WORSENING_QUESTION = "아까보다 상태가 나빠졌나요?"
    }
}
