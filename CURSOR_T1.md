# Cursor — T1 implementation contract

Implement only the deterministic A3 core.

1. Do not add AI, MCP, A2UI, Android, network or cloud dependencies.
2. Keep domain models aligned with `schemas/`.
3. Preserve the invariant: Prediction may prepare. Policy may authorize. Execution may change the world. Observation determines what actually happened.
4. Planner must never emit an undeclared partial plan.
5. Keep planning deterministic: stable capability ordering and stable state signatures.
6. Run `./gradlew :core:test`.
7. Do not move into adapters, a3ui or intent-model until T1–T10 are green.

Note: Kotlin's standard `Result<T>` is single-parameter, so this implementation uses the explicit `PlanResult` sealed type instead of the non-compiling `Result<Plan, PlannerError>` notation from the conceptual spec.
