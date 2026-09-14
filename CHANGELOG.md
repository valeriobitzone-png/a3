# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
as defined in `GOVERNANCE.md`.

Each historical entry cites the git tag and the tagged commit SHA.

## [Unreleased]

### Added

- Repository governance at the root: `GOVERNANCE.md`, `CONTRIBUTING.md`, and this file. The GV gate tags `governance-v0.1` on the introducing commit. This is not a protocol MAJOR/MINOR.

### Changed

- A3UI perf **(b)**: catalog target remains 60 fps (p95 < 16.7 ms); overlay expanded target is 30 fps (p95 < 33.3 ms). Declared because measured HIGH overlay expanded p95 on A024 gfxinfo is 200 ms, so **(a)** (16.7 ms on both platforms) cannot close. Overlay auto-detect on A024 defaults to BLUR_OFF with a visible profile pill. Mac display blur is `NSVisualEffectView`; CPU `OverlayBlur` is not the display path.
- A3UI perf compliance (docs, `a3ui-perf-v0.2`): (1) Mac catalog compliance metric is **presentation vsync** (missed-vsync count), not raw p95. HIGH catalog: 60 fps in presentation after hitch (missed=0 on 315 frames); raw p95 **17.116 ms** is vsync jitter. MID catalog raw p95 **17.829 ms** ≰ HIGH **17.116 ms** declared as jitter; PF-003 rescoped to overlay (Mac catalog exception written, not silent green). (2) Android: no profile meets the target → default = cheapest profile (BLUR_OFF) + fps **UNVERIFIED** (`GOVERNANCE.md` §10); gfxinfo nominal **200 ms** marked as suspected harvest artifact until an on-screen jank-frame count exists.

### Deprecated

- None.

### Removed

- None.

### Fixed

- None.

### Security

- None.

## [0.2.0] - 2026-09-13

Protocol v2. Envelope types `io.a3ep.*`, mandatory attestation on v2 output, lock vectors in `conformance/vectors/v2/`. `SPEC_A3-EP` 0.2.0. A v2 parser MUST accept `a3.*` input and MUST normalize it.

### Added

- `protocol-v2-v0.1` (2026-09-12, `c6936c3`) — A3-EP lock v2; `io.a3ep.*` type registry; `attester_id` MUST NOT equal `requester_id` on irreversible action.
- `a3ui-conformance-v0.1` (2026-09-13, `42fff35`) — mark-to-CTA conformance fixtures; a renderer that enables a forbidden action MUST fail on Android and Mac.
- `a3ui-a11y-v0.1` (2026-09-13, `f0aeac5`) — TalkBack and VoiceOver MUST read type, reason, and the forbidden action; a dimmed button MUST NOT ship as the spoken state.

### Changed

- Envelope output MUST emit `io.a3ep.*`. Input `a3.*` remains accepted and is normalized (`protocol-v2-v0.1`, `c6936c3`).
- `SPEC_A3-EP` version 0.2.0 obsoletes 0.1.0 (`protocol-v2-v0.1`, `c6936c3`).

### Deprecated

- Envelope type names `a3.*` on the wire. They MUST remain parseable. Removal, if any, MUST follow `GOVERNANCE.md` (two MINOR releases of warning, then a subsequent MAJOR).

### Removed

- None. Lock vectors in `conformance/vectors/v1/` MUST remain byte-identical.

### Fixed

- None.

### Security

- Attestation: `attester_id` MUST NOT equal `requester_id` on `io.a3ep.action.authorized` (`protocol-v2-v0.1`, `c6936c3`).

## [0.1.0] - 2026-09-12

Protocol v1. Envelope types `a3.*`, lock vectors in `conformance/vectors/v1/`, live Gemini receipt proof, Kotlin and Python on the same vectors.

### Added

- `core-v0.1` (2026-08-28, `be15393`) — deterministic core; T1–T10.
- `prediction-core-v0.1` (2026-08-28, `85d26b4`) — prediction barrier; only `AcceptedObservation` applies.
- `projection-core-v0.1` (2026-08-29, `01441b3`) — read-only presentation transformer.
- `a3ui-core-v0.1` (2026-08-29, `a54d85b`) — read-only compiler of temporal intent.
- `renderer-android-v0.1` (2026-08-29, `31059c0`) — Android renderer as a read-only A3UI interpreter.
- `mcp-adapter-v0.1` (2026-08-29, `fe44975`) — MCP adapter that stops at Observation.
- `a3ui-core-v0.2` (2026-08-29, `bc40251`) — closed catalog of Node, Binding, and GestureBinding.
- `intent-model-v0.1` (2026-08-30, `03027df`) — IntentProvider without belief writes.
- `launcher-v0.1` (2026-08-30, `f8fd310`) — Android composition root.
- `renderer-android-v0.3` (2026-08-30, `a230ab6`) — catalog item and action copy.
- `core-json-v0.1` (2026-08-30, `b5d3a2d`) — canonical JSON engine in `:core:json`.
- `core-v0.3` (2026-08-30, `b5d3a2d`) — same commit as `core-json-v0.1`; JSON engine extraction.
- `a3ui-core-v0.3` (2026-08-30, `b5d3a2d`) — consume `:core:json`.
- `prediction-core-v0.2` (2026-08-30, `b5d3a2d`) — consume `:core:json`.
- `projection-core-v0.2` (2026-08-30, `b5d3a2d`) — consume `:core:json`.
- `renderer-android-v0.4` (2026-08-30, `b5d3a2d`) — consume `:core:json`.
- `renderer-android-v0.5` (2026-08-30, `a1f3f2f`) — Theme grammar on the train catalog.
- `renderer-android-v0.6` (2026-08-30, `e0add84`) — real-time foley bound to existing signals.
- `core-admission-v0.1` (2026-09-07, `785fd65`) — pure `evaluate` fold; BeliefState stores Claims.
- `core-v0.4` (2026-09-07, `785fd65`) — admission fold.
- `a3ui-v0.4` (2026-09-07, `785fd65`) — admission fold on the a3ui line.
- `intent-model-v0.2` (2026-09-07, `785fd65`) — admission fold.
- `launcher-v0.2` (2026-09-07, `785fd65`) — admission fold.
- `mcp-adapter-v0.2` (2026-09-07, `785fd65`) — admission fold.
- `prediction-v0.3` (2026-09-07, `785fd65`) — admission fold.
- `projection-v0.3` (2026-09-07, `785fd65`) — admission fold.
- `renderer-android-v0.7` (2026-09-07, `785fd65`) — admission fold.
- `core-action-v0.1` (2026-09-07, `2b35939`) — ActionState machine; receipts MUST NOT become believed reality.
- `core-v0.5` (2026-09-07, `2b35939`) — ActionState split.
- `mcp-adapter-v0.3` (2026-09-07, `2b35939`) — ActionState split.
- `broker-v0.1` (2026-09-07, `7a60b52`) — signed consent register for irreversible tool calls.
- `broker-v0.2` (2026-09-10, `673531f`) — keystore bound; first command MUST NOT hang on a locked keychain.
- `a3ui-core-v0.5` (2026-09-10, `edbe7b2`) — belief uncertainty on A3UI nodes.
- `launcher-v0.3` (2026-09-10, `edbe7b2`) — uncertainty on nodes.
- `renderer-android-v0.8` (2026-09-10, `edbe7b2`) — uncertainty on nodes.
- `launcher-v0.4` (2026-09-10, `0b7ee2d`) — held or stale MUST NOT look believed.
- `renderer-android-v0.9` (2026-09-10, `0b7ee2d`) — held or stale MUST NOT look believed.
- `launcher-v0.5` (2026-09-10, `4b26b91`) — calendar fixture: believed, aging, held, contradicted.
- `launcher-v0.6` (2026-09-10, `dc9637d`) — UNKNOWN as a dashed full-opacity frame.
- `renderer-android-v0.10` (2026-09-10, `dc9637d`) — UNKNOWN dashed frame.
- `renderer-mac-v0.1` (2026-09-10, `8f99cde`) — macOS desktop renderer; same epistemic axis.
- `renderer-android-v0.11` (2026-09-10, `b088efa`) — a3ui-graphics liquid glass tokens.
- `renderer-mac-v0.2` (2026-09-10, `b088efa`) — a3ui-graphics liquid glass tokens.
- `renderer-android-v0.12` (2026-09-10, `2eef271`) — motion tokens (spring, bezier).
- `renderer-mac-v0.3` (2026-09-10, `2eef271`) — motion tokens.
- `renderer-android-v0.13` (2026-09-11, `0110efe`) — color and type tokens.
- `renderer-mac-v0.4` (2026-09-11, `0110efe`) — color and type tokens.
- `renderer-android-v0.14` (2026-09-11, `ea7c25f`) — glass and motion tokens (DoF, parallax).
- `renderer-mac-v0.5` (2026-09-11, `ea7c25f`) — glass and motion tokens.
- `renderer-android-v0.15` (2026-09-11, `65d4067`) — audio and haptic tokens.
- `renderer-mac-v0.6` (2026-09-11, `65d4067`) — audio and haptic tokens.
- `showcase-v0.1` (2026-09-11, `5240812`) — navigable a3ui showcase.
- `agent-v0.1` (2026-09-11, `0e5b1e7`) — personal agent surface; gated options; honest previews.
- `showcase-v0.2` (2026-09-11, `8715cad`) — frost showcase chrome.
- `renderer-android-v0.16` (2026-09-11, `b6d7c5a`) — blur a replica of the scene under catalog glass.
- `renderer-mac-v0.7` (2026-09-11, `b6d7c5a`) — blur a replica of the scene under catalog glass.
- `showcase-v0.3` (2026-09-11, `bd2dc60`) — train, hotel, and calendar as simultaneous states.
- `core-temporal-v0.1` (2026-09-11, `9787ec9`) — event, observe, admit, and present times.
- `core-truth-v0.1` (2026-09-11, `839a333`) — truth class and provenance; HYPOTHESIS MUST NOT become FACT without admitted real verification.
- `core-envelope-v0.1` (2026-09-11, `0a4304c`) — RFC 8785 and CloudEvents; `a3.*` types; lock v1 bytes.
- `core-confidence-v0.1` (2026-09-12, `83bd85e`) — five confidence dimensions; weighted minimum; a sixth axis MUST NOT be added without a MAJOR (see `GOVERNANCE.md`).
- `t12-live-v0.1` (2026-09-12, `7d573ea`) — live Gemini call; SUCCESS MUST NOT be admitted as FACT without a real postcondition.
- `overlay-v0.1` (2026-09-12, `adb5694`) — system overlay; declared blur and permission fallbacks.
- `overlay-v0.2` (2026-09-12, `fbc7f2a`) — overlay COLLAPSED to an epistemic pill by default.
- `spec-v0.1` (2026-09-12, `629713a`) — `SPEC_A3-EP` 0.1.0 as RFC 2119 hygiene rules.
- `a3ui-spec-v0.1` (2026-09-12, `4b441c5`) — `SPEC_A3UI` 0.1.0 as RFC 2119 window-manager rules.
- `conformance-v0.1` (2026-09-12, `f66813c`) — executable A3-EP suite; Kotlin and Python MUST fail the same lock v1 vectors.

### Changed

- `core-v0.2` (2026-08-29, `1b73946`) — replay committed belief as a fold of `WorldState.apply`.
- `renderer-android-v0.2` (2026-08-29, `f8b3afe`) — drop FoundationInput alias; honest host stall.
- `core-confidence-v0.2` (2026-09-12, `62695b5`) — recency rounded IEEE-754 nearest-even; lock matches `2^(-10799/21600)`; score threshold unchanged.

### Deprecated

- None.

### Removed

- None.

### Fixed

- None.

### Security

- `broker-v0.1` (2026-09-07, `7a60b52`) and `broker-v0.2` (2026-09-10, `673531f`) — irreversible actions gated by signed consent and a bound keystore.
