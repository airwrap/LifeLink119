package com.lifelink119.app.core

data class CooperationSnapshot(
    val id: String, val state: String, val title: String,
    val vehicleId: String? = null, val vehicleEligible: Boolean = false,
    val generation: Int = 0, val missionId: String? = null, val missionRemaining: Int = 0,
    val beaconState: String = "OFF", val beaconAuthority: String? = null,
    val beaconApprovalId: String? = null, val beaconSequence: Int = 0,
    val beaconRemaining: Int = 0, val beaconEnabled: Boolean = false,
    val syntheticOnly: Boolean = false, val simulationOnly: Boolean = false, val hardwareConnected: Boolean = true,
    val baselineSeconds: Int = 0, val supportSeconds: Int? = null, val headStartSeconds: Int = 0,
    val candidates: String = "", val events: String = "",
)

object CooperationPolicy {
    val terminal = setOf("HANDED_OVER","CANCELLED","REVOKED","WITHDRAWN","EXPIRED")
    fun canDisplay(s: CooperationSnapshot?, now: Long, freshAt: Long, deadline: Long,
                   foreground: Boolean, connected: Boolean, volunteerRole: Boolean): Boolean {
        if (s == null || !foreground || !connected || !volunteerRole) return false
        if (freshAt <= 0 || now < freshAt || now-freshAt >= 8000 || now >= deadline) return false
        if (!s.id.matches(Regex("POC-[0-9]{16}")) || s.vehicleId !in setOf("DEMO-HELPER-A","DEMO-HELPER-D") || !s.vehicleEligible) return false
        if (s.state !in setOf("AUTHORIZED","ON_SCENE") || s.missionRemaining !in 1..300 || s.generation <= 0) return false
        if (s.missionId != "SIM-MISSION-${s.id}-${s.generation}") return false
        return s.syntheticOnly && s.simulationOnly && !s.hardwareConnected && s.beaconState=="ACTIVE" && s.beaconEnabled &&
            s.beaconAuthority in setOf("119","112") && s.beaconRemaining in 1..120 && s.beaconSequence > 0 &&
            s.beaconApprovalId == "SIM-COOP-LIGHT-${s.id}-${s.generation}-${s.vehicleId}-${s.beaconSequence}"
    }
}
