package com.lifelink119.app.core

data class CooperationCase(val id: String,val title: String,val baselineSeconds: Int,val activationSeconds: Int,val requiredCapability: String,val available: Boolean)
data class CooperationVehicle(val id: String,val name: String,val travelSeconds: Int,val capabilities: Set<String>,val optedIn: Boolean,val available: Boolean)
data class CooperationCatalog(val cases: List<CooperationCase> = emptyList(),val vehicles: List<CooperationVehicle> = emptyList())

/** Local role simulation using the same fixtures, IDs and display policy as the existing PC experiment. */
class OfflineCooperationEngine(private val catalog: CooperationCatalog,private val clock: ()->Long,
    private val allowed: ()->Boolean = {true},private val previewAllowed: ()->Boolean = {true}) {
    var snapshot: CooperationSnapshot?=null; private set
    var events: List<String> = emptyList(); private set
    private var owner: String?=null
    private var case: CooperationCase?=null
    private var missionDeadline=0L
    private var beaconDeadline=0L
    private var born=0L
    private var endedAt: Long?=null
    private var foreground=true
    private var sequence=0
    private val declined=mutableSetOf<String>()
    var onChange: ()->Unit = {}
    val cases get()=catalog.cases
    val vehicles get()=catalog.vehicles
    val elapsedSeconds get()=if(born==0L)0 else (((endedAt ?: clock())-born).coerceAtLeast(0)/1000).toInt()
    fun attach(sessionId: String?){clear();owner=sessionId}
    fun clear(){snapshot=null;case=null;missionDeadline=0;beaconDeadline=0;born=0;endedAt=null;declined.clear();events=emptyList()}
    private fun change(event: String,next: CooperationSnapshot){if(next.state in CooperationPolicy.terminal && endedAt==null)endedAt=clock();snapshot=next;events=(events+event).takeLast(40);onChange()}
    private fun current(): CooperationSnapshot {tick();check(allowed()){ "먼저 가상 위치 공유에 동의하고 연결 시연을 시작하세요." };return requireNotNull(snapshot)}
    fun eligible(id: String): Boolean {val c=case ?: return false;val v=vehicles.find{it.id==id} ?: return false
        return c.available && v.optedIn && v.available && c.requiredCapability in v.capabilities && id !in declined}
    fun request(caseId: String,consent: Boolean){check(allowed());require(consent);val session=requireNotNull(owner);require(session.matches(Regex("DEMO-[0-9]{4,}")))
        check(snapshot==null || snapshot!!.state in CooperationPolicy.terminal)
        case=catalog.cases.first{it.id==caseId};val c=case!!;born=clock();endedAt=null;declined.clear();missionDeadline=0;beaconDeadline=0;events=emptyList()
        sequence++;check(sequence<=9999)
        change("REQUEST_CONSENT",CooperationSnapshot("POC-"+session.removePrefix("DEMO-").takeLast(12).padStart(12,'0')+sequence.toString().padStart(4,'0'),"REQUESTED",c.title,
            syntheticOnly=true,simulationOnly=true,hardwareConnected=false,baselineSeconds=c.baselineSeconds,
            candidates=vehicles.joinToString("\n"){v->"${v.name} · ${if(eligible(v.id))"참여 조건 충족" else "참여 조건 불충족"}"}))
    }
    fun review(){val s=current();check(s.state=="REQUESTED");change("OPERATOR_REVIEW",s.copy(state="REVIEWED"))}
    fun invite(vehicleId: String){val s=current();check(s.state=="REVIEWED");check(eligible(vehicleId)){"동의·교육·가용 조건에 맞는 가상 차량만 초대할 수 있습니다."}
        change("VOLUNTEER_INVITED",s.copy(state="INVITED",vehicleId=vehicleId,vehicleEligible=true))}
    fun respond(accept: Boolean){val s=current();check(s.state=="INVITED");if(!accept)declined.add(requireNotNull(s.vehicleId))
        change(if(accept)"VOLUNTEER_ACCEPTED" else "VOLUNTEER_DECLINED",s.copy(state=if(accept)"ACCEPTED" else "REVIEWED",vehicleId=if(accept)s.vehicleId else null,vehicleEligible=accept))}
    fun authorize(){val s=current();check(s.state=="ACCEPTED" && eligible(requireNotNull(s.vehicleId)));val generation=s.generation+1
        val support=case!!.activationSeconds+vehicles.first{it.id==s.vehicleId}.travelSeconds;missionDeadline=clock()+300_000
        change("MOCK_MISSION_GRANTED",s.copy(state="AUTHORIZED",generation=generation,missionId="SIM-MISSION-${s.id}-$generation",missionRemaining=300,
            supportSeconds=support,headStartSeconds=maxOf(0,s.baselineSeconds-support),beaconState="OFF",beaconEnabled=false))}
    fun requestBeacon(authority: String){val s=current();check(s.state in setOf("AUTHORIZED","ON_SCENE"));require(authority in setOf("119","112"));check(s.beaconState !in setOf("REQUESTED","REVIEWING","APPROVED","ACTIVE"))
        change("BEACON_REQUESTED",s.copy(beaconState="REQUESTED",beaconAuthority=authority,beaconApprovalId=null,beaconEnabled=false,beaconRemaining=0))}
    fun reviewBeacon(authority: String){val s=current();check(s.beaconState=="REQUESTED" && s.beaconAuthority==authority);change("BEACON_EMERGENCY_REVIEWED",s.copy(beaconState="REVIEWING"))}
    fun decideBeacon(authority: String,approved: Boolean){val s=current();check(s.beaconState=="REVIEWING" && s.beaconAuthority==authority)
        val seq=s.beaconSequence+1;beaconDeadline=if(approved)minOf(missionDeadline,clock()+120_000) else 0
        change(if(approved)"BEACON_ROLE_APPROVED" else "BEACON_ROLE_REJECTED",s.copy(beaconState=if(approved)"APPROVED" else "REJECTED",beaconSequence=seq,
            beaconApprovalId=if(approved)"SIM-COOP-LIGHT-${s.id}-${s.generation}-${s.vehicleId}-$seq" else null,beaconRemaining=if(approved)minOf(120,s.missionRemaining) else 0,beaconEnabled=false))}
    fun activateBeacon(){val s=current();check(foreground && previewAllowed());check(s.beaconState=="APPROVED");check(s.beaconRemaining>0)
        change("LOCAL_PREVIEW_STARTED",s.copy(beaconState="ACTIVE",beaconEnabled=true))}
    fun pausePreview(){val s=snapshot ?: return;if(s.beaconEnabled)change("LOCAL_PREVIEW_HIDDEN",s.copy(beaconState="APPROVED",beaconEnabled=false))}
    fun foreground(value: Boolean){foreground=value;if(!value)pausePreview()}
    fun revokeBeacon(){val s=current();beaconDeadline=0;change("BEACON_REVOKED",s.copy(beaconState="REVOKED",beaconRemaining=0,beaconEnabled=false,beaconApprovalId=null))}
    fun arrive(){val s=current();check(s.state=="AUTHORIZED");change("VOLUNTEER_ON_SCENE",s.copy(state="ON_SCENE"))}
    fun finish(state: String){val s=current();require(state in CooperationPolicy.terminal);if(state=="HANDED_OVER")check(s.state=="ON_SCENE")
        if(s.state in CooperationPolicy.terminal){check(s.state==state);return}
        missionDeadline=0;beaconDeadline=0;change(state,s.copy(state=state,missionRemaining=0,beaconState="REVOKED",beaconRemaining=0,beaconEnabled=false,beaconApprovalId=null))}
    fun invalidate(){val s=snapshot ?: return;missionDeadline=0;beaconDeadline=0;change("CONTEXT_REVOKED",s.copy(state="REVOKED",missionRemaining=0,beaconState="REVOKED",beaconRemaining=0,beaconEnabled=false,beaconApprovalId=null))}
    fun tick(){val s=snapshot ?: return;if(!allowed() && s.state !in CooperationPolicy.terminal){invalidate();return};val now=clock();val mission=if(missionDeadline>0)((missionDeadline-now+999)/1000).coerceAtLeast(0).toInt() else 0
        val beacon=if(beaconDeadline>0)((beaconDeadline-now+999)/1000).coerceAtLeast(0).toInt() else 0
        if(missionDeadline>0 && mission==0){missionDeadline=0;beaconDeadline=0;change("MISSION_EXPIRED",s.copy(state="EXPIRED",missionRemaining=0,beaconState="EXPIRED",beaconRemaining=0,beaconEnabled=false,beaconApprovalId=null));return}
        if(beaconDeadline>0 && beacon==0){beaconDeadline=0;change("BEACON_EXPIRED",s.copy(missionRemaining=mission,beaconState="EXPIRED",beaconRemaining=0,beaconEnabled=false,beaconApprovalId=null));return}
        if(s.missionRemaining!=mission || s.beaconRemaining!=beacon){snapshot=s.copy(missionRemaining=mission,beaconRemaining=beacon);onChange()}
        if(!previewAllowed())pausePreview()
    }
    /** Render predicates are pure; the explicit tick advances expiry without reentrant UI rebuilds. */
    fun canDisplay(): Boolean {val now=clock();return allowed() && previewAllowed() && CooperationPolicy.canDisplay(snapshot,now,now.coerceAtLeast(1),beaconDeadline,foreground,true,true)}
}
