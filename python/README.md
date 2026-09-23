# a3ep (Python)

The A3-EP truth law for Python agents: truth class, provenance, promotion, and the rejects that keep an agent from believing its own receipts. Standard library only, Python 3.9+.

```python
from a3ep import Receipt, Verification, admit

admit("FACT", basis=Receipt.of({"ok": True}))                  # A3Reject CF-001
admit("OBSERVATION", basis=Receipt.of({"ok": True}))           # OBSERVATION
admit("FACT", basis=Verification("ver-1", "obs-1", "REAL"))    # FACT
```

Scope: the truth element of CORE (SPEC_A3-EP sections 2–4). Envelope, ordering and confidence are implemented in Kotlin (`core/`), Go (a3-go) and TypeScript (a3-ts).

Install: not published on PyPI. Use it from the repository (`sys.path.insert(0, "path/to/a3/python")`) or copy the `a3ep/` folder. Tests: `python3 python/tests/test_a3ep.py`.
