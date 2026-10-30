"""Optional localhost metadata publication plus the preserved actual SDK read surface."""
import json
import os
from pathlib import Path
import urllib.request
from contest_ui import ContestUI
from ui_snapshot_client import verify_installed_apk

root=Path(__file__).resolve().parents[1];out=root/'verification/v06/pc-sdk';sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580'];result={'status':'FAIL'}
def snapshots():
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open('http://127.0.0.1:8781/api/demo/expansion/snapshots',timeout=5) as r:return json.load(r)['items']
try:
 with ContestUI(adb,out) as ui:
    ui.home();ui.tap('x_emergency');ui.tap('x_119');ui.expect('x_session_status','가상 상담원 응답');ui.tap('x_primary_2');ui.tap('x_share_location');ui.tap('x_back');ui.tap('x_support');ui.tap('x_center');ui.tap('x_pc_consent');ui.tap('x_pc_publish');ui.expect('x_pc_status','게시됨')
    data=snapshots();assert len(data)==1 and data[0]['sharedFields']==['LOCATION'];assert set(data[0])=={'schemaVersion','syntheticOnly','id','revision','target','scenarioId','status','sharedFields','video','videoSource','police','navigation','sign'}
    ui.tap('x_pc_revoke');ui.expect('x_pc_status','철회됨');assert snapshots()==[]
    ui.home();ui.tap('x_more');ui.tap('x_power');ui.tap('x_sdk');ui.tap('read_driver_door');door=ui.expect('diagnostic_result','조회 결과: READ',seconds=22);ui.tap('read_battery');battery=ui.expect('diagnostic_result','배터리 조회 결과: READ',seconds=22);ui.snap('sdk-read');ui.home()
    result={'status':'PASS','metadataOnly':True,'revoked':True,'door':door,'battery':battery,'apkSha256':verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.6.0-demo')}
except Exception as e:result={'status':'FAIL','error':str(e)};print('FAIL',str(e),flush=True)
finally:(out.parent/'pc-sdk.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
if result['status']!='PASS':raise SystemExit(1)
