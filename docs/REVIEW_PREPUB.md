# REVIEW_PREPUB

Pre-publication handoff audit for the six sibling repositories. Publication and push remain manual acts of the committer and were not performed by this slice.

## Audit table

| ID | Invariant | Evidence | Result |
|---|---|---|---|
| PP-001 | Personal/private workstation identifiers absent from repository contents | Cross-repository `rg` scrub; real GitHub clone URLs are the declared exception | PASS |
| PP-002 | The former private-consumer mapping is removed and references are generic | `docs/PRIVATE_CONSUMER_MAPPING.md` removed; reviews and graphics docs use “private consumer” or “consumer bridge” | PASS |
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

- Removed `docs/PRIVATE_CONSUMER_MAPPING.md`; no replacement maps a private application concept into A3-EP CORE.
- Reworded historical reviews that contained personal names, workstation paths, device labels, or private-consumer names.
- Replaced extension examples using a private consumer name with the generic `x-consumer-gate` key. The `x-*` namespace contract is unchanged.
- Reworded `a3ui-graphics` manifest and consumer notes to describe a generic adopting consumer.
- GitHub repository URLs remain because they are real distribution URLs explicitly allowed by PP-001; the organization/repository path is not treated as personal content. The Go module/import path `github.com/valeriobitzone-png/a3-go/...` is the build-required canonical module identifier and is declared under the same distribution exception.
- Technical occurrences such as “monoid” and audio channel constants are protocol/API terminology, not private-person references.

## SPDX and locked bytes

A3's idempotent SPDX pass reports `scanned=447 changed=0` on the verification run. Counts: `.kt=371`, `.kts=34`, `.py=7`, `.sh=5`, and 30 CC-BY Markdown files under `spec/`. The family pass reports `scanned=472 changed=52` on its first run and `changed=0` on its second: all `.ts/.tsx/.go` and sibling Python sources are now covered as well. JSON is intentionally excluded because it has no comment syntax; lock vectors and fixtures were not edited.

The v2 lock set and A3UI fixtures were compared byte-for-byte; expected SHA-256 values are recorded below. No source change alters bytes beneath an SPDX header.

| v2 file | SHA-256 |
|---|---|
| `tm-order.json` | `e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e` |
| `tm-dedup.json` | `b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba` |
| `tm-fold.json` | `f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5` |
| `truth-vectors.json` | `1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6` |
| `envelope-rfc8785.json` | `2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb` |
| `envelope-payload.json` | `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b` |
| `envelope-event.json` | `fa6e007a23751ad55c22291b64982f0d7c8287eb5723b446a3a5fd72c470e939` |
| `envelope-hashes.json` | `d27e67f05719b77daeb14a4d219a87cb332998fe2c6d163571f35e7b90d76ff5` |
| `confidence-vectors.json` | `25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee` |

A3UI fixture hashes: `calendar-contradicted.json` `b75fc757b55b4cee3ecf8672f79d0208d6468c685d2324da2f81bab48dc5e61e`; `flight-fact.json` `74da66bf0efd89a318ca3ecd0c74aade7b41e7d4b36f6c108327b20b04ce8493`; `hotel-stale.json` `10868385500e4de0536036c539e70f16d6eef9990f892f964c97dc8eb057e6bf`; `train-pending.json` `f7593a76448802158623bfe326713f77d4d460a9e5eaca24ba988974ae77ce3e4`.


## Provenance and status

Measured: repository scans, source-header audit, cover file hashes/types, test exits, and lock-vector hashes. Deduced: semantic equivalence across independent implementations from the shared lock corpus. Unverified: public adoption, physical Android FPS, representative mid hardware, and downstream visual fidelity.

## Delivery

| Repository | HEAD commit | Tag |
|---|---|---|
| `a3` | `HEAD (tagged)` | `closeout-v1.0` |
| `a3-ts` | `HEAD (tagged)` | `prepub-v1.0` |
| `a3-go` | `HEAD (tagged)` | `prepub-v1.0` |
| `a3ui-web` | `HEAD (tagged)` | `prepub-v1.0` |
| `a3ui-cli` | `HEAD (tagged)` | `prepub-v1.0` |
| `a3ui-graphics` | `HEAD (tagged)` | `prepub-v1.0` |

No push or publication was performed.
