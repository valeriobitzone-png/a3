# REVIEW_A3UI_LIFECYCLE

Slice: **B1 — ephemeral surface lifecycle**  
Status: **green**  
Scope: headless `:a3ui:lifecycle`; no renderer code or graphics dependency.

## Contract

The surface lifecycle is explicit and role-gated:

`PROPOSED → MOUNTED → ALIVE → FROZEN → DISMISSED`

Producers propose only. The shell owns mounting, activation, freezing, revival, and explicit dismissal. Pre-mount proposals are queued by container. A frozen surface declares preserved and discarded state. Dismissal removes the surface from shell memory.

## Audit table

| Test | Invariant | Evidence | Result |
|------|-----------|----------|--------|
| LC-001 | Complete lifecycle path is accepted | `SurfaceLifecycleTest.LC_001_complete_path_is_valid` | PASS |
| LC-002 | Producer dismissal is rejected | `SurfaceLifecycleTest.LC_002_producer_dismiss_is_rejected` | PASS |
| LC-003 | Shell cannot create `PROPOSED` | `SurfaceLifecycleTest.LC_003_shell_cannot_create_proposed` | PASS |
| LC-004 | Pre-mount queue retains and mounts proposals | `SurfaceLifecycleTest.LC_004_pre_mount_proposal_is_queued_and_mounted_when_container_arrives` | PASS |
| LC-005 | `DISMISSED` leaves no registry reference | `SurfaceLifecycleTest.LC_005_dismissed_surface_leaves_no_memory_reference` | PASS |
| LC-006 | Revival restores declared state and reports discarded state | `SurfaceLifecycleTest.LC_006_revival_restores_declared_values_and_reports_the_rest` | PASS |
| LC-007 | Spec contains lifecycle section and transition table | `SurfaceLifecycleTest.LC_007_spec_contains_surface_lifecycle_and_transition_table` | PASS |

## Gate commands

- `./gradlew :a3ui:lifecycle:test --tests 'a3.a3ui.lifecycle.SurfaceLifecycleTest'` — PASS (7 tests).
- `./gradlew test` — PASS (`BUILD SUCCESSFUL`, 388 actionable tasks; LC-001..007 all passed).

## Freeze audit

Frozen paths are checked with:

```text
git diff --name-only -- core/ broker/ agent/ renderers/ launcher/ overlay/ adapters/ conformance/ a3ui-web/ spec/SPEC_A3-EP.md
```

Observed result: empty. The only spec change is `spec/SPEC_A3UI.md`; the only implementation is the new headless `a3ui/lifecycle` module and its tests.

## Provenance and exclusions

This implementation is new headless Kotlin code in this repository. It imports no renderer, graphics, private-consumer, broker, agent, or A3-EP implementation module. No B3–B7 work is included. No renderer is used by the tests. No files or tests were deleted or weakened.
