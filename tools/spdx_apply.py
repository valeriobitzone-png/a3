#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.

"""Apply project SPDX headers without changing source content below the header."""
from __future__ import annotations

import hashlib
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SUFFIXES = {".kt", ".kts", ".py", ".sh"}
SPEC_SUFFIX = ".md"
SPDX = "SPDX-License-Identifier:"
APACHE = "// SPDX-License-Identifier: Apache-2.0\n// Part of the A3 universe. See LICENSE.\n"
HASH_MARKER = "# SPDX-License-Identifier: Apache-2.0\n# Part of the A3 universe. See LICENSE.\n"
CC_BY = "<!-- SPDX-License-Identifier: CC-BY-4.0 -->\n"


def relative(path: Path) -> Path:
    return path.relative_to(ROOT)


def excluded(path: Path) -> bool:
    rel = relative(path).as_posix()
    return rel.startswith("conformance/vectors/") or rel.startswith("conformance/a3ui/fixtures/")


def candidates() -> list[Path]:
    paths: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or excluded(path):
            continue
        if any(part in {".git", ".gradle", ".kotlin", "build"} for part in path.parts):
            continue
        if path.suffix in SOURCE_SUFFIXES or (path.suffix == SPEC_SUFFIX and "spec" in relative(path).parts):
            paths.append(path)
    return sorted(paths)


def insertion_point(data: bytes, comment: str) -> tuple[int, bytes]:
    """Return insertion offset and header bytes, preserving shebang/encoding semantics."""
    text = data.decode("utf-8")
    lines = text.splitlines(keepends=True)
    offset = 0
    if lines and lines[0].startswith("#!"):
        offset += len(lines[0].encode("utf-8"))
    # Python's encoding cookie must stay in the first two physical lines.
    if lines and offset == 0 and lines[0].lower().find("coding") >= 0 and "=" in lines[0]:
        offset += len(lines[0].encode("utf-8"))
    elif len(lines) > 1 and lines[1].lower().find("coding") >= 0 and "=" in lines[1]:
        offset += len(lines[0].encode("utf-8")) + len(lines[1].encode("utf-8"))
    return offset, comment.encode("utf-8")


def add_header(path: Path) -> tuple[bool, str]:
    before = path.read_bytes()
    if SPDX.encode("utf-8") in before:
        return False, "already-present"
    if path.suffix == SPEC_SUFFIX:
        offset, header = 0, CC_BY.encode("utf-8")
    elif path.suffix in {".kt", ".kts"}:
        offset, header = 0, APACHE.encode("utf-8")
    else:
        offset, header = insertion_point(before, HASH_MARKER)
    body = before[offset:]
    expected_hash = hashlib.sha256(body).hexdigest()
    after = before[:offset] + header + body
    if hashlib.sha256(after[offset + len(header):]).hexdigest() != expected_hash:
        raise RuntimeError(f"content hash changed while preparing {path}")
    path.write_bytes(after)
    if hashlib.sha256(path.read_bytes()[offset + len(header):]).hexdigest() != expected_hash:
        raise RuntimeError(f"content hash changed after writing {path}")
    return True, "added"


def main() -> int:
    counts: Counter[str] = Counter()
    changed: list[str] = []
    for path in candidates():
        did_change, result = add_header(path)
        counts[path.suffix] += 1
        if did_change:
            changed.append(relative(path).as_posix())
    print(f"scanned={sum(counts.values())} changed={len(changed)}")
    for suffix in sorted(counts):
        print(f"{suffix}: {counts[suffix]} scanned")
    for name in changed:
        print(f"added: {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
