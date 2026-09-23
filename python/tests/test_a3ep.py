# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""python/a3ep against the shared corpus: lock v2 truth vectors, CF fixtures,
and the receipt forms. Run: python3 python/tests/test_a3ep.py"""
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "python"))

import a3ep  # noqa: E402
from a3ep import A3Reject, Bearer, Receipt, Verification, admit, from_receipt, promote  # noqa: E402

FIXTURES = ROOT / "conformance" / "fixtures"
VECTORS = ROOT / "conformance" / "vectors" / "v2"


def load(p: Path):
    return json.loads(p.read_text(encoding="utf-8"))


class LockVectors(unittest.TestCase):
    """Same bytes as the Kotlin, Go and TypeScript implementations."""

    def test_truth_vectors(self):
        v = load(VECTORS / "truth-vectors.json")
        self.assertEqual(from_receipt({"exit_code": 0, "printed": "SUCCESS"}).to_json(), v["receipt"])
        hyp = Bearer(a3ep.HYPOTHESIS, a3ep.DERIVED_MODEL, "model:slot")
        self.assertEqual(hyp.to_json(), v["hypothesis"])
        real = Verification("ver-1", "obs-verify", "real")
        self.assertEqual(promote(hyp, real).to_json(), v["fact_promoted"])
        self.assertEqual(promote(hyp, Verification("ver-sand", "x", "sandbox")).to_json(), v["sandbox"])


class ReceiptIsNotFact(unittest.TestCase):

    def setUp(self):
        self.doc = load(FIXTURES / "receipt-forms.json")

    def test_every_form_is_observation(self):
        for f in self.doc["forms"]:
            with self.subTest(f["name"]):
                self.assertEqual(from_receipt(f["receipt"]).truth_class, a3ep.OBSERVATION)

    def test_fact_from_any_receipt_is_cf001(self):
        for f in self.doc["forms"]:
            for claimed in self.doc["rule"]["claimed_fact_rejects"]:
                with self.subTest(f["name"], claimed=claimed):
                    with self.assertRaises(A3Reject) as e:
                        admit(claimed, basis=Receipt.of(f["receipt"]))
                    self.assertEqual(e.exception.code, "CF-001")
                    self.assertTrue(str(e.exception).startswith("CF-001: "))

    def test_observation_from_any_receipt_is_admitted(self):
        for f in self.doc["forms"]:
            for claimed in self.doc["rule"]["claimed_observation_admits"]:
                with self.subTest(f["name"], claimed=claimed):
                    self.assertEqual(admit(claimed, basis=Receipt.of(f["receipt"])).truth_class,
                                     a3ep.OBSERVATION)

    def test_original_fixture(self):
        fx = load(FIXTURES / "receipt-fact-violation.json")
        with self.assertRaises(A3Reject) as e:
            admit(fx["claimed"]["truth_class"], basis=Receipt.of(fx))
        self.assertEqual(e.exception.code, "CF-001")


class OtherTruthRejects(unittest.TestCase):

    def test_cf002_sandbox(self):
        fx = load(FIXTURES / "sandbox-fact-violation.json")
        v = Verification(fx["verification_id"], fx["admitted_id"], fx["environment"])
        with self.assertRaises(A3Reject) as e:
            admit(fx["claimed"]["truth_class"], basis=v)
        self.assertEqual(e.exception.code, "CF-002")

    def test_cf003_no_verification(self):
        with self.assertRaises(A3Reject) as e:
            admit("FACT")
        self.assertEqual(e.exception.code, "CF-003")

    def test_cf005_unknown_with_conclusion(self):
        with self.assertRaises(A3Reject) as e:
            admit("UNKNOWN", provenance="INFERRED", proposition="price is 40 EUR")
        self.assertEqual(e.exception.code, "CF-005")
        self.assertEqual(admit("UNKNOWN", provenance="INFERRED").truth_class, a3ep.UNKNOWN)

    def test_real_verification_is_the_only_way_to_fact(self):
        b = admit("FACT", basis=Verification("ver-9", "obs-9", "REAL"))
        self.assertEqual((b.truth_class, b.provenance, b.ref), ("FACT", "OBSERVED_SIGNED", "ver-9"))

    def test_no_silent_defaults(self):
        for bad in (None, "", "BELIEVED"):
            with self.subTest(bad):
                with self.assertRaises(A3Reject) as e:
                    admit(bad)
                self.assertEqual(e.exception.code, "TR-003")
        with self.assertRaises(A3Reject):
            admit("HYPOTHESIS")                       # provenance required

    def test_promote_without_verification_keeps_hypothesis(self):
        h = Bearer(a3ep.HYPOTHESIS, a3ep.DERIVED_MODEL, "m")
        self.assertIs(promote(h, None), h)


if __name__ == "__main__":
    unittest.main()
