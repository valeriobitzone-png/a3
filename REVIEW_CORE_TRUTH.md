# REVIEW_CORE_TRUTH

AUDIT-FIRST. Protocol: FASE CORE-TRUTH — classi di verità + provenienza come legge del dato. Nuovo modulo `:core:truth`. Consuma i tipi di `:core:admission` (`AcceptedObservation`, `SourceType`) senza toccarli. Serializzazione via `:core:json`. Frozen: `core/admission`, `core/action`, `core/json`, `core/temporal`, `a3ui`, `renderers`, `broker`, `agent`, `showcase`. Niente push.

**Contratto:** ogni proposizione porta `truthClass` e `provenance`. UNKNOWN è prima classe. HYPOTHESIS diventa FACT solo con `VerificationAdmitted` in ambiente reale. Un receipt non è un fatto.

---

## Freeze (TC-007)

```
git diff --stat -- core/admission core/action core/json core/temporal a3ui renderers broker agent showcase
```

vuoto. Solo `:core:truth` + `settings.gradle.kts` (`include(":core:truth")`) + questo REVIEW.

---

## Tipi

| TruthClass | Significato |
|------------|-------------|
| `FACT` | osservato e ammesso (verifica deterministica ammessa) |
| `OBSERVATION` | evidenza raccolta (receipt, postcondizione, misura, sandbox) |
| `HYPOTHESIS` | ipotesi non confermata (modello, council, RAG) |
| `UNKNOWN` | evidenza insufficiente — prima classe, mai default nascosto |

Provenance: `OBSERVED_SIGNED` · `DERIVED_MODEL` · `INFERRED` · `HUMAN_ADMITTED`.

`TruthBearer { truthClass, provenance, ref? }` — i primi due non hanno default. `parseBearer` senza campo → `TruthReject` (`missing truthClass` / `missing provenance`).

`VerificationAdmitted { verificationId, admittedId, environment: REAL | SANDBOX }`. `admittedId` è l'`id` di un `AcceptedObservation` (ponte admission, senza modificare admission).

---

## Invarianti

| Id | Legge |
|----|-------|
| TR-001 | HYPOTHESIS → FACT solo con `VerificationAdmitted` REAL; `ref` = `verificationId`. Senza verifica resta HYPOTHESIS. Sandbox non promuove a FACT. |
| TR-002 | Evidenza insufficiente → `fromInsufficient` = UNKNOWN. Nessun default a FACT/HYPOTHESIS. |
| TR-003 | Mancanza di classe o provenance → rifiuto esplicito. |
| TR-004 | exit 0 / `"SUCCESS"` → OBSERVATION (`fromProcessOutcome`), mai FACT. |
| TR-005 | Sandbox → OBSERVATION. FACT solo `fromRealAdmitted` / promote con environment REAL. |
| TR-006 | Mapping asse dichiarativo (stringhe a3ui, senza dipendere da a3ui): HYPOTHESIS ↛ BELIEVED; UNKNOWN → support/status `unknown`; FACT + BELIEVED solo se freshness ≠ `stale`. |

`projectAxis` non importa `a3ui`. `requireAxisCoherent` rifiuta le dichiarazioni vietate.

`provenanceFrom(SourceType)` è l'unico mapping admission → provenance (DIRECT→OBSERVED_SIGNED, GROUNDED_BY_MODEL→DERIVED_MODEL, INFERRED→INFERRED). HUMAN_ADMITTED non ha SourceType.

---

## Vettori lock (Step 4 / 2ª impl.)

File: `core/truth/src/test/resources/truth-vectors.json`  
SHA-1: `59ce3d78c3cabdeaa5109f0bcf8a3431818e42e9`

Chiavi del corpus (TreeMap, enum lowercase, Instant non presente):

| Chiave | truth_class / asse |
|--------|-------------------|
| `hypothesis` | hypothesis / derived_model / `model:slot` |
| `fact_promoted` | fact / observed_signed / `ver-1` |
| `unknown` | unknown / inferred / `gap:price` |
| `receipt` | observation / `exit:0:SUCCESS` |
| `sandbox` | observation / `ver-sand` |
| `axis_hypothesis` | status `held` (non believed) |
| `axis_unknown` | support/status/action `unknown` |
| `axis_fact_fresh` | status `believed` |
| `axis_fact_stale` | status `held` |
| `verification` | REAL, admitted_id `obs-verify` |

Una 2ª implementazione deve riprodurre questi byte. Rigenerare solo con `TC_DUMP=1`.

---

## Tabella audit — TC-001..007

| Test | Invariante | Percorso | Negativo | PASS |
|------|------------|----------|----------|------|
| TC-001 | promozione esplicita | `promoteHypothesis(null)` resta; REAL → FACT con `ref=ver-1` | OBSERVATION non promuove | **PASS** |
| TC-002 | UNKNOWN prima classe | `fromInsufficient` | non FACT, non HYPOTHESIS | **PASS** |
| TC-003 | obbligatorietà | `parseBearer` | manca provenance/classe → `TruthReject` | **PASS** |
| TC-004 | receipt ≠ risultato | `fromProcessOutcome(0,"SUCCESS")` | truth_class ≠ fact | **PASS** |
| TC-005 | sandbox ≠ resolved | promote SANDBOX → OBSERVATION; `fromRealAdmitted(SANDBOX)` throw | FACT solo REAL | **PASS** |
| TC-006 | asse dichiarativo | fixture hypo/unknown/fact×freshness | HYPOTHESIS+BELIEVED e FACT+STALE+BELIEVED throw | **PASS** |
| TC-007 | freeze + vettori + no `now()` | git diff + ArchUnit + `truth-vectors.json` | dipendenze temporal/a3ui/action | **PASS** |

Gate:

```
./gradlew :core:truth:test --offline
```

---

## Vietato (verificato)

Admission e temporal non toccati. Nessun `now()`. Nessun default silenzioso di truth class. Nessuna promozione implicita. Receipt non è FACT. Niente push.

---

## Tag

`core-truth-v0.1` solo a gate verde. Niente push.
