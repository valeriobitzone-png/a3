# REVIEW_A3UI_REBINDING

Slice: **B3 — re-binding without form regeneration**  
Status: **green after final gate**  
Scope: headless `:a3ui:lifecycle`; no renderer code or graphics dependency.

## Contract

A `formKey` identifies generated form shape. `FormRegistry` keeps one active form per key and exposes a measurable generation counter. `SurfaceBinding` keeps `surfaceId` stable while replacing only a `DataRef`. Values never enter the form shape. Form-key/schema invalidation is explicit and generates a new form. Frozen rebinds are queued until restore; dismissed rebinds reject.

## Audit table

| Test | Invariant | Evidence | Result |
|------|-----------|----------|--------|
| RB-001 | N value updates generate one form | `RebindingTest.RB_001_value_updates_generate_one_form`; counter = **1** after 25 updates | PASS |
| RB-002 | `surfaceId` is stable through N rebinds | `RebindingTest.RB_002_surface_id_is_stable_through_rebinds` | PASS |
| RB-003 | Binding carries `DataRef`, not copied values | `RebindingTest.RB_003_binding_keeps_a_data_ref_not_a_value_copy` | PASS |
| RB-004 | Form-key change regenerates explicitly | `RebindingTest.RB_004_form_key_change_regenerates_and_is_declared`; counter = **2** | PASS |
| RB-005 | Rebind on `DISMISSED` rejects explicitly | `RebindingTest.RB_005_rebind_on_dismissed_rejects_explicitly` | PASS |
| RB-006 | ALIVE applies immediately; FROZEN queues until restore; PROPOSED/MOUNTED retain proposal data | `RebindingTest.RB_006_state_controls_when_rebind_is_applied` | PASS |
| RB-007 | Spec contains re-binding and invalidation rule | `RebindingTest.RB_007_spec_contains_rebinding_invalidation_rule` | PASS |
| RB-008 | Frozen paths have empty diff | `RebindingTest.RB_008_frozen_paths_have_empty_diff` | PASS |

## Gate commands

- `./gradlew :a3ui:lifecycle:test --tests 'a3.a3ui.lifecycle.RebindingTest'` — PASS (8 tests).
- `./gradlew test` — PASS (`BUILD SUCCESSFUL`; full repository suite).

## Freeze audit

```text
git diff --name-only -- core/ broker/ agent/ renderers/ launcher/ overlay/ adapters/ conformance/ a3ui-web/ spec/SPEC_A3-EP.md
```

Observed result: empty. No renderer or graphics code was added. No conformance suite was recreated or changed. No B4–B7 work is included.

## Provenance and exclusions

The implementation is new headless Kotlin code in this repository. `DataRef` requires `source` and `lineage`; no value payload is copied into a form. No private-consumer import or name is used. No tests were deleted or weakened, and no renderer is used by the B3 tests.
