# Event Model

Envelope:
```json
{"id":"ev_…","t":"…Z","source":"runtime","type":"observation.accepted","causal_id":"ev_…","state_version":7,"integrate_mode":"SUPERSEDE_KEYS","payload":{}}
```

The event log is append-only. `causal_id` links the immediate causal predecessor.

`observation.accepted` carries `integrate_mode` ∈ {`SUPERSEDE_KEYS`, `COMPENSATE`} on the envelope plus an `Observation` payload. The mode is data on the event; it is never inferred from observation id prefixes. Replay of committed belief is a fold of `WorldState.apply` in `:core:runtime` over a **fresh** `WorldState`, not a write performed by `EventLog`.

Core event types: `context.observed`, `intent.updated`, `goal.set`, `goal.cleared`, `plan.proposed`, `plan.approved`, `plan.rejected`, `capability.discovered`, `trust.requested`, `trust.granted`, `trust.denied`, `execution.started`, `execution.committed`, `execution.rolled_back`, `observation.accepted`, `state.updated`, `prediction.updated`, `prediction.invalidated`, `projection.updated`.
