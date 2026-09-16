#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 A3 contributors

"""Closeout checks that do not mutate the repository."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SUFFIXES = {".kt", ".kts", ".py", ".sh"}
TEXT_SUFFIXES = SOURCE_SUFFIXES | {".md", ".txt", ".html", ".json"}
SCRUB_TERMS = ("<redacted-user>", "/" + "Users/", "<redacted-host>", "<redacted-device-id>", "<redacted-device-id>")
SCRUB = re.compile("|".join(re.escape(term) for term in SCRUB_TERMS), re.I)
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
        if any(re.search(re.escape(term), text, re.I) for term in ("whats" + "app", "wa" + ".me", "<redacted-user>", "/" + "Users/", "<redacted-host>")):
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
