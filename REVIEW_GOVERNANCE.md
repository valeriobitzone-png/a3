# REVIEW_GOVERNANCE

FASE GOVERNANCE. Unfrozen: root `CHANGELOG.md`, `GOVERNANCE.md`, `CONTRIBUTING.md`. Frozen: code, specs, tests. No push. Tag `governance-v0.1` only at green.

`spec/GOVERNANCE.md` was not created. Spec files are frozen; spec versioning is in root `GOVERNANCE.md` §3. `SPEC_A3UI` remains 0.1.0 (the document Version line). `SPEC_A3-EP` remains 0.2.0. Those versions stay independent of implementation tags.

## Audit

| ID | Invariant | Result | Evidence |
|----|-----------|--------|----------|
| GV-001 | `CHANGELOG.md` present; Keep a Changelog; Unreleased / 0.2.0 / 0.1.0 populated | PASS | `wc -l CHANGELOG.md` → 162. Headers `## [Unreleased]`, `## [0.2.0] - 2026-09-13`, `## [0.1.0] - 2026-09-12`. Subsections Added, Changed, Deprecated, Removed, Fixed, Security on each. |
| GV-002 | `GOVERNANCE.md` present; semver defined; deprecation ≥ 2 MINOR; lock vectors immutable | PASS | `wc -l GOVERNANCE.md` → 102. MAJOR = envelope/truth/confidence-ordering break. MINOR = new mark or optional field. PATCH = fix, typo, docs. Deprecation: at least two MINOR then removal only in a later MAJOR. Lock dirs `v1/` `v2/` immutable; next is `v3/`. |
| GV-003 | `CONTRIBUTING.md` has `Cosa non fare` (10 constraints) and `Come proporre un'estensione` | PASS | `grep -n "Cosa non fare"` → line 17. `grep -n "Come proporre un'estensione"` → line 32. Items 1–10. Issue title `Extension proposal`; weekend/non-CORE; fixture+tests; wait for review. |
| GV-004 | Every emitted tag `core-*` `broker-*` `agent-*` `showcase-*` `renderer-*` `overlay-*` `spec-*` `conformance-*` `protocol-*` `a3ui-*` cited with date and SHA | PASS | 55 tags. Zero missing names, SHAs, or dates (script over `git for-each-ref`). `t12-live-v0.1` also cited under 0.1.0 (T12 Gemini). |
| GV-005 | No slogans in GOVERNANCE/CONTRIBUTING; RFC 2119 + operational rules | PASS | Files use MUST / MUST NOT / SHOULD / MAY. No “What should become true”, no “repo not a protocol”. Constraint 5 forbids slogans in normative text. |
| GV-006 | Frozen trees unchanged; only root docs | PASS | `git diff --stat -- core broker agent a3ui renderers launcher overlay adapters conformance spec` empty. Porcelain before commit: `CHANGELOG.md`, `CONTRIBUTING.md`, `GOVERNANCE.md`, this REVIEW. |

## Commands (this machine, 2026-09-13)

```
wc -l CHANGELOG.md GOVERNANCE.md CONTRIBUTING.md
#     162 CHANGELOG.md
#     102 GOVERNANCE.md
#      39 CONTRIBUTING.md

git tag | grep -E '^(core|broker|agent|showcase|renderer|overlay|spec|conformance|protocol|a3ui)-' | wc -l
#      55

git diff --stat -- core broker agent a3ui renderers launcher overlay adapters conformance spec
# (empty)
```

## Protocol split in the changelog

- `[0.1.0] - 2026-09-12` — `a3.*`, lock `conformance/vectors/v1/`, T12 Gemini (`t12-live-v0.1` `7d573ea`), dual impl (`conformance-v0.1` `f66813c`).
- `[0.2.0] - 2026-09-13` — `io.a3ep.*`, attestation, lock `v2/` (`protocol-v2-v0.1` `c6936c3`), plus a3ui conformance and a11y tags on 2026-09-13.
- `[Unreleased]` — these governance files. Tag `governance-v0.1` is not a protocol MINOR.

## Freeze note

No Kotlin, no spec body, no lock bytes, no tests. `README.md` was not edited (not in the unfrozen set).
