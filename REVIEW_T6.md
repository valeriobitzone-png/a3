# REVIEW_T6 — MCP Adapter 0.1

**Unfrozen:** `adapters/` (+ `spec/25-mcp-adapter.md`, `settings.gradle.kts` include, this review).  
**Frozen:** `core/` (`core-v0.2` intact), `prediction/`, `projection/`, `a3ui/`, `renderers/`.  
**T6-INV:** the adapter translates to `Observation` and stops. Mint and `WorldState.apply` stay in `:core:runtime`.

Port used: `a3.core.runtime.Executor` (`fun execute(capability, now): Observation`). No new port in core.

---

## S1 — substitution (epistemic bytes)

**Invariant:** same goal + capability graph + injected clock; native `Executor` vs in-process `McpCapabilityExecutor` → `CanonicalJson.bytesState` identical.

**How:** train plan through `Runtime.execute`. Native returns `Observation("o_${cap.id}", …)` with `cap.effects` (empty fact ids). MCP stub returns `mcp://stub/${id}` as observation id/executionRef, **same** facts (`k/v/confidence/source/times`, id blank). `Fact.source` stays `calendar`/`train`, not `mcp://`.

**Not tautological:** a poisoned executor that sets `Fact.source` to `mcp://stub/…` produces different STATE bytes.

**Negative:** provenance on `Fact.source` is visible in belief; observation id is not.

**Result:** PASS.

---

## S2 — write barrier

**Invariant:** `:adapters:mcp` does not mint, apply, or name `AcceptedObservation`. Executor return type is `Observation`.

**How:** ArchUnit (production): no dependency on `AcceptedObservation` / `AcceptedObservationToken` / `WorldState`. Reflection: `McpCapabilityExecutor.execute` returns `Observation`. Source walk of `src/main`: no write tokens.

**Negative:** those types are absent from adapter main; commit stays in runtime.

**Result:** PASS.

---

## S3 — graph / S3 completeness

**Invariant:** `:core:runtime` does not depend on `:adapters:mcp`. If MCP vanished, epistemic core would not notice.

**How:** `core/runtime/build.gradle.kts` has no `project(":adapters…)`. ArchUnit: `a3.core.runtime..` ↛ `a3.adapters..`. Adapter gradle depends on `:core:runtime` and `:core:world-api`. Injection is at the test composition root (`Runtime.execute(..., mcp, …)`).

**Negative:** runtime methods have no `a3.adapters` parameter types.

**Result:** PASS. T1–T10 live in `:core:runtime` without the adapter on the runtime classpath.

---

## S4 — regression

Command: `./gradlew test --rerun-tasks`  
Host: locale, 2026-08-29. Exit code: 0.

```
McpAdapterAcceptanceTest > S1_substitution_same_facts_yield_identical_belief_bytes() PASSED
McpAdapterAcceptanceTest > S3_runtime_gradle_does_not_depend_on_mcp_adapter() PASSED
McpAdapterArchitectureTest > S3_core_runtime_does_not_depend_on_adapters() PASSED
McpAdapterArchitectureTest > S2_adapter_stops_at_observation_and_does_not_write_world() PASSED

T1–T10, R1–R9, W1–W5, V1–V4 PASSED
P1–P11, P12–P27, P28–P42, P43–P56 PASSED
P52 P55 PASSED (debug + release)

BUILD SUCCESSFUL in 7m 47s
93 actionable tasks: 93 executed
```

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| S1 | B1==B2 CanonicalJson STATE | `Runtime.execute` nativo vs stub MCP | stesso fold apply; id observation diverso | `Fact.source=mcp://` cambia i byte | **PASS** |
| S2 | adapter ↛ mint/apply | ArchUnit + return type + walk main | bytecode, non un flag | tipi write assenti | **PASS** |
| S3 | runtime ↛ adapters | Gradle + ArchUnit | file runtime invariato vs `core-v0.2` | nessun param adapter | **PASS** |
| S4 | T1–T56, W, V, S verdi | `./gradlew test --rerun-tasks` | suite ereditata + adapter | — | **PASS** |

**T6 GAP: nessuno.**

---

## Deviazioni

1. Stub in-process; no live MCP server (in scope).
2. `settings.gradle.kts` includes `:adapters:mcp` (lecito; non è dipendenza di runtime).
3. Observation id/`executionRef` = `mcp://stub/${capability.id}`; facts unchanged.

---

## Grep pre-tag (vuoti su adapters)

`AcceptedObservation` / `WorldState.apply` / `mintAccepted` — empty.  
`http`/`socket`/`network` in `adapters/*/src/main` — empty.

`git diff core-v0.2 -- core` — empty.

**Tag:** `mcp-adapter-v0.1`.
