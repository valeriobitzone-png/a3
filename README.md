# A3 — Adaptive Agent Architecture 0.1

A3 is an intent-to-outcome runtime. A3UI is its projection layer.

> MCP: What can I call?  
> A2UI: What can I render?  
> **A3: What should become true?**  
> **A3UI: How should that future state be experienced?**

### Invariant
> Prediction may prepare. Policy may authorize. Execution may change the world. Observation determines what actually happened.

The 0.1 core is deterministic-first and works without an LLM. MCP and A2UI are optional adapters.

## Repository
- `A3_SPEC_0.1.md` — master specification
- `spec/` — normative module specifications
- `schemas/` — JSON Schemas
- `core/` — deterministic Kotlin runtime
- `adapters/` — reserved for MCP/A2UI adapters
- `a3ui/` — reserved for the projection layer
- `intent-model/` — optional AI providers
- `tests/` — acceptance fixtures

## Build
```bash
./gradlew :core:test
```

## 0.1 acceptance
T1–T10 must pass without any AI/model dependency.
