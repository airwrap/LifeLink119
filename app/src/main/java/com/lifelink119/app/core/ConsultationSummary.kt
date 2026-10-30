package com.lifelink119.app.core

/** Future providers must remain explicit opt-ins; the demo always uses this local template. */
interface ConsultationSummaryGateway {
    val source: CapabilityStatus
    fun summarize(input: ConsultationSummaryInput): String
}

data class ConsultationSummaryInput(val who: String,val discovered: String,val where: String,
    val observation: String,val desiredHelp: String,val demoGlucose: Int?)

object OfflineTemplateSummaryGateway : ConsultationSummaryGateway {
    override val source=CapabilityStatus.REAL
    override fun summarize(input: ConsultationSummaryInput): String = with(input) {
        "누가: $who\n언제: $discovered\n어디서: $where\n상황: $observation\n원하는 도움: $desiredHelp"+
            (demoGlucose?.let { "\n가상 혈당 입력: $it mg/dL · 해석·판정 없음" } ?: "")
    }
}
