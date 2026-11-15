package com.lifelink119.app.data

import android.content.Context
import com.lifelink119.app.core.*
import org.json.JSONArray
import org.json.JSONObject

object ExperienceCatalogParser {
    fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    fun profile(o: JSONObject) =
        PersonProfile(
            o.getString("id"),
            o.getString("aliasKey"),
            o.getString("ageKey"),
            strings(o.optJSONArray("conditions")).toSet(),
            strings(o.optJSONArray("medications")).toSet(),
            strings(o.optJSONArray("allergies")).toSet(),
            o.optString("pregnancyKey", "NONE"),
            o.optString("guardianKey", "NONE"),
            AccessPreferences(
                strings(o.optJSONArray("access")).toSet(),
                o.optBoolean("largeText"),
                o.optBoolean("easy"),
            ),
            o.optBoolean("safeCallRegistered"),
        )

    fun profileJson(p: PersonProfile) =
        JSONObject()
            .put("id", p.id)
            .put("aliasKey", p.aliasKey)
            .put("ageKey", p.ageKey)
            .put("conditions", JSONArray(p.conditions.toList()))
            .put("medications", JSONArray(p.medications.toList()))
            .put("allergies", JSONArray(p.allergies.toList()))
            .put("pregnancyKey", p.pregnancyKey)
            .put("guardianKey", p.guardianKey)
            .put("access", JSONArray(p.access.needs.toList()))
            .put("largeText", p.access.largeText)
            .put("easy", p.access.easy)
            .put("safeCallRegistered", p.safeCallRegistered)

    fun parse(raw: String): ExperienceCatalog {
        val o = JSONObject(raw)
        require(o.getBoolean("syntheticOnly"))
        val entries = o.getJSONArray("scenarios")
        val scenarios =
            (0 until entries.length()).map { i ->
                val x = entries.getJSONObject(i)
                ScenarioSpec(
                    x.getString("id"),
                    x.getString("title"),
                    ConnectionTarget.valueOf(x.getString("target")),
                    strings(x.getJSONArray("priorities")).map(SupportAction::valueOf),
                    if (x.isNull("profileHint")) null else x.getString("profileHint"),
                    x.getString("context"),
                    x.getString("observation"),
                )
            }
        val words =
            o.getJSONObject("words").let { groups ->
                groups.keys().asSequence().associateWith { key ->
                    groups.getJSONObject(key).let { g ->
                        g.keys().asSequence().associateWith(g::getString)
                    }
                }
            }
        val seeds =
            o.getJSONArray("profiles").let { a ->
                (0 until a.length()).map { profile(a.getJSONObject(it)) }
            }
        val result = ExperienceCatalog(scenarios, seeds, words)
        require(
            scenarios.map { it.id }.toSet() ==
                (1..14).map { "S%02d".format(java.util.Locale.ROOT, it) }.toSet() +
                    (1..4).map { "E%02d".format(java.util.Locale.ROOT, it) } +
                    "D01"
        )
        require(scenarios.size == 19)
        require(seeds.all(result::validProfile))
        require(
            scenarios.all {
                it.priorities.distinct().size == 3 &&
                    (it.profileHint == null || seeds.any { p -> p.id == it.profileHint })
            }
        )
        return result
    }
}

object CooperationCatalogParser {
    fun parse(raw: String): CooperationCatalog {
        val o = JSONObject(raw)
        require(o.getBoolean("syntheticOnly"))
        require(o.getString("coordinateSystem") == "DEMO_GRID")
        val cases =
            o.getJSONArray("scenarios").let { a ->
                (0 until a.length()).map { i ->
                    val x = a.getJSONObject(i)
                    CooperationCase(
                        x.getString("id"),
                        x.getString("title"),
                        x.getInt("baselineSeconds"),
                        x.getInt("activationSeconds"),
                        x.getString("requiredCapability"),
                        x.getBoolean("available"),
                    )
                }
            }
        val vehicles =
            o.getJSONArray("vehicles").let { a ->
                (0 until a.length()).map { i ->
                    val x = a.getJSONObject(i)
                    CooperationVehicle(
                        x.getString("id"),
                        x.getString("name"),
                        x.getInt("travelSeconds"),
                        ExperienceCatalogParser.strings(x.getJSONArray("capabilities")).toSet(),
                        x.getBoolean("optedIn"),
                        x.getBoolean("available"),
                    )
                }
            }
        require(cases.map { it.id }.toSet() == setOf("EARLY_HELP", "EMS_FIRST", "NO_RESPONDER"))
        require(cases.size == 3)
        require(
            vehicles.map { it.id }.toSet() ==
                setOf("DEMO-HELPER-A", "DEMO-HELPER-B", "DEMO-HELPER-C", "DEMO-HELPER-D")
        )
        require(vehicles.size == 4)
        require(cases.all { it.baselineSeconds in 1..3600 && it.activationSeconds in 0..300 })
        require(vehicles.all { it.travelSeconds in 1..3600 })
        return CooperationCatalog(cases, vehicles)
    }
}

/** Only IDs from the fixed, synthetic vocabulary are persisted. No real-data input mode. */
class FakeProfileRepository(context: Context, private val catalog: ExperienceCatalog) {
    private val prefs =
        context.getSharedPreferences("lifelink_synthetic_profiles_v05", Context.MODE_PRIVATE)
    var storageUnavailable = false
        private set

    fun load(): List<PersonProfile> =
        runCatching {
                if (!prefs.contains("profiles_cipher") && !prefs.contains("profiles"))
                    return catalog.profiles
                val legacy = !prefs.contains("profiles_cipher")
                val raw =
                    if (legacy) prefs.getString("profiles", "[]") ?: "[]"
                    else
                        MedicalCrypto.decrypt(
                            requireNotNull(prefs.getString("profiles_cipher", null))
                        )
                require(raw.length <= 20_000)
                val a = JSONArray(raw)
                require(a.length() <= 12)
                (0 until a.length())
                    .map { ExperienceCatalogParser.profile(a.getJSONObject(it)) }
                    .also { p ->
                        require(
                            p.all(catalog::validProfile) &&
                                p.map { it.id }.distinct().size == p.size
                        )
                        if (legacy) save(p)
                    }
            }
            .onFailure { storageUnavailable = true }
            .getOrDefault(emptyList())

    fun save(values: List<PersonProfile>) {
        require(values.size <= 12 && values.all(catalog::validProfile))
        val encrypted =
            MedicalCrypto.encrypt(
                JSONArray(values.map(ExperienceCatalogParser::profileJson)).toString()
            )
        check(prefs.edit().putString("profiles_cipher", encrypted).remove("profiles").commit())
        storageUnavailable = false
    }

    fun eraseMedicalData() {
        check(prefs.edit().remove("profiles_cipher").remove("profiles").commit())
        storageUnavailable = false
    }

    fun preferences() = Pair(prefs.getBoolean("largeText", false), prefs.getBoolean("easy", false))

    fun preferences(largeText: Boolean, easy: Boolean) {
        prefs.edit().putBoolean("largeText", largeText).putBoolean("easy", easy).apply()
    }
}

interface EmergencyCallGateway {
    fun request(target: ConnectionTarget): String

    val source: CapabilityStatus
}

/** Uses the existing MockDispatchGateway; contains no telephone Intent or network request. */
class DemoEmergencyCallGateway(private val dispatch: MockDispatchGateway) : EmergencyCallGateway {
    override val source = CapabilityStatus.MOCK

    override fun request(target: ConnectionTarget): String {
        val receipt = dispatch.connect(target.legacy)
        return "DEMO-" +
            System.currentTimeMillis().toString() +
            receipt.ticket.removePrefix("DEMO-").takeLast(4).padStart(4, '0')
    }
}

object ExperienceStateCodec {
    /**
     * Android saved-instance state contains no personal profile, clinical text or medical consent
     * payload.
     */
    fun encodePrivacySafe(s: EmergencySession?): String? =
        encode(
            s?.copy(
                personId = null,
                shared =
                    s.shared.filterKeys { it in setOf(ShareField.LOCATION, ShareField.VEHICLE) },
                observation = "HELP",
                discovered = "NOW",
                demoGlucose = null,
                video = VideoStatus.STOPPED,
                sign = SignStatus.OFF,
                safeCall = "NOT_REQUESTED",
                voice = "OFF",
                messages = emptyList(),
                driving = s.driving?.privacySafe(),
            )
        )

    fun encode(s: EmergencySession?): String? =
        s?.let {
            JSONObject()
                .put("id", it.id)
                .put("target", it.target.name)
                .put("scenario", it.scenarioId)
                .put("status", it.status.name)
                .put("person", it.personId)
                .put("shared", JSONObject(it.shared.mapKeys { e -> e.key.name }))
                .put("observation", it.observation)
                .put("discovered", it.discovered)
                .put("glucose", it.demoGlucose)
                .put("video", it.video.name)
                .put("videoSource", it.videoSource.name)
                .put("police", it.police.name)
                .put("navigation", it.navigation.name)
                .put("destination", it.destination)
                .put("sign", it.sign.name)
                .put(
                    "driving",
                    it.driving?.let { d ->
                        JSONObject()
                            .put("trigger", d.trigger.name)
                            .put("safety", d.safety.name)
                            .put("report", d.report.name)
                            .put("reconfirm", d.reconfirm)
                    },
                )
                .put("guidance", it.guidance)
                .put("safeCall", it.safeCall)
                .put("voice", it.voice)
                .put("messages", JSONArray(it.messages))
                .put("events", JSONArray(it.events))
                .put("revision", it.revision)
                .toString()
        }

    fun decode(
        raw: String?,
        catalog: ExperienceCatalog,
        profiles: List<PersonProfile>,
    ): EmergencySession? =
        runCatching {
                if (raw == null) return null
                require(raw.length <= 24_000)
                val o = JSONObject(raw)
                val person = o.optString("person").takeIf { id -> profiles.any { it.id == id } }
                val observation = o.getString("observation")
                require(catalog.words["observations"]?.containsKey(observation) == true)
                val discovered = o.getString("discovered")
                require(discovered in setOf("NOW", "AGO5", "AGO10", "AGO30"))
                val glucose =
                    if (o.isNull("glucose")) null
                    else o.getInt("glucose").also { require(it in 20..600) }
                val phrases = catalog.words["phrases"].orEmpty().values
                val allowedMessages =
                    phrases.map { "나(가상): $it" }.toSet() + "가상 상담원: 선택한 도움 요청을 확인했습니다."
                val status = LinkStatus.valueOf(o.getString("status"))
                val target = ConnectionTarget.valueOf(o.getString("target"))
                val driving =
                    o.optJSONObject("driving")?.let { d ->
                        require(
                            o.getString("scenario") == "D01" &&
                                target == ConnectionTarget.EMERGENCY_119
                        )
                        require(
                            d.keys().asSequence().toSet() ==
                                setOf("trigger", "safety", "report", "reconfirm")
                        )
                        DrivingEmergencyState(
                                DrivingTrigger.valueOf(d.getString("trigger")),
                                DrivingSafety.valueOf(d.getString("safety")),
                                DrivingReport.valueOf(d.getString("report")),
                                d.optBoolean("reconfirm"),
                            )
                            .also {
                                require(
                                    it.report != DrivingReport.HANDED_OVER ||
                                        it.safety == DrivingSafety.SAFE_PLACE_REPORTED
                                )
                            }
                    }
                val s =
                    EmergencySession(
                        o.getString("id"),
                        target,
                        o.getString("scenario"),
                        status,
                        person,
                        observation = observation,
                        discovered = discovered,
                        demoGlucose = glucose,
                        video = VideoStatus.valueOf(o.getString("video")),
                        videoSource = VideoSource.valueOf(o.getString("videoSource")),
                        police = PoliceStatus.valueOf(o.getString("police")),
                        navigation = NavigationStatus.valueOf(o.getString("navigation")),
                        destination =
                            o.optString("destination").takeIf {
                                it in setOf("MEET_B", "HOSPITAL_A")
                            },
                        sign = SignStatus.valueOf(o.getString("sign")),
                        guidance = o.optBoolean("guidance"),
                        safeCall =
                            o.optString("safeCall").takeIf {
                                it in
                                    setOf("NOT_REQUESTED", "MOCK_REGISTERED", "MOCK_NOT_REGISTERED")
                            } ?: "NOT_REQUESTED",
                        voice =
                            o.optString("voice").takeIf { it == "소리 없이 문장 강조로 음성 안내를 시연합니다." }
                                ?: "OFF",
                        messages =
                            ExperienceCatalogParser.strings(o.optJSONArray("messages"))
                                .filter { it in allowedMessages }
                                .takeLast(12),
                        events =
                            ExperienceCatalogParser.strings(o.optJSONArray("events"))
                                .filter { it.matches(Regex("[A-Z_]{1,40}")) }
                                .takeLast(40),
                        revision = o.optInt("revision", 1).coerceIn(1, 1_000_000),
                        driving = driving,
                    )
                val engine = SessionEngine(catalog, profiles)
                engine.restore(s, null)
                val shared = o.optJSONObject("shared") ?: JSONObject()
                val clean =
                    ShareField.entries
                        .mapNotNull { field ->
                            if (!shared.has(field.name)) null
                            else {
                                val value = shared.getString(field.name)
                                val expected =
                                    when (field) {
                                        ShareField.VIDEO ->
                                            "${s.videoSource.label} · 합성 도형 · 실제 촬영·전송 없음"
                                        ShareField.TEXT -> value.takeIf { it in phrases }
                                        else -> engine.value(field)
                                    }
                                if (value == expected) field to value else null
                            }
                        }
                        .toMap()
                if (s.ended)
                    s.copy(
                        personId = null,
                        shared = emptyMap(),
                        demoGlucose = null,
                        messages = emptyList(),
                        video = VideoStatus.STOPPED,
                        sign = SignStatus.OFF,
                        navigation = NavigationStatus.NONE,
                        destination = null,
                        guidance = false,
                        police = PoliceStatus.NONE,
                        safeCall = "NOT_REQUESTED",
                        voice = "OFF",
                        observation = "HELP",
                        discovered = "NOW",
                        driving = null,
                    )
                else
                    s.copy(
                        shared = clean,
                        video =
                            if (s.video == VideoStatus.SHARING && ShareField.VIDEO !in clean)
                                VideoStatus.STOPPED
                            else s.video,
                        navigation =
                            if (ShareField.LOCATION in clean) s.navigation
                            else NavigationStatus.NONE,
                        destination = if (ShareField.LOCATION in clean) s.destination else null,
                    )
            }
            .getOrNull()
}
