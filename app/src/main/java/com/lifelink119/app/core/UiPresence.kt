package com.lifelink119.app.core

/** Activity lifecycle and in-Activity navigation are independent. */
class UiPresence {
    var foreground = true
    var visible = true
    val active
        get() = foreground && visible
}
