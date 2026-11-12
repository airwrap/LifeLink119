package com.lifelink119.app.core

enum class CapabilityStatus { REAL, MOCK, NOT_AVAILABLE, NEEDS_PARTNERSHIP }
enum class ConnectionTarget(val label: String, val purpose: String, val legacy: EmergencyScenario) {
    EMERGENCY_119("119 구조·구급", "긴급한 구조·구급 요청", EmergencyScenario.MEDICAL),
    CONSULT_119("119 응급의료상담", "전문 상담원에게 도움 요청", EmergencyScenario.MEDICAL),
    EMERGENCY_112("112 긴급신고", "범죄·폭력·교통위험 신고", EmergencyScenario.POLICE),
    COUNSEL_109("109 심리위기 상담", "프라이버시를 지키는 전문 상담", EmergencyScenario.WELLBEING)
}
enum class LinkStatus { IDLE, REQUESTING, DEMO_CONNECTED, SUPPORT_IN_PROGRESS, FAILED, COMPLETED, CANCELLED }
enum class SupportAction(val label: String) {
    CARD("응급정보 공유"), VIDEO("영상 보여주기"), LOCATION("위치 공유"), TEXT("문자로 설명"),
    SIGN("수어통역 시연"), PHRASES("의사표현 카드"), VOICE("음성 도움말"), EASY("쉬운 안내"),
    MOBILITY("승하차 도움"), GUARDIAN("보호자 정보"), SUMMARY("상담 전달용 요약"),
    POLICE("112 공동대응"), NAVIGATION("만남 장소 안내"), SAFE_CALL("119안심콜 모형"),
    POWER("EV 전력 계산"), VEHICLE("차량 정보"), PRIVACY("공유·프라이버시"), MANUAL("기본 응급 매뉴얼"), COOPERATION("주변 차량 협력")
}
enum class ShareField(val label: String) {
    ALIAS("가상 별명·연령대"), CONDITIONS("병력 참고"), MEDICATIONS("복용약 참고"), ALLERGIES("알레르기"),
    PREGNANCY("임신 관련 참고"), ACCESS("접근성·이동 도움"), GUARDIAN("가상 보호자"),
    LOCATION("가상 위치"), VEHICLE("가상 차량 정보"), SUMMARY("상담 전달용 요약"), TEXT("선택한 의사표현"), VIDEO("합성 영상")
}
enum class VideoStatus { OFF, REQUESTED, APPROVED, SHARING, STOPPED }
enum class VideoSource(val label: String) { FRONT("가상 전방 영상"), REAR("가상 후방 영상"), PHONE("가상 스마트폰 환자 영상") }
enum class PoliceStatus { NONE, REQUESTED, REVIEWING, APPROVED, REJECTED }
enum class NavigationStatus { NONE, PROPOSED, CONFIRMED, SET }
enum class SignStatus { OFF, REQUESTED, CONNECTED }

data class AccessPreferences(val needs: Set<String> = emptySet(), val largeText: Boolean = false, val easy: Boolean = false)
data class PersonProfile(
    val id: String, val aliasKey: String, val ageKey: String, val conditions: Set<String> = emptySet(),
    val medications: Set<String> = emptySet(), val allergies: Set<String> = emptySet(), val pregnancyKey: String = "NONE",
    val guardianKey: String = "NONE", val access: AccessPreferences = AccessPreferences(), val safeCallRegistered: Boolean = false
)
data class ScenarioSpec(val id: String, val title: String, val target: ConnectionTarget, val priorities: List<SupportAction>,
    val profileHint: String? = null, val context: String = "", val observation: String = "HELP")
data class ExperienceCatalog(val scenarios: List<ScenarioSpec>, val profiles: List<PersonProfile>, val words: Map<String, Map<String, String>>) {
    fun scenario(id: String) = scenarios.first { it.id == id }
    fun word(group: String, key: String) = words[group]?.get(key) ?: "정보 없음"
    fun validProfile(p: PersonProfile): Boolean = p.id.matches(Regex("DEMO-P-[0-9]{1,6}")) &&
        words["aliases"]?.containsKey(p.aliasKey) == true && words["ages"]?.containsKey(p.ageKey) == true &&
        p.conditions.all { words["conditions"]?.containsKey(it) == true } &&
        p.medications.all { words["medications"]?.containsKey(it) == true } &&
        p.allergies.all { words["allergies"]?.containsKey(it) == true } &&
        words["pregnancy"]?.containsKey(p.pregnancyKey) == true && words["guardians"]?.containsKey(p.guardianKey) == true &&
        p.access.needs.all { words["access"]?.containsKey(it) == true }
}

/** Actual platform restrictions and a MORE restrictive test overlay are combined, never bypassed. */
data class DrivingRestrictions(
    val source: CapabilityStatus = CapabilityStatus.NOT_AVAILABLE, val known: Boolean = false,
    val requiresOptimization: Boolean = false, val flags: Int = 0, val noVideo: Boolean = true,
    val noKeyboard: Boolean = true, val noSetup: Boolean = true, val noMessages: Boolean = true, val limitContent: Boolean = true,
    val detail: String = "차량 제한 상태 확인 중"
) {
    val complexBlocked get() = !known || requiresOptimization || noKeyboard || noSetup
    val videoBlocked get() = !known || requiresOptimization || noVideo
    val messagesBlocked get() = !known || requiresOptimization || noMessages
    val galleryBlocked get() = !known || requiresOptimization || limitContent
    fun withTestRestriction(enabled: Boolean) = if (!enabled) this else copy(noVideo = true, noKeyboard = true, noSetup = true,
        noMessages = true, limitContent = true, detail = "$detail · 추가 제한 시연")
    companion object {
        fun nonAutomotiveDemo() = DrivingRestrictions(known=true, noVideo=false, noKeyboard=false, noSetup=false,
            noMessages=false, limitContent=false, detail="차량 제한 API 없는 기기 · 오프라인 시연")
        fun unrestrictedFixture() = nonAutomotiveDemo().copy(source=CapabilityStatus.MOCK, detail="단위 검사 가상 제한")
    }
}
data class ActionPlan(val primary: List<SupportAction>, val additional: List<SupportAction>)
object ContextualActionResolver {
    fun resolve(profile: PersonProfile?, scenario: ScenarioSpec, target: ConnectionTarget, restrictions: DrivingRestrictions): ActionPlan {
        val access = profile?.access?.needs.orEmpty()
        val preferred = when {
            target == ConnectionTarget.EMERGENCY_112 -> listOf(SupportAction.TEXT, SupportAction.LOCATION, SupportAction.POLICE)
            target == ConnectionTarget.COUNSEL_109 -> listOf(SupportAction.PRIVACY, SupportAction.TEXT, SupportAction.SUMMARY)
            "HEARING" in access -> listOf(SupportAction.TEXT, SupportAction.SIGN, SupportAction.LOCATION)
            "LANGUAGE" in access -> listOf(SupportAction.PHRASES, SupportAction.TEXT, SupportAction.LOCATION)
            "VISION" in access -> listOf(SupportAction.VOICE, SupportAction.CARD, SupportAction.LOCATION)
            "COGNITIVE" in access -> listOf(SupportAction.EASY, SupportAction.GUARDIAN, SupportAction.LOCATION)
            "MOBILITY" in access -> listOf(SupportAction.MOBILITY, SupportAction.CARD, SupportAction.LOCATION)
            else -> scenario.priorities
        }
        val all = (preferred + listOf(SupportAction.CARD, SupportAction.LOCATION, SupportAction.SUMMARY) + SupportAction.entries).distinct().filter {
            (it!=SupportAction.POWER || scenario.id=="E01") && (target!=ConnectionTarget.COUNSEL_109 || it !in setOf(SupportAction.POLICE,SupportAction.NAVIGATION,SupportAction.COOPERATION,SupportAction.VEHICLE))
        }
        val primary = all.filter { !(restrictions.videoBlocked && it in setOf(SupportAction.VIDEO, SupportAction.SIGN)) &&
            !(restrictions.messagesBlocked && it in setOf(SupportAction.TEXT, SupportAction.PHRASES)) }.take(3)
        return ActionPlan(primary, all.filterNot { it in primary })
    }
}

data class EmergencySession(
    val id: String, val target: ConnectionTarget, val scenarioId: String, val status: LinkStatus = LinkStatus.REQUESTING,
    val personId: String? = null, val shared: Map<ShareField, String> = emptyMap(), val observation: String = "HELP",
    val discovered: String = "NOW", val demoGlucose: Int? = null, val video: VideoStatus = VideoStatus.OFF,
    val videoSource: VideoSource = VideoSource.PHONE, val police: PoliceStatus = PoliceStatus.NONE,
    val navigation: NavigationStatus = NavigationStatus.NONE, val destination: String? = null,
    val sign: SignStatus = SignStatus.OFF, val guidance: Boolean = false, val safeCall: String = "NOT_REQUESTED",
    val voice: String = "OFF", val messages: List<String> = emptyList(), val events: List<String> = listOf("REQUESTED"), val revision: Int = 1, val driving: DrivingEmergencyState? = null
) {
    val active get() = status in setOf(LinkStatus.DEMO_CONNECTED, LinkStatus.SUPPORT_IN_PROGRESS)
    val ended get() = status in setOf(LinkStatus.COMPLETED, LinkStatus.CANCELLED)
}

class SessionEngine(val catalog: ExperienceCatalog, initialProfiles: List<PersonProfile> = catalog.profiles,
    private val summaries: ConsultationSummaryGateway = OfflineTemplateSummaryGateway,
    cooperationCatalog: CooperationCatalog = CooperationCatalog(),clock: ()->Long = {System.nanoTime()/1_000_000}) {
    var profiles: List<PersonProfile> = initialProfiles.filter(catalog::validProfile); private set
    var current: EmergencySession? = null; private set
    var driverProfileId: String? = null; private set
    var restrictions = DrivingRestrictions(); private set
    var locationAvailable = true
    var failNextConnection = false
    var privateUnlocked = false; private set
    var videoPreview = false; private set
    val safeCallLink = SafeCallLink()
    // Ephemeral local-only telemetry: deliberately outside EmergencySession/JSON/PC payloads.
    var vehicleContextShare: VehicleContextShare? = null; private set
    var onChange: () -> Unit = {}
    val cooperation=OfflineCooperationEngine(cooperationCatalog,clock,
        allowed={current?.active==true && current?.target!=ConnectionTarget.COUNSEL_109 && ShareField.LOCATION in current!!.shared && locationAvailable},
        previewAllowed={!restrictions.videoBlocked && !restrictions.complexBlocked})
    init { cooperation.onChange={onChange()} }
    private fun update(event: String, next: EmergencySession) {
        current = next.copy(revision=(current?.revision ?: 0)+1, events=(next.events + event).takeLast(40)); onChange()
    }
    fun start(id: String, target: ConnectionTarget, scenarioId: String = "E04", personId: String? = null) {
        require(id.matches(Regex("DEMO-[0-9]{4,}"))); catalog.scenario(scenarioId);require(scenarioId!="D01" || target==ConnectionTarget.EMERGENCY_119)
        privateUnlocked=false;videoPreview=false;vehicleContextShare=null
        current=EmergencySession(id,target,scenarioId,personId=personId?.takeIf { id2 -> profiles.any { it.id==id2 } },observation=catalog.scenario(scenarioId).observation,driving=if(scenarioId=="D01")DrivingEmergencyState()else null)
        safeCallLink.attach(id,current?.personId)
        cooperation.attach(id)
        onChange()
    }
    /** Repeated shortcuts reopen the same pending/active driving demo, including failure recovery. */
    fun startDriving(id: String, trigger: DrivingTrigger = DrivingTrigger.TOUCH) {
        if(current?.scenarioId=="D01" && current?.ended==false)return
        check(trigger!=DrivingTrigger.VOICE_EXAMPLE || !restrictions.complexBlocked)
        start(id,ConnectionTarget.EMERGENCY_119,"D01")
        update("DRIVING_DEMO_STARTED",requireNotNull(current).copy(driving=DrivingEmergencyState(trigger)))
    }
    private fun drivingChange(event: String, change: (DrivingEmergencyState)->DrivingEmergencyState) {
        val s=active();check(!restrictions.complexBlocked) { "플랫폼 제한 중에는 상세 역할 시연을 사용할 수 없습니다." }
        update(event,s.copy(driving=change(requireNotNull(s.driving))))
    }
    fun drivingSend()=drivingChange("DRIVING_REPORT_SENT") { it.send() }
    fun drivingAcknowledge()=drivingChange("DRIVING_OPERATOR_ACKNOWLEDGED") { it.acknowledge() }
    fun drivingStopped()=drivingChange("DRIVING_STOP_REPORTED") { it.stopped() }
    fun drivingSafe()=drivingChange("DRIVING_SAFETY_REPORTED") { it.safe() }
    fun drivingHandover()=drivingChange("DRIVING_MOCK_HANDOVER") { it.handover() }
    fun answer() { val s=current ?: return;check(s.status==LinkStatus.REQUESTING)
        if(failNextConnection){failNextConnection=false;update("CONNECTION_FAILED",s.copy(status=LinkStatus.FAILED))}
        else update("MOCK_OPERATOR_ANSWERED",s.copy(status=LinkStatus.DEMO_CONNECTED))
    }
    fun retry() { val s=current ?: return;check(s.status==LinkStatus.FAILED);update("RETRY_REQUESTED",s.copy(status=LinkStatus.REQUESTING)) }
    private fun active(): EmergencySession = requireNotNull(current).also { check(it.active) { "가상 상담원 응답 후 이용하세요." } }
    fun person() = current?.personId?.let { id -> profiles.find { it.id==id } }
    fun selectPerson(id: String?) {
        val s=active();require(id==null || profiles.any { it.id==id });privateUnlocked=false;videoPreview=false;vehicleContextShare=null;cooperation.invalidate()
        safeCallLink.attach(s.id,id)
        update("PERSON_SELECTED",s.copy(personId=id,shared=s.shared.filterKeys { it in setOf(ShareField.LOCATION,ShareField.VEHICLE) },observation=catalog.scenario(s.scenarioId).observation,discovered="NOW",demoGlucose=null,video=VideoStatus.STOPPED,sign=SignStatus.OFF,messages=emptyList(),safeCall="NOT_REQUESTED",navigation=NavigationStatus.NONE,destination=null,guidance=false,police=PoliceStatus.NONE))
    }
    fun selectCase(id: String) { val s=active();val c=catalog.scenario(id);require(id!="D01" || s.target==ConnectionTarget.EMERGENCY_119);videoPreview=false;cooperation.invalidate();safeCallLink.attach(s.id,s.personId);update("CONTEXT_SELECTED",s.copy(scenarioId=id,safeCall="NOT_REQUESTED",driving=if(id=="D01")DrivingEmergencyState()else null,observation=c.observation,discovered="NOW",demoGlucose=null,shared=s.shared-setOf(ShareField.SUMMARY,ShareField.VIDEO),video=VideoStatus.STOPPED,navigation=NavigationStatus.NONE,destination=null,guidance=false,police=PoliceStatus.NONE)) }
    fun unlockPrivate(confirmed: Boolean) { check(confirmed);check(!restrictions.complexBlocked);privateUnlocked=true;onChange() }
    fun lockPrivate() { privateUnlocked=false;videoPreview=false;safeCallLink.lock();current=current?.copy(safeCall="NOT_REQUESTED");onChange() }
    fun setRestrictions(value: DrivingRestrictions) { restrictions=value;if(value.videoBlocked)videoPreview=false;if(value.complexBlocked){privateUnlocked=false;vehicleContextShare=null;safeCallLink.lock();current=current?.copy(safeCall="NOT_REQUESTED")};if(value.videoBlocked || value.complexBlocked)cooperation.pausePreview();onChange() }
    fun background() { val changed=privateUnlocked || videoPreview || vehicleContextShare!=null || safeCallLink.outcome!=SafeCallOutcome.NOT_REQUESTED;safeCallLink.lock();current=current?.copy(safeCall="NOT_REQUESTED");privateUnlocked=false;videoPreview=false;vehicleContextShare=null;cooperation.foreground(false);if(changed)onChange() }
    fun foreground(){cooperation.foreground(true);cooperation.tick()}
    fun addProfile(p: PersonProfile) { require(catalog.validProfile(p));require(profiles.none { it.id==p.id });check(profiles.size<12);profiles=profiles+p;onChange() }
    fun editProfile(p: PersonProfile) { require(catalog.validProfile(p));require(profiles.any { it.id==p.id });profiles=profiles.map { if(it.id==p.id)p else it };if(current?.personId==p.id && current?.active==true)selectPerson(p.id) else onChange() }
    fun deleteProfile(id: String) { profiles=profiles.filterNot { it.id==id };if(driverProfileId==id)driverProfileId=null;if(current?.personId==id && current?.active==true)selectPerson(null) else onChange() }
    fun restoreProfiles() { profiles=catalog.profiles;driverProfileId=null;if(current?.active==true)selectPerson(null) else onChange() }
    fun selectDriver(id: String?) { require(id==null || profiles.any { it.id==id });driverProfileId=id;onChange() }
    fun restore(s: EmergencySession?, driver: String?) { require(s==null || (s.id.matches(Regex("DEMO-[0-9]{4,}")) && catalog.scenarios.any { it.id==s.scenarioId }));current=s;safeCallLink.attach(s?.id,s?.personId);vehicleContextShare=null;cooperation.attach(s?.id);driverProfileId=driver?.takeIf { id -> profiles.any { it.id==id } };privateUnlocked=false;videoPreview=false }
    fun shareVehicleContext(snapshot: VehicleContextSnapshot,consent: Boolean,now: Long) {
        val s=active();check(s.target!=ConnectionTarget.COUNSEL_109);check(!restrictions.complexBlocked)
        vehicleContextShare=VehicleContextPolicy.consent(snapshot,s.id,consent,now);onChange()
    }
    fun revokeVehicleContext(){vehicleContextShare=null;onChange()}
    fun expireVehicleContext(now: Long){if(vehicleContextShare?.fresh(now)==false)revokeVehicleContext()}
    fun alias(p: PersonProfile) = catalog.word("aliases",p.aliasKey)
    private fun words(group: String, keys: Set<String>) = if(keys.isEmpty()) "선택 없음" else keys.joinToString(" · ") { catalog.word(group,it) }
    fun value(field: ShareField): String? {
        val p=person()
        return when(field) {
            ShareField.ALIAS -> p?.let { "${alias(it)} · ${catalog.word("ages",it.ageKey)}" }
            ShareField.CONDITIONS -> p?.let { words("conditions",it.conditions) }
            ShareField.MEDICATIONS -> p?.let { words("medications",it.medications) }
            ShareField.ALLERGIES -> p?.let { words("allergies",it.allergies) }
            ShareField.PREGNANCY -> p?.let { catalog.word("pregnancy",it.pregnancyKey) }
            ShareField.ACCESS -> p?.let { words("access",it.access.needs) }
            ShareField.GUARDIAN -> p?.let { catalog.word("guardians",it.guardianKey) }
            ShareField.LOCATION -> if(locationAvailable) "가상 훈련장 A · DEMO_GRID (120, 80) · GPS 아님" else null
            ShareField.VEHICLE -> "DEMO-CAR-01 · 가상 전기차 · 가상 SOC 72% · 실제 SDK와 별개"
            ShareField.SUMMARY -> summary()
            ShareField.TEXT -> current?.messages?.lastOrNull()
            ShareField.VIDEO -> null
        }
    }
    fun share(fields: Set<ShareField>) {
        val s=active();require(fields.isNotEmpty());require(ShareField.VIDEO !in fields)
        val payload=fields.associateWith { requireNotNull(value(it)) { "공유할 정보가 없습니다." } }
        update("SELECTED_FIELDS_SHARED",s.copy(status=LinkStatus.SUPPORT_IN_PROGRESS,shared=s.shared+payload))
    }
    fun revoke(field: ShareField? = null) {
        val s=active();val fields=if(field==null)ShareField.entries.toSet() else setOf(field)
        if(ShareField.VIDEO in fields)videoPreview=false
        if(ShareField.LOCATION in fields)cooperation.invalidate()
        if(field==null || field==ShareField.VEHICLE)vehicleContextShare=null
        update("CONSENT_REVOKED",s.copy(shared=s.shared-fields,video=if(ShareField.VIDEO in fields)VideoStatus.STOPPED else s.video,
            navigation=if(ShareField.LOCATION in fields)NavigationStatus.NONE else s.navigation,destination=if(ShareField.LOCATION in fields)null else s.destination))
    }
    fun observe(key: String, discovered: String, glucose: Int? = null) {
        val s=active();check(!restrictions.complexBlocked);require(catalog.words["observations"]?.containsKey(key)==true)
        require(discovered in setOf("NOW","AGO5","AGO10","AGO30"));require(glucose==null || glucose in 20..600)
        update("SUMMARY_EDITED",s.copy(observation=key,discovered=discovered,demoGlucose=glucose,shared=s.shared-ShareField.SUMMARY))
    }
    fun summary(): String {
        val s=current ?: return "진행 중인 시연 없음"
        val whenText=mapOf("NOW" to "방금 발견(시연)","AGO5" to "5분 전 발견(시연)","AGO10" to "10분 전 발견(시연)","AGO30" to "30분 전 발견(시연)")[s.discovered]
        return summaries.summarize(ConsultationSummaryInput(person()?.let(::alias) ?: "미등록 승객",whenText.orEmpty(),
            if(locationAvailable) "가상 훈련장 A" else "위치 없음",catalog.word("observations",s.observation),s.target.purpose,s.demoGlucose))
    }
    fun requestVideo(source: VideoSource) { val s=active();check(!restrictions.videoBlocked);videoPreview=false;update("VIDEO_REQUESTED",s.copy(video=VideoStatus.REQUESTED,videoSource=source,shared=s.shared-ShareField.VIDEO)) }
    fun approveVideo() { val s=active();check(s.video==VideoStatus.REQUESTED);check(!restrictions.videoBlocked);update("VIDEO_CONSENT_GRANTED",s.copy(video=VideoStatus.APPROVED)) }
    fun startVideo() { val s=active();check(s.video==VideoStatus.APPROVED);check(!restrictions.videoBlocked);videoPreview=true;update("SYNTHETIC_VIDEO_SHARED",s.copy(video=VideoStatus.SHARING,shared=s.shared+(ShareField.VIDEO to "${s.videoSource.label} · 합성 도형 · 실제 촬영·전송 없음"))) }
    fun stopVideo() { val s=active();videoPreview=false;update("VIDEO_STOPPED",s.copy(video=VideoStatus.STOPPED,shared=s.shared-ShareField.VIDEO)) }
    fun openVideoPreview() { val s=active();check(s.video==VideoStatus.SHARING && ShareField.VIDEO in s.shared && !restrictions.videoBlocked);videoPreview=true;onChange() }
    fun sendPhrase(key: String) { val s=active();check(!restrictions.messagesBlocked);val text=requireNotNull(catalog.words["phrases"]?.get(key));update("MOCK_TEXT_SENT",s.copy(messages=(s.messages+"나(가상): $text").takeLast(12),shared=s.shared+(ShareField.TEXT to text))) }
    fun reply() { val s=active();update("MOCK_TEXT_REPLIED",s.copy(messages=(s.messages+"가상 상담원: 선택한 도움 요청을 확인했습니다.").takeLast(12))) }
    fun sign(connect: Boolean) { val s=active();if(connect)check(!restrictions.videoBlocked);update(if(connect)"MOCK_SIGN_CONNECTED" else "MOCK_SIGN_STOPPED",s.copy(sign=if(connect)SignStatus.CONNECTED else SignStatus.OFF)) }
    fun voice() { val s=active();update("MOCK_VOICE_GUIDANCE",s.copy(voice="소리 없이 문장 강조로 음성 안내를 시연합니다.")) }
    fun requestPolice() { val s=active();check(s.target!=ConnectionTarget.COUNSEL_109);update("POLICE_REQUESTED",s.copy(police=PoliceStatus.REQUESTED)) }
    fun reviewPolice() { val s=active();check(s.police==PoliceStatus.REQUESTED);update("POLICE_REVIEWING",s.copy(police=PoliceStatus.REVIEWING)) }
    fun decidePolice(approved: Boolean) { val s=active();check(s.police==PoliceStatus.REVIEWING);update(if(approved)"POLICE_MOCK_APPROVED" else "POLICE_REJECTED",s.copy(police=if(approved)PoliceStatus.APPROVED else PoliceStatus.REJECTED)) }
    fun giveGuidance() { val s=active();check(s.target in setOf(ConnectionTarget.EMERGENCY_119,ConnectionTarget.CONSULT_119));update("MOCK_OPERATOR_GUIDANCE",s.copy(guidance=true)) }
    fun proposeNavigation(destination: String) { val s=active();check(s.guidance);check(ShareField.LOCATION in s.shared && locationAvailable);require(destination in setOf("MEET_B","HOSPITAL_A"));update("NAVIGATION_PROPOSED",s.copy(destination=destination,navigation=NavigationStatus.PROPOSED)) }
    fun confirmNavigation() { val s=active();check(s.navigation==NavigationStatus.PROPOSED);update("DESTINATION_CONFIRMED",s.copy(navigation=NavigationStatus.CONFIRMED)) }
    fun setNavigation() { val s=active();check(s.navigation==NavigationStatus.CONFIRMED && s.guidance && ShareField.LOCATION in s.shared && locationAvailable);update("MOCK_DESTINATION_SET",s.copy(navigation=NavigationStatus.SET)) }
    fun safeCall(consent: Boolean) { val s=active();check(consent);update("MOCK_SAFE_CALL_LOOKUP",s.copy(safeCall=when(person()?.safeCallRegistered){true->"MOCK_REGISTERED";false->"MOCK_NOT_REGISTERED";null->"NOT_REQUESTED"})) }
    fun configureSafeCall(role: RegistrationRole?=null,line: DemoCallLine?=null,authority: Boolean?=null) {
        val s=active();check(privateUnlocked && !restrictions.complexBlocked)
        role?.let(safeCallLink::setRole);line?.let(safeCallLink::setLine);authority?.let(safeCallLink::setAuthority)
        update("SAFE_CALL_CONTEXT_CHANGED",s.copy(safeCall="NOT_REQUESTED"))
    }
    fun beginSafeCall(consent: Boolean,fault: SafeCallFault=SafeCallFault.NONE): SafeCallLookup? {
        val s=active();check(privateUnlocked && !restrictions.complexBlocked)
        val request=safeCallLink.begin(person()?.safeCallRegistered==true,consent,fault)
        update("MOCK_SAFE_CALL_STARTED",s.copy(safeCall="NOT_REQUESTED"));return request
    }
    fun completeSafeCall(request: SafeCallLookup): Boolean {
        if(current?.active!=true || !privateUnlocked || restrictions.complexBlocked)return false
        if(!safeCallLink.complete(request))return false
        val coarse=when(safeCallLink.outcome){SafeCallOutcome.MATCHED->"MOCK_REGISTERED";SafeCallOutcome.NOT_FOUND->"MOCK_NOT_REGISTERED";else->"NOT_REQUESTED"}
        update("MOCK_SAFE_CALL_FINISHED",active().copy(safeCall=coarse));return true
    }
    fun revokeSafeCall(){val s=active();safeCallLink.lock();update("SAFE_CALL_CONSENT_REVOKED",s.copy(safeCall="NOT_REQUESTED"))}
    fun finish(cancelled: Boolean=false) {
        val s=current ?: return;privateUnlocked=false;videoPreview=false;vehicleContextShare=null
        safeCallLink.attach(null,null)
        cooperation.clear()
        update("SESSION_DATA_CLEARED",s.copy(status=if(cancelled)LinkStatus.CANCELLED else LinkStatus.COMPLETED,personId=null,shared=emptyMap(),observation="HELP",demoGlucose=null,
            video=VideoStatus.STOPPED,police=PoliceStatus.NONE,navigation=NavigationStatus.NONE,destination=null,sign=SignStatus.OFF,voice="OFF",messages=emptyList(),safeCall="NOT_REQUESTED",guidance=false,discovered="NOW",driving=null))
    }
}

data class PowerEstimate(val availableKwh: Double?, val hours: Double?, val issue: String?)
object V2lCalculator {
    fun estimate(electric: Boolean, supported: Boolean, capacityKwh: Double, soc: Double, reserve: Double, efficiency: Double, loadW: Double, maxOutputW: Double=3600.0): PowerEstimate {
        if(!electric || !supported)return PowerEstimate(null,null,"V2L 지원 차종·사양을 확인하세요. 현재 가상 조건은 미지원입니다.")
        if(listOf(capacityKwh,soc,reserve,efficiency,loadW,maxOutputW).any { !it.isFinite() } || capacityKwh<=0 || soc !in 0.0..100.0 || reserve !in 0.0..100.0 || efficiency<=0 || efficiency>1 || loadW<0 || maxOutputW<=0)
            return PowerEstimate(null,null,"계산 범위와 숫자를 확인하세요.")
        val energy=capacityKwh*maxOf(0.0,soc-reserve)/100.0*efficiency
        if(loadW==0.0)return PowerEstimate(energy,null,"부하 0W에서는 사용 시간을 계산하지 않습니다.")
        if(loadW>maxOutputW)return PowerEstimate(energy,null,"가정한 출력 한도를 초과했습니다.")
        return PowerEstimate(energy,energy/(loadW/1000.0),null)
    }
}
