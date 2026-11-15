package com.lifelink119.app.core.emergency

/** Live foreground observations do not inherit a separate family-record PIN timeout. */
class EmergencyObservationPrivacy {
    var locked = false
        private set

    private var authenticationBound = false

    fun leave(hasPrivateObservations: Boolean) {
        locked = hasPrivateObservations
        if (locked) authenticationBound = true
    }

    fun restore() {
        authenticationBound = true
        locked = false
    }

    fun unlock(authenticated: Boolean) {
        check(authenticated)
        locked = false
    }

    fun expireAuthentication() {
        if (authenticationBound) locked = true
    }

    fun clear() {
        locked = false
        authenticationBound = false
    }
}
