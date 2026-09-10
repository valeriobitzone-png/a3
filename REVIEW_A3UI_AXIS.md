# REVIEW_A3UI_AXIS

AUDIT-FIRST. Protocol: FASE A3UI-AXIS (catalogo v2 additivo). Baseline `673531f` (`broker-v0.2` chiuso). Core / admission / action / json / prediction / projection / adapters / intent-model / broker **frozen**. Niente push. L'asse è un campo su `Node`, non un ruolo.

**Unfrozen:** `:a3ui`, `:renderers:android-core`, `:renderers:android-compose`, `:launcher`.  
**Schema:** `schemas/a3uisurface.schema.json` (campo `axis` opzionale su `node`).

`asOf` e `producedAt` sono input al confine. `presentationHash = SHA-256(canonical(nodes + bindings))` — clock-free, motion fuori.

---

## Freeze (AX-008)

```
git diff --stat -- core/ core/json core/admission core/action prediction/ projection/ adapters/ intent-model/ broker/
```

vuoto.

Moduli eseguiti **separati**, niente `./gradlew test` aggregato, niente `--rerun-tasks`:

```
./gradlew :a3ui:test --offline
./gradlew :renderers:android-core:test --offline
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :launcher:testDebugUnitTest --offline
```

`:a3ui` non dipende da `:core:admission` / `:core:action`. ActionPhase / CompensationPhase / Esito.HELD entrano come **wire names** (`unknown`, `compensated`, `admissionHeld`) su `EpistemicFacts`.

---

## Tabella audit — AX-001..008

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| AX-001 | A3UI-1 purity | stesso `PresentationState`+`asOf` → stesso `presentationHash`; `producedAt` diverso non entra nel hash | `grep` `now()` / `Instant.now` in `a3ui/src/main` vuoto | **PASS** |
| AX-002 | CANON omit-when-default | `Node` senza asse ≡ `axis=EpistemicAxis()` ≡ tutti i default espliciti | byte con `"axis"` / `"support"` su default → fail | **PASS** |
| AX-003 | A3UI-3 incerto esposto | conf 0.6 → MEDIUM; HELD → HELD; `actionPhase=unknown` → UNKNOWN; `expires<asOf` → STALE | default axis su uno di questi → `uncertain rendered as certain` | **PASS** |
| AX-004 | A3UI-6 a11y | `stateDescription` non vuota; `reducedMotion` la mostra comunque (`n1-axis` + semantics) | motion come unica resa — assente | **PASS** |
| AX-005 | A3UI-4 catalogo chiuso | surface v2 su `CatalogProfile.V1` → copy resa, asse `null`, `degradation=axis-ignored` | crash v1 su asse v2 — assente | **PASS** |
| AX-006 | A3UI-5 | `GestureBinding.emit()` → `IntentCandidate`; ArchUnit ↛ `Command` / `BeliefState` | Command / write belief | **PASS** |
| AX-007 | MOTION-BOUNDARY | hash identico al variare di `MotionSpec`; motion JSON diverso | hash che include stiffness | **PASS** |
| AX-008 | freeze + moduli | diff frozen vuoto; EpistemicAxis solo in `:a3ui`; test per modulo | aggregato / `--rerun-tasks` non usati | **PASS** |

**GAP: nessuno.**

---

## Byte canonici (AX-002)

Implicito, `EpistemicAxis()`, e default espliciti (HIGH/FRESH/BELIEVED/NA) — **identici**:

```
{"children":[],"id":"n","role":"text"}
```

Non-default (support MEDIUM), chiave `axis` presente, solo campi non-default:

```
{"axis":{"support":"medium"},"children":[],"id":"n","role":"text"}
```

Disciplina `:core:json` omit-nulls: default omesso, non `{}`.

---

## Negativi (AX-003)

`Epistemic.requireExposed(EpistemicAxis(), facts, asOf)` throw `uncertain rendered as certain` per:

- `Claim.confidence = 0.6`
- `admissionHeld = true`
- `revisionContradicted = true`
- `actionPhase = "unknown"`
- `expiresAt < asOf` (STALE)

Renderizzare incerto-come-certo è non conforme.

---

## Clock (AX-001)

```
rg -n 'now\(\)|Instant\.now' a3ui/src/main
```

vuoto. `DeterministicA3UICompiler(producedAt: Instant, …)` — niente `InstantSource.now()` nel modulo.

---

## Esito test (output reale)

Host: locale, 2026-09-10. Exit code: 0 per ogni modulo.

### `:a3ui:test`

```
A3UIAxisTest > AX_003_uncertain_is_exposed_and_default_axis_fails() PASSED
A3UIAxisTest > AX_001_same_presentation_and_asOf_same_hash_and_main_is_clock_free() PASSED
A3UIAxisTest > AX_006_gesture_binding_emits_intent_candidate_only() PASSED
A3UIAxisTest > AX_002_omit_when_default_canonical_bytes_match() PASSED
A3UIAxisTest > AX_007_motion_does_not_enter_presentation_hash() PASSED
A3UIAxisTest > AX_008_axis_lives_in_a3ui_not_frozen_core_modules() PASSED
A3UIArchitectureTest > A3UI_5_gesture_path_does_not_depend_on_command_or_belief PASSED
A3UIArchitectureTest > A3UI_5_a3ui_does_not_depend_on_belief_state PASSED
J3ByteIdentityTest > J3_bytes_match_corpus_fixed_before_move() PASSED
P57–P64, P28–P41, P40 PASSED
BUILD SUCCESSFUL
```

### `:renderers:android-core:test`

```
A3UIAxisRendererTest > AX_004_non_default_axis_has_state_description_with_reduced_motion() PASSED
A3UIAxisRendererTest > AX_005_v1_path_renders_content_ignores_axis_declares_degradation() PASSED
A3UIAxisRendererTest > AX_006_gesture_interpreter_emits_intent_candidate() PASSED
RendererArchitectureTest > A3UI_5_interpreter_does_not_depend_on_command PASSED
J3ByteIdentityTest > J3_bytes_match_corpus_fixed_before_move() PASSED
R1–R7, P46–P56 PASSED
BUILD SUCCESSFUL
```

### `:renderers:android-compose:testDebugUnitTest`

```
A3UIAxisComposeTest > AX_004_axis_state_description_visible_with_reduced_motion PASSED
R1–R7, R9–R10, P52, P55, T9, T10 PASSED
BUILD SUCCESSFUL
```

### `:launcher:testDebugUnitTest`

```
A3UIAxisLauncherTest > AX_axis_fixture_exposes_medium_support_on_price PASSED
L2, L3, L5–L7, L9, F2, F3, G2, L4 PASSED
BUILD SUCCESSFUL
```

---

## Cosa è l'asse (e cosa non è)

- Campo opzionale su `Node` / `RenderedNode`. I 7 ruoli restano chiusi.
- Derivazione pura: `Claim.confidence` → support; `expires_at` vs `asOf` → freshness; HELD / CONTRADICTED → status; `ActionState` wire → action.
- `stateDescription` = token di enum (`support medium`), non copy di badge.
- `CatalogProfile.V1` su surface v2: contenuto (atom `v`) reso, asse ignorato, `degradation = "axis-ignored"`.
- `GestureBinding.emit()` = `IntentCandidate`. Nessun `Command`.

---

## Tag

`a3ui-core-v0.5` · `renderer-android-v0.8` · `launcher-v0.3`.  
Non bumpati: `core-v0.5`, `core-admission-v0.1`, `core-action-v0.1`, `core-json-v0.1`, prediction, projection, adapters, intent-model, broker. Niente push. STOP.
