SEI NELLA FASE T8 — LAUNCHER 0.1. Unico modulo nuovo: launcher/ → tag launcher-v0.1.
Lecito: settings.gradle.kts include(":launcher") + launcher/build.gradle.kts.
FROZEN SOURCE: core/, prediction/, projection/, a3ui/, renderers/, adapters/, intent-model/.
Niente file nuovi sotto quei path.

Fuori scope: AI, MCP vivo, --rerun-tasks aggregato, nuovi campi Intent, FoundationInput.

MODALITÀ: AUDIT-FIRST. Un GAP blocca il tag.

FIRME SUL DISCO (non inventare):
  ComposeRenderer(output: RenderedOutput, onAction: (String)->Unit)
  interpret(surface, presentation, ctx) = path 0.2 (nodes)
  interpret(surface, ctx) = path 0.1 (ignora nodes[])
  PolicyDecision.CONFIRM  (non TrustDecision)
  PrefetchComposeCache.get / composeOffscreen  (non cambiare :renderers)
  mint/apply solo via Runtime.execute + TrustGrant. Launcher NON chiama WorldState.apply.

HOST:
  ViewModel fa infer → plan → compile(projection, presentation) → interpret 0.2 → StateFlow<RenderedOutput>
  A3Screen chiama ComposeRenderer(output, onAction)
  onAction("confirm") → Policy; se CONFIRM mostra TrustDialog; grant → Runtime.execute
  Niente implementation(:prediction). Forecast non è un secondo motore.

TEST in launcher/ (unit Robolectric / compose-ui-test). Espresso-su-Button VIETATO.

L1 COMPILE: ./gradlew :launcher:assembleDebug exit 0.
   build.gradle.kts: no :prediction, no :adapters:mcp.
   :a3ui/:projection solo come libreria frozen se servono compile/PresentationState.

L2 TRENO (compose test): path 0.2, Node list con item, testTag su text con atomo departure.
   confirm = Box clickable / semantics Button, NON androidx.compose.material.Button(.
   Negative: interpret 0.1 sulla stessa surface → nodes vuoti; 0.2 → list presente.

L3 MOTION: due output con MorphSpec id semantico; ComposeMorphApplier wrappa.
   stiffness dal RenderedOutput.spring (già interpretato). Non toccare renderer.

L4 PREFETCH: ViewModel usa PrefetchComposeCache.composeOffscreen.
   hit su version match; dopo BeliefState version++ get o composeOffscreen → miss.
   Non modificare PrefetchComposeCache. Invalidation nel ViewModel.

L5 TRUST: step IRREVERSIBLE → PolicyDecision.CONFIRM → TrustDialog visibile.
   Approva → Runtime.execute (grant), NON mint nel launcher.
   reversible/ALLOW → dialog assente.
   grep launcher/src/main: vuoto su WorldState.apply e mintAcceptedObservation.

L6 ROLLBACK: Executor di test ritorna Observation ≠ expected → Runtime compensating.
   Overlay visibile. observed==expected → overlay assente.
   Launcher non chiama apply.

L7 A11Y: semantics su Compose catalog.
   action → Role.Button (Box, non widget Button). field → Role.EditableText (BasicTextField).
   text → testo. list → collection. stack NON è Role.List.
   role assente non arriva (A3UI schema).

L8 REGRESSION onesta:
   ./gradlew :launcher:assembleDebug :launcher:testDebugUnitTest
   :intent-model:test SENZA --rerun-tasks se serve.
   Aggregato --rerun-tasks = stall host (R8), non GAP. Non ritestare frozen src.

GATE:
  assembleDebug + testDebugUnitTest verdi; L1–L8 zero GAP.
  grep -rn "WorldState.apply\|mintAcceptedObservation" launcher/src/main | grep -v "import\|//"   (vuoto)
  grep -rn "Button(\|Card(\|Dialog(\|AlertDialog(\|TextField(" launcher --include='*.kt' | grep src/main | grep -v "BasicTextField("
    (vuoto; Dialog overlay = composable vostro, NON material Dialog()
     se usate un nome, non androidx.compose.material.Dialog)
  grep -rn "http\|socket\|gemini\|cloud" launcher/src/main | grep -v "import\|//"  (vuoto)
  REVIEW_T8.md tabella. Tag launcher-v0.1. Commit solo launcher/ + settings include.
  NON taggare se hai toccato frozen source.
  Se gradle daemon log fermo su Executing build >2 min: STOP, non --rerun-tasks.
