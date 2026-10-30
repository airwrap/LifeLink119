"""ADB smoke check on an already running, dedicated Pleos test emulator.

Uses synthetic in-app sessions only. Optional font-size test restores the setting.
Run: python scripts/smoke-ui.py --serial emulator-5580
"""
import argparse
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "verification"
PACKAGE = "com.lifelink119.app"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--font-scale", action="store_true", help="Include global font configuration test; Pleos may obscure the app during this change")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    command = [str(sdk / "platform-tools/adb.exe"), "-s", args.serial]
    OUT.mkdir(exist_ok=True)
    results = []

    def adb(*parts, timeout=25):
        return subprocess.check_output(command + list(parts), timeout=timeout).decode("utf-8", "replace").strip()

    assert args.serial.startswith("emulator-"), "Use a dedicated test emulator."
    assert "Pleos_Connect_API_34" in adb("emu", "avd", "name"), "Unexpected AVD."

    def dump(name=None):
        adb("shell", "uiautomator", "dump", "/data/local/tmp/lifelink-ui.xml")
        xml = adb("shell", "cat", "/data/local/tmp/lifelink-ui.xml")
        tree = ET.fromstring(xml)
        assert any(n.get("resource-id") == PACKAGE + ":id/safety_banner" for n in tree.iter("node")), "Demo banner missing or app obscured"
        if name:
            (OUT / (name + ".xml")).write_text(xml, encoding="utf-8")
        return tree

    def node(tree, key):
        return next((n for n in tree.iter("node") if n.get("resource-id") == PACKAGE + ":id/" + key), None)

    def text(tree):
        return "\n".join(n.get("text", "") for n in tree.iter("node"))

    def tap(key, tree=None):
        tree = tree if tree is not None else dump()
        target = node(tree, key)
        if target is None:
            # Only scroll the app's own content. The fixed banner remains visible.
            scroll = next(n for n in tree.iter("node") if n.get("class") == "android.widget.ScrollView" and n.get("package") == PACKAGE)
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", scroll.get("bounds")))
            adb("shell", "input", "swipe", str((x1+x2)//2), str(y2-80), str((x1+x2)//2), str(y1+80), "300")
            target = node(dump(), key)
        assert target is not None, key + " missing"
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", target.get("bounds")))
        assert x2 > x1 and y2 > y1, key + " clipped"
        adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))

    def capture(name):
        dump(name)
        adb("shell", "screencap", "-p", "/data/local/tmp/lifelink-shot.png")
        adb("pull", "/data/local/tmp/lifelink-shot.png", str(OUT / (name + ".png")))

    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    capture("ui-home")
    for index, scenario in enumerate(("medical", "video", "transport", "police", "wellbeing")):
        home = dump()
        tap("scenario_" + scenario, home)
        intro = dump()
        assert "1  " in text(intro)
        tap("next_step", intro)
        review = dump()
        for key in ("share_vehicle", "share_location"):
            assert node(review, key).get("checked") == "false", "Consent must start off"
        if scenario == "video":
            tap("share_vehicle", review)
            tap("share_location")
            capture("ui-review-selected")
        elif scenario == "transport":
            tap("share_vehicle", review)
        tap("connect_demo")
        connected = dump()
        ticket = node(connected, "session_ticket").get("text")
        assert ticket == "DEMO-" + str(index + 1).zfill(4), ticket
        if scenario == "medical":
            assert "공유 안 함" in text(connected)
            capture("ui-connected-default")
        if scenario == "video" and args.font_scale:
            assert "DEMO_GRID" in text(connected)
            # Configuration recreation must preserve the synthetic ticket and choices.
            old_scale = adb("shell", "settings", "get", "system", "font_scale")
            try:
                adb("shell", "settings", "put", "system", "font_scale", "1.5")
                time.sleep(6)
                # Pleos may move focus to its DrivingView during global configuration changes.
                # Bring the existing Activity forward without clearing its task or state.
                adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
                recreated = dump()
                assert node(recreated, "session_ticket").get("text") == ticket
                assert "DEMO_GRID" in text(recreated)
                capture("ui-font150-session")
            finally:
                if old_scale == "null":
                    adb("shell", "settings", "delete", "system", "font_scale")
                else:
                    adb("shell", "settings", "put", "system", "font_scale", old_scale)
                time.sleep(6)
                adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
        if scenario == "video":
            assert "DEMO_GRID" in text(dump())
            tap("close_session")
            assert "종료" in text(dump())
            tap("back_button")
            restored = dump()
            assert node(restored, "share_vehicle").get("checked") == "true"
            assert node(restored, "share_location").get("checked") == "true"
            # Back then forward creates a new local session; preserve the count expectation.
            tap("back_button", restored)
            tap("back_button")
            results.append({"scenario": scenario, "ticket": ticket, "consentDefaultsOff": True,
                            "backPreservedSelection": True, "fontRecreationTestIncluded": args.font_scale, "closed": True})
            print("PASS " + scenario + " " + ticket, flush=True)
            continue
        tap("close_session", connected)
        closed = dump()
        assert node(closed, "close_session") is None
        assert "종료" in text(closed)
        tap("home_button", closed)
        results.append({"scenario": scenario, "ticket": ticket, "consentDefaultsOff": True, "closed": True})
        print("PASS " + scenario + " " + ticket, flush=True)

    tap("sdk_diagnostic")
    tap("read_driver_door")
    start = time.monotonic()
    while True:
        state = dump()
        result = node(state, "diagnostic_result").get("text")
        if "조회 결과:" in result:
            break
        assert time.monotonic() - start < 25, "Gateway did not finish/timeout"
        time.sleep(1)
    capture("ui-sdk-diagnostic")
    results.append({"sdkDiagnostic": result, "elapsedSeconds": round(time.monotonic()-start, 1)})
    tap("back_button")
    capture("ui-home-final")
    report = {"serial": args.serial, "results": results, "status": "PASS"}
    (OUT / "ui-smoke.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)


if __name__ == "__main__":
    main()
