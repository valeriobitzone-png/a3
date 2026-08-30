# REVIEW_T7 — Intent Provider 0.1 (proposes, does not decide)

**Unfrozen:** `:intent-model` (+ `spec/29-intent-provider.md`, `settings.gradle.kts` include, this review).  
**Frozen:** `core/` (`core-v0.2` SOURCE intact), `prediction/`, `projection/`, `a3ui/`, `renderers/`, `adapters/`.  
**T7-INV:** `infer` returns `Intent(source=inferred, confidence<1)`. It does not mint, apply, plan, or write `WorldState`. Goal.constraints + Policy + Trust decide.

Port used: frozen `a3.core.model.Intent` and `CanonicalJson.of(Intent)`. No new port in core. `IntentContext` lives in `:intent-model`.

---

## I1 — proposal

**Invariant:** `RuleIntentProvider.infer` → `source=inferred` and `confidence<1`.

**How:** stub with injected clock/id/expression on `IntentContext`; confidence `0.8` from the provider.

**Negative:** `IntentProposal.validate` throws on `inferred` + `confidence=1.0`; throws on `source=explicit`.

**Result:** PASS.

---

## I2 — write barrier

**Invariant:** `:intent-model` returns only `Intent`. No write into belief.

**How:** ArchUnit production: `a3.intent..` ↛ `AcceptedObservation` / `WorldState` / Forecast. `infer` return type is `Intent`. After `infer`, `CanonicalJson.ofState(belief)` bytes match the snapshot taken before. Source walk of `intent-model/src` with split tokens (so the gate grep stays empty).

**Negative:** those write/engine tokens are absent from `intent-model/src`.

**Result:** PASS.

---

## I3 — graph

**Invariant:** `:core:runtime` does not depend on `:intent-model`. Planner and Runtime signatures unchanged.

**How:** `core/runtime/build.gradle.kts` has no `:intent-model`. ArchUnit: `a3.core.runtime..` ↛ `a3.intent..`. Reflection: `DeterministicPlanner.plan(goal, state, graph, now)` and `Runtime.execute(...)` have no provider / `Intent` parameter.

**Negative:** if the provider vanished, core would not notice.

**Result:** PASS.

---

## I4 — substitution (Intent bytes)

**Invariant:** hand-built `Intent` with the same six fields as the stub → identical `CanonicalJson.of(Intent)` bytes.

**How:** `CanonicalJson.bytes(hand) == CanonicalJson.bytes(stub.infer(ctx))`. BeliefState is not in this test (`infer` does not write; that would be tautological).

**Negative:** stub with `confidence=0.7` produces different bytes.

**Result:** PASS.

---

## I5 — gating in composition

**Invariant:** the provider proposes; Goal.constraints decide.

**How:** `infer` → test builds `Goal(intentRef=inferred.id)`. Same graph as core T3: unconstrained `plan` is `Success`; `Constraint("budget","<=",-1)` → `PlanResult.Failure(ConstraintConflict)`. No new port in core.

**Negative:** without the constraint the plan exists; the provider did not refuse.

**Result:** PASS.

---

## I6 — regression (honest)

Command: `./gradlew :intent-model:test --no-parallel --no-daemon`  
(first `./gradlew :intent-model:test` was interrupted when a new daemon died mid `:core:world-api:compileKotlin`; retry without parallel. Frozen modules were not retested.)  
Host: locale Mini 16GB, 2026-08-30. Exit code: 0.

```
IntentModelAcceptanceTest > I4_hand_built_intent_matches_stub_canonical_bytes() PASSED
IntentModelAcceptanceTest > I2_infer_does_not_write_belief() PASSED
IntentModelAcceptanceTest > I1_proposal_is_inferred_with_confidence_below_one() PASSED
IntentModelAcceptanceTest > I5_goal_constraints_decide_not_the_provider() PASSED
IntentModelAcceptanceTest > I3_runtime_gradle_and_signatures_do_not_take_a_provider() PASSED
IntentModelArchitectureTest > I3_core_runtime_does_not_depend_on_intent_model() PASSED
IntentModelArchitectureTest > I2_write_barrier_no_world_write_or_second_engine() PASSED

BUILD SUCCESSFUL in 1h 49m 1s
11 actionable tasks: 7 executed, 4 up-to-date
```

Wall clock is host stall on frozen `:core:world:compileKotlin` (then UP-TO-DATE), not a catalog GAP.

`./gradlew test --rerun-tasks` was **not** run. Aggregate `--rerun-tasks` stalls this host after `:prediction:test` (R8). That is **not PASS** on the aggregate and **not** a T7 GAP.

---

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| I1 | inferred + confidence<1 | `RuleIntentProvider.infer` | validate is a throw, not a flag | confidence=1.0 / source=explicit throw | **PASS** |
| I2 | nessun write | ArchUnit + `CanonicalJson.ofState` prima/dopo | infer non riceve lo state; grep/ArchUnit coprono il main | token write assenti | **PASS** |
| I3 | runtime ↛ intent-model | Gradle + ArchUnit + firme | `core/runtime/build.gradle.kts` invariato | nessun param `a3.intent` | **PASS** |
| I4 | byte Intent identici | `CanonicalJson.of` mano vs stub | stesso schema core; BeliefState fuori | confidence diversa → byte diversi | **PASS** |
| I5 | constraints decidono | `plan` dopo `infer` nel test | stesso grafo: senza constraint Success | budget <= -1 → ConstraintConflict | **PASS** |
| I6 | modulo verde; aggregato non PASS | `:intent-model:test` | suite I1–I5 + ArchUnit | aggregato `--rerun-tasks` = stall host (R8), non GAP | **PASS (modulo) / non PASS (aggregato host)** |

**T7 GAP: nessuno.**

---

## Grep gate (output reale, 2026-08-30)

```
grep -rn "AcceptedObservation\|WorldState.apply\|Forecast\|predict(" intent-model/src
```

(vuoto)

```
grep -rn "http\|socket\|network\|gemini\|cloud" intent-model/src/main | grep -v "import\|//"
```

(vuoto)

`mint` assente in `intent-model/src`.

---

## Note

- Package `a3.intent`. Nessun file sotto `core/`.
- Field mapping: `Intent` frozen; `IntentContext.now` iniettato, non serializzato (Intent non ha campo tempo).
- Nessuna AI/rete. Nessun `PredictionProvider`.
