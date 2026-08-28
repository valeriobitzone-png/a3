# REVIEW_T1 — A3 core 0.1

**Gate:** T1–T10 verdi + review 8/8 + invariante epistemico PASS.  
**Scope:** solo `core/`. Non toccati `adapters/`, `a3ui/`, `intent-model/`. Nessuna dipendenza AI/MCP/A2UI/network.  
**Tag:** `core-v0.1` (applicato solo dopo questo documento e i test verdi).

`PlanResult.Success` / `PlanResult.Failure` sostituisce la notazione spec `Result<Plan, PlannerError>` (Kotlin `Result<T>` è monoparametro). Vedi `CURSOR_T1.md`.

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

BUILD SUCCESSFUL in 6s
5 actionable tasks: 5 executed
```

T1–T10: 10/10 PASSED. Review R1–R9 + schema: PASSED.

---

## Tabella PASS/FAIL (8 criteri + invariante)

| # | Criterio | Cosa verificato | Test | Esito |
|---|----------|-----------------|------|-------|
| 1 | Precondizioni: `canApply ⟺` ogni fatto k=v presente **e** `validAt(now)` | `Transition.canApply`: `current(now)` (già filtrato da `validAt`) contiene k=v per ogni precondizione. Assente → false; scaduto → false; k=v valido anche con confidence bassa → true; valore diverso → false. Confidence **non** è un gate di `canApply`. | `R1_preconditions_canApply_iff_validAt` | **PASS** |
| 2 | Confidence: `min(conf precond) × reliability` | `calendar.next` conf 0.5, reliability 0.8 → effetto `train.selected` conf 0.40. Precondizioni vuote: min = 1.0. | `R2_confidence_min_precond_times_reliability` | **PASS** |
| 3 | Fact history: effetto contraddittorio SUPERSEDE, mai overwrite silenzioso | Due Observation su `ticket.owned` true poi false: storia size=2, `superseded_by` valorizzato sul precedente, current = false, version 2. | `R3_contradictory_effect_supersedes_never_silent_overwrite` | **PASS** |
| 4 | Rollback compensativo: mai decrementare `state.version`. v_n → mismatch → v_{n+1} compensativo | Due commit (v2) poi mismatch: rollback → version **3** (> 2). Fatti precedente supersedati; restore come nuovi fatti `source=rollback` se lo stato da ripristinare non è vuoto; storia nel log (`execution.rolled_back`, `state.updated`). | `R4_compensatory_rollback_never_decrements_version`, `T7_loopMismatch` | **PASS** |
| 5 | STALE_STATE: planner rifiuta piani su fatti **richiesti** con `expires_at < now` | T5: `calendar.next` expired → `StaleState`. `observed_at > now` **non** è STALE. Fatto scaduto non richiesto (`weather.temp`) → Success. `expires_at == now` → escluso da `validAt` ma **non** STALE (`< now`). | `T5_staleState`, `R5_stale_state_only_when_expires_at_before_now` | **PASS** |
| 6 | Constraint solving: vincoli Goal; violazione → CONSTRAINT_CONFLICT | `budget <= -1` con `cost.money = 0` → `ConstraintConflict`. Ops `<= < >= > == =` su `budget` / `timeMin` / fatti numerici. | `T3_constraintConflict`, `R6_constraint_violation_is_constraint_conflict` | **PASS** |
| 7 | Determinismo: niente random / iterazione non ordinata; stesso input → stesso piano | Clock e ID iniettati/derivati (mai `Instant.now()` / UUID). Capability ordinate con `TreeMap`. Confronti byte-for-byte su canonical JSON. Stesso piano anche se il grafo arriva come `HashMap`. | `T9_determinism`, `R7_determinism_canonical_bytes` | **PASS** |
| 8 | Replay: ricostruzione da eventi == stato originale | `EventLog.replayState` piega i payload `state.updated`; canonical STATE bytes identici, stessa `version`. | `T10_replay`, `R8_replay_state_equals_original` | **PASS** |
| 9 | **Invariante epistemico R1–R5:** solo `Observation.accepted` scrive `WorldState` | `PredictionWrite` / `PolicyWrite` / `ExecutionWrite` → `WriteResult.Rejected`, evento `state.write_rejected`, version resta 0. `world.write(OBSERVATION_ACCEPTED, …)` → Accepted, version 1. | `R9_epistemic_only_observation_accepted_writes` | **PASS** |

Closed-loop T6/T7: `observed.k=v == expected.k=v` → commit; diverso → rollback (`committed=false`, `rolledBack=true`).

---

## Canonical serialization

Definizione normativa: `a3.core.serialize.CanonicalJson`.

1. UTF-8, compact, nessuno whitespace, nessun BOM.
2. Chiavi oggetto ordinate lessicograficamente (`String.compareTo`, code unit UTF-16).
3. Collezioni non ordinate (fatti) ordinate per `(k, observed_at, id, source)`. Step di piano ordinati per `seq`. `deps` / `interaction_requirements` ordinati.
4. `Instant` → ISO-8601 `Instant.toString()` (UTC, suffisso `Z`).
5. Campi opzionali null **omessi**.
6. Numeri: valori interi come integer; altrimenti `Double.toString()`.
7. Stringhe JSON-escaped; boolean `true`/`false`.
8. Profilo **SCHEMA** (validazione JSON Schema): omette `id` e `superseded_by` sui fatti (gli schema hanno `additionalProperties: false`).
9. Profilo **STATE** (replay): include `id` e `superseded_by` per round-trip della storia.
10. Clock e ID **non** sono generati dal serializer; i caller li iniettano o li derivano deterministicamente.
11. Path decisionale: `TreeMap` / `TreeSet` / liste ordinate. Nessun `HashMap`/`HashSet` nel planner, nelle transizioni, nel merge, nel runtime.

Confronto T9/T10: `assertContentEquals(canonical.toByteArray(UTF_8), …)`.

ID deterministici:

| Entità | Forma |
|--------|--------|
| Piano | `plan_${goal.id}` |
| Fatto (se `id` vuoto) | `fact_v${version}_${index}_${k}` |
| Evento runtime | `ev_${seq}_{start\|commit\|state\|rollback\|trust}` |
| Compensazione | Observation `obs_rollback_${plan.id}` |

Clock: parametro `Instant now` su `plan` / `execute`. `FixedClock` / `SequentialIdGenerator` in `a3.core.time` per i caller. Mai `Instant.now()` nel core.

---

## Validazione schema

Ogni modello di dominio è serializzato in canonical SCHEMA JSON e validato contro il file in `schemas/`:

| Modello | Schema |
|---------|--------|
| Goal | `goal.schema.json` |
| Intent | `intent.schema.json` |
| Plan | `plan.schema.json` |
| WorldStateSnapshot | `state.schema.json` |
| TrustGrant | `trust.schema.json` |
| Outcome | `outcome.schema.json` |
| Observation | `observation.schema.json` |
| Capability | `capability.schema.json` |
| Prediction | `prediction.schema.json` |
| Projection | `projection.schema.json` |

Jackson è usato **solo** per parsare schema e istanza; i byte canonici sono prodotti da `CanonicalJson`.

---

## Deviazioni dichiarate

1. **`PlanResult` vs `Result<Plan, PlannerError>`** — obbligatorio in Kotlin; documentato in `CURSOR_T1.md`.
2. **`Fact.id` / `Fact.supersededBy`** — metadati interni di storia, assenti dagli schema JSON; profilo SCHEMA li omette, profilo STATE li include.
3. **`Plan.totalCost`** — presente in memoria, assente da `plan.schema.json` (`additionalProperties: false`) quindi omesso dal JSON canonico.
4. **`TrustGrant.expiresAt`** — usato dal `TrustGate` a runtime; lo schema espone `ttl`, non `expires_at`. Il JSON canonico omette `expiresAt`.
5. **STALE_STATE è `expires_at < now`**, non `<=`. Allineato al requisito T5. A `expires_at == now` il fatto non è `validAt` ma non emette STALE_STATE; il planner può riprodurlo (es. `calendar.read`).
6. **STALE_STATE solo se un fatto *richiesto* da una precondizione del grafo è scaduto** senza sostituto `validAt`. Fatti scaduti non richiesti non bloccano il piano (spec: “when a plan requires them”).
7. **`canApply` non filtra sulla confidence** della precondizione. La confidence entra solo in `min(conf) × reliability`.
8. **Una capability al massimo una volta per piano** (BFS 0.1). Sufficiente per T1–T10.
9. **Constraint solver 0.1:** chiavi `budget`, `time`/`timeMin`, oppure valore numerico di un fatto. Operatori sconosciuti → nessuna violazione.
10. **T4_missingPrecondition** asserisce `Success`: `calendar.read` produce la precondizione di `train.search`. `MISSING_PRECONDITION` è coperto da `missing_precondition_when_producer_blocked` (solo `train.search`, stato vuoto).
11. **Runtime** aggiorna una copia `BeliefState` solo tramite `ObservationAcceptance` dopo match observed/expected. Il gate di store `WorldState.write` rifiuta Prediction/Policy/Execution; l’esecuzione non ha pathway di scrittura diretta.
12. **Counterfactual planning** (spec 18) è fuori dal gate 0.1.
13. **Jackson** non è un provider AI; serve solo alla validazione schema.
14. **Planner simulation** chiama `ObservationAcceptance` su copie di pianificazione, mai sul `WorldState` committed.

Nessuna di queste deviazioni rende FAIL un criterio della tabella.

---

## Gate di rilascio

- T1–T10 verdi
- 8/8 + invariante epistemico PASS
- Questo file presente

**Definizione di fatto soddisfatta.** Tag `core-v0.1`. MCP, A3UI e AI restano fuori scope.
