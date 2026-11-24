"""Fast harness arithmetic/statistics checks; integration is run.py --smoke."""
import unittest
import subprocess
import sys
import tempfile
from pathlib import Path

from run import describe, expected
from summarize_jfr import summarize


class RunnerTest(unittest.TestCase):
    def test_profile_filters_threads_and_deduplicates_frames(self):
        frame = {"method": {"type": {"name": "com/github/esrrhs/javalua/Vm"}, "name": "execute"}}
        execution = {"type": "jdk.ExecutionSample", "values": {
            "sampledThread": {"javaName": "main"}, "startTime": "2026-11-24T18:00:00+08:00",
            "stackTrace": {"frames": [frame, frame]}}}
        background = {"type": "jdk.ExecutionSample", "values": {
            "sampledThread": {"javaName": "other"}}}
        result = summarize([execution, background])
        self.assertEqual(result["main_thread_event_counts"], {"jdk.ExecutionSample": 1})
        self.assertEqual(result["execution_inclusive_samples"], {"com.github.esrrhs.javalua.Vm.execute": 1})
        self.assertEqual(result["execution_leaf_samples"], {"com.github.esrrhs.javalua.Vm.execute": 1})

    def test_statistics(self):
        self.assertEqual(describe([9, 1, 3, 7, 5]), dict(
            count=5, min=1, p25=3, median=5, p75=7, max=9))
        self.assertEqual(describe([4])["p75"], 4)
        self.assertEqual(describe([2, 4])["p25"], 2.5)

    def test_refuses_evidence_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "existing.json"
            output.write_text("keep me")
            result = subprocess.run([sys.executable, str(Path(__file__).with_name("run.py")),
                                     "--output", str(output)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn("output already exists", result.stderr)
            self.assertEqual(output.read_text(), "keep me")

    def test_rejects_invalid_bounds_before_build(self):
        with tempfile.TemporaryDirectory() as directory:
            for option, value in (("--n", "0"), ("--forks", "0"), ("--warmup-ms", "-1"),
                                  ("--n", "1000001")):
                result = subprocess.run([sys.executable, str(Path(__file__).with_name("run.py")),
                                         "--output", str(Path(directory) / "unused.json"), option, value],
                                        capture_output=True, text=True)
                self.assertEqual(result.returncode, 2)
                self.assertFalse((Path(directory) / "unused.json").exists())

    def test_closed_form_checksums(self):
        for n in (1, 7, 9, 10, 11, 100, 10000):
            self.assertEqual(expected("strings", n), sum(52 + i % 10 for i in range(1, n + 1)))
            self.assertEqual(expected("arithmetic", n), sum(i * 3 for i in range(1, n + 1)))
            self.assertEqual(expected("tables", n), sum(i * 3 for i in range(1, n + 1)))
            self.assertEqual(expected("allocations", n), sum(i + 1 for i in range(1, n + 1)))


if __name__ == "__main__":
    unittest.main()
