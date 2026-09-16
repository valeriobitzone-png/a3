<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# Projection Core

Normative rules for the presentation branch. Projection is off-path relative to the closed loop `Intent → Goal → State → Capability → Plan → Policy → Execution → Observation → State`.

**T3-INV:** Projection is a read-only transformer. It describes meaning and presentation intent. The renderer describes pixels. Projection never contains rendered output.

If projection is disabled, A3 remains fully functional.

## Taxonomy

| Type | Meaning | Owner |
|------|---------|--------|
| believed reality (`BeliefReader`) | what should become true | core world-api |
| possible reality | what could become true | prediction |
| speculative work | prepared prefetch | prediction |
| `PresentationState` | semantic presentation | projection |
| `Projection` | contextual presentation intent | projection |
| `RenderedOutput` | renderer-owned artifact | renderer (never a field of Projection) |
| Observation | measured reality | core |

## Read-only chain

```
BeliefReader --read--> PresentationState --contextualize--> Projection --consume--> Renderer --> RenderedOutput
possible-reality --> ProjectionCandidate --(expires | invalidates | waits for reality)
```

Compile-time: `:projection` depends only on `:core:world-api` and `:prediction`. It does not depend on `:core:world`, `:core:runtime`, `:a3ui`, or `:renderers`. Therefore it cannot name committed world apply, nor mint `AcceptedObservation`.

## Types

### CausalLineage

Answers “why am I showing this?”. Fields:

| Field | JSON | Role |
|-------|------|------|
| state identity | `state_identity` | context / believed-reality identity |
| state version | `source_state_version` | integer version at projection time |
| causal event | `causal_event_id` | event that produced this artifact |
| future state | `future_state_id` | set when sourced from possible reality |
| forecast | `forecast_id` | set when sourced from a forecast |

A belief-backed `Projection` always has identity, version, causal event. A `ProjectionCandidate` always has all five.

### PresentationState

Device-agnostic semantic snapshot. **JSON field `source_state_version` (integer). Never `state_ref`.**

Fields: `id`, `source_state_version`, `produced_at`, `atoms`, `lineage`.

Each `PresentationAtom`: `meaning`, `k`, `v`, `priority` (integer). No pixel, color, width, height, font, or density-as-pixels fields.

Atoms are ordered by ranking key: `priority` descending, `k` ascending, `meaning` ascending.

### FormFactorHints

`form_factor` ∈ `phone` | `tablet` | `car` | `glasses` | `desktop`.

`density` ∈ `compact` | `comfortable` | `spacious`.

No `minWidthDp`. Density is semantic, not a pixel budget.

### Projection

Contextual presentation intent: `id`, `presentation_id`, `context_ref`, `form_factor_hints`, `interaction_requirements` (sorted), `lineage`, `status` ∈ `proposed` | `ready` | `superseded`.

**No `rendered_output` field.** Renderers consume `PresentationState` and own `RenderedOutput`.

### ProjectionCandidate

Prefetch presentation from possible reality: `id`, `future_state_id`, `forecast_id`, `context_ref`, `presentation`, `base_state_version`, `status`, `expires_at`, `priority`, `rank`, `lineage`.

`status` ∈ `prepared` | `invalidated` | `expired`.

### RenderedOutput

`kind`, `body`. Produced only by a renderer. Never stored on Projection.

## Ranking

Deterministic, no ML, no HashMap/HashSet on the path (`TreeMap` / sorted lists).

Atom priority: `floor(confidence * 100)` for fact-derived atoms.

Atom order: `priority` desc, `k` asc, `meaning` asc.

Candidate order: `priority` desc (`floor(score * 1000)` from possible-reality score), `future_state_id` asc, `id` asc. `rank` is 1-based after sort.

## Invalidation

```
live(c, current_version) := c.status = prepared ∧ c.base_state_version = current_version ∧ (no expiry ∨ t < expires_at)
```

If `base_state_version != current_state_version`, the candidate is auto-invalidated (status `invalidated`). It waits for reality: it is not committed.

## Events

Append-only `ProjectionEventLog`. Types: `projection.updated`, `projection.candidate.updated`, `projection.invalidated`. Replay reconstructs last projection and candidates by id. Replay does not write believed reality.

## Canonical serialization

`a3.projection.serialize.CanonicalJson`: UTF-8 compact, keys sorted, atoms/candidates in ranked order, nulls omitted, injected clock/IDs never generated here. P13 and P20 compare UTF-8 bytes.

## Out of scope

Android, Compose, A2UI, MCP, AI, network, UI frameworks, `adapters/`, `a3ui/`, `renderers/` product code. T1 `schemas/projection.schema.json` remains the frozen 0.1 envelope (`state_ref`) used by core schema tests; T3 contracts are `presentationstate.schema.json`, `projectioncandidate.schema.json`, and `projection-core.schema.json`.
