#!/usr/bin/env python3
"""AU-001..010 gate for spec/SPEC_A3UI.md. Does not modify code."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "spec" / "SPEC_A3UI.md"
FIXTURE = ROOT / "spec" / "fixtures" / "au-004-contradicted.json"
WORDS_PER_PAGE = 500
PAGE_LIMIT = 12
GOVERNMENT = (
    "Sopra il lavoro reale, prima del tap, vedi cosa il sistema sa, "
    "da dove, quanto è vecchio, e quale azione è vietata."
)
KEYWORDS = re.compile(r"\b(MUST NOT|MUST|SHOULD|MAY)\b")
SLOGANS = re.compile(
    r"OS sensoriale|restyler ogni app|impedisce ogni errore|"
    r"guardiano|\bguardian\b|desideri|\bCouncil\b|\bCritic\b|\bLLM\b",
    re.IGNORECASE,
)
TUPLE_FIELDS = (
    "oggetto",
    "truth_class",
    "provenance",
    "age",
    "confidence",
    "actions_permitted",
    "actions_forbidden",
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
        fail("AU-001", f"missing {SPEC}")
    return SPEC.read_text(encoding="utf-8")


def first_line(text: str) -> str:
    return text.splitlines()[0].strip() if text.splitlines() else ""


def normative_body(text: str) -> str:
    start = text.find(BODY_START)
    end = text.find(BODY_END)
    if start < 0 or end < 0 or end <= start:
        fail("AU-001", "normative body markers missing")
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


def au_001(text: str, body: str) -> None:
    if first_line(text) != GOVERNMENT:
        fail("AU-001", f"line 1 is not the government sentence: {first_line(text)!r}")
    w = words(strip_fences_tables_headings(body))
    pages = (len(w) + WORDS_PER_PAGE - 1) // WORDS_PER_PAGE
    if pages > PAGE_LIMIT:
        fail("AU-001", f"{pages} pages ({len(w)} words) > {PAGE_LIMIT}")
    pass_("AU-001", f"{len(w)} words, {pages} page(s) ≤ {PAGE_LIMIT}")


def au_002(body: str) -> None:
    slogans = SLOGANS.findall(body)
    if slogans:
        fail("AU-002", f"inspirational tokens in body: {slogans}")
    prose = strip_fences_tables_headings(body)
    bad = []
    for s in sentences(prose):
        if not s.endswith((".", "!", "?")):
            continue
        if not KEYWORDS.search(s):
            bad.append(s)
    if bad:
        fail("AU-002", "sentences without MUST/MUST NOT/SHOULD/MAY:\n  - " + "\n  - ".join(bad))
    pass_("AU-002", f"{len(sentences(prose))} normative sentences")


def au_003(body: str) -> None:
    missing = [f for f in TUPLE_FIELDS if f not in body]
    if missing:
        fail("AU-003", f"tuple missing {missing}")
    if "MUST NOT omit a field" not in body:
        fail("AU-003", "omit-forbidden missing")
    if "explicit placeholder" not in body:
        fail("AU-003", "placeholder law missing")
    pass_("AU-003")


def au_004(body: str) -> None:
    if not FIXTURE.exists():
        fail("AU-004", f"missing {FIXTURE}")
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    if data.get("mark") != "CONTRADICTED":
        fail("AU-004", "fixture mark")
    if data.get("cta_enabled") is not False:
        fail("AU-004", "fixture must disable CTA")
    if data.get("reason_visible") is not True:
        fail("AU-004", "fixture must show reason")
    reason = data.get("reason")
    if not reason or "contradicted" not in str(reason):
        fail("AU-004", "fixture reason missing")
    if reason not in body:
        fail("AU-004", "fixture reason not in spec")
    if "MUST disable the CTA" not in body:
        fail("AU-004", "CONTRADICTED disable missing")
    pass_("AU-004", f"reason={reason}")


def au_005(body: str) -> None:
    need = [
        "MUST start COLLAPSED",
        "pass through",
        "MUST collapse to the pill immediately after user choice plus action dispatch",
        "SHOULD collapse after 20s",
    ]
    missing = [n for n in need if n not in body]
    if missing:
        fail("AU-005", f"missing {missing}")
    pass_("AU-005")


def au_006(body: str) -> None:
    for token in (
        "blur unavailable",
        "haptic Mac limited",
        "permission denied",
        "OpenGraph blocked",
        "MUST NOT crash",
        "MUST NOT stay silent",
    ):
        if token not in body:
            fail("AU-006", f"missing {token}")
    pass_("AU-006")


def au_007(body: str) -> None:
    for profile in ("high-end", "mid", "blur-off", "particles-off"):
        if profile not in body:
            fail("AU-007", f"missing profile {profile}")
    if "minimum profile MUST be blur-off plus particles-off" not in body:
        fail("AU-007", "minimum profile not defined")
    if "MUST NOT jank on the declared mid profile" not in body:
        fail("AU-007", "jank law missing")
    pass_("AU-007")


def au_008(body: str) -> None:
    if "stateDescription" not in body:
        fail("AU-008", "stateDescription missing")
    if "TalkBack and VoiceOver MUST read type plus reason plus the forbidden action" not in body:
        fail("AU-008", "a11y read path missing")
    if "MUST NOT read only \"dimmed button\"" not in body and "MUST NOT read only “dimmed button”" not in body:
        if 'MUST NOT read only "dimmed button"' not in body:
            fail("AU-008", "dimmed-button forbid missing")
    if "Reduced motion MUST zero motion" not in body:
        fail("AU-008", "reduced motion missing")
    pass_("AU-008")


def au_009(body: str) -> None:
    if "Screen capture MUST NOT be the default input" not in body:
        fail("AU-009", "no default capture missing")
    if "Marks MUST remain above glass even when the app underneath is hostile" not in body:
        fail("AU-009", "hostile under-app missing")
    if "MUST NOT expose sensitive surface content to the app underneath" not in body:
        fail("AU-009", "no leak to under-app missing")
    pass_("AU-009")


def au_010() -> None:
    proc = subprocess.run(
        [
            "git",
            "diff",
            "--stat",
            "--",
            ".",
            ":!spec",
            ":!REVIEW_SPEC_A3EP.md",
            ":!REVIEW_SPEC_A3UI.md",
            ":!REVIEW_CONFORMANCE_A3EP.md",
            ":!REVIEW_PROTOCOL_V2.md",
            ":!conformance",
            ":!settings.gradle.kts",
            ":!core/envelope",
        ],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        fail("AU-010", proc.stderr.strip() or "git diff failed")
    if proc.stdout.strip():
        fail("AU-010", f"code diff not empty:\n{proc.stdout}")
    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    allowed = ("spec/", "REVIEW_SPEC_A3EP.md", "REVIEW_SPEC_A3UI.md", "REVIEW_CONFORMANCE_A3EP.md", "REVIEW_PROTOCOL_V2.md", "conformance/", "settings.gradle.kts", "core/envelope/")
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
            fail("AU-010", f"unexpected path {line}")
    pass_("AU-010")


def main() -> int:
    text = spec_text()
    body = normative_body(text)
    au_001(text, body)
    au_002(body)
    au_003(body)
    au_004(body)
    au_005(body)
    au_006(body)
    au_007(body)
    au_008(body)
    au_009(body)
    au_010()
    return 0


if __name__ == "__main__":
    sys.exit(main())
