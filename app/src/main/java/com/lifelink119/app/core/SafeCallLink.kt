package com.lifelink119.app.core

enum class RegistrationRole(val label: String) { SELF("수혜자 본인"), PROXY("가족·대리자") }
enum class LocalRegistration(val label: String) { UNKNOWN("등록 여부 확인 안 됨"), USER_REPORTED("등록 완료 · 사용자 확인") }
enum class DemoCallLine(val label: String) {
    REGISTERED("등록한 전화"), OTHER("다른 가족 전화"), VEHICLE("차량 자체 회선"), UNKNOWN("회선 확인 불가"), SHARED("가족 공용 전화")
}
enum class SafeCallFault(val label: String) { NONE("정상 응답"), TIMEOUT("시간 초과"), ERROR("조회 오류"), UNSUPPORTED("기관 연계 미지원") }
enum class SafeCallOutcome(val label: String) {
    NOT_REQUESTED("확인 전"), TARGET_REQUIRED("도움 대상자를 선택해 주세요"), AUTHORITY_REQUIRED("가족 정보 관리 권한을 확인해 주세요"),
    PENDING("등록 정보 확인 중"), MATCHED("예시 등록번호와 일치"), DIFFERENT("등록번호와 다른 회선입니다"),
    UNKNOWN("발신 회선을 확인할 수 없습니다"), AMBIGUOUS("공용 번호입니다 · 대상 확인이 필요합니다"),
    NOT_FOUND("등록된 예시 정보가 없습니다"), TIMEOUT("확인 시간이 초과되었습니다"), ERROR("등록 정보를 확인하지 못했습니다"),
    UNSUPPORTED("공식 기관 조회는 연결되지 않았습니다")
}

/** Synthetic context only. Real local records/phone numbers never enter this controller. */
data class SafeCallLookup internal constructor(
    val generation: Long, val session: String, val person: String, val registered: Boolean,
    val line: DemoCallLine, val fault: SafeCallFault
)

class SafeCallLink {
    private var session: String? = null
    private var person: String? = null
    private var generation = 0L
    var role = RegistrationRole.SELF; private set
    var line = DemoCallLine.UNKNOWN; private set
    var authorityConfirmed = false; private set
    var consent = false; private set
    var outcome = SafeCallOutcome.NOT_REQUESTED; private set
    val officialConnected = false

    fun attach(sessionId: String?, personId: String?) {
        require(sessionId == null || sessionId.matches(Regex("DEMO-[0-9]{4,}")))
        require(personId == null || personId.matches(Regex("DEMO-P-(?:[1-9]|1[0-2])")))
        session = sessionId; person = personId; role = RegistrationRole.SELF; line = DemoCallLine.UNKNOWN
        authorityConfirmed = false; revoke()
    }
    fun setRole(value: RegistrationRole) { role = value; authorityConfirmed = false; revoke() }
    fun setAuthority(value: Boolean) { authorityConfirmed = value; revoke() }
    fun setLine(value: DemoCallLine) { line = value; revoke() }
    fun revoke() { generation++; consent = false; outcome = SafeCallOutcome.NOT_REQUESTED }
    fun lock() { authorityConfirmed = false; revoke() }
    fun begin(registered: Boolean, accepted: Boolean, fault: SafeCallFault = SafeCallFault.NONE): SafeCallLookup? {
        check(accepted) { "이번 확인에 동의해 주세요." }
        check(session != null) { "긴급 연결을 먼저 시작하세요." }
        revoke()
        if (person == null) { outcome = SafeCallOutcome.TARGET_REQUIRED; return null }
        if (role == RegistrationRole.PROXY && !authorityConfirmed) { outcome = SafeCallOutcome.AUTHORITY_REQUIRED; return null }
        consent = true; outcome = SafeCallOutcome.PENDING
        return SafeCallLookup(generation, requireNotNull(session), requireNotNull(person), registered, line, fault)
    }
    fun complete(request: SafeCallLookup): Boolean {
        if (!consent || outcome != SafeCallOutcome.PENDING || request.generation != generation || request.session != session || request.person != person) return false
        outcome = when (request.fault) {
            SafeCallFault.TIMEOUT -> SafeCallOutcome.TIMEOUT
            SafeCallFault.ERROR -> SafeCallOutcome.ERROR
            SafeCallFault.UNSUPPORTED -> SafeCallOutcome.UNSUPPORTED
            SafeCallFault.NONE -> when {
                !request.registered -> SafeCallOutcome.NOT_FOUND
                request.line == DemoCallLine.REGISTERED -> SafeCallOutcome.MATCHED
                request.line == DemoCallLine.UNKNOWN -> SafeCallOutcome.UNKNOWN
                request.line == DemoCallLine.SHARED -> SafeCallOutcome.AMBIGUOUS
                else -> SafeCallOutcome.DIFFERENT
            }
        }
        return true
    }
}

/** Public navigation only: no patient data, API endpoint, token, autofill or submission. */
enum class OfficialHealthSite(val label: String, val url: String) {
    SAFE_CALL("119안심콜 공식 가입·수정", "https://u119.nfa.go.kr/"),
    SAFE_CALL_GUIDE("소방청 이용 안내", "https://www.nfa.go.kr/nfa/safetyinfo/emergencyservice/urelaxcallservice/"),
    HEALTHWAY("건강정보 고속도로 공식 안내", "https://www.myhealthway.go.kr/")
}
