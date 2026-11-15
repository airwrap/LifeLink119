package com.lifelink119.app.vehicle

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lifelink119.app.core.emergency.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only current address. No destination, route history, search, steering or ETA request. */
class PleosEmergencyNavigationProvider(context: Context) : NavigationProvider {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker =
        Executors.newSingleThreadExecutor {
            Thread(it, "lifelink-navi-read").apply { isDaemon = true }
        }
    private var sdk: NaviHelper? = null
    private var generation = 0L
    private var closed = false
    private val workerBusy = AtomicBoolean(false)

    override fun read(done: (Evidence<String>) -> Unit) {
        if (closed) return
        val token = ++generation
        var finished = false
        var listener: NaviHelperEventListener? = null
        lateinit var timeout: Runnable
        fun finish(value: Evidence<String>) {
            main.post {
                if (closed || token != generation || finished) return@post
                finished = true
                main.removeCallbacks(timeout)
                listener?.let { runCatching { sdk?.removeListener(it) } }
                done(value)
            }
        }
        timeout = Runnable { finish(Evidence(source = "NaviHelper · 주소 응답 없음")) }
        main.postDelayed(timeout, 5_000)
        if (app.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            finish(Evidence(source = "NaviHelper · 조회 권한 없음"))
            return
        }
        if (!workerBusy.compareAndSet(false, true)) {
            finish(Evidence(source = "NaviHelper · 이전 SDK 작업 지연"))
            return
        }
        worker.execute {
            try {
                val client = sdk ?: NaviHelper(app).also { sdk = it }
                listener =
                    object : NaviHelperEventListener {
                        override fun onCurrentLocationInfo(
                            currentLocationInfo: CurrentLocationInfo
                        ) {
                            val address =
                                currentLocationInfo.address.takeIf {
                                    it.isNotBlank() &&
                                        it.length <= 180 &&
                                        it.none(Char::isISOControl)
                                }
                            // SDK 3.0.5 CurrentLocationInfo contains no measurement timestamp.
                            finish(
                                Evidence(
                                    address,
                                    if (address == null) EvidenceStatus.UNKNOWN else sdkOrigin(),
                                    "NaviHelper 3.0.5 · 측정 시각 미제공",
                                    null,
                                    SystemClock.elapsedRealtime(),
                                )
                            )
                        }
                    }
                client.addListener(requireNotNull(listener))
                client.initialize(
                    onConnected = {
                        runCatching {
                                if (!closed && token == generation && !finished)
                                    worker.execute {
                                        workerBusy.set(true)
                                        try {
                                            if (!closed && token == generation && !finished) {
                                                client.getCurrentLocationInfo()
                                            }
                                        } catch (_: Exception) {
                                            finish(Evidence(source = "NaviHelper · 주소 조회 실패"))
                                        } finally {
                                            workerBusy.set(false)
                                        }
                                    }
                            }
                            .onFailure { finish(Evidence(source = "NaviHelper · 주소 조회 실패")) }
                    },
                    onFailed = {
                        finish(
                            Evidence(
                                status = EvidenceStatus.UNSUPPORTED,
                                source = "NaviHelper · 서비스 연결 불가",
                            )
                        )
                    },
                )
            } catch (_: Exception) {
                finish(Evidence(source = "NaviHelper · 서비스/권한 오류"))
            } catch (_: LinkageError) {
                finish(
                    Evidence(status = EvidenceStatus.UNSUPPORTED, source = "NaviHelper · 런타임 미지원")
                )
            } finally {
                workerBusy.set(false)
            }
        }
    }

    override fun close() {
        closed = true
        generation++
        main.removeCallbacksAndMessages(null)
        worker.execute {
            runCatching { sdk?.release() }
            sdk = null
        }
        worker.shutdown()
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.NAVI_ROUTE"
    }
}
