# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""a3ep: the A3-EP truth law for Python agents (SPEC_A3-EP 0.2.0, sections 2-4).

Standard library only, Python 3.9+. This package implements the truth
element of CORE: truth class, provenance, promotion, and the rejects that
keep an agent from believing its own receipts. It does not implement the
envelope, ordering, or confidence elements; use the Kotlin, Go, or
TypeScript implementations for those.

    from a3ep import Receipt, Verification, admit

    receipt = Receipt.of(tool_result)            # whatever the tool returned
    admit("FACT", basis=receipt)                 # raises A3Reject CF-001
    admit("OBSERVATION", basis=receipt)          # Bearer(OBSERVATION, ...)
    admit("FACT", basis=Verification("ver-1", "obs-1", "REAL"))   # FACT

The one idea: a receipt says the call was answered. It never says the world
changed. Only an independent verification in the REAL environment can.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any, Optional, Union

__all__ = [
    "FACT", "OBSERVATION", "HYPOTHESIS", "UNKNOWN",
    "OBSERVED_SIGNED", "DERIVED_MODEL", "INFERRED", "HUMAN_ADMITTED",
    "REAL", "SANDBOX",
    "A3Reject", "Bearer", "Receipt", "Verification",
    "from_receipt", "promote", "admit",
]
__version__ = "0.2.0a1"

FACT, OBSERVATION, HYPOTHESIS, UNKNOWN = "FACT", "OBSERVATION", "HYPOTHESIS", "UNKNOWN"
TRUTH_CLASSES = (FACT, OBSERVATION, HYPOTHESIS, UNKNOWN)
OBSERVED_SIGNED, DERIVED_MODEL = "OBSERVED_SIGNED", "DERIVED_MODEL"
INFERRED, HUMAN_ADMITTED = "INFERRED", "HUMAN_ADMITTED"
PROVENANCES = (OBSERVED_SIGNED, DERIVED_MODEL, INFERRED, HUMAN_ADMITTED)
REAL, SANDBOX = "REAL", "SANDBOX"


class A3Reject(Exception):
    """An explicit rejection. `code` is the conformance code (CF-00N, TR-00N)."""

    def __init__(self, code: str, reason: str) -> None:
        self.code = code
        self.reason = reason
        super().__init__(f"{code}: {reason}")


def _name(raw: Any, allowed: tuple, what: str) -> str:
    if raw is None or not str(raw).strip():
        raise A3Reject("TR-003", f"missing {what}")        # no silent default (section 3)
    name = str(raw).strip().upper().replace("-", "_")
    if name not in allowed:
        raise A3Reject("TR-003", f"unknown {what} {raw!r}")
    return name


@dataclass(frozen=True)
class Bearer:
    """A proposition's truth class, provenance, and the ref it rests on."""
    truth_class: str
    provenance: str
    ref: Optional[str] = None

    def to_json(self) -> dict:
        out = {"provenance": self.provenance.lower(), "truth_class": self.truth_class.lower()}
        if self.ref:
            out["ref"] = self.ref
        return out


@dataclass(frozen=True)
class Receipt:
    """An execution receipt: what a process, tool, API, or MCP server answered.

    The content is kept for the record and for the ref. It is never read to
    decide a truth class."""
    payload: Any

    @classmethod
    def of(cls, payload: Any) -> "Receipt":
        return payload if isinstance(payload, Receipt) else cls(payload)

    def ref(self) -> str:
        p = self.payload
        if isinstance(p, dict) and isinstance(p.get("exit_code"), int) and "status" not in p:
            label = str(p.get("printed") or "").strip()
            return f"exit:{p['exit_code']}" + (f":{label}" if label else "")
        raw = json.dumps(p, sort_keys=True, separators=(",", ":"), ensure_ascii=False, default=str)
        return "receipt:sha256:" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


@dataclass(frozen=True)
class Verification:
    """VerificationAdmitted: an independent check of the world, and where it ran."""
    verification_id: str
    admitted_id: str
    environment: str

    def __post_init__(self) -> None:
        if not str(self.verification_id).strip():
            raise A3Reject("TR-003", "missing verificationId")
        object.__setattr__(self, "environment",
                           _name(self.environment, (REAL, SANDBOX), "environment"))


def from_receipt(receipt: Any) -> Bearer:
    """Section 3: an execution receipt is OBSERVATION, whatever it contains."""
    return Bearer(OBSERVATION, OBSERVED_SIGNED, Receipt.of(receipt).ref())


def promote(hypothesis: Bearer, verification: Optional[Verification]) -> Bearer:
    """TR-001: HYPOTHESIS becomes FACT only with VerificationAdmitted in REAL.
    Without verification it is unchanged; in SANDBOX it becomes OBSERVATION."""
    if hypothesis.truth_class != HYPOTHESIS:
        raise A3Reject("TR-001", "promote requires HYPOTHESIS")
    if verification is None:
        return hypothesis
    if verification.environment == SANDBOX:
        return Bearer(OBSERVATION, OBSERVED_SIGNED, verification.verification_id)
    return Bearer(FACT, OBSERVED_SIGNED, verification.verification_id)


Basis = Union[Receipt, Verification, None]


def admit(claimed: Any, basis: Basis = None, *, provenance: Any = None,
          proposition: Any = None) -> Bearer:
    """Admit a claimed truth class on a basis, or reject it explicitly.

    basis=Receipt       FACT -> CF-001. Anything else -> OBSERVATION.
    basis=Verification  SANDBOX: FACT -> CF-002, else OBSERVATION.
                        REAL: FACT with provenance OBSERVED_SIGNED.
    basis=None          FACT -> CF-003. UNKNOWN with a proposition -> CF-005.
                        Otherwise the claim, with an explicit provenance.
    """
    cls = _name(claimed, TRUTH_CLASSES, "truth class")
    if isinstance(basis, Receipt):
        if cls == FACT:
            raise A3Reject("CF-001", "FACT from an execution receipt "
                                     "(a receipt is OBSERVATION whatever it contains)")
        return from_receipt(basis)
    if isinstance(basis, Verification):
        if basis.environment == SANDBOX:
            if cls == FACT:
                raise A3Reject("CF-002", "FACT on sandbox (not REAL)")
            return Bearer(OBSERVATION, OBSERVED_SIGNED, basis.verification_id)
        return Bearer(cls, OBSERVED_SIGNED, basis.verification_id)
    if basis is not None:
        raise A3Reject("TR-003", f"unknown basis {type(basis).__name__}")
    if cls == FACT:
        raise A3Reject("CF-003", "FACT without VerificationAdmitted")
    if cls == UNKNOWN and proposition not in (None, ""):
        raise A3Reject("CF-005", "UNKNOWN with invented conclusion (proposition not null)")
    return Bearer(cls, _name(provenance, PROVENANCES, "provenance"))
