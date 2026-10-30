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


class VirtualConsole:
    def __init__(self,serial='emulator-5580'):
        if not serial.startswith('emulator-'):raise ValueError('Dedicated emulator only')
        self.serial=serial
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
        if self._adb('emu','avd','name').splitlines()[0]!='Pleos_Connect_API_34':raise RuntimeError('Unexpected AVD; device preserved')
        expected=f'{self.serial} tcp:8088 tcp:8088'
        forwards=self._adb('forward','--list').splitlines()
        if expected not in forwards:
            if any(' tcp:8088 ' in line for line in forwards):raise RuntimeError('8088 is forwarded to another device; preserved')
            self._adb('forward','--no-rebind','tcp:8088','tcp:8088')
        with self._open('http://127.0.0.1:8088/',timeout=5) as r:
            if b'Pleos Connect Virtual Console' not in r.read(2048):raise RuntimeError('Unexpected localhost service')

    def request(self,path,values=None):
        self._verify_mapping()
        if values is not None:path+='?'+urllib.parse.urlencode(values)
        request=urllib.request.Request('http://127.0.0.1:8088'+path,method='GET' if values is None else 'PUT')
        for attempt in range(3):
            self._verify_mapping()
            if values is not None and self.thread is not None and not self.stop_event.is_set():self._heartbeat()
            try:
                with self._open(request,timeout=5) as r:return json.loads(r.read(200_000))
            except urllib.error.HTTPError as error:
                detail=error.read(2000).decode(errors='replace')
                if error.code!=503 or attempt==2:raise RuntimeError(f'Virtual Console {error.code} at {path}: {detail}') from error
                time.sleep(.25)

    def _verify_mapping(self):
        if f'{self.serial} tcp:8088 tcp:8088' not in self._adb('forward','--list').splitlines():raise RuntimeError('ADB forward no longer belongs to test AVD')

    def state(self):return self.request('/vehicle/state')
    def _heartbeat(self,closing=False):
        self._verify_mapping()
        path='/browser/session?'+urllib.parse.urlencode({'client':self.client,**({'closing':1} if closing else {})})
        req=urllib.request.Request('http://127.0.0.1:8088'+path,data=b'',method='POST')
        with self._open(req,timeout=5) as r:return json.load(r)
    def __enter__(self):
        self._heartbeat()
        def keep_alive():
            while not self.stop_event.wait(.8):
                try:self._heartbeat();self.heartbeat_error=None
                except Exception as error:self.heartbeat_error=str(error)
        self.thread=threading.Thread(target=keep_alive,daemon=True);self.thread.start();return self
    def __exit__(self,*args):
        try:self.park()
        finally:
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
