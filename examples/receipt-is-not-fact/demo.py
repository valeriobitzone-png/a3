#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
"""The tool said "sent". Nothing was sent.

An agent is asked to email an invoice. It calls a send_email tool. The tool
answers {"ok": true, "message_id": ...}. That answer is a receipt: proof
that the call returned, not proof that the email left.

    Run 1  naive agent   believes the receipt, tells the user "sent".
                         The outbox is empty.
    Run 2  with A3       the same belief is refused: CF-001, FACT from an
                         execution receipt. The receipt is kept as
                         OBSERVATION; the agent checks the outbox; nothing
                         there, so it does not say "sent".
    Run 3  with A3       a working tool. The agent checks the outbox, finds
                         the message, and only then records FACT.

Standard library only. Python 3.9+. Nothing leaves your machine: the
"world" is a temporary folder that stands in for the mail server's outbox.

    python3 examples/receipt-is-not-fact/demo.py          # the story
    python3 examples/receipt-is-not-fact/demo.py --json   # machine-readable
"""
from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "python"))

from a3ep import A3Reject, Receipt, Verification, admit  # noqa: E402

CLAIM = "invoice INV-7 was emailed to anna@example.com"


# ── the world: an outbox folder the agent does not control ──────────────
class Outbox:
    def __init__(self, folder: Path) -> None:
        self.folder = folder

    def contains(self, message_id: str) -> bool:
        return (self.folder / f"{message_id}.eml").is_file()


def send_email_broken(outbox: Outbox, to: str, body: str) -> dict:
    """A tool with a bug: it answers ok and writes nothing."""
    return {"ok": True, "message_id": "msg-001"}


def send_email_working(outbox: Outbox, to: str, body: str) -> dict:
    (outbox.folder / "msg-002.eml").write_text(f"To: {to}\n\n{body}\n", encoding="utf-8")
    return {"ok": True, "message_id": "msg-002"}


# ── the agents ──────────────────────────────────────────────────────────
def naive_agent(tool, outbox: Outbox) -> dict:
    receipt = tool(outbox, "anna@example.com", "Invoice INV-7")
    belief = {"claim": CLAIM, "truth_class": "FACT", "because": "tool returned ok"}
    return {"receipt": receipt, "belief": belief, "says": "Done: the invoice was sent."}


def a3_agent(tool, outbox: Outbox) -> dict:
    receipt = tool(outbox, "anna@example.com", "Invoice INV-7")
    steps = []

    # 1. the tempting shortcut, refused
    try:
        admit("FACT", basis=Receipt.of(receipt))
        steps.append({"step": "FACT from receipt", "result": "ADMITTED"})   # never happens
    except A3Reject as e:
        steps.append({"step": "FACT from receipt", "result": "REJECTED", "code": e.code,
                      "reason": e.reason})

    # 2. what the receipt lawfully is
    observed = admit("OBSERVATION", basis=Receipt.of(receipt))
    steps.append({"step": "receipt recorded", "result": observed.truth_class, "ref": observed.ref})

    # 3. check the world, independently of the tool
    found = outbox.contains(receipt["message_id"])
    steps.append({"step": "check outbox", "result": "FOUND" if found else "NOT FOUND"})
    if not found:
        says = "The tool answered ok, but the email is not in the outbox. Not marking it as sent."
        return {"receipt": receipt, "steps": steps,
                "belief": {"claim": CLAIM, "truth_class": observed.truth_class, "ref": observed.ref},
                "says": says}

    verification = Verification(f"ver-{receipt['message_id']}", f"outbox:{receipt['message_id']}", "REAL")
    fact = admit("FACT", basis=verification)
    steps.append({"step": "FACT from REAL verification", "result": fact.truth_class, "ref": fact.ref})
    return {"receipt": receipt, "steps": steps,
            "belief": {"claim": CLAIM, "truth_class": fact.truth_class, "ref": fact.ref},
            "says": "Sent, and verified in the outbox."}


# ── the three runs ──────────────────────────────────────────────────────
def run() -> dict:
    out = {}
    with tempfile.TemporaryDirectory() as tmp:
        outbox = Outbox(Path(tmp))
        naive = naive_agent(send_email_broken, outbox)
        naive["world"] = "outbox empty" if not outbox.contains("msg-001") else "email in outbox"
        out["run1_naive"] = naive
        out["run2_a3_broken_tool"] = a3_agent(send_email_broken, outbox)
        out["run3_a3_working_tool"] = a3_agent(send_email_working, outbox)
    ok = (out["run1_naive"]["belief"]["truth_class"] == "FACT"
          and out["run1_naive"]["world"] == "outbox empty"
          and out["run2_a3_broken_tool"]["steps"][0].get("code") == "CF-001"
          and out["run2_a3_broken_tool"]["belief"]["truth_class"] == "OBSERVATION"
          and out["run3_a3_working_tool"]["steps"][0].get("code") == "CF-001"
          and out["run3_a3_working_tool"]["belief"]["truth_class"] == "FACT")
    out["a3_behaved_as_specified"] = ok
    return out


def show(out: dict) -> str:
    r1, r2, r3 = out["run1_naive"], out["run2_a3_broken_tool"], out["run3_a3_working_tool"]
    lines = [
        "Task: email invoice INV-7 to anna@example.com",
        "",
        "RUN 1 · naive agent · tool has a bug",
        f"  tool answered   {json.dumps(r1['receipt'])}",
        f"  agent believes  FACT: {CLAIM}",
        f"  agent says      \"{r1['says']}\"",
        f"  the world       {r1['world']}   <- the agent believes something false",
        "",
        "RUN 2 · agent with A3 · same buggy tool",
        f"  tool answered   {json.dumps(r2['receipt'])}",
    ]

    def steps(r):
        for s in r["steps"]:
            if s["result"] == "REJECTED":
                lines.append(f"  A3              REJECTED {s['code']}: {s['reason']}")
            elif s["step"] == "receipt recorded":
                lines.append(f"  recorded as     {s['result']} ({s['ref']})")
            elif s["step"] == "check outbox":
                lines.append(f"  check outbox    {s['result']}")
            else:
                lines.append(f"  A3              {s['result']} (ref {s['ref']})")

    steps(r2)
    lines += [f"  agent says      \"{r2['says']}\"", "",
              "RUN 3 · agent with A3 · working tool",
              f"  tool answered   {json.dumps(r3['receipt'])}"]
    steps(r3)
    lines += [f"  agent says      \"{r3['says']}\"", "",
              "A receipt says the call returned. Only a check of the world says it happened.",
              "A3 behaved as specified: " + ("yes" if out["a3_behaved_as_specified"] else "NO")]
    return "\n".join(lines)


def main(argv: list) -> int:
    out = run()
    print(json.dumps(out, indent=2, ensure_ascii=False) if "--json" in argv else show(out))
    return 0 if out["a3_behaved_as_specified"] else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
