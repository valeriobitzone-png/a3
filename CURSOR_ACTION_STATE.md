FASE CORE-ACTION — Action State Machine (Hardening step 2).
BASELINE: HEAD 785fd65 · core-admission-v0.1 CHIUSO. Non riaprire admission.
Unico modulo nuovo: :core:action. Scongelati SOLO per wiring di confine:
:core:runtime (execute/dispatch path), :adapters:mcp (receipt → ActionEvent,
mai BeliefState). FROZEN: :core:admission, :core:json, renderers/,
:intent-model, :prediction, :projection, :a3ui, :launcher (salvo se un
test di regression fallisce solo per import meccanico — documenta, non
ampliare scope). Niente push.
Tag core-action-v0.1 + bump solo a gate verde (10/10). Un punto non
dimostrato → GAP, niente tag. Nessuna correzione documentale sostituisce
una proprietà non provata sul disco.

MODALITÀ: AUDIT-FIRST. PROTOCOLLO DI VERIFICA CONGELATO: non aggiungere
requisiti durante la run. Non T12. Non spec/41. Non broker prodotto.
Non step 3/4/5.

SE qualcosa “sembra admission”: nuova interazione action↔admission,
NON modifica retroattiva del gate admission.

════════════════════════════════════
SEI CARDINI (già decisi — non rinegoziare)
════════════════════════════════════
1. ActionState ≠ BeliefState
2. COMPLETED ≠ OBSERVED
3. UNKNOWN è stato di prima classe
4. Transizioni pure guidate da eventi timbrati al confine
5. Compensazione = nuova azione/evento, non rollback
6. AUTH-1/2 con planDigest + beliefRevisionHash

════════════════════════════════════
MODULO :core:action (cieco)
════════════════════════════════════
Dipende da :core:json + stdlib. ArchUnit: ↛ BeliefState writer/apply,
↛ :core:admission evaluate (può riferire AcceptedObservation solo come
id/link opaco), ↛ prediction/projection/a3ui/renderers/intent/adapters.

TIPI (minimi):
ActionState — macchina a stati; NON vive dentro BeliefState.
PlanPhase: PROPOSED | VALIDATED | AUTHORIZED | DENIED | EXPIRED | STALE_BELIEF
ActionPhase: CREATED | DISPATCHED | ACKNOWLEDGED | COMPLETED | FAILED |
  UNKNOWN | OBSERVED | CONTRADICTED
CompensationPhase: NOT_REQUIRED | AVAILABLE | REQUESTED | COMPENSATED |
  COMPENSATION_FAILED | UNCOMPENSABLE

ActionEvent (timestamp SEMPRE campo dell'evento, mai now()):
  PlanProposed(at, intentDigest)
  PlanValidated(at, planDigest)
  AuthorizationGranted(at, authorizationDigest)
  AuthorizationDenied(at, reason)
  CommandCreated(at, commandDigest)
  CommandDispatched(at, dispatchId)
  ExecutorAcknowledged(at, receiptId)
  ExecutorCompleted(at, receiptId)
  ExecutorFailed(at, reason)
  TimeoutObserved(at, timeoutId)
  ObservationLinked(at, acceptedObservationId)
  ContradictionLinked(at, acceptedObservationId)
  CompensationRequested(at, compensationPlanDigest)
  CompensationCompleted(at, receiptId)
  CompensationFailed(at, reason)

Authorization {
  authorizationId, planDigest, beliefRevisionHash, principal, scopes,
  resource, maximumImpact, expiresAt, idempotencyKey,
  policyId, policyVersion, policyDigest,
  consentPresentationHash?   // opzionale step 2
}

Command {
  commandId, planDigest, authorizationId, idempotencyKey, ...
}

ExecutionReceipt — prova di completamento tool/executor; NON claim di dominio.

FIRMA PURA:
transition(current: ActionState, event: ActionEvent): ActionState
  — nessun now(), nessuna mutazione BeliefState, nessun side effect.

CONFINE (impuro lecito):
  executor/adapter → ActionEvent timbrato → transition
  ExecutorCompleted → ActionPhase.COMPLETED  (mai BeliefState)
  AcceptedObservation (già ammessa) → ObservationLinked → OBSERVED
  ObservationCandidate non ammessa → nessun link OBSERVED

════════════════════════════════════
INVARIANTI
════════════════════════════════════
ACTION-STATE-1
  ActionState MUST NOT be stored inside BeliefState.
  Action transitions MUST NOT mutate BeliefState.
  Only AcceptedObservation folded by BeliefState.apply(...) may affect BeliefState.

ACTION-CLOCK-1
  Action transitions MUST NOT read ambient time.
  All timestamps MUST be event fields stamped at the boundary.

ACTION-UNKNOWN-1
  Timeout MUST produce UNKNOWN unless failure is independently established.
  UNKNOWN ≠ FAILED. UNKNOWN ≠ SUCCESS. UNKNOWN non auto-risolve a SUCCESS.

RECEIPT-1
  ExecutionReceipt MUST NOT establish an external-world claim directly.
  ExecutorCompleted → COMPLETED only. Mai ExecutorCompleted → BeliefState.

AUTH-1  Command MUST match the authorized planDigest.
AUTH-2  If beliefRevisionHash changes in a way that invalidates a plan
  precondition, authorization MUST NOT be executed; require re-scoring.
AUTH-3  An expired Authorization MUST NOT produce a Command.
AUTH-4  A Command MUST carry an idempotencyKey.

COMP-1
  Compensation MUST be a new action + new observations.
  MUST NOT delete or rewrite the original action history.

COMPLETED ≠ OBSERVED
  COMPLETED = receipt tool/executor.
  OBSERVED = Source → ObservationCandidate → Admission ADMIT →
  BeliefState.apply → poi ObservationLinked.

════════════════════════════════════
TEST (positivo + negativo; negativi devono fallire)
════════════════════════════════════
ACT-001 ActionState separato da BeliefState
  + transition(ActionState, event) cambia ActionState
  - transition muta BeliefState → throw/GAP

ACT-002 Receipt non scrive belief
  + ExecutorCompleted → COMPLETED
  - ExecutorCompleted → BeliefState mutation → throw/GAP

ACT-003 Completed non observed
  + completed receipt senza observation → COMPLETED only
  - completed receipt marcato OBSERVED → throw/GAP

ACT-004 AcceptedObservation collega OBSERVED
  + AcceptedObservation correlata → OBSERVED
  - ObservationCandidate non ammessa → non OBSERVED

ACT-005 Timeout produce UNKNOWN
  + TimeoutObserved dopo dispatch → UNKNOWN
  - timeout → FAILED senza prova indipendente → throw/GAP

ACT-006 Revoked in flight produce UNKNOWN
  + revoke after dispatch → in-flight outcome UNKNOWN
  - success dichiarato senza observation → throw/GAP
  (success senza observation = COMPLETED, non OBSERVED; non SUCCESS di dominio)

ACT-007 Authorization planDigest
  + command.planDigest == authorization.planDigest → allowed
  - mismatch → deny/throw

ACT-008 Authorization beliefRevisionHash
  + unchanged valid belief revision → allowed
  - stale/changed invalidating revision → deny/re-score

ACT-009 Authorization expiry
  + event.at < expiresAt → command allowed
  - event.at >= expiresAt → command denied
  (confronto su campi evento/auth, non now() dentro transition)

ACT-010 Idempotency key required
  + command has idempotencyKey
  - missing idempotencyKey → throw

ACT-011 Compensation as new action
  + compensation creates new ActionState chain
  - compensation rewrites original action → throw/GAP

ACT-012 UNKNOWN is terminal until evidence
  + UNKNOWN remains UNKNOWN
  + later AcceptedObservation may link OBSERVED/CONTRADICTED
  - UNKNOWN auto-resolves to SUCCESS → throw/GAP

════════════════════════════════════
REGRESSION (separati, NO aggregato, NO --rerun-tasks)
════════════════════════════════════
:core:action:test · :core:admission:test · :core:runtime:test ·
:adapters:mcp:test
Altri moduli frozen: non lanciare salvo breakage meccanico documentato.
Mai ./gradlew test --rerun-tasks.

════════════════════════════════════
GATE (10 punti, output reali)
════════════════════════════════════
1 ActionState non vive in BeliefState (ArchUnit + test)
2 ExecutorCompleted non scrive belief
3 COMPLETED e OBSERVED distinti
4 UNKNOWN testato con timeout e revoca in-flight
5 Authorization vincolata a planDigest + beliefRevisionHash
6 Command richiede idempotencyKey
7 Compensazione = nuova action, non rewrite
8 Nessuna lettura di now() dentro transition (grep + ArchUnit)
9 Regression moduli separati, no aggregato
10 REVIEW_CORE_ACTION.md con output reali (tabella ACT|invariante|±|PASS/GAP,
   grep now()/BeliefState, forward-ref step 3 se serve)

════════════════════════════════════
TAG E BUMP
════════════════════════════════════
core-action-v0.1. Bump di sorgente solo moduli davvero toccati
(documenta la lista). NON bumpare :core:admission / core-admission-v0.1.
NON bumpare core-json-v0.1, renderer-*, intent-model-* salvo riclassifica
meccanica documentata come in admission.

════════════════════════════════════
VIETATO
════════════════════════════════════
Riaprire admission · nuovi *Spec · modificare :core:json engine ·
toccare renderers/intent/prediction/projection/a3ui oltre wiring
meccanico documentato · push/LICENSE/public · --rerun-tasks aggregato ·
ExecutorCompleted → BeliefState · COMPLETED ⇒ OBSERVED · UNKNOWN → SUCCESS
automatico · transition che legge now() · ActionState dentro BeliefState ·
compensation che riscrive la storia · Command senza idempotencyKey ·
auth senza planDigest/beliefRevisionHash · spec/41 · broker prodotto ·
T12-spike · step 3/4/5.

STOP dopo il tag. Poi solo ciò che Valerio ordina esplicitamente.
