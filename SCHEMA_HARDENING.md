# Schema / semantic-boundary hardening (pre-1.0)

Items here are **intentional** and must not be “cleaned up” during a tagged phase
unless the phase explicitly schedules the resolution.

## H3 [OPEN — pre-1.0] ProjectionCandidate semantic boundary

`a3.prediction.model.ProjectionCandidate`  = speculative prediction artifact (`uiStateHint`)
`a3.projection.model.ProjectionCandidate`  = semantic presentation candidate
                                             (`PresentationState` + lineage + expiry)

Catena: `FutureState` → `prediction.candidate` → `Projection` → `projection.candidate` → A3UI `PrefetchSpec`

La duplicazione è INTENZIONALE: fonderla ora farebbe collassare epistemologia
e presentazione nello stesso oggetto. Risoluzione (rename o unificazione)
solo dopo aver osservato l'uso reale in T4/T5.

T4 rule: `:a3ui` imports `ProjectionCandidate` **only** from `a3.projection.model`.
`PrefetchSpec.candidate_ref` refers exclusively to that type. Do not unify or rename
either type in T4.

T5 did not unify the two types (H3 remains OPEN).

## LANGUAGE GAP [A3UI 0.2] — T5 probe: prenotazione treno

**Esito P56: (b) LANGUAGE GAP.** A3UI 0.1 is not sufficient to declare a live train-booking interface. The renderer interprets only what 0.1 already names. It does not invent the missing semantics.

### What A3UI 0.1 *does* express (and T5 interprets)

- `density_hint` (`compact` | `comfortable` | `spacious`)
- semantic `color_tokens` (`accent`, `success`, `anticipation_highlight`)
- `MotionSpec` (stiffness / damping / curve / duration hint)
- `MorphSpec` shared-element **ids** (not geometry)
- `GestureMap` opaque names (`swipe-left` → `dismiss`, `confirm` → `confirm`)
- `HapticMap` pattern + intensity hint
- `PrefetchSpec` metadata (candidate ref, version, ttl, status)
- causal `lineage` (identity, version, event, optional future/forecast refs)

### What A3UI 0.1 does *not* express (candidates for 0.2)

1. **Content / atoms on the surface.** Presentation facts (`calendar.next`, `train.selected`, `ticket.owned`, copy, values) live on `PresentationState` and are **not** fields of `A3UISurface`. A booking UI cannot declare “Milano→Roma 08:30” from A3UI 0.1 alone.
2. **Component vocabulary.** No list, row, card, field, button, stepper, or timetable widget type.
3. **Layout / slots / hierarchy.** No structure beyond a flat token/motion/gesture bundle.
4. **Text catalog / i18n bindings.**
5. **Input and validation** (station pickers, passenger count, payment).
6. **Step / navigation graph** (search → results → confirm).
7. **Gesture targets.** Actions are names, not bindings to content nodes.
8. **Content-bearing prefetch.** Prefetch is status metadata, not a second surface of facts.

Closing these gaps belongs to A3UI 0.2, not to renderer special-cases.

## A3UI 0.2 — payload closed (a); frozen renderer remains (b)

A3UI 0.2 expresses the train scenario as `Node` / `Binding` / `GestureBinding` on `A3UISurface`. Copy is the injected atom value (`BindingCopy`), not compiler-invented text. The T5 renderer is still 0.1 and may ignore `nodes[]`. Historical P56 stays **(b) LANGUAGE GAP**: (b)→(a) is payload expressibility, not new pixels.
