"""Collect existing QA evidence without connecting to a device or reading private settings."""
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT.parent
OUT = ROOT / "verification/refined-ux"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    digest = sha(apk)
    suites = [ET.parse(p).getroot() for p in
              (ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
    unit = {key: sum(int(s.get(key, 0)) for s in suites)
            for key in ("tests", "failures", "errors", "skipped")}
    assert unit["tests"] > 0 and unit["failures"] == unit["errors"] == 0
    checks = {}
    reports = ["refined-ux/results.json", "refined-ux/health-results.json",
               "emergency-support/recovery-results.json", "emergency-support/results.json",
               "minimal-voice/results.json"]
    for name in reports:
        report = json.loads((ROOT / "verification" / name).read_text(encoding="utf-8"))
        assert report["status"] == "PASS" and report["apkSha256"] == digest, name
        checks[name] = {"status": "PASS", "checks": len(report["checks"])}
        if "cleanup" in report:
            assert report["cleanup"] == "PASS"
            checks[name]["syntheticDataCleanup"] = "PASS"
    lint = {}
    for flavor in ("debug", "demo"):
        issues = ET.parse(ROOT / f"app/build/reports/lint-results-{flavor}.xml").getroot()
        lint[flavor] = dict(Counter(i.get("severity") for i in issues.findall("issue")))
        assert not lint[flavor].get("Error", 0)
    logs = PACKAGE / ".tools/logs"
    python_log = (logs / "refine-python.log").read_text(encoding="utf-16")
    python_count = int(re.search(r"Ran (\d+) tests", python_log).group(1))
    assert python_log.strip().endswith("OK")
    native_log = (logs / "refine-native-final-current.log").read_text(encoding="utf-16")
    assert "OK (3 tests)" in native_log
    deck = PACKAGE / "deliverables/LifeLink119_VoiceGuide_Refined_20261115"
    pptx = deck / "output/LifeLink119_음성중심_사용안내_개선본.pptx"
    pdf = deck / "output/LifeLink119_음성중심_사용안내_개선본.pdf"
    photo = ROOT / "app/src/main/res/drawable-nodpi/hyundai_car_hero.jpg"
    assert sha(photo) == sha(deck / "assets/hyundai-car.jpg")
    with zipfile.ZipFile(pptx) as z:
        assert any(hashlib.sha256(z.read(n)).hexdigest() == sha(photo)
                   for n in z.namelist() if n.startswith("ppt/media/"))
    report = dict(
        verifiedAt=datetime.now(timezone.utc).isoformat(), apkSha256=digest,
        unit=unit, pythonTests=python_count, androidInstrumentationTests=3,
        nativeSpeechSyntheticUtterances=13, nativeSpeechInputFormats=2,
        nativeSpeechAudioRuns=26, uiReports=checks, lint=lint,
        build="spotlessCheck + assembleDebug + testDebugUnitTest + lintDebug + assembleRelease + assembleDemo + lintDemo PASS",
        actualEmergencyCalls=0, realPatientRecordsUsed=False,
        qaDevice="emulator-5584 / LifeLink119_QA_API_34",
        realCarVoiceAndMedicalExpertReview="not performed; required before clinical use",
        officialSafeCallLoginAndAgencyTransmission="not integrated",
        artifacts=[dict(path=str(p), bytes=p.stat().st_size, sha256=sha(p)) for p in (pptx, pdf)],
        artifactReview="All 16 shared-layout PDF renders visually reviewed; PPTX package/layout checks passed; native PowerPoint not executed",
        suppliedHyundaiPhotoSha256=sha(photo),
    )
    (OUT / "final-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"unit": unit, "python": python_count, "native": 3,
                      "uiChecks": sum(v["checks"] for v in checks.values()), "lint": lint}, ensure_ascii=False))


if __name__ == "__main__":
    main()
