#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.

from __future__ import annotations

import hashlib
from collections import Counter
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[2]
REPOSITORIES = tuple(WORKSPACE / name for name in ("a3", "a3-ts", "a3-go", "a3ui-web", "a3ui-cli", "a3ui-graphics"))
SOURCE_SUFFIXES = {".kt", ".kts", ".py", ".sh", ".ts", ".tsx", ".go"}
SPDX = b"SPDX-License-Identifier:"
HEADERS = {
    ".kt": b"// SPDX-License-Identifier: Apache-2.0\n// Part of the A3 universe. See LICENSE.\n",
    ".kts": b"// SPDX-License-Identifier: Apache-2.0\n// Part of the A3 universe. See LICENSE.\n",
    ".go": b"// SPDX-License-Identifier: Apache-2.0\n// Part of the A3 universe. See LICENSE.\n",
    ".ts": b"// SPDX-License-Identifier: Apache-2.0\n// Part of the A3 universe. See LICENSE.\n",
    ".tsx": b"// SPDX-License-Identifier: Apache-2.0\n// Part of the A3 universe. See LICENSE.\n",
    ".py": b"# SPDX-License-Identifier: Apache-2.0\n# Part of the A3 universe. See LICENSE.\n",
    ".sh": b"# SPDX-License-Identifier: Apache-2.0\n# Part of the A3 universe. See LICENSE.\n",
}


def excluded(path: Path) -> bool:
    parts = path.parts
    if any(part in {".git", ".gradle", ".kotlin", "build", "node_modules"} for part in parts):
        return True
    rel = path.relative_to(next(root for root in REPOSITORIES if path.is_relative_to(root))).as_posix()
    return rel.startswith("conformance/vectors/") or rel.startswith("conformance/a3ui/fixtures/")


def insertion_offset(data: bytes) -> int:
    lines = data.splitlines(keepends=True)
    if lines and lines[0].startswith(b"#!"):
        return len(lines[0])
    if lines and b"coding" in lines[0].lower() and b"=" in lines[0]:
        return len(lines[0])
    if len(lines) > 1 and b"coding" in lines[1].lower() and b"=" in lines[1]:
        return len(lines[0]) + len(lines[1])
    return 0


def apply(path: Path) -> bool:
    before = path.read_bytes()
    if SPDX in before[:800]:
        return False
    header = HEADERS[path.suffix]
    offset = insertion_offset(before)
    body = before[offset:]
    digest = hashlib.sha256(body).digest()
    after = before[:offset] + header + body
    if hashlib.sha256(after[offset + len(header):]).digest() != digest:
        raise RuntimeError(f"content hash changed before write: {path}")
    path.write_bytes(after)
    if hashlib.sha256(path.read_bytes()[offset + len(header):]).digest() != digest:
        raise RuntimeError(f"content hash changed after write: {path}")
    return True


def main() -> int:
    counts: Counter[str] = Counter()
    changed: Counter[str] = Counter()
    for root in REPOSITORIES:
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.suffix not in SOURCE_SUFFIXES or excluded(path):
                continue
            key = f"{root.name}:{path.suffix}"
            counts[key] += 1
            if apply(path):
                changed[key] += 1
    print("scanned=" + str(sum(counts.values())) + " changed=" + str(sum(changed.values())))
    for key in sorted(counts):
        print(f"{key}: scanned={counts[key]} changed={changed[key]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
