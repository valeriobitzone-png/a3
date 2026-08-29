# REVIEW_T4 — A3 A3UI core 0.1 (declarative temporal intent)

**Gate:** T1–T10 + P1–P27 (re-audited, `--rerun-tasks`) then P28–P42 + barriere P11ter/P40/P41/P42.  
**T4-INV:** A3UI is a read-only compiler from `Projection` to `A3UISurface`. Motion/morph/gesture/haptics/prefetch are semantic data. No renderer-owned output. No world writes.  
**Tag:** `a3ui-core-v0.1` only if this file lists **zero GAP**.  
**Frozen:** `core-v0.1` (`be15393`), `prediction-core-v0.1` (`85d26b4`), `projection-core-v0.1` (`01441b3`). `core/`, `prediction/`, `projection/`, `adapters/`, `renderers/` **not modified**.  
**No Android / Compose / A2UI / MCP / AI / network.**

---

## Esito test (output reale)

Comando: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-29. Exit code: 0.

```
> Task :a3ui:test

A3UIArchitectureTest > P11ter_a3ui_does_not_depend_on_world_runtime_renderers_adapters PASSED

A3UIArchitectureTest > P42_a3ui_does_not_depend_on_prediction_domain_candidate PASSED

A3UIAcceptanceTest > P37_canonical_determinism_a3ui_surface() PASSED

A3UIAcceptanceTest > P36_inherited_read_paths_still_compose() PASSED

A3UIAcceptanceTest > P29_a3ui_surface_is_deterministic() PASSED

A3UIAcceptanceTest > P41_surface_has_no_raster_or_hardcoded_fill_fields() PASSED

A3UIAcceptanceTest > P35_surface_preserves_causal_lineage() PASSED

A3UIAcceptanceTest > P39_a3ui_does_not_mutate_projection_or_presentation() PASSED

A3UIAcceptanceTest > P28_a3ui_consumes_projection_without_mutating_it() PASSED

A3UIAcceptanceTest > P38_a3ui_does_not_produce_renderer_artifact() PASSED

A3UIAcceptanceTest > P34_prefetch_spec_is_projection_domain_and_invalidates() PASSED

A3UIAcceptanceTest > P30_motion_spec_is_declarative_data() PASSED

A3UIAcceptanceTest > P33_haptic_map_is_semantic() PASSED

A3UIAcceptanceTest > P31_morph_spec_shared_elements_are_declarative() PASSED

A3UIAcceptanceTest > P32_gesture_map_is_semantic() PASSED

A3UIArchitectureTest > P11ter_gradle_module_depends_only_on_projection_and_world_api() PASSED

A3UIBarrierTest > P40_no_a3ui_method_mutates_projection_presentation_or_believed_reality() PASSED

> Task :core:runtime:test
CoreAcceptanceTest T1–T10 PASSED
InvariantReviewTest R1–R9 + missing_precondition_when_producer_blocked PASSED
SchemaValidationTest PASSED
PredictionWorldGateTest P1 P2 P7 P9 PASSED

> Task :prediction:test
P3–P6 P8 P10 P11 PASSED

> Task :projection:test
P12–P27 P11bis P25 PASSED

BUILD SUCCESSFUL in 1m 33s
23 actionable tasks: 23 executed
```

T1–T10: 10/10. P1–P27: verdi. P28–P42 + barriere: verdi. **GAP: nessuno.**

---

## Protocollo audit

Per ogni test: (1) percorso reale, nessuno stub della classe sotto test; (2) assert sull’invariante dichiarato; (3) non tautologico; (4) boundary di tipo/modulo dove pertinente.

---

## Tabella audit — ereditati T1–T27

Stesso giudizio di `REVIEW_T3.md` (moduli frozen, non modificati). Suite `--rerun-tasks` verde.

| Nota | Trattamento T4 |
|------|----------------|
| T4 CoreAcceptance (`missingPrecondition` asserts Success) | **PASS†** naming congelato; invariante `MISSING_PRECONDITION` in `InvariantReviewTest` |
| P9 smoke vs T1–T10 completi | **PASS††** prova piena = questo gradle + P36 |
| Due tipi `ProjectionCandidate` | **intenzionale** (H3); T4 non unifica |

†/†† non sono GAP T4: non si può patchare T1–T3.

---

## Tabella audit — P28–P42

| Test | Invariante verificato | Come (non tautologico) | PASS/GAP |
|------|----------------------|------------------------|----------|
| P28 | A3UI legge `Projection` senza mutarla | `ProjectionEngine.readBelief` reale; bytes projection/presentation **prima/dopo** `compile` | **PASS** |
| P29 | `A3UISurface` deterministico | due compiler, stessi clock/ID, UTF-8 identici | **PASS** |
| P30 | `MotionSpec` è dato (stiffness/damping/curve/`duration_hint`) | tabella density → numeri; nessun metodo `animate`/`start`/`play` | **PASS** |
| P31 | morph shared-element dichiarativo | `to` = presentation id; shared = requirement o atom `k`; nessun campo x/y | **PASS** |
| P32 | gesture semantico | `swipe-left`→`dismiss`, `confirm`→`confirm`; campi solo gesture/action | **PASS** |
| P33 | haptic semantico | `light`/`medium`; nessun `Vibrator`/`perform` | **PASS** |
| P34 | PrefetchSpec sul candidate **projection**; invalidazione; linker rifiuta id ignoti; no world | `bind` version mismatch → `invalidated`; id non registrato throws; schema rifiuta `may_commit`; param FQCN `a3.projection.model.ProjectionCandidate` | **PASS** |
| P35 | lineage projection → presentation → state | `surface.lineage == projection.lineage`; refs id; prefetch espone future/forecast | **PASS** |
| P36 | T1–T27 ancora verdi | gradle `--rerun-tasks` (output sopra) + composizione engine→compiler | **PASS** |
| P37 | canonical bytes | HashMap shuffle requirements → stessi bytes; chiavi ordinate | **PASS** |
| P38 | A3UI non produce renderer artifact | return type `A3UISurface`; nessun field del tipo renderer | **PASS** |
| P39 | non muta Projection/Presentation / believed reality | bytes invariati; nessun parametro `Belief`+`State` | **PASS** |
| P11ter | `:a3ui` ↛ world/runtime/renderers/adapters | ArchUnit + `build.gradle.kts` solo `:projection` + `:core:world-api` | **PASS** |
| P40 | nessun metodo muta projection/presentation/belief | reflection apply/commit/write assenti | **PASS** |
| P41 | surface senza raster/hex/`rendered_*` | campi + JSON + schema; token `accent` | **PASS** |
| P42 | type boundary prediction candidate | ArchUnit FQCN prediction-domain candidate | **PASS** |

**GAP: nessuno.**

---

## Prova che A3UI è dichiarativo/semantico

| Superficie | Dato, non device |
|------------|------------------|
| Motion | `stiffness`/`damping`/`curve`/`duration_hint` scelti da `density_hint` |
| Morph | `mode=shared-element`, `shared` = id semantici |
| Gesture | vocabolario (`swipe-left` / `dismiss`) |
| Haptic | `pattern` + `intensity_hint` |
| Colore | token `accent` / `success` / `anticipation_highlight` |
| Prefetch | `candidate_ref` + version + ttl + status; **nessun** `may_commit` |

Nessuna API Android/Compose/animazione/aptica. `TemporalBuilder` e `DeterministicA3UICompiler` sono funzioni pure rispetto al world (side-effect solo registry in-memory del linker).

---

## Prova del TYPE BOUNDARY

Catena: possible-reality → `a3.prediction.model.ProjectionCandidate` (`uiStateHint`) → `Projection` → `a3.projection.model.ProjectionCandidate` → `PrefetchSpec`.

| Check | Evidence |
|-------|----------|
| Import | `:a3ui` non dipende da `:prediction` in Gradle |
| Bytecode | P42: nessuna classe `a3.a3ui..` dipende dal FQCN prediction-domain candidate |
| API compiler | `compilePrefetch` parametro = `a3.projection.model.ProjectionCandidate` (P34) |
| Runtime | `PrefetchLinker.bind` throws se `candidate_ref` non è stato `remember` (dominio projection) |
| Grep source | `prediction.model.ProjectionCandidate` **vuoto** in `a3ui/src` |
| Documented | `SCHEMA_HARDENING.md` H3 OPEN; T4 non unifica i due tipi |

`:prediction` resta visibile transitivamente via `:projection`; il confine è **uso del tipo**, non classloader isolation totale.

---

## Prova di lineage

`compile(projection)`: `projection_ref` = `projection.id`, `presentation_ref` = `presentationId`, `lineage` copiata (identity, `source_state_version`, causal event).

`compilePrefetch(candidate)`: `lineage` del candidate (cinque campi quando sourced da possible-reality), `presentation_ref` = `candidate.presentation.id`, `prefetch.candidate_ref` = `candidate.id`.

P35 asserisce uguaglianza strutturale, non un round-trip tautologico del valore appena costruito nel test di lineage sul path `readBelief`.

---

## Grep pre-tag (`a3ui/src`)

Tutti **vuoti**:

```
grep -rn "WorldState\|BeliefState\|FutureState" a3ui/src | grep -v "read\|import"
grep -rn "prediction.model.ProjectionCandidate" a3ui/src
grep -rn "pixel\|rendered_output\|#[0-9a-fA-F]\{6\}" a3ui/src | grep -v "import\|//"
```

Decision path: `TreeMap` / `TreeSet` / `ArrayList`. Nessun `HashMap`/`HashSet` in `a3ui/src/main`.

---

## Deviazioni dichiarate

1. **H3 duplicazione `ProjectionCandidate`**: intenzionale, pre-1.0; T4 osserva l’uso (PrefetchSpec) senza rename/merge.
2. **T4 CoreAcceptance naming** e **P9 smoke**: frozen T1/T2; non GAP.
3. **`compilePrefetch` non riceve `FormFactorHints`**: density prefetch = `comfortable` (candidate T3 non porta form factor). Live `compile` usa hints della Projection.
4. **`projection_ref` sul path prefetch** = `candidate.contextRef` (il candidate T3 non ha un `projection.id`). Lineage e `candidate_ref` restano la prova causale.
5. **Schema validator duplicato** in `:a3ui` (non si importa `:core:runtime`).
6. **`PrefetchLinker.remember` muta un registro in-memory**, non Projection/Presentation/belief.

Nessuna di queste è un GAP sugli invarianti T4.

---

## Gate di rilascio

- `./gradlew test --rerun-tasks` verde
- T1–T27 verdi dopo audit
- P28–P42 verdi, percorso reale
- Type boundary P42 + grep
- Lineage P35
- Questo file: zero GAP

**Definizione di fatto soddisfatta.** Tag `a3ui-core-v0.1`.
