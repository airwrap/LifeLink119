package com.lifelink119.app.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import com.lifelink119.app.core.CapabilityStatus
import com.lifelink119.app.core.DrivingRestrictions
import com.lifelink119.app.BuildConfig

/** Official AAOS read-only restrictions. No DO declaration, speed inference or vehicle writes. */
class DrivingRestrictionsGateway(context: Context, private val changed: (DrivingRestrictions)->Unit) {
    private var car: Car?=null
    private var manager: CarUxRestrictionsManager?=null
    private var closed=false
    private var lastFlags: Int?=null
    private var lastOptimization: Boolean?=null
    private val listener=CarUxRestrictionsManager.OnUxRestrictionsChangedListener { accept(it) }
    init {
        try {
            car=Car.createCar(context,null,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,Car.CarServiceLifecycleListener { connected,ready ->
                if(closed)return@CarServiceLifecycleListener
                if(!ready){changed(DrivingRestrictions(detail="차량 제한 서비스 연결 없음 · 상세 기능 제한"));return@CarServiceLifecycleListener}
                try {
                    manager=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    manager?.registerListener(listener)
                    manager?.currentCarUxRestrictions?.let(::accept) ?: changed(DrivingRestrictions(detail="차량 제한 값 없음 · 상세 기능 제한"))
                } catch(_: Exception) { changed(DrivingRestrictions(detail="차량 제한 조회 불가 · 상세 기능 제한")) }
            })
        } catch(_: Exception) { changed(DrivingRestrictions(detail="차량 제한 초기화 불가 · 상세 기능 제한")) }
    }
    private fun accept(value: CarUxRestrictions) {
        if(closed)return
        val f=value.activeRestrictions
        if(BuildConfig.DEBUG && (lastFlags!=f || lastOptimization!=value.isRequiresDistractionOptimization))
            android.util.Log.i("LifeLinkCarUx","ActiveRestrictions=$f requiresOptimization=${value.isRequiresDistractionOptimization}")
        lastFlags=f;lastOptimization=value.isRequiresDistractionOptimization
        changed(DrivingRestrictions(CapabilityStatus.REAL,true,value.isRequiresDistractionOptimization,f,
            f and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO!=0,
            f and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD!=0,
            f and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP!=0,
            f and CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE!=0,
            f and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT!=0,
            "플랫폼 제한 수신 · ActiveRestrictions=$f"))
    }
    fun release() { closed=true;runCatching { manager?.unregisterListener() };runCatching { car?.disconnect() };manager=null;car=null }
}
