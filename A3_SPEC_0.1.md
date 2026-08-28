# A3 0.1 — Master Specification

**A3 = Adaptive Agent Architecture** · **A3UI = projection layer**

A3 is an intent-to-outcome runtime: it decides what state should become true, executes only authorized transitions, observes what actually happened, and projects state to the appropriate interface.

## Invariants
1. Determinism-first.
2. State is belief; facts carry confidence and temporal validity.
3. Everything is an append-only event.
4. Capability is a state transition: preconditions → effects.
5. Nothing irreversible without Trust/Policy authorization.
6. UI is a projection of state.
7. MCP/A2UI are optional adapters.

## Closed loop
`CONTEXT → INTENT → GOAL → WORLD MODEL → CAPABILITY GRAPH → PLAN → POLICY/TRUST → EXECUTE → OBSERVE → WORLD STATE UPDATE`

Predictive branch:
`INTENT FORECAST → FUTURE STATE → FUTURE UI → PREFETCH`
Prediction prepares but never commits.

## Core distinction
MCP answers “What can I call?”
A2UI answers “What can I render?”
A3 answers “What should become true?”
A3UI answers “How should that future state be experienced?”

## Engineering revisions
- Beliefs have `observed_at`, optional `expires_at`, source and confidence.
- Stale facts are excluded from planning.
- `ExpectedOutcome` and `ObservedOutcome` are distinct.
- Observation is a first-class entity and the only execution result that mutates belief state.
- Planner failures are typed.
- Counterfactual planning is experimental and outside the deterministic acceptance gate.
