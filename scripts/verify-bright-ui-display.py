"""Real display coordinates and ADB touch, without instrumenting the app.

Complements native View assertions after Android's launcher/task resizing.
Only public home/connection screens; no credentials, records, or real calls.
"""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,time,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--revision',choices=['v17','v18'],default='v17');args=parser.parse_args()
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580']
    def run(*a):return subprocess.check_output(adb+list(a),timeout=40)
    assert run('emu','avd','name').decode().splitlines()[0] in ('LifeLink119_Android34_UI_20261107','LifeLink119_SafeCall_20261108')
    assert 'feature:android.hardware.type.automotive' not in run('shell','pm','list','features').decode()
    original=run('shell','wm','size').decode();o=re.search(r'Override size:\s*(\d+x\d+)',original)
    density=int(re.findall(r'(?:Override|Physical) density:\s*(\d+)',run('shell','wm','density').decode())[-1])/160
    out=ROOT/('verification/bright-ui-v17/actual-display' if args.revision=='v17' else 'verification/v18/actual-display');out.mkdir(parents=True,exist_ok=True)
    result={'status':'FAIL','checks':[],'input':'Actual adb shell input tap; app is not instrumented'}
    def rect(n):return list(map(int,re.findall(r'-?\d+',n.get('bounds'))))
    def launch():
        run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity');time.sleep(.8)
    def tree():
        remote='/data/local/tmp/lifelink-bright-public.xml'
        try:
            run('shell','uiautomator','dump',remote);raw=run('shell','cat',remote).decode();t=ET.fromstring(raw)
        finally:run('shell','rm','-f',remote)
        if any(n.get('resource-id')=='android:id/alertTitle' and n.get('text')=="Pixel Launcher isn't responding" for n in t.iter('node')):
            n=next(n for n in t.iter('node') if n.get('resource-id')=='android:id/aerr_close');x1,y1,x2,y2=rect(n)
            run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1);launch()
            result['launcherAnrClosed']=True
            return tree()
        return t,raw
    def wait(key,text=''):
        deadline=time.monotonic()+40
        while time.monotonic()<deadline:
            t,raw=tree()
            for n in t.iter('node'):
                if n.get('resource-id')=='com.lifelink119.app:id/'+key and text in n.get('text',''):return n,t,raw
            time.sleep(.3)
        raise AssertionError('Actual visible control not observed: '+key)
    def tap(n):
        assert n.get('enabled')=='true';x1,y1,x2,y2=rect(n)
        run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
    def capture(label,raw):
        assert 'privacy_pin' not in raw
        (out/(label+'.xml')).write_text(raw,encoding='utf-8')
        png=run('exec-out','screencap','-p');assert png.startswith(b'\x89PNG');(out/(label+'.png')).write_bytes(png)
    try:
        for resolution in ['1280x720','1024x600']:
            width,height=map(int,resolution.split('x'));run('shell','wm','size',resolution)
            run('shell','am','force-stop','com.lifelink119.app')
            run('shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity');time.sleep(1)
            _,t,raw=wait('x_emergency');home={}
            for key in ['x_emergency','x_consult','x_home_112','x_family']:
                n=next(n for n in t.iter('node') if n.get('resource-id')=='com.lifelink119.app:id/'+key);r=rect(n)
                assert r[0]>=0 and r[1]>=0 and r[2]<=width and r[3]<=height,(key,r,resolution)
                assert (r[2]-r[0])/density>=96 and (r[3]-r[1])/density>=96,(key,r)
                home[key]={'bounds':r,'text':n.get('text')}
            capture(resolution+'-home',raw)
            tap(next(n for n in t.iter('node') if n.get('resource-id')=='com.lifelink119.app:id/x_emergency'))
            _,t,raw=wait('x_session_status','가상 상담원 응답');actions={}
            for key in ['x_guided_video','x_driving_details','x_driving_extra','x_end']:
                n=next(n for n in t.iter('node') if n.get('resource-id')=='com.lifelink119.app:id/'+key);r=rect(n)
                assert r[2]<=width and r[3]<=height and (r[3]-r[1])/density>=96
                actions[key]=r
            capture(resolution+'-119-connected',raw)
            result['checks'].append({'resolution':resolution,'status':'PASS','home':home,'connectionActions':actions,'actual119Touch':True})
            print('PASS real display '+resolution+': four home targets and connection controls fit; actual119 touch',flush=True)
        result['status']='PASS'
    except Exception as e:result['error']=repr(e);print('FAIL '+repr(e),flush=True)
    finally:
        run('shell','wm','size',o.group(1) if o else 'reset')
        result['originalDisplayRestored']=run('shell','wm','size').decode().strip()==original.strip()
        if not result['originalDisplayRestored']:result['status']='FAIL'
        result['apkSha256']=hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
        (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        run('shell','am','force-stop','com.lifelink119.app');launch()
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
