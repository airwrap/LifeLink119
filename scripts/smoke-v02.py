"""End-to-end synthetic Android/localhost check on a dedicated Pleos AVD.

Requires the v0.2 debug APK, a running localhost server, and adb reverse.
Does not reset existing sessions or touch another device.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "verification"
PKG = "com.lifelink119.app"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--release-check", action="store_true")
    parser.add_argument("--slow-poll-check", action="store_true", help="Requires server --read-delay 1.2")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    adb_path = str(sdk / "platform-tools/adb.exe")
    command = [adb_path, "-s", args.serial]
    results = []

    def adb(*parts, timeout=35):
        return subprocess.check_output(command + list(parts), timeout=timeout).decode("utf-8", "replace").strip()

    def api(path, data=None):
        body = json.dumps(data).encode("utf-8") if data is not None else None
        request = urllib.request.Request("http://127.0.0.1:8781/api/demo" + path, data=body,
                                         headers={"Content-Type":"application/json"})
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.load(response)

    def nodes(tree):
        return list(tree.iter("node"))

    def node(tree, key):
        return next((n for n in nodes(tree) if n.get("resource-id") == PKG+":id/"+key), None)

    def text(tree):
        return "\n".join(n.get("text", "") for n in nodes(tree))

    def dump(banner=True):
        adb("shell", "uiautomator", "dump", "/data/local/tmp/lifelink-v02.xml")
        xml = adb("shell", "cat", "/data/local/tmp/lifelink-v02.xml")
        tree = ET.fromstring(xml)
        if banner:
            assert node(tree, "safety_banner") is not None, "App hidden or demo banner missing"
        return tree

    def scroll(tree, direction):
        view = next((n for n in nodes(tree) if n.get("class") == "android.widget.ScrollView" and n.get("package") == PKG), None)
        assert view is not None, "No app ScrollView"
        x1,y1,x2,y2 = map(int,re.findall(r"\d+",view.get("bounds")))
        top,bottom=y1+100,y2-100
        start,end=(bottom,top) if direction=="down" else (top,bottom)
        adb("shell","input","swipe",str((x1+x2)//2),str(start),str((x1+x2)//2),str(end),"300")

    def find(key):
        tree = dump()
        for direction in ("down","up"):
            previous = ""
            for _ in range(6):
                target=node(tree,key)
                if target is not None:
                    bounds=list(map(int,re.findall(r"\d+",target.get('bounds'))))
                    required_height=70 if target.get('class') in {'android.widget.Button','android.widget.CheckBox'} and key!='back_button' else 1
                    if bounds[3]-bounds[1]>=required_height:
                        return tree,target
                current=ET.tostring(tree)
                if current==previous:
                    break
                previous=current
                scroll(tree,direction)
                tree=dump()
        raise AssertionError("Missing control: "+key+" / "+text(tree)[-1000:])

    def tap_node(target):
        x1,y1,x2,y2=map(int,re.findall(r"\d+",target.get("bounds")))
        assert x2>x1 and y2>y1, "Control clipped"
        assert target.get("enabled")!="false", "Control disabled: "+target.get("resource-id", "")
        adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))

    def tap(key):
        tree,target=find(key)
        tap_node(target)
        return tree

    def wait_text(key, expected, timeout=22):
        start=time.monotonic()
        while time.monotonic()-start<timeout:
            tree,target=find(key)
            if expected in target.get("text", ""):
                return tree,target.get("text", "")
            time.sleep(.4)
        raise AssertionError("Expected "+expected+" in "+key)

    def capture(name):
        tree=dump()
        (OUT/(name+".xml")).write_bytes(ET.tostring(tree,encoding="utf-8",xml_declaration=True))
        adb("shell","screencap","-p","/data/local/tmp/lifelink-v02.png")
        adb("pull","/data/local/tmp/lifelink-v02.png",str(OUT/(name+".png")))

    assert args.serial.startswith("emulator-")
    assert "Pleos_Connect_API_34" in adb("emu","avd","name")
    assert api("/health")["version"]=="0.2.0"
    adb("reverse","tcp:8781","tcp:8781")
    adb("shell","am","force-stop",PKG)
    adb("shell","am","start","-W","-n",PKG+"/.MainActivity")
    try:
        locked=dump()
        assert node(locked,"confirm_parked") is not None
        assert node(locked,"scenario_medical") is None
        capture("v02-release-locked" if args.release_check else "v02-android-locked")
        tap("confirm_parked")
        capture("v02-release-home" if args.release_check else "v02-android-home")
        tap("simulate_driving")
        assert node(dump(),"scenario_medical") is None
        tap("confirm_parked")
        if args.release_check:
            before={s['id'] for s in api('/sessions')['items']}
            tap('scenario_medical');tap('next_step');tap('connect_demo')
            tree,target=find('share_console')
            assert target.get('enabled')=='false','Release allowed local sharing'
            assert '앱 내부 시연만 제공합니다' in text(tree)
            assert {s['id'] for s in api('/sessions')['items']}==before
            capture('v02-release-local-sharing-disabled')
            report={'status':'PASS','releaseLocalSharingDisabled':True,'serverSessionCountUnchanged':True}
            (OUT/'v02-release-runtime.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
            print(json.dumps(report),flush=True)
            return
        for index,scenario in enumerate(("MEDICAL","VIDEO","TRANSPORT","POLICE","WELLBEING"),1):
            tap("scenario_"+scenario.lower())
            if scenario=="VIDEO":
                tap("case_picker")
                popup=dump(False)
                choice=next(n for n in nodes(popup) if n.get("text")=="가상 상황 B · 정보 선택 확인 연습")
                tap_node(choice)
            tap("next_step")
            for key in ("share_vehicle","share_location"):
                _,target=find(key)
                assert target.get("checked")=="false", "Consent must start off"
            if scenario=="VIDEO":
                _,target=find("share_video");assert target.get("checked")=="false"
                for key in ("share_vehicle","share_location","share_video"):tap(key)
                capture("v02-android-review")
            elif scenario=="TRANSPORT":tap("share_vehicle")
            elif scenario=="POLICE":tap("share_location")
            before={s["id"] for s in api("/sessions")["items"]}
            tap("connect_demo")
            _,ticket=find("session_ticket")
            local_ticket=ticket.get("text")
            assert local_ticket==f"DEMO-{index:04d}"
            assert {s["id"] for s in api("/sessions")["items"]}==before, "Shared without explicit action"
            tap("share_console")
            _,message=wait_text("console_result","DEMO-")
            added=[s for s in api("/sessions")["items"] if s["id"] not in before]
            assert len(added)==1
            item=added[0];server_id=item["id"]
            assert item["scenario"]==scenario
            assert item["consent"]=={"vehicle":scenario in {"VIDEO","TRANSPORT"},"location":scenario in {"VIDEO","POLICE"},"video":scenario=="VIDEO"}
            if item['vehicle'] is not None:assert item['vehicle']['id']=='DEMO-CAR-01'
            if item['location'] is not None:assert item['location']['coordinateSystem']=='DEMO_GRID'
            if scenario=="VIDEO":
                assert item["caseCode"]=="VIDEO_SCENE_B"
                assert item["videoLink"].startswith("demo://video/")
                for action in ("ACKNOWLEDGE","REVIEW","GUIDANCE"):
                    api("/sessions/"+server_id+"/actions",{"syntheticOnly":True,"action":action})
                wait_text("console_result","모의 안내")
                capture("v02-android-console-guidance")
                tap("simulate_driving")
                assert node(dump(),"console_result") is None
                tap("confirm_parked")
                tap("view_route")
                find("route_preview")
                capture("v02-android-route")
                api("/sessions/"+server_id+"/revoke",{"syntheticOnly":True})
                wait_text("return_session","모의 상담으로 돌아가기")
                start=time.monotonic()
                while "위치 공유가 선택되지 않아" not in text(dump()):
                    assert time.monotonic()-start<20,"Route did not update on revoke"
                    time.sleep(.5)
                assert node(dump(),"route_preview") is None
                tap("return_session")
                _,message=wait_text("console_result","제거")
                assert not any(api("/sessions/"+server_id)["consent"].values())
            tap("close_session")
            _,message=wait_text("console_result","모의 종료")
            assert api("/sessions/"+server_id)["status"]=="DEMO_CLOSED"
            tap("home_button")
            results.append({"scenario":scenario,"localTicket":local_ticket,"consoleTicket":server_id,"consentDefaultsOff":True,"closed":True})
            print("PASS "+scenario+" "+server_id,flush=True)

        # Exercise actual connection failure and retry without creating duplicate sessions.
        tap("scenario_medical");tap("next_step");tap("connect_demo")
        before={s['id'] for s in api('/sessions')['items']}
        adb("reverse","--remove","tcp:8781")
        tap("share_console")
        wait_text("console_result","연결하지 못했습니다")
        capture("v02-android-offline")
        assert {s['id'] for s in api('/sessions')['items']}==before
        adb("reverse","tcp:8781","tcp:8781")
        tap("share_console");wait_text("console_result","DEMO-")
        added=[s for s in api('/sessions')['items'] if s['id'] not in before]
        assert len(added)==1
        tap("close_session");wait_text("console_result","모의 종료");tap("home_button")
        results.append({"offlineRetry":"PASS","duplicateSessions":0})

        if args.slow_poll_check:
            tap('scenario_medical');tap('next_step');tap('connect_demo');tap('share_console')
            wait_text('console_result','DEMO-')
            _,close_target=find('close_session')
            app_pid=adb('shell','pidof',PKG).strip()
            def poll_log():return adb('logcat','-d','--pid='+app_pid,'-s','LifeLinkConsole:D')
            before_count=poll_log().count('poll_start')
            start=time.monotonic()
            while poll_log().count('poll_start')<=before_count:
                assert time.monotonic()-start<12,'No status poll observed'
                time.sleep(.05)
            tap_node(close_target)
            wait_text('console_result','모의 종료')
            assert 'queued_close' in poll_log(),'Close was not exercised during a pending read'
            tap('home_button')
            results.append({'closeDuringStatusRefresh':'PASS','queuedActionObserved':True})

        tap("sdk_diagnostic");tap("read_driver_door")
        _,door=wait_text("diagnostic_result","조회 결과:")
        capture("v02-android-door")
        tap("read_battery")
        _,battery=wait_text("diagnostic_result","배터리 조회 결과:")
        capture("v02-android-battery")
        results.append({"door":door,"battery":battery})
        tap("back_button")
        capture("v02-android-home-final")
        report={"status":"PASS","serial":args.serial,"results":results,"drivingGate":"simulation only"}
        (OUT/'v02-android-e2e.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)
    except Exception as error:
        try:capture("v02-android-failure")
        except Exception:pass
        (OUT/'v02-android-e2e-failure.json').write_text(json.dumps({"error":str(error),"completed":results},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        raise
    finally:
        adb("reverse","tcp:8781","tcp:8781")


if __name__=='__main__':
    main()
