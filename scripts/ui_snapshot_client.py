"""Short-lived ADB-only client for the separate UI verification package."""
import json
import hashlib
import socket
import subprocess
import time
import os
import re
import xml.etree.ElementTree as ET

def verify_installed_apk(adb,apk,sdk,version):
    """Use identical APK bytes when the busy package dumpsys omits metadata."""
    user=subprocess.check_output(adb+['shell','am','get-current-user'],timeout=15).decode().strip()
    if not user.isdigit():
        raise RuntimeError('Could not identify the current Android user')
    paths=subprocess.check_output(adb+['shell','pm','path','--user',user,'com.lifelink119.app'],timeout=30).decode().strip()
    if not paths.startswith('package:'):
        raise RuntimeError('The APK is not installed for the current Android user')
    remote=paths.split('package:',1)[1].splitlines()[0]
    installed=subprocess.check_output(adb+['shell','sha256sum',remote],timeout=30).decode().split()[0]
    expected=hashlib.sha256(apk.read_bytes()).hexdigest()
    if installed != expected:
        raise RuntimeError('Installed APK bytes differ from the APK under test')
    metadata=subprocess.check_output([str(sdk/'build-tools/35.0.0/aapt.exe'),'dump','badging',str(apk)],timeout=20).decode()
    if "versionName='"+version+"'" not in metadata:
        raise RuntimeError('Unexpected APK version')
    return installed

class SnapshotClient:
    def __init__(self,adb,auto_auth=False):
        self.adb=adb;self.process=None;self.port=None;self.auto_auth=auto_auth;self._auth_busy=False;self.reconnections=0
    def authenticate_if_needed(self):
        if not self.auto_auth or self._auth_busy:return False
        self._auth_busy=True
        try:
            raw=self.request('GET com.lifelink119.app:id/privacy_pin')
            present='privacy_pin' in raw.get('hierarchy','')
            if not present:
                lock=self.request('GET com.lifelink119.app:id/privacy_unlock')
                if 'privacy_unlock' not in lock.get('hierarchy',''):return False
                if self.request('CLICK com.lifelink119.app:id/privacy_unlock').get('clicked')!='true':return False
                time.sleep(.2);raw=self.request('GET com.lifelink119.app:id/privacy_pin');present='privacy_pin' in raw.get('hierarchy','')
                if not present:return True
            pin=os.environ.get('LIFELINK_TEST_PIN','')
            if not re.fullmatch(r'[0-9]{6,12}',pin):raise RuntimeError('Local test runner must supply an ephemeral LIFELINK_TEST_PIN; never send a personal PIN to chat')
            assert self.request('SET_TEXT com.lifelink119.app:id/privacy_pin '+pin).get('changed')=='true'
            if 'privacy_confirm' in self.request('GET com.lifelink119.app:id/privacy_confirm').get('hierarchy',''):
                assert self.request('SET_TEXT com.lifelink119.app:id/privacy_confirm '+pin).get('changed')=='true'
            assert self.request('CLICK android:id/button1').get('clicked')=='true'
            end=time.monotonic()+90
            while time.monotonic()<end:
                state=self.request('GET com.lifelink119.app:id/privacy_pin')
                if 'privacy_pin' not in state.get('hierarchy',''):time.sleep(.25);return True
                time.sleep(.15)
            raise RuntimeError('Test medical authentication did not complete; credential content is redacted')
        finally:self._auth_busy=False
    def start(self, choose_setup=True):
        self.port=int(subprocess.check_output(self.adb+['forward','tcp:0','localabstract:lifelink-ui-verify'],timeout=20))
        user=subprocess.check_output(self.adb+['shell','am','get-current-user'],timeout=20).decode().strip()
        assert user.isdigit(), 'No current Android user'
        self.process=subprocess.Popen(self.adb+['shell','am','instrument','-w','--user',user,'-e','persistent','true','com.lifelink119.verify/.SnapshotInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        end=time.monotonic()+60;last=None
        while time.monotonic()<end:
            try:
                value=self.request('PING')
                if value.get('ready') is True:
                    if choose_setup:
                        chosen=self.request('CLICK com.lifelink119.app:id/x_ai_setup_local')
                        if chosen.get('clicked')=='true':time.sleep(.35)
                    return self
                last=value
            except (OSError,ValueError) as error:last=str(error)
            time.sleep(.25)
        self.close();raise RuntimeError('Persistent snapshot helper did not start: '+str(last))
    def request(self,command):
        with socket.create_connection(('127.0.0.1',self.port),timeout=40) as connection:
            connection.sendall((command+'\n').encode())
            with connection.makefile('rb') as stream:
                data=stream.readline(1_000_000)
            if not data:raise OSError('Snapshot helper closed the connection')
            return json.loads(data)
    def dump(self):
        return self._hierarchy('DUMP')
    def _hierarchy(self,command):
        for attempt in range(2):
            for _ in range(6):
                value=self.request(command)
                if 'hierarchy' in value:return value['hierarchy']
                if 'No active accessibility root' not in value.get('error',''):raise RuntimeError(str(value))
                time.sleep(.2)
            if attempt==0:
                # Reconnect only the verification service. Preserve the tested
                # app/task/auth state and keep all destination assertions.
                self.close();self.start();self.reconnections+=1
                print('UI helper reconnected after an automotive window transition',flush=True)
        raise RuntimeError(str(value))
    def click(self,key):
        command='CLICK com.lifelink119.app:id/'+key
        value=self.request(command)
        if 'No active accessibility root' in value.get('error',''):
            self._hierarchy('DUMP');value=self.request(command)
        clicked=value.get('clicked')=='true'
        if not clicked and not key.startswith('privacy_') and self.authenticate_if_needed():
            clicked=self.request('CLICK com.lifelink119.app:id/'+key).get('clicked')=='true'
        # ACTION_CLICK can return before the main looper lays out the new page.
        # One-shot instrumentation previously hid this with its startup delay.
        if clicked:time.sleep(.35)
        return clicked
    def node(self,key):
        for _ in range(6):
            hierarchy=self._hierarchy('GET com.lifelink119.app:id/'+key)
            if not key.startswith('privacy_') and hierarchy=='<hierarchy/>' and self.authenticate_if_needed():continue
            return hierarchy
        raise RuntimeError('Repeated medical authentication did not expose the expected test control')
    def scroll(self,direction):
        assert direction in ('up','down')
        command='SCROLL_FORWARD' if direction=='down' else 'SCROLL_BACKWARD'
        value=self.request(command)
        if 'No active accessibility root' in value.get('error',''):
            self._hierarchy('DUMP');value=self.request(command)
        moved=value.get('scrolled')=='true'
        if moved:time.sleep(.35)
        return moved
    def close(self):
        try:
            if self.port is not None:self.request('STOP')
        except (OSError,ValueError):pass
        if self.process is not None:
            try:self.process.communicate(timeout=10)
            except subprocess.TimeoutExpired:
                subprocess.run(self.adb+['shell','am','force-stop','com.lifelink119.verify'],timeout=20,check=True)
                self.process.communicate(timeout=10)
        if self.port is not None:
            subprocess.run(self.adb+['forward','--remove','tcp:'+str(self.port)],timeout=20,check=True)
        self.process=None;self.port=None
