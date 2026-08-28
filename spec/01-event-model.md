# Event Model

Envelope:
```json
{"id":"ev_…","t":"…Z","source":"runtime","type":"state.updated","causal_id":"ev_…","state_version":7,"payload":{}}
```

The event log is append-only. Replay reconstructs state deterministically. `causal_id` links the immediate causal predecessor.

Core event types: `context.observed`, `intent.updated`, `goal.set`, `goal.cleared`, `plan.proposed`, `plan.approved`, `plan.rejected`, `capability.discovered`, `trust.requested`, `trust.granted`, `trust.denied`, `execution.started`, `execution.committed`, `execution.rolled_back`, `state.updated`, `prediction.updated`, `projection.updated`.
