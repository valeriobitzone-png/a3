# REVIEW_CORE_ACTION

AUDIT-FIRST. Protocol: `CURSOR_ACTION_STATE.md`. Baseline `785fd65` (`core-admission-v0.1` chiuso). Non riaprire admission. Niente push. Non T12, non spec/41, non broker, non step 3/4/5.

## Pre-flight

Unfrozen: `:core:runtime` (execute/dispatch), `:adapters:mcp` (receipt → ActionEvent, mai BeliefState). Frozen: `:core:admission`, `:core:json`, renderers/, intent-model, prediction, projection, a3ui, launcher. Nuovo: `:core:action` (`:core:json` + stdlib).

Action↔admission: solo `ObservationLinked(acceptedObservationId)` opaco. Nessuna modifica a `evaluate` / fold admission.

`git diff --stat 785fd65 -- core/admission core/json renderers intent-model prediction projection a3ui launcher` → empty.

---

## Audit table

| test | invariant | ± | PASS/GAP |
|---|---|---|---|
| ACT-001 | ACTION-STATE-1 | + transition cambia ActionState; BeliefState fields ↛ ActionState. − `rewriteActionHistory()` / apply event→belief throw | PASS |
| ACT-002 | RECEIPT-1 | + ExecutorCompleted → COMPLETED. − `receiptCannotClaimDomain` / `applyReceiptToBelief` throw | PASS |
| ACT-003 | COMPLETED ≠ OBSERVED | + completed senza observation resta COMPLETED. − `completedIsNotObserved()` throw | PASS |
| ACT-004 | OBSERVED solo dopo Accepted | + ObservationLinked(id) → OBSERVED. − blank id / candidateCannotLinkObserved throw; candidate non ammessa non osserva | PASS |
| ACT-005 | ACTION-UNKNOWN-1 timeout | + TimeoutObserved dopo DISPATCHED → UNKNOWN. − ExecutorFailed("timeout…") throw | PASS |
| ACT-006 | UNKNOWN in-flight revoke | + AuthorizationDenied dopo DISPATCHED → UNKNOWN. − domainSuccessWithoutObservation throw; COMPLETED ≠ OBSERVED | PASS |
| ACT-007 | AUTH-1 planDigest | + match → CommandCreated. − mismatch throw | PASS |
| ACT-008 | AUTH-2 beliefRevisionHash | + unchanged → DISPATCHED. − stale → STALE_BELIEF, non DISPATCHED | PASS |
| ACT-009 | AUTH-3 expiry | + `event.at < expiresAt` → command. − `event.at >= expiresAt` → EXPIRED, command null (no `now()` in transition) | PASS |
| ACT-010 | AUTH-4 idempotencyKey | + Command con key. − blank key throw | PASS |
| ACT-011 | COMP-1 | + `requestCompensation` child nuovo, parent events prefix preserved. − rewriteActionHistory throw | PASS |
| ACT-012 | UNKNOWN terminal until evidence | + resta UNKNOWN; ObservationLinked → OBSERVED; ContradictionLinked → CONTRADICTED. − ExecutorCompleted da UNKNOWN throw | PASS |

---

## Gate 1 — ActionState non vive in BeliefState

PASS.

```
ActionTest > ACT_001_action_state_is_separate_from_belief() PASSED
ActionBoundaryTest > ACT_001_dispatch_does_not_mutate_belief() PASSED
ActionTest > architecture_action_is_blind_and_has_no_now() PASSED
```

`BeliefState` fields: `version`, `facts`, `admittedKeys`. `grep -rn ActionState core/world/src --include='*.kt'` empty.

`CanonicalJson.ofState` omits `actionPhase` (`execute_links_observed_only_after_admit`).

---

## Gate 2 — ExecutorCompleted non scrive belief

PASS.

```
ActionTest > ACT_002_receipt_does_not_write_belief() PASSED
ActionBoundaryTest > ACT_002_executor_completed_does_not_write_belief() PASSED
McpAdapterAcceptanceTest > receipt_becomes_action_event_and_never_writes_belief() PASSED
```

`applyReceiptToBelief` / `applyActionEventToBelief` throw. MCP `receipt` → `ExecutorCompleted`; `call()` resta `ObservationCandidate`.

---

## Gate 3 — COMPLETED e OBSERVED distinti

PASS.

```
ActionTest > ACT_003_completed_is_not_observed() PASSED
ActionTest > ACT_004_accepted_links_observed_candidate_does_not() PASSED
ActionBoundaryTest > execute_links_observed_only_after_admit() PASSED
```

Runtime: `completeWithReceipt` → COMPLETED; `linkAccepted` solo dopo `admit` + `world.apply(minted)`.

---

## Gate 4 — UNKNOWN (timeout e revoca in-flight)

PASS.

```
ActionTest > ACT_005_timeout_produces_unknown_not_failed() PASSED
ActionTest > ACT_006_revoke_in_flight_is_unknown_success_needs_observation() PASSED
ActionTest > ACT_012_unknown_is_terminal_until_evidence() PASSED
```

---

## Gate 5 — Authorization planDigest + beliefRevisionHash

PASS.

```
ActionTest > ACT_007_command_plan_digest_must_match_authorization() PASSED
ActionTest > ACT_008_belief_revision_hash_must_match() PASSED
ActionTest > ACT_009_expired_authorization_cannot_produce_command() PASSED
```

---

## Gate 6 — Command richiede idempotencyKey

PASS.

```
ActionTest > ACT_010_idempotency_key_required() PASSED
```

---

## Gate 7 — Compensazione = nuova action, non rewrite

PASS.

```
ActionTest > ACT_011_compensation_is_a_new_action_chain() PASSED
```

Mismatch di esecuzione: `requestCompensation` sul parent COMPLETED; belief compensate path invariato.

---

## Gate 8 — nessuna lettura di `now()` dentro transition

PASS. Command:

```bash
grep -rnE 'now\(\)|Instant\.now|BeliefState' core/action/src/main
```

Real output: empty (exit 1, no lines).

```
ActionTest > architecture_action_is_blind_and_has_no_now() PASSED
```

ArchUnit: production `a3.core.action` ↛ `BeliefState` / `BeliefWriter` / `Instant.now` / runtime / prediction / projection / a3ui / renderers / intent / adapters. Gradle `:core:action` implementation = `:core:json` only.

Timestamps are event fields. Boundary (`ActionDispatch`, `Runtime.execute`) stamps `now` already injected.

---

## Gate 9 — regression moduli separati, no aggregato

PASS. Invocazioni distinte. Nessun `./gradlew test`. Nessun `--rerun-tasks`.

```
./gradlew :core:action:test
ActionTest > ACT_001_action_state_is_separate_from_belief() PASSED
… ACT_002 … ACT_012 … architecture_action_is_blind_and_has_no_now() PASSED
BUILD SUCCESSFUL
```

```
./gradlew :core:admission:test
BUILD SUCCESSFUL
:core:admission:test UP-TO-DATE
```

Admission non toccato (`git diff 785fd65 -- core/admission` empty). XML `AdmissionTest` 19 tests, 0 failures (ADM-001…015 inclusi).

```
./gradlew :core:runtime:clean :core:runtime:test
ActionBoundaryTest > ACT_001_dispatch_does_not_mutate_belief() PASSED
ActionBoundaryTest > ACT_002_executor_completed_does_not_write_belief() PASSED
ActionBoundaryTest > execute_links_observed_only_after_admit() PASSED
T1–T10, W, V, R, P1/P2/P7/P9, J3 PASSED
BUILD SUCCESSFUL
```

(`clean` sul modulo runtime: worker Gradle stale `NoClassDefFoundError`; non `--rerun-tasks`, non aggregato.)

```
./gradlew :adapters:mcp:clean :adapters:mcp:test
McpAdapterAcceptanceTest > S1_substitution_same_facts_yield_identical_belief_bytes() PASSED
McpAdapterAcceptanceTest > S3_runtime_gradle_does_not_depend_on_mcp_adapter() PASSED
McpAdapterAcceptanceTest > MCP_BOUND_1_call_returns_candidate_apply_without_evaluate_throws() PASSED
McpAdapterAcceptanceTest > receipt_becomes_action_event_and_never_writes_belief() PASSED
McpAdapterAcceptanceTest > MCP_BOUND_2_admission_in_the_middle_keeps_belief_bytes_and_omits_admission_metadata() PASSED
McpAdapterArchitectureTest > S3_core_runtime_does_not_depend_on_adapters() PASSED
McpAdapterArchitectureTest > S2_adapter_stops_at_observation_and_does_not_write_world() PASSED
BUILD SUCCESSFUL
```

Frozen prediction/projection/a3ui/renderers/intent/launcher: non lanciati (nessun breakage meccanico).

---

## Gate 10 — questo file

PASS. Tabella ACT|invariante|±|PASS/GAP sopra. Grep Gate 8 vuoto. 10/10.

---

## Forward-ref step 3

Nessuno. `consentPresentationHash` resta opzionale sullo `Authorization` e non è un gate di questa run.

---

## Note

- Campi extra su alcuni `ActionEvent` (`AuthorizationGranted.authorization`, `CommandCreated.command` + `beliefRevisionHash`, `CommandDispatched.beliefRevisionHash`) servono AUTH-1/2; non sono gate nuovi.
- `:core:action` testImplementation `:core:world` solo per ACT-001/ArchUnit di confine. Production ↛ BeliefState.
- Interazione action↔admission: id opaco dopo `evaluate` + `BeliefState.apply`. Non è riapertura del gate admission.

---

## Tag e bump (10/10)

`core-action-v0.1`. Bump di sorgente: `core-v0.5` (`:core:runtime`), `mcp-adapter-v0.3` (`:adapters:mcp`). Non bumpati: `:core:admission` / `core-admission-v0.1`, `core-json-v0.1`, renderer-*, intent-model-*, prediction, projection, a3ui, launcher.

Niente push. STOP. Poi solo ciò che il committente ordina esplicitamente.
