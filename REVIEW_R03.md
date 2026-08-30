# REVIEW_R03 — ComposeCatalog paints Binding copy on item/action

**Unfrozen:** `:renderers:android-compose` (`ComposeCatalog.kt` + R9/R10 tests). Tag `renderer-android-v0.3`.  
**Frozen SOURCE intact:** `core/`, `prediction/`, `projection/`, `a3ui/`, `adapters/`, `intent-model/`, `launcher/`, `:renderers:android-core`.

Copy is `node.text` from interpret 0.2. Never invented. Foundation `BasicText` only. No Material `Text` / `Button`.

Screenshots (not in git):
- `<home>/Downloads/a3-r03-paint.png` — `08:45` / `09:12` / `10:03`, `Ada`, `12.40`, confirm atom `true`
- `<home>/Downloads/a3-r03-trust.png` — tap on `true` → overlay copy `approve`

---

## R9 — paint

**Invariant:** interpret 0.2 with list/item timetable Bindings + action confirm Binding → `BasicText` shows `09:12`, `10:03`, and the confirm atom (`true`).

**How:** catalog `BoundCopy` paints `node.text` when non-empty. `contentDescription` is that same string.

**Negative:** `onNodeWithText("Confirm")` does not exist.

**Result:** **PASS.** Emulator dump and `a3-r03-paint.png` show `09:12`, `10:03`, `true`. T8b F3 GAP is closed on pixels.

---

## R10 — no invented copy

**Invariant:** item/action without Binding keep empty `node.text`. No extra `BasicText`. No `Confirm` / `Conferma` / `loading` in `ComposeCatalog.kt`. `Text(` greps empty after excluding `BasicText`.

**How:** unbound surface in compose test + source walk.

**Result:** **PASS.**

---

## R11 — regression

Command: `./gradlew :renderers:android-compose:testDebugUnitTest :launcher:testDebugUnitTest --no-parallel`  
No `--rerun-tasks`. Host: Mini 16GB, 2026-08-30. Exit 0.

```
Renderer03ComposeAcceptanceTest > R9_item_and_action_paint_interpreter_copy PASSED
Renderer03ComposeAcceptanceTest > R10_unbound_item_and_action_do_not_invent_copy PASSED
PrefetchComposeCacheTest > P52_prefetch_cache_is_offscreen_and_respects_invalidation PASSED
RecompositionPurityTest > P55_recomposition_does_not_write_a3_state PASSED
Renderer02ComposeAcceptanceTest > R4_list_maps_to_lazy_column_of_boxes PASSED
Renderer02ComposeAcceptanceTest > R7_compose_main_stays_on_foundation_catalog PASSED
Renderer02ComposeAcceptanceTest > R6_compose_appliers_still_wrap_the_node_tree PASSED
Renderer02ComposeAcceptanceTest > R1_node_ir_compose_maps_stack_text_action PASSED
Renderer02ComposeAcceptanceTest > R5_field_maps_to_basic_text_field PASSED
Renderer02ComposeAcceptanceTest > R3_gesture_clickable_emits_action_name PASSED
BUILD SUCCESSFUL in 17s
```

`:launcher:testDebugUnitTest` — 18 tests PASSED (prior run in the same command, then UP-TO-DATE). Frozen launcher source not edited.

---

## F4 — tap (visual)

Tap the painted confirm atom (`true`, clickable Box `[0,569][126,695]`) → `TrustHoldOverlay` shows `approve` (`a3-r03-trust.png`, dump `text="approve"`). No invented Confirm button.

**Result:** **PASS.**

---

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| R9 | item/action paint interpreter copy | compose `onNodeWithText` + emulator | copy is atom `09:12`/`10:03`/`true`, not a catalog literal | `Confirm` assente | **PASS** |
| R10 | no invented labels | unbound nodes + grep catalog | empty `node.text` → no `BoundCopy` | Confirm/Conferma/loading assenti | **PASS** |
| R11 | compose + launcher tests | gradle without `--rerun-tasks` | 0.2 suite still green | aggregato `--rerun-tasks` not run | **PASS** |
| F4 | tap confirm → overlay | emulator tap on `true` | overlay is existing `approve`, not a new Button | dump contains `approve` | **PASS** |

**R0.3 GAP: nessuno.** F3 (T8b) closed in the catalog, not in `:launcher`.

---

## Grep / freeze

`git diff` on `core/ prediction/ projection/ a3ui/ adapters/ intent-model/ launcher/ renderers/android-core/` is empty.

```
grep Confirm/Conferma/loading ComposeCatalog.kt  → vuoto
grep Text( | grep -v BasicText                   → vuoto
```
