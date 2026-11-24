#!/usr/bin/env python3
"""Fail if the dedicated real-extension gate was absent, partial, skipped or red."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def check(path):
    suite = ET.parse(path).getroot()
    cases = suite.findall("testcase")
    if not suite.get("name", "").endswith(".RealExtensionCompatibilityIT"):
        raise ValueError("wrong test suite")
    if len(cases) != 60 or int(suite.get("tests", "0")) != 60:
        raise ValueError(f"expected exactly 60 real module comparisons, got {len(cases)}")
    for attr in ("skipped", "failures", "errors"):
        if int(suite.get(attr, "0")) != 0:
            raise ValueError(f"nonzero {attr}")
    if any(c.find(tag) is not None for c in cases for tag in ("skipped", "failure", "error")):
        raise ValueError("skipped or unsuccessful test case")
    print("real C extension gate: 60 executed, 0 failures/errors/skips")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: check_c_extension_gate.py <surefire XML>")
    try:
        check(Path(sys.argv[1]))
    except (OSError, ValueError, ET.ParseError) as exc:
        raise SystemExit(f"real C extension gate FAILED: {exc}") from exc
