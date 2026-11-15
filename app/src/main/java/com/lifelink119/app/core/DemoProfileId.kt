package com.lifelink119.app.core

object DemoProfileId {
    private val pattern = Regex("DEMO-P-[1-9][0-9]{0,5}")

    fun valid(value: String) = pattern.matches(value)
}
