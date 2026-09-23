# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""Public boundary guard: every rule fires on a synthetic sample, and clean
text passes. No private term appears here: the private digest tables are
swapped for digests of synthetic tokens during the test.

Run: python3 python/tests/test_public_boundary.py"""
import hashlib
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import public_boundary as PB  # noqa: E402

# Samples of forbidden shapes are split with "|" and joined at run time, so the
# guard scans this file clean with no allowlist entry (an allowlist here would
# also hide a real leak added to this file later).
J = lambda s: s.replace("|", "")  # noqa: E731

H = lambda s: hashlib.sha256(s.encode()).hexdigest()  # noqa: E731
FAKE = {"INTERNAL": frozenset({H("zzprivatesys")}), "INTERNAL_EXACT": frozenset({H("ZZCASE")}),
        "USER_HOST": frozenset({H("zzuser"), H("zz-host-name")}),
        "DEVICES": frozenset({H("zzserial9x7")}), "PERSONAL_EMAILS": frozenset({H("someone@example.org")})}


def rules(text):
    return {r for r, _ in PB.scan_text(text)}


class Rules(unittest.TestCase):

    def setUp(self):
        self.p = [patch.object(PB, k, v) for k, v in FAKE.items()]
        for x in self.p:
            x.start()

    def tearDown(self):
        for x in self.p:
            x.stop()

    def test_each_rule_fires(self):
        cases = {
            "PB-001": ["see the zzprivatesys notes", "the ZZCASE plane", "zzprivatesys-data repo"],
            "PB-002": [J("open /Us|ers/someone/x.log"), J("cd /ho|me/alice/work/"), J(r"C:\Us|ers\bob\file")],
            "PB-003": ["user zzuser logged in", J("host Mac-zz-host-name.lo|cal"), J("ping my-box.lo|cal")],
            "PB-004": ["serial zzserial9x7", J("adb -s ABCDEF|123456 install"), J("ro.serial|no=ABCDEF123456")],
            "PB-005": [J("token gh|p_") + "a" * 36, J("-----BEGIN RSA PRIV|ATE KEY-----"),
                       J("api|_key = '") + "b" * 24 + "'"],
            "PB-006": ["https://github.com/" + PB.OWNER + "/secret-thing"],
            "PB-006W": ["https://github.com/" + PB.OWNER + "/a3ui-web"],
            "PB-008": ["Author: X <someone@example.org>"],
        }
        for rule, samples in cases.items():
            for s in samples:
                with self.subTest(rule=rule, sample=s):
                    self.assertIn(rule, rules(s))

    def test_clean_text_passes(self):
        for s in ["CHANNEL_OUT_MONO and a mono raster", "https://github.com/" + PB.OWNER + "/a3-go",
                  "adb -s <redacted-device-id> install", "the <home> directory", "/home/runner/work/a3",
                  "localhost:8080", "noreply@anthropic.com", "Valerio signed the review"]:
            with self.subTest(s):
                self.assertEqual(rules(s) - {"PB-006W"}, set())

    def test_case_exact_terms_ignore_ordinary_lowercase(self):
        self.assertIn("PB-001", rules("ZZCASE"))
        self.assertNotIn("PB-001", rules("zzcase"))

    def test_forbidden_files(self):
        for path in [".env", "config/.env.local", "k.pem", "data/x.sqlite", ".mcp.json",
                     "docs/PRIVATE_MAPPING.md", ".codex/config.toml", "local.properties"]:
            with self.subTest(path):
                self.assertIn("PB-007", {r for r, _ in PB.scan_path(path)})
        self.assertEqual(PB.scan_path("tools/readme_gate.py"), [])

    def test_self_exemption_is_path_exact(self):
        self.assertTrue(PB._self_exempt("tools/public_boundary.py", "PB-004"))
        self.assertFalse(PB._self_exempt("docs/public_boundary.py", "PB-004"))
        self.assertFalse(PB._self_exempt("tools/public_boundary.py", "PB-005"))

    def test_sha_allow_needs_hex_prefix(self):
        rules_ = [("PB-008", "abcdef1234")]
        self.assertTrue(PB._allowed(rules_, "PB-008", "abcdef1"))
        self.assertTrue(PB._allowed(rules_, "PB-008", "abcdef1234567890"))
        self.assertFalse(PB._allowed(rules_, "PB-008", "abc"))          # too short
        self.assertFalse(PB._allowed(rules_, "PB-008", "a"))            # a path named "a"
        self.assertFalse(PB._allowed(rules_, "PB-002", "abcdef1234"))   # other rule


class OnRepository(unittest.TestCase):
    """End to end on a throwaway git repository."""

    def git(self, *a, env=None):
        return subprocess.run(["git", "-C", self.d, *a], capture_output=True, text=True, env=env)

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = self.tmp.name
        self.env = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@users.noreply.github.com",
                        GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@users.noreply.github.com")
        self.git("init", "-q")
        Path(self.d, "ok.md").write_text("clean\n")
        self.git("add", ".")
        self.git("commit", "-q", "-m", "clean", env=self.env)

    def tearDown(self):
        self.tmp.cleanup()

    def test_tree_history_staged_and_allowlist(self):
        self.assertEqual(PB.run(Path(self.d), "tree"), 0)
        self.assertEqual(PB.run(Path(self.d), "history"), 0)
        Path(self.d, "leak.md").write_text(J("log at /Us|ers/someone/leak.txt\n"))
        self.git("add", "leak.md")
        self.assertEqual(PB.run(Path(self.d), "staged"), 1)          # pre-commit blocks
        self.git("commit", "-q", "-m", "leak", env=self.env)
        self.assertEqual(PB.run(Path(self.d), "history"), 1)         # CI blocks
        self.assertEqual(PB.run(Path(self.d), "range", "HEAD~1..HEAD"), 1)   # pre-push blocks
        Path(self.d, ".public-boundary-allow").write_text("PB-002 leak.md # synthetic example path\n")
        self.git("add", ".public-boundary-allow")
        self.git("commit", "-q", "-m", "allow", env=self.env)
        self.assertEqual(PB.run(Path(self.d), "tree"), 0)
        Path(self.d, ".public-boundary-allow").write_text("PB-002 leak.md\n")   # no reason
        self.assertEqual(PB.run(Path(self.d), "tree"), 1)


if __name__ == "__main__":
    unittest.main()
