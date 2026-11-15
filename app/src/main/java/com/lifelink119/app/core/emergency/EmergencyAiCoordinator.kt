package com.lifelink119.app.core.emergency

enum class SupportStep {
    OFF,
    CONSCIOUSNESS,
    BREATHING,
    SYMPTOMS,
    WAITING,
    STOPPED,
}

enum class ObservationCode(val label: String, val group: String) {
    CONSCIOUS_YES("의식 있음이라고 응답", "의식"),
    CONSCIOUS_NO("의식 없음이라고 응답", "의식"),
    CONSCIOUS_UNKNOWN("의식 여부를 모르겠다고 응답", "의식"),
    BREATHING_YES("정상 호흡이라고 응답", "호흡"),
    BREATHING_NO("정상 호흡이 없다고 응답", "호흡"),
    BREATHING_UNKNOWN("정상 호흡인지 모르겠다고 응답", "호흡"),
    BLEEDING("심한 출혈이라고 응답", "증상"),
    CHEST_PAIN("가슴 통증이라고 응답", "증상"),
    STROKE_SIGNS("얼굴·팔·말의 갑작스런 이상이라고 응답", "증상"),
    INJURY("교통사고 후 부상이 있다고 응답", "증상"),
    FIRE("차량 화재·연기 또는 갇힘이라고 응답", "현장"),
    WORSENING("이전보다 상태가 나빠졌다고 응답", "변화"),
    ALONE("혼자 기다린다고 응답", "동행"),
    COMPANION("가족·동승자가 환자라고 응답", "동행"),
    UNKNOWN("상태를 설명하기 어렵다고 응답", "증상"),
}

data class PatientObservation(val code: ObservationCode, val at: Long, val source: EvidenceStatus) {
    override fun toString() = "PatientObservation(REDACTED)"
}

data class GuidanceCard(
    val id: String,
    val title: String,
    val text: String,
    val source: String,
    val manualId: String? = null,
)

/** Versioned, authored text; a provider may select a pre-approved phrase, never write guidance. */
object VerifiedGuidance {
    const val VERSION = "2026-11-14-review-pending"
    const val NOTICE = "공식 자료 기반 시연 안내 · 의료전문가 검토 전. 실제 119 상담원·의료진 지시를 우선하세요."
    const val KDCA =
        "https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6226"
    const val AHA = "https://cpr.heart.org/en/resuscitation-science/2024-first-aid-guidelines"
    val cards =
        listOf(
                GuidanceCard(
                    "WAIT",
                    "함께 상태를 확인하겠습니다",
                    "곁에서 반응과 호흡 변화를 살펴주세요. 변화가 생기면 119에 다시 알려주세요.",
                    KDCA,
                    "first",
                ),
                GuidanceCard(
                    "RESPONSE",
                    "반응이 없거나 확인하기 어렵습니다",
                    "즉시 119에 알리고 정상 호흡을 확인하세요. 주변에 도움과 AED를 요청하고 상담원의 지시를 받으세요.",
                    KDCA,
                    "first",
                ),
                GuidanceCard(
                    "BREATHING",
                    "정상 호흡이 확인되지 않습니다",
                    "즉시 119에 알리고 AED와 주변 도움을 요청하세요. 심폐소생술은 환자 연령과 상황에 맞는 119 지시를 받으세요.",
                    KDCA,
                    "aed",
                ),
                GuidanceCard(
                    "BLEEDING",
                    "심한 출혈을 알리세요",
                    "안전한 상황에서 깨끗한 천이나 거즈로 출혈 부위를 직접 압박하며 119 안내를 받으세요.",
                    AHA,
                    "bleeding",
                ),
                GuidanceCard(
                    "CHEST",
                    "가슴 통증을 알리세요",
                    "즉시 119에 증상을 알리세요. 직접 운전하지 말고 전문 도움을 요청하세요. 약 복용 여부는 의료진에게 확인하세요.",
                    AHA,
                ),
                GuidanceCard(
                    "STROKE",
                    "갑작스런 변화를 알리세요",
                    "얼굴·팔·말에 갑작스런 이상이 있으면 즉시 119에 알리세요. 처음 달라진 시각이나 마지막 정상 시각을 전하세요. 앱은 뇌졸중을 진단하지 않습니다.",
                    AHA,
                ),
                GuidanceCard(
                    "INJURY",
                    "현장 안전부터 확인하세요",
                    "2차 사고 위험을 살피고 119에 부상과 위치를 알리세요. 환자를 불필요하게 움직이지 말고 현장 위험과 상담원 지시에 따르세요.",
                    AHA,
                ),
                GuidanceCard(
                    "FIRE",
                    "화재·연기·갇힘을 즉시 알리세요",
                    "119에 화재·연기와 갇힌 사람이 있는지 알리세요. 자신의 안전을 먼저 확보하고 위험한 차량으로 다시 접근하지 마세요.",
                    "https://inje119.gwd.go.kr/egf/bp/board/article/download?fileSeq=1228326",
                ),
                GuidanceCard(
                    "WORSENING",
                    "상태 변화를 즉시 119에 다시 알리세요",
                    "새로운 증상과 달라진 시각, 현재 위치를 전하세요. 콜백 대기 중이어도 재신고할 수 있습니다.",
                    KDCA,
                ),
                GuidanceCard(
                    "ALONE",
                    "짧게 함께 확인하겠습니다",
                    "안전한 곳에서 주변에 도움을 요청하세요. 말씀하기 어려우면 응답을 건너뛸 수 있습니다. 상태 변화가 있으면 119에 알리세요.",
                    KDCA,
                ),
                GuidanceCard(
                    "COMPANION",
                    "가족·동승자 곁에서 확인하세요",
                    "환자의 반응과 호흡 변화를 살피고 119 상담원의 지시를 함께 따르세요.",
                    KDCA,
                ),
            )
            .associateBy { it.id }

    fun card(id: String) = cards[id] ?: cards.getValue("WAIT")

    fun forObservation(code: ObservationCode): String =
        when (code) {
            ObservationCode.CONSCIOUS_NO,
            ObservationCode.CONSCIOUS_UNKNOWN -> "RESPONSE"
            ObservationCode.BREATHING_NO,
            ObservationCode.BREATHING_UNKNOWN -> "BREATHING"
            ObservationCode.BLEEDING -> "BLEEDING"
            ObservationCode.CHEST_PAIN -> "CHEST"
            ObservationCode.STROKE_SIGNS -> "STROKE"
            ObservationCode.INJURY -> "INJURY"
            ObservationCode.FIRE -> "FIRE"
            ObservationCode.WORSENING -> "WORSENING"
            ObservationCode.ALONE -> "ALONE"
            ObservationCode.COMPANION -> "COMPANION"
            else -> "WAIT"
        }

    val urgent = setOf("RESPONSE", "BREATHING", "BLEEDING", "CHEST", "STROKE", "FIRE", "WORSENING")

    /** Presentation priority only, not a medical triage score. Critical warnings remain visible. */
    fun retain(previous: String, next: String): String {
        val fixedPriority =
            mapOf(
                "FIRE" to 100,
                "BREATHING" to 90,
                "RESPONSE" to 80,
                "BLEEDING" to 70,
                "WORSENING" to 60,
                "STROKE" to 50,
                "CHEST" to 50,
            )
        return if ((fixedPriority[previous] ?: 0) > (fixedPriority[next] ?: 0)) previous else next
    }
}

data class EmergencySupportSession(
    val incidentId: String,
    val aiSessionState: SupportStep = SupportStep.OFF,
    val verifiedGuidanceId: String = "WAIT",
    val patientObservations: List<PatientObservation> = emptyList(),
    val escalationRequired: Boolean = false,
    val lastUpdatedAt: Long,
) {
    val conversationSummary
        get() = handover()

    fun handover(): String {
        if (patientObservations.isEmpty()) return "환자 상태 입력 없음 · 진단/AI 추론 없음"
        val first =
            patientObservations
                .filter { it.code.group in setOf("의식", "호흡") }
                .groupBy { it.code.group }
                .values
                .map { it.first() }
        val latest =
            patientObservations
                .groupBy {
                    if (it.code.group in setOf("의식", "호흡", "변화")) it.code.group else it.code.name
                }
                .values
                .map { it.last() }
        return (listOf(patientObservations.first()) + first + latest)
            .distinct()
            .sortedBy { it.at }
            .joinToString("\n") {
                "${EmergencyPacket.time(it.at)} · ${it.code.label} [${it.source.label}]"
            } + if (escalationRequired) "\n상태·증상 변화를 119에 다시 알릴 필요가 있습니다." else ""
    }

    override fun toString() = "EmergencySupportSession(REDACTED)"
}

data class LlmRequest(val step: SupportStep, val easy: Boolean = true)

interface LlmProvider {
    fun suggest(request: LlmRequest, done: (String?) -> Unit)

    fun interpret(request: VoiceMeaningRequest, done: (ObservationCode?) -> Unit) {
        done(null)
    }

    fun cancel() {}
}

class MockLlmProvider(private val fail: Boolean = false) : LlmProvider {
    override fun suggest(request: LlmRequest, done: (String?) -> Unit) {
        done(if (fail) null else SafePhrases.allowed(request.step).first())
    }
}

object SafePhrases {
    private val phrases =
        mapOf(
            "CONSCIOUSNESS" to "제가 함께 상황을 확인하겠습니다. 환자는 의식이 있나요?",
            "CONSCIOUSNESS_EASY" to "환자를 불렀을 때 반응하나요?",
            "BREATHING" to "환자가 정상적으로 숨을 쉬고 있나요?",
            "BREATHING_EASY" to "숨을 평소처럼 쉬고 있나요?",
            "SYMPTOMS" to "지금 가장 알리고 싶은 상태는 무엇인가요?",
            "SYMPTOMS_EASY" to "어떤 변화가 있나요?",
            "WAITING" to "기다리는 동안 상태가 달라진 점이 있나요?",
            "WAITING_EASY" to "달라진 점이 있나요?",
        )

    fun allowed(step: SupportStep) =
        listOf(step.name, "${step.name}_EASY")
            .filter { it in phrases }
            .ifEmpty { listOf("WAITING") }

    fun text(id: String) = phrases[id] ?: phrases.getValue("WAITING")

    fun validate(step: SupportStep, id: String?) = id?.takeIf { it in allowed(step) }
}

/** No diagnosis, dispatch or free-form medical output. User observations alone drive the rules. */
class EmergencyAiCoordinator(private val clock: () -> Long) {
    var session: EmergencySupportSession? = null
        private set

    var consent = false
        private set

    var phraseId: String? = null
        private set

    var externalState = "오프라인 고정 안내"
        private set

    private var generation = 0L

    fun attach(id: String) {
        cancelExternal()
        consent = false
        session = EmergencySupportSession(id, lastUpdatedAt = clock())
    }

    fun start(consented: Boolean) {
        check(consented)
        val old = requireNotNull(session)
        consent = true
        session = old.copy(aiSessionState = SupportStep.CONSCIOUSNESS, lastUpdatedAt = clock())
        phraseId = null
    }

    fun answer(code: ObservationCode, source: EvidenceStatus = EvidenceStatus.USER) {
        check(consent)
        require(source in setOf(EvidenceStatus.USER, EvidenceStatus.SIMULATION))
        val s = requireNotNull(session)
        check(s.aiSessionState !in setOf(SupportStep.OFF, SupportStep.STOPPED))
        val next =
            when (code) {
                ObservationCode.CONSCIOUS_YES,
                ObservationCode.CONSCIOUS_NO,
                ObservationCode.CONSCIOUS_UNKNOWN -> SupportStep.BREATHING
                ObservationCode.BREATHING_YES,
                ObservationCode.BREATHING_NO,
                ObservationCode.BREATHING_UNKNOWN -> SupportStep.SYMPTOMS
                else -> SupportStep.WAITING
            }
        val guide = VerifiedGuidance.forObservation(code)
        // Keep an urgent card visible until the user contacts professionals; benign answers never
        // erase it.
        val keptGuide = VerifiedGuidance.retain(s.verifiedGuidanceId, guide)
        val observations = s.patientObservations + PatientObservation(code, clock(), source)
        val keep = mutableSetOf(0)
        for (group in setOf("의식", "호흡")) {
            observations.indexOfFirst { it.code.group == group }.takeIf { it >= 0 }?.let(keep::add)
        }
        observations
            .map { it.code }
            .distinct()
            .forEach { code -> keep.add(observations.indexOfLast { it.code == code }) }
        observations
            .indexOfLast { VerifiedGuidance.forObservation(it.code) in VerifiedGuidance.urgent }
            .takeIf { it >= 0 }
            ?.let(keep::add)
        for (index in observations.indices.reversed()) {
            if (keep.size >= 24) break
            keep.add(index)
        }
        session =
            s.copy(
                aiSessionState = next,
                verifiedGuidanceId = keptGuide,
                patientObservations = observations.filterIndexed { index, _ -> index in keep },
                escalationRequired = s.escalationRequired || guide in VerifiedGuidance.urgent,
                lastUpdatedAt = clock(),
            )
        cancelExternal()
    }

    fun question() =
        phraseId?.let(SafePhrases::text)
            ?: SafePhrases.text(session?.aiSessionState?.name ?: "CONSCIOUSNESS")

    fun stop() {
        cancelExternal()
        session = session?.copy(aiSessionState = SupportStep.STOPPED)
    }

    fun clear() {
        stop()
        consent = false
        session = null
    }

    fun restore(value: EmergencySupportSession) {
        cancelExternal()
        consent = true
        session = value.copy(aiSessionState = SupportStep.STOPPED)
    }

    fun cancelExternal() {
        generation++
        phraseId = null
        externalState = "오프라인 고정 안내"
    }

    fun rephrase(provider: LlmProvider, explicitConsent: Boolean, done: () -> Unit) {
        val s = requireNotNull(session)
        check(consent && explicitConsent)
        if (
            s.escalationRequired ||
                s.aiSessionState !in
                    setOf(
                        SupportStep.CONSCIOUSNESS,
                        SupportStep.BREATHING,
                        SupportStep.SYMPTOMS,
                        SupportStep.WAITING,
                    )
        )
            return
        val token = ++generation
        externalState = "문장 선택 요청 중 · 기본 안내 계속 사용 가능"
        provider.suggest(LlmRequest(s.aiSessionState)) { id ->
            if (token == generation && session?.aiSessionState == s.aiSessionState) {
                phraseId = SafePhrases.validate(s.aiSessionState, id)
                externalState =
                    if (phraseId == null) "외부 응답 없음/검증 실패 · 오프라인 안내 유지" else "검증된 쉬운 문장 사용"
                done()
            }
        }
    }
}
