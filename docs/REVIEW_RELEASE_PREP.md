# REVIEW_RELEASE_PREP

Release-preparation slice for conversation-trace scrub and GitHub presentation. Protocol behavior, specifications, lock vectors, fixtures, and tests are preserved.

## Audit table

| ID | Invariant | Evidence | Result |
|---|---|---|---|
| SC-001 | Conversational traces removed from mutable documentation and metadata | Six-repository targeted scan; remaining matches are confined to frozen specification/test guard literals | PASS with frozen exceptions |
| SC-002 | Forbidden meta-work files absent | Filename scan for `PIANO_GENERALE*`, `COS*HO_AGGIUNTO_IO*`, and `SPEC*Universo A3*` | PASS |
| SC-003 | Historical process language is factual and technical | A3 reviews, changelog, closeout notes, and graphics documentation reworded | PASS |
| GP-001 | CI workflows are valid and mirror local gates | Six `.github/workflows/ci.yml` files; syntax parsed with Ruby Psych; local commands recorded below | PASS |
| GP-002 | Badges make only license, tag, and workflow claims | Six README badge sets use license, GitHub latest-tag, and local CI workflow links | PASS |
| GP-003 | Community health files are present | Bug/extension issue templates, pull request template, SECURITY, and CODE_OF_CONDUCT in every repository | PASS |
| GP-004 | README family structure is coherent | All six READMEs use cover, one-liner, badges, What it is/NOT, Status, Quickstart, Architecture, Testing, Contributing, License, Provenance | PASS |
| GP-005 | Family cross-links are present | Each README links the other five family repositories | PASS |
| GP-006 | GitHub metadata guidance is present | `docs/GITHUB_METADATA.md` in all six repositories; A3 contains the family matrix | PASS |
| GP-007 | Product code/spec/vectors/fixtures/tests remain unchanged | Path audit and regression suites; only documentation, workflow, community, and metadata files changed | PASS |
| GP-008 | Lock vectors retain expected SHA-256 values | Nine A3 v2 vector hashes compared before/after; no changes | PASS |

## Frozen exceptions for SC-001

The repository contains two intentionally preserved classes of literals because the task requires code, specifications, and tests to remain byte-identical: a protocol note in the frozen `spec/` tree and source-level provider guard assertions in a frozen test. These are technical guard text, not conversation records. All mutable documentation, README files, reviews, changelogs, and metadata are scrubbed.

## Workflows created

| Repository | Workflow | Gate |
|---|---|---|
| a3 | `.github/workflows/ci.yml` | Python spec, A3UI spec, and Python conformance; no Android SDK |
| a3-ts | `.github/workflows/ci.yml` | `pnpm typecheck`, `pnpm test` |
| a3-go | `.github/workflows/ci.yml` | `go test ./...` |
| a3ui-web | `.github/workflows/ci.yml` | `npm run typecheck`, `npx vitest run` |
| a3ui-cli | `.github/workflows/ci.yml` | `python3 -m pytest -q` |
| a3ui-graphics | `.github/workflows/ci.yml` | `tests/gs_test.py`, `tests/consumer_test.py` |

CI badges become live after each workflow is pushed and its first run completes; the badges do not claim a run before publication.

## Local gate output

- A3 Python spec/conformance: SP-001..SP-007, AU-001..AU-010, and CS/PV gates passed after the A3 commit.
- a3-ts: typecheck and all 14 tests passed; INT-009 freeze passed after the A3 commit.
- a3-go: `go test ./...` passed.
- a3ui-web: typecheck and 43/43 Vitest tests passed, including W-014.
- a3ui-cli: `uv run --with pytest --python 3.11 python -m pytest -q` passed; 13 tests.
- a3ui-graphics: GS-001..004 and CS-001..006 passed.
- YAML: all six workflow files parse with Ruby Psych.

## Freeze audit

No `.kt`, `.kts`, `.ts`, `.tsx`, `.go`, runtime `.py`, shell implementation, normative spec, lock vector, fixture, or test file was intentionally changed. The only A3 `spec/` path considered was restored because the freeze requires specifications to remain byte-identical.

## Provenance

Measured: tracked-file diffs, workflow syntax, local command exits, README structure, community-file inventory, and lock hashes. The CI badge state is pending the first GitHub run after publication. Hardware performance and downstream adoption remain unverified.

## Delivery

| Repository | Commit | Local tag |
|---|---|---|
| a3 | current HEAD | **not created**: SC-001 retains two frozen technical literals required by the byte-preservation rule |
| a3-ts | `5ed1aa0db0147bc73d1f4e5de0a4f0ec6172d9d0` | `release-prep-v0.1` |
| a3-go | `1108ac3ddd38e1b581c3f6e07cce61e02cc88a4b` | `release-prep-v0.1` |
| a3ui-web | `a47dc2b1a181b05435c3f4390eea2937697773c3` | `release-prep-v0.1` |
| a3ui-cli | `2af8494782f669814b74cab5505f9a43a9ac7955` | `release-prep-v0.1` |
| a3ui-graphics | `eee030423e7904b36aeab1d5cc6e1805e2b901f8` | `release-prep-v0.1` |

The A3 commit is complete, but the strict all-zero scrub gate is intentionally not represented as green because the task also requires specifications and tests to remain byte-identical.
