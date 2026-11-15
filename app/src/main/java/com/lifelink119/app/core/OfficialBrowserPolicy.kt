package com.lifelink119.app.core

import java.net.URI
import java.util.Locale

/**
 * Public official sites only. No intent/tel/SMS, local files, arbitrary ports or login URL input.
 */
object OfficialBrowserPolicy {
    private val hosts =
        setOf(
            "u119.nfa.go.kr",
            "www.nfa.go.kr",
            "nfa.go.kr",
            "www.myhealthway.go.kr",
            "myhealthway.go.kr",
        )

    fun allowedHost(url: String): String? =
        runCatching {
                if (url.any { it <= ' ' || it == '\\' || it == '\u007f' }) return null
                val uri = URI(url)
                val host = uri.host?.lowercase(Locale.ROOT) ?: return null
                if (
                    !uri.scheme.equals("https", ignoreCase = true) ||
                        uri.rawUserInfo != null ||
                        uri.port !in setOf(-1, 443) ||
                        host !in hosts
                )
                    null
                else host
            }
            .getOrNull()

    fun allows(url: String) = allowedHost(url) != null

    /** Websites can contain text entry and video, so all relevant platform restrictions apply. */
    fun canBrowse(value: DrivingRestrictions) =
        !value.complexBlocked &&
            !value.videoBlocked &&
            !value.messagesBlocked &&
            !value.limitContent
}
