# A3 — A3-EP protocol and A3UI

![A3 cover](docs/assets/cover.png)

## What it is

A3-EP is a protocol that keeps belief, action, and reality separate. A3UI consumes that protocol and exposes marks, provenance, age, confidence, and permitted or forbidden actions before interaction.

## What it is not

It is not an autonomous decision-maker, a replacement for evidence, a renderer-specific UI framework, or a claim of universal device performance. Android FPS and representative mid hardware remain **UNVERIFIED**.

## Status

- **VERIFIED:** A3-EP v0.2.0 lock-vector conformance, Kotlin implementation, A3UI semantic conformance, lifecycle, re-binding, opaque extensions, and repository test gates.
- **UNVERIFIED:** physical Android FPS, representative mid hardware, and production adoption outside this repository family.

## Get it

```bash
git clone https://github.com/valeriobitzone-png/a3.git
cd a3
# Requirements: JDK 21+, Android SDK for Android tasks, Python 3
```

Structure: `spec/` is normative; `core/`, `broker/`, `agent/`, `a3ui/`, and renderers are implementations; `conformance/` contains lock vectors and fixtures; `docs/` contains process and reviews.

## Prove it

```bash
./gradlew test
python3 spec/test_spec.py
python3 spec/test_a3ui_spec.py
python3 conformance/src/test/python/test_conformance.py
./gradlew :a3ui:conformance:test
```

Expected result: all commands exit 0. The shared vectors and fixtures must remain byte-identical.

## Integrate it

Validate **your implementation** by reading [`spec/SPEC_A3-EP.md`](spec/SPEC_A3-EP.md), then run the conformance suite against your own envelopes and declared provenance. Do not import implementation code from another repository; the spec produces and implementations consume.

## Repositories

| Repository | Role |
|---|---|
| `a3` | A3-EP implementation, normative specs, Kotlin A3UI, and conformance source |
| `a3-ts` | TypeScript reference implementation and lock-vector tests |
| `a3-go` | Go reference implementation and lock-vector tests |
| `a3ui-web` | Native Web Components A3UI renderer |
| `a3ui-cli` | Python stdlib textual A3UI renderer |
| `a3ui-graphics` | Renderer-neutral phase/token graphics specification |

## License

Code is Apache-2.0 (`LICENSE`, `NOTICE`). Specifications and schemas under `spec/` are CC BY 4.0 (`spec/LICENSE-CC-BY`). Graphics are licensed by `a3ui-graphics`.

## Provenance

The normative source is `spec/`; implementation behavior is measured by executable tests and lock hashes. Statements about physical devices or downstream adoption are deductions or plans, not measured facts. Slice history, tags, and reviews are part of the provenance record.
