package com.lifelink119.app.data

/** Activity memory only; never serialized, logged or used by any dispatch gateway. */
class HealthEditorDraft {
    private var value: PersonalHealthRecord? = null
    var validationMessage: String = ""

    fun begin(saved: PersonalHealthRecord): PersonalHealthRecord {
        if (value?.id != saved.id) {
            value = saved
            validationMessage = ""
        }
        return requireNotNull(value)
    }

    fun update(record: PersonalHealthRecord) {
        value = record
        validationMessage = ""
    }

    fun clear() {
        value = null
        validationMessage = ""
    }

    override fun toString() = "HealthEditorDraft([REDACTED])"
}
