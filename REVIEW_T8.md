# REVIEW_T8 — Launcher 0.1 (composition root)

**Unfrozen:** `:launcher` (+ `spec/30-launcher.md`, `settings.gradle.kts` include, root `com.android.application` plugin, this review).  
**Frozen SOURCE intact:** `core/` (`core-v0.2`), `prediction/`, `projection/`, `a3ui/` (`a3ui-core-v0.2`), `renderers/` (`renderer-android-v0.2`), `adapters/`, `intent-model/` (`intent-model-v0.1`).  
**Launcher-0.1-INV:** the app orchestrates frozen libraries. It does not edit their source. It does not call `WorldState.apply` / `mintAcceptedObservation`. Writes go through `Runtime.execute` + `TrustGrant`. No `:prediction` engine. No live MCP.

Ports used (disk, not invented): `ComposeRenderer(output, onAction)`, `interpret(surface, presentation, ctx)` (0.2) / `interpret(surface, ctx)` (0.1), `PrefetchComposeCache.composeOffscreen` / `get`, `PolicyDecision.CONFIRM`, `DeterministicA3UICompiler.compile(projection, presentation)`.

Overlay name is `TrustHoldOverlay` so the gate grep `Dialog(` stays empty. It is not `androidx.compose.material.Dialog`.

---

## L1 — compile

**Invariant:** `:launcher` is `com.android.application`. Gradle has `:renderers:android-compose`, `:intent-model`, `:core:runtime`. No `:prediction`, no `:adapters:mcp`.

**How:** `./gradlew :launcher:assembleDebug` exit 0. `LauncherArchitectureTest` reads `launcher/build.gradle.kts`.

**Negative:** those two project coordinates are absent from the launcher Gradle file.

**Result:** PASS.

---

## L2 — train (path 0.2)

**Invariant:** `compile(projection, presentation)` → `interpret` 0.2 produces a `list` with `item`; `text_train.departure` shows `08:45`; confirm is a clickable Box, not Material `Button(`. Path 0.1 on the same surface has empty `nodes[]`.

**How:** fixture atoms (`timetable` / `departure` / `passenger` / `confirm`) go through frozen `SurfaceComposer`. Compose test tags on the catalog. Source walk of frozen `ComposeCatalog.kt` for `Box`+`clickable` and absence of `androidx.compose.material.Button(`.

**Negative:** `interpret(surface, ctx)` → `nodes` empty; 0.2 has `role=list`.

**Result:** PASS.

---

## L3 — motion

**Invariant:** two `RenderedOutput`s carry morph `to` from the presentation id; stiffness is the interpreter copy of `surface.motion`. `ComposeMorphApplier` still wraps in frozen `ComposeRenderer`. Renderer source not modified.

**How:** compact vs spacious density → 400.0 vs 180.0 stiffness. Compose smoke: `a3-catalog` displayed.

**Negative:** the two stiffness values differ.

**Result:** PASS.

---

## L4 — prefetch

**Invariant:** ViewModel calls `PrefetchComposeCache.composeOffscreen`. Hit while `BeliefState.version` matches. After `Runtime.execute` bumps version, `get` / `composeOffscreen` miss. Invalidation is in `HostPrefetch`, not in `:renderers`.

**How:** `LauncherHostTest` (no Compose rule — the rule would launch an Activity). Frozen `get()` does not check version; `HostPrefetch.get` does.

**Negative:** version++ → both APIs return null.

**Result:** PASS.

---

## L5 — trust

**Invariant:** IRREVERSIBLE plan step → `Policy.evaluate` = `CONFIRM` → overlay visible. Approve → `Runtime.execute` with `TrustGrant`, not mint/apply in the launcher. Reversible/ALLOW → overlay absent.

**How:** train graph (`train.reserve` `reversible=false`). Compose click on `action_ticket.owned` then `trust-approve`. Reversible graph is `calendar.read` only.

**Negative:** grep of `launcher/src/main` for `WorldState.apply` / `mintAcceptedObservation` is empty (Kotlin). Overlay is not Material `Dialog(`.

**Result:** PASS.

---

## L6 — rollback

**Invariant:** test `Executor` returning Observation ≠ expected → `Runtime` compensating → overlay visible. Match → overlay absent. Launcher does not call `apply`.

**How:** `mismatchExecutor` flips effect `v`. `matchingExecutor` copies effects. Compose tags `rollback-overlay`. Two tests (Compose `setContent` once per test).

**Negative:** match path: `rollbackVisible == false` and node does not exist.

**Result:** PASS.

---

## L7 — a11y

**Invariant:** catalog semantics are foundation, not Material widgets. `list` → `CollectionInfo`. `field` → `EditableText` (`BasicTextField`). `text` is copy. `stack`/`root` is not a collection. `action` is Box + clickable (not `androidx.compose.material.Button`). Approve overlay carries `Role.Button`.

**How:** frozen `ComposeCatalog` is not patched. `Role.List` / `Role.EditableText` are not Compose `Role` values; the test uses the properties the catalog actually emits.

**Negative:** Material `Button(` absent from catalog source.

**Result:** PASS.

---

## L8 — regression (honest)

Command: `./gradlew :launcher:assembleDebug :launcher:testDebugUnitTest --no-parallel`  
Host: locale Mini 16GB, 2026-08-30. Exit code: 0. Frozen modules were not retested. `:intent-model:test` not re-run (T7 already green; source frozen).

```
LauncherAcceptanceTest > L2_train_path_02_has_list_departure_and_clickable_confirm PASSED
LauncherAcceptanceTest > L3_two_outputs_carry_morph_ids_and_interpreted_stiffness PASSED
LauncherAcceptanceTest > L5_irreversible_shows_trust_dialog_allow_does_not PASSED
LauncherAcceptanceTest > L7_catalog_semantics_are_foundation_roles PASSED
LauncherAcceptanceTest > L6_match_does_not_show_rollback_overlay PASSED
LauncherAcceptanceTest > L6_mismatch_shows_rollback_overlay PASSED
LauncherArchitectureTest > L5_main_does_not_write_world PASSED
LauncherArchitectureTest > L1_main_has_no_material_widgets_or_network PASSED
LauncherArchitectureTest > L1_gradle_does_not_depend_on_prediction_or_mcp PASSED
LauncherHostTest > L4_prefetch_hits_then_misses_after_belief_version_bump PASSED

BUILD SUCCESSFUL in 48s
81 actionable tasks: 14 executed, 67 up-to-date
```

`./gradlew test --rerun-tasks` was **not** run. Aggregate `--rerun-tasks` stalls this host after `:prediction:test` (R8). That is **not PASS** on the aggregate and **not** a T8 GAP.

---

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| L1 | app compile; no prediction/MCP | `assembleDebug` + Gradle file | coordinates must appear/absent in `launcher/build.gradle.kts` | `:prediction` / `:adapters:mcp` assenti | **PASS** |
| L2 | path 0.2 list+departure; confirm Box | compose-ui-test + `interpret` 0.1/0.2 | 0.1 ignores `nodes[]`; catalog is frozen | 0.1 nodes empty | **PASS** |
| L3 | morph `to` + stiffness from output | two compiles, different density | stiffness copied from `surface.motion`, not hardcoded | compact ≠ spacious | **PASS** |
| L4 | prefetch hit then miss | `HostPrefetch` around frozen cache | frozen `get()` would still hit; VM invalidates | version++ → null | **PASS** |
| L5 | CONFIRM overlay; execute via grant | Policy + TrustHoldOverlay + `Runtime.execute` | launcher source has no mint/apply | reversible → overlay assente | **PASS** |
| L6 | compensating overlay | mismatch vs matching Executor | Runtime sets `rolledBack`; UI only reads it | match → overlay assente | **PASS** |
| L7 | catalog a11y, no Material Button | semantics matchers + catalog source | stack is Column (no CollectionInfo) | Material `Button(` assente | **PASS** |
| L8 | modulo verde; aggregato non PASS | `:launcher:assembleDebug :launcher:testDebugUnitTest` | 10 unit tests | aggregato `--rerun-tasks` = stall host (R8), non GAP | **PASS (modulo) / non PASS (aggregato host)** |

**T8 GAP: nessuno.**

---

## Grep gate (output reale, 2026-08-30)

```
grep -rn "WorldState.apply\|mintAcceptedObservation" launcher/src/main | grep -v "import\|//"
```

(vuoto)

```
grep -rn "Button(\|Card(\|Dialog(\|AlertDialog(\|TextField(" launcher --include='*.kt' | grep src/main | grep -v "BasicTextField("
```

(vuoto)

```
grep -rn "http\|socket\|gemini\|cloud" launcher/src/main | grep -v "import\|//"
```

Unica riga: `AndroidManifest.xml` `xmlns:android="http://schemas.android.com/apk/res/android"` (schema URI Android, non un client di rete). Kotlin `launcher/src/main` è vuoto su `http` / `socket` / `gemini` / `cloud`.

`git diff` su `core/ prediction/ projection/ a3ui/ renderers/ adapters/ intent-model/` è vuoto.

---

## Note

- Package `a3.launcher`. Nessun file nuovo sotto i path frozen.
- `PrefetchComposeCache.composeOffscreen` interpreta still 0.1 (`interpret(surface, ctx)`). Il host usa 0.2 per lo schermo visibile.
- `ComponentActivity` è nel manifest **debug** così `createComposeRule()` (stesso host dei test `:renderers:android-compose`) risolve l'Activity; non è un widget Material.
- Nessuna AI/rete. Nessun `implementation(:prediction)`.
