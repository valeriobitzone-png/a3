# REVIEW_T2 — A3 prediction core 0.1 (type-system barrier)

**Gate:** T1–T10 + P1–P11 green. Invariant *Prediction may prepare / may not commit* enforced by the **type system**, not a boolean.  
**Tag:** `prediction-core-v0.1` (overwrites the previous T2 tag). Frozen T1 tag `core-v0.1` is unchanged.  
**No AI / MCP / A2UI / renderer / network.**

---

## Esito test (output reale)

Comando: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-28. Exit code: 0.

```
> Task :prediction:test

PredictionArchitectureTest > P11_prediction_does_not_depend_on_world_or_runtime PASSED

PredictionAcceptanceTest > P5_forecast_ranking_deterministic() PASSED

PredictionAcceptanceTest > P3_PreparedState_expires_after_TTL() PASSED

PredictionAcceptanceTest > P8_prediction_may_produce_ProjectionCandidate_but_never_Outcome() PASSED

PredictionAcceptanceTest > P6_prediction_event_log_replayable() PASSED

PredictionAcceptanceTest > P10_canonical_determinism_Forecast_FutureState() PASSED

PredictionAcceptanceTest > P4_new_context_invalidates_previous_prepared_state() PASSED

> Task :core:runtime:test

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

PredictionWorldGateTest > P7_observation_accepted_remains_only_WorldState_writer() PASSED

PredictionWorldGateTest > P1_prediction_produces_FutureState_without_modifying_WorldState() PASSED

PredictionWorldGateTest > P9_core_T1_T10_remain_green() PASSED

PredictionWorldGateTest > P2_PreparedState_cannot_be_committed_into_WorldState() PASSED

BUILD SUCCESSFUL in 23s
13 actionable tasks: 13 executed
```

T1–T10: 10/10 PASSED. P1–P11: 11/11 PASSED (P11 = ArchUnit).

---

## STEP 1 — Audit e rimozioni

| Rimozione | Dove stava | Perché |
|-----------|------------|--------|
| `WorldState.write(EpistemicSource, Observation, …)` | `a3.core.world.WorldState` | Overload che accettava Observation + un enum; il booleano di fatto (`source == OBSERVATION_ACCEPTED`) era ignorabile. |
| `EpistemicSource` (`PREDICTION` / `POLICY` / `EXECUTION` / `OBSERVATION_ACCEPTED`) | `WorldState.kt` | Non serve più un discriminante a runtime. |
| `WriteResult.Rejected` | `WorldState.kt` | Non esiste più un write rifiutato a runtime: i pathway extra non compilano. |
| `PredictionWrite` / `PolicyWrite` / `ExecutionWrite` | `core/.../prediction/PredictionWrite.kt` | Adapter che chiamava `world.write(PREDICTION\|POLICY\|EXECUTION, …)`. |
| `WorldState.apply(PreparedState)` / `apply(FutureState)` / `apply(Forecast)` / `apply(Observation)` | mai presenti come overload; **vietati** | Unico `apply` = `apply(AcceptedObservation)`. |
| `PreparedState.commitToWorldState()` / `toWorldState()` / `toBeliefState()` / `commit()` | mai presenti; **vietati** | P2/P8 verificano l'assenza via reflection. |
| Campo `may_commit` su PreparedState | **non era nello schema JSON né nel modello T2 taggato** | Eliminato dal disegno: un booleano è insufficiente. Commento nel modello + spec 19. |

`ObservationAcceptance.apply(BeliefState, Observation)` resta: è il merge su **copie** (planner / replay), non `WorldState.apply`.

---

## STEP 2 — Moduli Gradle

| Modulo | Contenuto | Dipendenze |
|--------|-----------|------------|
| `:core:world-api` | `Fact`, `BeliefReader`, `ReadBelief`, clock/ID iniettati | nessuna |
| `:core:world` | `BeliefState`, `WorldState`, `AcceptedObservation` (ctor `protected`), `Observation`, `EventLog` | `world-api` |
| `:core:runtime` | planner, runtime, `AcceptedObservationToken` (**ctor `internal`**), `acceptedObservation()` | `world` |
| `:prediction` | Forecast / engine / canonical JSON predittivo | **solo `world-api`** |

`WorldState.apply` accetta **solo** `AcceptedObservation`. Nessun altro overload.

Kotlin `internal` è per compilation unit: il token costruibile (`AcceptedObservationToken`) vive in `:core:runtime` con costruttore `internal`. Il tipo nominale `AcceptedObservation` sta in `:core:world` con costruttore `protected` così `apply` può nominarlo senza ciclo Gradle. Prediction non dipende da `world` né da `runtime`, quindi non può mintare il token né chiamare `apply`.

Le quattro categorie epistemiche (Observation, Prediction, Policy, Execution) restano tipi distinti, senza supertipo mutabile.

---

## STEP 3 — P11 ArchUnit (output reale)

```
PredictionArchitectureTest > P11_prediction_does_not_depend_on_world_or_runtime PASSED
```

Regola: nessuna classe in `a3.prediction..` dipende da `a3.core.world` o `a3.core.runtime..`.

Riga documentata in `PredictionArchitectureTest` e in P2:

```
// worldState.apply(preparedState)   ← NON COMPILA: nessun overload esiste.
```

---

## Prova compile-time (grep)

Eseguiti sulla working tree (escluso `**/build/**`). Pathway di scrittura **assenti** nel codice eseguibile:

| Query | Risultato |
|-------|-----------|
| `fun write(` | **vuoto** |
| `apply(prepared` / `apply(future` / `apply(forecast` | solo commenti `← NON COMPILA` |
| `PredictionWrite` / `enum class EpistemicSource` | **assenti** dal source Kotlin |
| `may_commit` in `.kt` eseguibile | solo Javadoc su PreparedState (nessun campo) |
| `WorldState.apply` | un solo overload: `fun apply(accepted: AcceptedObservation)` |

`ObservationAcceptance.apply` e `Transition.apply` restano (merge/simulazione, non commit WorldState).

---

## Invariante: prepare / not commit

| Check | Evidence | Test | Esito |
|-------|----------|------|-------|
| Produce FutureState | hypothesised facts, tipo ≠ BeliefState | P1 | **PASS** |
| Non modifica WorldState | version 0, bytes STATE invariati | P1 | **PASS** |
| Nessun commit method / overload | reflection + unico `apply(AcceptedObservation)` | P2, R9 | **PASS** |
| TTL | `get` live iff `prepared_at <= t < expires_at` | P3 | **PASS** |
| Nuovo context invalida | `prediction.invalidated` | P4 | **PASS** |
| Ranking deterministico | score desc, capability_ref asc, id asc | P5 | **PASS** |
| Replay event log predittivo | `PredictionReplay` | P6 | **PASS** |
| Solo Observation.accepted scrive | token runtime → `apply`; prediction non ha WorldState | P7, R9 | **PASS** |
| ProjectionCandidate, mai Outcome | P8 | P8 | **PASS** |
| T1–T10 verdi | CoreAcceptanceTest | P9, T1–T10 | **PASS** |
| Canonical UTF-8 Forecast/FutureState | P10 | P10 | **PASS** |
| ArchUnit isolation | P11 | P11 | **PASS** |

---

## P1–P11

| # | Criterio | Esito |
|---|----------|-------|
| P1 | FutureState without modifying WorldState | **PASS** |
| P2 | PreparedState cannot be committed into WorldState | **PASS** |
| P3 | PreparedState expires after TTL | **PASS** |
| P4 | New context invalidates previous prepared state | **PASS** |
| P5 | Forecast ranking deterministic | **PASS** |
| P6 | Prediction event log replayable | **PASS** |
| P7 | Observation.accepted remains the only WorldState writer | **PASS** |
| P8 | ProjectionCandidate, never Outcome | **PASS** |
| P9 | Core T1–T10 remain green | **PASS** |
| P10 | Canonical UTF-8 bytes for Forecast / FutureState | **PASS** |
| P11 | ArchUnit: `a3.prediction..` ↛ `a3.core.world` / `a3.core.runtime..` | **PASS** |

---

## T1–T10

Comportamento del planner, merge, STALE_STATE (`expires_at < now`), closed-loop, rollback compensativo, canonical SCHEMA/STATE: invariati. API di commit: `world.apply(acceptedObservation(obs, now))` al posto di `world.write(OBSERVATION_ACCEPTED, obs, now)`. Semantica del commit accettato identica.

---

## Canonical serialization

- Runtime: `a3.core.serialize.CanonicalJson` (T1, senza tipi Forecast/PreparedState).
- Prediction: `a3.prediction.serialize.CanonicalJson` (stesse regole: chiavi TreeMap, fatti ordinati, UTF-8 compact).

---

## Deviazioni dichiarate

1. **`AcceptedObservation` è `protected` in `:core:world`**, subclass `internal` in `:core:runtime`. Kotlin non consente `internal` cross-module sul tipo nominato da `WorldState.apply`.
2. **Niente `WriteResult.Rejected` / `state.write_rejected`**. Il rifiuto è compile-time. R9 verifica un solo `apply(AcceptedObservation)` e l'assenza di `write`.
3. **`may_commit` non era un campo nel tag T2 precedente**; hardening lo esclude dal modello e dallo schema (lo schema T1 `prediction.schema.json` è l'envelope `Prediction`, senza `may_commit`).
4. **`:prediction` non usa `CapabilityGraph` / `Transition`**. Usa `CapabilityHint` + overlay su `BeliefReader` (stesso ranking).
5. **Event log predittivo** è `PredictionEventLog` nel modulo prediction (non `EventLog` di world), così prediction non dipende da `a3.core.world`.
6. **One-step lookahead**, non un piano concatenato.
7. **`ProjectionCandidate` ≠ A3UI `Projection`**.
8. **Planner smart-cast** su `Fact.expiresAt`: variabile locale (proprietà public API di altro modulo). Semantica STALE invariata.

Nessuna di queste rende FAIL T1–T10 o P1–P11.

---

## Gate di rilascio

- `./gradlew test` green
- T1–T10 verdi
- P1–P11 verdi
- Barriera compile-time (ArchUnit + grep + unico `apply`)
- Questo file presente

**Definizione di fatto soddisfatta.** Tag `prediction-core-v0.1`.
