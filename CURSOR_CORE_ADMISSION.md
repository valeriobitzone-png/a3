FASE CORE-ADMISSION — Terminology + Admission (Hardening step 1).
Unico modulo nuovo: :core:admission. Scongelati SOLO per migrazione meccanica
e write-path: :core:runtime, :prediction, :projection, :a3ui, :adapters:mcp,
:launcher. FROZEN: renderers/, :core:json, :intent-model. Niente push.
Tag core-admission-v0.1 + bump solo a gate verde (9/9). Un solo punto non
dimostrato → GAP, niente tag. Nessuna correzione documentale sostituisce una
proprietà non provata sul disco.

MODALITÀ: AUDIT-FIRST. PROTOCOLLO DI VERIFICA CONGELATO: non aggiungere
requisiti durante la run.

════════════════════════════════════
PASSO 0 — PRE-FLIGHT (prima di toccare qualsiasi cosa)
════════════════════════════════════
grep -rnE '\bWorldState\b|\bFact\b' renderers/ intent-model/ core/json/ --include='*.kt'
Vuoto → procedi. Non vuoto → riclassifica quei file (frozen → rename meccanico,
aggiorna Gate 9 e bump-list) OPPURE dichiara che il grep repo-wide non è il gate.
Scrivilo. I due gate (grep vuoto + diff frozen vuoto) devono essere consistenti
nel testo, non a runtime.

════════════════════════════════════
MIGRAZIONE MECCANICA (repo-wide)
════════════════════════════════════
\bWorldState\b → BeliefState · \bFact\b → Claim ·
apply(ObservationCandidate) → apply(AcceptedObservation)
("Artifact"/"Factory"/"satisfaction" NON matchano.)
Grep post vuoti: '\bWorldState\b', '\bFact\b', 'apply\(ObservationCandidate\)'.
Eccezioni: solo commenti storici espliciti.

════════════════════════════════════
MODULO :core:admission (cieco)
════════════════════════════════════
Dipende da :core:json + stdlib. ArchUnit: ↛ runtime/prediction/projection/a3ui/
renderers/intent/adapters.

TIPI:
SourceId = value class con normalizzazione canonica (trim, lowercase).
ObservationCandidate { source: SourceId, id, occurredAt, observedAt,
  ingestedAt, data, dataschema, confidenceProfile }
ConfidenceProfile { sourceType: DIRECT|INFERRED|GROUNDED_BY_MODEL,
  opaque }   // PROVISIONAL pending step 5
AdmissionPolicy = SNAPSHOT IMMUTABILE identificato da
  (policyId, policyVersion, policyDigest).
  policyDigest copre: regole admission, TTL, blacklist, holdModelGrounded,
  schema set, byte/digest degli schemi, profilo+versione del validatore.
AdmissionDecision { esito: ADMIT|REJECT|HELD, reasonCode, policyId,
  policyVersion }   // PURA: nessun admittedAt, nessun clock
AcceptedObservation { candidate, decision(=ADMIT), admittedAt }
AdmissionLogEntry { candidate, decision(=REJECT|HELD), at, reasonCode }

REGISTRO POLICY:
- registrare stesso (policyId, policyVersion) con policyDigest diverso → fail
- mutare lo schema set (o qualsiasi campo) di una policy registrata → fail
  o impossibile per costruzione

FIRMA PURA:
evaluate(candidate, immutablePolicySnapshot, asOf, admittedIdSet): AdmissionDecision
  asOf := candidate.ingestedAt (canonica online; offline: asOf fisso registrato)
  admittedIdSet: Set<Pair<SourceId,String>>

FLOW (split obbligatorio):
Source.observe() timbra observedAt (confine, impuro lecito)
→ evaluate(...)
  ├─ ADMIT → admittedAt := boundaryClock.now() → AcceptedObservation → fold
  └─ REJECT|HELD → at := boundaryClock.now() → AdmissionLogEntry → log (MAI fold)
BeliefState.apply(AcceptedObservation) → version bump; altro → throw.

REASON CODES (chiuso): ADMIT · REJECT_STALE · REJECT_POLICY_DENY ·
REJECT_SCHEMA_INVALID · REJECT_DUPLICATE · HELD_FOR_REVIEW

SEMANTICHE:
ttl: null = skip staleness · 0 = reject-all · <0 = throw
schema: assente → REJECT_SCHEMA_INVALID · non nel policy set →
REJECT_SCHEMA_INVALID (stesso concetto: non validabile)
HELD: holdModelGrounded ∧ sourceType == GROUNDED_BY_MODEL (trigger non numerico)

INVARIANTI:
BELIEF-1 solo il fold su AcceptedObservation deriva BeliefState.
ADMISSION-1 nessun candidate tocca BeliefState prima dell'admission.
ADMISSION-2 decision deterministica dato (candidate, immutablePolicySnapshot,
  asOf, admittedIdSet). Forward-ref: ordine dedup → step 3.
ADMISSION-3 ogni Accepted ha reasonCode + policyVersion.
ADMISSION-4 admission non muta BeliefState.
ADMISSION-5 solo ADMIT produce AcceptedObservation; REJECT/HELD → log, mai fold.
ADMISSION-6 metadati admission (reasonCode, policyVersion, admittedAt) NON in
  BeliefState; fold proietta candidate.data → Claim only; stateHash solo su
  BeliefState.
REPLAY-1 fold(initial, ordered Accepted bytes, fold version) → stesso stateHash.
  Forward-ref: semantica d'ordine → step 3.
DUPLICATE-1 reapply stessa AcceptedObservation = no-op; dedup key = (source,id).
LLM-1 output LLM mai ObservationCandidate diretto; estrazione LLM → Source =
  documento, sourceType = GROUNDED_BY_MODEL.
REGOLA CLOCK: nessun tipo nel fold genera timestamp nel corpo; i timbri sono
  dati di confine. (Vale per step 2.)

════════════════════════════════════
TEST (positivo + negativo; negativi devono fallire)
════════════════════════════════════
ADM-001 Accepted prodotto; candidate senza admission → apply throw.
ADM-002 byte-identity della DECISIONE PURA su stessi (candidate, snapshot,
  asOf, admittedIdSet). Sensibilità (non "negativo"): admittedIdSet con id già
  dentro → REJECT_DUPLICATE.
ADM-003 reasonCode ∈ enum; senza → throw.
ADM-004 observedAt oltre ttl vs asOf → REJECT_STALE; fresco → ADMIT;
  ttl null → skip; ttl 0 → reject-all; ttl <0 → throw.
ADM-005 blacklist → REJECT_POLICY_DENY; consentita → ADMIT.
ADM-006 (source,id) già ammesso → REJECT_DUPLICATE; stesso id source diverse →
  entrambi ADMIT.
ADM-007 data non conforme → REJECT_SCHEMA_INVALID; conforme → ADMIT;
  dataschema assente → REJECT_SCHEMA_INVALID; non nel policy set →
  REJECT_SCHEMA_INVALID.
ADM-007-neg-a stesso (policyId, policyVersion) con digest diverso → fail.
ADM-007-neg-b mutare schema set di policy registrata → fail/impossibile.
ADM-007-neg-c due evaluate con stesso (candidate, policyDigest, asOf,
  admittedIdSet) → decision byte-identiche (anti-tautologia lato schema).
ADM-008 holdModelGrounded ∧ GROUNDED_BY_MODEL → HELD; altrimenti altri gate;
  HELD → log, non Accepted, non fold.
ADM-009 fold di [Accepted] → stateHash H; ri-fold → stesso H; ri-admission
  dentro il fold → throw.
ADM-010 apply(Accepted) → bump; apply(candidate) → throw; apply(logEntry) → throw.
ADM-011 evaluate non muta BeliefState (ArchUnit).
ADM-012 estrazione LLM → source=documento, GROUNDED_BY_MODEL; source="llm"
  diretta → throw.
ADM-013 id stesso da A e B → entrambi; da A due volte → secondo REJECT_DUPLICATE.
ADM-014 HELD → log; apply(logEntry) → throw; HELD→Accepted → throw.
ADM-015 apply(stessa Accepted)×2 e ×3 → stesso stateHash; secondo apply che
  muta → throw/GAP.

NEGATIVO CONFINE :adapters:mcp:
MCP-BOUND-1 adapter.call() → ObservationCandidate; → AcceptedObservation → throw;
  candidate non raggiunge BeliefState senza evaluate.
MCP-BOUND-2 S1–S3 con admission in mezzo (policy permissiva, ADMIT) →
  byte-identity BeliefState pre/post migrazione (ADMISSION-6).

════════════════════════════════════
REGRESSION (separati, NO aggregato, NO --rerun-tasks)
════════════════════════════════════
:core:admission:test · :core:runtime:test · :prediction:test · :projection:test ·
:a3ui:test · :adapters:mcp:test · :launcher:testDebugUnitTest
Nessun test su :renderers/* e :intent-model (frozen, non toccano BeliefState).

════════════════════════════════════
GATE (9 punti, output reali)
════════════════════════════════════
1 pre-flight audit (vuoto o decisione documentata)
2 grep word-boundary vuoti
3 ADM-002 su decisione pura
4 binding univoco (policyId, policyVersion) → policyDigest
5 schema set immutabile e nel digest (ADM-007-neg-a/b/c)
6 validator profile nel digest
7 ADM-015 fold-level
8 MCP-BOUND-2 con ADMISSION-6
9 git diff renderers/ intent-model/ core/json/ vuoto (o coerente col pre-flight)

REVIEW_CORE_ADMISSION.md: pre-flight, tabella audit (test|invariante|percorso|
negativo|PASS/GAP), output reali dei grep, forward-ref step 3, note
(confidenceProfile provisional; HELD lifecycle fuori scope; regola clock).

════════════════════════════════════
TAG E BUMP
════════════════════════════════════
core-admission-v0.1. Bump di sorgente: core-v0.4, prediction-v0.3,
projection-v0.3, a3ui-v0.4, mcp-adapter-v0.2, launcher-v0.2 (+ riclassificati
dal pre-flight). NON bumpati: core-json-v0.1, renderer-android-v0.6,
intent-model-v0.1 (bump transitivo solo se documentato).

════════════════════════════════════
VIETATO
════════════════════════════════════
Nuovi *Spec/PredictionProvider · modificare :core:json engine · toccare
renderers/intent-model (salvo pre-flight) · push/LICENSE/public ·
--rerun-tasks aggregato · bump senza cambio sorgente non documentato ·
HELD nel fold · LLM come Source · dedup su id solo · admission che legge
BeliefState · evaluate che legge orologio o ritorna admittedAt · metadati
admission in BeliefState · SourceId non canonico · registro policy mutabile
non vincolato dal digest · aggiungere requisiti durante la run.

STOP dopo il tag. Poi step 2 (action state machine) dai tre cardini,
NON T12-spike.
