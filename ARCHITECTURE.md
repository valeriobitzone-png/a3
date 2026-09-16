# Architecture

A3 is organized as three planes with one direction of dependency:

```text
A3-EP protocol and lock vectors
        ↓
A3UI semantic projection and conformance
        ↓
Renderer / host consumers and graphics tokens
```

## Plane 1 — protocol

`core/`, `schemas/`, `conformance/`, and `spec/SPEC_A3-EP.md` define epistemic hygiene: envelope identity, truth class, provenance, ordering, confidence, and postconditions. A3-EP does not require A3UI. Receipts, sandbox results, and model output do not become FACT without the protocol's admission path.

## Plane 2 — semantic A3UI

`a3ui/`, `projection/`, `a3ui/lifecycle/`, and `spec/SPEC_A3UI.md` consume the protocol. They define surface tuples, seven catalog primitives, marks, CTA law, lifecycle, re-binding, stable surface identity, and opaque consumer extensions. The `:a3ui:conformance` suite checks semantics independently of renderer chrome.

## Plane 3 — consumers

`renderers/android-*`, `renderers/mac-compose`, `a3ui-web`, `a3ui-cli`, `overlay/`, `launcher/`, and `a3ui-graphics` are consumers or host shells. They may differ in layout, accessibility APIs, blur, motion, and text output. They must not invent protocol truth or add consumer-specific primitives to the seven-role catalog. Consumer-specific data travels through `x-*` extensions.

## CORE and extensions

A3-EP CORE is the strict subset defined by its spec: envelope, truth, ordering, confidence, and postcondition. Projection, overlay, sensory treatment, agent behavior, and renderer chrome are extensions. An extension MUST NOT relax a CORE requirement. The agent and any future Council/Critic role are application layers, not protocol primitives.

## Evidence boundaries

Shared fixture bytes are the semantic oracle for the A3UI renderer matrix. A review records the exact command, fixture, implementation, and environment. Performance screenshots and device captures are evidence for a run; Android fps and MID hardware remain UNVERIFIED until an on-screen jank count and representative hardware validation exist.
