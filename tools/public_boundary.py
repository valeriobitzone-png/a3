#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 A3 contributors
"""Public boundary guard for the public A3 repositories.

A public repository must not carry: references to the owner's private systems,
local paths, local user names or host names, device identifiers, credentials,
references to private repositories, or internal-only artifacts. This guard is
deterministic, standard library only, and runs the same way pre-commit,
pre-push and in CI. Any finding is PUBLIC_BOUNDARY_FAIL: publication blocked.

    python3 tools/public_boundary.py                    # tracked files at HEAD
    python3 tools/public_boundary.py --staged           # pre-commit: staged content
    python3 tools/public_boundary.py --range A..B       # pre-push: new commits (diff, messages, identities)
    python3 tools/public_boundary.py --history          # every reachable object (CI)
    python3 tools/public_boundary.py --root ../a3-go    # another repository

Private terms are listed only as SHA-256 digests of the token: a guard must not
publish what it guards. Digests of common words can be guessed; they keep the
list out of plain text, they are not encryption.

Exceptions live in `.public-boundary-allow` at the repository root, one per
line: `<RULE> <path-glob-or-commit-sha> # reason`. An exception without a
reason is itself a failure.
"""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import re
import subprocess
import sys
from pathlib import Path

PUBLIC_REPOS = {"a3", "a3-go", "a3-ts"}                      # OWNER_CONFIRMED_PUBLIC
NOT_CONFIRMED = {"a3ui-cli", "a3ui-web", "a3ui-graphics"}      # PUBLIC_NOT_OWNER_CONFIRMED: warn
OWNER = "valeriobitzone-png"

_H = lambda s: hashlib.sha256(s.encode()).hexdigest()  # noqa: E731

# PB-001 internal names of private systems (lowercased token, any case)
INTERNAL = frozenset({
    "eb576f31edf395189e0c85449b3325d77bfddaa3439ad7bc578736049de22200",
    "4f4b638c5e618622e13d7bf6f0266c25f927b9b308a48c14b2ab3f8d1ff42d78",
    "943ba292100b55c31adb8b8419726b7b8ec6b0364ed7fd1425e5aec5b9e0e191",
    "516901d252fcccd41649d0dd708542a8aec25eab67c4f64abee6907951bb2571",
    "fe2a75c2827af1ba4b54300537b793fbb72af54e6728c11406154f82cbca2ee9",
    "d22c1e315c7ffb7f241b07bec6b37212200ada43189b5e7e0ea7ba5131d44b1c",
    "70503ae39c73676d28b2e65794bbec61190ad38db310ab5a32ac81d4d38118c1",
    "e5e3d91d62a668aaf7a62f831813c545c132de94c4633181e452edfff9cf2b05",
    "93d4215b20d1c73626d42fd1262e90506746a9038a4b989b545085d9d2a27186",
    "0c8df122e7ef9eaf4364fd179b1778ba715a861864968724081474c8295d34b6",
    "4d74e9234df06fabcefc9b02e4e4e144dc6f3c63d7f9388055b61d3fae6a93ad",
    "f73daa0eb9c509c31f42a42217f2ec9e867f772e3f23fb098deab3174a666eec",
})
# PB-001 internal names that are ordinary words in lower case: exact case only
INTERNAL_EXACT = frozenset({
    "8e4d22eb5e815ba9c6ea768b45996aac604c77891d6e5ae4c5b6fffe8dbb8cec",
    "8db695521cae2b114595dc442e5ef4a6976c693823b8634d416f6c28723878b6",
    "224ea01e4a299102cf8da1698a931bad291415dcefea7493576c17cf1fa960b9",
})
# PB-003 local user and host names
USER_HOST = frozenset({
    "4f4b638c5e618622e13d7bf6f0266c25f927b9b308a48c14b2ab3f8d1ff42d78",
    "31bfe2b3b4d3f909bcb3be1b059bacc26ae770efdf3e595a27c7f8f73cf74ed2",
})
# PB-004 device identifiers
DEVICES = frozenset({
    "2b89b5c78ebbeb66f6b49092e7c790f53e8a47f6c78c80904dcb35759c2c6b7a",
    "5d07417abd2f3eef176ea0c546abd9b74c011d397aed0dc59cafd2e9ead65d76",
})
# PB-008 personal e-mail addresses (whole address, lowercased)
PERSONAL_EMAILS = frozenset({
    "a92db35fe2767afab37dad5ef413d13cafe53b001e61544be15be3a373a823ba",
})

TOKEN = re.compile(r"(?<![A-Za-z0-9_])[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*(?![A-Za-z0-9_])")
LOCAL_PATH = re.compile(r"/Users/[A-Za-z0-9._-]+/|/home/(?!runner/)[a-z0-9._-]+/|[A-Za-z]:\\{1,2}Users\\{1,2}")
HOST_LIKE = re.compile(r"\b[A-Za-z0-9]+(?:-[A-Za-z0-9]+)+\.local\b")
DEVICE_LIKE = re.compile(r"adb\s+-s\s+(?!<|\$|\")[A-Za-z0-9]{8,}|ro\.serialno\s*[=:]\s*[A-Za-z0-9]{8,}|\bserial\s*=\s*[A-Za-z0-9]{8,}")
SECRETS = re.compile(
    r"gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}"
    r"|-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----|xox[abpr]-[A-Za-z0-9-]{10,}"
    r"|AIza[0-9A-Za-z_-]{35}|(?i:(?:api[_-]?key|secret|token|password)\s*[:=]\s*['\"][A-Za-z0-9/+_=-]{16,}['\"])")
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
REPO_REF = re.compile(r"github\.com[/:]" + OWNER + r"/([A-Za-z0-9._-]+?)(?:\.git)?(?=[/\s)\"'`#]|$)")
FORBIDDEN_FILES = (".env", ".env.*", "*.pem", "*.key", "id_rsa*", "id_ed25519*", "*.sqlite", "*.sqlite3",
                   "*.db", ".mcp.json", "kasetto.yaml", "kasetto.lock", "*.keystore", "*.p12",
                   "*MAPPING.md", "local.properties", "credentials*.json", "*.mobileprovision")
FORBIDDEN_DIRS = (".codex", ".opencode", ".claude", ".cursor")


def _tokens(text: str):
    for m in TOKEN.finditer(text):
        tok = m.group(0)
        parts = tok.split("-")
        for i in range(len(parts)):
            for j in range(i + 1, len(parts) + 1):
                yield "-".join(parts[i:j])


def scan_text(text: str) -> list[tuple[str, str]]:
    """[(rule, detail)] for one text. Details never repeat the matched secret."""
    found = []
    for tok in _tokens(text):
        low = _H(tok.lower())
        if low in INTERNAL or _H(tok) in INTERNAL_EXACT:
            found.append(("PB-001", "internal name of a private system"))
        if low in USER_HOST:
            found.append(("PB-003", "local user or host name"))
        if low in DEVICES:
            found.append(("PB-004", "device identifier"))
    if LOCAL_PATH.search(text):
        found.append(("PB-002", "local filesystem path"))
    if HOST_LIKE.search(text):
        found.append(("PB-003", "local host name (*.local)"))
    if DEVICE_LIKE.search(text):
        found.append(("PB-004", "device serial in a command or field"))
    if SECRETS.search(text):
        found.append(("PB-005", "credential or secret"))
    for m in EMAIL.finditer(text):
        if _H(m.group(0).lower()) in PERSONAL_EMAILS:
            found.append(("PB-008", "personal e-mail address"))
    for m in REPO_REF.finditer(text):
        name = m.group(1)
        if name in NOT_CONFIRMED:
            found.append(("PB-006W", f"reference to {name} (PUBLIC_NOT_OWNER_CONFIRMED)"))
        elif name not in PUBLIC_REPOS:
            found.append(("PB-006", "reference to a repository outside the public A3 set"))
    return sorted(set(found))


def scan_path(path: str) -> list[tuple[str, str]]:
    parts = path.split("/")
    name = parts[-1]
    out = []
    if any(fnmatch.fnmatch(name, pat) for pat in FORBIDDEN_FILES) or any(p in FORBIDDEN_DIRS for p in parts[:-1]):
        out.append(("PB-007", "internal-only or secret-bearing artifact"))
    out += [(r, "in path: " + d) for r, d in scan_text(path)]
    return out


def _git(root: Path, *args: str, binary: bool = False):
    p = subprocess.run(["git", "-C", str(root), *args], capture_output=True)
    if p.returncode != 0:
        raise SystemExit(f"PUBLIC_BOUNDARY_FAIL: git {' '.join(args)}: {p.stderr.decode(errors='replace').strip()}")
    return p.stdout if binary else p.stdout.decode("utf-8", errors="replace")


def _text(blob: bytes):
    if b"\x00" in blob[:8192]:
        return None
    return blob.decode("utf-8", errors="replace")


def _allow(root: Path):
    f = root / ".public-boundary-allow"
    rules, bad = [], []
    if f.exists():
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            body, _, reason = line.partition("#")
            fields = body.split()
            if len(fields) != 2 or not reason.strip():
                bad.append(f".public-boundary-allow:{n}: needs '<RULE> <glob-or-sha> # reason'")
                continue
            rules.append((fields[0], fields[1]))
    return rules, bad


def _allowed(rules, rule, where):
    """A glob matches a path; a commit/tag sha (full or abbreviated) matches its object."""
    return any(r == rule and (fnmatch.fnmatch(where, g) or _same_object(where, g)) for r, g in rules)


_HEX = re.compile(r"[0-9a-f]{7,40}")


def _same_object(a: str, b: str) -> bool:
    return bool(_HEX.fullmatch(a) and _HEX.fullmatch(b)) and (a.startswith(b) or b.startswith(a))


SELF = "tools/public_boundary.py"


def _self_exempt(where: str, rule: str) -> bool:
    """Only the guard's own file, at its own path, may hold the digest tables."""
    return where == SELF and rule in ("PB-001", "PB-003", "PB-004", "PB-008")


def collect(root: Path, mode: str, rng: str | None):
    """Yields (where, text-or-None, path-or-None)."""
    if mode == "tree":
        for path in _git(root, "ls-files", "-z").split("\0"):
            if path:
                blob = (root / path).read_bytes() if (root / path).is_file() else b""
                yield path, _text(blob), path
    elif mode == "staged":
        for path in _git(root, "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z").split("\0"):
            if path:
                yield path, _text(_git(root, "show", f":{path}", binary=True)), path
    else:
        revs = ["--all"] if mode == "history" else rng.split()
        listed = [l.partition(" ") for l in _git(root, "rev-list", "--objects", *revs).splitlines() if l]
        paths = {oid: path for oid, _, path in listed}
        order = list(paths)
        p = subprocess.run(["git", "-C", str(root), "cat-file", "--batch"],
                           input=("\n".join(order) + "\n").encode(), capture_output=True)
        out, i = p.stdout, 0
        while i < len(out):
            j = out.index(b"\n", i)
            oid, kind, size = out[i:j].decode().split()
            data = out[j + 1:j + 1 + int(size)]
            i = j + 1 + int(size) + 1
            if kind == "blob":
                yield f"{paths[oid] or oid} ({oid[:10]})", _text(data), paths[oid] or None
            elif kind in ("commit", "tag"):
                yield f"{kind} {oid[:10]}", data.decode("utf-8", errors="replace"), None


def run(root: Path, mode: str = "tree", rng: str | None = None) -> int:
    rules, bad = _allow(root)
    fails, warns = list(bad), []
    for where, text, path in collect(root, mode, rng):
        hits = scan_path(path) if path else []
        if text is not None:
            hits += scan_text(text)
        for rule, detail in sorted(set(hits)):
            if _self_exempt(where.split(" (")[0], rule):
                continue      # the guard's own digest tables
            key = where.split(" ")[1] if where.startswith(("commit ", "tag ")) else where.split(" (")[0]
            if _allowed(rules, rule, key):
                continue
            (warns if rule.endswith("W") else fails).append(f"{rule} {where}: {detail}")
    for w in warns:
        print("WARN", w)
    if fails:
        for f in fails:
            print("FAIL", f)
        print(f"PUBLIC_BOUNDARY_FAIL: {len(fails)} finding(s). PUBLICATION BLOCKED.")
        return 1
    print(f"PUBLIC_BOUNDARY_PASS ({mode}{' ' + rng if rng else ''}; {len(warns)} warning(s))")
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--staged", action="store_true")
    g.add_argument("--history", action="store_true")
    g.add_argument("--range")
    a = ap.parse_args(argv)
    mode = "staged" if a.staged else "history" if a.history else "range" if a.range else "tree"
    return run(Path(a.root).resolve(), mode, a.range)


if __name__ == "__main__":
    sys.exit(main())
