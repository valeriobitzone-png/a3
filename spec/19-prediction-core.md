# Prediction Core

Normative rules for the optional predictive branch. The closed loop remains `Intent → Goal → State → Capability → Plan → Policy → Execution → Observation → State`. Prediction is off-path.

**Invariant:** Prediction prepares but never commits. Prepared artifacts are not WorldState. Only `Observation.accepted` may write committed belief.

If prediction is disabled, A3 remains fully functional.

## Types

`BeliefState` and `PreparedState` are distinct types. There is no `PreparedState.commitToWorldState()`. A FutureState is a hypothesized snapshot, not a belief merge and not an `Outcome`.

### Forecast

A `Forecast` is a ranked list of `ForecastCandidate` values for one context.

Fields: `id`, `context_ref`, `context_signature`, `candidates`, `produced_at`, optional `policy_ref`.

`candidates` is ordered by the ranking key below. It does not contain `Outcome`.

### ForecastCandidate

Fields: `id`, `future_state_id`, `capability_ref`, `score`, `rank`.

`rank` is 1-based after stable sort. `score` is produced by the scoring rule, not by a model.

### FutureState

A `FutureState` is a one-step hypothesized snapshot derived from current `BeliefState` by simulating a capability that `canApply` now.

Fields: `id`, `context_ref`, `capability_ref`, `facts`, `score`, `produced_at`.

Simulation uses the same transition operator as planning, on a **copy**. It must not call `WorldState.write`. Facts are the simulated `current(now)` list (sorted). A FutureState is not `BeliefState` and is not `Outcome`.

### PreparedState

A `PreparedState` is the prefetch artifact selected from a forecast (the top-ranked FutureState). It may be used to prepare projection off-path.

Fields: `id`, `forecast_id`, `future_state_id`, `context_ref`, `context_signature`, `facts`, `status`, `prepared_at`, `expires_at`, `ttl_seconds`.

`status` ∈ `prepared` | `expired` | `invalidated`.

Expiry is evaluated against the injected clock (see TTL). Invalidation is an explicit status transition (see PredictionInvalidation).

### PredictionPolicy

Fields: `id`, `ttl_seconds`, `max_candidates`, `min_score`.

`ttl_seconds` sets `expires_at = prepared_at + ttl`. `max_candidates` truncates after ranking. Candidates with `score < min_score` are dropped before ranking.

### PredictionStatus

Wire values: `prepared`, `expired`, `invalidated`.

### ProjectionCandidate

Prediction **may** produce a `ProjectionCandidate` from a FutureState (`id`, `future_state_ref`, `context_ref`, `ui_state_hint`). It is a prefetch hint, not A3UI rendering, not MCP, and **never** an `Outcome`.

## Scoring and ranking

Rule-based, no ML, no LLM, no network.

One-step lookahead only: each currently applicable capability is simulated independently from the same current belief (not a chained plan; planning remains the planner).

```
score(C, G) = reliability(C) / (1 + money(C) + timeMin(C)) × boost(C, G)

boost(C, G) = 1 + G.priority   if G is present and C.effects overlap G.desired_state on (k, v)
            = 1               otherwise
```

Ranking key, stable:

1. `score` descending
2. `capability_ref` ascending
3. `id` ascending

Capability iteration uses `TreeMap` order of capability ids. IDs and clock are injected (`SequentialIdGenerator`, `InstantSource`). No `Instant.now()`, no UUID, no `HashMap`/`HashSet` on the decision path.

## TTL

```
expires_at := prepared_at + ttl_seconds
live(p, t) := p.status = prepared ∧ prepared_at <= t < expires_at
```

`PreparedStateStore.get(id)` returns the value iff `live`. Otherwise null.

- `t == expires_at` → not live (same convention as fact `validAt`: `t < expires_at`).
- Expiry is a function of the injected clock at read time; the store does not write WorldState.
- An expired prepared state is not revived by a later clock step backward in production use; tests may set the clock.

## PredictionInvalidation

A new context invalidates previous live prepared state.

Context identity is `(context_ref, context_signature)`. Signature is the canonical concatenation of `context_ref`, goal id (or empty), intent id (or empty), and current facts ordered by `(k, id)` as `k=v` joined by `;`.

When `predict` is invoked with a different pair than a stored live `PreparedState`:

1. Those entries are marked `invalidated`.
2. Event `prediction.invalidated` is appended (payload: `PredictionInvalidation`).
3. `get` returns null for them.

Same `(context_ref, context_signature)` does not invalidate.

## Events

Append-only, replayable from `EventLog`. Prediction events never fold into `EventLog.replayState` (that fold remains `state.updated` only).

| type | payload | effect on replay |
|------|---------|------------------|
| `prediction.updated` | `Forecast` | last forecast by id |
| `prediction.updated` | `PreparedState` | last prepared by id |
| `prediction.invalidated` | `PredictionInvalidation` | listed prepared ids → `invalidated` |

`PredictionReplay` reconstructs forecasts and prepared entries. Reconstructing prediction artifacts must not write WorldState.

`WorldState.write(PREDICTION, …)` remains `Rejected` and logs `state.write_rejected` (T1 epistemic gate).

## Canonical serialization

`a3.core.serialize.CanonicalJson` (same rules as T1):

1. UTF-8, compact, no whitespace, no BOM.
2. Object keys sorted lexicographically (`String.compareTo`).
3. Fact arrays sorted by `(k, observed_at, id, source)`. Candidate arrays stored already ranked; encoded in that order. Invalidation `prepared_ids` sorted.
4. `Instant` → `Instant.toString()` (UTC, `Z`).
5. Null optionals omitted.
6. Integer-valued numbers as integers; otherwise `Double.toString()`.
7. SCHEMA profile omits internal fact `id` / `superseded_by`.
8. Clock and IDs are never generated by the serializer.

P10 compares UTF-8 bytes of `Forecast` and `FutureState`.

## Epistemic boundary

- Prediction may prepare `FutureState` / `PreparedState` / `ProjectionCandidate`.
- Prediction may not commit. No `PreparedState.commitToWorldState()`.
- Any write attempt through `WorldState.write` with source `PREDICTION` is rejected and logged.
- Policy and Execution writes remain rejected. Only `OBSERVATION_ACCEPTED` succeeds.

## Out of scope

AI, MCP, A2UI, A3UI renderer, network, adapters, `intent-model`. Counterfactual ranking (spec 18) is not prediction core.
