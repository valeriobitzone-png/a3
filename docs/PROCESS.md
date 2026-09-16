# A3 slice process

This repository is governed by the repository state and executable tests. When a document and the code disagree, the code and tests win; update the document rather than bypassing the contract.

## Opening a slice

Copy this template into the issue or working note before editing:

```text
Slice name:
Objective:
Owner:
Paths unfrozen:
Paths frozen:
Numbered gates:
Review path:
Local tag:
Push: forbidden until explicit publication approval
Known divergences:
```

A slice has one objective. The implementation consumes the normative spec; it does not silently create a competing protocol. A producer proposes data and a renderer consumes it. UNKNOWN remains a valid first-class result; receipts, sandboxes, and model output do not become FACT by implication. Every generated artefact has a source and lineage.

## Required sequence

1. Inspect branch, status, tags, spec, and existing tests.
2. Declare the paths that are unfrozen. Every other path is frozen.
3. Write or update numbered tests before broad implementation changes.
4. Make the smallest implementation change that satisfies the contract.
5. Add a `docs/REVIEW_<SLICE>.md` with a table mapping every gate to evidence and result.
6. Run the numbered gates and the required regression suite.
7. Check `git diff --check`, the frozen-path diff, and the review-assets policy.
8. Commit only the slice paths and review. Create the local tag only after all gates are green.
9. Do not push. Publication is a separate, manual act by the maintainer.

## Freeze rule

A frozen path must have an empty diff and no untracked files introduced by the slice. Tests must not be weakened, deleted, or made conditional merely to pass. Generated screenshots and recordings may be rewritten only when the slice explicitly owns `review-assets/`; such rewrites must be listed in the REVIEW and must not contain personal data.

## Review template

```markdown
# REVIEW_<SLICE>

## Scope
- Objective:
- Unfrozen paths:
- Frozen paths:
- Spec consumed:

## Audit
| ID | Invariant | Command/test | Evidence | Result |
|---|---|---|---|---|
| XX-001 | ... | ... | ... | PASS/FAIL |

## Divergences

Each divergence names the implementation that is wrong, the normative rule, and the evidence. Divergences are declared; they are never silently reconciled.

## Gate output

Paste the relevant command names and exit status. State any environment limitation honestly.

## Delivery
- Commit:
- Local tag:
- Push: none
```

## Divergences and evidence

Compose, Web Components, CLI, and future consumers may differ in chrome or platform capability. They must agree on protocol semantics for the same fixture. A difference must be recorded with its owner and reason; do not normalize outputs in a way that hides the disagreement. A test result is evidence for the exact environment and fixture used, not a universal product claim.
