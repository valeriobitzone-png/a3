# REVIEW_A3UI_CONFORMANCE_B5

Slice: **B5a — A3UI Compose conformance extension**  
Status: **green after final gate**  
Scope: existing `:a3ui:conformance` extended with programmatic AC-024..AC-033 checks; no graphical renderer was added.

## Audit table

| Test | Invariant | Evidence | Result |
|------|-----------|----------|--------|
| AC-001..AC-023 | Existing mark→CTA, accessibility, screenshot, and freeze regression suite | Existing conformance tests and Android/Mac conformance tasks | PASS |
| AC-024 | Pre-mount surface is queued and mounted at container arrival | `B5ConformanceTest.AC_024_surface_is_queued_before_mount_and_mounted_on_container_arrival` | PASS |
| AC-025 | Producer dismissal rejects explicitly | `B5ConformanceTest.AC_025_producer_dismiss_is_rejected_explicitly` | PASS |
| AC-026 | Dismissed surface leaves no memory reference | `B5ConformanceTest.AC_026_dismissed_surface_has_no_memory_reference` | PASS |
| AC-027 | N value updates keep generation counter at 1 | `B5ConformanceTest.AC_027_value_updates_keep_generation_counter_at_one` | PASS |
| AC-028 | `surfaceId` remains stable through N rebinds | `B5ConformanceTest.AC_028_surface_id_remains_stable_through_rebinds` | PASS |
| AC-029 | Form-key change regenerates at generation 2 | `B5ConformanceTest.AC_029_form_key_change_regenerates_to_generation_two` | PASS |
| AC-030 | `x-mono-gate` extension crosses opaquely | `B5ConformanceTest.AC_030_x_mono_gate_extension_passes_opaque` | PASS |
| AC-031 | Key without `x-` rejects explicitly | `B5ConformanceTest.AC_031_extension_without_x_prefix_is_rejected_explicitly` | PASS |
| AC-032 | Unknown extension is ignored while surface is drawn | `B5ConformanceTest.AC_032_unknown_extension_is_ignored_and_surface_is_drawn` | PASS |
| AC-033 | Surface remains valid without extensions | `B5ConformanceTest.AC_033_surface_without_extensions_remains_valid` | PASS |

## Gate commands

- `./gradlew :a3ui:conformance:test --tests 'a3.a3ui.conformance.B5ConformanceTest'` — PASS (10 new tests plus existing Android/Mac task dependencies).
- `./gradlew :a3ui:conformance:test` — PASS (AC-001..AC-033 and Android/Mac regression tasks).
- `./gradlew test` — PASS (`BUILD SUCCESSFUL`, full repository suite).

## Freeze audit

```text
git diff --name-only -- core/ broker/ agent/ renderers/ launcher/ overlay/ adapters/ a3ui-web/ spec/
```

Observed result: empty. Only the existing conformance module and its review were changed. No renderer test, B5b web suite, MONO, or graphics repository was touched.
