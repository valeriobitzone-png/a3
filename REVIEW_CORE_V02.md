# REVIEW_CORE_V02 — closed-loop writer on the path

**Unfrozen:** `core/` only → tag `core-v0.2`.  
**Frozen (untouched):** `prediction/`, `projection/`, `a3ui/`, `renderers/`, `adapters/` vs `renderer-android-v0.1`.  
**T5-INV unchanged:** renderer remains a read-only interpreter.

The single-writer barrier is now true **on the execute path**, not only on the `WorldState` class.

---

## 1. W1 — commit goes through `WorldState.apply`

**Invariant:** after a successful execute, `world.committed.version = prev + N` (N = committed steps, each apply is +1), `ExecutionResult.state === world.committed`, and that state equals an **independent** `WorldState.apply` of the minted observations. The event log contains `observation.accepted` as the causal parent of each `state.updated` version bump.

**How:** happy-path train plan (3 steps). Runtime mints with the real `PolicyDecision` and calls `world.apply`. Reconstruction is `WorldStateReplay.replay` (fresh world, fold of apply). Bytes compared with `CanonicalJson.bytesState`.

**Not tautological:** the independent world never went through `Runtime.execute`. If execute still merged on a detached copy, `observation.accepted` would be absent and reconstruction would stay at version 0.

**Negative:** trust-denied T8 does not emit `observation.accepted`; version stays 0.

**Result:** PASS. Three `observation.accepted` events, versions 1–2–3, each `state.updated.causalId` is the accepted event id.

---

## 2. W2 — rollback mints compensating accepted, version monotonic

**Invariant:** mismatch → mint compensating observation → `WorldState.apply` with `IntegrateMode.COMPENSATE` → new version, never decrement; superseded facts remain in history.

**How:** two successful steps then mismatch (same as R4). Independent `WorldStateReplay.replay` reads `integrate_mode=COMPENSATE` from the event (not from `obs_rollback_` prefix). Assert versions `[1,2,3]`, superseded `calendar.next` / `train.selected`, no live `ticket.owned=true`.

**Not tautological:** SUPERSEDE_KEYS on an empty restore would leave the two commits live at v3; COMPENSATE through the gate is required for byte match.

**Negative:** first-step mismatch also applies compensating (empty restore on empty world → v1); T7 asserts `result.state === world.committed` and independent apply bytes.

**Result:** PASS. R4 still version 3.

---

## 3. W3 — single public committed write

**Invariant:** the only production call that mutates `WorldState.committed` is `WorldState.apply(AcceptedObservation)`. The former public belief-write `ObservationAcceptance.apply` is gone.

**How:**
- `Class.forName("a3.core.world."+"ObservationAcceptance")` → `ClassNotFoundException`
- reflection: one `apply`, parameter `AcceptedObservation`; no `write`; `committed` field not public
- ArchUnit (production classes only): no `a3.core..` class except `Runtime` and `WorldStateReplay` calls `WorldState.apply`
- ArchUnit: `Runtime` and `WorldStateReplay` **do** call `WorldState.apply`; `EventLog` does not
- source: `Runtime.kt` contains `world.apply(` and `mintAcceptedObservation`; no `.integrate(` on the execute path

**Negative:** planner, `Transition`, `EventLog` cannot call `WorldState.apply` (ArchUnit). `EventLog` is a log (list reader). Replay folds apply in `:core:runtime` on a **fresh** world.

**Grep (empty):** `grep -rn "ObservationAcceptance.apply" core/*/src | grep -v "WorldState"`

**Result:** PASS.

---

## 4. W4 — T6 / T7 assert the committed world, not a detached copy

**T6:** `result.state === world.committed`; version and UTF-8 STATE bytes equal `WorldStateReplay.replay`. Ticket fact present.

**T7:** rollback flags; `result.state === world.committed`; bytes equal independent apply of the compensating accepted observation.

**Not tautological:** identity with `world.committed` plus a second `WorldState` instance. A copy that happened to merge the same facts would fail `===`.

**Result:** PASS.

---

## 5. W5 — planner stays on the copy

**Invariant:** planning is counterfactual. The planner does not mint `AcceptedObservation` and does not call `WorldState.apply`.

**How:** `DeterministicPlanner.plan` on `world.committed` leaves version 0, STATE bytes unchanged, empty event log. `Transition.apply` produces a v1 **copy** while the same `WorldState` stays at v0 with no `observation.accepted`. ArchUnit: `a3.core.planner` / `a3.core.capability` neither depend on `AcceptedObservation` nor call `WorldState.apply`. Source has no `mintAcceptedObservation` / `WorldState.apply`.

**Negative:** if the planner minted and applied, W5 bytes and ArchUnit would fail.

**Result:** PASS.

---

## 6. W6 — T1–T56 regression

Command: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-29. Exit code: 0.

```
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

InvariantReviewTest > R1–R9 PASSED
SchemaValidationTest > every_model_validates_against_its_schema() PASSED

WriterArchitectureTest > W3_single_entry_world_state_apply_is_the_only_committed_write() PASSED
WriterArchitectureTest > W5_planner_and_transition_do_not_mint_or_call_world_apply() PASSED
WriterPathTest > W2_rollback_mints_compensating_accepted_and_bumps_monotonically() PASSED
WriterPathTest > W1_commit_goes_through_world_state_apply() PASSED
WriterPathTest > W5_planner_stays_on_copy_and_does_not_mint_or_apply() PASSED

ReplayWriterTest > V1_every_accepted_event_carries_integrate_mode_and_main_does_not_branch_on_rollback_prefix() PASSED
ReplayWriterTest > V3_replay_fold_matches_committed_bytes_for_supersede_and_compensate() PASSED
ReplayWriterTest > V2_apply_is_the_only_writer_event_log_does_not_apply_or_integrate() PASSED
ReplayWriterTest > V4_accepted_without_mode_or_unknown_mode_throws_and_does_not_guess() PASSED

PredictionWorldGateTest > P1 P2 P7 P9 PASSED

> Task :prediction:test
P3–P6 P8 P10 P11 PASSED

> Task :projection:test
P11bis P12–P27 P25 PASSED

> Task :a3ui:test
P11ter P28–P42 P40 PASSED

> Task :renderers:android-core:test
P43–P51 P53 P54 P56 PASSED

> Task :renderers:android-compose:testDebugUnitTest
P52 P55 PASSED

> Task :renderers:android-compose:testReleaseUnitTest
P52 P55 PASSED

BUILD SUCCESSFUL in 8m 35s
89 actionable tasks: 89 executed
```

T1–T56 remain green. W1–W5 green.

### Audit W1–W6

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| W1 | commit = apply(minted) | execute + second WorldState | reconstruction from log, not `result.state` vs itself | no accepted events if gate skipped | **PASS** |
| W2 | rollback = apply(compensating) | 2 commits + mismatch | COMPENSATE required for bytes; versions 1,2,3 | SUPERSEDE would leave live facts | **PASS** |
| W3 | unica write WorldState = apply | ArchUnit + ClassNotFound + grep | production bytecode, not a mock | ObservationAcceptance class assente | **PASS** |
| W4 | T6/T7 = committed world | `===` + independent apply | identity + second instance | detached copy fails `===` | **PASS** |
| W5 | planner su copia | plan/Transition vs live WorldState | world bytes frozen; ArchUnit | mint/apply would emit accepted | **PASS** |
| W6 | T1–T56 | `./gradlew test --rerun-tasks` | suite ereditata | — | **PASS** |

**Writer GAP: nessuno.**

---

## Replay-writer (chiude core-v0.2)

`EventLog` is a log. `observation.accepted` carries `integrate_mode` on the envelope. `WorldStateReplay.replay` creates a **fresh** `WorldState` and folds `WorldState.apply`. T10 and R8 use that fold. P6/P18 untouched.

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| V1 | ogni accepted ha `integrate_mode`; main non brancha sul prefisso | execute + walk `src/main` | schema + source, non un mock | `startsWith`+prefisso assente | **PASS** |
| V2 | apply è l'unico writer; EventLog non apply/integrate | ArchUnit W3 + source EventLog / WorldStateReplay | bytecode + file | EventLog senza `integrate(` | **PASS** |
| V3 | SUPERSEDE + COMPENSATE → bytes = committed | execute mismatch + `WorldStateReplay.replay` | replica ≠ `world.committed`; CanonicalJson | mode letto dall'evento | **PASS** |
| V4 | missing/unknown mode → throw | log sintetico `NORMAL` / mode assente | non defaulta SUPERSEDE | non indovina dal payload id | **PASS** |
| V5 | T1–T10, P1–P56, W1–W5, V1–V4 | `./gradlew test --rerun-tasks` | suite completa | — | **PASS** |

R2 (non gate): `DECISIONS.md` — futuro: `WorldState.apply` live rifiuta duplicate event id.

---

## 7. Deviazioni dichiarate (non GAP)

1. **`ObservationAcceptance` rimosso**, non reso `internal`. Il merge su copie è `BeliefState.integrate` (planner / `Transition`). Non assegna `WorldState.committed`. Replay committed è `WorldStateReplay` (fold di `apply`), non `EventLog`.
2. **Mismatch al primo step** ora passa dal gate compensativo (v1 su world vuoto). Prima si saltava l'apply se `version == before`. Allineato al contratto ROLLBACK della spec; T7 non asseriva version 0.
3. **`Runtime.execute` prende `WorldState`**, non una `BeliefState` staccata. `ExecutionResult.state` è `world.committed`.
4. **`WriteResult.Accepted` include `acceptedEventId`** per la catena causale `observation.accepted` → `execution.committed` / `execution.rolled_back`.
5. **`mintAcceptedObservation(observation, policyDecision, …)`** è il mint del loop. `acceptedObservation` resta un wrapper ALLOW per P2/P7/R9 (type-gate, non il closed loop).
6. **H3 / consolidamento validator/CanonicalJson / `missingPrecondition`:** non fatti. Aggiunti `event.schema.json`, `ModelValidator.event`, e `integrate_mode` sull'encoding Event (campo envelope, non un merge di CanonicalJson).
7. **AGP `testReleaseUnitTest`** duplica P52/P55 (invariato da T5).
8. **`obs_rollback_`** resta naming cosmetico dell'observation id. Il mode è sull'evento.

Nessuna di queste riapre un write laterale sul world committed.

---

## 8. Git summary + tag

Solo `core/` (+ questa review a root):

- `WorldState.apply` emette `observation.accepted` con `integrate_mode` poi `state.updated`
- `WorldStateReplay` fold di apply su world fresco; `EventLog.replay()` è solo lista
- test W1–W5, V1–V4; T10/R8 sul fold
- `Runtime` minta e chiama `world.apply` su commit e rollback
- planner / `Transition` restano su `BeliefState.integrate`
- test W1–W5; T6/T7 aggiornati (W4)

`git diff renderer-android-v0.1 -- prediction projection a3ui renderers adapters` è vuoto.

**Tag:** `core-v0.2` sul commit writer. Zero GAP.
