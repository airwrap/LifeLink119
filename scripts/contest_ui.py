"""Shared bounded native test harness for LifeLink's own accessibility nodes."""
from pathlib import Path
import subprocess
import time
import re
import xml.etree.ElementTree as ET
from ui_snapshot_client import SnapshotClient
from capture_emulator import capture_emulator

class ContestUI:
    def __init__(self,adb,out):self.adb=adb;self.out=Path(out);self.out.mkdir(parents=True,exist_ok=True);self.client=SnapshotClient(adb,auto_auth=True)
    def __enter__(self):self.client.start();self.focus();return self
    def __exit__(self,*a):self.client.close()
    def focus(self):
        # Accessibility actions alone do not focus a multi-resume AAOS task.
        # The v0.6 singleTask Activity preserves the current session and page.
        subprocess.run(self.adb+['shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity'],capture_output=True,check=True,timeout=35)
        time.sleep(.25)
    def open_medical(self,control,ready):
        for _ in range(3):
            self.focus();self.tap(control);self.client.authenticate_if_needed()
            end=time.monotonic()+2
            while time.monotonic()<end:
                if self.node(ready) is not None:return
                time.sleep(.2)
            if self.node(control) is None:break
        raise AssertionError('Authenticated destination unavailable: '+ready)
    def node(self,k):return ET.fromstring(self.client.node(k)).find('.//node')
    def tap(self,k):
        if self.client.click(k):return
        for d in ('down','up'):
            for _ in range(18):
                response=self.client.request('SCROLL_FORWARD' if d=='down' else 'SCROLL_BACKWARD')
                if 'error' in response:raise AssertionError(response)
                if response.get('scrolled')!='true':break
                time.sleep(.15)
                if self.client.click(k):return
        raise AssertionError('Control unavailable: '+k)
    def expect(self,k,value,seconds=15):
        end=time.monotonic()+seconds;last=''
        while time.monotonic()<end:
            n=self.node(k)
            if n is not None:
                last=n.get('text','')
                if value in last:return last
            time.sleep(.2)
        raise AssertionError(f'{k}: expected {value}, got {last}')
    def home(self):
        if self.node('x_home') is not None:self.tap('x_home')
        self.expect('x_emergency','')
    def gallery(self,id):
        self.home();self.tap('x_more');self.tap('x_tools');self.tap('x_gallery');self.tap('x_case_'+id.lower());self.expect('x_session_status','가상 상담원 응답')
    def support(self,id):self.tap('x_support');self.tap('x_action_'+id)
    def snap(self,name):
        tree=self.client.dump();(self.out/(name+'.xml')).write_text(tree,encoding='utf-8')
        capture_emulator(self.adb,self.out/(name+'.png'))
    def confirm(self):
        if self.client.authenticate_if_needed():return
        for _ in range(5):
            response=self.client.request('CLICK android:id/button1')
            if response.get('clicked')=='true':break
            time.sleep(.25)
        else:assert self.node('x_profile_alias') is not None or self.node('x_profile_save') is not None,response
        time.sleep(.35)
    def set_value(self,k,value,kind='SET_TEXT'):
        self.client.authenticate_if_needed()
        if self.node(k) is None:
            for d in ('down','up'):
                for _ in range(15):
                    if not self.client.scroll(d):break
                    if self.node(k) is not None:break
                if self.node(k) is not None:break
        assert self.node(k) is not None,'Input control was not visible: '+k
        result=self.client.request(f'{kind} com.lifelink119.app:id/{k} {value}');assert result.get('changed')=='true',result;time.sleep(.2)
    def labels(self):return [n.get('text','') for n in ET.fromstring(self.client.dump()).iter('node') if n.get('text')]
    @staticmethod
    def bounds(n):return list(map(int,re.findall(r'-?\d+',n.get('bounds'))))
