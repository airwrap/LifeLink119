package com.lifelink119.app.core

import org.junit.Assert.*
import org.junit.Test

class OfficialBrowserPolicyTest {
    @Test
    fun officialEntryAndSameOriginLoginRemainAvailable() {
        OfficialHealthSite.entries.forEach {
            assertTrue(it.url, OfficialBrowserPolicy.allows(it.url))
        }
        assertTrue(
            OfficialBrowserPolicy.allows("https://u119.nfa.go.kr/main/loginForm?menuCd=login")
        )
        assertTrue(
            OfficialBrowserPolicy.allows("HTTPS://U119.NFA.GO.KR:443/cm/main/index.xml#login")
        )
        assertEquals(
            "u119.nfa.go.kr",
            OfficialBrowserPolicy.allowedHost("https://u119.nfa.go.kr/?token=not-displayed"),
        )
    }

    @Test
    fun deceptiveHostsAndAuthoritiesCannotEscapeTheOfficialSites() {
        listOf(
                "https://u119.nfa.go.kr.evil.example/",
                "https://evil-u119.nfa.go.kr/",
                "https://u119.nfa.go.kr@evil.example/",
                "https://evil.example@u119.nfa.go.kr/",
                "https://u119.nfa.go.kr:8443/",
                "https://u119.nfa.go.kr./",
                "https://u119.nfa.go.kr\\@evil.example/",
                "https://u119.nfa.go.kr%2fevil.example/",
                "https://u119.nfa.go.kr%00.evil.example/",
                "https://127.0.0.1/",
                "https://localhost/",
                "https://[::1]/",
                "https://u119.nfa.go.kr/\n",
                " https://u119.nfa.go.kr/",
                "//u119.nfa.go.kr/",
                "https:////u119.nfa.go.kr/",
                "not a URL",
            )
            .forEach { assertFalse(it, OfficialBrowserPolicy.allows(it)) }
    }

    @Test
    fun webLinksCannotCallEmergencyNumbersOpenFilesOrLaunchApps() {
        listOf(
                "tel:119",
                "tel:112",
                "tel:109",
                "sms:119",
                "intent://u119.nfa.go.kr/#Intent;scheme=https;end",
                "javascript:alert(1)",
                "file:///data/data/com.lifelink119.app/shared_prefs/x.xml",
                "content://com.lifelink119.app/",
                "data:text/html,<script>alert(1)</script>",
                "http://u119.nfa.go.kr/",
                "http://127.0.0.1:8781/",
                "about:blank",
            )
            .forEach { assertFalse(it, OfficialBrowserPolicy.allows(it)) }
    }

    @Test
    fun unknownOrRestrictedVehicleStateBlocksAllBrowserEntry() {
        assertFalse(OfficialBrowserPolicy.canBrowse(DrivingRestrictions()))
        val parked = DrivingRestrictions.unrestrictedFixture()
        assertTrue(OfficialBrowserPolicy.canBrowse(parked))
        listOf(
                parked.copy(known = false),
                parked.copy(requiresOptimization = true),
                parked.copy(noKeyboard = true),
                parked.copy(noSetup = true),
                parked.copy(noVideo = true),
                parked.copy(noMessages = true),
                parked.copy(limitContent = true),
                parked.withTestRestriction(true),
            )
            .forEach { assertFalse(OfficialBrowserPolicy.canBrowse(it)) }
    }
}
