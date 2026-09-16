# REVIEW_PREPUB

Pre-publication handoff audit for the six sibling repositories. Publication and push remain manual acts of the committer and were not performed by this slice.

## Audit table

| ID | Invariant | Evidence | Result |
|---|---|---|---|
| PP-001 | Personal/private workstation identifiers absent from repository contents | Cross-repository `rg` scrub; real GitHub clone URLs are the declared exception | PASS |
| PP-002 | The former private-consumer mapping is removed and references are generic | `docs/MONO_A3_MAPPING.md` removed; reviews and graphics docs use “private consumer” or “consumer bridge” | PASS |
| PP-003 | Declared source scope has SPDX headers | `a3/tools/closeout_audit.py`; 371 `.kt`, 34 `.kts`, 6 `.py`, 5 `.sh`; missing=0 | PASS |
| PP-004 | Every repository has an ad hoc README and in-repo cover | Six `README.md` files and six `docs/assets/cover.png` files | PASS |
| PP-005 | Repository suites pass | A3 Kotlin/Python/spec gates; TS typecheck/tests; Go tests; Web npm gate; CLI pytest; graphics GS/CS gates | PASS |
| PP-006 | Shared lock vectors remain byte-identical | SHA-256 comparison against repository HEAD and declared lock values | PASS |
| PP-007 | This review records scope, evidence, provenance, and exceptions | This file | PASS |

## Repository handoff matrix

| Repository | Role | README | Cover | Local tag |
|---|---|---|---|---|
| `a3` | Protocol, Kotlin A3UI, conformance | PASS | PASS | `closeout-v1.0` |
| `a3-ts` | TypeScript reference implementation | PASS | PASS | `prepub-v1.0` |
| `a3-go` | Go reference implementation | PASS | PASS | `prepub-v1.0` |
| `a3ui-web` | Native Web Components renderer | PASS | PASS | `prepub-v1.0` |
| `a3ui-cli` | Python stdlib textual renderer | PASS | PASS | `prepub-v1.0` |
| `a3ui-graphics` | Renderer-neutral graphics tokens/spec | PASS | PASS | `prepub-v1.0` |

## Scrub inventory

- Removed `docs/MONO_A3_MAPPING.md`; no replacement maps a private application concept into A3-EP CORE.
- Reworded historical reviews that contained personal names, workstation paths, device labels, or private-consumer names.
- Replaced extension examples using a private consumer name with the generic `x-consumer-gate` key. The `x-*` namespace contract is unchanged.
- Reworded `a3ui-graphics` manifest and consumer notes to describe a generic adopting consumer.
- GitHub repository URLs remain because they are real distribution URLs explicitly allowed by PP-001; the organization/repository path is not treated as personal content.
- Technical occurrences such as “monoid” and audio channel constants are protocol/API terminology, not private-person references.

## SPDX and locked bytes

A3's idempotent SPDX pass reports `scanned=446 changed=0` on the verification run. Counts: `.kt=371`, `.kts=34`, `.py=6`, `.sh=5`, and 30 CC-BY Markdown files under `spec/`. JSON is intentionally excluded because it has no comment syntax; lock vectors and fixtures were not edited.

The v2 lock set and A3UI fixtures were compared byte-for-byte; expected SHA-256 values are the values recorded in the A3, TypeScript, and Go reviews. No source change alters bytes beneath an SPDX header.

## Provenance and status

Measured: repository scans, source-header audit, cover file hashes/types, test exits, and lock-vector hashes. Deduced: semantic equivalence across independent implementations from the shared lock corpus. Unverified: public adoption, physical Android FPS, representative mid hardware, and downstream visual fidelity.

No push or publication was performed.
