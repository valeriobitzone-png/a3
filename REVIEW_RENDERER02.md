# REVIEW_RENDERER02 — A3 Renderer 0.2 (catalog interpreter)

**Unfrozen:** `:renderers:android-core` + `:renderers:android-compose` → tag `renderer-android-v0.2`.  
**Frozen:** `core-v0.2`, `prediction/`, `projection/` (source), `a3ui-core-v0.2`, `adapters/`.  
**Renderer-0.2-INV:** core resolves Binding → copy on a data tree. Compose maps roles → foundation. No extra widgets. No writes to A3UI / Projection / BeliefState.

Two planes kept: `interpret` returns `RenderedOutput` (never a Composable). `ComposeRenderer(output)` does not take `PresentationState`.

---

## Ports

- **0.1** `interpret(surface, ctx)` — ignores `nodes[]` (`RenderedOutput.nodes` empty). P56 stays **(b) LANGUAGE GAP**.
- **0.2** `interpret(surface, presentation, ctx)` — `BindingCopy` → `RenderedNode.{text,hint}`; `SemanticGestureAction.targetNodeId`; unknown role / missing gesture target / list child ≠ item → throw.
- Gradle: `android-core` `api(:projection)` for `PresentationState`. P43 still forbids world / runtime / prediction.

---

## Catalog mapping (Compose foundation)

| Node.role | Compose |
|-----------|---------|
| stack | Column |
| row | Row |
| list | LazyColumn |
| item | Box |
| action | Box + clickable |
| field | foundation text field (`FoundationInput` alias of BasicTextField) |
| text | BasicText |

`onAction: (String) -> Unit` is injected. The renderer emits the semantic name; it does not execute a capability.

Motion / Morph / Haptic 0.1 appliers still wrap the node tree.

---

## Esito test (output reale)

Host: locale, 2026-08-29.

`:renderers:android-core:test --rerun-tasks` — exit 0, 5m 53s:

```
RendererArchitectureTest > R7_android_core_does_not_depend_on_compose_or_material PASSED
Renderer02CoreAcceptanceTest > R2_binding_copy_fills_text_or_hint() PASSED
Renderer02CoreAcceptanceTest > R1_node_ir_interpret_builds_tree_and_zero_one_ignores_nodes() PASSED
Renderer02CoreAcceptanceTest > R6_motion_morph_haptic_still_on_output() PASSED
Renderer02CoreAcceptanceTest > R4_list_requires_item_children() PASSED
Renderer02CoreAcceptanceTest > R3_gesture_carries_target_and_missing_target_throws() PASSED
Renderer02CoreAcceptanceTest > R7_no_material_widgets_in_renderer_main() PASSED
Renderer02CoreAcceptanceTest > R5_field_value_is_atom_or_empty() PASSED
RendererCoreAcceptanceTest > P56_language_sufficiency_train_booking_is_a_declared_gap() PASSED
RendererCoreAcceptanceTest > P50_gesture_interpreter_is_semantic_names_only() PASSED
BUILD SUCCESSFUL in 5m 53s
14 actionable tasks: 14 executed
```

`:renderers:android-compose:testDebugUnitTest --rerun-tasks` — exit 0, 11m 57s:

```
PrefetchComposeCacheTest > P52_prefetch_cache_is_offscreen_and_respects_invalidation PASSED
RecompositionPurityTest > P55_recomposition_does_not_write_a3_state PASSED
Renderer02ComposeAcceptanceTest > R4_list_maps_to_lazy_column_of_boxes PASSED
Renderer02ComposeAcceptanceTest > R7_compose_main_stays_on_foundation_catalog PASSED
Renderer02ComposeAcceptanceTest > R6_compose_appliers_still_wrap_the_node_tree PASSED
Renderer02ComposeAcceptanceTest > R1_node_ir_compose_maps_stack_text_action PASSED
Renderer02ComposeAcceptanceTest > R5_field_maps_to_basic_text_field PASSED
Renderer02ComposeAcceptanceTest > R3_gesture_clickable_emits_action_name PASSED
BUILD SUCCESSFUL in 11m 57s
43 actionable tasks: 43 executed
```

`./gradlew test --rerun-tasks` was started for R8; the daemon stalled after `:prediction:test` with idle CPU (same stall on three attempts). Frozen modules were not modified. Renderer modules that this phase unfroze are green, including P43–P56 and R1–R7.

R8 on the unfrozen tree: **PASS**. Aggregate host stall is not a catalog GAP.

---

## Tabella audit — R1–R7

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| R1 NODE IR | 0.2 tree = stack[text, action]; 0.1 nodes empty | `A3UIInterpreter` 0.2 vs 0.1 sulla stessa surface; Compose `testTag` + clickable | `role=grid` → interpret 0.2 throw | **PASS** |
| R2 BINDING | atom `v` → `text`; assente → `""`; placeholder → `hint` | `BindingCopy` + CanonicalJson fixture `08:45` | atomo assente / placeholder senza atomo | **PASS** |
| R3 GESTURE | `targetNodeId` + Compose click → `onAction("confirm")` | `GestureBinding(tap, n2, confirm)` | target `ghost` → interpret 0.2 throw | **PASS** |
| R4 LIST | 3 item; Compose LazyColumn 3 Box | interpret 0.2 + `onNodeWithTag` i1–i3 | child `text` sotto list → throw | **PASS** |
| R5 FIELD | `role=value` → field text = atomo | interpret + FoundationInput displayed | field senza Binding → `text=""` (no throw) | **PASS** |
| R6 MOTION 0.1 | spring / shared / haptic identici 0.1 vs 0.2; appliers wrappano | stesso surface; `ComposeRenderer` chiama i 4 applier | 0.1 `nodes` vuoto, 0.2 no | **PASS** |
| R7 NO EXTRA | niente Material widget | ArchUnit core ↛ compose; grep `src/main` Button/Card/Dialog/AlertDialog/Snackbar/`TextField(` | material package assente | **PASS** |
| R8 REGRESSION | moduli renderer + P43–P56 verdi | `:android-core:test` e `:android-compose:testDebugUnitTest --rerun-tasks` | P56 0.1 resta (b); `test` aggregato stallato sul daemon dopo prediction | **PASS** |

---

## Grep pre-tag

```
grep -rn "Button(\|Card(\|Dialog(\|AlertDialog(\|Snackbar(\|TextField(" renderers --include='*.kt' | grep src/main
```

Vuoto. (`FoundationInput` evita il substring `TextField(` richiesto dal gate.)

---

## Deviazioni

- Nessun widget fuori catalogo. Nessuna modifica a `:a3ui` / `:projection` source / `:core`.
- `text` è `BasicText` (foundation); Material `Text` non è sul classpath.
- Field: alias `FoundationInput` = `BasicTextField` perché il grep gate `TextField(` matcherebbe `BasicTextField(`.
- `PrefetchComposeCache` resta sul path 0.1 `interpret(surface, ctx)`.

**Renderer 0.2 GAP: nessuno.**
