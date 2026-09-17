# A3 — A3-EP protocol and A3UI

![A3 cover](docs/assets/cover.png)

A protocol and UI layer that keeps belief, action, and reality separate before interaction.

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE) [![Latest tag](https://img.shields.io/github/v/tag/valeriobitzone-png/a3?sort=semver)](https://github.com/valeriobitzone-png/a3/tags) [![CI](https://github.com/valeriobitzone-png/a3/actions/workflows/ci.yml/badge.svg)](https://github.com/valeriobitzone-png/a3/actions/workflows/ci.yml)

## What it is

A3-EP is a protocol for separating belief, action, and reality. A3UI consumes it and exposes marks, provenance, age, confidence, and permitted or forbidden actions before interaction.

## What it is NOT

It is not an autonomous decision-maker, a replacement for evidence, a renderer-specific UI framework, or a claim of universal device performance.

## Status

- **VERIFIED:** A3-EP v0.2.0 lock-vector conformance, Kotlin implementation, A3UI semantic conformance, lifecycle, re-binding, opaque extensions, and repository test gates.
- **UNVERIFIED:** physical Android FPS, representative mid hardware, and production adoption outside this repository family.

## Quickstart

### Get it

```bash
git clone https://github.com/valeriobitzone-png/a3.git
cd a3
# Requirements: JDK 21+, Android SDK for Android tasks, Python 3
```

### Prove it

```bash
python3 spec/test_spec.py
python3 spec/test_a3ui_spec.py
python3 conformance/src/test/python/test_conformance.py
```

The SDK-independent gate above is the public CI path. `./gradlew test` additionally covers the Kotlin modules when an Android SDK is available.

### Integrate it

Read [`spec/SPEC_A3-EP.md`](spec/SPEC_A3-EP.md), then run the conformance suite against your own envelopes and declared provenance. Do not import implementation code from another repository; the spec produces and implementations consume.

## Architecture

`spec/` is normative; `core/`, `broker/`, `agent/`, `a3ui/`, and renderers are implementations; `conformance/` contains vectors and fixtures; `docs/` contains process and review evidence.

## Testing & conformance

Python spec/conformance gates are run in CI without Android SDK. Kotlin and A3UI module gates are recorded in the review history. Shared vectors and fixtures are locked by SHA-256.

## Family

- [a3-ts](https://github.com/valeriobitzone-png/a3-ts) — TypeScript reference implementation
- [a3-go](https://github.com/valeriobitzone-png/a3-go) — Go reference implementation
- [a3ui-web](https://github.com/valeriobitzone-png/a3ui-web) — Web Components renderer
- [a3ui-cli](https://github.com/valeriobitzone-png/a3ui-cli) — textual Python renderer
- [a3ui-graphics](https://github.com/valeriobitzone-png/a3ui-graphics) — renderer-neutral graphics tokens

## Contributing

Read [`CONTRIBUTING.md`](CONTRIBUTING.md), preserve provenance, and keep locked vectors, fixtures, and the spec/implementation boundary explicit.

## License

Code is Apache-2.0 (`LICENSE`, `NOTICE`). Specifications and schemas under `spec/` are CC BY 4.0 (`spec/LICENSE-CC-BY`).

## Provenance

Normative claims come from `spec/`; implementation behavior is measured by executable tests and lock hashes. Physical-device performance and downstream adoption are unverified deductions, not measured facts.
