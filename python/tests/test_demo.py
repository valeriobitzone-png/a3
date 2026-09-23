# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""The receipt-is-not-fact demo is reproducible: same output, byte for byte,
and it exits 0 only when A3 behaved as specified. Run: python3 python/tests/test_demo.py"""
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEMO = ROOT / "examples" / "receipt-is-not-fact"


class Demo(unittest.TestCase):

    def run_demo(self, *args):
        return subprocess.run([sys.executable, str(DEMO / "demo.py"), *args],
                              capture_output=True, text=True, cwd=str(ROOT), timeout=60)

    def test_output_is_the_expected_one(self):
        p = self.run_demo()
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertEqual(p.stdout, (DEMO / "expected_output.txt").read_text(encoding="utf-8"))

    def test_json_tells_the_same_story(self):
        p = self.run_demo("--json")
        self.assertEqual(p.returncode, 0, p.stderr)
        out = json.loads(p.stdout)
        self.assertTrue(out["a3_behaved_as_specified"])
        self.assertEqual(out["run1_naive"]["belief"]["truth_class"], "FACT")
        self.assertEqual(out["run1_naive"]["world"], "outbox empty")
        for run in ("run2_a3_broken_tool", "run3_a3_working_tool"):
            self.assertEqual(out[run]["steps"][0]["code"], "CF-001")
        self.assertEqual(out["run2_a3_broken_tool"]["belief"]["truth_class"], "OBSERVATION")
        self.assertEqual(out["run3_a3_working_tool"]["belief"]["truth_class"], "FACT")

    def test_works_from_any_directory(self):
        p = subprocess.run([sys.executable, str(DEMO / "demo.py")], capture_output=True,
                           text=True, cwd=str(DEMO), timeout=60)
        self.assertEqual(p.returncode, 0, p.stderr)


if __name__ == "__main__":
    unittest.main()
