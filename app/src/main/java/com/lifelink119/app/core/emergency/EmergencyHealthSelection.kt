package com.lifelink119.app.core.emergency

/** Incident-bound, RAM-only authorization. No patient details, default subject or LLM payload. */
class EmergencyHealthSelection {
    private var incident: String? = null
    private var selected: String? = null

    fun select(incidentId: String, profileId: String, authenticated: Boolean, restricted: Boolean) {
        check(authenticated && !restricted)
        require(incidentId.isNotBlank())
        require(profileId.matches(Regex("SELF|LOCAL-[a-f0-9-]{36}")))
        incident = incidentId
        selected = profileId
    }

    fun profileId(incidentId: String?, authenticated: Boolean, restricted: Boolean): String? =
        selected?.takeIf { incident == incidentId && authenticated && !restricted }

    fun clear() {
        incident = null
        selected = null
    }

    override fun toString() = "EmergencyHealthSelection(REDACTED)"
}
