package com.lifelink119.app.core.emergency

/** One question at a time. A negative/unknown answer never implies reception or dispatch. */
class EmergencyCallCheck {
    enum class Step {
        CONNECTED,
        CALLBACK,
        DISPATCH,
        DONE,
    }

    var step = Step.CONNECTED
        private set

    val question: String
        get() =
            when (step) {
                Step.CONNECTED -> "지금 119 상담원과 통화 중인가요?"
                Step.CALLBACK -> "119에서 다시 전화할 때까지 기다리라는 안내를 받았나요?"
                Step.DISPATCH -> "구급차 출동을 기다리라는 안내를 받았나요?"
                Step.DONE -> "확인한 내용만 반영했습니다."
            }

    fun reset() {
        step = Step.CONNECTED
    }

    fun reply(answer: Boolean?): EmergencyCallStatus? {
        if (step == Step.DONE) return null
        val before = step
        step =
            if (answer == null || answer) Step.DONE
            else
                when (before) {
                    Step.CONNECTED -> Step.CALLBACK
                    Step.CALLBACK -> Step.DISPATCH
                    else -> Step.DONE
                }
        if (answer != true) return null
        return when (before) {
            Step.CONNECTED -> EmergencyCallStatus.OPERATOR
            Step.CALLBACK -> EmergencyCallStatus.CALLBACK
            Step.DISPATCH -> EmergencyCallStatus.DISPATCH_WAIT
            Step.DONE -> null
        }
    }
}

object EmergencyCallSpeech {
    /** Exact user statements only. Never infer a connection state from a symptom or silence. */
    fun status(text: String): EmergencyCallStatus? =
        when (text.replace(Regex("[\\s.,!?]"), "")) {
            "통화중이에요",
            "상담원과통화중이에요",
            "119와통화중이에요" -> EmergencyCallStatus.OPERATOR
            "콜봇과통화중이에요" -> EmergencyCallStatus.CALLBOT
            "연결이안돼요",
            "전화가안돼요" -> EmergencyCallStatus.DELAYED
            "콜백기다리래요",
            "다시전화준대요" -> EmergencyCallStatus.CALLBACK
            "출동기다리래요",
            "구급차출동을기다리래요" -> EmergencyCallStatus.DISPATCH_WAIT
            "접수됐대요" -> EmergencyCallStatus.REPORTED
            "구조대왔어요",
            "구급대가왔어요" -> EmergencyCallStatus.ARRIVED
            else -> null
        }
}
