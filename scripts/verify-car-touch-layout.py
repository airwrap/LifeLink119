"""Verify real Android display sizes and touch input on the dedicated UI AVD.

Uses public pages only, preserves FLAG_SECURE, restores the original display size.
"""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, time, xml.etree.ElementTree as ET
from native_window_client import WindowClient

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    parser.add_argument('--revision',choices=['v13','v14','v15','v16','v17'],default='v13')
    args=parser.parse_args()
    assert args.serial.startswith('emulator-')
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    out=ROOT/('verification/unslop-'+args.revision+'/car-layout');out.mkdir(parents=True,exist_ok=True)
    def run(*a):return subprocess.check_output(adb+list(a),timeout=35)
    avd=run('emu','avd','name').decode().splitlines()[0]
    assert avd=='LifeLink119_Android34_UI_20261107','Dedicated non-automotive UI AVD only'
    original=run('shell','wm','size').decode()
    override=re.search(r'Override size:\s*(\d+x\d+)',original)
    original_override=override.group(1) if override else None
    density=int(re.findall(r'(?:Physical|Override) density:\s*(\d+)',run('shell','wm','density').decode())[-1])/160
    results=[];c=None;restored=False
    def node(key):return ET.fromstring(c.node(key)).find('.//node')
    def wait(key,text=''):
        end=time.monotonic()+12
        while time.monotonic()<end:
            n=node(key)
            if n is not None and text in n.get('text',''):return n
            time.sleep(.15)
        raise AssertionError('Unavailable: '+key)
    def tap(key):
        if c.click(key):return
        for direction in ['down','up']:
            for _ in range(20):
                if not c.scroll(direction):break
                if c.click(key):return
        raise AssertionError('Not reachable: '+key)
    def bounds(n):return list(map(int,re.findall(r'-?\d+',n.get('bounds',''))))
    def capture(label,keys=()):
        time.sleep(.4)
        assert not c.request('PING').get('secureRefused')
        tree=ET.fromstring(c.dump())
        (out/(resolution+'-'+label+'.jpg')).write_bytes(c.frame())
        (out/(resolution+'-'+label+'.xml')).write_text(ET.tostring(tree,encoding='unicode'),encoding='utf-8')
        scroll=bounds(wait('x_scroll'))
        controls={}
        for n in tree.iter('node'):
            if n.get('class')!='android.widget.Button':continue
            rect=bounds(n)
            assert rect[2]-rect[0]>=96*density-1 and rect[3]-rect[1]>=96*density-1,(label,n.attrib,'small touch target')
            assert n.get('gravity')=='17' and n.get('text-clipped')=='false' and n.get('ellipsis-count')=='0',(label,n.attrib,'label alignment/clipping')
        for key in keys:
            n=wait(key);r=bounds(n)
            assert r[0]>=scroll[0] and r[1]>=scroll[1] and r[2]<=scroll[2] and r[3]<=scroll[3],(resolution,label,key,r,scroll,'requires scrolling')
            controls[key]={'bounds':r,'text':n.get('text'),'heightDp':round((r[3]-r[1])/density,1)}
        return controls
    try:
        for resolution in ['1280x720','1024x600']:
            run('shell','wm','size',resolution);time.sleep(1)
            run('shell','am','force-stop','com.lifelink119.app')
            run('shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
            c=WindowClient(adb);c.start()
            run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
            homes=capture('home',['x_emergency','x_consult','x_home_112','x_family'])
            if args.revision in ('v14','v15','v16','v17'):
                home_nodes=[wait(key) for key in homes]
                assert all(n.get('background-color')==('fff2f0e8' if args.revision=='v17' else 'ff3e4754' if args.revision in ('v15','v16') else 'ffffffff') for n in home_nodes),'Equal neutral home surfaces'
                assert len({n.get('text-size-px') for n in home_nodes})==1,'Equal home title sizes'
                assert len({n.get('text-color') for n in home_nodes})==1,'Equal home title colors'
            # This is actual Android input, separate from the helper's performClick path.
            run('shell','uiautomator','dump','/data/local/tmp/lifelink-car-public.xml')
            public=run('shell','cat','/data/local/tmp/lifelink-car-public.xml').decode()
            run('shell','rm','/data/local/tmp/lifelink-car-public.xml')
            # Display resizing can leave the test AVD's Pixel Launcher in an
            # ANR dialog. Close that named failed launcher using its real UI;
            # never dismiss application/security dialogs or change app data.
            public_tree=ET.fromstring(public)
            if any(n.get('resource-id')=='android:id/alertTitle' and n.get('text')=="Pixel Launcher isn't responding" for n in public_tree.iter('node')):
                close=next(n for n in public_tree.iter('node') if n.get('resource-id')=='android:id/aerr_close')
                x1,y1,x2,y2=bounds(close);run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
                run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
                run('shell','uiautomator','dump','/data/local/tmp/lifelink-car-public.xml')
                public=run('shell','cat','/data/local/tmp/lifelink-car-public.xml').decode()
                run('shell','rm','/data/local/tmp/lifelink-car-public.xml')
                print('Recovered named Pixel Launcher ANR through its Close app control',flush=True)
            (out/(resolution+'-public.xml')).write_text(public,encoding='utf-8')
            target=next(n for n in ET.fromstring(public).iter('node') if n.get('resource-id')=='com.lifelink119.app:id/x_emergency')
            x1,y1,x2,y2=bounds(target)
            run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            wait('x_session_status','가상 상담원 응답')
            driving=capture('driving',['x_guided_video','x_driving_details','x_driving_extra','x_end'])
            tap('x_guided_video');capture('video-request',['x_guide_request'])
            tap('x_home');tap('x_consult');capture('consult',['x_medical','x_109'])
            tap('x_medical');wait('x_session_status','가상 상담원 응답')
            consult=capture('consult-session',['x_guided_video','x_primary_1','x_support','x_end'])
            tap('x_home');tap('x_more');tap('x_manual')
            time.sleep(.3)
            # Legacy pages use a different ScrollView; capture, without assuming all six topics fit.
            assert not c.request('PING').get('secureRefused')
            (out/(resolution+'-manual.jpg')).write_bytes(c.frame())
            tap('manual_first');time.sleep(.3)
            (out/(resolution+'-manual-detail.jpg')).write_bytes(c.frame())
            results.append({'resolution':resolution,'status':'PASS','home':homes,'driving':driving,'consultSession':consult,'actual119Touch':True})
            print('PASS '+resolution+': home and connection actions visible without scrolling; actual119 touch',flush=True)
            c.close();c=None
    finally:
        if c:c.close()
        run('shell','wm','size',original_override or 'reset')
        restored=run('shell','wm','size').decode().strip()==original.strip()
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
        (out/'results.json').write_text(json.dumps({'status':'PASS' if len(results)==2 and restored else 'FAIL','avd':avd,'automotivePlatform':False,'apkSha256':hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest(),'checks':results,'originalDisplayRestored':restored,'limitations':['Standard Android UI checks, not Pleos/real CarUX or real calls.','No medical authentication, private captures, or human touch/rotary validation.']},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    assert restored,'Original display could not be restored'

if __name__=='__main__':main()
