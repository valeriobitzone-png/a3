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
CLOSEOUT_SOURCES = {
    "renderers/android-compose/src/main/kotlin/a3/renderers/android/compose/MotionRaster.kt",
    "renderers/mac-compose/src/main/kotlin/a3/renderers/mac/compose/MotionRaster.kt",
    "showcase/scripts/record-android-v3.sh",
    "docs/harvest-android-gfxinfo.sh",
    "agent/src/test/kotlin/a3/agent/AgentSurfaceTest.kt",
    "overlay/common/src/test/kotlin/a3/overlay/OverlayCommonTest.kt",
}
TEXT_SUFFIXES = {".kt", ".kts", ".py", ".sh", ".swift", ".java", ".ts", ".md", ".txt", ".html", ".json"}
SCRUB = re.compile(r"<redacted-user>|/Users/|<redacted-host>|<redacted-device-id>|<redacted-device-id>", re.I)
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
        if re.search(r"whatsapp|wa\.me|<redacted-user>|/Users/|<redacted-host>", text, re.I):
            bad.append(str(path.relative_to(ROOT)))
    for path in (ROOT / "review-assets").rglob("*"):
        if path.is_file() and re.search(r"whatsapp|openlibrary|opengraph", path.name, re.I):
            bad.append(str(path.relative_to(ROOT)))
    return sorted(set(bad))


def check_spdx() -> list[str]:
    missing = []
    for relative in sorted(CLOSEOUT_SOURCES):
        path = ROOT / relative
        if path.exists() and "SPDX-License-Identifier:" not in path.read_text(encoding="utf-8"):
            missing.append(relative)
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
    if args.all_source:
        legacy = []
        for path, text in text_files(ROOT):
            if path.suffix.lower() in {".kt", ".kts", ".py", ".sh", ".swift", ".java", ".ts"} and "SPDX-License-Identifier:" not in text:
                legacy.append(str(path.relative_to(ROOT)))
        if legacy:
            failures.append(f"legacy SPDX headers missing: {len(legacy)} source files")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("closeout audit: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
