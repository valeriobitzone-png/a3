# REVIEW_CORE_ADMISSION

AUDIT-FIRST. Protocol: `CURSOR_CORE_ADMISSION.md`. No extra gates. No push.

## PASSO 0 — PRE-FLIGHT

Command:

```bash
grep -rnE '\bWorldState\b|\bFact\b' renderers/ intent-model/ core/json/ --include='*.kt'
```

`core/json/`: empty (no hits).

Not empty — recclassify these frozen **test** files for mechanical rename only (`Fact`→`Claim`; ArchUnit/string tokens `WorldState`→writer-fold tokens that do not reintroduce `\bWorldState\b`):

- `renderers/android-core/src/test/kotlin/a3/renderers/android/core/RendererCoreAcceptanceTest.kt`
- `renderers/android-compose/src/test/kotlin/a3/renderers/android/compose/RecompositionPurityTest.kt`
- `intent-model/src/test/kotlin/a3/intent/IntentModelAcceptanceTest.kt`
- `intent-model/src/test/kotlin/a3/intent/IntentModelArchitectureTest.kt`

Production trees `renderers/*/src/main`, `intent-model/src/main`, and all of `core/json/` stay untouched.

**Gate 1 / Gate 9 consistency (text, not runtime):** Gate 1 is this documented recclassification, not an empty pre-flight grep. Gate 9 `git diff renderers/ intent-model/ core/json/` is PASS only if the diff is exactly that mechanical rename in the four recclassified tests, plus empty `core/json/` and empty frozen production trees.

After recclassification, the same PASSO 0 command is empty:

```
=== PASSO0 after recclass ===
(empty)
```

**Collision (not a new product type):** `\bWorldState\b` → `BeliefState` cannot rename the mutable writer class onto the existing snapshot `data class BeliefState`. Writer class identifier becomes `BeliefWriter` (EventLog + commit envelope). Fold is `BeliefState.apply(AcceptedObservation)`. Grep gate is empty `\bWorldState\b`, not a third belief type.

**Bump riclassificati (9/9):** `intent-model-v0.2`, `renderer-android-v0.7` (documented transitive). Frozen otherwise: `core-json-v0.1`.

`:core:world` / `:core:world-api` are not in the unfrozen list; they are touched only for `Fact`→`Claim` and the BeliefState write-path (`apply`). `:core:json` engine is not thawed.

---

## Audit table

| test | invariant | path | negativo | PASS/GAP |
|---|---|---|---|---|
| ADM-001 | BELIEF-1 / ADMISSION-1 | `BeliefState.apply(Accepted)` after evaluate; `apply(candidate)` | candidate senza admission → throw | PASS |
| ADM-002 | ADMISSION-2 | `evaluate` twice, `decisionBytes` | admittedIdSet con id già dentro → REJECT_DUPLICATE | PASS |
| ADM-003 | ADMISSION-3 | enum chiuso; ctor | senza/incoerente → throw | PASS |
| ADM-004 | TTL | evaluate ttl 5/60/null/0 | ttl&lt;0 → throw | PASS |
| ADM-005 | blacklist | deny vs allow | blacklist → REJECT_POLICY_DENY | PASS |
| ADM-006 | DUPLICATE-1 evaluate | (source,id) | stesso id source diverse → entrambi ADMIT | PASS |
| ADM-007 | schema | conforme / non conforme | assente / non nel set → REJECT_SCHEMA_INVALID | PASS |
| ADM-007-neg-a | registro | stesso (id,version) digest diverso | register → fail | PASS |
| ADM-007-neg-b | immutabilità schema set | unmodifiable map + register | mutate/register → fail | PASS |
| ADM-007-neg-c | ADMISSION-2 schema | stesso (candidate, digest, asOf, set) | — | PASS |
| ADM-008 | ADMISSION-5 HELD | hold ∧ GROUNDED_BY_MODEL | HELD → log, non Accepted, non fold | PASS |
| ADM-009 | REPLAY-1 fold | apply [Accepted] due volte da vuoto | ri-admission `apply(candidate)` → throw | PASS |
| ADM-010 | BELIEF-1 | apply(Accepted) bump | apply(candidate)/apply(logEntry) → throw | PASS |
| ADM-011 | ADMISSION-4 | ArchUnit + Evaluate.kt | evaluate ↛ BeliefState, no clock, no admittedAt | PASS |
| ADM-012 | LLM-1 | source=document GROUNDED_BY_MODEL | SourceId("llm") → throw | PASS |
| ADM-013 | DUPLICATE-1 | A e B stesso id | A due volte → REJECT_DUPLICATE | PASS |
| ADM-014 | ADMISSION-5 | HELD log | apply(logEntry) throw; HELD→Accepted throw | PASS |
| ADM-015 | DUPLICATE-1 fold | same Accepted ×2 ×3 stesso stateHash | apply(candidate) → throw | PASS |
| MCP-BOUND-1 | ADMISSION-1 confine | `adapter.call()` → ObservationCandidate | apply(candidate) throw; no Accepted dal adapter | PASS |
| MCP-BOUND-2 | ADMISSION-6 | S1 native vs MCP + admission in mezzo | ofState senza reasonCode/policyVersion/admittedAt | PASS |

---

## Gate 1 — pre-flight audit

PASS. Decisione documentata sopra. Hit pre-run: 4 test frozen (renderers ×2, intent-model ×2). `core/json/` vuoto.

---

## Gate 2 — grep word-boundary vuoti

Command:

```bash
grep -rnE '\bWorldState\b|\bFact\b|apply\(ObservationCandidate\)' --include='*.kt' --exclude-dir=build --exclude-dir=.git .
```

Real output: empty (exit 0, no lines).

`WorldStateReplay` / `WorldStateSnapshot` non matchano `\bWorldState\b`. `facts` / `FACT_ORDER` non matchano `\bFact\b`. Overload Kotlin `apply(candidate: ObservationCandidate)` non matcha `apply\(ObservationCandidate\)`.

---

## Gate 3 — ADM-002 decisione pura

```
AdmissionTest > ADM_002_pure_decision_byte_identity_and_duplicate_sensitivity() PASSED
```

Stessi `(candidate, snapshot, asOf, admittedIdSet)` → `decisionBytes` identici. `admittedIdSet` con la coppia già dentro → `REJECT_DUPLICATE`, bytes diversi.

---

## Gate 4 — binding univoco (policyId, policyVersion) → policyDigest

```
AdmissionTest > ADM_007_neg_a_same_id_version_different_digest_fails() PASSED
```

Stesso `(p, 1)` con schema set diverso → digest diverso → `PolicyRegistry.register` throw.

---

## Gate 5 — schema set immutabile e nel digest (ADM-007-neg-a/b/c)

```
AdmissionTest > ADM_007_neg_a_same_id_version_different_digest_fails() PASSED
AdmissionTest > ADM_007_neg_b_mutating_registered_schema_set_fails() PASSED
AdmissionTest > ADM_007_neg_c_same_digest_same_decision_bytes() PASSED
```

`schemas` è `Collections.unmodifiableMap`. Digest copre `schemaSet` + SHA-256 dei byte. Due `evaluate` con lo stesso digest → decision bytes identici.

---

## Gate 6 — validator profile nel digest

```
AdmissionTest > validator_profile_and_version_are_in_policy_digest() PASSED
```

`validatorProfile` o `validatorVersion` diversi → `policyDigest` diverso.

---

## Gate 7 — ADM-015 fold-level

```
AdmissionTest > ADM_015_reapply_same_accepted_is_noop_mutating_apply_throws() PASSED
```

`apply(stessa AcceptedObservation)` ×2 e ×3 → stesso `stateHash` e stessa `version`. `apply(candidate)` → throw.

---

## Gate 8 — MCP-BOUND-2 + ADMISSION-6

```
McpAdapterAcceptanceTest > S1_substitution_same_facts_yield_identical_belief_bytes() PASSED
McpAdapterAcceptanceTest > MCP_BOUND_1_call_returns_candidate_apply_without_evaluate_throws() PASSED
McpAdapterAcceptanceTest > MCP_BOUND_2_admission_in_the_middle_keeps_belief_bytes_and_omits_admission_metadata() PASSED
McpAdapterArchitectureTest > S2_adapter_stops_at_observation_and_does_not_write_world() PASSED
McpAdapterArchitectureTest > S3_core_runtime_does_not_depend_on_adapters() PASSED
```

Runtime.execute passa da `evaluate` (policy permissiva, ADMIT). Native vs MCP → `CanonicalJson.bytesState` identici. `ofState` non contiene `reasonCode`, `admittedAt`, `"policyVersion"`, `"policyId"`. Fold proietta `candidate.data` → `Claim` only.

---

## Gate 9 — git diff frozen coerente col pre-flight

```
=== GATE9 names renderers+intent ===
intent-model/src/test/kotlin/a3/intent/IntentModelAcceptanceTest.kt
intent-model/src/test/kotlin/a3/intent/IntentModelArchitectureTest.kt
renderers/android-compose/src/test/kotlin/a3/renderers/android/compose/RecompositionPurityTest.kt
renderers/android-core/src/test/kotlin/a3/renderers/android/core/RendererCoreAcceptanceTest.kt
=== GATE9 core/json ===
(empty)
```

Diff: solo rename meccanico `Fact`→`Claim` e token ArchUnit `WorldState`→`BeliefWriter` / write-token `a3.core.admission.AcceptedObservation`. Nessun `src/main` frozen. `core/json/` vuoto.

---

## Regression (separati, no aggregato, no `--rerun-tasks`)

| task | result |
|---|---|
| `:core:admission:test` | BUILD SUCCESSFUL, 18 test PASSED |
| `:core:runtime:test` | BUILD SUCCESSFUL (T1–T10, W, V, R, P1/P2/P7/P9, J3) |
| `:prediction:test` | BUILD SUCCESSFUL |
| `:projection:test` | BUILD SUCCESSFUL |
| `:a3ui:test` | BUILD SUCCESSFUL |
| `:adapters:mcp:test` | BUILD SUCCESSFUL |
| `:launcher:testDebugUnitTest` | BUILD SUCCESSFUL |

Nessun test gate su `:renderers/*` o `:intent-model` (frozen wrt BeliefState writer). I 4 test riclassificati compilano via launcher.

---

## Forward-ref step 3

- **Ordine dedup** vs altri gate: questa run usa duplicate → blacklist → schema → ttl → HELD → ADMIT. Non è normativa per lo step 3.
- **Semantica d'ordine del fold** (REPLAY-1): il replay legge `integrate_mode` dall'envelope `observation.accepted`; non è una semantica d'ordine admission.

---

## Note

- `confidenceProfile` è PROVISIONAL pending step 5.
- HELD lifecycle fuori scope: HELD → `AdmissionLogEntry`, mai fold, mai `AcceptedObservation`.
- Regola clock: `evaluate` non legge orologio e non ritorna `admittedAt`. `asOf := candidate.ingestedAt`. `admittedAt` è timbro di confine (`now` già iniettato in Runtime / replay `event.t`).
- `admittedKeys` su `BeliefState` è memoria fold-level DUPLICATE-1 `(source,id)`, omessa da `CanonicalJson.ofState` (non è reasonCode / policyVersion / admittedAt).

---

## Tag e bump (9/9)

`core-admission-v0.1`. Bump di sorgente: `core-v0.4`, `prediction-v0.3`, `projection-v0.3`, `a3ui-v0.4`, `mcp-adapter-v0.2`, `launcher-v0.2`. Riclassificati: `intent-model-v0.2`, `renderer-android-v0.7`. Non bumpati: `core-json-v0.1`.

Niente push. STOP. Prossimo: step 2 (action state machine), non T12-spike.
