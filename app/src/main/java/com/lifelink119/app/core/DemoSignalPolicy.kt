package com.lifelink119.app.core

/** An unauthenticated localhost ROLE simulation, never a vehicle lamp authorization. */
data class DemoSignalSnapshot(
    val state: String = "OFF",
    val authority: String? = null,
    val approvalId: String? = null,
    val remainingSeconds: Int = 0,
    val previewEnabled: Boolean = false,
    val simulationOnly: Boolean = true,
    val hardwareConnected: Boolean = false,
)

object DemoSignalPolicy {
    fun canDisplay(
        signal: DemoSignalSnapshot,
        sessionId: String?,
        now: Long,
        lastSuccess: Long,
        deadline: Long,
        foreground: Boolean,
        connected: Boolean,
    ): Boolean =
        foreground &&
            connected &&
            lastSuccess > 0 &&
            now >= lastSuccess &&
            now - lastSuccess < 8_000 &&
            now < deadline &&
            signal.simulationOnly &&
            !signal.hardwareConnected &&
            signal.state == "PREVIEW_ACTIVE" &&
            signal.previewEnabled &&
            signal.remainingSeconds in 1..120 &&
            signal.authority in setOf("119", "112") &&
            sessionId != null &&
            signal.approvalId?.startsWith("SIM-LIGHT-$sessionId-") == true
}
