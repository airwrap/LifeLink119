"""Local control adapter for the official Virtual Console on the dedicated test AVD.

Endpoints are taken from the emulator's served console JavaScript. Never connects
to a physical device or accepts a remote host. All writes require an AVD identity
check and an exact ADB forward mapping.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import uuid
import urllib.parse
import urllib.request
import urllib.error
import http.client


class VirtualConsole:
    def __init__(self,serial='emulator-5580',avd='Pleos_Connect_API_34',port=8088):
        if not serial.startswith('emulator-'):raise ValueError('Dedicated emulator only')
        if avd not in {'Pleos_Connect_API_34','LifeLink119_UI_20261107','LifeLink119_SDK_Compat_20261107'}:raise ValueError('Unexpected test AVD')
        if not 1024<=port<=65535:raise ValueError('Invalid loopback test port')
        self.serial=serial
        self.avd=avd;self.port=port;self.base='http://127.0.0.1:'+str(port)
        sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
        self.adb=[str(sdk/'platform-tools/adb.exe'),'-s',serial]
        self.client='lifelink-contest-'+uuid.uuid4().hex
        self.stop_event=threading.Event();self.thread=None
        self.heartbeat_error=None
        self._verify()

    def _adb(self,*args):return subprocess.check_output(self.adb+list(args),stderr=subprocess.STDOUT,timeout=15).decode().replace('\r','')
    @staticmethod
    def _open(request,timeout):
        # This adapter is loopback-only; never send console commands to the
        # Windows/environment proxy, which may not bypass 127.0.0.1.
        return urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request,timeout=timeout)
    def _verify(self):
        if self._adb('emu','avd','name').splitlines()[0]!=self.avd:raise RuntimeError('Unexpected AVD; device preserved')
        expected=f'{self.serial} tcp:{self.port} tcp:8088'
        forwards=self._adb('forward','--list').splitlines()
        if expected not in forwards:
            if any(f' tcp:{self.port} ' in line for line in forwards):raise RuntimeError('Test port is forwarded to another device; preserved')
            self._adb('forward','--no-rebind',f'tcp:{self.port}','tcp:8088')
        with self._open(self.base+'/',timeout=5) as r:
            if b'Pleos Connect Virtual Console' not in r.read(2048):raise RuntimeError('Unexpected localhost service')

    def request(self,path,values=None):
        self._verify_mapping()
        if values is not None:path+='?'+urllib.parse.urlencode(values)
        request=urllib.request.Request(self.base+path,method='GET' if values is None else 'PUT')
        for attempt in range(5):
            self._verify_mapping()
            try:
                if values is not None and self.thread is not None and not self.stop_event.is_set():self._heartbeat()
                with self._open(request,timeout=5) as r:return json.loads(r.read(200_000))
            except urllib.error.HTTPError as error:
                detail=error.read(2000).decode(errors='replace')
                if error.code!=503 or attempt==4:raise RuntimeError(f'Virtual Console {error.code} at {path}: {detail}') from error
            except (OSError,http.client.HTTPException) as error:
                if attempt==4:raise RuntimeError(f'Virtual Console transport unavailable at {path}: {error}') from error
            time.sleep(.3)

    def _verify_mapping(self):
        if f'{self.serial} tcp:{self.port} tcp:8088' not in self._adb('forward','--list').splitlines():raise RuntimeError('ADB forward no longer belongs to test AVD')
        if self._adb('emu','avd','name').splitlines()[0]!=self.avd:raise RuntimeError('Test AVD identity changed')

    def state(self):return self.request('/vehicle/state')
    def driver_door(self,opened):
        if type(opened) is not bool:raise ValueError('Door fixture must be a boolean')
        feature=next((f for f in self.request('/vehicle/catalog')['features'] if f['id']=='door-pos'),None)
        if feature is None or feature['write_endpoint']!='/vehicle/body' or feature['control'].get('field_by_area',{}).get('front_left')!='door_fl':
            raise RuntimeError('Official console door contract changed; no value written')
        return self.request('/vehicle/body',{'door_fl':str(opened).lower()})
    def soc(self,percent):
        if type(percent) is not int or not 0<=percent<=100:raise ValueError('SOC fixture must be an integer percentage')
        feature=next((f for f in self.request('/vehicle/catalog')['features'] if f['id']=='ev-battery-soc'),None)
        if feature is None or feature['write_endpoint']!='/vehicle/ev-battery' or feature['control'].get('params')!={'field':'soc'} or feature['control'].get('field')!='value':
            raise RuntimeError('Official console SOC contract changed; no value written')
        return self.request('/vehicle/ev-battery',{'field':'soc','value':percent})
    def _heartbeat(self,closing=False):
        self._verify_mapping()
        path='/browser/session?'+urllib.parse.urlencode({'client':self.client,**({'closing':1} if closing else {})})
        req=urllib.request.Request(self.base+path,data=b'',method='POST')
        for attempt in range(5):
            if not closing and self.stop_event.is_set():return {'active':False}
            try:
                with self._open(req,timeout=5) as r:return json.load(r)
            except (OSError,http.client.HTTPException):
                if attempt==4:raise
                time.sleep(.3)
    def __enter__(self):
        if self.thread is not None and self.thread.is_alive():raise RuntimeError('Console session already active')
        self.stop_event.clear();self.heartbeat_error=None
        self._heartbeat()
        def keep_alive():
            while not self.stop_event.wait(.8):
                try:self._heartbeat();self.heartbeat_error=None
                except Exception as error:self.heartbeat_error=str(error)
        self.thread=threading.Thread(target=keep_alive,daemon=True);self.thread.start();return self
    def __exit__(self,*args):
        try:self.park()
        finally:self.close()
    def close(self):
        self.stop_event.set()
        if self.thread:self.thread.join(timeout=6)
        self._heartbeat(closing=True)
    def gear(self,gear):
        if gear not in {'Park','Drive','Neutral','Reverse'}:raise ValueError('Invalid fixture gear')
        return self.request('/vehicle/gear',{'gear':gear})
    def controls(self,speed=0,accelerator=0,brake=0,steering=0):
        if not 0<=speed<=50 or not 0<=accelerator<=100 or not 0<=brake<=100 or not -540<=steering<=540:raise ValueError('Out of bounded demo range')
        return self.request('/vehicle/controls',dict(speed=speed,accelerator=accelerator,brake=brake,steering=steering))
    def park(self):
        self.controls();self.gear('Park')
        deadline=time.monotonic()+4
        while time.monotonic()<deadline:
            state=self.state()
            if (state.get('gear',{}).get('actual') or {}).get('value')=='Park' and state['controls']['speed_kph']==0:return state
            time.sleep(.15)
        raise RuntimeError('Emulator Park state was not confirmed')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['status','park']);p.add_argument('--serial',default='emulator-5580');a=p.parse_args()
    v=VirtualConsole(a.serial)
    if a.action=='park':
        with v:print(json.dumps(v.park(),ensure_ascii=False,indent=2))
    else:print(json.dumps(v.state(),ensure_ascii=False,indent=2))
