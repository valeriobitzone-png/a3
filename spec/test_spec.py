#!/usr/bin/env python3
"""SP-001..007 gate for spec/SPEC_A3-EP.md. Does not modify code."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "spec" / "SPEC_A3-EP.md"
WORDS_PER_PAGE = 500
PAGE_LIMIT = 20
KEYWORDS = re.compile(r"\b(MUST NOT|MUST|SHOULD|MAY)\b")
SLOGANS = re.compile(
    r"guardiano|\bguardian\b|desideri|\bdesires\b|\bCouncil\b|\bCritic\b|"
    r"la verità|\bthe truth\b(?! class)",
    re.IGNORECASE,
)
BODY_START = "## 1. Scope"
BODY_END = "## Appendix A."


def fail(code: str, msg: str) -> None:
    print(f"FAIL {code}: {msg}")
    raise SystemExit(1)


def pass_(code: str, detail: str = "") -> None:
    extra = f" {detail}" if detail else ""
    print(f"PASS {code}{extra}")


def spec_text() -> str:
    if not SPEC.exists():
        fail("SP-001", f"missing {SPEC}")
    return SPEC.read_text(encoding="utf-8")


def normative_body(text: str) -> str:
    start = text.find(BODY_START)
    end = text.find(BODY_END)
    if start < 0 or end < 0 or end <= start:
        fail("SP-001", "normative body markers missing")
    return text[start:end]


def strip_fences_tables_headings(text: str) -> str:
    out = []
    in_fence = False
    for line in text.splitlines():
        if line.startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if line.startswith("#"):
            continue
        if line.startswith("|"):
            continue
        if line.startswith("---"):
            continue
        out.append(line)
    return "\n".join(out)


def words(text: str) -> list[str]:
    return re.findall(r"[A-Za-z0-9_.:`*-]+", text)


def sentences(text: str) -> list[str]:
    chunks = re.split(r"(?<=[.!?])\s+", text)
    out = []
    for chunk in chunks:
        s = " ".join(chunk.split())
        if not s:
            continue
        if re.fullmatch(r"[-*`\s]+", s):
            continue
        out.append(s)
    return out


def sp_001(body: str) -> None:
    w = words(strip_fences_tables_headings(body))
    pages = (len(w) + WORDS_PER_PAGE - 1) // WORDS_PER_PAGE
    if pages > PAGE_LIMIT:
        fail("SP-001", f"{pages} pages ({len(w)} words) > {PAGE_LIMIT}")
    pass_("SP-001", f"{len(w)} words, {pages} page(s) ≤ {PAGE_LIMIT}")


def sp_002(text: str, body: str) -> None:
    slogans = SLOGANS.findall(body)
    if slogans:
        fail("SP-002", f"inspirational tokens in body: {slogans}")
    prose = strip_fences_tables_headings(body)
    bad = []
    for s in sentences(prose):
        if not s.endswith((".", "!", "?")):
            continue
        if not KEYWORDS.search(s):
            bad.append(s)
    if bad:
        fail("SP-002", "sentences without MUST/MUST NOT/SHOULD/MAY:\n  - " + "\n  - ".join(bad))
    pass_("SP-002", f"{len(sentences(prose))} normative sentences")


def sp_003(body: str) -> None:
    need = ["t_observe", "source_id", "seq", "lexicographic"]
    missing = [n for n in need if n not in body]
    if missing:
        fail("SP-003", f"missing {missing}")
    if "(t_observe, source_id, seq)" not in body:
        fail("SP-003", "order triple missing")
    if "tie-break" not in body and "tie break" not in body:
        fail("SP-003", "tie-break missing")
    pass_("SP-003")


def sp_004(body: str) -> None:
    if "MUST NOT fold the history" not in body and "MUST NOT fold history" not in body:
        fail("SP-004", "history MUST NOT fold missing")
    if "canonical projection" not in body:
        fail("SP-004", "canonical projection missing")
    if "(min, ⊤)" not in body and "(min, ⊤)" not in body:
        if "min, ⊤" not in body and "(min," not in body:
            fail("SP-004", "confidence monoid missing")
    if "MUST be defined only on" not in body:
        fail("SP-004", "fold exclusive scope missing")
    pass_("SP-004")


def sp_005(body: str) -> None:
    types = [
        "io.a3ep.belief.admitted",
        "io.a3ep.action.authorized",
        "io.a3ep.env.postcondition",
        "a3.belief.admitted",
    ]
    missing = [t for t in types if t not in body]
    if missing:
        fail("SP-005", f"registry missing {missing}")
    if "R3" not in body or "lock v2" not in body:
        fail("SP-005", "R3 lock v2 rename missing")
    lock = ROOT / "core/envelope/src/test/resources/envelope-event.json"
    if lock.exists() and "a3.belief.admitted" not in lock.read_text(encoding="utf-8"):
        fail("SP-005", "lock v1 type drifted from spec")
    pass_("SP-005")


def sp_006(body: str) -> None:
    if "strict subset" not in body:
        fail("SP-006", "CORE ⊂ spec not declared")
    for item in ("envelope", "truth", "ordering", "confidence", "postcondition"):
        if item not in body.lower():
            fail("SP-006", f"CORE missing {item}")
    for ext in ("overlay", "sensory", "agent"):
        if ext not in body.lower():
            fail("SP-006", f"extension {ext} not listed")
    if "outside CORE" not in body:
        fail("SP-006", "extensions not excluded from CORE")
    pass_("SP-006")


def sp_007() -> None:
    proc = subprocess.run(
        ["git", "diff", "--stat", "--", ".", ":!spec", ":!REVIEW_SPEC_A3EP.md"],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        fail("SP-007", proc.stderr.strip() or "git diff failed")
    if proc.stdout.strip():
        fail("SP-007", f"code diff not empty:\n{proc.stdout}")
    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    allowed = ("spec/", "REVIEW_SPEC_A3EP.md")
    ignore = (".kotlin/", ".DS_Store")
    for line in status.stdout.splitlines():
        if not line.strip():
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        if any(path.startswith(i) for i in ignore):
            continue
        if not any(path == a or path.startswith(a) for a in allowed):
            fail("SP-007", f"unexpected path {line}")
    pass_("SP-007")


def main() -> int:
    text = spec_text()
    body = normative_body(text)
    sp_001(body)
    sp_002(text, body)
    sp_003(body)
    sp_004(body)
    sp_005(body)
    sp_006(body)
    sp_007()
    return 0


if __name__ == "__main__":
    sys.exit(main())
