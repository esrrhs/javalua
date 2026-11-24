#!/usr/bin/env python3
"""Regression checks for the extension gate's fail-closed infrastructure (no compiler/network)."""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import check_c_extension_gate as gate
import prepare_c_extensions as prepare


class GateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.xml = Path(self.tmp.name) / "report.xml"
        self.suite = ET.Element("testsuite", name="com.github.esrrhs.javalua.RealExtensionCompatibilityIT",
                                tests="60", skipped="0", failures="0", errors="0")
        for i in range(60):
            ET.SubElement(self.suite, "testcase", name=str(i))

    def check(self):
        ET.ElementTree(self.suite).write(self.xml)
        with contextlib.redirect_stdout(io.StringIO()):
            gate.check(self.xml)

    def test_complete_pass(self):
        self.check()

    def test_missing_report_fails(self):
        with self.assertRaises(OSError):
            gate.check(self.xml)

    def test_partial_factory_fails(self):
        self.suite.remove(self.suite[-1])
        with self.assertRaises(ValueError):
            self.check()

    def test_skip_fails(self):
        ET.SubElement(self.suite[0], "skipped")
        with self.assertRaises(ValueError):
            self.check()

    def test_failure_fails(self):
        ET.SubElement(self.suite[0], "failure")
        with self.assertRaises(ValueError):
            self.check()

    def test_metadata_error_fails(self):
        self.suite.set("errors", "1")
        with self.assertRaises(ValueError):
            self.check()

    def test_wrong_suite_fails(self):
        self.suite.set("name", "SomeOtherIT")
        with self.assertRaises(ValueError):
            self.check()


class PreparationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        stock = self.root / "target/lua53/src"
        stock.mkdir(parents=True)
        (stock / "lua.h").touch()
        (stock / "lua").touch()
        for name, value in (("ROOT", self.root), ("DEST", self.root / "modules")):
            context = patch.object(prepare, name, value)
            context.start()
            self.addCleanup(context.stop)

    def test_pinned_archives_and_six_dual_header_compiles(self):
        with patch.object(prepare.subprocess, "check_output", return_value=b"Lua 5.3.4 oracle"), \
             patch.object(prepare.subprocess, "run") as compiler, \
             contextlib.redirect_stdout(io.StringIO()):
            prepare.main()
        self.assertEqual(6, compiler.call_count)
        includes = [call.args[0][call.args[0].index("-I") + 1] for call in compiler.call_args_list]
        self.assertEqual(3, includes.count(str(self.root / "native/lua")))
        self.assertEqual(3, includes.count(str(self.root / "target/lua53/src")))

    def test_compile_failure_is_fatal(self):
        with patch.object(prepare.subprocess, "check_output", return_value=b"Lua 5.3.4 oracle"), \
             patch.object(prepare.subprocess, "run", side_effect=subprocess.CalledProcessError(1, "cc")), \
             contextlib.redirect_stdout(io.StringIO()), \
             self.assertRaises(subprocess.CalledProcessError):
            prepare.main()

    def test_wrong_oracle_is_fatal(self):
        with patch.object(prepare.subprocess, "check_output", return_value=b"Lua 5.4.7 oracle"), \
             contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
            prepare.main()

    def test_corrupt_archive_is_fatal_before_compiler(self):
        fixtures = self.root / "fixtures"
        (fixtures / "upstream").mkdir(parents=True)
        (fixtures / "upstream/bad.tar.gz").write_bytes(b"corrupt")
        (fixtures / "manifest.json").write_text(json.dumps({"modules": [
            {"archive": "bad.tar.gz", "sha256": "0" * 64}]}))
        with patch.object(prepare, "FIXTURES", fixtures), \
             patch.object(prepare.subprocess, "run") as compiler, self.assertRaises(SystemExit):
            prepare.main()
        compiler.assert_not_called()


if __name__ == "__main__":
    unittest.main()
