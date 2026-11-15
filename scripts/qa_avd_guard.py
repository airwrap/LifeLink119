"""Fail-closed guard for scripts that create or erase private test data.

The everyday Pleos and standard demo AVDs are intentionally excluded.
An acquired guard permits cleanup of data produced by this run only.
"""
import re
import subprocess
import xml.etree.ElementTree as ET

QA_AVDS = frozenset({
    "LifeLink119_QA_API_34",
    "LifeLink119_Std_QA_API_34",
    "LifeLink119_Verify_20261101",
    "LifeLink119_Android34_UI_20261107",
    "LifeLink119_SafeCall_20261108",
})
PRIVATE_KEYS = {
    "lifelink_emergency_checkpoint": {"cipher"},
    "lifelink_medical_lock_v1": {"pin_salt", "pin_verifier"},
    "lifelink_personal_health_v1": {"record_cipher", "records_cipher"},
    "lifelink_synthetic_profiles_v05": {"profiles", "profiles_cipher"},
}


class QaAvdGuard:
    def __init__(self, adb, run=subprocess.run):
        self.adb = list(adb)
        self.run = run
        self.identity = None
        self.acquired = False
        if "-s" not in self.adb or not re.fullmatch(r"emulator-[0-9]+", self.adb[self.adb.index("-s") + 1]):
            raise RuntimeError("QA requires an explicit emulator serial; no device was changed")

    def _call(self, *args):
        return self.run(self.adb + list(args), capture_output=True, timeout=20)

    def _checked(self, *args):
        result = self._call(*args)
        if result.returncode:
            raise RuntimeError("QA target could not be verified; no reset is permitted")
        return result.stdout.decode("utf-8").strip()

    def verify_target(self):
        name = self._checked("emu", "avd", "name").splitlines()[0]
        if name not in QA_AVDS:
            raise RuntimeError("Use a dedicated QA AVD. Everyday demo AVDs are preserved")
        user = self._checked("shell", "am", "get-current-user")
        boot = self._checked("shell", "cat", "/proc/sys/kernel/random/boot_id")
        if not user.isdigit() or not re.fullmatch(r"[a-f0-9-]{36}", boot):
            raise RuntimeError("QA user/boot identity is unavailable")
        identity = (name, user, boot)
        if self.identity is not None and identity != self.identity:
            raise RuntimeError("QA target changed during this run; cleanup refused")
        self.identity = identity
        return user

    def acquire_clean(self):
        user = self.verify_target()
        self._checked("shell", "run-as", "com.lifelink119.app", "--user", user, "pwd")
        for name, keys in PRIVATE_KEYS.items():
            result = self._call("shell", "run-as", "com.lifelink119.app", "--user", user,
                                "cat", "shared_prefs/" + name + ".xml")
            if result.returncode:
                if b"No such file or directory" in result.stderr:
                    continue
                raise RuntimeError("Private storage inspection failed; cleanup refused")
            try:
                root = ET.fromstring(result.stdout)
            except ET.ParseError as error:
                raise RuntimeError("Unrecognized private storage; cleanup refused") from error
            if root.tag != "map" or any(node.get("name") in keys for node in root):
                raise RuntimeError("Existing PIN/profile/medical data found; preserved without changes")
        self.acquired = True
        return self

    def before_cleanup(self):
        if not self.acquired:
            raise RuntimeError("Cleanup requires a clean QA guard acquired before testing")
        self.verify_target()

    def microphone_for_test(self):
        """Grant only on the already-acquired QA target; return an exact permission restore action."""
        if not self.acquired:
            raise RuntimeError("Acquire a clean QA guard before changing a permission")
        user = self.verify_target()
        raw = self._checked('shell', 'dumpsys', 'package', 'com.lifelink119.app')
        section = re.search(r'(?ms)^\s*User '+user+r':.*?(?=^\s*User \d+:|\Z)', raw)
        if not section:
            raise RuntimeError('QA user permission state unavailable')
        granted = 'android.permission.RECORD_AUDIO: granted=true' in section.group(0)
        self._checked('shell', 'pm', 'grant', '--user', user, 'com.lifelink119.app', 'android.permission.RECORD_AUDIO')
        def restore():
            self.verify_target()
            if not granted:
                self._checked('shell', 'pm', 'revoke', '--user', user, 'com.lifelink119.app', 'android.permission.RECORD_AUDIO')
        return restore


def android_sdk():
    import os
    from pathlib import Path
    return Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or
                str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
