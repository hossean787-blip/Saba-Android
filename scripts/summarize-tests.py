#!/usr/bin/env python3
"""Summarize actual JUnit XML evidence; fail if no results exist."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

files = sorted(Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
if not files:
    raise SystemExit("No JUnit XML results found")
totals = dict(tests=0, failures=0, errors=0, skipped=0)
suites = []
for file in files:
    root = ET.parse(file).getroot()
    counts = {key: int(root.get(key, 0)) for key in totals}
    for key, value in counts.items():
        totals[key] += value
    suites.append(dict(name=root.get("name"), **counts))
result = dict(**totals, passed=totals["tests"]-totals["failures"]-totals["errors"]-totals["skipped"], suites=suites)
output = Path("app/build/reports/auth-verification")
output.mkdir(parents=True, exist_ok=True)
(output / "junit-summary.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print("JUNIT_SUMMARY " + json.dumps({key: value for key, value in result.items() if key != "suites"}))
if totals["failures"] or totals["errors"]:
    raise SystemExit(1)
