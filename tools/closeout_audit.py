#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 A3 contributors

"""Closeout checks that do not mutate the repository."""
from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SUFFIXES = {".kt", ".kts", ".py", ".sh"}
TEXT_SUFFIXES = SOURCE_SUFFIXES | {".md", ".txt", ".html", ".json"}
# Private identifiers are kept as SHA-256 digests of the lowercased token, never
# in clear: a scrub list must not publish what it scrubs.
SCRUB_DIGESTS = frozenset({
    "4f4b638c5e618622e13d7bf6f0266c25f927b9b308a48c14b2ab3f8d1ff42d78",
    "31bfe2b3b4d3f909bcb3be1b059bacc26ae770efdf3e595a27c7f8f73cf74ed2",
    "2b89b5c78ebbeb66f6b49092e7c790f53e8a47f6c78c80904dcb35759c2c6b7a",
    "5d07417abd2f3eef176ea0c546abd9b74c011d397aed0dc59cafd2e9ead65d76",
})


def _scrub_hit(text: str) -> bool:
    if "/users/" in text.lower():
        return True
    for token in re.findall(r"[A-Za-z0-9-]+", text):
        parts = token.lower().split("-")
        for i in range(len(parts)):
            for j in range(i + 1, len(parts) + 1):
                if hashlib.sha256("-".join(parts[i:j]).encode()).hexdigest() in SCRUB_DIGESTS:
                    return True
    return False


class _Scrub:
    search = staticmethod(_scrub_hit)


SCRUB = _Scrub()
OVERCLAIM = re.compile(r"OS sensoriale|restyler|impedisce ogni errore|guardiano", re.I)


def text_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        if any(part in {".git", ".gradle", "build", ".kotlin"} for part in path.parts):
            continue
        try:
            yield path, path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue


def check_scrub() -> list[str]:
    return [
        str(path.relative_to(ROOT))
        for path, text in text_files(ROOT)
        if path != Path(__file__) and SCRUB.search(text)
    ]


def check_assets() -> list[str]:
    bad = []
    for path, text in text_files(ROOT / "review-assets"):
        if _scrub_hit(text) or any(re.search(re.escape(term), text, re.I) for term in ("whats" + "app", "wa" + ".me")):
            bad.append(str(path.relative_to(ROOT)))
    for path in (ROOT / "review-assets").rglob("*"):
        if path.is_file() and re.search(r"whatsapp|openlibrary|opengraph", path.name, re.I):
            bad.append(str(path.relative_to(ROOT)))
    return sorted(set(bad))


def source_files():
    for path, _ in text_files(ROOT):
        relative = path.relative_to(ROOT).as_posix()
        if path.suffix in SOURCE_SUFFIXES and not relative.startswith("conformance/vectors/") and not relative.startswith("conformance/a3ui/fixtures/"):
            yield path


def check_spdx() -> list[str]:
    missing = []
    for path in source_files():
        if "SPDX-License-Identifier:" not in path.read_text(encoding="utf-8"):
            missing.append(str(path.relative_to(ROOT)))
    for path, _ in text_files(ROOT / "spec"):
        if path.suffix == ".md" and "SPDX-License-Identifier: CC-BY-4.0" not in path.read_text(encoding="utf-8"):
            missing.append(str(path.relative_to(ROOT)))
    return missing


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--all-source", action="store_true", help="also report legacy files without SPDX headers")
    args = parser.parse_args()
    failures: list[str] = []
    if missing := check_scrub():
        failures.append("scrub: " + ", ".join(missing))
    if missing := check_assets():
        failures.append("review-assets: " + ", ".join(missing))
    required = [ROOT / "LICENSE", ROOT / "NOTICE", ROOT / "spec/LICENSE-CC-BY", ROOT / "docs/PROCESS.md"]
    if any(not path.exists() for path in required):
        failures.append("required license/process file missing")
    if missing := check_spdx():
        failures.append("SPDX: " + ", ".join(missing))
    if OVERCLAIM.search((ROOT / "MANIFESTO.md").read_text(encoding="utf-8")):
        failures.append("manifesto contains an overclaim")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    counts = {}
    for path in source_files():
        counts[path.suffix] = counts.get(path.suffix, 0) + 1
    print("closeout audit: PASS")
    print("source counts: " + ", ".join(f"{key}={counts[key]}" for key in sorted(counts)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
