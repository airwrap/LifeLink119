"""Capture the public home after the family Hyundai artwork update."""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "scripts"))
from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard

from qa_avd_guard import android_sdk
adb = [str(android_sdk() / "platform-tools/adb.exe"), "-s", "emulator-5584"]
guard = QaAvdGuard(adb)
guard.verify_target()
subprocess.run(adb + ["install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk")], check=True)
out = ROOT / "verification/family-hyundai"
out.mkdir(parents=True, exist_ok=True)
c = WindowClient(adb)
try:
    c.start(choose_setup=False)
    c.click("x_home")
    hierarchy = c.dump()
    assert "x_emergency" in hierarchy
    assert not c.request("PING").get("secureRefused")
    (out / "home.jpg").write_bytes(c.frame())
    (out / "home.xml").write_text(hierarchy, encoding="utf-8")
    print(json.dumps({"homeCaptured": True, "privateScreenCaptured": False}))
finally:
    c.close()
    subprocess.run(adb + ["shell", "am", "start", "-W", "-n", "com.lifelink119.app/.MainActivity"], check=True, capture_output=True)
