# REVIEW_A3UI_EXTENSIONS

Slice: **B4 — opaque extension namespace**  
Status: **green after final gate**  
Scope: headless `:a3ui:lifecycle`; no renderer code or graphics dependency.

## Contract

`SurfaceExtensions` accepts optional `x-*` keys and transports their payloads opaquely. `ExtensionValidator` validates only the key prefix. `ExtensionRendererStub` draws the base surface and ignores keys it does not recognize. The seven A3UI primitives are unchanged.

## Examples

Valid key and opaque payload:

```json
{"x-consumer-gate":{"status":"pending"}}
```

Invalid key rejected explicitly:

```text
{"gate":{"status":"pending"}}
→ InvalidExtensionKeyException: keys MUST start with x-
```

## Audit table

| Test | Invariant | Evidence | Result |
|------|-----------|----------|--------|
| NS-001 | Valid `x-consumer-gate` payload crosses unchanged | `ExtensionsTest.NS_001_valid_consumer_extension_crosses_opaque_protocol` | PASS |
| NS-002 | Key without `x-` is rejected | `ExtensionsTest.NS_002_key_without_x_prefix_is_rejected_explicitly` | PASS |
| NS-003 | Unknown extension is ignored while base surface draws | `ExtensionsTest.NS_003_unknown_extension_is_ignored_by_renderer_stub` | PASS |
| NS-004 | Extensions are optional | `ExtensionsTest.NS_004_extensions_are_optional` | PASS |
| NS-005 | Spec defines opaque transport/non-interpretation | `ExtensionsTest.NS_005_spec_contains_opaque_transport_rule` | PASS |
| NS-006 | Frozen paths have empty diff | `ExtensionsTest.NS_006_frozen_paths_have_empty_diff` | PASS |

## Gate commands

- `./gradlew :a3ui:lifecycle:test --tests 'a3.a3ui.lifecycle.*'` — PASS (21 tests: LC-001..007, RB-001..008, NS-001..006).
- `./gradlew test` — PASS (`BUILD SUCCESSFUL`, full repository suite).

## Freeze audit

```text
git diff --name-only -- core/ broker/ agent/ renderers/ launcher/ overlay/ adapters/ conformance/ a3ui-web/ spec/SPEC_A3-EP.md
```

Observed result: empty. No renderer or graphics code was added, and no conformance suite was recreated or changed. B5–B7 are not included.

## Provenance and exclusions

The implementation is new headless Kotlin code in this repository. Extension payloads are not interpreted. No private-consumer import or name is used. No tests were deleted or weakened, and no renderer is used by the B4 tests.
