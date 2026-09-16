<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# A3 Intent Provider 0.1 — propone, non decide

## Scopo
Fonte di Intent, non decisore. Runtime deterministico. Intent passa come dato.

## Regola
Il provider PROPONE. Goal.constraints + Policy + Trust DECIDONO.
Intent inferito: source=inferred, confidence<1. Non è un'azione.

## Solo IntentProvider
Niente PredictionProvider. Forecast = :prediction frozen. Niente secondo motore (famiglia H3).

## Disco (non inventare)
Intent = a3.core.model.Intent in :core:runtime (frozen). Campi: id, expression, modality, source, confidence, goalRef.
source ∈ {explicit, inferred}. Non aggiungere campi a Intent.
IntentContext NON esiste in core → definito in :intent-model.
CanonicalJson.of(Intent) esiste già (core serialize).

Planner.plan(goal, state, graph, now) — niente Intent.
Runtime.execute(plan, world, …) — niente Intent, niente IntentProvider.

## Struttura
:intent-model  (nuovo; include in settings.gradle.kts)
  api(:core:runtime)  // solo per il tipo Intent; core non dipende indietro
  IntentContext       data class nel modulo (clock iniettato; niente rete)
  IntentProvider      fun infer(ctx: IntentContext): Intent
  IntentProposal.validate  inferred ⇒ source=inferred AND confidence<1 else throw
  RuleIntentProvider  deterministico, test. Nessuna AI.

:core:runtime ↛ :intent-model. Nessun port nuovo in core.

## T7-INV
infer ritorna Intent(source=inferred, confidence<1).
Non mint, non WorldState.apply, non plan, non Forecast, non tocca WorldState.
Sostituire mano vs stub: stesso Intent ⇒ stessi byte CanonicalJson.of(Intent).
BeliefState immutato da infer (I4 write-barrier).

## Fuori scope
AI/rete · PredictionProvider · source di core/ · launcher (T8) · nuovi campi Intent.
