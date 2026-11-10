"""Self-tests: a green Maven exit must not hide a disappeared differential gate."""
import contextlib
import io
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

import check_ci_gates as gates


class GateTests(unittest.TestCase):
    def check(self, mode="native", omit=None, counts=None, skip=None):
        with tempfile.TemporaryDirectory() as tmp:
            names = gates.ALWAYS_REQUIRED + (gates.NATIVE_REQUIRED if mode == "native" else [])
            for name in names:
                if name == omit:
                    continue
                count = (counts or {}).get(name, {"FuzzTest": 3, "GeneratedDiffTest": 64, "UpstreamSuiteTest": 21}.get(name, 1))
                suite = ET.Element("testsuite", name=name, tests=str(count),
                                   skipped=str(int(name == skip)), errors="0", failures="0")
                for i in range(count):
                    case = ET.SubElement(suite, "testcase", name="case" + str(i))
                    if name == skip and i == 0:
                        ET.SubElement(case, "skipped")
                ET.ElementTree(suite).write(Path(tmp, "TEST-" + name + ".xml"))
            args = ["check_ci_gates.py", "1", "--dir=" + tmp]
            if mode == "java":
                args.append("--nonative")
            with patch("sys.argv", args), contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                gates.main()

    def test_both_modes(self):
        self.check()
        self.check("java")

    def test_fuzz_cannot_disappear_in_java_mode(self):
        with self.assertRaises(SystemExit):
            self.check("java", omit="FuzzTest")

    def test_dynamic_factory_cannot_shrink(self):
        with self.assertRaises(SystemExit):
            self.check(counts={"GeneratedDiffTest": 1})

    def test_oracle_skip_is_not_gc_skip(self):
        with self.assertRaises(SystemExit):
            self.check(skip="CodegenTest")

    def test_java_cannot_drop_upstream_semantic_files(self):
        with self.assertRaises(SystemExit):
            self.check("java", counts={"UpstreamSuiteTest": 20})

    def test_native_gate_cannot_disappear(self):
        with self.assertRaises(SystemExit):
            self.check(omit="NativeApiTest")


if __name__ == "__main__":
    unittest.main()
