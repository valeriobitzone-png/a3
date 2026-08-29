# REVIEW_A3UI02 — A3UI 0.2 closed catalog (P56 b→a)

**Unfrozen:** `:a3ui` → tag `a3ui-core-v0.2` (+ `spec/26-a3ui-02.md`, `schemas/a3uisurface.schema.json`, `SCHEMA_HARDENING.md` 0.2 note, this review).  
**Frozen:** `core/` (`core-v0.2`), `prediction/`, `projection/`, `renderers/` (`renderer-android-v0.1`), `adapters/` (`mcp-adapter-v0.1`).  
**A3UI-0.2-INV:** Node = role. Binding = `atom.k` → `node.id`. Gesture has `targetNodeId`. Hierarchy = children. Extra `*Spec` = GAP.

Port 0.1 kept: `compile(projection: Projection)`.  
Port 0.2: `compile(projection, presentation: PresentationState)` with frozen `a3.projection.model.PresentationState`. `:projection` not modified.

---

## P56 (b)→(a) on the payload

T5 P56 remains **(b) LANGUAGE GAP** on the frozen renderer: `interpret` still does not read `nodes[]`, and `SCHEMA_HARDENING.md` still contains `(b) LANGUAGE GAP` / `prenotazione treno`.

A3UI 0.2 closes **expressibility**. P63 injects a `PresentationState` with timetable / station / passenger / price atoms (copy lives on atoms, not in `a3ui/src/main`). The compiled `A3UISurface` contains:

| Need | Payload |
|------|---------|
| timetable | `list` of `item` + `Binding` `content` |
| select | `row` → `[text, action]` + `GestureBinding` `select` |
| passenger | `field` + `Binding` `value` |
| price | `text` + `Binding` `content` |
| confirm | `action` + `GestureBinding` `confirm` with `targetNodeId` in the tree |

Copy is `BindingCopy.of(binding, presentation)` = atom `v`, or empty if the atom is absent. Milano is not in the compiler and not in CanonicalJson of the surface. The 0.1 renderer may ignore `nodes[]`.

---

## Esito test (output reale)

Comando: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-29. Exit code: 0.

```
A3UI02AcceptanceTest > P59_hierarchy_is_children_not_layout() PASSED
A3UI02AcceptanceTest > P63_train_scenario_is_expressible_from_injected_atoms() PASSED
A3UI02AcceptanceTest > P58_binding_copy_is_atom_value_or_empty() PASSED
A3UI02AcceptanceTest > P60_gesture_target_must_exist_in_tree() PASSED
A3UI02AcceptanceTest > P62_no_extra_spec_types() PASSED
A3UI02AcceptanceTest > P57_role_catalog_is_closed() PASSED
A3UI02AcceptanceTest > P64_steps_are_a_tree_not_a_state_machine() PASSED
A3UI02AcceptanceTest > P61_prefetch_atom_keys_without_second_surface() PASSED

A3UIAcceptanceTest P28–P41 PASSED
A3UIBarrierTest P40 PASSED
RendererCoreAcceptanceTest > P56_language_sufficiency_train_booking_is_a_declared_gap() PASSED

T1–T10, W1–W5, V1–V4 PASSED
P1–P56, S1–S3 PASSED (S4 = this gradle gate)
P52 P55 PASSED (debug + release)

BUILD SUCCESSFUL in 16m 33s
93 actionable tasks: 93 executed
```

P65 = this command. **GAP: nessuno.**

---

## Tabella audit — P57–P64

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| P57 ROLE CATALOG | schema accetta solo i 7 `role` | JSON v0.2 + `$ref` node; enum su `Node.role` | `grid` / `modal` / `tooltip` → `validateCanonical` throw | **PASS** |
| P58 BINDING | atomo presente → copy = `v`; assente → vuoto | `compile(projection, presentation)` reale; `BindingCopy`; clock/ID iniettati; CanonicalJson A==B | chiave assente / `role=placeholder` non inventa testo; surface JSON senza `42.00` | **PASS** |
| P59 HIERARCHY | `row > [text, action]` via `children` | atomi `station`+`select` → `SurfaceComposer` | additionalProperty `layout` → schema reject | **PASS** |
| P60 TARGET | `targetNodeId` ∈ tree | compile 0.2 con atomo `confirm`; `collectIds` | `interactionRequirements=confirm` + presentation vuota → compile throw (`target`) | **PASS** |
| P61 PREFETCH | `PrefetchSpec.atomKeys` opzionale | `compilePrefetch` su candidate con `ticket.owned` | schema `additionalProperties: false` rifiuta `prefetchContent` / `prefetchData` / `prefetchSurface` | **PASS** |
| P62 NO EXTRA SPEC | solo `MotionSpec` / `MorphSpec` / `PrefetchSpec` | walk `a3ui/src` `class *Spec` | `Input`/`Validation`/`Layout`/`Content`/`Node`/`Binding` `*Spec`, `StepGraph`, `prefetchSurface` assenti; `HapticMap` resta | **PASS** |
| P63 TRAIN (b→a) | scenario treno = Node/Binding/GestureBinding | test **inietta** PresentationState (orario/stazione/passeggero/prezzo); compile 0.2 | grep `a3ui/src/main`: vuoto su widget / Vi+ew / @Compos+able / `#hex` / and+roid / Milano | **PASS** |
| P64 STEPS | tutti i passi nel tree, non state machine | tre atomi `step` → `stack` di `item` + `next-step` / `confirm` | token `Step`+`Graph` assente in `a3ui/src` | **PASS** |
| P65 | suite ereditata verde | `./gradlew test --rerun-tasks` | — | **PASS** |

---

## Grep pre-tag (`a3ui/src`)

```
grep -rn "class .*Spec" a3ui/src | grep -v "MotionSpec\|MorphSpec\|PrefetchSpec"
grep -rn "InputSpec\|ValidationSpec\|StepGraph\|LayoutSpec\|ContentSpec\|prefetchSurface" a3ui/src
grep -rn "i18n\|formatter" a3ui/src/main
grep -rn "View\|@Composable\|android\." a3ui/src | grep -v "import\|//"
```

Tutti vuoti.

---

## Deviazioni

- Nessun `*Spec` extra. Nessun `prefetchSurface`. Nessuna modifica a `:projection` / `renderers/`.
- `compile(projection)` 0.1 resta: non vede gli atomi; se ci sono gesture temporali, emette `Node(id=root)` come target. Non throw se manca un action node 0.2 — quello è il path 0.2.
- Copy non è un campo del JSON: è l'atomo iniettato. Il payload esprime lo scenario come albero + binding keys.
- `SCHEMA_HARDENING.md` tiene il testo storico **(b) LANGUAGE GAP** (P56 renderer). Sotto: nota 0.2 (a) sul payload.
- `MotionSpec` / `MorphSpec` / `HapticMap` immutati. Non esiste `temporal-components`. Non esiste `HapticSpec`.

**A3UI 0.2 GAP: nessuno.**
