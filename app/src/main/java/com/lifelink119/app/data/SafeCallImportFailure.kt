package com.lifelink119.app.data

/**
 * Only fixed codes/field names cross the error boundary. Never show parser exceptions or values.
 */
class SafeCallImportFailure(code: String, field: String? = null) :
    IllegalArgumentException("SafeCall import rejected") {
    val code = code.takeIf { it in messages } ?: "READ"
    val field = field?.takeIf { it in SafeCallWebImport.labels }

    fun userMessage(): String {
        val label = field?.let { SafeCallWebImport.labels.getValue(it) }
        return messages.getValue(code) +
            (label?.let { "\n확인할 항목: $it" } ?: "") +
            "\n기존 저장 정보는 유지됩니다. 확인 코드: $code" +
            (field?.let { "/$it" } ?: "")
    }

    companion object {
        private val messages =
            mapOf(
                "ORIGIN" to "안심콜 공식 사이트의 본인 병력정보 화면에서 다시 시도해 주세요.",
                "FORM_NOT_READY" to "본인 인증을 마치고 병력정보 화면이 완전히 열린 후 다시 시도해 주세요.",
                "DIALOG_OPEN" to "웹사이트 안의 인증·알림 창을 먼저 닫고 다시 시도해 주세요.",
                "IDENTITY_NOT_READY" to "본인 이름과 등록 휴대폰 번호가 가려지지 않은 정보 확인 화면을 열어주세요.",
                "UNSUPPORTED_FORM" to "웹사이트의 항목 구조를 읽지 못했습니다. 해당 항목을 확인해 주세요.",
                "TOO_LARGE" to "가져올 정보가 처리 한도를 초과했습니다.",
                "VALIDATION" to "가져온 항목의 형식을 확인하지 못해 저장하지 않았습니다.",
                "ENCRYPTION" to "가져온 정보를 안전하게 암호화하지 못해 저장하지 않았습니다.",
                "READ" to "현재 웹 화면에서 정보를 읽지 못했습니다. 화면이 완전히 열린 후 다시 시도해 주세요.",
            )
    }
}
