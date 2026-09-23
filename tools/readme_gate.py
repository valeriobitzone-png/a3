#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""README gate: every command a stranger copies from README.md runs, literally.

If the README and the code drift apart, this gate goes red. It reads the
README the way a person does, by section, and runs what they would run:

  RG-001  "See it in 2 minutes": the clone line points at the public repo,
          and the demo command in the same block exits 0.
  RG-002  the transcript shown under it is what the demo prints (lines
          containing "..." are elisions and are skipped).
  RG-003  "Use it in your agent": the Python snippet, saved and run exactly
          as the README says (from the a3 folder), prints the output shown.
  RG-004  "Check that it holds": every command exits 0.
  RG-005  every relative link in the README resolves to a file or folder.

Standard library only, Python 3.9+.

    python3 tools/readme_gate.py              # from the repository root
    python3 tools/readme_gate.py --root DIR   # a clone somewhere else
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

PUBLIC_CLONE = "git clone --depth 1 https://github.com/valeriobitzone-png/a3.git"
SELF = "tools/readme_gate.py"


class GateFail(Exception):
    pass


def sections(readme: str) -> dict:
    """{'## heading': [(lang, code), ...]} in document order."""
    out, current = {}, "_preamble"
    out[current] = []
    fence = re.compile(r"^```(\w*)\n(.*?)^```", re.S | re.M)
    pos = 0
    for m in re.finditer(r"^## (.+)$", readme, re.M):
        out[current].extend((f.group(1), f.group(2)) for f in fence.finditer(readme[pos:m.start()]))
        current, pos = m.group(1).strip(), m.end()
        out[current] = []
    out[current].extend((f.group(1), f.group(2)) for f in fence.finditer(readme[pos:]))
    return out


def block(secs: dict, title: str, lang: str, index: int = 0) -> str:
    blocks = [code for (l, code) in secs.get(title, []) if l == lang]
    if len(blocks) <= index:
        raise GateFail(f"README section '{title}' has no {lang} block #{index + 1}")
    return blocks[index]


def commands(code: str) -> list:
    """Shell lines as a person copies them: comments stripped, blanks dropped."""
    out = []
    for line in code.splitlines():
        line = re.sub(r"\s+#.*$", "", line).strip()
        if line and not line.startswith("#"):
            out.append(line)
    return out


def sh(cmd: str, root: Path) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, shell=True, cwd=str(root), capture_output=True, text=True, timeout=300)


def rg_001_002(secs: dict, root: Path) -> None:
    cmds = commands(block(secs, "See it in 2 minutes", "bash"))
    if PUBLIC_CLONE not in cmds:
        raise GateFail(f"RG-001 the clone line is not `{PUBLIC_CLONE}`: {cmds}")
    if "cd a3" not in cmds:
        raise GateFail("RG-001 no `cd a3` after the clone")
    runnable = [c for c in cmds if c not in (PUBLIC_CLONE, "cd a3")]
    if not runnable:
        raise GateFail("RG-001 no command to run after the clone")
    printed = ""
    for c in runnable:
        p = sh(c, root)
        if p.returncode != 0:
            raise GateFail(f"RG-001 `{c}` exited {p.returncode}: {p.stderr.strip()[:300]}")
        printed += p.stdout
    print(f"PASS RG-001 clone line is the public repo; {len(runnable)} command(s) exit 0")

    shown = block(secs, "See it in 2 minutes", "text")
    actual = {l.rstrip() for l in printed.splitlines()}
    missing = [l for l in shown.splitlines()
               if l.strip() and "..." not in l and l.rstrip() not in actual]
    if missing:
        raise GateFail("RG-002 README shows lines the demo does not print:\n  " + "\n  ".join(missing))
    kept = sum(1 for l in shown.splitlines() if l.strip() and "..." not in l)
    print(f"PASS RG-002 {kept} transcript lines match the demo output")


def rg_003(secs: dict, root: Path) -> None:
    code = block(secs, "Use it in your agent", "python")
    shown = block(secs, "Use it in your agent", "text")
    with tempfile.TemporaryDirectory() as t:
        agent = Path(t) / "agent.py"          # same file, run from the a3 folder as instructed
        agent.write_text(code, encoding="utf-8")
        p = sh(f'python3 "{agent}"', root)    # the README says: python3 agent.py
    if p.returncode != 0:
        raise GateFail(f"RG-003 agent.py exited {p.returncode}: {p.stderr.strip()[:300]}")
    if p.stdout.strip() != shown.strip():
        raise GateFail(f"RG-003 agent.py printed:\n{p.stdout}\nREADME shows:\n{shown}")
    print("PASS RG-003 agent.py snippet runs and prints what the README shows")


def rg_004(secs: dict, root: Path) -> None:
    cmds = [c for c in commands(block(secs, "Check that it holds", "bash")) if SELF not in c]
    for c in cmds:
        p = sh(c, root)
        if p.returncode != 0:
            raise GateFail(f"RG-004 `{c}` exited {p.returncode}: {(p.stdout + p.stderr)[-400:]}")
    print(f"PASS RG-004 {len(cmds)} check command(s) exit 0")


def rg_005(readme: str, root: Path) -> None:
    links = re.findall(r"\]\(([^)#\s]+)(?:#[^)]*)?\)", readme)
    local = [l for l in links if not re.match(r"^[a-z]+:", l)]
    broken = [l for l in local if not (root / l).exists()]
    if broken:
        raise GateFail(f"RG-005 broken relative links: {broken}")
    print(f"PASS RG-005 {len(local)} relative links resolve")


def main(argv: list) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    root = Path(ap.parse_args(argv).root).resolve()
    readme = (root / "README.md").read_text(encoding="utf-8")
    secs = sections(readme)
    try:
        rg_001_002(secs, root)
        rg_003(secs, root)
        rg_004(secs, root)
        rg_005(readme, root)
    except GateFail as e:
        print(f"FAIL {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
