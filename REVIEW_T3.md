# REVIEW_T3 — A3 projection core 0.1 (read-only transformer)

**Gate:** T1–T10 + P1–P11 (re-audited, `--rerun-tasks`) then P12–P24 + barriere P11bis/P25/P26/P27.  
**T3-INV:** Projection is a read-only transformer. It describes meaning and presentation intent. The renderer describes the artifact. Projection never contains renderer output.  
**Tag:** `projection-core-v0.1` only if this file lists **zero GAP**.  
**Frozen:** `core-v0.1` (`be15393`), `prediction-core-v0.1` (`85d26b4`). `core/`, `prediction/`, `adapters/`, `renderers/` **not modified**.  
**No Android / Compose / A2UI / MCP / AI / network.**

---

## Esito test (output reale)

Comando: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-29. Exit code: 0.

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

> Task :projection:test

ProjectionArchitectureTest > P11bis_projection_does_not_depend_on_world_or_runtime PASSED

ProjectionAcceptanceTest > P14_presentation_state_is_device_agnostic() PASSED

ProjectionAcceptanceTest > P22_projection_does_not_carry_renderer_artifact() PASSED

ProjectionAcceptanceTest > P15_candidate_from_possible_reality_without_commit() PASSED

ProjectionAcceptanceTest > P24_causal_lineage_is_complete() PASSED

ProjectionAcceptanceTest > P20_canonical_determinism_presentation_and_candidate() PASSED

ProjectionAcceptanceTest > P12_projection_reads_belief_without_mutating_it() PASSED

ProjectionAcceptanceTest > P21_renderer_independence() PASSED

ProjectionAcceptanceTest > P17_form_factor_hints_use_semantic_density_only() PASSED

ProjectionAcceptanceTest > P13_presentation_state_is_deterministic() PASSED

ProjectionAcceptanceTest > P19_inherited_read_paths_still_compose() PASSED

ProjectionAcceptanceTest > P16_priority_ranking() PASSED

ProjectionAcceptanceTest > P26_presentation_state_has_no_raster_or_typography_fields() PASSED

ProjectionAcceptanceTest > P18_projection_event_log_is_replayable() PASSED

ProjectionAcceptanceTest > P23_candidate_auto_invalidated_when_base_version_differs() PASSED

ProjectionAcceptanceTest > P27_projection_type_rejects_renderer_payload() PASSED

ProjectionArchitectureTest > P11bis_gradle_module_does_not_depend_on_world_or_runtime() PASSED

ProjectionBarrierTest > P25_no_projection_method_mutates_believed_or_possible_reality() PASSED

BUILD SUCCESSFUL in 3s
18 actionable tasks: 18 executed
```

T1–T10: 10/10 PASSED. P1–P11: 11/11 PASSED. P12–P24 + P11bis + P25–P27: PASSED. Zero GAP.

---

## Protocollo audit (test verde ≠ invariante dimostrato)

Per ogni test: (1) percorso reale, nessuno stub della classe sotto test; (2) assert sull’invariante dichiarato; (3) non tautologico; (4) boundary di tipo/modulo dove pertinente.

---

## Tabella audit — T1–T10 (ereditati, `:core:runtime`, non modificati)

| Test | Invariante verificato | Percorso reale | PASS/GAP |
|------|----------------------|----------------|----------|
| T1 | piano Success verso `ticket.owned` con grafo calendar→train | `DeterministicPlanner.plan` | **PASS** |
| T2 | goal senza capability → `MissingCapability` | planner reale | **PASS** |
| T3 | vincolo `budget <= -1` → `ConstraintConflict` | planner reale | **PASS** |
| T4 | *nome* “missingPrecondition”; **assert = Success** perché `calendar.read` produce la precondizione | planner reale; **non** dimostra `MISSING_PRECONDITION` | **PASS†** |
| T5 | fatto con `expires_at < now` → `StaleState` | planner + `BeliefState` | **PASS** |
| T6 | closed-loop: observed==expected → `committed` e fatto presente | `Runtime.execute` | **PASS** |
| T7 | mismatch → rollback, non commit | `Runtime.execute` | **PASS** (versione non-decrement: **R4**) |
| T8 | IRREVERSIBLE senza grant → outcome `failed` | `Runtime.execute` | **PASS** |
| T9 | stesso input → stesso `PlanResult` UTF-8; HashMap shuffle irrilevante | planner + `CanonicalJson.planResult` | **PASS** |
| T10 | replay log + `replayState` bytes-equal allo stato committato | `EventLog` + runtime | **PASS** |

† T4 è una **deviazione di naming congelata in `core-v0.1`**. Non è un GAP T3: l’invariante `MISSING_PRECONDITION` è dimostrato da `InvariantReviewTest.missing_precondition_when_producer_blocked` (stesso `--rerun-tasks`). `core/` è immutabile in T3.

R1–R9 restano la prova strutturata delle invarianti T1 (canApply, confidence, stale, rollback, epistemica). Non sono GAP.

---

## Tabella audit — P1–P11 (ereditati, non modificati)

| Test | Invariante verificato | Percorso reale | PASS/GAP |
|------|----------------------|----------------|----------|
| P1 | possible-reality senza mutare committed world (version 0, bytes STATE) | `PredictionEngine.predict` + `WorldState` | **PASS** |
| P2 | nessun `apply(PreparedState)`; unico `apply(AcceptedObservation)` | reflection + token runtime | **PASS** |
| P3 | live iff `t < expires_at` | `PreparedStateStore` | **PASS** |
| P4 | nuovo context → `prediction.invalidated` | engine + store | **PASS** |
| P5 | ranking score desc / capability_ref asc / id asc; bytes canonici | `RuleBasedForecaster` | **PASS** |
| P6 | replay forecast/prepared | `PredictionReplay` | **PASS** |
| P7 | solo observation accepted scrive il world | `WorldState.apply` | **PASS** |
| P8 | candidate di prefetch, mai Outcome | `PredictionResult` (tipo T2, `uiStateHint`) | **PASS** |
| P9 | smoke T1 happy-path + apply dopo predict | **non** riesegue T2–T10 | **PASS††** |
| P10 | Forecast / possible-reality UTF-8 byte-for-byte | `CanonicalJson` prediction | **PASS** |
| P11 | `a3.prediction..` ↛ `a3.core.world` / `a3.core.runtime..` | ArchUnit | **PASS** |

†† P9 è uno smoke (congelato T2). La prova “T1–T10 + P1–P11 ancora verdi” è **P19 + questo `gradlew test --rerun-tasks`**, non P9 da solo. Non GAP: gli invarianti P1–P11 restano coperti dai test nominati.

---

## Tabella audit — P12–P24 + barriere

| Test | Invariante verificato | Come (non tautologico) | PASS/GAP |
|------|----------------------|------------------------|----------|
| P12 | Projection **legge** belief senza mutarlo | `BeliefReader` con `ArrayList` live; triple id/k/v/confidence invariate dopo `readBelief`; version 4; atomi `floor(confidence*100)`; nessun `apply`/`commit`/`write` su `ProjectionEngine` | **PASS** |
| P13 | `PresentationState` deterministico + schema `source_state_version` (mai `state_ref`) | due engine, stessi clock/ID; UTF-8 identici; JSON schema **rifiuta** `state_ref` additional | **PASS** |
| P14 | device-agnostic | stesso reader, density compact vs spacious → **stessi** bytes presentation, **diversi** bytes Projection; campi istanza hints = solo `formFactor`+`density` | **PASS** |
| P15 | candidate da possible-reality senza commit | `PredictionEngine.predict` reale; fatti/score del source immutati; `PreparedState` ancora live nello store; nessun `apply` | **PASS** |
| P16 | priority ranking | atomi 94/50/21 e ordine k atteso; candidate `fs_b` (0.9) poi tie `fs_a`/`fs_z` per id | **PASS** |
| P17 | density `compact\|comfortable\|spacious` | enum wire; JSON; `FormFactorHints("watch")` throws | **PASS** |
| P18 | event log replayable | bytes Projection e candidate dopo `ProjectionReplay` = originali | **PASS** |
| P19 | T1–T10 + P1–P11 ancora verdi **dopo** T3 | suite gradle (output sopra) + composizione `predict` → `readFutureStates` e `readBelief` su fatto T1-like | **PASS** |
| P20 | canonical determinism | HashMap shuffle dei fatti → stessi bytes presentation; candidate ripetuti byte-equal | **PASS** |
| P21 | renderer independence | `MockRendererA`/`B` puri (`fun interface`); output diversi; bytes `PresentationState` invariati | **PASS** |
| P22 | Projection non contiene renderer payload | nessun field `RenderedOutput`; schema additional property rifiutata; canonical senza chiave vietata | **PASS** |
| P23 | auto-invalidazione se `base_state_version != current` | `liveCandidate` → null; oggetto originale resta `PREPARED`; replay → `INVALIDATED`; expiry `t == expires_at` non live | **PASS** |
| P24 | causal lineage completa | belief: identity+version+causal, future/forecast null; candidate: tutti e cinque allineati al source | **PASS** |
| P11bis | `:projection` ↛ world/runtime/a3ui/renderers | ArchUnit + `build.gradle.kts` (`world-api` + `prediction` only) | **PASS** |
| P25 | nessun metodo muta believed/possible reality | reflection: niente apply/commit/write; tipi committed world assenti; possible-reality solo su metodi `read*` | **PASS** |
| P26 | PresentationState senza width/height/color/font / minWidth / raster fields | campi Java + JSON + schema classpath | **PASS** |
| P27 | Projection senza campo renderer payload | type + schema `projection-core.schema.json` | **PASS** |

**GAP: nessuno.**

---

## Prova della catena read-only

```
BeliefReader --read--> PresentationState --contextualize--> Projection --consume--> Renderer --> RenderedOutput
possible-reality --> ProjectionCandidate --(expires | invalidates | waits for reality)
```

| Anello | Prova |
|--------|--------|
| Gradle | `:projection` `api(project(":core:world-api"))` + `api(project(":prediction"))`. Nessun `project(":core:world")`, `:core:runtime`, `:a3ui`, `:renderers`. |
| Compile-time | P11bis ArchUnit: `a3.projection..` ↛ `a3.core.world` / `a3.core.runtime..`. Impossibile nominare `WorldState.apply` o mintare `AcceptedObservation`. |
| Read belief | P12: snapshot della lista fatti **prima/dopo** `readBelief`. |
| No mutate API | P25: surface pubblica senza apply/commit/write. |
| No commit da candidate | P15: store prediction ancora live; source facts invariati. |
| Invalidation waits | P23: version mismatch → non live, non committed. |
| Renderer consume | P21: `PresentationRenderer.render(PresentationState) → RenderedOutput`; presentation bytes immutati. |
| Projection ─X→ renderer payload | P22/P27. |
| Prediction ─X→ WorldState | invariato (P1/P2/P11, moduli frozen). |

Vietato a compile-time: Projection/Prediction/Renderer → WorldState. T3 aggiunge il taglio Projection ↛ world/runtime.

---

## Prova della causal lineage

Ogni artefatto risponde a “perché sto mostrando questo?”.

| Artefatto | `state_identity` | `source_state_version` | `causal_event_id` | `future_state_id` | `forecast_id` |
|-----------|------------------|------------------------|-------------------|-------------------|---------------|
| `PresentationState` / `Projection` da `readBelief` | `contextRef` | `reader.version` | id evento iniettato | assente | assente |
| `ProjectionCandidate` da `readFutureState` | `contextRef` | `currentVersion` (= `base_state_version`) | id evento iniettato | id del possible-reality | id forecast |

P24 asserisce i cinque campi sul candidate e i tre obbligatori sul belief-backed projection, con uguaglianza `projection.lineage == presentation.lineage`. Schema candidate: `lineage.forecast_id` e `lineage.future_state_id` **required**.

---

## Grep pre-tag

Eseguiti su **source** (`projection/src`, `projection/build.gradle.kts`) — entrambi **vuoti**:

```
grep -rn "WorldState\|BeliefState\|FutureState" projection/src projection/build.gradle.kts | grep -v "read\|import"
grep -rn "pixel\|minWidthDp\|rendered_output" projection/src projection/build.gradle.kts | grep -v "import\|//"
```

Il grep letterale su `projection/` **include `build/`**. I `.class` contengono il binary name `FutureState` (parametro di `readFutureState`) e costanti stringa concatenate dal compilatore. Non è source. La regola è rispettata sul tree versionato.

Ogni riga source che noma possible-reality contiene `read` o `import` (parametri `readSource` / `readSources`, `readConstruct`, `readFutureState`).

---

## Canonical JSON

`a3.projection.serialize.CanonicalJson`: UTF-8 compact, chiavi `TreeMap`, null omessi (tranne `v` degli atomi), clock/ID iniettati. Jackson **parse-only** per JSON Schema. P13/P20 confrontano byte arrays.

Percorso decisionale engine: `ArrayList`, `TreeSet`, `sortWith`. Nessun `HashMap`/`HashSet`. `TreeMap` solo in serialize/replay/schema cache.

---

## Deviazioni dichiarate

1. **T1 `schemas/projection.schema.json` resta l’envelope 0.1** (`state_ref`, form factor string). Congelato da `SchemaValidationTest`. I contratti T3 sono `presentationstate.schema.json`, `projectioncandidate.schema.json`, `projection-core.schema.json`. `PresentationState` usa **`source_state_version` (integer), mai `state_ref`**.
2. **T4 naming** (Success vs “missingPrecondition”): frozen `core-v0.1`. Invariante reale in `InvariantReviewTest`.
3. **P9 smoke** vs P19 suite: frozen T2. P19 = gradle `--rerun-tasks` + composizione read-path.
4. **Due tipi `ProjectionCandidate`**: T2 `a3.prediction.model.ProjectionCandidate` (`uiStateHint`) vs T3 `a3.projection.model.ProjectionCandidate` (presentation + lineage). P8 testa il primo; P15–P24 il secondo.
5. **Input belief = `BeliefReader` / `ReadBelief`**, non la classe `BeliefState` (inaccessibile da `:projection`). Semantica: stessa reality believed via world-api.
6. **`liveCandidate` non muta l’oggetto originale** (copy-on-invalidate via event log), analogo a T2 `peek` vs `get`.
7. **Mock renderer P21**: `fun interface` in test, zero framework UI.
8. **Schema validator duplicato** in `:projection` (non si importa `:core:runtime`).

Nessuna di queste è un GAP sugli invarianti T3.

---

## Gate di rilascio

- `./gradlew test --rerun-tasks` verde
- T1–T10 + P1–P11 verdi dopo audit
- P12–P24 verdi, percorso reale
- P11bis / P25 / P26 / P27 verdi
- Catena read-only dimostrata (Gradle + ArchUnit + P12/P15/P21/P25)
- Lineage dimostrata (P24 + schema)
- Grep source vuoti
- Questo file: zero GAP

**Definizione di fatto soddisfatta.** Tag `projection-core-v0.1`.
