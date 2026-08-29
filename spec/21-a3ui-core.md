# A3UI Core

Normative rules for temporal and visual **intent**. A3UI is off-path relative to
`Intent → Goal → State → Capability → Plan → Policy → Execution → Observation → State`.

**Rule:** A3UI describes temporal and visual intent. The renderer describes the artifact.

**T4-INV:** A3UI is a read-only compiler from `Projection` to `A3UISurface`.
It describes motion, morph, gesture, haptics, and prefetch as **semantic data**.
It does not produce renderer-owned output, does not mutate projection or belief,
and does not write committed reality. Prefetch prepares a surface to show.
Reality enters only via observation accepted (outside this module).

If A3UI is disabled, A3 remains fully functional.

## Gradle

`:a3ui` depends only on `:projection` and `:core:world-api`.
It does not depend on `:core:world`, `:core:runtime`, `:renderers`, or `:adapters`.
`:prediction` may appear on the classpath transitively via `:projection`.
**Type boundary:** no class in `a3.a3ui..` may depend on
`a3.prediction.model.ProjectionCandidate` (import, alias, or wrap).

See `SCHEMA_HARDENING.md` H3.

## Types

### A3UISurface

Declarative temporal description: `id`, `projection_ref`, `presentation_ref`,
`lineage` (causal: identity, `source_state_version`, event, optional future/forecast),
`density_hint` ∈ `compact` | `comfortable` | `spacious`,
`color_tokens` (semantic only: `accent`, `anticipation_highlight`, `success`, …),
`motion`, `morph`, `gestures`, `haptics`, optional `prefetch`, `produced_at`.

No hardcoded hex. No extent fields. No renderer payload.

### MotionSpec

Physics as data: `stiffness`, `damping`, `curve`, `duration_hint` (milliseconds, a hint).
No device animation API.

### MorphSpec

Shared-element interpolation: `to`, `mode`, `shared` (semantic ids), `motion`.

### GestureMap

Semantic vocabulary (`swipe-left` → `dismiss`). No coordinate fields.

### HapticMap

Semantic events (`pattern`, `intensity_hint`). No device haptic API.

### PrefetchSpec

Bound to `a3.projection.model.ProjectionCandidate`:
`candidate_ref`, `base_state_version`, `confidence`, `ttl_ms`, `status`
∈ `prepared` | `invalidated` | `expired`.

No `may_commit`. Invalidated when `base_state_version != current_state_version`
(via `PrefetchLinker`) or when the candidate status/expiry already says so.

`PrefetchLinker` refuses at runtime any `candidate_ref` that is not registered
in the projection domain.

## Compiler

```
interface A3UICompiler {
  fun compile(projection: Projection): A3UISurface
  fun compilePrefetch(candidate: ProjectionCandidate): A3UISurface
}
```

The `ProjectionCandidate` parameter is exclusively `a3.projection.model.ProjectionCandidate`.
Both methods are read-only. Clock and IDs are injected.

## Canonical JSON

UTF-8 compact, sorted keys, injected clock/IDs. P29 and P37 compare bytes.

## Out of scope

Android, Compose, A2UI, MCP, AI, network, UI frameworks, `adapters/`, `renderers/` product code.
