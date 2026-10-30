"""Validate the designed localhost contract; does not start a server."""
import copy
import json
import re
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[1]
FORMATS = FormatChecker()


@FORMATS.checks("date-time", raises=ValueError)
def date_time(value):
    # Keep validation effective even without jsonschema's optional RFC3339 extra.
    if not isinstance(value, str):
        return True
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})", value):
        return False
    return datetime.fromisoformat(value.replace("Z", "+00:00")).utcoffset() is not None


def load(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main():
    request = load("docs/contracts/demo-session-request.schema.json")
    response = load("docs/contracts/demo-session.schema.json")
    for schema in (request, response):
        Draft202012Validator.check_schema(schema)
    request_validator = Draft202012Validator(request, format_checker=FORMATS)
    response_validator = Draft202012Validator(response, format_checker=FORMATS)
    fixtures = load("docs/fixtures/demo-sessions.json")
    for fixture in fixtures:
        response_validator.validate(fixture)
        request_validator.validate({k: v for k, v in fixture.items() if k in request["properties"]})

    cases = []

    def reject(name, edit):
        value = copy.deepcopy(fixtures[1])
        edit(value)
        assert not response_validator.is_valid(value), name
        cases.append(name)

    reject("non-synthetic data", lambda x: x.update(syntheticOnly=False))
    reject("external video URL", lambda x: x.update(videoLink="https://example.invalid/video"))
    reject("unknown personal field", lambda x: x.update(phone="FAKE-PHONE"))
    reject("VIN", lambda x: x["vehicle"].update(vin="FAKE-VIN"))
    reject("GPS", lambda x: x["location"].update(latitude=0, longitude=0))
    reject("location without consent", lambda x: x["consent"].update(location=False))
    reject("vehicle without consent", lambda x: x["consent"].update(vehicle=False))
    reject("video without consent", lambda x: x["consent"].update(video=False))
    reject("agency mismatch", lambda x: x.update(agency=fixtures[2]["agency"]))
    reject("non-demo ID", lambda x: x.update(id="REAL-0001"))
    reject("invalid timestamp", lambda x: x.update(createdAt="yesterday"))
    report = {"schemas": 2, "positiveResponseFixtures": len(fixtures),
              "positiveRequests": len(fixtures), "rejectedCases": cases, "result": "PASS"}
    output = ROOT / "verification/contract-validation.json"
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
