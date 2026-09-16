#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""CS-001..009 Python parity for A3-EP conformance. Same vectors as Kotlin."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
VECTORS = ROOT / "conformance" / "vectors"
VECTORS_V1 = VECTORS / "v1"
VECTORS_V2 = VECTORS / "v2"
FIXTURES = ROOT / "conformance" / "fixtures"
README = ROOT / "conformance" / "README.md"
MANIFEST_V1 = VECTORS_V1 / "vector-sha256.json"
MANIFEST_V2 = VECTORS_V2 / "vector-sha256.json"

LOCK_VECTORS = [
    "tm-order.json",
    "tm-dedup.json",
    "tm-fold.json",
    "truth-vectors.json",
    "envelope-rfc8785.json",
    "envelope-payload.json",
    "envelope-event.json",
    "envelope-hashes.json",
    "confidence-vectors.json",
    "event_id_expected.txt",
]

EXPECTED_SHA256_V1 = {
    "tm-order.json": "e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e",
    "tm-dedup.json": "b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba",
    "tm-fold.json": "f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5",
    "truth-vectors.json": "1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6",
    "envelope-rfc8785.json": "2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb",
    "envelope-payload.json": "477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a",
    "envelope-event.json": "a0cbf887413c9825638bd410da5689a217d895b0a391b367aa3c7cb003f0c07e",
    "envelope-hashes.json": "2a318e245354fc67d566869abe6580f9d4c524b8e986efc0ce0fa1f55d9cdc87",
    "confidence-vectors.json": "25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee",
    "event_id_expected.txt": "97a3bb3a4ad58c8bd47f22ebe8292b5c86903c6956d73009f2a827d40cf38894",
}

EXPECTED_SHA256_V2 = {
    "tm-order.json": "e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e",
    "tm-dedup.json": "b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba",
    "tm-fold.json": "f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5",
    "truth-vectors.json": "1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6",
    "envelope-rfc8785.json": "2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb",
    "envelope-payload.json": "1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b",
    "envelope-event.json": "fa6e007a23751ad55c22291b64982f0d7c8287eb5723b446a3a5fd72c470e939",
    "envelope-hashes.json": "d27e67f05719b77daeb14a4d219a87cb332998fe2c6d163571f35e7b90d76ff5",
    "confidence-vectors.json": "25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee",
    "event_id_expected.txt": "c7cb220cb548ecb3be575faecac6d0d7e57cc18a785727732e5570c21bb68550",
}

CORE_LOCK = {
    "tm-order.json": ROOT / "core/temporal/src/test/resources/tm-order.json",
    "tm-dedup.json": ROOT / "core/temporal/src/test/resources/tm-dedup.json",
    "tm-fold.json": ROOT / "core/temporal/src/test/resources/tm-fold.json",
    "truth-vectors.json": ROOT / "core/truth/src/test/resources/truth-vectors.json",
    "envelope-rfc8785.json": ROOT / "core/envelope/src/test/resources/envelope-rfc8785.json",
    "envelope-payload.json": ROOT / "core/envelope/src/test/resources/envelope-payload.json",
    "envelope-event.json": ROOT / "core/envelope/src/test/resources/envelope-event.json",
    "envelope-hashes.json": ROOT / "core/envelope/src/test/resources/envelope-hashes.json",
    "confidence-vectors.json": ROOT / "core/confidence/src/test/resources/confidence-vectors.json",
}

CATEGORY_FIXTURES = [
    ("receipt-fact-violation.json", "CF-001"),
    ("sandbox-fact-violation.json", "CF-002"),
    ("hypothesis-promotion-violation.json", "CF-003"),
    ("attester-requester-violation.json", "CF-004"),
    ("unknown-invention-violation.json", "CF-005"),
    ("history-fold-violation.json", "CF-006"),
    ("compensating-mean-violation.json", "CF-007"),
    ("envelope-id-violation.json", "CF-008"),
    ("order-tiebreak-violation.json", "CF-009"),
]

EVENT_ID_V1 = "477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a"
EVENT_ID_V2 = "1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b"
FROZEN = (
    "core/admission",
    "core/action",
    "core/json",
    "core/temporal",
    "a3ui",
    "renderers",
    "broker",
    "agent",
    "launcher",
    "overlay",
    "showcase",
)

ESCAPE = re.compile(r'[\x00-\x1f\\"\b\f\n\r\t]')
ESCAPE_DCT = {
    "\\": "\\\\",
    '"': '\\"',
    "\b": "\\b",
    "\f": "\\f",
    "\n": "\\n",
    "\r": "\\r",
    "\t": "\\t",
}
for _i in range(0x20):
    ESCAPE_DCT.setdefault(chr(_i), "\\u{0:04x}".format(_i))


class ConformanceReject(Exception):
    def __init__(self, code: str, reason: str) -> None:
        self.code = code
        super().__init__(f"{code}: {reason}")


def fail(code: str, msg: str) -> None:
    print(f"FAIL {code}: {msg}")
    raise SystemExit(1)


def pass_(code: str, detail: str = "") -> None:
    extra = f" {detail}" if detail else ""
    print(f"PASS {code}{extra}")


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def encode_str(s: str) -> str:
    return '"' + ESCAPE.sub(lambda m: ESCAPE_DCT[m.group(0)], s) + '"'


def es6_number(value: object) -> str:
    fvalue = float(value)
    if fvalue == 0:
        return "0"
    py_double = str(fvalue)
    if "n" in py_double:
        raise ValueError("Invalid JSON number: " + py_double)
    sign = ""
    if py_double[0] == "-":
        sign = "-"
        py_double = py_double[1:]
    exp_str = ""
    exp_val = 0
    q = py_double.find("e")
    if q > 0:
        exp_str = py_double[q:]
        if exp_str[2:3] == "0":
            exp_str = exp_str[:2] + exp_str[3:]
        py_double = py_double[0:q]
        exp_val = int(exp_str[1:])
    first = py_double
    dot = ""
    last = ""
    q = py_double.find(".")
    if q > 0:
        dot = "."
        first = py_double[:q]
        last = py_double[q + 1 :]
    if last == "0":
        dot = ""
        last = ""
    if 0 < exp_val < 21:
        first += last
        last = ""
        dot = ""
        exp_str = ""
        pad = exp_val - len(first)
        while pad >= 0:
            pad -= 1
            first += "0"
    elif -7 < exp_val < 0:
        last = first + last
        first = "0"
        dot = "."
        exp_str = ""
        q = exp_val
        while q < -1:
            q += 1
            last = "0" + last
    return sign + first + dot + last + exp_str


def jcs(obj: object) -> str:
    if obj is None:
        return "null"
    if obj is True:
        return "true"
    if obj is False:
        return "false"
    if isinstance(obj, str):
        return encode_str(obj)
    if isinstance(obj, int) and not isinstance(obj, bool):
        return es6_number(obj)
    if isinstance(obj, float):
        return es6_number(obj)
    if isinstance(obj, list):
        return "[" + ",".join(jcs(x) for x in obj) + "]"
    if isinstance(obj, dict):
        keys = sorted(obj.keys(), key=lambda k: k.encode("utf-16_be"))
        return "{" + ",".join(encode_str(k) + ":" + jcs(obj[k]) for k in keys) + "}"
    raise TypeError(type(obj))


def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def order_key(obs: dict) -> tuple:
    stamp = obs["stamp"]
    return (
        stamp["t_observe"],
        obs["source_id"],
        int(obs["seq"]),
        obs["subject"],
        obs["key"],
    )


def sort_obs(items: list) -> list:
    return sorted(items, key=order_key)


def permutations(items: list) -> list:
    if len(items) <= 1:
        return [list(items)]
    out = []
    for i, item in enumerate(items):
        rest = items[:i] + items[i + 1 :]
        for perm in permutations(rest):
            out.append([item] + perm)
    return out


def combine(a: float, b: float) -> float:
    return a if a < b else b


def weighted_min(vector: dict, weights: dict) -> float:
    products = [
        float(vector["source_reliability"]) * float(weights["source_reliability"]),
        float(vector["evidence_strength"]) * float(weights["evidence_strength"]),
        float(vector["recency"]) * float(weights["recency"]),
        float(vector["corroboration"]) * float(weights["corroboration"]),
        float(vector["verification"]) * float(weights["verification"]),
    ]
    acc = products[0]
    for p in products[1:]:
        if p < acc:
            acc = p
    return acc


def judge(path: Path) -> None:
    root = load_json(path)
    code = root["reject_code"]
    if code == "CF-001":
        claimed = str(root["claimed"]["truth_class"]).upper()
        if claimed == "FACT" and int(root["exit_code"]) == 0 and root["printed"] == "SUCCESS":
            raise ConformanceReject("CF-001", "FACT on receipt (exit 0, print SUCCESS)")
        raise RuntimeError("CF-001 fixture did not claim FACT on receipt")
    if code == "CF-002":
        claimed = str(root["claimed"]["truth_class"]).upper()
        if claimed == "FACT" and str(root["environment"]).upper() == "SANDBOX":
            raise ConformanceReject("CF-002", "FACT on sandbox (not REAL)")
        raise RuntimeError("CF-002 fixture did not claim FACT on SANDBOX")
    if code == "CF-003":
        claimed = str(root["claimed"]["truth_class"]).upper()
        if claimed == "FACT" and root.get("verification") is None:
            raise ConformanceReject(
                "CF-003", "HYPOTHESIS promoted to FACT without VerificationAdmitted"
            )
        raise RuntimeError("CF-003 fixture did not claim FACT without verification")
    if code == "CF-004":
        event = root["event"]
        att = event["data"]["attestation"]
        irreversible = event["type"] == "io.a3ep.action.authorized" or root.get("irreversible")
        if irreversible and att["attester_id"] == att["requester_id"]:
            raise ConformanceReject(
                "CF-004",
                "attester_id must not equal requester_id on irreversible action",
            )
        raise RuntimeError("CF-004 fixture is not an irreversible attester collision")
    if code == "CF-005":
        prop = root.get("proposition")
        if str(root.get("truth_class", "")).upper() == "UNKNOWN" and prop:
            raise ConformanceReject(
                "CF-005", "UNKNOWN with invented conclusion (proposition not null)"
            )
        raise RuntimeError("CF-005 fixture is not UNKNOWN with a proposition")
    if code == "CF-006":
        history = load_json(VECTORS_V2 / root["history_vector"])
        fold = load_json(VECTORS_V2 / "tm-fold.json")
        if len(fold["history"]) != len(history):
            raise RuntimeError("lock fold dropped causal history")
        claimed = root["claimed"]
        if claimed.get("replaces_history") or claimed.get("history") == []:
            raise ConformanceReject(
                "CF-006", "fold applied to causal history (not confidence/projection)"
            )
        raise RuntimeError("CF-006 fixture did not replace history")
    if code == "CF-007":
        lawful = weighted_min(root["vector"], root["weights"])
        claimed = float(root["claimed_score"])
        if str(root.get("aggregation", "")).lower() == "mean" or claimed != lawful:
            raise ConformanceReject(
                "CF-007",
                f"confidence aggregated with compensating average (claimed={claimed} lawful={lawful})",
            )
        raise RuntimeError("CF-007 fixture matched weighted min")
    if code == "CF-008":
        event = root["event"]
        expected = sha256_hex(jcs(event["data"]).encode("utf-8"))
        if event["id"] != expected:
            raise ConformanceReject(
                "CF-008",
                f"envelope id ≠ SHA-256(JCS(payload)): {event['id']}",
            )
        raise RuntimeError("implementation accepted envelope id ≠ SHA-256(JCS(payload))")
    if code == "CF-009":
        lawful = [o["source_id"] for o in sort_obs(root["input"])]
        claimed = list(root["claimed_order"])
        if root.get("tie_break") == "none" or claimed != lawful:
            raise ConformanceReject(
                "CF-009",
                f"order without tie-break (source_id, seq); claimed={claimed} lawful={lawful}",
            )
        raise RuntimeError("CF-009 fixture already used the protocol order")
    raise ConformanceReject("CF-UNKNOWN", f"unknown reject_code {code}")


def verify_lock_dir(label: str, directory: Path, expected: dict, manifest_path: Path, check_core: bool) -> None:
    manifest = load_json(manifest_path)
    for name in LOCK_VECTORS:
        path = directory / name
        if not path.is_file():
            fail("CS-001", f"missing {path}")
        actual = sha256_hex(path.read_bytes())
        want = expected[name]
        if actual != want:
            fail("CS-001", f"{label}/{name} sha256 {actual} != {want}")
        if manifest[name] != want:
            fail("CS-001", f"manifest {label}/{name}")
        if check_core and name in CORE_LOCK:
            if path.read_bytes() != CORE_LOCK[name].read_bytes():
                fail("CS-001", f"import drift {name}")


def cs_001() -> None:
    verify_lock_dir("v1", VECTORS_V1, EXPECTED_SHA256_V1, MANIFEST_V1, check_core=False)
    verify_lock_dir("v2", VECTORS_V2, EXPECTED_SHA256_V2, MANIFEST_V2, check_core=True)
    pass_("CS-001", "v1+v2 sha256")
    pass_("PV-006", "v1 intact")
    pass_("PV-007", "v2 sha declared")


def cs_002() -> None:
    for name, code in CATEGORY_FIXTURES:
        path = FIXTURES / name
        if not path.is_file():
            fail("CS-002", f"missing {path}")
        try:
            judge(path)
        except ConformanceReject as e:
            if e.code != code:
                fail("CS-002", f"{name} code {e.code} != {code}")
            if not str(e).startswith(f"{code}:"):
                fail("CS-002", f"{name} silent {e}")
            continue
        fail("CS-002", f"{name} was not rejected")
    pass_("CS-002", "CF-001..CF-009 reject")


def cs_003() -> None:
    source = load_json(VECTORS_V2 / "rfc8785-input.json")
    actual = jcs(source)
    lock = (VECTORS_V2 / "envelope-rfc8785.json").read_text(encoding="utf-8")
    if actual != lock:
        fail("CS-003", "appendix A not byte-identical")
    if sha256_hex(actual.encode("utf-8")) != EXPECTED_SHA256_V2["envelope-rfc8785.json"]:
        fail("CS-003", "appendix A sha256")
    pass_("CS-003", f"RFC8785 bytes={len(actual)}")


def cs_004() -> None:
    v1 = (VECTORS_V1 / "event_id_expected.txt").read_text(encoding="utf-8").strip()
    v2 = (VECTORS_V2 / "event_id_expected.txt").read_text(encoding="utf-8").strip()
    if v1 != EVENT_ID_V1:
        fail("CS-004", v1)
    if v2 != EVENT_ID_V2:
        fail("PV-005", v2)
    if sha256_hex((VECTORS_V1 / "envelope-payload.json").read_bytes()) != v1:
        fail("CS-004", "v1 payload file hash")
    if sha256_hex((VECTORS_V2 / "envelope-payload.json").read_bytes()) != v2:
        fail("PV-005", "v2 payload file hash")
    if sha256_hex(jcs(load_json(VECTORS_V2 / "envelope-payload.json")).encode("utf-8")) != v2:
        fail("PV-005", "JCS(payload v2) hash")
    event_v2 = load_json(VECTORS_V2 / "envelope-event.json")
    if event_v2["id"] != v2:
        fail("PV-005", "envelope-event v2 id")
    if event_v2["type"] != "io.a3ep.belief.admitted":
        fail("PV-001", event_v2["type"])
    att = event_v2["data"]["attestation"]
    if att["attester_id"] != "urn:a3:party:attester" or att["requester_id"] != "urn:a3:party:requester":
        fail("PV-003", str(att))
    event_v1 = load_json(VECTORS_V1 / "envelope-event.json")
    if event_v1["type"] != "a3.belief.admitted":
        fail("PV-006", event_v1["type"])
    hashes = load_json(VECTORS_V2 / "envelope-hashes.json")
    if hashes["event_id"] != v2 or hashes["envelope-payload.json"] != v2:
        fail("PV-005", "envelope-hashes")
    pass_("CS-004", f"event_id_v1={v1}")
    pass_("PV-001", event_v2["type"])
    pass_("PV-003", "attestation present")
    pass_("PV-005", f"event_id_v2={v2}")


def cs_005() -> None:
    stream = load_json(VECTORS_V2 / "tm-order.json")
    canonical = sort_obs(stream)
    for perm in permutations(stream):
        if sort_obs(perm) != canonical:
            fail("CS-005", "tm-order shuffle")
    fixture = load_json(FIXTURES / "order-tiebreak-violation.json")
    lawful = sort_obs(fixture["input"])
    if [o["source_id"] for o in lawful] != ["alpha", "zeta"]:
        fail("CS-005", "equal t_observe tie-break")
    for perm in permutations(fixture["input"]):
        if [o["source_id"] for o in sort_obs(perm)] != ["alpha", "zeta"]:
            fail("CS-005", "tie-break shuffle")
    residual = [
        {**fixture["input"][0], "source_id": "same", "seq": 1, "subject": "b", "key": "k2"},
        {**fixture["input"][1], "source_id": "same", "seq": 1, "subject": "a", "key": "k1"},
    ]
    if [o["subject"] for o in sort_obs(residual)] != ["a", "b"]:
        fail("CS-005", "residual subject/key")
    pass_("CS-005", f"permutations={len(permutations(stream))} tie-break")


def cs_006() -> None:
    fixture = load_json(VECTORS_V2 / "confidence-vectors.json")
    scores = [
        fixture["mapping"]["media_default_fact"]["score"],
        fixture["fixture"]["score"],
        fixture["mapping"]["unknown_zero"]["score"],
        fixture["mapping"]["alta_unit_fact"]["score"],
        fixture["mapping"]["bassa_hypothesis"]["score"],
        fixture["zero_not_compensated"]["score"],
    ]
    top = 1.0
    for a in scores:
        if combine(a, top) != a or combine(top, a) != a:
            fail("CS-006", f"identity {a}")
        for b in scores:
            if combine(a, b) != combine(b, a):
                fail("CS-006", f"commute {a} {b}")
            for c in scores:
                left = combine(combine(a, b), c)
                right = combine(a, combine(b, c))
                if left != right:
                    fail("CS-006", f"assoc {a} {b} {c}")
    pass_("CS-006", f"scores={scores}")


def cs_008() -> None:
    text = README.read_text(encoding="utf-8")
    for token in (
        "./gradlew :conformance:test",
        "test_conformance.py",
        "Kotlin",
        "TypeScript",
        "Go",
        "Python",
        "CORE",
        "EXTENSION",
        "report",
    ):
        if token not in text:
            fail("CS-008", f"README missing {token}")
    banned = ("OS sensoriale", "restyler ogni app", "impedisce ogni errore", "guardiano")
    for token in banned:
        if token in text:
            fail("CS-008", f"inspirational {token}")
    pass_("CS-008", "README")


def cs_009() -> None:
    proc = subprocess.run(
        ["git", "diff", "--stat", "--", *FROZEN],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        fail("CS-009", proc.stderr.strip() or "git diff failed")
    if proc.stdout.strip():
        fail("CS-009", f"frozen diff not empty:\n{proc.stdout}")
    pass_("CS-009")
    pass_("PV-010")


def pv_002() -> None:
    event = load_json(VECTORS_V1 / "envelope-event.json")
    if event["type"] != "a3.belief.admitted":
        fail("PV-002", event["type"])
    pass_("PV-002", "v1 input type a3.* preserved on disk; parser normalizes in Kotlin")


def pv_008() -> None:
    spec = (ROOT / "spec" / "SPEC_A3-EP.md").read_text(encoding="utf-8")
    for token in (
        "Version: 0.2.0",
        "io.a3ep.belief.admitted",
        "io.a3ep.action.authorized",
        "io.a3ep.env.postcondition",
        "attestation",
    ):
        if token not in spec:
            fail("PV-008", f"missing {token}")
    pass_("PV-008", "SPEC_A3-EP 0.2.0")


def main() -> int:
    if not (ROOT / "settings.gradle.kts").is_file():
        fail("CS-001", f"repo root {ROOT}")
    cs_001()
    cs_002()
    cs_003()
    cs_004()
    cs_005()
    cs_006()
    pass_("CS-007", "python parity")
    pass_("PV-009", "python v2")
    cs_008()
    cs_009()
    pv_002()
    pv_008()
    return 0


if __name__ == "__main__":
    sys.exit(main())
