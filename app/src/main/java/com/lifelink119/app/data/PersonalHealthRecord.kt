package com.lifelink119.app.data

import android.content.Context
import org.json.JSONObject

/** User-entered local data only. It is never used by the mock dispatch gateway. */
data class PersonalHealthRecord(
    val name: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val medicationHistory: String = ""
) { override fun toString() = "PersonalHealthRecord([REDACTED])" }

object PersonalHealthCodec {
    fun validate(r: PersonalHealthRecord): Boolean = r.name.length <= 80 &&
        r.conditions.length <= 1500 && r.allergies.length <= 1000 && r.medicationHistory.length <= 3000
    fun encode(r: PersonalHealthRecord): String {
        require(validate(r))
        return JSONObject().put("version",1).put("name",r.name).put("conditions",r.conditions)
            .put("allergies",r.allergies).put("medicationHistory",r.medicationHistory).toString()
    }
    fun decode(raw: String): PersonalHealthRecord {
        require(raw.length <= 35000)
        val o=JSONObject(raw);require(o.getInt("version")==1)
        return PersonalHealthRecord(o.getString("name"),o.getString("conditions"),o.getString("allergies"),o.getString("medicationHistory"))
            .also { require(validate(it)) }
    }
}

/** AES-GCM key belongs to Android Keystore; no plaintext preference or network copy. */
class PersonalHealthStore(context: Context) {
    private val prefs=context.getSharedPreferences("lifelink_personal_health_v1",Context.MODE_PRIVATE)
    fun load(): PersonalHealthRecord? = prefs.getString("record_cipher",null)?.let { PersonalHealthCodec.decode(MedicalCrypto.decrypt(it)) }
    fun save(record: PersonalHealthRecord) {
        val cipher=MedicalCrypto.encrypt(PersonalHealthCodec.encode(record))
        check(prefs.edit().putString("record_cipher",cipher).commit())
    }
    fun erase() { check(prefs.edit().remove("record_cipher").commit()) }
}
