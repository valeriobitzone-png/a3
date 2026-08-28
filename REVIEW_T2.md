# REVIEW_T2 — A3 prediction core 0.1

**Gate:** P1–P10 green, T1–T10 still green, invariant *Prediction may prepare / may not commit* PASS.  
**Scope:** prediction core under `core/` (`a3.core.prediction.model`, `a3.core.prediction.engine`) plus `spec/19-prediction-core.md`. Not product adapters: `adapters/`, `a3ui/`, `intent-model/` untouched. No AI/MCP/A2UI/network/LLM.  
**Tag:** `prediction-core-v0.1` (applied only after this document and tests green). Frozen T1 tag `core-v0.1` is unchanged.

---

## Esito test (output reale)

Comando: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-28. Exit code: 0.

```
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:processResources
> Task :core:processTestResources NO-SOURCE
> Task :core:compileKotlin
> Task :core:compileJava NO-SOURCE
> Task :core:classes
> Task :core:jar
> Task :core:compileTestKotlin
> Task :core:compileTestJava NO-SOURCE
> Task :core:testClasses UP-TO-DATE

> Task :core:test

CoreAcceptanceTest > T7_loopMismatch() PASSED

CoreAcceptanceTest > T8_trustBlocked() PASSED

CoreAcceptanceTest > T9_determinism() PASSED

CoreAcceptanceTest > T6_loopCommit() PASSED

CoreAcceptanceTest > T4_missingPrecondition() PASSED

CoreAcceptanceTest > T1_happyPath() PASSED

CoreAcceptanceTest > T2_noPlan() PASSED

CoreAcceptanceTest > T5_staleState() PASSED

CoreAcceptanceTest > T10_replay() PASSED

CoreAcceptanceTest > T3_constraintConflict() PASSED

InvariantReviewTest > R7_determinism_canonical_bytes() PASSED

InvariantReviewTest > R1_preconditions_canApply_iff_validAt() PASSED

InvariantReviewTest > R9_epistemic_only_observation_accepted_writes() PASSED

InvariantReviewTest > R3_contradictory_effect_supersedes_never_silent_overwrite() PASSED

InvariantReviewTest > R6_constraint_violation_is_constraint_conflict() PASSED

InvariantReviewTest > R5_stale_state_only_when_expires_at_before_now() PASSED

InvariantReviewTest > R2_confidence_min_precond_times_reliability() PASSED

InvariantReviewTest > R8_replay_state_equals_original() PASSED

InvariantReviewTest > missing_precondition_when_producer_blocked() PASSED

InvariantReviewTest > R4_compensatory_rollback_never_decrements_version() PASSED

SchemaValidationTest > every_model_validates_against_its_schema() PASSED

PredictionAcceptanceTest > P5_forecast_ranking_deterministic() PASSED

PredictionAcceptanceTest > P3_PreparedState_expires_after_TTL() PASSED

PredictionAcceptanceTest > P7_observation_accepted_remains_only_WorldState_writer() PASSED

PredictionAcceptanceTest > P8_prediction_may_produce_ProjectionCandidate_but_never_Outcome() PASSED

PredictionAcceptanceTest > P6_prediction_event_log_replayable() PASSED

PredictionAcceptanceTest > P10_canonical_determinism_Forecast_FutureState() PASSED

PredictionAcceptanceTest > P4_new_context_invalidates_previous_prepared_state() PASSED

PredictionAcceptanceTest > P1_prediction_produces_FutureState_without_modifying_WorldState() PASSED

PredictionAcceptanceTest > P9_core_T1_T10_remain_green() PASSED

PredictionAcceptanceTest > P2_PreparedState_cannot_be_committed_into_WorldState() PASSED

BUILD SUCCESSFUL in 7s
5 actionable tasks: 5 executed
```

T1–T10: 10/10 PASSED. P1–P10: 10/10 PASSED. Review R1–R9 + schema: PASSED.

---

## Invariant: Prediction may prepare / may not commit

| Check | Evidence | Test | Esito |
|-------|----------|------|-------|
| Produces `FutureState` | One-step simulation of applicable capabilities; facts are hypothesized `current(now)` | P1 | **PASS** |
| Does not modify WorldState | Version stays 0; STATE canonical bytes unchanged; no `state.updated` from prediction | P1 | **PASS** |
| No `PreparedState.commitToWorldState()` | Method absent on the type; `PreparedState` ≠ `BeliefState` | P2 | **PASS** |
| Commit attempt rejected | `WorldState.write(PREDICTION, …)` and `PredictionWrite.attempt` → `WriteResult.Rejected`, `state.write_rejected` | P2, P7 | **PASS** |
| TTL | `get` live iff `prepared_at <= t < expires_at`; `t == expires_at` → null | P3 | **PASS** |
| New context invalidates | Previous live prepared marked `invalidated`; `prediction.invalidated` logged | P4 | **PASS** |
| Only Observation.accepted writes | After predict, PREDICTION/POLICY/EXECUTION rejected; `OBSERVATION_ACCEPTED` → version 1 | P7, R9 | **PASS** |
| ProjectionCandidate, never Outcome | Engine returns `ProjectionCandidate`; no `Outcome` payloads or `outcome` field | P8 | **PASS** |

WorldState write gate is unchanged from T1: only `EpistemicSource.OBSERVATION_ACCEPTED` succeeds.

---

## P1–P10

| # | Criterio | Esito |
|---|----------|-------|
| P1 | FutureState without modifying WorldState | **PASS** |
| P2 | PreparedState cannot be committed into WorldState | **PASS** |
| P3 | PreparedState expires after TTL | **PASS** |
| P4 | New context invalidates previous prepared state | **PASS** |
| P5 | Forecast ranking deterministic (score desc, capability_ref asc, id asc; HashMap graph same bytes) | **PASS** |
| P6 | Prediction event log replayable (`prediction.updated` / `prediction.invalidated`) | **PASS** |
| P7 | Observation.accepted remains the only WorldState writer | **PASS** |
| P8 | ProjectionCandidate, never Outcome | **PASS** |
| P9 | Core T1–T10 remain green | **PASS** |
| P10 | Canonical UTF-8 bytes for Forecast / FutureState | **PASS** |

---

## T1–T10

Unchanged tests in `CoreAcceptanceTest.kt`. All PASSED in the same `./gradlew test` run. `core-v0.1` behavior: WorldState write gate, planner `PlanResult`, canonical SCHEMA/STATE profiles, clock/IDs, replay of `state.updated` are unmodified.

---

## Canonical serialization

Additive branches in `a3.core.serialize.CanonicalJson` for Forecast, ForecastCandidate, FutureState, PreparedState, PredictionPolicy, PredictionStatus, ProjectionCandidate, PredictionInvalidation. Existing SCHEMA validation (T1 `Prediction` envelope, T9/T10, `SchemaValidationTest`) unchanged.

Rules reused: sorted keys (`TreeMap`), sorted facts `(k, observed_at, id, source)`, omitted nulls, UTF-8 compact. P10 uses `assertContentEquals` on UTF-8 bytes.

---

## Core files touched (T1 freeze)

| File | Why | T1–T10 semantics |
|------|-----|------------------|
| `CanonicalJson.kt` | Additive `when` branches for prediction types | Unchanged for Plan/BeliefState/schema models |
| `spec/14-prediction.md` | Pointer to spec 19 | Docs only |
| `spec/01-event-model.md` | Listed `prediction.invalidated` | Docs only |
| `A3_SPEC_0.1.md` | Pointer to spec 19 | Docs only |

No edits to `WorldState.kt`, `BeliefState.kt`, `Planner.kt`, `Runtime.kt`, `EventLog.replayState`, `PredictionWrite`, `Models.kt`, or T1–T10 tests.

---

## Deviazioni dichiarate

1. **One-step lookahead**, not a chained plan. Multi-step search remains the planner. Prediction ranks currently applicable capabilities independently from the same belief copy.
2. **`ProjectionCandidate` ≠ A3UI `Projection`**. Prefetch hint only; not validated against `projection.schema.json`; no renderer.
3. **No new JSON Schema files** for Forecast/FutureState/PreparedState. Byte contract is CanonicalJson. The T1 `prediction.schema.json` envelope (`Prediction`) is unchanged.
4. **Expiry is evaluated at read time** against the injected clock. Stored status stays `prepared` until explicit invalidation; `get` returns null when `t >= expires_at`.
5. **`prediction.invalidated`** is the invalidation event (listed in spec 01 and 19). Forecast/prepared still use `prediction.updated`. These events are ignored by `EventLog.replayState`.
6. **Scoring** `reliability / (1 + money + timeMin) × boost`; boost is `1 + goal.priority` only when effects overlap desired `(k,v)`.
7. **Top-ranked FutureState** is the single `PreparedState` stored per `predict` call. Other candidates remain on the Forecast.
8. **Jackson** still schema-only (T1); prediction path does not use it.

Nessuna di queste deviazioni rende FAIL P1–P10 o T1–T10.

---

## Gate di rilascio

- `./gradlew test` green
- T1–T10 verdi
- P1–P10 verdi
- Questo file presente
- Invariante epistemico: solo Observation.accepted scrive WorldState
- Invariante predittivo: prepare, never commit

**Definizione di fatto soddisfatta.** Tag `prediction-core-v0.1`. MCP, A3UI e AI restano fuori scope.
