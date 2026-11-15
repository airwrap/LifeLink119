package com.lifelink119.app.vehicle

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lifelink119.app.core.*

/** User-started foreground polling. SDK failures never select example data. */
class VehicleContextController(
    private val gateway: PleosVehicleGateway,
    private val changed: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    var snapshot = VehicleContextSnapshot()
        private set

    var busy = false
        private set

    var monitoring = false
        private set

    private var visible = false
    private var closed = false
    private var generation = 0L
    private val poll = Runnable { if (visible && monitoring) refresh() }

    fun visible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (!value) {
            monitoring = false
            handler.removeCallbacks(poll)
            generation++
            busy = false
        }
    }

    fun monitor(value: Boolean) {
        monitoring = value && visible
        if (monitoring) refresh() else handler.removeCallbacks(poll)
    }

    fun refresh() {
        if (closed || !visible || busy) return
        handler.removeCallbacks(poll)
        val token = ++generation
        busy = true
        snapshot = VehicleContextSnapshot()
        changed()
        val metrics = VehicleMetric.entries
        fun next(index: Int) {
            if (closed || !visible || token != generation) return
            if (index == metrics.size) {
                busy = false
                changed()
                if (monitoring) handler.postDelayed(poll, 5_000)
                return
            }
            val metric = metrics[index]
            gateway.readObservation(metric) { result ->
                if (closed || !visible || token != generation) return@readObservation
                snapshot =
                    snapshot.copy(
                        samples =
                            snapshot.samples +
                                (metric to
                                    VehicleContextPolicy.observation(
                                        metric,
                                        result,
                                        SystemClock.elapsedRealtime(),
                                    ))
                    )
                changed()
                next(index + 1)
            }
        }
        next(0)
    }

    fun example(kind: String) {
        if (closed || !visible) return
        handler.removeCallbacks(poll)
        generation++
        busy = false
        monitoring = false
        snapshot = VehicleContextPolicy.example(kind, SystemClock.elapsedRealtime())
        changed()
    }

    fun background() {
        visible(false)
        snapshot = VehicleContextSnapshot()
    }

    fun close() {
        closed = true
        generation++
        monitoring = false
        busy = false
        handler.removeCallbacksAndMessages(null)
        snapshot = VehicleContextSnapshot()
    }
}
