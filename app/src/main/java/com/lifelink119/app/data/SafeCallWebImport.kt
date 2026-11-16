package com.lifelink119.app.data

import com.lifelink119.app.core.LocalRegistration
import com.lifelink119.app.core.OfficialBrowserPolicy
import com.lifelink119.app.core.RegistrationRole
import org.json.JSONArray
import org.json.JSONObject

/** A reviewed copy of a web form, NOT an official API response or registration verification. */
data class SafeCallWebSource(
    val retrievedAt: Long,
    val fields: List<String>,
    val editedAfterImport: Boolean = false,
) {
    override fun toString() = "SafeCallWebSource([REDACTED])"
}

class SafeCallImportPreview(
    val record: PersonalHealthRecord,
    val imported: List<Pair<String, String>>,
    val missing: List<String>,
    val identityConflict: Boolean,
) {
    override fun toString() = "SafeCallImportPreview([REDACTED])"
}

object SafeCallWebImport {
    const val MAX_JSON_BYTES = 96_000
    val labels =
        linkedMapOf(
            "name" to "이름",
            "registeredPhone" to "등록 휴대폰 번호",
            "applicationType" to "신청유형",
            "applicationDetail" to "신청유형 상세",
            "mobility" to "거동여부",
            "bloodGroup" to "혈액형",
            "rhesus" to "Rh",
            "elevator" to "승강기",
            "canCall" to "전화통화 가능여부",
            "callUnavailableReason" to "통화불가 사유",
            "pastHistory" to "과거질환·수술력",
            "currentConditions" to "현재질환",
            "medicationHistory" to "복용약물",
            "infectiousDiseases" to "법정감염병",
            "facilityName" to "진료기관",
            "specialty" to "진료과목",
            "dayPhone" to "주간 연락처",
            "nightPhone" to "야간 연락처",
            "disabilities" to "장애유형",
            "discomfort" to "불편사항",
            "constitution" to "특이체질",
            "responderRequests" to "구급대원 요구사항",
            "frequentPlace" to "자주 가는 곳",
            "guardians" to "보호자 목록",
        )

    fun allowsOrigin(url: String) = OfficialBrowserPolicy.allowedHost(url) == "u119.nfa.go.kr"

    fun validSource(value: SafeCallWebSource) =
        value.retrievedAt > 0 &&
            value.fields.isNotEmpty() &&
            value.fields.size <= labels.size &&
            value.fields.distinct().size == value.fields.size &&
            value.fields.all { it in labels }

    private fun text(value: Any): String {
        require(value is String)
        require(
            !value.contains(
                Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f\\u202a-\\u202e\\u2066-\\u2069]")
            )
        )
        return value.trim()
    }

    /**
     * Whitelist both keys and types; never evaluate expressions or infer diagnosis/code meanings.
     */
    fun prepare(
        raw: String,
        retrievedAt: Long,
        existing: PersonalHealthRecord?,
    ): SafeCallImportPreview {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES)
        val root = JSONObject(raw)
        require(root.keys().asSequence().toSet() == setOf("schema", "origin", "fields", "missing"))
        require(root.optInt("schema") == 1 && !root.has("error"))
        require(root.getString("origin") == "https://u119.nfa.go.kr")
        val f = root.getJSONObject("fields")
        require(f.keys().asSequence().all { it in labels })
        require(retrievedAt > 0)
        val missing = root.getJSONArray("missing")
        require(missing.length() <= labels.size)
        repeat(missing.length()) { require(missing.getString(it) in labels) }
        fun value(key: String) = if (f.has(key)) text(f.get(key)) else ""
        fun list(key: String): List<String> {
            if (!f.has(key)) return emptyList()
            val array = f.getJSONArray(key)
            require(array.length() <= SafeCallDetailsCodec.MAX_LIST_ITEMS)
            return (0 until array.length())
                .map { text(array.get(it)) }
                .also { entries ->
                    require(
                        entries.all {
                            it.isNotBlank() && it.length <= SafeCallDetailsCodec.MAX_LIST_TEXT
                        }
                    )
                    require(entries.distinct().size == entries.size)
                }
        }
        fun optional(key: String, yes: Set<String>, no: Set<String>): OptionalAnswer =
            when (val v = value(key)) {
                "" -> OptionalAnswer.UNSPECIFIED
                in yes -> OptionalAnswer.YES
                in no -> OptionalAnswer.NO
                else -> throw SafeCallImportFailure("VALIDATION", key)
            }
        val name = value("name")
        val phone = value("registeredPhone")
        if (name.isBlank() || name.length > 80 || name.contains(Regex("[*●•]")))
            throw SafeCallImportFailure("VALIDATION", "name")
        if (phone.isBlank() || !SafeCallDetailsCodec.validPhone(phone))
            throw SafeCallImportFailure("VALIDATION", "registeredPhone")
        val base = existing ?: PersonalHealthRecord()
        require(base.id == "SELF" && base.role == RegistrationRole.SELF)
        val identityConflict =
            (base.name.isNotBlank() && base.name != name) ||
                (base.registeredPhone.isNotBlank() &&
                    base.registeredPhone.filter(Char::isDigit) != phone.filter(Char::isDigit))
        val d = base.safeCall
        fun merged(key: String, old: String) = value(key).ifBlank { old }
        fun joined(key: String, old: List<String>) = (old + list(key)).distinct()
        val importedGuardians =
            if (!f.has("guardians")) emptyList()
            else {
                val a = f.getJSONArray("guardians")
                require(a.length() <= SafeCallDetailsCodec.MAX_GUARDIANS)
                (0 until a.length()).map { i ->
                    val g = a.getJSONObject(i)
                    require(
                        g.keys().asSequence().toSet() ==
                            setOf(
                                "name",
                                "relation",
                                "mobile",
                                "phone",
                                "email",
                                "living",
                                "smsPreference",
                            )
                    )
                    fun s(key: String) = text(g.get(key))
                    SafeCallGuardian(
                            name = s("name"),
                            relation = s("relation"),
                            mobile = s("mobile"),
                            phone = s("phone"),
                            email = s("email"),
                            living =
                                when (s("living")) {
                                    "",
                                    "미입력" -> LivingArrangement.UNSPECIFIED
                                    "동거" -> LivingArrangement.TOGETHER
                                    "별거" -> LivingArrangement.SEPARATE
                                    else -> throw SafeCallImportFailure("VALIDATION", "guardians")
                                },
                            smsPreference =
                                when (s("smsPreference")) {
                                    "",
                                    "미입력" -> OptionalAnswer.UNSPECIFIED
                                    "예",
                                    "수신" -> OptionalAnswer.YES
                                    "아니요",
                                    "아니오",
                                    "미수신" -> OptionalAnswer.NO
                                    else -> throw SafeCallImportFailure("VALIDATION", "guardians")
                                },
                        )
                        .also { require(!it.name.contains(Regex("[*●•]"))) }
                }
            }
        // Do not drop an existing person because a virtualized web grid has unloaded their row.
        // Keep web ordering first so a newly reviewed contact is not hidden behind an older phone.
        // Retain unmatched old rows: virtualized grids may not have loaded every guardian.
        val guardians = (importedGuardians + d.guardians).distinct()
        val elevator = optional("elevator", setOf("있음", "예"), setOf("없음", "아니요"))
        val canCall = optional("canCall", setOf("가능", "예"), setOf("불가능", "아니요"))
        val blood = value("bloodGroup").removeSuffix("형")
        if (blood !in setOf("", "A", "B", "AB", "O"))
            throw SafeCallImportFailure("VALIDATION", "bloodGroup")
        val rh =
            when (val v = value("rhesus")) {
                "",
                "+",
                "-" -> v
                "Rh+",
                "RH+",
                "Rh(+)" -> "+"
                "Rh-",
                "RH-",
                "Rh(-)" -> "-"
                else -> throw SafeCallImportFailure("VALIDATION", "rhesus")
            }
        val details =
            d.copy(
                applicationType = merged("applicationType", d.applicationType),
                applicationDetail = merged("applicationDetail", d.applicationDetail),
                mobility = merged("mobility", d.mobility),
                bloodGroup = blood.ifBlank { d.bloodGroup },
                rhesus = rh.ifBlank { d.rhesus },
                elevator = if (elevator == OptionalAnswer.UNSPECIFIED) d.elevator else elevator,
                canCall = if (canCall == OptionalAnswer.UNSPECIFIED) d.canCall else canCall,
                callUnavailableReason = merged("callUnavailableReason", d.callUnavailableReason),
                pastHistory = merged("pastHistory", d.pastHistory),
                currentConditions = joined("currentConditions", d.currentConditions),
                infectiousDiseases = joined("infectiousDiseases", d.infectiousDiseases),
                facilityName = merged("facilityName", d.facilityName),
                specialty = merged("specialty", d.specialty),
                dayPhone = merged("dayPhone", d.dayPhone),
                nightPhone = merged("nightPhone", d.nightPhone),
                disabilities = joined("disabilities", d.disabilities),
                discomfort = merged("discomfort", d.discomfort),
                constitution = merged("constitution", d.constitution),
                responderRequests = merged("responderRequests", d.responderRequests),
                frequentPlace = merged("frequentPlace", d.frequentPlace),
                guardians = guardians,
                guardianCallConfirmed = false,
            )
        // Contact confirmation is performed by the user in the review, not inferred from a website.
        require(
            SafeCallDetailsCodec.error(details.copy(canCall = OptionalAnswer.UNSPECIFIED)) == null
        )
        val imported =
            labels.keys.filter { key ->
                if (!f.has(key)) false
                else
                    when (val v = f.get(key)) {
                        is JSONArray -> v.length() > 0
                        is String -> v.isNotBlank()
                        else -> false
                    }
            }
        // Medical details are optional. A loaded, blank medical form is legitimate and must
        // not require users to invent a condition to import their own identity/contact.
        // Identity-only payloads with no recognized medical form controls remain rejected.
        if (!f.keys().asSequence().any { it !in setOf("name", "registeredPhone") })
            throw SafeCallImportFailure("FORM_NOT_READY")
        val record =
            base.copy(
                name = name,
                registeredPhone = phone,
                medicationHistory = merged("medicationHistory", base.medicationHistory),
                // Visiting a form is not proof that the server has registered/saved it.
                registration =
                    if (identityConflict) LocalRegistration.UNKNOWN else base.registration,
                safeCall = details,
                updatedAt = retrievedAt,
                webSource = SafeCallWebSource(retrievedAt, imported),
            )
        require(record.medicationHistory.length <= 3000)
        val preview =
            imported.map { key ->
                labels.getValue(key) to
                    when (key) {
                        "guardians" ->
                            guardians.joinToString("\n") {
                                listOf(
                                        it.name,
                                        it.relation,
                                        it.mobile,
                                        it.phone,
                                        it.email,
                                        it.living.label,
                                        it.smsPreference.label,
                                    )
                                    .filter(String::isNotBlank)
                                    .joinToString(" · ")
                            }
                        "currentConditions",
                        "infectiousDiseases",
                        "disabilities" -> list(key).joinToString(" · ")
                        else -> value(key)
                    }
            }
        return SafeCallImportPreview(
            record,
            preview,
            labels.filterKeys { !f.has(it) }.values.toList(),
            identityConflict,
        )
    }
}
