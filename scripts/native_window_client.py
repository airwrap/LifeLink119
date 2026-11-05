from pathlib import Path
import subprocess,socket,time,json,struct
class WindowClient:
    def __init__(self,adb):self.adb=adb;self.port=None;self.process=None
    def start(self):
        self.port=int(subprocess.check_output(self.adb+['forward','tcp:0','localabstract:lifelink-native-verify'],timeout=20))
        user=subprocess.check_output(self.adb+['shell','am','get-current-user'],timeout=15).decode().strip()
        self.process=subprocess.Popen(self.adb+['shell','am','instrument','-w','--user',user,'com.lifelink119.nativeverify/.WindowInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        for _ in range(180):
            try:
                r=self.request('PING')
                if r.get('ready'):return self
            except (OSError,ValueError):pass
            if self.process.poll() is not None:raise RuntimeError(self.process.communicate()[0].decode(errors='replace'))
            time.sleep(.25)
        raise RuntimeError('Window capture helper did not become ready')
    def request(self,command):
        with socket.create_connection(('127.0.0.1',self.port),timeout=35) as s:
            s.sendall((command+'\n').encode())
            return json.loads(s.makefile('rb').readline(1000000))
    def frame(self):
        with socket.create_connection(('127.0.0.1',self.port),timeout=35) as s:
            s.sendall(b'FRAME\n');f=s.makefile('rb');header=f.read(4)
            if len(header)!=4:raise RuntimeError('Frame stream closed')
            size=struct.unpack('>i',header)[0]
            if size<0:raise RuntimeError('Protected window capture refused; FLAG_SECURE preserved')
            data=f.read(size)
            if len(data)!=size:raise RuntimeError('Incomplete native frame')
            return data
    def node(self,k):return self.request('GET '+k).get('hierarchy','<hierarchy/>')
    def dump(self):return self.request('DUMP')['hierarchy']
    def click(self,k):
        result=self.request('CLICK '+k).get('clicked')=='true'
        if result:time.sleep(.12)
        return result
    def scroll(self,d):
        result=self.request('SCROLL_FORWARD' if d=='down' else 'SCROLL_BACKWARD').get('scrolled')=='true'
        if result:time.sleep(.15)
        return result
    def close(self):
        try:self.request('STOP')
        except (OSError,ValueError):pass
        if self.process:
            try:
                output=self.process.communicate(timeout=10)[0]
                if output:
                    log=Path(__file__).resolve().parents[1]/'verification/v07/native-window-last.log';log.parent.mkdir(parents=True,exist_ok=True);log.write_bytes(output)
            except subprocess.TimeoutExpired:self.process.terminate()
        if self.port:subprocess.run(self.adb+['forward','--remove','tcp:'+str(self.port)],capture_output=True,timeout=20)
