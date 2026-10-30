package com.lifelink119.app.core

/** All choices start off. They select synthetic fixtures, never device data. */
data class DemoShareSelection(
    val vehicle: Boolean = false,
    val location: Boolean = false,
    val video: Boolean = false,
)
