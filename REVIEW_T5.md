# REVIEW_T5 — A3 Android Renderer 0.1

**Frozen baselines (unmodified):** `core-v0.1` (`be15393`), `prediction-core-v0.1` (`85d26b4`), `projection-core-v0.1` (`01441b3`), `a3ui-core-v0.1` (`a54d85b`).  
**T5-INV:** the renderer is a read-only interpreter. Compose depends on A3UI interpretation; A3 does not depend on Compose.

---

## 1. P56 — Language Sufficiency

**Esito: (b) LANGUAGE GAP.**

The train-booking scenario was built on the **real** T4 path (`ProjectionEngine.readBelief` with `calendar.next` / `train.selected` / `ticket.owned` → `DeterministicA3UICompiler.compile` → `A3UIInterpreter.interpret`). The interpreter emitted tokens, density scale, spring params, shared-element **ids**, semantic gestures (`confirm`, `swipe-left`/`dismiss`), and haptics that A3UI 0.1 already names.

It did **not** emit timetable copy, stations, passenger fields, or widgets. Interpreter sources contain no `train.selected` / `ticket.owned` branches. Canonical `RenderedOutput` contains no `Milano` / `binario` / `timetable`.

**What A3UI 0.1 expresses (interpreted):** density hint, color tokens, motion, morph ids, gesture names, haptic hints, prefetch metadata, lineage.

**What A3UI 0.1 does not express (A3UI 0.2 candidates, listed in `SCHEMA_HARDENING.md`):** content/atoms on the surface; component vocabulary; layout/slots; copy bindings; input/validation; step graph; gesture-to-content targeting; content-bearing prefetch.

This (b) is the scientific result of T5, **not** a T5 GAP. Inventing that UI inside the renderer would have been a T5 GAP.

---

## 2. P55 — Recomposition Purity

**Test:** `RecompositionPurityTest.P55_recomposition_does_not_write_a3_state` (Robolectric + `createComposeRule`).

**How:** interpret once; snapshot UTF-8 bytes of `A3UISurface` and `RenderedOutput`; `ComposeRenderer(output)` with a `ticks` state read in composition; `SideEffect` increments a counter; **8** forced `ticks.intValue++` after first composition.

**Invariant:** `total >= afterFirst + 8` recompositions **and** surface bytes + output bytes unchanged.

**Negative:** the composable is given an already-interpreted `RenderedOutput`; it has no API to write projection/belief or to call prediction/observe.

**Result:** PASSED. Minimum **9** compositions (1 first + 8 forced). Byte-for-byte invariance of A3UISurface and RenderedOutput. Separation: A3 state → immutable surface → Compose → pixels, no reverse path.

---

## 3. P43 / P45 — dependency barrier + write barrier

**P43 (ArchUnit):** `a3.renderers..` ↛ `a3.core.world`, `a3.core.runtime..`, `a3.prediction..`.  
**P43 (Gradle):** `:renderers:android-core` `api(:a3ui)` only; no `:core:world`, `:core:runtime`, `:prediction`.  
**P43 (grep):** no `prediction.` in `renderers/*/src`.

**P45 (ArchUnit):** no dependency on `a3.core.world` + `Accepted`+`Observation`.  
**P45 (reflection):** `A3UIInterpreter` has no `apply`/`commit`; interpret returns renderer `RenderedOutput`; no world/accepted parameter types.  
**P45 (grep):** no `WorldState` / `BeliefState` / `FutureState` / `AcceptedObservation` in renderer source.

**P44 (terminal):** a3ui/projection/prediction/runtime Gradle files do not depend on `:renderers`; a3ui main sources do not name `a3.renderers.android.core.model.RenderedOutput`.

---

## 4. P52 — Prefetch

**Test:** `PrefetchComposeCacheTest.P52_prefetch_cache_is_offscreen_and_respects_invalidation` on the **real** `DeterministicA3UICompiler.compilePrefetch`.

| Path | Result |
|------|--------|
| prepared, `currentStateVersion == base` | cache hit, `get(candidate_ref)` returns output |
| `currentStateVersion != base` | `null` (invalidation) |
| candidate `INVALIDATED` | `null`, nothing stored |
| ttl 0 / expired | `null` |
| `apply` / `commit` on cache | absent |

Off-screen = interpret + `TreeMap` store. No world write.

---

## 5. Output reale della suite

Comando: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-29. Exit code: 0.

```
> Task :renderers:android-core:test

RendererArchitectureTest > P43_renderers_do_not_depend_on_world_runtime_or_prediction PASSED

RendererArchitectureTest > P45_renderers_do_not_depend_on_accepted_observation_type PASSED

RendererArchitectureTest > P45_no_apply_or_observation_token_on_interpreter() PASSED

RendererArchitectureTest > P44_rendered_output_is_terminal_and_not_consumed_by_a3_modules() PASSED

RendererArchitectureTest > P43_gradle_android_core_depends_only_on_a3ui() PASSED

RendererCoreAcceptanceTest > P49_morph_interpreter_keeps_semantic_ids_without_coordinates() PASSED

RendererCoreAcceptanceTest > P56_language_sufficiency_train_booking_is_a_declared_gap() PASSED

RendererCoreAcceptanceTest > P46_token_resolver_uses_injected_context_only() PASSED

RendererCoreAcceptanceTest > P51_haptic_interpreter_has_no_device_api_in_core() PASSED

RendererCoreAcceptanceTest > P50_gesture_interpreter_is_semantic_names_only() PASSED

RendererCoreAcceptanceTest > P53_inherited_compiler_path_still_composes() PASSED

RendererCoreAcceptanceTest > P47_density_resolver_maps_three_hints() PASSED

RendererCoreAcceptanceTest > P54_form_factor_independence() PASSED

RendererCoreAcceptanceTest > P48_motion_interpreter_is_pure_and_deterministic() PASSED

> Task :prediction:test
P3–P6 P8 P10 P11 PASSED

> Task :a3ui:test
P28–P42 P11ter P40 PASSED

> Task :projection:test
P12–P27 P11bis P25 PASSED

> Task :core:runtime:test
T1–T10, R1–R9, SchemaValidation, P1 P2 P7 P9 PASSED

> Task :renderers:android-compose:testDebugUnitTest

PrefetchComposeCacheTest > P52_prefetch_cache_is_offscreen_and_respects_invalidation PASSED

RecompositionPurityTest > P55_recomposition_does_not_write_a3_state PASSED

> Task :renderers:android-compose:testReleaseUnitTest

PrefetchComposeCacheTest > P52_prefetch_cache_is_offscreen_and_respects_invalidation PASSED

RecompositionPurityTest > P55_recomposition_does_not_write_a3_state PASSED

BUILD SUCCESSFUL in 1m 52s
89 actionable tasks: 89 executed
```

T1–T42 remain green (P53). P43–P56 green.

### Audit P43–P56 (invariante / come / non tautologico / negativo)

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| P43 | renderers ↛ world/runtime/prediction | ArchUnit + Gradle + grep | legge bytecode e `build.gradle.kts`, non un mock | dipendenza vietata non esiste | **PASS** |
| P44 | RenderedOutput terminale | Gradle moduli A3 + scan a3ui src | i moduli frozen non referenziano il tipo T5 | consumo da A3 assente | **PASS** |
| P45 | no apply / no accepted observation | ArchUnit + reflection interpreter | return type e parametri, non un flag | `apply`/`commit` assenti | **PASS** |
| P46 | token → colore da context | interpret train surface; source TokenResolver | colore 0,90,200 iniettato; JSON senza `#` | context vuoto → throw | **PASS** |
| P47 | density → scala | tre hint + interpret | 0.85/1.0/1.2 distinti | `"dense"` throw | **PASS** |
| P48 | MotionSpec → SpringParams puro | due interpret, canonical bytes | stiffness copiata dallo spec compilato T4 | no `animate`/`start` | **PASS** |
| P49 | morph = id semantici | MorphInterpreter su surface T4 | shared ordinati = spec; no x/y | nessun campo coordinata | **PASS** |
| P50 | gesture = nomi | GestureInterpreter | swipe-left/dismiss dal compiler T4 | no x/y | **PASS** |
| P51 | haptic semantico in core | interpret + walk `src/main` | pattern double-tap da T4 | Vibrator assente in main | **PASS** |
| P52 | prefetch off-screen, no commit | `compilePrefetch` reale + cache | hit vs version mismatch | INVALIDATED/ttl0 → null | **PASS** |
| P53 | T1–T42 verdi | `./gradlew test --rerun-tasks` | suite ereditata | — | **PASS** |
| P54 | due context, surface immutata | compact/phone vs spacious/tablet | scale e formFactor diversi; spring uguale | bytes surface identici | **PASS** |
| P55 | recomposition pura vs A3 | Robolectric ComposeRenderer | ≥9 composition; bytes surface+output | no predict API | **PASS** |
| P56 | language probe | compiler+interpreter treno | output senza copy inventata; hardening (b) | no workaround renderer | **PASS** (b) |

**T5 GAP: nessuno.** LANGUAGE GAP A3UI 0.2 è documentato, non è un GAP del renderer.

---

## 6. Deviazioni dichiarate

1. **P56 esito (b)** — atteso dalla spec T5; non è un fallimento del renderer.
2. **H3** ProjectionCandidate duplication — OPEN, non toccato.
3. **Due `RenderedOutput`:** T3 `a3.projection.model.RenderedOutput` (kind/body mock) resta frozen; T5 introduce `a3.renderers.android.core.model.RenderedOutput` (albero interpretato). Nessuna modifica a T3.
4. **`ComposeMorphApplier`** is a structural slot (shared-element APIs of Compose are richer than A3UI 0.1 ids). It does not invent booking widgets.
5. **`local.properties` / SDK** — machine-local, gitignored; not part of the tag tree.
6. **android-compose `testReleaseUnitTest`** duplicates debug unit tests (AGP default). Same assertions.
7. **Root `gradle.properties` jvmargs 1536m + AndroidX** — required to compile AGP; not a T1–T4 module change.
8. **Schema validator still duplicated** in a3ui (out of T5 scope).

Nessuna di queste viola T5-INV.

---

## 7. Git summary + tag

Staged tree (T5 only; `core/`, `prediction/`, `projection/`, `a3ui/`, `adapters/` unchanged vs `a3ui-core-v0.1`):

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` — include renderer modules, AGP/Compose plugins, AndroidX
- `spec/22-renderer-android.md`
- `SCHEMA_HARDENING.md` — LANGUAGE GAP A3UI 0.2 (H3 still OPEN)
- `renderers/android-core/**` — interpreter JVM
- `renderers/android-compose/**` — Compose materialization + P52/P55
- `REVIEW_T5.md`

`local.properties` is gitignored (SDK path).

**Tag:** `renderer-android-v0.1` on the T5 commit. Zero T5 GAP.
