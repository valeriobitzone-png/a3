# a3ui conformance

Category suite for SPEC_A3UI 0.1.0. A renderer that enables a CTA the spec forbids fails. Dual platform: Android (Robolectric) + Mac (Compose desktop).

Module: `:a3ui:conformance` (consumes `:a3ui`; fixtures reuse showcase-v0.3 hotel/calendar/train plus flight FACT). `:showcase` is an Android application so it cannot be a Gradle `implementation` of this library — parity with `showcase/shared/ShowcaseScene.kt` is the consume gate. Showcase itself is not modified (no FACT, no `stateDescription`); the conformance host applies SPEC_A3UI §4–7.

## Run

From the repository root:

```
./gradlew :a3ui:conformance:test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

That runs:

- JVM category tests AC-001..008 and freeze AC-023
- `:a3ui:conformance:android:test` (AC-009, 011, 013, 015, 017, 019, 021 + screenshots)
- `:a3ui:conformance:mac:test` (AC-010, 012, 014, 016, 018, 020, 022 + screenshots)

Screenshots and parse trees land in `conformance/a3ui/screenshots/{android,mac}/`.

## Fixtures

Lawful surfaces in `fixtures/`:

| File | Mark | CTA |
|------|------|-----|
| `hotel-stale.json` | STALE, OBSERVATION, €89, age 2h | enabled + warning `stale 2h` |
| `calendar-contradicted.json` | CONTRADICTED, 9:00+9:30 | disabled + `contradicted: resolve conflict first` |
| `train-pending.json` | PENDING / UNKNOWN, `in verifica` | disabled, reserved slot, no spinner |
| `flight-fact.json` | FACT, BELIEVED, now | enabled, baseline only |

Violation fixtures in `fixtures/violations/` are explicit rejects (AC-001..008). Silence is not a pass.

## Add a fixture

1. Add JSON under `fixtures/` with the surface tuple plus `cta_enabled`, `reason`/`warning`, and `state_description`.
2. Add a matching `SurfaceFixture` in `A3UiFixtures` if it is lawful, or a `fixtures/violations/ac-NNN-*.json` if it is a MUST NOT.
3. Capture both platforms (`capture("NAME", ...)` in Android and Mac tests).
4. Re-run `:a3ui:conformance:test`. Fail if the CTA mapping violates SPEC_A3UI §4.

## CORE vs this suite

A3-EP CORE is envelope/truth/ordering/confidence. This suite is a3ui mark→CTA. It MUST NOT relax A3-EP. Frozen trees: `core/`, `broker/`, `agent/`, `renderers/`, `launcher/`, `overlay/`, `adapters/`.
