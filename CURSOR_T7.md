SEI NELLA FASE T7 — INTENT PROVIDER RISTRETTO.
Unico modulo nuovo: intent-model/ → tag intent-model-v0.1.
Lecito: settings.gradle.kts include(":intent-model") + intent-model/build.gradle.kts.
FROZEN: core/ (core-v0.2) SOURCE, prediction/, projection/, a3ui/, renderers/, adapters/.
Niente file nuovi sotto core/. Niente test nuovi sotto core/runtime/src/test.

Fuori scope: PredictionProvider, H3, CanonicalJson 4° copy, missingPrecondition, README, launcher, --rerun-tasks aggregato.

MODALITÀ: AUDIT-FIRST. Un GAP blocca il tag.

REGOLA: provider PROPONE. Goal/Policy/Trust DECIDONO. Mint/apply restano :core:runtime.

TIPI SUL DISCO (non inventare):
  a3.core.model.Intent(id, expression, modality, source, confidence, goalRef?)
  source ∈ {explicit, inferred}; confidence in [0,1].
  CanonicalJson.of(Intent) già in a3.core.serialize.CanonicalJson.
  DeterministicPlanner.plan(goal, state, graph, now)
  Runtime.execute(plan, world, capabilities, executor, now) — NON aggiungere Intent/Provider.

IntentContext: data class in :intent-model (clock iniettato). NON in core.

STRUTTURA:
  :intent-model api(project(":core:runtime"))
  IntentProvider { fun infer(ctx: IntentContext): Intent }
  IntentProposal.validate: source=inferred && confidence<1 else throw
  RuleIntentProvider: deterministico. Nessuna AI/rete.
  Package a3.intent…  (non a3.core)

TEST I1–I6 — tutti in intent-model/src/test:

I1 PROPOSAL: RuleIntentProvider.infer → Intent(source=inferred, confidence<1).
   Negative: validate su inferred+confidence=1.0 throw; validate su source=explicit throw.

I2 WRITE BARRIER: ArchUnit+grep intent-model/src → zero AcceptedObservation,
   WorldState.apply, mint, Forecast, predict(. Ritorna SOLO Intent.
   Dopo infer, CanonicalJson.ofState(belief) identico al prima (nessuna write).

I3 GRAPH: core/runtime/build.gradle.kts NON ha :intent-model.
   ArchUnit a3.core.runtime.. ↛ a3.intent..
   Runtime.execute / Planner.plan firme invariate (nessun parametro provider).

I4 SUBSTITUTION (byte Intent, non BeliefState):
   Intent fatto a mano = stesso id/expression/modality/source/confidence/goalRef dello stub.
   CanonicalJson.of(mano) bytes == CanonicalJson.of(stub.infer(ctx)).
   Negative: stub con confidence diversa → bytes diversi.
   BeliefState non entra in I4: infer non scrive; sarebbe tautologia.

I5 GATING in COMPOSIZIONE (test, core come libreria):
   infer → nel TEST costruisci Goal con constraints che il piano viola
   → DeterministicPlanner.plan(...) → PlanResult.Failure(ConstraintConflict)
   (o TrustBlocked via Policy). Il provider non decide. Niente nuovo port in core.

I6 REGRESSION onesta:
   ./gradlew :intent-model:test   (e se serve :core:runtime:test SENZA --rerun-tasks)
   Aggregato --rerun-tasks = stall host 16GB dopo :prediction:test (R8, non GAP).
   Non ritestare frozen modules. Non --rerun-tasks.

VINCOLI: nessuna AI/rete; clock/ID iniettati; nessun Forecast; nessun campo nuovo su Intent.

GATE:
  :intent-model:test verde; I1–I6 zero GAP.
  grep -rn "AcceptedObservation\|WorldState.apply\|Forecast\|predict(" intent-model/src
    (vuoto; "mint" solo se è chiamata, non substring in commenti)
  grep -rn "http\|socket\|network\|gemini\|cloud" intent-model/src/main | grep -v "import\|//"
    (vuoto)
  REVIEW_T7.md tabella (test|invariante|percorso|negativo|PASS/GAP).
  Tag intent-model-v0.1. Commit solo intent-model/ + settings.gradle.kts include.
  NON taggare se hai toccato core/src.
