package com.lifelink119.app.core
import org.junit.Assert.*
import org.junit.Test

class CooperationPolicyTest {
    private val run="POC-0000000024680001"
    private fun snapshot()=CooperationSnapshot(run,"AUTHORIZED","가상 상황","DEMO-HELPER-A",true,1,"SIM-MISSION-$run-1",300,
        "ACTIVE","119","SIM-COOP-LIGHT-$run-1-DEMO-HELPER-A-1",1,120,true,true,true,false)
    private fun visible(s:CooperationSnapshot?=snapshot(),now:Long=2000,fresh:Long=1000,deadline:Long=121000,foreground:Boolean=true,connected:Boolean=true,volunteer:Boolean=true)=
        CooperationPolicy.canDisplay(s,now,fresh,deadline,foreground,connected,volunteer)
    @Test fun valid_role_and_approval_can_display(){assertTrue(visible());assertTrue(visible(snapshot().copy(beaconAuthority="112")))}
    @Test fun requestor_or_unfocused_or_disconnected_never_displays(){assertFalse(visible(volunteer=false));assertFalse(visible(foreground=false));assertFalse(visible(connected=false))}
    @Test fun old_or_future_or_expired_data_is_off(){assertFalse(visible(now=9000));assertFalse(visible(now=999));assertFalse(visible(deadline=2000));assertFalse(visible(fresh=0))}
    @Test fun actual_hardware_or_non_demo_data_is_off(){assertFalse(visible(snapshot().copy(hardwareConnected=true)));assertFalse(visible(snapshot().copy(syntheticOnly=false)));assertFalse(visible(snapshot().copy(simulationOnly=false)))}
    @Test fun wrong_run_vehicle_or_approval_is_off(){assertFalse(visible(snapshot().copy(id="POC-9999999999999999")));assertFalse(visible(snapshot().copy(vehicleId="DEMO-HELPER-B")));assertFalse(visible(snapshot().copy(vehicleEligible=false)));assertFalse(visible(snapshot().copy(beaconApprovalId="SIM-COOP-LIGHT-stale")))}
    @Test fun terminal_mission_or_no_beacon_lease_is_off(){for(state in CooperationPolicy.terminal)assertFalse(visible(snapshot().copy(state=state)));assertFalse(visible(snapshot().copy(missionRemaining=0)));assertFalse(visible(snapshot().copy(beaconRemaining=0)));assertFalse(visible(snapshot().copy(beaconState="APPROVED")));assertFalse(visible(snapshot().copy(beaconAuthority="109")))}
}
