# A3 — Adaptive Agent Architecture

Runtime intent-to-outcome. La 0.1 è deterministic-first e gira senza LLM.
MCP e A2UI sono adapter opzionali, non fondazione.

> MCP: What can I call?
> A2UI: What can I render?
> **A3: What should become true?**
> **A3UI: How should that future state be experienced?**

### Invariante
> Prediction may prepare. Policy may authorize. Execution may change the world. Observation determines what actually happened.

L'unico writer di `WorldState` è `WorldState.apply(AcceptedObservation)`: vero su classe, sul closed loop live, e sul replay (fold di apply su un world fresco).

## Strati

| Strato | Domanda | Modulo | Tag |
|---|---|---|---|
| CORE | What should become true? | `core/` | `core-v0.2` |
| PREDICTION | What could become true? | `prediction/` | `prediction-core-v0.1` |
| PROJECTION | How should meaning be presented? | `projection/` | `projection-core-v0.1` |
| A3UI | How does presentation evolve in time? | `a3ui/` | `a3ui-core-v0.2` |
| RENDERER | How is it materialized here? | `renderers/` | `renderer-android-v0.3` |
| ADAPTERS | How does the existing world plug in? | `adapters/` | `mcp-adapter-v0.1` |
| INTENT | What intent is inferred? | `intent-model/` | `intent-model-v0.1` |
| LAUNCHER | Where is the composition root? | `launcher/` | `launcher-v0.1` (+ T8b `7a9d03b` senza tag) |

## Tassonomia
`BeliefState` = reality believed · `FutureState` = possible reality · `PreparedState` = speculative work · `PresentationState` = semantic presentation · `Projection` = presentation intent · `RenderedOutput` = renderer-owned · `Observation` = measured reality.

## Repository
- `A3_SPEC_0.1.md` — master specification
- `spec/` — specifiche normative
- `schemas/` — JSON Schema
- `core/world-api` — `Fact`, `BeliefReader` (read-only)
- `core/world` — `WorldState.apply(AcceptedObservation)` only
- `core/runtime` — planner, execution, mint di `AcceptedObservation`, replay fold
- `prediction/` — forecast deterministico (dipende solo da `world-api`)
- `projection/` — transformer read-only
- `a3ui/` — compiler di intent temporale (0.2)
- `renderers/` — interprete A3UI (Android 0.3)
- `adapters/` — MCP (`mcp-adapter-v0.1`)
- `intent-model/` — Intent inferito, senza write su belief (`intent-model-v0.1`)
- `launcher/` — composition root Android (`launcher-v0.1`; T8b `7a9d03b` senza tag)

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
