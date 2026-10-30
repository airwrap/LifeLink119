"""Bounded ADB boot wait; captures native stderr during normal cold boot."""
import argparse
import subprocess
import time


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--adb',required=True)
    p.add_argument('--serial',required=True)
    p.add_argument('--seconds',type=float,default=240)
    args=p.parse_args()
    end=time.monotonic()+args.seconds
    while time.monotonic()<end:
        try:
            result=subprocess.run([args.adb,'-s',args.serial,'shell','getprop','sys.boot_completed'],
                                  capture_output=True,text=True,timeout=max(.1,min(5,end-time.monotonic())))
            if result.returncode==0 and result.stdout.strip()=='1':
                print('Android boot complete.',flush=True)
                return 0
        except subprocess.TimeoutExpired:
            pass
        time.sleep(max(0,min(3,end-time.monotonic())))
    print('Android boot did not complete within the configured time.',flush=True)
    return 1


if __name__=='__main__':
    raise SystemExit(main())
