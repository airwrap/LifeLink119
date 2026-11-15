package com.lifelink119.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Unanswered must never be interpreted as a negative clinical or consent answer. */
enum class OptionalAnswer(val label: String) {
    UNSPECIFIED("미입력"),
    YES("예"),
    NO("아니요"),
}

enum class LivingArrangement(val label: String) {
    UNSPECIFIED("미입력"),
    TOGETHER("동거"),
    SEPARATE("별거"),
}

data class SafeCallGuardian(
    val name: String = "",
    val relationCategory: String = "",
    val relation: String = "",
    val mobile: String = "",
    val phone: String = "",
    val email: String = "",
    val living: LivingArrangement = LivingArrangement.UNSPECIFIED,
    val smsPreference: OptionalAnswer = OptionalAnswer.UNSPECIFIED,
) {
    override fun toString() = "SafeCallGuardian([REDACTED])"
}

/** User-entered copy of the five supplied official form sections, never an agency response. */
data class SafeCallDetails(
    val applicationType: String = "",
    val applicationDetail: String = "",
    val mobility: String = "",
    val bloodGroup: String = "",
    val rhesus: String = "",
    val elevator: OptionalAnswer = OptionalAnswer.UNSPECIFIED,
    val canCall: OptionalAnswer = OptionalAnswer.UNSPECIFIED,
    val callUnavailableReason: String = "",
    val pastHistory: String = "",
    val currentConditions: List<String> = emptyList(),
    val infectiousDiseases: List<String> = emptyList(),
    val facilityName: String = "",
    val specialty: String = "",
    val dayPhone: String = "",
    val nightPhone: String = "",
    val disabilities: List<String> = emptyList(),
    val discomfort: String = "",
    val constitution: String = "",
    val responderRequests: String = "",
    val frequentPlace: String = "",
    val guardians: List<SafeCallGuardian> = emptyList(),
    val guardianCallConfirmed: Boolean = false,
) {
    override fun toString() = "SafeCallDetails([REDACTED])"
}

object SafeCallDetailsCodec {
    const val MAX_GUARDIANS = 8
    const val MAX_LIST_ITEMS = 16
    const val MAX_LIST_TEXT = 120
    private val scalarLimits =
        linkedMapOf(
            "applicationType" to 80,
            "applicationDetail" to 80,
            "mobility" to 120,
            "bloodGroup" to 2,
            "rhesus" to 2,
            "callUnavailableReason" to 300,
            "pastHistory" to 1500,
            "facilityName" to 120,
            "specialty" to 80,
            "dayPhone" to 24,
            "nightPhone" to 24,
            "discomfort" to 500,
            "constitution" to 500,
            "responderRequests" to 500,
            "frequentPlace" to 15,
        )

    private fun scalars(d: SafeCallDetails) =
        linkedMapOf(
            "applicationType" to d.applicationType,
            "applicationDetail" to d.applicationDetail,
            "mobility" to d.mobility,
            "bloodGroup" to d.bloodGroup,
            "rhesus" to d.rhesus,
            "callUnavailableReason" to d.callUnavailableReason,
            "pastHistory" to d.pastHistory,
            "facilityName" to d.facilityName,
            "specialty" to d.specialty,
            "dayPhone" to d.dayPhone,
            "nightPhone" to d.nightPhone,
            "discomfort" to d.discomfort,
            "constitution" to d.constitution,
            "responderRequests" to d.responderRequests,
            "frequentPlace" to d.frequentPlace,
        )

    fun validPhone(value: String) =
        value.isBlank() ||
            value.matches(Regex("[+0-9 ()-]{7,24}")) && value.count(Char::isDigit) in 7..15

    fun error(d: SafeCallDetails): String? {
        if (scalars(d).any { (key, value) -> value.length > scalarLimits.getValue(key) })
            return "입력 길이를 확인해 주세요. 자주 가는 곳은 15자까지 입력할 수 있습니다."
        if (d.bloodGroup !in setOf("", "A", "B", "AB", "O") || d.rhesus !in setOf("", "+", "-"))
            return "혈액형과 Rh 선택을 확인해 주세요."
        if (!validPhone(d.dayPhone) || !validPhone(d.nightPhone))
            return "진료기관의 주간·야간 전화번호 형식을 확인해 주세요."
        if (
            listOf(d.currentConditions, d.infectiousDiseases, d.disabilities).any { list ->
                list.size > MAX_LIST_ITEMS ||
                    list.any { it.isBlank() || it.length > MAX_LIST_TEXT } ||
                    list.distinct().size != list.size
            }
        )
            return "질환·감염병·장애 목록은 빈 항목과 중복 없이 각각 16개, 항목당 120자까지 입력해 주세요."
        if (d.guardians.size > MAX_GUARDIANS) return "보호자는 8명까지 차량에 보관할 수 있습니다."
        d.guardians.forEach { g ->
            if (
                g.name.isBlank() ||
                    g.name.length > 80 ||
                    g.relationCategory.length > 80 ||
                    g.relation.length > 80
            )
                return "보호자 이름과 관계의 입력 길이를 확인해 주세요."
            if (g.mobile.isBlank() && g.phone.isBlank()) return "보호자에게 연락할 휴대폰 또는 전화번호를 입력해 주세요."
            if (!validPhone(g.mobile) || !validPhone(g.phone)) return "보호자 연락처 형식을 확인해 주세요."
            if (
                g.email.length > 254 ||
                    g.email.isNotBlank() && !g.email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            )
                return "보호자 이메일 형식을 확인해 주세요."
        }
        if (d.canCall == OptionalAnswer.NO && (d.guardians.isEmpty() || !d.guardianCallConfirmed))
            return "통화가 불가능한 경우 통화 가능한 보호자의 연락처를 등록하고 확인해 주세요."
        return null
    }

    fun encode(d: SafeCallDetails): JSONObject {
        require(error(d) == null) { error(d).orEmpty() }
        return JSONObject().apply {
            scalars(d).forEach { (key, value) -> put(key, value) }
            put("elevator", d.elevator.name)
            put("canCall", d.canCall.name)
            put("currentConditions", JSONArray(d.currentConditions))
            put("infectiousDiseases", JSONArray(d.infectiousDiseases))
            put("disabilities", JSONArray(d.disabilities))
            put("guardianCallConfirmed", d.guardianCallConfirmed)
            put(
                "guardians",
                JSONArray(
                    d.guardians.map { g ->
                        JSONObject()
                            .put("name", g.name)
                            .put("relationCategory", g.relationCategory)
                            .put("relation", g.relation)
                            .put("mobile", g.mobile)
                            .put("phone", g.phone)
                            .put("email", g.email)
                            .put("living", g.living.name)
                            .put("smsPreference", g.smsPreference.name)
                    }
                ),
            )
        }
    }

    fun decode(o: JSONObject): SafeCallDetails {
        fun list(key: String): List<String> {
            val a = o.getJSONArray(key)
            require(a.length() <= MAX_LIST_ITEMS)
            return (0 until a.length()).map { a.getString(it) }
        }
        val guardians = o.getJSONArray("guardians")
        require(guardians.length() <= MAX_GUARDIANS)
        return SafeCallDetails(
                applicationType = o.getString("applicationType"),
                applicationDetail = o.getString("applicationDetail"),
                mobility = o.getString("mobility"),
                bloodGroup = o.getString("bloodGroup"),
                rhesus = o.getString("rhesus"),
                elevator = OptionalAnswer.valueOf(o.getString("elevator")),
                canCall = OptionalAnswer.valueOf(o.getString("canCall")),
                callUnavailableReason = o.getString("callUnavailableReason"),
                pastHistory = o.getString("pastHistory"),
                currentConditions = list("currentConditions"),
                infectiousDiseases = list("infectiousDiseases"),
                facilityName = o.getString("facilityName"),
                specialty = o.getString("specialty"),
                dayPhone = o.getString("dayPhone"),
                nightPhone = o.getString("nightPhone"),
                disabilities = list("disabilities"),
                discomfort = o.getString("discomfort"),
                constitution = o.getString("constitution"),
                responderRequests = o.getString("responderRequests"),
                frequentPlace = o.getString("frequentPlace"),
                guardians =
                    (0 until guardians.length()).map { index ->
                        val g = guardians.getJSONObject(index)
                        SafeCallGuardian(
                            g.getString("name"),
                            g.getString("relationCategory"),
                            g.getString("relation"),
                            g.getString("mobile"),
                            g.getString("phone"),
                            g.getString("email"),
                            LivingArrangement.valueOf(g.getString("living")),
                            OptionalAnswer.valueOf(g.getString("smsPreference")),
                        )
                    },
                guardianCallConfirmed = o.getBoolean("guardianCallConfirmed"),
            )
            .also { require(error(it) == null) { error(it).orEmpty() } }
    }
}
