<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# A3 Launcher 0.1 — primo volto

## Scopo
Composition root Android. Non è un prodotto. Materializza A3UI 0.2 via renderer-android-v0.2.

## Launcher-0.1-INV
Il launcher orchestra moduli frozen come librerie. Non modifica i loro source.
Non chiama WorldState.apply / mintAcceptedObservation (quello è Runtime.execute).
Catalogo chiuso: Node/Binding/GestureBinding. Foundation only (niente Material Button).

## Disco (non inventare)
ComposeRenderer(output: RenderedOutput, onAction: (String) -> Unit)
A3UIInterpreter.interpret(surface, presentation, ctx) → RenderedOutput  (path 0.2)
interpret(surface, ctx) ignora nodes[] (path 0.1)
PrefetchComposeCache.composeOffscreen / get(candidateRef) — non scongelare :renderers
PolicyDecision { ALLOW, CONFIRM, DENY }  — niente TrustDecision
TrustTier.IRREVERSIBLE → Policy.evaluate = CONFIRM
DeterministicA3UICompiler.compile(projection, presentation): A3UISurface
PresentationState = a3.projection.model (frozen)

## Dipendenze gradle
:launcher (com.android.application)
  implementation(:renderers:android-compose)  // porta android-core, a3ui, projection in transito
  implementation(:intent-model)
  implementation(:core:runtime)
Lecito chiamare compile() di :a3ui e tipi di :projection (librerie frozen).
VIETATO: implementation(:prediction)  (niente secondo forecast)
VIETATO: implementation(:adapters:mcp)
VIETATO: modificare source di qualsiasi frozen module.

## Componenti
MainActivity        composition root: WorldState, DeterministicPlanner, Runtime, RuleIntentProvider, A3UICompiler, clock
A3HostViewModel     BeliefState + A3UISurface + RenderedOutput (StateFlow). Niente motore :prediction.
                    interpret 0.2 nel ViewModel (dati), ComposeRenderer nel composable.
A3Screen            ComposeRenderer(output, onAction)
TrustDialog         overlay se PolicyDecision.CONFIRM (hold). Approva → TrustGrant nel Runtime, non apply in UI.
RollbackOverlay     crack + return se Runtime.execute mismatch (compensating). Solo UI.

## Demo (fixture nel launcher, non in core)
RuleIntentProvider → Intent inferred confidence<1
Planner.plan su Goal+grafo test (calendar.read, train.search, train.reserve)
compile(projection, presentation) → surface con nodes[]
interpret 0.2 → ComposeRenderer
confirm → Policy CONFIRM → TrustDialog → execute
mismatch → rollback overlay. Match → morph.

## Accessibility (semantics Compose, non Material widget)
list → Collection  · item → ListItem  · action → Button (semantics, Box clickable)
field → EditText (BasicTextField)  · text → StaticText
stack → nessuno ROLE_LIST (è Column). row → nessuno LIST_ITEM.

## Fuori scope
AI · MCP vivo · source frozen · --rerun-tasks aggregato · sync.
