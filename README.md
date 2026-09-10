# A3 — Adaptive Agent Architecture

Runtime intent-to-outcome. La 0.1 è deterministic-first e gira senza LLM.
MCP e A2UI sono adapter opzionali, non fondazione.

> MCP: What can I call?
> A2UI: What can I render?
> **A3: What should become true?**
> **A3UI: How should that future state be experienced?**

### Invariante
> Prediction may prepare. Policy may authorize. Execution may change the world. Observation determines what actually happened.

L'unico fold committed è `BeliefState.apply(AcceptedObservation)`: vero sul closed loop live e sul replay (fold su un writer fresco). `BeliefWriter` è solo l'envelope EventLog.

## Strati

| Strato | Domanda | Modulo | Tag |
|---|---|---|---|
| CORE | What should become true? | `core/` | `core-v0.5` + `core-admission-v0.1` + `core-action-v0.1` |
| PREDICTION | What could become true? | `prediction/` | `prediction-v0.3` |
| PROJECTION | How should meaning be presented? | `projection/` | `projection-v0.3` |
| A3UI | How does presentation evolve in time? | `a3ui/` | `a3ui-v0.4` |
| RENDERER | How is it materialized here? | `renderers/` | `renderer-android-v0.7` |
| ADAPTERS | How does the existing world plug in? | `adapters/` | `mcp-adapter-v0.3` |
| INTENT | What intent is inferred? | `intent-model/` | `intent-model-v0.2` |
| LAUNCHER | Where is the composition root? | `launcher/` | `launcher-v0.2` |
| BROKER | Gate on irreversible actions | `broker/` | `broker-v0.2` (private; DECISION-2 open) |

## Tassonomia
`Claim` = belief atom · `BeliefState` = reality believed · `ActionState` = executor/plan machine (not inside BeliefState) · `FutureState` = possible reality · `PreparedState` = speculative work · `PresentationState` = semantic presentation · `Projection` = presentation intent · `RenderedOutput` = renderer-owned · `Observation` = measured reality · `ObservationCandidate` = pre-admission.

## Repository
- `A3_SPEC_0.1.md` — master specification
- `spec/` — specifiche normative
- `schemas/` — JSON Schema
- `core/world-api` — `Claim`, `BeliefReader` (read-only)
- `core/admission` — `evaluate` puro, cieco (`core-admission-v0.1`)
- `core/action` — ActionState machine, cieca (`core-action-v0.1`)
- `core/world` — `BeliefState.apply(AcceptedObservation)`; `BeliefWriter` envelope
- `core/runtime` — planner, execution, admit via evaluate, replay fold, action dispatch
- `core/json` — canonical engine (`core-json-v0.1`, non bumpato)
- `prediction/` — forecast deterministico (dipende solo da `world-api`)
- `projection/` — transformer read-only
- `a3ui/` — compiler di intent temporale
- `renderers/` — interprete A3UI (Android)
- `adapters/` — MCP (`mcp-adapter-v0.3`)
- `intent-model/` — Intent inferito, senza write su belief (`intent-model-v0.2`)
- `launcher/` — composition root Android (`launcher-v0.2`)
- `broker/` — irreversible-action keys broker (`broker-v0.2`, private; excluded from a future first public release until DECISION-2)

## Accettazione
T1–T10 (core) · P1–P11 (prediction) · P12–P27 (projection) · P28–P42 (a3ui) · P43–P56 (renderer) · W1–W5 (writer) · V1–V5 (replay) + barriere ArchUnit.

```bash
./gradlew test
```

## Confini (ArchUnit)
prediction ↛ core:world/runtime · projection ↛ world/runtime/a3ui/renderers · a3ui ↛ world/runtime/renderers/adapters · renderers ↛ world/runtime/prediction · a3ui importa `ProjectionCandidate` solo da `a3.projection.model` · `:core:runtime` ↛ `:adapters` (l'adapter si inietta al composition root).

## Hardening aperto
live reject duplicate event id (R2, futuro).

P56 language gap: chiuso in payload (a3ui 0.2) e paint item/action (renderer 0.3).
