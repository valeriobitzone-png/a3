# Renderer Android

The first physical interpreter of `A3UISurface`. The renderer is an **interpreter, not an agent**.

**T5-INV:** Consume `A3UISurface`, produce renderer-owned `RenderedOutput`. Do not mutate A3UI/projection/belief. Do not predict, plan, accept observations, or write committed reality. Recomposition is pure with respect to A3 state.

**Direction:** A3 does not depend on Compose. Compose depends on the Android interpretation of A3UI.

```
:a3ui
   ↑
:renderers:android-core      (JVM Kotlin, interpreter)
   ↑
:renderers:android-compose   (Android + Compose, materialization)
```

`:renderers:*` depends on `:a3ui` only (plus Compose BOM on the compose module). No `:core:world`, `:core:runtime`, `:prediction`. Prediction types may exist on the transitive classpath; they must not be used (P43).

## android-core

Pure function:

```
interpret(surface: A3UISurface, ctx: RendererContext): RenderedOutput
```

`RendererContext` injects token→color data, density, formFactor, clock. Concrete colors live only in the context, never as literals in interpreter logic.

Interpreters: tokens, density, motion, morph, gestures, haptics. No Android SDK. No device haptic APIs.

## android-compose

Materializes `RenderedOutput` only. Appliers map interpreted data to Compose primitives. `PrefetchComposeCache` holds off-screen interpreted output for a prepared `PrefetchSpec`; it does not commit reality.

## Language

The renderer interprets A3UI 0.1. It does not invent missing semantics. Gaps go to `SCHEMA_HARDENING.md` (A3UI 0.2).

## Out of scope

Launcher, overlay, accessibility, MCP, A2UI, AI, network. H3 ProjectionCandidate duplication remains OPEN.
