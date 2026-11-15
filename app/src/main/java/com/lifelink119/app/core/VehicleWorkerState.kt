package com.lifelink119.app.core

/** A timed-out blocking SDK call is quarantined until it actually returns. */
class VehicleWorkerState {
    enum class State {
        IDLE,
        BUSY,
        STALLED,
    }

    @Volatile
    var state = State.IDLE
        private set

    @Synchronized
    fun begin(): Boolean {
        if (state != State.IDLE) return false
        state = State.BUSY
        return true
    }

    @Synchronized
    fun timeout() {
        if (state == State.BUSY) state = State.STALLED
    }

    @Synchronized
    fun complete() {
        state = State.IDLE
    }
}
