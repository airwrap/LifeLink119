package com.lifelink119.app.core

import org.junit.Assert.*
import org.junit.Test

class HealthLinkExampleTest {
    @Test
    fun explicitConsentIsRequiredAndCanBeRevoked() {
        val demo = HealthLinkExample()
        assertFalse(demo.canShowExample())
        assertThrows(IllegalArgumentException::class.java) { demo.agree(false) }
        assertFalse(demo.canShowExample())
        demo.agree(true)
        assertTrue(demo.canShowExample())
        demo.revoke()
        assertFalse(demo.canShowExample())
    }
}
